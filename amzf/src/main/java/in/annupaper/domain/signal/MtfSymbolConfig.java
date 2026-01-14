package in.annupaper.domain.signal;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Symbol-specific MTF configuration overrides.
 * All fields are nullable - NULL means "use global default".
 */
public record MtfSymbolConfig(
    String symbolConfigId,
    String symbol,
    String userBrokerId,

    // All fields nullable - NULL = use global

    // HTF Config
    Integer htfCandleCount,
    Integer htfCandleMinutes,
    BigDecimal htfWeight,

    // ITF Config
    Integer itfCandleCount,
    Integer itfCandleMinutes,
    BigDecimal itfWeight,

    // LTF Config
    Integer ltfCandleCount,
    Integer ltfCandleMinutes,
    BigDecimal ltfWeight,

    // Zone Detection
    BigDecimal buyZonePct,

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

    // Kelly Sizing
    BigDecimal kellyFraction,
    BigDecimal maxKellyMultiplier,

    // Entry Pricing
    Boolean useLimitOrders,
    BigDecimal entryOffsetPct,

    // Exit Targets
    BigDecimal minProfitPct,
    BigDecimal targetRMultiple,
    BigDecimal stretchRMultiple,
    Boolean useTrailingStop,
    BigDecimal trailingStopActivationPct,
    BigDecimal trailingStopDistancePct,

    // Timestamps
    Instant createdAt,
    Instant updatedAt
) {
    /**
     * Resolve effective configuration by merging symbol overrides with global defaults.
     * Returns a complete MtfGlobalConfig with all non-null symbol values taking precedence.
     */
    public MtfGlobalConfig resolveEffective(MtfGlobalConfig global) {
        return new MtfGlobalConfig(
            "RESOLVED",

            // HTF
            htfCandleCount != null ? htfCandleCount : global.htfCandleCount(),
            htfCandleMinutes != null ? htfCandleMinutes : global.htfCandleMinutes(),
            htfWeight != null ? htfWeight : global.htfWeight(),

            // ITF
            itfCandleCount != null ? itfCandleCount : global.itfCandleCount(),
            itfCandleMinutes != null ? itfCandleMinutes : global.itfCandleMinutes(),
            itfWeight != null ? itfWeight : global.itfWeight(),

            // LTF
            ltfCandleCount != null ? ltfCandleCount : global.ltfCandleCount(),
            ltfCandleMinutes != null ? ltfCandleMinutes : global.ltfCandleMinutes(),
            ltfWeight != null ? ltfWeight : global.ltfWeight(),

            // Zone
            buyZonePct != null ? buyZonePct : global.buyZonePct(),
            global.htfBuyZonePct(),
            global.itfBuyZonePct(),
            global.ltfBuyZonePct(),

            // Confluence
            minConfluenceType != null ? minConfluenceType : global.minConfluenceType(),
            strengthThresholdVeryStrong != null ? strengthThresholdVeryStrong : global.strengthThresholdVeryStrong(),
            strengthThresholdStrong != null ? strengthThresholdStrong : global.strengthThresholdStrong(),
            strengthThresholdModerate != null ? strengthThresholdModerate : global.strengthThresholdModerate(),
            multiplierVeryStrong != null ? multiplierVeryStrong : global.multiplierVeryStrong(),
            multiplierStrong != null ? multiplierStrong : global.multiplierStrong(),
            multiplierModerate != null ? multiplierModerate : global.multiplierModerate(),
            multiplierWeak != null ? multiplierWeak : global.multiplierWeak(),

            // Log-Utility
            maxPositionLogLoss != null ? maxPositionLogLoss : global.maxPositionLogLoss(),
            maxPortfolioLogLoss != null ? maxPortfolioLogLoss : global.maxPortfolioLogLoss(),
            global.maxSymbolLogLoss(),

            // Kelly
            kellyFraction != null ? kellyFraction : global.kellyFraction(),
            maxKellyMultiplier != null ? maxKellyMultiplier : global.maxKellyMultiplier(),

            // Entry
            useLimitOrders != null ? useLimitOrders : global.useLimitOrders(),
            entryOffsetPct != null ? entryOffsetPct : global.entryOffsetPct(),

            // Exit
            minProfitPct != null ? minProfitPct : global.minProfitPct(),
            targetRMultiple != null ? targetRMultiple : global.targetRMultiple(),
            stretchRMultiple != null ? stretchRMultiple : global.stretchRMultiple(),
            useTrailingStop != null ? useTrailingStop : global.useTrailingStop(),
            trailingStopActivationPct != null ? trailingStopActivationPct : global.trailingStopActivationPct(),
            trailingStopDistancePct != null ? trailingStopDistancePct : global.trailingStopDistancePct(),

            // Averaging Re-Entry Gates
            global.minReentrySpacingAtrMultiplier(),

            // Velocity Throttling (always from global, not symbol-specific)
            global.rangeAtrThresholdWide(),
            global.rangeAtrThresholdHealthy(),
            global.rangeAtrThresholdTight(),
            global.velocityMultiplierWide(),
            global.velocityMultiplierHealthy(),
            global.velocityMultiplierTight(),
            global.velocityMultiplierCompressed(),
            global.bodyRatioThresholdLow(),
            global.bodyRatioThresholdCritical(),
            global.bodyRatioPenaltyLow(),
            global.bodyRatioPenaltyCritical(),
            global.rangeLookbackBars(),
            global.stressThrottleEnabled(),
            global.maxStressDrawdown(),

            // Utility Asymmetry Gate (always from global, not symbol-specific)
            global.utilityAlpha(),
            global.utilityBeta(),
            global.utilityLambda(),
            global.minAdvantageRatio(),
            global.utilityGateEnabled(),

            // Timestamps
            global.createdAt(),
            global.updatedAt()
        );
    }
}
