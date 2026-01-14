# P0 Gates Verification Guide

## Overview

This document provides comprehensive verification steps for all 7 P0 production readiness gates.

**Current P0 Gates Status:**

| Gate | Status | Implementation |
|------|--------|----------------|
| ORDER_EXECUTION_IMPLEMENTED | ✅ TRUE | P0-E: OrderExecutionService with single-writer pattern |
| POSITION_TRACKING_LIVE | ✅ TRUE | ExitSignalService queries TradeRepository (not HashMap) |
| BROKER_RECONCILIATION_RUNNING | ✅ TRUE | P0-C: PendingOrderReconciler instantiated and started |
| TICK_DEDUPLICATION_ACTIVE | ✅ TRUE | P0-D: Two-window dedupe in TickCandleBuilder |
| SIGNAL_DB_CONSTRAINTS_APPLIED | ⏳ FALSE | TODO: Run V007 migration and verify |
| TRADE_IDEMPOTENCY_CONSTRAINTS | ⏳ FALSE | TODO: Run V007 migration and verify |
| ASYNC_EVENT_WRITER_IF_PERSIST | ✅ TRUE | Async event writer enabled |

**PROD_READY Enforcement:**
- When `release.readiness=PROD_READY`, system will NOT start unless ALL gates are TRUE
- See: `StartupConfigValidator.java` and `P0DebtRegistry.java`

---

## Gate 1: ORDER_EXECUTION_IMPLEMENTED ✅

**Status:** TRUE

**Implementation:** P0-E: Single-Writer Trade State
- `OrderExecutionService.java` - Single writer for trade creation
- `TradeRepository.markRejectedByIntentId()` - Rejection path
- Trade always created in CREATED state before broker call

**Verification Steps:**

1. Check OrderExecutionService exists:
   ```bash
   ls -la src/main/java/in/annupaper/service/execution/OrderExecutionService.java
   ```

2. Verify single-writer pattern:
   ```bash
   grep -rn "tradeRepo\.insert" src/main/java/
   # Should only find: OrderExecutionService.java:88
   ```

3. Test order execution flow:
   - Submit approved intent → OrderExecutionService.executeIntent()
   - Verify trade created with status=CREATED (query DB immediately)
   - Broker accepts → verify only ONE trade row exists
   - Broker rejects → verify markRejectedByIntentId called (UPDATE not INSERT)

**Acceptance Criteria:**
- ✅ Only OrderExecutionService creates trade rows
- ✅ Trade always created in CREATED state first
- ✅ Rejection uses UPDATE (markRejectedByIntentId)
- ✅ No duplicate trades (intent idempotency enforced)

**Test Guide:** See `P0E_SINGLE_WRITER_TRADE_STATE_TEST.md`

---

## Gate 2: POSITION_TRACKING_LIVE ✅

**Status:** TRUE

**Implementation:** ExitSignalService queries TradeRepository
- Removed HashMap-based trade tracking
- `onTick()` queries `TradeRepository.findBySymbol()` for open trades
- DB is single source of truth for position tracking

**Verification Steps:**

1. Check ExitSignalService uses TradeRepository:
   ```bash
   grep "TradeRepository" src/main/java/in/annupaper/service/signal/ExitSignalService.java
   # Should find: "private final TradeRepository tradeRepo;"
   ```

2. Verify no HashMap usage:
   ```bash
   grep "HashMap.*Trade\|Map<String, OpenTrade>" \
        src/main/java/in/annupaper/service/signal/ExitSignalService.java
   # Should return: No matches
   ```

3. Test position tracking:
   - Open trade in DB (status=OPEN)
   - Send tick for that symbol
   - Verify ExitSignalService queries DB (check logs: "Query DB for open trades")
   - Verify exit conditions checked against DB trade (not HashMap)

**Acceptance Criteria:**
- ✅ ExitSignalService has TradeRepository dependency
- ✅ No HashMap<String, OpenTrade> in ExitSignalService
- ✅ onTick() queries TradeRepository.findBySymbol()
- ✅ DB is source of truth for open positions

---

## Gate 3: BROKER_RECONCILIATION_RUNNING ✅

**Status:** TRUE

**Implementation:** P0-C: Pending Order Reconciler
- `PendingOrderReconciler.java` - Reconciles CREATED/PENDING trades
- Started in `App.java` (lines 297-299, 417-418)
- Runs every 30 seconds

**Verification Steps:**

1. Check PendingOrderReconciler exists:
   ```bash
   ls -la src/main/java/in/annupaper/service/execution/PendingOrderReconciler.java
   ```

2. Verify wired up in App.java:
   ```bash
   grep "PendingOrderReconciler" src/main/java/in/annupaper/bootstrap/App.java
   # Should find instantiation and .start() call
   ```

3. Test reconciliation:
   - Start system
   - Create trade in CREATED state with broker_order_id set
   - Wait 30 seconds (reconciler interval)
   - Verify reconciler updates trade status to PENDING or OPEN
   - Check logs: "Reconciling X pending trades"

**Acceptance Criteria:**
- ✅ PendingOrderReconciler instantiated in App.java
- ✅ start() method called during application startup
- ✅ Reconciler runs every 30 seconds
- ✅ Updates trades using last_broker_update_at (not created_at)
- ✅ Rate limiting active (max 5 concurrent broker calls)

**Test Guide:** See `P0C_BROKER_RECONCILIATION_TEST.md`

---

## Gate 4: TICK_DEDUPLICATION_ACTIVE ✅

**Status:** TRUE

**Implementation:** P0-D: Two-Window Tick Deduplication
- `TickCandleBuilder.java` - Two-window dedupe pattern
- Primary key: symbol + exchange timestamp + price + volume
- Fallback key: symbol + system time + price + volume
- Windows rotate every 60 seconds (bounded memory)

**Verification Steps:**

1. Check dedupe implementation exists:
   ```bash
   grep "currentDedupeWindow\|previousDedupeWindow" \
        src/main/java/in/annupaper/service/candle/TickCandleBuilder.java
   # Should find both windows declared
   ```

2. Test deduplication:
   - Send duplicate tick (same symbol, timestamp, price, volume)
   - Verify second tick rejected with log: "Duplicate tick detected"
   - Check metrics: `duplicateTicks` incremented
   - Verify only ONE candle update (not two)

3. Test window rotation:
   - Process ticks for 60 seconds
   - At 60-second mark, verify log: "Dedupe windows rotated"
   - Verify memory bounded (windows don't grow indefinitely)

4. Test fallback key:
   - Send tick with timestamp=0 (missing exchange timestamp)
   - Verify fallback key used: `SYMBOL|SYS:epochSeconds|price|volume`
   - Check metrics: `missingExchangeTimestamp` incremented

**Acceptance Criteria:**
- ✅ Two-window pattern implemented (current + previous)
- ✅ Primary dedupe key uses exchange timestamp
- ✅ Fallback key uses system time (when exchange timestamp missing)
- ✅ Windows rotate every 60 seconds
- ✅ Memory bounded (no unbounded growth)
- ✅ Metrics API available: getDedupeMetrics()

**Test Guide:** See `P0D_TICK_DEDUPLICATION_TEST.md`

---

## Gate 5: SIGNAL_DB_CONSTRAINTS_APPLIED ⏳

**Status:** FALSE (requires V007 migration)

**Implementation:** V007 migration adds signal deduplication constraints
- Unique index on: (symbol, confluence_type, signal_day, effective_floor, effective_ceiling)
- Generated column: signal_day = DATE(generated_at)
- 2-decimal precision: NUMERIC(18,2) for floor/ceiling
- CHECK constraints: ensure 2-decimal precision

**Migration File:** `sql/V007__add_idempotency_constraints.sql` (lines 81-159)

**Verification Steps:**

1. Check V007 migration exists:
   ```bash
   ls -la sql/V007__add_idempotency_constraints.sql
   ```

2. Run V007 migration:
   ```bash
   # Using Flyway
   flyway migrate

   # Or manually
   psql -U postgres -d annupaper -f sql/V007__add_idempotency_constraints.sql
   ```

3. Verify constraints applied:
   ```sql
   -- Check signal_day generated column exists
   SELECT column_name, data_type, is_generated, generation_expression
   FROM information_schema.columns
   WHERE table_name = 'signals'
     AND column_name = 'signal_day';

   -- Expected:
   -- column_name: signal_day
   -- data_type: date
   -- is_generated: ALWAYS
   -- generation_expression: DATE(generated_at)

   -- Check unique index exists
   SELECT indexname, indexdef
   FROM pg_indexes
   WHERE tablename = 'signals'
     AND indexname = 'idx_signal_dedupe';

   -- Expected:
   -- indexname: idx_signal_dedupe
   -- indexdef: CREATE UNIQUE INDEX idx_signal_dedupe ON signals
   --           USING btree (symbol, confluence_type, signal_day,
   --                        effective_floor, effective_ceiling)

   -- Check CHECK constraints
   SELECT conname, pg_get_constraintdef(oid)
   FROM pg_constraint
   WHERE conrelid = 'signals'::regclass
     AND conname LIKE 'chk_effective_%';

   -- Expected:
   -- chk_effective_floor_precision
   -- chk_effective_ceiling_precision
   ```

4. Test signal deduplication:
   ```java
   // Insert duplicate signal (should succeed idempotently)
   Signal signal1 = new Signal(..., effectiveFloor=2400.00, effectiveCeiling=2500.00);
   signalRepo.upsert(signal1);

   Signal signal2 = new Signal(..., effectiveFloor=2400.00, effectiveCeiling=2500.00);
   signalRepo.upsert(signal2);  // Should update existing, not insert new

   // Verify only 1 row
   List<Signal> signals = signalRepo.findBySymbol("RELIANCE");
   assertEquals(1, signals.size());
   ```

**Acceptance Criteria:**
- ✅ V007 migration run successfully
- ✅ signal_day generated column exists
- ✅ Unique dedupe index exists
- ✅ CHECK constraints enforce 2-decimal precision
- ✅ Signal upsert works idempotently

**After Verification:**
```java
// Update P0DebtRegistry.java line 26:
"SIGNAL_DB_CONSTRAINTS_APPLIED", true,  // ✅ V007 migration applied and verified
```

---

## Gate 6: TRADE_IDEMPOTENCY_CONSTRAINTS ⏳

**Status:** FALSE (requires V007 migration)

**Implementation:** V007 migration adds trade idempotency constraints
- Unique constraint: `uq_trades_intent_id` (one trade per intent)
- Unique constraint: `uq_trades_client_order_id` (broker idempotency)
- Partial unique index: `uq_trades_broker_order_id` (WHERE broker_order_id IS NOT NULL)
- Columns added: `intent_id`, `client_order_id`, `last_broker_update_at`

**Migration File:** `sql/V007__add_idempotency_constraints.sql` (lines 18-80)

**Verification Steps:**

1. Check V007 migration exists:
   ```bash
   ls -la sql/V007__add_idempotency_constraints.sql
   ```

2. Run V007 migration:
   ```bash
   # Using Flyway
   flyway migrate

   # Or manually
   psql -U postgres -d annupaper -f sql/V007__add_idempotency_constraints.sql
   ```

3. Verify constraints applied:
   ```sql
   -- Check unique constraints exist
   SELECT conname, contype, pg_get_constraintdef(oid)
   FROM pg_constraint
   WHERE conrelid = 'trades'::regclass
     AND conname LIKE 'uq_trades_%';

   -- Expected:
   -- uq_trades_intent_id: UNIQUE (intent_id)
   -- uq_trades_client_order_id: UNIQUE (client_order_id)

   -- Check partial unique index on broker_order_id
   SELECT indexname, indexdef
   FROM pg_indexes
   WHERE tablename = 'trades'
     AND indexname = 'uq_trades_broker_order_id';

   -- Expected:
   -- CREATE UNIQUE INDEX uq_trades_broker_order_id ON trades
   -- USING btree (broker_order_id) WHERE (broker_order_id IS NOT NULL)

   -- Check columns exist
   SELECT column_name, data_type
   FROM information_schema.columns
   WHERE table_name = 'trades'
     AND column_name IN ('intent_id', 'client_order_id', 'last_broker_update_at');

   -- Expected: 3 rows returned
   ```

4. Test trade idempotency:
   ```java
   // Test intent_id uniqueness
   Trade trade1 = new Trade(..., intentId="intent-123", ...);
   tradeRepo.insert(trade1);

   Trade trade2 = new Trade(..., intentId="intent-123", ...);
   // Should throw exception: duplicate key value violates unique constraint "uq_trades_intent_id"
   assertThrows(() -> tradeRepo.insert(trade2));

   // Test upsert (should update existing)
   Trade updated = tradeRepo.upsert(trade2);
   assertNotNull(updated);

   // Verify only 1 row
   List<Trade> trades = tradeRepo.findAll();
   assertEquals(1, trades.stream().filter(t -> "intent-123".equals(t.intentId())).count());
   ```

**Acceptance Criteria:**
- ✅ V007 migration run successfully
- ✅ Unique constraint on intent_id exists
- ✅ Unique constraint on client_order_id exists
- ✅ Partial unique index on broker_order_id exists
- ✅ Columns intent_id, client_order_id, last_broker_update_at exist
- ✅ Trade upsert works idempotently

**After Verification:**
```java
// Update P0DebtRegistry.java line 27:
"TRADE_IDEMPOTENCY_CONSTRAINTS", true,  // ✅ V007 migration applied and verified
```

---

## Gate 7: ASYNC_EVENT_WRITER_IF_PERSIST ✅

**Status:** TRUE

**Implementation:** Async event writer enabled for event persistence
- If `persist.tickEvents=true`, events written asynchronously
- Prevents blocking tick processing

**Verification Steps:**

1. Check event persistence configuration:
   ```bash
   # Check config file for persist.tickEvents setting
   grep "persist.tickEvents" config/*.properties
   ```

2. If `persist.tickEvents=true`, verify async writer:
   ```bash
   grep "AsyncEventWriter\|CompletableFuture.*Event" \
        src/main/java/in/annupaper/service/core/EventService.java
   ```

3. Test async event writing:
   - Enable event persistence: `persist.tickEvents=true`
   - Send ticks (generate TICK events)
   - Verify events written asynchronously (logs: "Event persisted asynchronously")
   - Verify tick processing not blocked

**Acceptance Criteria:**
- ✅ If persist.tickEvents=false, no async writer needed (gate = true)
- ✅ If persist.tickEvents=true, async writer must be enabled
- ✅ Events persisted without blocking tick processing

---

## Production Readiness Checklist

Before setting `release.readiness=PROD_READY`, verify ALL gates:

### Code Verification
- [ ] All 5 code gates implemented and set to TRUE in P0DebtRegistry
- [ ] No HashMap-based trade tracking (ExitSignalService uses DB)
- [ ] OrderExecutionService is single writer for trades
- [ ] PendingOrderReconciler started in App.java
- [ ] TickCandleBuilder has two-window dedupe

### Database Migration Verification
- [ ] Run V007 migration: `flyway migrate`
- [ ] Verify signal constraints: check signal_day column and idx_signal_dedupe
- [ ] Verify trade constraints: check intent_id uniqueness and broker_order_id partial index
- [ ] Test signal upsert works idempotently
- [ ] Test trade upsert works idempotently
- [ ] Update P0DebtRegistry: set SIGNAL_DB_CONSTRAINTS_APPLIED = true
- [ ] Update P0DebtRegistry: set TRADE_IDEMPOTENCY_CONSTRAINTS = true

### Integration Testing
- [ ] Run all P0 test guides:
  - [ ] P0A_STARTUP_VALIDATION_TEST.md
  - [ ] P0B_IDEMPOTENCY_CONSTRAINTS_TEST.md
  - [ ] P0C_BROKER_RECONCILIATION_TEST.md
  - [ ] P0D_TICK_DEDUPLICATION_TEST.md
  - [ ] P0E_SINGLE_WRITER_TRADE_STATE_TEST.md
- [ ] End-to-end test: signal → intent → order → fill → exit
- [ ] Verify no duplicate trades created
- [ ] Verify no duplicate signals created
- [ ] Verify broker reconciliation heals state correctly

### Startup Validation
- [ ] Set `release.readiness=PROD_READY` in config
- [ ] Start system
- [ ] Verify startup succeeds (no P0 gate failures)
- [ ] Check logs: "✅ All P0 gates resolved"

### Final Verification SQL
```sql
-- Verify all constraints applied
SELECT 'Trades: intent_id unique' AS check_name,
       COUNT(*) AS exists
FROM pg_constraint
WHERE conrelid = 'trades'::regclass
  AND conname = 'uq_trades_intent_id'
UNION ALL
SELECT 'Trades: client_order_id unique', COUNT(*)
FROM pg_constraint
WHERE conrelid = 'trades'::regclass
  AND conname = 'uq_trades_client_order_id'
UNION ALL
SELECT 'Trades: broker_order_id partial index', COUNT(*)
FROM pg_indexes
WHERE tablename = 'trades'
  AND indexname = 'uq_trades_broker_order_id'
UNION ALL
SELECT 'Signals: dedupe index', COUNT(*)
FROM pg_indexes
WHERE tablename = 'signals'
  AND indexname = 'idx_signal_dedupe'
UNION ALL
SELECT 'Signals: signal_day generated', COUNT(*)
FROM information_schema.columns
WHERE table_name = 'signals'
  AND column_name = 'signal_day'
  AND is_generated = 'ALWAYS';

-- Expected: all rows return 1
```

---

## Post-Gate Resolution: System Behavior

Once ALL P0 gates are TRUE and `release.readiness=PROD_READY`:

**Startup Behavior:**
- System validates all gates during startup
- If any gate is FALSE, system exits with code 1
- Logs show which gates are unresolved
- Production deployment is blocked until all gates TRUE

**Production Operation:**
- All idempotency constraints enforced (no duplicates)
- Tick deduplication active (bounded memory)
- Position tracking queries DB (not HashMap)
- Order execution uses single-writer pattern
- Broker reconciliation runs every 30 seconds
- All P0 enforcement gates active

**Monitoring:**
- Check dedupe metrics: `TickCandleBuilder.getDedupeMetrics()`
- Check reconciler metrics: logs every reconciliation cycle
- Check trade state: verify no CREATED trades stuck > 5 minutes
- Check constraints: monitor database for constraint violations

---

## Troubleshooting

### Problem: Startup fails with "P0 gate unresolved"
**Solution:** Check which gate is FALSE in P0DebtRegistry, implement or verify that gate

### Problem: V007 migration fails
**Solution:**
- Check if columns already exist: `\d trades` and `\d signals` in psql
- Drop conflicting constraints/indexes before re-running
- Check migration log for specific SQL errors

### Problem: Duplicate trades created despite constraints
**Solution:**
- Verify V007 migration ran: check uq_trades_intent_id exists
- Check OrderExecutionService is being used (not old ExecutionOrchestrator)
- Verify intent_id is unique per signal×user-broker

### Problem: ExitSignalService still using HashMap
**Solution:**
- Check updated code deployed
- Verify constructor has TradeRepository parameter
- Check onTick() queries tradeRepo.findBySymbol()

---

## References

- `P0DebtRegistry.java` - Single source of truth for gate status
- `StartupConfigValidator.java` - Enforces gates at startup
- `COMPREHENSIVE_IMPLEMENTATION_PLAN.md` - Phase 1 implementation plan
- Test guides: `P0A_*.md`, `P0B_*.md`, `P0C_*.md`, `P0D_*.md`, `P0E_*.md`
- Migration: `sql/V007__add_idempotency_constraints.sql`
