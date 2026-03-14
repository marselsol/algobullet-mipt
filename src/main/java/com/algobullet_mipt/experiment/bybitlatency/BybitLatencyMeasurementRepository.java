package com.algobullet_mipt.experiment.bybitlatency;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface BybitLatencyMeasurementRepository extends JpaRepository<BybitLatencyMeasurementEntry, Long> {
    java.util.List<BybitLatencyMeasurementEntry> findTop200ByOrderByCreatedAtDesc();

    java.util.List<BybitLatencyMeasurementEntry> findTop200ByRunIdOrderByCreatedAtDesc(String runId);

    boolean existsByRunIdAndTransportAndSymbolAndTimeframeAndSignalTime(
            String runId,
            String transport,
            String symbol,
            String timeframe,
            Instant signalTime
    );
}
