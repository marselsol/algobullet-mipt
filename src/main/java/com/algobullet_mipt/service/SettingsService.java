package com.algobullet_mipt.service;

import com.algobullet_mipt.domain.market.port.MarketDataPort;
import com.algobullet_mipt.model.EmaSettings;
import com.algobullet_mipt.model.PumpSettings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class SettingsService {
    private static final int DEFAULT_EMA_WATCHLIST_SIZE = 15;

    private final PumpSettings pump;
    private final EmaSettings ema;

    public SettingsService(MarketDataPort marketDataPort) {
        this.pump = new PumpSettings();
        this.ema = new EmaSettings();

        try {
            List<String> topSymbols = marketDataPort.getTopUsdtSymbols(DEFAULT_EMA_WATCHLIST_SIZE);
            if (topSymbols != null && !topSymbols.isEmpty()) {
                ema.clearWatchlist();
                for (String symbol : topSymbols) {
                    ema.addToWatchlist(symbol, ema.getFast(), ema.getSlow(), ema.getTimeframe());
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to prefill EMA watchlist from market data: {}", ex.getMessage());
        }
    }

    public PumpSettings pump() {
        return pump;
    }

    public EmaSettings ema() {
        return ema;
    }
}
