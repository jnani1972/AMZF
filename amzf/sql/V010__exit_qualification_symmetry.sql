-- ════════════════════════════════════════════════════════════════════════════
-- V010: Exit Qualification Symmetry
-- ════════════════════════════════════════════════════════════════════════════
--
-- PURPOSE:
-- Complete exit qualification architecture to mirror entry qualification.
-- Adds ExitIntent for execution qualification + outcome tracking.
-- Moves cooldown enforcement to DB for restart safety.
-- Persists trade direction for correct exit logic.
--
-- CHANGES:
-- 1. exit_intents table (execution qualification + order tracking)
-- 2. trades.direction column (persist at creation, not derive)
-- 3. Updated generate_exit_episode() with cooldown enforcement
-- 4. Indexes for exit intent queries
--
-- SYMMETRY ACHIEVED:
-- Entry: Signal → Delivery → Intent (APPROVED/REJECTED) → Trade
-- Exit:  Signal → Intent (APPROVED/REJECTED) → Order → Trade Close
--
-- ════════════════════════════════════════════════════════════════════════════

-- ════════════════════════════════════════════════════════════════════════════
-- 1. EXIT INTENTS (Execution Qualification + Outcome Tracking)
-- ════════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS exit_intents (
    -- Identity
    exit_intent_id VARCHAR(36) PRIMARY KEY,

    -- References
    exit_signal_id VARCHAR(36) NOT NULL REFERENCES exit_signals(exit_signal_id),
    trade_id VARCHAR(36) NOT NULL,  -- FK to trades (composite key, skip for now)
    user_broker_id VARCHAR(36) NOT NULL,  -- FK to user_brokers (composite key, skip for now)

    -- Exit context
    exit_reason VARCHAR(20) NOT NULL,
    episode_id INTEGER NOT NULL,

    -- Qualification outcome
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    -- PENDING: Awaiting qualification
    -- APPROVED: Qualified for execution
    -- REJECTED: Failed qualification
    -- PLACED: Order placed with broker
    -- FILLED: Order filled, trade can close
    -- FAILED: Order placement/execution failed
    -- CANCELLED: Manually cancelled or superseded

    validation_passed BOOLEAN NOT NULL,
    validation_errors TEXT[],

    -- Execution details (if APPROVED)
    calculated_qty INTEGER,
    order_type VARCHAR(10),  -- MARKET | LIMIT
    limit_price NUMERIC(20,2),
    product_type VARCHAR(20),

    -- Broker execution tracking
    broker_order_id VARCHAR(100),  -- Set when PLACED
    placed_at TIMESTAMP,
    filled_at TIMESTAMP,
    cancelled_at TIMESTAMP,

    -- Failure tracking
    error_code VARCHAR(50),
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,

    -- Standard audit
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 1,

    -- Constraints
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'PLACED', 'FILLED', 'FAILED', 'CANCELLED')),
    CHECK (exit_reason IN ('TARGET_HIT', 'STOP_LOSS', 'TRAILING_STOP', 'TIME_BASED', 'MANUAL', 'SUPERSEDED')),
    CHECK (validation_passed = true OR status = 'REJECTED'),
    CHECK (status != 'PLACED' OR broker_order_id IS NOT NULL),
    CHECK (status != 'FILLED' OR filled_at IS NOT NULL)
);

-- ════════════════════════════════════════════════════════════════════════════
-- IDEMPOTENCY ENFORCEMENT: Exit Intent Uniqueness
-- ════════════════════════════════════════════════════════════════════════════
-- One intent per (trade, user_broker, reason, episode)
-- Prevents duplicate exit orders for same episode
-- ════════════════════════════════════════════════════════════════════════════

CREATE UNIQUE INDEX idx_exit_intent_unique ON exit_intents (
    trade_id,
    user_broker_id,
    exit_reason,
    episode_id
) WHERE deleted_at IS NULL;

COMMENT ON INDEX idx_exit_intent_unique IS
'Ensures one exit intent per (trade, user_broker, reason, episode).
Prevents duplicate exit orders. Idempotency enforcement.';

-- ════════════════════════════════════════════════════════════════════════════
-- Performance Indexes
-- ════════════════════════════════════════════════════════════════════════════

-- Query by exit signal
CREATE INDEX idx_exit_intents_signal ON exit_intents (exit_signal_id)
WHERE deleted_at IS NULL;

-- Query by trade + status (find pending exits for trade)
CREATE INDEX idx_exit_intents_trade_status ON exit_intents (trade_id, status)
WHERE deleted_at IS NULL;

-- Query by broker order ID (reconciliation)
CREATE INDEX idx_exit_intents_broker_order ON exit_intents (broker_order_id)
WHERE broker_order_id IS NOT NULL AND deleted_at IS NULL;

-- Find pending/approved intents (execution queue)
CREATE INDEX idx_exit_intents_pending ON exit_intents (status, created_at)
WHERE status IN ('PENDING', 'APPROVED') AND deleted_at IS NULL;

-- Find failed intents (retry queue)
CREATE INDEX idx_exit_intents_failed ON exit_intents (status, retry_count, created_at)
WHERE status = 'FAILED' AND deleted_at IS NULL;

COMMENT ON TABLE exit_intents IS
'Exit execution qualification and order tracking. Mirrors trade_intents for entry.
Only TMS (via OrderExecutionService) may update status to PLACED/FILLED/FAILED.
Exit intent lifecycle: PENDING → APPROVED/REJECTED → PLACED → FILLED/FAILED/CANCELLED';

-- ════════════════════════════════════════════════════════════════════════════
-- 2. PERSIST TRADE DIRECTION
-- ════════════════════════════════════════════════════════════════════════════
-- Direction must be persisted at trade creation, not derived.
-- Required for correct exit logic (LONG vs SHORT exit conditions differ).
-- ════════════════════════════════════════════════════════════════════════════

DO $$
BEGIN
    -- Add direction column if not exists
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'trades' AND column_name = 'direction'
    ) THEN
        ALTER TABLE trades
        ADD COLUMN direction VARCHAR(10);

        -- Add constraint after column exists
        ALTER TABLE trades
        ADD CONSTRAINT chk_trades_direction
        CHECK (direction IN ('BUY', 'SELL'));

        RAISE NOTICE 'Added direction column to trades table';
    ELSE
        RAISE NOTICE 'Direction column already exists in trades table';
    END IF;
END $$;

-- Create index for direction queries
CREATE INDEX IF NOT EXISTS idx_trades_direction ON trades (direction)
WHERE deleted_at IS NULL;

COMMENT ON COLUMN trades.direction IS
'Trade direction: BUY (LONG) or SELL (SHORT).
Persisted at trade creation from entry signal.
Required for correct exit qualification logic.';

-- ════════════════════════════════════════════════════════════════════════════
-- 3. COOLDOWN ENFORCEMENT IN DB (Restart-Safe)
-- ════════════════════════════════════════════════════════════════════════════
-- Move cooldown from in-memory to DB function.
-- Episode generation rejects if within cooldown window.
-- ════════════════════════════════════════════════════════════════════════════

CREATE OR REPLACE FUNCTION generate_exit_episode(
    p_trade_id VARCHAR(36),
    p_exit_reason VARCHAR(20)
) RETURNS INTEGER AS $$
DECLARE
    v_last_episode_time TIMESTAMP;
    v_cooldown_seconds INTEGER := 30;  -- 30 second re-arm cooldown
    v_next_episode INTEGER;
BEGIN
    -- Get timestamp of last episode for this (trade, reason)
    SELECT MAX(created_at) INTO v_last_episode_time
    FROM exit_signals
    WHERE trade_id = p_trade_id
      AND exit_reason = p_exit_reason
      AND deleted_at IS NULL;

    -- Check cooldown (restart-safe)
    IF v_last_episode_time IS NOT NULL THEN
        IF NOW() < (v_last_episode_time + (v_cooldown_seconds || ' seconds')::INTERVAL) THEN
            -- Cooldown active - raise exception to reject episode generation
            RAISE EXCEPTION 'EXIT_COOLDOWN_ACTIVE: Exit cooldown for trade % reason % active until %',
                p_trade_id,
                p_exit_reason,
                (v_last_episode_time + (v_cooldown_seconds || ' seconds')::INTERVAL);
        END IF;
    END IF;

    -- Generate next episode ID (monotonic increment)
    SELECT COALESCE(MAX(episode_id), 0) + 1 INTO v_next_episode
    FROM exit_signals
    WHERE trade_id = p_trade_id
      AND exit_reason = p_exit_reason
      AND deleted_at IS NULL;

    RETURN v_next_episode;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION generate_exit_episode IS
'Generate monotonic episode ID for exit signal.
Enforces 30-second re-arm cooldown at DB level.
Raises exception if cooldown active (restart-safe).
Returns next episode ID if allowed.';

-- ════════════════════════════════════════════════════════════════════════════
-- 4. HELPER FUNCTIONS
-- ════════════════════════════════════════════════════════════════════════════

-- Function to atomically consume exit intent (mark as PLACED)
CREATE OR REPLACE FUNCTION place_exit_order(
    p_exit_intent_id VARCHAR(36),
    p_broker_order_id VARCHAR(100)
) RETURNS BOOLEAN AS $$
DECLARE
    v_current_status VARCHAR(20);
    v_version INTEGER;
    v_rows_updated INTEGER;
BEGIN
    -- Get current status and version
    SELECT status, version INTO v_current_status, v_version
    FROM exit_intents
    WHERE exit_intent_id = p_exit_intent_id
      AND deleted_at IS NULL
    FOR UPDATE;  -- Lock row

    -- Check if intent is in APPROVED state
    IF v_current_status IS NULL THEN
        RETURN FALSE;  -- Intent not found
    END IF;

    IF v_current_status != 'APPROVED' THEN
        RETURN FALSE;  -- Intent not approved or already placed
    END IF;

    -- Atomically update to PLACED with optimistic lock
    UPDATE exit_intents
    SET status = 'PLACED',
        broker_order_id = p_broker_order_id,
        placed_at = NOW(),
        updated_at = NOW(),
        version = version + 1
    WHERE exit_intent_id = p_exit_intent_id
      AND version = v_version
      AND deleted_at IS NULL;

    GET DIAGNOSTICS v_rows_updated = ROW_COUNT;

    RETURN v_rows_updated = 1;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION place_exit_order IS
'Atomically transition exit intent from APPROVED → PLACED.
Uses optimistic locking to prevent race conditions.
Returns true if transition successful, false otherwise.';

-- ════════════════════════════════════════════════════════════════════════════
-- VERIFICATION
-- ════════════════════════════════════════════════════════════════════════════

DO $$
DECLARE
    v_table_count INTEGER;
    v_index_count INTEGER;
    v_function_count INTEGER;
    v_direction_exists BOOLEAN;
BEGIN
    -- Count tables
    SELECT COUNT(*) INTO v_table_count
    FROM information_schema.tables
    WHERE table_schema = 'public'
      AND table_name = 'exit_intents';

    -- Count indexes
    SELECT COUNT(*) INTO v_index_count
    FROM pg_indexes
    WHERE schemaname = 'public'
      AND indexname IN (
          'idx_exit_intent_unique',
          'idx_exit_intents_signal',
          'idx_exit_intents_trade_status',
          'idx_exit_intents_broker_order',
          'idx_exit_intents_pending',
          'idx_exit_intents_failed',
          'idx_trades_direction'
      );

    -- Count functions
    SELECT COUNT(*) INTO v_function_count
    FROM pg_proc
    WHERE proname IN ('place_exit_order');
    -- Note: generate_exit_episode already existed, so we don't count it as new

    -- Check direction column
    SELECT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'trades' AND column_name = 'direction'
    ) INTO v_direction_exists;

    -- Report
    RAISE NOTICE '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━';
    RAISE NOTICE '✅ V010 Migration Verification';
    RAISE NOTICE '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━';
    RAISE NOTICE '';
    RAISE NOTICE '   Tables created: exit_intents (% found)', v_table_count;
    RAISE NOTICE '   Indexes created: % (expected 7)', v_index_count;
    RAISE NOTICE '   Functions created/updated: % (expected 1+)', v_function_count;
    RAISE NOTICE '   trades.direction column: %', CASE WHEN v_direction_exists THEN 'EXISTS' ELSE 'MISSING' END;
    RAISE NOTICE '';
    RAISE NOTICE '⚠️  Exit qualification symmetry enabled:';
    RAISE NOTICE '      - ExitIntent tracks execution qualification + outcome';
    RAISE NOTICE '      - Cooldown enforced at DB level (restart-safe)';
    RAISE NOTICE '      - Trade direction persisted for correct exit logic';
    RAISE NOTICE '';
    RAISE NOTICE '✅ Entry/Exit symmetry achieved';
    RAISE NOTICE '   Entry: Signal → Delivery → Intent → Trade';
    RAISE NOTICE '   Exit:  Signal → Intent → Order → Trade Close';
    RAISE NOTICE '';
END $$;
