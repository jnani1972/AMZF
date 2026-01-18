-- Migration: Add session_expiry_at to user_brokers table
-- Purpose: Enable monitoring of broker session expiry directly from user_brokers
-- Date: 2026-01-18

-- Add session_expiry_at column to user_brokers table
ALTER TABLE user_brokers 
ADD COLUMN IF NOT EXISTS session_expiry_at TIMESTAMP WITH TIME ZONE;

-- Create index for monitoring queries
CREATE INDEX IF NOT EXISTS idx_user_brokers_session_expiry 
ON user_brokers(session_expiry_at) 
WHERE deleted_at IS NULL AND enabled = true;

-- Populate with data from user_broker_sessions (latest active session per user_broker)
UPDATE user_brokers ub
SET session_expiry_at = (
    SELECT ubs.token_valid_till
    FROM user_broker_sessions ubs
    WHERE ubs.user_broker_id = ub.user_broker_id
      AND ubs.deleted_at IS NULL
      AND ubs.session_status = 'ACTIVE'
    ORDER BY ubs.token_valid_till DESC
    LIMIT 1
)
WHERE ub.deleted_at IS NULL;

-- Verification query
SELECT 
    COUNT(*) as total_brokers,
    COUNT(session_expiry_at) as brokers_with_expiry,
    COUNT(*) FILTER (WHERE session_expiry_at < NOW()) as expired_sessions
FROM user_brokers
WHERE deleted_at IS NULL;
