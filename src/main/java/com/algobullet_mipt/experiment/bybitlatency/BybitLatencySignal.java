package com.algobullet_mipt.experiment.bybitlatency;

import java.time.Instant;

public record BybitLatencySignal(
        String symbol,
        String timeframe,
        String signalType,
        Instant candleOpenTime,
        Instant signalTime,
        double changePercent,
        String details
) {
}
