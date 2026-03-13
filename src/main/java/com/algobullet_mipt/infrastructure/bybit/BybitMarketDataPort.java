package com.algobullet_mipt.infrastructure.bybit;

import com.algobullet_mipt.domain.market.model.KlineCandle;
import com.algobullet_mipt.domain.market.port.MarketDataPort;
import com.bybit.api.client.domain.CategoryType;
import com.bybit.api.client.domain.GenericResponse;
import com.bybit.api.client.domain.market.MarketInterval;
import com.bybit.api.client.domain.market.InstrumentStatus;
import com.bybit.api.client.domain.market.request.MarketDataRequest;
import com.bybit.api.client.domain.market.response.instrumentInfo.InstrumentEntry;
import com.bybit.api.client.domain.market.response.instrumentInfo.InstrumentInfoResult;
import com.bybit.api.client.domain.market.response.kline.MarketKlineEntry;
import com.bybit.api.client.domain.market.response.kline.MarketKlineResult;
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
import java.util.Locale;
import java.util.Optional;
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
    private static final long REST_THROTTLE_MS = 50L;

    private final BybitApiMarketRestClient marketRestClient;
    private final Clock clock;
    private final Duration klinesTtl;
    private final Duration instrumentsTtl;
    private final ConcurrentMap<String, CacheEntry<List<KlineCandle>>> klinesCache = new ConcurrentHashMap<>();
    private volatile CacheEntry<Set<String>> tradingLinearSymbolsCache;
    private final Object restThrottleLock = new Object();

    @Autowired
    public BybitMarketDataPort(BybitApiMarketRestClient marketRestClient) {
        this(marketRestClient, Clock.systemUTC(), Duration.ofSeconds(15), Duration.ofMinutes(10));
    }

    BybitMarketDataPort(
            BybitApiMarketRestClient marketRestClient,
            Clock clock,
            Duration klinesTtl
    ) {
        this(marketRestClient, clock, klinesTtl, Duration.ofMinutes(10));
    }

    BybitMarketDataPort(
            BybitApiMarketRestClient marketRestClient,
            Clock clock,
            Duration klinesTtl,
            Duration instrumentsTtl
    ) {
        this.marketRestClient = marketRestClient;
        this.clock = clock;
        this.klinesTtl = klinesTtl;
        this.instrumentsTtl = instrumentsTtl;
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

        sleepBeforeRestRequest();
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

    @Override
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
        CacheEntry<Set<String>> cached = tradingLinearSymbolsCache;
        if (isFresh(cached)) {
            return cached.value();
        }

        MarketDataRequest request = MarketDataRequest.builder()
                .category(CategoryType.LINEAR)
                .instrumentStatus(InstrumentStatus.TRADING)
                .limit(1000)
                .build();

        sleepBeforeRestRequest();
        GenericResponse<InstrumentInfoResult> response = BybitResponseMapper.parse(
                marketRestClient.getInstrumentsInfo(request),
                InstrumentInfoResult.class
        );
        assertSuccess(response);

        List<InstrumentEntry> entries = response.getResult() != null && response.getResult().getInstrumentEntries() != null
                ? response.getResult().getInstrumentEntries()
                : List.of();

        Set<String> symbols = entries.stream()
                .map(InstrumentEntry::getSymbol)
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        tradingLinearSymbolsCache = new CacheEntry<>(symbols, clock.instant().plus(instrumentsTtl));
        return symbols;
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

    private void sleepBeforeRestRequest() {
        synchronized (restThrottleLock) {
            try {
                Thread.sleep(REST_THROTTLE_MS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Ожидание перед REST-запросом к Bybit было прервано", ex);
            }
        }
    }

    private record CacheEntry<T>(T value, Instant expiresAt) {
    }
}
