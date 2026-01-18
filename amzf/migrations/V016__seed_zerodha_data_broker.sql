-- V016__seed_zerodha_data_broker.sql
-- Seeds 'Zerodha' as the default DATA broker for the admin user.

BEGIN;

-- 1. Insert Zerodha Broker Definition
INSERT INTO brokers (
    broker_id, 
    broker_code, 
    broker_name, 
    adapter_class, 
    status, 
    config
) VALUES (
    'B_ZERODHA', 
    'ZERODHA', 
    'Zerodha Kite', 
    'in.annupaper.infrastructure.broker.adapters.ZerodhaAdapter', 
    'ACTIVE',
    '{"apiUrl": "https://api.kite.trade", "loginUrl": "https://kite.trade/connect/login"}'::jsonb
) ON CONFLICT (broker_id) DO NOTHING;

-- 2. Link Admin User to Zerodha as DATA broker
-- Uses subquery to find admin user by email (created by App.ensureAdminExists)
INSERT INTO user_brokers (
    user_broker_id,
    user_id,
    broker_id,
    role,
    status,
    enabled,
    capital_allocated,
    max_exposure
)
SELECT 
    'UB_ADMIN_ZERODHA',                  -- user_broker_id
    u.user_id,                           -- user_id (from users table)
    'B_ZERODHA',                         -- broker_id
    'DATA',                              -- role (DATA broker)
    'ACTIVE',                            -- status
    true,                                -- enabled
    100000.00,                           -- capital_allocated
    100000.00                            -- max_exposure
FROM users u
WHERE u.email = 'admin@annupaper.com'
ON CONFLICT (user_id, broker_id) DO UPDATE 
SET role = 'DATA', status = 'ACTIVE';    -- Ensure role is DATA if exists

COMMIT;
