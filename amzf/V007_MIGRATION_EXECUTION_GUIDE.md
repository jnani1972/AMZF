# V007 Migration Execution Guide

## Overview

This guide provides step-by-step instructions for executing the V007 migration to complete the final 2 P0 gates.

**What V007 Does:**
- Adds trade idempotency constraints (intent_id, client_order_id, broker_order_id)
- Adds signal deduplication constraints (symbol + confluence_type + signal_day + floor + ceiling)
- Adds last_broker_update_at column for reconciliation
- Adds CHECK constraints for 2-decimal precision
- Creates indexes for fast queries

**P0 Gates Affected:**
- SIGNAL_DB_CONSTRAINTS_APPLIED: false → true
- TRADE_IDEMPOTENCY_CONSTRAINTS: false → true

---

## Pre-Migration Checklist

### 1. Backup Database

**CRITICAL: Always backup before running migrations!**

```bash
# Backup entire database
pg_dump -U postgres -d annupaper -F c -f annupaper_backup_$(date +%Y%m%d_%H%M%S).dump

# Or backup just the tables being modified
pg_dump -U postgres -d annupaper -t trades -t signals -F c -f annupaper_trades_signals_backup_$(date +%Y%m%d_%H%M%S).dump
```

**Verify backup:**
```bash
# Check backup file size (should be > 0)
ls -lh annupaper_backup_*.dump
```

### 2. Check Current Schema State

```sql
-- Connect to database
psql -U postgres -d annupaper

-- Check if trades table has intent_id column already
\d trades

-- Check if signals table has signal_day column already
\d signals

-- Check existing constraints
SELECT conname, contype, pg_get_constraintdef(oid)
FROM pg_constraint
WHERE conrelid IN ('trades'::regclass, 'signals'::regclass);

-- Check existing indexes
SELECT tablename, indexname, indexdef
FROM pg_indexes
WHERE tablename IN ('trades', 'signals')
ORDER BY tablename, indexname;
```

**Expected State Before Migration:**
- `trades.intent_id` - May or may not exist (migration handles both)
- `trades.client_order_id` - Should NOT exist yet
- `trades.last_broker_update_at` - Should NOT exist yet
- `signals.signal_day` - Should NOT exist yet
- Unique constraint `uq_trades_intent_id` - Should NOT exist yet
- Unique index `idx_signal_dedupe` - Should NOT exist yet

### 3. Check for Data Conflicts

```sql
-- Check if any existing trades would violate intent_id uniqueness
-- (Only matters if intent_id column already exists)
SELECT intent_id, COUNT(*)
FROM trades
WHERE intent_id IS NOT NULL
GROUP BY intent_id
HAVING COUNT(*) > 1;

-- Expected: 0 rows (no duplicates)

-- Check if any existing signals would violate dedupe constraint
SELECT symbol, confluence_type, DATE(generated_at) as signal_day,
       effective_floor, effective_ceiling, COUNT(*)
FROM signals
GROUP BY symbol, confluence_type, DATE(generated_at),
         effective_floor, effective_ceiling
HAVING COUNT(*) > 1;

-- Expected: 0 rows (no duplicates)
-- If rows returned: You have duplicate signals that need manual cleanup
```

**If Duplicates Found:**

For duplicate trades:
```sql
-- Keep the earliest trade, soft-delete duplicates
UPDATE trades t1
SET deleted_at = NOW()
WHERE deleted_at IS NULL
  AND EXISTS (
    SELECT 1 FROM trades t2
    WHERE t2.intent_id = t1.intent_id
      AND t2.created_at < t1.created_at
      AND t2.deleted_at IS NULL
  );
```

For duplicate signals:
```sql
-- Keep the most recent signal, mark others as DUPLICATE
UPDATE signals s1
SET status = 'DUPLICATE'
WHERE status != 'DUPLICATE'
  AND EXISTS (
    SELECT 1 FROM signals s2
    WHERE s2.symbol = s1.symbol
      AND s2.confluence_type = s1.confluence_type
      AND DATE(s2.generated_at) = DATE(s1.generated_at)
      AND s2.effective_floor = s1.effective_floor
      AND s2.effective_ceiling = s1.effective_ceiling
      AND s2.generated_at > s1.generated_at
  );
```

### 4. Stop Application (Recommended)

```bash
# Stop the application to prevent writes during migration
# (Prevents race conditions with constraint creation)
systemctl stop annupaper
# Or: pkill -f "java.*annupaper"

# Verify stopped
ps aux | grep annupaper
```

---

## Migration Execution

### Option 1: Using Flyway (Recommended)

**If using Flyway for migrations:**

```bash
# Navigate to project directory
cd /Users/jnani/Desktop/AnnuPaper/annu-v04

# Check Flyway status
flyway info

# Expected output should show V007 as "Pending"

# Run migration
flyway migrate

# Verify migration succeeded
flyway info

# Expected output should show V007 as "Success"
```

**If Flyway migration fails:**
- Check logs for specific error
- See "Troubleshooting" section below
- Consider manual migration (Option 2)

### Option 2: Manual SQL Execution

**If NOT using Flyway or Flyway fails:**

```bash
# Connect to database
psql -U postgres -d annupaper

# Run migration script
\i sql/V007__add_idempotency_constraints.sql

# Check for errors in output
# Look for: "COMMIT" at end (success)
# Or: "ROLLBACK" (failure - see Troubleshooting)
```

**Alternative: Run from command line**

```bash
psql -U postgres -d annupaper -f sql/V007__add_idempotency_constraints.sql
```

### Monitor Migration Progress

The migration should take 1-5 minutes depending on data size:
- Adding columns: Fast (< 1 second per column)
- Creating indexes: Medium (10-60 seconds per index on large tables)
- Adding constraints: Fast (< 1 second per constraint if no violations)

**Watch for:**
- Errors about duplicate keys (means data cleanup needed)
- Lock timeout errors (means application still writing - stop it first)
- Syntax errors (means migration file corrupted)

---

## Post-Migration Verification

### 1. Verify Columns Added

```sql
-- Check trades table columns
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_name = 'trades'
  AND column_name IN ('intent_id', 'client_order_id', 'last_broker_update_at')
ORDER BY column_name;

-- Expected: 3 rows
-- intent_id: text, YES
-- client_order_id: text, YES
-- last_broker_update_at: timestamp with time zone, YES

-- Check signals table columns
SELECT column_name, data_type, is_generated, generation_expression
FROM information_schema.columns
WHERE table_name = 'signals'
  AND column_name = 'signal_day';

-- Expected: 1 row
-- signal_day: date, ALWAYS, DATE(generated_at)

-- Verify signal columns changed to NUMERIC(18,2)
SELECT column_name, data_type, numeric_precision, numeric_scale
FROM information_schema.columns
WHERE table_name = 'signals'
  AND column_name IN ('effective_floor', 'effective_ceiling');

-- Expected: 2 rows with numeric_scale = 2
```

### 2. Verify Constraints Created

```sql
-- Check trades constraints
SELECT conname, contype, pg_get_constraintdef(oid)
FROM pg_constraint
WHERE conrelid = 'trades'::regclass
  AND conname LIKE 'uq_trades_%'
ORDER BY conname;

-- Expected:
-- uq_trades_client_order_id: UNIQUE (client_order_id)
-- uq_trades_intent_id: UNIQUE (intent_id)

-- Check signals constraints
SELECT conname, contype, pg_get_constraintdef(oid)
FROM pg_constraint
WHERE conrelid = 'signals'::regclass
  AND conname LIKE 'chk_effective_%'
ORDER BY conname;

-- Expected:
-- chk_effective_ceiling_precision: CHECK (effective_ceiling = round(effective_ceiling, 2))
-- chk_effective_floor_precision: CHECK (effective_floor = round(effective_floor, 2))
```

### 3. Verify Indexes Created

```sql
-- Check trades indexes
SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename = 'trades'
  AND indexname IN (
    'uq_trades_broker_order_id',
    'idx_trades_pending',
    'idx_trades_open',
    'idx_trades_intent_id',
    'idx_trades_client_order_id'
  )
ORDER BY indexname;

-- Expected: 5 rows

-- Check signals indexes
SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename = 'signals'
  AND indexname = 'idx_signal_dedupe';

-- Expected: 1 row
-- idx_signal_dedupe ON signals (symbol, confluence_type, signal_day,
--                                 effective_floor, effective_ceiling)
```

### 4. Run Comprehensive Verification Script

Use the provided verification script: `verify_v007_migration.sql`

```bash
psql -U postgres -d annupaper -f verify_v007_migration.sql
```

**Expected Output:**
```
 check_name                              | status
-----------------------------------------+---------
 Trades: intent_id column                | PASS
 Trades: client_order_id column          | PASS
 Trades: last_broker_update_at column    | PASS
 Trades: intent_id unique constraint     | PASS
 Trades: client_order_id unique          | PASS
 Trades: broker_order_id partial index   | PASS
 Signals: signal_day column              | PASS
 Signals: floor precision NUMERIC(18,2)  | PASS
 Signals: ceiling precision NUMERIC(18,2)| PASS
 Signals: dedupe index                   | PASS
 Signals: floor CHECK constraint         | PASS
 Signals: ceiling CHECK constraint       | PASS
(12 rows)

All checks passed: YES
```

### 5. Test Idempotency

```sql
-- Test trade insert (should succeed)
INSERT INTO trades (
  trade_id, intent_id, client_order_id, user_id, broker_id, user_broker_id,
  signal_id, symbol, trade_number, entry_qty, status, created_at, updated_at, version
) VALUES (
  gen_random_uuid()::text, 'test-intent-001', 'test-client-001',
  'user-1', 'broker-1', 'ub-1', 'signal-1', 'TEST', 1, 100,
  'CREATED', NOW(), NOW(), 1
);

-- Test duplicate intent_id (should FAIL with unique violation)
INSERT INTO trades (
  trade_id, intent_id, client_order_id, user_id, broker_id, user_broker_id,
  signal_id, symbol, trade_number, entry_qty, status, created_at, updated_at, version
) VALUES (
  gen_random_uuid()::text, 'test-intent-001', 'test-client-002',
  'user-1', 'broker-1', 'ub-1', 'signal-1', 'TEST', 1, 100,
  'CREATED', NOW(), NOW(), 1
);
-- Expected error: duplicate key value violates unique constraint "uq_trades_intent_id"

-- Clean up test data
DELETE FROM trades WHERE intent_id = 'test-intent-001';

-- Test signal upsert (should succeed)
INSERT INTO signals (
  signal_id, symbol, confluence_type, generated_at,
  effective_floor, effective_ceiling, direction, signal_type,
  ref_price, confidence, status, created_at, updated_at, version
) VALUES (
  gen_random_uuid()::text, 'TEST', 'TRIPLE', NOW(),
  2400.00, 2500.00, 'LONG', 'ENTRY', 2450.00, 0.8,
  'ACTIVE', NOW(), NOW(), 1
)
ON CONFLICT (symbol, confluence_type, signal_day, effective_floor, effective_ceiling)
DO UPDATE SET status = 'ACTIVE', updated_at = NOW()
RETURNING signal_id, symbol, signal_day;

-- Run twice (should update, not insert new)
INSERT INTO signals (
  signal_id, symbol, confluence_type, generated_at,
  effective_floor, effective_ceiling, direction, signal_type,
  ref_price, confidence, status, created_at, updated_at, version
) VALUES (
  gen_random_uuid()::text, 'TEST', 'TRIPLE', NOW(),
  2400.00, 2500.00, 'LONG', 'ENTRY', 2450.00, 0.8,
  'ACTIVE', NOW(), NOW(), 1
)
ON CONFLICT (symbol, confluence_type, signal_day, effective_floor, effective_ceiling)
DO UPDATE SET status = 'ACTIVE', updated_at = NOW()
RETURNING signal_id, symbol, signal_day;

-- Verify only 1 signal (not 2)
SELECT COUNT(*) as signal_count
FROM signals
WHERE symbol = 'TEST'
  AND confluence_type = 'TRIPLE'
  AND signal_day = CURRENT_DATE
  AND effective_floor = 2400.00
  AND effective_ceiling = 2500.00;
-- Expected: 1

-- Clean up test data
DELETE FROM signals WHERE symbol = 'TEST';
```

---

## Update P0DebtRegistry

After successful verification, update the P0 debt registry:

### Edit P0DebtRegistry.java

```bash
# Open file
vim src/main/java/in/annupaper/bootstrap/P0DebtRegistry.java
# Or use your preferred editor
```

**Change lines 26-27:**

```java
// BEFORE:
"SIGNAL_DB_CONSTRAINTS_APPLIED", false, // TODO: Run V007 migration and verify (not V015)
"TRADE_IDEMPOTENCY_CONSTRAINTS", false, // TODO: Run V007 migration and verify (not V015)

// AFTER:
"SIGNAL_DB_CONSTRAINTS_APPLIED", true,  // ✅ V007 migration applied and verified
"TRADE_IDEMPOTENCY_CONSTRAINTS", true,  // ✅ V007 migration applied and verified
```

### Verify All Gates True

```bash
grep "P0_GATES = Map.of" -A 8 src/main/java/in/annupaper/bootstrap/P0DebtRegistry.java
```

**Expected Output:**
```java
private static final Map<String, Boolean> P0_GATES = Map.of(
    "ORDER_EXECUTION_IMPLEMENTED", true,
    "POSITION_TRACKING_LIVE", true,
    "BROKER_RECONCILIATION_RUNNING", true,
    "TICK_DEDUPLICATION_ACTIVE", true,
    "SIGNAL_DB_CONSTRAINTS_APPLIED", true,     // ✅ All true!
    "TRADE_IDEMPOTENCY_CONSTRAINTS", true,     // ✅ All true!
    "ASYNC_EVENT_WRITER_IF_PERSIST", true
);
```

### Rebuild Application

```bash
# Compile updated P0DebtRegistry
mvn compile
# Or: gradle build

# Run tests to ensure nothing broken
mvn test
```

---

## Restart Application

### Start in DEV Mode First (Safety Check)

```bash
# Set to DEV mode temporarily
export RELEASE_READINESS=DEV

# Start application
java -jar target/annupaper-v04.jar
# Or: mvn spring-boot:run
# Or: systemctl start annupaper

# Check logs for startup
tail -f logs/application.log

# Look for:
# "✅ All P0 gates resolved" (even in DEV mode)
# No startup errors
```

### Test Application Functions

```bash
# Test database connectivity
curl http://localhost:8080/api/health

# Test trade operations (if API available)
curl http://localhost:8080/api/trades

# Test signal operations
curl http://localhost:8080/api/signals

# Monitor logs for constraint violations
tail -f logs/application.log | grep "constraint"
# Should see: No violations
```

### Enable PROD_READY Mode

```bash
# Stop application
systemctl stop annupaper

# Set PROD_READY in config
vim config/application.properties
# Set: release.readiness=PROD_READY

# Or: export environment variable
export RELEASE_READINESS=PROD_READY

# Start application
systemctl start annupaper

# Check startup
tail -f logs/application.log

# Look for:
# "✅ PROD_READY mode: All P0 gates resolved"
# "✅ Startup validation passed"
# Application should start successfully!
```

---

## Troubleshooting

### Problem: Migration fails with "column already exists"

**Cause:** Migration was partially run before

**Solution:**
```sql
-- Check what exists
\d trades
\d signals

-- If columns exist but constraints don't, run only constraint creation:
-- Extract relevant ALTER TABLE statements from V007 and run manually
```

### Problem: Unique constraint violation during migration

**Cause:** Duplicate data exists

**Solution:**
See "Pre-Migration Checklist" → "Check for Data Conflicts"

### Problem: Lock timeout error

**Cause:** Application still writing to database

**Solution:**
```bash
# Terminate all connections to database
psql -U postgres -d postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = 'annupaper' AND pid <> pg_backend_pid();"

# Retry migration
psql -U postgres -d annupaper -f sql/V007__add_idempotency_constraints.sql
```

### Problem: Index creation takes too long (> 10 minutes)

**Cause:** Large tables, slow disk, or lack of resources

**Solution:**
```sql
-- Create indexes CONCURRENTLY (doesn't lock table)
DROP INDEX IF EXISTS idx_signal_dedupe;
CREATE UNIQUE INDEX CONCURRENTLY idx_signal_dedupe ON signals (
    symbol, confluence_type, signal_day, effective_floor, effective_ceiling
);

-- Check progress
SELECT now(), query, state, wait_event_type, wait_event
FROM pg_stat_activity
WHERE query LIKE '%CREATE%INDEX%';
```

### Problem: Application won't start after migration

**Cause:** P0DebtRegistry not updated

**Solution:**
```bash
# Check current gate status
grep "SIGNAL_DB_CONSTRAINTS_APPLIED\|TRADE_IDEMPOTENCY_CONSTRAINTS" \
     src/main/java/in/annupaper/bootstrap/P0DebtRegistry.java

# Should both be true
# If not, update as per "Update P0DebtRegistry" section above
```

---

## Rollback (If Needed)

**If migration fails and you need to rollback:**

### Option 1: Restore from Backup

```bash
# Restore entire database
pg_restore -U postgres -d annupaper -c annupaper_backup_YYYYMMDD_HHMMSS.dump

# Or restore just tables
pg_restore -U postgres -d annupaper -t trades -t signals -c annupaper_trades_signals_backup_YYYYMMDD_HHMMSS.dump
```

### Option 2: Manual Rollback

```sql
-- Run rollback script
\i sql/V007_rollback.sql
```

See `V007_rollback.sql` for complete rollback script.

---

## Success Criteria

✅ **Migration is successful when:**

1. All verification SQL queries return expected results
2. verify_v007_migration.sql shows "All checks passed: YES"
3. Test trade insert succeeds, duplicate intent_id fails
4. Test signal upsert succeeds, dedupe works
5. P0DebtRegistry updated (both gates TRUE)
6. Application starts successfully in PROD_READY mode
7. Logs show: "✅ All P0 gates resolved"

---

## Next Steps After Success

1. **Update Documentation:**
   - Mark V007 as complete in CHANGELOG
   - Update README with new constraints

2. **Monitor Production:**
   - Watch for constraint violations
   - Monitor database performance (new indexes)
   - Check reconciler logs

3. **Proceed to Phase 2:**
   - See COMPREHENSIVE_IMPLEMENTATION_PLAN.md
   - Implement P1 features (next priority)

---

## References

- Migration file: `sql/V007__add_idempotency_constraints.sql`
- Verification script: `verify_v007_migration.sql`
- Rollback script: `V007_rollback.sql`
- P0 Gates Guide: `P0_GATES_VERIFICATION_GUIDE.md`
- Implementation Plan: `COMPREHENSIVE_IMPLEMENTATION_PLAN.md`
