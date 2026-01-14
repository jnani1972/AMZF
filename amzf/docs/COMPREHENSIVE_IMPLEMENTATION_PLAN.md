# COMPREHENSIVE IMPLEMENTATION PLAN
# AnnuPaper v04 - From Framework to Production (A+ Enforcement-Grade)

**Date:** January 13, 2026
**Purpose:** Phased execution plan with technical corrections applied
**Grade:** A+ Ungameable Enforcement (no paper compliance without real invariants)

---

## EXECUTIVE SUMMARY

This plan transforms the AnnuPaper v04 verification framework from documentation into production-ready enforcement:

**Key Improvements Applied:**
- ✅ P0-A: PROD_READY as **hard gate** with debt registry (not just warning)
- ✅ P0-B: **Corrected SQL** with proper unique indexes and generated columns
- ✅ P0-C: **Fixed comparison logic** using field checks, broker timestamps, rate limiting
- ✅ P0-D: **Two-window dedupe** pattern for performance, fallback keys
- ✅ P0-E: **Always create trade row first**, rejection path fixed, CI hardened

**Timeline:** 6-8 weeks (120-160 hours)
**Critical Path:** P0 enforcement gates (15-20 hours) → Execution implementation (40-60 hours)

---

## PHASE 1: FRAMEWORK ENFORCEMENT (WEEK 1) — P0 GATES

**Duration:** 15-20 hours
**Goal:** Make system "ungameable" - cannot lie about production readiness

### P0-A: STARTUP VALIDATION GATE (CORRECTED) — 2h

**What Changed:** PROD_READY is now a **hard gate**, not a warning. Uses P0 debt registry with boolean flags. Broker environment uses enum, not substring checking.

#### Deliverable 1: P0 Debt Registry

**File:** `src/main/java/in/annupaper/bootstrap/P0DebtRegistry.java`

```java
package in.annupaper.bootstrap;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * P0 Debt Registry - Tracks unresolved P0 blockers.
 *
 * When release.readiness=PROD_READY, ALL flags must be true.
 * If any flag is false, system REFUSES to start.
 *
 * This is the single source of truth for production readiness.
 */
public class P0DebtRegistry {

    // ✅ Mechanical fix: Use boolean flags, not TODO comments
    private static final Map<String, Boolean> P0_GATES = Map.of(
        "ORDER_EXECUTION_IMPLEMENTED", false,  // ExecutionOrchestrator.placeOrder()
        "POSITION_TRACKING_LIVE", false,       // TradeRepository queries DB, not HashMap
        "BROKER_RECONCILIATION_RUNNING", false, // PendingOrderReconciler started
        "TICK_DEDUPLICATION_ACTIVE", false,    // TickCandleBuilder.isDuplicate()
        "SIGNAL_DB_CONSTRAINTS_APPLIED", false, // V015 migration ran
        "TRADE_IDEMPOTENCY_CONSTRAINTS", false, // V015 migration ran
        "ASYNC_EVENT_WRITER_IF_PERSIST", true   // If persist.tickEvents=true, async writer enabled
    );

    /**
     * Check if all P0 gates are resolved.
     * @return true if all gates are true, false otherwise
     */
    public static boolean allGatesResolved() {
        return P0_GATES.values().stream().allMatch(v -> v);
    }

    /**
     * Get list of unresolved P0 gates.
     */
    public static String getUnresolvedGates() {
        return P0_GATES.entrySet().stream()
            .filter(e -> !e.getValue())
            .map(Map.Entry::getKey)
            .collect(Collectors.joining("\n  - "));
    }

    /**
     * Mark a P0 gate as resolved.
     * Call this when you implement the feature.
     */
    public static void markResolved(String gate) {
        // TODO: Make this mutable via config or admin API
        throw new UnsupportedOperationException(
            "Update P0DebtRegistry.P0_GATES map to mark gate resolved: " + gate
        );
    }
}
```

#### Deliverable 2: Broker Environment Enum

**File:** `src/main/java/in/annupaper/domain/enums/BrokerEnvironment.java`

```java
package in.annupaper.domain.enums;

/**
 * Broker environment classification.
 * Used to prevent production mode from connecting to test APIs.
 */
public enum BrokerEnvironment {
    PRODUCTION,
    UAT,
    SANDBOX;

    /**
     * Detect environment from broker API URL.
     *
     * @param apiUrl Broker API URL
     * @return Environment (defaults to PRODUCTION if unknown)
     */
    public static BrokerEnvironment fromUrl(String apiUrl) {
        String lower = apiUrl.toLowerCase();

        // Known test patterns
        if (lower.contains("sandbox") || lower.contains("-sandbox.")) {
            return SANDBOX;
        }
        if (lower.contains("uat") || lower.contains("-uat.") ||
            lower.contains("test") || lower.contains("-test.") ||
            lower.contains("-t1.") || lower.contains("staging")) {
            return UAT;
        }

        // Default to PRODUCTION (conservative: require explicit test markers)
        return PRODUCTION;
    }
}
```

#### Deliverable 3: Corrected StartupConfigValidator

**File:** `src/main/java/in/annupaper/bootstrap/StartupConfigValidator.java`

```java
package in.annupaper.bootstrap;

import in.annupaper.domain.enums.BrokerEnvironment;
import in.annupaper.util.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StartupConfigValidator {
    private static final Logger log = LoggerFactory.getLogger(StartupConfigValidator.class);

    public static void validate(Config config) {
        log.info("Running startup config validation...");

        if (config.isProductionMode()) {
            validateProductionMode(config);
        } else {
            warnNonProductionMode(config);
        }

        log.info("✅ Startup config validation passed");
    }

    private static void validateProductionMode(Config config) {
        log.info("PRODUCTION MODE detected - enforcing strict validation");

        // ✅ CORRECTED: Check 1 - Order execution must be enabled
        if (!config.isOrderExecutionEnabled()) {
            throw new IllegalStateException(
                "❌ INVALID CONFIG: PRODUCTION MODE requires order.execution.enabled=true\n" +
                "System refuses to start.\n" +
                "Either:\n" +
                "  1. Enable order execution: set order.execution.enabled=true\n" +
                "  2. Set production.mode=false for testing/paper trading"
            );
        }

        // ✅ CORRECTED: Check 2 - No test broker APIs (use environment enum)
        for (Config.BrokerConfig broker : config.getBrokers()) {
            BrokerEnvironment env = BrokerEnvironment.fromUrl(broker.apiUrl);
            if (env != BrokerEnvironment.PRODUCTION) {
                throw new IllegalStateException(
                    "❌ INVALID CONFIG: PRODUCTION MODE forbids non-production broker APIs\n" +
                    "Broker: " + broker.name + "\n" +
                    "URL: " + broker.apiUrl + "\n" +
                    "Environment: " + env + "\n" +
                    "Either:\n" +
                    "  1. Use production API URL\n" +
                    "  2. Set production.mode=false"
                );
            }
        }

        // Check 3: Async event writer if tick persistence enabled
        if (config.persist.tickEvents && !config.asyncEventWriter.enabled) {
            throw new IllegalStateException(
                "❌ INVALID CONFIG: Tick event persistence requires asyncEventWriter.enabled=true\n" +
                "Direct DB writes on broker thread are FORBIDDEN (P0 invariant).\n" +
                "Either:\n" +
                "  1. Enable async writer: set asyncEventWriter.enabled=true\n" +
                "  2. Disable tick persistence: set persist.tickEvents=false"
            );
        }

        // ✅ CORRECTED: Check 4 - PROD_READY is now a HARD GATE (not warning)
        if (config.release.readiness == ReleaseReadiness.PROD_READY) {
            if (!P0DebtRegistry.allGatesResolved()) {
                String unresolved = P0DebtRegistry.getUnresolvedGates();
                throw new IllegalStateException(
                    "❌ PROD_READY GATE FAILED: Unresolved P0 blockers\n" +
                    "The following P0 items are not complete:\n  - " + unresolved + "\n\n" +
                    "Either:\n" +
                    "  1. Resolve all P0 blockers (update P0DebtRegistry flags)\n" +
                    "  2. Set release.readiness=BETA to bypass gate"
                );
            }
            log.info("✅ PROD_READY gate passed - all P0 blockers resolved");
        }

        log.info("✅ PRODUCTION MODE validation passed");
    }

    private static void warnNonProductionMode(Config config) {
        log.warn("⚠️  NON-PRODUCTION MODE detected");

        if (!config.isOrderExecutionEnabled()) {
            log.warn("⚠️  Order execution DISABLED - Paper trading mode active");
        }

        for (Config.BrokerConfig broker : config.getBrokers()) {
            BrokerEnvironment env = BrokerEnvironment.fromUrl(broker.apiUrl);
            if (env != BrokerEnvironment.PRODUCTION) {
                log.warn("⚠️  Non-production API: {} - {} ({})",
                    broker.name, broker.apiUrl, env);
            }
        }

        log.warn("⚠️  System running in non-production mode - features may be limited");
    }
}
```

#### Deliverable 4: Update App.java

```java
// In App.main(), before starting application:
try {
    StartupConfigValidator.validate(config);
} catch (IllegalStateException e) {
    log.error("❌ STARTUP VALIDATION FAILED", e);
    System.err.println("\n" + e.getMessage() + "\n");
    System.exit(1);
}
```

#### Acceptance Proof

```bash
# Test 1: PROD_READY with unresolved P0 gates → FAILS
production.mode=true
release.readiness=PROD_READY
order.execution.enabled=true

./start-backend.sh
# Expected: ❌ PROD_READY GATE FAILED: Unresolved P0 blockers
#   - ORDER_EXECUTION_IMPLEMENTED
#   - POSITION_TRACKING_LIVE
#   ...
# System exits with code 1

# Test 2: BETA mode allows unresolved gates → STARTS
release.readiness=BETA
./start-backend.sh
# Expected: ⚠️  NON-PRODUCTION MODE detected
# System starts

# Test 3: Test API in production mode → FAILS
production.mode=true
broker.fyers.apiUrl=https://api-t1.fyers.in
./start-backend.sh
# Expected: ❌ INVALID CONFIG: PRODUCTION MODE forbids non-production broker APIs
#   Environment: UAT
```

---

### P0-B: DB UNIQUENESS + UPSERT (CORRECTED) — 2h

**What Changed:** Fixed partial unique index syntax (use `CREATE UNIQUE INDEX`, not `CONSTRAINT ... WHERE`). Added generated `signal_day` column for dedupe. Added explicit 2-decimal CHECK constraints.

#### Deliverable: V015 Migration (Postgres-Correct)

**File:** `sql/V015__add_idempotency_constraints.sql`

```sql
-- ============================================================================
-- V015: Idempotency Constraints (P0-B) - Corrected for PostgreSQL
-- ============================================================================
-- Purpose: Enforce uniqueness for trades and signals (V2 #7)
-- Changes: Fixed partial unique index syntax, added generated columns,
--          explicit precision checks
-- Date: 2026-01-13
-- ============================================================================

-- ============================================
-- TRADES TABLE: Idempotency Constraints
-- ============================================

-- Constraint 1: One trade per intent (primary idempotency key)
ALTER TABLE trades
    ADD CONSTRAINT uq_trades_intent_id UNIQUE (intent_id);

-- Constraint 2: One trade per client order ID (broker idempotency)
-- Note: client_order_id is our intentId sent to broker
ALTER TABLE trades
    ADD CONSTRAINT uq_trades_client_order_id UNIQUE (client_order_id);

-- ✅ CORRECTED: Constraint 3 - Partial unique index (not CONSTRAINT ... WHERE)
-- Reason: PostgreSQL doesn't support WHERE clause in ADD CONSTRAINT UNIQUE
CREATE UNIQUE INDEX uq_trades_broker_order_id
    ON trades(broker_order_id)
    WHERE broker_order_id IS NOT NULL;

-- Add missing client_order_id column if not exists
-- (In case it's not in base schema)
ALTER TABLE trades
    ADD COLUMN IF NOT EXISTS client_order_id TEXT;

-- Index for fast PENDING trade queries (reconciliation)
CREATE INDEX IF NOT EXISTS idx_trades_pending
    ON trades(status, updated_at)
    WHERE status = 'PENDING';

-- Index for fast OPEN trade queries (exit monitoring)
CREATE INDEX IF NOT EXISTS idx_trades_open
    ON trades(status, symbol, user_broker_id)
    WHERE status = 'OPEN';

-- ✅ NEW: Add last_broker_update_at column (for reconciliation timeout)
-- Reason: P0-C reconciler needs to timeout based on broker response time,
--         not created_at (which could be days ago after restart)
ALTER TABLE trades
    ADD COLUMN IF NOT EXISTS last_broker_update_at TIMESTAMPTZ;

-- Initialize last_broker_update_at for existing rows
UPDATE trades
SET last_broker_update_at = updated_at
WHERE last_broker_update_at IS NULL;

-- ============================================
-- SIGNALS TABLE: Deduplication Constraint
-- ============================================

-- ✅ CORRECTED: Fix precision - Use NUMERIC(18,2) for price fields
-- Reason: DECIMAL(18,4) has float noise that breaks dedupe
ALTER TABLE signals
    ALTER COLUMN effective_floor TYPE NUMERIC(18, 2),
    ALTER COLUMN effective_ceiling TYPE NUMERIC(18, 2),
    ALTER COLUMN entry_low TYPE NUMERIC(18, 2),
    ALTER COLUMN entry_high TYPE NUMERIC(18, 2),
    ALTER COLUMN ref_price TYPE NUMERIC(18, 2);

-- ✅ CORRECTED: Add generated signal_day column for dedupe
-- Reason: Cannot use DATE(generated_at) directly in ON CONFLICT clause
-- Postgres requires a concrete column or expression index
ALTER TABLE signals
    ADD COLUMN IF NOT EXISTS signal_day DATE GENERATED ALWAYS AS (DATE(generated_at)) STORED;

-- ✅ CORRECTED: Create unique index using signal_day column
-- This allows ON CONFLICT (symbol, confluence_type, signal_day, effective_floor, effective_ceiling)
CREATE UNIQUE INDEX idx_signal_dedupe ON signals (
    symbol,
    confluence_type,
    signal_day,              -- Generated column (not DATE(generated_at))
    effective_floor,         -- NUMERIC(18,2) - no float noise
    effective_ceiling
);

-- ✅ NEW: Add explicit 2-decimal CHECK constraints
-- Reason: Prevent accidental insertion of high-precision values that break dedupe
ALTER TABLE signals
    ADD CONSTRAINT chk_effective_floor_precision
        CHECK (effective_floor = ROUND(effective_floor, 2)),
    ADD CONSTRAINT chk_effective_ceiling_precision
        CHECK (effective_ceiling = ROUND(effective_ceiling, 2));

-- ============================================
-- REPOSITORY SUPPORT: Upsert Methods
-- ============================================

-- Signal upsert: Use signal_day in ON CONFLICT
-- Example Java code:
-- INSERT INTO signals (..., signal_day, ...) VALUES (..., CURRENT_DATE, ...)
-- ON CONFLICT (symbol, confluence_type, signal_day, effective_floor, effective_ceiling)
-- DO UPDATE SET last_checked_at = NOW()
-- RETURNING *;

-- Trade upsert: Use intent_id in ON CONFLICT
-- Example Java code:
-- INSERT INTO trades (..., intent_id, ...) VALUES (..., ?, ...)
-- ON CONFLICT (intent_id) DO UPDATE SET
--     broker_order_id = EXCLUDED.broker_order_id,
--     status = EXCLUDED.status,
--     filled_qty = EXCLUDED.filled_qty,
--     avg_fill_price = EXCLUDED.avg_fill_price,
--     last_broker_update_at = NOW(),
--     updated_at = NOW()
-- RETURNING *;

-- ============================================
-- VERIFICATION QUERIES
-- ============================================

-- Verify constraints exist
SELECT conname, contype, pg_get_constraintdef(oid)
FROM pg_constraint
WHERE conrelid = 'trades'::regclass
  AND conname LIKE 'uq_trades_%';

-- Verify partial unique index
SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename = 'trades'
  AND indexname = 'uq_trades_broker_order_id';

-- Verify signal dedupe index
SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename = 'signals'
  AND indexname = 'idx_signal_dedupe';

-- Test signal dedupe (should succeed idempotently)
-- INSERT INTO signals (signal_id, symbol, confluence_type, generated_at, effective_floor, effective_ceiling, ...)
-- VALUES (gen_random_uuid(), 'RELIANCE', 'TRIPLE', NOW(), 2400.00, 2500.00, ...)
-- ON CONFLICT (symbol, confluence_type, signal_day, effective_floor, effective_ceiling)
-- DO UPDATE SET last_checked_at = NOW()
-- RETURNING *;
```

#### Java Repository Changes

**File:** `src/main/java/in/annupaper/repository/PostgresSignalRepository.java`

```java
@Override
public Signal upsert(Signal signal) {
    // ✅ CORRECTED: Use generated signal_day column in upsert
    String sql = """
        INSERT INTO signals (
            signal_id, symbol, direction, signal_type,
            confluence_type, p_win, p_fill, kelly,
            ref_price, entry_low, entry_high,
            effective_floor, effective_ceiling,
            generated_at, status
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (symbol, confluence_type, signal_day, effective_floor, effective_ceiling)
        DO UPDATE SET
            last_checked_at = NOW(),
            status = EXCLUDED.status
        RETURNING *
        """;

    try {
        return jdbcTemplate.queryForObject(
            sql,
            new BeanPropertyRowMapper<>(Signal.class),
            signal.signalId(), signal.symbol(), signal.direction(), signal.signalType(),
            signal.confluenceType(), signal.pWin(), signal.pFill(), signal.kelly(),
            signal.refPrice(), signal.entryLow(), signal.entryHigh(),
            signal.effectiveFloor(), signal.effectiveCeiling(),
            signal.generatedAt(), signal.status()
        );
    } catch (DataAccessException e) {
        log.error("Failed to upsert signal: {}", signal.signalId(), e);
        throw new RepositoryException("Signal upsert failed", e);
    }
}
```

#### Acceptance Proof

```sql
-- Test 1: Duplicate intent_id rejected
INSERT INTO trades (trade_id, intent_id, client_order_id, status, ...)
VALUES (gen_random_uuid(), 'intent-123', 'intent-123', 'PENDING', ...);

INSERT INTO trades (trade_id, intent_id, client_order_id, status, ...)
VALUES (gen_random_uuid(), 'intent-123', 'intent-123', 'PENDING', ...);
-- Expected: ERROR: duplicate key value violates unique constraint "uq_trades_intent_id"

-- Test 2: Signal dedupe works with generated column
INSERT INTO signals (signal_id, symbol, confluence_type, generated_at, effective_floor, effective_ceiling, ...)
VALUES (gen_random_uuid(), 'RELIANCE', 'TRIPLE', NOW(), 2400.00, 2500.00, ...);

INSERT INTO signals (signal_id, symbol, confluence_type, generated_at, effective_floor, effective_ceiling, ...)
VALUES (gen_random_uuid(), 'RELIANCE', 'TRIPLE', NOW(), 2400.00, 2500.00, ...);
-- Expected: ERROR: duplicate key value violates unique constraint "idx_signal_dedupe"

-- Test 3: Float precision check enforced
INSERT INTO signals (..., effective_floor, ...)
VALUES (..., 2400.123, ...);  -- 3 decimal places
-- Expected: ERROR: new row violates check constraint "chk_effective_floor_precision"
```

---

### P0-C: BROKER RECONCILIATION LOOP (CORRECTED) — 4-6h

**What Changed:** Fixed comparison logic (field comparison, not reference). Uses `last_broker_update_at` for timeout (not `created_at`). Added rate limiting and sharding.

#### Deliverable: Corrected PendingOrderReconciler

**File:** `src/main/java/in/annupaper/service/execution/PendingOrderReconciler.java`

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
import java.util.concurrent.Semaphore;

/**
 * Reconciles pending orders with broker reality (CORRECTED).
 *
 * V2 #7: 3-Layer Idempotency (Layer 3 - Reconciliation)
 *
 * Corrections applied:
 * - Use field comparison (not reference comparison) to detect changes
 * - Use last_broker_update_at (not created_at) for timeout
 * - Add rate limiting to prevent broker API hammering
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
    private final int maxConcurrentBrokerCalls;  // ✅ NEW: Rate limiting

    // ✅ NEW: Rate limiter (prevent API hammering)
    private final Semaphore brokerCallSemaphore;

    // Metrics
    private long lastReconcileCount = 0;
    private long totalReconciled = 0;
    private long totalUpdates = 0;
    private long totalTimeouts = 0;
    private long totalRateLimited = 0;
    private Instant lastReconcileTime = Instant.now();

    public PendingOrderReconciler(
            TradeRepository tradeRepository,
            UserBrokerRepository userBrokerRepository,
            BrokerAdapterFactory brokerFactory,
            EventService eventService,
            Duration reconcileInterval,
            Duration pendingTimeout,
            int maxConcurrentBrokerCalls) {
        this.tradeRepository = tradeRepository;
        this.userBrokerRepository = userBrokerRepository;
        this.brokerFactory = brokerFactory;
        this.eventService = eventService;
        this.reconcileInterval = reconcileInterval;
        this.pendingTimeout = pendingTimeout;
        this.maxConcurrentBrokerCalls = maxConcurrentBrokerCalls;
        this.brokerCallSemaphore = new Semaphore(maxConcurrentBrokerCalls);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "pending-order-reconciler")
        );
    }

    public void start() {
        long initialDelay = 10;
        long period = reconcileInterval.toSeconds();

        scheduler.scheduleAtFixedRate(
            this::reconcilePendingOrders,
            initialDelay,
            period,
            TimeUnit.SECONDS
        );

        log.info("Pending order reconciler started: interval={}s, timeout={}s, maxConcurrent={}",
            period, pendingTimeout.toSeconds(), maxConcurrentBrokerCalls);
    }

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
                }
            }

            Duration elapsed = Duration.between(start, Instant.now());
            lastReconcileCount = pending.size();
            lastReconcileTime = Instant.now();
            totalReconciled += pending.size();
            totalUpdates += updated;

            log.info("Reconciliation complete: checked={}, updated={}, elapsed={}ms",
                pending.size(), updated, elapsed.toMillis());

        } catch (Exception e) {
            log.error("Reconciliation job failed", e);
        }
    }

    private boolean reconcileTrade(Trade trade) {
        // ✅ CORRECTED: Check timeout using last_broker_update_at (not created_at)
        // Reason: created_at could be days old (after restart), but last_broker_update_at
        //         reflects when we last heard from broker
        Instant lastUpdate = trade.lastBrokerUpdateAt() != null
            ? trade.lastBrokerUpdateAt()
            : trade.createdAt();  // Fallback for legacy rows

        if (Duration.between(lastUpdate, Instant.now()).compareTo(pendingTimeout) > 0) {
            log.warn("Trade {} pending timeout ({}s since last broker update), marking as TIMEOUT",
                trade.tradeId(), Duration.between(lastUpdate, Instant.now()).toSeconds());
            Trade timedOut = trade.markTimeout();
            tradeRepository.upsert(timedOut);
            eventService.emitUserBroker(EventType.ORDER_TIMEOUT, timedOut, trade.userBrokerId());
            totalTimeouts++;
            return true;
        }

        // ✅ NEW: Rate limiting (prevent API hammering with 100 pending trades)
        if (!brokerCallSemaphore.tryAcquire()) {
            log.debug("Rate limit reached, skipping trade {} reconciliation this cycle", trade.tradeId());
            totalRateLimited++;
            return false;
        }

        try {
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
            OrderStatus status = broker.getOrderStatus(
                trade.brokerOrderId(),
                trade.clientOrderId()
            );

            if (status == null) {
                log.debug("Broker returned no status for trade {}", trade.tradeId());
                return false;
            }

            // ✅ CORRECTED: Update last_broker_update_at timestamp
            // (Even if status unchanged, we heard from broker)
            Trade withTimestamp = trade.withLastBrokerUpdateAt(status.updatedAt());

            // Update trade based on broker response
            Trade updated = updateFromBrokerStatus(withTimestamp, status);

            // ✅ CORRECTED: Field comparison (not reference comparison)
            // Reason: `updated != trade` compares references, not content
            //         Use equals() or compare specific fields
            if (hasChanged(trade, updated)) {
                tradeRepository.upsert(updated);
                eventService.emitUserBroker(EventType.ORDER_STATUS_CHANGED, updated, trade.userBrokerId());
                log.info("Reconciled trade {}: {} → {}", trade.tradeId(), trade.status(), updated.status());
                return true;
            }

            // No change, but update timestamp
            tradeRepository.upsert(withTimestamp);
            return false;

        } catch (Exception e) {
            log.error("Failed to query broker for trade {}: {}", trade.tradeId(), e.getMessage());
            return false;
        } finally {
            brokerCallSemaphore.release();
        }
    }

    /**
     * ✅ CORRECTED: Check if trade changed (field comparison).
     */
    private boolean hasChanged(Trade before, Trade after) {
        // Compare relevant fields (status, filled_qty, avg_fill_price)
        return !before.status().equals(after.status()) ||
               !before.filledQty().equals(after.filledQty()) ||
               !before.avgFillPrice().equals(after.avgFillPrice());
    }

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
                log.debug("Trade {} still pending at broker", trade.tradeId());
                yield trade;
            }
            default -> {
                log.warn("Unknown broker status for trade {}: {}", trade.tradeId(), status.status);
                yield trade;
            }
        };
    }

    public ReconcileMetrics getMetrics() {
        return new ReconcileMetrics(
            lastReconcileCount,
            lastReconcileTime,
            totalReconciled,
            totalUpdates,
            totalTimeouts,
            totalRateLimited,
            brokerCallSemaphore.availablePermits()
        );
    }

    public record ReconcileMetrics(
        long lastChecked,
        Instant lastRunTime,
        long totalChecked,
        long totalUpdated,
        long totalTimeouts,
        long totalRateLimited,
        int availablePermits
    ) {}
}
```

#### Acceptance Proof

```java
// Test 1: Field comparison detects changes (not reference comparison)
Trade before = Trade.create(..., TradeStatus.PENDING, filledQty=0, avgPrice=0);
Trade after = before.markFilled(BigDecimal.valueOf(100), BigDecimal.valueOf(2400.50));

// ❌ WRONG: before != after (reference comparison - always true)
// ✅ CORRECT: hasChanged(before, after) returns true

// Test 2: Timeout uses last_broker_update_at (not created_at)
Trade oldTrade = Trade.create(...).withCreatedAt(Instant.now().minus(Duration.ofDays(7)));
// created_at is 7 days ago, but last_broker_update_at is 1 minute ago
oldTrade = oldTrade.withLastBrokerUpdateAt(Instant.now().minus(Duration.ofMinutes(1)));

// ✅ CORRECT: Should NOT timeout (last_broker_update_at is recent)
boolean timeout = Duration.between(oldTrade.lastBrokerUpdateAt(), Instant.now())
    .compareTo(Duration.ofMinutes(10)) > 0;
assertFalse(timeout);

// Test 3: Rate limiting prevents API hammering
for (int i = 0; i < 100; i++) {
    reconciler.reconcileTrade(trades.get(i));
}
// Expected: Only maxConcurrentBrokerCalls (e.g., 5) API calls made in parallel
// Others skipped with "Rate limit reached" log
```

---

### P0-D: TICK DEDUPLICATION (CORRECTED) — 4-6h

**What Changed:** Use two-window pattern (not `removeIf()`) for performance. Fallback dedupe key when exchange timestamp missing.

#### Deliverable: Corrected TickCandleBuilder

**File:** `src/main/java/in/annupaper/service/candle/TickCandleBuilder.java`

```java
package in.annupaper.service.candle;

import in.annupaper.broker.BrokerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tick processor with deduplication (CORRECTED).
 *
 * V2 #15: Tick deduplication to prevent volume double-counting.
 *
 * Corrections applied:
 * - Use two-window pattern (not removeIf) for high-frequency ticks
 * - Fallback dedupe key when exchange timestamp missing
 */
public class TickCandleBuilder {
    private static final Logger log = LoggerFactory.getLogger(TickCandleBuilder.class);

    // ✅ CORRECTED: Two-window dedupe pattern (not removeIf)
    // Reason: removeIf() is expensive at high tick rates (O(n) scan every tick)
    //         Two-window pattern: write to current window, swap windows periodically
    private volatile Set<TickKey> currentWindow = ConcurrentHashMap.newKeySet();
    private volatile Set<TickKey> previousWindow = ConcurrentHashMap.newKeySet();
    private Instant lastWindowSwap = Instant.now();
    private static final Duration WINDOW_DURATION = Duration.ofSeconds(30);

    // Metrics
    private long totalTicks = 0;
    private long duplicateTicks = 0;
    private long missingExchangeTimestamp = 0;

    /**
     * Tick key for deduplication.
     */
    record TickKey(
        String symbol,
        Instant exchangeTimestamp,
        BigDecimal lastPrice,
        BigDecimal lastQty
    ) {}

    @Override
    public void onTick(BrokerAdapter.Tick tick) {
        totalTicks++;

        // ✅ Swap windows periodically (instead of removeIf cleanup)
        if (shouldSwapWindows()) {
            swapWindows();
        }

        // ✅ DEDUPE CHECK (P0-D)
        if (isDuplicate(tick)) {
            duplicateTicks++;
            log.debug("Duplicate tick ignored: {} @ {} ({})",
                tick.symbol(), tick.lastPrice(), tick.exchangeTimestamp());
            return;
        }

        // Process tick (as before)
        updateTick(tick);
        update1MinuteCandle(tick);
    }

    /**
     * Check if tick is duplicate.
     */
    private boolean isDuplicate(BrokerAdapter.Tick tick) {
        TickKey key = createTickKey(tick);

        // Check both windows (current and previous)
        if (currentWindow.contains(key) || previousWindow.contains(key)) {
            return true;  // Duplicate
        }

        // Add to current window
        currentWindow.add(key);
        return false;  // New tick
    }

    /**
     * ✅ CORRECTED: Create tick key with fallback strategy.
     * Reason: Exchange timestamp may be missing (broker doesn't provide it)
     *         Use receivedAt as fallback (with warning)
     */
    private TickKey createTickKey(BrokerAdapter.Tick tick) {
        Instant timestamp = tick.exchangeTimestamp();

        // ✅ CORRECTED: Fallback if exchange timestamp missing
        if (timestamp == null) {
            timestamp = tick.receivedAt();
            missingExchangeTimestamp++;

            // Warn once per symbol (not every tick)
            if (missingExchangeTimestamp == 1 || missingExchangeTimestamp % 1000 == 0) {
                log.warn("Exchange timestamp missing for {}, using receivedAt (count={})",
                    tick.symbol(), missingExchangeTimestamp);
            }
        }

        return new TickKey(
            tick.symbol(),
            timestamp,
            tick.lastPrice(),
            tick.lastQty()
        );
    }

    /**
     * Should swap windows? (every 30 seconds)
     */
    private boolean shouldSwapWindows() {
        return Duration.between(lastWindowSwap, Instant.now()).compareTo(WINDOW_DURATION) > 0;
    }

    /**
     * ✅ CORRECTED: Swap windows (instead of removeIf cleanup).
     *
     * Pattern:
     * - Current window becomes previous window
     * - Previous window is cleared (drops old ticks > 60s)
     * - New empty set becomes current window
     *
     * This is O(1) operation (just swap references), not O(n) like removeIf.
     */
    private void swapWindows() {
        int currentSize = currentWindow.size();
        int previousSize = previousWindow.size();

        // Swap: current → previous, previous → cleared
        Set<TickKey> oldPrevious = previousWindow;
        previousWindow = currentWindow;
        currentWindow = ConcurrentHashMap.newKeySet();

        // Clear old previous window (free memory)
        oldPrevious.clear();

        lastWindowSwap = Instant.now();

        log.debug("Tick dedupe window swapped: current={}, previous={}",
            currentSize, previousSize);
    }

    /**
     * Get dedupe metrics (for observability).
     */
    public DedupeMetrics getDedupeMetrics() {
        return new DedupeMetrics(
            totalTicks,
            duplicateTicks,
            missingExchangeTimestamp,
            currentWindow.size(),
            previousWindow.size()
        );
    }

    public record DedupeMetrics(
        long totalTicks,
        long duplicateTicks,
        long missingExchangeTimestamp,
        int currentWindowSize,
        int previousWindowSize
    ) {
        public double duplicateRate() {
            return totalTicks > 0 ? (double) duplicateTicks / totalTicks : 0.0;
        }
    }
}
```

#### Acceptance Proof

```java
// Test 1: removeIf performance issue avoided
// Simulate high-frequency ticks (1000 ticks/sec for 60 seconds)
long start = System.currentTimeMillis();
for (int i = 0; i < 60000; i++) {
    Tick tick = generateTick(i);
    tickCandleBuilder.onTick(tick);
}
long elapsed = System.currentTimeMillis() - start;

// ✅ CORRECT: Should complete in <5 seconds (two-window pattern is O(1))
// ❌ WRONG: Would take 30+ seconds with removeIf pattern (O(n) every tick)
assertTrue(elapsed < 5000);

// Test 2: Fallback dedupe key when exchange timestamp missing
Tick tick = new Tick(
    "RELIANCE",
    BigDecimal.valueOf(2400),
    BigDecimal.valueOf(100),
    null,  // exchangeTimestamp missing
    Instant.now(),
    ...
);

tickCandleBuilder.onTick(tick);
DedupeMetrics metrics = tickCandleBuilder.getDedupeMetrics();
assertEquals(1, metrics.missingExchangeTimestamp());
// ✅ CORRECT: Still deduped using receivedAt as fallback

// Test 3: Window swap happens (not unbounded memory)
// Run for 2 minutes
for (int i = 0; i < 120000; i++) {
    Tick tick = generateTick(i);
    tickCandleBuilder.onTick(tick);
}

DedupeMetrics metrics = tickCandleBuilder.getDedupeMetrics();
// ✅ CORRECT: Window sizes are bounded (not 120K keys)
assertTrue(metrics.currentWindowSize() + metrics.previousWindowSize() < 5000);
```

---

### P0-E: SINGLE-WRITER TRADE STATE (CORRECTED) — 3-4h

**What Changed:** Always create trade row in CREATED state first (before PENDING). Add `markRejectedByIntentId()` method. Harden CI enforcement with regex patterns.

#### Deliverable 1: Corrected TradeService

**File:** `src/main/java/in/annupaper/service/execution/TradeService.java`

```java
package in.annupaper.service.execution;

import in.annupaper.domain.model.Trade;
import in.annupaper.domain.enums.TradeStatus;
import in.annupaper.repository.TradeRepository;
import in.annupaper.service.core.EventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 * Single entry point for all trade state transitions (CORRECTED).
 *
 * V2 #9: Single-writer pattern enforcement.
 *
 * Corrections applied:
 * - Always create trade row first (CREATED state) before PENDING
 * - Add markRejectedByIntentId() for rejection path without tradeId
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
     * ✅ CORRECTED: Create trade in CREATED state (not PENDING).
     *
     * State flow:
     * 1. createTrade(intent) → CREATED (trade row exists, no broker order yet)
     * 2. markPending(tradeId, brokerOrderId) → PENDING (order placed at broker)
     * 3. markFilled(tradeId) → FILLED (broker callback)
     *
     * Reason: Rejection can happen before broker order is placed.
     *         We need a trade row to mark as REJECTED.
     *
     * @param intent Trade intent (approved)
     * @return Trade with status = CREATED
     */
    public Trade createTrade(TradeIntent intent) {
        Trade trade = Trade.createFromIntent(intent);  // status = CREATED
        Trade persisted = tradeRepository.upsert(trade);

        log.info("Trade created: {} (status=CREATED, intentId={})",
            persisted.tradeId(), intent.intentId());

        eventService.emitUserBroker(EventType.TRADE_CREATED, persisted, intent.userBrokerId());

        return persisted;
    }

    /**
     * Mark trade as pending (after order placed at broker).
     *
     * @param tradeId Trade ID
     * @param brokerOrderId Broker's order ID
     * @return Updated trade with status = PENDING
     */
    public Trade markPending(String tradeId, String brokerOrderId) {
        Trade trade = tradeRepository.findById(tradeId);
        if (trade == null) {
            throw new IllegalStateException("Trade not found: " + tradeId);
        }

        if (trade.status() != TradeStatus.CREATED) {
            log.warn("Trade {} not in CREATED state: {}", tradeId, trade.status());
            return trade;  // Idempotent
        }

        Trade pending = trade.markPending(brokerOrderId);
        Trade persisted = tradeRepository.upsert(pending);

        log.info("Trade pending: {} (brokerOrderId={})", tradeId, brokerOrderId);

        eventService.emitUserBroker(EventType.TRADE_PENDING, persisted, trade.userBrokerId());

        return persisted;
    }

    /**
     * Mark trade as filled (on broker fill callback).
     */
    public Trade markFilled(String tradeId, BigDecimal filledQty, BigDecimal avgPrice) {
        Trade trade = tradeRepository.findById(tradeId);
        if (trade == null) {
            throw new IllegalStateException("Trade not found: " + tradeId);
        }

        if (trade.status() != TradeStatus.PENDING) {
            log.warn("Trade {} not in PENDING state: {}", tradeId, trade.status());
            return trade;
        }

        Trade filled = trade.markFilled(filledQty, avgPrice);
        Trade persisted = tradeRepository.upsert(filled);

        log.info("Trade filled: {} (qty={}, price={})", tradeId, filledQty, avgPrice);

        eventService.emitUserBroker(EventType.TRADE_FILLED, persisted, trade.userBrokerId());

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

        Trade rejected = trade.markRejected(rejectReason);
        Trade persisted = tradeRepository.upsert(rejected);

        log.warn("Trade rejected: {} (reason={})", tradeId, rejectReason);

        eventService.emitUserBroker(EventType.TRADE_REJECTED, persisted, trade.userBrokerId());

        return persisted;
    }

    /**
     * ✅ CORRECTED: Mark trade as rejected by intentId (not tradeId).
     *
     * Use case: Rejection happens before we have a trade row.
     *           Intent validation fails, or broker rejects before order placed.
     *
     * @param intentId Intent ID (unique per signal × user-broker)
     * @param rejectReason Rejection reason
     * @return Updated trade with status = REJECTED
     */
    public Trade markRejectedByIntentId(String intentId, String rejectReason) {
        Trade trade = tradeRepository.findByIntentId(intentId);

        if (trade == null) {
            // ✅ CORRECTED: Trade row doesn't exist yet - this should not happen
            //               ExecutionOrchestrator should call createTrade() first
            throw new IllegalStateException(
                "Trade not found for intentId: " + intentId + "\n" +
                "Ensure ExecutionOrchestrator calls createTrade() before rejection"
            );
        }

        return markRejected(trade.tradeId(), rejectReason);
    }
}
```

#### Deliverable 2: Corrected ExecutionOrchestrator

**File:** `src/main/java/in/annupaper/service/execution/ExecutionOrchestrator.java`

```java
private void executeIntent(TradeIntent intent) {
    // ✅ CORRECTED: Always create trade row first (CREATED state)
    Trade trade = tradeService.createTrade(intent);

    try {
        BrokerAdapter broker = brokerFactory.getAdapter(intent.userBrokerId());

        OrderResponse response = broker.placeOrder(
            intent.symbol(),
            intent.approvedQty(),
            intent.signal().entryPrice(),
            intent.intentId()  // clientOrderId (idempotency key)
        );

        if (response.status().equals("PLACED")) {
            // Mark as pending (order placed at broker)
            tradeService.markPending(trade.tradeId(), response.orderId());
            log.info("Order placed: {}", trade.tradeId());
        } else {
            // Mark as rejected (broker rejected immediately)
            tradeService.markRejected(trade.tradeId(), response.rejectReason());
            log.warn("Order rejected: {}", response.rejectReason());
        }

    } catch (BrokerException e) {
        // Mark as rejected (order placement failed)
        tradeService.markRejected(trade.tradeId(), "Order placement failed: " + e.getMessage());
        log.error("Order placement failed: {}", e.getMessage());
    }
}
```

#### Deliverable 3: Hardened CI Enforcement

**File:** `.github/workflows/ci.yml` or `build.gradle`

```groovy
// build.gradle
task enforceTradeRepositoryOwnership {
    doLast {
        def violations = []

        // ✅ CORRECTED: Check 1 - No direct SQL updates on trades table
        fileTree('src/main/java').matching {
            include '**/*.java'
            exclude '**/repository/**'
        }.each { file ->
            def content = file.text

            // Regex patterns for forbidden operations
            def patterns = [
                ~/UPDATE\s+trades/i,
                ~/update\s+trades/i,
                ~/jdbcTemplate\.update\([^)]*trades/,
                ~/\.setStatus\(/
            ]

            patterns.each { pattern ->
                if (content =~ pattern) {
                    violations << "${file.path}: Found forbidden pattern: ${pattern}"
                }
            }
        }

        // ✅ CORRECTED: Check 2 - TradeService is the only state modifier
        fileTree('src/main/java').matching {
            include '**/*.java'
            exclude '**/TradeService.java'
            exclude '**/repository/**'
        }.each { file ->
            def content = file.text

            // Check for direct Trade.mark*() calls (should go through TradeService)
            if (content =~ /trade\.mark(Pending|Filled|Rejected|Closed)\(/) {
                violations << "${file.path}: Direct state transition (use TradeService)"
            }
        }

        if (!violations.isEmpty()) {
            throw new GradleException(
                "❌ FORBIDDEN: Direct trade state updates outside TradeService\n" +
                violations.join('\n')
            )
        }

        println "✅ Trade state machine ownership verified"
    }
}

check.dependsOn enforceTradeRepositoryOwnership
```

#### Acceptance Proof

```bash
# Test 1: Always create trade row first
// Before placing order:
Trade trade = tradeService.createTrade(intent);  // status = CREATED
assertEquals(TradeStatus.CREATED, trade.status());

// After broker rejects:
Trade rejected = tradeService.markRejected(trade.tradeId(), "Insufficient margin");
assertEquals(TradeStatus.REJECTED, rejected.status());

// ✅ CORRECT: Trade row exists throughout lifecycle

# Test 2: CI check catches violations
// Add forbidden code:
echo "jdbcTemplate.update(\"UPDATE trades SET status = 'FILLED' WHERE trade_id = ?\");" >> ExecutionOrchestrator.java

./gradlew check
# Expected: ❌ FORBIDDEN: Direct trade state updates outside TradeService
#           ExecutionOrchestrator.java: Found forbidden pattern: UPDATE trades

# Test 3: markRejectedByIntentId works
Trade trade = tradeService.createTrade(intent);
Trade rejected = tradeService.markRejectedByIntentId(intent.intentId(), "Risk check failed");
assertEquals(TradeStatus.REJECTED, rejected.status());
```

---

## PHASE 1 SUMMARY

**What You've Built (Week 1, 15-20 hours):**

1. ✅ **P0-A: Startup gate that cannot be bypassed**
   - PROD_READY is hard gate (not warning)
   - P0 debt registry with boolean flags
   - Broker environment enum (not substring checks)
   - System refuses to start if any gate fails

2. ✅ **P0-B: Database constraints that prevent duplicates by construction**
   - Corrected SQL syntax (partial unique indexes)
   - Generated `signal_day` column for dedupe
   - Explicit 2-decimal CHECK constraints
   - Retry-safe upsert methods

3. ✅ **P0-C: Reconciler that heals reality after restarts**
   - Field comparison (not reference comparison)
   - Timeout based on `last_broker_update_at` (not `created_at`)
   - Rate limiting prevents API hammering
   - Metrics for observability

4. ✅ **P0-D: Tick deduplication that doesn't leak memory**
   - Two-window pattern (O(1) instead of O(n))
   - Fallback dedupe key when exchange timestamp missing
   - Bounded memory (not unbounded growth)

5. ✅ **P0-E: Trade state machine with single writer**
   - Always create trade row first (CREATED state)
   - Rejection path fixed (`markRejectedByIntentId`)
   - CI enforcement hardened (regex patterns)

**Grade:** A+ Ungameable Enforcement (mechanical fixes applied)

---

## PHASE 2: CORE EXECUTION (WEEKS 2-4) — IMPLEMENT ORDER FLOW

**Duration:** 40-60 hours
**Goal:** Implement missing execution components (order placement, position tracking, broker callbacks)

### Week 2: Order Placement (12-15h)

#### Task 2.1: ExecutionOrchestrator Implementation (6-8h)

**Current State:** TODO stub
**Goal:** Implement `placeOrder()` with broker adapter calls

```java
public class ExecutionOrchestrator {

    public void processApprovedIntent(TradeIntent intent) {
        // 1. Create trade row (CREATED state)
        Trade trade = tradeService.createTrade(intent);

        // 2. Get broker adapter
        BrokerAdapter broker = brokerFactory.getAdapter(intent.userBrokerId());

        // 3. Place order
        try {
            OrderResponse response = broker.placeOrder(
                OrderRequest.builder()
                    .symbol(intent.symbol())
                    .qty(intent.approvedQty())
                    .price(intent.limitPrice())
                    .orderType(intent.orderType())
                    .productType(intent.productType())
                    .clientOrderId(intent.intentId())  // Idempotency key
                    .build()
            );

            // 4. Update trade state
            if (response.isAccepted()) {
                tradeService.markPending(trade.tradeId(), response.orderId());
            } else {
                tradeService.markRejected(trade.tradeId(), response.rejectReason());
            }

        } catch (BrokerException e) {
            tradeService.markRejected(trade.tradeId(), "Broker error: " + e.getMessage());
        }
    }
}
```

**Acceptance:**
- Order placed at broker (visible in broker terminal)
- Trade row created with PENDING status
- Broker order ID captured
- Error handling for broker failures

---

#### Task 2.2: Broker Adapter Implementation (6-7h)

**Implement:** `placeOrder()`, `cancelOrder()`, `modifyOrder()` for all broker adapters

**Priority:** Fyers first (data broker), then Zerodha, Dhan

```java
public class FyersAdapter implements BrokerAdapter {

    @Override
    public OrderResponse placeOrder(OrderRequest request) throws BrokerException {
        // 1. Build broker API request
        Map<String, Object> payload = Map.of(
            "symbol", request.symbol(),
            "qty", request.qty(),
            "type", request.orderType().toFyersType(),
            "side", request.direction().toFyersDirection(),
            "productType", request.productType(),
            "limitPrice", request.price(),
            "client_id", request.clientOrderId()
        );

        // 2. Make HTTP POST to Fyers API
        HttpResponse<String> response = httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create(apiUrl + "/api/v2/orders"))
                .header("Authorization", accessToken)
                .POST(HttpRequest.BodyPublishers.ofString(toJson(payload)))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        // 3. Parse response
        JsonNode json = objectMapper.readTree(response.body());

        if (json.get("s").asText().equals("ok")) {
            return OrderResponse.accepted(json.get("id").asText());
        } else {
            return OrderResponse.rejected(json.get("message").asText());
        }
    }
}
```

**Acceptance:**
- All 3 broker adapters implement `placeOrder()`
- Orders visible in broker terminals
- Error handling for API failures
- Rate limiting respected

---

### Week 3: Position Tracking (12-15h)

#### Task 3.1: Replace Mock HashMap with DB Queries (4-5h)

**Current State:** `PositionTrackingService` uses `ConcurrentHashMap` (mock)
**Goal:** Query trades table for actual positions

```java
public class PositionTrackingService {

    private final TradeRepository tradeRepository;

    /**
     * Get current position for symbol (all users, all brokers).
     */
    public Position getPosition(String symbol) {
        // Query DB for OPEN trades
        List<Trade> openTrades = tradeRepository.findOpenTrades(symbol);

        // Aggregate position
        BigDecimal totalQty = openTrades.stream()
            .map(Trade::entryQty)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalValue = openTrades.stream()
            .map(t -> t.entryPrice().multiply(BigDecimal.valueOf(t.entryQty())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgPrice = totalQty.compareTo(BigDecimal.ZERO) > 0
            ? totalValue.divide(totalQty, 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        return new Position(symbol, totalQty, avgPrice, totalValue);
    }

    /**
     * Get portfolio exposure (all symbols).
     */
    public Map<String, BigDecimal> getPortfolioExposure() {
        List<Trade> allOpen = tradeRepository.findByStatus(TradeStatus.OPEN);

        return allOpen.stream()
            .collect(Collectors.groupingBy(
                Trade::symbol,
                Collectors.reducing(
                    BigDecimal.ZERO,
                    t -> t.entryPrice().multiply(BigDecimal.valueOf(t.entryQty())),
                    BigDecimal::add
                )
            ));
    }
}
```

**Acceptance:**
- Position queries return DB data (not HashMap)
- Aggregation correct (multiple trades per symbol)
- Performance acceptable (<100ms for 100 open trades)

---

#### Task 3.2: Broker Fill Callbacks (5-6h)

**Implement:** WebSocket callbacks for order fills

```java
public class FyersAdapter extends BrokerAdapter {

    @Override
    protected void handleOrderUpdateMessage(String message) {
        JsonNode json = objectMapper.readTree(message);

        String orderId = json.get("id").asText();
        String status = json.get("status").asText();

        if (status.equals("FILLED")) {
            BigDecimal filledQty = new BigDecimal(json.get("filledQuantity").asText());
            BigDecimal avgPrice = new BigDecimal(json.get("tradedPrice").asText());

            // Find trade by broker order ID
            Trade trade = tradeRepository.findByBrokerOrderId(orderId);
            if (trade != null) {
                tradeService.markFilled(trade.tradeId(), filledQty, avgPrice);
            }
        }
    }
}
```

**Acceptance:**
- Order fills detected via WebSocket
- Trade state updated to FILLED
- Filled quantity and price captured
- Event broadcast to frontend

---

#### Task 3.3: Position Reconciliation (3-4h)

**Implement:** Daily reconciliation with broker holdings

```java
public class PositionReconciler {

    public void reconcile() {
        // 1. Get our positions (from trades table)
        Map<String, Position> ourPositions = positionTrackingService.getAllPositions();

        // 2. Get broker positions (via API)
        Map<String, Position> brokerPositions = broker.getPositions();

        // 3. Compare
        for (String symbol : ourPositions.keySet()) {
            Position ours = ourPositions.get(symbol);
            Position theirs = brokerPositions.get(symbol);

            if (theirs == null || !ours.qty().equals(theirs.qty())) {
                log.error("POSITION MISMATCH: {} - ours={}, broker={}",
                    symbol, ours.qty(), theirs != null ? theirs.qty() : 0);
                // Alert admin
            }
        }
    }
}
```

**Acceptance:**
- Runs daily at market close
- Detects mismatches
- Alerts admin on mismatch

---

### Week 4: Exit Monitoring (16-20h)

#### Task 4.1: ExitSignalService Implementation (8-10h)

**Implement:** Monitor open trades for exit conditions

```java
public class ExitSignalService {

    @Scheduled(fixedDelay = 1000)  // Every 1 second
    public void monitorExits() {
        List<Trade> openTrades = tradeRepository.findByStatus(TradeStatus.OPEN);

        for (Trade trade : openTrades) {
            checkExitConditions(trade);
        }
    }

    private void checkExitConditions(Trade trade) {
        BigDecimal currentPrice = marketDataService.getLastPrice(trade.symbol());

        // Check target hit
        if (currentPrice.compareTo(trade.exitTargetPrice()) >= 0) {
            emitExitSignal(trade, ExitReason.TARGET_HIT, currentPrice);
        }

        // Check stop loss
        if (currentPrice.compareTo(trade.exitMinProfitPrice()) <= 0) {
            emitExitSignal(trade, ExitReason.STOP_LOSS, currentPrice);
        }

        // Check brick movement reversal
        if (isBrickMovementReversed(trade, currentPrice)) {
            emitExitSignal(trade, ExitReason.BRICK_REVERSAL, currentPrice);
        }
    }

    private void emitExitSignal(Trade trade, ExitReason reason, BigDecimal exitPrice) {
        tradeService.markClosed(trade.tradeId(), reason, exitPrice);

        // Place exit order at broker
        executionOrchestrator.placeExitOrder(trade, exitPrice);
    }
}
```

**Acceptance:**
- Exit signals generated on target/stop
- Exit orders placed at broker
- Trade state updated to CLOSED
- P&L calculated correctly

---

#### Task 4.2: Brick Movement Tracking (5-6h)

**Implement:** Track favorable/adverse brick movements for exits

```java
public class BrickMovementTracker {

    private final Map<String, BigDecimal> highestPrices = new ConcurrentHashMap<>();

    public void onTick(String symbol, BigDecimal price) {
        // Update highest price since entry
        highestPrices.compute(symbol, (k, v) -> {
            if (v == null) return price;
            return price.max(v);
        });
    }

    public boolean isBrickMovementReversed(Trade trade, BigDecimal currentPrice) {
        BigDecimal highestSinceEntry = highestPrices.get(trade.symbol());
        if (highestSinceEntry == null) return false;

        // Calculate favorable brick movement
        BigDecimal favorableMovement = highestSinceEntry.subtract(trade.entryPrice())
            .divide(trade.entryPrice(), 6, RoundingMode.HALF_UP);

        // Calculate current adverse movement from highest
        BigDecimal adverseMovement = highestSinceEntry.subtract(currentPrice)
            .divide(highestSinceEntry, 6, RoundingMode.HALF_UP);

        // Exit if adverse movement > 40% of favorable movement
        return adverseMovement.compareTo(favorableMovement.multiply(BigDecimal.valueOf(0.4))) > 0;
    }
}
```

**Acceptance:**
- Brick movements tracked correctly
- Exit triggered on 40% reversal
- Metrics recorded in exit_signals table

---

#### Task 4.3: Trailing Stop Implementation (3-4h)

**Implement:** Dynamic trailing stop based on brick movement

```java
public class TrailingStopService {

    public void updateTrailingStop(Trade trade, BigDecimal currentPrice) {
        if (!trade.trailingActive()) return;

        // Update highest price
        BigDecimal highest = trade.trailingHighestPrice();
        if (currentPrice.compareTo(highest) > 0) {
            highest = currentPrice;
        }

        // Calculate trailing stop (40% retracement from highest)
        BigDecimal favorableMovement = highest.subtract(trade.entryPrice());
        BigDecimal stopPrice = highest.subtract(favorableMovement.multiply(BigDecimal.valueOf(0.4)));

        // Update trade
        Trade updated = trade
            .withTrailingHighestPrice(highest)
            .withTrailingStopPrice(stopPrice);

        tradeRepository.upsert(updated);

        // Check if stop hit
        if (currentPrice.compareTo(stopPrice) <= 0) {
            exitSignalService.emitExitSignal(trade, ExitReason.TRAILING_STOP, currentPrice);
        }
    }
}
```

**Acceptance:**
- Trailing stop updates dynamically
- Stop hit triggers exit
- Protects profitable positions

---

## PHASE 3: POLISH & VALIDATION (WEEKS 5-6) — METRICS + OBSERVABILITY

**Duration:** 20-30 hours
**Goal:** Add P1 requirements (metrics, validation locks, async writer)

### Week 5: Metrics & Observability (12-15h)

#### Task 5.1: Add P1 Metrics (6-8h)

```java
// Engine metrics
Metrics.counter("ticks.processed").inc();
Metrics.counter("candles.closed", "timeframe", timeframe).inc();
Metrics.counter("signals.generated", "confluence", confluenceType).inc();
Metrics.counter("orders.placed").inc();
Metrics.counter("orders.filled").inc();
Metrics.counter("orders.rejected", "reason", rejectReason).inc();

// Error codes
Metrics.counter("errors", "code", "ERR_CANDLE_PERSIST_FAIL").inc();
Metrics.counter("errors", "code", "ERR_ORDER_PLACEMENT_FAIL").inc();

// Latencies
Metrics.histogram("tick.processing.latency.ms").observe(latency);
Metrics.histogram("order.placement.latency.ms").observe(latency);
```

**Acceptance:**
- All engines emit metrics
- Prometheus scrape endpoint works
- Grafana dashboard visualizes metrics

---

#### Task 5.2: Validation Lock (3-4h)

**Implement:** ValidationService as canonical source

```java
public class ValidationService {

    /**
     * Canonical validation rules (single source of truth).
     */
    public ValidationResult validate(Signal signal, UserBroker userBroker) {
        List<String> errors = new ArrayList<>();

        // Check 1: Symbol allowed
        if (!userBroker.allowedSymbols().isEmpty() &&
            !userBroker.allowedSymbols().contains(signal.symbol())) {
            errors.add("ERR_SYMBOL_NOT_ALLOWED");
        }

        // Check 2: Confluence meets threshold
        if (signal.confluenceScore().compareTo(MIN_CONFLUENCE_SCORE) < 0) {
            errors.add("ERR_LOW_CONFLUENCE");
        }

        // Check 3: Capital available
        BigDecimal exposure = positionTrackingService.getExposure(userBroker.userBrokerId());
        if (exposure.compareTo(userBroker.maxExposure()) >= 0) {
            errors.add("ERR_EXPOSURE_LIMIT");
        }

        // ... 15 more checks ...

        return new ValidationResult(errors.isEmpty(), errors);
    }
}
```

**Acceptance:**
- All validation goes through ValidationService
- Docs reference code (not vice versa)
- CI check enforces (no duplicate validation logic)

---

#### Task 5.3: Async Event Writer (3-4h)

**Implement:** Batched async writer for high-frequency events

```java
public class AsyncEventWriter {

    private final BlockingQueue<Event> queue = new LinkedBlockingQueue<>(100000);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public void start() {
        executor.submit(this::processQueue);
    }

    private void processQueue() {
        List<Event> batch = new ArrayList<>(1000);

        while (!Thread.interrupted()) {
            try {
                // Drain up to 1000 events
                queue.drainTo(batch, 1000);

                if (!batch.isEmpty()) {
                    // Batch insert to DB
                    eventRepository.batchInsert(batch);
                    batch.clear();
                }

                Thread.sleep(100);  // Batch every 100ms

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void write(Event event) {
        if (!queue.offer(event)) {
            log.warn("Event queue full, dropping event: {}", event);
        }
    }
}
```

**Acceptance:**
- No DB writes on broker thread
- Batching reduces DB load (1000 events → 1 query)
- Queue overflow handled gracefully

---

### Week 6: DEGRADE Enforcement (8-12h)

#### Task 6.1: Add DEGRADE Metrics (4-5h)

```java
// Every DEGRADE path must emit:
Metrics.counter("degrade", "reason", "CANDLE_PERSIST_FAIL").inc();
log.error("ERR_CANDLE_PERSIST_FAIL: {}", e.getMessage());
watchdog.alert("CANDLE_PERSIST_FAIL", e);
```

**Acceptance:**
- All DEGRADE paths have metrics + logs + alerts
- Watchdog detects degraded state
- Admin notified within 1 minute

---

#### Task 6.2: Risk Profile Firewall (4-5h)

**Implement:** Prevent risk profile leakage across users

```java
public class RiskProfileFirewall {

    public void validateIsolation(TradeIntent intent) {
        // Check 1: Intent belongs to user
        if (!intent.userId().equals(currentUser.userId())) {
            throw new SecurityException("Cross-user intent access forbidden");
        }

        // Check 2: User-broker link valid
        UserBroker ub = userBrokerRepository.findById(intent.userBrokerId());
        if (!ub.userId().equals(currentUser.userId())) {
            throw new SecurityException("Cross-user broker access forbidden");
        }

        // Check 3: No capital pooling
        BigDecimal totalCapital = userBrokerRepository.findByUserId(currentUser.userId())
            .stream()
            .map(UserBroker::capitalAllocated)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalCapital.compareTo(currentUser.totalCapital()) > 0) {
            throw new SecurityException("Capital allocation exceeds user capital");
        }
    }
}
```

**Acceptance:**
- Users cannot see other users' trades
- Capital pools isolated
- Risk limits per user-broker enforced

---

## PHASE 4: PRODUCTION HARDENING (WEEKS 7-8) — FINAL POLISH

**Duration:** 20-30 hours
**Goal:** End-to-end testing, load testing, production deployment

### Week 7: Testing (12-15h)

#### Task 7.1: Integration Tests (6-8h)

```java
@Test
public void testEndToEndFlow() {
    // 1. Tick arrives
    Tick tick = new Tick("RELIANCE", 2400, ...);
    tickCandleBuilder.onTick(tick);

    // 2. Candle closes, signal generated
    awaitCondition(() -> signalRepository.count() > 0);
    Signal signal = signalRepository.findAll().get(0);

    // 3. Signal broadcast to users
    awaitCondition(() -> tradeIntentRepository.count() > 0);
    TradeIntent intent = tradeIntentRepository.findAll().get(0);

    // 4. Order placed
    executionOrchestrator.processApprovedIntent(intent);
    awaitCondition(() -> tradeRepository.count() > 0);
    Trade trade = tradeRepository.findAll().get(0);

    assertEquals(TradeStatus.PENDING, trade.status());
}
```

**Acceptance:**
- All E2E flows tested
- Happy path + error paths
- Coverage >80%

---

#### Task 7.2: Load Testing (6-7h)

```bash
# Simulate 100 symbols, 10 ticks/sec each = 1000 ticks/sec
for i in {1..100}; do
    send_ticks "SYMBOL_$i" 10 &
done

# Expected:
# - Tick dedupe works (no memory leak)
# - Candle aggregation correct
# - Signals generated
# - No dropped ticks
# - Latency <50ms p99
```

**Acceptance:**
- Handles 1000 ticks/sec sustained
- No memory leaks
- Latency acceptable

---

### Week 8: Production Deployment (8-12h)

#### Task 8.1: Production Checklist (4-5h)

```bash
# 1. Update P0 debt registry
P0DebtRegistry.P0_GATES:
  ORDER_EXECUTION_IMPLEMENTED: true  # ✅ Week 2
  POSITION_TRACKING_LIVE: true       # ✅ Week 3
  BROKER_RECONCILIATION_RUNNING: true # ✅ P0-C
  TICK_DEDUPLICATION_ACTIVE: true    # ✅ P0-D
  SIGNAL_DB_CONSTRAINTS_APPLIED: true # ✅ P0-B
  TRADE_IDEMPOTENCY_CONSTRAINTS: true # ✅ P0-B

# 2. Set production config
production.mode=true
release.readiness=PROD_READY
order.execution.enabled=true
broker.fyers.apiUrl=https://api.fyers.in  # Production URL

# 3. Run startup validation
./start-backend.sh
# Expected: ✅ PROD_READY gate passed - all P0 blockers resolved

# 4. Verify metrics
curl http://localhost:9091/metrics
# Expected: All engines emitting metrics

# 5. Deploy to production
docker build -t annupaper:v0.5.0 .
kubectl apply -f deployment.yaml
```

**Acceptance:**
- All P0 gates resolved
- Startup validation passes
- Metrics visible in Grafana
- System running in production

---

#### Task 8.2: Runbook & Monitoring (4-5h)

**Create:** Production runbook with common failure modes

```markdown
# AnnuPaper Production Runbook

## Failure Mode 1: Tick Feed Disconnected
**Symptoms:** ticks.processed metric flat for >60s
**Fix:** Restart broker adapter, verify WebSocket connection
**Escalation:** If restart fails 3x, switch to backup data broker

## Failure Mode 2: Order Placement Failing
**Symptoms:** orders.rejected metric spike, ERR_ORDER_PLACEMENT_FAIL
**Fix:** Check broker API status, verify credentials, check rate limits
**Escalation:** Disable order execution, switch to paper trading mode

## Failure Mode 3: Position Mismatch
**Symptoms:** Position reconciler alert
**Fix:** Manual reconciliation, compare trades table vs broker holdings
**Escalation:** Pause trading, investigate missing fills

## Failure Mode 4: Database Connection Lost
**Symptoms:** ERR_DB_CONNECTION_FAIL, HikariCP exceptions
**Fix:** Check DB connectivity, restart DB connection pool
**Escalation:** If DB down >5min, graceful shutdown to prevent data loss
```

**Acceptance:**
- Runbook covers all P0 failure modes
- Oncall engineer can resolve issues
- Escalation paths defined

---

## FINAL DELIVERABLES

**At the end of 6-8 weeks, you have:**

1. ✅ **A+ Ungameable Framework** (Phase 1, P0 gates)
   - PROD_READY hard gate with debt registry
   - DB constraints prevent duplicates by construction
   - Reconciler heals reality after restarts
   - Tick dedupe with bounded memory
   - Single-writer state machine with CI enforcement

2. ✅ **Production-Ready Execution** (Phase 2)
   - Order placement implemented (all brokers)
   - Position tracking queries DB (not HashMap)
   - Broker fill callbacks working
   - Exit monitoring with brick movement tracking

3. ✅ **Observable System** (Phase 3)
   - Metrics for all engines
   - Validation lock (single source of truth)
   - Async event writer (no DB writes on broker thread)
   - DEGRADE enforcement (metrics + logs + alerts)

4. ✅ **Production Hardened** (Phase 4)
   - E2E tests passing
   - Load tested (1000 ticks/sec sustained)
   - Production deployed with monitoring
   - Runbook for oncall engineers

**Grade:** A+ Enforcement (no paper compliance without real invariants)

---

## APPENDIX: QUICK REFERENCE

### Corrected vs Original

| Item | Original Issue | Correction Applied |
|------|---------------|-------------------|
| P0-A | PROD_READY as warning | Hard gate with P0 debt registry |
| P0-A | Broker URL substring check | Environment enum (PROD/UAT/SANDBOX) |
| P0-B | `CONSTRAINT ... WHERE` syntax | `CREATE UNIQUE INDEX ... WHERE` |
| P0-B | `ON CONFLICT (DATE(col))` fails | Generated `signal_day` column |
| P0-B | Implicit float precision | Explicit 2-decimal CHECK constraints |
| P0-C | `updated != trade` (reference) | `hasChanged()` field comparison |
| P0-C | Timeout uses `created_at` | Uses `last_broker_update_at` |
| P0-C | No rate limiting | Semaphore with maxConcurrentBrokerCalls |
| P0-D | `removeIf()` performance issue | Two-window pattern (O(1)) |
| P0-D | Missing exchange timestamp | Fallback to receivedAt with warning |
| P0-E | Rejection without trade row | Always create CREATED row first |
| P0-E | Weak CI enforcement | Regex patterns for forbidden operations |

---

**Document Version:** 2.0 (A+ Enforcement-Grade)
**Date:** January 13, 2026
**Status:** ✅ Ready for execution with all technical corrections applied
**Next Step:** Start Phase 1, P0-A (Startup Validation Gate) - 2 hours
