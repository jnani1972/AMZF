-- ═══════════════════════════════════════════════════════════════════════════
-- Migration: Add last tick price and timestamp to watchlist
-- Purpose: Store latest market price for each symbol to show LTP in Market Watch
-- ═══════════════════════════════════════════════════════════════════════════

-- Add last_price column to store the last traded price
ALTER TABLE watchlist
ADD COLUMN IF NOT EXISTS last_price DECIMAL(18,4);

-- Add last_tick_time column to store when the last tick was received
ALTER TABLE watchlist
ADD COLUMN IF NOT EXISTS last_tick_time TIMESTAMPTZ;

-- Create index for efficient queries on last_tick_time
CREATE INDEX IF NOT EXISTS idx_watchlist_last_tick ON watchlist(last_tick_time DESC)
WHERE last_tick_time IS NOT NULL;

-- Add comment explaining the columns
COMMENT ON COLUMN watchlist.last_price IS 'Last traded price (LTP) from most recent tick';
COMMENT ON COLUMN watchlist.last_tick_time IS 'Timestamp when last tick was received';
