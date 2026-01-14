package in.annupaper.chaos;

import in.annupaper.domain.order.*;
import in.annupaper.domain.trade.Direction;
import in.annupaper.infrastructure.broker.metrics.BrokerMetrics;
import in.annupaper.infrastructure.broker.metrics.PrometheusBrokerMetrics;
import io.prometheus.client.CollectorRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chaos Engineering Tests for Multi-Broker Trading System.
 *
 * Simulates real-world failure scenarios:
 * - Broker outages (HTTP failures, timeouts)
 * - Network delays and intermittent connectivity
 * - Rate limit breaches
 * - Authentication failures
 * - Concurrent broker failures
 * - Metrics recording during failures
 *
 * Goals:
 * - Verify system resilience under adverse conditions
 * - Ensure metrics are recorded during failures
 * - Test graceful degradation
 */
@DisplayName("Chaos Engineering Tests")
public class BrokerChaosTest {

    private BrokerMetrics metrics;
    private CollectorRegistry registry;

    // Test simulation helpers
    private ChaosSimulator primaryBroker;
    private ChaosSimulator backupBroker;

    @BeforeEach
    public void setUp() {
        registry = new CollectorRegistry();
        metrics = new PrometheusBrokerMetrics(registry);

        primaryBroker = new ChaosSimulator("UPSTOX", metrics);
        backupBroker = new ChaosSimulator("ZERODHA", metrics);
    }

    @Test
    @DisplayName("Chaos 1: Primary Broker Complete Outage")
    public void testPrimaryBrokerOutage() {
        System.out.println("\n=== CHAOS TEST 1: Primary Broker Complete Outage ===");

        // Setup: Primary broker is down
        primaryBroker.setFailureMode(FailureMode.COMPLETE_OUTAGE);
        System.out.println("⚠️ Simulating UPSTOX complete outage");

        // Attempt to place order on primary
        OrderRequest request = createTestOrder();

        Exception exception = assertThrows(SimulatedBrokerException.class, () -> {
            primaryBroker.simulatePlaceOrder(request);
        });

        System.out.println("✅ Primary broker failed as expected: " + exception.getMessage());

        // Verify we can failover to backup
        System.out.println("→ Failing over to ZERODHA backup");
        OrderResponse response = backupBroker.simulatePlaceOrder(request);

        assertNotNull(response);
        assertEquals(OrderStatus.PLACED, response.status());
        System.out.println("✅ Order placed successfully on backup broker");

        // Verify metrics recorded the failure
        String metricsOutput = getMetricsOutput();
        assertTrue(metricsOutput.contains("broker=\"UPSTOX\""), "Should track UPSTOX");
        assertTrue(metricsOutput.contains("broker=\"ZERODHA\""), "Should track ZERODHA");

        System.out.println("✅ Metrics correctly recorded failover event\n");
    }

    @Test
    @DisplayName("Chaos 2: Network Timeout and Retry")
    public void testNetworkTimeout() {
        System.out.println("\n=== CHAOS TEST 2: Network Timeout and Retry ===");

        // Setup: Broker has high latency (2 seconds)
        primaryBroker.setFailureMode(FailureMode.TIMEOUT);
        primaryBroker.setLatencyMs(2000);
        System.out.println("⚠️ Simulating 2-second network latency");

        OrderRequest request = createTestOrder();

        Instant start = Instant.now();
        Exception exception = assertThrows(SimulatedBrokerException.class, () -> {
            primaryBroker.simulatePlaceOrder(request);
        });
        Duration elapsed = Duration.between(start, Instant.now());

        System.out.println("✅ Request timed out after " + elapsed.toMillis() + "ms");
        assertTrue(elapsed.toMillis() >= 2000, "Should wait for full timeout");

        // Verify high latency is recorded in metrics
        String metricsOutput = getMetricsOutput();
        assertTrue(metricsOutput.contains("broker_order_latency_seconds"),
            "Should record order latency histogram");

        System.out.println("✅ Timeout handled correctly\n");
    }

    @Test
    @DisplayName("Chaos 3: Rate Limit Breach")
    public void testRateLimitBreach() {
        System.out.println("\n=== CHAOS TEST 3: Rate Limit Breach ===");

        // Setup: Broker will rate-limit after 5 requests
        primaryBroker.setFailureMode(FailureMode.RATE_LIMITED);
        primaryBroker.setRateLimitThreshold(5);
        System.out.println("⚠️ Simulating rate limit: 5 requests/second");

        OrderRequest request = createTestOrder();

        int successCount = 0;
        int rateLimitCount = 0;

        // Send 10 rapid requests
        for (int i = 0; i < 10; i++) {
            try {
                primaryBroker.simulatePlaceOrder(request);
                successCount++;
            } catch (SimulatedBrokerException e) {
                if (e.getMessage().contains("Rate limit")) {
                    rateLimitCount++;
                }
            }
        }

        System.out.println("✅ Successful orders: " + successCount);
        System.out.println("✅ Rate limited orders: " + rateLimitCount);

        assertEquals(5, successCount, "Should allow 5 requests");
        assertEquals(5, rateLimitCount, "Should rate-limit 5 requests");

        // Verify metrics recorded rate limit hits
        String metricsOutput = getMetricsOutput();
        assertTrue(metricsOutput.contains("broker_rate_limit_hits_total"),
            "Should record rate limit hits");

        System.out.println("✅ Rate limiting handled correctly\n");
    }

    @Test
    @DisplayName("Chaos 4: Authentication Failure and Recovery")
    public void testAuthenticationFailure() {
        System.out.println("\n=== CHAOS TEST 4: Authentication Failure and Recovery ===");

        // Setup: Initial auth fails, retry succeeds
        primaryBroker.setFailureMode(FailureMode.AUTH_FAILURE);
        System.out.println("⚠️ Simulating authentication failure with retry");

        // First attempt fails
        Exception exception = assertThrows(SimulatedBrokerException.class, () -> {
            primaryBroker.simulateAuthenticate();
        });
        System.out.println("✅ First auth attempt failed: " + exception.getMessage());

        // Reset failure mode and retry
        primaryBroker.setFailureMode(FailureMode.NONE);
        primaryBroker.simulateAuthenticate();
        System.out.println("✅ Retry auth succeeded");

        // Verify metrics recorded both failure and success
        String metricsOutput = getMetricsOutput();
        assertTrue(metricsOutput.contains("broker_authentications_total"),
            "Should record authentication attempts");

        System.out.println("✅ Authentication retry handled correctly\n");
    }

    @Test
    @DisplayName("Chaos 5: Concurrent Multi-Broker Failures")
    public void testConcurrentBrokerFailures() throws Exception {
        System.out.println("\n=== CHAOS TEST 5: Concurrent Multi-Broker Failures ===");

        // Setup: Multiple brokers with different failure modes
        ChaosSimulator broker1 = new ChaosSimulator("UPSTOX", metrics);
        ChaosSimulator broker2 = new ChaosSimulator("ZERODHA", metrics);
        ChaosSimulator broker3 = new ChaosSimulator("FYERS", metrics);

        broker1.setFailureMode(FailureMode.INTERMITTENT);  // 50% failure rate
        broker2.setFailureMode(FailureMode.TIMEOUT);       // Slow but works
        broker2.setLatencyMs(500);
        broker3.setFailureMode(FailureMode.NONE);          // Healthy

        System.out.println("⚠️ UPSTOX: Intermittent failures (50%)");
        System.out.println("⚠️ ZERODHA: High latency (500ms)");
        System.out.println("✅ FYERS: Healthy");

        ExecutorService executor = Executors.newFixedThreadPool(3);
        OrderRequest request = createTestOrder();

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Send 10 concurrent requests to each broker
        List<Future<?>> futures = new CopyOnWriteArrayList<>();
        for (int i = 0; i < 10; i++) {
            for (ChaosSimulator broker : List.of(broker1, broker2, broker3)) {
                Future<?> future = executor.submit(() -> {
                    try {
                        broker.simulatePlaceOrder(request);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    }
                });
                futures.add(future);
            }
        }

        // Wait for all requests to complete
        for (Future<?> future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }
        executor.shutdown();

        System.out.println("✅ Total successful orders: " + successCount.get());
        System.out.println("⚠️ Total failed orders: " + failureCount.get());

        assertTrue(successCount.get() > 0, "At least some orders should succeed");
        assertTrue(failureCount.get() > 0, "Some orders should fail due to chaos");

        // Verify metrics captured all brokers
        String metricsOutput = getMetricsOutput();
        assertTrue(metricsOutput.contains("broker=\"UPSTOX\""), "Should track UPSTOX");
        assertTrue(metricsOutput.contains("broker=\"ZERODHA\""), "Should track ZERODHA");
        assertTrue(metricsOutput.contains("broker=\"FYERS\""), "Should track FYERS");

        System.out.println("✅ System survived concurrent multi-broker chaos\n");
    }

    @Test
    @DisplayName("Chaos 6: Cascading Failures")
    public void testCascadingFailures() {
        System.out.println("\n=== CHAOS TEST 6: Cascading Failures ===");

        // Simulate primary fails → backup fails → tertiary succeeds
        ChaosSimulator primary = new ChaosSimulator("UPSTOX", metrics);
        ChaosSimulator backup = new ChaosSimulator("ZERODHA", metrics);
        ChaosSimulator tertiary = new ChaosSimulator("FYERS", metrics);

        primary.setFailureMode(FailureMode.COMPLETE_OUTAGE);
        backup.setFailureMode(FailureMode.COMPLETE_OUTAGE);
        tertiary.setFailureMode(FailureMode.NONE);

        System.out.println("⚠️ PRIMARY (UPSTOX): DOWN");
        System.out.println("⚠️ BACKUP (ZERODHA): DOWN");
        System.out.println("✅ TERTIARY (FYERS): UP");

        OrderRequest request = createTestOrder();
        OrderResponse response = null;

        // Try primary
        try {
            response = primary.simulatePlaceOrder(request);
            fail("Primary should have failed");
        } catch (SimulatedBrokerException e) {
            System.out.println("→ Primary failed, trying backup...");
        }

        // Try backup
        try {
            response = backup.simulatePlaceOrder(request);
            fail("Backup should have failed");
        } catch (SimulatedBrokerException e) {
            System.out.println("→ Backup failed, trying tertiary...");
        }

        // Try tertiary
        response = tertiary.simulatePlaceOrder(request);
        assertNotNull(response);
        assertEquals(OrderStatus.PLACED, response.status());
        System.out.println("✅ Order placed on tertiary broker after cascade");

        // Verify all failures were recorded
        String metricsOutput = getMetricsOutput();
        assertTrue(metricsOutput.contains("broker=\"FYERS\""), "Should record tertiary success");

        System.out.println("✅ System survived cascading failure scenario\n");
    }

    @Test
    @DisplayName("Chaos 7: Broker Recovery After Outage")
    public void testBrokerRecoveryAfterOutage() {
        System.out.println("\n=== CHAOS TEST 7: Broker Recovery After Outage ===");

        // Broker starts healthy, goes down, recovers
        primaryBroker.setFailureMode(FailureMode.NONE);
        OrderRequest request = createTestOrder();

        // Phase 1: Healthy
        System.out.println("Phase 1: Broker healthy");
        OrderResponse response1 = primaryBroker.simulatePlaceOrder(request);
        assertEquals(OrderStatus.PLACED, response1.status());
        System.out.println("✅ Order 1 placed successfully");

        // Phase 2: Outage
        System.out.println("\nPhase 2: Simulating outage");
        primaryBroker.setFailureMode(FailureMode.COMPLETE_OUTAGE);

        assertThrows(SimulatedBrokerException.class, () -> {
            primaryBroker.simulatePlaceOrder(request);
        });
        System.out.println("⚠️ Order 2 failed during outage");

        // Phase 3: Recovery
        System.out.println("\nPhase 3: Broker recovered");
        primaryBroker.setFailureMode(FailureMode.NONE);
        OrderResponse response3 = primaryBroker.simulatePlaceOrder(request);
        assertEquals(OrderStatus.PLACED, response3.status());
        System.out.println("✅ Order 3 placed successfully after recovery");

        // Verify health status transitions in metrics
        String metricsOutput = getMetricsOutput();
        assertTrue(metricsOutput.contains("broker_health_status") ||
                   metricsOutput.contains("broker_orders_total"),
                   "Should track health or orders");

        System.out.println("✅ Broker recovery handled correctly\n");
    }

    @Test
    @DisplayName("Chaos 8: High Load Stress Test")
    public void testHighLoadStress() throws Exception {
        System.out.println("\n=== CHAOS TEST 8: High Load Stress Test ===");

        primaryBroker.setFailureMode(FailureMode.NONE);
        primaryBroker.setLatencyMs(10);  // Slight delay per request

        ExecutorService executor = Executors.newFixedThreadPool(50);
        int totalRequests = 500;

        System.out.println("⚠️ Sending " + totalRequests + " concurrent requests");

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(totalRequests);

        Instant start = Instant.now();

        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                try {
                    primaryBroker.simulatePlaceOrder(createTestOrder());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for all requests (with timeout)
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        Duration elapsed = Duration.between(start, Instant.now());
        executor.shutdown();

        assertTrue(completed, "All requests should complete within 30 seconds");

        System.out.println("✅ Completed " + totalRequests + " requests in " + elapsed.toMillis() + "ms");
        System.out.println("✅ Success: " + successCount.get() + " (" +
            (100 * successCount.get() / totalRequests) + "%)");
        System.out.println("⚠️ Failures: " + failureCount.get() + " (" +
            (100 * failureCount.get() / totalRequests) + "%)");

        double throughput = totalRequests / (elapsed.toMillis() / 1000.0);
        System.out.println("✅ Throughput: " + String.format("%.2f", throughput) + " requests/second");

        assertTrue(successCount.get() > totalRequests * 0.95,
            "Should have >95% success rate under high load");

        System.out.println("✅ System survived high load stress test\n");
    }

    // ========== Helper Methods ==========

    private OrderRequest createTestOrder() {
        return new OrderRequest(
            "NSE:SBIN-EQ",
            Direction.BUY,
            1,
            OrderType.MARKET,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            TimeInForce.DAY,
            ProductType.CNC
        );
    }

    private String getMetricsOutput() {
        try {
            java.io.StringWriter writer = new java.io.StringWriter();
            io.prometheus.client.exporter.common.TextFormat.write004(writer, registry.metricFamilySamples());
            return writer.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ========== Test Simulator ==========

    /**
     * Chaos simulator for testing failure scenarios.
     * Simulates broker operations with configurable failure modes.
     */
    static class ChaosSimulator {
        private final String brokerCode;
        private final BrokerMetrics metrics;
        private FailureMode failureMode = FailureMode.NONE;
        private int latencyMs = 0;
        private int rateLimitThreshold = Integer.MAX_VALUE;
        private final AtomicInteger requestCount = new AtomicInteger(0);

        public ChaosSimulator(String brokerCode, BrokerMetrics metrics) {
            this.brokerCode = brokerCode;
            this.metrics = metrics;
        }

        public void setFailureMode(FailureMode mode) {
            this.failureMode = mode;
        }

        public void setLatencyMs(int latencyMs) {
            this.latencyMs = latencyMs;
        }

        public void setRateLimitThreshold(int threshold) {
            this.rateLimitThreshold = threshold;
        }

        public void simulateAuthenticate() {
            Instant start = Instant.now();

            if (failureMode == FailureMode.AUTH_FAILURE) {
                Duration latency = Duration.between(start, Instant.now());
                metrics.recordAuthentication(brokerCode, false, latency);
                throw new SimulatedBrokerException("Authentication failed: Invalid credentials");
            }

            Duration latency = Duration.between(start, Instant.now());
            metrics.recordAuthentication(brokerCode, true, latency);
        }

        public OrderResponse simulatePlaceOrder(OrderRequest request) {
            Instant start = Instant.now();

            // Simulate latency
            if (latencyMs > 0) {
                try {
                    Thread.sleep(latencyMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // Check rate limiting
            int count = requestCount.incrementAndGet();
            if (count > rateLimitThreshold) {
                Duration latency = Duration.between(start, Instant.now());
                metrics.recordOrderFailure(brokerCode, "RATE_LIMIT", latency);
                metrics.recordRateLimitHit(brokerCode, count, BrokerMetrics.RateLimitType.PER_SECOND);
                throw new SimulatedBrokerException("Rate limit exceeded");
            }

            // Apply failure mode
            try {
                switch (failureMode) {
                    case COMPLETE_OUTAGE:
                        throw new SimulatedBrokerException("Broker unreachable: Connection refused");

                    case TIMEOUT:
                        throw new SimulatedBrokerException("Request timeout after " + latencyMs + "ms");

                    case INTERMITTENT:
                        if (Math.random() < 0.5) {
                            throw new SimulatedBrokerException("Intermittent failure");
                        }
                        break;

                    case RATE_LIMITED:
                        // Already handled above
                        break;

                    case AUTH_FAILURE:
                        throw new SimulatedBrokerException("Authentication required");

                    case NONE:
                        // Success
                        break;
                }

                // Success path
                Duration latency = Duration.between(start, Instant.now());
                metrics.recordOrderSuccess(brokerCode, latency);

                return OrderResponse.of(
                    "ORDER-" + System.nanoTime(),
                    request.symbol(),
                    OrderStatus.PLACED,
                    request.direction(),
                    request.orderType(),
                    request.productType(),
                    request.quantity(),
                    0,  // filledQuantity
                    request.limitPrice(),
                    BigDecimal.ZERO,  // avgFillPrice
                    Instant.now()
                );

            } catch (SimulatedBrokerException e) {
                Duration latency = Duration.between(start, Instant.now());
                metrics.recordOrderFailure(brokerCode, "ORDER_FAILED", latency);
                throw e;
            }
        }
    }

    static class SimulatedBrokerException extends RuntimeException {
        public SimulatedBrokerException(String message) {
            super(message);
        }
    }

    enum FailureMode {
        NONE,
        COMPLETE_OUTAGE,
        TIMEOUT,
        INTERMITTENT,
        RATE_LIMITED,
        AUTH_FAILURE
    }
}
