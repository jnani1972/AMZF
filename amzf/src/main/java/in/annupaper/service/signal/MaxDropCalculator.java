package in.annupaper.service.signal;

import in.annupaper.domain.data.Candle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * MaxDrop Calculator - Calculates maximum single-candle drop and rise from historical candles.
 *
 * MaxDrop = Maximum drop from previous close to current low (as percentage)
 * MaxRise = Maximum rise from previous close to current high (as percentage)
 *
 * Used for:
 * - Building distribution of price movements
 * - Calculating fill probabilities
 * - Zone movement analysis
 */
public final class MaxDropCalculator {

    /**
     * Calculate max drop and rise from candle history.
     *
     * @param candles List of candles (should have at least 2 candles)
     * @return MaxDropResult with maxDrop, maxRise, and all drops/rises
     */
    public static MaxDropResult calculate(List<Candle> candles) {
        if (candles == null || candles.size() < 2) {
            // Return default minimum values if insufficient data
            return new MaxDropResult(
                new BigDecimal("0.01"),  // 1% minimum drop
                new BigDecimal("0.01"),  // 1% minimum rise
                List.of(new BigDecimal("0.01")),
                List.of(new BigDecimal("0.01"))
            );
        }

        List<BigDecimal> drops = new ArrayList<>();
        List<BigDecimal> rises = new ArrayList<>();

        // Calculate drop/rise for each candle relative to previous close
        for (int i = 1; i < candles.size(); i++) {
            Candle prev = candles.get(i - 1);
            Candle curr = candles.get(i);

            BigDecimal prevClose = prev.close();
            BigDecimal currLow = curr.low();
            BigDecimal currHigh = curr.high();

            // Drop = (prev_close - curr_low) / prev_close
            // Only positive drops (when price actually dropped)
            BigDecimal drop = prevClose.subtract(currLow)
                .divide(prevClose, 6, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO);
            drops.add(drop);

            // Rise = (curr_high - prev_close) / prev_close
            // Only positive rises (when price actually rose)
            BigDecimal rise = currHigh.subtract(prevClose)
                .divide(prevClose, 6, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO);
            rises.add(rise);
        }

        // Find maximum drop and rise
        BigDecimal maxDrop = drops.stream()
            .max(BigDecimal::compareTo)
            .orElse(new BigDecimal("0.01"));

        BigDecimal maxRise = rises.stream()
            .max(BigDecimal::compareTo)
            .orElse(new BigDecimal("0.01"));

        // Ensure minimum threshold of 1% to avoid division by zero
        maxDrop = maxDrop.max(new BigDecimal("0.01"));
        maxRise = maxRise.max(new BigDecimal("0.01"));

        return new MaxDropResult(maxDrop, maxRise, drops, rises);
    }

    /**
     * Result of max drop calculation.
     */
    public record MaxDropResult(
        BigDecimal maxDrop,      // Maximum single-candle drop (as decimal, e.g., 0.05 = 5%)
        BigDecimal maxRise,      // Maximum single-candle rise (as decimal)
        List<BigDecimal> allDrops,  // All drops observed
        List<BigDecimal> allRises   // All rises observed
    ) {
        /**
         * Get max drop as percentage (e.g., 5.0 for 5%).
         */
        public BigDecimal maxDropPercent() {
            return maxDrop.multiply(new BigDecimal("100"));
        }

        /**
         * Get max rise as percentage (e.g., 5.0 for 5%).
         */
        public BigDecimal maxRisePercent() {
            return maxRise.multiply(new BigDecimal("100"));
        }

        /**
         * Calculate how many "maxDrop zones" a given price movement represents.
         *
         * @param priceMovement Movement as decimal (e.g., 0.03 = 3%)
         * @return Number of zones (e.g., 2.5 zones)
         */
        public BigDecimal calculateZones(BigDecimal priceMovement) {
            if (maxDrop.compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.ZERO;
            }
            return priceMovement.divide(maxDrop, 2, RoundingMode.HALF_UP);
        }

        /**
         * Get summary statistics.
         */
        public String getSummary() {
            return String.format(
                "MaxDrop: %.2f%%, MaxRise: %.2f%%, Samples: %d",
                maxDropPercent().doubleValue(),
                maxRisePercent().doubleValue(),
                allDrops.size()
            );
        }
    }
}
