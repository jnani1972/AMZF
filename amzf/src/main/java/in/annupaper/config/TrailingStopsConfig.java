package in.annupaper.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration for trailing stop exit strategy.
 *
 * Controls when and how trailing stops activate and move.
 * Used by ExitSignalService for dynamic stop-loss management.
 */
public record TrailingStopsConfig(
    @JsonProperty("activationPercent")
    double activationPercent,      // % profit before trailing stop activates (e.g., 1.0 = 1%)

    @JsonProperty("trailingPercent")
    double trailingPercent,         // % distance from highest price (e.g., 0.5 = 0.5%)

    @JsonProperty("updateFrequency")
    String updateFrequency,         // TICK | BRICK | CANDLE

    @JsonProperty("minMovePercent")
    double minMovePercent,          // Minimum % move to update stop (e.g., 0.1 = 0.1%)

    @JsonProperty("maxLossPercent")
    double maxLossPercent,          // Maximum % loss allowed (e.g., 2.0 = 2%)

    @JsonProperty("lockProfitPercent")
    double lockProfitPercent        // % profit at which to lock in gains (e.g., 3.0 = 3%)
) {
    /**
     * Default configuration with conservative settings.
     */
    public static TrailingStopsConfig defaults() {
        return new TrailingStopsConfig(
            1.0,    // Activate after 1% profit
            0.5,    // Trail 0.5% below highest
            "TICK", // Update on every tick
            0.1,    // Update if price moves 0.1%
            2.0,    // Max 2% loss
            3.0     // Lock profit at 3%
        );
    }

    /**
     * Validate configuration values.
     */
    public boolean isValid() {
        return activationPercent > 0 && activationPercent <= 100
            && trailingPercent > 0 && trailingPercent <= 100
            && minMovePercent >= 0 && minMovePercent <= 100
            && maxLossPercent > 0 && maxLossPercent <= 100
            && lockProfitPercent > 0 && lockProfitPercent <= 100
            && (updateFrequency.equals("TICK")
                || updateFrequency.equals("BRICK")
                || updateFrequency.equals("CANDLE"));
    }
}
