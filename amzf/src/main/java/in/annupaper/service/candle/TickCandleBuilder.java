package in.annupaper.service.candle;

import in.annupaper.broker.BrokerAdapter;
import in.annupaper.domain.common.EventType;
import in.annupaper.domain.data.TimeframeType;
import in.annupaper.domain.data.Candle;
import in.annupaper.service.core.EventService;
import in.annupaper.repository.WatchlistRepository;
import in.annupaper.service.MarketDataCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * TickCandleBuilder - Build 1-minute candles from incoming ticks.
 *
 * Pattern: tick-only candle builder with uptime gaps
 * - Only builds 1-minute candles (source of truth)
 * - 25-min and 125-min are aggregated from closed 1-min candles by CandleAggregator
 * - Detects gaps and triggers backfill via HistoryBackfiller
 * - Uses SessionClock for proper market session alignment
 */
public final class TickCandleBuilder implements BrokerAdapter.TickListener {
    private static final Logger log = LoggerFactory.getLogger(TickCandleBuilder.class);

    private final CandleStore candleStore;
    private final EventService eventService;
    private final MarketDataCache marketDataCache;
    private final HistoryBackfiller historyBackfiller;
    private final CandleAggregator candleAggregator;

    // Watchdog manager for tick liveness tracking (optional, set via setter)
    private in.annupaper.service.WatchdogManager watchdogManager;

    // Partial 1-minute candles: symbol -> PartialCandle
    private final Map<String, PartialCandle> partialCandles = new ConcurrentHashMap<>();

    // ✅ P0-D: Two-window tick deduplication (not removeIf!)
    // Current window: actively checked for duplicates
    private Set<String> currentDedupeWindow = ConcurrentHashMap.newKeySet();

    // Previous window: kept for grace period during window swap
    private Set<String> previousDedupeWindow = ConcurrentHashMap.newKeySet();

    // Last window swap time (for periodic rotation)
    private volatile Instant lastWindowSwap = Instant.now();

    // Window rotation lock (prevent concurrent swaps)
    private final ReentrantLock windowSwapLock = new ReentrantLock();

    // Dedupe metrics
    private final AtomicLong totalTicks = new AtomicLong(0);
    private final AtomicLong duplicateTicks = new AtomicLong(0);
    private final AtomicLong missingExchangeTimestamp = new AtomicLong(0);

    // FIX: Use MarketDataCache instead of direct DB writes for better performance
    public TickCandleBuilder(
        CandleStore candleStore,
        EventService eventService,
        MarketDataCache marketDataCache,
        HistoryBackfiller historyBackfiller,
        CandleAggregator candleAggregator
    ) {
        this.candleStore = candleStore;
        this.eventService = eventService;
        this.marketDataCache = marketDataCache;
        this.historyBackfiller = historyBackfiller;
        this.candleAggregator = candleAggregator;
    }

    /**
     * Set watchdog manager for tick liveness tracking.
     * Called from App.java after both TickCandleBuilder and WatchdogManager are created.
     */
    public void setWatchdogManager(in.annupaper.service.WatchdogManager watchdogManager) {
        this.watchdogManager = watchdogManager;
    }

    /**
     * Process incoming tick and update 1-minute candle.
     */
    @Override
    public void onTick(BrokerAdapter.Tick tick) {
        totalTicks.incrementAndGet();

        // ✅ P0-D: Dedupe check BEFORE any processing
        String dedupeKey = generateDedupeKey(tick);

        // Check if tick is duplicate (check both windows for grace period)
        if (currentDedupeWindow.contains(dedupeKey) || previousDedupeWindow.contains(dedupeKey)) {
            duplicateTicks.incrementAndGet();
            log.trace("Duplicate tick detected: {} (key: {})", tick.symbol(), dedupeKey);
            return;  // Skip duplicate
        }

        // Add to current window
        currentDedupeWindow.add(dedupeKey);

        // Rotate windows if needed (every 60 seconds)
        rotateWindowsIfNeeded();

        String symbol = tick.symbol();
        BigDecimal price = tick.lastPrice();
        long volume = tick.volume();
        Instant timestamp = Instant.ofEpochMilli(tick.timestamp());

        // Skip if outside market hours
        if (!SessionClock.isWithinSession(timestamp)) {
            log.debug("Tick outside market hours for {}: {}", symbol, timestamp);
            return;
        }

        // Record tick for watchdog liveness tracking
        if (watchdogManager != null) {
            watchdogManager.recordTick(symbol);
        }

        // Update in-memory cache for Market Watch (fast!)
        marketDataCache.updateTick(symbol, price, timestamp);

        // Publish TICK event to WebSocket for Market Watch real-time updates
        Map<String, Object> tickPayload = new HashMap<>();
        tickPayload.put("symbol", symbol);
        tickPayload.put("lastPrice", price);
        tickPayload.put("volume", volume);
        tickPayload.put("timestamp", timestamp.toString());
        eventService.emitGlobal(EventType.TICK, tickPayload, "TICK_BUILDER");

        // Only build 1-minute candles from ticks
        update1MinuteCandle(symbol, price, volume, timestamp);
    }

    /**
     * ✅ P0-D: Generate dedupe key for tick.
     *
     * Primary key: symbol + exchangeTimestamp + lastPrice + volume
     * Fallback key (when exchange timestamp missing/invalid):
     *   symbol + lastPrice + volume + systemTimeRoundedToSecond
     *
     * This ensures:
     * - Same tick from broker (with exchange timestamp) is deduplicated
     * - High-frequency ticks without exchange timestamp use fallback
     * - Bounded memory: keys are strings, not keeping full tick objects
     */
    private String generateDedupeKey(BrokerAdapter.Tick tick) {
        String symbol = tick.symbol();
        long exchangeTimestamp = tick.timestamp();
        BigDecimal price = tick.lastPrice();
        long volume = tick.volume();

        // Primary dedupe key: use exchange timestamp if available
        if (exchangeTimestamp > 0) {
            // Format: SYMBOL|exchangeTimestamp|price|volume
            return String.format("%s|%d|%s|%d", symbol, exchangeTimestamp, price, volume);
        }

        // ✅ Fallback dedupe key: exchange timestamp missing
        missingExchangeTimestamp.incrementAndGet();

        // Use system time rounded to second (provides ~1 second dedupe window)
        long systemTimeSeconds = Instant.now().getEpochSecond();

        // Format: SYMBOL|SYS:systemTimeSeconds|price|volume
        return String.format("%s|SYS:%d|%s|%d", symbol, systemTimeSeconds, price, volume);
    }

    /**
     * ✅ P0-D: Rotate dedupe windows every 60 seconds.
     *
     * Pattern: Two-window rotation (not removeIf!)
     * - Swap current -> previous
     * - Clear new current window
     * - Bounded memory: previous window cleared on next rotation
     *
     * This ensures:
     * - No O(n) removeIf cleanup during tick processing
     * - Grace period: ticks in previous window still deduplicated
     * - Automatic memory reclamation every 60 seconds
     */
    private void rotateWindowsIfNeeded() {
        Instant now = Instant.now();
        long secondsSinceLastSwap = ChronoUnit.SECONDS.between(lastWindowSwap, now);

        if (secondsSinceLastSwap < 60) {
            return;  // Not time to rotate yet
        }

        // Try to acquire lock (non-blocking)
        if (!windowSwapLock.tryLock()) {
            return;  // Another thread is already swapping
        }

        try {
            // Double-check after acquiring lock
            secondsSinceLastSwap = ChronoUnit.SECONDS.between(lastWindowSwap, now);
            if (secondsSinceLastSwap < 60) {
                return;
            }

            // Rotate windows
            int previousSize = previousDedupeWindow.size();
            int currentSize = currentDedupeWindow.size();

            // Swap: current -> previous
            previousDedupeWindow = currentDedupeWindow;

            // Create new empty current window
            currentDedupeWindow = ConcurrentHashMap.newKeySet();

            // Update last swap time
            lastWindowSwap = now;

            log.info("✅ Dedupe windows rotated: previous={} keys discarded, current={} keys moved to previous",
                previousSize, currentSize);

        } finally {
            windowSwapLock.unlock();
        }
    }

    /**
     * Update 1-minute candle from tick.
     */
    private void update1MinuteCandle(
        String symbol,
        BigDecimal price,
        long volume,
        Instant timestamp
    ) {
        PartialCandle partial = partialCandles.get(symbol);

        // Get 1-minute candle start time using SessionClock
        Instant candleStart = SessionClock.floorToMinute(timestamp);

        // Check if we need to close existing candle and start new one
        if (partial != null && !candleStart.equals(partial.startTime)) {
            // Close and persist previous candle
            close1MinuteCandle(symbol, partial);

            // Detect gaps and trigger backfill if needed
            checkAndBackfillGap(symbol, partial.startTime, candleStart);

            partial = null;
        }

        // Create new partial candle if needed
        if (partial == null) {
            partial = new PartialCandle(candleStart, price, price, price, price, volume);
            partialCandles.put(symbol, partial);
        } else {
            // Update existing partial candle
            partial.high = partial.high.max(price);
            partial.low = partial.low.min(price);
            partial.close = price;
            partial.volume += volume;
        }
    }

    /**
     * Check for gaps and trigger backfill if needed.
     */
    private void checkAndBackfillGap(String symbol, Instant lastCandleStart, Instant currentCandleStart) {
        // Expected next candle start is 1 minute after last
        Instant expectedNext = lastCandleStart.plus(1, ChronoUnit.MINUTES);

        // If there's a gap (more than 1 minute), trigger backfill
        if (expectedNext.isBefore(currentCandleStart)) {
            long gapMinutes = ChronoUnit.MINUTES.between(expectedNext, currentCandleStart);
            log.info("Gap detected for {} 1-min: {} minutes (from {} to {})",
                symbol, gapMinutes, expectedNext, currentCandleStart);

            // Trigger backfill asynchronously to avoid blocking tick processing
            CompletableFuture.runAsync(() -> {
                historyBackfiller.backfillIfNeeded(symbol, TimeframeType.MINUTE_1, currentCandleStart);
            }).exceptionally(ex -> {
                log.error("Failed to backfill gap for {}: {}", symbol, ex.getMessage());
                return null;
            });
        }
    }

    /**
     * Close and persist a completed 1-minute candle.
     */
    private void close1MinuteCandle(String symbol, PartialCandle partial) {
        Candle candle = Candle.of(
            symbol,
            TimeframeType.MINUTE_1,
            partial.startTime,
            partial.open.doubleValue(),
            partial.high.doubleValue(),
            partial.low.doubleValue(),
            partial.close.doubleValue(),
            partial.volume
        );

        // Store candle (in-memory + PostgreSQL)
        candleStore.addIntraday(candle);

        // Emit CANDLE event for 1-minute
        Map<String, Object> payload = new HashMap<>();
        payload.put("symbol", symbol);
        payload.put("timeframe", TimeframeType.MINUTE_1.name());
        payload.put("timestamp", partial.startTime.toEpochMilli());
        payload.put("open", partial.open);
        payload.put("high", partial.high);
        payload.put("low", partial.low);
        payload.put("close", partial.close);
        payload.put("volume", partial.volume);

        eventService.emitGlobal(EventType.CANDLE, payload, "TICK_BUILDER");

        log.debug("Closed 1-min candle: {} @ {}", symbol, partial.startTime);

        // Trigger aggregation for 25-min and 125-min candles
        candleAggregator.on1MinuteCandleClose(symbol, candle);
    }

    /**
     * Force close all partial 1-minute candles (on shutdown or market close).
     */
    public void closeAll() {
        log.info("Closing all partial 1-minute candles");

        for (Map.Entry<String, PartialCandle> entry : partialCandles.entrySet()) {
            String symbol = entry.getKey();
            PartialCandle partial = entry.getValue();

            if (partial != null) {
                close1MinuteCandle(symbol, partial);
            }
        }

        partialCandles.clear();
    }

    /**
     * Time-based finalizer - Close candles if minute boundary has passed.
     * Should be called periodically (every 1-5 seconds) by a scheduler.
     */
    public void finalizeStaleCandles() {
        Instant now = Instant.now();

        if (!SessionClock.isWithinSession(now)) {
            return;  // Skip if outside market hours
        }

        Instant currentMinuteBoundary = SessionClock.floorToMinute(now);

        for (Map.Entry<String, PartialCandle> entry : partialCandles.entrySet()) {
            String symbol = entry.getKey();
            PartialCandle partial = entry.getValue();

            if (partial != null && partial.startTime.isBefore(currentMinuteBoundary)) {
                // Candle is from previous minute, close it
                close1MinuteCandle(symbol, partial);

                // Check for gaps
                checkAndBackfillGap(symbol, partial.startTime, currentMinuteBoundary);

                // Remove from map (will be recreated on next tick)
                partialCandles.remove(symbol);
            }
        }
    }

    /**
     * ✅ P0-D: Get dedupe metrics (for monitoring/health checks).
     */
    public Map<String, Long> getDedupeMetrics() {
        Map<String, Long> metrics = new HashMap<>();
        metrics.put("totalTicks", totalTicks.get());
        metrics.put("duplicateTicks", duplicateTicks.get());
        metrics.put("missingExchangeTimestamp", missingExchangeTimestamp.get());
        metrics.put("currentWindowSize", (long) currentDedupeWindow.size());
        metrics.put("previousWindowSize", (long) previousDedupeWindow.size());

        // Calculate dedupe rate
        long total = totalTicks.get();
        if (total > 0) {
            long dupes = duplicateTicks.get();
            metrics.put("dedupeRatePercent", (dupes * 100) / total);
        } else {
            metrics.put("dedupeRatePercent", 0L);
        }

        return metrics;
    }

    /**
     * ✅ P0-D: Clear dedupe windows (called on shutdown).
     */
    public void clearDedupeWindows() {
        log.info("Clearing dedupe windows: current={} keys, previous={} keys",
            currentDedupeWindow.size(), previousDedupeWindow.size());

        currentDedupeWindow.clear();
        previousDedupeWindow.clear();
    }

    /**
     * Partial candle being built from ticks.
     */
    private static class PartialCandle {
        final Instant startTime;
        final BigDecimal open;
        BigDecimal high;
        BigDecimal low;
        BigDecimal close;
        long volume;

        PartialCandle(Instant startTime, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, long volume) {
            this.startTime = startTime;
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
            this.volume = volume;
        }
    }
}
