package in.annupaper.service.signal;

import in.annupaper.domain.data.Candle;
import in.annupaper.domain.signal.MtfGlobalConfig;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Velocity Calculator - Structural deployment throttle based on Range/ATR regime.
 *
 * Constitutional Principle:
 * "Velocity is a structural deployment throttle derived from Range_ATR (price range
 * normalized by ATR), discretized into regime buckets, and dynamically reduced by
 * portfolio log-loss stress. It is not derived from short-term price speed or momentum."
 *
 * Formula: V = V_base(Range_ATR) × g(stress)
 *
 * Where:
 * - Range_ATR = (Highest High - Lowest Low) / ATR over lookback period
 * - V_base comes from discrete regime buckets (wide, healthy, tight, compressed)
 * - g(stress) is stress throttle based on portfolio drawdown
 *
 * Optional brake: Body ratio penalty (reduces only, never amplifies)
 */
public final class VelocityCalculator {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;

    /**
     * Calculate Range/ATR ratio for velocity determination.
     *
     * Range_ATR = (High - Low) / ATR
     *
     * Where:
     * - High = Highest high over lookback period
     * - Low = Lowest low over lookback period
     * - ATR = Average True Range over same lookback period
     *
     * @param candles List of candles (must be >= lookback bars)
     * @param lookbackBars Number of bars to look back (typically 50-100)
     * @param atr Average True Range for the lookback period
     * @return Range_ATR ratio
     */
    public static BigDecimal calculateRangeATR(
        List<Candle> candles,
        int lookbackBars,
        BigDecimal atr
    ) {
        if (candles == null || candles.isEmpty()) {
            return ZERO;
        }

        if (atr.compareTo(ZERO) <= 0) {
            return ZERO;
        }

        // Take most recent lookbackBars candles
        int startIndex = Math.max(0, candles.size() - lookbackBars);
        List<Candle> recentCandles = candles.subList(startIndex, candles.size());

        if (recentCandles.isEmpty()) {
            return ZERO;
        }

        // Find highest high and lowest low
        BigDecimal highestHigh = recentCandles.get(0).high();
        BigDecimal lowestLow = recentCandles.get(0).low();

        for (Candle candle : recentCandles) {
            if (candle.high().compareTo(highestHigh) > 0) {
                highestHigh = candle.high();
            }
            if (candle.low().compareTo(lowestLow) < 0) {
                lowestLow = candle.low();
            }
        }

        // Range = High - Low
        BigDecimal range = highestHigh.subtract(lowestLow);

        // Range_ATR = Range / ATR
        return range.divide(atr, 6, RoundingMode.HALF_UP);
    }

    /**
     * Get velocity base multiplier from Range_ATR using discrete regime buckets.
     *
     * Lookup Table (Structural, Stable):
     * Range_ATR ≥ 8:  Wide, smooth     → 1.00 (full deployment)
     * Range_ATR 5-8:  Healthy          → 0.75
     * Range_ATR 3-5:  Tight            → 0.50
     * Range_ATR < 3:  Compressed       → 0.25 (minimal deployment)
     *
     * @param rangeATR Range/ATR ratio
     * @param config MTF configuration with velocity thresholds
     * @return Velocity base multiplier [0.25, 1.00]
     */
    public static BigDecimal getVelocityBase(BigDecimal rangeATR, MtfGlobalConfig config) {
        if (rangeATR.compareTo(config.rangeAtrThresholdWide()) >= 0) {
            return config.velocityMultiplierWide();  // 1.00
        }
        if (rangeATR.compareTo(config.rangeAtrThresholdHealthy()) >= 0) {
            return config.velocityMultiplierHealthy();  // 0.75
        }
        if (rangeATR.compareTo(config.rangeAtrThresholdTight()) >= 0) {
            return config.velocityMultiplierTight();  // 0.50
        }
        return config.velocityMultiplierCompressed();  // 0.25
    }

    /**
     * Calculate body ratio for candle compression detection.
     *
     * BodyRatio = EMA(|Close - Open|) / ATR
     *
     * This measures how compressed candles are relative to overall volatility.
     * Low body ratio indicates choppy, sideways action.
     *
     * @param candles Recent candles for EMA calculation
     * @param atr Average True Range
     * @param emaSpan EMA span (typically 10-20 bars)
     * @return Body ratio
     */
    public static BigDecimal calculateBodyRatio(
        List<Candle> candles,
        BigDecimal atr,
        int emaSpan
    ) {
        if (candles == null || candles.isEmpty()) {
            return ZERO;
        }

        if (atr.compareTo(ZERO) <= 0) {
            return ZERO;
        }

        // Calculate EMA of |Close - Open|
        BigDecimal alpha = new BigDecimal("2.0").divide(
            new BigDecimal(emaSpan + 1),
            6,
            RoundingMode.HALF_UP
        );

        BigDecimal ema = ZERO;
        boolean first = true;

        for (Candle candle : candles) {
            BigDecimal bodySize = candle.close().subtract(candle.open()).abs();

            if (first) {
                ema = bodySize;
                first = false;
            } else {
                // EMA = α × current + (1 - α) × EMA_prev
                ema = alpha.multiply(bodySize).add(
                    ONE.subtract(alpha).multiply(ema)
                );
            }
        }

        // BodyRatio = EMA / ATR
        return ema.divide(atr, 6, RoundingMode.HALF_UP);
    }

    /**
     * Apply body ratio penalty to velocity base (penalty only, never amplify).
     *
     * Constitutional Rule: Body ratio is a brake, not an accelerator.
     *
     * Penalty Schedule:
     * - BodyRatio < critical_threshold (0.15): multiply by critical_penalty (0.5)
     * - BodyRatio < low_threshold (0.25):     multiply by low_penalty (0.7)
     * - BodyRatio >= low_threshold:           no penalty (1.0)
     *
     * @param velocityBase V_base from Range_ATR lookup
     * @param bodyRatio Calculated body ratio
     * @param config MTF configuration with body ratio thresholds
     * @return Adjusted velocity base (reduced only, never increased)
     */
    public static BigDecimal applyBodyRatioPenalty(
        BigDecimal velocityBase,
        BigDecimal bodyRatio,
        MtfGlobalConfig config
    ) {
        // Critical compression: severe penalty
        if (bodyRatio.compareTo(config.bodyRatioThresholdCritical()) < 0) {
            return velocityBase.multiply(config.bodyRatioPenaltyCritical());
        }

        // Low compression: moderate penalty
        if (bodyRatio.compareTo(config.bodyRatioThresholdLow()) < 0) {
            return velocityBase.multiply(config.bodyRatioPenaltyLow());
        }

        // No penalty
        return velocityBase;
    }

    /**
     * Calculate final velocity throttle: V = V_base × g(stress) [× body_penalty].
     *
     * This is the complete velocity calculation integrating:
     * 1. Range_ATR regime → V_base (structural)
     * 2. Portfolio stress → g(stress) (dynamic)
     * 3. Body ratio → optional brake (penalty only)
     *
     * @param rangeATR Range/ATR ratio
     * @param bodyRatio Body compression ratio (optional, can be null)
     * @param stressThrottle Stress throttle factor from drawdown
     * @param config MTF configuration
     * @return Final velocity throttle [0.0625, 1.00]
     */
    public static BigDecimal calculateFinalVelocity(
        BigDecimal rangeATR,
        BigDecimal bodyRatio,
        BigDecimal stressThrottle,
        MtfGlobalConfig config
    ) {
        // Step 1: Get V_base from Range_ATR regime
        BigDecimal vBase = getVelocityBase(rangeATR, config);

        // Step 2: Apply body ratio penalty (optional brake)
        if (bodyRatio != null && bodyRatio.compareTo(ZERO) > 0) {
            vBase = applyBodyRatioPenalty(vBase, bodyRatio, config);
        }

        // Step 3: Apply stress throttle
        BigDecimal finalVelocity = vBase.multiply(stressThrottle);

        // Ensure minimum velocity (0.0625 = 0.25 * 0.25)
        BigDecimal minVelocity = new BigDecimal("0.0625");
        return finalVelocity.max(minVelocity);
    }

    /**
     * Result of velocity calculation with full diagnostics.
     */
    public record VelocityResult(
        BigDecimal rangeATR,              // Range/ATR ratio
        BigDecimal velocityBase,          // V_base from regime lookup
        BigDecimal bodyRatio,             // Body compression ratio (or null)
        BigDecimal bodyPenaltyApplied,    // Penalty factor applied [0.5-1.0]
        BigDecimal stressThrottle,        // Stress throttle factor [0.25-1.0]
        BigDecimal finalVelocity,         // Final V = V_base × penalties
        String regime                     // "WIDE", "HEALTHY", "TIGHT", "COMPRESSED"
    ) {
        /**
         * Get summary string.
         */
        public String getSummary() {
            return String.format(
                "Regime=%s (Range/ATR=%.2f), V_base=%.2f, StressThrottle=%.2f, FinalV=%.2f",
                regime,
                rangeATR.doubleValue(),
                velocityBase.doubleValue(),
                stressThrottle.doubleValue(),
                finalVelocity.doubleValue()
            );
        }

        /**
         * Check if velocity throttle is significant (< 0.75).
         */
        public boolean isThrottled() {
            return finalVelocity.compareTo(new BigDecimal("0.75")) < 0;
        }
    }

    /**
     * Calculate full velocity analysis with diagnostics.
     *
     * @param candles Candle data for calculations
     * @param atr Average True Range
     * @param stressThrottle Stress throttle from portfolio drawdown
     * @param config MTF configuration
     * @return VelocityResult with all metrics
     */
    public static VelocityResult calculateVelocityFull(
        List<Candle> candles,
        BigDecimal atr,
        BigDecimal stressThrottle,
        MtfGlobalConfig config
    ) {
        // Calculate Range/ATR
        BigDecimal rangeATR = calculateRangeATR(
            candles,
            config.rangeLookbackBars(),
            atr
        );

        // Get V_base from regime
        BigDecimal vBase = getVelocityBase(rangeATR, config);

        // Calculate body ratio (optional)
        BigDecimal bodyRatio = calculateBodyRatio(candles, atr, 15);

        // Apply body penalty
        BigDecimal vBaseWithPenalty = applyBodyRatioPenalty(vBase, bodyRatio, config);
        BigDecimal bodyPenalty = vBase.compareTo(ZERO) > 0
            ? vBaseWithPenalty.divide(vBase, 6, RoundingMode.HALF_UP)
            : ONE;

        // Calculate final velocity
        BigDecimal finalV = vBaseWithPenalty.multiply(stressThrottle);
        BigDecimal minV = new BigDecimal("0.0625");
        finalV = finalV.max(minV);

        // Determine regime
        String regime;
        if (rangeATR.compareTo(config.rangeAtrThresholdWide()) >= 0) {
            regime = "WIDE";
        } else if (rangeATR.compareTo(config.rangeAtrThresholdHealthy()) >= 0) {
            regime = "HEALTHY";
        } else if (rangeATR.compareTo(config.rangeAtrThresholdTight()) >= 0) {
            regime = "TIGHT";
        } else {
            regime = "COMPRESSED";
        }

        return new VelocityResult(
            rangeATR,
            vBase,
            bodyRatio,
            bodyPenalty,
            stressThrottle,
            finalV,
            regime
        );
    }
}
