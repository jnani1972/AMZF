package in.annupaper.service.signal;

import in.annupaper.domain.model.Trade;

import java.math.BigDecimal;
import java.util.List;

/**
 * Averaging Gate Validator - Enforces averaging-only re-entry discipline.
 *
 * Constitutional Gates:
 * 1. P_new ≤ P_near (averaging only, no pyramiding)
 * 2. P_near - P_new ≥ N × ATR (minimum spacing, anti-death-spiral)
 *
 * Where:
 * - P_near = nearest existing entry price to current market price
 * - N = configurable multiplier (default: 2.0)
 * - ATR = Average True Range (calculated from DAILY candles)
 */
public final class AveragingGateValidator {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /**
     * Find nearest entry price to current price from open trades.
     *
     * P_near = argmin |P_i - P_mkt|
     *
     * If two prices are equidistant, choose the higher one (conservative).
     *
     * @param openTrades List of open trades for symbol
     * @param currentPrice Current market price
     * @return Nearest entry price, or null if no open trades
     */
    public static BigDecimal findNearestEntry(List<Trade> openTrades, BigDecimal currentPrice) {
        if (openTrades == null || openTrades.isEmpty() || currentPrice == null) {
            return null;
        }

        BigDecimal nearestEntry = null;
        BigDecimal minDistance = null;

        for (Trade trade : openTrades) {
            if (trade.entryPrice() == null) {
                continue;
            }

            BigDecimal distance = trade.entryPrice().subtract(currentPrice).abs();

            if (nearestEntry == null || distance.compareTo(minDistance) < 0) {
                nearestEntry = trade.entryPrice();
                minDistance = distance;
            } else if (distance.compareTo(minDistance) == 0) {
                // Equidistant - choose higher price (conservative)
                if (trade.entryPrice().compareTo(nearestEntry) > 0) {
                    nearestEntry = trade.entryPrice();
                }
            }
        }

        return nearestEntry;
    }

    /**
     * Check if new price passes averaging-only gates.
     *
     * Gate 1: P_new ≤ P_near (no pyramiding)
     * Gate 2: P_near - P_new ≥ multiplier × ATR (minimum spacing)
     *
     * Combined: P_new ≤ P_near - (multiplier × ATR)
     *
     * @param newPrice Candidate re-entry price
     * @param nearestEntry P_near (nearest existing entry)
     * @param atr Daily ATR value
     * @param atrMultiplier Spacing multiplier (e.g., 2.0)
     * @return True if gates pass, false if rejected
     */
    public static boolean passesAveragingGates(
        BigDecimal newPrice,
        BigDecimal nearestEntry,
        BigDecimal atr,
        BigDecimal atrMultiplier
    ) {
        if (newPrice == null || nearestEntry == null || atr == null || atrMultiplier == null) {
            return false;
        }

        if (atr.compareTo(ZERO) <= 0 || atrMultiplier.compareTo(ZERO) <= 0) {
            return false;
        }

        // Gate 1: Must be below or equal to nearest entry (averaging only)
        if (newPrice.compareTo(nearestEntry) > 0) {
            return false;  // REJECT: Attempting to pyramid
        }

        // Gate 2: Must have minimum spacing
        BigDecimal minSpacing = atr.multiply(atrMultiplier);
        BigDecimal actualSpacing = nearestEntry.subtract(newPrice);

        if (actualSpacing.compareTo(minSpacing) < 0) {
            return false;  // REJECT: Too close, death spiral risk
        }

        return true;  // PASS: Both gates satisfied
    }

    /**
     * Result of averaging gate validation with detailed diagnostics.
     */
    public record AveragingGateResult(
        boolean passesGate1,           // P_new ≤ P_near
        boolean passesGate2,           // Spacing ≥ N × ATR
        boolean passesAllGates,        // Combined result
        BigDecimal nearestEntry,       // P_near
        BigDecimal actualSpacing,      // P_near - P_new
        BigDecimal requiredSpacing,    // N × ATR
        String rejectionReason         // Why rejected (if rejected)
    ) {
        /**
         * Get summary string.
         */
        public String getSummary() {
            if (passesAllGates) {
                return String.format(
                    "PASS: Spacing %.2f >= required %.2f (P_near=%.2f)",
                    actualSpacing,
                    requiredSpacing,
                    nearestEntry
                );
            } else {
                return String.format(
                    "REJECT: %s (spacing %.2f < required %.2f, P_near=%.2f)",
                    rejectionReason,
                    actualSpacing,
                    requiredSpacing,
                    nearestEntry
                );
            }
        }
    }

    /**
     * Validate averaging gates with full diagnostics.
     *
     * @param newPrice Candidate re-entry price
     * @param nearestEntry P_near
     * @param atr Daily ATR
     * @param atrMultiplier Spacing multiplier
     * @return AveragingGateResult with detailed diagnostics
     */
    public static AveragingGateResult validateWithDiagnostics(
        BigDecimal newPrice,
        BigDecimal nearestEntry,
        BigDecimal atr,
        BigDecimal atrMultiplier
    ) {
        if (newPrice == null || nearestEntry == null || atr == null || atrMultiplier == null) {
            return new AveragingGateResult(
                false, false, false, nearestEntry,
                ZERO, ZERO, "Missing parameters"
            );
        }

        boolean gate1 = newPrice.compareTo(nearestEntry) <= 0;
        BigDecimal requiredSpacing = atr.multiply(atrMultiplier);
        BigDecimal actualSpacing = nearestEntry.subtract(newPrice);
        boolean gate2 = actualSpacing.compareTo(requiredSpacing) >= 0;

        boolean allPass = gate1 && gate2;
        String reason = null;

        if (!gate1) {
            reason = "Pyramiding not allowed (P_new > P_near)";
        } else if (!gate2) {
            reason = "Insufficient spacing (death spiral risk)";
        }

        return new AveragingGateResult(
            gate1,
            gate2,
            allPass,
            nearestEntry,
            actualSpacing,
            requiredSpacing,
            reason
        );
    }

    /**
     * Check if symbol has any open positions.
     *
     * @param openTrades All open trades
     * @param symbol Symbol to check
     * @return True if symbol has open positions
     */
    public static boolean hasOpenPositions(List<Trade> openTrades, String symbol) {
        if (openTrades == null || openTrades.isEmpty() || symbol == null) {
            return false;
        }

        for (Trade trade : openTrades) {
            if (symbol.equals(trade.symbol())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get all open trades for a specific symbol.
     *
     * @param openTrades All open trades
     * @param symbol Symbol to filter by
     * @return List of trades for symbol (may be empty)
     */
    public static List<Trade> getSymbolTrades(List<Trade> openTrades, String symbol) {
        if (openTrades == null || symbol == null) {
            return List.of();
        }

        return openTrades.stream()
            .filter(trade -> symbol.equals(trade.symbol()))
            .toList();
    }
}
