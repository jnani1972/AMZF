# COMPLETENESS FRAMEWORK V2 UPGRADE
# AnnuPaper v04 - Making Verification Ungameable

**Date:** January 13, 2026
**Purpose:** Upgrade COMPLETENESS_VERIFICATION_ADDENDUM.md from V1 to V2 with enforcement gates
**Status:** Drop-in improvements to prevent hand-waving proofs

---

## SUMMARY OF UPGRADES

This document contains 10 critical improvements that make the completeness framework **enforcement-ready**:

1. ✅ **3-Part Reachability Proof** (construction + registration + invocation)
2. ✅ **Source of Truth + Reconciliation Rule** (for all stateful artifacts)
3. ✅ **Zero Hot-Path DB Writes Enforcement** (config gate)
4. ✅ **Canonical Validation List** (docs follow code)
5. ✅ **No Silent Downgrade** (metrics + logs + alerts required)
6. ✅ **Production Mode Invariant** (can't start without order execution)
7. ✅ **DB Uniqueness + Broker Reconciliation** (for idempotency)
8. ✅ **Tick Dedupe as P1/P0** (corrupts candles → release blocker)
9. ✅ **State Machine Ownership Enforcement** (only TradeRepository writes)
10. ✅ **Risk Profile Leak Prevention** (never in Signal entity)

**Impact:** Transforms "should be called" into "proven to be called", prevents silent data loss, makes P0 gate unbypassable.

---

## UPGRADE 1: 3-PART REACHABILITY PROOF (MANDATORY)

### Problem
Current reachability proofs say "called from X" but don't prove:
- How was it constructed?
- Where was it registered?
- Did it actually run?

This allows "should be called" hand-waving.

### Solution: Standardized 3-Part Proof

**Replace all "Reachability Proof" sections in Section 8.1 with this template:**

```markdown
**Reachability Proof (3 Parts REQUIRED):**

1. **Construction Proof:**
   - Who instantiates this component? (file:line)
   - Example: `MarketDataCache cache = new MarketDataCache();` in App.java:245

2. **Registration Proof:**
   - Who registers callbacks/schedules/routes? (file:line)
   - Example: `tickBuilder.registerListener(mtfSignalGenerator);` in App.java:352

3. **Invocation Proof:**
   - Evidence it ran at least once (log line / metric / test / manual verification)
   - Example: Grep logs for "TickCandleBuilder.onTick" → 15,432 occurrences today
   - Example: Metric `ticks.processed.count` = 2.4M

**Status:**
- ✅ All 3 proofs provided → Component is WIRED
- ⚠️ 2 of 3 proofs → PARTIAL (needs invocation proof)
- ❌ < 2 proofs → NOT WIRED (dead code)
```

### Example: Market Data Engine (Updated)

```markdown
#### 8.1.1 MARKET DATA ENGINE

**Entry Points:**
1. BrokerAdapter.onTick(tick)
   ├─ Registered in: FyersAdapter.connectWebSocket() [line 187]
   ├─ Callback: ws.onTextMessage() → processTickMessage() → onTick()
   └─ Calls: TickCandleBuilder.onTick() [service/candle/TickCandleBuilder.java:72]

**Reachability Proof (3 Parts):**

1. **Construction Proof:**
   - `TickCandleBuilder tickBuilder = new TickCandleBuilder(...);` in App.java:318
   - `MtfSignalGenerator signalGen = new MtfSignalGenerator(...);` in App.java:350

2. **Registration Proof:**
   - Broker callback: `adapter.setTickListener(tickBuilder);` in App.java:368
   - Signal listener: `tickBuilder.addTickListener(signalGen);` in App.java:352
   - Exit listener: `tickBuilder.addTickListener(exitSignalService);` in App.java:355
   - Finalizer scheduled: `scheduler.scheduleAtFixedRate(tickBuilder::finalizeStaleCandles, 2, TimeUnit.SECONDS);` in App.java:342

3. **Invocation Proof:**
   - Log grep: `grep "TickCandleBuilder.onTick" backend.log | wc -l` → 15,432 calls today
   - Metric: `marketdata.ticks.processed` = 2,456,789 (Prometheus)
   - Manual: Connect broker, watch logs stream "Tick received: RELIANCE @ 2450.50"

**Status:** ✅ WIRED - All 3 proofs present
```

---

## UPGRADE 2: SOURCE OF TRUTH + RECONCILIATION RULE

### Problem
In-memory caches can diverge from DB without periodic reconciliation.

### Solution: Mandatory Reconciliation for Stateful Artifacts

**Add new subsection 8.4.6 "Reconciliation Requirements":**

```markdown
#### 8.4.6 RECONCILIATION REQUIREMENTS

For each stateful artifact, define:

1. **Canonical Source of Truth:**
   - DB table + unique key
   - Example: `signals` table, PK = `signal_id`

2. **In-Memory Cache (Optional):**
   - Allowed for performance
   - Must be rebuildable from DB
   - Example: `ExitSignalService.openTrades` → loaded from `trades` WHERE `status = 'OPEN'`

3. **Reconciliation Job:**
   - Periodic check to heal drift
   - Frequency: Every N minutes (based on criticality)
   - Example: Every 5 min, reload open trades from DB

**Reconciliation Table:**

| Artifact | Source of Truth | Cache | Reconciliation | Frequency |
|----------|----------------|--------|----------------|-----------|
| **Signal** | signals table (PK: signal_id) | ❌ None | Not needed (immutable) | N/A |
| **TradeIntent** | trade_intents table (PK: intent_id) | ❌ None | Not needed (immutable) | N/A |
| **Order** | orders table (PK: order_id) | ❌ None | Broker reconciliation (query status) | Every 1 min |
| **Trade** | trades table (PK: trade_id) | ✅ ExitSignalService.openTrades | Load WHERE status='OPEN' | Every 5 min |
| **Candle (partial)** | ❌ None (ephemeral) | ✅ TickCandleBuilder.partialCandles | Not needed (finalized to DB on close) | N/A |
| **MarketDataCache** | ❌ None (real-time only) | ✅ latestTicks | Not needed (tick stream is source) | N/A |

**Enforcement:**
- ❌ Cache WITHOUT reconciliation → P1 TODO (add reconciliation job)
- ❌ Cache WITHOUT DB source of truth → P0 BLOCKER (fix architecture)
```

---

## UPGRADE 3: ZERO HOT-PATH DB WRITES ENFORCEMENT

### Problem
"Zero hot-path DB writes" is documented but not enforced - could regress silently.

### Solution: Config Invariant Gate

**Replace Section 13.1 "Resolution" with:**

```markdown
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

**ENFORCEMENT (Config Invariant):**

```java
// In EventService
if (config.persist.tick.events) {
    // ❌ FORBIDDEN: Direct sync DB write on broker thread
    // repo.append(tickEvent);  // BLOCKS broker thread

    // ✅ REQUIRED: Enqueue to AsyncEventWriter
    asyncEventWriter.enqueue(tickEvent);
}

// In App startup validation
if (config.persist.tick.events && !config.asyncEventWriter.enabled) {
    throw new IllegalStateException(
        "INVALID CONFIG: persist.tick.events=true requires asyncEventWriter.enabled=true. " +
        "Direct DB writes on broker thread are FORBIDDEN (P0 invariant)."
    );
}
```

**Architectural Rule (P0):**
- ❌ `repo.append()` in broker callback → FORBIDDEN
- ✅ `asyncEventWriter.enqueue()` → ALLOWED
- ✅ Code review MUST check this invariant

**Future Implementation:**
- TODO: Implement AsyncEventWriter (bounded queue + dedicated writer thread)
- TODO: Add metric `event.persist.async.queue.depth` (alert if > 10K)
```

**Documentation Update:**
```markdown
"Zero hot-path DB writes for tick PRICES (in-memory MarketDataCache).
Tick EVENT persistence (if enabled) MUST use AsyncEventWriter (never sync on broker thread)."
```
```

---

## UPGRADE 4: CANONICAL VALIDATION LIST

### Problem
Docs say "10-point gating" but code has 12 checks - docs can drift from code.

### Solution: Code is Source of Truth

**Replace Section 13.2 "Resolution" with:**

```markdown
**Resolution:**

DECISION: ValidationService.validate() check list is the CANONICAL source of truth.
Documentation must match it, not the other way around.

**CANONICAL LIST** (as of January 13, 2026):

File: `ValidationService.java`
Method: `validate(Signal signal, UserBroker userBroker, UserContext userContext)`

```java
// Hard Checks (1-6): Basic filters
1. brokerEnabled && brokerConnected
2. !portfolio.isPaused()
3. symbolInWatchlist(symbol)
4. signal.hasTripleConfluence()
5. signal.pWin() >= config.minWinProb()
6. kelly >= config.minKellyFraction()

// Constitutional Sizing Gate (7)
7. SizingResult result = positionSizingService.calculatePositionSize(...)
   // Returns MINIMUM of 7 constraints

// Soft Checks (8-13): Value/capital/limits
8. result.quantity() >= config.minQty()
9. result.value() >= config.minValue()
10. result.value() <= broker.maxPerTrade()
11. portfolioExposure + result.value() <= config.maxPortfolioExposure()
12. userContext.dailyLoss() <= config.maxDailyLoss()  // TODO: Calculate
    userContext.weeklyLoss() <= config.maxWeeklyLoss()  // TODO: Calculate
13. !userContext.inCooldown()
```

**Documentation Standard:**
- ✅ Count checks in code → Update docs to match
- ❌ Docs say "N-point" but code has N+2 → Update docs to N+2
- ✅ Add comment in ValidationService.java: "This is the canonical check list"

**Current Name:** "12-Point Per-User-Broker Validation"
- Hard checks: 1-6
- Sizing gate: 7 (applies 7 constitutional constraints internally)
- Soft checks: 8-13
```

---

## UPGRADE 5: NO SILENT DOWNGRADE

### Problem
"DEGRADE (candles lost)" paths exist without metrics/alerts - can quietly lose data forever.

### Solution: Mandatory Observability for Degradation

**Add to all "Failure & Fallback" sections in 8.1:**

```markdown
**DEGRADE Requirements (Mandatory):**

When any component returns "DEGRADE" (continues processing with data loss), it MUST:

1. **Increment Counter Metric:**
   - Example: `candles.persist.fail.count++`
   - Example: `events.broadcast.dropped.count++`

2. **Emit Structured Log:**
   - With stable error code
   - Example: `log.error("ERR_CANDLE_PERSIST_FAIL", "Failed to persist candle", symbol, timeframe, exception)`

3. **Watchdog Visibility:**
   - If count > threshold/min → `WATCHDOG_ALERT`
   - Example: If `candles.persist.fail.count` > 10/min → Alert: "Candle persistence failing"

**Enforcement:**
- ❌ "DEGRADE" without all 3 → P1 TODO (add observability)
- ❌ Counter/log exists but no watchdog → P2 TODO (add alert rule)
```

**Example (Market Data Engine):**

```markdown
Failure: Database unavailable for candle persist
  ├─ Detection: SQLException on CandleStore.addIntraday()
  ├─ Action: Log error, continue processing
  └─ Fallback: DEGRADE (candles lost, memory cache intact)

     **Observability (REQUIRED):**
     1. Metric: `candles.persist.fail.count.inc()` [Prometheus counter]
     2. Log: `log.error("ERR_CANDLE_PERSIST_FAIL", "DB unavailable", symbol, timeframe, e)`
     3. Watchdog: If count > 10/min → `WATCHDOG_ALERT(CANDLE_PERSISTENCE_FAILING)`

     ⚠️ TODO: Buffer candles in-memory for retry (instead of dropping)
```

---

## UPGRADE 6: PRODUCTION MODE INVARIANT

### Problem
System could accidentally start in production with order execution disabled ("paper trading by mistake").

### Solution: Startup Validation Gate

**Add to Section 8.2.2 (after TODO Guard Requirements):**

```markdown
#### 8.2.3 PRODUCTION MODE INVARIANT (Startup Gate)

**Problem:** Accidentally starting in production mode with critical features disabled.

**Solution:** Startup validation that fails fast if config is invalid.

**Enforcement Code (Required in App.main()):**

```java
// App.java startup validation
public static void main(String[] args) {
    Config config = loadConfig();

    // Production mode invariant
    if (config.isProductionMode()) {
        // GATE 1: Order execution MUST be enabled
        if (!config.isOrderExecutionEnabled()) {
            throw new IllegalStateException(
                "PRODUCTION MODE requires order.execution.enabled=true. " +
                "System refuses to start. " +
                "Either enable order execution OR set production.mode=false."
            );
        }

        // GATE 2: Must use production broker APIs (not test)
        for (BrokerConfig broker : config.getBrokers()) {
            if (broker.apiUrl.contains("test") || broker.apiUrl.contains("-t1.")) {
                throw new IllegalStateException(
                    "PRODUCTION MODE forbids test broker APIs. " +
                    "Broker: " + broker.name + ", URL: " + broker.apiUrl
                );
            }
        }

        // GATE 3: Async event writer MUST be enabled if persisting ticks
        if (config.persist.tick.events && !config.asyncEventWriter.enabled) {
            throw new IllegalStateException(
                "PRODUCTION MODE with tick persistence requires asyncEventWriter.enabled=true."
            );
        }

        log.info("✅ PRODUCTION MODE validation passed");
    } else {
        // Non-production: Warn about disabled features
        log.warn("⚠️ NON-PRODUCTION MODE: Orders may be disabled or mocked.");
        if (!config.isOrderExecutionEnabled()) {
            log.warn("⚠️ Order execution DISABLED - Paper trading mode");
        }
    }

    // Start application
    startApplication(config);
}
```

**P0 TODO Gate Update:**

P0 TODOs can be shipped with feature flags ONLY if `production.mode=false`.

If `production.mode=true`:
- ❌ All P0 TODOs MUST be resolved (no guards, no flags)
- ❌ Paper trading mode is NOT production-ready

**Release Checklist:**
```
☐ production.mode=true
☐ order.execution.enabled=true
☐ All broker APIs are production URLs (no "-t1", no "test")
☐ asyncEventWriter.enabled=true (if tick persistence enabled)
☐ All P0 TODOs resolved (Section 8.2.1)
```
```

---

## UPGRADE 7: DB UNIQUENESS + BROKER RECONCILIATION

### Problem
Idempotency planned via `intentId` but missing:
- DB unique constraints
- Broker reconciliation loop

### Solution: Enforce at DB + Reconciliation Level

**Replace Section 8.4.1 "Where is idempotency enforced?" with:**

```markdown
**Where is idempotency enforced? (3 Layers)**

**Layer 1: Application Logic**
```java
// Use intentId as broker's clientOrderId
String clientOrderId = intent.intentId();
OrderResponse response = broker.placeOrder(
    intent.symbol(),
    intent.approvedQty(),
    intent.signal().entryPrice(),
    clientOrderId  // ← Idempotency key
);
```

**Layer 2: Database Uniqueness (REQUIRED)**

```sql
-- Schema enforcement
CREATE TABLE trades (
    trade_id UUID PRIMARY KEY,
    intent_id UUID NOT NULL UNIQUE,  -- ✅ Enforce: One trade per intent
    broker_order_id VARCHAR(100) UNIQUE,  -- ✅ Enforce: One trade per broker order
    client_order_id VARCHAR(100) UNIQUE,  -- ✅ Same as intentId
    user_broker_id UUID NOT NULL,
    signal_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    -- ... other fields
    CONSTRAINT fk_intent FOREIGN KEY (intent_id) REFERENCES trade_intents(intent_id)
);

CREATE INDEX idx_trades_pending ON trades(status) WHERE status = 'PENDING';
CREATE INDEX idx_trades_open ON trades(status) WHERE status = 'OPEN';

-- Upsert semantics (idempotent)
INSERT INTO trades (trade_id, intent_id, client_order_id, ...)
VALUES (?, ?, ?, ...)
ON CONFLICT (intent_id) DO UPDATE SET
    broker_order_id = EXCLUDED.broker_order_id,
    status = EXCLUDED.status,
    updated_at = NOW()
RETURNING *;
```

**Layer 3: Broker Reconciliation Loop (REQUIRED)**

```java
// Scheduled reconciliation (every 30-60 seconds)
@Scheduled(fixedDelay = 30_000)  // 30 seconds
public void reconcilePendingOrders() {
    List<Trade> pending = tradeRepository.findByStatus(TradeStatus.PENDING);

    for (Trade trade : pending) {
        try {
            // Query broker for order status
            OrderStatus status = broker.getOrderStatus(
                trade.brokerOrderId(),  // Broker's order ID
                trade.clientOrderId()   // Our intentId (fallback)
            );

            // Update trade based on broker response
            Trade updated = switch (status.status) {
                case "FILLED" -> trade.markFilled(status.filledQty, status.avgPrice);
                case "REJECTED" -> trade.markRejected(status.rejectReason);
                case "CANCELLED" -> trade.markCancelled();
                case "PENDING", "OPEN" -> trade;  // No change yet
                default -> {
                    log.warn("Unknown order status: {}", status.status);
                    yield trade;
                }
            };

            if (updated != trade) {
                tradeRepository.upsert(updated);
                eventService.emitUserBroker(EventType.ORDER_STATUS_CHANGED, updated);
            }

        } catch (Exception e) {
            log.error("Reconciliation failed for trade {}", trade.tradeId(), e);
            // Continue to next trade
        }
    }

    log.debug("Reconciled {} pending orders", pending.size());
}
```

**Restart Safety:**

When system restarts:
1. Load all `PENDING` trades from DB
2. Reconciliation loop runs on schedule
3. Updates trade status based on broker reality
4. No duplicate orders (broker dedupes on `clientOrderId`)

**Idempotency Proof:**
- ✅ Retry `executeIntent(intent)` → DB upsert (same `intent_id`)
- ✅ Broker sees duplicate `clientOrderId` → Returns existing order
- ✅ Reconciliation loop → Updates trade status to reality
- ✅ System restart → Pending orders reconciled on next loop
```

---

## UPGRADE 8: TICK DEDUPE AS P1/P0

### Problem
Tick dedupe marked as "nice-to-have" but duplicate ticks corrupt candles (volume, OHLC).

### Solution: Upgrade to P1 (or P0 if candles drive exits)

**Replace in Section 15.2 (TickCandleBuilder example):**

```markdown
6. IDEMPOTENCY:
   - ❌ NO - Tick reprocessing adds duplicate volume
   - ⚠️ **P1 TODO** (upgrade to P0 if candles drive exits): Dedupe by (symbol, exchangeTimestamp, lastPrice, lastQty)

   **Impact:** Duplicate ticks corrupt:
   - Candle volume (double-counted)
   - Candle close (last tick wins, may be stale)
   - Exit signals (if price-based exits use corrupted data)

   **Solution Required:**
   ```java
   // TickCandleBuilder
   private final Set<TickKey> recentTicks = ConcurrentHashMap.newKeySet();

   record TickKey(String symbol, Instant exchangeTs, BigDecimal price, BigDecimal qty) {}

   public void onTick(Tick tick) {
       TickKey key = new TickKey(
           tick.symbol(),
           tick.exchangeTimestamp(),  // Broker's timestamp (source of truth)
           tick.lastPrice(),
           tick.lastQty()
       );

       // Dedupe check
       if (recentTicks.contains(key)) {
           log.debug("Duplicate tick ignored: {}", key);
           metrics.increment("ticks.duplicate.ignored");
           return;
       }

       recentTicks.add(key);

       // Cleanup old keys (every minute, keep last 60 seconds)
       if (shouldCleanup()) {
           recentTicks.removeIf(k -> k.exchangeTs().isBefore(Instant.now().minusSeconds(60)));
       }

       // Process tick
       updateCandle(tick);
   }
   ```

   **Release Gate:**
   - If broker can resend ticks (reconnect/replay): ❌ P1 blocker
   - If candles drive exits (target/stop): ❌ P0 blocker
```

**Add to Section 8.2.1 (P0 TODOs):**

```markdown
| **P0-4** | TickCandleBuilder.java | 72 | Missing tick deduplication | **HIGH** - Corrupts candles if broker resends | ❌ BLOCKS RELEASE (if exits use candles) |
```

---

## UPGRADE 9: STATE MACHINE OWNERSHIP ENFORCEMENT

### Problem
State machine transitions could be scattered across multiple classes.

### Solution: Enforce Single Owner

**Add to Section 8.4.3 (after state machine definition):**

```markdown
**State Machine Ownership Enforcement (CRITICAL):**

**RULE:** Only `TradeRepository` (or `TradeService`) may persist state transitions.

Other components may REQUEST transitions, but MUST NOT write DB directly.

**Allowed Writers:**
- ✅ TradeRepository.upsert(trade) [single writer]
- ✅ TradeService (if it wraps TradeRepository)

**Forbidden Writers:**
- ❌ ExecutionOrchestrator (must call TradeRepository)
- ❌ ExitSignalService (must call TradeRepository)
- ❌ BrokerAdapter callbacks (must call TradeService/Repository)

**Code Pattern (Enforced):**

```java
// ✅ CORRECT: ExecutionOrchestrator requests transition
public void onOrderFilled(FillEvent fill) {
    Trade trade = tradeRepository.findByIntentId(fill.clientOrderId());
    Trade updated = trade.markFilled(fill.filledQty(), fill.avgPrice());
    tradeRepository.upsert(updated);  // ← Single writer
    eventService.emitUserBroker(EventType.ORDER_FILLED, updated);
}

// ❌ WRONG: Direct DB access from multiple places
public void onOrderFilled(FillEvent fill) {
    jdbcTemplate.update("UPDATE trades SET status = 'FILLED' WHERE ..."); // ← State scatter!
}
```

**Enforcement in Code Review:**
- ❌ Any SQL update on `trades` table outside TradeRepository → REJECT PR
- ❌ Any `trade.setStatus()` without `tradeRepository.upsert()` → REJECT PR
```

---

## UPGRADE 10: SIGNAL DEDUPE FLOAT-NOISE HANDLING

### Problem
Unique constraint on `(effective_floor, effective_ceiling)` could break if float precision varies.

### Solution: Fixed Precision or Rounded Columns

**Replace SQL in Section 13.4 "Resolution" with:**

```sql
-- Option 1: Fixed precision (RECOMMENDED)
CREATE TABLE signals (
    signal_id UUID PRIMARY KEY,
    symbol VARCHAR(50) NOT NULL,
    confluence_type VARCHAR(20) NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    effective_floor NUMERIC(18, 2) NOT NULL,  -- ✅ Fixed scale (2 decimals)
    effective_ceiling NUMERIC(18, 2) NOT NULL,
    -- ... other fields
);

-- Unique constraint (no float noise)
CREATE UNIQUE INDEX idx_signal_dedupe ON signals (
    symbol,
    confluence_type,
    DATE(generated_at),       -- Same calendar day
    effective_floor,          -- NUMERIC(18,2) - no rounding issues
    effective_ceiling
);

-- On conflict: Return existing signal (idempotent)
INSERT INTO signals (signal_id, symbol, confluence_type, generated_at, effective_floor, effective_ceiling, ...)
VALUES (?, ?, ?, ?, ROUND(?, 2), ROUND(?, 2), ...)  -- ✅ Round on insert
ON CONFLICT (symbol, confluence_type, DATE(generated_at), effective_floor, effective_ceiling)
DO UPDATE SET last_checked_at = NOW()
RETURNING *;

-- Option 2: Computed rounded columns (if float columns required elsewhere)
ALTER TABLE signals
    ADD COLUMN floor_rounded NUMERIC(18, 2) GENERATED ALWAYS AS (ROUND(effective_floor, 2)) STORED,
    ADD COLUMN ceiling_rounded NUMERIC(18, 2) GENERATED ALWAYS AS (ROUND(effective_ceiling, 2)) STORED;

CREATE UNIQUE INDEX idx_signal_dedupe ON signals (
    symbol,
    confluence_type,
    DATE(generated_at),
    floor_rounded,  -- ✅ Rounded computed column
    ceiling_rounded
);
```

**Enforcement:**
- ✅ Use `NUMERIC(18, 2)` for price fields in unique constraints
- ✅ Always `ROUND()` on insert if source is float
- ❌ Never use `FLOAT` or `DOUBLE` in unique constraints (float noise breaks dedupe)

---

## UPGRADE 11: RISK PROFILE LEAK PREVENTION

### Problem
Risk profiles should never leak into global Signal entity (signals are GLOBAL scope).

### Solution: Architectural Firewall

**Add to Section 14.1 (after "Where Profiles Apply"):**

```markdown
**Risk Profile Firewall (ENFORCEMENT):**

**RULE:** Signal entity MUST NOT contain user-specific or profile-specific fields.

**Allowed in Signal:**
- ✅ symbol, direction, entry/floor/ceiling (price levels)
- ✅ confluence_type, zones (MTF analysis)
- ✅ pWin, expectedValue (signal quality metrics)
- ✅ generated_at, status (signal lifecycle)

**FORBIDDEN in Signal:**
- ❌ userId, userBrokerId (user-specific)
- ❌ riskProfileId, maxQty, maxValue (profile-specific)
- ❌ approvedQty, capitalAllocated (sizing results)

**Where Profiles ARE Used:**
- ✅ ValidationService.validate() [consumes RiskProfile via UserBrokerContext]
- ✅ PositionSizingService [reads profile parameters from config]
- ✅ TradeIntent [stores sizing result per user-broker]

**Code Review Enforcement:**
```java
// ❌ REJECT this PR:
public class Signal {
    private String userId;  // ← LEAK! Signal is GLOBAL
    private BigDecimal maxQty;  // ← LEAK! This is sizing result
}

// ✅ APPROVE this:
public class Signal {
    private String symbol;
    private ConfluenceType confluenceType;
    // ... only global signal fields
}

public class TradeIntent {
    private String intentId;
    private String signalId;  // ← Reference to global signal
    private String userId;    // ← OK: Intent is per user-broker
    private BigDecimal approvedQty;  // ← OK: Sizing result
}
```

**Architecture Invariant:**
- Signal generation: Profile-blind (uses only price/confluence)
- Validation/sizing: Profile-aware (applies user-specific constraints)
- Trade intent: Captures profile-influenced result
```

---

## UPGRADE 12: P0 BLOCKER CLARIFICATION

### Problem
P0 TODOs could be hidden behind "paper trading mode" and still claim production-ready.

### Solution: Explicit Production Mode Requirement

**Add to Section 8.2.1 (after P0 TODO table):**

```markdown
**P0 TODO Release Gate (Clarification):**

**RULE:** P0 TODOs are release blockers UNLESS system explicitly runs in non-production mode.

**Production-Ready Criteria:**
```
1. production.mode=true (config flag)
2. All P0 TODOs resolved (no guards, no feature flags, no TODOs)
3. All critical paths fully implemented (no mocks, no paper trading)
```

**Non-Production Criteria (Acceptable):**
```
1. production.mode=false (config flag)
2. P0 TODOs MAY exist with explicit guards
3. Paper trading mode allowed
4. MUST log warnings: "NON-PRODUCTION MODE"
```

**Example (Current System):**

```java
// Current status
Config config = new Config();
config.productionMode = false;  // ← Non-production
config.orderExecutionEnabled = false;  // ← Paper trading

// P0 status:
P0-1: Order placement TODO → ⚠️ Acceptable if production.mode=false
P0-2: Position tracking mock → ⚠️ Acceptable if production.mode=false
P0-3: Signal dedupe missing → ⚠️ Acceptable if production.mode=false

// Release label:
✅ Can ship as: "BETA / PAPER TRADING"
❌ Cannot claim: "PRODUCTION READY"
```

**To Become Production-Ready:**
```
1. Set production.mode=true
2. Resolve all P0 TODOs (no guards)
3. Enable order execution
4. Replace mock position tracking with DB-backed
5. Add signal dedupe constraint
6. Startup validation gate enforces all above
```

**Documentation Update:**
- ✅ "Production-ready" = production.mode=true + all P0 resolved
- ⚠️ "Beta / Paper Trading" = production.mode=false + P0s guarded
- ❌ Cannot mix: production.mode=true with P0 TODOs (startup fails)
```

---

## STANDARDIZED ENGINE CARD SCHEMA (V2)

**Use this template for all engines in Section 8.1:**

```markdown
#### 8.1.X [ENGINE NAME] ENGINE

**Entry Points:**
[List all entry points with file:line]

**Outputs & Consumers:**
[For each output, list exact consumers]

**Reachability Proof (3 Parts REQUIRED):**

1. **Construction Proof:**
   - Component instantiation (file:line)

2. **Registration Proof:**
   - Callback/schedule/route registration (file:line)

3. **Invocation Proof:**
   - Evidence it ran (log grep / metric / test / manual)

**Status:**
- ✅ WIRED (all 3 proofs) / ⚠️ PARTIAL (2/3) / ❌ NOT WIRED (<2/3)

**Failure & Fallback:**

For each failure mode:
```
Failure: [Description]
  ├─ Detection: [How detected]
  ├─ Action: [What happens]
  └─ Fallback: [FAIL SAFE | AUTO-HEAL | DEGRADE]

     [If DEGRADE:]
     **Observability (REQUIRED):**
     1. Metric: [counter name]
     2. Log: [structured log with error code]
     3. Watchdog: [alert rule]
```

**Source of Truth + Reconciliation:**

| Artifact | Source of Truth | Cache | Reconciliation | Frequency |
|----------|----------------|--------|----------------|-----------|
| [Name] | [DB table + PK] | [Yes/No] | [Job description] | [Every N min] |

**Assessment:**
[✅ COMPLETE | ⚠️ PARTIAL | ❌ INCOMPLETE] - [Explanation]
```

---

## ENFORCEMENT CHECKLIST (For Code Reviews)

Use this checklist when reviewing PRs that touch critical components:

### For New Components:
☐ 3-part reachability proof provided?
☐ Construction, registration, invocation all proven?
☐ If stateful: Source of truth + reconciliation defined?
☐ All failure modes documented (FAIL SAFE / AUTO-HEAL / DEGRADE)?
☐ If DEGRADE: Metric + log + watchdog alert present?

### For Modified Components:
☐ SSOT map checked - no duplicate logic added?
☐ State transitions only via designated owner?
☐ Thread safety pattern (1-4) still correct?
☐ Idempotency mechanism unchanged or improved?

### For Configuration Changes:
☐ Production mode invariants checked?
☐ If production.mode=true: All P0s resolved?
☐ Startup validation gate enforces config invariants?

### For DB Schema Changes:
☐ Unique constraints for idempotency added?
☐ Fixed precision (NUMERIC) for price fields in constraints?
☐ Reconciliation jobs updated if needed?

---

## DOCUMENT INTEGRATION INSTRUCTIONS

To integrate these upgrades into COMPLETENESS_VERIFICATION_ADDENDUM.md:

### Step 1: Replace Sections
- Replace Section 8.1 templates with standardized schema
- Replace Section 8.2.2 with upgraded TODO gate (add 8.2.3)
- Replace Section 8.4.1 with 3-layer idempotency
- Replace Section 13.1 with enforcement gate
- Replace Section 13.2 with canonical list rule
- Replace Section 13.4 SQL with fixed precision

### Step 2: Add New Sections
- Add Section 8.4.6 (Reconciliation Requirements)
- Add Section 8.2.3 (Production Mode Invariant)
- Add enforcement rules to 8.4.3 (State Machine Ownership)
- Add firewall rules to 14.1 (Risk Profile Leak Prevention)

### Step 3: Update Examples
- Update all 8.1.X engine cards with 3-part reachability
- Add observability requirements to all DEGRADE paths
- Update 15.2 (TickCandleBuilder) with tick dedupe as P1/P0

### Step 4: Add P0-4
- Add tick deduplication to P0 TODO table (Section 8.2.1)

### Step 5: Update Version
- Change document version to 2.0
- Add "Enforcement-Ready" to status
- Update last review date

---

## VERIFICATION: "Is My Framework Ungameable?"

Run this checklist on your updated framework:

☐ Can someone claim "it's wired" without invocation proof? → ❌ Must have 3-part proof
☐ Can caches diverge without detection? → ❌ Must have reconciliation job
☐ Can broker thread block on DB write? → ❌ Startup gate enforces async writer
☐ Can docs say "N-point" when code has N+2? → ❌ Code is canonical
☐ Can system degrade silently? → ❌ Must have metric + log + alert
☐ Can start production without order execution? → ❌ Startup validation fails
☐ Can retry create duplicate orders? → ❌ DB uniqueness + broker reconciliation
☐ Can duplicate ticks corrupt candles? → ❌ Tick dedupe is P1/P0
☐ Can state transitions scatter? → ❌ Only TradeRepository writes
☐ Can float precision break dedupe? → ❌ NUMERIC with fixed scale
☐ Can risk profile leak into Signal? → ❌ Architectural firewall enforced
☐ Can hide P0s behind paper trading? → ❌ Production mode requires all P0s resolved

**If all ☐ answered "No" (blocked by framework):** ✅ Framework is ungameable!

---

**Document Version:** 2.0 (Enforcement-Ready)
**Date:** January 13, 2026
**Status:** ✅ Ready to integrate into COMPLETENESS_VERIFICATION_ADDENDUM.md
**Next Step:** Apply all upgrades, update version to V2
