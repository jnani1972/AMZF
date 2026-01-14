# VERIFICATION WALKTHROUGH CHECKLIST
# AnnuPaper v04 - Practical Code Review Guide

**Purpose:** Use this checklist during code walkthroughs to verify system correctness end-to-end
**Based on:** SYSTEM_CLASS_METHOD_MASTER.md
**Date:** January 13, 2026

---

## HOW TO USE THIS CHECKLIST

### For Each Engine/Module:

1. **Ask the developer to trace ONE complete flow** (e.g., "Show me tick → candle → signal → intent → order")
2. **For each class in the flow, ask the 5-line format:**
   - **Inputs:** What comes in?
   - **State read:** What does it read?
   - **Computation:** What does it do?
   - **Outputs:** What comes out?
   - **State write + idempotency:** What does it persist? Can it be called twice safely?

3. **Verify against this checklist**

---

## SECTION 1: TICK INGESTION & MARKET DATA

### 1.1 Tick Entry Point

**File:** `BrokerAdapter` implementations (FyersAdapter, etc.)

☐ **Q1.1:** What tick fields arrive from broker WebSocket?
   - Expected: `symbol, lastPrice, volume, timestamp, bid, ask`
   - Verify: No missing fields that could cause NPE

☐ **Q1.2:** What is the expected tick rate (per symbol, total)?
   - Expected: ~1-5 ticks/sec per symbol, ~500 symbols = ~2500 ticks/sec
   - Verify: Buffer sizes sufficient

☐ **Q1.3:** How do we handle out-of-order ticks?
   - Expected: Timestamp-based ordering OR accept as-is
   - Verify: No assumption of strict ordering

☐ **Q1.4:** How do we handle duplicate ticks (same timestamp)?
   - Expected: Accept last (overwrite) OR dedupe by timestamp
   - Verify: No double-counting in candle volume

☐ **Q1.5:** What happens if tick stream stops?
   - Expected: WatchdogManager detects (last tick > 5 min ago)
   - Verify: Alert emitted, auto-reconnect attempted

---

### 1.2 Tick Normalization

**File:** `TickCandleBuilder.onTick()`

☐ **Q2.1:** Where do we normalize price units (paise → rupees)?
   - Expected: In broker adapter before passing to TickCandleBuilder
   - Verify: All prices in consistent units

☐ **Q2.2:** What validations are applied before accepting tick?
   - Expected: Non-null checks, price > 0, volume >= 0
   - Verify: Invalid ticks rejected with log

☐ **Q2.3:** What is logged when a tick is rejected?
   - Expected: Reason code, symbol, raw tick data
   - Verify: Can debug rejected ticks from logs

---

### 1.3 Candle Building

**File:** `TickCandleBuilder`

☐ **Q3.1:** Trace the flow: Tick → 1-min candle → 25-min → 125-min
   ```
   BrokerAdapter.onTick(tick)
     → TickCandleBuilder.onTick()
       → update1MinuteCandle() [in-place mutation]
       → [Minute boundary] close1MinuteCandle()
         → CandleStore.addIntraday()
         → CandleAggregator.on1MinuteCandleClose()
           → aggregate(MINUTE_25)
           → aggregate(MINUTE_125)
   ```
   - Verify: Each step is called
   - Verify: Candles are persisted to DB

☐ **Q3.2:** What is the candle start time calculation logic?
   - Expected: `floorToMinute(timestamp)` for 1-min
   - Expected: `floorToIntervalFromSessionStart()` for 25/125-min
   - Verify: Alignment with market session start (9:15 AM IST)

☐ **Q3.3:** What happens if minute boundary is missed (tick gap)?
   - Expected: `finalizeStaleCandles()` closes old partial candles
   - Verify: Scheduled every 2 seconds to catch gaps

☐ **Q3.4:** Are partial candles thread-safe?
   - Expected: Single writer (broker thread only)
   - Verify: No concurrent mutations from HTTP threads

---

### 1.4 Market Data Cache

**File:** `MarketDataCache`

☐ **Q4.1:** What is the 3-tier LTP fallback strategy?
   - **Tier 1:** Real-time cache (`CHM.get(symbol)`)
   - **Tier 2:** Previous day close (DB query)
   - **Tier 3:** Null fallback
   - Verify: Each tier is tried in order

☐ **Q4.2:** Is MarketDataCache thread-safe?
   - Expected: `ConcurrentHashMap<String, TickData>`
   - Expected: TickData is immutable (Java record)
   - Verify: CHM.put() replaces entire object atomically

☐ **Q4.3:** What happens if cache is empty (market closed)?
   - Expected: Fallback to Tier 2 (DB query for DAILY close)
   - Verify: API still returns valid response (with previous close)

---

## SECTION 2: SIGNAL GENERATION & CONFLUENCE

### 2.1 Multi-Timeframe Analysis

**File:** `ConfluenceCalculator`

☐ **Q5.1:** What are the 4 timeframes and their purposes?
   - **DAILY:** 1 day (375 trading mins), historical analysis
   - **HTF:** 125 minutes, higher timeframe trend (50% weight)
   - **ITF:** 25 minutes, intermediate timeframe (30% weight)
   - **LTF:** 1 minute, lower timeframe precision (20% weight)
   - Verify: Weights sum to 100%

☐ **Q5.2:** What is the exact trigger moment for signal analysis?
   - Expected: MtfSignalGenerator.onTick() when price moved > 0.3%
   - Expected: OR scheduled analysis every 1 minute
   - Verify: Not analyzing on every tick (performance)

☐ **Q5.3:** What is the lookback requirement for each timeframe?
   - Expected: HTF=175 candles, ITF=75 candles, LTF=375 candles
   - Verify: Sufficient candles available before signal generation

☐ **Q5.4:** What happens if candles are not ready?
   - Expected: Skip signal generation, log warning
   - Verify: No signal generated with incomplete data

---

### 2.2 Zone Detection

**File:** `ZoneDetector`

☐ **Q6.1:** What is a "zone" and how is it calculated?
   - Expected: Buy zone = [floor, entry], Sell zone = [entry, ceiling]
   - Expected: Based on Donchian channels or similar
   - Verify: Zone boundaries are deterministic

☐ **Q6.2:** What is the "truth table" for confluence types?
   - **TRIPLE:** HTF + ITF + LTF all in buy zone
   - **DOUBLE:** Any 2 timeframes in buy zone
   - **SINGLE:** Only 1 timeframe in buy zone
   - **NONE:** 0 timeframes in buy zone
   - Verify: Logic matches this table

---

### 2.3 Constitutional Gate (3× Advantage)

**File:** `UtilityAsymmetryCalculator`

☐ **Q7.1:** What is the 3× advantage law formula?
   ```
   π = ln(ceiling / entry)  [profit potential]
   ℓ = ln(floor / entry)    [loss potential]
   U(π, ℓ) = utility function
   Requirement: U(π, ℓ) / π >= 3.0×
   ```
   - Verify: Formula implemented correctly
   - Verify: Default threshold is 3.0× (configurable)

☐ **Q7.2:** When is this gate applied?
   - Expected: BEFORE sizing, BEFORE fan-out
   - Expected: In SignalService.analyzeAndGenerateSignal()
   - Verify: Rejected signals do NOT fan out to users

☐ **Q7.3:** What happens if signal fails 3× advantage gate?
   - Expected: Signal NOT created, NOT persisted, NOT broadcast
   - Expected: Log reason: "INSUFFICIENT_ADVANTAGE"
   - Verify: No DB write, no WS broadcast

---

### 2.4 Signal De-duplication

**File:** `MtfSignalGenerator`, `BrickMovementTracker`

☐ **Q8.1:** How do we prevent duplicate signal generation?
   - **Mechanism 1:** Price threshold (0.3% movement required)
   - **Mechanism 2:** Time threshold (1 min between analyses)
   - **Mechanism 3:** Brick movement filter for exits
   - Verify: All 3 mechanisms active

☐ **Q8.2:** What is missing for full deduplication?
   - Expected: Unique constraint on Signal table
   - Expected: `UNIQUE(symbol, confluence_type, generated_at::date, effective_floor, effective_ceiling)`
   - Verify: TODO to add this constraint

---

## SECTION 3: POSITION SIZING (CONSTITUTIONAL)

### 3.1 7-Constraint Minimum

**File:** `MtfPositionSizer.calculatePositionSize()`

☐ **Q9.1:** What are the 7 constraints (in priority order)?
   1. **LOG_SAFE:** Position-level log-loss constraint (floor safety)
   2. **KELLY:** Kelly criterion × confluence multiplier
   3. **FILL:** Kelly qty × fill probability (pFill)
   4. **CAPITAL:** Available cash / entry price
   5. **PORTFOLIO_BUDGET:** Portfolio-level log-loss headroom
   6. **SYMBOL_BUDGET:** Symbol-level log-loss headroom
   7. **VELOCITY:** Velocity throttle (Range/ATR × kelly)
   - Verify: Returns MINIMUM of all 7

☐ **Q9.2:** Trace constraint #1 (LOG_SAFE):
   ```
   LogUtilityCalculator.calculateMaxLogSafeQty()
     → Input: floor, entry, L_pos (max position log-loss)
     → Formula: qty = VALUE / entry such that ln(floor / entry) >= L_pos
     → Output: Maximum safe quantity at given floor
   ```
   - Verify: Implementation matches formula

☐ **Q9.3:** Trace constraint #2 (KELLY):
   ```
   KellyCalculator.calculate()
     → Input: pWin, avgWin, avgLoss
     → Formula: K = (pWin × avgWin - (1-pWin) × avgLoss) / avgWin
     → Multiply by confluence factor:
       - TRIPLE: 1.0×
       - DOUBLE: 0.7×
       - SINGLE: 0.4×
     → Output: Kelly fraction of capital
   ```
   - Verify: pWin is NOT hardcoded 65% (TODO to fix)
   - Verify: Confluence multiplier applied

☐ **Q9.4:** Trace constraint #5 (PORTFOLIO_BUDGET):
   ```
   PortfolioRiskCalculator.calculatePortfolioHeadroom()
     → Input: e_port_max, current portfolio log-loss
     → Formula: headroom = e_port_max - current_loss
     → Output: Max qty such that new_trade_loss <= headroom
   ```
   - Verify: Rejects if portfolio budget exhausted

☐ **Q9.5:** What is the output format of sizing?
   ```java
   record SizingResult(
       BigDecimal quantity,
       String constraintApplied,  // Which constraint was minimum
       boolean rejected,
       String rejectReason
   )
   ```
   - Verify: constraintApplied is logged for debugging

---

### 3.2 Averaging / Rebuy Logic

**File:** `PositionSizingService.calculateAddSize()`, `AveragingGateValidator`

☐ **Q10.1:** What is the entry point for rebuy signals?
   - Expected: MtfSignalGenerator detects lower price signal
   - Expected: Call `calculateAddSize()` instead of `calculatePositionSize()`
   - Verify: Existing position is REQUIRED

☐ **Q10.2:** What are the averaging gate constraints?
   - **Gate 1:** Distance from entry >= ATR × threshold
   - **Gate 2:** Max pyramid level not exceeded (default 10)
   - **Gate 3:** Velocity/stress not too high
   - **Gate 4:** Capital availability for margin
   - Verify: All gates applied in AveragingGateValidator

☐ **Q10.3:** What happens if averaging is rejected?
   - Expected: Return qty = 0, reason code
   - Expected: TradeIntent created with REJECTED status
   - Verify: No order placed

☐ **Q10.4:** How is position identity maintained for averaging?
   - **TODO:** Clarify: Same Trade with `tradeNumber++` OR new Trade linked by `signalId`?
   - Verify: Document the actual implementation

---

## SECTION 4: VALIDATION & FAN-OUT

### 4.1 Signal Fan-Out

**File:** `ExecutionOrchestrator.fanOutSignal()`

☐ **Q11.1:** What is the fan-out mechanism?
   ```
   SignalService.generateAndProcess(signal)
     → ExecutionOrchestrator.fanOutSignal(signal)
       → Get all active EXEC brokers (parallel)
       → For each user-broker:
         → validateAndCreateIntent() [CompletableFuture]
           → ValidationService.validate()
           → Create TradeIntent (APPROVED or REJECTED)
           → TradeIntentRepository.insert()
   ```
   - Verify: Parallel execution with 5-sec timeout

☐ **Q11.2:** What is the timeout for validation?
   - Expected: 5 seconds per user-broker
   - Verify: Timeout results in REJECTED intent with timeout reason

☐ **Q11.3:** How many intents are created per signal?
   - Expected: M signals × N active EXEC brokers = M×N intents
   - Verify: Each user-broker gets exactly one intent per signal

---

### 4.2 Per-User Validation (10-Point)

**File:** `ValidationService.validate()`

☐ **Q12.1:** What are the 10 validation checks (in order)?
   1. Broker enabled & connected
   2. Portfolio not paused
   3. Symbol allowed (watchlist)
   4. Has triple confluence
   5. pWin >= 35%
   6. kelly >= 2%
   7. **[SIZING]** PositionSizingService.calculatePositionSize()
   8. Qty >= 1
   9. Value >= ₹1000
   10. Value <= maxPerTrade
   11. Capital constraints (exposure, log-loss)
   12. Daily/weekly loss limits
   13. Not in cooldown
   - Verify: All checks implemented

☐ **Q12.2:** What is the output of validation?
   ```java
   record ValidationResult(
       boolean passed,
       BigDecimal approvedQty,
       List<ValidationError> errors
   )
   ```
   - Verify: If any check fails, `passed = false` with reason codes

☐ **Q12.3:** What is missing in validation?
   - **TODO:** Daily/weekly loss limits not calculated in UserContext
   - Verify: Acknowledge this is a known gap

---

## SECTION 5: ORDER EXECUTION & POSITION TRACKING

### 5.1 Order Placement (CRITICAL GAP)

**File:** `ExecutionOrchestrator.executeIntent()`

☐ **Q13.1:** Is order placement implemented?
   - **Expected:** ❌ NO - TODO at line 235
   - **Current:** Only creates ORDER_CREATED event, does NOT call broker
   - Verify: Acknowledge this is P0 blocker

☐ **Q13.2:** What SHOULD the flow be?
   ```
   ExecutionOrchestrator.executeApprovedIntents(intents)
     → For each APPROVED intent:
       → executeIntent(intent)
         → [TODO] BrokerAdapter.placeOrder(symbol, qty, limitPrice, productType)
         → Handle order response (orderId, status)
         → EventService.emitUserBroker(ORDER_CREATED)
         → [Async] Broker fill callback
           → Create Trade (OPEN)
           → TradeRepository.insert()
           → EventService.emitUserBroker(ORDER_FILLED)
   ```
   - Verify: Flow is documented, implementation pending

☐ **Q13.3:** What is the idempotency key for orders?
   - Expected: `intentId` as unique order reference
   - Expected: Broker adapter should dedupe on retry
   - Verify: Plan for idempotency exists

---

### 5.2 Position Tracking (CRITICAL GAP)

**File:** `ExitSignalService`

☐ **Q14.1:** Is position tracking implemented?
   - **Expected:** ⚠️ PARTIAL - Uses mock in-memory HashMap
   - **Current:** `openTrades: HashMap<String, OpenTrade>` (not DB-backed)
   - Verify: Acknowledge this is P0 blocker

☐ **Q14.2:** What SHOULD the implementation be?
   ```
   ExitSignalService.onTick()
     → Query TradeRepository for OPEN trades
     → For each trade:
       → Check exit conditions (target, stop, time)
       → If triggered:
         → Update Trade status = CLOSED
         → Calculate realized P&L
         → EventService.emitUserBroker(SIGNAL_EXIT)
   ```
   - Verify: Flow is documented, implementation pending

☐ **Q14.3:** What happens on system restart?
   - **Current:** Mock HashMap loses all open trades
   - **Expected:** Load OPEN trades from DB on startup
   - Verify: Restart recovery plan exists

---

### 5.3 Exit Signal Generation

**File:** `ExitSignalService.onTick()`

☐ **Q15.1:** What are the 3 exit conditions?
   1. **TARGET_HIT:** currentPrice >= trade.targetPrice
   2. **STOP_LOSS:** currentPrice <= trade.stopLossPrice
   3. **TIME_BASED:** now > trade.entryTime + maxHoldTime
   - Verify: All 3 conditions checked on every tick

☐ **Q15.2:** How do we prevent duplicate exit signals?
   - **Mechanism:** BrickMovementTracker.shouldAllowExit()
   - **Logic:** Don't allow exit if price within same "brick" (threshold)
   - Verify: Prevents rapid re-entry at same exit level

☐ **Q15.3:** What is the exit signal flow?
   ```
   ExitSignalService.onTick()
     → For each OPEN trade:
       → Check exit conditions
       → If exit triggered:
         → BrickMovementTracker.shouldAllowExit()
         → If allowed:
           → emitExitSignal()
           → Trade status → CLOSED
           → EventService.emitUserBroker(SIGNAL_EXIT)
           → Calculate realized P&L
   ```
   - Verify: Each step is implemented

---

## SECTION 6: EVENT BROADCAST & WEBSOCKET

### 6.1 Event Scopes

**File:** `EventService`, `WsHub`

☐ **Q16.1:** What are the 3 event scopes?
   - **GLOBAL:** All authenticated users (TICK, CANDLE, SIGNAL_GENERATED)
   - **USER:** Specific user only (portfolio updates, P&L)
   - **USER_BROKER:** Specific user+broker combo (INTENT_APPROVED, ORDER_FILLED)
   - Verify: Each event type has correct scope

☐ **Q16.2:** How is scope enforced in broadcast?
   ```
   WsSession.shouldReceive(event)
     → Check topic subscription
     → Switch on event.scope():
       GLOBAL → return true
       USER → return event.userId == session.userId
       USER_BROKER → return event.userId == session.userId && event.userBrokerId in session.brokerIds
   ```
   - Verify: Filtering logic correct

---

### 6.2 Batching & Performance

**File:** `WsHub`

☐ **Q17.1:** What is the batching mechanism?
   - **Queue:** LinkedBlockingQueue (100K capacity)
   - **Interval:** 100ms flush interval
   - **Max per batch:** 2000 events
   - Verify: Flusher thread drains queue every 100ms

☐ **Q17.2:** What happens if queue is full?
   - Expected: BlockingQueue.offer() returns false (non-blocking)
   - Expected: Drop event (TODO: backpressure)
   - Verify: No blocking on enqueue

☐ **Q17.3:** What is the threading model?
   - **I/O Threads (Undertow):** Handle WS connections, messages
   - **Broker Threads:** Emit events to queue
   - **Flusher Thread (daemon):** Drains queue, broadcasts
   - Verify: No blocking between threads

---

## SECTION 7: THREAD SAFETY VERIFICATION

### 7.1 Concurrent Data Structures

☐ **Q18.1:** Is MarketDataCache thread-safe?
   - Storage: `ConcurrentHashMap<String, TickData>`
   - TickData: Immutable Java record
   - Update: CHM.put() atomic
   - Verify: ✅ SAFE

☐ **Q18.2:** Are partial candles thread-safe?
   - Storage: `ConcurrentHashMap<String, Map<TF, PartialCandle>>`
   - PartialCandle: Mutable BUT only broker thread writes
   - Pattern: Single-writer, multiple-reader
   - Verify: ✅ SAFE (single-threaded)

☐ **Q18.3:** Is WsHub thread-safe?
   - `activeSessions`: ConcurrentHashMap
   - `userChannels`: CHM with CHM.newKeySet() for set values
   - `WsSession.topics`: CHM.newKeySet()
   - Verify: ✅ SAFE (all concurrent collections)

☐ **Q18.4:** Are there any HashMap (non-thread-safe) in hot paths?
   - Expected: NO - All hot paths use ConcurrentHashMap
   - Verify: Grep for `new HashMap<>()` in service/candle, service/signal

---

### 7.2 Immutability Verification

☐ **Q19.1:** Which domain objects are immutable?
   - **Candle** (Java record)
   - **Signal** (immutable fields)
   - **TradeIntent** (immutable snapshot)
   - **TradeEvent** (persisted, no mutations)
   - **TickData** (Java record)
   - Verify: No setter methods on these classes

☐ **Q19.2:** Are there any mutable objects shared across threads?
   - Expected: PartialCandle (mutable BUT single writer)
   - Expected: All others are immutable or thread-local
   - Verify: No shared mutable state without synchronization

---

## SECTION 8: COMPLETENESS VERIFICATION

### 8.1 Wiring Completeness

☐ **Q20.1:** Trace one complete flow end-to-end (with actual file/method references):
   ```
   FyersAdapter.onTick() [broker/adapters/FyersAdapter.java:124]
     → TickCandleBuilder.onTick() [service/candle/TickCandleBuilder.java:72]
       → MarketDataCache.updateTick() [service/MarketDataCache.java:45]
       → update1MinuteCandle() [TickCandleBuilder.java:102]
       → close1MinuteCandle() [TickCandleBuilder.java:145]
         → CandleStore.addIntraday() [service/candle/CandleStore.java:78]
         → EventService.emitGlobal(CANDLE) [service/core/EventService.java:63]
         → CandleAggregator.on1MinuteCandleClose() [service/candle/CandleAggregator.java:55]
           → aggregate(MINUTE_25) [CandleAggregator.java:89]
           → aggregate(MINUTE_125) [CandleAggregator.java:89]
     → MtfSignalGenerator.onTick() [service/signal/MtfSignalGenerator.java:67]
       → SignalService.analyzeAndGenerateSignal() [service/signal/SignalService.java:132]
         → ConfluenceCalculator.analyze() [service/signal/ConfluenceCalculator.java:48]
         → UtilityAsymmetryCalculator.passesAdvantageGate() [service/signal/UtilityAsymmetryCalculator.java:25]
         → SignalRepository.insert() [repository/PostgresSignalRepository.java:42]
         → ExecutionOrchestrator.fanOutSignal() [service/execution/ExecutionOrchestrator.java:60]
           → validateAndCreateIntent() [ExecutionOrchestrator.java:134]
             → ValidationService.validate() [service/validation/ValidationService.java:39]
               → MtfPositionSizer.calculatePositionSize() [service/signal/MtfPositionSizer.java:50]
             → TradeIntentRepository.insert() [repository/PostgresTradeIntentRepository.java:38]
   ```
   - Verify: Each method is actually called (not dead code)
   - Verify: No missing links in chain

---

### 8.2 TODO/FIXME Audit

☐ **Q21.1:** Run grep for TODO/FIXME in critical paths:
   ```bash
   grep -r "TODO\|FIXME\|HACK\|XXX" src/main/java/in/annupaper/service/signal/
   grep -r "TODO\|FIXME\|HACK\|XXX" src/main/java/in/annupaper/service/execution/
   ```
   - List all TODOs with severity assessment
   - Verify: P0 TODOs are acknowledged blockers

☐ **Q21.2:** What are the P0 blockers?
   - **P0-1:** ExecutionOrchestrator.executeIntent() - Order placement not implemented
   - **P0-2:** ExitSignalService - Uses mock HashMap, not DB trades
   - **P0-3:** Signal deduplication - Missing unique constraint
   - Verify: All acknowledged in SYSTEM_CLASS_METHOD_MASTER.md

---

### 8.3 Duplicate Logic Check

☐ **Q22.1:** Is ATR calculated in one place only?
   - Expected: ATRCalculator.calculate() [single source]
   - Verify: No duplicate ATR logic in PositionSizingService

☐ **Q22.2:** Is confluence analysis in one place only?
   - Expected: ConfluenceCalculator.analyze() [single source]
   - Verify: No duplicate zone detection logic

☐ **Q22.3:** Is position sizing in one place only?
   - Expected: MtfPositionSizer.calculatePositionSize() [single source]
   - Verify: No duplicate sizing logic in ValidationService

---

### 8.4 Dead Code Check

☐ **Q23.1:** Are there any unused classes?
   - **DistributionAnalyzer:** LIKELY UNUSED (no grep references)
   - **MaxDropCalculator:** LIKELY UNUSED (limited references)
   - Verify: Confirm if these are dead code

☐ **Q23.2:** Are there any feature flags always off/on?
   - Expected: Check MTFConfig for unused flags
   - Verify: Remove dead feature flags

---

## SECTION 9: FAILURE MODES & RECOVERY

### 9.1 Tick Stream Failures

☐ **Q24.1:** What happens if broker WebSocket disconnects?
   - Expected: WatchdogManager detects (no ticks > 5 min)
   - Expected: Auto-reconnect attempted
   - Verify: Reconnection logic exists in BrokerAdapter

☐ **Q24.2:** What happens if ticks are delayed (backpressure)?
   - Expected: Broker thread blocks on DB write (known issue)
   - Expected: TODO: Move DB write to async thread
   - Verify: Bottleneck acknowledged in ARCHITECTURE.md

---

### 9.2 Database Failures

☐ **Q25.1:** What happens if PostgreSQL is unavailable?
   - Expected: HikariCP connection pool throws SQLException
   - Expected: WatchdogManager detects DB failure
   - Verify: Graceful degradation (no crash)

☐ **Q25.2:** What happens if candle persist fails?
   - Expected: Log error, candle lost (TODO: buffer)
   - Verify: System continues processing (no crash)

---

### 9.3 WebSocket Failures

☐ **Q26.1:** What happens if WS flusher thread dies?
   - Expected: Events enqueued but not sent
   - Expected: TODO: Monitor flusher thread health
   - Verify: Known gap, plan to fix

☐ **Q26.2:** What happens if client WebSocket disconnects?
   - Expected: Auto-cleanup from activeSessions map
   - Verify: No memory leak (sessions removed)

---

## SECTION 10: OBSERVABILITY & DEBUGGING

### 10.1 Correlation IDs

☐ **Q27.1:** Can we trace one tick → signal → intent → order?
   - Expected: Correlation ID passed through events
   - Verify: Logs contain correlation ID for tracing

☐ **Q27.2:** What log line proves each stage happened?
   - Tick received: `TickCandleBuilder.onTick()`
   - Candle closed: `TickCandleBuilder.close1MinuteCandle()`
   - Signal generated: `SignalService.generateAndProcess()`
   - Intent created: `ExecutionOrchestrator.validateAndCreateIntent()`
   - Verify: Each stage has log line

---

### 10.2 Metrics & Alerts

☐ **Q28.1:** What metrics exist?
   - Tick lag (time between tick timestamp and processing)
   - Candle lag (time between candle close and aggregation)
   - Signals emitted (count per symbol)
   - Qualification rejects (count by reason code)
   - Verify: Metrics exposed for monitoring

☐ **Q28.2:** What alerts exist?
   - Tick stream stopped (last tick > 5 min ago)
   - DB connection lost
   - WebSocket flusher stopped
   - Verify: Alerts configured in WatchdogManager

---

## SECTION 11: KILL SWITCHES & SAFETY

### 11.1 Emergency Controls

☐ **Q29.1:** What are the kill switches?
   - **Global disable trading:** Pause all portfolios
   - **Per-user disable:** Set user.status = PAUSED
   - **Per-symbol disable:** Remove from watchlist
   - Verify: Each kill switch is respected in validation

☐ **Q29.2:** Can we stop all order placement immediately?
   - Expected: Set global flag, ValidationService.validate() rejects all
   - Verify: Emergency stop mechanism exists

---

## FINAL VERIFICATION SCORECARD

### Per-Engine Checklist

| Engine | Wired? | Complete? | Thread-Safe? | TODOs? | Score |
|--------|--------|-----------|--------------|--------|-------|
| **Market Data** | ☐ | ☐ | ☐ | ☐ | /10 |
| **Signal Generation** | ☐ | ☐ | ☐ | ☐ | /10 |
| **Position Sizing** | ☐ | ☐ | ☐ | ☐ | /10 |
| **Validation** | ☐ | ☐ | ☐ | ☐ | /10 |
| **Execution** | ☐ | ☐ | ☐ | ☐ | /10 |
| **Position Tracking** | ☐ | ☐ | ☐ | ☐ | /10 |
| **Broadcast** | ☐ | ☐ | ☐ | ☐ | /10 |
| **Self-Healing** | ☐ | ☐ | ☐ | ☐ | /10 |

**Overall System Score:** ___/80

---

## USAGE INSTRUCTIONS

1. **Print this checklist** or open side-by-side with code
2. **Pick one engine** (e.g., Market Data)
3. **Ask developer to walk through** each section
4. **Check each ☐** as verified
5. **Note any gaps** in "Notes" column
6. **Score each engine** (0-10 scale)
7. **Repeat for all engines**

When done, you will have:
- ✅ Complete understanding of system
- ✅ List of TODOs prioritized
- ✅ Confidence in thread safety
- ✅ Verified end-to-end flows

---

**Document Version:** 1.0
**Last Updated:** January 13, 2026
**Next Review:** After each code walkthrough session
