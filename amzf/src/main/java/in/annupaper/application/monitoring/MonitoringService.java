package in.annupaper.application.monitoring;

import in.annupaper.domain.monitoring.*;
import in.annupaper.domain.trade.ExitIntent;
import in.annupaper.domain.broker.UserBroker;
import in.annupaper.repository.ExitIntentRepository;
import in.annupaper.repository.TradeRepository;
import in.annupaper.repository.UserBrokerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * System health monitoring service.
 *
 * Performs scheduled health checks and provides monitoring endpoints.
 * Replaces standalone SQL monitoring files with Java-based monitoring.
 */
public final class MonitoringService {
    private static final Logger log = LoggerFactory.getLogger(MonitoringService.class);

    private final ExitIntentRepository exitIntentRepository;
    private final TradeRepository tradeRepository;
    private final UserBrokerRepository userBrokerRepository;
    private final AlertService alertService;
    private final ScheduledExecutorService scheduler;

    public MonitoringService(
            ExitIntentRepository exitIntentRepository,
            TradeRepository tradeRepository,
            UserBrokerRepository userBrokerRepository,
            AlertService alertService) {
        this.exitIntentRepository = exitIntentRepository;
        this.tradeRepository = tradeRepository;
        this.userBrokerRepository = userBrokerRepository;
        this.alertService = alertService;
        this.scheduler = Executors.newScheduledThreadPool(3, r -> {
            Thread t = new Thread(r, "monitoring-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Start scheduled monitoring tasks.
     * Call this after application initialization.
     */
    public void start() {
        log.info("Starting monitoring service...");

        // Check for expired broker sessions (CRITICAL - Every 1 minute)
        scheduler.scheduleAtFixedRate(
            this::checkBrokerSessionsExpired,
            0,
            60,
            TimeUnit.SECONDS
        );

        // Check for stuck exit orders (CRITICAL - Every 1 minute)
        scheduler.scheduleAtFixedRate(
            this::checkStuckExitOrders,
            0,
            60,
            TimeUnit.SECONDS
        );

        // Check for broker sessions expiring soon (HIGH - Every 5 minutes)
        scheduler.scheduleAtFixedRate(
            this::checkBrokerSessionsExpiringSoon,
            0,
            300,
            TimeUnit.SECONDS
        );

        log.info("✓ Monitoring service started with 3 scheduled health checks");
    }

    /**
     * Stop scheduled monitoring tasks.
     * Call this during application shutdown.
     */
    public void stop() {
        log.info("Stopping monitoring service...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            log.info("✓ Monitoring service stopped");
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ========================================================================
    // SCHEDULED MONITORING TASKS (P0/P1 Critical Alerts)
    // ========================================================================

    /**
     * Check for expired broker sessions (CRITICAL - Every 1 minute).
     * Triggers immediate alert if broker session has expired.
     */
    void checkBrokerSessionsExpired() {
        try {
            log.debug("Checking for expired broker sessions...");

            List<UserBroker> expiredSessions = userBrokerRepository.findExpiredBrokerSessions();

            if (!expiredSessions.isEmpty()) {
                Alert alert = Alert.builder()
                    .alertType("BROKER_SESSION_EXPIRED")
                    .level(AlertLevel.CRITICAL)
                    .message(String.format("%d broker session(s) expired - re-authentication required immediately",
                        expiredSessions.size()))
                    .detail("expired_count", expiredSessions.size())
                    .detail("expired_brokers", expiredSessions)
                    .build();

                alertService.sendAlert(alert);
                log.error("CRITICAL: {} broker sessions expired", expiredSessions.size());
            }
        } catch (Exception e) {
            log.error("Error checking expired broker sessions: {}", e.getMessage(), e);
        }
    }

    /**
     * Check for stuck exit orders (CRITICAL - Every 1 minute).
     * Triggers alert if exit orders haven't been filled for more than 10 minutes.
     */
    void checkStuckExitOrders() {
        try {
            log.debug("Checking for stuck exit orders...");

            List<ExitIntent> stuckOrders = exitIntentRepository.findStuckExitIntents(10);

            if (!stuckOrders.isEmpty()) {
                Alert alert = Alert.builder()
                    .alertType("STUCK_EXIT_ORDER")
                    .level(AlertLevel.CRITICAL)
                    .message(String.format("%d exit order(s) stuck for more than 10 minutes - check broker connectivity",
                        stuckOrders.size()))
                    .detail("stuck_count", stuckOrders.size())
                    .detail("stuck_orders", stuckOrders)
                    .build();

                alertService.sendAlert(alert);
                log.error("CRITICAL: {} exit orders stuck", stuckOrders.size());
            }
        } catch (Exception e) {
            log.error("Error checking stuck exit orders: {}", e.getMessage(), e);
        }
    }

    /**
     * Check for broker sessions expiring soon (HIGH - Every 5 minutes).
     * Triggers alert if broker session expires within 1 hour.
     */
    void checkBrokerSessionsExpiringSoon() {
        try {
            log.debug("Checking for broker sessions expiring soon...");

            long expiringSoon = userBrokerRepository.countExpiringSoonBrokerSessions();

            if (expiringSoon > 0) {
                Alert alert = Alert.builder()
                    .alertType("BROKER_SESSION_EXPIRING")
                    .level(AlertLevel.HIGH)
                    .message(String.format("%d broker session(s) expiring within 1 hour - schedule re-authentication",
                        expiringSoon))
                    .detail("expiring_count", expiringSoon)
                    .build();

                alertService.sendAlert(alert);
                log.warn("HIGH: {} broker sessions expiring soon", expiringSoon);
            }
        } catch (Exception e) {
            log.error("Error checking expiring broker sessions: {}", e.getMessage(), e);
        }
    }

    // ========================================================================
    // SYSTEM HEALTH SNAPSHOT
    // ========================================================================

    /**
     * Get current system health snapshot.
     * Used by dashboard and health endpoints.
     *
     * @return System health snapshot
     */
    public SystemHealthSnapshot getSystemHealthSnapshot() {
        log.debug("Generating system health snapshot...");

        // Get trade metrics
        Map<String, Object> tradeMetrics = tradeRepository.getTradeHealthMetrics();

        // Get broker metrics
        long activeBrokers = userBrokerRepository.countActiveBrokers();
        long expiredSessions = userBrokerRepository.countExpiredBrokerSessions();
        long expiringSoon = userBrokerRepository.countExpiringSoonBrokerSessions();

        // Get pending operations
        long pendingExitIntents = exitIntentRepository.countPendingExitIntents();

        return new SystemHealthSnapshot(
            Instant.now(),
            ((Number) tradeMetrics.getOrDefault("total_open_trades", 0)).intValue(),
            ((Number) tradeMetrics.getOrDefault("long_positions", 0)).intValue(),
            ((Number) tradeMetrics.getOrDefault("short_positions", 0)).intValue(),
            ((Number) tradeMetrics.getOrDefault("total_exposure_value", 0.0)).doubleValue(),
            ((Number) tradeMetrics.getOrDefault("avg_holding_hours", 0.0)).doubleValue(),
            0, // pending trade intents - would need to add to repository
            (int) pendingExitIntents,
            0, // pending orders - would need to add to repository
            (int) activeBrokers,
            (int) expiredSessions,
            (int) expiringSoon
        );
    }

    /**
     * Get daily performance metrics for today.
     *
     * @return Daily performance metrics
     */
    public DailyPerformance getDailyPerformance() {
        log.debug("Generating daily performance metrics...");

        Map<String, Object> metrics = tradeRepository.getDailyPerformanceMetrics();

        if (metrics.isEmpty()) {
            // No trades closed today
            return new DailyPerformance(
                LocalDate.now(),
                0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0
            );
        }

        return new DailyPerformance(
            LocalDate.now(),
            ((Number) metrics.get("trades_closed")).intValue(),
            ((Number) metrics.get("winning_trades")).intValue(),
            ((Number) metrics.get("losing_trades")).intValue(),
            ((Number) metrics.get("win_rate_percent")).doubleValue(),
            ((Number) metrics.get("total_pnl")).doubleValue(),
            ((Number) metrics.get("avg_pnl_per_trade")).doubleValue(),
            ((Number) metrics.get("best_trade")).doubleValue(),
            ((Number) metrics.get("worst_trade")).doubleValue()
        );
    }

    /**
     * Check if system is healthy overall.
     *
     * @return true if all critical checks pass
     */
    public boolean isSystemHealthy() {
        SystemHealthSnapshot snapshot = getSystemHealthSnapshot();
        return snapshot.isHealthy();
    }
}
