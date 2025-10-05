package com.algobullet_mipt.domain;


public class PumpSettings {
    private boolean enabled = true;
    private double minChangePercent = 3.0;
    private String timeframe = "5m";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getMinChangePercent() {
        return minChangePercent;
    }

    public void setMinChangePercent(double minChangePercent) {
        this.minChangePercent = minChangePercent;
    }

    public String getTimeframe() {
        return timeframe;
    }

    public void setTimeframe(String timeframe) {
        this.timeframe = timeframe;
    }
}

