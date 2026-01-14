# COMPREHENSIVE_IMPLEMENTATION_PLAN.md
# AnnuPaper v04 — From Framework to Production (A+ Enforcement‑Grade)

**Date:** January 13, 2026  
**Purpose:** Phased execution plan with technical corrections applied  
**Grade Target:** **A+ Ungameable Enforcement** (no “paper compliance” without real invariants)

---

## Executive Summary

This plan upgrades AnnuPaper v04 from **documentation-first verification** to **mechanical enforcement** and then to **production-ready execution**.

### Key Corrections Included (from your draft)

- **P0‑A**: `PROD_READY` is a **hard startup gate** using a **P0 debt registry** (no “warn & continue”).
- **P0‑B**: **PostgreSQL‑correct** uniqueness + dedupe (partial unique indexes, generated column).
- **P0‑C**: Correct reconciliation comparisons (field checks, broker timestamps), plus rate limiting.
- **P0‑D**: High‑performance tick dedupe (two‑window swap), fallback keys when exchange ts missing.
- **P0‑E**: **Always create Trade row first** (CREATED → PENDING…), rejection path fixed, CI hardened.

### Timeline

- **Phase 1 (P0):** 15–20 hours → “Cannot lie / bypass / silently degrade”
- **Phase 2 (Execution core):** 40–60 hours
- **Phase 3 (Observability + locks):** 20–30 hours
- **Phase 4 (Harden + deploy):** 20–30 hours  
**Total:** 6–8 weeks (120–160 hours)

---

## PHASE 1 — Framework Enforcement (Week 1): P0 Gates (15–20h)

### P0‑A — Startup Validation Gate (2h)

#### Deliverable 1: P0 Debt Registry

**File:** `src/main/java/in/annupaper/bootstrap/P0DebtRegistry.java`

> **Note:** This is intentionally static + explicit. You “resolve” gates by changing flags to `true`
> when the corresponding invariant is truly implemented + verified.

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
 * Single source of truth for production readiness.
 */
public final class P0DebtRegistry {

    private P0DebtRegistry() {}

    // ✅ Mechanical fix: boolean flags, not TODO comments
    private static final Map<String, Boolean> P0_GATES = Map.of(
        "ORDER_EXECUTION_IMPLEMENTED", false,
        "POSITION_TRACKING_LIVE", false,
        "BROKER_RECONCILIATION_RUNNING", false,
        "TICK_DEDUPLICATION_ACTIVE", false,
        "SIGNAL_DB_CONSTRAINTS_APPLIED", false,
        "TRADE_IDEMPOTENCY_CONSTRAINTS", false,

        // Conditional gate (example): only required if tick persistence is enabled
        "ASYNC_EVENT_WRITER_IF_PERSIST", true
    );

    public static boolean allGatesResolved() {
        return P0_GATES.values().stream().allMatch(Boolean::booleanValue);
    }

    public static String getUnresolvedGates() {
        String list = P0_GATES.entrySet().stream()
            .filter(e -> !e.getValue())
            .map(Map.Entry::getKey)
            .collect(Collectors.joining("\n  - "));
        return list.isBlank() ? "(none)" : list;
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

    public static BrokerEnvironment fromUrl(String apiUrl) {
        if (apiUrl == null) return PRODUCTION;

        String lower = apiUrl.toLowerCase();

        if (lower.contains("sandbox") || lower.contains("-sandbox.")) {
            return SANDBOX;
        }
        if (lower.contains("uat") || lower.contains("-uat.") ||
            lower.contains("test") || lower.contains("-test.") ||
            lower.contains("-t1.") || lower.contains("staging")) {
            return UAT;
        }
        return PRODUCTION;
    }
}
```

#### Deliverable 3: StartupConfigValidator (Corrected)

**File:** `src/main/java/in/annupaper/bootstrap/StartupConfigValidator.java`

**Pre-req:** ensure these exist:
- `ReleaseReadiness` enum (e.g., `BETA`, `PROD_READY`)
- `Config` exposes: `isProductionMode()`, `isOrderExecutionEnabled()`, `persist.tickEvents`, `asyncEventWriter.enabled`,
  `release.readiness`, and `getBrokers()` returning list of broker configs.

```java
package in.annupaper.bootstrap;

import in.annupaper.domain.enums.BrokerEnvironment;
import in.annupaper.domain.enums.ReleaseReadiness;
import in.annupaper.util.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StartupConfigValidator {

    private static final Logger log = LoggerFactory.getLogger(StartupConfigValidator.class);

    private StartupConfigValidator() {}

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

        // Check 1: Order execution must be enabled in production mode
        if (!config.isOrderExecutionEnabled()) {
            throw new IllegalStateException(
                "❌ INVALID CONFIG: PRODUCTION MODE requires order.execution.enabled=true\n" +
                "System refuses to start.\n" +
                "Either:\n" +
                "  1) Enable order execution: order.execution.enabled=true\n" +
                "  2) Set production.mode=false for paper/testing"
            );
        }

        // Check 2: No UAT/SANDBOX broker endpoints in production mode
        for (Config.BrokerConfig broker : config.getBrokers()) {
            BrokerEnvironment env = BrokerEnvironment.fromUrl(broker.apiUrl);
            if (env != BrokerEnvironment.PRODUCTION) {
                throw new IllegalStateException(
                    "❌ INVALID CONFIG: PRODUCTION MODE forbids non-production broker APIs\n" +
                    "Broker: " + broker.name + "\n" +
                    "URL: " + broker.apiUrl + "\n" +
                    "Environment: " + env + "\n" +
                    "Either:\n" +
                    "  1) Use production API URL\n" +
                    "  2) Set production.mode=false"
                );
            }
        }

        // Check 3: If tick events are persisted, async writer must be enabled
        if (config.persist.tickEvents && !config.asyncEventWriter.enabled) {
            throw new IllegalStateException(
                "❌ INVALID CONFIG: persist.tickEvents=true requires asyncEventWriter.enabled=true\n" +
                "Direct DB writes on broker thread are FORBIDDEN (P0 invariant).\n" +
                "Either:\n" +
                "  1) asyncEventWriter.enabled=true\n" +
                "  2) persist.tickEvents=false"
            );
        }

        // Check 4: PROD_READY is a HARD gate using P0DebtRegistry
        if (config.release.readiness == ReleaseReadiness.PROD_READY) {
            if (!P0DebtRegistry.allGatesResolved()) {
                throw new IllegalStateException(
                    "❌ PROD_READY GATE FAILED: Unresolved P0 blockers\n" +
                    "The following P0 items are not complete:\n  - " +
                    P0DebtRegistry.getUnresolvedGates() + "\n\n" +
                    "Either:\n" +
                    "  1) Implement & verify all P0 blockers, then flip flags in P0DebtRegistry\n" +
                    "  2) Set release.readiness=BETA to bypass the PROD_READY gate"
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
                log.warn("⚠️  Non-production API: {} - {} ({})", broker.name, broker.apiUrl, env);
            }
        }

        log.warn("⚠️  System running in non-production mode - features may be limited");
    }
}
```

#### Deliverable 4: Wire into App startup

```java
// In App.main(), before startApplication():
try {
    StartupConfigValidator.validate(config);
} catch (IllegalStateException e) {
    log.error("❌ STARTUP VALIDATION FAILED", e);
    System.err.println("\n" + e.getMessage() + "\n");
    System.exit(1);
}
```

#### P0‑A Acceptance Proof

```bash
# Test 1: PROD_READY with unresolved P0 gates → FAILS
production.mode=true
release.readiness=PROD_READY
order.execution.enabled=true
./start-backend.sh
# Expected: ❌ PROD_READY GATE FAILED ... exits code 1

# Test 2: BETA mode bypasses PROD_READY gate → STARTS (still validates production.mode rules)
release.readiness=BETA
./start-backend.sh

# Test 3: Test API in production mode → FAILS
production.mode=true
broker.fyers.apiUrl=https://api-t1.fyers.in
./start-backend.sh
# Expected: ❌ forbids non-production broker APIs (Environment: UAT)
```

---

### P0‑B — DB Uniqueness + Upsert (Postgres-correct) (2h)

#### Deliverable: V015 Migration (Corrected ordering + syntax)

**File:** `sql/V015__add_idempotency_constraints.sql`

✅ **Corrections applied vs draft:**
- `client_order_id` column is added **before** its unique constraint
- Partial unique index uses `CREATE UNIQUE INDEX ... WHERE`
- `signal_day` generated column allows stable `ON CONFLICT`
- Adds precision checks to prevent hidden dedupe breaks
- Notes `pgcrypto` for `gen_random_uuid()` (if used)

```sql
-- ============================================================================
-- V015: Idempotency Constraints (P0-B) — PostgreSQL
-- Date: 2026-01-13
-- ============================================================================

-- If you use gen_random_uuid(), ensure:
-- CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =========================
-- TRADES: idempotency keys
-- =========================

-- Ensure column exists before constraints
ALTER TABLE trades
    ADD COLUMN IF NOT EXISTS client_order_id TEXT;

-- 1) One trade per intent (primary idempotency key)
ALTER TABLE trades
    ADD CONSTRAINT uq_trades_intent_id UNIQUE (intent_id);

-- 2) One trade per client order id (broker idempotency; typically same as intentId)
ALTER TABLE trades
    ADD CONSTRAINT uq_trades_client_order_id UNIQUE (client_order_id);

-- 3) Broker order id may be null before acceptance; enforce uniqueness only when present
CREATE UNIQUE INDEX IF NOT EXISTS uq_trades_broker_order_id
    ON trades(broker_order_id)
    WHERE broker_order_id IS NOT NULL;

-- Reconciliation support
CREATE INDEX IF NOT EXISTS idx_trades_pending
    ON trades(status, updated_at)
    WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_trades_open
    ON trades(status, symbol, user_broker_id)
    WHERE status = 'OPEN';

-- Reconciler timestamp (broker reality)
ALTER TABLE trades
    ADD COLUMN IF NOT EXISTS last_broker_update_at TIMESTAMPTZ;

UPDATE trades
SET last_broker_update_at = updated_at
WHERE last_broker_update_at IS NULL;

-- =========================
-- SIGNALS: dedupe keys
-- =========================

-- Normalize to NUMERIC(18,2) to avoid float noise
ALTER TABLE signals
    ALTER COLUMN effective_floor TYPE NUMERIC(18,2) USING ROUND(effective_floor::numeric, 2),
    ALTER COLUMN effective_ceiling TYPE NUMERIC(18,2) USING ROUND(effective_ceiling::numeric, 2),
    ALTER COLUMN entry_low TYPE NUMERIC(18,2) USING ROUND(entry_low::numeric, 2),
    ALTER COLUMN entry_high TYPE NUMERIC(18,2) USING ROUND(entry_high::numeric, 2),
    ALTER COLUMN ref_price TYPE NUMERIC(18,2) USING ROUND(ref_price::numeric, 2);

-- Generated day column for ON CONFLICT / dedupe
ALTER TABLE signals
    ADD COLUMN IF NOT EXISTS signal_day DATE
        GENERATED ALWAYS AS (DATE(generated_at)) STORED;

-- Dedupe index (one signal per day per confluence + price band)
CREATE UNIQUE INDEX IF NOT EXISTS idx_signal_dedupe ON signals (
    symbol,
    confluence_type,
    signal_day,
    effective_floor,
    effective_ceiling
);

-- Precision guards (enforcement)
ALTER TABLE signals
    ADD CONSTRAINT IF NOT EXISTS chk_effective_floor_precision
        CHECK (effective_floor = ROUND(effective_floor, 2)),
    ADD CONSTRAINT IF NOT EXISTS chk_effective_ceiling_precision
        CHECK (effective_ceiling = ROUND(effective_ceiling, 2));

-- =========================
-- Verification queries
-- =========================

-- Constraints
SELECT conname, contype, pg_get_constraintdef(oid)
FROM pg_constraint
WHERE conrelid = 'trades'::regclass
  AND conname LIKE 'uq_trades_%';

-- Indexes
SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename IN ('trades','signals')
  AND indexname IN ('uq_trades_broker_order_id','idx_signal_dedupe');
```

#### Repository Upsert Notes

- Trade upsert should use `ON CONFLICT (intent_id)` (or `client_order_id`) and update broker fields + status.
- Signal upsert should use `ON CONFLICT (symbol, confluence_type, signal_day, effective_floor, effective_ceiling)`.

**Important:** The unique index is an index, not a constraint — Postgres still supports `ON CONFLICT` as long as the target matches a unique index.

---

### P0‑C — Broker Reconciliation Loop (4–6h)

Deliver `PendingOrderReconciler.java` exactly as per your corrected design:
- timeout uses `last_broker_update_at`
- field comparison (not reference)
- rate limiting via semaphore
- per‑cycle metrics exposed

**Implementation rule:** reconciliation must **only** transition trade state via `TradeService` (single-writer).

---

### P0‑D — Tick Deduplication (4–6h)

Deliver `TickCandleBuilder` two‑window dedupe:
- O(1) swaps every 30s
- checks `currentWindow` + `previousWindow`
- fallback to `receivedAt` if exchange ts missing (warn rate-limited)

**Implementation rule:** dedupe should execute **before** any volume/ohlc accumulation.

---

### P0‑E — Single‑Writer Trade State Machine (3–4h)

Deliver:
- `TradeService` (single entry point) — **create trade row first** in `CREATED`
- refactor `ExecutionOrchestrator` to call:
  - `tradeService.createTrade(intent)` → `CREATED`
  - then broker call → `markPending()` or `markRejected()`
- CI gate that fails if:
  - direct SQL updates to `trades` exist outside repository
  - direct `trade.mark*()` transitions exist outside `TradeService`

---

## PHASE 2 — Core Execution (Weeks 2–4): Implement Order Flow (40–60h)

### Week 2: Order placement (12–15h)
- Implement `ExecutionOrchestrator.processApprovedIntent()`
- Implement broker adapters (start with Fyers):
  - `placeOrder()`, `cancelOrder()`, `modifyOrder()`
  - idempotency key = `clientOrderId = intentId`

### Week 3: Position tracking (12–15h)
- Replace in‑memory position maps with DB queries
- Implement broker fill callbacks:
  - WS order updates → find trade by broker order id → `tradeService.markFilled(...)`
- Implement daily position reconciliation vs broker holdings

### Week 4: Exit monitoring (16–20h)
- Implement `ExitSignalService`
- Brick movement + trailing stop services
- Ensure exit flow places broker exit order and closes trade via `TradeService`

---

## PHASE 3 — Polish & Validation (Weeks 5–6): Metrics + Locks (20–30h)

- Prometheus metrics: ticks, candles, signals, orders, errors
- Canonical `ValidationService` (single source of truth) + CI enforcement
- Async event writer (no DB writes on broker thread)
- DEGRADE enforcement (metric + log + alert for every degrade path)

---

## PHASE 4 — Production Hardening (Weeks 7–8): Test + Deploy (20–30h)

- Integration tests (E2E happy + failure paths), target ≥80% coverage where feasible
- Load test: 100 symbols × 10 ticks/sec = 1000 ticks/sec sustained
- Production checklist:
  - all P0 gates flipped to true
  - `production.mode=true`, `release.readiness=PROD_READY`
  - metrics + dashboards + runbook

---

## Quick “Corrected vs Original” Reference

| Item | Original Issue | Correction Applied |
|------|----------------|-------------------|
| P0‑A | PROD_READY as warning | Hard gate with P0 debt registry |
| P0‑A | Broker URL substring check only | Enum detection (PROD/UAT/SANDBOX) |
| P0‑B | `ADD CONSTRAINT ... WHERE` | `CREATE UNIQUE INDEX ... WHERE` |
| P0‑B | `ON CONFLICT (DATE(col))` | Generated `signal_day` column |
| P0‑B | Float precision leaks | NUMERIC(18,2) + CHECK rounding |
| P0‑C | `updated != trade` reference compare | `hasChanged()` field compare |
| P0‑C | Timeout uses `created_at` | uses `last_broker_update_at` |
| P0‑C | No API rate limiting | semaphore maxConcurrentBrokerCalls |
| P0‑D | `removeIf()` O(n) per tick | two‑window O(1) swap |
| P0‑D | missing exchange timestamp | fallback key uses `receivedAt` |
| P0‑E | rejection before trade row | always create `CREATED` row first |
| P0‑E | weak enforcement | CI regex checks for forbidden operations |

---

## Next Step

**Start Phase 1 with P0‑A** (Startup Validation Gate).  
Once P0‑A is in place, you immediately get: **“System refuses to lie”**.
