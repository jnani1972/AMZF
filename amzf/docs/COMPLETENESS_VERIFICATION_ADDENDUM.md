# COMPLETENESS VERIFICATION ADDENDUM
# AnnuPaper v04 - Enhanced Master List Sections

**Purpose:** Drop-in sections to enhance SYSTEM_CLASS_METHOD_MASTER.md with completeness verification
**Date:** January 13, 2026

---

## SECTION 8 (ENHANCED): COMPLETENESS VERIFICATION

### 8.1 ENGINE COMPLETENESS CARDS

For each engine, this section provides: Entry points, Outputs & consumers, Reachability proof, Failure & fallback.

---

#### 8.1.1 MARKET DATA ENGINE

**Entry Points:**
```
1. BrokerAdapter.onTick(tick)
   ├─ Registered in: FyersAdapter.connectWebSocket() [line 187]
   ├─ Callback: ws.onTextMessage() → processTickMessage() → onTick()
   └─ Calls: TickCandleBuilder.onTick() [service/candle/TickCandleBuilder.java:72]

2. ScheduledExecutor (candle finalizer)
   ├─ Registered in: App.java [line 342]
   ├─ Schedule: Every 2 seconds
   └─ Calls: TickCandleBuilder.finalizeStaleCandles()
```

**Outputs & Consumers:**
```
Output 1: Updated MarketDataCache
  └─ Consumers:
     ├─ HTTP API (AdminService.marketWatch) [reads LTP]
     ├─ MtfSignalGenerator.onTick() [checks price movement]
     └─ ExitSignalService.onTick() [checks exit conditions]

Output 2: Closed 1-min Candles
  └─ Consumers:
     ├─ CandleAggregator.on1MinuteCandleClose() [aggregates to 25/125-min]
     ├─ EventService.emitGlobal(CANDLE) [broadcasts to WS clients]
     └─ CandleStore.addIntraday() [persists to DB + memory]

Output 3: Aggregated 25/125-min Candles
  └─ Consumers:
     ├─ ConfluenceCalculator.analyze() [MTF analysis]
     ├─ PositionSizingService.calculatePositionSize() [ATR calc]
     └─ CandleStore.addIntraday() [persists]
```

**Reachability Proof:**
- ✅ Started in: App.java:360-370 (broker connection bootstrap)
- ✅ Callback registered: FyersAdapter.connectWebSocket() line 187
- ✅ Tick flow verified: Broker WS → onTick() → TickCandleBuilder
- ✅ Candle finalizer: Scheduled executor registered at App startup
- ✅ Consumers wired: All 3 outputs have active consumers

**Failure & Fallback:**
```
Failure: Broker WebSocket disconnects
  ├─ Detection: WatchdogManager.checkTickStream() [last tick > 5 min]
  ├─ Action: Auto-reconnect via BrokerAdapter.reconnect()
  └─ Fallback: DEGRADE (use previous DAILY close for LTP)

Failure: Database unavailable for candle persist
  ├─ Detection: SQLException on CandleStore.addIntraday()
  ├─ Action: Log error, continue processing
  └─ Fallback: DEGRADE (candles lost, memory cache intact)
     ⚠️ TODO: Buffer candles for retry

Failure: Candle aggregation fails (missing 1-min candles)
  ├─ Detection: Insufficient candles in bucket
  ├─ Action: Skip aggregation, log warning
  └─ Fallback: FAIL SAFE (no 25/125-min candle generated)
```

**Assessment:** ✅ **COMPLETE** - All entry points wired, all outputs consumed, reachability proven, failure modes defined.

---

#### 8.1.2 SIGNAL GENERATION ENGINE

**Entry Points:**
```
1. MtfSignalGenerator.onTick(tick)
   ├─ Registered in: TickCandleBuilder.onTick() [line 85]
   ├─ Condition: Price moved > 0.3% from last analysis
   └─ Calls: SignalService.analyzeAndGenerateSignal()

2. ScheduledExecutor (periodic analysis)
   ├─ Registered in: App.java [line 354]
   ├─ Schedule: Every 1 minute
   └─ Calls: MtfSignalGenerator.performSignalAnalysis()
```

**Outputs & Consumers:**
```
Output 1: Signal (persisted to DB)
  └─ Consumers:
     ├─ ExecutionOrchestrator.fanOutSignal() [validates per user]
     ├─ EventService.emitGlobal(SIGNAL_GENERATED) [broadcasts to WS]
     └─ SignalRepository [audit trail]

Output 2: ExitSignal (when exit triggered)
  └─ Consumers:
     ├─ TradeRepository [updates Trade status = CLOSED]
     ├─ EventService.emitUserBroker(SIGNAL_EXIT) [notifies user]
     └─ ExitSignalService.removeTrade() [stops monitoring]
```

**Reachability Proof:**
- ✅ Started in: App.java:354 (scheduled executor)
- ✅ Tick listener: Registered in TickCandleBuilder.onTick() line 85
- ✅ Signal flow verified: Tick → price check → analyzeAndGenerateSignal()
- ✅ Consumers wired: ExecutionOrchestrator.fanOutSignal() called at line 171

**Failure & Fallback:**
```
Failure: Confluence calculation fails (missing candles)
  ├─ Detection: CandleStore returns insufficient candles
  ├─ Action: Skip signal generation, log warning
  └─ Fallback: FAIL SAFE (no signal generated)

Failure: 3× Advantage gate rejects signal
  ├─ Detection: UtilityAsymmetryCalculator.passesAdvantageGate() = false
  ├─ Action: Signal NOT created, NOT persisted
  └─ Fallback: EXPECTED (constitutional gate working)

Failure: Signal DB persist fails
  ├─ Detection: SQLException on SignalRepository.insert()
  ├─ Action: Log error, DO NOT fan out
  └─ Fallback: FAIL SAFE (signal lost, no orders placed)
     ⚠️ Critical: DB is source of truth

Failure: Exit monitoring (mock HashMap)
  ├─ Detection: ExitSignalService uses in-memory HashMap
  ├─ Action: ❌ INCOMPLETE - Lost on restart
  └─ Fallback: ❌ NONE - P0 blocker
```

**Assessment:** ⚠️ **PARTIAL** - Signal generation complete, exit monitoring incomplete (mock HashMap).

---

#### 8.1.3 POSITION SIZING ENGINE

**Entry Points:**
```
1. ValidationService.validate()
   ├─ Called by: ExecutionOrchestrator.validateAndCreateIntent() [line 147]
   ├─ Context: Per user-broker validation during fan-out
   └─ Calls: PositionSizingService.calculatePositionSize()

2. PositionSizingService.calculateAddSize() (for averaging)
   ├─ Called by: ValidationService.validate() [if existing position]
   └─ Calls: AveragingGateValidator.validateAveragingGate()
```

**Outputs & Consumers:**
```
Output: SizingResult (quantity, constraint, rejected, rejectReason)
  └─ Consumers:
     ├─ ValidationService [creates TradeIntent with approved qty]
     ├─ TradeIntentRepository [persists sizing decision]
     └─ EventService.emitUserBroker(INTENT_APPROVED/REJECTED)
```

**Reachability Proof:**
- ✅ Started in: ExecutionOrchestrator.fanOutSignal() line 60
- ✅ Parallel validation: CompletableFuture per user-broker
- ✅ Sizing called: ValidationService.validate() line 147
- ✅ Consumers wired: Result used to create TradeIntent

**Failure & Fallback:**
```
Failure: ATR calculation fails (missing DAILY candles)
  ├─ Detection: CandleStore returns null/insufficient candles
  ├─ Action: Return SizingResult(rejected=true, reason="MISSING_ATR")
  └─ Fallback: FAIL SAFE (intent REJECTED)

Failure: Any constraint unavailable (portfolio data, existing positions)
  ├─ Detection: Null/exception during constraint calculation
  ├─ Action: Return SizingResult(rejected=true, reason="DATA_UNAVAILABLE")
  └─ Fallback: FAIL SAFE (intent REJECTED)

Failure: All constraints reject (qty = 0)
  ├─ Detection: MINIMUM of 7 constraints = 0
  ├─ Action: Return SizingResult(rejected=false, quantity=0, constraint=<reason>)
  └─ Fallback: EXPECTED (constitutional constraints working)

Failure: pWin hardcoded 65%
  ├─ Detection: SignalService.java line 416 (hardcoded)
  ├─ Action: ⚠️ INACCURATE Kelly sizing
  └─ Fallback: ⚠️ P1 TODO - Calculate from historical win rate
```

**Assessment:** ✅ **COMPLETE** - All 7 constraints implemented, deterministic, fail-safe. P1 TODO: pWin calculation.

---

#### 8.1.4 EXECUTION ENGINE

**Entry Points:**
```
1. ExecutionOrchestrator.fanOutSignal(signal)
   ├─ Called by: SignalService.generateAndProcess() [line 171]
   ├─ Context: After signal persisted to DB
   └─ Calls: validateAndCreateIntent() per user-broker (parallel)

2. ExecutionOrchestrator.executeApprovedIntents(intents)
   ├─ Called by: ❌ NOT CALLED - TODO
   ├─ Expected: After fan-out completes, filter APPROVED intents
   └─ Calls: executeIntent(intent) per approved intent
```

**Outputs & Consumers:**
```
Output 1: TradeIntent (APPROVED or REJECTED)
  └─ Consumers:
     ├─ TradeIntentRepository [persisted]
     ├─ EventService.emitUserBroker(INTENT_APPROVED/REJECTED)
     └─ ❌ ExecutionOrchestrator.executeApprovedIntents() - NOT WIRED

Output 2: Order (TODO - not created yet)
  └─ Consumers:
     ├─ ❌ BrokerAdapter.placeOrder() - NOT IMPLEMENTED
     ├─ ❌ EventService.emitUserBroker(ORDER_CREATED) - event only, no order
     └─ ❌ Order fill callback - NOT WIRED
```

**Reachability Proof:**
- ✅ Entry point 1: fanOutSignal() called from SignalService line 171
- ⚠️ Entry point 2: executeApprovedIntents() NOT CALLED (TODO)
- ❌ Order placement: NOT WIRED - P0 blocker

**Failure & Fallback:**
```
Failure: Validation timeout (5 seconds)
  ├─ Detection: CompletableFuture timeout
  ├─ Action: Create TradeIntent(REJECTED, reason="TIMEOUT")
  └─ Fallback: FAIL SAFE (intent rejected)

Failure: Order placement (NOT IMPLEMENTED)
  ├─ Detection: ❌ executeIntent() TODO at line 235
  ├─ Action: ❌ Only creates ORDER_CREATED event, no broker call
  └─ Fallback: ❌ NONE - P0 blocker

Failure: Broker order rejected
  ├─ Detection: ❌ NOT HANDLED - placeOrder() not implemented
  ├─ Action: ❌ TODO - Handle broker rejection
  └─ Fallback: ❌ TODO - Create ORDER_REJECTED event

Failure: Partial fill
  ├─ Detection: ❌ NOT HANDLED - fill callback not wired
  ├─ Action: ❌ TODO - Update Trade with partial qty
  └─ Fallback: ❌ TODO - Decision: cancel remaining or wait?
```

**Assessment:** ❌ **INCOMPLETE** - Intent creation works, order placement missing (P0 blocker).

---

#### 8.1.5 POSITION TRACKING ENGINE

**Entry Points:**
```
1. ExitSignalService.onTick()
   ├─ Registered in: TickCandleBuilder.onTick() [line 88]
   ├─ Context: Check exit conditions for open trades
   └─ Reads: openTrades HashMap (MOCK)

2. ExitSignalService.addTrade(trade)
   ├─ Called by: ❌ NOT CALLED - TODO (should be in order fill callback)
   ├─ Expected: After order fill, add to monitoring
   └─ Writes: openTrades HashMap
```

**Outputs & Consumers:**
```
Output: ExitSignal
  └─ Consumers:
     ├─ ❌ TradeRepository (should update status = CLOSED)
     ├─ EventService.emitUserBroker(SIGNAL_EXIT)
     └─ ExitSignalService.removeTrade() [stops monitoring]
```

**Reachability Proof:**
- ✅ Entry point 1: onTick() called from TickCandleBuilder line 88
- ❌ Entry point 2: addTrade() NOT CALLED (no fill callback)
- ❌ Trade loading: NO DB LOAD on startup (lost on restart)

**Failure & Fallback:**
```
Failure: Open trades lost on restart
  ├─ Detection: HashMap is in-memory only
  ├─ Action: ❌ Trades lost, no exit monitoring
  └─ Fallback: ❌ NONE - P0 blocker

Failure: Exit signal not persisted
  ├─ Detection: ExitSignal emitted but Trade status not updated
  ├─ Action: ❌ Trade remains OPEN in DB
  └─ Fallback: ❌ Inconsistent state - P0 blocker

Failure: Duplicate exit signals (same price)
  ├─ Detection: BrickMovementTracker.shouldAllowExit()
  ├─ Action: Reject exit if within brick threshold
  └─ Fallback: EXPECTED (dedupe working)
```

**Assessment:** ❌ **INCOMPLETE** - Exit logic exists, position tracking is mock HashMap (P0 blocker).

---

#### 8.1.6 BROADCAST ENGINE

**Entry Points:**
```
1. EventService.emitGlobal/emitUser/emitUserBroker()
   ├─ Called by: All engines (TickCandleBuilder, SignalService, etc.)
   ├─ Context: After persisting to DB
   └─ Calls: WsHub.publish(tradeEvent)

2. WsHub flusher thread (scheduled)
   ├─ Registered in: WsHub constructor [line 58]
   ├─ Schedule: Every 100ms
   └─ Calls: flushBatch()
```

**Outputs & Consumers:**
```
Output: WebSocket BATCH messages
  └─ Consumers:
     ├─ Frontend clients (subscribed to topics)
     ├─ Filtered by: WsSession.shouldReceive(event)
     └─ Delivered via: WebSockets.sendText()
```

**Reachability Proof:**
- ✅ Started in: WsHub constructor (daemon thread)
- ✅ Publisher entry: EventService.emit*() methods
- ✅ Enqueue: WsHub.publish() enqueues to BlockingQueue
- ✅ Flush: Daemon thread drains queue every 100ms
- ✅ Consumers: WS clients subscribe via /ws?token=<jwt>

**Failure & Fallback:**
```
Failure: Queue full (100K capacity)
  ├─ Detection: batchQueue.offer() returns false
  ├─ Action: Drop event (non-blocking)
  └─ Fallback: DEGRADE (event lost)
     ⚠️ TODO: Add backpressure mechanism

Failure: Flusher thread dies
  ├─ Detection: ❌ NOT MONITORED - TODO
  ├─ Action: ❌ Events enqueued but not sent
  └─ Fallback: ❌ TODO - Restart flusher thread

Failure: Client WebSocket disconnects
  ├─ Detection: Undertow onClose() callback
  ├─ Action: Remove from activeSessions, userChannels
  └─ Fallback: EXPECTED (cleanup automatic)

Failure: Broadcast send fails (client unreachable)
  ├─ Detection: WebSockets.sendText() exception
  ├─ Action: Log error, continue to next client
  └─ Fallback: DEGRADE (client misses event)
```

**Assessment:** ✅ **COMPLETE** - Batching works, scoped delivery correct. P2 TODO: Monitor flusher health.

---

#### 8.1.7 SELF-HEALING ENGINE

**Entry Points:**
```
1. WatchdogManager health check (scheduled)
   ├─ Registered in: App.java [line 378]
   ├─ Schedule: Every 2 minutes
   └─ Calls: performHealthCheck()
```

**Outputs & Consumers:**
```
Output: Health check results (6 systems)
  └─ Consumers:
     ├─ EventService.emitGlobal(WATCHDOG_ALERT) [on failure]
     ├─ Auto-healing actions (reconnect, clear cache, etc.)
     └─ Logs (for monitoring)
```

**Reachability Proof:**
- ✅ Started in: App.java:378 (scheduled executor)
- ✅ Checks 6 systems: DB, data broker, WS, OAuth, tick stream, candles
- ✅ Auto-healing: Reconnect on broker disconnect

**Failure & Fallback:**
```
Failure: Database check fails
  ├─ Detection: HikariCP pool validation query fails
  ├─ Action: Log error, emit WATCHDOG_ALERT
  └─ Fallback: ALERT (no auto-heal for DB)

Failure: Data broker disconnected
  ├─ Detection: Last tick > 5 min ago
  ├─ Action: Auto-reconnect via BrokerAdapter.reconnect()
  └─ Fallback: AUTO-HEAL (reconnect attempted)

Failure: WebSocket hub inactive
  ├─ Detection: activeSessions.isEmpty()
  ├─ Action: Log warning (no action needed)
  └─ Fallback: EXPECTED (no clients connected)

Failure: Tick stream stale
  ├─ Detection: Last tick timestamp > 5 min ago
  ├─ Action: Trigger broker reconnect
  └─ Fallback: AUTO-HEAL

Failure: Candles stale
  ├─ Detection: Last candle generated > 10 min ago
  ├─ Action: Trigger finalizeStaleCandles()
  └─ Fallback: AUTO-HEAL
```

**Assessment:** ✅ **COMPLETE** - All 6 systems monitored, auto-healing implemented.

---

### 8.2 PRODUCTION PATH TODO GATE (RELEASE BLOCKER)

This section defines TODOs that **BLOCK** "production-ready" status.

#### 8.2.1 P0 TODOs (Critical Path - MUST FIX)

| ID | File | Line | Issue | Impact | Release Gate |
|----|------|------|-------|--------|--------------|
| **P0-1** | ExecutionOrchestrator.java | 235 | `executeIntent()` missing `broker.placeOrder()` | **CRITICAL** - No actual orders placed | ❌ BLOCKS RELEASE |
| **P0-2** | ExitSignalService.java | 45-50 | Uses mock `HashMap<String, OpenTrade>` | **CRITICAL** - Exit monitoring lost on restart | ❌ BLOCKS RELEASE |
| **P0-3** | SignalRepository | N/A | Missing unique constraint on Signal | **HIGH** - Duplicate signals possible | ❌ BLOCKS RELEASE |

**Release Requirement:** All P0 TODOs must be resolved OR behind explicit guard.

#### 8.2.2 P0 TODO Guard Requirements

If a P0 TODO cannot be fixed immediately, it MUST be guarded by:

**Option 1: Feature Flag**
```java
if (config.isOrderExecutionEnabled()) {
    broker.placeOrder(...);
} else {
    throw new UnsupportedOperationException(
        "Order execution disabled by feature flag. " +
        "Enable 'order.execution.enabled' in config to place orders."
    );
}
```

**Option 2: Config Gate**
```java
if (brokerConfig.getOrderMode() == OrderMode.PAPER_TRADING) {
    log.info("Paper trading mode - order not placed");
    return mockOrderResponse();
} else {
    broker.placeOrder(...);
}
```

**Option 3: Explicit Exception**
```java
throw new UnsupportedOperationException(
    "Order execution not implemented. " +
    "TODO: Implement BrokerAdapter.placeOrder() integration. " +
    "See ExecutionOrchestrator.java:235"
);
```

**Forbidden:**
```java
// ❌ WRONG: Silent TODO (no error, no flag, no log)
// TODO: Place order
return;
```

---

#### 8.2.3 P1 TODOs (Important - Should Fix)

| ID | File | Line | Issue | Impact | Release Gate |
|----|------|------|-------|--------|--------------|
| **P1-1** | App.java | 263-266 | UserContext missing dailyLoss, weeklyLoss | MEDIUM - Risk limits not enforced | ⚠️ WARN |
| **P1-2** | SignalService.java | 416 | pWin hardcoded 65% | MEDIUM - Inaccurate Kelly sizing | ⚠️ WARN |
| **P1-3** | PositionSizingService.java | 129 | TODO: Track actual portfolio peak | MEDIUM - Drawdown calc conservative | ⚠️ WARN |
| **P1-4** | FyersAdapter.java | 38,74,153 | Using test API (api-t1.fyers.in) | MEDIUM - Production requires real API | ⚠️ WARN |

**Release Requirement:** P1 TODOs should be fixed, but can ship with warnings if documented.

---

#### 8.2.4 P2 TODOs (Nice-to-have - Can Ship)

| ID | File | Line | Issue | Impact | Release Gate |
|----|------|------|-------|--------|--------------|
| P2-1 | BrickMovementTracker.java | 99 | TODO: Load thresholds from config | LOW - Hardcoded brick thresholds | ✅ OK |
| P2-2 | WsHub.java | N/A | TODO: Monitor flusher thread health | LOW - No auto-restart | ✅ OK |
| P2-3 | EventService.java | N/A | TODO: Buffer candles on DB failure | LOW - Candles lost on DB fail | ✅ OK |

**Release Requirement:** P2 TODOs are enhancements, ship as-is.

---

### 8.3 SINGLE SOURCE OF TRUTH MAP (SSOT)

This section enforces "no duplication" by requiring exactly one owner per rule family.

| Rule Family | Owner Class | Package | Callers | Forbidden Duplicates |
|-------------|-------------|---------|---------|---------------------|
| **ATR Calculation** | ATRCalculator | service.signal | PositionSizingService, ConfluenceCalculator (future) | ❌ No manual ATR in PositionSizingService |
| **Confluence Zones** | ConfluenceCalculator | service.signal | SignalService | ❌ No zone detection in SignalService or MtfSignalGenerator |
| **Zone Detection (per TF)** | ZoneDetector | service.signal | ConfluenceCalculator | ❌ No Donchian/zone logic elsewhere |
| **Utility Gate (3× Advantage)** | UtilityAsymmetryCalculator | service.signal | SignalService | ❌ No U(π,ℓ) calculation elsewhere |
| **Position Sizing (7 Constraints)** | MtfPositionSizer | service.signal | PositionSizingService | ❌ No sizing logic in ValidationService |
| **Log-Loss Constraint (Position)** | LogUtilityCalculator | service.signal | MtfPositionSizer | ❌ No log-utility elsewhere |
| **Kelly Sizing** | KellyCalculator | service.signal | MtfPositionSizer | ❌ No Kelly formula elsewhere |
| **Portfolio Risk Budget** | PortfolioRiskCalculator | service.signal | MtfPositionSizer | ❌ No portfolio headroom elsewhere |
| **Velocity Throttle** | VelocityCalculator | service.signal | MtfPositionSizer | ❌ No Range/ATR calc elsewhere |
| **Averaging Gates** | AveragingGateValidator | service.signal | PositionSizingService | ❌ No rebuy logic elsewhere |
| **Rebuy Sizing** | PositionSizingService.calculateAddSize() | service.signal | ValidationService | ❌ No averaging in MtfPositionSizer |
| **Signal Deduplication** | ⚠️ NOT CONSOLIDATED | N/A | MtfSignalGenerator (price threshold), DB (missing unique constraint) | ⚠️ TODO: Add unique constraint on Signal table |
| **Exit Deduplication** | BrickMovementTracker | service.signal | ExitSignalService | ✅ Single source |
| **Timeframe Calculations** | SessionClock | service.candle | TickCandleBuilder, CandleAggregator | ❌ No time floor logic elsewhere |
| **1-Min Candle Building** | TickCandleBuilder | service.candle | BrokerAdapter callbacks | ❌ No candle logic in adapters |
| **25/125-Min Aggregation** | CandleAggregator | service.candle | Event-driven (onTick callback) | ❌ No aggregation elsewhere |
| **LTP Cache** | MarketDataCache | service | TickCandleBuilder (write), HTTP API (read) | ❌ No direct broker price reads |
| **Validation (12-Point)** | ValidationService | service.validation | ExecutionOrchestrator | ❌ No validation logic in SignalService |
| **Trade Intent Creation** | ExecutionOrchestrator | service.execution | SignalService | ❌ No intent creation elsewhere |
| **Event Scoping** | TradeEvent (domain model) | domain.model | EventService | ❌ No scope logic in WsHub |
| **WS Filtering** | WsSession.shouldReceive() | transport.ws | WsHub flusher | ❌ No filtering in EventService |

**Assessment:**
- ✅ **Most rules have single owner** - Good separation
- ⚠️ **Signal deduplication NOT consolidated** - Needs unique constraint
- ⚠️ **pWin calculation missing** - Hardcoded in SignalService

**Enforcement:**
- Code reviews MUST check this table before approving changes
- New calculation logic MUST update this table with single owner
- Duplicate implementations → REJECT PR

---

### 8.4 END-TO-END COMPLETION CHECKLIST

This section verifies the critical path: **Signal → Intent → Order → Trade → Exit**

---

#### 8.4.1 ORDER EXECUTION COMPLETION

**Where is executeApprovedIntents() called from?**
```
❌ NOT CALLED
Expected: After ExecutionOrchestrator.fanOutSignal() completes
Location: ExecutionOrchestrator.java line 220 (method exists, not wired)
```

**Where is executeIntent(intent) implemented?**
```
⚠️ PARTIAL IMPLEMENTATION
File: ExecutionOrchestrator.java line 235
Current: Only creates ORDER_CREATED event
Missing: broker.placeOrder() call
```

**What is the adapter method signature?**
```java
// Expected in BrokerAdapter interface:
OrderResponse placeOrder(
    String symbol,
    BigDecimal quantity,
    BigDecimal limitPrice,
    String productType,  // "INTRADAY" or "DELIVERY"
    String orderType,    // "LIMIT" or "MARKET"
    String clientOrderId // Idempotency key (intentId)
);

record OrderResponse(
    String orderId,        // Broker's order ID
    String status,         // "PLACED" | "REJECTED" | "FILLED"
    String rejectReason,   // If rejected
    Instant placedAt
);
```

**Where is idempotency enforced?**
```
⚠️ TODO - Plan:
1. Use intentId as clientOrderId in broker order
2. Broker adapter should dedupe on clientOrderId
3. On retry, broker returns existing order (not new order)
4. Store broker's orderId in Trade table for reconciliation
```

**How are retries handled without double-order?**
```
⚠️ TODO - Plan:
1. Check if Trade exists with intentId before placing order
2. If Trade exists and status = PENDING:
   - Query broker for order status by clientOrderId
   - Update Trade based on broker response
3. If Trade exists and status = OPEN/FILLED:
   - Skip order placement (already filled)
4. If no Trade:
   - Place order with clientOrderId = intentId
```

**Assessment:** ❌ **INCOMPLETE** - P0 blocker, requires implementation.

---

#### 8.4.2 FILL CALLBACKS COMPLETION

**How does broker callback reach system?**
```
⚠️ TODO - Design options:

Option 1: WebSocket callback (Fyers)
  ├─ FyersAdapter.connectWebSocket() subscribes to order updates
  ├─ Callback: ws.onTextMessage() → processOrderUpdate()
  └─ Calls: onOrderFill(orderId, filledQty, fillPrice, timestamp)

Option 2: Webhook (some brokers)
  ├─ POST /api/broker-callback/{userBrokerId}/order-fill
  ├─ Undertow handler validates signature
  └─ Calls: BrokerAdapterFactory.getAdapter().onOrderFill()

Option 3: Polling (fallback)
  ├─ ScheduledExecutor queries broker every 5 seconds
  ├─ For each PENDING order: getOrderStatus(orderId)
  └─ If filled: Call onOrderFill()

Recommendation: Option 1 (WS) with Option 3 (polling) as backup
```

**Where is fill event converted to immutable Trade?**
```java
// Expected in BrokerAdapter:
void onOrderFill(FillEvent fillEvent) {
    // 1. Find Trade by clientOrderId (intentId)
    Trade trade = tradeRepository.findByIntentId(fillEvent.clientOrderId());

    // 2. Update or create Trade
    if (trade == null) {
        trade = Trade.createFromFill(fillEvent);
    } else {
        trade = trade.withFill(fillEvent.filledQty(), fillEvent.fillPrice());
    }

    // 3. Persist
    tradeRepository.upsert(trade);

    // 4. Emit event
    eventService.emitUserBroker(EventType.ORDER_FILLED, trade, userBrokerId);

    // 5. Add to exit monitoring
    exitSignalService.addTrade(trade);
}
```

**Where is TradeRepository.insert/update performed?**
```
⚠️ TODO - Expected:
File: repository/PostgresTradeRepository.java
Method: upsert(Trade trade)
Logic:
  - INSERT ON CONFLICT (intent_id) DO UPDATE
  - Update: filled_qty, avg_fill_price, status, updated_at
```

**How do partial fills update position state?**
```
⚠️ TODO - Design decision needed:

Option 1: Update single Trade record
  ├─ Trade.filledQty increments on each partial fill
  ├─ Trade.status = PARTIALLY_FILLED until fully filled
  └─ Trade.status = OPEN when fully filled

Option 2: Create multiple Trade records (one per fill)
  ├─ Each fill creates new Trade linked by orderId
  ├─ Portfolio aggregates all fills for position
  └─ More audit detail, more complex queries

Recommendation: Option 1 (single Trade, update on partial fill)
```

**Assessment:** ❌ **INCOMPLETE** - P0 blocker, requires design + implementation.

---

#### 8.4.3 POSITION STATE MACHINE COMPLETION

**What is the canonical state machine?**
```
Trade Status States (REQUIRED):

PENDING → Order placed, waiting for fill
  ├─ Transition: After broker.placeOrder() returns orderId
  └─ Can transition to: FILLED, REJECTED, CANCELLED

FILLED → Order fully filled
  ├─ Transition: On fill callback (filledQty == orderedQty)
  └─ Can transition to: OPEN (if entry), CLOSED (if exit)

OPEN → Position opened, monitoring for exit
  ├─ Transition: After FILLED and added to ExitSignalService
  └─ Can transition to: CLOSED

CLOSED → Position exited, P&L realized
  ├─ Transition: On exit signal (target/stop/time)
  └─ Final state

REJECTED → Order rejected by broker
  ├─ Transition: On broker rejection
  └─ Final state

CANCELLED → Order cancelled by user/system
  ├─ Transition: On cancel request
  └─ Final state

Additional States (for averaging):
PYRAMIDING → Adding to existing position (rebuy in progress)
  ├─ Transition: During calculateAddSize() execution
  └─ Can transition to: OPEN (after rebuy fills)
```

**Which class owns the state machine?**
```
✅ SINGLE OWNER: Trade (domain model)

State transitions MUST be encapsulated in Trade methods:

class Trade {
    // Immutable record, updates return new instance

    public static Trade createPending(TradeIntent intent, String orderId);
    public Trade markFilled(BigDecimal filledQty, BigDecimal avgPrice);
    public Trade markOpen();
    public Trade markClosed(ExitReason reason, BigDecimal exitPrice);
    public Trade markRejected(String rejectReason);
}

❌ FORBIDDEN: State transitions in ExecutionOrchestrator, ExitSignalService
```

**Where are transitions executed?**
```
Transition triggers:

PENDING → FILLED:
  ├─ Trigger: BrokerAdapter.onOrderFill()
  ├─ Location: Order fill callback handler
  └─ Method: Trade.markFilled()

FILLED → OPEN:
  ├─ Trigger: After ExitSignalService.addTrade()
  ├─ Location: Fill callback handler (after adding to monitoring)
  └─ Method: Trade.markOpen()

OPEN → CLOSED:
  ├─ Trigger: ExitSignalService.onTick() detects exit condition
  ├─ Location: Exit signal handler
  └─ Method: Trade.markClosed()

PENDING → REJECTED:
  ├─ Trigger: Broker rejection response
  ├─ Location: Order placement error handler
  └─ Method: Trade.markRejected()
```

**Assessment:** ⚠️ **PARTIAL** - State machine defined, transitions NOT implemented.

---

#### 8.4.4 EXIT MONITORING COMPLETION

**Where do open trades come from on startup?**
```
❌ NOT IMPLEMENTED

Expected:
1. ExitSignalService.initialize() called on App startup
2. Query TradeRepository for OPEN trades
3. Load into monitoring (addTrade() for each)

Current:
- HashMap<String, OpenTrade> (mock, lost on restart)
```

**Is ExitSignalService reading DB or cache?**
```
❌ MOCK ONLY

Current: In-memory HashMap
Expected: Query DB on startup, then track in-memory for performance
```

**Where is exit order placed?**
```
⚠️ TODO - Design decision:

Option 1: Same ExecutionOrchestrator
  ├─ ExitSignalService.emitExitSignal() creates ExitIntent
  ├─ ExecutionOrchestrator.executeExitIntent() places order
  └─ Reuses same order placement logic

Option 2: Direct broker call (bypass orchestrator)
  ├─ ExitSignalService.onTick() directly calls broker.placeOrder()
  ├─ Faster execution (no validation delay)
  └─ Risk: No validation, no intent audit trail

Recommendation: Option 1 (reuse ExecutionOrchestrator for consistency)
```

**How is exit idempotency enforced?**
```
✅ PARTIAL - BrickMovementTracker

Current:
- BrickMovementTracker.shouldAllowExit() prevents duplicate exits at same price
- Brick threshold prevents rapid re-entry

Missing:
- Idempotency key for exit order (use tradeId as clientOrderId)
- Check if exit order already placed before placing new one
```

**Assessment:** ❌ **INCOMPLETE** - P0 blocker, requires DB-backed trade loading.

---

#### 8.4.5 E2E COMPLETION SUMMARY

| Component | Status | P0 Blocker | Assessment |
|-----------|--------|------------|------------|
| **Order Placement** | ❌ NOT IMPLEMENTED | YES | executeIntent() TODO |
| **Fill Callbacks** | ❌ NOT IMPLEMENTED | YES | No callback handler |
| **Trade State Machine** | ⚠️ PARTIAL | YES | States defined, transitions not wired |
| **Position Tracking** | ❌ NOT IMPLEMENTED | YES | Mock HashMap only |
| **Exit Monitoring** | ⚠️ PARTIAL | YES | Logic exists, DB load missing |
| **Exit Order Placement** | ❌ NOT IMPLEMENTED | YES | No exit executor |
| **Idempotency (Orders)** | ⚠️ PARTIAL | YES | Intent exists, broker dedupe missing |
| **Idempotency (Exits)** | ✅ DONE | NO | BrickMovementTracker works |

**Overall E2E Status:** ❌ **NOT PRODUCTION READY** - 6 of 8 components incomplete.

---

## SECTION 13: CONSISTENCY ISSUES & RESOLUTIONS

This section addresses consistency issues found during verification.

### 13.1 "Zero Hot-Path DB Writes" vs EventService Persistence

**Issue:**
- Documentation claims "Zero hot-path DB writes (in-memory tick cache)"
- BUT: EventService.emitGlobal(TICK) includes `TradeEventRepository.append()` (sync DB write)
- This blocks the broker thread on every tick

**Current State:**
```java
// EventService.java
private TradeEvent persistAndBroadcast(TradeEvent e) {
    TradeEvent persisted = repo.append(e);  // ⚠️ BLOCKS broker thread (1-10ms)
    wsHub.publish(persisted);               // Fast (enqueue)
    return persisted;
}
```

**Questions to Answer:**
1. Is `emitGlobal(TICK)` enabled in production?
2. Are tick events persisted or only candle/signal/intents?
3. What is the definition of "hot path"?

**Resolution:**
```
DECISION: "Hot path" = tick PRICE caching, NOT tick EVENT persistence

Clarification:
✅ Tick prices: Zero DB writes (MarketDataCache only)
⚠️ Tick events: Optional DB persistence (for audit, replay)

Production Config:
- TICK events: Disabled by default (no DB write)
- CANDLE events: Enabled (persisted)
- SIGNAL events: Enabled (persisted)
- INTENT/ORDER events: Enabled (persisted)

If TICK events enabled:
- Known bottleneck (1-10ms DB write blocks broker thread)
- Future fix: Move event persistence to async thread
```

**Documentation Update:**
```
"Zero hot-path DB writes for tick PRICES (in-memory MarketDataCache).
Tick EVENT persistence is optional and can block broker thread if enabled."
```

---

### 13.2 "10-Point Gating" vs Actual 12-13 Checks

**Issue:**
- Documentation says "10-point per-user-broker validation"
- Actual implementation has 12-13 checks

**Current State:**
```
ValidationService.validate() performs:
 1. Broker enabled & connected
 2. Portfolio not paused
 3. Symbol allowed (watchlist)
 4. Has triple confluence
 5. pWin >= 35%
 6. kelly >= 2%
 7. [SIZING] PositionSizingService (7 constraints)
 8. Qty >= 1
 9. Value >= ₹1000
10. Value <= maxPerTrade
11. Capital constraints (exposure, log-loss)
12. Daily/weekly loss limits (TODO: not calculated)
13. Not in cooldown
```

**Resolution:**
```
DECISION: Rename to "12-Point Gating" with 3 categories

Hard Checks (1-6): Basic filters
Sizing Gate (7): Constitutional 7-constraint sizing
Soft Checks (8-13): Value/capital/limits

Documentation:
"12-point per-user-broker validation with constitutional sizing gate"
```

**Documentation Update:**
```markdown
### Validation Flow (12-Point Gating)

**Hard Checks (1-6):**
- Broker enabled, portfolio active, symbol allowed
- Confluence requirements, probability thresholds

**Constitutional Sizing Gate (7):**
- PositionSizingService.calculatePositionSize()
- Returns MINIMUM of 7 constraints

**Soft Checks (8-13):**
- Minimum qty/value, max per trade
- Capital exposure, log-loss budgets
- Daily/weekly loss limits (TODO)
- Cooldown periods
```

---

### 13.3 ExitSignalService Uses Mock HashMap

**Issue:**
- ExitSignalService.openTrades is in-memory HashMap
- Trades lost on system restart
- Not backed by TradeRepository

**Current State:**
```java
// ExitSignalService.java line 45
private final Map<String, OpenTrade> openTrades = new HashMap<>();

public void onTick() {
    for (OpenTrade trade : openTrades.values()) {
        // Check exit conditions
    }
}
```

**Resolution:**
```
DECISION: Replace with DB-backed trade loader

Implementation Plan:
1. Add ExitSignalService.initialize() called on App startup
2. Query TradeRepository.findByStatus(TradeStatus.OPEN)
3. Load into ConcurrentHashMap for performance
4. On exit: Update Trade in DB, remove from map
5. Add periodic sync (every 1 min) to catch missed updates

Code:
private final ConcurrentHashMap<String, Trade> openTrades = new ConcurrentHashMap<>();

public void initialize() {
    List<Trade> openTrades = tradeRepository.findByStatus(TradeStatus.OPEN);
    for (Trade trade : openTrades) {
        this.openTrades.put(trade.tradeId(), trade);
    }
    log.info("Loaded {} open trades for exit monitoring", openTrades.size());
}
```

**Assessment:** ❌ P0 blocker - Must implement before production.

---

### 13.4 Signal Deduplication Not Enforceable

**Issue:**
- Time/price thresholds exist (0.3% movement, 1-min interval)
- But NO unique constraint on Signal table
- Same signal could be generated twice if price bounces

**Current State:**
```
Signal Deduplication:
✅ MtfSignalGenerator.lastAnalyzedPrice (0.3% threshold)
✅ MtfSignalGenerator.lastAnalysisTime (1 min interval)
❌ No DB unique constraint

Risk: Signal duplicates if:
1. Price moves 0.3%+, signal generated
2. Price drops back within same minute
3. Scheduled analysis re-analyzes same symbol
```

**Resolution:**
```sql
-- Add unique constraint to Signal table
CREATE UNIQUE INDEX idx_signal_dedupe ON signals (
    symbol,
    confluence_type,
    DATE(generated_at),  -- Same calendar day
    effective_floor,     -- Same zone
    effective_ceiling
);

-- On conflict: Return existing signal (idempotent)
INSERT INTO signals (...) VALUES (...)
ON CONFLICT (symbol, confluence_type, DATE(generated_at), effective_floor, effective_ceiling)
DO UPDATE SET last_checked_at = NOW()
RETURNING *;
```

**Assessment:** ❌ P0 blocker - Add constraint before production.

---

### 13.5 Idempotency Not Enforced for Order Execution

**Issue:**
- ExecutionOrchestrator.fanOutSignal() creates intents (idempotent per signal × user-broker)
- BUT: Order placement has no idempotency key
- Retry could create duplicate orders

**Current State:**
```
TradeIntent has intentId (unique per signal × user-broker)
BUT: executeIntent() doesn't use it as clientOrderId
```

**Resolution:**
```
DECISION: Use intentId as broker's clientOrderId

Implementation:
1. Pass intentId to broker.placeOrder(clientOrderId = intentId)
2. Broker adapter must dedupe on clientOrderId
3. On retry: Broker returns existing order (not new order)
4. Store broker's orderId in Trade for reconciliation

Code:
OrderResponse placeOrder(
    String symbol,
    BigDecimal quantity,
    BigDecimal limitPrice,
    String clientOrderId  // ← intentId (idempotency key)
);

// In executeIntent():
String clientOrderId = intent.intentId();
OrderResponse response = broker.placeOrder(
    intent.symbol(),
    intent.approvedQty(),
    intent.signal().entryPrice(),
    clientOrderId
);
```

**Assessment:** ❌ P0 blocker - Required for production safety.

---

## SECTION 14: RISK PROFILE BUNDLES

This section defines where risk profiles (Conservative/Balanced/Aggressive) apply in the system.

### 14.1 Profile Scope

**Where Profiles Apply:**
- ✅ Per-user validation (ValidationService)
- ✅ Per-user sizing (PositionSizingService)
- ❌ NEVER in global signal generation (signals are GLOBAL)

**Profile Storage:**
```
Option 1: RiskProfileId on UserBroker
- Each user-broker can have different profile
- Example: User has Conservative on Zerodha, Aggressive on Fyers

Option 2: RiskProfileId on User (global)
- One profile per user across all brokers
- Simpler, less flexible

Recommendation: Option 1 (per user-broker)
```

### 14.2 Profile Parameters

**Profile Definition:**
```java
record RiskProfile(
    String profileId,         // "CONSERVATIVE" | "BALANCED" | "AGGRESSIVE"

    // Capital allocation
    BigDecimal maxSymbolCapitalPct,    // % of total capital per symbol
    BigDecimal maxPortfolioExposure,   // % of total capital in open positions

    // Constitutional constraints
    BigDecimal maxPortfolioLogLoss,    // e_port_max
    BigDecimal maxSymbolLogLoss,       // e_sym_max
    BigDecimal maxPositionLogLoss,     // L_pos

    // Signal filtering
    ConfluenceType minConfluence,      // TRIPLE | DOUBLE | SINGLE
    BigDecimal minWinProb,             // pWin threshold (e.g., 0.35)
    BigDecimal minKellyFraction,       // Kelly threshold (e.g., 0.02)

    // Averaging/rebuy
    int maxPyramidLevel,               // Max rebuys per position
    BigDecimal rebuySpacingATR,        // Min ATR distance for rebuy

    // Throttling
    BigDecimal velocityMultiplier,     // Velocity throttle adjustment
    BigDecimal stressThrottleBase,     // Stress throttle base

    // Time-based limits
    Duration cooldownPeriod,           // Min time between trades
    Duration maxHoldTime,              // Max time to hold position

    // Loss limits
    BigDecimal maxDailyLossPct,        // Daily loss limit (% of capital)
    BigDecimal maxWeeklyLossPct        // Weekly loss limit (% of capital)
);
```

### 14.3 Example Profiles

**Conservative:**
```java
RiskProfile.CONSERVATIVE = new RiskProfile(
    profileId = "CONSERVATIVE",
    maxSymbolCapitalPct = 0.05,        // 5% per symbol
    maxPortfolioExposure = 0.30,       // 30% total exposure
    maxPortfolioLogLoss = 0.15,        // 15% portfolio budget
    maxSymbolLogLoss = 0.10,           // 10% per symbol
    maxPositionLogLoss = 0.08,         // 8% per position
    minConfluence = TRIPLE,            // Require triple confluence
    minWinProb = 0.40,                 // 40% win probability
    minKellyFraction = 0.03,           // 3% Kelly
    maxPyramidLevel = 3,               // Max 3 rebuys
    rebuySpacingATR = 2.0,             // 2 ATR spacing
    velocityMultiplier = 0.7,          // 70% velocity
    cooldownPeriod = Duration.ofHours(4),
    maxHoldTime = Duration.ofDays(7),
    maxDailyLossPct = 0.02,            // 2% daily loss limit
    maxWeeklyLossPct = 0.05            // 5% weekly loss limit
);
```

**Balanced:**
```java
RiskProfile.BALANCED = new RiskProfile(
    profileId = "BALANCED",
    maxSymbolCapitalPct = 0.08,        // 8% per symbol
    maxPortfolioExposure = 0.50,       // 50% total exposure
    maxPortfolioLogLoss = 0.25,        // 25% portfolio budget
    maxSymbolLogLoss = 0.15,           // 15% per symbol
    maxPositionLogLoss = 0.12,         // 12% per position
    minConfluence = DOUBLE,            // Double confluence OK
    minWinProb = 0.35,                 // 35% win probability
    minKellyFraction = 0.02,           // 2% Kelly
    maxPyramidLevel = 5,               // Max 5 rebuys
    rebuySpacingATR = 1.5,             // 1.5 ATR spacing
    velocityMultiplier = 1.0,          // 100% velocity
    cooldownPeriod = Duration.ofHours(2),
    maxHoldTime = Duration.ofDays(5),
    maxDailyLossPct = 0.03,            // 3% daily loss limit
    maxWeeklyLossPct = 0.08            // 8% weekly loss limit
);
```

**Aggressive:**
```java
RiskProfile.AGGRESSIVE = new RiskProfile(
    profileId = "AGGRESSIVE",
    maxSymbolCapitalPct = 0.12,        // 12% per symbol
    maxPortfolioExposure = 0.70,       // 70% total exposure
    maxPortfolioLogLoss = 0.35,        // 35% portfolio budget
    maxSymbolLogLoss = 0.20,           // 20% per symbol
    maxPositionLogLoss = 0.15,         // 15% per position
    minConfluence = SINGLE,            // Single confluence OK
    minWinProb = 0.30,                 // 30% win probability
    minKellyFraction = 0.01,           // 1% Kelly
    maxPyramidLevel = 10,              // Max 10 rebuys
    rebuySpacingATR = 1.0,             // 1 ATR spacing
    velocityMultiplier = 1.3,          // 130% velocity
    cooldownPeriod = Duration.ofHours(1),
    maxHoldTime = Duration.ofDays(3),
    maxDailyLossPct = 0.05,            // 5% daily loss limit
    maxWeeklyLossPct = 0.12            // 12% weekly loss limit
);
```

### 14.4 Profile Application

**Where Profile Parameters Are Used:**

| Parameter | Applied In | How |
|-----------|-----------|-----|
| maxSymbolCapitalPct | MtfPositionSizer | Constraint #4 (CAPITAL) |
| maxPortfolioExposure | ValidationService | Check exposure before approval |
| maxPortfolioLogLoss | MtfPositionSizer | Constraint #5 (PORTFOLIO_BUDGET) |
| maxSymbolLogLoss | MtfPositionSizer | Constraint #6 (SYMBOL_BUDGET) |
| maxPositionLogLoss | LogUtilityCalculator | Constraint #1 (LOG_SAFE) |
| minConfluence | ValidationService | CHECK #4 (has triple confluence) |
| minWinProb | ValidationService | CHECK #5 (pWin >= threshold) |
| minKellyFraction | ValidationService | CHECK #6 (kelly >= threshold) |
| maxPyramidLevel | AveragingGateValidator | Gate #2 (pyramid limit) |
| rebuySpacingATR | AveragingGateValidator | Gate #1 (distance check) |
| velocityMultiplier | VelocityCalculator | Constraint #7 (VELOCITY) |
| cooldownPeriod | ValidationService | CHECK #13 (not in cooldown) |
| maxDailyLossPct | ValidationService | CHECK #12 (daily loss limit) |
| maxWeeklyLossPct | ValidationService | CHECK #12 (weekly loss limit) |

### 14.5 Profile vs Constitutional Invariants

**Constitutional Invariants (NEVER vary by profile):**
- ✅ 3× Advantage Law (U(π, ℓ) / π >= 3.0)
- ✅ 7-Constraint Minimum (always return MIN)
- ✅ Single Data Broker
- ✅ Immutable Snapshots
- ✅ GLOBAL Signal Scope

**Profile-Driven Parameters (CAN vary):**
- ✅ Confluence requirement (TRIPLE vs DOUBLE vs SINGLE)
- ✅ Capital allocation (symbol %, portfolio %)
- ✅ Log-loss budgets (e_port_max, e_sym_max, L_pos)
- ✅ Rebuy rules (pyramid levels, spacing)
- ✅ Time limits (cooldown, max hold)
- ✅ Loss limits (daily, weekly)

**Guideline:**
- If it affects **system correctness** → Constitutional invariant
- If it affects **risk appetite** → Profile parameter

---

## SECTION 15: 7-LINE COMPLETENESS QUESTIONNAIRE

Use this format in code reviews to verify any class/method completeness.

### 15.1 The 7-Line Format

For any important class or method, ask the developer to answer:

```
1. ENTRY: Who calls it? (exact method + file:line)
2. INPUTS: What inputs are required? What happens if missing?
3. READS: What state is read? Who owns it?
4. WRITES: What state is written? Where?
5. EVENTS: What is emitted? Who consumes it?
6. IDEMPOTENCY: What prevents duplicates on retry/restart?
7. FAILURE: What happens when dependency fails?
```

If they can't answer any line → ❌ Code is partial/unsafe.

---

### 15.2 Example: TickCandleBuilder.onTick()

**Class:** TickCandleBuilder
**Method:** `void onTick(BrokerAdapter.Tick tick)`

```
1. ENTRY:
   - Called by: BrokerAdapter.onTick() [FyersAdapter.java:124]
   - Registered: FyersAdapter.connectWebSocket() line 187 (WS callback)

2. INPUTS:
   - Required: Tick(symbol, lastPrice, volume, timestamp)
   - If null: NPE (no guard) ⚠️ TODO: Add validation
   - If price <= 0: Should reject ⚠️ TODO: Add validation

3. READS:
   - partialCandles.get(symbol) [own state, CHM]
   - No external state

4. WRITES:
   - MarketDataCache.updateTick() [CHM.put() atomic]
   - partialCandles.put() [in-place mutation, single thread]
   - CandleStore.addIntraday() [on minute boundary, DB + memory]
   - WatchdogManager.recordTick() [CHM.put()]

5. EVENTS:
   - EventService.emitGlobal(TICK) [optional, can block broker thread]
   - EventService.emitGlobal(CANDLE) [on minute boundary]
   - Consumers: WsHub (broadcasts), CandleAggregator (aggregates)

6. IDEMPOTENCY:
   - ❌ NO - Tick reprocessing adds duplicate volume
   - TODO: Dedupe by (symbol, timestamp) before processing

7. FAILURE:
   - DB unavailable: Log error, continue (candle lost) ⚠️ DEGRADE
   - WS queue full: Event dropped (non-blocking) ⚠️ DEGRADE
   - Broker disconnect: WatchdogManager detects, auto-reconnects ✅ AUTO-HEAL
```

**Assessment:** ✅ Mostly complete, ⚠️ Needs input validation, ⚠️ No tick dedupe.

---

### 15.3 Example: MtfPositionSizer.calculatePositionSize()

**Class:** MtfPositionSizer
**Method:** `SizingResult calculatePositionSize(...)`

```
1. ENTRY:
   - Called by: PositionSizingService.calculatePositionSize() [line 87]
   - Called by: ValidationService.validate() [line 147]
   - Context: Per user-broker validation during fan-out

2. INPUTS:
   - Required: Signal, UserBroker, availableCash, existingPositions, config
   - If null Signal: NPE ⚠️ Should validate
   - If missing candles: Return REJECTED("MISSING_ATR") ✅ FAIL SAFE

3. READS:
   - CandleStore (for DAILY candles, ATR calc)
   - LogUtilityCalculator (constraint #1)
   - KellyCalculator (constraint #2)
   - PortfolioRiskCalculator (constraint #5)
   - VelocityCalculator (constraint #7)

4. WRITES:
   - ❌ NONE - Pure function, returns SizingResult

5. EVENTS:
   - ❌ NONE - No events emitted

6. IDEMPOTENCY:
   - ✅ YES - Pure function, deterministic given inputs

7. FAILURE:
   - ATR calculation fails: Return REJECTED("MISSING_ATR") ✅ FAIL SAFE
   - Portfolio data unavailable: Return REJECTED("DATA_UNAVAILABLE") ✅ FAIL SAFE
   - Any constraint throws exception: Caught, return REJECTED ✅ FAIL SAFE
```

**Assessment:** ✅ COMPLETE - Stateless, deterministic, fail-safe on all failures.

---

### 15.4 Example: ExecutionOrchestrator.executeIntent()

**Class:** ExecutionOrchestrator
**Method:** `void executeIntent(TradeIntent intent)`

```
1. ENTRY:
   - Called by: ❌ NOT CALLED - TODO
   - Expected: executeApprovedIntents() should call this
   - File: ExecutionOrchestrator.java line 235

2. INPUTS:
   - Required: TradeIntent (APPROVED status)
   - If REJECTED: Should skip ⚠️ TODO: Add check

3. READS:
   - ❌ TODO: Should read Trade to check if already executed

4. WRITES:
   - ❌ TODO: Should create Trade(status=PENDING)
   - ⚠️ Currently: Only emits ORDER_CREATED event (no actual order)

5. EVENTS:
   - EventService.emitUserBroker(ORDER_CREATED)
   - Consumers: WsHub (broadcasts to user-broker)
   - ⚠️ Missing: ORDER_FILLED event (no fill callback)

6. IDEMPOTENCY:
   - ❌ NO - Retry would create duplicate orders
   - ⚠️ TODO: Use intentId as clientOrderId in broker order

7. FAILURE:
   - Broker order rejected: ❌ NOT HANDLED
   - Broker API unavailable: ❌ NOT HANDLED
   - Order timeout: ❌ NOT HANDLED
```

**Assessment:** ❌ INCOMPLETE - P0 blocker, requires full implementation.

---

### 15.5 Completeness Scoring

For each 7-line answer, score:
- **2 points** if fully answered with code references
- **1 point** if partial (TODO acknowledged with plan)
- **0 points** if unanswered or "don't know"

**Max score:** 14 points per method

**Interpretation:**
- **14/14:** ✅ Production-grade
- **11-13:** ⚠️ Usable but has gaps
- **7-10:** ⚠️ Partial implementation
- **0-6:** ❌ Incomplete / unsafe

---

## DOCUMENT UPDATES REQUIRED

To incorporate this addendum into SYSTEM_CLASS_METHOD_MASTER.md:

1. **Replace Section 8** with enhanced "8.1-8.4" from this document
2. **Add Section 13** (Consistency Issues) after Section 12
3. **Add Section 14** (Risk Profile Bundles) after Section 13
4. **Add Section 15** (7-Line Questionnaire) after Section 14
5. **Update Section 1.1** to clarify "Zero hot-path DB writes" (tick prices vs events)
6. **Update Section 8.1** to change "10-point gating" to "12-point gating"

---

**Document Version:** 2.0 (Addendum)
**Date:** January 13, 2026
**Next Review:** After P0 fixes (order execution, position tracking, signal dedupe)
