package in.annupaper.service.candle;

import in.annupaper.domain.data.TimeframeType;
import in.annupaper.domain.repository.WatchlistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * MTF Backfill Service - Ensures sufficient historical candles for MTF strategy.
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

    // Trading hours per day (9:15 AM to 3:30 PM = 6.25 hours)
    private static final double TRADING_HOURS_PER_DAY = 6.25;

    public MtfBackfillService(
        HistoryBackfiller historyBackfiller,
        CandleAggregator candleAggregator,
        WatchlistRepository watchlistRepo
    ) {
        this.historyBackfiller = historyBackfiller;
        this.candleAggregator = candleAggregator;
        this.watchlistRepo = watchlistRepo;
    }

    /**
     * Backfill all MTF candles for a specific symbol.
     *
     * @param symbol Symbol to backfill
     * @param userBrokerId User broker ID for watchlist lookup
     * @return Backfill result with counts
     */
    public MtfBackfillResult backfillSymbol(String symbol, String userBrokerId) {
        log.info("[MTF BACKFILL] Starting backfill for symbol: {}", symbol);

        Instant now = Instant.now();

        // Calculate backfill start time for HTF (175 candles of 125-min)
        // 175 candles × 125 min = 21,875 minutes = ~364 hours
        // At 6.25 hours/day = 58 trading days (use 60 to be safe)
        Instant htfStart = now.minus(60, ChronoUnit.DAYS);

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
                now
            );

        } catch (Exception e) {
            log.error("[MTF BACKFILL] Failed to backfill {}: {}", symbol, e.getMessage(), e);
            return new MtfBackfillResult(
                symbol,
                false,
                0,
                "Backfill failed: " + e.getMessage(),
                htfStart,
                now
            );
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
        Instant toTimestamp
    ) {
        public String getSummary() {
            return String.format(
                "%s: %s (%d candles from %s to %s) - %s",
                symbol,
                success ? "SUCCESS" : "FAILED",
                candlesBackfilled,
                fromTimestamp,
                toTimestamp,
                message
            );
        }
    }
}
