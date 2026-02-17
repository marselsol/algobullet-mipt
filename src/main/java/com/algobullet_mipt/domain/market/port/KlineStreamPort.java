package com.algobullet_mipt.domain.market.port;

public interface KlineStreamPort {
    void subscribe(String symbol, String timeframe);

    void unsubscribe(String symbol, String timeframe);
}
