package com.algobullet_mipt.infrastructure.bybit;

import com.algobullet_mipt.domain.market.model.KlineCandle;
import com.algobullet_mipt.domain.market.port.MarketDataPort;
import com.bybit.api.client.domain.CategoryType;
import com.bybit.api.client.domain.GenericResponse;
import com.bybit.api.client.domain.market.MarketInterval;
import com.bybit.api.client.domain.market.request.MarketDataRequest;
import com.bybit.api.client.domain.market.response.kline.MarketKlineEntry;
import com.bybit.api.client.domain.market.response.kline.MarketKlineResult;
import com.bybit.api.client.domain.market.response.tickers.TickerEntry;
import com.bybit.api.client.domain.market.response.tickers.TickersResult;
import com.bybit.api.client.restApi.BybitApiMarketRestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Component
@ConditionalOnProperty(
        prefix = "app.features",
        name = "use-real-market-data",
        havingValue = "true"
)
public class BybitMarketDataPort implements MarketDataPort {

    private static final Set<String> STABLE_COINS = Set.of(
            "USDT", "USDC", "BUSD", "TUSD", "DAI", "USDP", "USTC", "USDJ", "PAX", "GUSD",
            "HUSD", "FRAX", "LUSD", "EURS", "USDK", "USDS", "SUSD"
    );

    private final BybitApiMarketRestClient marketRestClient;

    public BybitMarketDataPort(BybitApiMarketRestClient marketRestClient) {
        this.marketRestClient = marketRestClient;
    }

    @Override
    public List<String> getTopUsdtSymbols(int limit) {
        if (limit <= 0) {
            return List.of();
        }

        MarketDataRequest request = MarketDataRequest.builder()
                .category(CategoryType.LINEAR)
                .build();

        GenericResponse<TickersResult> response = BybitResponseMapper.parse(
                marketRestClient.getMarketTickers(request),
                TickersResult.class
        );
        assertSuccess(response);

        List<TickerEntry> tickers = response.getResult() != null && response.getResult().getTickerEntries() != null
                ? response.getResult().getTickerEntries()
                : List.of();

        return tickers.stream()
                .filter(this::isValidUsdtTicker)
                .sorted(this::compareByTurnoverDesc)
                .limit(limit)
                .map(TickerEntry::getSymbol)
                .toList();
    }

    @Override
    public List<KlineCandle> getRecentKlines(String symbol, String timeframe, int limit) {
        if (symbol == null || symbol.isBlank() || limit <= 0) {
            return List.of();
        }

        MarketDataRequest request = MarketDataRequest.builder()
                .category(CategoryType.LINEAR)
                .symbol(symbol.trim().toUpperCase())
                .marketInterval(mapInterval(timeframe))
                .limit(limit)
                .build();

        GenericResponse<MarketKlineResult> response = BybitResponseMapper.parse(
                marketRestClient.getMarketLinesData(request),
                MarketKlineResult.class
        );
        assertSuccess(response);

        List<MarketKlineEntry> entries = response.getResult() != null && response.getResult().getMarketKlineEntries() != null
                ? response.getResult().getMarketKlineEntries()
                : List.of();

        return entries.stream()
                .sorted(Comparator.comparingLong(MarketKlineEntry::getStartTime))
                .map(this::toKlineCandle)
                .toList();
    }

    private boolean isValidUsdtTicker(TickerEntry ticker) {
        if (ticker == null || ticker.getSymbol() == null) {
            return false;
        }
        String symbol = ticker.getSymbol();
        if (!symbol.endsWith("USDT") || symbol.length() <= 4) {
            return false;
        }
        String baseAsset = symbol.substring(0, symbol.length() - 4);
        return !STABLE_COINS.contains(baseAsset) && parseDecimal(ticker.getTurnover24h()).compareTo(BigDecimal.ZERO) > 0;
    }

    private int compareByTurnoverDesc(TickerEntry left, TickerEntry right) {
        return parseDecimal(right.getTurnover24h()).compareTo(parseDecimal(left.getTurnover24h()));
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
        if (timeframe == null || timeframe.isBlank()) {
            throw new IllegalArgumentException("Timeframe must not be blank");
        }
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

    private <T> void assertSuccess(GenericResponse<T> response) {
        if (response == null) {
            throw new IllegalStateException("Bybit response is null");
        }
        if (response.getRetCode() != 0) {
            throw new IllegalStateException("Bybit API error: " + response.getRetCode() + " " + response.getRetMsg());
        }
    }
}
