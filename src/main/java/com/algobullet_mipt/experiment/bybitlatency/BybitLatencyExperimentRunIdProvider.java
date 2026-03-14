package com.algobullet_mipt.experiment.bybitlatency;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class BybitLatencyExperimentRunIdProvider {

    private final String runId = UUID.randomUUID().toString();

    public String getRunId() {
        return runId;
    }
}
