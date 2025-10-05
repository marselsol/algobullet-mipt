package com.algobullet_mipt.domain;


public class EmaSettings {
    private boolean enabled = true;
    private int fast = 9;
    private int slow = 21;
    private String timeframe = "15m";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getFast() { return fast; }
    public void setFast(int fast) { this.fast = fast; }
    public int getSlow() { return slow; }
    public void setSlow(int slow) { this.slow = slow; }
    public String getTimeframe() { return timeframe; }
    public void setTimeframe(String timeframe) { this.timeframe = timeframe; }
}
