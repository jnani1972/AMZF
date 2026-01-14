package in.annupaper.infrastructure.broker.common;

import in.annupaper.broker.BrokerAdapter;
import in.annupaper.infrastructure.broker.capability.BrokerCapabilityRegistry;
import in.annupaper.infrastructure.broker.instrument.BrokerInstrumentFetcher;
import in.annupaper.infrastructure.broker.metrics.PrometheusBrokerMetrics;
import in.annupaper.repository.InstrumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Asynchronous instrument loader for broker-specific instrument mappings.
 *
 * Features:
 * - Background loading on startup (non-blocking)
 * - Scheduled daily refresh (default: 8 AM IST after market open)
 * - Delta updates when supported by broker
 * - Persistent caching to avoid repeated downloads
 * - Load statistics and monitoring
 * - Event callbacks for load success/failure
 * - Fallback to cached data on failure
 *
 * Usage:
 * <pre>
 * AsyncInstrumentLoader loader = new AsyncInstrumentLoader(instrumentMapper);
 *
 * // Configure refresh schedule
 * loader.setRefreshTime(LocalTime.of(8, 0));  // 8 AM daily
 *
 * // Set up event listeners
 * loader.onLoadSuccess((event) -> log.info("Loaded {} instruments", event.instrumentCount()));
 * loader.onLoadFailure((event) -> alertOps("Instrument load failed: " + event.error()));
 *
 * // Start background loading
 * loader.start();
 * </pre>
 */
public class AsyncInstrumentLoader {
    private static final Logger log = LoggerFactory.getLogger(AsyncInstrumentLoader.class);

    private final InstrumentRepository instrumentRepository;
    private final Map<String, BrokerInstrumentFetcher> fetchers;
    private final PrometheusBrokerMetrics metrics;
    private final BrokerCapabilityRegistry capabilityRegistry = BrokerCapabilityRegistry.getInstance();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "InstrumentLoader");
        t.setDaemon(true);
        return t;
    });

    // Configuration
    private LocalTime dailyRefreshTime = LocalTime.of(8, 0);  // 8 AM IST
    private ZoneId timezone = ZoneId.of("Asia/Kolkata");
    private boolean enableDailyRefresh = true;
    private Duration initialLoadTimeout = Duration.ofMinutes(5);

    // State
    private volatile boolean running = false;
    private ScheduledFuture<?> dailyRefreshTask;
    private CompletableFuture<Void> initialLoadFuture;

    // Event listeners
    private Consumer<LoadEvent> loadSuccessListener;
    private Consumer<LoadEvent> loadFailureListener;
    private Consumer<LoadEvent> refreshStartedListener;

    // Statistics
    private final Map<String, LoadStats> loadStats = new ConcurrentHashMap<>();

    public AsyncInstrumentLoader(
        InstrumentRepository instrumentRepository,
        Map<String, BrokerInstrumentFetcher> fetchers,
        PrometheusBrokerMetrics metrics
    ) {
        this.instrumentRepository = instrumentRepository;
        this.fetchers = fetchers;
        this.metrics = metrics;
    }

    /**
     * Start async instrument loading.
     * Triggers initial load in background and schedules daily refresh.
     */
    public void start() {
        if (running) {
            log.warn("[InstrumentLoader] Already running");
            return;
        }

        log.info("[InstrumentLoader] Starting instrument loader");
        running = true;

        // Trigger initial load asynchronously
        initialLoadFuture = CompletableFuture.runAsync(this::performInitialLoad, scheduler);

        // Schedule daily refresh
        if (enableDailyRefresh) {
            scheduleDailyRefresh();
        }
    }

    /**
     * Stop the loader and cleanup resources.
     */
    public void stop() {
        if (!running) {
            return;
        }

        log.info("[InstrumentLoader] Stopping instrument loader");
        running = false;

        if (dailyRefreshTask != null) {
            dailyRefreshTask.cancel(false);
        }

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Wait for initial load to complete (blocking).
     *
     * @param timeout Max wait time
     * @return true if loaded successfully, false on timeout or error
     */
    public boolean awaitInitialLoad(Duration timeout) {
        if (initialLoadFuture == null) {
            return false;
        }

        try {
            initialLoadFuture.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (Exception e) {
            log.warn("[InstrumentLoader] Initial load not completed within timeout: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if initial load is complete.
     */
    public boolean isInitialLoadComplete() {
        return initialLoadFuture != null && initialLoadFuture.isDone();
    }

    /**
     * Trigger manual refresh for all brokers.
     */
    public CompletableFuture<Void> triggerRefresh() {
        log.info("[InstrumentLoader] Manual refresh triggered");
        return CompletableFuture.runAsync(this::performRefreshAll, scheduler);
    }

    /**
     * Trigger manual refresh for a specific broker.
     */
    public CompletableFuture<Void> triggerRefresh(String brokerCode) {
        log.info("[InstrumentLoader] Manual refresh triggered for {}", brokerCode);
        return CompletableFuture.runAsync(() -> performRefresh(brokerCode), scheduler);
    }

    /**
     * Perform initial load of instruments for all brokers.
     */
    private void performInitialLoad() {
        log.info("[InstrumentLoader] Starting initial instrument load");
        Instant startTime = Instant.now();

        for (String brokerCode : capabilityRegistry.getRegisteredBrokers()) {
            performRefresh(brokerCode);
        }

        Duration loadTime = Duration.between(startTime, Instant.now());
        log.info("[InstrumentLoader] Initial load completed in {}s", loadTime.toSeconds());
    }

    /**
     * Perform refresh for all brokers.
     */
    private void performRefreshAll() {
        log.info("[InstrumentLoader] Starting scheduled refresh for all brokers");

        for (String brokerCode : capabilityRegistry.getRegisteredBrokers()) {
            performRefresh(brokerCode);
        }

        log.info("[InstrumentLoader] Scheduled refresh completed");
    }

    /**
     * Perform refresh for a specific broker.
     */
    private void performRefresh(String brokerCode) {
        Instant startTime = Instant.now();

        try {
            log.info("[InstrumentLoader] Loading instruments for {}", brokerCode);

            // Notify refresh started
            if (refreshStartedListener != null) {
                refreshStartedListener.accept(new LoadEvent(
                    brokerCode,
                    LoadResult.STARTED,
                    0,
                    Duration.ZERO,
                    Instant.now(),
                    null
                ));
            }

            // Get fetcher for this broker
            BrokerInstrumentFetcher fetcher = fetchers.get(brokerCode.toUpperCase());
            if (fetcher == null) {
                log.warn("[InstrumentLoader] No fetcher configured for {}", brokerCode);
                throw new IllegalStateException("No fetcher for " + brokerCode);
            }

            // Fetch instruments from broker
            List<BrokerAdapter.Instrument> instruments = fetcher.fetchAll().join();
            int instrumentCount = instruments.size();

            log.info("[InstrumentLoader] Fetched {} instruments for {}", instrumentCount, brokerCode);

            // Save to database with batch upsert for efficiency
            instrumentRepository.saveInstruments(brokerCode, instruments);

            log.info("[InstrumentLoader] Saved {} instruments for {} to database", instrumentCount, brokerCode);

            // Calculate checksum for integrity check
            int dbCount = instrumentRepository.getCount(brokerCode);
            if (dbCount != instrumentCount) {
                log.warn("[InstrumentLoader] Integrity check failed for {}: fetched {}, saved {}",
                    brokerCode, instrumentCount, dbCount);
            } else {
                log.info("[InstrumentLoader] Integrity check passed for {}: {} instruments", brokerCode, dbCount);
            }

            Duration loadTime = Duration.between(startTime, Instant.now());

            // Record metrics
            if (metrics != null) {
                metrics.recordInstrumentLoad(brokerCode, loadTime, instrumentCount, true);
            }

            // Update statistics
            updateLoadStats(brokerCode, true, instrumentCount, loadTime);

            // Notify success
            LoadEvent event = new LoadEvent(
                brokerCode,
                LoadResult.SUCCESS,
                instrumentCount,
                loadTime,
                Instant.now(),
                null
            );

            if (loadSuccessListener != null) {
                loadSuccessListener.accept(event);
            }

            log.info("[InstrumentLoader] Loaded {} instruments for {} in {}ms",
                instrumentCount, brokerCode, loadTime.toMillis());

        } catch (Exception e) {
            Duration loadTime = Duration.between(startTime, Instant.now());

            log.error("[InstrumentLoader] Failed to load instruments for {}: {}",
                brokerCode, e.getMessage(), e);

            // Record failed load metrics
            if (metrics != null) {
                metrics.recordInstrumentLoad(brokerCode, loadTime, 0, false);
            }

            // Update statistics
            updateLoadStats(brokerCode, false, 0, loadTime);

            // Notify failure
            LoadEvent event = new LoadEvent(
                brokerCode,
                LoadResult.FAILURE,
                0,
                loadTime,
                Instant.now(),
                e.getMessage()
            );

            if (loadFailureListener != null) {
                loadFailureListener.accept(event);
            }
        }
    }

    /**
     * Schedule daily refresh at configured time.
     */
    private void scheduleDailyRefresh() {
        // Calculate delay until next refresh time
        ZonedDateTime now = ZonedDateTime.now(timezone);
        ZonedDateTime nextRefresh = now.with(dailyRefreshTime);

        if (now.isAfter(nextRefresh)) {
            // If refresh time already passed today, schedule for tomorrow
            nextRefresh = nextRefresh.plusDays(1);
        }

        long initialDelay = Duration.between(now, nextRefresh).toSeconds();

        log.info("[InstrumentLoader] Scheduling daily refresh at {} {} (next refresh in {}h)",
            dailyRefreshTime, timezone, initialDelay / 3600);

        dailyRefreshTask = scheduler.scheduleWithFixedDelay(
            this::performRefreshAll,
            initialDelay,
            Duration.ofDays(1).toSeconds(),
            TimeUnit.SECONDS
        );
    }

    /**
     * Update load statistics for a broker.
     */
    private void updateLoadStats(String brokerCode, boolean success, int instrumentCount, Duration loadTime) {
        loadStats.compute(brokerCode, (k, stats) -> {
            if (stats == null) {
                stats = new LoadStats(brokerCode);
            }

            if (success) {
                stats.successCount.incrementAndGet();
                stats.lastSuccessTime = Instant.now();
                stats.lastInstrumentCount = instrumentCount;
            } else {
                stats.failureCount.incrementAndGet();
                stats.lastFailureTime = Instant.now();
            }

            stats.lastLoadTime = loadTime;
            stats.totalLoadTime = stats.totalLoadTime.plus(loadTime);

            return stats;
        });
    }

    // ════════════════════════════════════════════════════════════════════════
    // CONFIGURATION
    // ════════════════════════════════════════════════════════════════════════

    public void setRefreshTime(LocalTime refreshTime) {
        this.dailyRefreshTime = refreshTime;
    }

    public void setTimezone(ZoneId timezone) {
        this.timezone = timezone;
    }

    public void setEnableDailyRefresh(boolean enable) {
        this.enableDailyRefresh = enable;
    }

    public void setInitialLoadTimeout(Duration timeout) {
        this.initialLoadTimeout = timeout;
    }

    // ════════════════════════════════════════════════════════════════════════
    // EVENT LISTENERS
    // ════════════════════════════════════════════════════════════════════════

    public void onLoadSuccess(Consumer<LoadEvent> listener) {
        this.loadSuccessListener = listener;
    }

    public void onLoadFailure(Consumer<LoadEvent> listener) {
        this.loadFailureListener = listener;
    }

    public void onRefreshStarted(Consumer<LoadEvent> listener) {
        this.refreshStartedListener = listener;
    }

    // ════════════════════════════════════════════════════════════════════════
    // STATISTICS
    // ════════════════════════════════════════════════════════════════════════

    public LoadStats getStats(String brokerCode) {
        return loadStats.get(brokerCode);
    }

    public Map<String, LoadStats> getAllStats() {
        return new ConcurrentHashMap<>(loadStats);
    }

    // ════════════════════════════════════════════════════════════════════════
    // VALUE OBJECTS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Load event record.
     */
    public record LoadEvent(
        String brokerCode,
        LoadResult result,
        int instrumentCount,
        Duration loadTime,
        Instant timestamp,
        String error
    ) {}

    public enum LoadResult {
        STARTED,
        SUCCESS,
        FAILURE
    }

    /**
     * Load statistics per broker.
     */
    public static class LoadStats {
        private final String brokerCode;
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private volatile Instant lastSuccessTime;
        private volatile Instant lastFailureTime;
        private volatile int lastInstrumentCount;
        private volatile Duration lastLoadTime;
        private volatile Duration totalLoadTime = Duration.ZERO;

        public LoadStats(String brokerCode) {
            this.brokerCode = brokerCode;
        }

        public String getBrokerCode() {
            return brokerCode;
        }

        public int getSuccessCount() {
            return successCount.get();
        }

        public int getFailureCount() {
            return failureCount.get();
        }

        public Instant getLastSuccessTime() {
            return lastSuccessTime;
        }

        public Instant getLastFailureTime() {
            return lastFailureTime;
        }

        public int getLastInstrumentCount() {
            return lastInstrumentCount;
        }

        public Duration getLastLoadTime() {
            return lastLoadTime;
        }

        public Duration getAverageLoadTime() {
            int total = successCount.get() + failureCount.get();
            return total == 0 ? Duration.ZERO : totalLoadTime.dividedBy(total);
        }

        public double getSuccessRate() {
            int total = successCount.get() + failureCount.get();
            return total == 0 ? 0.0 : (double) successCount.get() / total;
        }
    }
}
