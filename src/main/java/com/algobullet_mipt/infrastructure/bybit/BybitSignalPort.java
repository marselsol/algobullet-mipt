package com.algobullet_mipt.infrastructure.bybit;

import com.algobullet_mipt.domain.market.model.KlineCandle;
import com.algobullet_mipt.domain.market.port.MarketDataPort;
import com.algobullet_mipt.domain.signal.port.SignalPort;
import com.algobullet_mipt.model.EmaSettings;
import com.algobullet_mipt.model.PumpSettings;
import com.algobullet_mipt.model.Signal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@ConditionalOnProperty(
        prefix = "app.features",
        name = "use-real-market-data",
        havingValue = "true"
)
@RequiredArgsConstructor
@Slf4j
public class BybitSignalPort implements SignalPort {

    private final MarketDataPort marketDataPort;

    @Override
    public List<Signal> buildFeed(PumpSettings pump, EmaSettings ema) {
        List<Signal> signals = new ArrayList<>();
        Instant now = Instant.now();

        if (pump.isEnabled()) {
            signals.addAll(buildPumpSignals(pump, now));
        }

        if (ema.isEnabled()) {
            for (EmaSettings.EmaWatch watch : ema.getWatchlist()) {
                try {
                    Optional<String> normalizedSymbol = marketDataPort.normalizeLinearSymbol(watch.getSymbol());
                    if (normalizedSymbol.isEmpty()) {
                        log.info("Skipping EMA watch with invalid LINEAR symbol: {}", watch.getSymbol());
                        continue;
                    }

                    int barsToLoad = Math.max(watch.getSlow() + 5, 60);
                    List<KlineCandle> candles = marketDataPort.getRecentKlines(
                            normalizedSymbol.get(),
                            watch.getTimeframe(),
                            barsToLoad
                    );
                    Signal emaSignal = buildEmaSignal(normalizedSymbol.get(), watch, candles);
                    if (emaSignal != null) {
                        signals.add(emaSignal);
                    }
                } catch (Exception ex) {
                    log.warn("EMA signal build failed for {} {}: {}", watch.getSymbol(), watch.getTimeframe(), ex.getMessage());
                }
            }
        }

        signals.sort(Comparator.comparing(Signal::time).reversed());
        return signals;
    }

    private List<Signal> buildPumpSignals(PumpSettings pump, Instant now) {
        try {
            List<String> candidates = resolvePumpCandidates(pump);
            List<Signal> result = new ArrayList<>();

            for (String rawSymbol : candidates) {
                try {
                    Optional<String> normalized = marketDataPort.normalizeLinearSymbol(rawSymbol);
                    if (normalized.isEmpty()) {
                        continue;
                    }

                    List<KlineCandle> candles = marketDataPort.getRecentKlines(normalized.get(), pump.getTimeframe(), 3);
                    Signal pumpSignal = buildPumpSignalFromCandles(normalized.get(), pump, candles, now);
                    if (pumpSignal != null) {
                        result.add(pumpSignal);
                    }
                } catch (Exception ex) {
                    log.debug("Pump screener failed for {} {}: {}", rawSymbol, pump.getTimeframe(), ex.getMessage());
                }
            }

            return result;
        } catch (Exception ex) {
            log.warn("Pump screener failed: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<String> resolvePumpCandidates(PumpSettings pump) {
        Set<String> candidates = new LinkedHashSet<>();
        candidates.addAll(pump.getWatchlist());

        // Если watchlist пустой, используем ликвидные символы как fallback.
        return candidates.stream().limit(30).toList();
    }

    private Signal buildPumpSignalFromCandles(String symbol, PumpSettings pump, List<KlineCandle> candles, Instant fallbackTime) {
        if (candles == null || candles.size() < 2) {
            return null;
        }

        KlineCandle previous = candles.get(candles.size() - 2);
        KlineCandle latest = candles.get(candles.size() - 1);
        if (previous.close() == null || latest.close() == null || previous.close().signum() <= 0) {
            return null;
        }

        var changePercent = latest.close()
                .subtract(previous.close())
                .multiply(java.math.BigDecimal.valueOf(100))
                .divide(previous.close(), 4, java.math.RoundingMode.HALF_UP);

        if (changePercent.doubleValue() < pump.getMinChangePercent()) {
            return null;
        }

        int strength = Math.max(1, Math.min(5, (int) Math.round(changePercent.doubleValue() / Math.max(1.0, pump.getMinChangePercent()))));
        Instant signalTime = latest.openTime() != null ? latest.openTime() : fallbackTime;

        return new Signal(
                signalTime,
                symbol,
                "PUMP",
                "Рост %.2f%% за %s (close %.4f -> %.4f)".formatted(
                        changePercent.doubleValue(),
                        pump.getTimeframe(),
                        previous.close().doubleValue(),
                        latest.close().doubleValue()
                ),
                strength
        );
    }

    private Signal buildEmaSignal(String symbol, EmaSettings.EmaWatch watch, List<KlineCandle> candles) {
        if (candles == null || candles.size() < watch.getSlow() + 2) {
            return null;
        }

        BarSeries series = new BaseBarSeriesBuilder().withName(symbol).build();
        Duration barDuration = toBarDuration(watch.getTimeframe());

        for (KlineCandle candle : candles) {
            series.barBuilder()
                    .timePeriod(barDuration)
                    .endTime(candle.openTime().plus(barDuration))
                    .openPrice(candle.open())
                    .highPrice(candle.high())
                    .lowPrice(candle.low())
                    .closePrice(candle.close())
                    .volume(candle.volume())
                    .amount(candle.turnover())
                    .add();
        }

        ClosePriceIndicator close = new ClosePriceIndicator(series);
        EMAIndicator fastEma = new EMAIndicator(close, watch.getFast());
        EMAIndicator slowEma = new EMAIndicator(close, watch.getSlow());

        int last = series.getEndIndex();
        int prev = last - 1;
        if (prev < 0) {
            return null;
        }

        Num fastPrev = fastEma.getValue(prev);
        Num slowPrev = slowEma.getValue(prev);
        Num fastNow = fastEma.getValue(last);
        Num slowNow = slowEma.getValue(last);

        boolean crossedUp = fastPrev.isLessThanOrEqual(slowPrev) && fastNow.isGreaterThan(slowNow);
        boolean crossedDown = fastPrev.isGreaterThanOrEqual(slowPrev) && fastNow.isLessThan(slowNow);

        if (crossedUp) {
            Instant signalTime = candles.get(candles.size() - 1).openTime();
            return new Signal(
                    signalTime,
                    symbol,
                    "EMA_BUY",
                    "EMA%s/%s bullish crossover on %s".formatted(watch.getFast(), watch.getSlow(), watch.getTimeframe()),
                    5
            );
        }

        if (crossedDown) {
            Instant signalTime = candles.get(candles.size() - 1).openTime();
            return new Signal(
                    signalTime,
                    symbol,
                    "EMA_SELL",
                    "EMA%s/%s bearish crossover on %s".formatted(watch.getFast(), watch.getSlow(), watch.getTimeframe()),
                    5
            );
        }

        return null;
    }

    private Duration toBarDuration(String timeframe) {
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
            default -> throw new IllegalArgumentException("Unsupported timeframe: " + timeframe);
        };
    }
}
