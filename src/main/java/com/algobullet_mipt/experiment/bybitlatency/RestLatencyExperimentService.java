package com.algobullet_mipt.experiment.bybitlatency;

import com.algobullet_mipt.domain.market.model.KlineCandle;
import com.algobullet_mipt.service.AppFeatureFlagService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@ConditionalOnProperty(prefix = "app.experiment.bybit-latency", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "app.features", name = "use-real-market-data", havingValue = "true")
@Slf4j
public class RestLatencyExperimentService {

    private final BybitLatencyExperimentProperties properties;
    private final AppFeatureFlagService featureFlagService;
    private final BybitExperimentRestMarketDataClient restMarketDataClient;
    private final ExperimentPumpSignalEvaluator evaluator;
    private final BybitLatencyMeasurementWriter writer;
    private final ConcurrentMap<String, Instant> lastSignalTimes = new ConcurrentHashMap<>();
    private volatile Instant startedAt;

    public RestLatencyExperimentService(
            BybitLatencyExperimentProperties properties,
            AppFeatureFlagService featureFlagService,
            BybitExperimentRestMarketDataClient restMarketDataClient,
            ExperimentPumpSignalEvaluator evaluator,
            BybitLatencyMeasurementWriter writer
    ) {
        this.properties = properties;
        this.featureFlagService = featureFlagService;
        this.restMarketDataClient = restMarketDataClient;
        this.evaluator = evaluator;
        this.writer = writer;
    }

    @PostConstruct
    public void init() {
        startedAt = Instant.now();
        if (properties.getMode().usesRest()) {
            log.info("Bybit latency experiment REST enabled: symbols={} timeframe={} delay={}ms startedAt={}",
                    properties.getSymbols(), properties.getTimeframe(), properties.getPollingDelayMs(), startedAt);
        }
    }

    @Scheduled(fixedDelayString = "${app.experiment.bybit-latency.polling-delay-ms:500}")
    public void poll() {
        if (!properties.getMode().usesRest() || !featureFlagService.isBybitLatencyExperimentEnabled()) {
            return;
        }

        for (String rawSymbol : properties.getSymbols()) {
            Optional<String> symbol = restMarketDataClient.normalizeLinearSymbol(rawSymbol);
            if (symbol.isEmpty()) {
                continue;
            }

            try {
                List<KlineCandle> candles = restMarketDataClient.getRecentKlines(symbol.get(), properties.getTimeframe(), 3);
                if (candles.size() < 3) {
                    continue;
                }

                KlineCandle previous = candles.get(candles.size() - 3);
                KlineCandle latestClosed = candles.get(candles.size() - 2);
                evaluator.buildClosedCandleObservation(
                                symbol.get(),
                                properties.getTimeframe(),
                                properties.getMinChangePercent(),
                                previous,
                                latestClosed
                        )
                        .filter(signal -> shouldRecord(symbol.get(), signal.signalTime()))
                        .ifPresent(signal -> {
                            Instant detectedAt = Instant.now();
                            writer.write(BybitLatencyExperimentTransport.REST, signal, detectedAt, Instant.now());
                        });
            } catch (Exception ex) {
                log.warn("Bybit latency experiment REST poll failed for {} {}: {}",
                        rawSymbol, properties.getTimeframe(), ex.getMessage());
            }
        }
    }

    private boolean shouldRecord(String symbol, Instant signalTime) {
        Instant localStartedAt = startedAt;
        if (signalTime == null || localStartedAt == null || !signalTime.isAfter(localStartedAt)) {
            return false;
        }

        Instant previous = lastSignalTimes.putIfAbsent(symbol, signalTime);
        if (previous == null) {
            return true;
        }
        if (signalTime.isAfter(previous)) {
            lastSignalTimes.put(symbol, signalTime);
            return true;
        }
        return false;
    }
}
