-- Migration: Create oauth_states table for OAuth callback state management
-- Purpose: Track OAuth state parameters to prevent CSRF and handle server restarts
-- Author: System
-- Date: 2026-01-14

-- Create oauth_states table
CREATE TABLE oauth_states (
  state VARCHAR(100) NOT NULL PRIMARY KEY,
  user_broker_id VARCHAR(50) NOT NULL,
  broker_id VARCHAR(50) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  expires_at TIMESTAMPTZ NOT NULL,
  used_at TIMESTAMPTZ,

  -- Audit trail
  deleted_at TIMESTAMPTZ
);

-- Index for cleanup of expired states
CREATE INDEX idx_oauth_states_expiry
ON oauth_states(expires_at)
WHERE deleted_at IS NULL AND used_at IS NULL;

-- Index for user_broker lookup
CREATE INDEX idx_oauth_states_user_broker
ON oauth_states(user_broker_id)
WHERE deleted_at IS NULL;

-- Comments
COMMENT ON TABLE oauth_states IS 'OAuth state parameters for CSRF protection and callback validation';
COMMENT ON COLUMN oauth_states.state IS 'Random state parameter sent to OAuth provider';
COMMENT ON COLUMN oauth_states.user_broker_id IS 'User-broker combination this state belongs to';
COMMENT ON COLUMN oauth_states.broker_id IS 'Broker code (FYERS, ZERODHA, etc.)';
COMMENT ON COLUMN oauth_states.created_at IS 'When state was generated';
COMMENT ON COLUMN oauth_states.expires_at IS 'When state expires (typically 10-15 minutes)';
COMMENT ON COLUMN oauth_states.used_at IS 'When callback consumed this state (idempotency check)';
COMMENT ON COLUMN oauth_states.deleted_at IS 'Soft delete timestamp';

-- Migration notes:
-- 1. State is a random UUID to prevent CSRF attacks
-- 2. expires_at prevents replay attacks (states expire after 15 minutes)
-- 3. used_at ensures callback is idempotent (duplicate POSTs don't re-exchange token)
-- 4. Survives server restarts (unlike in-memory storage)
-- 5. Example flow:
--    - Server generates state=abc123, stores in DB
--    - Opens browser to FYERS with state=abc123
--    - FYERS redirects to callback?auth_code=xyz&state=abc123
--    - Backend validates state exists, not expired, not used
--    - Backend marks used_at=NOW(), exchanges token
--    - Subsequent refresh returns "already done"
