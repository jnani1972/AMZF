package in.annupaper.service;

import in.annupaper.broker.BrokerAdapter;
import in.annupaper.broker.BrokerAdapterFactory;
import in.annupaper.domain.broker.Broker;
import in.annupaper.domain.broker.UserBroker;
import in.annupaper.domain.broker.UserBrokerSession;
import in.annupaper.domain.data.TimeframeType;
import in.annupaper.domain.data.Candle;
import in.annupaper.repository.BrokerRepository;
import in.annupaper.repository.UserBrokerRepository;
import in.annupaper.repository.UserBrokerSessionRepository;
import in.annupaper.repository.WatchlistRepository;
import in.annupaper.service.candle.*;
import in.annupaper.service.signal.ExitSignalService;
import in.annupaper.service.signal.MtfSignalGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Watchdog Manager - Self-healing system monitor.
 *
 * Monitors all critical components and automatically fixes issues:
 * - Broker adapter connection
 * - WebSocket connection
 * - Tick stream health
 * - Candle generation
 * - Database connection
 * - OAuth session validity
 *
 * Runs every 2 minutes to ensure system health.
 */
public final class WatchdogManager {
    private static final Logger log = LoggerFactory.getLogger(WatchdogManager.class);

    private final DataSource dataSource;
    private final BrokerAdapterFactory brokerFactory;
    private final UserBrokerRepository userBrokerRepo;
    private final BrokerRepository brokerRepo;
    private final WatchlistRepository watchlistRepo;
    private final UserBrokerSessionRepository sessionRepo;
    private final CandleStore candleStore;
    private final TickCandleBuilder tickCandleBuilder;
    private final ExitSignalService exitSignalService;
    private final MtfSignalGenerator mtfSignalGenerator;
    private final RecoveryManager recoveryManager;
    private final MtfBackfillService mtfBackfillService;

    // Track last successful tick per symbol
    private final Map<String, Instant> lastTickTimestamp = new ConcurrentHashMap<>();

    // Track last health check results
    private Instant lastHealthCheck = Instant.now();
    private int consecutiveFailures = 0;

    public WatchdogManager(
        DataSource dataSource,
        BrokerAdapterFactory brokerFactory,
        UserBrokerRepository userBrokerRepo,
        BrokerRepository brokerRepo,
        WatchlistRepository watchlistRepo,
        UserBrokerSessionRepository sessionRepo,
        CandleStore candleStore,
        TickCandleBuilder tickCandleBuilder,
        ExitSignalService exitSignalService,
        MtfSignalGenerator mtfSignalGenerator,
        RecoveryManager recoveryManager,
        MtfBackfillService mtfBackfillService
    ) {
        this.dataSource = dataSource;
        this.brokerFactory = brokerFactory;
        this.userBrokerRepo = userBrokerRepo;
        this.brokerRepo = brokerRepo;
        this.watchlistRepo = watchlistRepo;
        this.sessionRepo = sessionRepo;
        this.candleStore = candleStore;
        this.tickCandleBuilder = tickCandleBuilder;
        this.exitSignalService = exitSignalService;
        this.mtfSignalGenerator = mtfSignalGenerator;
        this.recoveryManager = recoveryManager;
        this.mtfBackfillService = mtfBackfillService;
    }

    /**
     * Record tick received for a symbol.
     * Called by TickCandleBuilder to track liveness.
     */
    public void recordTick(String symbol) {
        lastTickTimestamp.put(symbol, Instant.now());
    }

    /**
     * Run health checks and auto-heal if needed.
     * Called by scheduled executor every 2 minutes.
     */
    public void performHealthCheck() {
        lastHealthCheck = Instant.now();

        log.info("[WATCHDOG] ════════════════════════════════════════════════════════");
        log.info("[WATCHDOG] Starting health check at {}", lastHealthCheck);
        log.info("[WATCHDOG] ════════════════════════════════════════════════════════");

        int issuesDetected = 0;
        int issuesFixed = 0;

        try {
            // 1. Database health
            if (!checkDatabaseHealth()) {
                issuesDetected++;
                if (healDatabase()) issuesFixed++;
            } else {
                log.info("[WATCHDOG] ✓ Database connection healthy");
            }

            // 2. Data broker health
            if (!checkDataBrokerHealth()) {
                issuesDetected++;
                if (healDataBroker()) issuesFixed++;
            } else {
                log.info("[WATCHDOG] ✓ Data broker connection healthy");
            }

            // 3. WebSocket health
            if (!checkWebSocketHealth()) {
                issuesDetected++;
                if (healWebSocket()) issuesFixed++;
            } else {
                log.info("[WATCHDOG] ✓ WebSocket connection healthy");
            }

            // 4. OAuth session health
            if (!checkSessionHealth()) {
                issuesDetected++;
                log.warn("[WATCHDOG] ⚠ OAuth session expiring soon - manual refresh recommended");
            } else {
                log.info("[WATCHDOG] ✓ OAuth session healthy");
            }

            // 5. Tick stream health (only during market hours)
            if (isMarketOpen()) {
                if (!checkTickStreamHealth()) {
                    issuesDetected++;
                    if (healTickStream()) issuesFixed++;
                } else {
                    log.info("[WATCHDOG] ✓ Tick stream healthy");
                }
            } else {
                log.info("[WATCHDOG] ⊘ Market closed, skipping tick stream check");
            }

            // 6. Candle health
            if (!checkCandleHealth()) {
                issuesDetected++;
                if (healCandles()) issuesFixed++;
            } else {
                log.info("[WATCHDOG] ✓ Candle generation healthy");
            }

            // Update failure counter
            if (issuesDetected > 0) {
                consecutiveFailures++;
            } else {
                consecutiveFailures = 0;
            }

            log.info("[WATCHDOG] ════════════════════════════════════════════════════════");
            log.info("[WATCHDOG] Health check complete: {} issues detected, {} auto-fixed", issuesDetected, issuesFixed);
            log.info("[WATCHDOG] Consecutive failures: {}", consecutiveFailures);
            log.info("[WATCHDOG] ════════════════════════════════════════════════════════");

        } catch (Exception e) {
            log.error("[WATCHDOG] Fatal error during health check: {}", e.getMessage(), e);
            consecutiveFailures++;
        }
    }

    /**
     * Check database connection health.
     */
    private boolean checkDatabaseHealth() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(5); // 5 second timeout
        } catch (Exception e) {
            log.error("[WATCHDOG] ✗ Database health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Heal database connection (mostly just logs, HikariCP auto-recovers).
     */
    private boolean healDatabase() {
        log.warn("[WATCHDOG] ⚕ Database connection issue detected, HikariCP will auto-recover");
        return false; // HikariCP handles recovery
    }

    /**
     * Check data broker adapter health.
     */
    private boolean checkDataBrokerHealth() {
        try {
            Optional<UserBroker> dataBrokerOpt = userBrokerRepo.findDataBroker();
            if (dataBrokerOpt.isEmpty()) {
                log.warn("[WATCHDOG] ✗ No data broker configured");
                return false;
            }

            String userBrokerId = dataBrokerOpt.get().userBrokerId();
            BrokerAdapter adapter = brokerFactory.get(userBrokerId);

            if (adapter == null) {
                log.warn("[WATCHDOG] ✗ Data broker adapter not found in cache");
                return false;
            }

            if (!adapter.isConnected()) {
                log.warn("[WATCHDOG] ✗ Data broker adapter disconnected");
                return false;
            }

            return true;

        } catch (Exception e) {
            log.error("[WATCHDOG] ✗ Data broker health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Heal data broker by reconnecting.
     */
    private boolean healDataBroker() {
        try {
            log.info("[WATCHDOG] ⚕ Attempting to heal data broker connection...");

            Optional<UserBroker> dataBrokerOpt = userBrokerRepo.findDataBroker();
            if (dataBrokerOpt.isEmpty()) {
                log.error("[WATCHDOG] Cannot heal: No data broker configured");
                return false;
            }

            UserBroker dataBroker = dataBrokerOpt.get();
            String userBrokerId = dataBroker.userBrokerId();

            Optional<Broker> brokerOpt = brokerRepo.findById(dataBroker.brokerId());
            if (brokerOpt.isEmpty()) {
                log.error("[WATCHDOG] Cannot heal: Broker not found");
                return false;
            }

            String brokerCode = brokerOpt.get().brokerCode();

            // Remove cached adapter and force recreation
            log.info("[WATCHDOG] Clearing cached adapter for {}", userBrokerId);
            brokerFactory.remove(userBrokerId);

            // Recreate adapter (will auto-connect with session)
            log.info("[WATCHDOG] Recreating adapter for {}", brokerCode);
            BrokerAdapter adapter = brokerFactory.getOrCreate(userBrokerId, brokerCode);

            if (adapter != null && adapter.isConnected()) {
                log.info("[WATCHDOG] ✓ Data broker reconnected successfully");

                // Resubscribe to tick stream
                healTickStream();

                return true;
            } else {
                log.error("[WATCHDOG] ✗ Failed to reconnect data broker");
                return false;
            }

        } catch (Exception e) {
            log.error("[WATCHDOG] ✗ Failed to heal data broker: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Check WebSocket connection health.
     */
    private boolean checkWebSocketHealth() {
        try {
            Optional<UserBroker> dataBrokerOpt = userBrokerRepo.findDataBroker();
            if (dataBrokerOpt.isEmpty()) {
                return true; // No data broker, no WebSocket needed
            }

            String userBrokerId = dataBrokerOpt.get().userBrokerId();
            BrokerAdapter adapter = brokerFactory.get(userBrokerId);

            if (adapter == null) {
                log.warn("[WATCHDOG] ✗ Data broker adapter not found");
                return false;
            }

            // Check if adapter is FyersAdapter (has WebSocket support)
            if (adapter instanceof in.annupaper.broker.adapters.FyersAdapter) {
                in.annupaper.broker.adapters.FyersAdapter fyersAdapter =
                    (in.annupaper.broker.adapters.FyersAdapter) adapter;

                if (!fyersAdapter.isWebSocketConnected()) {
                    log.warn("[WATCHDOG] ✗ WebSocket disconnected");
                    return false;
                }
            }

            return true;

        } catch (Exception e) {
            log.error("[WATCHDOG] ✗ WebSocket health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Heal WebSocket connection by reconnecting.
     */
    private boolean healWebSocket() {
        try {
            log.info("[WATCHDOG] ⚕ Attempting to heal WebSocket connection...");

            Optional<UserBroker> dataBrokerOpt = userBrokerRepo.findDataBroker();
            if (dataBrokerOpt.isEmpty()) {
                return false;
            }

            String userBrokerId = dataBrokerOpt.get().userBrokerId();
            BrokerAdapter adapter = brokerFactory.get(userBrokerId);

            if (adapter == null) {
                log.error("[WATCHDOG] Cannot heal WebSocket: adapter not found");
                return false;
            }

            // Check if adapter is FyersAdapter (has WebSocket support)
            if (adapter instanceof in.annupaper.broker.adapters.FyersAdapter) {
                in.annupaper.broker.adapters.FyersAdapter fyersAdapter =
                    (in.annupaper.broker.adapters.FyersAdapter) adapter;

                fyersAdapter.reconnectWebSocket();

                // Resubscribe after reconnection
                List<String> symbols = watchlistRepo.findByUserBrokerId(userBrokerId).stream()
                    .filter(w -> w.enabled())
                    .map(w -> w.symbol())
                    .distinct()
                    .toList();

                if (!symbols.isEmpty()) {
                    log.info("[WATCHDOG] Resubscribing to ticks for {} symbols after WebSocket reconnect", symbols.size());
                    adapter.subscribeTicks(symbols, tickCandleBuilder);
                    adapter.subscribeTicks(symbols, exitSignalService);
                    adapter.subscribeTicks(symbols, mtfSignalGenerator);
                }

                if (fyersAdapter.isWebSocketConnected()) {
                    log.info("[WATCHDOG] ✓ WebSocket reconnected successfully");
                    return true;
                } else {
                    log.error("[WATCHDOG] ✗ WebSocket reconnection failed");
                    return false;
                }
            }

            return true; // Not a FyersAdapter, no WebSocket to heal

        } catch (Exception e) {
            log.error("[WATCHDOG] ✗ Failed to heal WebSocket: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Check OAuth session health.
     */
    private boolean checkSessionHealth() {
        try {
            Optional<UserBroker> dataBrokerOpt = userBrokerRepo.findDataBroker();
            if (dataBrokerOpt.isEmpty()) {
                return true; // No data broker, no session needed
            }

            String userBrokerId = dataBrokerOpt.get().userBrokerId();
            Optional<UserBrokerSession> sessionOpt = sessionRepo.findActiveSession(userBrokerId);

            if (sessionOpt.isEmpty()) {
                log.warn("[WATCHDOG] ✗ No active OAuth session found");
                return false;
            }

            UserBrokerSession session = sessionOpt.get();
            Instant now = Instant.now();
            Instant validTill = session.tokenValidTill();

            if (validTill.isBefore(now)) {
                log.warn("[WATCHDOG] ✗ OAuth session expired");
                return false;
            }

            // Warn if expires within 1 hour
            if (validTill.isBefore(now.plus(1, ChronoUnit.HOURS))) {
                log.warn("[WATCHDOG] ⚠ OAuth session expires soon: {}", validTill);
                return false;
            }

            return true;

        } catch (Exception e) {
            log.error("[WATCHDOG] ✗ Session health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check tick stream health during market hours.
     */
    private boolean checkTickStreamHealth() {
        try {
            if (lastTickTimestamp.isEmpty()) {
                log.warn("[WATCHDOG] ✗ No ticks recorded yet");
                return false;
            }

            Instant now = Instant.now();
            int staleSymbols = 0;

            for (Map.Entry<String, Instant> entry : lastTickTimestamp.entrySet()) {
                String symbol = entry.getKey();
                Instant lastTick = entry.getValue();

                // If no tick for 5 minutes during market hours, consider stale
                if (lastTick.isBefore(now.minus(5, ChronoUnit.MINUTES))) {
                    log.warn("[WATCHDOG] ✗ Stale ticks for {}: last tick {}", symbol, lastTick);
                    staleSymbols++;
                }
            }

            if (staleSymbols > 0) {
                log.warn("[WATCHDOG] ✗ {} symbols have stale ticks", staleSymbols);
                return false;
            }

            return true;

        } catch (Exception e) {
            log.error("[WATCHDOG] ✗ Tick stream health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Heal tick stream by resubscribing.
     */
    private boolean healTickStream() {
        try {
            log.info("[WATCHDOG] ⚕ Attempting to heal tick stream...");

            Optional<UserBroker> dataBrokerOpt = userBrokerRepo.findDataBroker();
            if (dataBrokerOpt.isEmpty()) {
                return false;
            }

            String userBrokerId = dataBrokerOpt.get().userBrokerId();
            BrokerAdapter adapter = brokerFactory.get(userBrokerId);

            if (adapter == null || !adapter.isConnected()) {
                log.error("[WATCHDOG] Cannot heal tick stream: adapter not connected");
                return false;
            }

            // Get watchlist symbols
            List<String> symbols = watchlistRepo.findByUserBrokerId(userBrokerId).stream()
                .filter(w -> w.enabled())
                .map(w -> w.symbol())
                .distinct()
                .toList();

            if (symbols.isEmpty()) {
                log.warn("[WATCHDOG] No symbols to subscribe");
                return false;
            }

            // Resubscribe to ticks
            log.info("[WATCHDOG] Resubscribing to ticks for {} symbols", symbols.size());
            adapter.subscribeTicks(symbols, tickCandleBuilder);
            adapter.subscribeTicks(symbols, exitSignalService);
            adapter.subscribeTicks(symbols, mtfSignalGenerator);

            log.info("[WATCHDOG] ✓ Tick stream resubscribed");
            return true;

        } catch (Exception e) {
            log.error("[WATCHDOG] ✗ Failed to heal tick stream: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Check candle generation health.
     */
    private boolean checkCandleHealth() {
        try {
            // Get data broker watchlist symbols
            Optional<UserBroker> dataBrokerOpt = userBrokerRepo.findDataBroker();
            if (dataBrokerOpt.isEmpty()) {
                return true; // No data broker, no candles needed
            }

            List<String> symbols = watchlistRepo.findByUserBrokerId(dataBrokerOpt.get().userBrokerId()).stream()
                .filter(w -> w.enabled())
                .map(w -> w.symbol())
                .distinct()
                .limit(3) // Check first 3 symbols as sample
                .toList();

            if (symbols.isEmpty()) {
                return true;
            }

            Instant now = Instant.now();
            int issueCount = 0;

            for (String symbol : symbols) {
                // Check 1-min candles
                Candle latest1Min = candleStore.getLatest(symbol, TimeframeType.MINUTE_1);
                if (latest1Min == null || latest1Min.timestamp().isBefore(now.minus(10, ChronoUnit.MINUTES))) {
                    log.warn("[WATCHDOG] ✗ Stale 1-min candles for {}", symbol);
                    issueCount++;
                }

                // Check 125-min candles (MTF HTF)
                Candle latest125Min = candleStore.getLatest(symbol, TimeframeType.MINUTE_125);
                if (latest125Min == null) {
                    log.warn("[WATCHDOG] ✗ No 125-min candles for {}", symbol);
                    issueCount++;
                }
            }

            return issueCount == 0;

        } catch (Exception e) {
            log.error("[WATCHDOG] ✗ Candle health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Heal candle generation by triggering recovery/backfill.
     */
    private boolean healCandles() {
        try {
            log.info("[WATCHDOG] ⚕ Attempting to heal candle generation...");

            Optional<UserBroker> dataBrokerOpt = userBrokerRepo.findDataBroker();
            if (dataBrokerOpt.isEmpty()) {
                return false;
            }

            String userBrokerId = dataBrokerOpt.get().userBrokerId();

            List<String> symbols = watchlistRepo.findByUserBrokerId(userBrokerId).stream()
                .filter(w -> w.enabled())
                .map(w -> w.symbol())
                .distinct()
                .toList();

            if (symbols.isEmpty()) {
                return false;
            }

            // Run recovery for all symbols
            log.info("[WATCHDOG] Running recovery for {} symbols", symbols.size());
            recoveryManager.recoverAll(symbols);

            log.info("[WATCHDOG] ✓ Candle recovery triggered");
            return true;

        } catch (Exception e) {
            log.error("[WATCHDOG] ✗ Failed to heal candles: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Check if market is currently open (NSE hours: 9:15 AM - 3:30 PM IST).
     */
    private boolean isMarketOpen() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
        int hour = now.getHour();
        int minute = now.getMinute();

        // Market hours: 9:15 AM - 3:30 PM
        int currentMinutes = hour * 60 + minute;
        int marketOpen = 9 * 60 + 15;   // 9:15 AM
        int marketClose = 15 * 60 + 30; // 3:30 PM

        // Skip weekends
        int dayOfWeek = now.getDayOfWeek().getValue();
        if (dayOfWeek == 6 || dayOfWeek == 7) { // Saturday or Sunday
            return false;
        }

        return currentMinutes >= marketOpen && currentMinutes <= marketClose;
    }

    /**
     * Get health status summary.
     */
    public String getHealthSummary() {
        return String.format(
            "Watchdog Status: Last check=%s, Consecutive failures=%d, Ticks tracked=%d",
            lastHealthCheck,
            consecutiveFailures,
            lastTickTimestamp.size()
        );
    }
}
