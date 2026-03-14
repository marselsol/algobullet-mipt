package com.algobullet_mipt.experiment.bybitlatency;

public enum BybitLatencyExperimentMode {
    REST,
    WS,
    DUAL;

    public boolean usesRest() {
        return this == REST || this == DUAL;
    }

    public boolean usesWs() {
        return this == WS || this == DUAL;
    }
}
