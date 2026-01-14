package in.annupaper.service.signal;

import in.annupaper.domain.data.Candle;
import in.annupaper.domain.signal.MtfGlobalConfig;
import in.annupaper.domain.trade.Trade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MTF Position Sizer - Calculates position size with all constraints applied.
 *
 * Applies multiple constraints in priority order:
 * 1. Log-utility constraint (position level)
 * 2. Kelly sizing with confluence multiplier
 * 3. Fill probability weighting
 * 4. Capital availability
 * 5. Portfolio log-loss budget (constitutional)
 * 6. Symbol log-loss budget (constitutional)
 *
 * Returns the MINIMUM of all constraints to ensure safety.
 */
public final class MtfPositionSizer {

    /**
     * Calculate position size for a buy zone entry.
     *
     * @param zonePrice Price within buy zone to enter at
     * @param effectiveFloor Strictest floor (max of HTF/ITF/LTF floors)
     * @param effectiveCeiling Most conservative ceiling (min of HTF/ITF/LTF ceilings)
     * @param pWin Probability of hitting ceiling before floor
     * @param pFill Probability of order getting filled
     * @param kelly Full Kelly fraction (signal edge/strength only, K)
     * @param confluenceMultiplier Multiplier based on confluence strength (0.5x to 1.2x)
     * @param capSym Capital allocated for this symbol
     * @param existingQty Existing position quantity
     * @param existingAvg Existing position average cost
     * @param openTrades All open trades for portfolio/symbol risk calculation
     * @param symbol Symbol being traded
     * @param candles Candle data for velocity calculation (Range/ATR)
     * @param atr Average True Range for velocity calculation
     * @param currentPortfolioValue Current portfolio value for stress calculation
     * @param peakPortfolioValue Peak portfolio value for stress calculation
     * @param config MTF configuration
     * @return PositionSizeResult with final quantity and constraint details
     */
    public static PositionSizeResult calculatePositionSize(
        BigDecimal zonePrice,
        BigDecimal effectiveFloor,
        BigDecimal effectiveCeiling,
        BigDecimal pWin,
        BigDecimal pFill,
        BigDecimal kelly,
        BigDecimal confluenceMultiplier,
        BigDecimal capSym,
        int existingQty,
        BigDecimal existingAvg,
        List<Trade> openTrades,
        String symbol,
        List<Candle> candles,
        BigDecimal atr,
        BigDecimal currentPortfolioValue,
        BigDecimal peakPortfolioValue,
        MtfGlobalConfig config
    ) {
        // CONSTITUTIONAL GATE 1: Utility Asymmetry (3× Advantage) - PRE-CHECK
        // Must pass BEFORE sizing calculations
        if (config.utilityGateEnabled()) {
            boolean passesUtilityGate = UtilityAsymmetryCalculator.passesAdvantageGate(
                pWin,
                // π = ln(ceiling/entry) - upside log return
                BigDecimal.valueOf(Math.log(
                    effectiveCeiling.divide(zonePrice, 6, RoundingMode.HALF_UP).doubleValue()
                )),
                // ℓ = ln(floor/entry) - downside log return
                BigDecimal.valueOf(Math.log(
                    effectiveFloor.divide(zonePrice, 6, RoundingMode.HALF_UP).doubleValue()
                )),
                config
            );

            if (!passesUtilityGate) {
                // REJECT: Insufficient utility advantage (< 3×)
                return createRejectedResult(zonePrice, "UTILITY_GATE_FAILED");
            }
        }

        Map<String, Integer> constraints = new HashMap<>();

        // Constraint 1: Log-utility (position level)
        int maxLogSafe = LogUtilityCalculator.calculateMaxLogSafeQty(
            zonePrice,
            effectiveFloor,
            config.maxPositionLogLoss(),
            existingQty,
            existingAvg,
            capSym
        );
        constraints.put("LOG_SAFE_POSITION", maxLogSafe);

        // CONSTITUTIONAL VELOCITY THROTTLE
        // Formula: V = V_base(Range_ATR) × g(stress)
        // Then: K_eff = K × V
        BigDecimal velocityThrottle = BigDecimal.ONE;  // Default: no throttle
        BigDecimal stressThrottle = BigDecimal.ONE;    // Default: no stress

        if (config.stressThrottleEnabled()) {
            // Calculate stress throttle from portfolio drawdown
            stressThrottle = PortfolioRiskCalculator.calculateStressThrottle(
                PortfolioRiskCalculator.calculatePortfolioDrawdown(
                    openTrades,
                    currentPortfolioValue,
                    peakPortfolioValue
                ),
                config.maxStressDrawdown()
            );

            // Calculate velocity throttle: V = V_base × g(stress)
            if (candles != null && !candles.isEmpty() && atr != null && atr.compareTo(BigDecimal.ZERO) > 0) {
                velocityThrottle = VelocityCalculator.calculateFinalVelocity(
                    VelocityCalculator.calculateRangeATR(candles, config.rangeLookbackBars(), atr),
                    VelocityCalculator.calculateBodyRatio(candles, atr, 15),
                    stressThrottle,
                    config
                );
            } else {
                // No candle data → use stress throttle only
                velocityThrottle = stressThrottle;
            }
        }

        // Apply velocity throttle: K_eff = K × V (local only, doesn't mutate kelly)
        BigDecimal kellyEffective = kelly.multiply(velocityThrottle);

        // Constraint 2: Kelly sizing with confluence multiplier and velocity throttle
        // kelly_qty = (capital * K_eff * kelly_fraction * confluence_multiplier) / price
        BigDecimal kellyCapital = capSym
            .multiply(kellyEffective)
            .multiply(config.kellyFraction())
            .multiply(confluenceMultiplier)
            .multiply(config.maxKellyMultiplier());

        int kellyQty = kellyCapital
            .divide(zonePrice, 0, RoundingMode.DOWN)
            .intValue();
        constraints.put("KELLY_SIZED", kellyQty);

        // Constraint 3: Fill probability weighted
        // Reduce kelly qty by fill probability
        int fillWeightedQty = new BigDecimal(kellyQty)
            .multiply(pFill)
            .setScale(0, RoundingMode.DOWN)
            .intValue();
        constraints.put("FILL_WEIGHTED", fillWeightedQty);

        // Constraint 4: Capital availability
        // How much capital remains after existing position?
        BigDecimal existingCapitalUsed = existingAvg
            .multiply(new BigDecimal(existingQty));
        BigDecimal remainingCapital = capSym.subtract(existingCapitalUsed).max(BigDecimal.ZERO);

        int maxCapitalQty = remainingCapital
            .divide(zonePrice, 0, RoundingMode.DOWN)
            .intValue();
        constraints.put("CAPITAL_AVAILABLE", maxCapitalQty);

        // Constraint 5: Portfolio log-loss budget (constitutional)
        // Calculate per-leg log loss: ℓ = ln(S / P)
        BigDecimal perLegLogLoss = LogUtilityCalculator.calculatePerLegLogLoss(zonePrice, effectiveFloor);

        // Calculate portfolio headroom: e_max = (L_port - R_port) / ℓ
        BigDecimal currentPortfolioLogLoss = PortfolioRiskCalculator.calculateCurrentPortfolioLogLoss(openTrades);
        BigDecimal portfolioHeadroom = PortfolioRiskCalculator.calculatePortfolioHeadroom(
            currentPortfolioLogLoss,
            config.maxPortfolioLogLoss(),
            perLegLogLoss
        );

        // Convert exposure weight to quantity
        int portfolioQty = PortfolioRiskCalculator.convertExposureToQty(
            portfolioHeadroom,
            capSym,
            zonePrice
        );
        constraints.put("PORTFOLIO_BUDGET", portfolioQty);

        // Constraint 6: Symbol log-loss budget (constitutional)
        // Calculate symbol headroom: e_max = (L_sym - R_sym) / ℓ
        BigDecimal currentSymbolLogLoss = PortfolioRiskCalculator.calculateSymbolLogLoss(openTrades, symbol);
        BigDecimal symbolHeadroom = PortfolioRiskCalculator.calculateSymbolHeadroom(
            currentSymbolLogLoss,
            config.maxSymbolLogLoss(),
            perLegLogLoss
        );

        // Convert exposure weight to quantity
        int symbolQty = PortfolioRiskCalculator.convertExposureToQty(
            symbolHeadroom,
            capSym,
            zonePrice
        );
        constraints.put("SYMBOL_BUDGET", symbolQty);

        // Apply minimum of all constraints
        int finalQty = constraints.values().stream()
            .min(Integer::compareTo)
            .orElse(0);

        // Determine which constraint was limiting
        String limitingConstraint = constraints.entrySet().stream()
            .filter(e -> e.getValue() == finalQty)
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse("UNKNOWN");

        // Calculate total cost
        BigDecimal totalCost = zonePrice.multiply(new BigDecimal(finalQty));

        // Calculate new average cost
        BigDecimal newAvgCost = BigDecimal.ZERO;
        if (existingQty + finalQty > 0) {
            BigDecimal totalPositionCost = existingAvg.multiply(new BigDecimal(existingQty))
                .add(totalCost);
            newAvgCost = totalPositionCost.divide(
                new BigDecimal(existingQty + finalQty),
                6,
                RoundingMode.HALF_UP
            );
        }

        return new PositionSizeResult(
            finalQty,
            limitingConstraint,
            constraints,
            totalCost,
            newAvgCost,
            pWin,
            pFill,
            kelly,
            confluenceMultiplier
        );
    }

    /**
     * Calculate position size for adding to existing position in a lower zone.
     *
     * This is similar to initial entry but with stricter constraints since we're averaging down.
     * Validates averaging-only gates FIRST before sizing.
     *
     * @param zonePrice Price in lower zone
     * @param effectiveFloor Updated floor
     * @param effectiveCeiling Ceiling
     * @param existingQty Current quantity
     * @param existingAvg Current average
     * @param capSym Symbol capital
     * @param openTrades All open trades for averaging gate validation
     * @param symbol Symbol being traded
     * @param candles Candle data for velocity calculation
     * @param atr Daily ATR value (from yesterday)
     * @param currentPortfolioValue Current portfolio value
     * @param peakPortfolioValue Peak portfolio value
     * @param config MTF config
     * @return Position size for adding (quantity=0 if averaging gates fail)
     */
    public static PositionSizeResult calculateAddSize(
        BigDecimal zonePrice,
        BigDecimal effectiveFloor,
        BigDecimal effectiveCeiling,
        int existingQty,
        BigDecimal existingAvg,
        BigDecimal capSym,
        List<Trade> openTrades,
        String symbol,
        List<Candle> candles,
        BigDecimal atr,
        BigDecimal currentPortfolioValue,
        BigDecimal peakPortfolioValue,
        MtfGlobalConfig config
    ) {
        // CONSTITUTIONAL GATE: Validate averaging-only discipline FIRST
        // Get symbol trades and find P_near
        List<Trade> symbolTrades = AveragingGateValidator.getSymbolTrades(openTrades, symbol);

        if (!symbolTrades.isEmpty()) {
            BigDecimal nearestEntry = AveragingGateValidator.findNearestEntry(symbolTrades, zonePrice);

            if (nearestEntry != null) {
                // Validate both gates: P_new ≤ P_near AND P_near - P_new ≥ N × ATR
                boolean passesGates = AveragingGateValidator.passesAveragingGates(
                    zonePrice,
                    nearestEntry,
                    atr,
                    config.minReentrySpacingAtrMultiplier()
                );

                if (!passesGates) {
                    // REJECT: Return zero-quantity result with rejection marker
                    return createRejectedResult(zonePrice, "AVERAGING_GATE_FAILED");
                }
            }
        }

        // Recalculate P(win) and Kelly with new price
        BigDecimal maxDrop = new BigDecimal("0.02");  // Default 2% - should be passed in

        BigDecimal pWin = KellyCalculator.calculatePWin(
            zonePrice, effectiveFloor, effectiveCeiling, maxDrop
        );

        BigDecimal kelly = KellyCalculator.calculateKelly(
            zonePrice, effectiveFloor, effectiveCeiling, pWin
        );

        // Use reduced confluence multiplier for adding (0.75x of original)
        BigDecimal confluenceMultiplier = new BigDecimal("0.75");

        // Higher fill probability when averaging down (market has moved in our direction)
        BigDecimal pFill = new BigDecimal("0.95");

        return calculatePositionSize(
            zonePrice,
            effectiveFloor,
            effectiveCeiling,
            pWin,
            pFill,
            kelly,
            confluenceMultiplier,
            capSym,
            existingQty,
            existingAvg,
            openTrades,
            symbol,
            candles,
            atr,
            currentPortfolioValue,
            peakPortfolioValue,
            config
        );
    }

    /**
     * Create a rejected position size result (zero quantity).
     *
     * @param zonePrice Entry price attempted
     * @param rejectionReason Reason for rejection
     * @return PositionSizeResult with quantity=0
     */
    private static PositionSizeResult createRejectedResult(BigDecimal zonePrice, String rejectionReason) {
        Map<String, Integer> constraints = new HashMap<>();
        constraints.put(rejectionReason, 0);

        return new PositionSizeResult(
            0,
            rejectionReason,
            constraints,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO
        );
    }

    /**
     * Result of position sizing calculation.
     */
    public record PositionSizeResult(
        int quantity,                          // Final quantity to buy
        String limitingConstraint,             // Which constraint limited the size
        Map<String, Integer> allConstraints,   // All constraint values
        BigDecimal totalCost,                  // Total cost of this trade
        BigDecimal newAvgCost,                 // New average cost after trade
        BigDecimal pWin,                       // Probability of win
        BigDecimal pFill,                      // Probability of fill
        BigDecimal kelly,                      // Full Kelly fraction
        BigDecimal confluenceMultiplier        // Confluence multiplier applied
    ) {
        /**
         * Check if position size is valid (> 0).
         */
        public boolean isValid() {
            return quantity > 0;
        }

        /**
         * Get summary string.
         */
        public String getSummary() {
            return String.format(
                "Qty=%d (limited by %s), Cost=$%.2f, NewAvg=$%.2f, P(win)=%.2f%%, P(fill)=%.2f%%",
                quantity,
                limitingConstraint,
                totalCost.doubleValue(),
                newAvgCost.doubleValue(),
                pWin.multiply(new BigDecimal("100")).doubleValue(),
                pFill.multiply(new BigDecimal("100")).doubleValue()
            );
        }

        /**
         * Get detailed breakdown of all constraints.
         */
        public String getConstraintBreakdown() {
            StringBuilder sb = new StringBuilder();
            sb.append("Constraint Breakdown:\n");
            allConstraints.forEach((name, value) -> {
                String marker = name.equals(limitingConstraint) ? " ← LIMITING" : "";
                sb.append(String.format("  %s: %d%s\n", name, value, marker));
            });
            return sb.toString();
        }

        /**
         * Calculate risk-reward ratio.
         */
        public BigDecimal getRiskRewardRatio() {
            if (pWin.compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.ZERO;
            }
            BigDecimal pLoss = BigDecimal.ONE.subtract(pWin);
            return pWin.divide(pLoss, 4, RoundingMode.HALF_UP);
        }

        /**
         * Check if Kelly criterion is satisfied.
         */
        public boolean satisfiesKellyCriterion() {
            return kelly.compareTo(BigDecimal.ZERO) > 0;
        }
    }
}
