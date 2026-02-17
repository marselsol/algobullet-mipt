package com.algobullet_mipt.domain.market.port;

import com.algobullet_mipt.domain.market.model.KlineCandle;

import java.util.List;

public interface MarketDataPort {
    List<String> getTopUsdtSymbols(int limit);

    List<KlineCandle> getRecentKlines(String symbol, String timeframe, int limit);
}
