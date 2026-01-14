# Signal-to-Trade Complete Flow Analysis

**Date:** 2026-01-13
**Purpose:** Document current state of signal → validation → trade → exit flow
**Status:** ⚠️ PARTIAL - Key gaps identified

---

## Your Question

> "I want to understand that broadcasting of signals to various user brokers and qualification of signals by userbrokers and there by creation of trades and trade handing over the tradeobject to create order, based on the fact that is that the first active order for that symbol, to classify as NEWBUY otherwise REBUY and following through that order for EXIT conditions to meet and subsequently exiting the order and closing the trade...All this is there?"

---

## Short Answer

**Mostly YES, but 3 critical gaps:**

✅ Signal broadcasting to user-brokers - **COMPLETE**
✅ Signal qualification/validation per user-broker - **COMPLETE**
✅ Trade creation with proper state - **COMPLETE**
❌ **GAP 1:** Trade handover to order execution - **DISCONNECTED** (emits event only, doesn't place order)
❌ **GAP 2:** NEWBUY vs REBUY classification - **NOT IMPLEMENTED** (tradeNumber hardcoded to 1)
✅ Exit condition monitoring - **COMPLETE**
❌ **GAP 3:** Exit order placement - **NOT IMPLEMENTED** (emits event only)
❌ Trade closure - **NOT IMPLEMENTED**

**Estimated Fix:** 8-12 hours to complete all gaps

---

## Detailed Flow Analysis

### Step 1: ✅ Signal Broadcasting (COMPLETE)

**File:** `SignalService.java:90`

```java
// Fan out to all user-brokers
List<TradeIntent> intents = executionOrchestrator.fanOutSignal(signal);
```

**What Happens:**
1. Signal generated (manual or automated)
2. `ExecutionOrchestrator.fanOutSignal()` called
3. Gets all active EXEC brokers: `userBrokerRepo.findAllActiveExecBrokers()`
4. Creates parallel validation tasks for each user-broker

**Status:** ✅ **WORKING**
**Evidence:** `ExecutionOrchestrator.java:60-106`

---

### Step 2: ✅ Signal Qualification per User-Broker (COMPLETE)

**File:** `ExecutionOrchestrator.java:111-160`

```java
private TradeIntent validateAndCreateIntent(Signal signal, UserBroker userBroker) {
    // Get user context
    ValidationService.UserContext userContext = userContextProvider.apply(userBroker.userId());

    // Validate signal
    ValidationResult result = validationService.validate(signal, userBroker, userContext);

    // Create intent (APPROVED or REJECTED)
    IntentStatus status = result.passed() ? IntentStatus.APPROVED : IntentStatus.REJECTED;

    return new TradeIntent(intentId, signal, userBroker, result, ...);
}
```

**What Gets Validated:**
- ✅ Survival layer (risk budgets, max loss, position limits)
- ✅ Constitutional position sizing
- ✅ Velocity throttling
- ✅ Utility asymmetry (3× advantage requirement)
- ✅ Portfolio exposure limits
- ✅ Symbol-level limits

**Output:** `TradeIntent` with:
- `validationPassed` = true/false
- `calculatedQty` (if approved)
- `calculatedValue`
- `orderType` (MARKET/LIMIT)
- `limitPrice`
- `productType` (CNC/MIS/NRML)
- `validationErrors` (if rejected)

**Status:** ✅ **WORKING**
**Evidence:** Intents created and persisted to DB

---

### Step 3: ✅ Trade Creation (COMPLETE)

**File:** `OrderExecutionService.java:168-249`

```java
private Trade createTradeInCreatedState(TradeIntent intent, Signal signal) {
    return new Trade(
        tradeId,
        intent.userId(),
        intent.brokerId(),
        intent.userBrokerId(),
        signal.signalId(),
        intent.intentId(),
        signal.symbol(),
        1,  // ❌ tradeNumber - hardcoded! Should calculate NEWBUY vs REBUY

        // Entry details
        intent.limitPrice(),  // entryPrice (pending)
        intent.calculatedQty(),  // entryQty
        intent.calculatedValue(),  // entryValue
        null,  // entryTimestamp (will be set on fill)

        // Status
        "CREATED",  // ✅ Proper initial state

        // Exit targets
        signal.effectiveCeiling(),  // exitPrimaryPrice

        // Broker tracking
        null,  // brokerOrderId (will be set after order placement)
        intent.intentId(),  // clientOrderId (idempotency key)

        now,  // createdAt
        now   // updatedAt
    );
}
```

**Status:** ✅ **WORKING**
**Evidence:** Trades created in database with status=CREATED

---

### Step 4: ❌ GAP 1 - Trade Handover to Order Execution (DISCONNECTED)

**File:** `SignalService.java:93`

```java
// Execute approved intents
executionOrchestrator.executeApprovedIntents(intents);
```

**What Currently Happens:**

**File:** `ExecutionOrchestrator.java:234-266`

```java
private void executeIntent(TradeIntent intent) {
    // ✅ P0-E: DEPRECATED - Use OrderExecutionService instead
    log.warn("⚠️ P0-E: ExecutionOrchestrator.executeIntent() is deprecated.");

    // ❌ ONLY emits event, doesn't actually place order!
    eventService.emitUserBroker(
        EventType.ORDER_CREATED,
        intent.userId(),
        payload,
        ...
    );
}
```

**What SHOULD Happen:**

```java
private void executeIntent(TradeIntent intent) {
    // Call OrderExecutionService to create trade and place order
    orderExecutionService.executeIntent(intent)
        .thenAccept(trade -> log.info("Trade created: {}", trade.tradeId()))
        .exceptionally(e -> {
            log.error("Order execution failed: {}", e.getMessage());
            return null;
        });
}
```

**Status:** ❌ **MISSING - Orders not placed at broker!**

**Fix Required:**
1. Inject `OrderExecutionService` into `ExecutionOrchestrator`
2. Replace deprecated method with actual order placement
3. Update `App.java` to wire dependencies

**Estimated Time:** 30 minutes

---

### Step 5: ❌ GAP 2 - NEWBUY vs REBUY Classification (NOT IMPLEMENTED)

**Current State:**

**File:** `OrderExecutionService.java:181`

```java
1,  // tradeNumber (TODO: calculate from existing trades)
```

**What's Missing:**

```java
// Should calculate:
int tradeNumber = calculateTradeNumber(intent.userId(), signal.symbol());

private int calculateTradeNumber(String userId, String symbol) {
    // Count existing trades for this user + symbol
    List<Trade> existingTrades = tradeRepo.findByUserAndSymbol(userId, symbol);

    // Count only non-cancelled trades
    long count = existingTrades.stream()
        .filter(t -> !"CANCELLED".equals(t.status()) && !"REJECTED".equals(t.status()))
        .count();

    return (int) count + 1;  // Next trade number
}

// Then classify:
// tradeNumber == 1 → NEWBUY (first position for this symbol)
// tradeNumber > 1 → REBUY (averaging/adding to position)
```

**Why This Matters:**
- **NEWBUY:** First entry into symbol (full risk budget available)
- **REBUY:** Averaging into existing position (reduced risk, check averaging gate)

**Status:** ❌ **NOT IMPLEMENTED**

**Fix Required:**
1. Add `findByUserAndSymbol()` to `TradeRepository`
2. Implement `calculateTradeNumber()` in `OrderExecutionService`
3. Add trade classification logic to validation service

**Estimated Time:** 2-3 hours

---

### Step 6: ✅ EXIT Condition Monitoring (COMPLETE)

**File:** `ExitSignalService.java:53-106`

```java
@Override
public void onTick(BrokerAdapter.Tick tick) {
    String symbol = tick.symbol();
    BigDecimal price = tick.lastPrice();

    // ✅ Query DB for open trades (not HashMap!)
    List<Trade> openTrades = tradeRepo.findBySymbol(symbol).stream()
        .filter(Trade::isOpen)
        .toList();

    // Check exit conditions for each open trade
    for (Trade trade : openTrades) {
        checkExitConditions(trade, price);
    }
}

private void checkExitConditions(Trade trade, BigDecimal currentPrice) {
    ExitReason exitReason = null;

    // ✅ 1. Check target hit
    if (trade.exitTargetPrice() != null && currentPrice.compareTo(trade.exitTargetPrice()) >= 0) {
        exitReason = ExitReason.TARGET_HIT;
    }

    // ✅ 2. Check stop loss
    if (exitReason == null && currentPrice.compareTo(trade.entryEffectiveFloor()) <= 0) {
        exitReason = ExitReason.STOP_LOSS;
    }

    // ✅ 3. Check max holding time
    if (exitReason == null && isMaxHoldTimeExceeded(trade)) {
        exitReason = ExitReason.TIME_BASED;
    }

    // ✅ 4. Brick movement filter
    if (exitReason != null && brickTracker.shouldAllowExit(trade.symbol(), direction, currentPrice)) {
        emitExitSignal(trade, currentPrice, exitReason);
    }
}
```

**Exit Conditions Monitored:**
- ✅ Target price hit (exitTargetPrice)
- ✅ Stop loss breach (entryEffectiveFloor)
- ✅ Max holding time exceeded (30 days default)
- ✅ Brick movement filter (prevents chasing reversals)

**Status:** ✅ **WORKING**
**Evidence:** Exit signals emitted when conditions met

---

### Step 7: ❌ GAP 3 - Exit Order Placement (NOT IMPLEMENTED)

**Current State:**

**File:** `ExitSignalService.java:148-213`

```java
private void emitExitSignal(Trade trade, BigDecimal exitPrice, ExitReason exitReason) {
    // Create exit signal
    ExitSignal exitSignal = new ExitSignal(...);

    // ❌ ONLY emits event, doesn't place exit order!
    eventService.emitUserBroker(
        EventType.SIGNAL_EXIT,
        trade.userId(),
        payload,
        ...
    );
}
```

**What SHOULD Happen:**

```java
private void placeExitOrder(Trade trade, BigDecimal exitPrice, ExitReason exitReason) {
    // 1. Emit exit signal (for audit/logging)
    emitExitSignal(trade, exitPrice, exitReason);

    // 2. Mark trade as EXITING
    tradeRepo.markExiting(trade.tradeId(), exitReason, exitPrice);

    // 3. Place exit order at broker
    BrokerAdapter broker = brokerFactory.getOrCreate(trade.userBrokerId(), trade.brokerId());

    BrokerAdapter.OrderRequest exitOrder = new BrokerAdapter.OrderRequest(
        trade.symbol(),
        "NSE",
        trade.direction().equals("LONG") ? "SELL" : "BUY",  // Reverse direction
        "MARKET",  // Exit at market (or LIMIT if configured)
        trade.productType(),
        trade.entryQty(),  // Exit full position
        exitPrice,  // Limit price (if LIMIT order)
        null,  // No trigger price
        "DAY",
        trade.tradeId() + "-EXIT"  // Tag for tracking
    );

    broker.placeOrder(exitOrder)
        .thenAccept(result -> {
            if (result.success()) {
                tradeRepo.updateExitOrderId(trade.tradeId(), result.orderId());
                log.info("Exit order placed: {} @ {}", trade.tradeId(), exitPrice);
            } else {
                log.error("Exit order rejected: {} - {}", result.errorCode(), result.message());
                tradeRepo.markExitOrderFailed(trade.tradeId(), result.message());
            }
        });
}
```

**Status:** ❌ **NOT IMPLEMENTED - Exit signals generated but orders not placed!**

**Fix Required:**
1. Add exit order placement logic to `ExitSignalService`
2. Add `markExiting()` to `TradeRepository`
3. Handle exit order fills in `PendingOrderReconciler`
4. Add EXITING status to Trade model

**Estimated Time:** 4-6 hours

---

### Step 8: ❌ Trade Closure (NOT IMPLEMENTED)

**What's Missing:**

```java
// In PendingOrderReconciler or ExitSignalService
private void handleExitFill(Trade trade, OrderStatus orderStatus) {
    // 1. Calculate realized P&L
    BigDecimal exitPrice = orderStatus.avgPrice();
    BigDecimal exitValue = exitPrice.multiply(BigDecimal.valueOf(trade.entryQty()));
    BigDecimal realizedPnl = exitValue.subtract(trade.entryValue());

    // 2. Calculate holding days
    long holdingDays = Duration.between(trade.entryTimestamp(), Instant.now()).toDays();

    // 3. Mark trade as CLOSED
    tradeRepo.markClosed(
        trade.tradeId(),
        exitPrice,
        Instant.now(),
        orderStatus.orderId(),
        realizedPnl,
        holdingDays
    );

    // 4. Emit trade closure event
    eventService.emitUserBroker(
        EventType.TRADE_CLOSED,
        trade.userId(),
        Map.of(
            "tradeId", trade.tradeId(),
            "exitPrice", exitPrice,
            "realizedPnl", realizedPnl,
            "holdingDays", holdingDays
        ),
        ...
    );

    log.info("Trade closed: {} P&L={} days={}", trade.tradeId(), realizedPnl, holdingDays);
}
```

**Status:** ❌ **NOT IMPLEMENTED**

**Fix Required:**
1. Add `markClosed()` to `TradeRepository`
2. Implement P&L calculation
3. Update `PendingOrderReconciler` to detect exit fills
4. Emit trade closure events

**Estimated Time:** 2-3 hours

---

## Complete Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                    SIGNAL GENERATION                                │
└─────────────────────────────────────────────────────────────────────┘
                               ↓
                    ✅ SignalService.processSignal()
                               ↓
┌─────────────────────────────────────────────────────────────────────┐
│              STEP 1: BROADCAST TO USER-BROKERS                      │
│  ✅ ExecutionOrchestrator.fanOutSignal(signal)                      │
│     - Get all active EXEC brokers                                   │
│     - Parallel validation for each user-broker                      │
└─────────────────────────────────────────────────────────────────────┘
                               ↓
┌─────────────────────────────────────────────────────────────────────┐
│              STEP 2: VALIDATE PER USER-BROKER                       │
│  ✅ ValidationService.validate(signal, userBroker, userContext)     │
│     - Survival layer checks                                         │
│     - Position sizing                                               │
│     - Velocity throttling                                           │
│     - Utility asymmetry gate                                        │
│     → Output: TradeIntent (APPROVED or REJECTED)                    │
└─────────────────────────────────────────────────────────────────────┘
                               ↓
┌─────────────────────────────────────────────────────────────────────┐
│                   STEP 3: CREATE TRADE ROW                          │
│  ✅ OrderExecutionService.executeIntent(intent)                     │
│     - Create Trade with status=CREATED                              │
│     - ❌ tradeNumber hardcoded to 1 (should calculate NEWBUY/REBUY)│
│     - Insert into trades table                                      │
└─────────────────────────────────────────────────────────────────────┘
                               ↓
┌─────────────────────────────────────────────────────────────────────┐
│            ❌ GAP 1: TRADE HANDOVER TO ORDER EXECUTION              │
│  Current: ExecutionOrchestrator.executeIntent()                     │
│     - ❌ Only emits ORDER_CREATED event                             │
│     - ❌ Does NOT call OrderExecutionService                        │
│  Required:                                                          │
│     - Call OrderExecutionService.executeIntent(intent)              │
│     - Get broker adapter                                            │
│     - Place order at broker                                         │
└─────────────────────────────────────────────────────────────────────┘
                               ↓
┌─────────────────────────────────────────────────────────────────────┐
│                 STEP 4: PLACE ORDER AT BROKER                       │
│  ✅ OrderExecutionService (infrastructure exists)                   │
│     - Get broker adapter via BrokerAdapterFactory                   │
│     - Call broker.placeOrder(orderRequest)                          │
│     - Update trade: brokerOrderId, status=PENDING                   │
└─────────────────────────────────────────────────────────────────────┘
                               ↓
┌─────────────────────────────────────────────────────────────────────┐
│                 STEP 5: WAIT FOR ORDER FILL                         │
│  ✅ PendingOrderReconciler (polls every 30 seconds)                 │
│     - Query broker.getOrderStatus(orderId)                          │
│     - Detect fill                                                   │
│     - ⏳ Update trade: status=OPEN, entryPrice, entryTimestamp      │
└─────────────────────────────────────────────────────────────────────┘
                               ↓
┌─────────────────────────────────────────────────────────────────────┐
│            ❌ GAP 2: NEWBUY vs REBUY CLASSIFICATION                 │
│  Current: tradeNumber = 1 (always)                                  │
│  Required:                                                          │
│     - Calculate tradeNumber from existing trades                    │
│     - tradeNumber = 1 → NEWBUY (first position)                     │
│     - tradeNumber > 1 → REBUY (averaging)                           │
│     - Apply averaging gate validation                               │
└─────────────────────────────────────────────────────────────────────┘
                               ↓
┌─────────────────────────────────────────────────────────────────────┐
│           STEP 6: MONITOR EXIT CONDITIONS (EVERY TICK)              │
│  ✅ ExitSignalService.onTick(tick)                                  │
│     - Query DB for open trades on symbol                            │
│     - Check target hit                                              │
│     - Check stop loss breach                                        │
│     - Check max holding time                                        │
│     - Brick movement filter                                         │
│     → If condition met: emit EXIT_SIGNAL                            │
└─────────────────────────────────────────────────────────────────────┘
                               ↓
┌─────────────────────────────────────────────────────────────────────┐
│            ❌ GAP 3: EXIT ORDER PLACEMENT                           │
│  Current: ExitSignalService.emitExitSignal()                        │
│     - ❌ Only emits SIGNAL_EXIT event                               │
│     - ❌ Does NOT place exit order                                  │
│  Required:                                                          │
│     - Mark trade as EXITING                                         │
│     - Place exit order at broker (SELL for LONG, BUY for SHORT)    │
│     - Update trade.exitOrderId                                      │
└─────────────────────────────────────────────────────────────────────┘
                               ↓
┌─────────────────────────────────────────────────────────────────────┐
│                STEP 7: WAIT FOR EXIT ORDER FILL                     │
│  ⏳ PendingOrderReconciler (needs exit fill detection)              │
│     - Query broker.getOrderStatus(exitOrderId)                      │
│     - Detect exit fill                                              │
│     - Calculate realized P&L                                        │
│     - Mark trade: status=CLOSED                                     │
└─────────────────────────────────────────────────────────────────────┘
                               ↓
┌─────────────────────────────────────────────────────────────────────┐
│                  STEP 8: TRADE CLOSURE                              │
│  ❌ NOT IMPLEMENTED                                                 │
│  Required:                                                          │
│     - Calculate realizedPnl = exitValue - entryValue                │
│     - Calculate holdingDays                                         │
│     - Update trade: exitPrice, exitTimestamp, realizedPnl           │
│     - Emit TRADE_CLOSED event                                       │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Summary: What Exists vs What's Missing

### ✅ What EXISTS and WORKS:

1. **Signal Broadcasting** - Fans out to all user-brokers ✅
2. **Validation per User-Broker** - All gates enforced ✅
3. **Trade Creation** - Proper single-writer pattern ✅
4. **Broker Adapter Infrastructure** - All 5 brokers have placeOrder() ✅
5. **Order Status Reconciliation** - 30s polling loop ✅
6. **Exit Condition Monitoring** - Target/SL/time checks on every tick ✅
7. **Position Tracking** - Queries DB, not HashMap ✅

### ❌ What's MISSING:

1. **GAP 1: Order Placement** - ExecutionOrchestrator doesn't call OrderExecutionService (30 min fix)
2. **GAP 2: NEWBUY vs REBUY** - tradeNumber hardcoded to 1 (2-3 hour fix)
3. **GAP 3: Exit Order Placement** - Exit signals don't place orders (4-6 hour fix)
4. **Trade Closure** - No P&L calculation on exit fill (2-3 hour fix)

**Total Fix Time:** 8-12 hours

---

## Critical Gaps Impact

### GAP 1: Order Placement (HIGHEST PRIORITY)
**Impact:** ❌ **ORDERS NEVER PLACED AT BROKER**

Even though:
- Signals are generated ✅
- Validation passes ✅
- Intents are created ✅
- Trades are created in DB ✅

**BUT:** Orders are NOT sent to broker! Only events are emitted.

**User Experience:**
- User sees intent approved
- Trade row created in database
- **But no order appears in broker terminal** ❌

---

### GAP 2: NEWBUY vs REBUY (MEDIUM PRIORITY)
**Impact:** ⚠️ **All trades treated as NEWBUY (tradeNumber=1)**

**Problem:**
- First position in INFY → tradeNumber=1 (correct)
- Add to INFY position → tradeNumber=1 (WRONG! Should be 2)
- Third INFY position → tradeNumber=1 (WRONG! Should be 3)

**Consequences:**
- Averaging validation gate NOT enforced
- Cannot distinguish new positions from additions
- Position size calculations may be incorrect

---

### GAP 3: Exit Order Placement (HIGH PRIORITY)
**Impact:** ❌ **EXIT ORDERS NEVER PLACED**

Even though:
- Trades fill and go OPEN ✅
- Exit conditions monitored on every tick ✅
- Exit signals generated when stop/target hit ✅

**BUT:** Exit orders are NOT sent to broker! Only events are emitted.

**User Experience:**
- Trade hits target price
- Exit signal emitted
- **But no exit order placed** ❌
- **Position stays open indefinitely** ❌

---

## Recommendations

### Quick Fix (30 minutes) - Get Orders Placing
Fix GAP 1 only:
- Wire ExecutionOrchestrator → OrderExecutionService
- Test: Signal → Intent → Order at broker

**Command:** "Fix GAP 1" or "Wire up order execution"

---

### Complete Fix (8-12 hours) - Full Lifecycle
Fix all 3 gaps:
1. Order placement (30 min)
2. NEWBUY vs REBUY (2-3 hours)
3. Exit order placement (4-6 hours)
4. Trade closure (2-3 hours)

**Command:** "Fix all gaps" or "Complete trade lifecycle"

---

### Test Without Fixing (5 minutes)
Test current flow:
```bash
# Generate signal via API
curl -X POST http://localhost:9090/api/signals -d {...}

# Check if:
# 1. Intents created? → YES ✅
# 2. Trades created? → YES ✅
# 3. Orders at broker? → NO ❌ (GAP 1)
```

**Command:** "Test current flow"

---

## Next Steps

**What would you like to do?**

1. **Fix GAP 1 immediately** (30 min) - Get orders placing
2. **Fix all gaps** (8-12 hours) - Complete lifecycle
3. **Test current state** first to see the gaps in action
4. **Something else?**

---

**My Recommendation:** Start with GAP 1 (30 minutes). This gives you orders placing at broker immediately, and you can test the rest incrementally.

**Tell me:** "Fix GAP 1" when ready!
