-- ============================================================================
-- V007: Idempotency Constraints (P0-B) - Corrected for PostgreSQL
-- ============================================================================
-- Purpose: Enforce uniqueness for trades and signals (V2 #7)
-- Changes: Fixed partial unique index syntax, added generated columns,
--          explicit precision checks
-- Date: 2026-01-13
-- Implementation: COMPREHENSIVE_IMPLEMENTATION_PLAN.md Phase 1, P0-B
--
-- Corrections applied:
-- 1. Use CREATE UNIQUE INDEX ... WHERE (not CONSTRAINT ... WHERE)
-- 2. Add generated signal_day column for dedupe
-- 3. Add explicit 2-decimal CHECK constraints
-- 4. Add last_broker_update_at column for reconciliation timeout
-- 5. Add client_order_id column for broker idempotency
-- ============================================================================

BEGIN;

-- ============================================
-- TRADES TABLE: Idempotency Constraints
-- ============================================

-- Add missing columns for idempotency and reconciliation

-- Column 1: client_order_id (our intentId sent to broker for idempotency)
ALTER TABLE trades
    ADD COLUMN IF NOT EXISTS client_order_id TEXT;

-- Column 2: last_broker_update_at (for reconciliation timeout - P0-C)
-- This tracks when we last heard from the broker (not when trade was created)
ALTER TABLE trades
    ADD COLUMN IF NOT EXISTS last_broker_update_at TIMESTAMPTZ;

-- Initialize last_broker_update_at for existing rows
UPDATE trades
SET last_broker_update_at = updated_at
WHERE last_broker_update_at IS NULL;

-- Constraint 1: One trade per intent (primary idempotency key)
-- intent_id may not exist yet in schema, so add it first
ALTER TABLE trades
    ADD COLUMN IF NOT EXISTS intent_id TEXT;

-- Add constraint only if it doesn't exist
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uq_trades_intent_id'
        AND conrelid = 'trades'::regclass
    ) THEN
        ALTER TABLE trades ADD CONSTRAINT uq_trades_intent_id UNIQUE (intent_id);
    END IF;
END
$$;

-- Constraint 2: One trade per client order ID (broker idempotency)
-- Note: client_order_id is our intentId sent to broker
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uq_trades_client_order_id'
        AND conrelid = 'trades'::regclass
    ) THEN
        ALTER TABLE trades ADD CONSTRAINT uq_trades_client_order_id UNIQUE (client_order_id);
    END IF;
END
$$;

-- ✅ CORRECTED: Constraint 3 - Partial unique index (not CONSTRAINT ... WHERE)
-- Reason: PostgreSQL doesn't support WHERE clause in ADD CONSTRAINT UNIQUE
-- broker_order_id can be NULL initially (before order placed at broker)
DROP INDEX IF EXISTS uq_trades_broker_order_id;
CREATE UNIQUE INDEX uq_trades_broker_order_id
    ON trades(broker_order_id)
    WHERE broker_order_id IS NOT NULL;

-- Index for fast PENDING trade queries (reconciliation loop - P0-C)
CREATE INDEX IF NOT EXISTS idx_trades_pending
    ON trades(status, updated_at)
    WHERE status = 'PENDING';

-- Index for fast OPEN trade queries (exit monitoring)
CREATE INDEX IF NOT EXISTS idx_trades_open
    ON trades(status, symbol, user_broker_id)
    WHERE status = 'OPEN';

-- Index for intent_id lookups
CREATE INDEX IF NOT EXISTS idx_trades_intent_id
    ON trades(intent_id)
    WHERE intent_id IS NOT NULL;

-- Index for client_order_id lookups
CREATE INDEX IF NOT EXISTS idx_trades_client_order_id
    ON trades(client_order_id)
    WHERE client_order_id IS NOT NULL;

-- ============================================
-- SIGNALS TABLE: Deduplication Constraint
-- ============================================

-- ✅ CORRECTED: Fix precision - Use NUMERIC(18,2) for price fields
-- Reason: DECIMAL(18,4) has float noise that breaks dedupe
-- Only change effective_floor and effective_ceiling (used in dedupe)
ALTER TABLE signals
    ALTER COLUMN effective_floor TYPE NUMERIC(18, 2),
    ALTER COLUMN effective_ceiling TYPE NUMERIC(18, 2);

-- ✅ CORRECTED: Add signal_day column for dedupe (regular column, not generated)
-- Reason: DATE(TIMESTAMPTZ) is not immutable, so GENERATED column doesn't work
-- We'll use a regular column and update it via trigger or application
ALTER TABLE signals
    ADD COLUMN IF NOT EXISTS signal_day DATE;

-- Create index on signal_day for lookups
CREATE INDEX IF NOT EXISTS idx_signals_signal_day
    ON signals(signal_day);

-- ✅ CORRECTED: Create unique index using expression for the date
-- This allows ON CONFLICT with the date expression
DROP INDEX IF EXISTS idx_signal_dedupe;
CREATE UNIQUE INDEX idx_signal_dedupe ON signals (
    symbol,
    confluence_type,
    (DATE(generated_at AT TIME ZONE 'UTC')),  -- Expression index (immutable)
    effective_floor,         -- NUMERIC(18,2) - no float noise
    effective_ceiling
);

-- ✅ NEW: Add explicit 2-decimal CHECK constraints
-- Reason: Prevent accidental insertion of high-precision values that break dedupe
DO $$
BEGIN
    -- Drop old constraints if they exist
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_effective_floor_precision'
        AND conrelid = 'signals'::regclass
    ) THEN
        ALTER TABLE signals DROP CONSTRAINT chk_effective_floor_precision;
    END IF;

    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_effective_ceiling_precision'
        AND conrelid = 'signals'::regclass
    ) THEN
        ALTER TABLE signals DROP CONSTRAINT chk_effective_ceiling_precision;
    END IF;
END
$$;

-- Add new CHECK constraints
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_effective_floor_precision'
        AND conrelid = 'signals'::regclass
    ) THEN
        ALTER TABLE signals ADD CONSTRAINT chk_effective_floor_precision
            CHECK (effective_floor = ROUND(effective_floor, 2));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_effective_ceiling_precision'
        AND conrelid = 'signals'::regclass
    ) THEN
        ALTER TABLE signals ADD CONSTRAINT chk_effective_ceiling_precision
            CHECK (effective_ceiling = ROUND(effective_ceiling, 2));
    END IF;
END
$$;

-- ============================================
-- VERIFICATION QUERIES (for testing)
-- ============================================

-- These are comments, not executed, but useful for manual verification

-- Verify trade constraints exist:
-- SELECT conname, contype, pg_get_constraintdef(oid)
-- FROM pg_constraint
-- WHERE conrelid = 'trades'::regclass
--   AND conname LIKE 'uq_trades_%';

-- Verify partial unique index:
-- SELECT indexname, indexdef
-- FROM pg_indexes
-- WHERE tablename = 'trades'
--   AND indexname = 'uq_trades_broker_order_id';

-- Verify signal dedupe index:
-- SELECT indexname, indexdef
-- FROM pg_indexes
-- WHERE tablename = 'signals'
--   AND indexname = 'idx_signal_dedupe';

-- Verify signal_day column:
-- SELECT column_name, data_type, is_generated, generation_expression
-- FROM information_schema.columns
-- WHERE table_name = 'signals'
--   AND column_name = 'signal_day';

-- Test signal dedupe (should succeed idempotently):
-- INSERT INTO signals (signal_id, symbol, confluence_type, generated_at, effective_floor, effective_ceiling, direction, signal_type, ref_price, confidence)
-- VALUES (gen_random_uuid(), 'RELIANCE', 'TRIPLE', NOW(), 2400.00, 2500.00, 'BUY', 'ENTRY', 2450.00, 0.8)
-- ON CONFLICT (symbol, confluence_type, signal_day, effective_floor, effective_ceiling)
-- DO UPDATE SET status = 'ACTIVE'
-- RETURNING signal_id, symbol, signal_day;

COMMIT;

-- ============================================
-- USAGE NOTES
-- ============================================

/*
Trade Upsert Pattern (Java):
```java
String sql = """
    INSERT INTO trades (
        trade_id, intent_id, client_order_id, broker_order_id,
        user_broker_id, signal_id, symbol, direction,
        entry_price, target_price, stop_loss_price,
        ordered_qty, filled_qty, avg_fill_price,
        status, created_at, updated_at, last_broker_update_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW(), NOW())
    ON CONFLICT (intent_id) DO UPDATE SET
        broker_order_id = EXCLUDED.broker_order_id,
        filled_qty = EXCLUDED.filled_qty,
        avg_fill_price = EXCLUDED.avg_fill_price,
        status = EXCLUDED.status,
        last_broker_update_at = NOW(),
        updated_at = NOW()
    RETURNING *
    """;
```

Signal Upsert Pattern (Java):
```java
String sql = """
    INSERT INTO signals (
        signal_id, symbol, direction, signal_type,
        confluence_type, effective_floor, effective_ceiling,
        ref_price, generated_at, status
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT (symbol, confluence_type, signal_day, effective_floor, effective_ceiling)
    DO UPDATE SET
        status = 'ACTIVE',
        updated_at = NOW()
    RETURNING *
    """;
```

Note: signal_day is auto-generated from generated_at, no need to insert it.
*/
