package in.annupaper.service.signal;

import in.annupaper.domain.model.HistoricalCandle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * ATR Calculator - Calculate Average True Range from candle data.
 *
 * Constitutional Usage:
 * 1. Averaging Gate Spacing: P_near - P_new ≥ N × ATR
 * 2. Velocity Calculation: Range_ATR = (High - Low) / ATR
 *
 * ATR Source:
 * - DAILY candles only (not intraday)
 * - Yesterday's value (static during trading day)
 * - Updated once per day after market close
 *
 * Calculation Method:
 * - Wilder's smoothing: ATR_t = ((ATR_{t-1} × (n-1)) + TR_t) / n
 * - True Range: TR = max(H-L, |H-PC|, |L-PC|)
 */
public final class ATRCalculator {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /**
     * Calculate ATR using Wilder's smoothing method from daily candles.
     *
     * Returns yesterday's ATR value (most recent complete day).
     * Requires at least (period + 1) candles for accurate calculation.
     *
     * @param candles Daily candles in chronological order (oldest first)
     * @param period  ATR period (typically 14)
     * @return ATR value, or null if insufficient data
     */
    public static BigDecimal calculateDailyATR(List<HistoricalCandle> candles, int period) {
        if (candles == null || candles.isEmpty()) {
            return null;
        }

        if (period <= 0) {
            throw new IllegalArgumentException("ATR period must be positive: " + period);
        }

        // Need at least (period + 1) candles: period for initial ATR, +1 for previous
        // close
        if (candles.size() < period + 1) {
            return null;
        }

        // Step 1: Calculate initial ATR as simple average of first 'period' TRs
        BigDecimal sumTR = ZERO;

        for (int i = 1; i <= period; i++) {
            HistoricalCandle current = candles.get(i);
            HistoricalCandle previous = candles.get(i - 1);

            BigDecimal tr = calculateTrueRange(current, previous);
            sumTR = sumTR.add(tr);
        }

        BigDecimal atr = sumTR.divide(new BigDecimal(period), 6, RoundingMode.HALF_UP);

        // Step 2: Apply Wilder's smoothing for remaining candles
        // ATR_t = ((ATR_{t-1} × (n-1)) + TR_t) / n
        for (int i = period + 1; i < candles.size(); i++) {
            HistoricalCandle current = candles.get(i);
            HistoricalCandle previous = candles.get(i - 1);

            BigDecimal tr = calculateTrueRange(current, previous);

            // Wilder's smoothing
            BigDecimal prevWeight = atr.multiply(new BigDecimal(period - 1));
            atr = prevWeight.add(tr).divide(new BigDecimal(period), 6, RoundingMode.HALF_UP);
        }

        return atr;
    }

    /**
     * Calculate True Range for a candle.
     *
     * TR = max(H - L, |H - PC|, |L - PC|)
     *
     * Where:
     * - H = current high
     * - L = current low
     * - PC = previous close
     *
     * @param current  Current candle
     * @param previous Previous candle
     * @return True Range value
     */
    public static BigDecimal calculateTrueRange(HistoricalCandle current, HistoricalCandle previous) {
        if (current == null || previous == null) {
            throw new IllegalArgumentException("Candles cannot be null");
        }

        BigDecimal high = current.high();
        BigDecimal low = current.low();
        BigDecimal prevClose = previous.close();

        // TR component 1: H - L
        BigDecimal highLow = high.subtract(low);

        // TR component 2: |H - PC|
        BigDecimal highPrevClose = high.subtract(prevClose).abs();

        // TR component 3: |L - PC|
        BigDecimal lowPrevClose = low.subtract(prevClose).abs();

        // TR = max of three components
        return highLow.max(highPrevClose).max(lowPrevClose);
    }

    /**
     * Calculate Simple Moving Average of True Range (for initialization).
     *
     * Used as initial ATR value before applying Wilder's smoothing.
     *
     * @param candles Candles to calculate from
     * @param start   Start index (inclusive)
     * @param period  Number of candles to average
     * @return Average TR over period
     */
    public static BigDecimal calculateSimpleTRAverage(List<HistoricalCandle> candles, int start, int period) {
        if (candles == null || candles.size() < start + period) {
            return null;
        }

        BigDecimal sum = ZERO;

        for (int i = start; i < start + period; i++) {
            HistoricalCandle current = candles.get(i);
            HistoricalCandle previous = candles.get(i - 1);

            BigDecimal tr = calculateTrueRange(current, previous);
            sum = sum.add(tr);
        }

        return sum.divide(new BigDecimal(period), 6, RoundingMode.HALF_UP);
    }

    /**
     * Validate that candles are sufficient for ATR calculation.
     *
     * @param candles Candle list
     * @param period  ATR period
     * @return True if sufficient candles available
     */
    public static boolean hasSufficientData(List<HistoricalCandle> candles, int period) {
        return candles != null && candles.size() >= period + 1;
    }

    /**
     * Calculate ATR with fallback to simple TR average if insufficient data.
     *
     * Useful for early-stage symbols with limited history.
     *
     * @param candles        Daily candles
     * @param period         ATR period
     * @param fallbackPeriod Minimum period for fallback (e.g., 5)
     * @return ATR value, or null if even fallback fails
     */
    public static BigDecimal calculateDailyATRWithFallback(
            List<HistoricalCandle> candles,
            int period,
            int fallbackPeriod) {
        // Try full ATR calculation
        BigDecimal atr = calculateDailyATR(candles, period);
        if (atr != null) {
            return atr;
        }

        // Fallback to simple TR average if we have at least fallbackPeriod candles
        if (candles != null && candles.size() >= fallbackPeriod + 1) {
            return calculateSimpleTRAverage(candles, 1, Math.min(fallbackPeriod, candles.size() - 1));
        }

        return null;
    }

    /**
     * Result of ATR calculation with diagnostics.
     */
    public record ATRResult(
            BigDecimal atr, // ATR value
            int periodUsed, // Period used for calculation
            int candlesUsed, // Number of candles used
            boolean isFallback, // Whether fallback method was used
            String calculationMethod // "WILDER" or "SIMPLE_TR_AVERAGE"
    ) {
        /**
         * Get summary string.
         */
        public String getSummary() {
            return String.format(
                    "ATR=%.4f (period=%d, candles=%d, method=%s%s)",
                    atr.doubleValue(),
                    periodUsed,
                    candlesUsed,
                    calculationMethod,
                    isFallback ? ", FALLBACK" : "");
        }
    }

    /**
     * Calculate ATR with full diagnostics.
     *
     * @param candles Daily candles
     * @param period  ATR period
     * @return ATRResult with calculation details
     */
    public static ATRResult calculateWithDiagnostics(List<HistoricalCandle> candles, int period) {
        if (candles == null || candles.isEmpty()) {
            return null;
        }

        BigDecimal atr = calculateDailyATR(candles, period);

        if (atr != null) {
            return new ATRResult(
                    atr,
                    period,
                    candles.size(),
                    false,
                    "WILDER");
        }

        // Try fallback
        int fallbackPeriod = Math.min(5, candles.size() - 1);
        BigDecimal fallbackATR = calculateDailyATRWithFallback(candles, period, fallbackPeriod);

        if (fallbackATR != null) {
            return new ATRResult(
                    fallbackATR,
                    fallbackPeriod,
                    candles.size(),
                    true,
                    "SIMPLE_TR_AVERAGE");
        }

        return null;
    }
}
