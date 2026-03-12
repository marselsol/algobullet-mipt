package com.algobullet_mipt.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_pump_settings", schema = "algo")
public class UserPumpSettingsEntry {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "min_change_percent", nullable = false)
    private double minChangePercent = 0.8;

    @Column(name = "timeframe", nullable = false, length = 16)
    private String timeframe = "1m";

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

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
