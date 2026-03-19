package com.algobullet_mipt.service;

import com.algobullet_mipt.entity.AppFeatureFlagEntry;
import com.algobullet_mipt.repository.AppFeatureFlagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppFeatureFlagService {

    public static final String BYBIT_LATENCY_EXPERIMENT = "BYBIT_LATENCY_EXPERIMENT";

    private final AppFeatureFlagRepository repository;

    public AppFeatureFlagService(AppFeatureFlagRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public boolean isBybitLatencyExperimentEnabled() {
        return repository.findById(BYBIT_LATENCY_EXPERIMENT)
                .map(AppFeatureFlagEntry::isEnabled)
                .orElse(true);
    }

    @Transactional
    public void setBybitLatencyExperimentEnabled(boolean enabled) {
        AppFeatureFlagEntry entry = repository.findById(BYBIT_LATENCY_EXPERIMENT)
                .orElseGet(() -> {
                    AppFeatureFlagEntry created = new AppFeatureFlagEntry();
                    created.setFeatureKey(BYBIT_LATENCY_EXPERIMENT);
                    return created;
                });
        entry.setEnabled(enabled);
        repository.save(entry);
    }
}
