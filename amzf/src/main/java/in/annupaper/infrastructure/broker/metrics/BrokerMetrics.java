package in.annupaper.infrastructure.broker.metrics;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Broker metrics interface for monitoring and alerting.
 *
 * Implementations can publish to Prometheus, Grafana, CloudWatch, etc.
 *
 * Key metrics:
 * - Order success/failure rates
 * - Order latency (p50, p95, p99)
 * - Rate limit hits
 * - Authentication failures
 * - Connection drops
 * - Retry counts
 */
public interface BrokerMetrics {

    /**
     * Record successful order placement.
     *
     * @param brokerCode Broker code
     * @param latency Order placement latency
     */
    void recordOrderSuccess(String brokerCode, Duration latency);

    /**
     * Record failed order placement.
     *
     * @param brokerCode Broker code
     * @param errorType Type of error (RATE_LIMIT, INVALID_ORDER, CONNECTION, etc.)
     * @param latency Time to failure
     */
    void recordOrderFailure(String brokerCode, String errorType, Duration latency);

    /**
     * Record order modification.
     *
     * @param brokerCode Broker code
     * @param success Whether modification succeeded
     * @param latency Modification latency
     */
    void recordOrderModification(String brokerCode, boolean success, Duration latency);

    /**
     * Record order cancellation.
     *
     * @param brokerCode Broker code
     * @param success Whether cancellation succeeded
     * @param latency Cancellation latency
     */
    void recordOrderCancellation(String brokerCode, boolean success, Duration latency);

    /**
     * Record rate limit hit.
     *
     * @param brokerCode Broker code
     * @param currentRate Current request rate
     * @param limitType Type of limit (PER_SECOND, PER_MINUTE, PER_DAY)
     */
    void recordRateLimitHit(String brokerCode, int currentRate, RateLimitType limitType);

    /**
     * Record retry attempt.
     *
     * @param brokerCode Broker code
     * @param attemptNumber Retry attempt number (1, 2, 3...)
     * @param retryReason Reason for retry
     */
    void recordRetry(String brokerCode, int attemptNumber, String retryReason);

    /**
     * Record authentication event.
     *
     * @param brokerCode Broker code
     * @param success Whether authentication succeeded
     * @param latency Authentication latency
     */
    void recordAuthentication(String brokerCode, boolean success, Duration latency);

    /**
     * Record connection event.
     *
     * @param brokerCode Broker code
     * @param event Connection event type
     */
    void recordConnectionEvent(String brokerCode, ConnectionEvent event);

    /**
     * Record WebSocket message.
     *
     * @param brokerCode Broker code
     * @param messageType Type of WebSocket message
     */
    void recordWebSocketMessage(String brokerCode, String messageType);

    /**
     * Record order update received.
     *
     * @param brokerCode Broker code
     * @param status Order status
     * @param latency Time from order placement to update
     */
    void recordOrderUpdate(String brokerCode, String status, Duration latency);

    /**
     * Get current metrics snapshot.
     *
     * @param brokerCode Broker code
     * @return Map of metric names to values
     */
    Map<String, Object> getMetrics(String brokerCode);

    /**
     * Get metrics for all brokers.
     *
     * @return Map of broker code to metrics
     */
    Map<String, Map<String, Object>> getAllMetrics();

    /**
     * Reset metrics for a broker.
     *
     * @param brokerCode Broker code
     */
    void reset(String brokerCode);

    /**
     * Reset all metrics.
     */
    void resetAll();

    // ════════════════════════════════════════════════════════════════════════
    // ENUMS
    // ════════════════════════════════════════════════════════════════════════

    enum RateLimitType {
        PER_SECOND,
        PER_MINUTE,
        PER_DAY,
        GLOBAL
    }

    enum ConnectionEvent {
        CONNECTING,
        CONNECTED,
        DISCONNECTED,
        RECONNECTING,
        HEARTBEAT_TIMEOUT,
        TOKEN_EXPIRED,
        ERROR
    }

    /**
     * Metrics snapshot.
     */
    record MetricsSnapshot(
        String brokerCode,
        Instant snapshotTime,

        // Order metrics
        long totalOrders,
        long successfulOrders,
        long failedOrders,
        double successRate,

        // Latency metrics (milliseconds)
        double avgLatency,
        double p50Latency,
        double p95Latency,
        double p99Latency,
        double maxLatency,

        // Error metrics
        long rateLimitHits,
        long authenticationFailures,
        long connectionDrops,
        long invalidOrders,

        // Retry metrics
        long totalRetries,
        double avgRetriesPerOrder,

        // Current state
        int currentRatePerSecond,
        int todayOrderCount,
        boolean isConnected,
        boolean isAuthenticated
    ) {}
}
