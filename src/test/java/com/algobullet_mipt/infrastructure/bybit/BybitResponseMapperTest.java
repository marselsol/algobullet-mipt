package com.algobullet_mipt.infrastructure.bybit;

import com.bybit.api.client.domain.GenericResponse;
import com.bybit.api.client.domain.market.response.instrumentInfo.InstrumentInfoResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BybitResponseMapperTest {

    @Test
    void ignoresUnknownFieldsInBybitPayload() {
        Map<String, Object> raw = Map.of(
                "retCode", 0,
                "retMsg", "OK",
                "result", Map.of(
                        "category", "linear",
                        "list", List.of(
                                Map.of(
                                        "symbol", "BTCUSDT",
                                        "lotSizeFilter", Map.of(
                                                "minOrderQty", "0.001",
                                                "qtyStep", "0.001",
                                                "maxMktOrderQty", "1000"
                                        )
                                )
                        )
                )
        );

        GenericResponse<InstrumentInfoResult> parsed =
                BybitResponseMapper.parse(raw, InstrumentInfoResult.class);

        assertThat(parsed.getRetCode()).isEqualTo(0);
        assertThat(parsed.getResult()).isNotNull();
        assertThat(parsed.getResult().getInstrumentEntries()).isNotEmpty();
        assertThat(parsed.getResult().getInstrumentEntries().get(0).getSymbol()).isEqualTo("BTCUSDT");
    }
}
