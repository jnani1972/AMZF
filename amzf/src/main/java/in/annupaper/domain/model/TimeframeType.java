package in.annupaper.domain.model;

/**
 * Timeframe types for Multi-Timeframe (MTF) analysis.
 */
public enum TimeframeType {
    /**
     * Daily Timeframe: 1 candle per trading day, 250 lookback (1 year).
     * Not used for MTF confluence, but for historical analysis and fallback prices.
     */
    DAILY(250, 375, 0.0),

    /**
     * Higher Timeframe: 125-minute candles, 175 lookback.
     * Weight: 0.50 (50% of confluence score)
     */
    HTF(175, 125, 0.50),

    /**
     * Intermediate Timeframe: 25-minute candles, 75 lookback.
     * Weight: 0.30 (30% of confluence score)
     */
    ITF(75, 25, 0.30),

    /**
     * Lower Timeframe: 1-minute candles, 375 lookback.
     * Weight: 0.20 (20% of confluence score)
     */
    LTF(375, 1, 0.20),

    // Explicit minute-based aliases for candle building
    /**
     * 1-minute candles (alias for LTF).
     */
    MINUTE_1(375, 1, 0.20),

    /**
     * 25-minute candles (alias for ITF).
     */
    MINUTE_25(75, 25, 0.30),

    /**
     * 125-minute candles (alias for HTF).
     */
    MINUTE_125(175, 125, 0.50),

    /**
     * 5-minute candles.
     */
    MINUTE_5(375, 5, 0.0),

    /**
     * 15-minute candles.
     */
    MINUTE_15(375, 15, 0.0),

    /**
     * 30-minute candles.
     */
    MINUTE_30(375, 30, 0.0),

    /**
     * 1-hour candles.
     */
    HOUR_1(375, 60, 0.0),

    /**
     * 1-day candles.
     */
    DAY_1(250, 375, 0.0);

    private final int lookback;
    private final int candleMinutes;
    private final double confluenceWeight;

    TimeframeType(int lookback, int candleMinutes, double confluenceWeight) {
        this.lookback = lookback;
        this.candleMinutes = candleMinutes;
        this.confluenceWeight = confluenceWeight;
    }

    public int getLookback() {
        return lookback;
    }

    public int getInterval() {
        return candleMinutes;
    }

    public double getConfluenceWeight() {
        return confluenceWeight;
    }

    /**
     * Get total minutes covered by this timeframe's lookback.
     */
    public int getTotalMinutes() {
        return lookback * candleMinutes;
    }

    /**
     * Get approximate trading days covered.
     */
    public int getTradingDays() {
        // Assuming 375 trading minutes per day
        return (int) Math.ceil((double) getTotalMinutes() / 375);
    }
}
