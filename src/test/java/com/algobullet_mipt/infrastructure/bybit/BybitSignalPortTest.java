package com.algobullet_mipt.infrastructure.bybit;

import com.algobullet_mipt.domain.market.model.KlineCandle;
import com.algobullet_mipt.domain.market.port.MarketDataPort;
import com.algobullet_mipt.model.EmaSettings;
import com.algobullet_mipt.model.PumpSettings;
import com.algobullet_mipt.model.Signal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BybitSignalPortTest {

    @Test
    void buildsEmaSignalsFromMarketKlines() {
        MarketDataPort marketDataPort = mock(MarketDataPort.class);
        BybitSignalPort signalPort = new BybitSignalPort(marketDataPort);

        List<KlineCandle> candles = crossoverCandles(Instant.parse("2026-02-17T12:00:00Z"));
        when(marketDataPort.normalizeLinearSymbol(anyString())).thenReturn(Optional.of("BTCUSDT"));
        when(marketDataPort.getRecentKlines(anyString(), anyString(), anyInt())).thenReturn(candles);

        EmaSettings ema = new EmaSettings();
        ema.removeFromWatchlist("BTCUSDT");
        ema.removeFromWatchlist("ETHUSDT");
        ema.removeFromWatchlist("SOLUSDT");
        ema.addToWatchlist("BTCUSDT", 3, 5, "1m");

        PumpSettings pump = new PumpSettings();
        pump.setEnabled(false);

        List<Signal> signals = signalPort.buildFeed(pump, ema);

        assertThat(signals).hasSize(1);
        assertThat(signals.get(0).type()).isEqualTo("EMA_BUY");
    }

    @Test
    void returnsPumpPlaceholderWhenPumpEnabled() {
        MarketDataPort marketDataPort = mock(MarketDataPort.class);
        BybitSignalPort signalPort = new BybitSignalPort(marketDataPort);

        EmaSettings ema = new EmaSettings();
        ema.setEnabled(false);
        PumpSettings pump = new PumpSettings();
        pump.setEnabled(true);

        List<Signal> signals = signalPort.buildFeed(pump, ema);

        assertThat(signals).hasSize(1);
        assertThat(signals.get(0).type()).isEqualTo("PUMP");
    }

    @Test
    void skipsInvalidWatchSymbolsBeforeLoadingKlines() {
        MarketDataPort marketDataPort = mock(MarketDataPort.class);
        BybitSignalPort signalPort = new BybitSignalPort(marketDataPort);

        EmaSettings ema = new EmaSettings();
        ema.removeFromWatchlist("BTCUSDT");
        ema.removeFromWatchlist("ETHUSDT");
        ema.removeFromWatchlist("SOLUSDT");
        ema.addToWatchlist("FAKEUSDT", 9, 21, "15m");

        PumpSettings pump = new PumpSettings();
        pump.setEnabled(false);

        when(marketDataPort.normalizeLinearSymbol(anyString())).thenReturn(Optional.empty());

        List<Signal> signals = signalPort.buildFeed(pump, ema);

        assertThat(signals).isEmpty();
        verify(marketDataPort, never()).getRecentKlines(anyString(), anyString(), anyInt());
    }

    private List<KlineCandle> crossoverCandles(Instant start) {
        List<KlineCandle> candles = new ArrayList<>();
        List<BigDecimal> closes = List.of(
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(20)
        );

        for (int i = 0; i < closes.size(); i++) {
            BigDecimal close = closes.get(i);
            BigDecimal open = i == 0 ? close : closes.get(i - 1);
            candles.add(new KlineCandle(
                    start.plusSeconds(60L * i),
                    open,
                    close.add(BigDecimal.valueOf(0.2)),
                    open.subtract(BigDecimal.valueOf(0.2)),
                    close,
                    BigDecimal.valueOf(1000 + i),
                    BigDecimal.valueOf(50000 + i * 100L)
            ));
        }
        return candles;
    }
}
