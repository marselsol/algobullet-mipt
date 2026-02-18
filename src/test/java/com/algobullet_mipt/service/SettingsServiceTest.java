package com.algobullet_mipt.service;

import com.algobullet_mipt.domain.market.port.MarketDataPort;
import com.algobullet_mipt.model.EmaSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SettingsServiceTest {

    @Test
    void preloadsEmaWatchlistWithTop15SymbolsAndDefault1mTimeframe() {
        MarketDataPort marketDataPort = mock(MarketDataPort.class);
        List<String> top15 = List.of(
                "BTCUSDT", "ETHUSDT", "SOLUSDT", "XRPUSDT", "ADAUSDT",
                "DOGEUSDT", "BNBUSDT", "LTCUSDT", "LINKUSDT", "AVAXUSDT",
                "DOTUSDT", "TRXUSDT", "MATICUSDT", "ATOMUSDT", "NEARUSDT"
        );
        when(marketDataPort.getTopUsdtSymbols(15)).thenReturn(top15);

        SettingsService settingsService = new SettingsService(marketDataPort);
        EmaSettings ema = settingsService.ema();

        assertThat(ema.getTimeframe()).isEqualTo("1m");
        assertThat(ema.getWatchlist()).hasSize(15);
        assertThat(ema.getWatchlist().stream().allMatch(w -> "1m".equals(w.getTimeframe()))).isTrue();
    }
}
