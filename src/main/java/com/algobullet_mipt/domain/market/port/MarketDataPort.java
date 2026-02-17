package com.algobullet_mipt.domain.market.port;

import java.util.List;

public interface MarketDataPort {
    List<String> getTopUsdtSymbols(int limit);
}
