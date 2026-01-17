-- V012: Enforce Single Pending Exit Per Trade
--
-- P0 VIOLATION FIX: Prevent multiple pending exit intents per trade
--
-- PROBLEM: Current constraint allows multiple pending exits per trade with different exit_reason:
--   CREATE UNIQUE INDEX idx_exit_intent_unique ON exit_intents (
--       trade_id, user_broker_id, exit_reason, episode_id
--   )
--   This allows: trade_123 with TARGET_HIT APPROVED + STOP_LOSS APPROVED simultaneously
--
-- SOLUTION: Add partial unique index on just trade_id for pending states
--   Only ONE exit intent per trade can be in PENDING, APPROVED, or PLACED status
--   After one exits (FILLED/FAILED/CANCELLED), another can be created
--
-- See: DIAGNOSTIC_AUDIT_REPORT.md - P0 Violation #2
-- Author: User-requested P0 fix
-- Date: 2026-01-13

-- ═══════════════════════════════════════════════════════════════
-- PRE-MIGRATION CHECK: Detect conflicting data
-- ═══════════════════════════════════════════════════════════════
-- Run this query BEFORE applying the migration to identify violations
-- If this returns > 0 rows, you MUST manually resolve conflicts first

-- Pre-check query (manual execution recommended):
-- SELECT
--     trade_id,
--     COUNT(*) AS pending_exit_count,
--     STRING_AGG(exit_intent_id || ' (' || status || ', ' || exit_reason || ')', ', ') AS conflicting_intents
-- FROM exit_intents
-- WHERE status IN ('PENDING', 'APPROVED', 'PLACED')
--   AND deleted_at IS NULL
-- GROUP BY trade_id
-- HAVING COUNT(*) > 1
-- ORDER BY pending_exit_count DESC;

-- If conflicts exist, resolve by:
-- 1. Mark older intents as CANCELLED (keep most recent)
-- 2. Or mark all as FAILED and let the system recreate
--
-- Example resolution:
-- UPDATE exit_intents
-- SET status = 'CANCELLED',
--     updated_at = NOW()
-- WHERE exit_intent_id IN ('older_intent_id_1', 'older_intent_id_2');

-- ═══════════════════════════════════════════════════════════════
-- MIGRATION: Create partial unique index
-- ═══════════════════════════════════════════════════════════════

-- Create partial unique index: one pending exit per trade
-- This constraint applies ONLY to rows where:
-- - status is PENDING, APPROVED, or PLACED (active exit intents)
-- - deleted_at IS NULL (not soft-deleted)
--
-- Once an exit intent moves to FILLED/FAILED/CANCELLED, it no longer
-- participates in the uniqueness constraint, allowing a new exit to be created.

CREATE UNIQUE INDEX IF NOT EXISTS idx_exit_intent_one_pending_per_trade
ON exit_intents (trade_id)
WHERE status::text IN ('PENDING', 'APPROVED', 'PLACED')
  AND deleted_at IS NULL;

-- Document the constraint
COMMENT ON INDEX idx_exit_intent_one_pending_per_trade IS E'P0 FIX: Enforces ONE pending exit intent per trade.\nPrevents race condition where TARGET_HIT and STOP_LOSS fire simultaneously.\nPartial index only applies to active states (PENDING, APPROVED, PLACED).\nOnce an exit completes (FILLED/FAILED/CANCELLED), a new exit can be created.\nSee: DIAGNOSTIC_AUDIT_REPORT.md - P0 Violation #2';

-- ═══════════════════════════════════════════════════════════════
-- VERIFICATION QUERY: Test the constraint
-- ═══════════════════════════════════════════════════════════════
-- After migration, verify constraint is active:

-- Check constraint exists:
-- SELECT indexname, indexdef
-- FROM pg_indexes
-- WHERE tablename = 'exit_intents'
--   AND indexname = 'idx_exit_intent_one_pending_per_trade';

-- Verify duplicate prevention (should fail with constraint violation):
-- INSERT INTO exit_intents (
--     exit_intent_id, trade_id, user_broker_id, episode_id,
--     exit_reason, status, created_at, updated_at
-- ) VALUES (
--     'test_dup_1', 'trade_123', 'ub_123', 'ep_123',
--     'TARGET_HIT', 'APPROVED', NOW(), NOW()
-- );
--
-- INSERT INTO exit_intents (
--     exit_intent_id, trade_id, user_broker_id, episode_id,
--     exit_reason, status, created_at, updated_at
-- ) VALUES (
--     'test_dup_2', 'trade_123', 'ub_123', 'ep_123',  -- Same trade_id
--     'STOP_LOSS', 'APPROVED', NOW(), NOW()            -- Different reason but APPROVED
-- );
-- ❌ Should fail: ERROR: duplicate key value violates unique constraint

-- ═══════════════════════════════════════════════════════════════
-- ROLLBACK PLAN
-- ═══════════════════════════════════════════════════════════════
-- To rollback this migration:
-- DROP INDEX IF EXISTS idx_exit_intent_one_pending_per_trade;

-- ═══════════════════════════════════════════════════════════════
-- IMPACT ASSESSMENT
-- ═══════════════════════════════════════════════════════════════
-- RISK: LOW
-- - Partial index has minimal storage/performance impact
-- - Only prevents duplicate pending exits (desired behavior)
-- - No data changes (constraint only)
-- - Rollback is trivial (drop index)
--
-- BENEFITS:
-- - Prevents race conditions in exit qualification
-- - Ensures deterministic exit behavior per trade
-- - DB-enforced correctness (ungameable)
-- - Aligns with business rules: one exit decision per trade at a time

-- ═══════════════════════════════════════════════════════════════
-- DEFINITION OF DONE
-- ═══════════════════════════════════════════════════════════════
-- [x] Migration file created
-- [x] Pre-migration check query documented
-- [x] Partial unique index created
-- [x] Index comment added
-- [x] Verification queries documented
-- [x] Rollback plan documented
-- [x] Risk assessment completed
--
-- VERIFY:
-- - Run pre-migration check (should return 0 rows or resolve conflicts)
-- - Apply migration (should succeed)
-- - Verify constraint exists (pg_indexes query)
-- - Test duplicate insert (should fail)
-- - Monitor application logs for constraint violations

-- End of V012
