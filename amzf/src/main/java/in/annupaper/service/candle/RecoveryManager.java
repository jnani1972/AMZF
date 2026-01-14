package in.annupaper.service.candle;

import in.annupaper.domain.data.TimeframeType;
import in.annupaper.domain.data.Candle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Recovery Manager - Handles startup and reconnect scenarios.
 *
 * Scenarios:
 * A. App online before market - Warmup from DB, start listening for ticks
 * B. App starts mid-market - Backfill from market open to now, then listen
 * C. Disconnect and reconnect - Detect gap, backfill missing period
 * D. App comes online post-market - Backfill entire session
 */
public final class RecoveryManager {
    private static final Logger log = LoggerFactory.getLogger(RecoveryManager.class);

    private final CandleStore candleStore;
    private final HistoryBackfiller historyBackfiller;
    private final CandleAggregator candleAggregator;

    public RecoveryManager(
        CandleStore candleStore,
        HistoryBackfiller historyBackfiller,
        CandleAggregator candleAggregator
    ) {
        this.candleStore = candleStore;
        this.historyBackfiller = historyBackfiller;
        this.candleAggregator = candleAggregator;
    }

    /**
     * Recover candles for a symbol on startup or reconnect.
     *
     * @param symbol Symbol to recover
     */
    public void recover(String symbol) {
        Instant now = Instant.now();
        Instant sessionStart = SessionClock.getTodaySessionStart();
        Instant sessionEnd = SessionClock.getTodaySessionEnd();

        log.info("Starting recovery for {} at {}", symbol, now);

        if (now.isBefore(sessionStart)) {
            // Scenario A: Before market opens
            handleBeforeMarket(symbol);
        } else if (now.isAfter(sessionEnd)) {
            // Scenario D: After market closes
            handleAfterMarket(symbol, sessionStart, sessionEnd);
        } else {
            // Scenario B or C: During market hours
            handleDuringMarket(symbol, sessionStart, now);
        }

        log.info("Recovery completed for {}", symbol);
    }

    /**
     * Scenario A: App online before market opens.
     * Warmup from database (load recent candles into memory).
     */
    private void handleBeforeMarket(String symbol) {
        log.info("Scenario A: App online before market for {}", symbol);

        // Warmup 1-minute candles from DB into memory
        candleStore.warmup(symbol, TimeframeType.MINUTE_1);

        // Warmup 25-minute candles
        candleStore.warmup(symbol, TimeframeType.MINUTE_25);

        // Warmup 125-minute candles
        candleStore.warmup(symbol, TimeframeType.MINUTE_125);

        log.info("Warmed up candles for {} from database", symbol);
    }

    /**
     * Scenario D: App comes online after market closes.
     * Backfill entire trading session.
     */
    private void handleAfterMarket(String symbol, Instant sessionStart, Instant sessionEnd) {
        log.info("Scenario D: App online after market for {}", symbol);

        // Check if we already have candles for today's session
        Candle latestCandle = candleStore.getLatest(symbol, TimeframeType.MINUTE_1);

        if (latestCandle != null && !latestCandle.timestamp().isBefore(sessionStart)) {
            log.info("Already have candles for today's session for {}", symbol);
            return;
        }

        // Backfill entire session (1-minute candles)
        log.info("Backfilling entire session for {} from {} to {}", symbol, sessionStart, sessionEnd);
        int backfilled = historyBackfiller.backfillRange(symbol, TimeframeType.MINUTE_1, sessionStart, sessionEnd);

        if (backfilled > 0) {
            // Regenerate aggregated candles (25-min and 125-min) from backfilled 1-min candles
            regenerateAggregatedCandles(symbol, sessionStart, sessionEnd);
        }
    }

    /**
     * Scenario B/C: App starts or reconnects during market hours.
     * Backfill from session start (or last known candle) to current time.
     */
    private void handleDuringMarket(String symbol, Instant sessionStart, Instant now) {
        Candle latestCandle = candleStore.getLatest(symbol, TimeframeType.MINUTE_1);

        if (latestCandle == null) {
            // Scenario B: No candles exist - backfill from session start
            log.info("Scenario B: App starts mid-market (no existing candles) for {}", symbol);
            int backfilled = historyBackfiller.backfillRange(symbol, TimeframeType.MINUTE_1, sessionStart, now);

            if (backfilled > 0) {
                regenerateAggregatedCandles(symbol, sessionStart, now);
            }
        } else {
            // Scenario C: Candles exist - check for gaps (disconnect/reconnect)
            Instant nextExpected = latestCandle.timestamp().plus(1, ChronoUnit.MINUTES);

            if (nextExpected.isBefore(now)) {
                // Gap detected - backfill missing period
                long gapMinutes = ChronoUnit.MINUTES.between(nextExpected, now);
                log.info("Scenario C: Gap detected for {} - {} minutes (from {} to {})",
                    symbol, gapMinutes, nextExpected, now);

                int backfilled = historyBackfiller.backfillRange(symbol, TimeframeType.MINUTE_1, nextExpected, now);

                if (backfilled > 0) {
                    regenerateAggregatedCandles(symbol, nextExpected, now);
                }
            } else {
                log.info("No gap detected for {} - up to date", symbol);
            }
        }
    }

    /**
     * Regenerate 25-min and 125-min candles from 1-min candles.
     */
    private void regenerateAggregatedCandles(String symbol, Instant from, Instant to) {
        log.info("Regenerating aggregated candles for {} from {} to {}", symbol, from, to);

        // Regenerate 25-min candles
        candleAggregator.backfillAggregatedCandles(symbol, TimeframeType.MINUTE_25, from, to);

        // Regenerate 125-min candles
        candleAggregator.backfillAggregatedCandles(symbol, TimeframeType.MINUTE_125, from, to);

        log.info("Aggregated candles regenerated for {}", symbol);
    }

    /**
     * Recover all symbols in a watchlist.
     *
     * @param symbols List of symbols to recover
     */
    public void recoverAll(List<String> symbols) {
        log.info("Starting recovery for {} symbols", symbols.size());

        for (String symbol : symbols) {
            try {
                recover(symbol);
            } catch (Exception e) {
                log.error("Failed to recover {}: {}", symbol, e.getMessage());
            }
        }

        log.info("Recovery completed for all symbols");
    }

    /**
     * Handle reconnect scenario (called when WebSocket reconnects).
     *
     * @param symbol Symbol to recover
     * @param lastKnownTimestamp Last known tick/candle timestamp before disconnect
     */
    public void handleReconnect(String symbol, Instant lastKnownTimestamp) {
        Instant now = Instant.now();

        if (!SessionClock.isWithinSession(now)) {
            log.info("Reconnected outside market hours for {}, skipping backfill", symbol);
            return;
        }

        // Calculate gap
        long gapMinutes = ChronoUnit.MINUTES.between(lastKnownTimestamp, now);

        if (gapMinutes > 1) {
            log.info("Reconnect gap detected for {}: {} minutes", symbol, gapMinutes);

            // Backfill missing period
            Instant backfillStart = SessionClock.floorToMinute(lastKnownTimestamp).plus(1, ChronoUnit.MINUTES);
            int backfilled = historyBackfiller.backfillRange(symbol, TimeframeType.MINUTE_1, backfillStart, now);

            if (backfilled > 0) {
                regenerateAggregatedCandles(symbol, backfillStart, now);
            }
        } else {
            log.info("No significant gap on reconnect for {}", symbol);
        }
    }
}
