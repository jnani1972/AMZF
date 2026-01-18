package in.annupaper.infrastructure.persistence;

import in.annupaper.application.port.output.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.annupaper.domain.model.ExitReason;
import in.annupaper.domain.model.ExitIntentStatus;
import in.annupaper.domain.model.ExitIntent;
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
 * PostgreSQL implementation of ExitIntentRepository.
 *
 * Exit intents track execution qualification and order outcomes.
 * Uses DB function place_exit_order() for atomic APPROVED → PLACED transition.
 */
public final class PostgresExitIntentRepository implements ExitIntentRepository {
    private static final Logger log = LoggerFactory.getLogger(PostgresExitIntentRepository.class);

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public PostgresExitIntentRepository(DataSource dataSource) {
        this.dataSource = dataSource;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Optional<ExitIntent> findById(String exitIntentId) {
        String sql = """
                SELECT * FROM exit_intents
                WHERE exit_intent_id = ? AND deleted_at IS NULL
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, exitIntentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find exit intent {}: {}", exitIntentId, e.getMessage());
            throw new RuntimeException("Failed to find exit intent", e);
        }
        return Optional.empty();
    }

    @Override
    public List<ExitIntent> findByExitSignalId(String exitSignalId) {
        String sql = """
                SELECT * FROM exit_intents
                WHERE exit_signal_id = ? AND deleted_at IS NULL
                ORDER BY created_at DESC
                """;

        List<ExitIntent> intents = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, exitSignalId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    intents.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find exit intents by signal {}: {}", exitSignalId, e.getMessage());
            throw new RuntimeException("Failed to find exit intents", e);
        }
        return intents;
    }

    @Override
    public List<ExitIntent> findByTradeId(String tradeId) {
        String sql = """
                SELECT * FROM exit_intents
                WHERE trade_id = ? AND deleted_at IS NULL
                ORDER BY created_at DESC
                """;

        List<ExitIntent> intents = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, tradeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    intents.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find exit intents by trade {}: {}", tradeId, e.getMessage());
            throw new RuntimeException("Failed to find exit intents", e);
        }
        return intents;
    }

    @Override
    public List<ExitIntent> findByStatus(ExitIntentStatus status) {
        String sql = """
                SELECT * FROM exit_intents
                WHERE status = ? AND deleted_at IS NULL
                ORDER BY created_at DESC
                """;

        List<ExitIntent> intents = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    intents.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find exit intents by status {}: {}", status, e.getMessage());
            throw new RuntimeException("Failed to find exit intents", e);
        }
        return intents;
    }

    @Override
    public List<ExitIntent> findPendingIntents() {
        String sql = """
                SELECT * FROM exit_intents
                WHERE status IN ('PENDING', 'APPROVED')
                  AND deleted_at IS NULL
                ORDER BY created_at ASC
                """;

        List<ExitIntent> intents = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                intents.add(mapRow(rs));
            }
        } catch (Exception e) {
            log.error("Failed to find pending exit intents: {}", e.getMessage());
            throw new RuntimeException("Failed to find pending exit intents", e);
        }
        return intents;
    }

    @Override
    public List<ExitIntent> findFailedIntents() {
        String sql = """
                SELECT * FROM exit_intents
                WHERE status = 'FAILED'
                  AND deleted_at IS NULL
                ORDER BY retry_count ASC, created_at ASC
                """;

        List<ExitIntent> intents = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                intents.add(mapRow(rs));
            }
        } catch (Exception e) {
            log.error("Failed to find failed exit intents: {}", e.getMessage());
            throw new RuntimeException("Failed to find failed exit intents", e);
        }
        return intents;
    }

    @Override
    public Optional<ExitIntent> findByBrokerOrderId(String brokerOrderId) {
        String sql = """
                SELECT * FROM exit_intents
                WHERE broker_order_id = ? AND deleted_at IS NULL
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, brokerOrderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find exit intent by broker order {}: {}", brokerOrderId, e.getMessage());
            throw new RuntimeException("Failed to find exit intent", e);
        }
        return Optional.empty();
    }

    @Override
    public void insert(ExitIntent intent) {
        String sql = """
                INSERT INTO exit_intents (
                    exit_intent_id, exit_signal_id, trade_id, user_broker_id,
                    exit_reason, episode_id,
                    status, validation_passed, validation_errors,
                    calculated_qty, order_type, limit_price, product_type,
                    broker_order_id, placed_at, filled_at, cancelled_at,
                    error_code, error_message, retry_count,
                    created_at, updated_at, version
                ) VALUES (
                    ?, ?, ?, ?,
                    ?, ?,
                    ?, ?, ?::text[],
                    ?, ?, ?, ?,
                    ?, ?, ?, ?,
                    ?, ?, ?,
                    ?, ?, ?
                )
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, intent.exitIntentId());
            ps.setString(2, intent.exitSignalId());
            ps.setString(3, intent.tradeId());
            ps.setString(4, intent.userBrokerId());

            ps.setString(5, intent.exitReason().name());
            ps.setInt(6, intent.episodeId());

            ps.setString(7, intent.status().name());
            ps.setBoolean(8, intent.validationPassed());
            ps.setArray(9, conn.createArrayOf("text", intent.validationErrors().toArray()));

            setIntOrNull(ps, 10, intent.calculatedQty());
            ps.setString(11, intent.orderType());
            setBigDecimalOrNull(ps, 12, intent.limitPrice());
            ps.setString(13, intent.productType());

            ps.setString(14, intent.brokerOrderId());
            setTimestampOrNull(ps, 15, intent.placedAt());
            setTimestampOrNull(ps, 16, intent.filledAt());
            setTimestampOrNull(ps, 17, intent.cancelledAt());

            ps.setString(18, intent.errorCode());
            ps.setString(19, intent.errorMessage());
            ps.setInt(20, intent.retryCount());

            ps.setTimestamp(21, Timestamp.from(intent.createdAt()));
            ps.setTimestamp(22, Timestamp.from(intent.updatedAt()));
            ps.setInt(23, intent.version());

            ps.executeUpdate();
            log.info("Exit intent inserted: {} for trade {} (reason: {}, episode: {})",
                    intent.exitIntentId(), intent.tradeId(), intent.exitReason(), intent.episodeId());

        } catch (Exception e) {
            log.error("Failed to insert exit intent: {}", e.getMessage());
            throw new RuntimeException("Failed to insert exit intent", e);
        }
    }

    @Override
    public void updateStatus(String exitIntentId, ExitIntentStatus status) {
        String sql = """
                UPDATE exit_intents
                SET status = ?, updated_at = NOW()
                WHERE exit_intent_id = ? AND deleted_at IS NULL
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status.name());
            ps.setString(2, exitIntentId);

            int updated = ps.executeUpdate();
            if (updated > 0) {
                log.debug("Exit intent status updated: {} → {}", exitIntentId, status);
            }
        } catch (Exception e) {
            log.error("Failed to update exit intent status: {}", e.getMessage());
            throw new RuntimeException("Failed to update exit intent", e);
        }
    }

    @Override
    public void updateStatus(String exitIntentId, String status, String errorCode, String errorMessage) {
        String sql = """
                UPDATE exit_intents
                SET status = ?,
                    error_code = ?,
                    error_message = ?,
                    updated_at = NOW()
                WHERE exit_intent_id = ? AND deleted_at IS NULL
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status);
            ps.setString(2, errorCode);
            ps.setString(3, errorMessage);
            ps.setString(4, exitIntentId);

            int updated = ps.executeUpdate();
            if (updated > 0) {
                log.info("Exit intent status updated: {} → {} (error: {} - {})",
                        exitIntentId, status, errorCode, errorMessage);
            }
        } catch (Exception e) {
            log.error("Failed to update exit intent status with error details: {}", e.getMessage());
            throw new RuntimeException("Failed to update exit intent", e);
        }
    }

    @Override
    public void updateBrokerOrderId(String exitIntentId, String brokerOrderId) {
        String sql = """
                UPDATE exit_intents
                SET broker_order_id = ?,
                    updated_at = NOW()
                WHERE exit_intent_id = ? AND deleted_at IS NULL
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, brokerOrderId);
            ps.setString(2, exitIntentId);

            int updated = ps.executeUpdate();
            if (updated > 0) {
                log.info("Exit intent broker order ID updated: {} → brokerOrderId={}",
                        exitIntentId, brokerOrderId);
            }
        } catch (Exception e) {
            log.error("Failed to update exit intent broker order ID: {}", e.getMessage());
            throw new RuntimeException("Failed to update broker order ID", e);
        }
    }

    @Override
    public boolean placeExitOrder(String exitIntentId, String brokerOrderId) {
        // Use DB function for atomic APPROVED → PLACED transition
        String sql = "SELECT place_exit_order(?, ?)";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, exitIntentId);
            ps.setString(2, brokerOrderId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    boolean success = rs.getBoolean(1);
                    if (success) {
                        log.info("✅ Exit order placed: intent {} → broker order {}", exitIntentId, brokerOrderId);
                    } else {
                        log.warn("⚠️ Exit order placement failed (already placed?): {}", exitIntentId);
                    }
                    return success;
                }
            }
        } catch (Exception e) {
            log.error("Failed to place exit order {}: {}", exitIntentId, e.getMessage());
            throw new RuntimeException("Failed to place exit order", e);
        }

        return false;
    }

    @Override
    public void markFilled(String exitIntentId) {
        String sql = """
                UPDATE exit_intents
                SET status = 'FILLED',
                    filled_at = NOW(),
                    updated_at = NOW()
                WHERE exit_intent_id = ? AND deleted_at IS NULL
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, exitIntentId);
            int updated = ps.executeUpdate();

            if (updated > 0) {
                log.info("✅ Exit intent filled: {}", exitIntentId);
            }
        } catch (Exception e) {
            log.error("Failed to mark exit intent as filled: {}", e.getMessage());
            throw new RuntimeException("Failed to mark filled", e);
        }
    }

    @Override
    public void markFailed(String exitIntentId, String errorCode, String errorMessage) {
        String sql = """
                UPDATE exit_intents
                SET status = 'FAILED',
                    error_code = ?,
                    error_message = ?,
                    updated_at = NOW()
                WHERE exit_intent_id = ? AND deleted_at IS NULL
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, errorCode);
            ps.setString(2, errorMessage);
            ps.setString(3, exitIntentId);

            int updated = ps.executeUpdate();
            if (updated > 0) {
                log.warn("❌ Exit intent failed: {} ({})", exitIntentId, errorCode);
            }
        } catch (Exception e) {
            log.error("Failed to mark exit intent as failed: {}", e.getMessage());
            throw new RuntimeException("Failed to mark failed", e);
        }
    }

    @Override
    public void markCancelled(String exitIntentId) {
        String sql = """
                UPDATE exit_intents
                SET status = 'CANCELLED',
                    cancelled_at = NOW(),
                    updated_at = NOW()
                WHERE exit_intent_id = ? AND deleted_at IS NULL
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, exitIntentId);
            int updated = ps.executeUpdate();

            if (updated > 0) {
                log.info("Exit intent cancelled: {}", exitIntentId);
            }
        } catch (Exception e) {
            log.error("Failed to mark exit intent as cancelled: {}", e.getMessage());
            throw new RuntimeException("Failed to mark cancelled", e);
        }
    }

    @Override
    public int incrementRetryCount(String exitIntentId) {
        String sql = """
                UPDATE exit_intents
                SET retry_count = retry_count + 1,
                    updated_at = NOW()
                WHERE exit_intent_id = ? AND deleted_at IS NULL
                RETURNING retry_count
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, exitIntentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int newCount = rs.getInt(1);
                    log.debug("Exit intent retry count incremented: {} → {}", exitIntentId, newCount);
                    return newCount;
                }
            }
        } catch (Exception e) {
            log.error("Failed to increment retry count: {}", e.getMessage());
            throw new RuntimeException("Failed to increment retry count", e);
        }

        return 0;
    }

    private ExitIntent mapRow(ResultSet rs) throws Exception {
        Timestamp placedTs = rs.getTimestamp("placed_at");
        Timestamp filledTs = rs.getTimestamp("filled_at");
        Timestamp cancelledTs = rs.getTimestamp("cancelled_at");
        Timestamp deletedTs = rs.getTimestamp("deleted_at");

        // Parse validation errors array
        Array errorsArray = rs.getArray("validation_errors");
        List<String> validationErrors = new ArrayList<>();
        if (errorsArray != null) {
            String[] errors = (String[]) errorsArray.getArray();
            validationErrors = List.of(errors);
        }

        return new ExitIntent(
                rs.getString("exit_intent_id"),
                rs.getString("exit_signal_id"),
                rs.getString("trade_id"),
                rs.getString("user_broker_id"),

                ExitReason.valueOf(rs.getString("exit_reason")),
                rs.getInt("episode_id"),

                ExitIntentStatus.valueOf(rs.getString("status")),
                rs.getBoolean("validation_passed"),
                validationErrors,

                (Integer) rs.getObject("calculated_qty"),
                rs.getString("order_type"),
                rs.getBigDecimal("limit_price"),
                rs.getString("product_type"),

                rs.getString("broker_order_id"),
                placedTs != null ? placedTs.toInstant() : null,
                filledTs != null ? filledTs.toInstant() : null,
                cancelledTs != null ? cancelledTs.toInstant() : null,

                rs.getString("error_code"),
                rs.getString("error_message"),
                rs.getInt("retry_count"),

                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                deletedTs != null ? deletedTs.toInstant() : null,
                rs.getInt("version"));
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

    // ========================================================================
    // MONITORING METHODS
    // ========================================================================

    @Override
    public long countStuckExitIntents(int thresholdMinutes) {
        String sql = """
                SELECT COUNT(*)
                FROM exit_intents
                WHERE status IN ('PENDING', 'APPROVED', 'PLACED')
                  AND created_at < NOW() - INTERVAL '1 minute' * ?
                  AND deleted_at IS NULL
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, thresholdMinutes);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (Exception e) {
            log.error("Failed to count stuck exit intents: {}", e.getMessage());
            throw new RuntimeException("Failed to count stuck exit intents", e);
        }
        return 0;
    }

    @Override
    public List<ExitIntent> findStuckExitIntents(int thresholdMinutes) {
        String sql = """
                SELECT * FROM exit_intents
                WHERE status IN ('PENDING', 'APPROVED', 'PLACED')
                  AND created_at < NOW() - INTERVAL '1 minute' * ?
                  AND deleted_at IS NULL
                ORDER BY created_at ASC
                """;

        List<ExitIntent> intents = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, thresholdMinutes);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    intents.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find stuck exit intents: {}", e.getMessage());
            throw new RuntimeException("Failed to find stuck exit intents", e);
        }
        return intents;
    }

    @Override
    public long countPendingExitIntents() {
        String sql = """
                SELECT COUNT(*)
                FROM exit_intents
                WHERE status IN ('PENDING', 'APPROVED', 'PLACED')
                  AND deleted_at IS NULL
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception e) {
            log.error("Failed to count pending exit intents: {}", e.getMessage());
            throw new RuntimeException("Failed to count pending exit intents", e);
        }
        return 0;
    }
}
