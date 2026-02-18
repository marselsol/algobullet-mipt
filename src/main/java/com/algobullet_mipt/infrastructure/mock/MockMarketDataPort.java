package com.algobullet_mipt.infrastructure.mock;

import com.algobullet_mipt.domain.market.model.KlineCandle;
import com.algobullet_mipt.domain.market.port.MarketDataPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@ConditionalOnProperty(
        prefix = "app.features",
        name = "use-real-market-data",
        havingValue = "false",
        matchIfMissing = true
)
public class MockMarketDataPort implements MarketDataPort {

    private static final Set<String> DEFAULT_SYMBOL_SET;
    private static final List<String> DEFAULT_SYMBOLS = List.of(
            "BTCUSDT",
            "ETHUSDT",
            "SOLUSDT",
            "XRPUSDT",
            "ADAUSDT",
            "DOGEUSDT",
            "BNBUSDT",
            "LTCUSDT",
            "LINKUSDT",
            "AVAXUSDT",
            "DOTUSDT",
            "TRXUSDT",
            "MATICUSDT",
            "ATOMUSDT",
            "NEARUSDT"
    );

    static {
        DEFAULT_SYMBOL_SET = Set.copyOf(DEFAULT_SYMBOLS);
    }

    @Override
    public List<String> getTopUsdtSymbols(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return DEFAULT_SYMBOLS.stream().limit(limit).toList();
    }

    @Override
    public List<KlineCandle> getRecentKlines(String symbol, String timeframe, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        List<KlineCandle> candles = new ArrayList<>();
        Instant now = Instant.now();
        BigDecimal base = BigDecimal.valueOf(100);

        for (int i = limit; i >= 1; i--) {
            BigDecimal close = base.add(BigDecimal.valueOf(i));
            candles.add(new KlineCandle(
                    now.minusSeconds(60L * i),
                    close.subtract(BigDecimal.ONE),
                    close.add(BigDecimal.ONE),
                    close.subtract(BigDecimal.valueOf(2)),
                    close,
                    BigDecimal.valueOf(1000 + i),
                    BigDecimal.valueOf(100000 + i * 100L)
            ));
        }
        return candles;
    }

    @Override
    public Optional<String> normalizeLinearSymbol(String symbol) {
        Optional<String> normalized = MarketDataPort.super.normalizeLinearSymbol(symbol);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }

        String candidate = normalized.get();
        if (DEFAULT_SYMBOL_SET.contains(candidate)) {
            return Optional.of(candidate);
        }

        String withUsdt = candidate.endsWith("USDT") ? candidate : candidate + "USDT";
        return DEFAULT_SYMBOL_SET.contains(withUsdt) ? Optional.of(withUsdt) : Optional.empty();
    }
}
