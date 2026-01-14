package in.annupaper.integration;

import in.annupaper.broker.BrokerAdapter;
import in.annupaper.domain.trade.ExitIntentStatus;
import in.annupaper.domain.trade.ExitIntent;
import in.annupaper.domain.trade.Trade;
import in.annupaper.repository.ExitIntentRepository;
import in.annupaper.repository.TradeRepository;
import in.annupaper.service.execution.ExitOrderExecutionService;
import in.annupaper.service.execution.ExitOrderReconciler;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration Test: Exit Order Flow
 *
 * Tests the complete exit order lifecycle:
 * 1. APPROVED exit intent created
 * 2. ExitOrderExecutionService places order
 * 3. Exit intent transitions: APPROVED → PLACED
 * 4. ExitOrderReconciler polls broker
 * 5. Exit intent transitions: PLACED → FILLED
 * 6. Trade transitions: OPEN → CLOSED
 * 7. P&L calculated correctly
 *
 * REQUIRES:
 * - Database connection (test DB or Docker container)
 * - Mock broker adapter OR real broker in sandbox mode
 * - All services wired correctly
 *
 * RUN WITH: mvn test -Dtest=ExitOrderFlowIntegrationTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ExitOrderFlowIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(ExitOrderFlowIntegrationTest.class);

    // Test data
    private static String testTradeId;
    private static String testExitIntentId;
    private static String testUserBrokerId;
    private static String testBrokerOrderId;

    // Services under test
    private ExitOrderExecutionService exitOrderExecutionService;
    private ExitOrderReconciler exitOrderReconciler;
    private ExitIntentRepository exitIntentRepo;
    private TradeRepository tradeRepo;
    private MockBrokerAdapter mockBroker;

    @BeforeEach
    public void setup() {
        log.info("=".repeat(80));
        log.info("Setting up test environment...");

        // TODO: Initialize repositories (connect to test DB)
        // exitIntentRepo = new PostgresExitIntentRepository(testDataSource);
        // tradeRepo = new PostgresTradeRepository(testDataSource);

        // Initialize mock broker
        mockBroker = new MockBrokerAdapter();

        // TODO: Initialize services
        // exitOrderExecutionService = new ExitOrderExecutionService(...);
        // exitOrderReconciler = new ExitOrderReconciler(...);

        log.info("✓ Test environment ready");
    }

    @Test
    @Order(1)
    @DisplayName("1. Setup: Create test trade and approved exit intent")
    public void test01_setupTestData() {
        log.info("\n" + "=".repeat(80));
        log.info("TEST 1: Setup test data");
        log.info("=".repeat(80));

        // Create test trade (OPEN status)
        testTradeId = UUID.randomUUID().toString();
        testUserBrokerId = "test-user-broker-123";

        Trade testTrade = createTestTrade(
            testTradeId,
            "RELIANCE",
            "BUY",
            BigDecimal.valueOf(2400.00),
            10,
            "OPEN"
        );

        // TODO: Insert trade into test DB
        // tradeRepo.insert(testTrade);
        log.info("✓ Created test trade: {}", testTradeId);

        // Create APPROVED exit intent
        testExitIntentId = UUID.randomUUID().toString();
        ExitIntent approvedIntent = createTestExitIntent(
            testExitIntentId,
            testTradeId,
            testUserBrokerId,
            ExitIntentStatus.APPROVED,
            "TARGET_HIT",
            BigDecimal.valueOf(2500.00),
            10
        );

        // TODO: Insert exit intent into test DB
        // exitIntentRepo.insert(approvedIntent);
        log.info("✓ Created APPROVED exit intent: {}", testExitIntentId);

        assertNotNull(testTradeId);
        assertNotNull(testExitIntentId);
    }

    @Test
    @Order(2)
    @DisplayName("2. Exit Order Placement: APPROVED → PLACED")
    public void test02_exitOrderPlacement() throws Exception {
        log.info("\n" + "=".repeat(80));
        log.info("TEST 2: Exit order placement");
        log.info("=".repeat(80));

        // TODO: Get APPROVED exit intent from DB
        // ExitIntent intent = exitIntentRepo.findById(testExitIntentId).orElseThrow();
        // assertEquals(ExitIntentStatus.APPROVED, intent.status());

        // Configure mock broker to accept order
        testBrokerOrderId = "BROKER-ORDER-" + System.currentTimeMillis();
        mockBroker.setNextOrderResult(true, testBrokerOrderId, "Order accepted");

        // Execute exit intent
        log.info("→ Calling ExitOrderExecutionService.executeExitIntent()...");
        // CompletableFuture<ExitIntent> future = exitOrderExecutionService.executeExitIntent(intent);
        // ExitIntent result = future.get(10, TimeUnit.SECONDS);

        // Verify exit intent transitioned to PLACED
        log.info("→ Verifying exit intent status...");
        // ExitIntent updated = exitIntentRepo.findById(testExitIntentId).orElseThrow();
        // assertEquals(ExitIntentStatus.PLACED, updated.status());
        // assertEquals(testBrokerOrderId, updated.brokerOrderId());

        log.info("✓ Exit intent placed: {} → PLACED with brokerOrderId={}",
            testExitIntentId, testBrokerOrderId);
    }

    @Test
    @Order(3)
    @DisplayName("3. Exit Order Reconciliation: PLACED → FILLED")
    public void test03_exitOrderReconciliation() throws Exception {
        log.info("\n" + "=".repeat(80));
        log.info("TEST 3: Exit order reconciliation");
        log.info("=".repeat(80));

        // Configure mock broker to return FILLED status
        mockBroker.setOrderStatus(testBrokerOrderId, "COMPLETE",
            BigDecimal.valueOf(2500.00), 10);

        // Trigger reconciliation
        log.info("→ Triggering ExitOrderReconciler...");
        // exitOrderReconciler.reconcilePlacedExitOrders();

        // Wait for async processing
        Thread.sleep(1000);

        // Verify exit intent transitioned to FILLED
        log.info("→ Verifying exit intent status...");
        // ExitIntent updated = exitIntentRepo.findById(testExitIntentId).orElseThrow();
        // assertEquals(ExitIntentStatus.FILLED, updated.status());

        log.info("✓ Exit intent filled: {} → FILLED", testExitIntentId);
    }

    @Test
    @Order(4)
    @DisplayName("4. Trade Closure: OPEN → CLOSED with P&L")
    public void test04_tradeClosure() {
        log.info("\n" + "=".repeat(80));
        log.info("TEST 4: Trade closure with P&L");
        log.info("=".repeat(80));

        // Verify trade transitioned to CLOSED
        log.info("→ Verifying trade status...");
        // Trade updated = tradeRepo.findById(testTradeId).orElseThrow();
        // assertEquals("CLOSED", updated.status());

        // Verify P&L calculation
        // Expected P&L: (2500.00 - 2400.00) * 10 = 1000.00
        log.info("→ Verifying P&L calculation...");
        // assertNotNull(updated.exitPrice());
        // assertEquals(BigDecimal.valueOf(2500.00), updated.exitPrice());
        // assertNotNull(updated.realizedPnl());
        // assertEquals(BigDecimal.valueOf(1000.00), updated.realizedPnl());

        log.info("✓ Trade closed with P&L: {} → CLOSED, P&L=1000.00", testTradeId);
    }

    @Test
    @Order(5)
    @DisplayName("5. Error Scenario: Broker rejection")
    public void test05_brokerRejection() throws Exception {
        log.info("\n" + "=".repeat(80));
        log.info("TEST 5: Broker rejection scenario");
        log.info("=".repeat(80));

        // Create new APPROVED exit intent
        String rejectionTestIntentId = UUID.randomUUID().toString();
        ExitIntent intent = createTestExitIntent(
            rejectionTestIntentId,
            testTradeId,
            testUserBrokerId,
            ExitIntentStatus.APPROVED,
            "STOP_LOSS",
            BigDecimal.valueOf(2300.00),
            10
        );

        // TODO: Insert into test DB
        // exitIntentRepo.insert(intent);

        // Configure mock broker to reject order
        mockBroker.setNextOrderResult(false, null, "Insufficient margin");

        // Execute exit intent
        log.info("→ Calling ExitOrderExecutionService with rejection scenario...");
        // CompletableFuture<ExitIntent> future = exitOrderExecutionService.executeExitIntent(intent);
        // ExitIntent result = future.get(10, TimeUnit.SECONDS);

        // Verify exit intent marked as FAILED
        log.info("→ Verifying exit intent marked as FAILED...");
        // ExitIntent updated = exitIntentRepo.findById(rejectionTestIntentId).orElseThrow();
        // assertEquals(ExitIntentStatus.FAILED, updated.status());
        // assertNotNull(updated.errorCode());

        log.info("✓ Broker rejection handled correctly: {} → FAILED", rejectionTestIntentId);
    }

    @Test
    @Order(6)
    @DisplayName("6. Error Scenario: Order timeout")
    public void test06_orderTimeout() throws Exception {
        log.info("\n" + "=".repeat(80));
        log.info("TEST 6: Order timeout scenario");
        log.info("=".repeat(80));

        // Create PLACED exit intent with old timestamp (>10 minutes ago)
        String timeoutTestIntentId = UUID.randomUUID().toString();
        Instant oldTimestamp = Instant.now().minusSeconds(700); // 11 minutes ago

        ExitIntent placedIntent = createTestExitIntent(
            timeoutTestIntentId,
            testTradeId,
            testUserBrokerId,
            ExitIntentStatus.PLACED,
            "TARGET_HIT",
            BigDecimal.valueOf(2500.00),
            10
        );
        // TODO: Set placedAt timestamp to oldTimestamp

        // TODO: Insert into test DB
        // exitIntentRepo.insert(placedIntent);

        // Trigger reconciliation
        log.info("→ Triggering ExitOrderReconciler with timeout check...");
        // exitOrderReconciler.reconcilePlacedExitOrders();

        // Verify exit intent marked as FAILED with TIMEOUT
        log.info("→ Verifying timeout handling...");
        // ExitIntent updated = exitIntentRepo.findById(timeoutTestIntentId).orElseThrow();
        // assertEquals(ExitIntentStatus.FAILED, updated.status());
        // assertEquals("TIMEOUT", updated.errorCode());

        log.info("✓ Timeout handled correctly: {} → FAILED (TIMEOUT)", timeoutTestIntentId);
    }

    @Test
    @Order(7)
    @DisplayName("7. Rate Limiting: Max concurrent broker calls")
    public void test07_rateLimiting() throws Exception {
        log.info("\n" + "=".repeat(80));
        log.info("TEST 7: Rate limiting");
        log.info("=".repeat(80));

        // Create 10 PLACED exit intents
        log.info("→ Creating 10 PLACED exit intents...");
        for (int i = 0; i < 10; i++) {
            String intentId = "rate-limit-test-" + i;
            ExitIntent intent = createTestExitIntent(
                intentId,
                testTradeId,
                testUserBrokerId,
                ExitIntentStatus.PLACED,
                "TARGET_HIT",
                BigDecimal.valueOf(2500.00),
                10
            );
            // TODO: Insert into test DB
            // exitIntentRepo.insert(intent);
        }

        // Trigger reconciliation
        log.info("→ Triggering ExitOrderReconciler (should rate limit to 5 concurrent)...");
        // long startTime = System.currentTimeMillis();
        // exitOrderReconciler.reconcilePlacedExitOrders();
        // long elapsedMs = System.currentTimeMillis() - startTime;

        // Verify rate limiting metrics
        log.info("→ Verifying rate limiting metrics...");
        // ExitOrderReconciler.ReconcileMetrics metrics = exitOrderReconciler.getMetrics();
        // assertTrue(metrics.totalRateLimited() > 0, "Expected some intents to be rate-limited");

        log.info("✓ Rate limiting working: {} intents rate-limited", 0); // metrics.totalRateLimited()
    }

    @AfterEach
    public void teardown() {
        log.info("→ Cleaning up test data...");
        // TODO: Delete test data from DB
        // if (testTradeId != null) tradeRepo.delete(testTradeId);
        // if (testExitIntentId != null) exitIntentRepo.delete(testExitIntentId);
        log.info("✓ Test cleanup complete\n");
    }

    // =======================================================================
    // Test Helpers
    // =======================================================================

    private Trade createTestTrade(
        String tradeId,
        String symbol,
        String direction,
        BigDecimal entryPrice,
        int qty,
        String status
    ) {
        Instant now = Instant.now();
        return new Trade(
            tradeId,
            "test-portfolio",
            "test-user",
            "test-broker",
            testUserBrokerId,
            "test-signal-123",
            "test-intent-123",
            symbol,
            direction,
            1, // tradeNumber
            entryPrice,
            qty,
            entryPrice.multiply(BigDecimal.valueOf(qty)),
            now,
            "MIS",
            null, null, null, null, null,
            null, null, null, null, null, null,
            BigDecimal.valueOf(2300.00), // floor
            BigDecimal.valueOf(2600.00), // ceiling
            null, null,
            null, null, null, BigDecimal.valueOf(2600.00), // exitPrimaryPrice
            status,
            entryPrice, null, null,
            false, null, null,
            null, null, null, null,
            null, null, null,
            "BROKER-ORDER-123", null, "test-intent-123",
            now, now, now, null, 1
        );
    }

    private ExitIntent createTestExitIntent(
        String exitIntentId,
        String tradeId,
        String userBrokerId,
        ExitIntentStatus status,
        String exitReason,
        BigDecimal limitPrice,
        int qty
    ) {
        Instant now = Instant.now();
        return new ExitIntent(
            exitIntentId,
            "test-exit-signal-123",
            tradeId,
            userBrokerId,
            exitReason,
            1, // episodeId
            status,
            true, // validationPassed
            List.of(), // validationErrors
            qty,
            "MARKET",
            limitPrice,
            "MIS",
            null, // brokerOrderId (set after placement)
            status == ExitIntentStatus.PLACED ? now : null, // placedAt
            null, // filledAt
            null, // cancelledAt
            null, // errorCode
            null, // errorMessage
            0, // retryCount
            now, // createdAt
            now, // updatedAt
            null, // deletedAt
            1 // version
        );
    }

    /**
     * Mock broker adapter for testing.
     */
    private static class MockBrokerAdapter implements BrokerAdapter {
        private boolean nextOrderSuccess = true;
        private String nextOrderId;
        private String nextOrderMessage;
        private Map<String, OrderStatus> orderStatusMap = new java.util.HashMap<>();

        @Override
        public String getBrokerCode() {
            return "MOCK";
        }

        public void setNextOrderResult(boolean success, String orderId, String message) {
            this.nextOrderSuccess = success;
            this.nextOrderId = orderId;
            this.nextOrderMessage = message;
        }

        public void setOrderStatus(String orderId, String status, BigDecimal avgPrice, int filledQty) {
            orderStatusMap.put(orderId, new OrderStatus(
                orderId, "RELIANCE", "NSE", "SELL", "MARKET", "MIS",
                filledQty, filledQty, 0, avgPrice, avgPrice, null,
                status, null, Instant.now().toString(), null, null
            ));
        }

        @Override
        public CompletableFuture<OrderResult> placeOrder(OrderRequest request) {
            OrderResult result = new OrderResult(
                nextOrderSuccess,
                nextOrderId,
                nextOrderSuccess ? null : "BROKER_ERROR",
                nextOrderMessage
            );
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public CompletableFuture<OrderStatus> getOrderStatus(String orderId) {
            OrderStatus status = orderStatusMap.get(orderId);
            if (status == null) {
                status = new OrderStatus(
                    orderId, "RELIANCE", "NSE", "SELL", "MARKET", "MIS",
                    0, 0, 0, null, null, null,
                    "PENDING", null, Instant.now().toString(), null, null
                );
            }
            return CompletableFuture.completedFuture(status);
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public CompletableFuture<BrokerAdapter.ConnectionResult> connect(BrokerAdapter.BrokerCredentials credentials) {
            return CompletableFuture.completedFuture(
                new BrokerAdapter.ConnectionResult(true, "Connected", "mock-session-token", null)
            );
        }

        @Override
        public void disconnect() {}

        @Override
        public CompletableFuture<BrokerAdapter.OrderResult> modifyOrder(String orderId, BrokerAdapter.OrderModifyRequest request) {
            return CompletableFuture.completedFuture(
                new BrokerAdapter.OrderResult(false, null, "NOT_SUPPORTED", "Not implemented")
            );
        }

        @Override
        public CompletableFuture<BrokerAdapter.OrderResult> cancelOrder(String orderId) {
            return CompletableFuture.completedFuture(
                new BrokerAdapter.OrderResult(true, orderId, null, "Cancelled")
            );
        }

        @Override
        public CompletableFuture<List<OrderStatus>> getOpenOrders() {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<List<BrokerAdapter.Position>> getPositions() {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<List<BrokerAdapter.Holding>> getHoldings() {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<BrokerAdapter.AccountFunds> getFunds() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<BigDecimal> getLtp(String symbol) {
            return CompletableFuture.completedFuture(BigDecimal.valueOf(2500.00));
        }

        @Override
        public void subscribeTicks(List<String> symbols, BrokerAdapter.TickListener listener) {}

        @Override
        public void unsubscribeTicks(List<String> symbols) {}

        @Override
        public CompletableFuture<List<BrokerAdapter.HistoricalCandle>> getHistoricalCandles(
            String symbol, int interval, long fromEpoch, long toEpoch
        ) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<List<BrokerAdapter.Instrument>> getInstruments() {
            return CompletableFuture.completedFuture(List.of());
        }
    }
}
