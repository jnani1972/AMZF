package in.annupaper.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Global MTF configuration with all 54 configuration fields.
 * This represents the default configuration used across all symbols.
 */
public record MtfGlobalConfig(
        String configId,

        // HTF (Higher Timeframe) Config
        int htfCandleCount,
        int htfCandleMinutes,
        BigDecimal htfWeight,

        // ITF (Intermediate Timeframe) Config
        int itfCandleCount,
        int itfCandleMinutes,
        BigDecimal itfWeight,

        // LTF (Lower Timeframe) Config
        int ltfCandleCount,
        int ltfCandleMinutes,
        BigDecimal ltfWeight,

        // Zone Detection
        BigDecimal buyZonePct,
        BigDecimal htfBuyZonePct,
        BigDecimal itfBuyZonePct,
        BigDecimal ltfBuyZonePct,

        // Confluence Settings
        String minConfluenceType,
        BigDecimal strengthThresholdVeryStrong,
        BigDecimal strengthThresholdStrong,
        BigDecimal strengthThresholdModerate,
        BigDecimal multiplierVeryStrong,
        BigDecimal multiplierStrong,
        BigDecimal multiplierModerate,
        BigDecimal multiplierWeak,

        // Log-Utility Constraints
        BigDecimal maxPositionLogLoss,
        BigDecimal maxPortfolioLogLoss,
        BigDecimal maxSymbolLogLoss,

        // Kelly Sizing
        BigDecimal kellyFraction,
        BigDecimal maxKellyMultiplier,

        // Entry Pricing
        boolean useLimitOrders,
        BigDecimal entryOffsetPct,

        // Exit Targets
        BigDecimal minProfitPct,
        BigDecimal targetRMultiple,
        BigDecimal stretchRMultiple,
        boolean useTrailingStop,
        BigDecimal trailingStopActivationPct,
        BigDecimal trailingStopDistancePct,

        // Averaging Re-Entry Gates
        BigDecimal minReentrySpacingAtrMultiplier,

        // Velocity Throttling - Range/ATR Regime Thresholds
        BigDecimal rangeAtrThresholdWide,
        BigDecimal rangeAtrThresholdHealthy,
        BigDecimal rangeAtrThresholdTight,

        // Velocity Throttling - Base Multipliers (Discrete Regime Buckets)
        BigDecimal velocityMultiplierWide,
        BigDecimal velocityMultiplierHealthy,
        BigDecimal velocityMultiplierTight,
        BigDecimal velocityMultiplierCompressed,

        // Velocity Throttling - Body Ratio Brake (Penalty Only, Never Amplify)
        BigDecimal bodyRatioThresholdLow,
        BigDecimal bodyRatioThresholdCritical,
        BigDecimal bodyRatioPenaltyLow,
        BigDecimal bodyRatioPenaltyCritical,

        // Velocity Throttling - Range Calculation
        int rangeLookbackBars,

        // Velocity Throttling - Stress Control
        boolean stressThrottleEnabled,
        BigDecimal maxStressDrawdown,

        // Utility Asymmetry Gate - Piecewise Utility in Log-Return Space
        BigDecimal utilityAlpha, // α ∈ [0.40, 0.80]: upside concavity (default 0.60)
        BigDecimal utilityBeta, // β ∈ [1.10, 2.00]: downside convexity (default 1.40)
        BigDecimal utilityLambda, // λ ∈ [1.00, 3.00]: loss aversion (default 1.00)
        BigDecimal minAdvantageRatio, // Minimum p·U(π) / (1-p)·|U(ℓ)| (default 3.0)
        boolean utilityGateEnabled, // Enable 3× advantage gate (default true)

        // Timestamps
        Instant createdAt,
        Instant updatedAt) {
    /**
     * Get confluence multiplier based on composite score.
     */
    public BigDecimal getConfluenceMultiplier(BigDecimal compositeScore) {
        if (compositeScore.compareTo(strengthThresholdVeryStrong) >= 0) {
            return multiplierVeryStrong; // 1.2x
        }
        if (compositeScore.compareTo(strengthThresholdStrong) >= 0) {
            return multiplierStrong; // 1.0x
        }
        if (compositeScore.compareTo(strengthThresholdModerate) >= 0) {
            return multiplierModerate; // 0.75x
        }
        return multiplierWeak; // 0.5x
    }

    /**
     * Get confluence strength label.
     */
    public String getConfluenceStrength(BigDecimal compositeScore) {
        if (compositeScore.compareTo(strengthThresholdVeryStrong) >= 0) {
            return "VERY_STRONG";
        }
        if (compositeScore.compareTo(strengthThresholdStrong) >= 0) {
            return "STRONG";
        }
        if (compositeScore.compareTo(strengthThresholdModerate) >= 0) {
            return "MODERATE";
        }
        return "WEAK";
    }
}
