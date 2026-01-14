package in.annupaper.infrastructure.broker.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Heartbeat manager for keeping WebSocket connections alive.
 *
 * Features:
 * - Periodic ping messages to keep connection alive
 * - Timeout detection for unresponsive connections
 * - Configurable ping interval and timeout
 * - Callback for connection health changes
 * - Thread-safe heartbeat tracking
 *
 * Usage:
 * <pre>
 * HeartbeatManager heartbeat = new HeartbeatManager(
 *     "ZERODHA",
 *     "user123",
 *     Duration.ofSeconds(30),  // Send ping every 30 seconds
 *     Duration.ofSeconds(60),  // Timeout after 60 seconds without pong
 *     () -> sendPingMessage(),
 *     isHealthy -> {
 *         if (!isHealthy) {
 *             reconnect();
 *         }
 *     }
 * );
 *
 * heartbeat.start();
 * // When pong received:
 * heartbeat.recordPong();
 * heartbeat.stop();
 * </pre>
 */
public class HeartbeatManager {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatManager.class);

    private final String brokerCode;
    private final String userBrokerId;
    private final Duration pingInterval;
    private final Duration timeout;
    private final Runnable pingFunction;
    private final Consumer<Boolean> healthCallback;

    private final ScheduledExecutorService scheduler;
    private volatile ScheduledFuture<?> pingTask;
    private volatile ScheduledFuture<?> timeoutTask;
    private volatile Instant lastPongTime;
    private volatile boolean running = false;
    private volatile boolean healthy = true;

    public HeartbeatManager(String brokerCode, String userBrokerId,
                           Duration pingInterval, Duration timeout,
                           Runnable pingFunction,
                           Consumer<Boolean> healthCallback) {
        this.brokerCode = brokerCode;
        this.userBrokerId = userBrokerId;
        this.pingInterval = pingInterval;
        this.timeout = timeout;
        this.pingFunction = pingFunction;
        this.healthCallback = healthCallback;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Heartbeat-" + brokerCode + "-" + userBrokerId);
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Start sending periodic ping messages.
     */
    public synchronized void start() {
        if (running) {
            log.warn("[{}:{}] Heartbeat manager already running", brokerCode, userBrokerId);
            return;
        }

        log.info("[{}:{}] Starting heartbeat manager (ping interval: {}s, timeout: {}s)",
            brokerCode, userBrokerId, pingInterval.getSeconds(), timeout.getSeconds());

        running = true;
        healthy = true;
        lastPongTime = Instant.now();

        // Schedule periodic ping
        pingTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                sendPing();
            } catch (Exception e) {
                log.error("[{}:{}] Failed to send ping", brokerCode, userBrokerId, e);
                markUnhealthy();
            }
        }, 0, pingInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Stop sending ping messages and cancel timeout checks.
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }

        log.info("[{}:{}] Stopping heartbeat manager", brokerCode, userBrokerId);
        running = false;

        if (pingTask != null) {
            pingTask.cancel(false);
            pingTask = null;
        }

        if (timeoutTask != null) {
            timeoutTask.cancel(false);
            timeoutTask = null;
        }

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Record receipt of pong message.
     * Resets timeout timer and marks connection as healthy.
     */
    public synchronized void recordPong() {
        lastPongTime = Instant.now();

        // Cancel existing timeout check
        if (timeoutTask != null) {
            timeoutTask.cancel(false);
            timeoutTask = null;
        }

        // Mark as healthy if was unhealthy
        if (!healthy) {
            log.info("[{}:{}] Connection recovered, marking as healthy", brokerCode, userBrokerId);
            markHealthy();
        }
    }

    /**
     * Check if connection is healthy (receiving pongs).
     *
     * @return true if last pong received within timeout window
     */
    public boolean isHealthy() {
        return healthy && isWithinTimeout();
    }

    /**
     * Get time since last pong received.
     *
     * @return Duration since last pong, or null if no pongs received
     */
    public Duration getTimeSinceLastPong() {
        Instant lastPong = lastPongTime;
        if (lastPong == null) {
            return null;
        }
        return Duration.between(lastPong, Instant.now());
    }

    /**
     * Send ping message and schedule timeout check.
     */
    private void sendPing() {
        if (!running) {
            return;
        }

        log.debug("[{}:{}] Sending ping", brokerCode, userBrokerId);

        try {
            pingFunction.run();
        } catch (Exception e) {
            log.error("[{}:{}] Ping function threw exception", brokerCode, userBrokerId, e);
            markUnhealthy();
            return;
        }

        // Schedule timeout check
        scheduleTimeoutCheck();
    }

    /**
     * Schedule timeout check for pong response.
     */
    private void scheduleTimeoutCheck() {
        if (timeoutTask != null) {
            timeoutTask.cancel(false);
        }

        timeoutTask = scheduler.schedule(() -> {
            if (isWithinTimeout()) {
                return;  // Pong received in time
            }

            log.warn("[{}:{}] Heartbeat timeout - no pong received for {} seconds",
                brokerCode, userBrokerId, timeout.getSeconds());
            markUnhealthy();

        }, timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Check if last pong was received within timeout window.
     */
    private boolean isWithinTimeout() {
        Instant lastPong = lastPongTime;
        if (lastPong == null) {
            return false;
        }
        Duration timeSinceLastPong = Duration.between(lastPong, Instant.now());
        return timeSinceLastPong.compareTo(timeout) < 0;
    }

    /**
     * Mark connection as healthy and notify callback.
     */
    private void markHealthy() {
        if (healthy) {
            return;  // Already healthy
        }

        healthy = true;
        if (healthCallback != null) {
            try {
                healthCallback.accept(true);
            } catch (Exception e) {
                log.error("[{}:{}] Health callback threw exception", brokerCode, userBrokerId, e);
            }
        }
    }

    /**
     * Mark connection as unhealthy and notify callback.
     */
    private void markUnhealthy() {
        if (!healthy) {
            return;  // Already unhealthy
        }

        healthy = false;
        if (healthCallback != null) {
            try {
                healthCallback.accept(false);
            } catch (Exception e) {
                log.error("[{}:{}] Health callback threw exception", brokerCode, userBrokerId, e);
            }
        }
    }

    /**
     * Create a default heartbeat manager for WebSocket connections.
     *
     * @param brokerCode Broker identifier
     * @param userBrokerId User-broker identifier
     * @param pingFunction Function to send ping message
     * @param healthCallback Callback when health status changes
     * @return HeartbeatManager with default settings
     */
    public static HeartbeatManager forWebSocket(String brokerCode, String userBrokerId,
                                               Runnable pingFunction,
                                               Consumer<Boolean> healthCallback) {
        return new HeartbeatManager(
            brokerCode,
            userBrokerId,
            Duration.ofSeconds(30),  // Ping every 30 seconds
            Duration.ofSeconds(60),  // Timeout after 60 seconds
            pingFunction,
            healthCallback
        );
    }
}
