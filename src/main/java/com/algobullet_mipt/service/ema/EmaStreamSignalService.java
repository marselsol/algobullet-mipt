package com.algobullet_mipt.service.ema;

import com.algobullet_mipt.domain.market.model.KlineCandle;
import com.algobullet_mipt.domain.market.model.KlineStreamUpdate;
import com.algobullet_mipt.model.EmaSettings;
import com.algobullet_mipt.model.Signal;
import com.algobullet_mipt.service.SettingsService;
import com.algobullet_mipt.service.SignalHistoryService;
import com.algobullet_mipt.service.market.KlineStreamRuntimeService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.features",
        name = "use-real-market-data",
        havingValue = "true"
)
@Slf4j
public class EmaStreamSignalService {

    private static final int MAX_SIGNALS = 200;
    private static final int MAX_CANDLES_PER_WATCH = 500;

    private final SettingsService settingsService;
    private final KlineStreamRuntimeService runtimeService;
    private final ObjectProvider<SignalHistoryService> signalHistoryServiceProvider;

    private final Map<WatchKey, WatchSubscription> subscriptions = new ConcurrentHashMap<>();
    private final Deque<Signal> recentSignals = new ArrayDeque<>();
    private final Object recentSignalsMonitor = new Object();

    @PostConstruct
    public void init() {
        refreshSubscriptions();
    }

    @PreDestroy
    public void shutdown() {
        for (WatchSubscription subscription : subscriptions.values()) {
            runtimeService.unsubscribe(subscription.listenerId());
        }
        subscriptions.clear();
    }

    public synchronized void refreshSubscriptions() {
        EmaSettings ema = settingsService.ema();
        Set<WatchKey> desiredKeys = ema.getWatchlist().stream()
                .map(WatchKey::fromWatch)
                .collect(java.util.stream.Collectors.toSet());

        List<WatchKey> toRemove = subscriptions.keySet().stream()
                .filter(existing -> !desiredKeys.contains(existing))
                .toList();
        for (WatchKey key : toRemove) {
            WatchSubscription removed = subscriptions.remove(key);
            if (removed != null) {
                runtimeService.unsubscribe(removed.listenerId());
                log.info("EMA stream: удалена подписка {} {}", key.symbol(), key.timeframe());
            }
        }

        for (EmaSettings.EmaWatch watch : ema.getWatchlist()) {
            WatchKey key = WatchKey.fromWatch(watch);
            if (subscriptions.containsKey(key)) {
                continue;
            }
            try {
                subscriptions.put(key, createSubscription(watch));
            } catch (Exception ex) {
                log.warn("EMA stream: не удалось создать подписку {} {}: {}",
                        watch.getSymbol(), watch.getTimeframe(), ex.getMessage());
            }
        }
    }

    public List<Signal> getRecentSignals(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        synchronized (recentSignalsMonitor) {
            return recentSignals.stream()
                    .sorted(Comparator.comparing(Signal::time).reversed())
                    .limit(limit)
                    .toList();
        }
    }

    private WatchSubscription createSubscription(EmaSettings.EmaWatch watch) {
        WatchState state = new WatchState(watch);
        int historyLimit = calculateHistoryLimit(watch);

        runtimeService.ensureRecentCandles(watch.getSymbol(), watch.getTimeframe(), historyLimit);
        preloadFromRuntime(state, historyLimit);

        UUID listenerId = runtimeService.subscribe(
                watch.getSymbol(),
                watch.getTimeframe(),
                historyLimit,
                update -> onKlineUpdate(state, update)
        );

        log.info("EMA stream: добавлена подписка {} fast={} slow={} tf={}",
                watch.getSymbol(), watch.getFast(), watch.getSlow(), watch.getTimeframe());
        return new WatchSubscription(listenerId, state);
    }

    private void preloadFromRuntime(WatchState state, int historyLimit) {
        List<KlineCandle> candles = runtimeService.getRecentCandles(
                state.watch.getSymbol(),
                state.watch.getTimeframe(),
                historyLimit
        );
        synchronized (state.monitor) {
            for (KlineCandle candle : candles) {
                upsertCandle(state.candles, candle);
            }
        }
    }

    private int calculateHistoryLimit(EmaSettings.EmaWatch watch) {
        return watch.getSlow() + 3;
    }

    private void onKlineUpdate(WatchState state, KlineStreamUpdate update) {
        if (!Boolean.TRUE.equals(update.closed())) {
            return;
        }

        Signal signalToStore;
        synchronized (state.monitor) {
            upsertCandle(state.candles, update.candle());
            signalToStore = buildSignalIfCrossed(state, update.candle().openTime());
            if (signalToStore != null) {
                state.lastSignalTime = signalToStore.time();
            }
        }

        if (signalToStore != null) {
            addRecentSignal(signalToStore);
            saveSignalToHistory(signalToStore, state.watch.getTimeframe());
            log.info("EMA stream signal: {} {} {}", signalToStore.symbol(), signalToStore.type(), signalToStore.text());
        }
    }

    private void saveSignalToHistory(Signal signal, String timeframe) {
        SignalHistoryService signalHistoryService = signalHistoryServiceProvider.getIfAvailable();
        if (signalHistoryService == null) {
            return;
        }
        try {
            signalHistoryService.saveEmaStreamSignal(signal, timeframe);
        } catch (Exception ex) {
            log.warn("EMA stream: не удалось сохранить сигнал в БД {} {}: {}",
                    signal.symbol(), signal.type(), ex.getMessage());
        }
    }

    private Signal buildSignalIfCrossed(WatchState state, Instant candleTime) {
        if (state.candles.size() < state.watch.getSlow() + 2) {
            return null;
        }

        BarSeries series = new BaseBarSeriesBuilder().withName(state.watch.getSymbol()).build();
        Duration barDuration = toBarDuration(state.watch.getTimeframe());

        for (KlineCandle candle : state.candles) {
            series.barBuilder()
                    .timePeriod(barDuration)
                    .endTime(candle.openTime().plus(barDuration))
                    .openPrice(candle.open())
                    .highPrice(candle.high())
                    .lowPrice(candle.low())
                    .closePrice(candle.close())
                    .volume(candle.volume())
                    .amount(candle.turnover())
                    .add();
        }

        int last = series.getEndIndex();
        int prev = last - 1;
        if (prev < 0) {
            return null;
        }

        ClosePriceIndicator close = new ClosePriceIndicator(series);
        EMAIndicator fastEma = new EMAIndicator(close, state.watch.getFast());
        EMAIndicator slowEma = new EMAIndicator(close, state.watch.getSlow());

        Num fastPrev = fastEma.getValue(prev);
        Num slowPrev = slowEma.getValue(prev);
        Num fastNow = fastEma.getValue(last);
        Num slowNow = slowEma.getValue(last);

        boolean crossedUp = fastPrev.isLessThanOrEqual(slowPrev) && fastNow.isGreaterThan(slowNow);
        boolean crossedDown = fastPrev.isGreaterThanOrEqual(slowPrev) && fastNow.isLessThan(slowNow);

        if (!crossedUp && !crossedDown) {
            return null;
        }

        String type = crossedUp ? "EMA_BUY" : "EMA_SELL";
        String directionText = crossedUp ? "bullish crossover" : "bearish crossover";

        if (state.lastSignalTime != null && !candleTime.isAfter(state.lastSignalTime)) {
            return null;
        }

        return new Signal(
                candleTime,
                state.watch.getSymbol(),
                type,
                "EMA%s/%s %s on %s".formatted(
                        state.watch.getFast(),
                        state.watch.getSlow(),
                        directionText,
                        state.watch.getTimeframe()
                ),
                5
        );
    }

    private void addRecentSignal(Signal signal) {
        synchronized (recentSignalsMonitor) {
            recentSignals.addFirst(signal);
            while (recentSignals.size() > MAX_SIGNALS) {
                recentSignals.removeLast();
            }
        }
    }

    private void upsertCandle(Deque<KlineCandle> candles, KlineCandle candle) {
        KlineCandle last = candles.peekLast();
        if (last != null && last.openTime().equals(candle.openTime())) {
            candles.removeLast();
        }
        candles.addLast(candle);
        while (candles.size() > MAX_CANDLES_PER_WATCH) {
            candles.removeFirst();
        }
    }

    private Duration toBarDuration(String timeframe) {
        return switch (timeframe) {
            case "1m" -> Duration.ofMinutes(1);
            case "3m" -> Duration.ofMinutes(3);
            case "5m" -> Duration.ofMinutes(5);
            case "15m" -> Duration.ofMinutes(15);
            case "30m" -> Duration.ofMinutes(30);
            case "1h" -> Duration.ofHours(1);
            case "2h" -> Duration.ofHours(2);
            case "4h" -> Duration.ofHours(4);
            case "6h" -> Duration.ofHours(6);
            case "12h" -> Duration.ofHours(12);
            case "1d" -> Duration.ofDays(1);
            case "1w" -> Duration.ofDays(7);
            default -> throw new IllegalArgumentException("Неподдерживаемый timeframe: " + timeframe);
        };
    }

    private record WatchKey(String symbol, int fast, int slow, String timeframe) {
        private static WatchKey fromWatch(EmaSettings.EmaWatch watch) {
            return new WatchKey(watch.getSymbol(), watch.getFast(), watch.getSlow(), watch.getTimeframe());
        }
    }

    private record WatchSubscription(UUID listenerId, WatchState state) {
    }

    private static final class WatchState {
        private final Object monitor = new Object();
        private final EmaSettings.EmaWatch watch;
        private final Deque<KlineCandle> candles = new ArrayDeque<>();
        private Instant lastSignalTime;

        private WatchState(EmaSettings.EmaWatch watch) {
            this.watch = watch;
        }
    }
}
