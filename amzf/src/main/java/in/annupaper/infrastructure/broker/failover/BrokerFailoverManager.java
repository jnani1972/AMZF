package in.annupaper.infrastructure.broker.failover;

import in.annupaper.infrastructure.broker.capability.BrokerCapabilityRegistry;
import in.annupaper.infrastructure.broker.metrics.PrometheusBrokerMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Broker failover manager for graceful degradation and health monitoring.
 *
 * Features:
 * - Continuous health monitoring for all brokers
 * - Automatic detection of broker failures
 * - Configurable degradation strategies (read-only, queueing, failover)
 * - Event notifications for state transitions
 * - Statistics and health status per broker
 * - Graceful recovery when broker comes back online
 *
 * Degradation Strategies:
 * 1. READ_ONLY: Disable new orders, allow monitoring/positions/cancellation
 * 2. QUEUEING: Queue critical orders, execute when broker recovers
 * 3. FAILOVER: Automatically switch to backup broker (if configured)
 * 4. ALERT_ONLY: Just notify, don't take action
 *
 * Usage:
 * <pre>
 * BrokerFailoverManager failover = new BrokerFailoverManager();
 *
 * // Configure degradation strategy
 * failover.setDegradationStrategy(DegradationStrategy.READ_ONLY);
 *
 * // Set up event listeners
 * failover.onBrokerDown((event) -> alertOps("Broker down: " + event.brokerCode()));
 * failover.onBrokerRecovered((event) -> log.info("Broker recovered: " + event.brokerCode()));
 * failover.onDegradationActivated((event) -> notifyUsers("Read-only mode: " + event.brokerCode()));
 *
 * // Start health monitoring
 * failover.start();
 *
 * // Check broker health before operations
 * if (failover.isBrokerHealthy("UPSTOX")) {
 *     // Place order
 * } else {
 *     // Handle degradation
 * }
 * </pre>
 */
public class BrokerFailoverManager {
    private static final Logger log = LoggerFactory.getLogger(BrokerFailoverManager.class);

    private final BrokerCapabilityRegistry capabilityRegistry = BrokerCapabilityRegistry.getInstance();
    private PrometheusBrokerMetrics metrics;  // Optional metrics collector

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "BrokerHealthMonitor");
        t.setDaemon(true);
        return t;
    });

    // Configuration
    private Duration healthCheckInterval = Duration.ofSeconds(30);
    private int consecutiveFailuresThreshold = 3;  // Mark as down after 3 failures
    private int consecutiveSuccessesThreshold = 2;  // Mark as up after 2 successes
    private DegradationStrategy degradationStrategy = DegradationStrategy.READ_ONLY;

    // State
    private volatile boolean running = false;
    private ScheduledFuture<?> healthCheckTask;

    // Health tracking per broker
    private final Map<String, BrokerHealth> brokerHealthMap = new ConcurrentHashMap<>();

    // Event listeners
    private Consumer<FailoverEvent> brokerDownListener;
    private Consumer<FailoverEvent> brokerRecoveredListener;
    private Consumer<FailoverEvent> degradationActivatedListener;
    private Consumer<FailoverEvent> degradationDeactivatedListener;

    /**
     * Start health monitoring.
     */
    public synchronized void start() {
        if (running) {
            log.warn("[FailoverManager] Already running");
            return;
        }

        log.info("[FailoverManager] Starting broker health monitoring (interval: {}s)",
            healthCheckInterval.toSeconds());
        running = true;

        // Initialize health for all registered brokers
        for (String brokerCode : capabilityRegistry.getRegisteredBrokers()) {
            brokerHealthMap.put(brokerCode, new BrokerHealth(brokerCode));
        }

        // Schedule periodic health checks
        healthCheckTask = scheduler.scheduleWithFixedDelay(
            this::performHealthChecks,
            0,  // Initial delay
            healthCheckInterval.toSeconds(),
            TimeUnit.SECONDS
        );
    }

    /**
     * Stop health monitoring.
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }

        log.info("[FailoverManager] Stopping broker health monitoring");
        running = false;

        if (healthCheckTask != null) {
            healthCheckTask.cancel(false);
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
     * Check if a broker is currently healthy.
     */
    public boolean isBrokerHealthy(String brokerCode) {
        BrokerHealth health = brokerHealthMap.get(brokerCode.toUpperCase());
        return health != null && health.status == BrokerStatus.HEALTHY;
    }

    /**
     * Check if a broker is in degraded mode.
     */
    public boolean isBrokerDegraded(String brokerCode) {
        BrokerHealth health = brokerHealthMap.get(brokerCode.toUpperCase());
        return health != null && health.status == BrokerStatus.DEGRADED;
    }

    /**
     * Check if a broker is down.
     */
    public boolean isBrokerDown(String brokerCode) {
        BrokerHealth health = brokerHealthMap.get(brokerCode.toUpperCase());
        return health != null && health.status == BrokerStatus.DOWN;
    }

    /**
     * Get health status for a broker.
     */
    public HealthStatus getHealthStatus(String brokerCode) {
        BrokerHealth health = brokerHealthMap.get(brokerCode.toUpperCase());
        if (health == null) {
            return null;
        }

        return new HealthStatus(
            health.brokerCode,
            health.status,
            health.consecutiveFailures.get(),
            health.consecutiveSuccesses.get(),
            health.totalChecks.get(),
            health.lastCheckTime,
            health.lastFailureTime,
            health.lastFailureReason,
            health.getUptime()
        );
    }

    /**
     * Get health status for all brokers.
     */
    public Map<String, HealthStatus> getAllHealthStatus() {
        Map<String, HealthStatus> statusMap = new ConcurrentHashMap<>();
        for (String brokerCode : brokerHealthMap.keySet()) {
            statusMap.put(brokerCode, getHealthStatus(brokerCode));
        }
        return statusMap;
    }

    /**
     * Manually mark a broker as down (for testing or manual intervention).
     */
    public void markBrokerDown(String brokerCode, String reason) {
        BrokerHealth health = brokerHealthMap.get(brokerCode.toUpperCase());
        if (health != null) {
            log.warn("[FailoverManager] Manually marking {} as down: {}", brokerCode, reason);
            transitionToDown(health, reason);
        }
    }

    /**
     * Manually mark a broker as recovered.
     */
    public void markBrokerRecovered(String brokerCode) {
        BrokerHealth health = brokerHealthMap.get(brokerCode.toUpperCase());
        if (health != null) {
            log.info("[FailoverManager] Manually marking {} as recovered", brokerCode);
            transitionToHealthy(health);
        }
    }

    /**
     * Perform health checks for all brokers.
     */
    private void performHealthChecks() {
        for (BrokerHealth health : brokerHealthMap.values()) {
            try {
                checkBrokerHealth(health);
            } catch (Exception e) {
                log.error("[FailoverManager] Error checking health for {}: {}",
                    health.brokerCode, e.getMessage(), e);
            }
        }
    }

    /**
     * Check health for a specific broker.
     */
    private void checkBrokerHealth(BrokerHealth health) {
        health.totalChecks.incrementAndGet();
        health.lastCheckTime = Instant.now();

        // TODO: Implement actual health check
        // Options:
        // 1. Ping broker API endpoint
        // 2. Check WebSocket connection status
        // 3. Monitor recent order success rate
        // 4. Check token validity

        boolean isHealthy = performHealthCheck(health.brokerCode);

        if (isHealthy) {
            handleHealthCheckSuccess(health);
        } else {
            handleHealthCheckFailure(health, "Health check failed");
        }
    }

    /**
     * Perform actual health check (placeholder).
     */
    private boolean performHealthCheck(String brokerCode) {
        // TODO: Implement actual health check logic
        // For now, assume all brokers are healthy
        return true;
    }

    /**
     * Handle successful health check.
     */
    private void handleHealthCheckSuccess(BrokerHealth health) {
        health.consecutiveFailures.set(0);
        health.consecutiveSuccesses.incrementAndGet();

        // If broker was down and now has enough consecutive successes, mark as recovered
        if (health.status != BrokerStatus.HEALTHY &&
            health.consecutiveSuccesses.get() >= consecutiveSuccessesThreshold) {

            log.info("[FailoverManager] Broker {} recovered after {} consecutive successes",
                health.brokerCode, health.consecutiveSuccesses.get());

            transitionToHealthy(health);
        }
    }

    /**
     * Handle failed health check.
     */
    private void handleHealthCheckFailure(BrokerHealth health, String reason) {
        health.consecutiveSuccesses.set(0);
        health.consecutiveFailures.incrementAndGet();
        health.lastFailureTime = Instant.now();
        health.lastFailureReason = reason;

        // If broker was healthy and now has enough consecutive failures, mark as down
        if (health.status == BrokerStatus.HEALTHY &&
            health.consecutiveFailures.get() >= consecutiveFailuresThreshold) {

            log.warn("[FailoverManager] Broker {} marked as down after {} consecutive failures: {}",
                health.brokerCode, health.consecutiveFailures.get(), reason);

            transitionToDown(health, reason);
        }
    }

    /**
     * Transition broker to HEALTHY status.
     */
    private void transitionToHealthy(BrokerHealth health) {
        BrokerStatus previousStatus = health.status;
        health.status = BrokerStatus.HEALTHY;
        health.statusChangedAt = Instant.now();

        // Record health status in metrics
        if (metrics != null) {
            Duration uptime = health.getUptime();
            metrics.updateHealthStatus(health.brokerCode, true, uptime);
        }

        if (previousStatus == BrokerStatus.DOWN || previousStatus == BrokerStatus.DEGRADED) {
            // Broker recovered
            FailoverEvent event = new FailoverEvent(
                health.brokerCode,
                FailoverEventType.BROKER_RECOVERED,
                previousStatus,
                BrokerStatus.HEALTHY,
                "Broker recovered after consecutive successful health checks",
                Instant.now()
            );

            if (brokerRecoveredListener != null) {
                brokerRecoveredListener.accept(event);
            }

            // Deactivate degradation
            if (previousStatus == BrokerStatus.DEGRADED && degradationDeactivatedListener != null) {
                FailoverEvent degradationEvent = new FailoverEvent(
                    health.brokerCode,
                    FailoverEventType.DEGRADATION_DEACTIVATED,
                    BrokerStatus.DEGRADED,
                    BrokerStatus.HEALTHY,
                    degradationStrategy.name() + " mode deactivated",
                    Instant.now()
                );
                degradationDeactivatedListener.accept(degradationEvent);
            }
        }
    }

    /**
     * Transition broker to DOWN status and activate degradation strategy.
     */
    private void transitionToDown(BrokerHealth health, String reason) {
        BrokerStatus previousStatus = health.status;
        health.status = BrokerStatus.DOWN;
        health.statusChangedAt = Instant.now();

        // Record health status in metrics
        if (metrics != null) {
            metrics.updateHealthStatus(health.brokerCode, false, Duration.ZERO);
        }

        // Notify broker down
        FailoverEvent downEvent = new FailoverEvent(
            health.brokerCode,
            FailoverEventType.BROKER_DOWN,
            previousStatus,
            BrokerStatus.DOWN,
            reason,
            Instant.now()
        );

        if (brokerDownListener != null) {
            brokerDownListener.accept(downEvent);
        }

        // Activate degradation strategy
        activateDegradation(health, reason);
    }

    /**
     * Activate degradation strategy for a broker.
     */
    private void activateDegradation(BrokerHealth health, String reason) {
        log.warn("[FailoverManager] Activating {} strategy for {}: {}",
            degradationStrategy, health.brokerCode, reason);

        health.status = BrokerStatus.DEGRADED;
        health.degradationActivatedAt = Instant.now();

        FailoverEvent event = new FailoverEvent(
            health.brokerCode,
            FailoverEventType.DEGRADATION_ACTIVATED,
            BrokerStatus.DOWN,
            BrokerStatus.DEGRADED,
            degradationStrategy.name() + " mode activated: " + reason,
            Instant.now()
        );

        if (degradationActivatedListener != null) {
            degradationActivatedListener.accept(event);
        }

        // Apply degradation strategy
        switch (degradationStrategy) {
            case READ_ONLY -> {
                log.warn("[FailoverManager] {} entering READ_ONLY mode: new orders disabled", health.brokerCode);
            }
            case QUEUEING -> {
                log.warn("[FailoverManager] {} entering QUEUEING mode: orders will be queued", health.brokerCode);
            }
            case FAILOVER -> {
                log.warn("[FailoverManager] {} entering FAILOVER mode: attempting failover", health.brokerCode);
                // TODO: Implement actual failover to backup broker
            }
            case ALERT_ONLY -> {
                log.warn("[FailoverManager] {} alert triggered, no action taken", health.brokerCode);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // CONFIGURATION
    // ════════════════════════════════════════════════════════════════════════

    public void setHealthCheckInterval(Duration interval) {
        this.healthCheckInterval = interval;
    }

    public void setConsecutiveFailuresThreshold(int threshold) {
        this.consecutiveFailuresThreshold = threshold;
    }

    public void setConsecutiveSuccessesThreshold(int threshold) {
        this.consecutiveSuccessesThreshold = threshold;
    }

    public void setDegradationStrategy(DegradationStrategy strategy) {
        this.degradationStrategy = strategy;
    }

    public void setMetrics(PrometheusBrokerMetrics metrics) {
        this.metrics = metrics;
    }

    // ════════════════════════════════════════════════════════════════════════
    // EVENT LISTENERS
    // ════════════════════════════════════════════════════════════════════════

    public void onBrokerDown(Consumer<FailoverEvent> listener) {
        this.brokerDownListener = listener;
    }

    public void onBrokerRecovered(Consumer<FailoverEvent> listener) {
        this.brokerRecoveredListener = listener;
    }

    public void onDegradationActivated(Consumer<FailoverEvent> listener) {
        this.degradationActivatedListener = listener;
    }

    public void onDegradationDeactivated(Consumer<FailoverEvent> listener) {
        this.degradationDeactivatedListener = listener;
    }

    // ════════════════════════════════════════════════════════════════════════
    // VALUE OBJECTS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Broker health tracking.
     */
    private static class BrokerHealth {
        final String brokerCode;
        volatile BrokerStatus status = BrokerStatus.HEALTHY;
        volatile Instant statusChangedAt = Instant.now();
        volatile Instant degradationActivatedAt;

        final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        final AtomicInteger consecutiveSuccesses = new AtomicInteger(0);
        final AtomicInteger totalChecks = new AtomicInteger(0);

        volatile Instant lastCheckTime;
        volatile Instant lastFailureTime;
        volatile String lastFailureReason;

        BrokerHealth(String brokerCode) {
            this.brokerCode = brokerCode;
        }

        Duration getUptime() {
            return Duration.between(statusChangedAt, Instant.now());
        }
    }

    /**
     * Broker status.
     */
    public enum BrokerStatus {
        HEALTHY,   // Broker is operational
        DEGRADED,  // Broker is down, degradation strategy active
        DOWN       // Broker is down, no degradation applied yet
    }

    /**
     * Degradation strategy.
     */
    public enum DegradationStrategy {
        READ_ONLY,   // Disable new orders, allow monitoring only
        QUEUEING,    // Queue critical orders for later execution
        FAILOVER,    // Switch to backup broker automatically
        ALERT_ONLY   // Just notify, don't take action
    }

    /**
     * Failover event.
     */
    public record FailoverEvent(
        String brokerCode,
        FailoverEventType eventType,
        BrokerStatus previousStatus,
        BrokerStatus newStatus,
        String reason,
        Instant timestamp
    ) {}

    public enum FailoverEventType {
        BROKER_DOWN,
        BROKER_RECOVERED,
        DEGRADATION_ACTIVATED,
        DEGRADATION_DEACTIVATED
    }

    /**
     * Health status snapshot.
     */
    public record HealthStatus(
        String brokerCode,
        BrokerStatus status,
        int consecutiveFailures,
        int consecutiveSuccesses,
        int totalChecks,
        Instant lastCheckTime,
        Instant lastFailureTime,
        String lastFailureReason,
        Duration uptime
    ) {
        public boolean isHealthy() {
            return status == BrokerStatus.HEALTHY;
        }

        public boolean isDegraded() {
            return status == BrokerStatus.DEGRADED;
        }

        public boolean isDown() {
            return status == BrokerStatus.DOWN;
        }
    }
}
