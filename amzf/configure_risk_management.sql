-- ========================================
-- RISK MANAGEMENT CONFIGURATION
-- ========================================
-- CRITICAL: Set these BEFORE enabling live trading
-- Current: ALL ZERO (DANGEROUS!)

-- Check current settings
SELECT user_broker_id, broker_id,
       capital_allocated, max_exposure, max_per_trade,
       max_open_trades, max_daily_loss, max_weekly_loss
FROM user_brokers
WHERE deleted_at IS NULL
ORDER BY user_broker_id;

-- ========================================
-- RECOMMENDED SETTINGS (CONSERVATIVE)
-- ========================================
-- Adjust these values based on your risk tolerance

UPDATE user_brokers
SET
    -- Total capital allocated to this broker account
    capital_allocated = 100000,      -- ₹1,00,000

    -- Maximum exposure (sum of all open positions)
    max_exposure = 50000,            -- ₹50,000 (50% of capital)

    -- Maximum per trade position size
    max_per_trade = 10000,           -- ₹10,000 (10% of capital)

    -- Maximum number of concurrent open positions
    max_open_trades = 5,             -- 5 positions max

    -- Daily loss limit (stop trading for the day)
    max_daily_loss = 5000,           -- ₹5,000 (5% of capital)

    -- Weekly loss limit (stop trading for the week)
    max_weekly_loss = 10000,         -- ₹10,000 (10% of capital)

    -- Updated timestamp
    updated_at = CURRENT_TIMESTAMP

WHERE user_broker_id = 'UB_DATA_E7DE4B'
  AND deleted_at IS NULL;

-- ========================================
-- VERIFY CHANGES
-- ========================================
SELECT user_broker_id,
       capital_allocated,
       max_exposure,
       max_per_trade,
       max_open_trades,
       max_daily_loss,
       max_weekly_loss,
       ROUND((max_exposure::numeric / capital_allocated::numeric) * 100, 1) as exposure_pct,
       ROUND((max_per_trade::numeric / capital_allocated::numeric) * 100, 1) as per_trade_pct,
       ROUND((max_daily_loss::numeric / capital_allocated::numeric) * 100, 1) as daily_loss_pct
FROM user_brokers
WHERE user_broker_id = 'UB_DATA_E7DE4B'
  AND deleted_at IS NULL;

-- ========================================
-- ADDITIONAL SAFETY SETTINGS (OPTIONAL)
-- ========================================
-- Add these columns if they don't exist:

-- Cooldown after hitting daily loss limit
-- ALTER TABLE user_brokers ADD COLUMN IF NOT EXISTS cooldown_after_loss_mins INTEGER DEFAULT 60;

-- Maximum orders per minute (prevent order storms)
-- ALTER TABLE user_brokers ADD COLUMN IF NOT EXISTS max_orders_per_minute INTEGER DEFAULT 10;

-- Maximum consecutive losses before requiring manual review
-- ALTER TABLE user_brokers ADD COLUMN IF NOT EXISTS max_consecutive_losses INTEGER DEFAULT 3;

-- ========================================
-- NOTES
-- ========================================
/*
These are CONSERVATIVE settings for a ₹1,00,000 account:

1. Capital Allocated: ₹1,00,000
   - Total capital you're willing to risk in this broker account

2. Max Exposure: ₹50,000 (50%)
   - You can have max ₹50,000 worth of open positions at any time
   - Protects against over-leverage

3. Max Per Trade: ₹10,000 (10%)
   - Each individual trade limited to ₹10,000
   - Diversifies risk across multiple positions

4. Max Open Trades: 5
   - Max 5 concurrent positions
   - Prevents over-diversification

5. Daily Loss Limit: ₹5,000 (5%)
   - If you lose ₹5,000 in a day, system stops trading
   - Prevents revenge trading

6. Weekly Loss Limit: ₹10,000 (10%)
   - If you lose ₹10,000 in a week, system stops for the week
   - Prevents drawdown spirals

ADJUST THESE BASED ON:
- Your risk tolerance
- Account size
- Trading strategy
- Market conditions
*/

-- ========================================
-- FOR PRODUCTION
-- ========================================
/*
1. Start CONSERVATIVE (use above values)
2. Monitor for 1 week
3. Adjust based on:
   - Win rate
   - Average trade size
   - Drawdown patterns
4. NEVER remove all limits
5. Review monthly
*/
