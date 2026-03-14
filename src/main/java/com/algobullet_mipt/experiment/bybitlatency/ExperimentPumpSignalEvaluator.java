package com.algobullet_mipt.experiment.bybitlatency;

import com.algobullet_mipt.domain.market.model.KlineCandle;
import com.algobullet_mipt.service.market.TimeframeDurationResolver;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;

@Component
public class ExperimentPumpSignalEvaluator {

    public Optional<BybitLatencySignal> buildClosedCandleObservation(
            String symbol,
            String timeframe,
            double minChangePercent,
            KlineCandle previous,
            KlineCandle latestClosed
    ) {
        if (previous == null || latestClosed == null) {
            return Optional.empty();
        }
        if (previous.close() == null || latestClosed.close() == null || latestClosed.openTime() == null) {
            return Optional.empty();
        }
        if (previous.close().signum() <= 0) {
            return Optional.empty();
        }

        BigDecimal changePercent = latestClosed.close()
                .subtract(previous.close())
                .multiply(BigDecimal.valueOf(100))
                .divide(previous.close(), 4, RoundingMode.HALF_UP);

        Instant signalTime = latestClosed.openTime().plus(TimeframeDurationResolver.resolve(timeframe));
        boolean thresholdHit = changePercent.doubleValue() >= minChangePercent;
        String details = "Изменение цены: %.4f%%; порог: %.4f%%; порог достигнут: %s"
                .formatted(changePercent.doubleValue(), minChangePercent, thresholdHit ? "да" : "нет");

        return Optional.of(new BybitLatencySignal(
                symbol,
                timeframe,
                thresholdHit ? "ПОРОГ_ПАМПА" : "ЗАКРЫТИЕ_СВЕЧИ",
                latestClosed.openTime(),
                signalTime,
                changePercent.doubleValue(),
                details
        ));
    }

    public Optional<BybitLatencySignal> evaluateClosedCandlePump(
            String symbol,
            String timeframe,
            double minChangePercent,
            KlineCandle previous,
            KlineCandle latestClosed
    ) {
        if (previous == null || latestClosed == null) {
            return Optional.empty();
        }
        if (previous.close() == null || latestClosed.close() == null || latestClosed.openTime() == null) {
            return Optional.empty();
        }
        if (previous.close().signum() <= 0) {
            return Optional.empty();
        }

        BigDecimal changePercent = latestClosed.close()
                .subtract(previous.close())
                .multiply(BigDecimal.valueOf(100))
                .divide(previous.close(), 4, RoundingMode.HALF_UP);

        if (changePercent.doubleValue() < minChangePercent) {
            return Optional.empty();
        }

        Instant signalTime = latestClosed.openTime().plus(TimeframeDurationResolver.resolve(timeframe));
        String details = "Изменение цены: %.4f%%; порог: %.4f%%".formatted(changePercent.doubleValue(), minChangePercent);
        return Optional.of(new BybitLatencySignal(
                symbol,
                timeframe,
                "ПАМП",
                latestClosed.openTime(),
                signalTime,
                changePercent.doubleValue(),
                details
        ));
    }
}
