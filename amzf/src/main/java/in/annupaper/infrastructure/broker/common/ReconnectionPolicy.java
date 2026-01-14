package in.annupaper.infrastructure.broker.common;

import java.time.Duration;
import java.time.Instant;

/**
 * Reconnection policy with exponential backoff for broker connections.
 *
 * Features:
 * - Exponential backoff with configurable multiplier
 * - Maximum retry limit
 * - Maximum backoff duration (cap)
 * - Circuit breaker after max failures
 * - Reset after successful connection
 *
 * Usage:
 * <pre>
 * ReconnectionPolicy policy = ReconnectionPolicy.builder()
 *     .initialDelay(Duration.ofSeconds(1))
 *     .maxDelay(Duration.ofMinutes(5))
 *     .multiplier(2.0)
 *     .maxAttempts(10)
 *     .build();
 *
 * while (policy.shouldRetry()) {
 *     try {
 *         connect();
 *         policy.recordSuccess();
 *         break;
 *     } catch (Exception e) {
 *         policy.recordFailure();
 *         Thread.sleep(policy.getNextDelay().toMillis());
 *     }
 * }
 * </pre>
 */
public class ReconnectionPolicy {

    private final Duration initialDelay;
    private final Duration maxDelay;
    private final double multiplier;
    private final int maxAttempts;

    private int attemptCount = 0;
    private Duration currentDelay;
    private Instant lastAttemptTime;
    private boolean circuitOpen = false;

    private ReconnectionPolicy(Duration initialDelay, Duration maxDelay,
                              double multiplier, int maxAttempts) {
        this.initialDelay = initialDelay;
        this.maxDelay = maxDelay;
        this.multiplier = multiplier;
        this.maxAttempts = maxAttempts;
        this.currentDelay = initialDelay;
    }

    /**
     * Check if another retry attempt should be made.
     *
     * @return true if retry should be attempted, false if circuit is open
     */
    public synchronized boolean shouldRetry() {
        if (circuitOpen) {
            return false;
        }
        return attemptCount < maxAttempts;
    }

    /**
     * Get the delay before the next retry attempt.
     * Uses exponential backoff.
     *
     * @return Duration to wait before next attempt
     */
    public synchronized Duration getNextDelay() {
        return currentDelay;
    }

    /**
     * Record a failed connection attempt.
     * Increments attempt count and calculates next backoff delay.
     */
    public synchronized void recordFailure() {
        attemptCount++;
        lastAttemptTime = Instant.now();

        // Exponential backoff
        long newDelayMillis = (long) (currentDelay.toMillis() * multiplier);
        currentDelay = Duration.ofMillis(Math.min(newDelayMillis, maxDelay.toMillis()));

        // Open circuit breaker if max attempts reached
        if (attemptCount >= maxAttempts) {
            circuitOpen = true;
        }
    }

    /**
     * Record a successful connection.
     * Resets all counters and closes circuit breaker.
     */
    public synchronized void recordSuccess() {
        attemptCount = 0;
        currentDelay = initialDelay;
        lastAttemptTime = null;
        circuitOpen = false;
    }

    /**
     * Reset the policy to initial state.
     * Useful for manual circuit breaker reset.
     */
    public synchronized void reset() {
        recordSuccess();
    }

    /**
     * Check if circuit breaker is open.
     *
     * @return true if circuit is open (max failures reached)
     */
    public synchronized boolean isCircuitOpen() {
        return circuitOpen;
    }

    /**
     * Get current attempt count.
     *
     * @return Number of failed attempts since last success
     */
    public synchronized int getAttemptCount() {
        return attemptCount;
    }

    /**
     * Get time of last attempt.
     *
     * @return Instant of last connection attempt, or null if no attempts made
     */
    public synchronized Instant getLastAttemptTime() {
        return lastAttemptTime;
    }

    /**
     * Create a builder for ReconnectionPolicy.
     *
     * @return Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a default policy for data broker connections.
     * Suitable for WebSocket reconnections.
     *
     * @return ReconnectionPolicy with default data broker settings
     */
    public static ReconnectionPolicy forDataBroker() {
        return builder()
            .initialDelay(Duration.ofSeconds(1))
            .maxDelay(Duration.ofMinutes(5))
            .multiplier(2.0)
            .maxAttempts(10)
            .build();
    }

    /**
     * Create a default policy for order broker connections.
     * More aggressive retries for order execution.
     *
     * @return ReconnectionPolicy with default order broker settings
     */
    public static ReconnectionPolicy forOrderBroker() {
        return builder()
            .initialDelay(Duration.ofMillis(500))
            .maxDelay(Duration.ofMinutes(2))
            .multiplier(1.5)
            .maxAttempts(15)
            .build();
    }

    /**
     * Builder for ReconnectionPolicy.
     */
    public static class Builder {
        private Duration initialDelay = Duration.ofSeconds(1);
        private Duration maxDelay = Duration.ofMinutes(5);
        private double multiplier = 2.0;
        private int maxAttempts = 10;

        public Builder initialDelay(Duration initialDelay) {
            if (initialDelay.isNegative() || initialDelay.isZero()) {
                throw new IllegalArgumentException("Initial delay must be positive");
            }
            this.initialDelay = initialDelay;
            return this;
        }

        public Builder maxDelay(Duration maxDelay) {
            if (maxDelay.isNegative() || maxDelay.isZero()) {
                throw new IllegalArgumentException("Max delay must be positive");
            }
            this.maxDelay = maxDelay;
            return this;
        }

        public Builder multiplier(double multiplier) {
            if (multiplier <= 1.0) {
                throw new IllegalArgumentException("Multiplier must be greater than 1.0");
            }
            this.multiplier = multiplier;
            return this;
        }

        public Builder maxAttempts(int maxAttempts) {
            if (maxAttempts <= 0) {
                throw new IllegalArgumentException("Max attempts must be positive");
            }
            this.maxAttempts = maxAttempts;
            return this;
        }

        public ReconnectionPolicy build() {
            if (initialDelay.compareTo(maxDelay) > 0) {
                throw new IllegalArgumentException("Initial delay cannot exceed max delay");
            }
            return new ReconnectionPolicy(initialDelay, maxDelay, multiplier, maxAttempts);
        }
    }
}
