-- V010: Exit Intents + DB-enforced exit episode cooldown
-- Apply in a transaction where possible.
-- Assumes Postgres.

BEGIN;

-- 1) EXIT INTENTS TABLE
CREATE TABLE IF NOT EXISTS exit_intents (
    exit_intent_id      VARCHAR(36) PRIMARY KEY,
    exit_signal_id      VARCHAR(36) NOT NULL REFERENCES exit_signals(exit_signal_id),

    trade_id            VARCHAR(36) NOT NULL,
    user_broker_id      VARCHAR(36) NOT NULL,
    exit_reason         VARCHAR(20) NOT NULL,
    episode_id          INTEGER     NOT NULL,

    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    validation_passed   BOOLEAN     NOT NULL,
    validation_errors   TEXT[],

    calculated_qty      INTEGER,
    order_type          VARCHAR(10),        -- MARKET | LIMIT
    limit_price         NUMERIC(20,2),
    product_type        VARCHAR(20),

    broker_order_id     VARCHAR(100),
    placed_at           TIMESTAMP,
    filled_at           TIMESTAMP,
    cancelled_at        TIMESTAMP,

    error_code          VARCHAR(50),
    error_message       TEXT,
    retry_count         INTEGER NOT NULL DEFAULT 0,

    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMP,
    version             INTEGER   NOT NULL DEFAULT 1,

    CONSTRAINT chk_exit_intent_status
        CHECK (status IN ('PENDING','APPROVED','REJECTED','PLACED','FILLED','FAILED','CANCELLED')),

    CONSTRAINT chk_exit_intent_rejected_has_no_pass
        CHECK (validation_passed = TRUE OR status = 'REJECTED'),

    CONSTRAINT chk_exit_intent_placed_has_order
        CHECK (status <> 'PLACED' OR broker_order_id IS NOT NULL),

    CONSTRAINT chk_exit_intent_filled_has_time
        CHECK (status <> 'FILLED' OR filled_at IS NOT NULL)
);

-- Idempotency: one intent per trade+broker+reason+episode
CREATE UNIQUE INDEX IF NOT EXISTS idx_exit_intent_unique
ON exit_intents (trade_id, user_broker_id, exit_reason, episode_id)
WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_exit_intents_signal
ON exit_intents (exit_signal_id)
WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_exit_intents_trade_status
ON exit_intents (trade_id, status)
WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_exit_intents_broker_order
ON exit_intents (broker_order_id)
WHERE broker_order_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_exit_intents_pending
ON exit_intents (status, created_at)
WHERE status IN ('PENDING','APPROVED');

-- 2) DB-ENFORCED COOLDOWN IN EPISODE GENERATOR
-- This replaces in-memory cooldown as enforcement.
-- Adjust table/column names to your actual exit_signals schema if different.

CREATE OR REPLACE FUNCTION generate_exit_episode(
    p_trade_id    VARCHAR(36),
    p_exit_reason VARCHAR(20),
    p_cooldown_seconds INTEGER DEFAULT 30
) RETURNS INTEGER AS $$
DECLARE
    v_last_time   TIMESTAMP;
    v_next_ep     INTEGER;
BEGIN
    -- last detection time
    SELECT MAX(created_at)
      INTO v_last_time
      FROM exit_signals
     WHERE trade_id = p_trade_id
       AND exit_reason = p_exit_reason
       AND deleted_at IS NULL;

    IF v_last_time IS NOT NULL THEN
        IF NOW() < (v_last_time + (p_cooldown_seconds || ' seconds')::INTERVAL) THEN
            RAISE EXCEPTION 'EXIT_COOLDOWN_ACTIVE'
                USING DETAIL = 'Cooldown active until ' || (v_last_time + (p_cooldown_seconds || ' seconds')::INTERVAL);
        END IF;
    END IF;

    SELECT COALESCE(MAX(episode_id), 0) + 1
      INTO v_next_ep
      FROM exit_signals
     WHERE trade_id = p_trade_id
       AND exit_reason = p_exit_reason
       AND deleted_at IS NULL;

    RETURN v_next_ep;
END;
$$ LANGUAGE plpgsql;

COMMIT;

-- Notes:
-- 1) Caller should catch EXIT_COOLDOWN_ACTIVE and treat as "not eligible yet".
-- 2) If your existing generator signature differs, adapt parameters accordingly.
