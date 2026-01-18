package in.annupaper.service.signal;

import in.annupaper.domain.model.MtfGlobalConfig;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Utility Asymmetry Calculator - Enforces 3× advantage gate with piecewise
 * utility.
 *
 * Constitutional Principle:
 * "A trade is admissible only if its expected upside utility exceeds its
 * expected
 * downside disutility by at least MIN_ADVANTAGE_RATIO (default: 3.0)."
 *
 * Formula: p · U(π) ≥ MIN_ADVANTAGE_RATIO · (1-p) · |U(ℓ)|
 *
 * Piecewise Utility in Log-Return Space:
 * U(r) = r^α for r ≥ 0 (concave gains)
 * U(r) = -λ · (-r)^β for r < 0 (convex losses)
 *
 * Where:
 * - α ∈ (0,1): upside concavity, default 0.60 (lower = faster saturation)
 * - β > 1: downside convexity, default 1.40 (higher = faster acceleration)
 * - λ ≥ 1: loss aversion multiplier, default 1.00 (3× is in gate formula)
 * - π = ln(T/P) > 0: upside log return to target
 * - ℓ = ln(S/P) < 0: downside log return to stop
 * - d = -ℓ > 0: positive downside magnitude
 */
public final class UtilityAsymmetryCalculator {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;

    /**
     * Calculate utility for a log return using piecewise utility function.
     *
     * U(r) = r^α for r ≥ 0 (concave gains)
     * U(r) = -λ · (-r)^β for r < 0 (convex losses)
     *
     * @param logReturn Log return (π for upside, ℓ for downside)
     * @param alpha     α parameter (upside concavity)
     * @param beta      β parameter (downside convexity)
     * @param lambda    λ parameter (loss aversion multiplier)
     * @return Utility value
     */
    public static BigDecimal calculateUtility(
            BigDecimal logReturn,
            BigDecimal alpha,
            BigDecimal beta,
            BigDecimal lambda) {
        if (logReturn.compareTo(ZERO) >= 0) {
            // Upside: U(r) = r^α
            double r = logReturn.doubleValue();
            double a = alpha.doubleValue();
            double utility = Math.pow(r, a);
            return BigDecimal.valueOf(utility).setScale(6, RoundingMode.HALF_UP);
        } else {
            // Downside: U(r) = -λ · (-r)^β
            double r = logReturn.doubleValue();
            double d = -r; // Convert to positive magnitude
            double b = beta.doubleValue();
            double l = lambda.doubleValue();
            double utility = -l * Math.pow(d, b);
            return BigDecimal.valueOf(utility).setScale(6, RoundingMode.HALF_UP);
        }
    }

    /**
     * Check if trade passes the 3× advantage gate (probability-based version).
     *
     * Gate: p · U(π) ≥ ratio · (1-p) · |U(ℓ)|
     *
     * With piecewise utility: p · π^α ≥ ratio · (1-p) · λ · d^β
     *
     * @param pWin              Probability of hitting target (p)
     * @param upsideLogReturn   Upside log return (π = ln(T/P) > 0)
     * @param downsideLogReturn Downside log return (ℓ = ln(S/P) < 0)
     * @param config            MTF configuration with utility parameters
     * @return True if gate passes, false if rejected
     */
    public static boolean passesAdvantageGate(
            BigDecimal pWin,
            BigDecimal upsideLogReturn,
            BigDecimal downsideLogReturn,
            MtfGlobalConfig config) {
        if (pWin == null || upsideLogReturn == null || downsideLogReturn == null) {
            return false;
        }

        // Validate upside is positive, downside is negative
        if (upsideLogReturn.compareTo(ZERO) <= 0 || downsideLogReturn.compareTo(ZERO) >= 0) {
            return false;
        }

        // Calculate utilities
        BigDecimal upsideUtility = calculateUtility(
                upsideLogReturn,
                config.utilityAlpha(),
                config.utilityBeta(),
                config.utilityLambda());

        BigDecimal downsideUtility = calculateUtility(
                downsideLogReturn,
                config.utilityAlpha(),
                config.utilityBeta(),
                config.utilityLambda());

        // Expected upside: p · U(π)
        BigDecimal expectedUpside = pWin.multiply(upsideUtility);

        // Expected downside: (1-p) · |U(ℓ)|
        BigDecimal pLoss = ONE.subtract(pWin);
        BigDecimal expectedDownside = pLoss.multiply(downsideUtility.abs());

        // Required: expected_upside ≥ ratio × expected_downside
        BigDecimal requiredUpside = config.minAdvantageRatio().multiply(expectedDownside);

        return expectedUpside.compareTo(requiredUpside) >= 0;
    }

    /**
     * Check if trade passes the 3× advantage gate (deterministic version, no
     * probability).
     *
     * Deterministic Gate: U(π) ≥ ratio · |U(ℓ)|
     *
     * With piecewise utility: π^α ≥ ratio · λ · d^β
     *
     * This is a stricter "shape-only" gate when probability is not trusted.
     *
     * @param upsideLogReturn   Upside log return (π = ln(T/P) > 0)
     * @param downsideLogReturn Downside log return (ℓ = ln(S/P) < 0)
     * @param config            MTF configuration with utility parameters
     * @return True if gate passes, false if rejected
     */
    public static boolean passesAdvantageGateDeterministic(
            BigDecimal upsideLogReturn,
            BigDecimal downsideLogReturn,
            MtfGlobalConfig config) {
        if (upsideLogReturn == null || downsideLogReturn == null) {
            return false;
        }

        // Validate upside is positive, downside is negative
        if (upsideLogReturn.compareTo(ZERO) <= 0 || downsideLogReturn.compareTo(ZERO) >= 0) {
            return false;
        }

        // Calculate utilities
        BigDecimal upsideUtility = calculateUtility(
                upsideLogReturn,
                config.utilityAlpha(),
                config.utilityBeta(),
                config.utilityLambda());

        BigDecimal downsideUtility = calculateUtility(
                downsideLogReturn,
                config.utilityAlpha(),
                config.utilityBeta(),
                config.utilityLambda());

        // Required: U(π) ≥ ratio × |U(ℓ)|
        BigDecimal requiredUtility = config.minAdvantageRatio().multiply(downsideUtility.abs());

        return upsideUtility.compareTo(requiredUtility) >= 0;
    }

    /**
     * Calculate required minimum probability for gate to pass.
     *
     * Rearranging the gate inequality:
     * p · π^α ≥ ratio · (1-p) · λ · d^β
     *
     * Solving for p:
     * p ≥ (ratio · λ · d^β) / (π^α + ratio · λ · d^β)
     *
     * @param upsideLogReturn   Upside log return (π)
     * @param downsideLogReturn Downside log return (ℓ)
     * @param config            MTF configuration with utility parameters
     * @return Minimum probability required [0,1]
     */
    public static BigDecimal calculateRequiredProbability(
            BigDecimal upsideLogReturn,
            BigDecimal downsideLogReturn,
            MtfGlobalConfig config) {
        if (upsideLogReturn == null || downsideLogReturn == null) {
            return ONE; // Impossible to satisfy
        }

        // Validate upside is positive, downside is negative
        if (upsideLogReturn.compareTo(ZERO) <= 0 || downsideLogReturn.compareTo(ZERO) >= 0) {
            return ONE; // Impossible to satisfy
        }

        // Calculate utilities
        BigDecimal upsideUtility = calculateUtility(
                upsideLogReturn,
                config.utilityAlpha(),
                config.utilityBeta(),
                config.utilityLambda());

        BigDecimal downsideUtility = calculateUtility(
                downsideLogReturn,
                config.utilityAlpha(),
                config.utilityBeta(),
                config.utilityLambda()).abs();

        // p_min = (ratio × downsideUtility) / (upsideUtility + ratio × downsideUtility)
        BigDecimal numerator = config.minAdvantageRatio().multiply(downsideUtility);
        BigDecimal denominator = upsideUtility.add(numerator);

        if (denominator.compareTo(ZERO) <= 0) {
            return ONE; // Impossible
        }

        BigDecimal pMin = numerator.divide(denominator, 6, RoundingMode.HALF_UP);

        // Clamp to [0, 1]
        return pMin.max(ZERO).min(ONE);
    }

    /**
     * Calculate advantage ratio for diagnostics.
     *
     * Advantage Ratio = [p · U(π)] / [(1-p) · |U(ℓ)|]
     *
     * @param pWin              Probability of win
     * @param upsideLogReturn   Upside log return
     * @param downsideLogReturn Downside log return
     * @param config            MTF configuration
     * @return Advantage ratio (should be ≥ min_advantage_ratio)
     */
    public static BigDecimal calculateAdvantageRatio(
            BigDecimal pWin,
            BigDecimal upsideLogReturn,
            BigDecimal downsideLogReturn,
            MtfGlobalConfig config) {
        if (pWin == null || upsideLogReturn == null || downsideLogReturn == null) {
            return ZERO;
        }

        BigDecimal upsideUtility = calculateUtility(
                upsideLogReturn,
                config.utilityAlpha(),
                config.utilityBeta(),
                config.utilityLambda());

        BigDecimal downsideUtility = calculateUtility(
                downsideLogReturn,
                config.utilityAlpha(),
                config.utilityBeta(),
                config.utilityLambda());

        BigDecimal expectedUpside = pWin.multiply(upsideUtility);
        BigDecimal pLoss = ONE.subtract(pWin);
        BigDecimal expectedDownside = pLoss.multiply(downsideUtility.abs());

        if (expectedDownside.compareTo(ZERO) <= 0) {
            return BigDecimal.valueOf(Double.MAX_VALUE); // Infinite advantage
        }

        return expectedUpside.divide(expectedDownside, 6, RoundingMode.HALF_UP);
    }

    /**
     * Result of utility asymmetry calculation with full diagnostics.
     */
    public record UtilityAsymmetryResult(
            BigDecimal upsideLogReturn, // π = ln(T/P)
            BigDecimal downsideLogReturn, // ℓ = ln(S/P)
            BigDecimal upsideUtility, // U(π)
            BigDecimal downsideUtility, // U(ℓ)
            BigDecimal expectedUpside, // p · U(π)
            BigDecimal expectedDownside, // (1-p) · |U(ℓ)|
            BigDecimal advantageRatio, // E[U_up] / E[U_down]
            BigDecimal requiredProbability, // Minimum p needed
            boolean passesGate, // Whether trade passes
            String rejectionReason // Why rejected (if rejected)
    ) {
        /**
         * Get summary string.
         */
        public String getSummary() {
            if (passesGate) {
                return String.format(
                        "PASS: Advantage=%.2f×, E[U_up]=%.4f, E[U_down]=%.4f, π=%.4f, ℓ=%.4f",
                        advantageRatio.doubleValue(),
                        expectedUpside.doubleValue(),
                        expectedDownside.doubleValue(),
                        upsideLogReturn.doubleValue(),
                        downsideLogReturn.doubleValue());
            } else {
                return String.format(
                        "REJECT: %s (Advantage=%.2f×, Required ≥3.0×, p_min=%.2f%%)",
                        rejectionReason,
                        advantageRatio.doubleValue(),
                        requiredProbability.multiply(new BigDecimal("100")).doubleValue());
            }
        }
    }

    /**
     * Calculate full utility asymmetry analysis with diagnostics.
     *
     * @param pWin        Probability of hitting target
     * @param entryPrice  Entry price (P)
     * @param targetPrice Target price (T)
     * @param stopPrice   Stop loss price (S)
     * @param config      MTF configuration
     * @return UtilityAsymmetryResult with all metrics
     */
    public static UtilityAsymmetryResult calculateFull(
            BigDecimal pWin,
            BigDecimal entryPrice,
            BigDecimal targetPrice,
            BigDecimal stopPrice,
            MtfGlobalConfig config) {
        // Calculate log returns
        // π = ln(T/P)
        BigDecimal upsideLogReturn = BigDecimal.valueOf(
                Math.log(targetPrice.divide(entryPrice, 6, RoundingMode.HALF_UP).doubleValue()))
                .setScale(6, RoundingMode.HALF_UP);

        // ℓ = ln(S/P)
        BigDecimal downsideLogReturn = BigDecimal.valueOf(
                Math.log(stopPrice.divide(entryPrice, 6, RoundingMode.HALF_UP).doubleValue()))
                .setScale(6, RoundingMode.HALF_UP);

        // Calculate utilities
        BigDecimal upsideUtility = calculateUtility(
                upsideLogReturn,
                config.utilityAlpha(),
                config.utilityBeta(),
                config.utilityLambda());

        BigDecimal downsideUtility = calculateUtility(
                downsideLogReturn,
                config.utilityAlpha(),
                config.utilityBeta(),
                config.utilityLambda());

        // Expected utilities
        BigDecimal expectedUpside = pWin.multiply(upsideUtility);
        BigDecimal pLoss = ONE.subtract(pWin);
        BigDecimal expectedDownside = pLoss.multiply(downsideUtility.abs());

        // Advantage ratio
        BigDecimal advantageRatio = calculateAdvantageRatio(
                pWin, upsideLogReturn, downsideLogReturn, config);

        // Required probability
        BigDecimal requiredP = calculateRequiredProbability(
                upsideLogReturn, downsideLogReturn, config);

        // Check gate
        boolean passes = passesAdvantageGate(
                pWin, upsideLogReturn, downsideLogReturn, config);

        String reason = null;
        if (!passes) {
            if (advantageRatio.compareTo(config.minAdvantageRatio()) < 0) {
                reason = String.format(
                        "Insufficient advantage (%.2f× < %.2f× required)",
                        advantageRatio.doubleValue(),
                        config.minAdvantageRatio().doubleValue());
            } else {
                reason = "Utility gate failed";
            }
        }

        return new UtilityAsymmetryResult(
                upsideLogReturn,
                downsideLogReturn,
                upsideUtility,
                downsideUtility,
                expectedUpside,
                expectedDownside,
                advantageRatio,
                requiredP,
                passes,
                reason);
    }
}
