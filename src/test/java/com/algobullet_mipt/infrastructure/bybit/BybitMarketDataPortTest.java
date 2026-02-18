package com.algobullet_mipt.infrastructure.bybit;

import com.algobullet_mipt.domain.market.model.KlineCandle;
import com.bybit.api.client.domain.GenericResponse;
import com.bybit.api.client.domain.market.response.instrumentInfo.InstrumentEntry;
import com.bybit.api.client.domain.market.response.instrumentInfo.InstrumentInfoResult;
import com.bybit.api.client.domain.market.response.kline.MarketKlineEntry;
import com.bybit.api.client.domain.market.response.kline.MarketKlineResult;
import com.bybit.api.client.domain.market.response.tickers.TickerEntry;
import com.bybit.api.client.domain.market.response.tickers.TickersResult;
import com.bybit.api.client.restApi.BybitApiMarketRestClient;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BybitMarketDataPortTest {

    @Test
    void returnsTopUsdtSymbolsSortedByTurnover() {
        BybitApiMarketRestClient client = mock(BybitApiMarketRestClient.class);
        BybitMarketDataPort port = new BybitMarketDataPort(client);

        TickerEntry btc = mock(TickerEntry.class);
        when(btc.getSymbol()).thenReturn("BTCUSDT");
        when(btc.getTurnover24h()).thenReturn("1000");

        TickerEntry eth = mock(TickerEntry.class);
        when(eth.getSymbol()).thenReturn("ETHUSDT");
        when(eth.getTurnover24h()).thenReturn("800");

        TickerEntry usdc = mock(TickerEntry.class);
        when(usdc.getSymbol()).thenReturn("USDCUSDT");
        when(usdc.getTurnover24h()).thenReturn("1200");

        TickersResult tickersResult = new TickersResult();
        tickersResult.setTickerEntries(List.of(eth, usdc, btc));

        GenericResponse<TickersResult> response = new GenericResponse<>();
        response.setRetCode(0);
        response.setResult(tickersResult);

        when(client.getMarketTickers(any())).thenReturn(response);

        List<String> symbols = port.getTopUsdtSymbols(2);

        assertThat(symbols).containsExactly("BTCUSDT", "ETHUSDT");
    }

    @Test
    void mapsAndSortsKlinesByOpenTimeAscending() {
        BybitApiMarketRestClient client = mock(BybitApiMarketRestClient.class);
        BybitMarketDataPort port = new BybitMarketDataPort(client);

        MarketKlineEntry newer = mock(MarketKlineEntry.class);
        when(newer.getStartTime()).thenReturn(2000L);
        when(newer.getOpenPrice()).thenReturn("11");
        when(newer.getHighPrice()).thenReturn("12");
        when(newer.getLowPrice()).thenReturn("10");
        when(newer.getClosePrice()).thenReturn("11.5");
        when(newer.getVolume()).thenReturn("100");
        when(newer.getTurnover()).thenReturn("1110");

        MarketKlineEntry older = mock(MarketKlineEntry.class);
        when(older.getStartTime()).thenReturn(1000L);
        when(older.getOpenPrice()).thenReturn("9");
        when(older.getHighPrice()).thenReturn("10");
        when(older.getLowPrice()).thenReturn("8");
        when(older.getClosePrice()).thenReturn("9.5");
        when(older.getVolume()).thenReturn("90");
        when(older.getTurnover()).thenReturn("855");

        MarketKlineResult result = mock(MarketKlineResult.class);
        when(result.getMarketKlineEntries()).thenReturn(List.of(newer, older));

        GenericResponse<MarketKlineResult> response = new GenericResponse<>();
        response.setRetCode(0);
        response.setResult(result);

        when(client.getMarketLinesData(any())).thenReturn(response);

        List<KlineCandle> candles = port.getRecentKlines("BTCUSDT", "1m", 2);

        assertThat(candles).hasSize(2);
        assertThat(candles.get(0).openTime().toEpochMilli()).isEqualTo(1000L);
        assertThat(candles.get(1).openTime().toEpochMilli()).isEqualTo(2000L);
        assertThat(candles.get(0).close().toPlainString()).isEqualTo("9.5");
    }

    @Test
    void cachesTopSymbolsWithinTtl() {
        BybitApiMarketRestClient client = mock(BybitApiMarketRestClient.class);
        MutableClock clock = new MutableClock(Instant.parse("2026-02-17T12:00:00Z"));
        BybitMarketDataPort port = new BybitMarketDataPort(client, clock, Duration.ofSeconds(30), Duration.ofSeconds(15));

        TickerEntry btc = mock(TickerEntry.class);
        when(btc.getSymbol()).thenReturn("BTCUSDT");
        when(btc.getTurnover24h()).thenReturn("1000");

        TickersResult tickersResult = new TickersResult();
        tickersResult.setTickerEntries(List.of(btc));

        GenericResponse<TickersResult> response = new GenericResponse<>();
        response.setRetCode(0);
        response.setResult(tickersResult);

        when(client.getMarketTickers(any())).thenReturn(response);

        List<String> first = port.getTopUsdtSymbols(1);
        List<String> second = port.getTopUsdtSymbols(1);

        assertThat(first).containsExactly("BTCUSDT");
        assertThat(second).containsExactly("BTCUSDT");
        verify(client, times(1)).getMarketTickers(any());
    }

    @Test
    void refreshesTopSymbolsAfterTtl() {
        BybitApiMarketRestClient client = mock(BybitApiMarketRestClient.class);
        MutableClock clock = new MutableClock(Instant.parse("2026-02-17T12:00:00Z"));
        BybitMarketDataPort port = new BybitMarketDataPort(client, clock, Duration.ofSeconds(10), Duration.ofSeconds(15));

        TickerEntry btc = mock(TickerEntry.class);
        when(btc.getSymbol()).thenReturn("BTCUSDT");
        when(btc.getTurnover24h()).thenReturn("1000");

        TickerEntry eth = mock(TickerEntry.class);
        when(eth.getSymbol()).thenReturn("ETHUSDT");
        when(eth.getTurnover24h()).thenReturn("900");

        TickersResult firstResult = new TickersResult();
        firstResult.setTickerEntries(List.of(btc));
        GenericResponse<TickersResult> firstResponse = new GenericResponse<>();
        firstResponse.setRetCode(0);
        firstResponse.setResult(firstResult);

        TickersResult secondResult = new TickersResult();
        secondResult.setTickerEntries(List.of(eth));
        GenericResponse<TickersResult> secondResponse = new GenericResponse<>();
        secondResponse.setRetCode(0);
        secondResponse.setResult(secondResult);

        when(client.getMarketTickers(any())).thenReturn(firstResponse, secondResponse);

        List<String> first = port.getTopUsdtSymbols(1);
        clock.advanceSeconds(11);
        List<String> second = port.getTopUsdtSymbols(1);

        assertThat(first).containsExactly("BTCUSDT");
        assertThat(second).containsExactly("ETHUSDT");
        verify(client, times(2)).getMarketTickers(any());
    }

    @Test
    void normalizesAndValidatesLinearSymbolByInstrumentsInfo() {
        BybitApiMarketRestClient client = mock(BybitApiMarketRestClient.class);
        BybitMarketDataPort port = new BybitMarketDataPort(client);

        InstrumentEntry btc = mock(InstrumentEntry.class);
        when(btc.getSymbol()).thenReturn("BTCUSDT");

        InstrumentInfoResult result = mock(InstrumentInfoResult.class);
        when(result.getInstrumentEntries()).thenReturn(List.of(btc));

        GenericResponse<InstrumentInfoResult> response = new GenericResponse<>();
        response.setRetCode(0);
        response.setResult(result);

        when(client.getInstrumentsInfo(any())).thenReturn(response);

        Optional<String> normalized = port.normalizeLinearSymbol(" btc/usdt ");

        assertThat(normalized).contains("BTCUSDT");
        verify(client, times(1)).getInstrumentsInfo(any());
    }

    @Test
    void returnsEmptyForUnknownLinearSymbol() {
        BybitApiMarketRestClient client = mock(BybitApiMarketRestClient.class);
        BybitMarketDataPort port = new BybitMarketDataPort(client);

        InstrumentInfoResult result = mock(InstrumentInfoResult.class);
        when(result.getInstrumentEntries()).thenReturn(List.of());

        GenericResponse<InstrumentInfoResult> response = new GenericResponse<>();
        response.setRetCode(0);
        response.setResult(result);

        when(client.getInstrumentsInfo(any())).thenReturn(response);

        Optional<String> normalized = port.normalizeLinearSymbol("UNKNOWNUSDT");

        assertThat(normalized).isEmpty();
        verify(client, times(1)).getInstrumentsInfo(any());
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }

        void advanceSeconds(long seconds) {
            current = current.plusSeconds(seconds);
        }
    }
}
