package in.annupaper.service.signal;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Log-Utility Calculator - Enforces log-safe position sizing constraints.
 *
 * Log-utility ensures that we never risk more than a specified log loss,
 * which prevents catastrophic drawdowns and ensures long-term capital preservation.
 *
 * Key Formula:
 * max_avg_cost = floor * e^(-max_log_loss)
 *
 * Example:
 * - floor = $100, max_log_loss = -8%
 * - max_avg_cost = $100 * e^(0.08) = $108.33
 * - This means we can add to position as long as avg cost stays below $108.33
 */
public final class LogUtilityCalculator {

    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /**
     * Calculate maximum average cost that satisfies log-utility constraint.
     *
     * max_avg_cost = floor * exp(-max_log_loss)
     *
     * @param floor Effective floor (stop loss price)
     * @param maxLogLoss Maximum log loss allowed (as negative decimal, e.g., -0.08 for -8%)
     * @return Maximum average cost
     */
    public static BigDecimal calculateMaxAvgCost(BigDecimal floor, BigDecimal maxLogLoss) {
        if (floor.compareTo(ZERO) <= 0) {
            return ZERO;
        }

        // exp(-max_log_loss) = exp(+abs(max_log_loss))
        double expValue = Math.exp(Math.abs(maxLogLoss.doubleValue()));
        BigDecimal multiplier = BigDecimal.valueOf(expValue);

        return floor.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate maximum log-safe quantity that can be bought.
     *
     * This uses binary search to find the maximum quantity where:
     * new_avg_cost <= max_avg_cost
     *
     * Where: new_avg_cost = (existing_qty * existing_avg + new_qty * entry_price) / (existing_qty + new_qty)
     *
     * @param entryPrice Price to enter at
     * @param effectiveFloor Stop loss price
     * @param maxLogLoss Maximum log loss (e.g., -0.08)
     * @param existingQty Existing position quantity
     * @param existingAvg Existing position average cost
     * @param capSym Capital allocated for this symbol
     * @return Maximum quantity that satisfies log-utility constraint
     */
    public static int calculateMaxLogSafeQty(
        BigDecimal entryPrice,
        BigDecimal effectiveFloor,
        BigDecimal maxLogLoss,
        int existingQty,
        BigDecimal existingAvg,
        BigDecimal capSym
    ) {
        if (entryPrice.compareTo(ZERO) <= 0 || effectiveFloor.compareTo(ZERO) <= 0) {
            return 0;
        }

        // Calculate max average cost allowed
        BigDecimal maxAvgCost = calculateMaxAvgCost(effectiveFloor, maxLogLoss);

        // If we already violate the constraint, return 0
        if (existingQty > 0 && existingAvg.compareTo(maxAvgCost) > 0) {
            return 0;
        }

        // Maximum possible quantity based on capital
        int maxPossibleQty = capSym.divide(entryPrice, 0, RoundingMode.DOWN).intValue();

        // Binary search for maximum quantity
        int left = 0;
        int right = maxPossibleQty;
        int result = 0;

        while (left <= right) {
            int mid = (left + right) / 2;

            // Calculate new average cost with this quantity
            BigDecimal totalCost = existingAvg.multiply(new BigDecimal(existingQty))
                .add(entryPrice.multiply(new BigDecimal(mid)));
            int totalQty = existingQty + mid;

            BigDecimal newAvgCost = totalQty > 0
                ? totalCost.divide(new BigDecimal(totalQty), 6, RoundingMode.HALF_UP)
                : ZERO;

            // Check if this quantity satisfies constraint
            if (newAvgCost.compareTo(maxAvgCost) <= 0) {
                result = mid;
                left = mid + 1;  // Try larger quantity
            } else {
                right = mid - 1;  // Try smaller quantity
            }
        }

        return result;
    }

    /**
     * Check if adding a specific quantity would violate log-utility constraint.
     *
     * @param entryPrice Price to enter at
     * @param qty Quantity to add
     * @param effectiveFloor Stop loss price
     * @param maxLogLoss Maximum log loss
     * @param existingQty Existing position quantity
     * @param existingAvg Existing position average cost
     * @return True if safe, false if violates constraint
     */
    public static boolean isSafeToAdd(
        BigDecimal entryPrice,
        int qty,
        BigDecimal effectiveFloor,
        BigDecimal maxLogLoss,
        int existingQty,
        BigDecimal existingAvg
    ) {
        if (qty <= 0) {
            return true;
        }

        BigDecimal maxAvgCost = calculateMaxAvgCost(effectiveFloor, maxLogLoss);

        // Calculate new average cost
        BigDecimal totalCost = existingAvg.multiply(new BigDecimal(existingQty))
            .add(entryPrice.multiply(new BigDecimal(qty)));
        int totalQty = existingQty + qty;

        BigDecimal newAvgCost = totalCost.divide(new BigDecimal(totalQty), 6, RoundingMode.HALF_UP);

        return newAvgCost.compareTo(maxAvgCost) <= 0;
    }

    /**
     * Calculate actual log loss if position hits floor.
     *
     * log_loss = ln(floor / avg_cost)
     *
     * @param avgCost Average cost of position
     * @param floor Stop loss price
     * @return Log loss (negative value)
     */
    public static BigDecimal calculateLogLoss(BigDecimal avgCost, BigDecimal floor) {
        if (avgCost.compareTo(ZERO) <= 0 || floor.compareTo(ZERO) <= 0) {
            return ZERO;
        }

        double ratio = floor.divide(avgCost, 6, RoundingMode.HALF_UP).doubleValue();
        double logLoss = Math.log(ratio);

        return BigDecimal.valueOf(logLoss).setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * Calculate per-leg log loss (ℓ) for new position.
     *
     * ℓ = ln(S / P)
     *
     * Where:
     * - S = stop price (floor)
     * - P = entry price
     *
     * Result is negative (loss) when S < P.
     *
     * @param entryPrice P (entry price)
     * @param stopPrice S (stop loss price)
     * @return Per-leg log loss (negative value)
     */
    public static BigDecimal calculatePerLegLogLoss(BigDecimal entryPrice, BigDecimal stopPrice) {
        if (entryPrice.compareTo(ZERO) <= 0 || stopPrice.compareTo(ZERO) <= 0) {
            return ZERO;
        }

        double ratio = stopPrice.divide(entryPrice, 6, RoundingMode.HALF_UP).doubleValue();
        double logLoss = Math.log(ratio);

        return BigDecimal.valueOf(logLoss).setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * Calculate portfolio-level log-safe capital allocation.
     *
     * Ensures that total portfolio exposure doesn't violate max portfolio log loss.
     *
     * @param portfolioValue Total portfolio value
     * @param maxPortfolioLogLoss Maximum portfolio log loss (e.g., -0.05 for -5%)
     * @param existingExposure Current total exposure
     * @return Maximum additional capital that can be allocated
     */
    public static BigDecimal calculateMaxPortfolioCapital(
        BigDecimal portfolioValue,
        BigDecimal maxPortfolioLogLoss,
        BigDecimal existingExposure
    ) {
        if (portfolioValue.compareTo(ZERO) <= 0) {
            return ZERO;
        }

        // Calculate maximum safe exposure
        // max_exposure = portfolio_value * (1 - e^(max_portfolio_log_loss))
        double expValue = Math.exp(maxPortfolioLogLoss.doubleValue());
        BigDecimal maxExposureRatio = ONE.subtract(BigDecimal.valueOf(expValue));
        BigDecimal maxTotalExposure = portfolioValue.multiply(maxExposureRatio);

        // Remaining capacity
        BigDecimal remainingCapacity = maxTotalExposure.subtract(existingExposure);

        return remainingCapacity.max(ZERO);
    }

    /**
     * Result of log-utility calculation with all constraints.
     */
    public record LogUtilityResult(
        BigDecimal maxAvgCost,           // Maximum average cost allowed
        int maxLogSafeQty,               // Maximum quantity allowed
        BigDecimal currentLogLoss,       // Current log loss if at floor
        boolean satisfiesConstraint,     // Whether current position satisfies constraint
        String constraintDescription     // Human-readable description
    ) {
        /**
         * Get summary string.
         */
        public String getSummary() {
            return String.format(
                "MaxAvgCost=$%.2f, MaxQty=%d, CurrentLogLoss=%.2f%%, Safe=%s",
                maxAvgCost.doubleValue(),
                maxLogSafeQty,
                currentLogLoss.multiply(new BigDecimal("100")).doubleValue(),
                satisfiesConstraint ? "YES" : "NO"
            );
        }
    }

    /**
     * Calculate full log-utility result with all metrics.
     *
     * @param entryPrice Entry price
     * @param effectiveFloor Stop loss price
     * @param maxLogLoss Maximum log loss
     * @param existingQty Existing quantity
     * @param existingAvg Existing average cost
     * @param capSym Symbol capital
     * @return LogUtilityResult with all metrics
     */
    public static LogUtilityResult calculateFull(
        BigDecimal entryPrice,
        BigDecimal effectiveFloor,
        BigDecimal maxLogLoss,
        int existingQty,
        BigDecimal existingAvg,
        BigDecimal capSym
    ) {
        BigDecimal maxAvgCost = calculateMaxAvgCost(effectiveFloor, maxLogLoss);

        int maxLogSafeQty = calculateMaxLogSafeQty(
            entryPrice, effectiveFloor, maxLogLoss, existingQty, existingAvg, capSym
        );

        BigDecimal currentLogLoss = existingQty > 0
            ? calculateLogLoss(existingAvg, effectiveFloor)
            : ZERO;

        boolean satisfiesConstraint = existingQty == 0 || existingAvg.compareTo(maxAvgCost) <= 0;

        String description = String.format(
            "Max avg cost: $%.2f, Floor: $%.2f, Max log loss: %.2f%%",
            maxAvgCost.doubleValue(),
            effectiveFloor.doubleValue(),
            maxLogLoss.multiply(new BigDecimal("100")).abs().doubleValue()
        );

        return new LogUtilityResult(
            maxAvgCost,
            maxLogSafeQty,
            currentLogLoss,
            satisfiesConstraint,
            description
        );
    }
}
