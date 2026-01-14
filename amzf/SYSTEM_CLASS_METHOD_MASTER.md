# SYSTEM CLASS METHOD MASTER LIST
# AnnuPaper v04 - Constitutional Trading System

**Date:** January 13, 2026
**Version:** 0.4.0
**Status:** Architecture verification complete
**Total Classes:** 114 Java files

---

## 1. SYSTEM OVERVIEW

### 1.1 Architecture Type
**Multi-timeframe Constitutional Position Sizing System**
- Event-driven, immutable snapshots, lock-free concurrency
- Multi-tenant (user × broker × strategy)
- Zero hot-path DB writes (in-memory tick cache)

### 1.2 Critical Pipeline
```
TICK → 1-MIN CANDLE → AGGREGATION (25/125-MIN) → MTF CONFLUENCE
→ CONSTITUTIONAL GATE (3× advantage) → SIGNAL (GLOBAL)
→ FAN-OUT → PER-USER VALIDATION → 7-CONSTRAINT SIZING
→ TRADE INTENT → ORDER EXECUTION → POSITION TRACKING
```

### 1.3 System Invariants (CONSTITUTIONAL)

1. **3× Advantage Law**: Signal rejected if U(π, ℓ) / π < 3.0× (utility asymmetry)
2. **7-Constraint Minimum**: Position size = MIN(LOG_SAFE, KELLY, FILL, CAPITAL, PORT_BUDGET, SYM_BUDGET, VELOCITY)
3. **Single Data Broker**: Only one `is_data_broker=true` broker provides tick stream
4. **Immutable Snapshots**: Candle, Signal, Trade are immutable records
5. **GLOBAL Signal Scope**: One signal broadcast to all users; qualification per user-broker
6. **No Loss Exit**: TODO - Implement smart exit before stop loss hit

---

## 2. ENGINE-WISE CLASS INDEX

### 2.1 MARKET DATA ENGINE

| Class | Package | Role | Owns State? | Thread Model |
|-------|---------|------|-------------|--------------|
| **MarketDataCache** | service | In-memory LTP cache | ✅ `latestTicks: CHM<String, TickData>` | Broker thread writes, HTTP threads read |
| **TickCandleBuilder** | service.candle | Ingests ticks, builds 1-min candles | ✅ `partialCandles: CHM<String, Map<TF, PartialCandle>>` | Broker thread only (single writer) |
| **CandleStore** | service.candle | Dual storage (memory + DB) | ✅ `cache: CHM<CacheKey, Candle>` | Single writer (broker thread) |
| **CandleAggregator** | service.candle | Aggregates 1-min → 25/125-min | ❌ Stateless | Broker thread (event-driven) |
| **CandleFetcher** | service.candle | Downloads historical from broker API | ❌ | Async thread pool |
| **HistoryBackfiller** | service.candle | Detects gaps, triggers async backfill | ❌ | Scheduled executor |
| **MtfBackfillService** | service.candle | Ensures MTF candles exist | ❌ | Scheduled executor |
| **SessionClock** | service.candle | Market session time calculations | ❌ Stateless | Any thread (pure functions) |
| **RecoveryManager** | service.candle | Recovers from tick gaps | ❌ | Manual trigger |

**Data Flow:**
```
Broker Tick → TickCandleBuilder.onTick()
  → MarketDataCache.updateTick() [CHM.put]
  → update1MinuteCandle() [in-place mutable, single thread]
  → [Minute boundary] close1MinuteCandle()
    → CandleStore.addIntraday() [memory + DB]
    → EventService.emitGlobal(CANDLE)
    → CandleAggregator.on1MinuteCandleClose()
      → aggregate(MINUTE_25)
      → aggregate(MINUTE_125)
```

---

### 2.2 SIGNAL GENERATION ENGINE

| Class | Package | Role | Owns State? | Deterministic? |
|-------|---------|------|-------------|----------------|
| **SignalService** | service.signal | Orchestrates signal generation | ❌ | Yes (given inputs) |
| **MtfSignalGenerator** | service.signal | Tick-based signal analysis trigger | ✅ `lastAnalyzedPrice: CHM` | Yes |
| **ConfluenceCalculator** | service.signal | Multi-timeframe zone analysis | ❌ | Yes |
| **ZoneDetector** | service.signal | Buy/sell zone calculation per TF | ❌ | Yes |
| **UtilityAsymmetryCalculator** | service.signal | Constitutional gate (3× advantage) | ❌ | Yes |
| **BrickMovementTracker** | service.signal | Exit signal deduplication | ✅ `lastExitPrices: CHM` | Yes |
| **ExitSignalService** | service.signal | Exit trigger detection (target/stop/time) | ⚠️ `openTrades: HashMap` (MOCK) | Yes |

**Signal Generation Flow:**
```
MtfSignalGenerator.onTick() [price moved > 0.3%]
  → SignalService.analyzeAndGenerateSignal(symbol, price)
    → ConfluenceCalculator.analyze(symbol, price)
      ├─ calculateZones() [fetch HTF/ITF/LTF candles]
      ├─ Determine confluence type (NONE/SINGLE/DOUBLE/TRIPLE)
      └─ Return TimeframeAnalysis
    → [GATE 1] UtilityAsymmetryCalculator.passesAdvantageGate()
      └─ Reject if U(π, ℓ) / π < 3.0×
    → Create Signal (GLOBAL)
    → SignalRepository.insert()
    → EventService.emitGlobal(SIGNAL_GENERATED)
    → ExecutionOrchestrator.fanOutSignal()
```

---

### 2.3 POSITION SIZING ENGINE (CONSTITUTIONAL)

| Class | Package | Role | Owns State? | Deterministic? |
|-------|---------|------|-------------|----------------|
| **PositionSizingService** | service.signal | High-level orchestrator | ❌ | Yes (stateless) |
| **MtfPositionSizer** | service.signal | Core sizing (7 constraints) | ❌ | Yes |
| **LogUtilityCalculator** | service.signal | Log-loss constraint (position level) | ❌ | Yes |
| **PortfolioRiskCalculator** | service.signal | Portfolio risk budgets | ❌ | Yes |
| **VelocityCalculator** | service.signal | Velocity throttle (Range/ATR) | ❌ | Yes |
| **ATRCalculator** | service.signal | Average True Range | ❌ | Yes |
| **PortfolioValueTracker** | service.signal | Portfolio value tracking | ❌ | Queries DB |
| **KellyCalculator** | service.signal | Kelly criterion sizing | ❌ | Yes |
| **AveragingGateValidator** | service.signal | Rebuy/averaging constraints | ❌ | Yes |
| **MaxDropCalculator** | service.signal | Portfolio max drawdown | ❌ | Queries DB |
| **DistributionAnalyzer** | service.signal | Monte Carlo stress testing | ❌ | UNUSED? |

**Sizing Constraint Flow:**
```
ValidationService.validate()
  → PositionSizingService.calculatePositionSize(signal, userBroker, userContext)
    → Fetch DAILY candles for ATR calculation
    → MtfPositionSizer.calculatePositionSize()
      ├─ [C1] LOG_SAFE: LogUtilityCalculator.calculateMaxLogSafeQty()
      ├─ [C2] KELLY: KellyCalculator.calculate() × confluence multiplier
      ├─ [C3] FILL: kelly_qty × pFill (weighted by fill probability)
      ├─ [C4] CAPITAL: (availableCash - reservedCapital) / entryPrice
      ├─ [C5] PORTFOLIO_BUDGET: PortfolioRiskCalculator.calculatePortfolioHeadroom()
      ├─ [C6] SYMBOL_BUDGET: Calculate symbol-level log-loss headroom
      ├─ [C7] VELOCITY: VelocityCalculator.calculateFinalVelocity() × kelly_qty
      └─ Return MINIMUM(C1, C2, C3, C4, C5, C6, C7)
```

---

### 2.4 VALIDATION & EXECUTION ENGINE

| Class | Package | Role | Owns State? | Deterministic? |
|-------|---------|------|-------------|----------------|
| **ValidationService** | service.validation | 10-point per-user-broker validation | ❌ | Yes (stateless) |
| **ExecutionOrchestrator** | service.execution | Signal fan-out & intent creation | ❌ | Yes |

**Validation Flow (10-Point Gating):**
```
ExecutionOrchestrator.fanOutSignal(signal)
  → For each EXEC broker (parallel CompletableFuture):
    → validateAndCreateIntent(signal, userBroker)
      → ValidationService.validate(signal, userBroker, userContext)
        ├─ [CHECK 1] Broker enabled & connected
        ├─ [CHECK 2] Portfolio not paused
        ├─ [CHECK 3] Symbol allowed (watchlist)
        ├─ [CHECK 4] Has triple confluence
        ├─ [CHECK 5] pWin >= MIN_WIN_PROB (35%)
        ├─ [CHECK 6] kelly >= MIN_KELLY (2%)
        ├─ [SIZING] PositionSizingService.calculatePositionSize()
        ├─ [CHECK 7] Qty >= MIN_QTY (1)
        ├─ [CHECK 8] Value >= MIN_VALUE (₹1000)
        ├─ [CHECK 9] Value <= broker.maxPerTrade()
        ├─ [CHECK 10] Capital constraints (exposure, log-loss)
        ├─ [CHECK 11] Daily/weekly loss limits (TODO: not calculated)
        ├─ [CHECK 12] Not in cooldown period
        └─ Return ValidationResult (passed/failed, errors, qty)
      → Create TradeIntent (APPROVED or REJECTED)
      → TradeIntentRepository.insert()
      → EventService.emitUserBroker(INTENT_APPROVED/REJECTED)
```

---

### 2.5 TRANSPORT & BROADCAST ENGINE

| Class | Package | Role | Owns State? | Thread Model |
|-------|---------|------|-------------|--------------|
| **WsHub** | transport.ws | Undertow WebSocket hub | ✅ `activeSessions: CHM<Channel, WsSession>` | I/O threads + flusher |
| **EventService** | service.core | Event persistence & broadcast | ❌ | Any thread (broker, HTTP) |
| **WsSession** | transport.ws | Per-client state | ✅ `topics: CHM.newKeySet()` | I/O threads |
| **TradeEvent** | domain.model | Immutable event for DB | ❌ | Immutable |

**Event Scopes:**
- **GLOBAL**: All authenticated users (TICK, CANDLE, SIGNAL_GENERATED)
- **USER**: Specific user only (portfolio updates)
- **USER_BROKER**: Specific user+broker combo (INTENT_APPROVED, ORDER_FILLED)

**Broadcast Flow:**
```
Business logic (any thread)
  → EventService.emitGlobal(type, payload)
    → TradeEventRepository.append() [DB write, sync]
    → WsHub.publish(tradeEvent) [enqueue to BlockingQueue]
      → [100ms later] WS Batch Flusher Thread
        → batchQueue.drainTo(2000 events)
        → For each WsSession:
          ├─ Filter by session.shouldReceive(event)
          │  ├─ Check topic subscription
          │  └─ Check scope (GLOBAL/USER/USER_BROKER)
          └─ WebSockets.sendText(BATCH message)
```

---

### 2.6 BROKER ADAPTER ENGINE

| Class | Package | Role | Thread Model |
|-------|---------|------|--------------|
| **BrokerAdapterFactory** | broker | Creates/manages adapters | Any thread |
| **BrokerAdapter** (interface) | broker | Common interface | Defined by impl |
| **FyersAdapter** | broker.adapters | Fyers API v3 + WS v2 | WS thread per instance |
| **ZerodhaAdapter** | broker.adapters | Zerodha Kite (historical only) | HTTP thread pool |
| **DhanAdapter** | broker.adapters | Dhan broker (historical only) | HTTP thread pool |
| **UpstoxAdapter** | broker.adapters | Upstox API (partial) | HTTP thread pool |
| **AlpacaAdapter** | broker.adapters | US market Alpaca (partial) | HTTP thread pool |

**Broker Roles:**
- **DATA_BROKER** (`is_data_broker=true`): Provides live tick stream
- **EXEC_BROKER** (`broker_role='EXEC'`): Places orders, manages positions

---

### 2.7 WATCHDOG & RECOVERY ENGINE

| Class | Package | Role | Thread Model |
|-------|---------|------|--------------|
| **WatchdogManager** | service | Self-healing monitor (2-min interval) | Scheduled executor |

**Watchdog Checks (6 Systems):**
1. Database connectivity (HikariCP pool)
2. Data broker connection (tick stream)
3. WebSocket hub active sessions
4. OAuth session validity
5. Tick stream health (last tick < 5 min ago)
6. Candle generation (stale candles)

---

## 3. CLASS RESPONSIBILITY CARDS

### 3.1 TickCandleBuilder

**Engine:** Market Data Engine
**Package:** `in.annupaper.service.candle`

**Responsibility:**
- Ingests broker ticks (entry point)
- Builds 1-minute candles in real-time
- Triggers candle close on minute boundary
- Records tick timestamps for watchdog

**Owns State:**
- `partialCandles: ConcurrentHashMap<String, Map<TimeframeType, PartialCandle>>`
- PartialCandle fields (mutable: open, high, low, close, volume)

**Reads State From:**
- None (receives ticks directly)

**Writes State To:**
- MarketDataCache (via updateTick())
- CandleStore (via addIntraday())
- EventService (via emitGlobal(CANDLE))
- WatchdogManager (via recordTick())

**Public Methods:**
```java
void onTick(BrokerAdapter.Tick tick)
void finalizeStaleCandles()
```

**Must NEVER:**
- Access broker APIs
- Place orders
- Generate signals
- Mutate global strategy state

**Failure Modes:**
- Tick stream stops → Watchdog detects & alerts
- Minute boundary missed → finalizeStaleCandles() recovers
- DB unavailable → Candles lost (TODO: buffer)

**Thread Safety:**
- Single writer (broker thread only)
- PartialCandle in-place mutation is SAFE (single-threaded)
- CHM for outer map is safe for concurrent reads

---

### 3.2 SignalService

**Engine:** Signal Generation Engine
**Package:** `in.annupaper.service.signal`

**Responsibility:**
- Orchestrates signal generation
- Enforces constitutional gate (3× advantage)
- Persists signals to DB
- Triggers fan-out to execution engine

**Owns State:**
- None (stateless orchestrator)

**Reads State From:**
- ConfluenceCalculator (MTF analysis)
- UtilityAsymmetryCalculator (advantage gate)
- SignalRepository (signal history)

**Writes State To:**
- SignalRepository (insert new signals)
- EventService (emitGlobal(SIGNAL_GENERATED))
- ExecutionOrchestrator (fanOutSignal())

**Public Methods:**
```java
void generateAndProcess(String symbol, BigDecimal currentPrice, String reason)
void analyzeAndGenerateSignal(String symbol, BigDecimal currentPrice)
void expireSignal(String signalId)
void cancelSignal(String signalId)
```

**Must NEVER:**
- Place orders
- Access user-specific data during signal generation (GLOBAL scope)
- Validate user-specific constraints

**Failure Modes:**
- Confluence calculation fails → Log & skip signal
- DB write fails → Log & skip (signal lost)
- Fan-out fails → Log but signal persisted

**Thread Safety:**
- Stateless, can be called from multiple threads
- DB inserts are atomic

---

### 3.3 MtfPositionSizer

**Engine:** Position Sizing Engine (Constitutional)
**Package:** `in.annupaper.service.signal`

**Responsibility:**
- Applies 7 constitutional constraints
- Returns MINIMUM of all constraint values
- Deterministic given inputs
- Never modifies state

**Owns State:**
- None (pure calculator)

**Reads State From:**
- CandleStore (for ATR calculation)
- PortfolioRiskCalculator (for portfolio headroom)
- LogUtilityCalculator (for position-level constraint)
- VelocityCalculator (for velocity throttle)

**Writes State To:**
- None (returns SizingResult)

**Public Methods:**
```java
SizingResult calculatePositionSize(
    Signal signal,
    UserBroker userBroker,
    BigDecimal availableCash,
    Map<String, BigDecimal> existingPositions,
    MTFConfig config
)

SizingResult calculateAddSize(
    Signal signal,
    Trade existingTrade,
    UserBroker userBroker,
    BigDecimal availableCash,
    MTFConfig config
)
```

**Must NEVER:**
- Access database directly
- Place orders
- Modify user portfolio
- Emit events

**Failure Modes:**
- ATR calculation fails (missing candles) → Reject signal
- Any constraint unavailable → Return REJECTED with reason

**Thread Safety:**
- Stateless, thread-safe
- Can be called in parallel for multiple users

---

### 3.4 ExecutionOrchestrator

**Engine:** Validation & Execution Engine
**Package:** `in.annupaper.service.execution`

**Responsibility:**
- Fan-out GLOBAL signals to all EXEC brokers
- Parallel validation (CompletableFuture with 5-sec timeout)
- Create TradeIntents (APPROVED or REJECTED)
- Execute approved intents (TODO: incomplete)

**Owns State:**
- None (stateless orchestrator)

**Reads State From:**
- UserBrokerRepository (get active EXEC brokers)
- ValidationService (validate per user-broker)

**Writes State To:**
- TradeIntentRepository (insert intents)
- EventService (emitUserBroker(INTENT_APPROVED/REJECTED))
- BrokerAdapter (TODO: placeOrder())

**Public Methods:**
```java
void fanOutSignal(Signal signal)
CompletableFuture<TradeIntent> validateAndCreateIntent(Signal signal, UserBroker userBroker)
void executeApprovedIntents(List<TradeIntent> intents)
```

**Must NEVER:**
- Generate signals
- Modify signal state
- Access market data directly

**Failure Modes:**
- Validation timeout (5 sec) → REJECTED with timeout reason
- DB write fails → Intent lost (TODO: retry)
- Order placement fails → TODO: handle (not implemented)

**Thread Safety:**
- Uses CompletableFuture for parallel validation
- Each validation isolated (no shared mutable state)

---

### 3.5 WsHub

**Engine:** Transport & Broadcast Engine
**Package:** `in.annupaper.transport.ws`

**Responsibility:**
- Manage WebSocket connections (Undertow)
- Batch events (100ms flush interval)
- Filter events by scope/subscription
- Broadcast to relevant clients

**Owns State:**
- `activeSessions: ConcurrentHashMap<WebSocketChannel, WsSession>`
- `userChannels: ConcurrentHashMap<String, Set<WebSocketChannel>>`
- `batchQueue: LinkedBlockingQueue<TradeEvent>`

**Reads State From:**
- WsSession (topics, brokerIds, userId)

**Writes State To:**
- WebSocket channels (sendText())

**Public Methods:**
```java
WebSocketConnectionCallback websocketHandler()
void publish(TradeEvent event)
void registerSession(WebSocketChannel channel, WsSession session)
void unregisterSession(WebSocketChannel channel)
```

**Must NEVER:**
- Access database directly
- Generate signals or orders
- Modify business logic state

**Failure Modes:**
- Client disconnects → Auto-cleanup from maps
- Queue full (100K capacity) → Drop events (TODO: backpressure)
- Flusher thread dies → Events not sent (TODO: monitor)

**Thread Safety:**
- CHM for session maps
- CHM.newKeySet() for set values (userChannels)
- Single flusher thread drains queue

---

## 4. METHOD CONTRACTS

### 4.1 TickCandleBuilder.onTick()

```java
void onTick(BrokerAdapter.Tick tick)
```

| Aspect | Definition |
|--------|-----------|
| **Inputs** | `Tick(symbol, lastPrice, volume, timestamp)` |
| **Reads** | `partialCandles.get(symbol)` |
| **Writes** | `MarketDataCache, CandleStore, EventService, partialCandles` |
| **Idempotent** | NO (appends to candle, emits events) |
| **Deterministic** | YES (given same tick stream) |
| **Called By** | BrokerAdapter.onTick() (broker WS thread) |
| **Side Effects** | DB write (EventService), WS broadcast (EventService) |
| **Thread Safe** | YES (single writer only) |

---

### 4.2 MtfPositionSizer.calculatePositionSize()

```java
SizingResult calculatePositionSize(
    Signal signal,
    UserBroker userBroker,
    BigDecimal availableCash,
    Map<String, BigDecimal> existingPositions,
    MTFConfig config
)
```

| Aspect | Definition |
|--------|-----------|
| **Inputs** | Signal, UserBroker, cash, positions, config |
| **Reads** | CandleStore (for ATR), PortfolioRiskCalculator |
| **Writes** | NONE (returns SizingResult) |
| **Idempotent** | YES (pure function) |
| **Deterministic** | YES (given same inputs) |
| **Called By** | ValidationService.validate() |
| **Side Effects** | NONE |
| **Thread Safe** | YES (stateless) |

**Returns:**
```java
record SizingResult(
    BigDecimal quantity,
    String constraintApplied,  // "LOG_SAFE", "KELLY", "PORTFOLIO_BUDGET", etc.
    boolean rejected,
    String rejectReason
)
```

---

### 4.3 ExecutionOrchestrator.fanOutSignal()

```java
void fanOutSignal(Signal signal)
```

| Aspect | Definition |
|--------|-----------|
| **Inputs** | Signal (GLOBAL) |
| **Reads** | UserBrokerRepository (active EXEC brokers) |
| **Writes** | TradeIntentRepository, EventService |
| **Idempotent** | INTENDED (but not enforced - TODO) |
| **Deterministic** | YES (given same broker list) |
| **Called By** | SignalService.generateAndProcess() |
| **Side Effects** | DB writes (intents), WS broadcasts (events) |
| **Thread Safe** | YES (uses CompletableFuture for parallelism) |

**Flow:**
1. Get all active EXEC brokers (parallel)
2. For each: validateAndCreateIntent() (parallel, 5-sec timeout)
3. Collect results
4. Return (approved intents available for execution)

---

### 4.4 ValidationService.validate()

```java
ValidationResult validate(
    Signal signal,
    UserBroker userBroker,
    UserContext userContext
)
```

| Aspect | Definition |
|--------|-----------|
| **Inputs** | Signal, UserBroker, UserContext |
| **Reads** | PositionSizingService, WatchlistRepository |
| **Writes** | NONE (returns ValidationResult) |
| **Idempotent** | YES (pure validation) |
| **Deterministic** | YES (given same inputs) |
| **Called By** | ExecutionOrchestrator.validateAndCreateIntent() |
| **Side Effects** | NONE |
| **Thread Safe** | YES (stateless) |

**Returns:**
```java
record ValidationResult(
    boolean passed,
    BigDecimal approvedQty,
    List<ValidationError> errors
)

record ValidationError(
    ValidationErrorCode code,
    String message
)
```

**Validation Order (10 checks):**
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
12. Daily/weekly loss limits (TODO)
13. Not in cooldown

---

### 4.5 WsHub.publish()

```java
void publish(TradeEvent event)
```

| Aspect | Definition |
|--------|-----------|
| **Inputs** | TradeEvent (immutable) |
| **Reads** | NONE |
| **Writes** | `batchQueue.offer(event)` |
| **Idempotent** | NO (appends to queue) |
| **Deterministic** | N/A (async broadcast) |
| **Called By** | EventService.emitGlobal/emitUser/emitUserBroker() |
| **Side Effects** | Enqueues event for WS broadcast (async) |
| **Thread Safe** | YES (BlockingQueue is thread-safe) |

**Note:** Actual broadcast happens 100ms later in flusher thread.

---

## 5. STATE OWNERSHIP MATRIX

### 5.1 Write Paths (Who Writes What)

| State | Owner Class | Storage | Writers | When | Thread Safety |
|-------|-------------|---------|---------|------|---------------|
| **Tick prices** | MarketDataCache | `CHM<String, TickData>` | TickCandleBuilder | On every tick | CHM.put() atomic |
| **Partial candles** | TickCandleBuilder | `CHM<String, Map<TF, PartialCandle>>` | TickCandleBuilder | On every tick | Single writer only |
| **1-min candles** | CandleStore | Memory + DB | TickCandleBuilder | Every minute | Single writer |
| **25/125-min candles** | CandleStore | Memory + DB | CandleAggregator | On 1-min close | Single writer |
| **Signals** | SignalRepository | PostgreSQL | SignalService | Confluence detected | Sequential IDs |
| **Trade Intents** | TradeIntentRepository | PostgreSQL | ExecutionOrchestrator | Fan-out parallel | Per intent unique ID |
| **Trades** | TradeRepository | PostgreSQL | Order fill callbacks | On broker fill | Per trade unique ID |
| **Events** | TradeEventRepository | PostgreSQL | EventService | Every emission | Sequential seq |
| **Last analyzed price** | MtfSignalGenerator | `CHM<String, BigDecimal>` | MtfSignalGenerator | On analysis | CHM.put() atomic |
| **User channels** | WsHub | `CHM<String, Set<Channel>>` | I/O threads | Connect/disconnect | CHM.newKeySet() |
| **Session topics** | WsSession | `Set<String>` | I/O threads | On subscribe | CHM.newKeySet() |
| **Last tick timestamp** | WatchdogManager | `CHM<String, Instant>` | TickCandleBuilder | On tick | CHM.put() atomic |

### 5.2 Read Paths (No Writes)

| State | Readers | Context | Performance |
|-------|---------|---------|-------------|
| **Latest tick prices** | HTTP API, ExitSignalService, MtfSignalGenerator | LTP lookup | O(1) CHM.get() |
| **1-min candles** | CandleAggregator | Aggregation | Memory-first, O(1) |
| **All timeframe candles** | ConfluenceCalculator, PositionSizingService | MTF analysis | Memory + DB fallback |
| **Signal repository** | ValidationService | Signal details | DB query |
| **Portfolio data** | ValidationService, PositionSizingService | Risk calculations | DB query |
| **Open trades** | ExitSignalService, PortfolioRiskCalculator | Exit monitoring | DB query |
| **WS sessions** | Batch flusher | Event filtering | CHM iteration |

### 5.3 Immutable Objects (Safe to Share)

- **Candle** (timestamp, OHLCV) - Java record
- **Signal** (signalId, symbol, direction, zones, prices) - Immutable
- **TradeIntent** (validation result snapshot) - Immutable
- **Trade** (entryPrice, qty, status) - Updates create new versions
- **TradeEvent** (eventId, type, payload, seq) - Persisted, immutable
- **TickData** (lastPrice, timestamp) - Java record

---

## 6. EVENT FLOW INDEX

### 6.1 Tick → Signal Flow

```
BrokerAdapter.onTick(tick)
  → TickCandleBuilder.onTick()
    → MarketDataCache.updateTick() [O(1) CHM.put]
    → EventService.emitGlobal(TICK) [optional broadcast]
    → update1MinuteCandle() [in-place mutation, safe]
    → [Minute boundary] close1MinuteCandle()
      → CandleStore.addIntraday() [memory + DB]
      → EventService.emitGlobal(CANDLE)
      → CandleAggregator.on1MinuteCandleClose()
        → aggregate(MINUTE_25)
        → aggregate(MINUTE_125)
        → CandleStore.addIntraday() [for aggregated]
  → ExitSignalService.onTick() [check exits]
  → MtfSignalGenerator.onTick()
    → [If price moved > 0.3%]
    → SignalService.analyzeAndGenerateSignal()
```

### 6.2 Signal → Intent Flow

```
SignalService.analyzeAndGenerateSignal(symbol, price)
  → ConfluenceCalculator.analyze() [MTF zones]
  → [GATE 1] UtilityAsymmetryCalculator.passesAdvantageGate()
  → Create Signal (GLOBAL)
  → SignalRepository.insert()
  → EventService.emitGlobal(SIGNAL_GENERATED)
  → ExecutionOrchestrator.fanOutSignal(signal)
    → Get all active EXEC brokers
    → For each broker (parallel):
      → validateAndCreateIntent()
        → ValidationService.validate()
          → PositionSizingService.calculatePositionSize()
            → MtfPositionSizer.calculatePositionSize()
              → [7 constraints, return MINIMUM]
        → Create TradeIntent (APPROVED or REJECTED)
        → TradeIntentRepository.insert()
        → EventService.emitUserBroker(INTENT_APPROVED/REJECTED)
```

### 6.3 Intent → Order Flow (TODO: INCOMPLETE)

```
ExecutionOrchestrator.executeApprovedIntents(intents)
  → For each APPROVED intent:
    → executeIntent(intent)
      → [TODO] BrokerAdapter.placeOrder() ← NOT IMPLEMENTED
      → EventService.emitUserBroker(ORDER_CREATED)
      → [WAITING FOR] Broker fill callback
        → Create Trade (OPEN)
        → TradeRepository.insert()
        → EventService.emitUserBroker(ORDER_FILLED)
        → ExitSignalService.addTrade() [monitor]
```

### 6.4 Exit Monitoring Flow

```
ExitSignalService.onTick()
  → For each OPEN trade (in-memory HashMap, MOCK):
    → Check: currentPrice >= target → TARGET_HIT
    → Check: currentPrice <= stopLoss → STOP_LOSS
    → Check: now > entryTime + maxHoldTime → TIME_BASED
    → If exit triggered:
      → BrickMovementTracker.shouldAllowExit() [dedupe]
      → emitExitSignal()
      → Calculate realized P&L
      → Trade status → CLOSED
      → EventService.emitUserBroker(SIGNAL_EXIT)
```

---

## 7. FORBIDDEN COUPLINGS (CONSTITUTIONAL)

### 7.1 Strict Boundaries

❌ **SignalService MUST NOT:**
- Access user-specific data (signals are GLOBAL)
- Place orders
- Validate user constraints
- Read portfolio state

❌ **TickCandleBuilder MUST NOT:**
- Generate signals
- Access broker APIs (only receives ticks)
- Place orders
- Validate user constraints

❌ **MtfPositionSizer MUST NOT:**
- Access database directly
- Place orders
- Emit events
- Modify user portfolio

❌ **ExecutionOrchestrator MUST NOT:**
- Generate signals
- Modify signal state
- Access market data directly (use MarketDataCache)

❌ **BrokerAdapter MUST NOT:**
- Generate signals
- Validate user constraints
- Access database directly (only callbacks)

### 7.2 Allowed Couplings

✅ **SignalService MAY:**
- Call ConfluenceCalculator
- Call UtilityAsymmetryCalculator
- Call ExecutionOrchestrator.fanOutSignal()
- Write to SignalRepository

✅ **ValidationService MAY:**
- Call PositionSizingService
- Read WatchlistRepository
- Read UserBrokerRepository

✅ **PositionSizingService MAY:**
- Call MtfPositionSizer
- Call LogUtilityCalculator
- Call PortfolioRiskCalculator
- Read CandleStore

---

## 8. COMPLETENESS CHECKLIST

### 8.1 Implementation Status

| Component | Status | Notes |
|-----------|--------|-------|
| **Tick ingestion** | ✅ COMPLETE | Single broker thread, CHM updates |
| **1-min candle building** | ✅ COMPLETE | In-place mutation, minute boundary |
| **Candle aggregation** | ✅ COMPLETE | 25/125-min from 1-min buckets |
| **MTF confluence analysis** | ✅ COMPLETE | HTF/ITF/LTF zone detection |
| **Constitutional gates** | ✅ COMPLETE | 3× advantage + 7 constraints |
| **Signal generation** | ✅ COMPLETE | GLOBAL broadcast |
| **Fan-out to users** | ✅ COMPLETE | Parallel validation with timeout |
| **Position sizing** | ✅ COMPLETE | 7 constraints, deterministic |
| **Validation (10-point)** | ⚠️ PARTIAL | Daily/weekly loss not calculated |
| **Trade intent creation** | ✅ COMPLETE | DB persist, USER_BROKER events |
| **Order execution** | ❌ INCOMPLETE | executeIntent() TODO |
| **Position tracking** | ❌ INCOMPLETE | ExitSignalService uses mock HashMap |
| **Exit monitoring** | ⚠️ PARTIAL | Target/stop/time logic exists, but mock trades |
| **WebSocket broadcast** | ✅ COMPLETE | Batching, scoped delivery |
| **Event sourcing** | ✅ COMPLETE | All events persisted with seq |
| **Self-healing** | ✅ COMPLETE | Watchdog monitors 6 systems |

### 8.2 TODOs Found (Critical Path)

| Priority | File | Line | Issue | Impact |
|----------|------|------|-------|--------|
| **P0** | ExecutionOrchestrator.java | 235 | executeIntent() missing broker.placeOrder() | **HIGH** - No actual orders placed |
| **P0** | ExitSignalService.java | 45-50 | Uses mock HashMap, not DB trades | **HIGH** - Exit monitoring broken |
| **P1** | App.java | 263-266 | UserContext missing dailyLoss, weeklyLoss | MEDIUM - Risk limits not enforced |
| **P1** | SignalService.java | 416 | pWin hardcoded 65% | MEDIUM - Inaccurate Kelly sizing |
| **P1** | PositionSizingService.java | 129 | TODO: Track actual portfolio peak | MEDIUM - Drawdown calc conservative |
| P2 | FyersAdapter.java | 38,74,153 | Using test API (api-t1.fyers.in) | MEDIUM - Production requires real API |
| P2 | BrickMovementTracker.java | 99 | TODO: Load thresholds from config | LOW - Hardcoded brick thresholds |

### 8.3 Duplicate Logic Check

| Area | Files | Assessment |
|------|-------|-----------|
| **ATR calculation** | ATRCalculator, PositionSizingService | ✅ Properly separated |
| **Timeframe calculations** | SessionClock (single source) | ✅ No duplication |
| **Confluence analysis** | ConfluenceCalculator (single source) | ✅ No duplication |
| **Position sizing constraints** | MtfPositionSizer (single source) | ✅ No duplication |

**Assessment:** ✅ **Minimal duplication** - good separation of concerns.

### 8.4 Dead Code Check

| Class | Status | Evidence |
|-------|--------|----------|
| **DistributionAnalyzer** | LIKELY UNUSED | No grep references; Monte Carlo not called |
| **MaxDropCalculator** | LIKELY UNUSED | Limited references |
| **AlpacaAdapter** | PARTIAL IMPL | US market support incomplete |
| **UpstoxAdapter** | PARTIAL IMPL | Incomplete implementation |

---

## 9. THREAD SAFETY PATTERNS

### 9.1 Pattern 1: Immutable Object Replacement

**Used in:** MarketDataCache, TickData

```java
// ✅ CORRECT: Replace entire immutable object
marketDataCache.updateTick(symbol, new BigDecimal("1234.50"), Instant.now());
// Internally: CHM.put(symbol, new TickData(price, ts))

// ❌ WRONG: In-place mutation of mutable object
tickData.setLastPrice(newPrice);  // Race condition!
```

**Why it works:**
- TickData is immutable (Java record)
- CHM.put() is atomic
- Readers get either old or new snapshot (no torn writes)

---

### 9.2 Pattern 2: Volatile for Immutable References

**Used in:** WsSession.lastActivity

```java
private volatile Instant lastActivity;  // ✅ Visibility guaranteed

public void touch() {
    this.lastActivity = Instant.now();  // Multiple I/O threads write
}
```

**Why it works:**
- Instant is immutable
- volatile ensures visibility across threads
- No need for AtomicReference (cheaper)

---

### 9.3 Pattern 3: ConcurrentHashMap for Set Values

**Used in:** WsHub.userChannels, WsSession.topics

```java
// ✅ CORRECT: Concurrent set in map value
ConcurrentMap<String, Set<WebSocketChannel>> userChannels = new ConcurrentHashMap<>();
Set<WebSocketChannel> channels = userChannels.computeIfAbsent(
    userId,
    k -> ConcurrentHashMap.newKeySet()  // Thread-safe set
);
channels.add(channel);  // Multiple threads can add/remove

// ❌ WRONG: Non-thread-safe set
userChannels.computeIfAbsent(userId, k -> new HashSet<>());  // Race condition!
```

---

### 9.4 Pattern 4: Single-Writer, Multiple-Reader

**Used in:** TickCandleBuilder.partialCandles

```java
// partialCandles: Only broker thread writes
private final Map<String, Map<TimeframeType, PartialCandle>> partialCandles = new ConcurrentHashMap<>();

// ✅ SAFE: Only broker thread mutates PartialCandle fields
partial.high = partial.high.max(price);
partial.close = price;

// ✅ SAFE: On close, publish immutable snapshot
Candle candle = Candle.of(symbol, timeframe, partial.startTime, ...);
candleStore.addIntraday(candle);
```

**Why it works:**
- PartialCandle is mutable BUT only broker thread writes
- On candle close, converted to immutable Candle
- Other threads read persisted Candles (immutable)

---

## 10. CRITICAL INVARIANTS

### 10.1 Position Tracking

**Invariant:** Each Trade has unique identity
```
Trade:
  - signalId: links to original Signal
  - intentId: links to validation result
  - userBrokerId: identifies execution broker
  - entryPrice, entryQty: size and cost
  - status: OPEN | CLOSED | CANCELLED
```

**Pyramid Limits:**
- Max open trades per portfolio: configured
- Max pyramid level: configurable (default 10)
- Existing position qty tracked for averaging

---

### 10.2 Rebuy/Averaging

**Entry Point:** MtfSignalGenerator detects lower price signal

**Flow:**
1. Detect signal (confluence found)
2. Check if existing position in symbol
3. If yes → Call `calculateAddSize()` (not `calculatePositionSize()`)
4. AveragingGateValidator applies strict gates:
   - Distance from entry >= ATR × threshold
   - Max pyramid level not exceeded
   - Velocity/stress not too high
5. Return qty (0 if gates reject)

**Invariant:** First entry creates Trade, subsequent entries update **same** Trade (needs verification).

---

### 10.3 Signal Deduplication

**Deduplication Strategy:**
```
✅ MtfSignalGenerator.lastAnalyzedPrice (0.3% threshold)
  └─ Only re-analyze if price moved > 0.3%

✅ MtfSignalGenerator.lastAnalysisTime
  └─ Only analyze once per minute

✅ BrickMovementTracker.shouldAllowExit()
  └─ Prevent duplicate exit signals

⚠️ Signal duplicates NOT prevented at DB level
  └─ Could generate duplicate if price bounces
```

**Recommendation:** Add unique index:
```sql
UNIQUE(symbol, confluence_type, generated_at::date, effective_floor, effective_ceiling)
```

---

### 10.4 Risk Limit Enforcement

**Portfolio Level:**
```
MaxPortfolioLogLoss: e_port_max
  └─ Current port log loss + new trade log loss <= e_port_max
  └─ Enforced in: MtfPositionSizer (constraint #5)
```

**Symbol Level:**
```
MaxSymbolLogLoss: e_sym_max
  └─ Current symbol log loss + new trade log loss <= e_sym_max
  └─ Enforced in: MtfPositionSizer (constraint #6)
```

**Position Level:**
```
MaxPositionLogLoss: L_pos
  └─ log(floor / entry) >= L_pos
  └─ Enforced in: LogUtilityCalculator
```

**Utility Gate (Constitutional):**
```
MinAdvantageRatio: r_min (default 3.0×)
  └─ U(π, ℓ) / π >= 3.0× (3× advantage required)
  └─ Enforced BEFORE sizing
  └─ π = ln(ceiling / entry), ℓ = ln(floor / entry)
```

**Daily/Weekly Loss Limits:**
```
TODO: Not calculated in UserContext (BUG)
  └─ MaxDailyLoss, MaxWeeklyLoss not enforced
```

---

### 10.5 Idempotency Guarantees

| Operation | Idempotent | Mechanism |
|-----------|-----------|-----------|
| **Signal generation** | ❌ NO | Price/time thresholds (can regenerate) |
| **Trade intent creation** | ✅ INTENDED | Per signal × user-broker, immutable |
| **Order placement** | ⚠️ PARTIAL | TODO: Not implemented |
| **Candle aggregation** | ✅ YES | Upsert on conflict (timestamp) |
| **Tick persistence** | ❌ NO | Events appended sequentially |
| **Event broadcast** | ❌ NO | May resend on WS reconnect |

---

## 11. VERIFICATION SCORECARD

### 11.1 Per-Engine Completeness (0-10 scale)

| Engine | Score | Notes |
|--------|-------|-------|
| **Market Data Engine** | 10/10 | ✅ Production-grade |
| **Signal Generation Engine** | 9/10 | ⚠️ pWin hardcoded, signal dedupe partial |
| **Position Sizing Engine** | 10/10 | ✅ Constitutional constraints complete |
| **Validation Engine** | 8/10 | ⚠️ Daily/weekly loss not calculated |
| **Execution Engine** | 4/10 | ❌ Order placement TODO |
| **Position Tracking Engine** | 3/10 | ❌ Exit monitoring mock only |
| **Broadcast Engine** | 10/10 | ✅ Batching, scoped delivery working |
| **Self-Healing Engine** | 10/10 | ✅ Watchdog monitors 6 systems |

**Overall System Score:** **7.5/10** (Production-ready for signal generation, needs execution completion)

---

## 12. RECOMMENDATIONS

### 12.1 Priority 0 (Critical)

1. **Implement Order Execution**
   - Complete ExecutionOrchestrator.executeIntent()
   - Call adapter.placeOrder()
   - Handle fill callbacks
   - Update Trade status

2. **Replace Mock Exit Monitoring**
   - ExitSignalService query TradeRepository (not HashMap)
   - Load OPEN trades on startup
   - Update Trade status on exit

3. **Add Signal Deduplication**
   - Unique index on Signal table
   - Prevent duplicate signals at same price/time

---

### 12.2 Priority 1 (Important)

4. **Calculate Actual Metrics**
   - pWin from historical win rate (not 65% hardcoded)
   - dailyLoss/weeklyLoss in UserContext
   - Actual portfolio peak (not total capital)

5. **Production Broker APIs**
   - Switch Fyers from test to production API
   - Complete Upstox/Alpaca implementations

---

### 12.3 Priority 2 (Nice-to-have)

6. **Document Rebuy Strategy**
   - Clarify: same Trade or new Trade?
   - Document tradeNumber semantics

7. **Load Config from DB**
   - Brick thresholds (BrickMovementTracker)
   - Move hardcoded values to MTFConfig

---

## APPENDIX: KEY CODE REFERENCES

### A.1 Pipeline Entry Points

- **App.java:360-370** - Tick stream setup
- **TickCandleBuilder.onTick()** - Tick ingestion (line 72)
- **SignalService.generateAndProcess()** - Signal generation (line 71)
- **ExecutionOrchestrator.fanOutSignal()** - Fan-out (line 60)
- **ValidationService.validate()** - Per-user validation (line 39)
- **ExecutionOrchestrator.executeApprovedIntents()** - Order execution (line 220)

### A.2 Constitutional Gates

- **UtilityAsymmetryCalculator.passesAdvantageGate()** - 3× advantage check
- **MtfPositionSizer.calculatePositionSize()** - 7 constraints (line 50)
- **LogUtilityCalculator.calculateMaxLogSafeQty()** - Position-level log
- **PortfolioRiskCalculator.calculatePortfolioHeadroom()** - Portfolio budget
- **AveragingGateValidator.validateAveragingGate()** - Rebuy constraints
- **BrickMovementTracker.shouldAllowExit()** - Exit dedupe

### A.3 State Management

- **CandleStore** - Dual (memory + DB) candle storage
- **MarketDataCache** - In-memory LTP cache
- **WsHub.activeSessions** - WebSocket session tracking
- **WatchdogManager.lastTickTimestamp** - Tick liveness
- **TickCandleBuilder.partialCandles** - In-flight 1-min candles

---

**Document Status:** ✅ COMPLETE
**Last Updated:** January 13, 2026
**Next Review:** After P0 fixes (order execution, trade tracking)
**Maintainer:** Architecture Team

---

## USAGE NOTES

This document is the **single source of truth** for system architecture verification. Use it to:

1. **Onboard new developers** - Read this before touching code
2. **Review PRs** - Verify changes don't violate forbidden couplings
3. **Debug production issues** - Trace event flows, identify state owners
4. **Refactor safely** - Understand dependencies before changing
5. **Plan new features** - Identify which engines to modify

**Update Policy:** This document MUST be updated when:
- New engine/class added
- State ownership changes
- Thread safety model changes
- Critical invariants modified
- Forbidden couplings violated (requires architecture review)

---
