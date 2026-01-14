package in.annupaper.service.signal;

import in.annupaper.domain.user.Portfolio;
import in.annupaper.domain.trade.Trade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Portfolio Value Tracker - Calculate current and peak portfolio values.
 *
 * Constitutional Usage:
 * - Stress Throttle: g(stress) based on portfolio drawdown
 * - Drawdown Formula: DD% = (peak - current) / peak
 *
 * Portfolio Value Calculation:
 * Current Value = Available Capital + Market Value of Open Positions
 *
 * Where:
 * - Available Capital = Total Capital - Reserved Capital
 * - Market Value = Σ(quantity × current_price) for all open positions
 *
 * Peak Value Tracking:
 * - Peak = max(current value over all time)
 * - Updated whenever current value exceeds peak
 * - Persisted to survive restarts
 */
public final class PortfolioValueTracker {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /**
     * Calculate current portfolio value.
     *
     * Current Value = Available Capital + Market Value of Open Positions
     *
     * @param portfolio Portfolio with capital allocation
     * @param openTrades All open trades
     * @param currentPrices Map of symbol → current price
     * @return Current portfolio value
     */
    public static BigDecimal calculateCurrentValue(
        Portfolio portfolio,
        List<Trade> openTrades,
        Map<String, BigDecimal> currentPrices
    ) {
        if (portfolio == null) {
            return ZERO;
        }

        // Start with available capital
        BigDecimal currentValue = portfolio.availableCapital();

        // Add market value of all open positions
        if (openTrades != null && !openTrades.isEmpty()) {
            for (Trade trade : openTrades) {
                BigDecimal currentPrice = currentPrices.get(trade.symbol());
                if (currentPrice != null) {
                    // Market value = quantity × current price
                    BigDecimal marketValue = currentPrice.multiply(new BigDecimal(trade.entryQty()));
                    currentValue = currentValue.add(marketValue);
                }
            }
        }

        return currentValue.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate current portfolio value using single symbol price.
     *
     * Useful when only one symbol is being analyzed.
     *
     * @param portfolio Portfolio with capital allocation
     * @param openTrades All open trades
     * @param symbol Symbol being analyzed
     * @param currentPrice Current price for the symbol
     * @return Current portfolio value
     */
    public static BigDecimal calculateCurrentValueForSymbol(
        Portfolio portfolio,
        List<Trade> openTrades,
        String symbol,
        BigDecimal currentPrice
    ) {
        if (portfolio == null) {
            return ZERO;
        }

        BigDecimal currentValue = portfolio.availableCapital();

        if (openTrades != null && !openTrades.isEmpty()) {
            for (Trade trade : openTrades) {
                if (trade.symbol().equals(symbol) && currentPrice != null) {
                    // Use provided current price for this symbol
                    BigDecimal marketValue = currentPrice.multiply(new BigDecimal(trade.entryQty()));
                    currentValue = currentValue.add(marketValue);
                } else {
                    // Use entry price for other symbols (approximation)
                    BigDecimal marketValue = trade.entryPrice().multiply(new BigDecimal(trade.entryQty()));
                    currentValue = currentValue.add(marketValue);
                }
            }
        }

        return currentValue.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate portfolio drawdown percentage.
     *
     * Drawdown% = (peak - current) / peak
     *
     * Returns negative value (e.g., -0.05 for 5% drawdown).
     *
     * @param currentValue Current portfolio value
     * @param peakValue Peak portfolio value
     * @return Drawdown as negative percentage (e.g., -0.05 for 5% down)
     */
    public static BigDecimal calculateDrawdown(BigDecimal currentValue, BigDecimal peakValue) {
        if (peakValue == null || peakValue.compareTo(ZERO) <= 0) {
            return ZERO;
        }

        if (currentValue == null) {
            currentValue = ZERO;
        }

        // Drawdown = (current - peak) / peak
        // Result is negative when portfolio is below peak
        BigDecimal difference = currentValue.subtract(peakValue);
        return difference.divide(peakValue, 6, RoundingMode.HALF_UP);
    }

    /**
     * Update peak portfolio value if current exceeds peak.
     *
     * @param currentValue Current portfolio value
     * @param currentPeak Current peak value
     * @return New peak value (max of current and peak)
     */
    public static BigDecimal updatePeak(BigDecimal currentValue, BigDecimal currentPeak) {
        if (currentPeak == null || currentPeak.compareTo(ZERO) <= 0) {
            // First time: initialize peak to current
            return currentValue;
        }

        // Update peak if current exceeds it
        return currentValue.max(currentPeak);
    }

    /**
     * Calculate total market value of open positions.
     *
     * @param openTrades All open trades
     * @param currentPrices Map of symbol → current price
     * @return Total market value
     */
    public static BigDecimal calculatePositionsMarketValue(
        List<Trade> openTrades,
        Map<String, BigDecimal> currentPrices
    ) {
        if (openTrades == null || openTrades.isEmpty()) {
            return ZERO;
        }

        BigDecimal totalValue = ZERO;

        for (Trade trade : openTrades) {
            BigDecimal currentPrice = currentPrices.get(trade.symbol());
            if (currentPrice != null) {
                BigDecimal marketValue = currentPrice.multiply(new BigDecimal(trade.entryQty()));
                totalValue = totalValue.add(marketValue);
            }
        }

        return totalValue.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate unrealized PnL for all open positions.
     *
     * PnL = Market Value - Cost Basis
     *
     * @param openTrades All open trades
     * @param currentPrices Map of symbol → current price
     * @return Unrealized PnL (positive = profit, negative = loss)
     */
    public static BigDecimal calculateUnrealizedPnL(
        List<Trade> openTrades,
        Map<String, BigDecimal> currentPrices
    ) {
        if (openTrades == null || openTrades.isEmpty()) {
            return ZERO;
        }

        BigDecimal totalPnL = ZERO;

        for (Trade trade : openTrades) {
            BigDecimal currentPrice = currentPrices.get(trade.symbol());
            if (currentPrice != null) {
                // Market value = quantity × current price
                BigDecimal marketValue = currentPrice.multiply(new BigDecimal(trade.entryQty()));

                // Cost basis = quantity × entry price
                BigDecimal costBasis = trade.entryPrice().multiply(new BigDecimal(trade.entryQty()));

                // PnL = market value - cost basis
                BigDecimal pnl = marketValue.subtract(costBasis);
                totalPnL = totalPnL.add(pnl);
            }
        }

        return totalPnL.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate portfolio return percentage.
     *
     * Return% = (current - initial) / initial
     *
     * @param currentValue Current portfolio value
     * @param initialCapital Initial capital
     * @return Return percentage (e.g., 0.10 for 10% gain)
     */
    public static BigDecimal calculateReturn(BigDecimal currentValue, BigDecimal initialCapital) {
        if (initialCapital == null || initialCapital.compareTo(ZERO) <= 0) {
            return ZERO;
        }

        BigDecimal gain = currentValue.subtract(initialCapital);
        return gain.divide(initialCapital, 6, RoundingMode.HALF_UP);
    }

    /**
     * Result of portfolio value calculation with diagnostics.
     */
    public record PortfolioValueResult(
        BigDecimal currentValue,        // Current portfolio value
        BigDecimal peakValue,           // Peak portfolio value
        BigDecimal availableCapital,    // Available (unreserved) capital
        BigDecimal positionsMarketValue, // Market value of open positions
        BigDecimal unrealizedPnL,       // Unrealized profit/loss
        BigDecimal drawdown,            // Drawdown from peak (negative)
        BigDecimal portfolioReturn,     // Return from initial capital
        int openPositionCount           // Number of open positions
    ) {
        /**
         * Check if portfolio is in drawdown.
         */
        public boolean isInDrawdown() {
            return drawdown.compareTo(ZERO) < 0;
        }

        /**
         * Get drawdown as positive percentage for display.
         */
        public BigDecimal getDrawdownPercent() {
            return drawdown.abs().multiply(new BigDecimal("100"));
        }

        /**
         * Get return as percentage for display.
         */
        public BigDecimal getReturnPercent() {
            return portfolioReturn.multiply(new BigDecimal("100"));
        }

        /**
         * Get summary string.
         */
        public String getSummary() {
            return String.format(
                "Portfolio: Value=$%.2f (Peak=$%.2f), DD=%.2f%%, Return=%.2f%%, Positions=%d, PnL=$%.2f",
                currentValue.doubleValue(),
                peakValue.doubleValue(),
                getDrawdownPercent().doubleValue(),
                getReturnPercent().doubleValue(),
                openPositionCount,
                unrealizedPnL.doubleValue()
            );
        }
    }

    /**
     * Calculate complete portfolio value analysis.
     *
     * @param portfolio Portfolio with capital allocation
     * @param openTrades All open trades
     * @param currentPrices Map of symbol → current price
     * @param peakValue Current peak value (will be updated if exceeded)
     * @return PortfolioValueResult with all metrics
     */
    public static PortfolioValueResult calculateFull(
        Portfolio portfolio,
        List<Trade> openTrades,
        Map<String, BigDecimal> currentPrices,
        BigDecimal peakValue
    ) {
        BigDecimal availableCapital = portfolio != null ? portfolio.availableCapital() : ZERO;
        BigDecimal positionsMarketValue = calculatePositionsMarketValue(openTrades, currentPrices);
        BigDecimal currentValue = availableCapital.add(positionsMarketValue);
        BigDecimal unrealizedPnL = calculateUnrealizedPnL(openTrades, currentPrices);

        // Update peak if current exceeds it
        BigDecimal updatedPeak = updatePeak(currentValue, peakValue);

        // Calculate drawdown from peak
        BigDecimal drawdown = calculateDrawdown(currentValue, updatedPeak);

        // Calculate return from initial capital
        BigDecimal initialCapital = portfolio != null ? portfolio.totalCapital() : ZERO;
        BigDecimal portfolioReturn = calculateReturn(currentValue, initialCapital);

        int openPositionCount = openTrades != null ? openTrades.size() : 0;

        return new PortfolioValueResult(
            currentValue,
            updatedPeak,
            availableCapital,
            positionsMarketValue,
            unrealizedPnL,
            drawdown,
            portfolioReturn,
            openPositionCount
        );
    }
}
