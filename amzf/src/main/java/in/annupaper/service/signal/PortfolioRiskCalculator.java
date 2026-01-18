package in.annupaper.service.signal;

import in.annupaper.domain.model.Trade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Portfolio Risk Calculator - Calculates portfolio and symbol-level log-loss risk.
 *
 * Constitutional risk tracking:
 * - R_port = Σ(all positions) e_j × ℓ_j
 * - R_sym = Σ(symbol positions) e_j × ℓ_j
 *
 * Where:
 * - e_j = exposure weight of position j
 * - ℓ_j = log loss of position j = ln(S_j / P_j)
 */
public final class PortfolioRiskCalculator {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /**
     * Calculate current portfolio log-loss (R_port).
     *
     * R_port = Σ(all open positions) currentLogReturn
     *
     * @param openTrades List of all open trades
     * @return Portfolio log loss (negative value representing total risk)
     */
    public static BigDecimal calculateCurrentPortfolioLogLoss(List<Trade> openTrades) {
        if (openTrades == null || openTrades.isEmpty()) {
            return ZERO;
        }

        BigDecimal totalLogLoss = ZERO;
        for (Trade trade : openTrades) {
            if (trade.currentLogReturn() != null) {
                totalLogLoss = totalLogLoss.add(trade.currentLogReturn());
            }
        }

        return totalLogLoss;
    }

    /**
     * Calculate symbol-level log-loss (R_sym).
     *
     * R_sym = Σ(symbol positions) currentLogReturn
     *
     * @param openTrades List of all open trades
     * @param symbol Symbol to filter by
     * @return Symbol log loss (negative value representing symbol risk)
     */
    public static BigDecimal calculateSymbolLogLoss(List<Trade> openTrades, String symbol) {
        if (openTrades == null || openTrades.isEmpty() || symbol == null) {
            return ZERO;
        }

        BigDecimal symbolLogLoss = ZERO;
        for (Trade trade : openTrades) {
            if (symbol.equals(trade.symbol()) && trade.currentLogReturn() != null) {
                symbolLogLoss = symbolLogLoss.add(trade.currentLogReturn());
            }
        }

        return symbolLogLoss;
    }

    /**
     * Calculate portfolio headroom for new position.
     *
     * Headroom (exposure weight) = (L_port - R_port) / ℓ_new
     *
     * Where:
     * - L_port = max allowed portfolio log loss (e.g., -0.05)
     * - R_port = current portfolio log loss
     * - ℓ_new = per-leg log loss for new position
     *
     * @param currentPortfolioLogLoss R_port (current portfolio risk)
     * @param maxPortfolioLogLoss L_port (budget, e.g., -0.05)
     * @param perLegLogLoss ℓ_new (new position risk per unit exposure)
     * @return Maximum exposure weight allowed [0,1], or 0 if no headroom
     */
    public static BigDecimal calculatePortfolioHeadroom(
        BigDecimal currentPortfolioLogLoss,
        BigDecimal maxPortfolioLogLoss,
        BigDecimal perLegLogLoss
    ) {
        if (perLegLogLoss.compareTo(ZERO) >= 0) {
            return ZERO;  // ℓ must be negative
        }

        BigDecimal headroom = maxPortfolioLogLoss.subtract(currentPortfolioLogLoss);

        if (headroom.compareTo(ZERO) <= 0) {
            return ZERO;  // No headroom left
        }

        // e_max = headroom / ℓ_new
        // (Both negative, so result is positive)
        BigDecimal maxExposure = headroom.divide(perLegLogLoss, 6, RoundingMode.DOWN);

        return maxExposure.max(ZERO);
    }

    /**
     * Calculate symbol headroom for new position.
     *
     * Headroom (exposure weight) = (L_sym - R_sym) / ℓ_new
     *
     * @param currentSymbolLogLoss R_sym (current symbol risk)
     * @param maxSymbolLogLoss L_sym (symbol budget, e.g., -0.10)
     * @param perLegLogLoss ℓ_new (new position risk per unit exposure)
     * @return Maximum exposure weight allowed [0,1], or 0 if no headroom
     */
    public static BigDecimal calculateSymbolHeadroom(
        BigDecimal currentSymbolLogLoss,
        BigDecimal maxSymbolLogLoss,
        BigDecimal perLegLogLoss
    ) {
        if (perLegLogLoss.compareTo(ZERO) >= 0) {
            return ZERO;  // ℓ must be negative
        }

        BigDecimal headroom = maxSymbolLogLoss.subtract(currentSymbolLogLoss);

        if (headroom.compareTo(ZERO) <= 0) {
            return ZERO;  // No headroom left
        }

        // e_max = headroom / ℓ_new
        BigDecimal maxExposure = headroom.divide(perLegLogLoss, 6, RoundingMode.DOWN);

        return maxExposure.max(ZERO);
    }

    /**
     * Convert exposure weight to quantity.
     *
     * Qty = floor((C × e) / P)
     *
     * @param exposureWeight e (exposure weight [0,1])
     * @param totalCapital C (total capital)
     * @param entryPrice P (entry price)
     * @return Quantity (integer)
     */
    public static int convertExposureToQty(
        BigDecimal exposureWeight,
        BigDecimal totalCapital,
        BigDecimal entryPrice
    ) {
        if (exposureWeight.compareTo(ZERO) <= 0 || totalCapital.compareTo(ZERO) <= 0 || entryPrice.compareTo(ZERO) <= 0) {
            return 0;
        }

        BigDecimal capitalForPosition = totalCapital.multiply(exposureWeight);
        int qty = capitalForPosition.divide(entryPrice, 0, RoundingMode.DOWN).intValue();

        return Math.max(0, qty);
    }

    /**
     * Result of portfolio risk calculation.
     */
    public record PortfolioRiskResult(
        BigDecimal currentPortfolioLogLoss,
        BigDecimal currentSymbolLogLoss,
        BigDecimal portfolioHeadroom,
        BigDecimal symbolHeadroom,
        int openPositionCount,
        int symbolPositionCount,
        boolean hasPortfolioHeadroom,
        boolean hasSymbolHeadroom
    ) {
        /**
         * Check if new position can be added.
         */
        public boolean canAddPosition() {
            return hasPortfolioHeadroom && hasSymbolHeadroom;
        }

        /**
         * Get limiting headroom (minimum of portfolio and symbol).
         */
        public BigDecimal getLimitingHeadroom() {
            return portfolioHeadroom.min(symbolHeadroom);
        }

        /**
         * Get summary string.
         */
        public String getSummary() {
            return String.format(
                "Portfolio: %.4f (headroom: %.4f), Symbol: %.4f (headroom: %.4f), Positions: %d/%d",
                currentPortfolioLogLoss,
                portfolioHeadroom,
                currentSymbolLogLoss,
                symbolHeadroom,
                symbolPositionCount,
                openPositionCount
            );
        }
    }

    /**
     * Calculate full portfolio risk analysis.
     *
     * @param openTrades All open trades
     * @param symbol Symbol for new position
     * @param maxPortfolioLogLoss L_port budget
     * @param maxSymbolLogLoss L_sym budget
     * @param perLegLogLoss ℓ_new for new position
     * @return PortfolioRiskResult with all metrics
     */
    public static PortfolioRiskResult calculateFull(
        List<Trade> openTrades,
        String symbol,
        BigDecimal maxPortfolioLogLoss,
        BigDecimal maxSymbolLogLoss,
        BigDecimal perLegLogLoss
    ) {
        BigDecimal portfolioLogLoss = calculateCurrentPortfolioLogLoss(openTrades);
        BigDecimal symbolLogLoss = calculateSymbolLogLoss(openTrades, symbol);

        BigDecimal portfolioHeadroom = calculatePortfolioHeadroom(
            portfolioLogLoss, maxPortfolioLogLoss, perLegLogLoss
        );

        BigDecimal symbolHeadroom = calculateSymbolHeadroom(
            symbolLogLoss, maxSymbolLogLoss, perLegLogLoss
        );

        int openCount = openTrades != null ? openTrades.size() : 0;
        int symbolCount = 0;
        if (openTrades != null) {
            for (Trade trade : openTrades) {
                if (symbol.equals(trade.symbol())) {
                    symbolCount++;
                }
            }
        }

        return new PortfolioRiskResult(
            portfolioLogLoss,
            symbolLogLoss,
            portfolioHeadroom,
            symbolHeadroom,
            openCount,
            symbolCount,
            portfolioHeadroom.compareTo(ZERO) > 0,
            symbolHeadroom.compareTo(ZERO) > 0
        );
    }

    /**
     * Calculate portfolio drawdown percentage for stress throttling.
     *
     * Drawdown = (Current Portfolio Value - Peak Portfolio Value) / Peak Portfolio Value
     *
     * This is a realized + unrealized drawdown calculation.
     *
     * @param openTrades List of all open trades
     * @param currentPortfolioValue Current total portfolio value
     * @param peakPortfolioValue Peak (highest) portfolio value since tracking began
     * @return Drawdown as negative decimal (e.g., -0.05 for -5% drawdown)
     */
    public static BigDecimal calculatePortfolioDrawdown(
        List<Trade> openTrades,
        BigDecimal currentPortfolioValue,
        BigDecimal peakPortfolioValue
    ) {
        if (peakPortfolioValue.compareTo(ZERO) <= 0) {
            return ZERO;
        }

        // Drawdown = (Current - Peak) / Peak
        BigDecimal drawdown = currentPortfolioValue
            .subtract(peakPortfolioValue)
            .divide(peakPortfolioValue, 6, RoundingMode.HALF_UP);

        // Drawdown should be negative or zero
        return drawdown.min(ZERO);
    }

    /**
     * Calculate stress throttle factor based on portfolio drawdown.
     *
     * Stress Throttle Function g(stress):
     * - When drawdown = 0%: g = 1.0 (no throttle)
     * - When drawdown = max_stress: g = 0.5 (50% throttle)
     * - When drawdown > max_stress: g = 0.25 (75% throttle, severe stress)
     *
     * Linear interpolation between 0% and max_stress.
     *
     * @param portfolioDrawdown Current drawdown (negative value, e.g., -0.05)
     * @param maxStressDrawdown Maximum allowed stress drawdown (e.g., -0.05 for -5%)
     * @return Throttle factor [0.25, 1.0]
     */
    public static BigDecimal calculateStressThrottle(
        BigDecimal portfolioDrawdown,
        BigDecimal maxStressDrawdown
    ) {
        // No stress → full throttle (1.0)
        if (portfolioDrawdown.compareTo(ZERO) >= 0) {
            return BigDecimal.ONE;
        }

        // No max defined or invalid → full throttle
        if (maxStressDrawdown.compareTo(ZERO) >= 0) {
            return BigDecimal.ONE;
        }

        // Calculate stress ratio: stress_ratio = drawdown / max_stress
        // Both are negative, so result is positive
        BigDecimal stressRatio = portfolioDrawdown.divide(
            maxStressDrawdown,
            6,
            RoundingMode.HALF_UP
        );

        // Piecewise throttle function:
        // stress_ratio ∈ [0, 1]    → g = 1.0 - 0.5 * stress_ratio  (1.0 to 0.5)
        // stress_ratio > 1         → g = 0.25 (severe stress)

        if (stressRatio.compareTo(BigDecimal.ONE) <= 0) {
            // Linear interpolation: g = 1.0 - 0.5 * stress_ratio
            BigDecimal throttleReduction = new BigDecimal("0.5").multiply(stressRatio);
            return BigDecimal.ONE.subtract(throttleReduction);
        } else {
            // Severe stress → heavy throttle
            return new BigDecimal("0.25");
        }
    }

    /**
     * Result of stress calculation.
     */
    public record StressResult(
        BigDecimal portfolioDrawdown,      // Current drawdown (negative)
        BigDecimal stressRatio,            // Drawdown / MaxDrawdown
        BigDecimal stressThrottle,         // Throttle factor [0.25, 1.0]
        String stressLevel                 // "NONE", "LOW", "MODERATE", "HIGH", "SEVERE"
    ) {
        /**
         * Get summary string.
         */
        public String getSummary() {
            return String.format(
                "Drawdown=%.2f%%, StressLevel=%s, Throttle=%.2f",
                portfolioDrawdown.multiply(new BigDecimal("100")).doubleValue(),
                stressLevel,
                stressThrottle.doubleValue()
            );
        }
    }

    /**
     * Calculate full stress analysis with diagnostics.
     *
     * @param openTrades All open trades
     * @param currentPortfolioValue Current portfolio value
     * @param peakPortfolioValue Peak portfolio value
     * @param maxStressDrawdown Maximum allowed stress drawdown
     * @return StressResult with all metrics
     */
    public static StressResult calculateStressFull(
        List<Trade> openTrades,
        BigDecimal currentPortfolioValue,
        BigDecimal peakPortfolioValue,
        BigDecimal maxStressDrawdown
    ) {
        BigDecimal drawdown = calculatePortfolioDrawdown(
            openTrades,
            currentPortfolioValue,
            peakPortfolioValue
        );

        BigDecimal stressRatio = ZERO;
        if (maxStressDrawdown.compareTo(ZERO) < 0 && drawdown.compareTo(ZERO) < 0) {
            stressRatio = drawdown.divide(maxStressDrawdown, 6, RoundingMode.HALF_UP);
        }

        BigDecimal throttle = calculateStressThrottle(drawdown, maxStressDrawdown);

        // Determine stress level
        String stressLevel;
        if (stressRatio.compareTo(ZERO) == 0) {
            stressLevel = "NONE";
        } else if (stressRatio.compareTo(new BigDecimal("0.33")) <= 0) {
            stressLevel = "LOW";
        } else if (stressRatio.compareTo(new BigDecimal("0.67")) <= 0) {
            stressLevel = "MODERATE";
        } else if (stressRatio.compareTo(BigDecimal.ONE) <= 0) {
            stressLevel = "HIGH";
        } else {
            stressLevel = "SEVERE";
        }

        return new StressResult(
            drawdown,
            stressRatio,
            throttle,
            stressLevel
        );
    }
}
