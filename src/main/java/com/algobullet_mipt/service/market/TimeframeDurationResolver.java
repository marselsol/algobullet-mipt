package com.algobullet_mipt.service.market;

import java.time.Duration;

public final class TimeframeDurationResolver {

    private TimeframeDurationResolver() {
    }

    public static Duration resolve(String timeframe) {
        return switch (timeframe) {
            case "1m" -> Duration.ofMinutes(1);
            case "3m" -> Duration.ofMinutes(3);
            case "5m" -> Duration.ofMinutes(5);
            case "15m" -> Duration.ofMinutes(15);
            case "30m" -> Duration.ofMinutes(30);
            case "1h" -> Duration.ofHours(1);
            case "2h" -> Duration.ofHours(2);
            case "4h" -> Duration.ofHours(4);
            case "6h" -> Duration.ofHours(6);
            case "12h" -> Duration.ofHours(12);
            case "1d" -> Duration.ofDays(1);
            case "1w" -> Duration.ofDays(7);
            default -> throw new IllegalArgumentException("Неподдерживаемый timeframe: " + timeframe);
        };
    }
}
