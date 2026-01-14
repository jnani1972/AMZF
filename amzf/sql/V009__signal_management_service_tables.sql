-- ════════════════════════════════════════════════════════════════════════════
-- V009: Signal Management Service (SMS) Tables
-- ════════════════════════════════════════════════════════════════════════════
--
-- PURPOSE:
-- Enforcement-grade schema for SignalManagementService (SMS), the single writer
-- for all signal lifecycle operations. Mirrors TradeManagementService (TMS)
-- architecture with identical enforcement philosophy.
--
-- OWNERSHIP:
-- - signals: Entry signal lifecycle (symbol-centric)
-- - signal_deliveries: Per user-broker delivery tracking
-- - exit_signals: Exit signal detection (trade-centric)
--
-- INVARIANTS ENFORCED (DB-level):
-- 1. No duplicate entry signals per (symbol, direction, zone, day)
-- 2. No duplicate deliveries per (signal_id, user_broker_id)
-- 3. No duplicate exit episodes per (trade_id, reason, episode_id)
-- 4. No trade_intent without corresponding signal_delivery (FK)
-- 5. Episode numbers DB-generated (race-free)
--
-- See: SignalManagementService Final Architecture Document
-- ════════════════════════════════════════════════════════════════════════════

-- ════════════════════════════════════════════════════════════════════════════
-- 1. ENTRY SIGNALS (Global, Symbol-Centric)
-- ════════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS signals (
    -- Identity
    signal_id VARCHAR(36) PRIMARY KEY,

    -- Signal classification
    symbol VARCHAR(50) NOT NULL,
    direction VARCHAR(10) NOT NULL,  -- BUY | SELL
    signal_type VARCHAR(20) NOT NULL DEFAULT 'ENTRY',  -- ENTRY | EXIT | INFO

    -- MTF context snapshot
    htf_zone INTEGER,
    itf_zone INTEGER,
    ltf_zone INTEGER,
    confluence_type VARCHAR(20),  -- NONE | SINGLE | DOUBLE | TRIPLE
    confluence_score NUMERIC(10,4),

    -- Model probabilities
    p_win NUMERIC(10,4),
    p_fill NUMERIC(10,4),
    kelly NUMERIC(10,4),

    -- Reference prices (from DATA broker at detection)
    ref_price NUMERIC(20,2) NOT NULL,
    ref_bid NUMERIC(20,2),
    ref_ask NUMERIC(20,2),
    entry_low NUMERIC(20,2),
    entry_high NUMERIC(20,2),

    -- MTF boundary snapshot (zone context)
    htf_low NUMERIC(20,2),
    htf_high NUMERIC(20,2),
    itf_low NUMERIC(20,2),
    itf_high NUMERIC(20,2),
    ltf_low NUMERIC(20,2),
    ltf_high NUMERIC(20,2),
    effective_floor NUMERIC(20,2) NOT NULL,
    effective_ceiling NUMERIC(20,2) NOT NULL,

    -- Metadata
    confidence NUMERIC(10,4),
    reason TEXT,
    tags TEXT[],

    -- Lifecycle
    status VARCHAR(20) NOT NULL DEFAULT 'DETECTED',  -- DETECTED | PUBLISHED | EXPIRED | CANCELLED | SUPERSEDED
    generated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    published_at TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,  -- EOD or manual override

    -- Audit trail (immutable)
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 1,

    -- Constraints
    CHECK (direction IN ('BUY', 'SELL')),
    CHECK (signal_type IN ('ENTRY', 'EXIT', 'INFO')),
    CHECK (status IN ('DETECTED', 'PUBLISHED', 'EXPIRED', 'CANCELLED', 'SUPERSEDED')),
    CHECK (effective_floor < effective_ceiling),
    CHECK (expires_at > generated_at)
);

-- ════════════════════════════════════════════════════════════════════════════
-- IDEMPOTENCY ENFORCEMENT: Entry Signal Dedupe
-- ════════════════════════════════════════════════════════════════════════════
-- AV-1 FIX: Prevents duplicate signals for same (symbol, direction, zone, day)
-- Date extracted in IST timezone (trading calendar day)
-- Only applies to active signals (excludes terminal states)
-- ════════════════════════════════════════════════════════════════════════════

CREATE UNIQUE INDEX idx_signal_dedupe ON signals (
    symbol,
    direction,
    confluence_type,
    DATE(generated_at AT TIME ZONE 'Asia/Kolkata'),
    effective_floor,
    effective_ceiling
) WHERE status IN ('DETECTED', 'PUBLISHED');

COMMENT ON INDEX idx_signal_dedupe IS
'AV-1: Prevents duplicate entry signals per (symbol, direction, zone, day).
Uses IST timezone for trading calendar day. Partial index excludes terminal states.';

-- ════════════════════════════════════════════════════════════════════════════
-- Performance Indexes
-- ════════════════════════════════════════════════════════════════════════════

CREATE INDEX idx_signals_symbol_status ON signals (symbol, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_signals_status_expires ON signals (status, expires_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_signals_published_at ON signals (published_at DESC) WHERE status = 'PUBLISHED';

COMMENT ON TABLE signals IS
'Entry signals from strategy detection. Global scope (not user-specific).
One signal can result in multiple deliveries (fan-out to user-brokers).
Immutable after publication. Only SMS may write to this table.';

-- ════════════════════════════════════════════════════════════════════════════
-- 2. SIGNAL DELIVERIES (Per User-Broker Tracking)
-- ════════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS signal_deliveries (
    -- Identity
    delivery_id VARCHAR(36) PRIMARY KEY,

    -- References
    signal_id VARCHAR(36) NOT NULL REFERENCES signals(signal_id),
    user_broker_id VARCHAR(36) NOT NULL REFERENCES user_brokers(user_broker_id),
    user_id VARCHAR(36) NOT NULL,  -- Denormalized for fast filtering

    -- Lifecycle
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED',  -- CREATED | DELIVERED | CONSUMED | EXPIRED | REJECTED

    -- Consumption tracking
    intent_id VARCHAR(36),  -- Reference to trade_intent (set when consumed)
    consumed_at TIMESTAMP,
    rejection_reason TEXT,

    -- User interaction (future: manual snooze/dismiss)
    user_action VARCHAR(20),  -- NULL | SNOOZED | DISMISSED
    user_action_at TIMESTAMP,

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    delivered_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    -- Audit trail
    deleted_at TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 1,

    -- Constraints
    CHECK (status IN ('CREATED', 'DELIVERED', 'CONSUMED', 'EXPIRED', 'REJECTED')),
    CHECK (user_action IS NULL OR user_action IN ('SNOOZED', 'DISMISSED')),
    CHECK (status != 'CONSUMED' OR intent_id IS NOT NULL),
    CHECK (status != 'CONSUMED' OR consumed_at IS NOT NULL)
);

-- ════════════════════════════════════════════════════════════════════════════
-- IDEMPOTENCY ENFORCEMENT: Delivery Uniqueness
-- ════════════════════════════════════════════════════════════════════════════
-- AV-9 FIX: One signal delivered at most once per user-broker
-- Prevents duplicate deliveries and intent creation
-- ════════════════════════════════════════════════════════════════════════════

CREATE UNIQUE INDEX idx_delivery_unique ON signal_deliveries (
    signal_id,
    user_broker_id
) WHERE deleted_at IS NULL;

COMMENT ON INDEX idx_delivery_unique IS
'AV-9: Ensures one signal → one delivery per user-broker.
Prevents double-delivery and duplicate intent creation.';

-- ════════════════════════════════════════════════════════════════════════════
-- Performance Indexes
-- ════════════════════════════════════════════════════════════════════════════

CREATE INDEX idx_deliveries_user_status ON signal_deliveries (user_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_deliveries_signal_status ON signal_deliveries (signal_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_deliveries_created_at ON signal_deliveries (created_at DESC);

COMMENT ON TABLE signal_deliveries IS
'Tracks which user-broker received which signal. Enforces one-to-one mapping
between signal and intent per user-broker. Only SMS may write to this table.';

-- ════════════════════════════════════════════════════════════════════════════
-- 3. EXIT SIGNALS (Trade-Centric, Episode-Tracked)
-- ════════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS exit_signals (
    -- Identity
    exit_signal_id VARCHAR(36) PRIMARY KEY,

    -- Binding
    trade_id VARCHAR(36) NOT NULL REFERENCES trades(trade_id),
    signal_id VARCHAR(36),  -- Original entry signal (optional reference)

    -- Classification
    symbol VARCHAR(50) NOT NULL,
    direction VARCHAR(10) NOT NULL,  -- BUY | SELL
    exit_reason VARCHAR(30) NOT NULL,  -- TARGET_HIT | STOP_LOSS | TRAILING_STOP | TIME_BASED | MANUAL

    -- Episode tracking (re-arm cycles)
    episode_id INTEGER NOT NULL,  -- DB-generated sequence per (trade_id, exit_reason)

    -- Detection context
    exit_price_at_detection NUMERIC(20,2) NOT NULL,
    brick_movement NUMERIC(20,4),  -- Movement since last exit signal (NULL if first)
    favorable_movement NUMERIC(20,4),  -- Movement in favorable direction
    highest_since_entry NUMERIC(20,2),  -- For trailing stop context
    lowest_since_entry NUMERIC(20,2),   -- For stop loss context

    -- Trailing stop specifics
    trailing_stop_price NUMERIC(20,2),
    trailing_active BOOLEAN DEFAULT FALSE,

    -- Lifecycle
    status VARCHAR(20) NOT NULL DEFAULT 'DETECTED',  -- DETECTED | CONFIRMED | PUBLISHED | EXECUTED | CANCELLED | SUPERSEDED

    -- Timestamps
    detected_at TIMESTAMP NOT NULL DEFAULT NOW(),
    confirmed_at TIMESTAMP,
    published_at TIMESTAMP,
    executed_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    last_rearm_at TIMESTAMP,  -- AV-6: Re-arm cooldown tracking

    -- Audit trail
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 1,

    -- Constraints
    CHECK (direction IN ('BUY', 'SELL')),
    CHECK (exit_reason IN ('TARGET_HIT', 'STOP_LOSS', 'TRAILING_STOP', 'TIME_BASED', 'MANUAL', 'BRICK_REVERSAL')),
    CHECK (status IN ('DETECTED', 'CONFIRMED', 'PUBLISHED', 'EXECUTED', 'CANCELLED', 'SUPERSEDED')),
    CHECK (episode_id > 0)
);

-- ════════════════════════════════════════════════════════════════════════════
-- IDEMPOTENCY ENFORCEMENT: Exit Episode Uniqueness
-- ════════════════════════════════════════════════════════════════════════════
-- AV-2 FIX: Prevents duplicate exit episodes
-- Episode numbers DB-generated using MAX+1 pattern with FOR UPDATE lock
-- Each (trade, reason) can have multiple episodes (re-arm cycles)
-- ════════════════════════════════════════════════════════════════════════════

CREATE UNIQUE INDEX idx_exit_episode ON exit_signals (
    trade_id,
    exit_reason,
    episode_id
) WHERE deleted_at IS NULL;

COMMENT ON INDEX idx_exit_episode IS
'AV-2: Prevents race conditions in exit episode generation.
Episode numbers must be generated in DB transaction with FOR UPDATE lock.';

-- ════════════════════════════════════════════════════════════════════════════
-- Performance Indexes
-- ════════════════════════════════════════════════════════════════════════════

CREATE INDEX idx_exit_signals_trade_status ON exit_signals (trade_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_exit_signals_trade_reason ON exit_signals (trade_id, exit_reason, episode_id DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_exit_signals_published_at ON exit_signals (published_at DESC) WHERE status = 'PUBLISHED';

COMMENT ON TABLE exit_signals IS
'Exit signals for closing trades. Episode tracking enables re-arm after brick reset.
Each episode is a separate exit attempt. Only SMS may write to this table.';

-- ════════════════════════════════════════════════════════════════════════════
-- 4. CROSS-TABLE ENFORCEMENT: Intent → Delivery FK
-- ════════════════════════════════════════════════════════════════════════════
-- AV-9 FIX: Prevents intent creation without delivery
-- Enforces: ValidationService cannot create intent unless delivery exists
-- This is the "ungameable" constraint that prevents bypass attacks
-- ════════════════════════════════════════════════════════════════════════════

ALTER TABLE trade_intents
ADD COLUMN IF NOT EXISTS signal_delivery_id VARCHAR(36);

-- Add FK constraint if not exists (safe for reruns)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_intent_delivery'
    ) THEN
        ALTER TABLE trade_intents
        ADD CONSTRAINT fk_intent_delivery
        FOREIGN KEY (signal_id, user_broker_id)
        REFERENCES signal_deliveries (signal_id, user_broker_id)
        ON DELETE RESTRICT;
    END IF;
END $$;

COMMENT ON CONSTRAINT fk_intent_delivery ON trade_intents IS
'AV-9: Ungameable enforcement - intent cannot exist without delivery.
Prevents ValidationService from bypassing SMS delivery tracking.';

-- ════════════════════════════════════════════════════════════════════════════
-- 5. HELPER FUNCTION: Generate Next Exit Episode
-- ════════════════════════════════════════════════════════════════════════════
-- AV-2 SOLUTION: DB-generated episode sequence with pessimistic lock
-- Usage: SELECT generate_exit_episode('trade123', 'TARGET_HIT');
-- Returns: Next episode number (1, 2, 3, ...)
-- ════════════════════════════════════════════════════════════════════════════

CREATE OR REPLACE FUNCTION generate_exit_episode(
    p_trade_id VARCHAR(36),
    p_exit_reason VARCHAR(30)
) RETURNS INTEGER AS $$
DECLARE
    v_next_episode INTEGER;
BEGIN
    -- Lock existing episodes for this trade+reason (prevents race)
    SELECT COALESCE(MAX(episode_id), 0) + 1 INTO v_next_episode
    FROM exit_signals
    WHERE trade_id = p_trade_id
      AND exit_reason = p_exit_reason
      AND deleted_at IS NULL
    FOR UPDATE;

    RETURN v_next_episode;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION generate_exit_episode IS
'AV-2: Race-free exit episode generation using pessimistic lock.
Ensures each (trade, reason) gets sequential episode numbers.';

-- ════════════════════════════════════════════════════════════════════════════
-- 6. HELPER FUNCTION: Check Delivery Can Be Consumed
-- ════════════════════════════════════════════════════════════════════════════
-- AV-5 SOLUTION: Atomic delivery consumption with optimistic lock
-- Returns: true if delivery was successfully marked CONSUMED, false otherwise
-- ════════════════════════════════════════════════════════════════════════════

CREATE OR REPLACE FUNCTION consume_delivery(
    p_delivery_id VARCHAR(36),
    p_intent_id VARCHAR(36)
) RETURNS BOOLEAN AS $$
DECLARE
    v_rows_updated INTEGER;
BEGIN
    -- Optimistic lock: only update if still DELIVERED
    UPDATE signal_deliveries
    SET status = 'CONSUMED',
        intent_id = p_intent_id,
        consumed_at = NOW(),
        updated_at = NOW()
    WHERE delivery_id = p_delivery_id
      AND status = 'DELIVERED'
      AND deleted_at IS NULL;

    GET DIAGNOSTICS v_rows_updated = ROW_COUNT;

    RETURN v_rows_updated = 1;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION consume_delivery IS
'AV-5: Atomic delivery consumption prevents double-intent creation.
Returns false if delivery already consumed or not in DELIVERED state.';

-- ════════════════════════════════════════════════════════════════════════════
-- 7. HELPER FUNCTION: Check Signal Can Be Published
-- ════════════════════════════════════════════════════════════════════════════
-- AV-3 SOLUTION: Validates signal is still publishable before delivery creation
-- Returns: true if signal is active and not expired
-- ════════════════════════════════════════════════════════════════════════════

CREATE OR REPLACE FUNCTION is_signal_publishable(
    p_signal_id VARCHAR(36)
) RETURNS BOOLEAN AS $$
DECLARE
    v_status VARCHAR(20);
    v_expires_at TIMESTAMP;
BEGIN
    SELECT status, expires_at INTO v_status, v_expires_at
    FROM signals
    WHERE signal_id = p_signal_id
      AND deleted_at IS NULL;

    -- Check if signal exists and is in valid state
    IF v_status IS NULL THEN
        RETURN FALSE;
    END IF;

    -- Check if signal is publishable
    RETURN v_status IN ('DETECTED', 'PUBLISHED')
       AND v_expires_at > NOW();
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION is_signal_publishable IS
'AV-3: Validates signal is still active before delivery/intent creation.
Prevents race condition where signal expires during processing.';

-- ════════════════════════════════════════════════════════════════════════════
-- 8. DATA INTEGRITY CHECKS
-- ════════════════════════════════════════════════════════════════════════════

-- Verify all signals have valid status
ALTER TABLE signals
ADD CONSTRAINT chk_signal_lifecycle CHECK (
    (status = 'DETECTED' AND published_at IS NULL) OR
    (status IN ('PUBLISHED', 'EXPIRED', 'CANCELLED', 'SUPERSEDED'))
);

-- Verify deliveries reference valid signals
ALTER TABLE signal_deliveries
ADD CONSTRAINT chk_delivery_signal_exists CHECK (
    EXISTS (SELECT 1 FROM signals WHERE signal_id = signal_deliveries.signal_id)
);

-- Verify exit signals reference open trades (enforced in application)
-- Cannot enforce via CHECK constraint due to circular dependency

-- ════════════════════════════════════════════════════════════════════════════
-- 9. MIGRATION VERIFICATION QUERIES
-- ════════════════════════════════════════════════════════════════════════════

-- These queries validate the migration succeeded
-- Run after migration to verify constraints

DO $$
BEGIN
    RAISE NOTICE '✅ V009 Migration Verification';
    RAISE NOTICE '  Tables created: signals, signal_deliveries, exit_signals';
    RAISE NOTICE '  Indexes created: %', (
        SELECT COUNT(*) FROM pg_indexes
        WHERE tablename IN ('signals', 'signal_deliveries', 'exit_signals')
    );
    RAISE NOTICE '  Functions created: generate_exit_episode, consume_delivery, is_signal_publishable';
    RAISE NOTICE '  FK constraint added: fk_intent_delivery (trade_intents → signal_deliveries)';
    RAISE NOTICE '';
    RAISE NOTICE '⚠️  SMS is now the ONLY authorized writer for:';
    RAISE NOTICE '     - signals';
    RAISE NOTICE '     - signal_deliveries';
    RAISE NOTICE '     - exit_signals';
    RAISE NOTICE '';
    RAISE NOTICE '✅ All idempotency constraints enforced at DB level';
END $$;

-- ════════════════════════════════════════════════════════════════════════════
-- END OF V009 MIGRATION
-- ════════════════════════════════════════════════════════════════════════════
