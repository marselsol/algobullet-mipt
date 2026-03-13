package com.algobullet_mipt.websocket;

import com.algobullet_mipt.model.Signal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;

public record SignalMessage(
        String event,
        Instant time,
        String symbol,
        String type,
        String text,
        int strength,
        String source,
        String timeframe
) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    public static SignalMessage from(Signal signal, String source, String timeframe) {
        return new SignalMessage(
                "signal",
                signal.time(),
                signal.symbol(),
                signal.type(),
                signal.text(),
                signal.strength(),
                source,
                timeframe
        );
    }

    public String toJson() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Не удалось сериализовать websocket-сообщение", ex);
        }
    }
}
