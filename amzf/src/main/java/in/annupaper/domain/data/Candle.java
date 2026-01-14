package in.annupaper.domain.data;

import in.annupaper.domain.data.TimeframeType;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * OHLCV Candle with computed metrics.
 */
// public record Candle(
//     String symbol,
//     Instant timestamp,
//     BigDecimal open,
//     BigDecimal high,
//     BigDecimal low,
//     BigDecimal close,
//     long volume
// ) {
// public record Candle(
//     String symbol,
//     TimeframeType timeframeType,
//     Instant timestamp,
//     BigDecimal open,
//     BigDecimal high,
//     BigDecimal low,
//     BigDecimal close,
//     long volume
// ) {
public record Candle(
    Long id,
    String symbol,
    TimeframeType timeframeType,
    Instant timestamp,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    long volume,
    Instant createdAt,
    Instant deletedAt,
    int version
) {
    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);
    
    /**
     * Drop percentage from high to low.
     * dropPct = (high - low) / high
     */
    public BigDecimal dropPct() {
        if (high.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return high.subtract(low).divide(high, MC);
    }
    
    /**
     * Rise percentage from low to high.
     * risePct = (high - low) / low
     */
    public BigDecimal risePct() {
        if (low.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return high.subtract(low).divide(low, MC);
    }
    
    /**
     * Range as percentage of close.
     * rangePct = (high - low) / close
     */
    public BigDecimal rangePct() {
        if (close.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return high.subtract(low).divide(close, MC);
    }
    
    /**
     * Check if candle is bullish (close > open).
     */
    public boolean isBullish() {
        return close.compareTo(open) > 0;
    }
    
    /**
     * Check if candle is bearish (close < open).
     */
    public boolean isBearish() {
        return close.compareTo(open) < 0;
    }
    
    /**
     * Get body size (absolute difference between open and close).
     */
    public BigDecimal bodySize() {
        return close.subtract(open).abs();
    }
    
    /**
     * Get body as percentage of range.
     */
    public BigDecimal bodyPct() {
        BigDecimal range = high.subtract(low);
        if (range.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return bodySize().divide(range, MC);
    }
    
    /**
     * Get upper wick size.
     */
    public BigDecimal upperWick() {
        return high.subtract(isBullish() ? close : open);
    }
    
    /**
     * Get lower wick size.
     */
    public BigDecimal lowerWick() {
        return (isBullish() ? open : close).subtract(low);
    }
    
    /**
     * Create candle from raw values.
     */
    // public static Candle of(String symbol, Instant ts, double o, double h, double l, double c, long v) {
    //     return new Candle(
    //         symbol, ts,
    //         BigDecimal.valueOf(o),
    //         BigDecimal.valueOf(h),
    //         BigDecimal.valueOf(l),
    //         BigDecimal.valueOf(c),
    //         v
    //     );
    // }
    public static Candle of(String symbol, TimeframeType tf, Instant ts, double o, double h, double l, double c, long v) {
        return new Candle(
            null, symbol, tf, ts,
            BigDecimal.valueOf(o),
            BigDecimal.valueOf(h),
            BigDecimal.valueOf(l),
            BigDecimal.valueOf(c),
            v,
            Instant.now(),
            null,
            1
        );
    }
}
