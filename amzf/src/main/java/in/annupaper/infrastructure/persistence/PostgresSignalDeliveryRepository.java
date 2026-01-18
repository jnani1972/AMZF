package in.annupaper.infrastructure.persistence;

import in.annupaper.application.port.output.*;

import in.annupaper.domain.model.SignalDelivery;
import in.annupaper.domain.model.DeliveryIndexEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * PostgreSQL implementation of SignalDeliveryRepository.
 *
 * CRITICAL: Uses DB function consume_delivery() for AV-5 atomic consumption.
 */
public final class PostgresSignalDeliveryRepository implements SignalDeliveryRepository {
    private static final Logger log = LoggerFactory.getLogger(PostgresSignalDeliveryRepository.class);

    private final DataSource dataSource;

    public PostgresSignalDeliveryRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public SignalDelivery findById(String deliveryId) {
        String sql = """
                SELECT * FROM signal_deliveries
                WHERE delivery_id = ? AND deleted_at IS NULL
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, deliveryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (Exception e) {
            log.error("Failed to find delivery {}: {}", deliveryId, e.getMessage());
            throw new RuntimeException("Failed to find delivery", e);
        }
        return null;
    }

    @Override
    public SignalDelivery findBySignalAndUserBroker(String signalId, String userBrokerId) {
        String sql = """
                SELECT * FROM signal_deliveries
                WHERE signal_id = ? AND user_broker_id = ? AND deleted_at IS NULL
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, signalId);
            ps.setString(2, userBrokerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (Exception e) {
            log.error("Failed to find delivery for signal {} and user-broker {}: {}",
                    signalId, userBrokerId, e.getMessage());
            throw new RuntimeException("Failed to find delivery", e);
        }
        return null;
    }

    @Override
    public List<SignalDelivery> findByUserId(String userId, String status) {
        String sql = status != null
                ? "SELECT * FROM signal_deliveries WHERE user_id = ? AND status = ? AND deleted_at IS NULL ORDER BY created_at DESC"
                : "SELECT * FROM signal_deliveries WHERE user_id = ? AND deleted_at IS NULL ORDER BY created_at DESC";

        List<SignalDelivery> deliveries = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userId);
            if (status != null) {
                ps.setString(2, status);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    deliveries.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find deliveries for user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to find deliveries", e);
        }
        return deliveries;
    }

    @Override
    public List<SignalDelivery> findBySignalId(String signalId) {
        String sql = """
                SELECT * FROM signal_deliveries
                WHERE signal_id = ? AND deleted_at IS NULL
                ORDER BY created_at ASC
                """;

        List<SignalDelivery> deliveries = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, signalId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    deliveries.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find deliveries for signal {}: {}", signalId, e.getMessage());
            throw new RuntimeException("Failed to find deliveries", e);
        }
        return deliveries;
    }

    @Override
    public List<DeliveryIndexEntry> findAllActiveForIndex() {
        String sql = """
                SELECT delivery_id, signal_id, user_broker_id
                FROM signal_deliveries
                WHERE status IN ('CREATED', 'DELIVERED')
                  AND deleted_at IS NULL
                """;

        List<DeliveryIndexEntry> entries = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                entries.add(new DeliveryIndexEntry(
                        rs.getString("delivery_id"),
                        rs.getString("signal_id"),
                        rs.getString("user_broker_id")));
            }
        } catch (Exception e) {
            log.error("Failed to fetch active deliveries for index: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch deliveries", e);
        }
        return entries;
    }

    @Override
    public List<SignalDelivery> findPendingDeliveries() {
        String sql = """
                SELECT * FROM signal_deliveries
                WHERE status IN ('CREATED', 'DELIVERED')
                  AND deleted_at IS NULL
                ORDER BY created_at ASC
                """;

        List<SignalDelivery> deliveries = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                deliveries.add(mapRow(rs));
            }
        } catch (Exception e) {
            log.error("Failed to find pending deliveries: {}", e.getMessage());
            throw new RuntimeException("Failed to find pending deliveries", e);
        }
        return deliveries;
    }

    @Override
    public void insert(SignalDelivery delivery) {
        String sql = """
                INSERT INTO signal_deliveries (
                    delivery_id, signal_id, user_broker_id, user_id,
                    status, intent_id, rejection_reason, user_action,
                    created_at, delivered_at, consumed_at, user_action_at,
                    updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, delivery.deliveryId());
            ps.setString(2, delivery.signalId());
            ps.setString(3, delivery.userBrokerId());
            ps.setString(4, delivery.userId());
            ps.setString(5, delivery.status());
            ps.setString(6, delivery.intentId());
            ps.setString(7, delivery.rejectionReason());
            ps.setString(8, delivery.userAction());
            ps.setTimestamp(9, Timestamp.from(delivery.createdAt()));
            setTimestampOrNull(ps, 10, delivery.deliveredAt());
            setTimestampOrNull(ps, 11, delivery.consumedAt());
            setTimestampOrNull(ps, 12, delivery.userActionAt());
            ps.setTimestamp(13, Timestamp.from(delivery.updatedAt()));
            ps.setInt(14, delivery.version());

            ps.executeUpdate();
            log.info("Delivery inserted: {} → {} for user-broker {}",
                    delivery.deliveryId(), delivery.signalId(), delivery.userBrokerId());

        } catch (Exception e) {
            log.error("Failed to insert delivery: {}", e.getMessage());
            throw new RuntimeException("Failed to insert delivery", e);
        }
    }

    @Override
    public boolean consumeDelivery(String deliveryId, String intentId) {
        // AV-5 FIX: Use DB function for atomic consumption with optimistic lock
        String sql = "SELECT consume_delivery(?, ?)";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, deliveryId);
            ps.setString(2, intentId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    boolean success = rs.getBoolean(1);
                    if (success) {
                        log.info("✅ Delivery consumed: {} → intent {}", deliveryId, intentId);
                    } else {
                        log.warn("⚠️ Delivery consumption failed (already consumed?): {}", deliveryId);
                    }
                    return success;
                }
            }
        } catch (Exception e) {
            log.error("Failed to consume delivery {}: {}", deliveryId, e.getMessage());
            throw new RuntimeException("Failed to consume delivery", e);
        }

        return false;
    }

    @Override
    public void updateStatus(String deliveryId, String status) {
        String sql = """
                UPDATE signal_deliveries
                SET status = ?, updated_at = NOW()
                WHERE delivery_id = ? AND deleted_at IS NULL
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status);
            ps.setString(2, deliveryId);

            int updated = ps.executeUpdate();
            if (updated > 0) {
                log.debug("Delivery status updated: {} → {}", deliveryId, status);
            }
        } catch (Exception e) {
            log.error("Failed to update delivery status: {}", e.getMessage());
            throw new RuntimeException("Failed to update delivery", e);
        }
    }

    @Override
    public void expireAllForSignal(String signalId) {
        String sql = """
                UPDATE signal_deliveries
                SET status = 'EXPIRED', updated_at = NOW()
                WHERE signal_id = ?
                  AND status IN ('CREATED', 'DELIVERED')
                  AND deleted_at IS NULL
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, signalId);
            int updated = ps.executeUpdate();

            if (updated > 0) {
                log.info("Expired {} deliveries for signal {}", updated, signalId);
            }
        } catch (Exception e) {
            log.error("Failed to expire deliveries for signal {}: {}", signalId, e.getMessage());
            throw new RuntimeException("Failed to expire deliveries", e);
        }
    }

    @Override
    public void cancelAllForSignal(String signalId) {
        String sql = """
                UPDATE signal_deliveries
                SET status = 'CANCELLED', updated_at = NOW()
                WHERE signal_id = ?
                  AND status IN ('CREATED', 'DELIVERED')
                  AND deleted_at IS NULL
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, signalId);
            int updated = ps.executeUpdate();

            if (updated > 0) {
                log.info("Cancelled {} deliveries for signal {}", updated, signalId);
            }
        } catch (Exception e) {
            log.error("Failed to cancel deliveries for signal {}: {}", signalId, e.getMessage());
            throw new RuntimeException("Failed to cancel deliveries", e);
        }
    }

    private SignalDelivery mapRow(ResultSet rs) throws SQLException {
        Timestamp deliveredTs = rs.getTimestamp("delivered_at");
        Timestamp consumedTs = rs.getTimestamp("consumed_at");
        Timestamp userActionTs = rs.getTimestamp("user_action_at");
        Timestamp deletedTs = rs.getTimestamp("deleted_at");

        return new SignalDelivery(
                rs.getString("delivery_id"),
                rs.getString("signal_id"),
                rs.getString("user_broker_id"),
                rs.getString("user_id"),
                rs.getString("status"),
                rs.getString("intent_id"),
                rs.getString("rejection_reason"),
                rs.getString("user_action"),
                rs.getTimestamp("created_at").toInstant(),
                deliveredTs != null ? deliveredTs.toInstant() : null,
                consumedTs != null ? consumedTs.toInstant() : null,
                userActionTs != null ? userActionTs.toInstant() : null,
                rs.getTimestamp("updated_at").toInstant(),
                deletedTs != null ? deletedTs.toInstant() : null,
                rs.getInt("version"));
    }

    private void setTimestampOrNull(PreparedStatement ps, int index, Instant value) throws SQLException {
        if (value != null) {
            ps.setTimestamp(index, Timestamp.from(value));
        } else {
            ps.setNull(index, Types.TIMESTAMP);
        }
    }
}
