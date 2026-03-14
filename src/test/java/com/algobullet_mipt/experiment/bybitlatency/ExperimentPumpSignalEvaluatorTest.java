package com.algobullet_mipt.experiment.bybitlatency;

import com.algobullet_mipt.domain.market.model.KlineCandle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ExperimentPumpSignalEvaluatorTest {

    private final ExperimentPumpSignalEvaluator evaluator = new ExperimentPumpSignalEvaluator();

    @Test
    void buildsPumpSignalForClosedCandleUsingCloseTime() {
        KlineCandle previous = new KlineCandle(
                Instant.parse("2026-03-14T10:00:00Z"),
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                new BigDecimal("100"),
                BigDecimal.ONE,
                BigDecimal.ONE
        );
        KlineCandle latest = new KlineCandle(
                Instant.parse("2026-03-14T10:01:00Z"),
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                new BigDecimal("102"),
                BigDecimal.ONE,
                BigDecimal.ONE
        );

        BybitLatencySignal signal = evaluator.evaluateClosedCandlePump("BTCUSDT", "1m", 1.0, previous, latest).orElseThrow();

        assertThat(signal.signalTime()).isEqualTo(Instant.parse("2026-03-14T10:02:00Z"));
        assertThat(signal.candleOpenTime()).isEqualTo(Instant.parse("2026-03-14T10:01:00Z"));
        assertThat(signal.signalType()).isEqualTo("PUMP");
        assertThat(signal.changePercent()).isEqualTo(2.0d);
    }

    @Test
    void skipsSignalWhenThresholdIsNotReached() {
        KlineCandle previous = new KlineCandle(
                Instant.parse("2026-03-14T10:00:00Z"),
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                new BigDecimal("100"),
                BigDecimal.ONE,
                BigDecimal.ONE
        );
        KlineCandle latest = new KlineCandle(
                Instant.parse("2026-03-14T10:01:00Z"),
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                new BigDecimal("100.5"),
                BigDecimal.ONE,
                BigDecimal.ONE
        );

        assertThat(evaluator.evaluateClosedCandlePump("BTCUSDT", "1m", 1.0, previous, latest)).isEmpty();
    }
}
