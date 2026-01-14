package in.annupaper.domain.broker;

import java.time.Instant;

/**
 * Domain model for user_broker_sessions table.
 * Tracks session tokens for user-broker combinations.
 *
 * Immutable record with audit trail (created_at, deleted_at, version).
 */
public record UserBrokerSession(
    String sessionId,
    String userBrokerId,
    String accessToken,
    Instant tokenValidTill,
    SessionStatus sessionStatus,
    Instant sessionStartedAt,
    Instant sessionEndedAt,

    // Audit trail
    Instant createdAt,
    Instant deletedAt,
    int version
) {
    public enum SessionStatus {
        ACTIVE,
        EXPIRED,
        REVOKED
    }

    /**
     * Check if session is currently active and not expired.
     */
    public boolean isActive() {
        if (sessionStatus != SessionStatus.ACTIVE || deletedAt != null) {
            return false;
        }

        if (tokenValidTill != null) {
            return Instant.now().isBefore(tokenValidTill);
        }

        return true;
    }

    /**
     * Check if session is expiring soon (within threshold).
     */
    public boolean isExpiringSoon(long thresholdSeconds) {
        if (tokenValidTill == null) {
            return false;
        }

        Instant expiryThreshold = Instant.now().plusSeconds(thresholdSeconds);
        return tokenValidTill.isBefore(expiryThreshold);
    }

    /**
     * Create a new session (version 1).
     */
    public static UserBrokerSession create(
        String sessionId,
        String userBrokerId,
        String accessToken,
        Instant tokenValidTill
    ) {
        return new UserBrokerSession(
            sessionId,
            userBrokerId,
            accessToken,
            tokenValidTill,
            SessionStatus.ACTIVE,
            Instant.now(),
            null,
            Instant.now(),
            null,
            1
        );
    }

    /**
     * Create new version with updated status (for immutable updates).
     */
    public UserBrokerSession withStatus(SessionStatus newStatus) {
        return new UserBrokerSession(
            sessionId,
            userBrokerId,
            accessToken,
            tokenValidTill,
            newStatus,
            sessionStartedAt,
            newStatus == SessionStatus.ACTIVE ? null : Instant.now(),
            Instant.now(),
            null,
            version + 1
        );
    }

    /**
     * Create new version with refreshed token (for immutable updates).
     */
    public UserBrokerSession withRefreshedToken(String newAccessToken, Instant newValidTill) {
        return new UserBrokerSession(
            sessionId,
            userBrokerId,
            newAccessToken,
            newValidTill,
            SessionStatus.ACTIVE,
            sessionStartedAt,
            null,
            Instant.now(),
            null,
            version + 1
        );
    }
}
