package in.annupaper.application.port.output;

import in.annupaper.domain.model.UserBrokerSession;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for user_broker_sessions table.
 * Manages session tokens for user-broker combinations.
 */
public interface UserBrokerSessionRepository {

    /**
     * Find active session for a user-broker combination.
     * Returns the most recent active session (deleted_at IS NULL, status=ACTIVE).
     */
    Optional<UserBrokerSession> findActiveSession(String userBrokerId);

    /**
     * Find session by ID.
     * Returns the current version (deleted_at IS NULL).
     */
    Optional<UserBrokerSession> findById(String sessionId);

    /**
     * Find all sessions for a user-broker (including expired/revoked).
     * Returns only current versions (deleted_at IS NULL).
     */
    List<UserBrokerSession> findByUserBrokerId(String userBrokerId);

    /**
     * Find sessions expiring before a given timestamp.
     * Useful for auto-refresh logic.
     */
    List<UserBrokerSession> findExpiringSessions(Instant before);

    /**
     * Insert new session (version 1).
     */
    void insert(UserBrokerSession session);

    /**
     * Update session (immutable: soft delete current + insert new version).
     */
    void update(UserBrokerSession session);

    /**
     * Delete session (soft delete).
     */
    void delete(String sessionId);
}
