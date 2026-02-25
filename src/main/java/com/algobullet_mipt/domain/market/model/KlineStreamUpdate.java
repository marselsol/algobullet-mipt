package com.algobullet_mipt.domain.market.model;

import java.time.Instant;

public record KlineStreamUpdate(
        KlineStreamChannel channel,
        KlineCandle candle,
        boolean closed,
        Instant receivedAt
) {
}
