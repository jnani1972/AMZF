package in.annupaper.infrastructure.persistence;

import in.annupaper.domain.repository.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.annupaper.domain.broker.BrokerRole;
import in.annupaper.domain.broker.UserBroker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * PostgreSQL implementation of UserBrokerRepository.
 */
public final class PostgresUserBrokerRepository implements UserBrokerRepository {
    private static final Logger log = LoggerFactory.getLogger(PostgresUserBrokerRepository.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    private final DataSource dataSource;
    
    public PostgresUserBrokerRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    @Override
    public Optional<UserBroker> findById(String userBrokerId) {
        String sql = """
            SELECT * FROM user_brokers WHERE user_broker_id = ? AND deleted_at IS NULL
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userBrokerId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Error finding user-broker by ID: {}", e.getMessage(), e);
        }

        return Optional.empty();
    }
    
    @Override
    public List<UserBroker> findByUserId(String userId) {
        String sql = """
            SELECT * FROM user_brokers WHERE user_id = ? AND deleted_at IS NULL ORDER BY created_at ASC
            """;

        return queryList(sql, userId);
    }
    
    @Override
    public List<UserBroker> findActiveExecBrokersByUserId(String userId) {
        String sql = """
            SELECT * FROM user_brokers
            WHERE user_id = ?
              AND role = 'EXEC'
              AND enabled = true
              AND status = 'ACTIVE'
              AND deleted_at IS NULL
            ORDER BY created_at ASC
            """;

        return queryList(sql, userId);
    }
    
    @Override
    public List<UserBroker> findAllActiveExecBrokers() {
        String sql = """
            SELECT ub.* FROM user_brokers ub
            JOIN users u ON ub.user_id = u.user_id AND u.deleted_at IS NULL
            WHERE ub.role = 'EXEC'
              AND ub.enabled = true
              AND ub.status = 'ACTIVE'
              AND ub.deleted_at IS NULL
              AND u.status = 'ACTIVE'
            ORDER BY ub.user_id ASC, ub.created_at ASC
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<UserBroker> results = new ArrayList<>();
            while (rs.next()) {
                results.add(mapRow(rs));
            }
            return results;

        } catch (Exception e) {
            log.error("Error finding active exec brokers: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public List<UserBroker> findAll() {
        String sql = """
            SELECT DISTINCT ON (ub.user_broker_id) ub.*
            FROM user_brokers ub
            JOIN users u ON ub.user_id = u.user_id AND u.deleted_at IS NULL
            WHERE ub.deleted_at IS NULL
              AND u.status = 'ACTIVE'
            ORDER BY ub.user_broker_id ASC, ub.version DESC, ub.created_at DESC
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<UserBroker> results = new ArrayList<>();
            while (rs.next()) {
                results.add(mapRow(rs));
            }
            return results;

        } catch (Exception e) {
            log.error("Error finding all user-brokers: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public Optional<UserBroker> findDataBroker() {
        String sql = """
            SELECT ub.* FROM user_brokers ub
            JOIN users u ON ub.user_id = u.user_id AND u.deleted_at IS NULL
            WHERE ub.role = 'DATA'
              AND ub.enabled = true
              AND ub.status = 'ACTIVE'
              AND ub.deleted_at IS NULL
              AND u.status = 'ACTIVE'
            LIMIT 1
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                return Optional.of(mapRow(rs));
            }

        } catch (Exception e) {
            log.error("Error finding data broker: {}", e.getMessage(), e);
        }

        return Optional.empty();
    }
    
    @Override
    public Optional<UserBroker> findByUserAndBroker(String userId, String brokerId) {
        String sql = """
            SELECT * FROM user_brokers
            WHERE user_id = ? AND broker_id = ? AND deleted_at IS NULL
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userId);
            ps.setString(2, brokerId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }

        } catch (Exception e) {
            log.error("Error finding user-broker: {}", e.getMessage(), e);
        }

        return Optional.empty();
    }
    
    // @Override
    // public UserBroker save(UserBroker ub) {
    //     String sql = """
    //         INSERT INTO user_brokers (
    //             user_broker_id, user_id, broker_id, role, credentials,
    //             connected, last_connected, connection_error,
    //             capital_allocated, max_exposure, max_per_trade, max_open_trades,
    //             allowed_symbols, blocked_symbols, allowed_products,
    //             max_daily_loss, max_weekly_loss, cooldown_minutes,
    //             status, enabled
    //         ) VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    //         ON CONFLICT (user_broker_id) DO UPDATE SET
    //             user_id = EXCLUDED.user_id,
    //             broker_id = EXCLUDED.broker_id,
    //             role = EXCLUDED.role,
    //             credentials = EXCLUDED.credentials,
    //             connected = EXCLUDED.connected,
    //             last_connected = EXCLUDED.last_connected,
    //             connection_error = EXCLUDED.connection_error,
    //             capital_allocated = EXCLUDED.capital_allocated,
    //             max_exposure = EXCLUDED.max_exposure,
    //             max_per_trade = EXCLUDED.max_per_trade,
    //             max_open_trades = EXCLUDED.max_open_trades,
    //             allowed_symbols = EXCLUDED.allowed_symbols,
    //             blocked_symbols = EXCLUDED.blocked_symbols,
    //             allowed_products = EXCLUDED.allowed_products,
    //             max_daily_loss = EXCLUDED.max_daily_loss,
    //             max_weekly_loss = EXCLUDED.max_weekly_loss,
    //             cooldown_minutes = EXCLUDED.cooldown_minutes,
    //             status = EXCLUDED.status,
    //             enabled = EXCLUDED.enabled,
    //             updated_at = NOW()
    //         RETURNING *
    //         """;
    //
    //     try (Connection conn = dataSource.getConnection();
    //          PreparedStatement ps = conn.prepareStatement(sql)) {
    //
    //         ps.setString(1, ub.userBrokerId());
    //         ps.setString(2, ub.userId());
    //         ps.setString(3, ub.brokerId());
    //         ps.setString(4, ub.role().name());
    //         ps.setString(5, ub.credentials() != null ? MAPPER.writeValueAsString(ub.credentials()) : "{}");
    //         ps.setBoolean(6, ub.connected());
    //         ps.setTimestamp(7, ub.lastConnected() != null ? Timestamp.from(ub.lastConnected()) : null);
    //         ps.setString(8, ub.connectionError());
    //         ps.setBigDecimal(9, ub.capitalAllocated());
    //         ps.setBigDecimal(10, ub.maxExposure());
    //         ps.setBigDecimal(11, ub.maxPerTrade());
    //         ps.setInt(12, ub.maxOpenTrades());
    //         ps.setArray(13, conn.createArrayOf("text", ub.allowedSymbols() != null ? ub.allowedSymbols().toArray() : new String[0]));
    //         ps.setArray(14, conn.createArrayOf("text", ub.blockedSymbols() != null ? ub.blockedSymbols().toArray() : new String[0]));
    //         ps.setArray(15, conn.createArrayOf("text", ub.allowedProducts() != null ? ub.allowedProducts().toArray() : new String[0]));
    //         ps.setBigDecimal(16, ub.maxDailyLoss());
    //         ps.setBigDecimal(17, ub.maxWeeklyLoss());
    //         ps.setInt(18, ub.cooldownMinutes());
    //         ps.setString(19, ub.status());
    //         ps.setBoolean(20, ub.enabled());
    //
    //         try (ResultSet rs = ps.executeQuery()) {
    //             if (rs.next()) {
    //                 return mapRow(rs);
    //             }
    //         }
    //
    //     } catch (Exception e) {
    //         log.error("Error saving user-broker: {}", e.getMessage(), e);
    //         throw new RuntimeException("Failed to save user-broker", e);
    //     }
    //
    //     return ub;
    // }
    @Override
    public UserBroker save(UserBroker ub) {
        // Check if exists
        Optional<UserBroker> existing = findById(ub.userBrokerId());

        if (existing.isPresent()) {
            // Immutable update: soft delete + insert new version
            performImmutableUpdate(ub);
        } else {
            // Insert new with version=1
            performInsert(ub);
        }

        return findById(ub.userBrokerId()).orElse(ub);
    }

    private void performInsert(UserBroker ub) {
        String sql = """
            INSERT INTO user_brokers (
                user_broker_id, user_id, broker_id, role, credentials,
                connected, last_connected, connection_error,
                capital_allocated, max_exposure, max_per_trade, max_open_trades,
                allowed_symbols, blocked_symbols, allowed_products,
                max_daily_loss, max_weekly_loss, cooldown_minutes,
                status, enabled, version
            ) VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, ub.userBrokerId());
            ps.setString(2, ub.userId());
            ps.setString(3, ub.brokerId());
            ps.setString(4, ub.role().name());
            ps.setString(5, ub.credentials() != null ? MAPPER.writeValueAsString(ub.credentials()) : "{}");
            ps.setBoolean(6, ub.connected());
            ps.setTimestamp(7, ub.lastConnected() != null ? Timestamp.from(ub.lastConnected()) : null);
            ps.setString(8, ub.connectionError());
            ps.setBigDecimal(9, ub.capitalAllocated());
            ps.setBigDecimal(10, ub.maxExposure());
            ps.setBigDecimal(11, ub.maxPerTrade());
            ps.setInt(12, ub.maxOpenTrades());
            ps.setArray(13, conn.createArrayOf("text", ub.allowedSymbols() != null ? ub.allowedSymbols().toArray() : new String[0]));
            ps.setArray(14, conn.createArrayOf("text", ub.blockedSymbols() != null ? ub.blockedSymbols().toArray() : new String[0]));
            ps.setArray(15, conn.createArrayOf("text", ub.allowedProducts() != null ? ub.allowedProducts().toArray() : new String[0]));
            ps.setBigDecimal(16, ub.maxDailyLoss());
            ps.setBigDecimal(17, ub.maxWeeklyLoss());
            ps.setInt(18, ub.cooldownMinutes());
            ps.setString(19, ub.status());
            ps.setBoolean(20, ub.enabled());

            ps.executeUpdate();
            log.info("UserBroker inserted: {} version 1", ub.userBrokerId());

        } catch (Exception e) {
            log.error("Error inserting user-broker: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to insert user-broker", e);
        }
    }

    private void performImmutableUpdate(UserBroker ub) {
        String queryVersionSql = "SELECT version FROM user_brokers WHERE user_broker_id = ? AND deleted_at IS NULL";
        String softDeleteSql = "UPDATE user_brokers SET deleted_at = NOW() WHERE user_broker_id = ? AND version = ?";
        String insertSql = """
            INSERT INTO user_brokers (
                user_broker_id, user_id, broker_id, role, credentials,
                connected, last_connected, connection_error,
                capital_allocated, max_exposure, max_per_trade, max_open_trades,
                allowed_symbols, blocked_symbols, allowed_products,
                max_daily_loss, max_weekly_loss, cooldown_minutes,
                status, enabled, version
            ) VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            int currentVersion;
            try (PreparedStatement ps = conn.prepareStatement(queryVersionSql)) {
                ps.setString(1, ub.userBrokerId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new RuntimeException("UserBroker not found: " + ub.userBrokerId());
                    }
                    currentVersion = rs.getInt("version");
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(softDeleteSql)) {
                ps.setString(1, ub.userBrokerId());
                ps.setInt(2, currentVersion);
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    throw new RuntimeException("Failed to soft delete user-broker version: " + ub.userBrokerId());
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, ub.userBrokerId());
                ps.setString(2, ub.userId());
                ps.setString(3, ub.brokerId());
                ps.setString(4, ub.role().name());
                ps.setString(5, ub.credentials() != null ? MAPPER.writeValueAsString(ub.credentials()) : "{}");
                ps.setBoolean(6, ub.connected());
                ps.setTimestamp(7, ub.lastConnected() != null ? Timestamp.from(ub.lastConnected()) : null);
                ps.setString(8, ub.connectionError());
                ps.setBigDecimal(9, ub.capitalAllocated());
                ps.setBigDecimal(10, ub.maxExposure());
                ps.setBigDecimal(11, ub.maxPerTrade());
                ps.setInt(12, ub.maxOpenTrades());
                ps.setArray(13, conn.createArrayOf("text", ub.allowedSymbols() != null ? ub.allowedSymbols().toArray() : new String[0]));
                ps.setArray(14, conn.createArrayOf("text", ub.blockedSymbols() != null ? ub.blockedSymbols().toArray() : new String[0]));
                ps.setArray(15, conn.createArrayOf("text", ub.allowedProducts() != null ? ub.allowedProducts().toArray() : new String[0]));
                ps.setBigDecimal(16, ub.maxDailyLoss());
                ps.setBigDecimal(17, ub.maxWeeklyLoss());
                ps.setInt(18, ub.cooldownMinutes());
                ps.setString(19, ub.status());
                ps.setBoolean(20, ub.enabled());
                ps.setInt(21, currentVersion + 1);

                ps.executeUpdate();
            }

            conn.commit();
            log.info("UserBroker updated: {} version {} → {}", ub.userBrokerId(), currentVersion, currentVersion + 1);

        } catch (Exception e) {
            log.error("Error updating user-broker: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update user-broker", e);
        }
    }
    
    // @Override
    // public void updateConnectionStatus(String userBrokerId, boolean connected, String errorMessage) {
    //     String sql = """
    //         UPDATE user_brokers SET
    //             connected = ?,
    //             last_connected = CASE WHEN ? THEN NOW() ELSE last_connected END,
    //             connection_error = ?,
    //             updated_at = NOW()
    //         WHERE user_broker_id = ?
    //         """;
    //
    //     try (Connection conn = dataSource.getConnection();
    //          PreparedStatement ps = conn.prepareStatement(sql)) {
    //
    //         ps.setBoolean(1, connected);
    //         ps.setBoolean(2, connected);
    //         ps.setString(3, errorMessage);
    //         ps.setString(4, userBrokerId);
    //
    //         ps.executeUpdate();
    //
    //     } catch (Exception e) {
    //         log.error("Error updating connection status: {}", e.getMessage(), e);
    //     }
    // }
    @Override
    public void updateConnectionStatus(String userBrokerId, boolean connected, String errorMessage) {
        // Fetch current record and update immutably
        Optional<UserBroker> current = findById(userBrokerId);
        if (current.isEmpty()) {
            log.warn("Cannot update connection status - UserBroker not found: {}", userBrokerId);
            return;
        }

        UserBroker ub = current.get();

        // Create updated record with new connection status
        UserBroker updated = new UserBroker(
            ub.userBrokerId(), ub.userId(), ub.brokerId(), ub.role(), ub.credentials(),
            connected,
            connected ? Instant.now() : ub.lastConnected(),
            errorMessage,
            ub.capitalAllocated(), ub.maxExposure(), ub.maxPerTrade(), ub.maxOpenTrades(),
            ub.allowedSymbols(), ub.blockedSymbols(), ub.allowedProducts(),
            ub.maxDailyLoss(), ub.maxWeeklyLoss(), ub.cooldownMinutes(),
            ub.status(), ub.enabled(), ub.createdAt(), ub.updatedAt(), ub.deletedAt(), ub.version()
        );

        performImmutableUpdate(updated);
    }
    
    private List<UserBroker> queryList(String sql, String userId) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, userId);
            
            List<UserBroker> results = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
            return results;
            
        } catch (Exception e) {
            log.error("Error querying user-brokers: {}", e.getMessage(), e);
            return List.of();
        }
    }
    
    private UserBroker mapRow(ResultSet rs) throws Exception {
        String userBrokerId = rs.getString("user_broker_id");
        String userId = rs.getString("user_id");
        String brokerId = rs.getString("broker_id");
        BrokerRole role = BrokerRole.valueOf(rs.getString("role"));

        String credJson = rs.getString("credentials");
        JsonNode credentials = credJson != null ? MAPPER.readTree(credJson) : null;

        boolean connected = rs.getBoolean("connected");
        Timestamp lastConnTs = rs.getTimestamp("last_connected");
        Instant lastConnected = lastConnTs != null ? lastConnTs.toInstant() : null;
        String connectionError = rs.getString("connection_error");

        BigDecimal capitalAllocated = rs.getBigDecimal("capital_allocated");
        BigDecimal maxExposure = rs.getBigDecimal("max_exposure");
        BigDecimal maxPerTrade = rs.getBigDecimal("max_per_trade");
        int maxOpenTrades = rs.getInt("max_open_trades");

        Array allowedArr = rs.getArray("allowed_symbols");
        List<String> allowedSymbols = allowedArr != null
            ? Arrays.asList((String[]) allowedArr.getArray())
            : List.of();

        Array blockedArr = rs.getArray("blocked_symbols");
        List<String> blockedSymbols = blockedArr != null
            ? Arrays.asList((String[]) blockedArr.getArray())
            : List.of();

        Array productsArr = rs.getArray("allowed_products");
        List<String> allowedProducts = productsArr != null
            ? Arrays.asList((String[]) productsArr.getArray())
            : List.of();

        BigDecimal maxDailyLoss = rs.getBigDecimal("max_daily_loss");
        BigDecimal maxWeeklyLoss = rs.getBigDecimal("max_weekly_loss");
        int cooldownMinutes = rs.getInt("cooldown_minutes");

        String status = rs.getString("status");
        boolean enabled = rs.getBoolean("enabled");

        Timestamp createdTs = rs.getTimestamp("created_at");
        Timestamp updatedTs = rs.getTimestamp("updated_at");
        Instant createdAt = createdTs != null ? createdTs.toInstant() : Instant.now();
        Instant updatedAt = updatedTs != null ? updatedTs.toInstant() : Instant.now();

        Timestamp deletedTs = rs.getTimestamp("deleted_at");
        Instant deletedAt = deletedTs != null ? deletedTs.toInstant() : null;

        int version = rs.getInt("version");

        return new UserBroker(
            userBrokerId, userId, brokerId, role, credentials,
            connected, lastConnected, connectionError,
            capitalAllocated, maxExposure, maxPerTrade, maxOpenTrades,
            allowedSymbols, blockedSymbols, allowedProducts,
            maxDailyLoss, maxWeeklyLoss, cooldownMinutes,
            status, enabled, createdAt, updatedAt, deletedAt, version
        );
    }

    // ========================================================================
    // MONITORING METHODS
    // ========================================================================

    @Override
    public long countExpiredBrokerSessions() {
        String sql = """
            SELECT COUNT(*)
            FROM user_brokers
            WHERE enabled = true
              AND deleted_at IS NULL
              AND session_expiry_at < NOW()
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception e) {
            log.error("Failed to count expired broker sessions: {}", e.getMessage());
            throw new RuntimeException("Failed to count expired broker sessions", e);
        }
        return 0;
    }

    @Override
    public long countExpiringSoonBrokerSessions() {
        String sql = """
            SELECT COUNT(*)
            FROM user_brokers
            WHERE enabled = true
              AND deleted_at IS NULL
              AND session_expiry_at > NOW()
              AND session_expiry_at < NOW() + INTERVAL '1 hour'
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception e) {
            log.error("Failed to count expiring broker sessions: {}", e.getMessage());
            throw new RuntimeException("Failed to count expiring broker sessions", e);
        }
        return 0;
    }

    @Override
    public List<UserBroker> findExpiredBrokerSessions() {
        String sql = """
            SELECT *
            FROM user_brokers
            WHERE enabled = true
              AND deleted_at IS NULL
              AND session_expiry_at < NOW()
            ORDER BY session_expiry_at ASC
            """;

        List<UserBroker> expired = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                expired.add(mapRow(rs));
            }
        } catch (Exception e) {
            log.error("Failed to find expired broker sessions: {}", e.getMessage());
            throw new RuntimeException("Failed to find expired broker sessions", e);
        }
        return expired;
    }

    @Override
    public long countActiveBrokers() {
        String sql = """
            SELECT COUNT(*)
            FROM user_brokers
            WHERE enabled = true
              AND deleted_at IS NULL
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception e) {
            log.error("Failed to count active brokers: {}", e.getMessage());
            throw new RuntimeException("Failed to count active brokers", e);
        }
        return 0;
    }
}
