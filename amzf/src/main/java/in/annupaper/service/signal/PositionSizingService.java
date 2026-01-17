package in.annupaper.service.signal;

import in.annupaper.domain.data.TimeframeType;
import in.annupaper.domain.data.Candle;
import in.annupaper.domain.signal.MtfGlobalConfig;
import in.annupaper.domain.user.Portfolio;
import in.annupaper.domain.trade.Trade;
import in.annupaper.domain.repository.MtfConfigRepository;
import in.annupaper.domain.repository.PortfolioRepository;
import in.annupaper.domain.repository.TradeRepository;
import in.annupaper.service.candle.CandleStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Position Sizing Service - High-level service that orchestrates constitutional position sizing.
 *
 * Responsibilities:
 * 1. Fetch required data (candles, ATR, portfolio, trades)
 * 2. Calculate portfolio values and stress metrics
 * 3. Delegate to MtfPositionSizer for actual sizing calculation
 * 4. Handle errors and fallbacks gracefully
 *
 * This service sits between ValidationService and MtfPositionSizer,
 * abstracting away the complexity of data fetching and preparation.
 */
public final class PositionSizingService {
    private static final Logger log = LoggerFactory.getLogger(PositionSizingService.class);

    private static final int DEFAULT_ATR_PERIOD = 14;
    private static final int FALLBACK_ATR_PERIOD = 5;

    private final CandleStore candleStore;
    private final PortfolioRepository portfolioRepo;
    private final TradeRepository tradeRepo;
    private final MtfConfigRepository mtfConfigRepo;

    public PositionSizingService(
        CandleStore candleStore,
        PortfolioRepository portfolioRepo,
        TradeRepository tradeRepo,
        MtfConfigRepository mtfConfigRepo
    ) {
        this.candleStore = candleStore;
        this.portfolioRepo = portfolioRepo;
        this.tradeRepo = tradeRepo;
        this.mtfConfigRepo = mtfConfigRepo;
    }

    /**
     * Calculate position size for a new entry.
     *
     * @param symbol Symbol to trade
     * @param zonePrice Entry price within buy zone
     * @param effectiveFloor Strictest floor (stop loss)
     * @param effectiveCeiling Most conservative ceiling (target)
     * @param pWin Probability of hitting ceiling before floor
     * @param pFill Probability of order getting filled
     * @param kelly Full Kelly fraction
     * @param confluenceMultiplier Multiplier based on confluence strength
     * @param portfolioId Portfolio ID for this user
     * @return PositionSizeResult with final quantity and diagnostics
     */
    public MtfPositionSizer.PositionSizeResult calculatePositionSize(
        String symbol,
        BigDecimal zonePrice,
        BigDecimal effectiveFloor,
        BigDecimal effectiveCeiling,
        BigDecimal pWin,
        BigDecimal pFill,
        BigDecimal kelly,
        BigDecimal confluenceMultiplier,
        String portfolioId
    ) {
        try {
            // 1. Get MTF config
            MtfGlobalConfig config = mtfConfigRepo.getGlobalConfig()
                .orElseThrow(() -> new RuntimeException("MTF global config not found"));

            // 2. Get portfolio
            Portfolio portfolio = portfolioRepo.findById(portfolioId)
                .orElseThrow(() -> new RuntimeException("Portfolio not found: " + portfolioId));

            BigDecimal capSym = calculateSymbolCapital(portfolio, config);

            // 3. Get all open trades for this portfolio
            List<Trade> openTrades = tradeRepo.findByPortfolioId(portfolioId).stream()
                .filter(t -> "OPEN".equals(t.status()))
                .toList();

            // 4. Get existing position for this symbol
            Optional<Trade> existingTrade = openTrades.stream()
                .filter(t -> t.symbol().equals(symbol))
                .findFirst();

            int existingQty = existingTrade.map(Trade::entryQty).orElse(0);
            BigDecimal existingAvg = existingTrade.map(Trade::entryPrice).orElse(BigDecimal.ZERO);

            // 5. Fetch daily candles and calculate ATR
            List<Candle> dailyCandles = fetchDailyCandles(symbol);
            BigDecimal atr = calculateATR(dailyCandles);

            if (atr == null || atr.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("[POSITION SIZING REJECTED] {}: No ATR available", symbol);
                return createRejectedResult(zonePrice, "NO_ATR_DATA");
            }

            // 6. Fetch LTF candles for velocity calculation
            List<Candle> ltfCandles = fetchLTFCandles(symbol, config.rangeLookbackBars());

            // 7. Calculate portfolio values
            Map<String, BigDecimal> currentPrices = new HashMap<>();
            currentPrices.put(symbol, zonePrice);

            BigDecimal currentPortfolioValue = PortfolioValueTracker.calculateCurrentValueForSymbol(
                portfolio,
                openTrades,
                symbol,
                zonePrice
            );

            // Use total capital as initial peak (conservative approach)
            // TODO: Track actual peak in database
            BigDecimal peakPortfolioValue = portfolio.totalCapital();

            // 8. Call MtfPositionSizer
            MtfPositionSizer.PositionSizeResult result = MtfPositionSizer.calculatePositionSize(
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
                ltfCandles,
                atr,
                currentPortfolioValue,
                peakPortfolioValue,
                config
            );

            if (result.isValid()) {
                log.info("[POSITION SIZING] {}: {} - {}", symbol, result.quantity(), result.getSummary());
            } else {
                log.warn("[POSITION SIZING REJECTED] {}: {}", symbol, result.limitingConstraint());
            }

            return result;

        } catch (Exception e) {
            log.error("[POSITION SIZING ERROR] {}: {}", symbol, e.getMessage(), e);
            return createRejectedResult(zonePrice, "SIZING_ERROR: " + e.getMessage());
        }
    }

    /**
     * Calculate position size for averaging down (adding to existing position).
     *
     * @param symbol Symbol to trade
     * @param zonePrice Lower zone price
     * @param effectiveFloor Updated floor
     * @param effectiveCeiling Ceiling
     * @param portfolioId Portfolio ID
     * @return PositionSizeResult (may be rejected by averaging gates)
     */
    public MtfPositionSizer.PositionSizeResult calculateAddSize(
        String symbol,
        BigDecimal zonePrice,
        BigDecimal effectiveFloor,
        BigDecimal effectiveCeiling,
        String portfolioId
    ) {
        try {
            // 1. Get MTF config
            MtfGlobalConfig config = mtfConfigRepo.getGlobalConfig()
                .orElseThrow(() -> new RuntimeException("MTF global config not found"));

            // 2. Get portfolio
            Portfolio portfolio = portfolioRepo.findById(portfolioId)
                .orElseThrow(() -> new RuntimeException("Portfolio not found: " + portfolioId));

            BigDecimal capSym = calculateSymbolCapital(portfolio, config);

            // 3. Get all open trades for this portfolio
            List<Trade> openTrades = tradeRepo.findByPortfolioId(portfolioId).stream()
                .filter(t -> "OPEN".equals(t.status()))
                .toList();

            // 4. Get existing position (REQUIRED for averaging)
            Optional<Trade> existingTrade = openTrades.stream()
                .filter(t -> t.symbol().equals(symbol))
                .findFirst();

            if (existingTrade.isEmpty()) {
                log.warn("[AVERAGING REJECTED] {}: No existing position to average into", symbol);
                return createRejectedResult(zonePrice, "NO_EXISTING_POSITION");
            }

            Trade trade = existingTrade.get();
            int existingQty = trade.entryQty();
            BigDecimal existingAvg = trade.entryPrice();

            // 5. Fetch daily candles and calculate ATR (required for averaging gate)
            List<Candle> dailyCandles = fetchDailyCandles(symbol);
            BigDecimal atr = calculateATR(dailyCandles);

            if (atr == null || atr.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("[AVERAGING REJECTED] {}: No ATR available for gate validation", symbol);
                return createRejectedResult(zonePrice, "NO_ATR_DATA");
            }

            // 6. Fetch LTF candles for velocity calculation
            List<Candle> ltfCandles = fetchLTFCandles(symbol, config.rangeLookbackBars());

            // 7. Calculate portfolio values
            BigDecimal currentPortfolioValue = PortfolioValueTracker.calculateCurrentValueForSymbol(
                portfolio,
                openTrades,
                symbol,
                zonePrice
            );

            BigDecimal peakPortfolioValue = portfolio.totalCapital();

            // 8. Call MtfPositionSizer.calculateAddSize()
            MtfPositionSizer.PositionSizeResult result = MtfPositionSizer.calculateAddSize(
                zonePrice,
                effectiveFloor,
                effectiveCeiling,
                existingQty,
                existingAvg,
                capSym,
                openTrades,
                symbol,
                ltfCandles,
                atr,
                currentPortfolioValue,
                peakPortfolioValue,
                config
            );

            if (result.isValid()) {
                log.info("[AVERAGING] {}: Adding {} shares at {} - {}",
                    symbol, result.quantity(), zonePrice, result.getSummary());
            } else {
                log.warn("[AVERAGING REJECTED] {}: {}", symbol, result.limitingConstraint());
            }

            return result;

        } catch (Exception e) {
            log.error("[AVERAGING ERROR] {}: {}", symbol, e.getMessage(), e);
            return createRejectedResult(zonePrice, "AVERAGING_ERROR: " + e.getMessage());
        }
    }

    /**
     * Calculate symbol capital allocation.
     *
     * Simple equal-weight allocation for now.
     * TODO: Support different allocation modes.
     */
    private BigDecimal calculateSymbolCapital(Portfolio portfolio, MtfGlobalConfig config) {
        // Equal weight allocation
        return portfolio.totalCapital()
            .multiply(portfolio.maxSymbolWeight());
    }

    /**
     * Fetch daily candles for ATR calculation.
     *
     * @param symbol Symbol
     * @return List of daily candles (may be empty)
     */
    private List<Candle> fetchDailyCandles(String symbol) {
        try {
            // Try memory first
            List<Candle> candles = candleStore.getFromMemory(symbol, TimeframeType.DAILY);

            if (candles.isEmpty()) {
                // Fallback to Postgres
                candles = candleStore.getFromPostgres(symbol, TimeframeType.DAILY,
                    TimeframeType.DAILY.getLookback());
            }

            return candles;
        } catch (Exception e) {
            log.warn("[CANDLE FETCH] Failed to fetch daily candles for {}: {}", symbol, e.getMessage());
            return List.of();
        }
    }

    /**
     * Fetch LTF candles for velocity calculation.
     *
     * @param symbol Symbol
     * @param lookbackBars Number of bars needed
     * @return List of LTF candles (may be empty)
     */
    private List<Candle> fetchLTFCandles(String symbol, int lookbackBars) {
        try {
            // Try memory first
            List<Candle> candles = candleStore.getFromMemory(symbol, TimeframeType.LTF);

            if (candles.isEmpty()) {
                // Fallback to Postgres
                candles = candleStore.getFromPostgres(symbol, TimeframeType.LTF, lookbackBars);
            }

            return candles;
        } catch (Exception e) {
            log.warn("[CANDLE FETCH] Failed to fetch LTF candles for {}: {}", symbol, e.getMessage());
            return List.of();
        }
    }

    /**
     * Calculate ATR from daily candles.
     *
     * @param dailyCandles Daily candles
     * @return ATR value or null if insufficient data
     */
    private BigDecimal calculateATR(List<Candle> dailyCandles) {
        if (dailyCandles == null || dailyCandles.isEmpty()) {
            return null;
        }

        // Try full ATR calculation
        BigDecimal atr = ATRCalculator.calculateDailyATR(dailyCandles, DEFAULT_ATR_PERIOD);

        if (atr == null) {
            // Fallback to shorter period
            atr = ATRCalculator.calculateDailyATRWithFallback(
                dailyCandles,
                DEFAULT_ATR_PERIOD,
                FALLBACK_ATR_PERIOD
            );
        }

        return atr;
    }

    /**
     * Create a rejected position size result.
     *
     * @param zonePrice Entry price
     * @param rejectionReason Reason for rejection
     * @return PositionSizeResult with quantity=0
     */
    private MtfPositionSizer.PositionSizeResult createRejectedResult(
        BigDecimal zonePrice,
        String rejectionReason
    ) {
        Map<String, Integer> constraints = new HashMap<>();
        constraints.put(rejectionReason, 0);

        return new MtfPositionSizer.PositionSizeResult(
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
}
