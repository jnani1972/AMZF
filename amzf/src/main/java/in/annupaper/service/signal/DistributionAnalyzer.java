package in.annupaper.service.signal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Distribution Analyzer - Builds Cumulative Distribution Function (CDF) from price movements.
 *
 * Converts raw price movements into a distribution of "zone movements" where each zone
 * represents one maxDrop unit. This allows us to calculate P(fill) based on how many
 * zones need to be crossed to reach entry/exit levels.
 *
 * Example:
 * - If maxDrop = 2% and price needs to move 6% to hit target
 * - That's 3 zones (6% / 2% = 3)
 * - CDF tells us: P(moving at least 3 zones) = X%
 */
public final class DistributionAnalyzer {

    /**
     * Build CDF from list of movements normalized by maxMovement.
     *
     * @param movements List of movements (as decimal, e.g., 0.03 = 3%)
     * @param maxMovement Maximum movement to normalize by (e.g., maxDrop)
     * @return CDF map: zones → probability of moving AT LEAST that many zones
     */
    public static Map<Integer, BigDecimal> buildDistribution(
        List<BigDecimal> movements,
        BigDecimal maxMovement
    ) {
        if (movements == null || movements.isEmpty()) {
            return getDefaultDistribution();
        }

        if (maxMovement.compareTo(BigDecimal.ZERO) == 0) {
            return getDefaultDistribution();
        }

        // Convert movements to zone counts
        Map<Integer, Integer> zoneCounts = new HashMap<>();
        int totalSamples = movements.size();

        for (BigDecimal movement : movements) {
            // Calculate how many zones this movement represents
            int zonesMoved = movement
                .divide(maxMovement, 0, RoundingMode.HALF_UP)
                .intValue();

            // Cap at 50 zones to prevent outliers
            zonesMoved = Math.min(zonesMoved, 50);

            zoneCounts.put(zonesMoved, zoneCounts.getOrDefault(zonesMoved, 0) + 1);
        }

        // Build CDF: P(moving AT LEAST N zones)
        TreeMap<Integer, BigDecimal> cdf = new TreeMap<>();
        int cumulativeCount = 0;

        // Start from highest zones and work down
        for (int zones = 50; zones >= 0; zones--) {
            cumulativeCount += zoneCounts.getOrDefault(zones, 0);

            BigDecimal probability = new BigDecimal(cumulativeCount)
                .divide(new BigDecimal(totalSamples), 4, RoundingMode.HALF_UP);

            cdf.put(zones, probability);
        }

        return cdf;
    }

    /**
     * Get fill probability for a given number of zones.
     *
     * @param cdf Cumulative distribution function
     * @param zones Number of zones to move
     * @return Probability of moving at least that many zones (0.0 to 1.0)
     */
    public static BigDecimal getFillProbability(Map<Integer, BigDecimal> cdf, int zones) {
        if (cdf == null || cdf.isEmpty()) {
            return getDefaultFillProbability(zones);
        }

        // Find exact or closest match
        if (cdf.containsKey(zones)) {
            return cdf.get(zones);
        }

        // Interpolate if exact zone not found
        Integer lowerZone = null;
        Integer upperZone = null;

        for (Integer z : cdf.keySet()) {
            if (z < zones) {
                if (lowerZone == null || z > lowerZone) {
                    lowerZone = z;
                }
            } else {
                if (upperZone == null || z < upperZone) {
                    upperZone = z;
                }
            }
        }

        // Use closest available
        if (lowerZone != null && upperZone != null) {
            // Linear interpolation
            BigDecimal lowerProb = cdf.get(lowerZone);
            BigDecimal upperProb = cdf.get(upperZone);
            BigDecimal ratio = new BigDecimal(zones - lowerZone)
                .divide(new BigDecimal(upperZone - lowerZone), 4, RoundingMode.HALF_UP);
            return lowerProb.add(upperProb.subtract(lowerProb).multiply(ratio));
        } else if (lowerZone != null) {
            return cdf.get(lowerZone);
        } else if (upperZone != null) {
            return cdf.get(upperZone);
        }

        return getDefaultFillProbability(zones);
    }

    /**
     * Calculate fill probability for entry level based on distance from current price.
     *
     * @param currentPrice Current market price
     * @param entryLevel Entry price level
     * @param maxDrop Maximum drop (for normalization)
     * @param cdf Distribution to use
     * @return Fill probability (0.0 to 1.0)
     */
    public static BigDecimal calculateEntryFillProbability(
        BigDecimal currentPrice,
        BigDecimal entryLevel,
        BigDecimal maxDrop,
        Map<Integer, BigDecimal> cdf
    ) {
        if (currentPrice.compareTo(BigDecimal.ZERO) == 0 || maxDrop.compareTo(BigDecimal.ZERO) == 0) {
            return new BigDecimal("0.50");  // Default 50%
        }

        // Calculate distance in zones
        BigDecimal distance = currentPrice.subtract(entryLevel).abs();
        BigDecimal distancePercent = distance.divide(currentPrice, 6, RoundingMode.HALF_UP);
        int zones = distancePercent.divide(maxDrop, 0, RoundingMode.HALF_UP).intValue();

        return getFillProbability(cdf, zones);
    }

    /**
     * Default distribution for when we have no historical data.
     * Uses exponential decay: P(N zones) = e^(-0.2*N)
     */
    private static Map<Integer, BigDecimal> getDefaultDistribution() {
        TreeMap<Integer, BigDecimal> defaultCdf = new TreeMap<>();

        for (int zones = 0; zones <= 50; zones++) {
            // Exponential decay model
            double probability = Math.exp(-0.2 * zones);
            defaultCdf.put(zones, new BigDecimal(probability).setScale(4, RoundingMode.HALF_UP));
        }

        return defaultCdf;
    }

    /**
     * Default fill probability using exponential decay.
     */
    private static BigDecimal getDefaultFillProbability(int zones) {
        double probability = Math.exp(-0.2 * zones);
        return new BigDecimal(probability).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Get distribution summary statistics.
     */
    public static String getDistributionSummary(Map<Integer, BigDecimal> cdf) {
        if (cdf == null || cdf.isEmpty()) {
            return "Empty distribution";
        }

        // Find P(1 zone), P(2 zones), P(5 zones)
        BigDecimal p1 = getFillProbability(cdf, 1);
        BigDecimal p2 = getFillProbability(cdf, 2);
        BigDecimal p5 = getFillProbability(cdf, 5);

        return String.format(
            "CDF: P(1z)=%.2f%%, P(2z)=%.2f%%, P(5z)=%.2f%%",
            p1.multiply(new BigDecimal("100")).doubleValue(),
            p2.multiply(new BigDecimal("100")).doubleValue(),
            p5.multiply(new BigDecimal("100")).doubleValue()
        );
    }
}
