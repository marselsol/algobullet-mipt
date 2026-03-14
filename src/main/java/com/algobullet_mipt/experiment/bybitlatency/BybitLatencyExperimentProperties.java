package com.algobullet_mipt.experiment.bybitlatency;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.experiment.bybit-latency")
public class BybitLatencyExperimentProperties {

    private boolean enabled;
    private BybitLatencyExperimentMode mode = BybitLatencyExperimentMode.DUAL;
    private List<String> symbols = new ArrayList<>(List.of(
            "BTCUSDT",
            "ETHUSDT",
            "XRPUSDT",
            "SOLUSDT",
            "DOGEUSDT"
    ));
    private String timeframe = "1m";
    private double minChangePercent = 0.8;
    private long pollingDelayMs = 500L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public BybitLatencyExperimentMode getMode() {
        return mode;
    }

    public void setMode(BybitLatencyExperimentMode mode) {
        this.mode = mode;
    }

    public List<String> getSymbols() {
        return symbols;
    }

    public void setSymbols(List<String> symbols) {
        this.symbols = symbols == null ? new ArrayList<>() : new ArrayList<>(symbols);
    }

    public String getTimeframe() {
        return timeframe;
    }

    public void setTimeframe(String timeframe) {
        this.timeframe = timeframe;
    }

    public double getMinChangePercent() {
        return minChangePercent;
    }

    public void setMinChangePercent(double minChangePercent) {
        this.minChangePercent = minChangePercent;
    }

    public long getPollingDelayMs() {
        return pollingDelayMs;
    }

    public void setPollingDelayMs(long pollingDelayMs) {
        this.pollingDelayMs = pollingDelayMs;
    }
}
