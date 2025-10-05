package com.algobullet_mipt.domain;

import java.time.Instant;

public record Signal(Instant time, String symbol, String type, String text, int strength) {
}
