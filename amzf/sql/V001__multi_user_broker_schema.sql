-- V001__multi_user_broker_schema.sql
-- PostgreSQL: Multi-user, multi-broker Annu trading system
--
-- Architecture:
--   - One admin DATA broker for market data feed (ticks, candles, signals)
--   - Multiple EXEC brokers per user for trade execution
--   - Signal fan-out: one signal → validation per (user, broker) → trade intents
--   - Events scoped: GLOBAL | USER | USER_BROKER

BEGIN;

-- ═══════════════════════════════════════════════════════════════════════════
-- USERS
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS users (
    user_id         TEXT PRIMARY KEY,
    email           TEXT UNIQUE NOT NULL,
    display_name    TEXT NOT NULL,
    password_hash   TEXT NOT NULL,                    -- bcrypt hash
    role            TEXT NOT NULL DEFAULT 'USER',    -- USER | ADMIN
    status          TEXT NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | SUSPENDED | DELETED
    preferences     JSONB NOT NULL DEFAULT '{}',     -- UI prefs, notification settings
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);

-- ═══════════════════════════════════════════════════════════════════════════
-- BROKERS (broker definitions - not per-user)
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS brokers (
    broker_id       TEXT PRIMARY KEY,
    broker_code     TEXT UNIQUE NOT NULL,            -- ZERODHA, FYERS, DHAN, ANGEL, etc.
    broker_name     TEXT NOT NULL,
    adapter_class   TEXT NOT NULL,                   -- Java adapter class name
    config          JSONB NOT NULL DEFAULT '{}',     -- broker-specific config
    supported_exchanges TEXT[] NOT NULL DEFAULT '{}', -- NSE, BSE, NFO, MCX
    supported_products TEXT[] NOT NULL DEFAULT '{}', -- CNC, MIS, NRML
    lot_sizes       JSONB NOT NULL DEFAULT '{}',     -- symbol -> lot size mapping
    margin_rules    JSONB NOT NULL DEFAULT '{}',     -- margin calculation rules
    rate_limits     JSONB NOT NULL DEFAULT '{}',     -- API rate limits
    status          TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_brokers_code ON brokers(broker_code);
CREATE INDEX IF NOT EXISTS idx_brokers_status ON brokers(status);

-- ═══════════════════════════════════════════════════════════════════════════
-- USER_BROKERS (user-broker links with credentials and role)
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS user_brokers (
    user_broker_id  TEXT PRIMARY KEY,
    user_id         TEXT NOT NULL REFERENCES users(user_id),
    broker_id       TEXT NOT NULL REFERENCES brokers(broker_id),
    
    -- Role: DATA (admin only, for market data) or EXEC (for trade execution)
    role            TEXT NOT NULL DEFAULT 'EXEC',    -- DATA | EXEC
    
    -- Credentials (encrypted JSON)
    credentials     JSONB NOT NULL DEFAULT '{}',     -- api_key, api_secret, access_token, etc.
    
    -- Connection state
    connected       BOOLEAN NOT NULL DEFAULT FALSE,
    last_connected  TIMESTAMPTZ,
    connection_error TEXT,
    
    -- User's capital/limits for this broker
    capital_allocated   DECIMAL(18,2) NOT NULL DEFAULT 0,
    max_exposure        DECIMAL(18,2) NOT NULL DEFAULT 0,
    max_per_trade       DECIMAL(18,2) NOT NULL DEFAULT 0,
    max_open_trades     INTEGER NOT NULL DEFAULT 10,
    allowed_symbols     TEXT[] NOT NULL DEFAULT '{}',   -- empty = all symbols
    blocked_symbols     TEXT[] NOT NULL DEFAULT '{}',
    allowed_products    TEXT[] NOT NULL DEFAULT '{}',   -- empty = all products
    
    -- Risk parameters
    max_daily_loss      DECIMAL(18,2) NOT NULL DEFAULT 0,
    max_weekly_loss     DECIMAL(18,2) NOT NULL DEFAULT 0,
    cooldown_minutes    INTEGER NOT NULL DEFAULT 0,     -- after loss, wait before next trade
    
    -- State
    status          TEXT NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | PAUSED | DISABLED
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    UNIQUE(user_id, broker_id)
);

CREATE INDEX IF NOT EXISTS idx_user_brokers_user ON user_brokers(user_id);
CREATE INDEX IF NOT EXISTS idx_user_brokers_broker ON user_brokers(broker_id);
CREATE INDEX IF NOT EXISTS idx_user_brokers_role ON user_brokers(role);
CREATE INDEX IF NOT EXISTS idx_user_brokers_enabled ON user_brokers(enabled) WHERE enabled = TRUE;

-- ═══════════════════════════════════════════════════════════════════════════
-- SIGNALS (generated from main DATA broker feed)
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS signals (
    signal_id       TEXT PRIMARY KEY,
    
    -- Signal identification
    symbol          TEXT NOT NULL,
    direction       TEXT NOT NULL,                   -- BUY | SELL
    signal_type     TEXT NOT NULL,                   -- ENTRY | EXIT | SCALE_IN | SCALE_OUT
    
    -- MTF context at signal generation
    htf_zone        INTEGER,
    itf_zone        INTEGER,
    ltf_zone        INTEGER,
    confluence_type TEXT,                            -- NONE | SINGLE | DOUBLE | TRIPLE
    confluence_score DECIMAL(8,6),
    
    -- Probabilities
    p_win           DECIMAL(8,6),
    p_fill          DECIMAL(8,6),
    kelly           DECIMAL(8,6),
    
    -- Reference prices (from DATA broker)
    ref_price       DECIMAL(18,4) NOT NULL,          -- LTP at signal time
    ref_bid         DECIMAL(18,4),
    ref_ask         DECIMAL(18,4),
    entry_low       DECIMAL(18,4),                   -- suggested entry range
    entry_high      DECIMAL(18,4),
    
    -- Boundaries snapshot
    htf_low         DECIMAL(18,4),
    htf_high        DECIMAL(18,4),
    itf_low         DECIMAL(18,4),
    itf_high        DECIMAL(18,4),
    ltf_low         DECIMAL(18,4),
    ltf_high        DECIMAL(18,4),
    effective_floor DECIMAL(18,4),
    effective_ceiling DECIMAL(18,4),
    
    -- Metadata
    confidence      DECIMAL(8,6) NOT NULL DEFAULT 0,
    reason          TEXT,
    tags            TEXT[] NOT NULL DEFAULT '{}',
    
    -- Timestamps
    generated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ,                     -- signal expiry
    
    -- Status
    status          TEXT NOT NULL DEFAULT 'ACTIVE'   -- ACTIVE | EXPIRED | CANCELLED
);

CREATE INDEX IF NOT EXISTS idx_signals_symbol ON signals(symbol);
CREATE INDEX IF NOT EXISTS idx_signals_generated ON signals(generated_at DESC);
CREATE INDEX IF NOT EXISTS idx_signals_status ON signals(status);
CREATE INDEX IF NOT EXISTS idx_signals_type ON signals(signal_type, direction);

-- ═══════════════════════════════════════════════════════════════════════════
-- TRADE_INTENTS (per user-broker validation result)
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS trade_intents (
    intent_id       TEXT PRIMARY KEY,
    signal_id       TEXT NOT NULL REFERENCES signals(signal_id),
    user_id         TEXT NOT NULL REFERENCES users(user_id),
    broker_id       TEXT NOT NULL REFERENCES brokers(broker_id),
    user_broker_id  TEXT NOT NULL REFERENCES user_brokers(user_broker_id),
    
    -- Validation result
    validation_passed BOOLEAN NOT NULL,
    validation_errors JSONB NOT NULL DEFAULT '[]',   -- array of error codes/messages
    
    -- Calculated values (if passed)
    calculated_qty  INTEGER,
    calculated_value DECIMAL(18,2),
    order_type      TEXT,                            -- MARKET | LIMIT
    limit_price     DECIMAL(18,4),
    product_type    TEXT,                            -- CNC | MIS | NRML
    
    -- Risk calculations
    log_impact      DECIMAL(10,6),
    portfolio_exposure_after DECIMAL(10,6),
    
    -- Execution tracking
    status          TEXT NOT NULL DEFAULT 'PENDING', -- PENDING | APPROVED | REJECTED | EXECUTED | FAILED
    order_id        TEXT,                            -- broker order ID if executed
    trade_id        TEXT,                            -- our trade ID if created
    
    -- Timestamps
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    validated_at    TIMESTAMPTZ,
    executed_at     TIMESTAMPTZ,
    
    -- Error tracking
    error_code      TEXT,
    error_message   TEXT
);

CREATE INDEX IF NOT EXISTS idx_trade_intents_signal ON trade_intents(signal_id);
CREATE INDEX IF NOT EXISTS idx_trade_intents_user ON trade_intents(user_id);
CREATE INDEX IF NOT EXISTS idx_trade_intents_user_broker ON trade_intents(user_broker_id);
CREATE INDEX IF NOT EXISTS idx_trade_intents_status ON trade_intents(status);
CREATE INDEX IF NOT EXISTS idx_trade_intents_created ON trade_intents(created_at DESC);

-- ═══════════════════════════════════════════════════════════════════════════
-- TRADE_EVENTS (extended with user_id, broker_id, scope)
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS trade_events (
    seq             BIGSERIAL PRIMARY KEY,
    event_type      TEXT NOT NULL,
    
    -- Scoping
    scope           TEXT NOT NULL DEFAULT 'GLOBAL',  -- GLOBAL | USER | USER_BROKER
    user_id         TEXT REFERENCES users(user_id),
    broker_id       TEXT REFERENCES brokers(broker_id),
    user_broker_id  TEXT REFERENCES user_brokers(user_broker_id),
    
    -- Payload
    payload         JSONB NOT NULL,
    
    -- Correlation
    signal_id       TEXT,
    intent_id       TEXT,
    trade_id        TEXT,
    order_id        TEXT,
    
    -- Metadata
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      TEXT                              -- user_id of actor
);

CREATE INDEX IF NOT EXISTS idx_trade_events_created_at ON trade_events(created_at);
CREATE INDEX IF NOT EXISTS idx_trade_events_type ON trade_events(event_type);
CREATE INDEX IF NOT EXISTS idx_trade_events_scope ON trade_events(scope);
CREATE INDEX IF NOT EXISTS idx_trade_events_user ON trade_events(user_id) WHERE user_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_trade_events_user_broker ON trade_events(user_broker_id) WHERE user_broker_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_trade_events_signal ON trade_events(signal_id) WHERE signal_id IS NOT NULL;

-- ═══════════════════════════════════════════════════════════════════════════
-- TRADE_EVENTS_AUDIT (unchanged from v01)
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS trade_events_audit (
    audit_id        BIGSERIAL PRIMARY KEY,
    seq             BIGINT NOT NULL,
    action          TEXT NOT NULL,
    old_row         JSONB,
    new_row         JSONB,
    changed_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    changed_by      TEXT
);

CREATE INDEX IF NOT EXISTS idx_trade_events_audit_seq ON trade_events_audit(seq);

-- ═══════════════════════════════════════════════════════════════════════════
-- PORTFOLIOS (per user)
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS portfolios (
    portfolio_id    TEXT PRIMARY KEY,
    user_id         TEXT NOT NULL REFERENCES users(user_id),
    name            TEXT NOT NULL,
    
    -- Capital
    total_capital   DECIMAL(18,2) NOT NULL DEFAULT 0,
    reserved_capital DECIMAL(18,2) NOT NULL DEFAULT 0,
    
    -- Constraints
    max_portfolio_log_loss DECIMAL(10,6) NOT NULL DEFAULT -0.05,
    max_symbol_weight DECIMAL(8,6) NOT NULL DEFAULT 0.10,
    max_symbols     INTEGER NOT NULL DEFAULT 20,
    
    -- Allocation
    allocation_mode TEXT NOT NULL DEFAULT 'EQUAL_WEIGHT',
    
    -- Status
    status          TEXT NOT NULL DEFAULT 'ACTIVE',
    paused          BOOLEAN NOT NULL DEFAULT FALSE,
    
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    UNIQUE(user_id, name)
);

CREATE INDEX IF NOT EXISTS idx_portfolios_user ON portfolios(user_id);

-- ═══════════════════════════════════════════════════════════════════════════
-- TRADES (per user, per broker execution)
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS trades (
    trade_id        TEXT PRIMARY KEY,
    portfolio_id    TEXT NOT NULL REFERENCES portfolios(portfolio_id),
    user_id         TEXT NOT NULL REFERENCES users(user_id),
    broker_id       TEXT NOT NULL REFERENCES brokers(broker_id),
    user_broker_id  TEXT NOT NULL REFERENCES user_brokers(user_broker_id),
    
    -- Signal reference
    signal_id       TEXT REFERENCES signals(signal_id),
    intent_id       TEXT REFERENCES trade_intents(intent_id),
    
    -- Identity
    symbol          TEXT NOT NULL,
    trade_number    INTEGER NOT NULL,
    
    -- Entry
    entry_price     DECIMAL(18,4) NOT NULL,
    entry_qty       INTEGER NOT NULL,
    entry_value     DECIMAL(18,2) NOT NULL,
    entry_timestamp TIMESTAMPTZ NOT NULL,
    product_type    TEXT NOT NULL,                   -- CNC | MIS | NRML
    
    -- MTF context at entry (snapshot)
    entry_htf_zone  INTEGER,
    entry_itf_zone  INTEGER,
    entry_ltf_zone  INTEGER,
    entry_confluence_type TEXT,
    entry_confluence_score DECIMAL(8,6),
    
    -- Boundaries snapshot
    entry_htf_low   DECIMAL(18,4),
    entry_htf_high  DECIMAL(18,4),
    entry_itf_low   DECIMAL(18,4),
    entry_itf_high  DECIMAL(18,4),
    entry_ltf_low   DECIMAL(18,4),
    entry_ltf_high  DECIMAL(18,4),
    entry_effective_floor DECIMAL(18,4),
    entry_effective_ceiling DECIMAL(18,4),
    
    -- Log metrics
    log_loss_at_floor DECIMAL(10,6),
    max_log_loss_allowed DECIMAL(10,6) NOT NULL DEFAULT -0.08,
    
    -- Exit targets (calculated at entry)
    exit_min_profit_price DECIMAL(18,4),
    exit_target_price DECIMAL(18,4),
    exit_stretch_price DECIMAL(18,4),
    exit_primary_price DECIMAL(18,4),
    
    -- Current state
    status          TEXT NOT NULL DEFAULT 'OPEN',    -- OPEN | CLOSED | CANCELLED
    current_price   DECIMAL(18,4),
    current_log_return DECIMAL(10,6),
    unrealized_pnl  DECIMAL(18,2),
    
    -- Trailing state
    trailing_active BOOLEAN NOT NULL DEFAULT FALSE,
    trailing_highest_price DECIMAL(18,4),
    trailing_stop_price DECIMAL(18,4),
    
    -- Exit details
    exit_price      DECIMAL(18,4),
    exit_timestamp  TIMESTAMPTZ,
    exit_trigger    TEXT,
    exit_order_id   TEXT,
    realized_pnl    DECIMAL(18,2),
    realized_log_return DECIMAL(10,6),
    holding_days    INTEGER,
    
    -- Broker reference
    broker_order_id TEXT,                            -- broker's order ID
    broker_trade_id TEXT,                            -- broker's trade ID
    
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_trades_portfolio ON trades(portfolio_id);
CREATE INDEX IF NOT EXISTS idx_trades_user ON trades(user_id);
CREATE INDEX IF NOT EXISTS idx_trades_user_broker ON trades(user_broker_id);
CREATE INDEX IF NOT EXISTS idx_trades_symbol ON trades(symbol);
CREATE INDEX IF NOT EXISTS idx_trades_status ON trades(status);
CREATE INDEX IF NOT EXISTS idx_trades_signal ON trades(signal_id) WHERE signal_id IS NOT NULL;

-- ═══════════════════════════════════════════════════════════════════════════
-- TRIGGERS: Audit + Immutability
-- ═══════════════════════════════════════════════════════════════════════════

-- Audit on insert
CREATE OR REPLACE FUNCTION audit_trade_events_insert()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO trade_events_audit(seq, action, old_row, new_row, changed_by)
    VALUES (NEW.seq, 'INSERT', NULL, to_jsonb(NEW), NEW.created_by);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_trade_events_audit_insert ON trade_events;
CREATE TRIGGER trg_trade_events_audit_insert
AFTER INSERT ON trade_events
FOR EACH ROW EXECUTE FUNCTION audit_trade_events_insert();

-- Block updates/deletes (immutability)
CREATE OR REPLACE FUNCTION block_trade_events_mutation()
RETURNS TRIGGER AS $$
DECLARE
    actor TEXT;
BEGIN
    actor := COALESCE(current_setting('app.user', true), 'unknown');
    
    IF TG_OP = 'UPDATE' THEN
        INSERT INTO trade_events_audit(seq, action, old_row, new_row, changed_by)
        VALUES (OLD.seq, 'UPDATE_ATTEMPT', to_jsonb(OLD), to_jsonb(NEW), actor);
        RAISE EXCEPTION 'trade_events is append-only: UPDATE forbidden';
    ELSIF TG_OP = 'DELETE' THEN
        INSERT INTO trade_events_audit(seq, action, old_row, new_row, changed_by)
        VALUES (OLD.seq, 'DELETE_ATTEMPT', to_jsonb(OLD), NULL, actor);
        RAISE EXCEPTION 'trade_events is append-only: DELETE forbidden';
    END IF;
    
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_trade_events_block_update ON trade_events;
CREATE TRIGGER trg_trade_events_block_update
BEFORE UPDATE ON trade_events
FOR EACH ROW EXECUTE FUNCTION block_trade_events_mutation();

DROP TRIGGER IF EXISTS trg_trade_events_block_delete ON trade_events;
CREATE TRIGGER trg_trade_events_block_delete
BEFORE DELETE ON trade_events
FOR EACH ROW EXECUTE FUNCTION block_trade_events_mutation();

-- updated_at triggers
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_users_updated ON users;
CREATE TRIGGER trg_users_updated BEFORE UPDATE ON users
FOR EACH ROW EXECUTE FUNCTION update_updated_at();

DROP TRIGGER IF EXISTS trg_brokers_updated ON brokers;
CREATE TRIGGER trg_brokers_updated BEFORE UPDATE ON brokers
FOR EACH ROW EXECUTE FUNCTION update_updated_at();

DROP TRIGGER IF EXISTS trg_user_brokers_updated ON user_brokers;
CREATE TRIGGER trg_user_brokers_updated BEFORE UPDATE ON user_brokers
FOR EACH ROW EXECUTE FUNCTION update_updated_at();

DROP TRIGGER IF EXISTS trg_portfolios_updated ON portfolios;
CREATE TRIGGER trg_portfolios_updated BEFORE UPDATE ON portfolios
FOR EACH ROW EXECUTE FUNCTION update_updated_at();

DROP TRIGGER IF EXISTS trg_trades_updated ON trades;
CREATE TRIGGER trg_trades_updated BEFORE UPDATE ON trades
FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- ═══════════════════════════════════════════════════════════════════════════
-- VIEWS
-- ═══════════════════════════════════════════════════════════════════════════

-- Active user-brokers view
CREATE OR REPLACE VIEW v_active_user_brokers AS
SELECT 
    ub.*,
    u.email AS user_email,
    u.display_name AS user_name,
    b.broker_code,
    b.broker_name,
    b.adapter_class
FROM user_brokers ub
JOIN users u ON ub.user_id = u.user_id
JOIN brokers b ON ub.broker_id = b.broker_id
WHERE ub.enabled = TRUE 
  AND ub.status = 'ACTIVE'
  AND u.status = 'ACTIVE'
  AND b.status = 'ACTIVE';

-- Data broker view (admin only)
CREATE OR REPLACE VIEW v_data_brokers AS
SELECT * FROM v_active_user_brokers WHERE role = 'DATA';

-- Exec brokers view (for trade execution)
CREATE OR REPLACE VIEW v_exec_brokers AS
SELECT * FROM v_active_user_brokers WHERE role = 'EXEC';

COMMIT;
