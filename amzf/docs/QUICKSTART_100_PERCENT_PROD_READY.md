# Quick Start: Achieve 100% PROD_READY

## 🎯 Goal: Complete Final 2 P0 Gates (5 minutes)

**Current Status:** 5/7 gates complete (71%)
**Target Status:** 7/7 gates complete (100%) ✅

---

## ⚡ Super Quick Method (Automated)

### One Command to Complete Migration:

```bash
cd /Users/jnani/Desktop/AnnuPaper/annu-v04
./run_v007_migration.sh
```

**What it does:**
- ✅ Checks database connection
- ✅ Creates backup automatically
- ✅ Checks for data conflicts
- ✅ Runs V007 migration
- ✅ Verifies migration success
- ✅ Shows next steps

**Time:** ~2 minutes

---

## 📋 Manual Method (Step-by-Step)

### Step 1: Backup Database (30 seconds)

```bash
pg_dump -U postgres -d annupaper -F c -f annupaper_backup_$(date +%Y%m%d_%H%M%S).dump
```

### Step 2: Run Migration (1 minute)

```bash
psql -U postgres -d annupaper -f sql/V007__add_idempotency_constraints.sql
```

**Expected output ends with:** `COMMIT`

### Step 3: Verify Migration (30 seconds)

```bash
psql -U postgres -d annupaper -f sql/verify_v007_migration.sql
```

**Expected output:** `✅ All checks passed: YES`

### Step 4: Update P0DebtRegistry (1 minute)

Edit: `src/main/java/in/annupaper/bootstrap/P0DebtRegistry.java`

**Change lines 26-27:**

```java
// BEFORE:
"SIGNAL_DB_CONSTRAINTS_APPLIED", false, // TODO: Run V007 migration
"TRADE_IDEMPOTENCY_CONSTRAINTS", false, // TODO: Run V007 migration

// AFTER:
"SIGNAL_DB_CONSTRAINTS_APPLIED", true,  // ✅ V007 migration verified
"TRADE_IDEMPOTENCY_CONSTRAINTS", true,  // ✅ V007 migration verified
```

### Step 5: Rebuild Application (1 minute)

```bash
mvn clean compile
```

---

## 🚀 Enable PROD_READY Mode

### Update Configuration:

```bash
# Edit config file
vim config/application.properties

# Or set environment variable
export RELEASE_READINESS=PROD_READY
```

### Start Application:

```bash
java -jar target/annupaper-v04.jar
```

### Verify Success:

```bash
tail -f logs/application.log
```

**Look for:**
```
✅ All P0 gates resolved
✅ PROD_READY mode: Startup validation passed
✅ Pending order reconciler started (P0-C)
✅ P0-E: OrderExecutionService initialized
```

---

## ✅ Success Indicators

### All Gates TRUE:

```java
P0_GATES = Map.of(
    "ORDER_EXECUTION_IMPLEMENTED", true,       // ✅
    "POSITION_TRACKING_LIVE", true,            // ✅
    "BROKER_RECONCILIATION_RUNNING", true,     // ✅
    "TICK_DEDUPLICATION_ACTIVE", true,         // ✅
    "SIGNAL_DB_CONSTRAINTS_APPLIED", true,     // ✅ After migration
    "TRADE_IDEMPOTENCY_CONSTRAINTS", true,     // ✅ After migration
    "ASYNC_EVENT_WRITER_IF_PERSIST", true      // ✅
);
```

### Application Starts Without Errors:

- No "P0 gate unresolved" errors
- No constraint violation errors
- Reconciler logs every 30 seconds
- Dedupe metrics available

### Database Constraints Active:

```sql
-- Quick verification query
SELECT
    (SELECT COUNT(*) FROM pg_constraint WHERE conrelid = 'trades'::regclass AND conname LIKE 'uq_trades_%') as trade_constraints,
    (SELECT COUNT(*) FROM pg_constraint WHERE conrelid = 'signals'::regclass AND conname LIKE 'chk_effective_%') as signal_constraints,
    (SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'trades' AND indexname LIKE 'idx_trades_%') as trade_indexes,
    (SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'signals' AND indexname = 'idx_signal_dedupe') as signal_index;

-- Expected:
-- trade_constraints: 2 (intent_id, client_order_id)
-- signal_constraints: 2 (floor, ceiling precision)
-- trade_indexes: 4+ (pending, open, intent_id, client_order_id)
-- signal_index: 1 (dedupe index)
```

---

## 🎉 You've Achieved 100% PROD_READY!

### What's Now Enforced:

1. ✅ **No Duplicate Trades** - Database-level uniqueness on intent_id
2. ✅ **No Duplicate Signals** - Dedupe on (symbol, type, day, floor, ceiling)
3. ✅ **No Duplicate Ticks** - Two-window deduplication with bounded memory
4. ✅ **Single-Writer Pattern** - Only OrderExecutionService creates trades
5. ✅ **Position Tracking from DB** - ExitSignalService queries TradeRepository
6. ✅ **Broker Reconciliation** - Heals state every 30 seconds
7. ✅ **Hard Gate Enforcement** - System refuses to start if any gate FALSE

### System Will Now:

- ✅ Refuse to start in PROD_READY mode if any gate unresolved
- ✅ Prevent duplicate trades at database level
- ✅ Prevent duplicate signals at database level
- ✅ Deduplicate ticks with O(1) performance
- ✅ Track positions from database (not HashMap)
- ✅ Reconcile pending orders every 30 seconds
- ✅ Enforce single-writer pattern for trade creation

---

## 🧪 Quick Smoke Tests

### Test 1: Trade Idempotency

```bash
# Submit same intent_id twice
curl -X POST http://localhost:8080/api/trades \
  -H "Content-Type: application/json" \
  -d '{"intentId": "test-123", ...}'

curl -X POST http://localhost:8080/api/trades \
  -H "Content-Type: application/json" \
  -d '{"intentId": "test-123", ...}'

# Expected: Second request updates existing trade (not creates duplicate)
```

### Test 2: Signal Deduplication

```sql
-- Insert duplicate signal (should upsert)
INSERT INTO signals (signal_id, symbol, confluence_type, generated_at,
                     effective_floor, effective_ceiling, ...)
VALUES (gen_random_uuid()::text, 'INFY', 'TRIPLE', NOW(),
        2400.00, 2500.00, ...)
ON CONFLICT (symbol, confluence_type, signal_day, effective_floor, effective_ceiling)
DO UPDATE SET status = 'ACTIVE', updated_at = NOW()
RETURNING signal_id;

-- Run twice - should return same signal_id second time
```

### Test 3: Tick Deduplication

```bash
# Check dedupe metrics
curl http://localhost:8080/api/health/tick-dedupe-metrics

# Expected:
# {
#   "totalTicks": 12345,
#   "duplicateTicks": 234,
#   "missingExchangeTimestamp": 56,
#   "currentWindowSize": 1234,
#   "previousWindowSize": 567,
#   "dedupeRatePercent": 1
# }
```

### Test 4: Reconciler Running

```bash
# Watch logs for reconciliation
tail -f logs/application.log | grep "Reconciling"

# Expected: Logs every 30 seconds
# "Reconciling X pending trades"
```

---

## 🚨 Troubleshooting

### Problem: Migration fails with "duplicate key"

**Solution:**
```bash
# Check for duplicates
psql -U postgres -d annupaper -c "
  SELECT intent_id, COUNT(*)
  FROM trades
  WHERE intent_id IS NOT NULL
  GROUP BY intent_id
  HAVING COUNT(*) > 1;
"

# Clean up if found
# See: V007_MIGRATION_EXECUTION_GUIDE.md - Pre-Migration Checklist
```

### Problem: Application won't start with "P0 gate unresolved"

**Solution:**
```bash
# Check P0DebtRegistry.java
grep "SIGNAL_DB_CONSTRAINTS_APPLIED\|TRADE_IDEMPOTENCY_CONSTRAINTS" \
  src/main/java/in/annupaper/bootstrap/P0DebtRegistry.java

# Both should be true
# If not, update as shown in Step 4 above
```

### Problem: Verification script shows "FAIL"

**Solution:**
```bash
# Check detailed output
psql -U postgres -d annupaper -f sql/verify_v007_migration.sql

# Review which check failed
# See: V007_MIGRATION_EXECUTION_GUIDE.md - Troubleshooting section
```

### Problem: Need to rollback migration

**Solution:**
```bash
# Rollback V007 changes
psql -U postgres -d annupaper -f sql/V007_rollback.sql

# Or restore from backup
pg_restore -U postgres -d annupaper -c annupaper_backup_YYYYMMDD_HHMMSS.dump
```

---

## 📞 Need Help?

### Documentation:
- **Full migration guide:** `V007_MIGRATION_EXECUTION_GUIDE.md`
- **Verification guide:** `P0_GATES_VERIFICATION_GUIDE.md`
- **Complete summary:** `PHASE1_P0_COMPLETION_SUMMARY.md`

### Quick Commands:
```bash
# Run automated migration
./run_v007_migration.sh

# Verify migration manually
psql -U postgres -d annupaper -f sql/verify_v007_migration.sql

# Rollback if needed
psql -U postgres -d annupaper -f sql/V007_rollback.sql

# Check application logs
tail -f logs/application.log
```

---

## 🎊 Congratulations!

Once you see "✅ All P0 gates resolved" in your logs, you have:

- ✅ **Production-grade enforcement** of all 7 P0 gates
- ✅ **Zero duplicate trades/signals/ticks** guaranteed
- ✅ **Single-writer pattern** enforced mechanically
- ✅ **Database as source of truth** for positions
- ✅ **Broker state healing** every 30 seconds
- ✅ **Ungameable startup validation**

**You're ready for production! 🚀**

---

**Estimated Time:** 5 minutes (automated) or 10 minutes (manual)
**Difficulty:** Easy (everything is automated)
**Risk:** Low (automatic backups, rollback scripts provided)
