-- ============================================================================
-- V002: Candles, Watchlist, and Exit Signals
-- ============================================================================

-- Candle storage (historical + intraday)
CREATE TABLE IF NOT EXISTS candles (
    id BIGSERIAL PRIMARY KEY,
    symbol TEXT NOT NULL,
    timeframe TEXT NOT NULL,  -- 'HTF_125', 'ITF_25', 'LTF_1'
    ts TIMESTAMPTZ NOT NULL,
    open NUMERIC(12,2) NOT NULL,
    high NUMERIC(12,2) NOT NULL,
    low NUMERIC(12,2) NOT NULL,
    close NUMERIC(12,2) NOT NULL,
    volume BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT unique_candle UNIQUE(symbol, timeframe, ts)
);

CREATE INDEX idx_candles_symbol_tf_ts ON candles(symbol, timeframe, ts DESC);
CREATE INDEX idx_candles_symbol_ts ON candles(symbol, ts DESC);

-- Watchlist (subscribed symbols)
CREATE TABLE IF NOT EXISTS watchlist (
    id BIGSERIAL PRIMARY KEY,
    user_broker_id TEXT NOT NULL,
    symbol TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    added_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT unique_watchlist_entry UNIQUE(user_broker_id, symbol)
);

CREATE INDEX idx_watchlist_user_broker ON watchlist(user_broker_id);
CREATE INDEX idx_watchlist_symbol ON watchlist(symbol);
CREATE INDEX idx_watchlist_enabled ON watchlist(enabled) WHERE enabled = true;

-- Exit signals (for brick movement tracking)
CREATE TABLE IF NOT EXISTS exit_signals (
    exit_signal_id TEXT PRIMARY KEY,
    trade_id TEXT NOT NULL,
    signal_id TEXT,
    symbol TEXT NOT NULL,
    direction TEXT NOT NULL,
    exit_reason TEXT NOT NULL,
    exit_price NUMERIC(12,2) NOT NULL,
    brick_movement NUMERIC(12,4),
    favorable_movement NUMERIC(12,4),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_exit_signals_trade ON exit_signals(trade_id);
CREATE INDEX idx_exit_signals_symbol_created ON exit_signals(symbol, created_at DESC);
CREATE INDEX idx_exit_signals_created ON exit_signals(created_at DESC);

-- Append-only enforcement for exit_signals
CREATE OR REPLACE FUNCTION prevent_exit_signal_modification()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'UPDATE not allowed on exit_signals (append-only)';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'DELETE not allowed on exit_signals (append-only)';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_exit_signals_immutable
    BEFORE UPDATE OR DELETE ON exit_signals
    FOR EACH ROW EXECUTE FUNCTION prevent_exit_signal_modification();

-- Audit table for exit signals
CREATE TABLE IF NOT EXISTS exit_signals_audit (
    audit_id BIGSERIAL PRIMARY KEY,
    exit_signal_id TEXT NOT NULL,
    trade_id TEXT NOT NULL,
    symbol TEXT NOT NULL,
    direction TEXT NOT NULL,
    exit_reason TEXT NOT NULL,
    exit_price NUMERIC(12,2) NOT NULL,
    brick_movement NUMERIC(12,4),
    audited_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE OR REPLACE FUNCTION audit_exit_signal()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO exit_signals_audit (
        exit_signal_id, trade_id, symbol, direction,
        exit_reason, exit_price, brick_movement
    ) VALUES (
        NEW.exit_signal_id, NEW.trade_id, NEW.symbol, NEW.direction,
        NEW.exit_reason, NEW.exit_price, NEW.brick_movement
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_exit_signal
    AFTER INSERT ON exit_signals
    FOR EACH ROW EXECUTE FUNCTION audit_exit_signal();
