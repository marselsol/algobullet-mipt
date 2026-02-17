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
import java.util.List;

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
            signals.add(new Signal(
                    now.minusSeconds(30),
                    "MARKET",
                    "PUMP",
                    "Pump screener will be switched to real market data in the next step",
                    1
            ));
        }

        if (ema.isEnabled()) {
            for (EmaSettings.EmaWatch watch : ema.getWatchlist()) {
                try {
                    int barsToLoad = Math.max(watch.getSlow() + 5, 60);
                    List<KlineCandle> candles = marketDataPort.getRecentKlines(
                            watch.getSymbol(),
                            watch.getTimeframe(),
                            barsToLoad
                    );
                    Signal emaSignal = buildEmaSignal(watch, candles);
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

    private Signal buildEmaSignal(EmaSettings.EmaWatch watch, List<KlineCandle> candles) {
        if (candles == null || candles.size() < watch.getSlow() + 2) {
            return null;
        }

        BarSeries series = new BaseBarSeriesBuilder().withName(watch.getSymbol()).build();
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
        boolean bullish = fastNow.isGreaterThan(slowNow);

        String type;
        String text;
        int strength;

        if (crossedUp) {
            type = "EMA_BUY";
            text = "EMA%s/%s bullish crossover on %s".formatted(watch.getFast(), watch.getSlow(), watch.getTimeframe());
            strength = 5;
        } else if (crossedDown) {
            type = "EMA_SELL";
            text = "EMA%s/%s bearish crossover on %s".formatted(watch.getFast(), watch.getSlow(), watch.getTimeframe());
            strength = 5;
        } else {
            type = "EMA";
            text = bullish
                    ? "EMA%s/%s bullish trend on %s".formatted(watch.getFast(), watch.getSlow(), watch.getTimeframe())
                    : "EMA%s/%s bearish trend on %s".formatted(watch.getFast(), watch.getSlow(), watch.getTimeframe());
            strength = 2;
        }

        Instant signalTime = candles.get(candles.size() - 1).openTime();
        return new Signal(signalTime, watch.getSymbol(), type, text, strength);
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
