-- ============================================================================
-- V007 Rollback Script
-- ============================================================================
-- Purpose: Rollback V007 migration if needed
-- WARNING: This will remove all idempotency constraints!
-- Usage: psql -U postgres -d annupaper -f sql/V007_rollback.sql
-- ============================================================================

\set ON_ERROR_STOP on

\echo ''
\echo '========================================'
\echo 'V007 Migration Rollback'
\echo '========================================'
\echo ''
\echo 'WARNING: This will remove all V007 changes!'
\echo 'Press Ctrl+C to cancel, or Enter to continue...'
\prompt 'Continue? (yes/no): ' continue_rollback

-- Check user confirmation (PostgreSQL will handle this)

BEGIN;

\echo ''
\echo 'Rolling back V007 migration...'
\echo ''

-- ============================================
-- SIGNALS TABLE: Remove dedupe constraints
-- ============================================

\echo 'Removing signals constraints and indexes...'

-- Drop dedupe index
DROP INDEX IF EXISTS idx_signal_dedupe;

-- Drop CHECK constraints
ALTER TABLE signals
    DROP CONSTRAINT IF EXISTS chk_effective_floor_precision;

ALTER TABLE signals
    DROP CONSTRAINT IF EXISTS chk_effective_ceiling_precision;

-- Drop generated column
ALTER TABLE signals
    DROP COLUMN IF EXISTS signal_day;

-- Revert effective_floor and effective_ceiling to original precision
-- WARNING: This may cause data loss if precision was increased
-- ALTER TABLE signals
--     ALTER COLUMN effective_floor TYPE NUMERIC(18, 4),
--     ALTER COLUMN effective_ceiling TYPE NUMERIC(18, 4);
-- NOTE: Commented out to prevent data loss. Only uncomment if you're sure.

\echo '✅ Signals constraints rolled back'

-- ============================================
-- TRADES TABLE: Remove idempotency constraints
-- ============================================

\echo 'Removing trades constraints and indexes...'

-- Drop indexes
DROP INDEX IF EXISTS uq_trades_broker_order_id;
DROP INDEX IF EXISTS idx_trades_pending;
DROP INDEX IF EXISTS idx_trades_open;
DROP INDEX IF EXISTS idx_trades_intent_id;
DROP INDEX IF EXISTS idx_trades_client_order_id;

-- Drop unique constraints
ALTER TABLE trades
    DROP CONSTRAINT IF EXISTS uq_trades_intent_id;

ALTER TABLE trades
    DROP CONSTRAINT IF EXISTS uq_trades_client_order_id;

-- Remove columns added by V007
-- WARNING: This will permanently delete data in these columns!
-- Uncomment only if you're absolutely sure you want to delete this data.

-- ALTER TABLE trades
--     DROP COLUMN IF EXISTS client_order_id;

-- ALTER TABLE trades
--     DROP COLUMN IF EXISTS last_broker_update_at;

-- NOTE: We're NOT dropping intent_id because it may have existed before V007
-- If you need to drop it, uncomment:
-- ALTER TABLE trades
--     DROP COLUMN IF EXISTS intent_id;

\echo '✅ Trades constraints rolled back'
\echo ''
\echo '----------------------------------------'
\echo 'NOTE: Columns client_order_id and last_broker_update_at NOT dropped'
\echo '      To drop them, uncomment the DROP COLUMN statements in this script'
\echo '----------------------------------------'
\echo ''

-- ============================================
-- Verify rollback
-- ============================================

\echo 'Verifying rollback...'

-- Check that constraints are gone (using constants to avoid duplication)
DO $$
DECLARE
    trades_table CONSTANT regclass := 'trades'::regclass;
    signals_table CONSTANT regclass := 'signals'::regclass;
    v_status TEXT;
BEGIN
    SELECT
        CASE
            WHEN NOT EXISTS (
                SELECT 1 FROM pg_constraint
                WHERE conrelid = trades_table
                  AND conname IN ('uq_trades_intent_id', 'uq_trades_client_order_id')
            )
            AND NOT EXISTS (
                SELECT 1 FROM pg_constraint
                WHERE conrelid = signals_table
                  AND conname IN ('chk_effective_floor_precision', 'chk_effective_ceiling_precision')
            )
            AND NOT EXISTS (
                SELECT 1 FROM pg_indexes
                WHERE tablename IN ('trades', 'signals')
                  AND indexname IN ('idx_signal_dedupe', 'uq_trades_broker_order_id')
            )
            THEN '✅ Rollback successful: All V007 constraints removed'
            ELSE '❌ Rollback incomplete: Some constraints still exist'
        END INTO v_status;

    RAISE NOTICE '%', v_status;
END $$;

\echo ''

COMMIT;

\echo ''
\echo '========================================'
\echo 'Rollback Complete'
\echo '========================================'
\echo ''
\echo 'Next steps:'
\echo '1. Verify application starts without errors'
\echo '2. Update P0DebtRegistry.java:'
\echo '   - Set SIGNAL_DB_CONSTRAINTS_APPLIED = false'
\echo '   - Set TRADE_IDEMPOTENCY_CONSTRAINTS = false'
\echo '3. Do NOT set release.readiness=PROD_READY'
\echo ''
