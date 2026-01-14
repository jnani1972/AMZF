package in.annupaper.service.signal;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Kelly Calculator - Calculates Kelly fraction and P(win) for position sizing.
 *
 * Kelly Formula: f* = (p*b - q) / b
 * Where:
 * - p = probability of win
 * - q = probability of loss (1-p)
 * - b = win/loss ratio (profit/loss)
 * - f* = fraction of capital to risk
 *
 * For mean reversion:
 * - Win = reaching ceiling (target)
 * - Loss = hitting floor (stop loss)
 * - P(win) calculated based on zones to ceiling vs zones to floor
 */
public final class KellyCalculator {

    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /**
     * Calculate P(win) based on distance to ceiling and floor in terms of maxDrop zones.
     *
     * P(win) = zonesToCeiling / (zonesToFloor + zonesToCeiling)
     *
     * Intuition: If ceiling is 3 zones away and floor is 1 zone away,
     * price has more "room" to hit floor first, so P(win) is lower.
     *
     * @param price Current entry price
     * @param floor Effective floor (stop loss)
     * @param ceiling Effective ceiling (target)
     * @param maxDrop Maximum drop for normalization
     * @return P(win) probability (0.0 to 1.0)
     */
    public static BigDecimal calculatePWin(
        BigDecimal price,
        BigDecimal floor,
        BigDecimal ceiling,
        BigDecimal maxDrop
    ) {
        if (price.compareTo(ZERO) == 0 || maxDrop.compareTo(ZERO) == 0) {
            return new BigDecimal("0.50");  // Default 50%
        }

        // Zones to floor = (price - floor) / (floor * maxDrop)
        BigDecimal distanceToFloor = price.subtract(floor);
        BigDecimal floorMovementSize = floor.multiply(maxDrop);
        BigDecimal zonesToFloor = distanceToFloor.divide(floorMovementSize, 4, RoundingMode.HALF_UP);

        // Zones to ceiling = (ceiling - price) / (price * maxDrop)
        BigDecimal distanceToCeiling = ceiling.subtract(price);
        BigDecimal ceilingMovementSize = price.multiply(maxDrop);
        BigDecimal zonesToCeiling = distanceToCeiling.divide(ceilingMovementSize, 4, RoundingMode.HALF_UP);

        // P(win) = zonesToCeiling / (zonesToFloor + zonesToCeiling)
        BigDecimal totalZones = zonesToFloor.add(zonesToCeiling);
        if (totalZones.compareTo(ZERO) == 0) {
            return new BigDecimal("0.50");
        }

        BigDecimal pWin = zonesToCeiling.divide(totalZones, 4, RoundingMode.HALF_UP);

        // Clamp between 0.10 and 0.90
        pWin = pWin.max(new BigDecimal("0.10"));
        pWin = pWin.min(new BigDecimal("0.90"));

        return pWin;
    }

    /**
     * Calculate Kelly fraction for position sizing.
     *
     * Kelly = (P_win * win_ratio - P_loss) / win_ratio
     * Where:
     * - win_ratio = (ceiling - price) / (price - floor)
     * - P_loss = 1 - P_win
     *
     * @param price Entry price
     * @param floor Effective floor (stop loss)
     * @param ceiling Effective ceiling (target)
     * @param pWin Probability of win
     * @return Kelly fraction (0.0 to 1.0, but typically much smaller)
     */
    public static BigDecimal calculateKelly(
        BigDecimal price,
        BigDecimal floor,
        BigDecimal ceiling,
        BigDecimal pWin
    ) {
        if (price.compareTo(ZERO) == 0) {
            return ZERO;
        }

        // Validate inputs
        if (price.compareTo(floor) <= 0 || ceiling.compareTo(price) <= 0) {
            return ZERO;  // Invalid price levels
        }

        // Win ratio = (ceiling - price) / (price - floor)
        BigDecimal potentialWin = ceiling.subtract(price);
        BigDecimal potentialLoss = price.subtract(floor);
        BigDecimal winRatio = potentialWin.divide(potentialLoss, 6, RoundingMode.HALF_UP);

        // P_loss = 1 - P_win
        BigDecimal pLoss = ONE.subtract(pWin);

        // Kelly = (P_win * win_ratio - P_loss) / win_ratio
        BigDecimal numerator = pWin.multiply(winRatio).subtract(pLoss);
        BigDecimal kelly = numerator.divide(winRatio, 6, RoundingMode.HALF_UP);

        // Kelly can be negative if expected value is negative (don't trade)
        kelly = kelly.max(ZERO);

        // Cap at 1.0 (100% of capital - though we'll apply fractional Kelly later)
        kelly = kelly.min(ONE);

        return kelly;
    }

    /**
     * Calculate full Kelly result with all intermediate values.
     *
     * @param price Entry price
     * @param floor Effective floor
     * @param ceiling Effective ceiling
     * @param maxDrop Maximum drop for P(win) calculation
     * @return KellyResult with pWin, kelly, win ratio, etc.
     */
    public static KellyResult calculateFull(
        BigDecimal price,
        BigDecimal floor,
        BigDecimal ceiling,
        BigDecimal maxDrop
    ) {
        BigDecimal pWin = calculatePWin(price, floor, ceiling, maxDrop);
        BigDecimal kelly = calculateKelly(price, floor, ceiling, pWin);

        BigDecimal potentialWin = ceiling.subtract(price);
        BigDecimal potentialLoss = price.subtract(floor);
        BigDecimal winRatio = potentialWin.divide(potentialLoss, 4, RoundingMode.HALF_UP);

        BigDecimal expectedValue = pWin.multiply(potentialWin)
            .subtract(ONE.subtract(pWin).multiply(potentialLoss));

        return new KellyResult(
            pWin,
            ONE.subtract(pWin),
            kelly,
            winRatio,
            potentialWin,
            potentialLoss,
            expectedValue
        );
    }

    /**
     * Apply fractional Kelly (e.g., 0.25 for quarter Kelly).
     * This reduces risk by using only a fraction of the full Kelly bet.
     *
     * @param kelly Full Kelly fraction
     * @param fraction Fraction to apply (e.g., 0.25 for quarter Kelly)
     * @return Fractional Kelly
     */
    public static BigDecimal applyFractionalKelly(BigDecimal kelly, BigDecimal fraction) {
        return kelly.multiply(fraction).setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * Result of Kelly calculation with all intermediate values.
     */
    public record KellyResult(
        BigDecimal pWin,           // Probability of win
        BigDecimal pLoss,          // Probability of loss
        BigDecimal kelly,          // Kelly fraction
        BigDecimal winRatio,       // Win/loss ratio (b)
        BigDecimal potentialWin,   // Upside (ceiling - price)
        BigDecimal potentialLoss,  // Downside (price - floor)
        BigDecimal expectedValue   // Expected P&L per unit
    ) {
        /**
         * Get Kelly as percentage.
         */
        public BigDecimal kellyPercent() {
            return kelly.multiply(new BigDecimal("100"));
        }

        /**
         * Check if trade has positive expected value.
         */
        public boolean isPositiveExpectedValue() {
            return expectedValue.compareTo(ZERO) > 0;
        }

        /**
         * Get summary string.
         */
        public String getSummary() {
            return String.format(
                "P(win)=%.2f%%, Kelly=%.2f%%, WinRatio=%.2f, EV=%.2f",
                pWin.multiply(new BigDecimal("100")).doubleValue(),
                kellyPercent().doubleValue(),
                winRatio.doubleValue(),
                expectedValue.doubleValue()
            );
        }
    }
}
