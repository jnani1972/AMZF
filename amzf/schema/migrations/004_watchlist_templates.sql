-- Migration: Watchlist Template System (4-level hierarchy)
-- Level 1: Templates (predefined symbol lists)
-- Level 2: Admin selected watchlists
-- Level 3: System default (auto-merged)
-- Level 4: User-broker watchlists (existing table)

-- =====================================================
-- Level 1: Watchlist Templates
-- =====================================================
CREATE TABLE IF NOT EXISTS watchlist_templates (
    template_id TEXT PRIMARY KEY,
    template_name TEXT NOT NULL UNIQUE,
    description TEXT,
    display_order INTEGER NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP WITH TIME ZONE,
    version INTEGER NOT NULL DEFAULT 1
);

CREATE INDEX idx_watchlist_templates_active ON watchlist_templates(template_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_watchlist_templates_enabled ON watchlist_templates(enabled) WHERE enabled = true;

-- =====================================================
-- Template Symbols
-- =====================================================
CREATE TABLE IF NOT EXISTS watchlist_template_symbols (
    id BIGSERIAL PRIMARY KEY,
    template_id TEXT NOT NULL REFERENCES watchlist_templates(template_id),
    symbol TEXT NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(template_id, symbol)
);

CREATE INDEX idx_template_symbols_template ON watchlist_template_symbols(template_id);
CREATE INDEX idx_template_symbols_symbol ON watchlist_template_symbols(symbol);

-- =====================================================
-- Level 2: Admin Selected Watchlists
-- =====================================================
CREATE TABLE IF NOT EXISTS watchlist_selected (
    selected_id TEXT PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    source_template_id TEXT REFERENCES watchlist_templates(template_id),
    description TEXT,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP WITH TIME ZONE,
    version INTEGER NOT NULL DEFAULT 1
);

CREATE INDEX idx_watchlist_selected_active ON watchlist_selected(selected_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_watchlist_selected_enabled ON watchlist_selected(enabled) WHERE enabled = true;

-- =====================================================
-- Selected Watchlist Symbols
-- =====================================================
CREATE TABLE IF NOT EXISTS watchlist_selected_symbols (
    id BIGSERIAL PRIMARY KEY,
    selected_id TEXT NOT NULL REFERENCES watchlist_selected(selected_id),
    symbol TEXT NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(selected_id, symbol)
);

CREATE INDEX idx_selected_symbols_selected ON watchlist_selected_symbols(selected_id);
CREATE INDEX idx_selected_symbols_symbol ON watchlist_selected_symbols(symbol);

-- =====================================================
-- Level 4: Modify existing watchlist table
-- =====================================================
ALTER TABLE watchlist ADD COLUMN IF NOT EXISTS is_custom BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE watchlist ADD COLUMN IF NOT EXISTS last_synced_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_watchlist_custom ON watchlist(is_custom);

-- =====================================================
-- Seed Data: Watchlist Templates
-- =====================================================
INSERT INTO watchlist_templates (template_id, template_name, description, display_order) VALUES
('TPL_ALL', 'All', 'All available symbols', 1),
('TPL_NIFTY50', 'NIFTY50', 'Nifty 50 Index constituents', 2),
('TPL_BANKING', 'Banking', 'Banking sector stocks', 3),
('TPL_NIFTY100', 'Nifty100', 'Nifty 100 Index constituents', 4)
ON CONFLICT (template_id) DO NOTHING;

-- Seed NIFTY50 symbols (sample - top 10)
INSERT INTO watchlist_template_symbols (template_id, symbol, display_order) VALUES
('TPL_NIFTY50', 'NSE:RELIANCE-EQ', 1),
('TPL_NIFTY50', 'NSE:TCS-EQ', 2),
('TPL_NIFTY50', 'NSE:HDFCBANK-EQ', 3),
('TPL_NIFTY50', 'NSE:INFY-EQ', 4),
('TPL_NIFTY50', 'NSE:ICICIBANK-EQ', 5),
('TPL_NIFTY50', 'NSE:HINDUNILVR-EQ', 6),
('TPL_NIFTY50', 'NSE:SBIN-EQ', 7),
('TPL_NIFTY50', 'NSE:BHARTIARTL-EQ', 8),
('TPL_NIFTY50', 'NSE:ITC-EQ', 9),
('TPL_NIFTY50', 'NSE:KOTAKBANK-EQ', 10)
ON CONFLICT (template_id, symbol) DO NOTHING;

-- Seed Banking symbols (sample)
INSERT INTO watchlist_template_symbols (template_id, symbol, display_order) VALUES
('TPL_BANKING', 'NSE:HDFCBANK-EQ', 1),
('TPL_BANKING', 'NSE:ICICIBANK-EQ', 2),
('TPL_BANKING', 'NSE:SBIN-EQ', 3),
('TPL_BANKING', 'NSE:KOTAKBANK-EQ', 4),
('TPL_BANKING', 'NSE:AXISBANK-EQ', 5),
('TPL_BANKING', 'NSE:INDUSINDBK-EQ', 6),
('TPL_BANKING', 'NSE:BANKBARODA-EQ', 7),
('TPL_BANKING', 'NSE:PNB-EQ', 8)
ON CONFLICT (template_id, symbol) DO NOTHING;

COMMIT;
