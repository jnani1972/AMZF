package in.annupaper.domain.signal;

import java.math.BigDecimal;

/**
 * Confluence strength based on score (lower score = stronger confluence).
 * Score is the weighted average of buy zone scores across timeframes.
 */
public enum ConfluenceStrength {
    NONE(1.0, BigDecimal.ZERO),           // No confluence
    WEAK(0.85, new BigDecimal("0.6")),     // score > 0.65
    MODERATE(0.65, new BigDecimal("0.8")), // score > 0.45
    STRONG(0.45, BigDecimal.ONE),          // score > 0.25
    VERY_STRONG(0.25, new BigDecimal("1.2")); // score <= 0.25
    
    private final double threshold;
    private final BigDecimal multiplier;
    
    ConfluenceStrength(double threshold, BigDecimal multiplier) {
        this.threshold = threshold;
        this.multiplier = multiplier;
    }
    
    /**
     * Upper threshold for this strength level.
     * Score must be <= threshold to qualify.
     */
    public double getThreshold() {
        return threshold;
    }
    
    /**
     * Position size multiplier based on strength.
     * VERY_STRONG = 1.2x, STRONG = 1.0x, MODERATE = 0.8x, WEAK = 0.6x
     */
    public BigDecimal getMultiplier() {
        return multiplier;
    }
    
    /**
     * Determine strength from confluence score.
     * Lower score = closer to floor = stronger confluence.
     */
    public static ConfluenceStrength fromScore(double score) {
        if (score <= 0.25) return VERY_STRONG;
        if (score <= 0.45) return STRONG;
        if (score <= 0.65) return MODERATE;
        if (score <= 0.85) return WEAK;
        return NONE;
    }
    
    /**
     * Check if this strength meets minimum requirement for entry.
     */
    public boolean meetsMinimum(ConfluenceStrength minimum) {
        return this.ordinal() >= minimum.ordinal();
    }
}
