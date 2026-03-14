package com.algobullet_mipt.experiment.bybitlatency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "bybit_latency_measurement",
        schema = "algo",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_bybit_latency_measurement_signal",
                columnNames = {"run_id", "transport", "symbol", "timeframe", "signal_time"}
        )
)
public class BybitLatencyMeasurementEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false, length = 64)
    private String runId;

    @Column(nullable = false, length = 8)
    private String transport;

    @Column(name = "signal_type", nullable = false, length = 32)
    private String signalType;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(nullable = false, length = 16)
    private String timeframe;

    @Column(name = "candle_open_time", nullable = false)
    private Instant candleOpenTime;

    @Column(name = "signal_time", nullable = false)
    private Instant signalTime;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "emitted_at", nullable = false)
    private Instant emittedAt;

    @Column(name = "detection_latency_ms", nullable = false)
    private long detectionLatencyMs;

    @Column(name = "emission_latency_ms", nullable = false)
    private long emissionLatencyMs;

    @Column(length = 512)
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getTransport() {
        return transport;
    }

    public void setTransport(String transport) {
        this.transport = transport;
    }

    public String getSignalType() {
        return signalType;
    }

    public void setSignalType(String signalType) {
        this.signalType = signalType;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getTimeframe() {
        return timeframe;
    }

    public void setTimeframe(String timeframe) {
        this.timeframe = timeframe;
    }

    public Instant getCandleOpenTime() {
        return candleOpenTime;
    }

    public void setCandleOpenTime(Instant candleOpenTime) {
        this.candleOpenTime = candleOpenTime;
    }

    public Instant getSignalTime() {
        return signalTime;
    }

    public void setSignalTime(Instant signalTime) {
        this.signalTime = signalTime;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(Instant detectedAt) {
        this.detectedAt = detectedAt;
    }

    public Instant getEmittedAt() {
        return emittedAt;
    }

    public void setEmittedAt(Instant emittedAt) {
        this.emittedAt = emittedAt;
    }

    public long getDetectionLatencyMs() {
        return detectionLatencyMs;
    }

    public void setDetectionLatencyMs(long detectionLatencyMs) {
        this.detectionLatencyMs = detectionLatencyMs;
    }

    public long getEmissionLatencyMs() {
        return emissionLatencyMs;
    }

    public void setEmissionLatencyMs(long emissionLatencyMs) {
        this.emissionLatencyMs = emissionLatencyMs;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
