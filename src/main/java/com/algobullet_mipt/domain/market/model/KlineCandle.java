package com.algobullet_mipt.domain.market.model;

import java.math.BigDecimal;
import java.time.Instant;

public record KlineCandle(
        Instant openTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        BigDecimal turnover
) {
}
