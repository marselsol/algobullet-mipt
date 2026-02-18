package com.algobullet_mipt.infrastructure.bybit;

import com.bybit.api.client.domain.GenericResponse;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

final class BybitResponseMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private BybitResponseMapper() {
    }

    static <T> GenericResponse<T> parse(Object rawResponse, Class<T> payloadClass) {
        return OBJECT_MAPPER.convertValue(
                rawResponse,
                OBJECT_MAPPER.getTypeFactory().constructParametricType(GenericResponse.class, payloadClass)
        );
    }
}
