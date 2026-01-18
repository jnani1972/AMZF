package in.annupaper.domain.model;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Combined Multi-Timeframe configuration and analysis result.
 * Combines HTF, ITF, and LTF analyses with confluence calculations.
 */
public record MTFConfig(
        String symbol,
        Instant analysisTime,
        BigDecimal currentPrice,

        // Individual timeframe analyses
        TimeframeAnalysis htf,
        TimeframeAnalysis itf,
        TimeframeAnalysis ltf,

        // Confluence
        ConfluenceType confluenceType,
        BigDecimal confluenceScore,
        ConfluenceStrength confluenceStrength,

        // Combined boundaries
        BigDecimal effectiveFloor, // max(htfLow, itfLow, ltfLow)
        BigDecimal effectiveCeiling, // min(htfHigh, itfHigh, ltfHigh)

        // Buy zone status per TF
        boolean htfInBuyZone,
        boolean itfInBuyZone,
        boolean ltfInBuyZone,

        // Derived
        boolean hasTripleConfluence) {
    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);

    /**
     * Create MTFConfig from individual timeframe analyses.
     */
    public static MTFConfig from(TimeframeAnalysis htf, TimeframeAnalysis itf,
            TimeframeAnalysis ltf, BigDecimal currentPrice) {
        // Determine confluence
        int buyZoneCount = 0;
        if (htf.inBuyZone())
            buyZoneCount++;
        if (itf.inBuyZone())
            buyZoneCount++;
        if (ltf.inBuyZone())
            buyZoneCount++;

        ConfluenceType confluenceType = ConfluenceType.fromCount(buyZoneCount);

        // Calculate weighted confluence score
        // Score = weighted average of buy zone scores (lower = better)
        BigDecimal score = htf.buyZoneScore().multiply(BigDecimal.valueOf(htf.timeframeType().getConfluenceWeight()))
                .add(itf.buyZoneScore().multiply(BigDecimal.valueOf(itf.timeframeType().getConfluenceWeight())))
                .add(ltf.buyZoneScore().multiply(BigDecimal.valueOf(ltf.timeframeType().getConfluenceWeight())));

        ConfluenceStrength strength = ConfluenceStrength.fromScore(score.doubleValue());

        // Calculate effective boundaries
        BigDecimal effectiveFloor = htf.tfLow().max(itf.tfLow()).max(ltf.tfLow());
        BigDecimal effectiveCeiling = htf.tfHigh().min(itf.tfHigh()).min(ltf.tfHigh());

        return new MTFConfig(
                htf.symbol(),
                Instant.now(),
                currentPrice,
                htf, itf, ltf,
                confluenceType,
                score,
                strength,
                effectiveFloor,
                effectiveCeiling,
                htf.inBuyZone(),
                itf.inBuyZone(),
                ltf.inBuyZone(),
                buyZoneCount == 3);
    }

    /**
     * Check if price is in tradeable range (between effective floor and ceiling).
     */
    public boolean isPriceInTradeableRange(BigDecimal price) {
        return price.compareTo(effectiveFloor) >= 0 && price.compareTo(effectiveCeiling) <= 0;
    }

    /**
     * Get log loss at effective floor for a given entry price.
     */
    public BigDecimal getLogLossAtFloor(BigDecimal entryPrice) {
        if (entryPrice.compareTo(BigDecimal.ZERO) <= 0)
            return BigDecimal.ZERO;
        return BigDecimal.valueOf(Math.log(effectiveFloor.divide(entryPrice, MC).doubleValue()));
    }

    /**
     * Get potential log gain at effective ceiling for a given entry price.
     */
    public BigDecimal getLogGainAtCeiling(BigDecimal entryPrice) {
        if (entryPrice.compareTo(BigDecimal.ZERO) <= 0)
            return BigDecimal.ZERO;
        return BigDecimal.valueOf(Math.log(effectiveCeiling.divide(entryPrice, MC).doubleValue()));
    }

    /**
     * Calculate P(win) based on distance ratios.
     * P(win) = dist_to_floor / (dist_to_floor + dist_to_ceiling)
     */
    public BigDecimal calculatePWin(BigDecimal price) {
        BigDecimal distToFloor = price.subtract(effectiveFloor);
        BigDecimal distToCeiling = effectiveCeiling.subtract(price);
        BigDecimal totalDist = distToFloor.add(distToCeiling);

        if (totalDist.compareTo(BigDecimal.ZERO) == 0)
            return BigDecimal.ZERO;

        return distToFloor.divide(totalDist, MC);
    }

    /**
     * Get the primary timeframe (HTF) for zone structure.
     */
    public TimeframeAnalysis getPrimaryTimeframe() {
        return htf;
    }

    /**
     * Get confluence multiplier for position sizing.
     */
    public BigDecimal getConfluenceMultiplier() {
        return confluenceStrength.getMultiplier();
    }
}
