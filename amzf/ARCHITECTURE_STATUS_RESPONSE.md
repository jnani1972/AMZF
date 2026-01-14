# Architecture Status Response - Complete System Audit

**Date**: January 13, 2026
**Version**: V010 + Trailing Stops
**Respondent**: Claude Code (after codebase analysis)

---

## 1) SYSTEM TRUTH AND OWNERSHIP

### 1.1 Single-writer map for each table

| Table | Single Writer | Status | Implementation |
|-------|--------------|--------|----------------|
| `signals` | **SignalManagementService** | ✅ Implemented | SMS only, via EntrySignalCoordinator (actor model) |
| `signal_deliveries` | **SignalManagementService** | ✅ Implemented | SMS only, after signal persisted |
| `trade_intents` | **ValidationService** | ✅ Implemented | Creates PENDING, ExecutionOrchestrator updates to APPROVED/REJECTED |
| `trades` | **TradeManagementService** (TMS) | ⚠️ **VIOLATION** | SHOULD be TMS only, but `OrderExecutionService` creates trades currently |
| `trade_fills` | **TMS** | ⚠️ **PARTIAL** | Table exists but not fully used (fills tracked in trade.avg_price) |
| `exit_signals` | **SignalManagementService** | ✅ Implemented | SMS only, via ExitSignalCoordinator (actor model) |
| `exit_intents` | **SignalManagementService** | ✅ Implemented | SMS creates, TMS updates on fill |
| `orders` table | **N/A** | ❌ Not implemented | No unified orders table exists |

**KEY FINDING**: TradeManagementService claims to be "ONLY" trade creator but **OrderExecutionService actually creates trades** (`OrderExecutionService.java:88`). This is a documentation vs reality mismatch.

### 1.2 Source of truth for trade state

**Answer**: **Directly mutated by services** (DB-first approach)

**Implementation**:
- Trade state stored in `trades` table with `status` column
- TradeManagementService reads from DB, never maintains in-memory state
- Each tick/event queries `tradeRepo.findBySymbol()` (ExitSignalService.java:62-64)
- State transitions via upsert pattern (immutable Trade records)
- Actor model (TradeCoordinator) ensures serial mutations per trade

**Source**: `TradeManagementServiceImpl.java:26-39`, `ExitSignalService.java:56-74`

### 1.3 Event bus semantics

**Answer**: **At-least-once delivery** (duplicates possible)

**Implementation**:
- `EventService` is fire-and-forget async delivery
- No acknowledgment or retry mechanism
- Handlers MUST be idempotent
- Pattern: "Persist first, then emit" (DB row exists before event)
- If emit fails after DB write, event is lost (no retry)

**Source**: Exit Qualification Architecture doc line 393: "Events are fire-and-forget (non-blocking)"

---

## 2) TICK → SIGNAL DETECTION PIPELINE

### 2.4 Tick entry point and ordering

**Implementation**:
- **Entry point**: `TickCandleBuilder` receives ticks from broker adapters
- **Deduplication**: Two-window dedupe (current + previous window) with bounded memory
- **Ordering guarantee**: ⚠️ **NONE** - ticks processed as received, no sequence number checking
- **Per-symbol ordering**: Implicit (single-threaded broker adapter), not enforced

**Source**: QUICKSTART_100_PERCENT_PROD_READY.md line 163

### 2.5 Entry detection cadence

**Answer**: **Candle-close-driven**

**Implementation**:
- `SignalService` analyzes completed candles (not individual ticks)
- MTF analysis requires 3 timeframes (HTF, ITF, LTF)
- Signal generated when candle closes in buy zone with confluence
- No tick-level entry signal generation

**Source**: SignalService structure (candle-based analysis)

### 2.6 Exit detection cadence

**Answer**: **Tick-driven + scheduled checks**

**Implementation**:
- **Tick-driven**: `ExitSignalService` implements `BrokerAdapter.TickListener`
- Checks on EVERY tick: target hit, stop loss, trailing stop
- **Scheduled checks**: Time-based exit (max hold days) computed on each tick
- No separate scheduler for exit checks (all tick-driven)

**Source**: `ExitSignalService.java:56-74` (onTick method)

### 2.7 Out-of-hours behavior

**Answer**: **Signals suppressed outside market hours**

**Implementation**:
- Market hours: 9:15 AM - 3:30 PM IST
- Entry signals suppressed in last 60 seconds before close
- Exit qualification enforces market hours per exit reason:
  - Stop loss: Anytime during 9:15-15:30
  - Target/manual: Avoid last 5 minutes (9:15-15:25)
- No buffering or recomputation at open

**Source**: `SignalManagementServiceImpl.java:121-124`, `ExitQualificationService.java:175-186`

---

## 3) SIGNAL QUALIFICATION BOUNDARY

### Entry Qualification

#### 3.8 Stage-1 vs Stage-2 qualification

**Implementation**:
- **Stage-1 (Symbol-level)**: Done in `SignalService` before signal creation
  - Confluence strength check
  - Zone validation (HTF/ITF/LTF)
  - ATR availability
  - ❌ **NOT** fully separated - mixed with signal generation

- **Stage-2 (User-broker eligibility)**: Done in `ValidationService`
  - Broker operational (active, connected)
  - Sufficient capital
  - Position sizing constraints (max log loss)
  - No overlapping positions
  - Utility asymmetry gate (3× advantage)

**Status**: ⚠️ **PARTIALLY SEPARATED** - Stage-1 not explicitly bounded

#### 3.9 ValidationService as sole decider

**Answer**: ✅ **YES**

**Implementation**:
- `ValidationService` is ONLY place that creates `TradeIntent` with APPROVED/REJECTED status
- Returns `ValidationResult` with pass/fail + reasons
- ExecutionOrchestrator calls ValidationService, respects decision
- No other service can approve/reject entry intents

**Source**: ValidationService interface contract

#### 3.10 Rebuy/newbuy classification

**Answer**: **During validation** (before intent creation)

**Implementation**:
- ValidationService checks for existing open trades on symbol
- If exists: classify as REBUY (re-entry/averaging)
- If not: classify as NEWBUY
- Stored in `trade_intents.trade_type` column
- TMS reads trade_type when creating trade

**Source**: Trade model line 19 (`tradeType` field)

### Exit Qualification

#### 3.11 Exit is symbol-generated, then qualified per trade

**Answer**: ⚠️ **PARTIALLY TRUE**

**Current State**:
- Exit conditions detected per-trade (NOT per-symbol)
- `ExitSignalService.onTick()` queries open trades for symbol
- Each trade checked individually for target/stop/time exit
- Exit qualification happens PER trade (not symbol-level)

**What's TRUE**: Exit detection IS per-symbol (tick arrives for symbol, check all open trades)
**What's FALSE**: Exit is NOT "symbol-generated" - it's trade-generated

**Source**: `ExitSignalService.java:71-73` (loops over open trades per symbol)

#### 3.12 Stage-1 (market condition) vs Stage-2 (execution qualification)

**Implementation**:
- **Stage-1 (Market condition met)**: Done in `ExitSignalService`
  - Price hit target/stop/trailing stop
  - Max hold time exceeded
  - Brick movement filter allows exit
  - ✅ **BOUNDED** - separate from execution

- **Stage-2 (Execution qualification)**: Done in `ExitQualificationService`
  - Broker operational
  - Trade state valid (OPEN)
  - Direction consistency (BUY trade → SELL exit)
  - No pending exit orders
  - Market hours check
  - Order type determination (MARKET vs LIMIT)

**Status**: ✅ **WELL SEPARATED**

**Source**: `ExitQualificationService.java:55-137`

#### 3.13 "One pending exit order per trade" rule

**Answer**: ✅ **YES** - Enforced in code + aspiration for DB

**Implementation**:
- **Code enforcement**: `ExitQualificationService.hasPendingExitOrder()` checks for existing pending/approved/placed exits
- **DB enforcement**: ❌ **NOT YET** - No unique constraint on exit_intents
- Unique constraint exists: `UNIQUE (trade_id, user_broker_id, exit_reason, episode_id)`
  - But this allows MULTIPLE pending exits with DIFFERENT reasons!
  - Example: TARGET_HIT (pending) + STOP_LOSS (pending) = allowed by DB, blocked by code

**Gap**: DB constraint doesn't enforce "one pending exit per trade" - it enforces "one pending exit per trade per reason per episode"

**Source**: `ExitQualificationService.java:167-170`, EXIT_QUALIFICATION_ARCHITECTURE.md line 45-47

---

## 4) INTENT → ORDER EXECUTION OWNERSHIP

#### 4.14 Entry: Who converts TradeIntent(APPROVED) → order placement?

**Answer**: **OrderExecutionService** (via ExecutionOrchestrator)

**Flow**:
1. ValidationService creates TradeIntent(APPROVED)
2. ExecutionOrchestrator.onIntentApproved() called
3. ExecutionOrchestrator delegates to OrderExecutionService
4. OrderExecutionService:
   - Creates trade row (status=CREATED)
   - Places order with broker
   - Updates trade status based on broker response

**Status**: ⚠️ **VIOLATES TMS OWNERSHIP** - OrderExecutionService creates trades, but TMS interface says "ONLY this service can create trade rows"

**Source**: `OrderExecutionService.java:27-39`, `TradeManagementService.java:12`

#### 4.15 Exit: Who converts ExitIntent(APPROVED) → order placement?

**Answer**: ⚠️ **NOT FULLY IMPLEMENTED**

**Current State**:
- ExitIntent created with APPROVED status by SignalManagementService
- ❌ **NO ORDER PLACEMENT CODE** - exit order placement logic missing
- TMS has `onBrokerOrderUpdate()` method but no exit order creation
- Gap: ExitIntent(APPROVED) → ??? → Order placement

**What EXISTS**:
- Exit qualification (ExitQualificationService)
- Exit intent creation (SignalManagementService)
- Exit intent table with broker_order_id, placed_at fields
- DB function `place_exit_order()` for atomic transition

**What's MISSING**:
- Service that reads APPROVED exit intents and places orders
- Reconciler for exit orders
- Exit order status tracking

**Source**: EXIT_QUALIFICATION_ARCHITECTURE.md line 79-82 (DB function exists but no service calls it)

#### 4.16 Unified Order table

**Answer**: ❌ **NO** - No unified orders table

**Current State**:
- Entry orders: Tracked in `trades` table (order_id column)
- Exit orders: Tracked in `exit_intents` table (broker_order_id column)
- No separate `orders` table for unified tracking

**Trade-offs**:
- ✅ Simpler (no joins needed)
- ❌ No unified order history view
- ❌ Can't query "all orders across entry/exit"
- ❌ Harder to implement order-level reconciliation

**Source**: Trade model (order_id field), ExitIntent model (broker_order_id field)

---

## 5) FILL DETECTION, PARTIAL FILLS, AND P&L

#### 5.17 How are fills detected?

**Answer**: **Broker websocket fills + polling reconciler**

**Implementation**:
- **Primary**: Broker adapter pushes order updates via websocket
- **Secondary**: `PendingOrderReconciler` polls every 30 seconds
- Reconciler queries broker for order status, heals state drift
- No separate "fill detection service" - integrated into order flow

**Source**: QUICKSTART_100_PERCENT_PROD_READY.md line 164 (reconciliation)

#### 5.18 Where are fills stored?

**Answer**: **b) In trade_fills table** (partial) + **a) As trade.avg_price updates** (primary)

**Implementation**:
- `trade_fills` table exists in schema
- ⚠️ **NOT FULLY USED** - fills primarily tracked as:
  - `trades.entry_price` (average fill price)
  - `trades.entry_qty` (total filled quantity)
- trade_fills table intended for partial fill tracking but not actively populated
- Exit fills tracked in `trades.exit_price`

**Gap**: Fill-level granularity lost, can't reconstruct fill history

#### 5.19 Partial fills

**Answer**: ⚠️ **PARTIALLY SUPPORTED** (intent, not execution)

**Current State**:
- Entry partials: Code checks `filledQty` vs `pendingQty` in broker updates
- Exit partials: ❌ **NOT SUPPORTED** - ExitQualificationService always exits full position (`exitQty = trade.entryQty`)
- No staged exit logic (e.g., 50% at target, 50% at stretch)

**Source**: `ExitQualificationService.java:116` (exits full position always)

#### 5.20 Who closes trade and computes P&L?

**Answer**: ✅ **TMS only** (TradeManagementService)

**Implementation**:
- TMS.onBrokerOrderUpdate() handles exit fill
- State transition: EXITING → CLOSED
- P&L computed: `realizedPnl = exitPrice - entryPrice (× qty)`
- ONLY TMS writes final trade state

**Status**: ✅ **CORRECT** - confirmed in TradeManagementService interface

**Source**: `TradeManagementService.java:16` (ONLY this service can close trades)

#### 5.21 P&L computed from fills or LTP?

**Answer**: **From fills** (preferred)

**Implementation**:
- P&L uses `trades.exit_price` (actual fill price from broker)
- NOT computed from LTP
- Formula: `(exit_price - entry_price) × entry_qty`

**Source**: Trade model (realizedPnl field computed from exit_price)

---

## 6) IDEMPOTENCY AND REPLAY SAFETY

#### 6.22 Idempotency keys + unique constraints

| Entity | Idempotency Key | DB Constraint | Status |
|--------|----------------|---------------|--------|
| **Signal** (entry) | (symbol, direction, zone_signature, trading_day) | ❌ **MISSING** | V007 migration intended, not deployed |
| **Delivery** | (signal_id, user_broker_id) | ✅ **EXISTS** | UNIQUE constraint |
| **TradeIntent** | (signal_delivery_id, user_broker_id) | ⚠️ **UNKNOWN** | Need to verify |
| **ExitSignal** | (trade_id, exit_reason, episode_id) | ✅ **EXISTS** | Unique constraint |
| **ExitIntent** | (trade_id, user_broker_id, exit_reason, episode_id) | ✅ **EXISTS** | UNIQUE constraint (V010) |

**Critical Gaps**:
1. Entry signal deduplication NOT enforced at DB (relies on in-memory supersession)
2. TradeIntent idempotency unclear (need to check schema)

**Source**: EXIT_QUALIFICATION_ARCHITECTURE.md line 44-47, SignalManagementServiceImpl supersession logic

#### 6.23 Replay plan - finding "stuck" items

**Answer**: ⚠️ **PARTIALLY IMPLEMENTED**

**What EXISTS**:
- Reconciler for pending entry orders (every 30 seconds)
- Signal expiry scheduler (every 1 minute) in SMS
- No reconciler for exit orders (gap)

**What's MISSING**:
- Stuck delivery detection (PENDING deliveries never consumed)
- Stuck exit intent detection (APPROVED but never placed)
- Trade heartbeat (open trades with no recent updates)
- Dead-letter queue for failed items

**Queries NEEDED** (provided in EXIT_QUALIFICATION_ARCHITECTURE.md):
```sql
-- Find stuck exit intents
SELECT * FROM exit_intents
WHERE status IN ('PENDING', 'APPROVED')
  AND created_at < NOW() - INTERVAL '5 minutes';
```

**Source**: EXIT_QUALIFICATION_ARCHITECTURE.md line 283-289

#### 6.24 Cooldowns/re-arm: in-memory or DB?

**Answer**: **HYBRID** (inconsistent)

**Entry Signals**:
- ❌ **IN-MEMORY** - `lastProcessedTimes` ConcurrentHashMap in SMS
- Lost on restart (re-arm state not persisted)
- Timestamp guard prevents out-of-order processing

**Exit Signals**:
- ✅ **DB-LEVEL** - `generate_exit_episode()` function checks DB for last exit time
- Restart-safe: cooldown enforced by timestamp comparison in DB
- 30-second cooldown per (trade_id, exit_reason)

**Gap**: Entry signal cooldown NOT restart-safe

**Source**: `SignalManagementServiceImpl.java:61-64` (in-memory), EXIT_QUALIFICATION_ARCHITECTURE.md line 59-72 (DB function)

---

## 7) FAILURE HANDLING AND RETRIES

#### 7.25 DB write succeeds but event emit fails

**Answer**: **Event is lost** (no retry)

**Implementation**:
- Pattern: "Persist first, then emit"
- If persist succeeds but emit fails, DB row exists but event never delivered
- No retry mechanism
- No dead-letter queue
- Impact: UI won't update, but DB state is correct

**Workaround**: Reconciler eventually heals state via polling

**Source**: Event emission is fire-and-forget (EventService structure)

#### 7.26 Event emit succeeds but DB write fails

**Answer**: **Cannot happen** (impossible by design)

**Reason**: "Persist first, then emit" pattern enforced everywhere
- DB write happens first
- Emit only called after successful DB write
- If DB write fails, exception thrown before emit

**Source**: SignalManagementServiceImpl pattern (create row, then emit)

#### 7.27 Entry order placement failure

**Answer**: **Trade marked REJECTED**

**Flow**:
1. OrderExecutionService creates trade (status=CREATED)
2. Broker order placement fails (exception or immediate rejection)
3. Trade updated to status=REJECTED via `tradeRepo.markRejectedByIntentId()`
4. TradeIntent updated to FAILED
5. No retry (terminal state)

**Source**: `OrderExecutionService.java:30` (rejection path uses UPDATE)

#### 7.28 Exit order placement failure

**Answer**: ⚠️ **NOT IMPLEMENTED** (exit order placement missing)

**Expected Behavior** (from architecture):
- ExitIntent status → FAILED
- Exit signal status → (unclear)
- Trade status → remains OPEN
- Retry via operator or reconciler

**Gap**: Since exit order placement doesn't exist yet, this flow is incomplete

#### 7.29 Retry policy

**Answer**: **Reconciler** (30-second polling)

**Implementation**:
- Entry orders: `PendingOrderReconciler` retries state sync every 30 seconds
- Exit orders: ❌ **NO RECONCILER** (gap)
- No exponential backoff
- No max retry limit (will retry forever until success)
- Manual operator intervention needed for permanent failures

**Source**: QUICKSTART_100_PERCENT_PROD_READY.md line 164

#### 7.30 Dead-letter / quarantine

**Answer**: ⚠️ **PARTIAL** - Terminal states exist, no quarantine mechanism

**Implementation**:
- Terminal states: REJECTED, FAILED, CANCELLED
- Can query for these states via SQL
- ❌ **NO QUARANTINE TABLE** - failed items stay in main tables
- ❌ **NO OPERATOR DASHBOARD** for failed items
- ❌ **NO ALERTING** on high failure rates

**Operator visibility**: Must manually query database

**Source**: Status enums (REJECTED, FAILED states exist)

---

## 8) MULTI-USER + MULTI-BROKER SEMANTICS

#### 8.31 One DATA broker for everyone?

**Answer**: ✅ **YES**

**Implementation**:
- Admin (or designated user) has one DATA broker configured
- DATA broker role: `BrokerRole.DATA`
- Ticks from DATA broker flow to all users
- Signals generated from DATA broker feed
- Each signal delivered to each user's EXEC brokers

**Source**: `UserBroker` model has `brokerRole` field (DATA vs EXEC)

#### 8.32 Multiple EXEC brokers per user?

**Answer**: ✅ **YES** (allowed)

**Implementation**:
- User can have multiple `user_broker` records with role=EXEC
- Each user-broker can receive signal deliveries
- Each user-broker can execute trades independently
- Capital allocated per user-broker
- Portfolio tracking per user-broker

**Source**: `user_brokers` table design (user_id + broker_id + role)

#### 8.33 Single signal → multiple trades for same user?

**Answer**: ✅ **YES** (one trade per user-broker)

**Flow**:
1. Signal generated (symbol=RELIANCE, direction=BUY)
2. Deliveries created for each active EXEC user-broker
3. User with 2 EXEC brokers → 2 deliveries
4. Each delivery validated independently
5. Can result in 2 trades (one per broker) IF both qualify

**Use case**: User has Zerodha + Fyers, both execute same signal

**Source**: Signal delivery fanout logic in SMS

---

## 9) UI AND OBSERVABILITY

#### 9.34 UI shows as ground truth

**Current State** (based on codebase):

| Entity | UI View | Source |
|--------|---------|--------|
| **Active signals** | ✅ Shown | `signals` table (status=PUBLISHED) |
| **Deliveries** | ⚠️ **UNKNOWN** | Need frontend verification |
| **Intents** | ⚠️ **UNKNOWN** | Frontend may show intents |
| **Orders** | ❌ **NOT UNIFIED** | Trade.order_id shown, no unified order view |
| **Trades** | ✅ Shown | `trades` table with full lifecycle |

**Gap**: No unified order tracking view (entry + exit orders)

#### 9.35 Metrics currently emitted

**Answer**: ⚠️ **EVENT-BASED, NOT METRICS** (no Prometheus/Grafana)

**Current State**:
- Events emitted to `event_log` table or event stream
- No metrics collection (counters, gauges, histograms)
- No Prometheus endpoint
- No Grafana dashboards
- Observability via:
  - Database queries
  - Application logs
  - Event log table

**Gap**: No real-time metrics dashboard

**Source**: EventService emits events, no metrics library integrated

#### 9.36 Top 3 production fears

**Based on Architecture**:

1. **Duplicate trades** - Entry signal idempotency not DB-enforced (in-memory supersession can fail on restart)
2. **Missed exits** - Exit order placement not implemented, exits stuck in APPROVED state forever
3. **Stuck orders** - No exit order reconciler, orders can be placed but never tracked to completion

**Source**: Gap analysis above

---

## 10) COMPLIANCE INVARIANTS

#### 10.37 Invariants currently enforced

| Invariant | Enforced? | How? |
|-----------|-----------|------|
| **No PUBLISHED without persisted row** | ✅ **YES** | "Persist first, then emit" pattern everywhere |
| **No delivery without published signal** | ✅ **YES** | SMS creates deliveries only for PUBLISHED signals |
| **No exit signal unless trade is OPEN** | ✅ **YES** | ExitSignalService filters for `trade.isOpen()` |
| **No trade without approved trade_intent** | ✅ **YES** | OrderExecutionService requires TradeIntent(APPROVED) |
| **No trade close without fill(s)** | ✅ **YES** | TMS only closes on exit fill (EXITING → CLOSED) |

**Source**: Code pattern analysis (persist-then-emit), ExitSignalService.java:62-64 (isOpen filter)

#### 10.38 Aspirational invariants (not enforced)

1. **No duplicate signals** - In-memory supersession, NOT DB-enforced (V007 migration not deployed)
2. **One pending exit per trade** - Code checks, but DB allows multiple exits with different reasons
3. **Exit order tracking** - Exit orders not reconciled (no exit order reconciler)
4. **Fill-level audit trail** - trade_fills table exists but not populated
5. **Event delivery guarantee** - At-least-once intended, but lost events not retried

---

## CRITICAL GAPS SUMMARY

### 🔴 HIGH PRIORITY (Correctness Risks)

1. **Exit Order Placement Missing** - ExitIntent(APPROVED) → ??? (no service places exit orders)
2. **Exit Order Reconciler Missing** - Placed exit orders never tracked to completion
3. **Trade Creation Ownership Violated** - OrderExecutionService creates trades, not TMS (violates stated contract)
4. **Entry Signal Idempotency** - In-memory supersession, lost on restart (not DB-enforced)
5. **Cooldown Inconsistency** - Entry cooldown in-memory (lost on restart), exit cooldown in DB (restart-safe)

### 🟡 MEDIUM PRIORITY (Operational Gaps)

6. **No Unified Order Table** - Entry/exit orders tracked separately, no unified view
7. **Partial Fill Support Incomplete** - Entry partials detected but not fully handled, exit partials not supported
8. **No Dead-Letter Queue** - Failed items stay in main tables, no quarantine mechanism
9. **Event Retry Missing** - Lost events never retried (at-least-once not guaranteed)
10. **No Metrics/Monitoring** - Observability via DB queries only, no real-time metrics

### 🟢 LOW PRIORITY (Nice-to-Have)

11. **trade_fills Not Populated** - Fill history lost, can't reconstruct partial fills
12. **No Operator Dashboard** - Manual DB queries needed for failure investigation
13. **No Alerting** - High failure rates go unnoticed

---

## RECOMMENDATIONS FOR TMS/SMS SYMMETRY

### Phase 1: Fix Critical Gaps (Week 1)

1. **Implement Exit Order Placement**
   - Create `ExitOrderExecutionService` (mirrors OrderExecutionService)
   - Reads APPROVED exit intents, places exit orders
   - Updates exit_intent status to PLACED
   - Uses DB function `place_exit_order()` for atomic transition

2. **Implement Exit Order Reconciler**
   - Polls broker every 30 seconds for exit order status
   - Updates exit_intent and trade state
   - Heals state drift (same as entry order reconciler)

3. **Fix Trade Creation Ownership**
   - **Option A**: Move trade creation from OrderExecutionService into TMS
   - **Option B**: Rename TMS docs to reflect reality (OrderExecutionService creates trades)
   - **Recommended**: Option A (enforce stated contract)

### Phase 2: Enforce Idempotency (Week 2)

4. **Deploy Entry Signal Deduplication**
   - Apply V007 migration (unique constraint on signals table)
   - Remove in-memory supersession (rely on DB constraint)
   - Restart-safe idempotency

5. **Fix Cooldown Consistency**
   - Move entry signal cooldown to DB (generate_entry_episode function)
   - OR document in-memory cooldown as acceptable trade-off
   - Consistent approach across entry/exit

### Phase 3: Unified Order Tracking (Week 3)

6. **Create Unified Orders Table**
   ```sql
   CREATE TABLE orders (
     order_id UUID PRIMARY KEY,
     order_type VARCHAR(10), -- 'ENTRY' | 'EXIT'
     trade_id UUID,
     intent_id UUID,
     broker_order_id VARCHAR(50),
     status VARCHAR(20),
     ...
   );
   ```
   - Central order tracking
   - Unified reconciler
   - Complete audit trail

---

## NEXT STEPS

To complete this audit, I need you to clarify:

1. **Is OrderExecutionService creating trades acceptable?** (violates TMS contract but works)
2. **Priority order for fixing gaps** (which 3 gaps matter most for your launch?)
3. **Timeline constraints** (how much time before production?)
4. **Risk tolerance** (can you launch with exit orders not implemented?)

Once you clarify, I can provide:
- Detailed refactor plan with code changes
- Migration strategy (how to fix without breaking existing system)
- Test plan for each gap fix
- Updated architecture diagrams showing corrected ownership

---

**Prepared by**: Claude Code
**Review Status**: Ready for user feedback
**Confidence Level**: High (based on codebase analysis + architecture docs)
