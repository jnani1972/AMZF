package in.annupaper.service.signal;

import in.annupaper.domain.model.MtfGlobalConfig;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * MTF Exit Calculator - Calculates multi-level exit targets with partial
 * position sizing.
 *
 * Exit Ladder (in order of price):
 * 1. BREAKEVEN - Close at avg cost (0% of remaining position)
 * 2. MIN_PROFIT - min_profit_pct above avg cost (25% of position)
 * 3. LTF_CEILING - LTF timeframe ceiling (25% of position)
 * 4. TARGET - target_r_multiple * risk (50% of remaining)
 * 5. ITF_CEILING - ITF timeframe ceiling (50% of remaining)
 * 6. STRETCH - stretch_r_multiple * risk (75% of remaining)
 * 7. HTF_CEILING - HTF timeframe ceiling (100% remaining)
 *
 * Never exit at a loss (log return < 0).
 */
public final class MtfExitCalculator {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;

    /**
     * Calculate exit targets for a mean reversion position.
     *
     * @param avgCost    Average cost of position
     * @param htfCeiling HTF timeframe ceiling
     * @param itfCeiling ITF timeframe ceiling
     * @param ltfCeiling LTF timeframe ceiling
     * @param config     MTF configuration
     * @return ExitTargets with ladder
     */
    public static ExitTargets calculateExitTargets(
            BigDecimal avgCost,
            BigDecimal htfCeiling,
            BigDecimal itfCeiling,
            BigDecimal ltfCeiling,
            MtfGlobalConfig config) {
        if (avgCost.compareTo(ZERO) <= 0) {
            return new ExitTargets(
                    avgCost,
                    avgCost,
                    avgCost,
                    avgCost,
                    avgCost,
                    List.of());
        }

        // Calculate R (risk) as distance from avg cost to floor
        // For mean reversion, floor is implicit - use min profit as proxy
        BigDecimal risk = avgCost.multiply(config.minProfitPct());

        // 1. Breakeven
        BigDecimal breakeven = avgCost;

        // 2. Min Profit
        BigDecimal minProfitPrice = avgCost.add(
                avgCost.multiply(config.minProfitPct()));

        // 3. Target (target_r_multiple * risk)
        BigDecimal targetPrice = avgCost.add(
                risk.multiply(config.targetRMultiple()));

        // 4. Stretch (stretch_r_multiple * risk)
        BigDecimal stretchPrice = avgCost.add(
                risk.multiply(config.stretchRMultiple()));

        // Build exit ladder
        List<ExitTarget> ladder = new ArrayList<>();

        // Add targets only if they're above avg cost (no losses)
        addTargetIfValid(ladder, "MIN_PROFIT", minProfitPrice, avgCost, 0.25);
        addTargetIfValid(ladder, "LTF_CEILING", ltfCeiling, avgCost, 0.25);
        addTargetIfValid(ladder, "TARGET", targetPrice, avgCost, 0.50);
        addTargetIfValid(ladder, "ITF_CEILING", itfCeiling, avgCost, 0.50);
        addTargetIfValid(ladder, "STRETCH", stretchPrice, avgCost, 0.75);
        addTargetIfValid(ladder, "HTF_CEILING", htfCeiling, avgCost, 1.0);

        // Sort by price ascending
        ladder.sort(Comparator.comparing(ExitTarget::price));

        // Remove near-duplicates (within 0.5% of each other)
        ladder = filterNearDuplicates(ladder);

        // Renormalize exit percentages to sum to 1.0
        ladder = normalizeExitPercentages(ladder);

        return new ExitTargets(
                breakeven,
                minProfitPrice,
                targetPrice,
                stretchPrice,
                htfCeiling,
                ladder);
    }

    /**
     * Add target to ladder if it's valid (above avg cost).
     */
    private static void addTargetIfValid(
            List<ExitTarget> ladder,
            String name,
            BigDecimal price,
            BigDecimal avgCost,
            double exitPct) {
        if (price.compareTo(avgCost) > 0) {
            ladder.add(new ExitTarget(
                    name,
                    price,
                    new BigDecimal(exitPct),
                    calculateLogReturn(avgCost, price)));
        }
    }

    /**
     * Filter out near-duplicate targets (within 0.5% of each other).
     * Keep the one with higher exit percentage.
     */
    private static List<ExitTarget> filterNearDuplicates(List<ExitTarget> ladder) {
        if (ladder.size() <= 1) {
            return ladder;
        }

        List<ExitTarget> filtered = new ArrayList<>();
        ExitTarget prev = null;

        for (ExitTarget curr : ladder) {
            if (prev == null) {
                prev = curr;
                continue;
            }

            // Check if within 0.5%
            BigDecimal priceDiff = curr.price().subtract(prev.price()).abs();
            BigDecimal priceDiffPct = priceDiff.divide(prev.price(), 6, RoundingMode.HALF_UP);

            if (priceDiffPct.compareTo(new BigDecimal("0.005")) < 0) {
                // Near duplicate - keep the one with higher exit %
                if (curr.exitPct().compareTo(prev.exitPct()) > 0) {
                    prev = curr;
                }
            } else {
                // Not a duplicate - add prev and continue
                filtered.add(prev);
                prev = curr;
            }
        }

        // Add last target
        if (prev != null) {
            filtered.add(prev);
        }

        return filtered;
    }

    /**
     * Normalize exit percentages to sum to 1.0.
     * Distributes remaining position across targets proportionally.
     */
    private static List<ExitTarget> normalizeExitPercentages(List<ExitTarget> ladder) {
        if (ladder.isEmpty()) {
            return ladder;
        }

        List<ExitTarget> normalized = new ArrayList<>();
        BigDecimal remainingPosition = ONE;

        for (int i = 0; i < ladder.size(); i++) {
            ExitTarget target = ladder.get(i);

            BigDecimal exitQty;
            if (i == ladder.size() - 1) {
                // Last target gets 100% of remaining
                exitQty = remainingPosition;
            } else {
                // Exit this percentage of remaining position
                exitQty = remainingPosition.multiply(target.exitPct());
            }

            normalized.add(new ExitTarget(
                    target.name(),
                    target.price(),
                    exitQty,
                    target.logReturn()));

            remainingPosition = remainingPosition.subtract(exitQty);
        }

        return normalized;
    }

    /**
     * Calculate log return from avg cost to price.
     */
    private static BigDecimal calculateLogReturn(BigDecimal avgCost, BigDecimal price) {
        if (avgCost.compareTo(ZERO) <= 0 || price.compareTo(ZERO) <= 0) {
            return ZERO;
        }

        double ratio = price.divide(avgCost, 6, RoundingMode.HALF_UP).doubleValue();
        double logReturn = Math.log(ratio);

        return BigDecimal.valueOf(logReturn).setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * Calculate trailing stop price based on current price and config.
     *
     * @param currentPrice Current market price
     * @param avgCost      Position average cost
     * @param config       MTF config
     * @return Trailing stop price (null if not activated)
     */
    public static BigDecimal calculateTrailingStop(
            BigDecimal currentPrice,
            BigDecimal avgCost,
            MtfGlobalConfig config) {
        if (!config.useTrailingStop()) {
            return null;
        }

        // Check if trailing stop is activated
        BigDecimal profitPct = currentPrice.subtract(avgCost)
                .divide(avgCost, 6, RoundingMode.HALF_UP);

        if (profitPct.compareTo(config.trailingStopActivationPct()) < 0) {
            return null; // Not yet activated
        }

        // Calculate trailing stop
        BigDecimal stopDistance = currentPrice.multiply(config.trailingStopDistancePct());
        BigDecimal stopPrice = currentPrice.subtract(stopDistance);

        // Never trail below breakeven
        return stopPrice.max(avgCost);
    }

    /**
     * Exit target in the ladder.
     */
    public record ExitTarget(
            String name, // Target name (e.g., "LTF_CEILING")
            BigDecimal price, // Target price
            BigDecimal exitPct, // Percentage of position to exit (0.0 to 1.0)
            BigDecimal logReturn // Log return at this target
    ) {
        /**
         * Get simple return as percentage.
         */
        public BigDecimal getReturnPercent() {
            double expReturn = Math.exp(logReturn.doubleValue()) - 1.0;
            return BigDecimal.valueOf(expReturn * 100).setScale(2, RoundingMode.HALF_UP);
        }

        /**
         * Get summary string.
         */
        public String getSummary() {
            return String.format(
                    "%s @ $%.2f (%.2f%% position, +%.2f%% return)",
                    name,
                    price.doubleValue(),
                    exitPct.multiply(new BigDecimal("100")).doubleValue(),
                    getReturnPercent().doubleValue());
        }
    }

    /**
     * Complete exit targets result.
     */
    public record ExitTargets(
            BigDecimal breakeven, // Breakeven price
            BigDecimal minProfit, // Minimum profit target
            BigDecimal target, // Main target
            BigDecimal stretch, // Stretch target
            BigDecimal maxTarget, // Maximum target (HTF ceiling)
            List<ExitTarget> ladder // Ordered exit ladder
    ) {
        /**
         * Get target count.
         */
        public int getTargetCount() {
            return ladder.size();
        }

        /**
         * Get next target above current price.
         */
        public ExitTarget getNextTarget(BigDecimal currentPrice) {
            return ladder.stream()
                    .filter(t -> t.price().compareTo(currentPrice) > 0)
                    .findFirst()
                    .orElse(null);
        }

        /**
         * Calculate how much of position should be closed at current price.
         */
        public BigDecimal getExitPctAtPrice(BigDecimal currentPrice) {
            BigDecimal totalExitPct = ZERO;

            for (ExitTarget target : ladder) {
                if (currentPrice.compareTo(target.price()) >= 0) {
                    totalExitPct = totalExitPct.add(target.exitPct());
                } else {
                    break;
                }
            }

            return totalExitPct.min(ONE);
        }

        /**
         * Get summary of all targets.
         */
        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Exit Ladder (%d targets):\n", ladder.size()));
            for (int i = 0; i < ladder.size(); i++) {
                sb.append(String.format("  %d. %s\n", i + 1, ladder.get(i).getSummary()));
            }
            return sb.toString();
        }

        /**
         * Check if all targets are valid (above breakeven).
         */
        public boolean isValid() {
            return ladder.stream().allMatch(t -> t.price().compareTo(breakeven) >= 0);
        }
    }
}
