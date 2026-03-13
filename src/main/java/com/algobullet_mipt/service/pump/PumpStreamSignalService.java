package com.algobullet_mipt.service.pump;

import com.algobullet_mipt.domain.market.model.KlineCandle;
import com.algobullet_mipt.domain.market.model.KlineStreamUpdate;
import com.algobullet_mipt.model.PumpSettings;
import com.algobullet_mipt.model.Signal;
import com.algobullet_mipt.service.SettingsService;
import com.algobullet_mipt.service.SignalHistoryService;
import com.algobullet_mipt.service.UserSignalPushService;
import com.algobullet_mipt.service.market.KlineStreamRuntimeService;
import com.algobullet_mipt.service.market.TimeframeDurationResolver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.features", name = "use-real-market-data", havingValue = "true")
@Slf4j
public class PumpStreamSignalService {

    private static final int MAX_CANDLES_PER_SYMBOL = 20;
    private static final int HISTORY_LIMIT = 3;

    private final SettingsService settingsService;
    private final KlineStreamRuntimeService runtimeService;
    private final SignalHistoryService signalHistoryService;
    private final UserSignalPushService userSignalPushService;

    private final ConcurrentMap<SubscriptionKey, SubscriptionState> subscriptions = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        refreshSubscriptions();
    }

    @PreDestroy
    public void shutdown() {
        for (SubscriptionState state : subscriptions.values()) {
            runtimeService.unsubscribe(state.listenerId());
        }
        subscriptions.clear();
    }

    public synchronized void refreshSubscriptions() {
        List<SettingsService.OwnedPumpSettings> allSettings = settingsService.getAllPumpSettings();
        Set<SubscriptionKey> desiredKeys = new LinkedHashSet<>();

        for (SettingsService.OwnedPumpSettings owned : allSettings) {
            PumpSettings pump = owned.settings();
            if (!pump.isEnabled()) {
                continue;
            }

            for (String symbol : pump.getWatchlist()) {
                desiredKeys.add(new SubscriptionKey(owned.userId(), symbol, pump.getTimeframe()));
            }
        }

        List<SubscriptionKey> toRemove = subscriptions.keySet().stream()
                .filter(key -> !desiredKeys.contains(key))
                .toList();
        for (SubscriptionKey key : toRemove) {
            SubscriptionState removed = subscriptions.remove(key);
            if (removed != null) {
                runtimeService.unsubscribe(removed.listenerId());
                log.info("Pump stream: удалена подписка user={} {} {}", key.userId(), key.symbol(), key.timeframe());
            }
        }

        for (SubscriptionKey key : desiredKeys) {
            if (subscriptions.containsKey(key)) {
                continue;
            }
            try {
                subscriptions.put(key, createSubscription(key, resolveThreshold(allSettings, key)));
            } catch (Exception ex) {
                log.warn("Pump stream: не удалось создать подписку user={} {} {}: {}",
                        key.userId(), key.symbol(), key.timeframe(), ex.getMessage());
            }
        }
    }

    private double resolveThreshold(List<SettingsService.OwnedPumpSettings> allSettings, SubscriptionKey key) {
        return allSettings.stream()
                .filter(owned -> key.userId().equals(owned.userId()))
                .map(SettingsService.OwnedPumpSettings::settings)
                .filter(PumpSettings::isEnabled)
                .filter(pump -> key.timeframe().equals(pump.getTimeframe()))
                .map(PumpSettings::getMinChangePercent)
                .findFirst()
                .orElse(0.8);
    }

    private SubscriptionState createSubscription(SubscriptionKey key, double thresholdPercent) {
        SubscriptionState state = new SubscriptionState(key, thresholdPercent);
        runtimeService.ensureRecentCandles(key.symbol(), key.timeframe(), HISTORY_LIMIT);
        preloadFromRuntime(state);

        UUID listenerId = runtimeService.subscribe(
                key.symbol(),
                key.timeframe(),
                HISTORY_LIMIT,
                update -> onKlineUpdate(state, update)
        );
        state.listenerId = listenerId;

        log.info("Pump stream: добавлена подписка user={} {} {} threshold={}%",
                key.userId(), key.symbol(), key.timeframe(), thresholdPercent);
        return state;
    }

    private void preloadFromRuntime(SubscriptionState state) {
        List<KlineCandle> candles = runtimeService.getRecentCandles(
                state.key.symbol(),
                state.key.timeframe(),
                HISTORY_LIMIT
        );
        synchronized (state.monitor) {
            for (KlineCandle candle : candles) {
                upsertCandle(state.candles, candle);
            }
        }
    }

    private void onKlineUpdate(SubscriptionState state, KlineStreamUpdate update) {
        if (!update.closed()) {
            return;
        }

        Signal signal = null;
        synchronized (state.monitor) {
            upsertCandle(state.candles, update.candle());
            if (state.candles.size() >= 2) {
                KlineCandle[] recent = state.candles.toArray(KlineCandle[]::new);
                KlineCandle previous = recent[recent.length - 2];
                KlineCandle latest = recent[recent.length - 1];
                signal = buildPumpSignal(state, previous, latest);
                if (signal != null) {
                    state.lastSignalTime = signal.time();
                }
            }
        }

        if (signal != null) {
            try {
                userSignalPushService.pushSignal(
                        state.key.userId(),
                        signal,
                        SignalHistoryService.SOURCE_PUMP_WS,
                        state.key.timeframe()
                );
            } catch (Exception ex) {
                log.warn("Pump stream: ошибка сохранения сигнала user={} {} {}: {}",
                        state.key.userId(), signal.symbol(), signal.type(), ex.getMessage());
            }
            try {
                signalHistoryService.savePumpWsSignal(state.key.userId(), signal, state.key.timeframe());
            } catch (Exception ex) {
                log.warn("Pump stream: не удалось отправить сигнал в websocket user={} {} {}: {}",
                        state.key.userId(), signal.symbol(), signal.type(), ex.getMessage());
            }
        }
    }

    private Signal buildPumpSignal(SubscriptionState state, KlineCandle previous, KlineCandle latest) {
        if (previous == null || latest == null || previous.close() == null || latest.close() == null) {
            return null;
        }
        if (previous.close().signum() <= 0) {
            return null;
        }

        BigDecimal changePercent = latest.close()
                .subtract(previous.close())
                .multiply(BigDecimal.valueOf(100))
                .divide(previous.close(), 4, RoundingMode.HALF_UP);

        if (changePercent.doubleValue() < state.thresholdPercent) {
            return null;
        }

        Instant signalTime = latest.openTime() != null
                ? latest.openTime().plus(TimeframeDurationResolver.resolve(state.key.timeframe()))
                : Instant.now();
        if (state.lastSignalTime != null && !signalTime.isAfter(state.lastSignalTime)) {
            return null;
        }

        int strength = Math.max(1, Math.min(5, (int) Math.round(changePercent.doubleValue() / Math.max(0.5, state.thresholdPercent))));
        return new Signal(
                signalTime,
                state.key.symbol(),
                "PUMP",
                "Рост %.2f%% за %s (stream)".formatted(changePercent.doubleValue(), state.key.timeframe()),
                strength
        );
    }

    private void upsertCandle(Deque<KlineCandle> candles, KlineCandle candle) {
        KlineCandle last = candles.peekLast();
        if (last != null && last.openTime().equals(candle.openTime())) {
            candles.removeLast();
        }
        candles.addLast(candle);
        while (candles.size() > MAX_CANDLES_PER_SYMBOL) {
            candles.removeFirst();
        }
    }

    private record SubscriptionKey(Long userId, String symbol, String timeframe) {
    }

    private static final class SubscriptionState {
        private final Object monitor = new Object();
        private final SubscriptionKey key;
        private final double thresholdPercent;
        private final Deque<KlineCandle> candles = new ArrayDeque<>();
        private volatile UUID listenerId;
        private Instant lastSignalTime;

        private SubscriptionState(SubscriptionKey key, double thresholdPercent) {
            this.key = key;
            this.thresholdPercent = thresholdPercent;
        }

        private UUID listenerId() {
            return listenerId;
        }
    }
}
