# P0-E: Single-Writer Trade State - Testing Guide

## Overview

This document provides comprehensive testing guidance for **P0-E: Single-Writer Trade State**.

**Goal:** Ensure trade rows are always created in CREATED state BEFORE calling broker, enforcing single-writer pattern.

**Implementation Files:**
- `src/main/java/in/annupaper/repository/TradeRepository.java` (lines 120-140)
- `src/main/java/in/annupaper/repository/PostgresTradeRepository.java` (lines 894-939)
- `src/main/java/in/annupaper/service/execution/OrderExecutionService.java` (full file)
- `src/main/java/in/annupaper/bootstrap/P0DebtRegistry.java` (line 22)

---

## Single-Writer Pattern

**Core Principle:** Only ONE path creates trade rows - `OrderExecutionService.executeIntent()`.

**Trade State Flow:**
```
1. CREATED    → Trade row inserted BEFORE broker call (single writer)
   ↓
2a. REJECTED  → Broker rejects immediately (via markRejectedByIntentId)
   OR
2b. PENDING   → Broker accepts, reconciler updates status
   ↓
3. OPEN       → Order filled (via reconciler)
   ↓
4. CLOSED     → Position exited
```

**Enforcement Mechanisms:**
1. ✅ Trade row created with `status=CREATED` BEFORE calling broker
2. ✅ Rejection path uses `UPDATE` (not insert) via `markRejectedByIntentId()`
3. ✅ Reconciler uses `UPDATE` (not insert) to transition CREATED → PENDING → OPEN
4. ✅ No other code path can create trade rows (single writer)

---

## Test Scenarios

### Test 1: Trade Created in CREATED State Before Broker Call

**Objective:** Verify that trade row is inserted with status=CREATED BEFORE broker is called.

**Setup:**
1. Start system with database logging enabled
2. Create mock broker adapter that simulates 5-second delay
3. Submit approved trade intent

**Test Steps:**
1. Call `OrderExecutionService.executeIntent(intent)`
2. Immediately after call (< 1 second), query database:
   ```sql
   SELECT trade_id, status, intent_id, broker_order_id, created_at
   FROM trades
   WHERE intent_id = 'test-intent-123';
   ```
3. Verify trade exists with `status=CREATED` and `broker_order_id=NULL`
4. Wait for broker response (5 seconds)
5. Verify reconciler updates trade to PENDING

**Acceptance Criteria:**
- ✅ Trade row exists in database BEFORE broker responds
- ✅ Trade status is `CREATED` (not PENDING or OPEN)
- ✅ Trade `broker_order_id` is NULL (not yet assigned by broker)
- ✅ Trade `intent_id` matches trade intent
- ✅ Trade `client_order_id` equals `intent_id` (P0-B idempotency)
- ✅ After broker accepts, reconciler updates status to PENDING

**SQL Verification:**
```sql
-- Check trade exists in CREATED state
SELECT status, broker_order_id, client_order_id, created_at
FROM trades
WHERE intent_id = 'test-intent-123'
  AND deleted_at IS NULL;

-- Expected:
-- status: CREATED
-- broker_order_id: NULL
-- client_order_id: test-intent-123
-- created_at: <timestamp>
```

---

### Test 2: Broker Immediate Rejection (markRejectedByIntentId)

**Objective:** Verify that broker rejection marks CREATED trade as REJECTED (not creates new trade).

**Setup:**
1. Start system with mock broker that rejects all orders
2. Mock broker returns: `OrderResult.failure("INSUFFICIENT_FUNDS", "AB123")`

**Test Steps:**
1. Submit approved trade intent
2. Verify trade created in CREATED state
3. Broker rejects order immediately
4. Query database for trade:
   ```sql
   SELECT trade_id, status, exit_trigger
   FROM trades
   WHERE intent_id = 'test-intent-123';
   ```
5. Verify only ONE trade row exists with status=REJECTED

**Acceptance Criteria:**
- ✅ Trade initially created with status=CREATED
- ✅ Broker rejection marks trade as REJECTED (via UPDATE, not INSERT)
- ✅ Only ONE trade row exists for this intent (no duplicates)
- ✅ Trade `exit_trigger` contains error code (`INSUFFICIENT_FUNDS`)
- ✅ Trade `updated_at` timestamp after broker rejection
- ✅ ORDER_REJECTED event emitted

**SQL Verification:**
```sql
-- Verify single trade with REJECTED status
SELECT COUNT(*), status, exit_trigger
FROM trades
WHERE intent_id = 'test-intent-123'
  AND deleted_at IS NULL
GROUP BY status, exit_trigger;

-- Expected:
-- COUNT: 1
-- status: REJECTED
-- exit_trigger: INSUFFICIENT_FUNDS
```

---

### Test 3: Broker Acceptance (Reconciler Updates Trade)

**Objective:** Verify that broker acceptance does NOT create new trade (reconciler updates existing).

**Setup:**
1. Start system with real broker adapter (UAT mode)
2. Ensure reconciler is running

**Test Steps:**
1. Submit approved trade intent
2. Verify trade created in CREATED state (query DB immediately)
3. Broker accepts order (returns `brokerOrderId`)
4. Wait for reconciler to run (30 seconds max)
5. Query database:
   ```sql
   SELECT trade_id, status, broker_order_id
   FROM trades
   WHERE intent_id = 'test-intent-123';
   ```
6. Verify only ONE trade row exists with status=PENDING or OPEN

**Acceptance Criteria:**
- ✅ Trade initially created with status=CREATED
- ✅ Broker returns `brokerOrderId` successfully
- ✅ Reconciler updates trade to PENDING (not creates new trade)
- ✅ Only ONE trade row exists for this intent
- ✅ Trade `broker_order_id` populated with broker's order ID
- ✅ Trade `last_broker_update_at` updated by reconciler
- ✅ When order fills, status updated to OPEN (still single row)

**SQL Verification:**
```sql
-- Verify single trade progressing through states
SELECT trade_id, status, broker_order_id, last_broker_update_at
FROM trades
WHERE intent_id = 'test-intent-123'
  AND deleted_at IS NULL;

-- Expected:
-- Only 1 row with status: PENDING or OPEN
-- broker_order_id: <broker's order ID>
-- last_broker_update_at: <recent timestamp>
```

---

### Test 4: No Trade Creation Without CREATED State First

**Objective:** Verify that no code path creates trades directly in PENDING/OPEN state (single writer).

**Setup:**
1. Review all code that uses `TradeRepository.insert()`
2. Ensure only `OrderExecutionService.createTradeInCreatedState()` calls insert

**Test Steps:**
1. Search codebase for `tradeRepo.insert`:
   ```bash
   grep -rn "tradeRepo\.insert" src/
   ```
2. Verify only ONE location: `OrderExecutionService.java:88`
3. Verify trade creation always sets `status="CREATED"`
4. Run full integration test suite
5. Check database for any trades with status != CREATED at creation

**Acceptance Criteria:**
- ✅ Only `OrderExecutionService` calls `tradeRepo.insert()`
- ✅ All inserted trades have `status=CREATED`
- ✅ No trades created directly in PENDING/OPEN state
- ✅ No code bypasses CREATED state

**SQL Verification:**
```sql
-- Check if any trades were created in non-CREATED state
-- (This query should return no results after P0-E implementation)
SELECT trade_id, status, created_at, updated_at
FROM trades
WHERE created_at = updated_at  -- Same timestamp = just created
  AND status != 'CREATED'
  AND deleted_at IS NULL;

-- Expected: 0 rows (all trades start in CREATED state)
```

---

### Test 5: markRejectedByIntentId Only Updates CREATED Trades

**Objective:** Verify that rejection method only updates trades in CREATED state (not PENDING/OPEN).

**Setup:**
1. Create trade in CREATED state
2. Manually update trade to PENDING (simulate broker acceptance)
3. Try to call `markRejectedByIntentId()` on PENDING trade

**Test Steps:**
1. Insert trade with status=CREATED:
   ```sql
   INSERT INTO trades (trade_id, intent_id, status, ...) VALUES (...);
   ```
2. Update trade to PENDING:
   ```sql
   UPDATE trades SET status = 'PENDING' WHERE intent_id = 'test-intent-123';
   ```
3. Call `tradeRepo.markRejectedByIntentId("test-intent-123", "TEST_ERROR", "Test")`
4. Check return value: should be `false` (not updated)
5. Verify trade still has status=PENDING (not REJECTED)

**Acceptance Criteria:**
- ✅ `markRejectedByIntentId()` returns `false` when trade not in CREATED state
- ✅ Trade status unchanged (remains PENDING)
- ✅ Log message: "No CREATED trade found for intent"
- ✅ Only trades in CREATED state can be marked as REJECTED

**SQL Verification:**
```sql
-- The UPDATE statement in markRejectedByIntentId has this WHERE clause:
-- WHERE intent_id = ? AND deleted_at IS NULL AND status = 'CREATED'

-- Test: try to update PENDING trade (should affect 0 rows)
UPDATE trades
SET status = 'REJECTED'
WHERE intent_id = 'test-intent-123'
  AND deleted_at IS NULL
  AND status = 'CREATED';

-- If trade is PENDING, this returns: 0 rows updated
```

---

### Test 6: Concurrent Order Execution (Idempotency)

**Objective:** Verify that duplicate intent submissions don't create duplicate trades.

**Setup:**
1. Create approved trade intent
2. Submit intent twice concurrently

**Test Steps:**
1. Submit same intent from two threads:
   ```java
   ExecutorService executor = Executors.newFixedThreadPool(2);
   executor.submit(() -> orderExecutionService.executeIntent(intent));
   executor.submit(() -> orderExecutionService.executeIntent(intent));
   ```
2. Wait for both to complete
3. Query database:
   ```sql
   SELECT COUNT(*) FROM trades WHERE intent_id = 'test-intent-123';
   ```

**Acceptance Criteria:**
- ✅ Only ONE trade created (despite two concurrent calls)
- ✅ P0-B idempotency constraint prevents duplicate: `UNIQUE (intent_id)`
- ✅ Second insert attempt fails with constraint violation
- ✅ Second thread handles exception gracefully

**SQL Verification:**
```sql
-- Verify only one trade exists
SELECT COUNT(*), trade_id, status
FROM trades
WHERE intent_id = 'test-intent-123'
  AND deleted_at IS NULL
GROUP BY trade_id, status;

-- Expected: COUNT = 1
```

---

### Test 7: Integration with PendingOrderReconciler

**Objective:** Verify that reconciler correctly updates CREATED trades to PENDING/OPEN.

**Setup:**
1. Start system with reconciler running
2. Create trade in CREATED state
3. Mock broker to return order status = PENDING

**Test Steps:**
1. Insert trade with status=CREATED
2. Ensure `broker_order_id` is set (simulating broker acceptance)
3. Wait for reconciler to run (30 seconds)
4. Verify reconciler calls `tradeRepo.upsert()` to update trade
5. Check trade status changed to PENDING

**Acceptance Criteria:**
- ✅ Reconciler detects CREATED trade with `broker_order_id`
- ✅ Reconciler updates trade to PENDING (not creates new trade)
- ✅ Trade `last_broker_update_at` updated by reconciler
- ✅ Only ONE trade row exists (single writer maintained)

**Reconciler Log Messages:**
```
INFO  PendingOrderReconciler - Reconciling 1 pending trades
DEBUG PendingOrderReconciler - Trade test-trade-123 status changed: CREATED -> PENDING
INFO  PendingOrderReconciler - Updated trade test-trade-123 via upsert
```

---

### Test 8: End-to-End Flow (CREATED → REJECTED)

**Objective:** Full end-to-end test of order placement with immediate broker rejection.

**Setup:**
1. Start full system (signal generator, orchestrator, execution service, reconciler)
2. Configure mock broker to reject all orders

**Test Steps:**
1. Generate signal → fan-out → create approved intent
2. Execute intent via `OrderExecutionService.executeIntent()`
3. Trace trade lifecycle:
   - a. Trade created in CREATED state
   - b. Broker called with order request
   - c. Broker rejects with error code
   - d. Trade marked as REJECTED
4. Verify events emitted: ORDER_PLACED (attempt), ORDER_REJECTED
5. Verify WebSocket client receives ORDER_REJECTED event

**Acceptance Criteria:**
- ✅ Trade created in CREATED state before broker call
- ✅ Broker rejection handled gracefully
- ✅ Trade marked as REJECTED (single row)
- ✅ Error code stored in `exit_trigger` field
- ✅ ORDER_REJECTED event emitted
- ✅ User notified via WebSocket

**Timeline Verification:**
```
T+0ms:   Trade inserted (status=CREATED)
T+10ms:  Broker API called
T+200ms: Broker responds (rejected)
T+210ms: Trade updated (status=REJECTED)
T+220ms: ORDER_REJECTED event emitted
T+230ms: WebSocket notification sent
```

---

### Test 9: End-to-End Flow (CREATED → PENDING → OPEN)

**Objective:** Full end-to-end test of order placement with broker acceptance and fill.

**Setup:**
1. Start full system with real broker (UAT mode)
2. Use liquid symbol with good liquidity (e.g., INFY, TCS)

**Test Steps:**
1. Generate signal → fan-out → create approved intent
2. Execute intent via `OrderExecutionService.executeIntent()`
3. Trace trade lifecycle:
   - a. Trade created in CREATED state
   - b. Broker called with order request
   - c. Broker accepts with `brokerOrderId`
   - d. Reconciler updates trade to PENDING
   - e. Order fills (market order)
   - f. Reconciler updates trade to OPEN
4. Verify only ONE trade row throughout lifecycle

**Acceptance Criteria:**
- ✅ Trade created in CREATED state
- ✅ Broker acceptance doesn't create new trade
- ✅ Reconciler updates status: CREATED → PENDING → OPEN
- ✅ Only ONE trade row exists (verified at each state transition)
- ✅ `broker_order_id` populated after broker acceptance
- ✅ `entry_price` and `entry_timestamp` populated after fill
- ✅ Events emitted: ORDER_PLACED, ORDER_FILLED

**SQL Timeline Verification:**
```sql
-- Query trade history (should show single row evolving through states)
SELECT trade_id, status, broker_order_id, entry_price, created_at, updated_at
FROM trades
WHERE intent_id = 'test-intent-123'
  AND deleted_at IS NULL
ORDER BY updated_at;

-- Expected: Single row with status=OPEN, broker_order_id set, entry_price filled
```

---

## Debugging Tips

### How to Enable DEBUG Logging

Add to `logback.xml`:
```xml
<logger name="in.annupaper.service.execution.OrderExecutionService" level="DEBUG"/>
<logger name="in.annupaper.repository.PostgresTradeRepository" level="DEBUG"/>
```

### How to Verify Single Writer

Search for all `tradeRepo.insert()` calls:
```bash
grep -rn "tradeRepo\.insert" src/main/java/
# Should only find: OrderExecutionService.java:88
```

Search for direct trade row creation:
```bash
grep -rn "new Trade(" src/main/java/
# Should only find: OrderExecutionService.java:163
```

### How to Test markRejectedByIntentId Directly

```java
// Create trade in CREATED state
Trade trade = createTradeInCreatedState(intent, signal);
tradeRepo.insert(trade);

// Mark as rejected
boolean updated = tradeRepo.markRejectedByIntentId(
    intent.intentId(),
    "TEST_ERROR",
    "Test rejection"
);

// Verify
assertTrue(updated);
Trade updated = tradeRepo.findByIntentId(intent.intentId());
assertEquals("REJECTED", updated.status());
assertEquals("TEST_ERROR", updated.exitTrigger());
```

---

## Success Criteria

**P0-E is considered complete when:**

1. ✅ **Single Writer:** Only `OrderExecutionService` creates trade rows
2. ✅ **CREATED State First:** All trades created with `status=CREATED` before broker call
3. ✅ **Rejection Path:** `markRejectedByIntentId()` updates (not inserts) trades
4. ✅ **No Duplicates:** Intent idempotency prevents duplicate trade rows
5. ✅ **Reconciler Integration:** Reconciler updates existing trades (not creates new)
6. ✅ **State Transitions:** CREATED → REJECTED or CREATED → PENDING → OPEN
7. ✅ **P0DebtRegistry:** ORDER_EXECUTION_IMPLEMENTED flag set to `true`

---

## Next Steps

After verifying P0-E, remaining P0 gates:
- **POSITION_TRACKING_LIVE:** Ensure TradeRepository queries DB (not HashMap)
- **BROKER_RECONCILIATION_RUNNING:** Verify PendingOrderReconciler started (P0-C already done)
- **SIGNAL_DB_CONSTRAINTS_APPLIED:** Run V007 migration (P0-B already done)
- **TRADE_IDEMPOTENCY_CONSTRAINTS:** Run V007 migration (P0-B already done)

---

## Implementation Notes

**Key Design Decisions:**

1. **Single Writer Pattern:** Only one code path creates trades
   - Prevents race conditions
   - Enforces consistent state transitions
   - Simplifies debugging (single source of truth)

2. **CREATED State First:** Trade row exists before broker call
   - Enables tracking of broker rejections
   - Provides audit trail of all order attempts
   - Prevents "phantom orders" (broker accepts but no DB row)

3. **UPDATE-Only Rejection:** `markRejectedByIntentId()` uses UPDATE
   - Ensures single trade row per intent
   - WHERE clause enforces status=CREATED (state guard)
   - Returns boolean to indicate if update succeeded

4. **Intent Idempotency:** UNIQUE constraint on `intent_id`
   - Prevents duplicate trade creation
   - Works with P0-B idempotency layer
   - Handles concurrent order submissions

**References:**
- `OrderExecutionService.java` - Main implementation
- `TradeRepository.java:120-140` - markRejectedByIntentId signature
- `PostgresTradeRepository.java:894-939` - markRejectedByIntentId implementation
- `P0DebtRegistry.java:22` - Gate flag
- `COMPREHENSIVE_IMPLEMENTATION_PLAN.md` - Phase 1, P0-E
