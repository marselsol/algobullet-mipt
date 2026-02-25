package com.algobullet_mipt.service.pump;

import com.algobullet_mipt.domain.market.model.KlineCandle;
import com.algobullet_mipt.domain.market.model.KlineStreamUpdate;
import com.algobullet_mipt.domain.market.port.MarketDataPort;
import com.algobullet_mipt.model.PumpSettings;
import com.algobullet_mipt.model.Signal;
import com.algobullet_mipt.service.SettingsService;
import com.algobullet_mipt.service.SignalHistoryService;
import com.algobullet_mipt.service.market.KlineStreamRuntimeService;
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
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
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

    private final SettingsService settingsService;
    private final MarketDataPort marketDataPort;
    private final KlineStreamRuntimeService runtimeService;
    private final SignalHistoryService signalHistoryService;

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
        PumpSettings pump = settingsService.pump();
        String timeframe = pump.getTimeframe();

        Set<String> desiredSymbols = resolveSymbolsForStreaming(pump);
        Set<SubscriptionKey> desiredKeys = desiredSymbols.stream()
                .map(symbol -> new SubscriptionKey(symbol, timeframe))
                .collect(java.util.stream.Collectors.toSet());

        List<SubscriptionKey> toRemove = subscriptions.keySet().stream()
                .filter(key -> !desiredKeys.contains(key) || !pump.isEnabled())
                .toList();
        for (SubscriptionKey key : toRemove) {
            SubscriptionState removed = subscriptions.remove(key);
            if (removed != null) {
                runtimeService.unsubscribe(removed.listenerId());
                log.info("Pump stream: удалена подписка {} {}", key.symbol(), key.timeframe());
            }
        }

        if (!pump.isEnabled()) {
            return;
        }

        for (SubscriptionKey key : desiredKeys) {
            if (subscriptions.containsKey(key)) {
                continue;
            }
            try {
                subscriptions.put(key, createSubscription(key, pump.getMinChangePercent()));
            } catch (Exception ex) {
                log.warn("Pump stream: не удалось создать подписку {} {}: {}", key.symbol(), key.timeframe(), ex.getMessage());
            }
        }
    }

    private Set<String> resolveSymbolsForStreaming(PumpSettings pump) {
        Set<String> symbols = new LinkedHashSet<>(pump.getWatchlist());
        if (!symbols.isEmpty()) {
            return symbols;
        }

        try {
            symbols.addAll(marketDataPort.getTopUsdtSymbols(20));
        } catch (Exception ex) {
            log.warn("Pump stream: не удалось загрузить top symbols для fallback: {}", ex.getMessage());
        }
        return symbols;
    }

    private SubscriptionState createSubscription(SubscriptionKey key, double thresholdPercent) {
        SubscriptionState state = new SubscriptionState(key, thresholdPercent);
        preload(state);

        UUID listenerId = runtimeService.subscribe(
                key.symbol(),
                key.timeframe(),
                update -> onKlineUpdate(state, update)
        );
        state.listenerId = listenerId;

        log.info("Pump stream: добавлена подписка {} {} threshold={}%", key.symbol(), key.timeframe(), thresholdPercent);
        return state;
    }

    private void preload(SubscriptionState state) {
        try {
            List<KlineCandle> candles = marketDataPort.getRecentKlines(state.key.symbol(), state.key.timeframe(), 3);
            synchronized (state.monitor) {
                for (KlineCandle candle : candles) {
                    upsertCandle(state.candles, candle);
                }
            }
        } catch (Exception ex) {
            log.warn("Pump stream: preload failed for {} {}: {}", state.key.symbol(), state.key.timeframe(), ex.getMessage());
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
                signalHistoryService.savePumpWsSignal(signal, state.key.timeframe());
            } catch (Exception ex) {
                log.warn("Pump stream: ошибка сохранения сигнала {} {}: {}", signal.symbol(), signal.type(), ex.getMessage());
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

        Instant signalTime = latest.openTime() != null ? latest.openTime() : Instant.now();
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

    private record SubscriptionKey(String symbol, String timeframe) {
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
