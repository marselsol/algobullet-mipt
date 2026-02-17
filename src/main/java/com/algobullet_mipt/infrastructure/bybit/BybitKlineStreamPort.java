package com.algobullet_mipt.infrastructure.bybit;

import com.algobullet_mipt.domain.market.port.KlineStreamPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.features",
        name = "use-real-market-data",
        havingValue = "true"
)
public class BybitKlineStreamPort implements KlineStreamPort {

    @Override
    public void subscribe(String symbol, String timeframe) {
        // TODO: replace with real Bybit websocket subscription.
    }

    @Override
    public void unsubscribe(String symbol, String timeframe) {
        // TODO: replace with real Bybit websocket unsubscription.
    }
}
