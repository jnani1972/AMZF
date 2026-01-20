package in.annupaper.application.service;

import in.annupaper.service.candle.HistoryBackfiller;
import in.annupaper.service.candle.CandleAggregator;
import in.annupaper.domain.model.TimeframeType;
import in.annupaper.application.port.output.WatchlistRepository;
import in.annupaper.application.port.output.MtfConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * MTF Backfill Service - Ensures sufficient historical candles for MTF
 * strategy.
 *
 * MTF Requirements:
 * - HTF (125-min): 175 candles = ~15 trading days
 * - ITF (25-min): 75 candles = ~5 trading days
 * - LTF (1-min): 375 candles = ~1 trading day
 *
 * This service backfills enough historical data to support these requirements.
 */
public final class MtfBackfillService {
    private static final Logger log = LoggerFactory.getLogger(MtfBackfillService.class);

    private final HistoryBackfiller historyBackfiller;
    private final CandleAggregator candleAggregator;
    private final WatchlistRepository watchlistRepo;
    private final MtfConfigRepository mtfConfigRepo;

    // Trading hours per day (9:15 AM to 3:30 PM = 6.25 hours)
    private static final double TRADING_HOURS_PER_DAY = 6.25;

    public MtfBackfillService(
            HistoryBackfiller historyBackfiller,
            CandleAggregator candleAggregator,
            WatchlistRepository watchlistRepo,
            MtfConfigRepository mtfConfigRepo) {
        this.historyBackfiller = historyBackfiller;
        this.candleAggregator = candleAggregator;
        this.watchlistRepo = watchlistRepo;
        this.mtfConfigRepo = mtfConfigRepo;
    }

    /**
     * Backfill all MTF candles for a specific symbol.
     *
     * @param symbol       Symbol to backfill
     * @param userBrokerId User broker ID for watchlist lookup
     * @return Backfill result with counts
     */
    public MtfBackfillResult backfillSymbol(String symbol, String userBrokerId) {
        log.info("[MTF BACKFILL] Starting backfill for symbol: {}", symbol);

        Instant now = Instant.now();

        // Fetch global config
        in.annupaper.domain.model.MtfGlobalConfig config = mtfConfigRepo.getGlobalConfig().orElse(null);

        // Calculate backfill start time for HTF
        // neededMinutes = htfCandleCount * htfCandleMinutes
        int reqCandles = config != null ? config.htfCandleCount() : 175; // Default safe fallback
        int candleMins = config != null ? config.htfCandleMinutes() : 125;

        long totalMinutesNeeded = (long) reqCandles * candleMins;

        // Convert to trading days: minutes / (6.25 * 60)
        double minutesPerDay = TRADING_HOURS_PER_DAY * 60;
        double tradingDaysNeeded = totalMinutesNeeded / minutesPerDay;

        // Convert to calendar days (approx ~1.45 factor for weekends + buffer)
        // Using 2.0 safety factor to ensure we have enough data
        long lookbackDays = (long) Math.ceil(tradingDaysNeeded * 2.0);

        if (lookbackDays < 7)
            lookbackDays = 7; // Minimum 1 week

        log.info("[MTF BACKFILL] Calc lookback: {} candles * {}m = {}m = {:.1f} trading days -> {} calendar days",
                reqCandles, candleMins, totalMinutesNeeded, tradingDaysNeeded, lookbackDays);

        Instant htfStart = now.minus(lookbackDays, ChronoUnit.DAYS);

        try {
            // Step 1: Backfill 1-min candles (used as base for aggregation) - use LTF
            log.info("[MTF BACKFILL] Backfilling LTF (1-min) candles from {} to {}", htfStart, now);
            int oneMinCount = historyBackfiller.backfillRange(symbol, TimeframeType.LTF, htfStart, now);
            log.info("[MTF BACKFILL] Backfilled {} LTF (1-min) candles", oneMinCount);

            // Step 2: Aggregate to ITF (25-min) candles
            log.info("[MTF BACKFILL] Aggregating to ITF (25-min) candles");
            candleAggregator.backfillAggregatedCandles(symbol, TimeframeType.ITF, htfStart, now);

            // Step 3: Aggregate to HTF (125-min) candles
            log.info("[MTF BACKFILL] Aggregating to HTF (125-min) candles");
            candleAggregator.backfillAggregatedCandles(symbol, TimeframeType.HTF, htfStart, now);

            log.info("[MTF BACKFILL] Completed backfill for {}", symbol);

            return new MtfBackfillResult(
                    symbol,
                    true,
                    oneMinCount,
                    "Backfill completed successfully",
                    htfStart,
                    now);

        } catch (Exception e) {
            log.error("[MTF BACKFILL] Failed to backfill {}: {}", symbol, e.getMessage(), e);
            return new MtfBackfillResult(
                    symbol,
                    false,
                    0,
                    "Backfill failed: " + e.getMessage(),
                    htfStart,
                    now);
        }
    }

    /**
     * Backfill MTF candles for all enabled watchlist symbols.
     *
     * @param userBrokerId User broker ID
     * @return List of backfill results
     */
    public List<MtfBackfillResult> backfillAllSymbols(String userBrokerId) {
        log.info("[MTF BACKFILL] Starting backfill for all symbols under userBrokerId: {}", userBrokerId);

        // Get all enabled watchlist symbols
        List<String> symbols = watchlistRepo.findByUserBrokerId(userBrokerId).stream()
                .filter(w -> w.enabled())
                .map(w -> w.symbol())
                .distinct()
                .toList();

        log.info("[MTF BACKFILL] Found {} symbols to backfill", symbols.size());

        return symbols.stream()
                .map(symbol -> backfillSymbol(symbol, userBrokerId))
                .toList();
    }

    /**
     * Result of MTF backfill operation.
     */
    public record MtfBackfillResult(
            String symbol,
            boolean success,
            int candlesBackfilled,
            String message,
            Instant fromTimestamp,
            Instant toTimestamp) {
        public String getSummary() {
            return String.format(
                    "%s: %s (%d candles from %s to %s) - %s",
                    symbol,
                    success ? "SUCCESS" : "FAILED",
                    candlesBackfilled,
                    fromTimestamp,
                    toTimestamp,
                    message);
        }
    }
}
