package com.algobullet_mipt.experiment.bybitlatency;

import com.algobullet_mipt.domain.market.model.KlineCandle;
import com.bybit.api.client.domain.CategoryType;
import com.bybit.api.client.domain.GenericResponse;
import com.bybit.api.client.domain.market.InstrumentStatus;
import com.bybit.api.client.domain.market.MarketInterval;
import com.bybit.api.client.domain.market.request.MarketDataRequest;
import com.bybit.api.client.domain.market.response.instrumentInfo.InstrumentEntry;
import com.bybit.api.client.domain.market.response.instrumentInfo.InstrumentInfoResult;
import com.bybit.api.client.domain.market.response.kline.MarketKlineEntry;
import com.bybit.api.client.domain.market.response.kline.MarketKlineResult;
import com.bybit.api.client.restApi.BybitApiMarketRestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(prefix = "app.features", name = "use-real-market-data", havingValue = "true")
public class BybitExperimentRestMarketDataClient {

    private final BybitApiMarketRestClient marketRestClient;

    public BybitExperimentRestMarketDataClient(BybitApiMarketRestClient marketRestClient) {
        this.marketRestClient = marketRestClient;
    }

    public List<KlineCandle> getRecentKlines(String symbol, String timeframe, int limit) {
        MarketDataRequest request = MarketDataRequest.builder()
                .category(CategoryType.LINEAR)
                .symbol(symbol)
                .marketInterval(mapInterval(timeframe))
                .limit(limit)
                .build();

        GenericResponse<MarketKlineResult> response = BybitExperimentResponseMapper.parse(
                marketRestClient.getMarketLinesData(request),
                MarketKlineResult.class
        );

        if (response == null || response.getRetCode() != 0) {
            throw new IllegalStateException("Bybit API error: " + (response == null ? "null" : response.getRetCode() + " " + response.getRetMsg()));
        }

        List<MarketKlineEntry> entries = response.getResult() != null && response.getResult().getMarketKlineEntries() != null
                ? response.getResult().getMarketKlineEntries()
                : List.of();

        return entries.stream()
                .sorted(Comparator.comparingLong(MarketKlineEntry::getStartTime))
                .map(this::toKlineCandle)
                .toList();
    }

    public Optional<String> normalizeLinearSymbol(String symbol) {
        if (symbol == null) {
            return Optional.empty();
        }

        String candidate = symbol
                .trim()
                .toUpperCase(Locale.ROOT)
                .replace("/", "")
                .replace("-", "")
                .replace("_", "");

        if (candidate.isBlank() || !candidate.matches("[A-Z0-9]{2,20}")) {
            return Optional.empty();
        }

        Set<String> tradingSymbols = getTradingLinearSymbols();
        if (tradingSymbols.contains(candidate)) {
            return Optional.of(candidate);
        }

        String withUsdt = candidate.endsWith("USDT") ? candidate : candidate + "USDT";
        return tradingSymbols.contains(withUsdt) ? Optional.of(withUsdt) : Optional.empty();
    }

    private Set<String> getTradingLinearSymbols() {
        MarketDataRequest request = MarketDataRequest.builder()
                .category(CategoryType.LINEAR)
                .instrumentStatus(InstrumentStatus.TRADING)
                .limit(1000)
                .build();

        GenericResponse<InstrumentInfoResult> response = BybitExperimentResponseMapper.parse(
                marketRestClient.getInstrumentsInfo(request),
                InstrumentInfoResult.class
        );

        if (response == null || response.getRetCode() != 0) {
            throw new IllegalStateException("Bybit API error: " + (response == null ? "null" : response.getRetCode() + " " + response.getRetMsg()));
        }

        List<InstrumentEntry> entries = response.getResult() != null && response.getResult().getInstrumentEntries() != null
                ? response.getResult().getInstrumentEntries()
                : List.of();

        return entries.stream()
                .map(InstrumentEntry::getSymbol)
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    private KlineCandle toKlineCandle(MarketKlineEntry entry) {
        return new KlineCandle(
                Instant.ofEpochMilli(entry.getStartTime()),
                parseDecimal(entry.getOpenPrice()),
                parseDecimal(entry.getHighPrice()),
                parseDecimal(entry.getLowPrice()),
                parseDecimal(entry.getClosePrice()),
                parseDecimal(entry.getVolume()),
                parseDecimal(entry.getTurnover())
        );
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private MarketInterval mapInterval(String timeframe) {
        return switch (timeframe) {
            case "1m" -> MarketInterval.ONE_MINUTE;
            case "3m" -> MarketInterval.THREE_MINUTES;
            case "5m" -> MarketInterval.FIVE_MINUTES;
            case "15m" -> MarketInterval.FIFTEEN_MINUTES;
            case "30m" -> MarketInterval.HALF_HOURLY;
            case "1h" -> MarketInterval.HOURLY;
            case "2h" -> MarketInterval.TWO_HOURLY;
            case "4h" -> MarketInterval.FOUR_HOURLY;
            case "6h" -> MarketInterval.SIX_HOURLY;
            case "12h" -> MarketInterval.TWELVE_HOURLY;
            case "1d" -> MarketInterval.DAILY;
            case "1w" -> MarketInterval.WEEKLY;
            case "1M" -> MarketInterval.MONTHLY;
            default -> throw new IllegalArgumentException("Unsupported timeframe: " + timeframe);
        };
    }
}
