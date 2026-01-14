-- Migration: Add session tracking columns to user_brokers table
-- Purpose: Track broker session tokens and their validity separately from permanent credentials
-- Author: System
-- Date: 2026-01-09

-- Add session tracking columns
ALTER TABLE user_brokers
ADD COLUMN IF NOT EXISTS access_token TEXT,
ADD COLUMN IF NOT EXISTS token_valid_till TIMESTAMPTZ;

-- Create index for token expiry queries
CREATE INDEX IF NOT EXISTS idx_user_brokers_token_expiry
ON user_brokers(user_broker_id, token_valid_till)
WHERE deleted_at IS NULL AND access_token IS NOT NULL;

-- Add comment explaining the schema
COMMENT ON COLUMN user_brokers.access_token IS 'Session-specific broker access token (temporary, expires)';
COMMENT ON COLUMN user_brokers.token_valid_till IS 'Timestamp when access_token expires (NULL = no expiry or unknown)';

-- Migration notes:
-- 1. access_token: Stores temporary session token (e.g., Fyers OAuth token)
-- 2. token_valid_till: Tracks when token expires (for auto-refresh logic)
-- 3. credentials (JSONB): Still stores permanent credentials (apiKey, apiSecret)
-- 4. Immutable audit trail: Each token refresh creates new version with soft delete
