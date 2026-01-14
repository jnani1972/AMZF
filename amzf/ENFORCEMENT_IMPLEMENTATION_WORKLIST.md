# ENFORCEMENT IMPLEMENTATION WORKLIST
# AnnuPaper v04 - Making Framework Ungameable (Execution-Grade)

**Date:** January 13, 2026
**Purpose:** Executable task list with deliverables + acceptance proofs
**Total Effort:** ~28-36 hours spread across P0/P1/P2

---

## EXECUTION ORDER (FASTEST TO STRONGEST)

```
Day 1: P0-A (Startup Gate) → 2h
Day 1: P0-B (DB Constraints) → 2h
Day 2: P0-C (Reconciliation Loop) → 4-6h
Day 3: P0-E (Single-Writer Transitions) → 3-4h
Day 4: P0-D (Tick Dedupe) → 4-6h
Week 2: P1-A/B/C (Metrics + Observability) → 6-8h
Week 2: P1-D/E (Validation Lock + Async Writer) → 4-6h
Week 3: P2-A (Risk Firewall) → 2-3h
```

**Total:** 27-37 hours (3-4 weeks part-time, 1 week full-time)

---

## P0 — "CANNOT LIE" ENFORCEMENTS (Must Land First)

### P0-A: STARTUP VALIDATION GATE
**Priority:** 🔴 P0 - Day 1
**Effort:** 2 hours
**Dependencies:** None

#### Deliverables

**1. Create `StartupConfigValidator.java`**

```java
package in.annupaper.bootstrap;

import in.annupaper.util.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StartupConfigValidator {
    private static final Logger log = LoggerFactory.getLogger(StartupConfigValidator.class);

    /**
     * Validates config at startup. Throws IllegalStateException if invalid.
     * Called by App.main() before system starts.
     */
    public static void validate(Config config) {
        log.info("Running startup config validation...");

        // GATE 1: Production mode requires order execution
        if (config.isProductionMode()) {
            validateProductionMode(config);
        } else {
            warnNonProductionMode(config);
        }

        log.info("✅ Startup config validation passed");
    }

    private static void validateProductionMode(Config config) {
        log.info("PRODUCTION MODE detected - enforcing strict validation");

        // Check 1: Order execution must be enabled
        if (!config.isOrderExecutionEnabled()) {
            throw new IllegalStateException(
                "INVALID CONFIG: PRODUCTION MODE requires order.execution.enabled=true\n" +
                "System refuses to start.\n" +
                "Either:\n" +
                "  1. Enable order execution: set order.execution.enabled=true\n" +
                "  2. Set production.mode=false for testing/paper trading"
            );
        }

        // Check 2: No test broker APIs
        for (Config.BrokerConfig broker : config.getBrokers()) {
            if (broker.apiUrl.contains("test") ||
                broker.apiUrl.contains("-t1.") ||
                broker.apiUrl.contains("sandbox")) {
                throw new IllegalStateException(
                    "INVALID CONFIG: PRODUCTION MODE forbids test/sandbox broker APIs\n" +
                    "Broker: " + broker.name + "\n" +
                    "URL: " + broker.apiUrl + "\n" +
                    "Either:\n" +
                    "  1. Use production API URL\n" +
                    "  2. Set production.mode=false"
                );
            }
        }

        // Check 3: Async event writer if tick persistence enabled
        if (config.persist.tickEvents && !config.asyncEventWriter.enabled) {
            throw new IllegalStateException(
                "INVALID CONFIG: Tick event persistence requires asyncEventWriter.enabled=true\n" +
                "Direct DB writes on broker thread are FORBIDDEN (P0 invariant).\n" +
                "Either:\n" +
                "  1. Enable async writer: set asyncEventWriter.enabled=true\n" +
                "  2. Disable tick persistence: set persist.tickEvents=false"
            );
        }

        // Check 4: Release readiness check (P0 TODOs)
        if (config.release.readiness == ReleaseReadiness.PROD_READY) {
            // TODO: Add checks for P0 TODOs resolved
            // For now, manual verification required
            log.warn("Release readiness set to PROD_READY - ensure all P0 TODOs resolved");
        }

        log.info("✅ PRODUCTION MODE validation passed");
    }

    private static void warnNonProductionMode(Config config) {
        log.warn("⚠️  NON-PRODUCTION MODE detected");

        if (!config.isOrderExecutionEnabled()) {
            log.warn("⚠️  Order execution DISABLED - Paper trading mode active");
        }

        for (Config.BrokerConfig broker : config.getBrokers()) {
            if (broker.apiUrl.contains("test") || broker.apiUrl.contains("-t1.")) {
                log.warn("⚠️  Test API detected: {} - {}", broker.name, broker.apiUrl);
            }
        }

        log.warn("⚠️  System running in non-production mode - features may be limited");
    }
}
```

**2. Update `App.java` to call validator**

```java
package in.annupaper.bootstrap;

public class App {
    private static final Logger log = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        log.info("=== AnnuPaper v04 Starting ===");

        // Load config
        Config config = loadConfig();

        // ✅ VALIDATE CONFIG BEFORE STARTING (P0-A)
        try {
            StartupConfigValidator.validate(config);
        } catch (IllegalStateException e) {
            log.error("❌ STARTUP VALIDATION FAILED", e);
            System.err.println("\n" + e.getMessage() + "\n");
            System.exit(1);
        }

        // Start application
        startApplication(config);
    }

    // ... rest of App.java
}
```

**3. Add config properties**

```properties
# config.properties (or application.yml)

# Production mode flag
production.mode=false  # Set to true for production

# Order execution
order.execution.enabled=false  # Set to true for live trading

# Async event writer (required if tick persistence enabled)
asyncEventWriter.enabled=false
asyncEventWriter.queueSize=100000
asyncEventWriter.batchSize=1000

# Tick event persistence (optional, for audit/replay)
persist.tickEvents=false

# Release readiness
release.readiness=BETA  # BETA | PROD_READY
```

#### Acceptance Proof

**Test 1: Invalid production config fails**
```bash
# Set production.mode=true, order.execution.enabled=false
./start-backend.sh

# Expected output:
# ❌ STARTUP VALIDATION FAILED
# INVALID CONFIG: PRODUCTION MODE requires order.execution.enabled=true
# System refuses to start.
# Process exits with code 1
```

**Test 2: Valid production config passes**
```bash
# Set production.mode=true, order.execution.enabled=true, broker URLs = production
./start-backend.sh

# Expected log:
# ✅ PRODUCTION MODE validation passed
# ✅ Startup config validation passed
# System starts successfully
```

**Test 3: Non-production warns but allows**
```bash
# Set production.mode=false
./start-backend.sh

# Expected log:
# ⚠️  NON-PRODUCTION MODE detected
# ⚠️  Order execution DISABLED - Paper trading mode active
# ✅ Startup config validation passed
# System starts
```

#### Checklist
- [ ] Created `StartupConfigValidator.java`
- [ ] Added `validate()` call to `App.main()`
- [ ] Added config properties
- [ ] Test 1 passed (invalid config fails)
- [ ] Test 2 passed (valid production succeeds)
- [ ] Test 3 passed (non-production warns)
- [ ] Documented in ADDENDUM.md Section 8.2.3

---

### P0-B: DB UNIQUENESS + UPSERT IDEMPOTENCY
**Priority:** 🔴 P0 - Day 1
**Effort:** 2 hours
**Dependencies:** None

#### Deliverables

**1. Create SQL migration `V015__add_idempotency_constraints.sql`**

```sql
-- Migration: Add idempotency constraints
-- Purpose: Enforce uniqueness for trades and signals (V2 #7)
-- Date: 2026-01-13

-- ============================================
-- TRADES TABLE: Idempotency Constraints
-- ============================================

-- Constraint 1: One trade per intent (primary idempotency key)
ALTER TABLE trades
    ADD CONSTRAINT uq_trades_intent_id UNIQUE (intent_id);

-- Constraint 2: One trade per client order ID (broker idempotency)
ALTER TABLE trades
    ADD CONSTRAINT uq_trades_client_order_id UNIQUE (client_order_id);

-- Constraint 3: One trade per broker order ID (if broker provides stable ID)
-- Note: broker_order_id can be NULL initially (before order placed)
ALTER TABLE trades
    ADD CONSTRAINT uq_trades_broker_order_id UNIQUE (broker_order_id)
    WHERE broker_order_id IS NOT NULL;

-- Index for fast PENDING trade queries (reconciliation)
CREATE INDEX IF NOT EXISTS idx_trades_pending
    ON trades(status, updated_at)
    WHERE status = 'PENDING';

-- Index for fast OPEN trade queries (exit monitoring)
CREATE INDEX IF NOT EXISTS idx_trades_open
    ON trades(status, symbol, user_broker_id)
    WHERE status = 'OPEN';

-- ============================================
-- SIGNALS TABLE: Deduplication Constraint
-- ============================================

-- Fix precision: Use NUMERIC(18,2) for price fields to avoid float noise
ALTER TABLE signals
    ALTER COLUMN effective_floor TYPE NUMERIC(18, 2),
    ALTER COLUMN effective_ceiling TYPE NUMERIC(18, 2),
    ALTER COLUMN entry_price TYPE NUMERIC(18, 2),
    ALTER COLUMN target_price TYPE NUMERIC(18, 2),
    ALTER COLUMN stop_loss_price TYPE NUMERIC(18, 2);

-- Unique constraint: Prevent duplicate signals for same symbol/day/zone
CREATE UNIQUE INDEX idx_signal_dedupe ON signals (
    symbol,
    confluence_type,
    DATE(generated_at),       -- Same calendar day (handles timezone)
    effective_floor,          -- NUMERIC(18,2) - no float noise
    effective_ceiling
);

-- ============================================
-- UPSERT SUPPORT (Optional Helper Function)
-- ============================================

-- Example: Upsert trade function (alternative to application-level upsert)
CREATE OR REPLACE FUNCTION upsert_trade(
    p_trade_id UUID,
    p_intent_id UUID,
    p_client_order_id VARCHAR(100),
    p_broker_order_id VARCHAR(100),
    p_status VARCHAR(20),
    p_filled_qty NUMERIC(18, 8),
    p_avg_fill_price NUMERIC(18, 2)
) RETURNS trades AS $$
DECLARE
    v_trade trades;
BEGIN
    INSERT INTO trades (trade_id, intent_id, client_order_id, broker_order_id, status, filled_qty, avg_fill_price, updated_at)
    VALUES (p_trade_id, p_intent_id, p_client_order_id, p_broker_order_id, p_status, p_filled_qty, p_avg_fill_price, NOW())
    ON CONFLICT (intent_id) DO UPDATE SET
        broker_order_id = EXCLUDED.broker_order_id,
        status = EXCLUDED.status,
        filled_qty = EXCLUDED.filled_qty,
        avg_fill_price = EXCLUDED.avg_fill_price,
        updated_at = NOW()
    RETURNING * INTO v_trade;

    RETURN v_trade;
END;
$$ LANGUAGE plpgsql;

-- ============================================
-- VERIFICATION QUERIES
-- ============================================

-- Check constraints exist
SELECT conname, contype, pg_get_constraintdef(oid)
FROM pg_constraint
WHERE conrelid = 'trades'::regclass
  AND conname LIKE 'uq_trades_%';

SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename = 'signals'
  AND indexname = 'idx_signal_dedupe';

-- Test idempotency (should succeed without error)
-- INSERT INTO trades (trade_id, intent_id, client_order_id, ...) VALUES (...)
-- ON CONFLICT (intent_id) DO UPDATE SET status = EXCLUDED.status;
```

**2. Update `TradeRepository` with upsert method**

```java
package in.annupaper.repository;

import in.annupaper.domain.model.Trade;

public interface TradeRepository {

    /**
     * Upsert trade (idempotent insert/update).
     * Uses intent_id as unique key - if exists, updates; if not, inserts.
     *
     * @param trade Trade to upsert
     * @return Persisted trade (with DB-generated fields if new)
     */
    Trade upsert(Trade trade);

    /**
     * Find trade by intent ID (idempotency lookup).
     *
     * @param intentId Intent ID (unique per signal × user-broker)
     * @return Trade if exists, null otherwise
     */
    Trade findByIntentId(String intentId);

    /**
     * Find all trades with given status.
     *
     * @param status Trade status (PENDING, OPEN, CLOSED, etc.)
     * @return List of trades
     */
    List<Trade> findByStatus(TradeStatus status);
}
```

**3. Implement upsert in `PostgresTradeRepository`**

```java
package in.annupaper.repository;

public class PostgresTradeRepository implements TradeRepository {

    private static final String UPSERT_SQL = """
        INSERT INTO trades (
            trade_id, intent_id, client_order_id, broker_order_id,
            user_broker_id, signal_id, symbol, direction,
            entry_price, target_price, stop_loss_price,
            ordered_qty, filled_qty, avg_fill_price,
            status, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
        ON CONFLICT (intent_id) DO UPDATE SET
            broker_order_id = EXCLUDED.broker_order_id,
            filled_qty = EXCLUDED.filled_qty,
            avg_fill_price = EXCLUDED.avg_fill_price,
            status = EXCLUDED.status,
            updated_at = NOW()
        RETURNING *
        """;

    @Override
    public Trade upsert(Trade trade) {
        try {
            return jdbcTemplate.queryForObject(
                UPSERT_SQL,
                new BeanPropertyRowMapper<>(Trade.class),
                trade.tradeId(), trade.intentId(), trade.clientOrderId(), trade.brokerOrderId(),
                trade.userBrokerId(), trade.signalId(), trade.symbol(), trade.direction(),
                trade.entryPrice(), trade.targetPrice(), trade.stopLossPrice(),
                trade.orderedQty(), trade.filledQty(), trade.avgFillPrice(),
                trade.status()
            );
        } catch (DataAccessException e) {
            log.error("Failed to upsert trade: {}", trade.tradeId(), e);
            throw new RepositoryException("Trade upsert failed", e);
        }
    }

    @Override
    public Trade findByIntentId(String intentId) {
        String sql = "SELECT * FROM trades WHERE intent_id = ?";
        try {
            return jdbcTemplate.queryForObject(sql, new BeanPropertyRowMapper<>(Trade.class), intentId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public List<Trade> findByStatus(TradeStatus status) {
        String sql = "SELECT * FROM trades WHERE status = ? ORDER BY updated_at DESC";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(Trade.class), status.name());
    }
}
```

#### Acceptance Proof

**Test 1: Duplicate intent_id rejected**
```java
// Test
Trade trade1 = Trade.create(intentId, ...);
tradeRepository.upsert(trade1);  // Insert

Trade trade2 = Trade.create(intentId, ...);  // Same intentId
tradeRepository.upsert(trade2);  // Update (not new insert)

List<Trade> trades = jdbcTemplate.query("SELECT * FROM trades WHERE intent_id = ?", intentId);
assertEquals(1, trades.size());  // Only one trade exists
```

**Test 2: Signal dedupe works**
```sql
-- Insert signal
INSERT INTO signals (signal_id, symbol, confluence_type, generated_at, effective_floor, effective_ceiling, ...)
VALUES (uuid_generate_v4(), 'RELIANCE', 'TRIPLE', NOW(), 2400.00, 2500.00, ...);

-- Try to insert duplicate (same symbol, confluence, day, zone)
INSERT INTO signals (signal_id, symbol, confluence_type, generated_at, effective_floor, effective_ceiling, ...)
VALUES (uuid_generate_v4(), 'RELIANCE', 'TRIPLE', NOW(), 2400.00, 2500.00, ...);
-- Expected: ERROR: duplicate key value violates unique constraint "idx_signal_dedupe"

-- Or use ON CONFLICT (idempotent)
INSERT INTO signals (...) VALUES (...)
ON CONFLICT (symbol, confluence_type, DATE(generated_at), effective_floor, effective_ceiling)
DO UPDATE SET last_checked_at = NOW()
RETURNING *;
-- Expected: Returns existing signal (idempotent)
```

**Test 3: Float precision doesn't break dedupe**
```sql
-- Both should dedupe (despite slight float differences)
INSERT INTO signals (..., effective_floor, ...) VALUES (..., 2400.001, ...);
INSERT INTO signals (..., effective_floor, ...) VALUES (..., 2400.0049, ...);
-- After NUMERIC(18,2): Both become 2400.00 → Dedupe works
```

#### Checklist
- [ ] Created SQL migration with constraints
- [ ] Added upsert method to TradeRepository
- [ ] Implemented upsert in PostgresTradeRepository
- [ ] Test 1 passed (duplicate intent rejected)
- [ ] Test 2 passed (signal dedupe works)
- [ ] Test 3 passed (float precision fixed)
- [ ] Run migration: `flyway migrate`
- [ ] Documented in ADDENDUM.md Section 8.4.1

---

### P0-C: BROKER RECONCILIATION LOOP
**Priority:** 🔴 P0 - Day 2
**Effort:** 4-6 hours
**Dependencies:** P0-B (Trade upsert)

#### Deliverables

**1. Create `PendingOrderReconciler.java`**

```java
package in.annupaper.service.execution;

import in.annupaper.broker.BrokerAdapter;
import in.annupaper.broker.BrokerAdapterFactory;
import in.annupaper.domain.enums.TradeStatus;
import in.annupaper.domain.model.Trade;
import in.annupaper.domain.model.UserBroker;
import in.annupaper.repository.TradeRepository;
import in.annupaper.repository.UserBrokerRepository;
import in.annupaper.service.core.EventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Reconciles pending orders with broker reality.
 *
 * Purpose: Make system restart-safe and handle missed fill callbacks.
 * Runs every 30-60 seconds, queries broker for PENDING orders, updates DB.
 *
 * V2 #7: 3-Layer Idempotency (Layer 3 - Reconciliation)
 */
public class PendingOrderReconciler {
    private static final Logger log = LoggerFactory.getLogger(PendingOrderReconciler.class);

    private final TradeRepository tradeRepository;
    private final UserBrokerRepository userBrokerRepository;
    private final BrokerAdapterFactory brokerFactory;
    private final EventService eventService;
    private final ScheduledExecutorService scheduler;

    private final Duration reconcileInterval;
    private final Duration pendingTimeout;

    // Metrics
    private long lastReconcileCount = 0;
    private long totalReconciled = 0;
    private long totalUpdates = 0;
    private Instant lastReconcileTime = Instant.now();

    public PendingOrderReconciler(
            TradeRepository tradeRepository,
            UserBrokerRepository userBrokerRepository,
            BrokerAdapterFactory brokerFactory,
            EventService eventService,
            Duration reconcileInterval,
            Duration pendingTimeout) {
        this.tradeRepository = tradeRepository;
        this.userBrokerRepository = userBrokerRepository;
        this.brokerFactory = brokerFactory;
        this.eventService = eventService;
        this.reconcileInterval = reconcileInterval;
        this.pendingTimeout = pendingTimeout;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "pending-order-reconciler")
        );
    }

    /**
     * Start reconciliation loop.
     * Call this from App startup after all services initialized.
     */
    public void start() {
        long initialDelay = 10; // Start after 10 seconds (allow system to stabilize)
        long period = reconcileInterval.toSeconds();

        scheduler.scheduleAtFixedRate(
            this::reconcilePendingOrders,
            initialDelay,
            period,
            TimeUnit.SECONDS
        );

        log.info("Pending order reconciler started: interval={}s, timeout={}s",
            period, pendingTimeout.toSeconds());
    }

    /**
     * Stop reconciliation loop (for graceful shutdown).
     */
    public void stop() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Pending order reconciler stopped");
    }

    /**
     * Reconcile all pending orders (scheduled job).
     */
    private void reconcilePendingOrders() {
        try {
            Instant start = Instant.now();
            List<Trade> pending = tradeRepository.findByStatus(TradeStatus.PENDING);

            if (pending.isEmpty()) {
                log.debug("No pending orders to reconcile");
                lastReconcileCount = 0;
                lastReconcileTime = Instant.now();
                return;
            }

            log.info("Reconciling {} pending orders...", pending.size());
            int updated = 0;

            for (Trade trade : pending) {
                try {
                    if (reconcileTrade(trade)) {
                        updated++;
                    }
                } catch (Exception e) {
                    log.error("Failed to reconcile trade {}: {}", trade.tradeId(), e.getMessage());
                    // Continue to next trade (don't fail entire reconciliation)
                }
            }

            Duration elapsed = Duration.between(start, Instant.now());
            lastReconcileCount = pending.size();
            lastReconcileTime = Instant.now();
            totalReconciled += pending.size();
            totalUpdates += updated;

            log.info("Reconciliation complete: checked={}, updated={}, elapsed={}ms",
                pending.size(), updated, elapsed.toMillis());

            // Metrics (TODO: Replace with actual metrics library)
            // Metrics.gauge("reconcile.pending.checked", pending.size());
            // Metrics.gauge("reconcile.pending.updated", updated);

        } catch (Exception e) {
            log.error("Reconciliation job failed", e);
            // Metrics.counter("reconcile.job.fail").inc();
        }
    }

    /**
     * Reconcile single trade with broker.
     *
     * @param trade Trade to reconcile
     * @return true if trade was updated, false if no change
     */
    private boolean reconcileTrade(Trade trade) {
        // Check timeout (optional: mark as FAILED if pending too long)
        if (Duration.between(trade.createdAt(), Instant.now()).compareTo(pendingTimeout) > 0) {
            log.warn("Trade {} pending timeout ({}s), marking as TIMEOUT",
                trade.tradeId(), pendingTimeout.toSeconds());
            Trade timedOut = trade.markTimeout();
            tradeRepository.upsert(timedOut);
            eventService.emitUserBroker(EventType.ORDER_TIMEOUT, timedOut, trade.userBrokerId());
            return true;
        }

        // Get broker adapter
        UserBroker userBroker = userBrokerRepository.findById(trade.userBrokerId());
        if (userBroker == null) {
            log.warn("UserBroker not found for trade {}: {}", trade.tradeId(), trade.userBrokerId());
            return false;
        }

        BrokerAdapter broker = brokerFactory.getAdapter(userBroker);
        if (broker == null || !broker.isConnected()) {
            log.debug("Broker not available for reconciliation: {}", userBroker.brokerName());
            return false;
        }

        // Query broker for order status
        try {
            OrderStatus status = broker.getOrderStatus(
                trade.brokerOrderId(),   // Broker's order ID (if available)
                trade.clientOrderId()    // Our intentId (fallback)
            );

            if (status == null) {
                log.debug("Broker returned no status for trade {}", trade.tradeId());
                return false;
            }

            // Update trade based on broker response
            Trade updated = updateFromBrokerStatus(trade, status);

            if (updated != trade) {
                tradeRepository.upsert(updated);
                eventService.emitUserBroker(EventType.ORDER_STATUS_CHANGED, updated, trade.userBrokerId());
                log.info("Reconciled trade {}: {} → {}", trade.tradeId(), trade.status(), updated.status());
                return true;
            }

            return false;

        } catch (Exception e) {
            log.error("Failed to query broker for trade {}: {}", trade.tradeId(), e.getMessage());
            return false;
        }
    }

    /**
     * Update trade based on broker status response.
     */
    private Trade updateFromBrokerStatus(Trade trade, OrderStatus status) {
        return switch (status.status) {
            case "FILLED", "COMPLETE" -> {
                log.info("Trade {} filled by broker: qty={}, price={}",
                    trade.tradeId(), status.filledQty, status.avgPrice);
                yield trade.markFilled(status.filledQty, status.avgPrice);
            }
            case "REJECTED" -> {
                log.warn("Trade {} rejected by broker: {}", trade.tradeId(), status.rejectReason);
                yield trade.markRejected(status.rejectReason);
            }
            case "CANCELLED" -> {
                log.info("Trade {} cancelled by broker", trade.tradeId());
                yield trade.markCancelled();
            }
            case "PENDING", "OPEN", "TRIGGER_PENDING" -> {
                // Still pending, no update needed
                log.debug("Trade {} still pending at broker", trade.tradeId());
                yield trade;
            }
            default -> {
                log.warn("Unknown broker status for trade {}: {}", trade.tradeId(), status.status);
                yield trade;
            }
        };
    }

    /**
     * Get reconciliation metrics (for observability).
     */
    public ReconcileMetrics getMetrics() {
        return new ReconcileMetrics(
            lastReconcileCount,
            lastReconcileTime,
            totalReconciled,
            totalUpdates
        );
    }

    public record ReconcileMetrics(
        long lastChecked,
        Instant lastRunTime,
        long totalChecked,
        long totalUpdated
    ) {}
}
```

**2. Add `getOrderStatus()` to `BrokerAdapter` interface**

```java
package in.annupaper.broker;

public interface BrokerAdapter {

    // ... existing methods ...

    /**
     * Query broker for order status (for reconciliation).
     *
     * @param brokerOrderId Broker's order ID (if available)
     * @param clientOrderId Our client order ID (intentId, for idempotency)
     * @return OrderStatus if found, null if not found
     * @throws BrokerException if query fails
     */
    OrderStatus getOrderStatus(String brokerOrderId, String clientOrderId) throws BrokerException;

    /**
     * Order status from broker.
     */
    record OrderStatus(
        String orderId,          // Broker's order ID
        String clientOrderId,    // Our client order ID (intentId)
        String status,           // "PENDING" | "FILLED" | "REJECTED" | "CANCELLED" | etc.
        BigDecimal filledQty,    // Filled quantity
        BigDecimal avgPrice,     // Average fill price
        String rejectReason,     // Rejection reason (if status = REJECTED)
        Instant updatedAt        // Last update time from broker
    ) {}
}
```

**3. Implement in `FyersAdapter` (example)**

```java
package in.annupaper.broker.adapters;

public class FyersAdapter implements BrokerAdapter {

    @Override
    public OrderStatus getOrderStatus(String brokerOrderId, String clientOrderId) throws BrokerException {
        try {
            // Option 1: Query by broker order ID (if available)
            if (brokerOrderId != null && !brokerOrderId.isEmpty()) {
                String url = apiUrl + "/orders/" + brokerOrderId;
                // ... make HTTP GET request ...
            }

            // Option 2: Query by client order ID (idempotency key)
            if (clientOrderId != null) {
                String url = apiUrl + "/orders?client_id=" + clientOrderId;
                // ... make HTTP GET request ...
            }

            // Parse response and return OrderStatus
            // ...

        } catch (Exception e) {
            throw new BrokerException("Failed to query order status", e);
        }
    }
}
```

**4. Register reconciler in `App.java`**

```java
package in.annupaper.bootstrap;

public class App {

    public static void main(String[] args) {
        // ... startup validation, config loading ...

        // Initialize services
        TradeRepository tradeRepository = new PostgresTradeRepository(dataSource);
        // ...

        // ✅ CREATE AND START RECONCILER (P0-C)
        PendingOrderReconciler reconciler = new PendingOrderReconciler(
            tradeRepository,
            userBrokerRepository,
            brokerFactory,
            eventService,
            Duration.ofSeconds(30),  // Reconcile every 30 seconds
            Duration.ofMinutes(10)   // Timeout pending orders after 10 minutes
        );
        reconciler.start();

        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down...");
            reconciler.stop();
            // ... other cleanup ...
        }));

        // Start application
        startUndertow(config);
    }
}
```

#### Acceptance Proof

**Test 1: Restart heals pending orders**
```bash
# Setup: Create pending order in DB
INSERT INTO trades (trade_id, intent_id, client_order_id, broker_order_id, status, ...)
VALUES (uuid_generate_v4(), 'intent-123', 'intent-123', 'broker-789', 'PENDING', ...);

# Kill app mid-pending (simulate crash)
kill -9 <pid>

# Restart app
./start-backend.sh

# Wait 30-60 seconds for reconciliation

# Expected: Trade status updated to FILLED/REJECTED based on broker reality
SELECT status FROM trades WHERE intent_id = 'intent-123';
-- Status should be FILLED (if broker filled) or REJECTED (if broker rejected)
```

**Test 2: Metrics increment**
```bash
# Check reconciler metrics
curl http://localhost:9091/api/admin/reconcile-metrics

# Expected response:
{
  "lastChecked": 5,
  "lastRunTime": "2026-01-13T10:30:00Z",
  "totalChecked": 127,
  "totalUpdated": 18
}
```

**Test 3: Reconciler logs visible**
```bash
tail -f backend.log | grep "PendingOrderReconciler"

# Expected logs:
# Pending order reconciler started: interval=30s, timeout=600s
# Reconciling 5 pending orders...
# Reconciled trade abc-123: PENDING → FILLED
# Reconciliation complete: checked=5, updated=2, elapsed=234ms
```

#### Checklist
- [ ] Created `PendingOrderReconciler.java`
- [ ] Added `getOrderStatus()` to BrokerAdapter
- [ ] Implemented in FyersAdapter (and other adapters)
- [ ] Registered reconciler in App.main()
- [ ] Test 1 passed (restart heals pending)
- [ ] Test 2 passed (metrics increment)
- [ ] Test 3 passed (logs visible)
- [ ] Documented in ADDENDUM.md Section 8.4.1

---

### P0-D: TICK DEDUPE (IF EXITS DEPEND ON CANDLES)
**Priority:** 🔴 P0 - Day 4 (or P1 if exits don't use candles)
**Effort:** 4-6 hours
**Dependencies:** None

#### Deliverables

**1. Add tick deduplication to `TickCandleBuilder`**

```java
package in.annupaper.service.candle;

import in.annupaper.broker.BrokerAdapter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TickCandleBuilder {
    private static final Logger log = LoggerFactory.getLogger(TickCandleBuilder.class);

    // Dedupe cache (bounded memory window)
    private final Set<TickKey> recentTicks = ConcurrentHashMap.newKeySet();
    private Instant lastCleanup = Instant.now();
    private static final Duration DEDUPE_WINDOW = Duration.ofSeconds(60);  // Keep last 60 seconds
    private static final Duration CLEANUP_INTERVAL = Duration.ofSeconds(10);  // Cleanup every 10s

    // Metrics
    private long totalTicks = 0;
    private long duplicateTicks = 0;

    /**
     * Tick key for deduplication.
     * Uses broker's exchange timestamp (source of truth) + price + qty.
     */
    record TickKey(
        String symbol,
        Instant exchangeTimestamp,  // From broker (not local receive time)
        BigDecimal lastPrice,
        BigDecimal lastQty
    ) {}

    @Override
    public void onTick(BrokerAdapter.Tick tick) {
        totalTicks++;

        // ✅ DEDUPE CHECK (P0-D)
        if (isDuplicate(tick)) {
            duplicateTicks++;
            log.debug("Duplicate tick ignored: {} @ {} ({})",
                tick.symbol(), tick.lastPrice(), tick.exchangeTimestamp());
            // Metrics.counter("ticks.duplicate.ignored").inc();
            return;
        }

        // Process tick (as before)
        updateTick(tick);
        update1MinuteCandle(tick);
        // ...
    }

    /**
     * Check if tick is duplicate.
     *
     * @param tick Tick to check
     * @return true if duplicate (should ignore), false if new
     */
    private boolean isDuplicate(BrokerAdapter.Tick tick) {
        TickKey key = new TickKey(
            tick.symbol(),
            tick.exchangeTimestamp(),  // Use broker's timestamp (not local)
            tick.lastPrice(),
            tick.lastQty()
        );

        // Cleanup old keys periodically
        if (shouldCleanup()) {
            cleanupOldTicks();
        }

        // Check if already seen
        if (recentTicks.contains(key)) {
            return true;  // Duplicate
        }

        // Add to cache
        recentTicks.add(key);
        return false;  // New tick
    }

    /**
     * Should run cleanup? (every 10 seconds)
     */
    private boolean shouldCleanup() {
        return Duration.between(lastCleanup, Instant.now()).compareTo(CLEANUP_INTERVAL) > 0;
    }

    /**
     * Cleanup ticks older than dedupe window (60 seconds).
     * Prevents unbounded memory growth.
     */
    private void cleanupOldTicks() {
        Instant cutoff = Instant.now().minus(DEDUPE_WINDOW);
        int sizeBefore = recentTicks.size();

        recentTicks.removeIf(key -> key.exchangeTimestamp().isBefore(cutoff));

        int sizeAfter = recentTicks.size();
        int removed = sizeBefore - sizeAfter;

        log.debug("Tick dedupe cleanup: removed {} old keys, {} remaining", removed, sizeAfter);
        lastCleanup = Instant.now();
    }

    /**
     * Get dedupe metrics (for observability).
     */
    public DedupeMetrics getDedupeMetrics() {
        return new DedupeMetrics(
            totalTicks,
            duplicateTicks,
            recentTicks.size()
        );
    }

    public record DedupeMetrics(
        long totalTicks,
        long duplicateTicks,
        int cacheSize
    ) {
        public double duplicateRate() {
            return totalTicks > 0 ? (double) duplicateTicks / totalTicks : 0.0;
        }
    }
}
```

**2. Add exchange timestamp to Tick model**

```java
package in.annupaper.broker;

public interface BrokerAdapter {

    record Tick(
        String symbol,
        BigDecimal lastPrice,
        BigDecimal lastQty,
        Instant exchangeTimestamp,  // ✅ ADD: Broker's timestamp (source of truth)
        Instant receivedAt,         // Local receive time (for latency measurement)
        BigDecimal bidPrice,
        BigDecimal askPrice,
        BigDecimal volume
    ) {}
}
```

**3. Update broker adapters to provide exchange timestamp**

```java
package in.annupaper.broker.adapters;

public class FyersAdapter implements BrokerAdapter {

    private void processTickMessage(String message) {
        // Parse broker message
        JsonNode json = objectMapper.readTree(message);

        // Extract fields
        String symbol = json.get("symbol").asText();
        BigDecimal lastPrice = new BigDecimal(json.get("ltp").asText());
        BigDecimal lastQty = new BigDecimal(json.get("ltq").asText());

        // ✅ Extract exchange timestamp (if broker provides)
        long exchangeTimestampMs = json.get("exchange_ts").asLong();  // Fyers provides this
        Instant exchangeTimestamp = Instant.ofEpochMilli(exchangeTimestampMs);

        Instant receivedAt = Instant.now();

        // Create tick
        Tick tick = new Tick(
            symbol,
            lastPrice,
            lastQty,
            exchangeTimestamp,  // ✅ Use broker's timestamp
            receivedAt,
            // ...
        );

        // Emit to listeners
        tickCandleBuilder.onTick(tick);
    }
}
```

#### Acceptance Proof

**Test 1: Duplicate ticks ignored (volume correct)**
```java
// Simulate broker reconnect (replays last 10 ticks)
for (int i = 0; i < 10; i++) {
    Tick tick = new Tick("RELIANCE", price, qty, exchangeTs, receivedAt, ...);
    tickCandleBuilder.onTick(tick);
}

// Expected: Only first occurrence processed
DedupeMetrics metrics = tickCandleBuilder.getDedupeMetrics();
assertEquals(10, metrics.totalTicks());
assertEquals(9, metrics.duplicateTicks());  // 9 duplicates ignored

// Check candle volume
Candle candle = candleStore.getLatest("RELIANCE", TimeframeType.MINUTE_1);
assertEquals(qty, candle.volume());  // Volume not double-counted
```

**Test 2: Metrics visible**
```bash
curl http://localhost:9091/api/admin/tick-dedupe-metrics

# Expected response:
{
  "totalTicks": 125437,
  "duplicateTicks": 234,
  "cacheSize": 1523,
  "duplicateRate": 0.0019
}
```

**Test 3: Cleanup prevents memory leak**
```java
// Simulate high-frequency ticks for 5 minutes
for (int i = 0; i < 300000; i++) {  // 1000 ticks/sec × 300 sec
    Tick tick = generateTick(i);
    tickCandleBuilder.onTick(tick);
}

// Expected: Cache size stays bounded (not 300K)
DedupeMetrics metrics = tickCandleBuilder.getDedupeMetrics();
assertTrue(metrics.cacheSize() < 10000);  // Cleanup working
```

#### Checklist
- [ ] Added tick deduplication to TickCandleBuilder
- [ ] Added exchangeTimestamp to Tick model
- [ ] Updated broker adapters to provide exchange timestamp
- [ ] Test 1 passed (duplicates ignored, volume correct)
- [ ] Test 2 passed (metrics visible)
- [ ] Test 3 passed (cleanup prevents memory leak)
- [ ] Documented in ADDENDUM.md Section 15.2

---

### P0-E: SINGLE-WRITER TRADE STATE ENFORCEMENT
**Priority:** 🔴 P0 - Day 3
**Effort:** 3-4 hours
**Dependencies:** P0-B (Trade upsert)

#### Deliverables

**1. Create `TradeService` (state transition orchestrator)**

```java
package in.annupaper.service.execution;

import in.annupaper.domain.model.Trade;
import in.annupaper.domain.enums.TradeStatus;
import in.annupaper.repository.TradeRepository;
import in.annupaper.service.core.EventService;

/**
 * Single entry point for all trade state transitions.
 *
 * Purpose: Enforce single-writer pattern (V2 #9).
 * All trade status changes MUST go through this service.
 *
 * FORBIDDEN: Direct SQL updates on trades table outside TradeRepository.
 * FORBIDDEN: trade.setStatus() without persisting via this service.
 */
public class TradeService {
    private static final Logger log = LoggerFactory.getLogger(TradeService.class);

    private final TradeRepository tradeRepository;
    private final EventService eventService;

    public TradeService(TradeRepository tradeRepository, EventService eventService) {
        this.tradeRepository = tradeRepository;
        this.eventService = eventService;
    }

    /**
     * Create pending trade (after order placed).
     *
     * @param intent Trade intent (approved)
     * @param brokerOrderId Broker's order ID
     * @return Trade with status = PENDING
     */
    public Trade createPending(TradeIntent intent, String brokerOrderId) {
        Trade trade = Trade.createPending(intent, brokerOrderId);
        Trade persisted = tradeRepository.upsert(trade);

        log.info("Trade created: {} (status=PENDING, intentId={})",
            persisted.tradeId(), intent.intentId());

        eventService.emitUserBroker(EventType.TRADE_CREATED, persisted, intent.userBrokerId());

        return persisted;
    }

    /**
     * Mark trade as filled (on broker fill callback).
     *
     * @param tradeId Trade ID
     * @param filledQty Filled quantity
     * @param avgPrice Average fill price
     * @return Updated trade with status = FILLED
     */
    public Trade markFilled(String tradeId, BigDecimal filledQty, BigDecimal avgPrice) {
        Trade trade = tradeRepository.findById(tradeId);
        if (trade == null) {
            throw new IllegalStateException("Trade not found: " + tradeId);
        }

        if (trade.status() != TradeStatus.PENDING) {
            log.warn("Trade {} not in PENDING state: {}", tradeId, trade.status());
            return trade;  // Idempotent
        }

        Trade filled = trade.markFilled(filledQty, avgPrice);
        Trade persisted = tradeRepository.upsert(filled);

        log.info("Trade filled: {} (qty={}, price={})", tradeId, filledQty, avgPrice);

        eventService.emitUserBroker(EventType.TRADE_FILLED, persisted, trade.userBrokerId());

        return persisted;
    }

    /**
     * Mark trade as open (after filled, before monitoring for exit).
     *
     * @param tradeId Trade ID
     * @return Updated trade with status = OPEN
     */
    public Trade markOpen(String tradeId) {
        Trade trade = tradeRepository.findById(tradeId);
        if (trade == null) {
            throw new IllegalStateException("Trade not found: " + tradeId);
        }

        if (trade.status() != TradeStatus.FILLED) {
            log.warn("Trade {} not in FILLED state: {}", tradeId, trade.status());
            return trade;  // Idempotent
        }

        Trade open = trade.markOpen();
        Trade persisted = tradeRepository.upsert(open);

        log.info("Trade opened: {}", tradeId);

        eventService.emitUserBroker(EventType.TRADE_OPENED, persisted, trade.userBrokerId());

        return persisted;
    }

    /**
     * Mark trade as closed (on exit signal).
     *
     * @param tradeId Trade ID
     * @param exitReason Exit reason (TARGET_HIT, STOP_LOSS, TIME_BASED)
     * @param exitPrice Exit price
     * @return Updated trade with status = CLOSED
     */
    public Trade markClosed(String tradeId, ExitReason exitReason, BigDecimal exitPrice) {
        Trade trade = tradeRepository.findById(tradeId);
        if (trade == null) {
            throw new IllegalStateException("Trade not found: " + tradeId);
        }

        if (trade.status() != TradeStatus.OPEN) {
            log.warn("Trade {} not in OPEN state: {}", tradeId, trade.status());
            return trade;  // Idempotent
        }

        Trade closed = trade.markClosed(exitReason, exitPrice);
        Trade persisted = tradeRepository.upsert(closed);

        log.info("Trade closed: {} (reason={}, price={})", tradeId, exitReason, exitPrice);

        eventService.emitUserBroker(EventType.TRADE_CLOSED, persisted, trade.userBrokerId());

        return persisted;
    }

    /**
     * Mark trade as rejected (on broker rejection).
     *
     * @param tradeId Trade ID
     * @param rejectReason Rejection reason
     * @return Updated trade with status = REJECTED
     */
    public Trade markRejected(String tradeId, String rejectReason) {
        Trade trade = tradeRepository.findById(tradeId);
        if (trade == null) {
            throw new IllegalStateException("Trade not found: " + tradeId);
        }

        if (trade.status() != TradeStatus.PENDING) {
            log.warn("Trade {} not in PENDING state: {}", tradeId, trade.status());
            return trade;  // Idempotent
        }

        Trade rejected = trade.markRejected(rejectReason);
        Trade persisted = tradeRepository.upsert(rejected);

        log.warn("Trade rejected: {} (reason={})", tradeId, rejectReason);

        eventService.emitUserBroker(EventType.TRADE_REJECTED, persisted, trade.userBrokerId());

        return persisted;
    }
}
```

**2. Update `ExecutionOrchestrator` to use `TradeService`**

```java
package in.annupaper.service.execution;

public class ExecutionOrchestrator {

    private final TradeService tradeService;  // ✅ Use TradeService (single writer)

    private void executeIntent(TradeIntent intent) {
        // ✅ CORRECT: Use TradeService for state transitions
        BrokerAdapter broker = brokerFactory.getAdapter(intent.userBrokerId());

        OrderResponse response = broker.placeOrder(
            intent.symbol(),
            intent.approvedQty(),
            intent.signal().entryPrice(),
            intent.intentId()  // clientOrderId (idempotency key)
        );

        if (response.status().equals("PLACED")) {
            // Create trade with PENDING status
            Trade trade = tradeService.createPending(intent, response.orderId());
            log.info("Order placed: {}", trade.tradeId());
        } else {
            // Mark as rejected
            tradeService.markRejected(intent.intentId(), response.rejectReason());
            log.warn("Order rejected: {}", response.rejectReason());
        }
    }
}
```

**3. Update `ExitSignalService` to use `TradeService`**

```java
package in.annupaper.service.signal;

public class ExitSignalService {

    private final TradeService tradeService;  // ✅ Use TradeService (single writer)

    private void emitExitSignal(Trade trade, ExitReason reason, BigDecimal exitPrice) {
        // ✅ CORRECT: Use TradeService for state transitions
        Trade closed = tradeService.markClosed(trade.tradeId(), reason, exitPrice);

        log.info("Exit signal emitted: {} (reason={}, price={})",
            trade.symbol(), reason, exitPrice);
    }
}
```

**4. Add CI check (optional, but recommended)**

```groovy
// build.gradle
task enforceTradeRepositoryOwnership {
    doLast {
        // Check: No direct SQL updates on trades table outside repository package
        def violations = fileTree('src/main/java').matching {
            include '**/*.java'
            exclude '**/repository/**'
        }.findAll { file ->
            file.text.contains('UPDATE trades') ||
            file.text.contains('update trades') ||
            file.text.contains('.setStatus(')
        }

        if (!violations.isEmpty()) {
            throw new GradleException(
                "❌ FORBIDDEN: Direct trade state updates outside TradeRepository\n" +
                "Files: ${violations*.path}\n" +
                "Use TradeService for all state transitions."
            )
        }

        println "✅ Trade state machine ownership verified"
    }
}

check.dependsOn enforceTradeRepositoryOwnership
```

#### Acceptance Proof

**Test 1: All transitions go through TradeService**
```bash
# Grep for direct trade updates
grep -r "UPDATE trades" src/main/java --exclude-dir=repository
# Expected: No matches (all updates in repository)

grep -r ".setStatus(" src/main/java --exclude-dir=repository
# Expected: No matches (all transitions via TradeService)
```

**Test 2: CI check fails if violated**
```bash
# Add forbidden code
echo "jdbcTemplate.update(\"UPDATE trades SET status = 'FILLED' WHERE id = ?\");" >> src/main/java/in/annupaper/service/Test.java

# Run CI
./gradlew check

# Expected:
# ❌ FORBIDDEN: Direct trade state updates outside TradeRepository
# Files: src/main/java/in/annupaper/service/Test.java
# Use TradeService for all state transitions.
```

**Test 3: State transitions work**
```java
// Create pending
Trade trade = tradeService.createPending(intent, "broker-123");
assertEquals(TradeStatus.PENDING, trade.status());

// Mark filled
Trade filled = tradeService.markFilled(trade.tradeId(), qty, price);
assertEquals(TradeStatus.FILLED, filled.status());

// Mark open
Trade open = tradeService.markOpen(trade.tradeId());
assertEquals(TradeStatus.OPEN, open.status());

// Mark closed
Trade closed = tradeService.markClosed(trade.tradeId(), ExitReason.TARGET_HIT, exitPrice);
assertEquals(TradeStatus.CLOSED, closed.status());
```

#### Checklist
- [ ] Created `TradeService` with state transition methods
- [ ] Updated ExecutionOrchestrator to use TradeService
- [ ] Updated ExitSignalService to use TradeService
- [ ] Added CI check (optional)
- [ ] Test 1 passed (no direct updates outside repository)
- [ ] Test 2 passed (CI check enforces)
- [ ] Test 3 passed (transitions work)
- [ ] Documented in ADDENDUM.md Section 8.4.3

---

## PROGRESS TRACKING

**P0 Status (Critical - Must Complete):**

| Task | Priority | Effort | Status | Completion Date |
|------|----------|--------|--------|-----------------|
| P0-A: Startup Validation Gate | 🔴 P0 | 2h | ☐ TODO | ___ |
| P0-B: DB Uniqueness + Upsert | 🔴 P0 | 2h | ☐ TODO | ___ |
| P0-C: Broker Reconciliation Loop | 🔴 P0 | 4-6h | ☐ TODO | ___ |
| P0-D: Tick Dedupe | 🔴 P0 | 4-6h | ☐ TODO | ___ |
| P0-E: Single-Writer Transitions | 🔴 P0 | 3-4h | ☐ TODO | ___ |

**Total P0:** 15-20 hours

---

## WHAT YOU HAVE AT THE END

**Non-Negotiable Proof Outputs:**

1. ✅ **Startup that refuses to lie**
   - production.mode=true + order.execution=false → Fails with clear error
   - Test APIs in production → Fails with clear error
   - Invalid configs → Fails with clear error

2. ✅ **DB that prevents duplicates by construction**
   - trades.intent_id UNIQUE → Cannot create duplicate trades
   - signals dedupe index → Cannot create duplicate signals
   - Retry-safe upsert → Idempotent inserts

3. ✅ **Reconciler that heals reality**
   - Restart → Pending orders reconciled automatically
   - Missed fill callbacks → Reconciler updates from broker
   - Metrics visible → reconcile.pending.checked, reconcile.pending.updated

4. ✅ **Metrics/logs that prove engines actually ran**
   - (P1) ticks.processed, candles.closed, signals.generated
   - (P1) Error codes: ERR_CANDLE_PERSIST_FAIL, ERR_ORDER_PLACEMENT_FAIL

5. ✅ **DEGRADE paths that cannot be silent**
   - (P1) Every DEGRADE has metric + log + watchdog alert

6. ✅ **Validation checklist that cannot drift**
   - (P1) ValidationService.validate() is canonical source
   - (P1) Docs reference code, not vice versa

7. ✅ **Trade state machine that cannot scatter**
   - TradeService is single writer
   - CI check enforces (no direct SQL outside repository)

---

## INTEGRATION INTO FRAMEWORK

After completing P0 tasks:

1. **Update COMPLETENESS_VERIFICATION_ADDENDUM.md:**
   - Mark P0-A, P0-B, P0-C, P0-D, P0-E as ✅ COMPLETE
   - Update reachability proofs with actual metrics
   - Update reconciliation table with actual jobs

2. **Update SYSTEM_CLASS_METHOD_MASTER.md:**
   - Add StartupConfigValidator to class index
   - Add PendingOrderReconciler to class index
   - Add TradeService to class index
   - Update state ownership matrix

3. **Update VERIFICATION_WALKTHROUGH_CHECKLIST.md:**
   - Add checks for startup validation
   - Add checks for reconciliation
   - Add checks for tick dedupe
   - Add checks for single-writer pattern

4. **Tag Version:**
   ```bash
   git tag -a v0.4.1 -m "V2 Framework P0 Enforcement Complete"
   git push origin v0.4.1
   ```

---

**Document Version:** 1.0 (P0 Worklist)
**Date:** January 13, 2026
**Status:** ✅ Ready for execution
**Next Step:** Start with P0-A (Startup Validation Gate) - 2 hours
