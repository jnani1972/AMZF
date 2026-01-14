package in.annupaper.infrastructure.broker.common;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ReconnectionPolicy.
 *
 * Tests:
 * - Exponential backoff calculations
 * - Circuit breaker behavior
 * - Reset functionality
 * - Boundary conditions
 */
class ReconnectionPolicyTest {

    @Test
    void testInitialState() {
        ReconnectionPolicy policy = ReconnectionPolicy.builder()
            .initialDelay(Duration.ofSeconds(1))
            .maxDelay(Duration.ofMinutes(5))
            .multiplier(2.0)
            .maxAttempts(10)
            .build();

        assertTrue(policy.shouldRetry(), "Should allow initial retry");
        assertEquals(0, policy.getAttemptCount(), "Initial attempt count should be 0");
        assertFalse(policy.isCircuitOpen(), "Circuit should be closed initially");
        assertNull(policy.getLastAttemptTime(), "No attempts made yet");
    }

    @Test
    void testExponentialBackoff() {
        ReconnectionPolicy policy = ReconnectionPolicy.builder()
            .initialDelay(Duration.ofSeconds(1))
            .maxDelay(Duration.ofMinutes(5))
            .multiplier(2.0)
            .maxAttempts(10)
            .build();

        // First failure: delay should be 1s
        Duration delay1 = policy.getNextDelay();
        assertEquals(Duration.ofSeconds(1), delay1, "First delay should be initial delay");

        policy.recordFailure();
        assertEquals(1, policy.getAttemptCount(), "Attempt count should be 1");

        // Second failure: delay should be 2s (1 * 2)
        Duration delay2 = policy.getNextDelay();
        assertEquals(Duration.ofSeconds(2), delay2, "Second delay should be 2s");

        policy.recordFailure();

        // Third failure: delay should be 4s (2 * 2)
        Duration delay3 = policy.getNextDelay();
        assertEquals(Duration.ofSeconds(4), delay3, "Third delay should be 4s");
    }

    @Test
    void testMaxDelayRespected() {
        ReconnectionPolicy policy = ReconnectionPolicy.builder()
            .initialDelay(Duration.ofSeconds(10))
            .maxDelay(Duration.ofSeconds(30))
            .multiplier(3.0)
            .maxAttempts(10)
            .build();

        // Initial delay: 10s
        assertEquals(Duration.ofSeconds(10), policy.getNextDelay());

        // After first failure: delay becomes 30s (10 * 3 = 30, hit max)
        policy.recordFailure();
        assertEquals(Duration.ofSeconds(30), policy.getNextDelay());

        // After second failure: still 30s (capped)
        policy.recordFailure();
        assertEquals(Duration.ofSeconds(30), policy.getNextDelay());

        // After third failure: still 30s (capped)
        policy.recordFailure();
        assertEquals(Duration.ofSeconds(30), policy.getNextDelay());
    }

    @Test
    void testCircuitBreakerOpensAfterMaxAttempts() {
        ReconnectionPolicy policy = ReconnectionPolicy.builder()
            .initialDelay(Duration.ofMillis(100))
            .maxDelay(Duration.ofSeconds(5))
            .multiplier(2.0)
            .maxAttempts(3)
            .build();

        assertTrue(policy.shouldRetry(), "Should retry before max attempts");
        assertFalse(policy.isCircuitOpen(), "Circuit should be closed");

        // First failure
        policy.recordFailure();
        assertTrue(policy.shouldRetry(), "Should still retry (1/3)");
        assertFalse(policy.isCircuitOpen());

        // Second failure
        policy.recordFailure();
        assertTrue(policy.shouldRetry(), "Should still retry (2/3)");
        assertFalse(policy.isCircuitOpen());

        // Third failure - should open circuit
        policy.recordFailure();
        assertFalse(policy.shouldRetry(), "Should NOT retry after max attempts");
        assertTrue(policy.isCircuitOpen(), "Circuit should be open");
    }

    @Test
    void testSuccessResetsPolicy() {
        ReconnectionPolicy policy = ReconnectionPolicy.builder()
            .initialDelay(Duration.ofSeconds(1))
            .maxDelay(Duration.ofMinutes(5))
            .multiplier(2.0)
            .maxAttempts(5)
            .build();

        // Record some failures
        policy.recordFailure();
        policy.recordFailure();
        assertEquals(2, policy.getAttemptCount());

        // Record success - should reset
        policy.recordSuccess();
        assertEquals(0, policy.getAttemptCount(), "Attempt count should reset");
        assertEquals(Duration.ofSeconds(1), policy.getNextDelay(), "Delay should reset to initial");
        assertFalse(policy.isCircuitOpen(), "Circuit should be closed");
        assertNull(policy.getLastAttemptTime(), "Last attempt time should be cleared");
    }

    @Test
    void testManualReset() {
        ReconnectionPolicy policy = ReconnectionPolicy.builder()
            .initialDelay(Duration.ofSeconds(1))
            .maxDelay(Duration.ofMinutes(5))
            .multiplier(2.0)
            .maxAttempts(2)
            .build();

        // Open circuit breaker
        policy.recordFailure();
        policy.recordFailure();
        assertTrue(policy.isCircuitOpen());

        // Manual reset
        policy.reset();
        assertFalse(policy.isCircuitOpen(), "Circuit should be closed after reset");
        assertEquals(0, policy.getAttemptCount());
        assertTrue(policy.shouldRetry());
    }

    @Test
    void testLastAttemptTimeTracking() throws InterruptedException {
        ReconnectionPolicy policy = ReconnectionPolicy.builder()
            .initialDelay(Duration.ofMillis(100))
            .maxDelay(Duration.ofSeconds(5))
            .multiplier(2.0)
            .maxAttempts(5)
            .build();

        assertNull(policy.getLastAttemptTime(), "No attempts yet");

        policy.recordFailure();
        assertNotNull(policy.getLastAttemptTime(), "Last attempt time should be set");

        Thread.sleep(50);  // Wait a bit
        policy.recordFailure();
        assertNotNull(policy.getLastAttemptTime(), "Last attempt time should be updated");
    }

    @Test
    void testDataBrokerDefaults() {
        ReconnectionPolicy policy = ReconnectionPolicy.forDataBroker();

        assertEquals(Duration.ofSeconds(1), policy.getNextDelay());
        assertEquals(0, policy.getAttemptCount());
        assertTrue(policy.shouldRetry());

        // Verify max delay by simulating many failures
        for (int i = 0; i < 20; i++) {
            policy.recordFailure();
        }
        assertTrue(policy.getNextDelay().compareTo(Duration.ofMinutes(5)) <= 0,
            "Delay should not exceed 5 minutes");
    }

    @Test
    void testOrderBrokerDefaults() {
        ReconnectionPolicy policy = ReconnectionPolicy.forOrderBroker();

        assertEquals(Duration.ofMillis(500), policy.getNextDelay());
        assertEquals(0, policy.getAttemptCount());
        assertTrue(policy.shouldRetry());

        // Verify max delay
        for (int i = 0; i < 20; i++) {
            policy.recordFailure();
        }
        assertTrue(policy.getNextDelay().compareTo(Duration.ofMinutes(2)) <= 0,
            "Delay should not exceed 2 minutes");
    }

    @Test
    void testBuilderValidation() {
        // Test negative initial delay
        assertThrows(IllegalArgumentException.class, () ->
            ReconnectionPolicy.builder()
                .initialDelay(Duration.ofSeconds(-1))
                .build()
        );

        // Test zero initial delay
        assertThrows(IllegalArgumentException.class, () ->
            ReconnectionPolicy.builder()
                .initialDelay(Duration.ZERO)
                .build()
        );

        // Test invalid multiplier
        assertThrows(IllegalArgumentException.class, () ->
            ReconnectionPolicy.builder()
                .multiplier(0.5)  // Must be > 1.0
                .build()
        );

        // Test zero max attempts
        assertThrows(IllegalArgumentException.class, () ->
            ReconnectionPolicy.builder()
                .maxAttempts(0)
                .build()
        );

        // Test initial delay > max delay
        assertThrows(IllegalArgumentException.class, () ->
            ReconnectionPolicy.builder()
                .initialDelay(Duration.ofMinutes(10))
                .maxDelay(Duration.ofMinutes(5))
                .build()
        );
    }
}
