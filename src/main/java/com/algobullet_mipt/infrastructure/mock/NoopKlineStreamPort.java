package com.algobullet_mipt.infrastructure.mock;

import com.algobullet_mipt.domain.market.port.KlineStreamPort;
import org.springframework.stereotype.Component;

@Component
public class NoopKlineStreamPort implements KlineStreamPort {

    @Override
    public void subscribe(String symbol, String timeframe) {
        // No-op mock implementation for MVP scaffold.
    }

    @Override
    public void unsubscribe(String symbol, String timeframe) {
        // No-op mock implementation for MVP scaffold.
    }
}
