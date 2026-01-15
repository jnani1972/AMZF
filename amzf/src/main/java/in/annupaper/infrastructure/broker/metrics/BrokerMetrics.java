package in.annupaper.infrastructure.broker.metrics;

import java.time.Duration;

/**
 * Interface for recording broker operation metrics.
 * Used to monitor broker health, latency, and error rates.
 */
public interface BrokerMetrics {

    /**
     * Record a successful order placement
     * @param brokerCode Broker code (e.g., FYERS, UPSTOX)
     * @param latency Duration of the operation
     */
    void recordOrderSuccess(String brokerCode, Duration latency);

    /**
     * Record a failed order placement
     * @param brokerCode Broker code
     * @param errorType Type of error (e.g., TIMEOUT, AUTH_FAILED)
     * @param latency Duration before failure
     */
    void recordOrderFailure(String brokerCode, String errorType, Duration latency);

    /**
     * Record a broker timeout
     * @param brokerCode Broker code
     */
    void recordTimeout(String brokerCode);

    /**
     * Record an authentication failure
     * @param brokerCode Broker code
     */
    void recordAuthFailure(String brokerCode);

    /**
     * Record a rate limit breach
     * @param brokerCode Broker code
     */
    void recordRateLimitBreach(String brokerCode);

    /**
     * Get total success count for a broker
     * @param brokerCode Broker code
     * @return Number of successful operations
     */
    double getSuccessCount(String brokerCode);

    /**
     * Get total failure count for a broker
     * @param brokerCode Broker code
     * @return Number of failed operations
     */
    double getFailureCount(String brokerCode);

    /**
     * Get timeout count for a broker
     * @param brokerCode Broker code
     * @return Number of timeouts
     */
    double getTimeoutCount(String brokerCode);
}
