package in.annupaper.infrastructure.persistence;

import in.annupaper.domain.repository.*;

import in.annupaper.domain.trade.Direction;
import in.annupaper.domain.trade.ExitReason;
import in.annupaper.domain.signal.ExitSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * PostgreSQL implementation of ExitSignalRepository.
 *
 * CRITICAL: Uses DB function generate_exit_episode() for AV-2 race-free episode generation.
 */
public final class PostgresExitSignalRepository implements ExitSignalRepository {
    private static final Logger log = LoggerFactory.getLogger(PostgresExitSignalRepository.class);

    private final DataSource dataSource;

    public PostgresExitSignalRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public ExitSignal findById(String exitSignalId) {
        String sql = """
            SELECT * FROM exit_signals
            WHERE exit_signal_id = ? AND deleted_at IS NULL
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, exitSignalId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (Exception e) {
            log.error("Failed to find exit signal {}: {}", exitSignalId, e.getMessage());
            throw new RuntimeException("Failed to find exit signal", e);
        }
        return null;
    }

    @Override
    public List<ExitSignal> findByTradeId(String tradeId) {
        String sql = """
            SELECT * FROM exit_signals
            WHERE trade_id = ? AND deleted_at IS NULL
            ORDER BY episode_id DESC
            """;

        List<ExitSignal> signals = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, tradeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    signals.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find exit signals for trade {}: {}", tradeId, e.getMessage());
            throw new RuntimeException("Failed to find exit signals", e);
        }
        return signals;
    }

    @Override
    public List<ExitSignal> findByTradeAndReason(String tradeId, String exitReason) {
        String sql = """
            SELECT * FROM exit_signals
            WHERE trade_id = ? AND exit_reason = ? AND deleted_at IS NULL
            ORDER BY episode_id DESC
            """;

        List<ExitSignal> signals = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, tradeId);
            ps.setString(2, exitReason);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    signals.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find exit signals for trade {} reason {}: {}",
                tradeId, exitReason, e.getMessage());
            throw new RuntimeException("Failed to find exit signals", e);
        }
        return signals;
    }

    @Override
    public ExitSignal findLatestByTradeAndReason(String tradeId, String exitReason) {
        String sql = """
            SELECT * FROM exit_signals
            WHERE trade_id = ? AND exit_reason = ? AND deleted_at IS NULL
            ORDER BY episode_id DESC
            LIMIT 1
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, tradeId);
            ps.setString(2, exitReason);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (Exception e) {
            log.error("Failed to find latest exit signal for trade {} reason {}: {}",
                tradeId, exitReason, e.getMessage());
            throw new RuntimeException("Failed to find exit signal", e);
        }
        return null;
    }

    @Override
    public int generateEpisode(String tradeId, String exitReason) {
        // AV-2 FIX: Use DB function with pessimistic lock
        String sql = "SELECT generate_exit_episode(?, ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, tradeId);
            ps.setString(2, exitReason);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int episode = rs.getInt(1);
                    log.debug("Generated exit episode: {} {} → episode {}",
                        tradeId, exitReason, episode);
                    return episode;
                }
            }
        } catch (Exception e) {
            log.error("Failed to generate episode for trade {} reason {}: {}",
                tradeId, exitReason, e.getMessage());
            throw new RuntimeException("Failed to generate episode", e);
        }

        throw new RuntimeException("Episode generation returned no result");
    }

    @Override
    public void insert(ExitSignal exitSignal) {
        String sql = """
            INSERT INTO exit_signals (
                exit_signal_id, trade_id, signal_id, symbol, direction, exit_reason,
                episode_id, exit_price_at_detection, brick_movement, favorable_movement,
                highest_since_entry, lowest_since_entry, trailing_stop_price, trailing_active,
                status, detected_at, confirmed_at, published_at, executed_at, cancelled_at,
                last_rearm_at, created_at, updated_at, version
            ) VALUES (
                ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?,
                ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?,
                ?, NOW(), NOW(), 1
            )
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, exitSignal.exitSignalId());
            ps.setString(2, exitSignal.tradeId());
            ps.setString(3, exitSignal.signalId());
            ps.setString(4, exitSignal.symbol());
            ps.setString(5, exitSignal.direction().name());
            ps.setString(6, exitSignal.exitReason().name());
            ps.setInt(7, 1);  // episode_id - should come from generateEpisode()
            ps.setBigDecimal(8, exitSignal.exitPrice());
            setBigDecimalOrNull(ps, 9, exitSignal.brickMovement());
            setBigDecimalOrNull(ps, 10, exitSignal.favorableMovement());
            setBigDecimalOrNull(ps, 11, null);  // highest_since_entry - not in current model
            setBigDecimalOrNull(ps, 12, null);  // lowest_since_entry - not in current model
            setBigDecimalOrNull(ps, 13, null);  // trailing_stop_price - not in current model
            ps.setBoolean(14, false);  // trailing_active
            ps.setString(15, "DETECTED");  // status
            ps.setTimestamp(16, Timestamp.from(exitSignal.timestamp()));
            ps.setNull(17, Types.TIMESTAMP);  // confirmed_at
            ps.setNull(18, Types.TIMESTAMP);  // published_at
            ps.setNull(19, Types.TIMESTAMP);  // executed_at
            ps.setNull(20, Types.TIMESTAMP);  // cancelled_at
            ps.setNull(21, Types.TIMESTAMP);  // last_rearm_at

            ps.executeUpdate();
            log.info("Exit signal inserted: {} for trade {} ({})",
                exitSignal.exitSignalId(), exitSignal.tradeId(), exitSignal.exitReason());

        } catch (Exception e) {
            log.error("Failed to insert exit signal: {}", e.getMessage());
            throw new RuntimeException("Failed to insert exit signal", e);
        }
    }

    @Override
    public void updateStatus(String exitSignalId, String status) {
        String sql = """
            UPDATE exit_signals
            SET status = ?, updated_at = NOW()
            WHERE exit_signal_id = ? AND deleted_at IS NULL
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status);
            ps.setString(2, exitSignalId);

            int updated = ps.executeUpdate();
            if (updated > 0) {
                log.debug("Exit signal status updated: {} → {}", exitSignalId, status);
            }
        } catch (Exception e) {
            log.error("Failed to update exit signal status: {}", e.getMessage());
            throw new RuntimeException("Failed to update exit signal", e);
        }
    }

    @Override
    public void cancel(String exitSignalId) {
        String sql = """
            UPDATE exit_signals
            SET status = 'CANCELLED', cancelled_at = NOW(), updated_at = NOW()
            WHERE exit_signal_id = ? AND deleted_at IS NULL
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, exitSignalId);

            int updated = ps.executeUpdate();
            if (updated > 0) {
                log.info("Exit signal cancelled: {}", exitSignalId);
            }
        } catch (Exception e) {
            log.error("Failed to cancel exit signal: {}", e.getMessage());
            throw new RuntimeException("Failed to cancel exit signal", e);
        }
    }

    private ExitSignal mapRow(ResultSet rs) throws SQLException {
        return new ExitSignal(
            rs.getString("exit_signal_id"),
            rs.getString("trade_id"),
            rs.getString("signal_id"),
            rs.getString("symbol"),
            Direction.valueOf(rs.getString("direction")),
            ExitReason.valueOf(rs.getString("exit_reason")),
            rs.getBigDecimal("exit_price_at_detection"),
            rs.getBigDecimal("brick_movement"),
            rs.getBigDecimal("favorable_movement"),
            rs.getTimestamp("detected_at").toInstant()
        );
    }

    private void setBigDecimalOrNull(PreparedStatement ps, int index, BigDecimal value) throws SQLException {
        if (value != null) {
            ps.setBigDecimal(index, value);
        } else {
            ps.setNull(index, Types.NUMERIC);
        }
    }
}
