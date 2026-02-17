package com.algobullet_mipt.infrastructure.bybit;

import com.algobullet_mipt.domain.market.port.MarketDataPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(
        prefix = "app.features",
        name = "use-real-market-data",
        havingValue = "true"
)
public class BybitMarketDataPort implements MarketDataPort {

    @Override
    public List<String> getTopUsdtSymbols(int limit) {
        // TODO: replace with Bybit market REST adapter.
        List<String> fallback = List.of("BTCUSDT", "ETHUSDT", "SOLUSDT", "XRPUSDT", "ADAUSDT");
        if (limit <= 0) {
            return List.of();
        }
        return fallback.stream().limit(limit).toList();
    }
}
