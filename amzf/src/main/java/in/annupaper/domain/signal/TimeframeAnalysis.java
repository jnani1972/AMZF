package in.annupaper.domain.signal;

import in.annupaper.domain.data.TimeframeType;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

/**
 * Analysis result for a single timeframe.
 * Immutable snapshot of TF state at analysis time.
 */
public record TimeframeAnalysis(
    String symbol,
    TimeframeType timeframeType,
    Instant analysisTime,
    
    // Boundaries
    BigDecimal tfLow,       // Lowest low in lookback
    BigDecimal tfHigh,      // Highest high in lookback
    BigDecimal range,       // tfHigh - tfLow
    
    // Historical drop analysis
    BigDecimal maxDrop,     // Maximum drop amount
    BigDecimal maxDropPct,  // Maximum drop percentage (from high)
    
    // Zone calculation
    int numZones,           // Number of zones (range / maxDropPct)
    int currentZone,        // Current price's zone (1-based)
    
    // Buy zone status
    boolean inBuyZone,      // Price in bottom 35% of range
    BigDecimal buyZoneScore, // 0.0 = at floor, 1.0 = at 35%+ ceiling
    
    // Drop distribution (for P(fill) calculation)
    List<BigDecimal> dropDistribution
) {
    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);
    private static final BigDecimal BUY_ZONE_MAX_PCT = new BigDecimal("0.35");
    
    /**
     * Check if a price is in the buy zone (bottom 35% of range).
     */
    public boolean isPriceInBuyZone(BigDecimal price) {
        if (tfLow.compareTo(BigDecimal.ZERO) == 0 || range.compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }
        BigDecimal distFromFloor = price.subtract(tfLow);
        BigDecimal pctFromFloor = distFromFloor.divide(range, MC);
        return pctFromFloor.compareTo(BUY_ZONE_MAX_PCT) <= 0;
    }
    
    /**
     * Get buy zone score for a price.
     * 0.0 = at floor (best), 1.0 = at 35% ceiling or above (worst for entry).
     */
    public BigDecimal getBuyZoneScoreForPrice(BigDecimal price) {
        if (tfLow.compareTo(BigDecimal.ZERO) == 0 || range.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ONE;
        }
        BigDecimal distFromFloor = price.subtract(tfLow);
        BigDecimal pctFromFloor = distFromFloor.divide(range, MC);
        
        // Normalize to 0-1 within buy zone (0-35%)
        BigDecimal score = pctFromFloor.divide(BUY_ZONE_MAX_PCT, MC);
        
        // Cap at 1.0
        return score.compareTo(BigDecimal.ONE) > 0 ? BigDecimal.ONE : score;
    }
    
    /**
     * Get zone number for a price (1-based).
     */
    public int getZoneForPrice(BigDecimal price) {
        if (maxDropPct.compareTo(BigDecimal.ZERO) == 0) return 1;
        
        BigDecimal distFromFloor = price.subtract(tfLow);
        if (distFromFloor.compareTo(BigDecimal.ZERO) <= 0) return 1;
        
        BigDecimal pctFromFloor = distFromFloor.divide(tfLow, MC);
        int zone = pctFromFloor.divide(maxDropPct, MC).intValue() + 1;
        
        return Math.max(1, Math.min(zone, numZones));
    }
    
    /**
     * Get P(fill) for a zone based on drop distribution.
     * Higher zones (closer to ceiling) have lower P(fill).
     */
    public BigDecimal getPFillForZone(int zone) {
        if (dropDistribution == null || dropDistribution.isEmpty()) {
            return BigDecimal.ONE;
        }
        
        // Zone 1 (current) = 1.0
        if (zone <= 1) return BigDecimal.ONE;
        
        // Use drop distribution to estimate probability
        int idx = Math.min(zone - 1, dropDistribution.size() - 1);
        return dropDistribution.get(idx);
    }
    
    /**
     * Get distance from price to floor as percentage.
     */
    public BigDecimal getDistToFloorPct(BigDecimal price) {
        if (tfLow.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return price.subtract(tfLow).divide(price, MC);
    }
    
    /**
     * Get distance from price to ceiling as percentage.
     */
    public BigDecimal getDistToCeilingPct(BigDecimal price) {
        if (price.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return tfHigh.subtract(price).divide(price, MC);
    }
    
    /**
     * Builder for creating TimeframeAnalysis.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String symbol;
        private TimeframeType timeframeType;
        private Instant analysisTime = Instant.now();
        private BigDecimal tfLow = BigDecimal.ZERO;
        private BigDecimal tfHigh = BigDecimal.ZERO;
        private BigDecimal maxDrop = BigDecimal.ZERO;
        private BigDecimal maxDropPct = BigDecimal.ZERO;
        private int numZones = 1;
        private int currentZone = 1;
        private boolean inBuyZone = false;
        private BigDecimal buyZoneScore = BigDecimal.ONE;
        private List<BigDecimal> dropDistribution = List.of();
        
        public Builder symbol(String symbol) { this.symbol = symbol; return this; }
        public Builder timeframeType(TimeframeType type) { this.timeframeType = type; return this; }
        public Builder analysisTime(Instant time) { this.analysisTime = time; return this; }
        public Builder tfLow(BigDecimal low) { this.tfLow = low; return this; }
        public Builder tfHigh(BigDecimal high) { this.tfHigh = high; return this; }
        public Builder maxDrop(BigDecimal drop) { this.maxDrop = drop; return this; }
        public Builder maxDropPct(BigDecimal pct) { this.maxDropPct = pct; return this; }
        public Builder numZones(int n) { this.numZones = n; return this; }
        public Builder currentZone(int z) { this.currentZone = z; return this; }
        public Builder inBuyZone(boolean in) { this.inBuyZone = in; return this; }
        public Builder buyZoneScore(BigDecimal score) { this.buyZoneScore = score; return this; }
        public Builder dropDistribution(List<BigDecimal> dist) { this.dropDistribution = dist; return this; }
        
        public TimeframeAnalysis build() {
            BigDecimal range = tfHigh.subtract(tfLow);
            return new TimeframeAnalysis(
                symbol, timeframeType, analysisTime,
                tfLow, tfHigh, range,
                maxDrop, maxDropPct,
                numZones, currentZone,
                inBuyZone, buyZoneScore,
                dropDistribution
            );
        }
    }
}
