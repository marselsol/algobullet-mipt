package com.algobullet_mipt.experiment.bybitlatency;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class BybitLatencyMeasurementWriter {

    private final BybitLatencyMeasurementRepository repository;
    private final BybitLatencyExperimentRunIdProvider runIdProvider;

    public BybitLatencyMeasurementWriter(
            BybitLatencyMeasurementRepository repository,
            BybitLatencyExperimentRunIdProvider runIdProvider
    ) {
        this.repository = repository;
        this.runIdProvider = runIdProvider;
    }

    public void write(
            BybitLatencyExperimentTransport transport,
            BybitLatencySignal signal,
            Instant detectedAt,
            Instant emittedAt
    ) {
        if (transport == null || signal == null || signal.signalTime() == null || detectedAt == null || emittedAt == null) {
            return;
        }

        String runId = runIdProvider.getRunId();
        if (repository.existsByRunIdAndTransportAndSymbolAndTimeframeAndSignalTime(
                runId,
                transport.name(),
                signal.symbol(),
                signal.timeframe(),
                signal.signalTime()
        )) {
            return;
        }

        BybitLatencyMeasurementEntry entry = new BybitLatencyMeasurementEntry();
        entry.setRunId(runId);
        entry.setTransport(transport.name());
        entry.setSignalType(signal.signalType());
        entry.setSymbol(signal.symbol());
        entry.setTimeframe(signal.timeframe());
        entry.setCandleOpenTime(signal.candleOpenTime());
        entry.setSignalTime(signal.signalTime());
        entry.setDetectedAt(detectedAt);
        entry.setEmittedAt(emittedAt);
        entry.setDetectionLatencyMs(Duration.between(signal.signalTime(), detectedAt).toMillis());
        entry.setEmissionLatencyMs(Duration.between(signal.signalTime(), emittedAt).toMillis());
        entry.setDetails(trimToLength(signal.details(), 512));

        try {
            repository.save(entry);
        } catch (DataIntegrityViolationException ignored) {
            // Duplicate detection for the same run/source/signal should not fail the experiment.
        }
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
