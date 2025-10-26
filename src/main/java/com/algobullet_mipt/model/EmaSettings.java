package com.algobullet_mipt.model;

import java.util.*;

public class EmaSettings {
    // Global toggle and defaults for new entries
    private boolean enabled = true;
    private int fast = 9;
    private int slow = 21;
    private String timeframe = "15m";

    // Per-symbol configurations, preserve insertion order
    private final Map<String, EmaWatch> watchlist = new LinkedHashMap<>();

    public EmaSettings() {
        // Seed with a few popular symbols using defaults
        addToWatchlist("BTCUSDT", fast, slow, timeframe);
        addToWatchlist("ETHUSDT", fast, slow, timeframe);
        addToWatchlist("SOLUSDT", fast, slow, timeframe);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    // Defaults
    public int getFast() {
        return fast;
    }

    public void setFast(int fast) {
        this.fast = fast;
    }

    public int getSlow() {
        return slow;
    }

    public void setSlow(int slow) {
        this.slow = slow;
    }

    public String getTimeframe() {
        return timeframe;
    }

    public void setTimeframe(String timeframe) {
        this.timeframe = timeframe;
    }

    // Watchlist API
    public List<EmaWatch> getWatchlist() {
        return Collections.unmodifiableList(new ArrayList<>(watchlist.values()));
    }

    public boolean addToWatchlist(String symbol, int fast, int slow, String timeframe) {
        String normalized = normalizeSymbol(symbol);
        if (normalized == null) return false;
        if (!isValidParams(fast, slow, timeframe)) return false;
        if (watchlist.containsKey(normalized)) return false; // avoid duplicates for now
        watchlist.put(normalized, new EmaWatch(normalized, fast, slow, timeframe));
        return true;
    }

    public boolean removeFromWatchlist(String symbol) {
        String normalized = normalizeSymbol(symbol);
        if (normalized == null) return false;
        return watchlist.remove(normalized) != null;
    }

    private boolean isValidParams(int fast, int slow, String timeframe) {
        if (fast < 3 || fast > 100) return false;
        if (slow < 5 || slow > 200) return false;
        if (fast >= slow) return false;
        return timeframe != null && timeframe.matches("(1m|3m|5m|15m|1h|4h|1d)");
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null) return null;
        String candidate = symbol.trim().toUpperCase(Locale.ROOT);
        if (candidate.isEmpty()) return null;
        if (!candidate.matches("[A-Z0-9]{2,20}")) return null;
        return candidate;
    }

    // DTO for per-symbol EMA config
    public static class EmaWatch {
        private final String symbol;
        private final int fast;
        private final int slow;
        private final String timeframe;

        public EmaWatch(String symbol, int fast, int slow, String timeframe) {
            this.symbol = symbol;
            this.fast = fast;
            this.slow = slow;
            this.timeframe = timeframe;
        }

        public String getSymbol() {
            return symbol;
        }

        public int getFast() {
            return fast;
        }

        public int getSlow() {
            return slow;
        }

        public String getTimeframe() {
            return timeframe;
        }
    }
}
