package com.algobullet_mipt.infrastructure.bybit;

import com.algobullet_mipt.domain.market.model.KlineCandle;
import com.bybit.api.client.domain.GenericResponse;
import com.bybit.api.client.domain.market.response.instrumentInfo.InstrumentEntry;
import com.bybit.api.client.domain.market.response.instrumentInfo.InstrumentInfoResult;
import com.bybit.api.client.domain.market.response.kline.MarketKlineEntry;
import com.bybit.api.client.domain.market.response.kline.MarketKlineResult;
import com.bybit.api.client.restApi.BybitApiMarketRestClient;
import org.junit.jupiter.api.Test;

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
}
