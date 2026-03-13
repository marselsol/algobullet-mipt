package com.algobullet_mipt.websocket;

import com.algobullet_mipt.model.Signal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public record SignalMessage(
        String event,
        String time,
        String symbol,
        String type,
        String text,
        int strength,
        String source,
        String timeframe
) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final ZoneId SOCKET_TIME_ZONE = ZoneId.of("Europe/Moscow");
    private static final DateTimeFormatter SOCKET_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public static SignalMessage from(Signal signal, String source, String timeframe) {
        return new SignalMessage(
                "signal",
                formatTime(signal.time()),
                signal.symbol(),
                signal.type(),
                signal.text(),
                signal.strength(),
                source,
                timeframe
        );
    }

    private static String formatTime(Instant time) {
        if (time == null) {
            return null;
        }
        return SOCKET_TIME_FORMATTER.format(time.atZone(SOCKET_TIME_ZONE));
    }

    public String toJson() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Не удалось сериализовать websocket-сообщение", ex);
        }
    }
}
