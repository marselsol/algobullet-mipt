package com.algobullet_mipt.infrastructure.bybit;

import com.algobullet_mipt.domain.market.model.KlineCandle;
import com.bybit.api.client.domain.GenericResponse;
import com.bybit.api.client.domain.market.response.kline.MarketKlineEntry;
import com.bybit.api.client.domain.market.response.kline.MarketKlineResult;
import com.bybit.api.client.domain.market.response.tickers.TickerEntry;
import com.bybit.api.client.domain.market.response.tickers.TickersResult;
import com.bybit.api.client.restApi.BybitApiMarketRestClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
}
