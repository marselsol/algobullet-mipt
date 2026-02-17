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
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@ConditionalOnProperty(
        prefix = "app.features",
        name = "use-real-market-data",
        havingValue = "true"
)
@Slf4j
public class BybitMarketDataPort implements MarketDataPort {

    private static final Set<String> STABLE_COINS = Set.of(
            "USDT", "USDC", "BUSD", "TUSD", "DAI", "USDP", "USTC", "USDJ", "PAX", "GUSD",
            "HUSD", "FRAX", "LUSD", "EURS", "USDK", "USDS", "SUSD"
    );

    private final BybitApiMarketRestClient marketRestClient;
    private final Clock clock;
    private final Duration topSymbolsTtl;
    private final Duration klinesTtl;
    private final ConcurrentMap<Integer, CacheEntry<List<String>>> topSymbolsCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CacheEntry<List<KlineCandle>>> klinesCache = new ConcurrentHashMap<>();

    @Autowired
    public BybitMarketDataPort(BybitApiMarketRestClient marketRestClient) {
        this(marketRestClient, Clock.systemUTC(), Duration.ofSeconds(30), Duration.ofSeconds(15));
    }

    BybitMarketDataPort(
            BybitApiMarketRestClient marketRestClient,
            Clock clock,
            Duration topSymbolsTtl,
            Duration klinesTtl
    ) {
        this.marketRestClient = marketRestClient;
        this.clock = clock;
        this.topSymbolsTtl = topSymbolsTtl;
        this.klinesTtl = klinesTtl;
    }

    @Override
    public List<String> getTopUsdtSymbols(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        CacheEntry<List<String>> cached = topSymbolsCache.get(limit);
        if (isFresh(cached)) {
            log.debug("Top symbols cache hit: limit={}", limit);
            return cached.value();
        }
        log.debug("Top symbols cache miss: limit={}", limit);

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

        List<String> symbols = tickers.stream()
                .filter(this::isValidUsdtTicker)
                .sorted(this::compareByTurnoverDesc)
                .limit(limit)
                .map(TickerEntry::getSymbol)
                .toList();
        topSymbolsCache.put(limit, new CacheEntry<>(symbols, clock.instant().plus(topSymbolsTtl)));
        return symbols;
    }

    @Override
    public List<KlineCandle> getRecentKlines(String symbol, String timeframe, int limit) {
        if (symbol == null || symbol.isBlank() || limit <= 0) {
            return List.of();
        }
        String normalizedSymbol = symbol.trim().toUpperCase();
        String cacheKey = normalizedSymbol + "|" + timeframe + "|" + limit;
        CacheEntry<List<KlineCandle>> cached = klinesCache.get(cacheKey);
        if (isFresh(cached)) {
            log.debug("Klines cache hit: key={}", cacheKey);
            return cached.value();
        }
        log.debug("Klines cache miss: key={}", cacheKey);

        MarketDataRequest request = MarketDataRequest.builder()
                .category(CategoryType.LINEAR)
                .symbol(normalizedSymbol)
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

        List<KlineCandle> candles = entries.stream()
                .sorted(Comparator.comparingLong(MarketKlineEntry::getStartTime))
                .map(this::toKlineCandle)
                .toList();
        klinesCache.put(cacheKey, new CacheEntry<>(candles, clock.instant().plus(klinesTtl)));
        return candles;
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

    private boolean isFresh(CacheEntry<?> entry) {
        return entry != null && clock.instant().isBefore(entry.expiresAt());
    }

    private record CacheEntry<T>(T value, Instant expiresAt) {
    }
}
