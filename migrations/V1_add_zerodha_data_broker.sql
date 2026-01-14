-- ============================================================================
-- Migration: Add Zerodha as DATA broker for dual-broker mode
-- Purpose: Configure Zerodha to provide market data while keeping FYERS for execution
-- ============================================================================

-- Step 1: Update existing FYERS broker to EXEC role only
-- This ensures FYERS is only used for order execution, not market data
UPDATE user_brokers
SET role = 'EXEC'
WHERE broker_id = (SELECT broker_id FROM brokers WHERE broker_code = 'FYERS')
  AND role = 'DATA';

-- Step 2: Insert Zerodha DATA broker configuration
-- Replace 'admin-user-id' with your actual user_id from users table
-- Replace 'YOUR_ZERODHA_API_KEY' and 'YOUR_ZERODHA_API_SECRET' with your actual credentials

INSERT INTO user_brokers (
    user_broker_id,
    user_id,
    broker_id,
    role,
    enabled,
    status,
    credentials,
    created_at,
    updated_at
)
SELECT
    'zerodha-data-001',
    u.user_id,
    b.broker_id,
    'DATA',
    true,
    'ACTIVE',
    jsonb_build_object(
        'apiKey', 'YOUR_ZERODHA_API_KEY',
        'apiSecret', 'YOUR_ZERODHA_API_SECRET'
    ),
    NOW(),
    NOW()
FROM
    users u,
    brokers b
WHERE
    u.username = 'admin'  -- Replace with your username
    AND b.broker_code = 'ZERODHA'
LIMIT 1
ON CONFLICT (user_broker_id) DO UPDATE SET
    role = 'DATA',
    enabled = true,
    status = 'ACTIVE',
    credentials = EXCLUDED.credentials,
    updated_at = NOW();

-- Step 3: Verify configuration
-- This should show:
-- - One Zerodha broker with role='DATA'
-- - One or more FYERS brokers with role='EXEC'

SELECT
    ub.user_broker_id,
    b.broker_name,
    b.broker_code,
    ub.role,
    ub.enabled,
    ub.status,
    ub.credentials->>'apiKey' as api_key_masked,
    ub.created_at
FROM
    user_brokers ub
    JOIN brokers b ON ub.broker_id = b.broker_id
WHERE
    ub.enabled = true
ORDER BY
    ub.role DESC, b.broker_code;

-- ============================================================================
-- Post-Migration Steps (Manual)
-- ============================================================================

-- 1. Generate Zerodha access token via OAuth flow:
--    - Visit: https://kite.trade/connect/login?api_key=YOUR_API_KEY
--    - Authorize the app
--    - Exchange request_token for access_token
--    - Insert into user_broker_sessions table

-- Example INSERT for user_broker_sessions (after OAuth):
/*
INSERT INTO user_broker_sessions (
    session_id,
    user_broker_id,
    access_token,
    refresh_token,
    token_valid_till,
    session_status,
    created_at,
    updated_at
)
VALUES (
    'zerodha-session-' || EXTRACT(EPOCH FROM NOW())::TEXT,
    'zerodha-data-001',
    'YOUR_ACCESS_TOKEN_FROM_OAUTH',
    NULL,  -- Zerodha doesn't provide refresh tokens
    NOW() + INTERVAL '1 day',  -- Zerodha tokens expire daily
    'ACTIVE',
    NOW(),
    NOW()
);
*/

-- 2. Restart the application to load the new Zerodha DATA adapter

-- 3. Verify dual-broker mode is working:
--    - Check /api/health endpoint
--    - Should show:
--      - dataFeed: { broker: 'ZERODHA', wsConnected: true }
--      - userBroker: { broker: 'FYERS', connected: true }

-- ============================================================================
-- Rollback Script (if needed)
-- ============================================================================

/*
-- Disable Zerodha DATA broker
UPDATE user_brokers
SET enabled = false, status = 'INACTIVE'
WHERE user_broker_id = 'zerodha-data-001';

-- Restore FYERS to DATA role
UPDATE user_brokers
SET role = 'DATA'
WHERE broker_id = (SELECT broker_id FROM brokers WHERE broker_code = 'FYERS')
  AND role = 'EXEC'
LIMIT 1;

-- Delete Zerodha session
DELETE FROM user_broker_sessions
WHERE user_broker_id = 'zerodha-data-001';
*/

-- ============================================================================
-- Notes
-- ============================================================================

-- Compliance Warning:
-- Zerodha's terms of service discourage using their data feed to trade on other brokers.
-- While many firms do this without issue, it's officially a grey area.
-- Be aware of potential policy changes.

-- Token Expiry:
-- - Zerodha access tokens expire every day at 6 AM IST
-- - You'll need to implement daily token refresh via OAuth
-- - The TokenRefreshWatchdog will detect token expiry and trigger reconnection

-- Rate Limits:
-- - Zerodha WebSocket can handle thousands of instruments
-- - Keep the number of subscriptions reasonable (<500 symbols recommended)

-- Data Quality:
-- - Zerodha provides high-quality, low-latency market data
-- - More reliable than FYERS for tick streaming
-- - Binary packet format is more efficient than JSON
