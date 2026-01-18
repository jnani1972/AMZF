package in.annupaper.service.candle;

import in.annupaper.infrastructure.broker.BrokerAdapterFactory;
import in.annupaper.domain.model.*;
import in.annupaper.application.port.output.BrokerRepository;
import in.annupaper.application.port.output.UserBrokerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import java.util.List;
import java.util.Optional;

/**
 * History Backfiller - Detects gaps and fetches missing candles from broker.
 *
 * Used when:
 * 1. First tick arrives and no previous candle exists
 * 2. App reconnects after downtime
 * 3. Gap detected between last candle and current time
 */
public final class HistoryBackfiller {
    private static final Logger log = LoggerFactory.getLogger(HistoryBackfiller.class);

    private final CandleStore candleStore;
    private final BrokerAdapterFactory brokerFactory;
    private final UserBrokerRepository userBrokerRepo;
    private final BrokerRepository brokerRepo;

    public HistoryBackfiller(
            CandleStore candleStore,
            BrokerAdapterFactory brokerFactory,
            UserBrokerRepository userBrokerRepo,
            BrokerRepository brokerRepo) {
        this.candleStore = candleStore;
        this.brokerFactory = brokerFactory;
        this.userBrokerRepo = userBrokerRepo;
        this.brokerRepo = brokerRepo;
    }

    /**
     * Get the data broker adapter (fetched dynamically).
     */
    private BrokerAdapter getDataBrokerAdapter() {
        try {
            // Find data broker
            Optional<in.annupaper.domain.model.UserBroker> dataBrokerOpt = userBrokerRepo.findDataBroker();
            if (dataBrokerOpt.isEmpty()) {
                log.warn("No data broker configured");
                return null;
            }

            in.annupaper.domain.model.UserBroker dataBroker = dataBrokerOpt.get();

            // Find broker details
            Optional<Broker> brokerOpt = brokerRepo.findById(dataBroker.brokerId());
            if (brokerOpt.isEmpty()) {
                log.warn("Broker not found: {}", dataBroker.brokerId());
                return null;
            }

            String brokerCode = brokerOpt.get().brokerCode();
            String userBrokerId = dataBroker.userBrokerId();

            // Get or create adapter
            BrokerAdapter adapter = brokerFactory.getOrCreate(userBrokerId, brokerCode);
            if (adapter == null || !adapter.isConnected()) {
                log.warn("Data broker adapter not connected: {}", brokerCode);
                return null;
            }

            return adapter;

        } catch (Exception e) {
            log.error("Failed to get data broker adapter: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Detect and fill gaps for a symbol and timeframe.
     *
     * @param symbol        Symbol to backfill
     * @param timeframe     Timeframe to backfill (1, 25, or 125 minutes)
     * @param upToTimestamp Fill gaps up to this timestamp (usually current time)
     * @return Number of candles backfilled
     */
    public int backfillIfNeeded(String symbol, TimeframeType timeframe, Instant upToTimestamp) {
        try {
            // Get the latest candle from store
            HistoricalCandle latestCandle = candleStore.getLatest(symbol, timeframe);

            Instant fromTimestamp;
            if (latestCandle == null) {
                // No candles exist - backfill from session start
                fromTimestamp = SessionClock.getTodaySessionStart();
                log.info("No existing candles for {} {}, backfilling from session start", symbol, timeframe);
            } else {
                // Calculate next expected candle timestamp
                int intervalMinutes = getIntervalMinutes(timeframe);
                Instant nextExpected = latestCandle.timestamp().plus(intervalMinutes, ChronoUnit.MINUTES);

                // Check if there's a gap
                if (!nextExpected.isBefore(upToTimestamp)) {
                    // No gap, we're up to date
                    return 0;
                }

                fromTimestamp = nextExpected;
                log.info("Gap detected for {} {}: from {} to {}", symbol, timeframe, nextExpected, upToTimestamp);
            }

            // Fetch historical candles from broker
            return fetchAndStore(symbol, timeframe, fromTimestamp, upToTimestamp);

        } catch (Exception e) {
            log.error("Failed to backfill {} {}: {}", symbol, timeframe, e.getMessage());
            return 0;
        }
    }

    /**
     * Fetch historical candles from broker and store them.
     *
     * @param symbol    Symbol to fetch
     * @param timeframe Timeframe to fetch
     * @param from      Start timestamp (inclusive)
     * @param to        End timestamp (inclusive)
     * @return Number of candles fetched and stored
     */
    private int fetchAndStore(String symbol, TimeframeType timeframe, Instant from, Instant to) {
        try {
            // Get data broker adapter
            BrokerAdapter adapter = getDataBrokerAdapter();
            if (adapter == null) {
                log.warn("Data broker not available, cannot backfill {} {}", symbol, timeframe);
                return 0;
            }

            // Call broker API (async)
            List<HistoricalCandle> historicalCandles = adapter.getHistoricalCandles(
                    symbol,
                    timeframe,
                    from.getEpochSecond(),
                    to.getEpochSecond()).join(); // Block and wait for result

            if (historicalCandles == null || historicalCandles.isEmpty()) {
                log.warn("No historical candles returned from broker for {} {}", symbol, timeframe);
                return 0;
            }

            // Convert broker candles to domain candles
            // Perform soft deduplication against existing candles in DB is handled by
            // repository upsert
            // Just pass the HistoricalCandles directly

            // Upsert batch to prevent duplicates
            candleStore.upsertBatch(historicalCandles);

            log.info("Backfilled {} candles for {} {} (from {} to {})",
                    historicalCandles.size(), symbol, timeframe, from, to);

            return historicalCandles.size();

        } catch (Exception e) {
            log.error("Failed to fetch historical candles from broker: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Backfill for specific time range (useful for manual backfill).
     *
     * @param symbol    Symbol to backfill
     * @param timeframe Timeframe to backfill
     * @param from      Start timestamp
     * @param to        End timestamp
     * @return Number of candles backfilled
     */
    public int backfillRange(String symbol, TimeframeType timeframe, Instant from, Instant to) {
        log.info("Manual backfill requested for {} {} from {} to {}", symbol, timeframe, from, to);
        return fetchAndStore(symbol, timeframe, from, to);
    }

    /**
     * Check if backfill is needed (gap exists).
     *
     * @param symbol           Symbol to check
     * @param timeframe        Timeframe to check
     * @param currentTimestamp Current timestamp
     * @return True if gap exists and backfill is needed
     */
    public boolean hasGap(String symbol, TimeframeType timeframe, Instant currentTimestamp) {
        HistoricalCandle latestCandle = candleStore.getLatest(symbol, timeframe);

        if (latestCandle == null) {
            return true; // No candles exist
        }

        int intervalMinutes = getIntervalMinutes(timeframe);
        Instant nextExpected = latestCandle.timestamp().plus(intervalMinutes, ChronoUnit.MINUTES);

        return nextExpected.isBefore(currentTimestamp);
    }

    /**
     * Convert TimeframeType to interval minutes.
     */
    private int getIntervalMinutes(TimeframeType timeframe) {
        return switch (timeframe) {
            case LTF, MINUTE_1 -> 1;
            case ITF, MINUTE_25 -> 25;
            case HTF, MINUTE_125 -> 125;
            case DAILY -> 1440; // Not used for intraday backfill
            default -> throw new IllegalArgumentException("Unsupported timeframe: " + timeframe);
        };
    }
}
