-- ============================================================================
-- V007 Migration Verification Script
-- ============================================================================
-- Purpose: Verify that V007 migration applied correctly
-- Usage: psql -U postgres -d annupaper -f verify_v007_migration.sql
-- ============================================================================

\set ON_ERROR_STOP on

\echo ''
\echo '========================================'
\echo 'V007 Migration Verification'
\echo '========================================'
\echo ''

-- Create verification function
CREATE OR REPLACE FUNCTION verify_v007_migration()
RETURNS TABLE (
    check_name TEXT,
    status TEXT
) AS $$
DECLARE
    trades_table CONSTANT regclass := 'trades'::regclass;
    signals_table CONSTANT regclass := 'signals'::regclass;
BEGIN
    -- Check 1: trades.intent_id column exists
    RETURN QUERY
    SELECT
        'Trades: intent_id column'::TEXT,
        CASE WHEN EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'trades' AND column_name = 'intent_id'
        ) THEN 'PASS' ELSE 'FAIL' END::TEXT;

    -- Check 2: trades.client_order_id column exists
    RETURN QUERY
    SELECT
        'Trades: client_order_id column'::TEXT,
        CASE WHEN EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'trades' AND column_name = 'client_order_id'
        ) THEN 'PASS' ELSE 'FAIL' END::TEXT;

    -- Check 3: trades.last_broker_update_at column exists
    RETURN QUERY
    SELECT
        'Trades: last_broker_update_at column'::TEXT,
        CASE WHEN EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'trades' AND column_name = 'last_broker_update_at'
        ) THEN 'PASS' ELSE 'FAIL' END::TEXT;

    -- Check 4: uq_trades_intent_id constraint exists
    RETURN QUERY
    SELECT
        'Trades: intent_id unique constraint'::TEXT,
        CASE WHEN EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = trades_table AND conname = 'uq_trades_intent_id'
        ) THEN 'PASS' ELSE 'FAIL' END::TEXT;

    -- Check 5: uq_trades_client_order_id constraint exists
    RETURN QUERY
    SELECT
        'Trades: client_order_id unique'::TEXT,
        CASE WHEN EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = trades_table AND conname = 'uq_trades_client_order_id'
        ) THEN 'PASS' ELSE 'FAIL' END::TEXT;

    -- Check 6: uq_trades_broker_order_id partial index exists
    RETURN QUERY
    SELECT
        'Trades: broker_order_id partial index'::TEXT,
        CASE WHEN EXISTS (
            SELECT 1 FROM pg_indexes
            WHERE tablename = 'trades' AND indexname = 'uq_trades_broker_order_id'
        ) THEN 'PASS' ELSE 'FAIL' END::TEXT;

    -- Check 7: signals.signal_day column exists (regular column, not generated)
    -- Note: Changed from generated column to regular column because DATE(TIMESTAMPTZ) isn't immutable
    RETURN QUERY
    SELECT
        'Signals: signal_day column'::TEXT,
        CASE WHEN EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'signals'
              AND column_name = 'signal_day'
              AND data_type = 'date'
        ) THEN 'PASS' ELSE 'FAIL' END::TEXT;

    -- Check 8: signals.effective_floor is NUMERIC(18,2)
    RETURN QUERY
    SELECT
        'Signals: floor precision NUMERIC(18,2)'::TEXT,
        CASE WHEN EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'signals'
              AND column_name = 'effective_floor'
              AND data_type = 'numeric'
              AND numeric_precision = 18
              AND numeric_scale = 2
        ) THEN 'PASS' ELSE 'FAIL' END::TEXT;

    -- Check 9: signals.effective_ceiling is NUMERIC(18,2)
    RETURN QUERY
    SELECT
        'Signals: ceiling precision NUMERIC(18,2)'::TEXT,
        CASE WHEN EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'signals'
              AND column_name = 'effective_ceiling'
              AND data_type = 'numeric'
              AND numeric_precision = 18
              AND numeric_scale = 2
        ) THEN 'PASS' ELSE 'FAIL' END::TEXT;

    -- Check 10: idx_signal_dedupe index exists
    RETURN QUERY
    SELECT
        'Signals: dedupe index'::TEXT,
        CASE WHEN EXISTS (
            SELECT 1 FROM pg_indexes
            WHERE tablename = 'signals' AND indexname = 'idx_signal_dedupe'
        ) THEN 'PASS' ELSE 'FAIL' END::TEXT;

    -- Check 11: chk_effective_floor_precision constraint exists
    RETURN QUERY
    SELECT
        'Signals: floor CHECK constraint'::TEXT,
        CASE WHEN EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = signals_table
              AND conname = 'chk_effective_floor_precision'
        ) THEN 'PASS' ELSE 'FAIL' END::TEXT;

    -- Check 12: chk_effective_ceiling_precision constraint exists
    RETURN QUERY
    SELECT
        'Signals: ceiling CHECK constraint'::TEXT,
        CASE WHEN EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = signals_table
              AND conname = 'chk_effective_ceiling_precision'
        ) THEN 'PASS' ELSE 'FAIL' END::TEXT;

    RETURN;
END;
$$ LANGUAGE plpgsql;

-- Run verification
SELECT * FROM verify_v007_migration();

\echo ''
\echo '----------------------------------------'

-- Summary check
SELECT CASE
    WHEN NOT EXISTS (
        SELECT 1 FROM verify_v007_migration() WHERE status = 'FAIL'
    )
    THEN '✅ All checks passed: YES'
    ELSE '❌ Some checks failed: NO'
END AS summary;

\echo '----------------------------------------'
\echo ''

-- Detailed constraint info (for debugging)
\echo 'Trades Constraints:'
DO $$
DECLARE
    trades_table CONSTANT regclass := 'trades'::regclass;
BEGIN
    RAISE NOTICE '%',
        (SELECT string_agg(conname || ' (' || CASE contype
               WHEN 'u' THEN 'UNIQUE'
               WHEN 'p' THEN 'PRIMARY KEY'
               WHEN 'c' THEN 'CHECK'
               WHEN 'f' THEN 'FOREIGN KEY'
           END || ')', ', ')
         FROM pg_constraint
         WHERE conrelid = trades_table
           AND conname LIKE 'uq_trades_%'
         ORDER BY conname);
END $$;

\echo ''
\echo 'Trades Indexes:'
SELECT indexname,
       CASE
           WHEN indexdef LIKE '%WHERE%' THEN 'PARTIAL'
           ELSE 'FULL'
       END AS index_type
FROM pg_indexes
WHERE tablename = 'trades'
  AND indexname LIKE 'idx_trades_%' OR indexname LIKE 'uq_trades_%'
ORDER BY indexname;

\echo ''
\echo 'Signals Constraints:'
DO $$
DECLARE
    signals_table CONSTANT regclass := 'signals'::regclass;
BEGIN
    RAISE NOTICE '%',
        (SELECT string_agg(conname || ' (' || CASE contype
               WHEN 'u' THEN 'UNIQUE'
               WHEN 'p' THEN 'PRIMARY KEY'
               WHEN 'c' THEN 'CHECK'
               WHEN 'f' THEN 'FOREIGN KEY'
           END || ')', ', ')
         FROM pg_constraint
         WHERE conrelid = signals_table
           AND (conname LIKE 'chk_effective_%' OR conname LIKE 'uq_signal_%')
         ORDER BY conname);
END $$;

\echo ''
\echo 'Signals Indexes:'
SELECT indexname
FROM pg_indexes
WHERE tablename = 'signals'
  AND indexname LIKE 'idx_signal_%'
ORDER BY indexname;

\echo ''
\echo '========================================'
\echo 'Verification Complete'
\echo '========================================'
\echo ''

-- Cleanup
DROP FUNCTION verify_v007_migration();
