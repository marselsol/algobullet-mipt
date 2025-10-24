package com.algobullet_mipt.model;

import java.time.Instant;

public record Signal(Instant time, String symbol, String type, String text, int strength) {
}
