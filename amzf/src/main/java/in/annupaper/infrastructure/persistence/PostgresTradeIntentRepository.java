package in.annupaper.infrastructure.persistence;

import in.annupaper.domain.repository.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.annupaper.domain.trade.IntentStatus;
import in.annupaper.domain.trade.TradeIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * PostgreSQL implementation of TradeIntentRepository with immutable audit trail.
 */
public final class PostgresTradeIntentRepository implements TradeIntentRepository {
    private static final Logger log = LoggerFactory.getLogger(PostgresTradeIntentRepository.class);

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public PostgresTradeIntentRepository(DataSource dataSource) {
        this.dataSource = dataSource;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public List<TradeIntent> findAll() {
        String sql = """
            SELECT * FROM trade_intents
            WHERE deleted_at IS NULL
            ORDER BY created_at DESC
            """;

        List<TradeIntent> intents = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                intents.add(mapRow(rs));
            }
        } catch (Exception e) {
            log.error("Failed to find all trade intents: {}", e.getMessage());
            throw new RuntimeException("Failed to find trade intents", e);
        }
        return intents;
    }

    @Override
    public Optional<TradeIntent> findById(String intentId) {
        String sql = """
            SELECT * FROM trade_intents
            WHERE intent_id = ? AND deleted_at IS NULL
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, intentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find trade intent {}: {}", intentId, e.getMessage());
            throw new RuntimeException("Failed to find trade intent", e);
        }
        return Optional.empty();
    }

    @Override
    public List<TradeIntent> findBySignalId(String signalId) {
        String sql = """
            SELECT * FROM trade_intents
            WHERE signal_id = ? AND deleted_at IS NULL
            ORDER BY created_at DESC
            """;

        List<TradeIntent> intents = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, signalId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    intents.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find trade intents by signal {}: {}", signalId, e.getMessage());
            throw new RuntimeException("Failed to find trade intents", e);
        }
        return intents;
    }

    @Override
    public List<TradeIntent> findByUserId(String userId) {
        String sql = """
            SELECT * FROM trade_intents
            WHERE user_id = ? AND deleted_at IS NULL
            ORDER BY created_at DESC
            """;

        List<TradeIntent> intents = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    intents.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find trade intents by user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to find trade intents", e);
        }
        return intents;
    }

    @Override
    public List<TradeIntent> findByUserBrokerId(String userBrokerId) {
        String sql = """
            SELECT * FROM trade_intents
            WHERE user_broker_id = ? AND deleted_at IS NULL
            ORDER BY created_at DESC
            """;

        List<TradeIntent> intents = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userBrokerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    intents.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find trade intents by user-broker {}: {}", userBrokerId, e.getMessage());
            throw new RuntimeException("Failed to find trade intents", e);
        }
        return intents;
    }

    @Override
    public List<TradeIntent> findByStatus(IntentStatus status) {
        String sql = """
            SELECT * FROM trade_intents
            WHERE status = ? AND deleted_at IS NULL
            ORDER BY created_at DESC
            """;

        List<TradeIntent> intents = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    intents.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find trade intents by status {}: {}", status, e.getMessage());
            throw new RuntimeException("Failed to find trade intents", e);
        }
        return intents;
    }

    @Override
    public void insert(TradeIntent intent) {
        String sql = """
            INSERT INTO trade_intents (
                intent_id, signal_id, user_id, broker_id, user_broker_id,
                validation_passed, validation_errors,
                calculated_qty, calculated_value, order_type, limit_price, product_type,
                log_impact, portfolio_exposure_after,
                status, order_id, trade_id,
                created_at, validated_at, executed_at,
                error_code, error_message, version
            ) VALUES (
                ?, ?, ?, ?, ?,
                ?, ?::jsonb,
                ?, ?, ?, ?, ?,
                ?, ?,
                ?, ?, ?,
                ?, ?, ?,
                ?, ?, 1
            )
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, intent.intentId());
            ps.setString(2, intent.signalId());
            ps.setString(3, intent.userId());
            ps.setString(4, intent.brokerId());
            ps.setString(5, intent.userBrokerId());

            ps.setBoolean(6, intent.validationPassed());
            ps.setString(7, objectMapper.writeValueAsString(intent.validationErrors()));

            setIntOrNull(ps, 8, intent.calculatedQty());
            setBigDecimalOrNull(ps, 9, intent.calculatedValue());
            ps.setString(10, intent.orderType());
            setBigDecimalOrNull(ps, 11, intent.limitPrice());
            ps.setString(12, intent.productType());

            setBigDecimalOrNull(ps, 13, intent.logImpact());
            setBigDecimalOrNull(ps, 14, intent.portfolioExposureAfter());

            ps.setString(15, intent.status().name());
            ps.setString(16, intent.orderId());
            ps.setString(17, intent.tradeId());

            ps.setTimestamp(18, Timestamp.from(intent.createdAt()));
            setTimestampOrNull(ps, 19, intent.validatedAt());
            setTimestampOrNull(ps, 20, intent.executedAt());

            ps.setString(21, intent.errorCode());
            ps.setString(22, intent.errorMessage());

            ps.executeUpdate();
            log.info("Trade intent inserted: {}", intent.intentId());

        } catch (Exception e) {
            log.error("Failed to insert trade intent: {}", e.getMessage());
            throw new RuntimeException("Failed to insert trade intent", e);
        }
    }

    @Override
    public void update(TradeIntent intent) {
        // Immutable update: soft delete old version, insert new version

        String queryVersionSql = """
            SELECT version FROM trade_intents
            WHERE intent_id = ? AND deleted_at IS NULL
            """;

        String softDeleteSql = """
            UPDATE trade_intents
            SET deleted_at = NOW()
            WHERE intent_id = ? AND version = ?
            """;

        String insertSql = """
            INSERT INTO trade_intents (
                intent_id, signal_id, user_id, broker_id, user_broker_id,
                validation_passed, validation_errors,
                calculated_qty, calculated_value, order_type, limit_price, product_type,
                log_impact, portfolio_exposure_after,
                status, order_id, trade_id,
                created_at, validated_at, executed_at,
                error_code, error_message, version
            ) VALUES (
                ?, ?, ?, ?, ?,
                ?, ?::jsonb,
                ?, ?, ?, ?, ?,
                ?, ?,
                ?, ?, ?,
                ?, ?, ?,
                ?, ?, ?
            )
            """;

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            // Step 1: Get current version
            int currentVersion;
            try (PreparedStatement ps = conn.prepareStatement(queryVersionSql)) {
                ps.setString(1, intent.intentId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new RuntimeException("Trade intent not found: " + intent.intentId());
                    }
                    currentVersion = rs.getInt("version");
                }
            }

            // Step 2: Soft delete current version
            try (PreparedStatement ps = conn.prepareStatement(softDeleteSql)) {
                ps.setString(1, intent.intentId());
                ps.setInt(2, currentVersion);
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    throw new RuntimeException("Failed to soft delete trade intent: " + intent.intentId());
                }
            }

            // Step 3: Insert new version
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, intent.intentId());
                ps.setString(2, intent.signalId());
                ps.setString(3, intent.userId());
                ps.setString(4, intent.brokerId());
                ps.setString(5, intent.userBrokerId());

                ps.setBoolean(6, intent.validationPassed());
                ps.setString(7, objectMapper.writeValueAsString(intent.validationErrors()));

                setIntOrNull(ps, 8, intent.calculatedQty());
                setBigDecimalOrNull(ps, 9, intent.calculatedValue());
                ps.setString(10, intent.orderType());
                setBigDecimalOrNull(ps, 11, intent.limitPrice());
                ps.setString(12, intent.productType());

                setBigDecimalOrNull(ps, 13, intent.logImpact());
                setBigDecimalOrNull(ps, 14, intent.portfolioExposureAfter());

                ps.setString(15, intent.status().name());
                ps.setString(16, intent.orderId());
                ps.setString(17, intent.tradeId());

                ps.setTimestamp(18, Timestamp.from(intent.createdAt()));
                setTimestampOrNull(ps, 19, intent.validatedAt());
                setTimestampOrNull(ps, 20, intent.executedAt());

                ps.setString(21, intent.errorCode());
                ps.setString(22, intent.errorMessage());
                ps.setInt(23, currentVersion + 1);

                ps.executeUpdate();
            }

            conn.commit();
            log.info("Trade intent updated: {} version {} → {}",
                     intent.intentId(), currentVersion, currentVersion + 1);

        } catch (Exception e) {
            log.error("Failed to update trade intent: {}", e.getMessage());
            throw new RuntimeException("Failed to update trade intent", e);
        }
    }

    @Override
    public void delete(String intentId) {
        // Soft delete: mark as deleted

        String queryVersionSql = """
            SELECT version FROM trade_intents
            WHERE intent_id = ? AND deleted_at IS NULL
            """;

        String softDeleteSql = """
            UPDATE trade_intents
            SET deleted_at = NOW()
            WHERE intent_id = ? AND version = ?
            """;

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            // Get current version
            int currentVersion;
            try (PreparedStatement ps = conn.prepareStatement(queryVersionSql)) {
                ps.setString(1, intentId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new RuntimeException("Trade intent not found: " + intentId);
                    }
                    currentVersion = rs.getInt("version");
                }
            }

            // Soft delete
            try (PreparedStatement ps = conn.prepareStatement(softDeleteSql)) {
                ps.setString(1, intentId);
                ps.setInt(2, currentVersion);
                int deleted = ps.executeUpdate();
                if (deleted == 0) {
                    throw new RuntimeException("Failed to delete trade intent: " + intentId);
                }
            }

            conn.commit();
            log.info("Trade intent soft-deleted: {} version {}", intentId, currentVersion);

        } catch (Exception e) {
            log.error("Failed to delete trade intent: {}", e.getMessage());
            throw new RuntimeException("Failed to delete trade intent", e);
        }
    }

    @Override
    public List<TradeIntent> findAllVersions(String intentId) {
        String sql = """
            SELECT * FROM trade_intents
            WHERE intent_id = ?
            ORDER BY version ASC
            """;

        List<TradeIntent> intents = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, intentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    intents.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find trade intent versions {}: {}", intentId, e.getMessage());
            throw new RuntimeException("Failed to find trade intent versions", e);
        }
        return intents;
    }

    @Override
    public Optional<TradeIntent> findByIdAndVersion(String intentId, int version) {
        String sql = """
            SELECT * FROM trade_intents
            WHERE intent_id = ? AND version = ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, intentId);
            ps.setInt(2, version);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find trade intent {} version {}: {}", intentId, version, e.getMessage());
            throw new RuntimeException("Failed to find trade intent version", e);
        }
        return Optional.empty();
    }

    private TradeIntent mapRow(ResultSet rs) throws Exception {
        Timestamp deletedTs = rs.getTimestamp("deleted_at");
        Instant deletedAt = deletedTs != null ? deletedTs.toInstant() : null;

        Timestamp validatedTs = rs.getTimestamp("validated_at");
        Instant validatedAt = validatedTs != null ? validatedTs.toInstant() : null;

        Timestamp executedTs = rs.getTimestamp("executed_at");
        Instant executedAt = executedTs != null ? executedTs.toInstant() : null;

        String errorsJson = rs.getString("validation_errors");
        List<TradeIntent.ValidationError> validationErrors = errorsJson != null
            ? objectMapper.readValue(errorsJson, new TypeReference<List<TradeIntent.ValidationError>>() {})
            : List.of();

        return new TradeIntent(
            rs.getString("intent_id"),
            rs.getString("signal_id"),
            rs.getString("user_id"),
            rs.getString("broker_id"),
            rs.getString("user_broker_id"),
            rs.getBoolean("validation_passed"),
            validationErrors,
            (Integer) rs.getObject("calculated_qty"),
            rs.getBigDecimal("calculated_value"),
            rs.getString("order_type"),
            rs.getBigDecimal("limit_price"),
            rs.getString("product_type"),
            rs.getBigDecimal("log_impact"),
            rs.getBigDecimal("portfolio_exposure_after"),
            IntentStatus.valueOf(rs.getString("status")),
            rs.getString("order_id"),
            rs.getString("trade_id"),
            rs.getTimestamp("created_at").toInstant(),
            validatedAt,
            executedAt,
            rs.getString("error_code"),
            rs.getString("error_message"),
            deletedAt,
            rs.getInt("version")
        );
    }

    private void setIntOrNull(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value != null) {
            ps.setInt(index, value);
        } else {
            ps.setNull(index, Types.INTEGER);
        }
    }

    private void setBigDecimalOrNull(PreparedStatement ps, int index, BigDecimal value) throws SQLException {
        if (value != null) {
            ps.setBigDecimal(index, value);
        } else {
            ps.setNull(index, Types.NUMERIC);
        }
    }

    private void setTimestampOrNull(PreparedStatement ps, int index, Instant value) throws SQLException {
        if (value != null) {
            ps.setTimestamp(index, Timestamp.from(value));
        } else {
            ps.setNull(index, Types.TIMESTAMP);
        }
    }
}
