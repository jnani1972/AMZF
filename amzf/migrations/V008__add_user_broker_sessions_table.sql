-- Migration: Create user_broker_sessions table
-- Purpose: Track session tokens and their validity for each user-broker combination
-- Author: System
-- Date: 2026-01-09

-- Create user_broker_sessions table
CREATE TABLE user_broker_sessions (
  session_id VARCHAR(50) NOT NULL,
  user_broker_id VARCHAR(50) NOT NULL,
  access_token TEXT NOT NULL,
  token_valid_till TIMESTAMPTZ,
  session_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  session_started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  session_ended_at TIMESTAMPTZ,

  -- Immutable audit trail
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted_at TIMESTAMPTZ,
  version INTEGER NOT NULL DEFAULT 1,

  PRIMARY KEY (session_id, version)
  -- Note: No FK constraint due to user_brokers having composite PK (user_broker_id, version)
  -- Application logic ensures user_broker_id references active user_brokers record
);

-- Index for finding active session for a user-broker
CREATE INDEX idx_user_broker_sessions_active
ON user_broker_sessions(user_broker_id, session_status)
WHERE deleted_at IS NULL;

-- Index for finding sessions expiring soon (for auto-refresh)
CREATE INDEX idx_user_broker_sessions_expiry
ON user_broker_sessions(token_valid_till)
WHERE deleted_at IS NULL AND session_status = 'ACTIVE';

-- Index for session lookup
CREATE INDEX idx_user_broker_sessions_lookup
ON user_broker_sessions(session_id)
WHERE deleted_at IS NULL;

-- Comments
COMMENT ON TABLE user_broker_sessions IS 'Session tokens for user-broker combinations (immutable audit trail)';
COMMENT ON COLUMN user_broker_sessions.session_id IS 'Unique session identifier';
COMMENT ON COLUMN user_broker_sessions.user_broker_id IS 'References user_brokers (user-broker combination)';
COMMENT ON COLUMN user_broker_sessions.access_token IS 'Session-specific broker access token (temporary, expires)';
COMMENT ON COLUMN user_broker_sessions.token_valid_till IS 'Timestamp when access_token expires (NULL = no expiry or unknown)';
COMMENT ON COLUMN user_broker_sessions.session_status IS 'ACTIVE, EXPIRED, REVOKED';
COMMENT ON COLUMN user_broker_sessions.session_started_at IS 'When session was created';
COMMENT ON COLUMN user_broker_sessions.session_ended_at IS 'When session was explicitly ended';
COMMENT ON COLUMN user_broker_sessions.created_at IS 'Immutable timestamp when this version was created';
COMMENT ON COLUMN user_broker_sessions.deleted_at IS 'Soft delete timestamp for immutable audit trail';
COMMENT ON COLUMN user_broker_sessions.version IS 'Version number for immutable audit trail';

-- Migration notes:
-- 1. Each user-broker combination (user_brokers.user_broker_id) can have multiple sessions
-- 2. Sessions are temporary - access tokens expire and get refreshed
-- 3. Immutable audit trail: Token refresh creates new version with soft delete
-- 4. session_status tracks lifecycle: ACTIVE → EXPIRED/REVOKED
-- 5. Example: User A + Fyers DATA (UB001) → Sessions: S001, S002 (after refresh)
