package com.algobullet_mipt.infrastructure.mock;

import com.algobullet_mipt.domain.market.port.MarketDataPort;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MockMarketDataPort implements MarketDataPort {

    private static final List<String> DEFAULT_SYMBOLS = List.of(
            "BTCUSDT",
            "ETHUSDT",
            "SOLUSDT",
            "XRPUSDT",
            "ADAUSDT"
    );

    @Override
    public List<String> getTopUsdtSymbols(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return DEFAULT_SYMBOLS.stream().limit(limit).toList();
    }
}
