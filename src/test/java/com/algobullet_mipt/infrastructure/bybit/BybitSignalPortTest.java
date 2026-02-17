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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BybitSignalPortTest {

    @Test
    void buildsEmaSignalsFromMarketKlines() {
        MarketDataPort marketDataPort = mock(MarketDataPort.class);
        BybitSignalPort signalPort = new BybitSignalPort(marketDataPort);

        List<KlineCandle> candles = risingCandles(80, Instant.parse("2026-02-17T12:00:00Z"), BigDecimal.valueOf(100));
        when(marketDataPort.getRecentKlines(anyString(), anyString(), anyInt())).thenReturn(candles);

        EmaSettings ema = new EmaSettings();
        PumpSettings pump = new PumpSettings();
        pump.setEnabled(false);

        List<Signal> signals = signalPort.buildFeed(pump, ema);

        assertThat(signals).isNotEmpty();
        assertThat(signals.stream().allMatch(s -> s.type().startsWith("EMA"))).isTrue();
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

    private List<KlineCandle> risingCandles(int count, Instant start, BigDecimal initial) {
        List<KlineCandle> candles = new ArrayList<>();
        BigDecimal current = initial;
        for (int i = 0; i < count; i++) {
            BigDecimal open = current;
            BigDecimal close = current.add(BigDecimal.valueOf(0.5));
            candles.add(new KlineCandle(
                    start.plusSeconds(60L * i),
                    open,
                    close.add(BigDecimal.valueOf(0.2)),
                    open.subtract(BigDecimal.valueOf(0.2)),
                    close,
                    BigDecimal.valueOf(1000 + i),
                    BigDecimal.valueOf(50000 + i * 100L)
            ));
            current = close;
        }
        return candles;
    }
}
