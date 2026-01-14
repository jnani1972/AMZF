# User Broker Session Management

## Overview

AnnuPaper v04 now has a dedicated `user_broker_sessions` table for managing temporary session tokens separately from permanent broker credentials.

## Why Separate Sessions Table?

### Problem with Storing Access Tokens in `user_brokers.credentials`:
1. **Access tokens are temporary** - They expire (e.g., Fyers = 24 hours)
2. **Access tokens need refresh** - Creating new versions of user_brokers just for token refresh is wasteful
3. **Multiple sessions possible** - Same user-broker can have concurrent sessions
4. **Session lifecycle** - Need to track session status (ACTIVE, EXPIRED, REVOKED)
5. **Different lifecycles** - Permanent credentials (apiKey, apiSecret) vs temporary tokens

### Solution: `user_broker_sessions` Table
Separate table that references `user_brokers` and manages session lifecycle independently.

## Architecture

### Database Schema

```sql
user_brokers (permanent credentials)
├── user_broker_id: "UB_DATA_E7DE4B"
├── credentials: {"apiKey": "xxx", "apiSecret": "yyy"}
└── ... (risk limits, settings, etc.)

user_broker_sessions (temporary sessions)
├── session_id: "SESSION_001"
├── user_broker_id: "UB_DATA_E7DE4B" (references user_brokers)
├── access_token: "eyJhbGc..."
├── token_valid_till: "2026-01-10 15:00:00"
├── session_status: "ACTIVE" | "EXPIRED" | "REVOKED"
└── ... (audit trail: created_at, deleted_at, version)
```

### Key Relationships

```
User A + Fyers DATA → user_broker_id: UB001
                    └─> Sessions:
                        ├── SESSION_001 (active, expires 2026-01-10)
                        ├── SESSION_002 (expired)
                        └── SESSION_003 (revoked)

User A + Fyers EXEC → user_broker_id: UB002
                    └─> Sessions:
                        └── SESSION_004 (active)

User B + Fyers DATA → user_broker_id: UB003
                    └─> Sessions:
                        └── SESSION_005 (active)
```

## Schema Details

### user_broker_sessions Table

| Column | Type | Description |
|--------|------|-------------|
| session_id | VARCHAR(50) | Unique session identifier |
| user_broker_id | VARCHAR(50) | References user_brokers (user-broker combination) |
| access_token | TEXT | Temporary OAuth/API token |
| token_valid_till | TIMESTAMPTZ | When token expires (NULL = unknown) |
| session_status | VARCHAR(20) | ACTIVE, EXPIRED, REVOKED |
| session_started_at | TIMESTAMPTZ | When session was created |
| session_ended_at | TIMESTAMPTZ | When session was explicitly ended |
| created_at | TIMESTAMPTZ | Audit trail: when this version was created |
| deleted_at | TIMESTAMPTZ | Soft delete (immutable audit trail) |
| version | INTEGER | Version number for immutable updates |

**Primary Key:** `(session_id, version)`

### Indexes

1. **idx_user_broker_sessions_active**: Fast lookup of active session for a user-broker
   - `(user_broker_id, session_status) WHERE deleted_at IS NULL`

2. **idx_user_broker_sessions_expiry**: Find sessions expiring soon (for auto-refresh)
   - `(token_valid_till) WHERE deleted_at IS NULL AND session_status = 'ACTIVE'`

3. **idx_user_broker_sessions_lookup**: Fast session lookup
   - `(session_id) WHERE deleted_at IS NULL`

## Usage Patterns

### 1. Create New Session (Login)

```java
// User authenticates with Fyers
String accessToken = fyersApi.generateAccessToken(authCode, appId, secretKey);
Instant validTill = Instant.now().plus(24, ChronoUnit.HOURS); // Fyers = 24h

UserBrokerSession session = UserBrokerSession.create(
    "SESSION_" + UUID.randomUUID(),
    "UB_DATA_E7DE4B",
    accessToken,
    validTill
);

sessionRepo.insert(session);
```

**Database:**
```sql
INSERT INTO user_broker_sessions (
  session_id, user_broker_id, access_token, token_valid_till,
  session_status, session_started_at, created_at, version
) VALUES (
  'SESSION_001', 'UB_DATA_E7DE4B', 'eyJhbGc...', '2026-01-10 15:00:00',
  'ACTIVE', NOW(), NOW(), 1
);
```

### 2. Find Active Session

```java
Optional<UserBrokerSession> session = sessionRepo.findActiveSession("UB_DATA_E7DE4B");

if (session.isPresent() && session.get().isActive()) {
    String accessToken = session.get().accessToken();
    // Use token for API calls
} else {
    // Need to refresh or create new session
}
```

**Query:**
```sql
SELECT * FROM user_broker_sessions
WHERE user_broker_id = 'UB_DATA_E7DE4B'
  AND deleted_at IS NULL
  AND session_status = 'ACTIVE'
ORDER BY created_at DESC
LIMIT 1;
```

### 3. Refresh Token (Immutable Update)

```java
UserBrokerSession currentSession = sessionRepo.findActiveSession("UB_DATA_E7DE4B").get();

// Get new token from broker
String newAccessToken = fyersApi.refreshToken(currentSession.accessToken());
Instant newValidTill = Instant.now().plus(24, ChronoUnit.HOURS);

// Create new version with refreshed token
UserBrokerSession refreshed = currentSession.withRefreshedToken(newAccessToken, newValidTill);
sessionRepo.update(refreshed);
```

**Database (Immutable):**
```sql
-- Step 1: Soft delete current version
UPDATE user_broker_sessions
SET deleted_at = NOW()
WHERE session_id = 'SESSION_001' AND version = 1 AND deleted_at IS NULL;

-- Step 2: Insert new version
INSERT INTO user_broker_sessions (
  session_id, user_broker_id, access_token, token_valid_till,
  session_status, session_started_at, created_at, version
) VALUES (
  'SESSION_001', 'UB_DATA_E7DE4B', 'eyJNEW...', '2026-01-11 15:00:00',
  'ACTIVE', '2026-01-10 14:00:00', NOW(), 2
);
```

### 4. Expire Session

```java
UserBrokerSession session = sessionRepo.findActiveSession("UB_DATA_E7DE4B").get();

UserBrokerSession expired = session.withStatus(SessionStatus.EXPIRED);
sessionRepo.update(expired);
```

### 5. Revoke Session (Logout)

```java
UserBrokerSession session = sessionRepo.findActiveSession("UB_DATA_E7DE4B").get();

UserBrokerSession revoked = session.withStatus(SessionStatus.REVOKED);
sessionRepo.update(revoked);
```

### 6. Auto-Refresh Expiring Sessions

```java
// Find sessions expiring in next 1 hour
Instant oneHourFromNow = Instant.now().plus(1, ChronoUnit.HOURS);
List<UserBrokerSession> expiring = sessionRepo.findExpiringSessions(oneHourFromNow);

for (UserBrokerSession session : expiring) {
    // Refresh each session
    String newToken = fyersApi.refreshToken(session.accessToken());
    Instant newValidTill = Instant.now().plus(24, ChronoUnit.HOURS);

    UserBrokerSession refreshed = session.withRefreshedToken(newToken, newValidTill);
    sessionRepo.update(refreshed);

    log.info("Auto-refreshed session {} for {}", session.sessionId(), session.userBrokerId());
}
```

## Integration with FyersAdapter

### Before (Accessing Token from Credentials)

```java
// ❌ Old approach - access token mixed with permanent credentials
public CompletableFuture<ConnectionResult> connect(BrokerCredentials credentials) {
    this.accessToken = credentials.accessToken();  // From user_brokers.credentials
    // ...
}
```

### After (Accessing Token from Session)

```java
// ✅ New approach - access token from separate session
public CompletableFuture<ConnectionResult> connect(BrokerCredentials credentials, UserBrokerSession session) {
    this.appId = credentials.apiKey();      // Permanent (from user_brokers.credentials)
    this.secretId = credentials.apiSecret(); // Permanent (from user_brokers.credentials)
    this.accessToken = session.accessToken(); // Temporary (from user_broker_sessions)
    // ...
}
```

## Immutable Audit Trail

Every session update creates a new version:

```sql
-- Session lifecycle audit trail
session_id   | version | status  | token_valid_till      | created_at           | deleted_at
-------------|---------|---------|----------------------|----------------------|----------------------
SESSION_001  | 1       | ACTIVE  | 2026-01-10 15:00:00  | 2026-01-10 14:00:00  | 2026-01-10 14:30:00
SESSION_001  | 2       | ACTIVE  | 2026-01-11 15:00:00  | 2026-01-10 14:30:00  | 2026-01-11 14:00:00
SESSION_001  | 3       | ACTIVE  | 2026-01-12 15:00:00  | 2026-01-11 14:00:00  | 2026-01-12 09:00:00
SESSION_001  | 4       | REVOKED | 2026-01-12 15:00:00  | 2026-01-12 09:00:00  | NULL (current)
```

**Benefits:**
- Complete history of all token refreshes
- Audit compliance for trading systems
- Can track when and why sessions ended
- Debugging and troubleshooting

## Session Status Transitions

```
         ┌─────────┐
         │ CREATED │
         └────┬────┘
              │
         [insert]
              │
              ▼
         ┌─────────┐
    ┌────┤ ACTIVE  │◄───┐
    │    └────┬────┘    │
    │         │         │
    │    [refresh]  [revive]
    │         │         │
    │         ▼         │
    │    ┌─────────┐    │
    ├───►│ ACTIVE  ├────┘
    │    │ (v+1)   │
    │    └────┬────┘
    │         │
    │   [expire / revoke]
    │         │
    │         ▼
    │    ┌─────────┐
    └───►│EXPIRED/ │
         │ REVOKED │
         └─────────┘
```

## Benefits

### 1. Clean Separation of Concerns
- **user_brokers.credentials**: Permanent, rarely changes (apiKey, apiSecret)
- **user_broker_sessions**: Temporary, changes frequently (access tokens)

### 2. Efficient Updates
- Token refresh doesn't create new user_brokers version
- Only session table gets new versions

### 3. Multiple Sessions Support
- Same user-broker can have multiple concurrent sessions (if needed)
- Historical sessions preserved for audit

### 4. Auto-Refresh Ready
- Easy to find sessions expiring soon
- Background job can auto-refresh tokens

### 5. Audit Compliance
- Full history of all session activities
- Track when tokens were refreshed, expired, revoked
- Trading system compliance

## Next Steps

### 1. Update FyersAdapter
- Modify `connect()` to accept `UserBrokerSession` parameter
- Use session's access token instead of credentials.accessToken()

### 2. Update AdminService
- Create session when user updates data broker
- Provide UI to manage sessions (refresh, revoke)

### 3. Implement Auto-Refresh
- Background job to refresh expiring sessions
- Run every hour, check for sessions expiring in next 2 hours

### 4. Update BrokerConnectionService
- Load active session when connecting broker
- Create new session if none exists

## Code Files

**Domain Model:**
- `/tmp/annu-v04/src/main/java/in/annupaper/domain/model/UserBrokerSession.java`

**Repository:**
- `/tmp/annu-v04/src/main/java/in/annupaper/repository/UserBrokerSessionRepository.java`
- `/tmp/annu-v04/src/main/java/in/annupaper/repository/PostgresUserBrokerSessionRepository.java`

**Migration:**
- `/tmp/annu-v04/migrations/V008__add_user_broker_sessions_table.sql`

## Migration Status

✅ Database table created
✅ Domain model implemented
✅ Repository implemented
✅ Code compiled successfully
⏳ Integration with services (pending)
⏳ Admin UI updates (pending)
⏳ Auto-refresh logic (pending)
