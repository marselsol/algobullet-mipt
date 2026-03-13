package com.algobullet_mipt.domain.market.port;

import com.algobullet_mipt.domain.market.model.KlineCandle;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public interface MarketDataPort {
    List<KlineCandle> getRecentKlines(String symbol, String timeframe, int limit);

    default Optional<String> normalizeLinearSymbol(String symbol) {
        if (symbol == null) {
            return Optional.empty();
        }
        String normalized = symbol
                .trim()
                .toUpperCase(Locale.ROOT)
                .replace("/", "")
                .replace("-", "")
                .replace("_", "");
        if (normalized.isBlank() || !normalized.matches("[A-Z0-9]{2,20}")) {
            return Optional.empty();
        }
        return Optional.of(normalized);
    }
}
