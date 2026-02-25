package com.algobullet_mipt.domain.market.model;

import java.util.Locale;

public record KlineStreamChannel(String symbol, String timeframe) {

    public KlineStreamChannel {
        symbol = normalizeSymbol(symbol);
        timeframe = normalizeTimeframe(timeframe);
    }

    private static String normalizeSymbol(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Symbol must not be null");
        }
        String normalized = value.trim()
                .toUpperCase(Locale.ROOT)
                .replace("/", "")
                .replace("-", "")
                .replace("_", "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Symbol must not be blank");
        }
        return normalized;
    }

    private static String normalizeTimeframe(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Timeframe must not be blank");
        }
        return value.trim();
    }
}
