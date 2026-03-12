package com.algobullet_mipt.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class PumpSettings {
    private static final Set<String> SUPPORTED_TIMEFRAMES = Set.of(
            "1m", "3m", "5m", "15m", "30m", "1h", "2h", "4h", "6h", "12h", "1d", "1w"
    );

    private boolean enabled = true;
    private double minChangePercent = 0.8;
    private String timeframe = "1m";
    private final Set<String> watchlist = new LinkedHashSet<>();

    public PumpSettings() {
        Collections.addAll(
                watchlist,
                "BTCUSDT",
                "ETHUSDT",
                "SOLUSDT",
                "XRPUSDT",
                "DOGEUSDT",
                "ADAUSDT",
                "BNBUSDT",
                "LINKUSDT",
                "AVAXUSDT",
                "TRXUSDT"
        );
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getMinChangePercent() {
        return minChangePercent;
    }

    public void setMinChangePercent(double minChangePercent) {
        this.minChangePercent = minChangePercent;
    }

    public String getTimeframe() {
        return timeframe;
    }

    public void setTimeframe(String timeframe) {
        if (isSupportedTimeframe(timeframe)) {
            this.timeframe = timeframe;
        }
    }

    public List<String> getWatchlist() {
        return Collections.unmodifiableList(new ArrayList<>(watchlist));
    }

    public boolean addToWatchlist(String symbol) {
        String normalized = normalizeSymbol(symbol);
        if (normalized == null) {
            return false;
        }
        return watchlist.add(normalized);
    }

    public boolean removeFromWatchlist(String symbol) {
        String normalized = normalizeSymbol(symbol);
        if (normalized == null) {
            return false;
        }
        return watchlist.remove(normalized);
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null) {
            return null;
        }
        String candidate = symbol.trim().toUpperCase(Locale.ROOT);
        if (candidate.isEmpty()) {
            return null;
        }
        if (!candidate.matches("[A-Z0-9]{2,20}")) {
            return null;
        }
        return candidate;
    }

    private boolean isSupportedTimeframe(String timeframe) {
        return timeframe != null && SUPPORTED_TIMEFRAMES.contains(timeframe);
    }
}
