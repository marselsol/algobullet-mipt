package com.algobullet_mipt.experiment.bybitlatency;

import com.algobullet_mipt.domain.market.model.KlineCandle;
import com.algobullet_mipt.domain.market.model.KlineStreamUpdate;
import com.algobullet_mipt.domain.market.port.MarketDataPort;
import com.algobullet_mipt.service.market.KlineStreamRuntimeService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@ConditionalOnProperty(prefix = "app.experiment.bybit-latency", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "app.features", name = "use-real-market-data", havingValue = "true")
@Slf4j
public class WsLatencyExperimentService {

    private static final int HISTORY_LIMIT = 3;
    private static final int MAX_CANDLES_PER_SYMBOL = 5;

    private final BybitLatencyExperimentProperties properties;
    private final KlineStreamRuntimeService runtimeService;
    private final MarketDataPort marketDataPort;
    private final ExperimentPumpSignalEvaluator evaluator;
    private final BybitLatencyMeasurementWriter writer;
    private final ConcurrentMap<String, SubscriptionState> subscriptions = new ConcurrentHashMap<>();

    public WsLatencyExperimentService(
            BybitLatencyExperimentProperties properties,
            KlineStreamRuntimeService runtimeService,
            MarketDataPort marketDataPort,
            ExperimentPumpSignalEvaluator evaluator,
            BybitLatencyMeasurementWriter writer
    ) {
        this.properties = properties;
        this.runtimeService = runtimeService;
        this.marketDataPort = marketDataPort;
        this.evaluator = evaluator;
        this.writer = writer;
    }

    @PostConstruct
    public void init() {
        if (!properties.getMode().usesWs()) {
            return;
        }

        for (String rawSymbol : properties.getSymbols()) {
            Optional<String> symbol = marketDataPort.normalizeLinearSymbol(rawSymbol);
            if (symbol.isEmpty()) {
                continue;
            }

            SubscriptionState state = new SubscriptionState(symbol.get());
            runtimeService.ensureRecentCandles(symbol.get(), properties.getTimeframe(), HISTORY_LIMIT);
            preloadHistory(state);

            UUID listenerId = runtimeService.subscribe(
                    symbol.get(),
                    properties.getTimeframe(),
                    HISTORY_LIMIT,
                    update -> onUpdate(state, update)
            );
            state.listenerId = listenerId;
            subscriptions.put(symbol.get(), state);
        }

        log.info("Bybit latency experiment WS enabled: symbols={} timeframe={}",
                subscriptions.keySet(), properties.getTimeframe());
    }

    @PreDestroy
    public void shutdown() {
        for (SubscriptionState state : subscriptions.values()) {
            if (state.listenerId != null) {
                runtimeService.unsubscribe(state.listenerId);
            }
        }
        subscriptions.clear();
    }

    private void preloadHistory(SubscriptionState state) {
        List<KlineCandle> candles = runtimeService.getRecentCandles(state.symbol, properties.getTimeframe(), HISTORY_LIMIT);
        synchronized (state.monitor) {
            for (KlineCandle candle : candles) {
                upsert(state.candles, candle);
            }
        }
    }

    private void onUpdate(SubscriptionState state, KlineStreamUpdate update) {
        if (!update.closed()) {
            return;
        }

        BybitLatencySignal signal = null;
        synchronized (state.monitor) {
            upsert(state.candles, update.candle());
            if (state.candles.size() >= 2) {
                KlineCandle[] recent = state.candles.toArray(KlineCandle[]::new);
                KlineCandle previous = recent[recent.length - 2];
                KlineCandle latestClosed = recent[recent.length - 1];
                signal = evaluator.buildClosedCandleObservation(
                        state.symbol,
                        properties.getTimeframe(),
                        properties.getMinChangePercent(),
                        previous,
                        latestClosed
                ).orElse(null);
                if (signal != null && !shouldRecord(state, signal.signalTime())) {
                    signal = null;
                }
            }
        }

        if (signal != null) {
            writer.write(BybitLatencyExperimentTransport.WS, signal, update.receivedAt(), Instant.now());
        }
    }

    private boolean shouldRecord(SubscriptionState state, Instant signalTime) {
        if (state.lastSignalTime == null || signalTime.isAfter(state.lastSignalTime)) {
            state.lastSignalTime = signalTime;
            return true;
        }
        return false;
    }

    private void upsert(Deque<KlineCandle> candles, KlineCandle candle) {
        KlineCandle last = candles.peekLast();
        if (last != null && last.openTime().equals(candle.openTime())) {
            candles.removeLast();
        }
        candles.addLast(candle);
        while (candles.size() > MAX_CANDLES_PER_SYMBOL) {
            candles.removeFirst();
        }
    }

    private static final class SubscriptionState {
        private final Object monitor = new Object();
        private final String symbol;
        private final Deque<KlineCandle> candles = new ArrayDeque<>();
        private UUID listenerId;
        private Instant lastSignalTime;

        private SubscriptionState(String symbol) {
            this.symbol = symbol;
        }
    }
}
