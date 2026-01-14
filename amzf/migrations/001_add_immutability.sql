-- Migration: Add Immutability (deleted_at, version columns) to all tables
-- This implements immutable audit trail pattern for regulatory compliance

-- =============================================================================
-- 1. Add columns to all tables
-- =============================================================================

-- Brokers table
ALTER TABLE brokers ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE DEFAULT NULL;
ALTER TABLE brokers ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 1;

-- Users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE DEFAULT NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 1;

-- User_brokers table
ALTER TABLE user_brokers ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE DEFAULT NULL;
ALTER TABLE user_brokers ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 1;

-- Portfolios table
ALTER TABLE portfolios ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE DEFAULT NULL;
ALTER TABLE portfolios ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 1;

-- Watchlist table
ALTER TABLE watchlist ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE DEFAULT NULL;
ALTER TABLE watchlist ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 1;

-- Candles table
ALTER TABLE candles ADD COLUMN IF NOT EXISTS id BIGSERIAL;
ALTER TABLE candles ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW();
ALTER TABLE candles ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE DEFAULT NULL;
ALTER TABLE candles ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 1;

-- Signals table
ALTER TABLE signals ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE DEFAULT NULL;
ALTER TABLE signals ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 1;

-- Trade_intents table
ALTER TABLE trade_intents ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE DEFAULT NULL;
ALTER TABLE trade_intents ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 1;

-- Trades table
ALTER TABLE trades ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE DEFAULT NULL;
ALTER TABLE trades ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 1;

-- =============================================================================
-- 2. Drop UPDATE triggers (replaced by immutability)
-- =============================================================================

DROP TRIGGER IF EXISTS trg_brokers_updated ON brokers;
DROP TRIGGER IF EXISTS trg_users_updated ON users;
DROP TRIGGER IF EXISTS trg_user_brokers_updated ON user_brokers;
DROP TRIGGER IF EXISTS trg_portfolios_updated ON portfolios;
DROP TRIGGER IF EXISTS trg_watchlist_updated ON watchlist;
DROP TRIGGER IF EXISTS trg_signals_updated ON signals;
DROP TRIGGER IF EXISTS trg_trade_intents_updated ON trade_intents;
DROP TRIGGER IF EXISTS trg_trades_updated ON trades;

-- =============================================================================
-- 3. Drop trade_events FK constraints first (they block PK changes)
-- =============================================================================

ALTER TABLE trade_events DROP CONSTRAINT IF EXISTS trade_events_user_id_fkey;
ALTER TABLE trade_events DROP CONSTRAINT IF EXISTS trade_events_broker_id_fkey;
ALTER TABLE trade_events DROP CONSTRAINT IF EXISTS trade_events_user_broker_id_fkey;

-- =============================================================================
-- 4. Drop all other foreign key constraints (will be recreated as deferrable)
-- =============================================================================

-- User_brokers FKs
ALTER TABLE user_brokers DROP CONSTRAINT IF EXISTS user_brokers_user_id_fkey;
ALTER TABLE user_brokers DROP CONSTRAINT IF EXISTS user_brokers_broker_id_fkey;
ALTER TABLE user_brokers DROP CONSTRAINT IF EXISTS fk_user_brokers_user;
ALTER TABLE user_brokers DROP CONSTRAINT IF EXISTS fk_user_brokers_broker;

-- Portfolios FKs
ALTER TABLE portfolios DROP CONSTRAINT IF EXISTS portfolios_user_id_fkey;
ALTER TABLE portfolios DROP CONSTRAINT IF EXISTS fk_portfolios_user;

-- Watchlist FKs
ALTER TABLE watchlist DROP CONSTRAINT IF EXISTS watchlist_user_broker_id_fkey;
ALTER TABLE watchlist DROP CONSTRAINT IF EXISTS fk_watchlist_user_broker;

-- Trade_intents FKs
ALTER TABLE trade_intents DROP CONSTRAINT IF EXISTS trade_intents_signal_id_fkey;
ALTER TABLE trade_intents DROP CONSTRAINT IF EXISTS trade_intents_user_id_fkey;
ALTER TABLE trade_intents DROP CONSTRAINT IF EXISTS trade_intents_broker_id_fkey;
ALTER TABLE trade_intents DROP CONSTRAINT IF EXISTS trade_intents_user_broker_id_fkey;
ALTER TABLE trade_intents DROP CONSTRAINT IF EXISTS fk_trade_intents_signal;
ALTER TABLE trade_intents DROP CONSTRAINT IF EXISTS fk_trade_intents_user;
ALTER TABLE trade_intents DROP CONSTRAINT IF EXISTS fk_trade_intents_broker;
ALTER TABLE trade_intents DROP CONSTRAINT IF EXISTS fk_trade_intents_user_broker;

-- Trades FKs
ALTER TABLE trades DROP CONSTRAINT IF EXISTS trades_user_id_fkey;
ALTER TABLE trades DROP CONSTRAINT IF EXISTS trades_broker_id_fkey;
ALTER TABLE trades DROP CONSTRAINT IF EXISTS trades_user_broker_id_fkey;
ALTER TABLE trades DROP CONSTRAINT IF EXISTS trades_signal_id_fkey;
ALTER TABLE trades DROP CONSTRAINT IF EXISTS trades_intent_id_fkey;
ALTER TABLE trades DROP CONSTRAINT IF EXISTS trades_portfolio_id_fkey;
ALTER TABLE trades DROP CONSTRAINT IF EXISTS fk_trades_user;
ALTER TABLE trades DROP CONSTRAINT IF EXISTS fk_trades_broker;
ALTER TABLE trades DROP CONSTRAINT IF EXISTS fk_trades_user_broker;
ALTER TABLE trades DROP CONSTRAINT IF EXISTS fk_trades_signal;
ALTER TABLE trades DROP CONSTRAINT IF EXISTS fk_trades_intent;
ALTER TABLE trades DROP CONSTRAINT IF EXISTS fk_trades_portfolio;

-- =============================================================================
-- 5. Change primary keys from (id) to (id, version)
-- =============================================================================

-- Brokers
ALTER TABLE brokers DROP CONSTRAINT brokers_pkey CASCADE;
ALTER TABLE brokers ADD PRIMARY KEY (broker_id, version);

-- Users
ALTER TABLE users DROP CONSTRAINT users_pkey CASCADE;
ALTER TABLE users ADD PRIMARY KEY (user_id, version);

-- User_brokers
ALTER TABLE user_brokers DROP CONSTRAINT user_brokers_pkey CASCADE;
ALTER TABLE user_brokers ADD PRIMARY KEY (user_broker_id, version);

-- Portfolios
ALTER TABLE portfolios DROP CONSTRAINT portfolios_pkey;
ALTER TABLE portfolios ADD PRIMARY KEY (portfolio_id, version);

-- Watchlist (uses id SERIAL, keep as is but add version to PK)
ALTER TABLE watchlist DROP CONSTRAINT watchlist_pkey;
ALTER TABLE watchlist ADD PRIMARY KEY (id, version);

-- Candles (symbol, timeframe, ts) composite key + version
ALTER TABLE candles DROP CONSTRAINT candles_pkey;
ALTER TABLE candles ADD PRIMARY KEY (symbol, timeframe, ts, version);

-- Signals
ALTER TABLE signals DROP CONSTRAINT signals_pkey;
ALTER TABLE signals ADD PRIMARY KEY (signal_id, version);

-- Trade_intents
ALTER TABLE trade_intents DROP CONSTRAINT trade_intents_pkey;
ALTER TABLE trade_intents ADD PRIMARY KEY (intent_id, version);

-- Trades
ALTER TABLE trades DROP CONSTRAINT trades_pkey;
ALTER TABLE trades ADD PRIMARY KEY (trade_id, version);

-- =============================================================================
-- 5.5. Drop old full unique constraints (conflict with versioning)
-- =============================================================================

-- These old unique constraints prevent multiple versions from existing
-- We replace them with partial unique indexes (WHERE deleted_at IS NULL)

-- Brokers: broker_code must be unique only among active records
ALTER TABLE brokers DROP CONSTRAINT IF EXISTS brokers_broker_code_key;

-- Users: email must be unique only among active records
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_email_key;

-- User_brokers: (user_id, broker_id) must be unique only among active records
ALTER TABLE user_brokers DROP CONSTRAINT IF EXISTS user_brokers_user_id_broker_id_key;

-- =============================================================================
-- 6. Create unique indexes on id columns (for FK references)
-- =============================================================================

-- These allow FKs to reference just the id without version
CREATE UNIQUE INDEX unique_brokers_id_active ON brokers(broker_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX unique_users_id_active ON users(user_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX unique_user_brokers_id_active ON user_brokers(user_broker_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX unique_portfolios_id_active ON portfolios(portfolio_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX unique_signals_id_active ON signals(signal_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX unique_trade_intents_id_active ON trade_intents(intent_id) WHERE deleted_at IS NULL;

-- =============================================================================
-- 7. Foreign key constraints - NOT RECREATED (architectural decision)
-- =============================================================================

-- ARCHITECTURAL NOTE:
-- Foreign key constraints are NOT recreated for versioned/immutable tables.
--
-- Reason: PostgreSQL requires a FULL unique constraint on referenced columns for FKs.
-- However, our immutable pattern allows multiple versions of the same ID to exist,
-- which is incompatible with a full unique constraint on just the id column.
--
-- We have partial unique indexes (WHERE deleted_at IS NULL) for business logic,
-- but PostgreSQL does not support using partial indexes as FK targets.
--
-- Referential integrity is enforced at the application layer through:
-- 1. Repository methods that check deleted_at IS NULL before inserting references
-- 2. Immutable insert pattern that preserves historical relationships
-- 3. Partial unique indexes preventing duplicate active records
--
-- Trade-off: Database-level RI is sacrificed for audit trail compliance.
-- This is a standard pattern in regulatory-compliant systems requiring full history.

-- SKIPPED: User_brokers -> Users FK
-- SKIPPED: User_brokers -> Brokers FK
-- SKIPPED: Portfolios -> Users FK
-- SKIPPED: Watchlist -> User_brokers FK
-- SKIPPED: Trade_intents -> Signals FK
-- SKIPPED: Trade_intents -> Users FK
-- SKIPPED: Trade_intents -> Brokers FK
-- SKIPPED: Trade_intents -> User_brokers FK
-- SKIPPED: Trades -> Users FK
-- SKIPPED: Trades -> Brokers FK
-- SKIPPED: Trades -> User_brokers FK
-- SKIPPED: Trades -> Signals FK
-- SKIPPED: Trades -> Trade_intents FK
-- SKIPPED: Trades -> Portfolios FK
-- SKIPPED: Trade_events -> Users FK
-- SKIPPED: Trade_events -> Brokers FK
-- SKIPPED: Trade_events -> User_brokers FK

-- =============================================================================
-- 8. Create indexes for active records and business logic
-- =============================================================================

-- Brokers
CREATE UNIQUE INDEX IF NOT EXISTS unique_brokers_code_active ON brokers(broker_code) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_brokers_version ON brokers(broker_id, version);

-- Users
CREATE UNIQUE INDEX IF NOT EXISTS unique_users_email_active ON users(email) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_users_version ON users(user_id, version);

-- User_brokers
CREATE INDEX IF NOT EXISTS idx_user_brokers_user_active ON user_brokers(user_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_user_brokers_broker_active ON user_brokers(broker_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_user_brokers_version ON user_brokers(user_broker_id, version);

-- Portfolios
CREATE INDEX IF NOT EXISTS idx_portfolios_user_active ON portfolios(user_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_portfolios_version ON portfolios(portfolio_id, version);

-- Watchlist
CREATE INDEX IF NOT EXISTS idx_watchlist_user_broker_active ON watchlist(user_broker_id) WHERE deleted_at IS NULL;

-- Candles
CREATE INDEX IF NOT EXISTS idx_candles_symbol_tf_active ON candles(symbol, timeframe) WHERE deleted_at IS NULL;

-- Signals
CREATE INDEX IF NOT EXISTS idx_signals_symbol_active ON signals(symbol) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_signals_status_active ON signals(status) WHERE deleted_at IS NULL;

-- Trade_intents
CREATE INDEX IF NOT EXISTS idx_trade_intents_signal_active ON trade_intents(signal_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_trade_intents_user_active ON trade_intents(user_id) WHERE deleted_at IS NULL;

-- Trades
CREATE INDEX IF NOT EXISTS idx_trades_user_active ON trades(user_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_trades_signal_active ON trades(signal_id) WHERE deleted_at IS NULL;

-- =============================================================================
-- Migration complete!
-- All tables now support immutable audit trail with deleted_at and version.
-- =============================================================================
