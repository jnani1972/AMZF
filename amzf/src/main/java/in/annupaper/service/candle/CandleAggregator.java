package in.annupaper.service.candle;

import in.annupaper.domain.model.EventType;
import in.annupaper.domain.model.TimeframeType;
import in.annupaper.domain.model.HistoricalCandle;
import in.annupaper.service.core.EventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Candle Aggregator - Build 25-min and 125-min candles from 1-min candles.
 *
 * Pattern: On every 1-min candle close, recompute the current 25-min and
 * 125-min buckets
 * from all 1-min candles in that bucket. This ensures correctness and
 * simplicity.
 *
 * Alignment: Buckets align from market session start (09:15 IST), not unix
 * epoch.
 */
public final class CandleAggregator {
    private static final Logger log = LoggerFactory.getLogger(CandleAggregator.class);

    private final CandleStore candleStore;
    private final EventService eventService;

    public CandleAggregator(CandleStore candleStore, EventService eventService) {
        this.candleStore = candleStore;
        this.eventService = eventService;
    }

    /**
     * Called when a 1-minute candle closes.
     * Recomputes both 25-min and 125-min buckets for the symbol.
     *
     * @param symbol       Symbol to aggregate
     * @param oneMinCandle The closed 1-minute candle
     */
    public void on1MinuteCandleClose(String symbol, HistoricalCandle oneMinCandle) {
        if (oneMinCandle.timeframe() != TimeframeType.MINUTE_1) {
            log.warn("Expected 1-min candle, got {}", oneMinCandle.timeframe());
            return;
        }

        // Aggregate and persist 25-min candle
        aggregate(symbol, TimeframeType.MINUTE_25, oneMinCandle.timestamp(), 25);

        // Aggregate and persist 125-min candle
        aggregate(symbol, TimeframeType.MINUTE_125, oneMinCandle.timestamp(), 125);
    }

    /**
     * Aggregate multi-minute candle from 1-min candles.
     *
     * @param symbol           Symbol to aggregate
     * @param targetTimeframe  Target timeframe (25-min or 125-min)
     * @param triggerTimestamp The timestamp of the 1-min candle that triggered this
     *                         aggregation
     * @param intervalMinutes  Interval size (25 or 125)
     */
    private void aggregate(String symbol, TimeframeType targetTimeframe, Instant triggerTimestamp,
            int intervalMinutes) {
        // Calculate bucket boundaries using SessionClock
        Instant bucketStart = SessionClock.floorToIntervalFromSessionStart(triggerTimestamp, intervalMinutes);
        Instant bucketEnd = bucketStart.plus(intervalMinutes, ChronoUnit.MINUTES);

        // Fetch all 1-min candles in this bucket from store
        List<HistoricalCandle> oneMinCandles = candleStore.getRange(
                symbol,
                TimeframeType.LTF,
                bucketStart,
                bucketEnd.minus(1, ChronoUnit.MINUTES) // Exclusive end, so subtract 1 minute
        );

        if (oneMinCandles.isEmpty()) {
            log.debug("No 1-min candles found for {} {} bucket {} to {}",
                    symbol, targetTimeframe, bucketStart, bucketEnd);
            return;
        }

        // Aggregate OHLCV
        BigDecimal open = oneMinCandles.get(0).open(); // First candle's open
        BigDecimal close = oneMinCandles.get(oneMinCandles.size() - 1).close(); // Last candle's close
        BigDecimal high = oneMinCandles.stream()
                .map(HistoricalCandle::high)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        BigDecimal low = oneMinCandles.stream()
                .map(HistoricalCandle::low)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        long volume = oneMinCandles.stream()
                .mapToLong(HistoricalCandle::volume)
                .sum();

        // Create aggregated candle
        HistoricalCandle aggregatedCandle = new HistoricalCandle(
                symbol,
                targetTimeframe,
                bucketStart,
                open,
                high,
                low,
                close,
                volume);
        // Upsert to prevent duplicates (recomputation overwrites)
        candleStore.upsert(aggregatedCandle);

        // Emit CANDLE event
        Map<String, Object> payload = new HashMap<>();
        payload.put("symbol", symbol);
        payload.put("timeframe", targetTimeframe.name());
        payload.put("timestamp", bucketStart.toEpochMilli());
        payload.put("open", open);
        payload.put("high", high);
        payload.put("low", low);
        payload.put("close", close);
        payload.put("volume", volume);

        eventService.emitGlobal(EventType.CANDLE, payload, "CANDLE_AGGREGATOR");

        log.debug("Aggregated {} candle for {} @ {} from {} 1-min candles",
                targetTimeframe, symbol, bucketStart, oneMinCandles.size());
    }

    /**
     * Manually aggregate a specific bucket (useful for backfill scenarios).
     *
     * @param symbol          Symbol to aggregate
     * @param targetTimeframe Target timeframe
     * @param bucketStart     Start of bucket to aggregate
     */
    public void aggregateBucket(String symbol, TimeframeType targetTimeframe, Instant bucketStart) {
        int intervalMinutes = switch (targetTimeframe) {
            case ITF, MINUTE_25 -> 25;
            case HTF, MINUTE_125 -> 125;
            default -> throw new IllegalArgumentException("Only ITF/MINUTE_25 and HTF/MINUTE_125 timeframes supported");
        };

        aggregate(symbol, targetTimeframe, bucketStart, intervalMinutes);
    }

    /**
     * Backfill aggregated candles for a time range.
     * Useful when 1-min candles are backfilled and multi-minute candles need
     * updating.
     *
     * @param symbol          Symbol to backfill
     * @param targetTimeframe Target timeframe (25-min or 125-min)
     * @param from            Start timestamp
     * @param to              End timestamp
     */
    public void backfillAggregatedCandles(String symbol, TimeframeType targetTimeframe, Instant from, Instant to) {
        int intervalMinutes = switch (targetTimeframe) {
            case ITF, MINUTE_25 -> 25;
            case HTF, MINUTE_125 -> 125;
            default -> throw new IllegalArgumentException("Only ITF/MINUTE_25 and HTF/MINUTE_125 timeframes supported");
        };

        // Align from/to to bucket boundaries
        Instant bucketStart = SessionClock.floorToIntervalFromSessionStart(from, intervalMinutes);
        Instant bucketEnd = SessionClock.floorToIntervalFromSessionStart(to, intervalMinutes);

        log.info("Backfilling {} candles for {} from {} to {}",
                targetTimeframe, symbol, bucketStart, bucketEnd);

        int count = 0;
        Instant current = bucketStart;

        while (!current.isAfter(bucketEnd)) {
            aggregate(symbol, targetTimeframe, current, intervalMinutes);
            current = current.plus(intervalMinutes, ChronoUnit.MINUTES);
            count++;
        }

        log.info("Backfilled {} {} candles for {}", count, targetTimeframe, symbol);
    }
}
