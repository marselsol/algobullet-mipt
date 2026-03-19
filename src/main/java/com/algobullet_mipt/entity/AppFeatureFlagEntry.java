package com.algobullet_mipt.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_feature_flags", schema = "algo")
public class AppFeatureFlagEntry {

    @Id
    @Column(name = "feature_key", nullable = false, length = 64)
    private String featureKey;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    public String getFeatureKey() {
        return featureKey;
    }

    public void setFeatureKey(String featureKey) {
        this.featureKey = featureKey;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
