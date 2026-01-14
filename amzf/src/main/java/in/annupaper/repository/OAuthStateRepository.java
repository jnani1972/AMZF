package in.annupaper.repository;

import in.annupaper.domain.broker.OAuthState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for OAuth state management.
 */
public final class OAuthStateRepository {
    private static final Logger log = LoggerFactory.getLogger(OAuthStateRepository.class);

    private final DataSource dataSource;

    public OAuthStateRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Generate and store a new OAuth state.
     *
     * @param userBrokerId User-broker ID
     * @param brokerId Broker code
     * @param expiryMinutes How many minutes until state expires
     * @return Generated state string
     */
    public String generateState(String userBrokerId, String brokerId, int expiryMinutes) {
        String state = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(expiryMinutes * 60L);

        String sql = """
            INSERT INTO oauth_states (state, user_broker_id, broker_id, created_at, expires_at)
            VALUES (?, ?, ?, ?, ?)
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, state);
            stmt.setString(2, userBrokerId);
            stmt.setString(3, brokerId);
            stmt.setTimestamp(4, Timestamp.from(now));
            stmt.setTimestamp(5, Timestamp.from(expiresAt));

            stmt.executeUpdate();
            log.info("[OAUTH STATE] Generated state for userBrokerId={}, expires in {}min", userBrokerId, expiryMinutes);
            return state;

        } catch (SQLException e) {
            log.error("[OAUTH STATE] Failed to generate state for userBrokerId={}", userBrokerId, e);
            throw new RuntimeException("Failed to generate OAuth state", e);
        }
    }

    /**
     * Find OAuth state by state parameter.
     */
    public Optional<OAuthState> findByState(String state) {
        String sql = """
            SELECT state, user_broker_id, broker_id, created_at, expires_at, used_at, deleted_at
            FROM oauth_states
            WHERE state = ?
              AND deleted_at IS NULL
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, state);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }

        } catch (SQLException e) {
            log.error("[OAUTH STATE] Failed to find state: {}", state, e);
            return Optional.empty();
        }
    }

    /**
     * Mark state as used (idempotency).
     *
     * @param state State parameter
     * @return true if marked, false if already used
     */
    public boolean markUsed(String state) {
        String sql = """
            UPDATE oauth_states
            SET used_at = ?
            WHERE state = ?
              AND deleted_at IS NULL
              AND used_at IS NULL
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setTimestamp(1, Timestamp.from(Instant.now()));
            stmt.setString(2, state);

            int updated = stmt.executeUpdate();
            if (updated > 0) {
                log.info("[OAUTH STATE] Marked state as used: {}", state);
                return true;
            } else {
                log.warn("[OAUTH STATE] State already used or not found: {}", state);
                return false;
            }

        } catch (SQLException e) {
            log.error("[OAUTH STATE] Failed to mark state as used: {}", state, e);
            return false;
        }
    }

    /**
     * Check if there's a login already in progress for a user-broker.
     * Returns true if there's an unused, unexpired state.
     *
     * @param userBrokerId User-broker ID
     * @return true if login already in progress
     */
    public boolean isLoginInProgress(String userBrokerId) {
        String sql = """
            SELECT COUNT(*) as count
            FROM oauth_states
            WHERE user_broker_id = ?
              AND deleted_at IS NULL
              AND used_at IS NULL
              AND expires_at > ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, userBrokerId);
            stmt.setTimestamp(2, Timestamp.from(Instant.now()));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt("count");
                    return count > 0;
                }
                return false;
            }

        } catch (SQLException e) {
            log.error("[OAUTH STATE] Failed to check login in progress for userBrokerId={}", userBrokerId, e);
            return false; // Assume not in progress on error
        }
    }

    /**
     * Clean up expired states (run periodically).
     *
     * @return Number of states deleted
     */
    public int cleanupExpired() {
        String sql = """
            UPDATE oauth_states
            SET deleted_at = ?
            WHERE deleted_at IS NULL
              AND expires_at < ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            Instant now = Instant.now();
            stmt.setTimestamp(1, Timestamp.from(now));
            stmt.setTimestamp(2, Timestamp.from(now));

            int deleted = stmt.executeUpdate();
            if (deleted > 0) {
                log.info("[OAUTH STATE] Cleaned up {} expired states", deleted);
            }
            return deleted;

        } catch (SQLException e) {
            log.error("[OAUTH STATE] Failed to cleanup expired states", e);
            return 0;
        }
    }

    /**
     * Map ResultSet row to OAuthState.
     */
    private OAuthState mapRow(ResultSet rs) throws SQLException {
        return new OAuthState(
            rs.getString("state"),
            rs.getString("user_broker_id"),
            rs.getString("broker_id"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("expires_at").toInstant(),
            rs.getTimestamp("used_at") != null ? rs.getTimestamp("used_at").toInstant() : null,
            rs.getTimestamp("deleted_at") != null ? rs.getTimestamp("deleted_at").toInstant() : null
        );
    }
}
