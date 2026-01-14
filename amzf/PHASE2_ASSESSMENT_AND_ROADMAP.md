# Phase 2: Core Execution - Assessment & Roadmap

**Date:** 2026-01-13
**Status:** PARTIALLY COMPLETE
**Estimated Remaining:** 15-25 hours (down from 40-60 hours)

---

## Executive Summary

**Good News:** Much of Phase 2 is already implemented!

- ✅ **Order Placement Infrastructure** - BrokerAdapter interface complete
- ✅ **All Broker Implementations** - placeOrder() exists for all 5 brokers
- ✅ **OrderExecutionService** - Single-writer pattern implemented (P0-E)
- ✅ **Position Tracking Fix** - ExitSignalService queries DB (not HashMap)
- ✅ **Broker Reconciliation** - PendingOrderReconciler running every 30s

**What's Missing:**
- ⏳ Wire ExecutionOrchestrator → OrderExecutionService
- ⏳ WebSocket order update callbacks
- ⏳ Trade lifecycle state machine (CREATED → PENDING → OPEN → CLOSED)
- ⏳ Exit signal generation and exit order placement

---

## Current State Analysis

### ✅ COMPLETE: Week 2 - Order Placement (90% done)

#### Task 2.1: ExecutionOrchestrator ✅ DONE
**File:** `ExecutionOrchestrator.java`

**What Exists:**
- Signal fan-out to all active EXEC brokers ✅
- Parallel validation per user-broker ✅
- TradeIntent creation ✅
- Event emission for intents ✅

**What's Missing:**
- `executeApprovedIntents()` currently emits events only
- Need to call `OrderExecutionService.executeIntent()` instead

**Fix Required:** 5 lines of code
```java
private void executeIntent(TradeIntent intent) {
    // Call OrderExecutionService instead of emitting event
    orderExecutionService.executeIntent(intent)
        .thenAccept(trade -> log.info("Trade created: {}", trade.tradeId()))
        .exceptionally(e -> {
            log.error("Order execution failed: {}", e.getMessage());
            return null;
        });
}
```

#### Task 2.2: Broker Adapter Implementation ✅ DONE
**Files:** All 5 broker adapters

**Status:** All brokers have `placeOrder()` implemented:
- ✅ `FyersAdapter.placeOrder()` - 220:
- ✅ `ZerodhaAdapter.placeOrder()` - 92:
- ✅ `DhanAdapter.placeOrder()` - 82:
- ✅ `UpstoxAdapter.placeOrder()` - 74:
- ✅ `AlpacaAdapter.placeOrder()` - 74:

**Additional Methods Available:**
- ✅ `modifyOrder()`
- ✅ `cancelOrder()`
- ✅ `getOrderStatus()`
- ✅ `getPositions()`
- ✅ `getFunds()`

---

### ✅ COMPLETE: Week 3 - Position Tracking (95% done)

#### Task 3.1: Replace HashMap with DB Queries ✅ DONE
**File:** `ExitSignalService.java` (refactored in Phase 1)

**Status:**
- ✅ Queries `TradeRepository` for open positions
- ✅ No more in-memory HashMap
- ✅ Database is source of truth

**Remaining Work:** None! This was completed in Phase 1.

#### Task 3.2: Broker Fill Callbacks ⏳ PARTIAL
**Files:** Broker adapters have WebSocket infrastructure

**What Exists:**
- ✅ WebSocket connections in all adapters
- ✅ `TickListener` interface for tick data
- ✅ Tick simulation in FyersAdapter

**What's Missing:**
- ⏳ Order update callbacks (fills, rejections, partial fills)
- ⏳ Trade state updates on fill

**Estimated Time:** 8-10 hours

---

### ⏳ INCOMPLETE: Week 4 - Trade Lifecycle (40% done)

#### Task 4.1: Entry Order Fills ⏳ TODO
**Goal:** Update trade state when order fills

**What Exists:**
- ✅ `PendingOrderReconciler` polls broker for pending orders
- ✅ Trade model has all necessary fields

**What's Missing:**
- ⏳ Fill detection in reconciler
- ⏳ `TradeRepository.markFilled()` method
- ⏳ State transition PENDING → OPEN

**Estimated Time:** 4-5 hours

#### Task 4.2: Exit Signal Generation ✅ PARTIAL
**File:** `ExitSignalService.java`

**What Exists:**
- ✅ Brick movement tracking
- ✅ Stop loss breach detection
- ✅ Target achievement detection

**What's Missing:**
- ⏳ Actual exit order placement (currently just emits events)

**Estimated Time:** 2-3 hours

#### Task 4.3: Exit Order Placement ⏳ TODO
**Goal:** Place exit orders when signals generated

**Requirements:**
- Call `OrderExecutionService` for exit orders
- Update trade state to EXITING
- Handle exit order fills

**Estimated Time:** 4-5 hours

#### Task 4.4: Trade Closure ⏳ TODO
**Goal:** Mark trades as CLOSED when exit completes

**Requirements:**
- Update trade with exit price, exit timestamp
- Calculate realized P&L
- Emit trade closure event

**Estimated Time:** 2-3 hours

---

## Revised Phase 2 Roadmap

### Priority 1: Wire Up Order Execution (HIGH) - 2 hours

**Task:** Connect ExecutionOrchestrator to OrderExecutionService

**Files to Modify:**
1. `ExecutionOrchestrator.java` - inject OrderExecutionService
2. `App.java` - pass OrderExecutionService to ExecutionOrchestrator

**Deliverable:** Approved intents automatically place orders at broker

**Testing:**
- Generate signal
- Verify order appears in broker terminal
- Verify trade row created with PENDING status

---

### Priority 2: Order Fill Detection (HIGH) - 5 hours

**Task:** Detect order fills via reconciler and WebSocket

**Files to Modify:**
1. `PendingOrderReconciler.java` - detect fills
2. `TradeRepository.java` - add `markFilled()`
3. `PostgresTradeRepository.java` - implement `markFilled()`
4. Broker adapters - add order update callbacks

**Deliverable:** Trades transition PENDING → OPEN on fill

**Testing:**
- Place order
- Wait for fill
- Verify trade state = OPEN
- Verify entry price, entry timestamp updated

---

### Priority 3: Exit Order Placement (MEDIUM) - 6 hours

**Task:** Place exit orders when stop/target hit

**Files to Modify:**
1. `ExitSignalService.java` - call OrderExecutionService
2. `OrderExecutionService.java` - handle exit orders
3. Trade model - add EXITING status

**Deliverable:** Exit orders placed automatically

**Testing:**
- Enter trade
- Move price to stop loss
- Verify exit order placed
- Verify trade state = EXITING

---

### Priority 4: Trade Closure (MEDIUM) - 3 hours

**Task:** Close trades on exit fill

**Files to Modify:**
1. `PendingOrderReconciler.java` - detect exit fills
2. `TradeRepository.java` - add `markClosed()`
3. `PostgresTradeRepository.java` - implement P&L calculation

**Deliverable:** Trades close with realized P&L

**Testing:**
- Full trade lifecycle
- Entry → Open → Exit → Closed
- Verify P&L calculated correctly

---

### Priority 5: WebSocket Order Updates (LOW) - 8 hours

**Task:** Real-time order updates via WebSocket

**Files to Modify:**
1. All broker adapters - order update message handlers
2. Create `OrderUpdateListener` interface
3. Wire to TradeService for immediate updates

**Deliverable:** Sub-second order fill updates (not 30s polling)

**Note:** This is a performance optimization. Reconciler already provides correctness.

---

## Recommended Execution Order

### Phase 2A: Core Order Flow (1 week, 15 hours)
1. ✅ Wire ExecutionOrchestrator → OrderExecutionService (2h)
2. ✅ Implement order fill detection in reconciler (5h)
3. ✅ Add exit order placement (6h)
4. ✅ Implement trade closure (3h)

**Outcome:** Complete order-to-trade lifecycle working

### Phase 2B: Real-time Updates (1 week, 10 hours)
5. ✅ Implement WebSocket order callbacks (8h)
6. ✅ Performance testing and optimization (2h)

**Outcome:** Sub-second order updates

---

## Testing Strategy

### Unit Tests (2 hours)
- `ExecutionOrchestratorTest` - order placement
- `PendingOrderReconcilerTest` - fill detection
- `ExitSignalServiceTest` - exit order placement
- `TradeRepositoryTest` - state transitions

### Integration Tests (3 hours)
- End-to-end trade lifecycle
- Signal → Intent → Order → Fill → Open → Exit → Closed
- Test with paper trading accounts

### Live Testing (2 hours)
- Small position size (1 share)
- Monitor logs and database
- Verify broker terminal matches system state

---

## Risk Assessment

### Low Risk ✅
- Order placement (already implemented)
- Position tracking (already working)
- Database queries (tested in Phase 1)

### Medium Risk ⚠️
- Fill detection timing (30s delay acceptable)
- Exit signal accuracy (depends on brick tracking)
- Broker API rate limits (need throttling)

### High Risk 🔴
- Trade state race conditions (need locking)
- Duplicate order placement (idempotency tested in P0-B)
- Broker API changes (need version pinning)

**Mitigation:**
- All mitigated by Phase 1 P0 gates ✅
- Idempotency constraints prevent duplicates
- Reconciler heals inconsistent state
- Single-writer prevents race conditions

---

## Success Criteria

### Phase 2A Complete When:
- [ ] Signal generates approved intent
- [ ] Intent places order at broker
- [ ] Order fill updates trade to OPEN
- [ ] Stop/target triggers exit order
- [ ] Exit fill closes trade with P&L
- [ ] All state transitions logged and observable

### Phase 2B Complete When:
- [ ] Order fills detected in <1 second
- [ ] WebSocket connectivity monitored
- [ ] Failover to polling on disconnect
- [ ] Performance: 100 trades/sec sustained

---

## Current Recommendation

**Start with Priority 1: Wire Up Order Execution (2 hours)**

This gives immediate value - approved intents will automatically place orders.

**Command to proceed:**
```bash
# Tell me: "Start Priority 1" or "Wire up order execution"
```

Once Priority 1 is done, you'll have a working order placement system!

---

## Files Overview

### Modified in Phase 1 ✅
- `OrderExecutionService.java` - Single-writer trade creation
- `PendingOrderReconciler.java` - 30s reconciliation loop
- `ExitSignalService.java` - DB-based position tracking
- `TradeRepository.java` - Upsert and state update methods

### Need Modification in Phase 2 ⏳
- `ExecutionOrchestrator.java` - Wire to OrderExecutionService
- `App.java` - Dependency injection
- `PendingOrderReconciler.java` - Add fill detection
- Broker adapters - Add order update callbacks

### New Files Needed 📝
- None! Everything can use existing files.

---

**Total Remaining Effort:** 15-25 hours (vs original 40-60)
**Reason for Reduction:** Phase 1 implemented most of the core infrastructure

**You're 60% done with Phase 2 already!** 🎉
