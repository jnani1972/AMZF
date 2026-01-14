package in.annupaper.repository;

import in.annupaper.domain.broker.UserBrokerSession;
import in.annupaper.domain.broker.UserBrokerSession.SessionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * PostgreSQL implementation of UserBrokerSessionRepository.
 * Implements immutable audit trail pattern.
 */
public class PostgresUserBrokerSessionRepository implements UserBrokerSessionRepository {
    private static final Logger log = LoggerFactory.getLogger(PostgresUserBrokerSessionRepository.class);

    private final DataSource dataSource;

    public PostgresUserBrokerSessionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<UserBrokerSession> findActiveSession(String userBrokerId) {
        String sql = """
            SELECT session_id, user_broker_id, access_token, token_valid_till, session_status,
                   session_started_at, session_ended_at, created_at, deleted_at, version
            FROM user_broker_sessions
            WHERE user_broker_id = ?
              AND deleted_at IS NULL
              AND session_status = 'ACTIVE'
            ORDER BY created_at DESC
            LIMIT 1
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, userBrokerId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Error finding active session for user_broker_id={}: {}", userBrokerId, e.getMessage());
        }

        return Optional.empty();
    }

    @Override
    public Optional<UserBrokerSession> findById(String sessionId) {
        String sql = """
            SELECT session_id, user_broker_id, access_token, token_valid_till, session_status,
                   session_started_at, session_ended_at, created_at, deleted_at, version
            FROM user_broker_sessions
            WHERE session_id = ?
              AND deleted_at IS NULL
            ORDER BY version DESC
            LIMIT 1
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, sessionId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Error finding session by id={}: {}", sessionId, e.getMessage());
        }

        return Optional.empty();
    }

    @Override
    public List<UserBrokerSession> findByUserBrokerId(String userBrokerId) {
        String sql = """
            SELECT session_id, user_broker_id, access_token, token_valid_till, session_status,
                   session_started_at, session_ended_at, created_at, deleted_at, version
            FROM user_broker_sessions
            WHERE user_broker_id = ?
              AND deleted_at IS NULL
            ORDER BY created_at DESC
            """;

        List<UserBrokerSession> sessions = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, userBrokerId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    sessions.add(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Error finding sessions for user_broker_id={}: {}", userBrokerId, e.getMessage());
        }

        return sessions;
    }

    @Override
    public List<UserBrokerSession> findExpiringSessions(Instant before) {
        String sql = """
            SELECT session_id, user_broker_id, access_token, token_valid_till, session_status,
                   session_started_at, session_ended_at, created_at, deleted_at, version
            FROM user_broker_sessions
            WHERE deleted_at IS NULL
              AND session_status = 'ACTIVE'
              AND token_valid_till IS NOT NULL
              AND token_valid_till < ?
            ORDER BY token_valid_till ASC
            """;

        List<UserBrokerSession> sessions = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setTimestamp(1, Timestamp.from(before));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    sessions.add(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Error finding expiring sessions before {}: {}", before, e.getMessage());
        }

        return sessions;
    }

    @Override
    public void insert(UserBrokerSession session) {
        String sql = """
            INSERT INTO user_broker_sessions (
                session_id, user_broker_id, access_token, token_valid_till, session_status,
                session_started_at, session_ended_at, created_at, deleted_at, version
            ) VALUES (?, ?, ?, ?, ?::text, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, session.sessionId());
            stmt.setString(2, session.userBrokerId());
            stmt.setString(3, session.accessToken());
            stmt.setTimestamp(4, session.tokenValidTill() != null ? Timestamp.from(session.tokenValidTill()) : null);
            stmt.setString(5, session.sessionStatus().name());
            stmt.setTimestamp(6, Timestamp.from(session.sessionStartedAt()));
            stmt.setTimestamp(7, session.sessionEndedAt() != null ? Timestamp.from(session.sessionEndedAt()) : null);
            stmt.setTimestamp(8, Timestamp.from(session.createdAt()));
            stmt.setTimestamp(9, session.deletedAt() != null ? Timestamp.from(session.deletedAt()) : null);
            stmt.setInt(10, session.version());

            stmt.executeUpdate();

            log.info("Inserted session: {} for user_broker={} (valid till: {})",
                     session.sessionId(), session.userBrokerId(), session.tokenValidTill());

        } catch (SQLException e) {
            log.error("Error inserting session: {}", e.getMessage());
            throw new RuntimeException("Failed to insert session", e);
        }
    }

    @Override
    public void update(UserBrokerSession session) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // Step 1: Soft delete current version
                String deleteSql = """
                    UPDATE user_broker_sessions
                    SET deleted_at = NOW()
                    WHERE session_id = ?
                      AND version = ?
                      AND deleted_at IS NULL
                    """;

                try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
                    stmt.setString(1, session.sessionId());
                    stmt.setInt(2, session.version() - 1);  // Previous version
                    stmt.executeUpdate();
                }

                // Step 2: Insert new version
                String insertSql = """
                    INSERT INTO user_broker_sessions (
                        session_id, user_broker_id, access_token, token_valid_till, session_status,
                        session_started_at, session_ended_at, created_at, deleted_at, version
                    ) VALUES (?, ?, ?, ?, ?::text, ?, ?, ?, ?, ?)
                    """;

                try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                    stmt.setString(1, session.sessionId());
                    stmt.setString(2, session.userBrokerId());
                    stmt.setString(3, session.accessToken());
                    stmt.setTimestamp(4, session.tokenValidTill() != null ? Timestamp.from(session.tokenValidTill()) : null);
                    stmt.setString(5, session.sessionStatus().name());
                    stmt.setTimestamp(6, Timestamp.from(session.sessionStartedAt()));
                    stmt.setTimestamp(7, session.sessionEndedAt() != null ? Timestamp.from(session.sessionEndedAt()) : null);
                    stmt.setTimestamp(8, Timestamp.from(session.createdAt()));
                    stmt.setTimestamp(9, session.deletedAt() != null ? Timestamp.from(session.deletedAt()) : null);
                    stmt.setInt(10, session.version());

                    stmt.executeUpdate();
                }

                conn.commit();

                log.info("Updated session: {} to version {} (status: {})",
                         session.sessionId(), session.version(), session.sessionStatus());

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }

        } catch (SQLException e) {
            log.error("Error updating session: {}", e.getMessage());
            throw new RuntimeException("Failed to update session", e);
        }
    }

    @Override
    public void delete(String sessionId) {
        String sql = """
            UPDATE user_broker_sessions
            SET deleted_at = NOW()
            WHERE session_id = ?
              AND deleted_at IS NULL
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, sessionId);
            int rowsUpdated = stmt.executeUpdate();

            if (rowsUpdated > 0) {
                log.info("Deleted session: {}", sessionId);
            }

        } catch (SQLException e) {
            log.error("Error deleting session: {}", e.getMessage());
            throw new RuntimeException("Failed to delete session", e);
        }
    }

    /**
     * Map ResultSet to UserBrokerSession domain model.
     */
    private UserBrokerSession mapResultSet(ResultSet rs) throws SQLException {
        Timestamp tokenValidTillTs = rs.getTimestamp("token_valid_till");
        Timestamp sessionEndedAtTs = rs.getTimestamp("session_ended_at");
        Timestamp deletedAtTs = rs.getTimestamp("deleted_at");

        return new UserBrokerSession(
            rs.getString("session_id"),
            rs.getString("user_broker_id"),
            rs.getString("access_token"),
            tokenValidTillTs != null ? tokenValidTillTs.toInstant() : null,
            SessionStatus.valueOf(rs.getString("session_status")),
            rs.getTimestamp("session_started_at").toInstant(),
            sessionEndedAtTs != null ? sessionEndedAtTs.toInstant() : null,
            rs.getTimestamp("created_at").toInstant(),
            deletedAtTs != null ? deletedAtTs.toInstant() : null,
            rs.getInt("version")
        );
    }
}
