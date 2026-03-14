package com.algobullet_mipt.experiment.bybitlatency;

import com.bybit.api.client.domain.GenericResponse;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

final class BybitExperimentResponseMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private BybitExperimentResponseMapper() {
    }

    static <T> GenericResponse<T> parse(Object rawResponse, Class<T> payloadClass) {
        return OBJECT_MAPPER.convertValue(
                rawResponse,
                OBJECT_MAPPER.getTypeFactory().constructParametricType(GenericResponse.class, payloadClass)
        );
    }
}
