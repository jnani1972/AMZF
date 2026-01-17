package in.annupaper.repository;

import in.annupaper.domain.trade.Trade;
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
 * PostgreSQL implementation of TradeRepository with immutable audit trail.
 */
public final class PostgresTradeRepository implements TradeRepository {
    private static final Logger log = LoggerFactory.getLogger(PostgresTradeRepository.class);

    private final DataSource dataSource;

    public PostgresTradeRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Trade> findAll() {
        String sql = """
            SELECT * FROM trades
            WHERE deleted_at IS NULL
            ORDER BY entry_timestamp DESC
            """;

        List<Trade> trades = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                trades.add(mapRow(rs));
            }
        } catch (Exception e) {
            log.error("Failed to find all trades: {}", e.getMessage());
            throw new RuntimeException("Failed to find trades", e);
        }
        return trades;
    }

    @Override
    public Optional<Trade> findById(String tradeId) {
        String sql = """
            SELECT * FROM trades
            WHERE trade_id = ? AND deleted_at IS NULL
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, tradeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find trade {}: {}", tradeId, e.getMessage());
            throw new RuntimeException("Failed to find trade", e);
        }
        return Optional.empty();
    }

    @Override
    public List<Trade> findByPortfolioId(String portfolioId) {
        String sql = """
            SELECT * FROM trades
            WHERE portfolio_id = ? AND deleted_at IS NULL
            ORDER BY entry_timestamp DESC
            """;

        List<Trade> trades = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, portfolioId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    trades.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find trades by portfolio {}: {}", portfolioId, e.getMessage());
            throw new RuntimeException("Failed to find trades", e);
        }
        return trades;
    }

    @Override
    public List<Trade> findByUserId(String userId) {
        String sql = """
            SELECT * FROM trades
            WHERE user_id = ? AND deleted_at IS NULL
            ORDER BY entry_timestamp DESC
            """;

        List<Trade> trades = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    trades.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find trades by user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to find trades", e);
        }
        return trades;
    }

    @Override
    public List<Trade> findBySymbol(String symbol) {
        String sql = """
            SELECT * FROM trades
            WHERE symbol = ? AND deleted_at IS NULL
            ORDER BY entry_timestamp DESC
            """;

        List<Trade> trades = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, symbol);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    trades.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find trades by symbol {}: {}", symbol, e.getMessage());
            throw new RuntimeException("Failed to find trades", e);
        }
        return trades;
    }

    @Override
    public List<Trade> findByUserAndSymbol(String userId, String symbol) {
        String sql = """
            SELECT * FROM trades
            WHERE user_id = ? AND symbol = ? AND deleted_at IS NULL
            ORDER BY entry_timestamp DESC
            """;

        List<Trade> trades = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userId);
            ps.setString(2, symbol);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    trades.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find trades by user {} and symbol {}: {}", userId, symbol, e.getMessage());
            throw new RuntimeException("Failed to find trades", e);
        }
        return trades;
    }

    @Override
    public List<Trade> findByStatus(String status) {
        String sql = """
            SELECT * FROM trades
            WHERE status = ? AND deleted_at IS NULL
            ORDER BY entry_timestamp DESC
            """;

        List<Trade> trades = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    trades.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find trades by status {}: {}", status, e.getMessage());
            throw new RuntimeException("Failed to find trades", e);
        }
        return trades;
    }

    @Override
    public List<Trade> findBySignalId(String signalId) {
        String sql = """
            SELECT * FROM trades
            WHERE signal_id = ? AND deleted_at IS NULL
            ORDER BY entry_timestamp DESC
            """;

        List<Trade> trades = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, signalId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    trades.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find trades by signal {}: {}", signalId, e.getMessage());
            throw new RuntimeException("Failed to find trades", e);
        }
        return trades;
    }

    @Override
    public List<Trade> findOpenTrades() {
        String sql = """
            SELECT * FROM trades
            WHERE status = 'OPEN' AND deleted_at IS NULL
            ORDER BY entry_timestamp DESC
            """;

        List<Trade> trades = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                trades.add(mapRow(rs));
            }
        } catch (Exception e) {
            log.error("Failed to find open trades: {}", e.getMessage());
            throw new RuntimeException("Failed to find open trades", e);
        }
        return trades;
    }

    @Override
    public List<Trade> findOpenTradesByUserId(String userId) {
        String sql = """
            SELECT * FROM trades
            WHERE user_id = ? AND status = 'OPEN' AND deleted_at IS NULL
            ORDER BY entry_timestamp DESC
            """;

        List<Trade> trades = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    trades.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find open trades by user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to find open trades", e);
        }
        return trades;
    }

    @Override
    public void insert(Trade trade) {
        String sql = """
            INSERT INTO trades (
                trade_id, portfolio_id, user_id, broker_id, user_broker_id,
                signal_id, intent_id, symbol, direction, trade_number,
                entry_price, entry_qty, entry_value, entry_timestamp, product_type,
                entry_htf_zone, entry_itf_zone, entry_ltf_zone,
                entry_confluence_type, entry_confluence_score,
                entry_htf_low, entry_htf_high, entry_itf_low, entry_itf_high,
                entry_ltf_low, entry_ltf_high, entry_effective_floor, entry_effective_ceiling,
                log_loss_at_floor, max_log_loss_allowed,
                exit_min_profit_price, exit_target_price, exit_stretch_price, exit_primary_price,
                status, current_price, current_log_return, unrealized_pnl,
                trailing_active, trailing_highest_price, trailing_stop_price,
                exit_price, exit_timestamp, exit_trigger, exit_order_id,
                realized_pnl, realized_log_return, holding_days,
                broker_order_id, broker_trade_id,
                created_at, updated_at, version
            ) VALUES (
                ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?,
                ?, ?, ?,
                ?, ?,
                ?, ?, ?, ?,
                ?, ?, ?, ?,
                ?, ?,
                ?, ?, ?, ?,
                ?, ?, ?, ?,
                ?, ?, ?,
                ?, ?, ?, ?,
                ?, ?, ?,
                ?, ?,
                ?, ?, 1
            )
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, trade.tradeId());
            ps.setString(2, trade.portfolioId());
            ps.setString(3, trade.userId());
            ps.setString(4, trade.brokerId());
            ps.setString(5, trade.userBrokerId());
            ps.setString(6, trade.signalId());
            ps.setString(7, trade.intentId());
            ps.setString(8, trade.symbol());
            ps.setString(9, trade.direction());
            ps.setInt(10, trade.tradeNumber());

            setBigDecimalOrNull(ps, 11, trade.entryPrice());
            ps.setInt(12, trade.entryQty());
            setBigDecimalOrNull(ps, 13, trade.entryValue());
            ps.setTimestamp(14, Timestamp.from(trade.entryTimestamp()));
            ps.setString(15, trade.productType());

            setIntOrNull(ps, 16, trade.entryHtfZone());
            setIntOrNull(ps, 17, trade.entryItfZone());
            setIntOrNull(ps, 18, trade.entryLtfZone());
            ps.setString(19, trade.entryConfluenceType());
            setBigDecimalOrNull(ps, 20, trade.entryConfluenceScore());

            setBigDecimalOrNull(ps, 21, trade.entryHtfLow());
            setBigDecimalOrNull(ps, 22, trade.entryHtfHigh());
            setBigDecimalOrNull(ps, 23, trade.entryItfLow());
            setBigDecimalOrNull(ps, 24, trade.entryItfHigh());
            setBigDecimalOrNull(ps, 25, trade.entryLtfLow());
            setBigDecimalOrNull(ps, 26, trade.entryLtfHigh());
            setBigDecimalOrNull(ps, 27, trade.entryEffectiveFloor());
            setBigDecimalOrNull(ps, 28, trade.entryEffectiveCeiling());

            setBigDecimalOrNull(ps, 29, trade.logLossAtFloor());
            setBigDecimalOrNull(ps, 30, trade.maxLogLossAllowed());

            setBigDecimalOrNull(ps, 31, trade.exitMinProfitPrice());
            setBigDecimalOrNull(ps, 32, trade.exitTargetPrice());
            setBigDecimalOrNull(ps, 33, trade.exitStretchPrice());
            setBigDecimalOrNull(ps, 34, trade.exitPrimaryPrice());

            ps.setString(35, trade.status());
            setBigDecimalOrNull(ps, 36, trade.currentPrice());
            setBigDecimalOrNull(ps, 37, trade.currentLogReturn());
            setBigDecimalOrNull(ps, 38, trade.unrealizedPnl());

            ps.setBoolean(39, trade.trailingActive());
            setBigDecimalOrNull(ps, 40, trade.trailingHighestPrice());
            setBigDecimalOrNull(ps, 41, trade.trailingStopPrice());

            setBigDecimalOrNull(ps, 42, trade.exitPrice());
            setTimestampOrNull(ps, 43, trade.exitTimestamp());
            ps.setString(44, trade.exitTrigger());
            ps.setString(45, trade.exitOrderId());

            setBigDecimalOrNull(ps, 46, trade.realizedPnl());
            setBigDecimalOrNull(ps, 47, trade.realizedLogReturn());
            setIntOrNull(ps, 48, trade.holdingDays());

            ps.setString(49, trade.brokerOrderId());
            ps.setString(50, trade.brokerTradeId());

            ps.setTimestamp(51, Timestamp.from(trade.createdAt()));
            ps.setTimestamp(52, Timestamp.from(trade.updatedAt()));

            ps.executeUpdate();
            log.info("Trade inserted: {}", trade.tradeId());

        } catch (Exception e) {
            log.error("Failed to insert trade: {}", e.getMessage());
            throw new RuntimeException("Failed to insert trade", e);
        }
    }

    @Override
    public void update(Trade trade) {
        // Immutable update: soft delete old version, insert new version

        String queryVersionSql = """
            SELECT version FROM trades
            WHERE trade_id = ? AND deleted_at IS NULL
            """;

        String softDeleteSql = """
            UPDATE trades
            SET deleted_at = NOW()
            WHERE trade_id = ? AND version = ?
            """;

        String insertSql = """
            INSERT INTO trades (
                trade_id, portfolio_id, user_id, broker_id, user_broker_id,
                signal_id, intent_id, symbol, direction, trade_number,
                entry_price, entry_qty, entry_value, entry_timestamp, product_type,
                entry_htf_zone, entry_itf_zone, entry_ltf_zone,
                entry_confluence_type, entry_confluence_score,
                entry_htf_low, entry_htf_high, entry_itf_low, entry_itf_high,
                entry_ltf_low, entry_ltf_high, entry_effective_floor, entry_effective_ceiling,
                log_loss_at_floor, max_log_loss_allowed,
                exit_min_profit_price, exit_target_price, exit_stretch_price, exit_primary_price,
                status, current_price, current_log_return, unrealized_pnl,
                trailing_active, trailing_highest_price, trailing_stop_price,
                exit_price, exit_timestamp, exit_trigger, exit_order_id,
                realized_pnl, realized_log_return, holding_days,
                broker_order_id, broker_trade_id,
                created_at, updated_at, version
            ) VALUES (
                ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?,
                ?, ?, ?,
                ?, ?,
                ?, ?, ?, ?,
                ?, ?, ?, ?,
                ?, ?,
                ?, ?, ?, ?,
                ?, ?, ?, ?,
                ?, ?, ?,
                ?, ?, ?, ?,
                ?, ?, ?,
                ?, ?,
                ?, ?, ?
            )
            """;

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            // Step 1: Get current version
            int currentVersion;
            try (PreparedStatement ps = conn.prepareStatement(queryVersionSql)) {
                ps.setString(1, trade.tradeId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new RuntimeException("Trade not found: " + trade.tradeId());
                    }
                    currentVersion = rs.getInt("version");
                }
            }

            // Step 2: Soft delete current version
            try (PreparedStatement ps = conn.prepareStatement(softDeleteSql)) {
                ps.setString(1, trade.tradeId());
                ps.setInt(2, currentVersion);
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    throw new RuntimeException("Failed to soft delete trade: " + trade.tradeId());
                }
            }

            // Step 3: Insert new version
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, trade.tradeId());
                ps.setString(2, trade.portfolioId());
                ps.setString(3, trade.userId());
                ps.setString(4, trade.brokerId());
                ps.setString(5, trade.userBrokerId());
                ps.setString(6, trade.signalId());
                ps.setString(7, trade.intentId());
                ps.setString(8, trade.symbol());
                ps.setInt(9, trade.tradeNumber());

                setBigDecimalOrNull(ps, 10, trade.entryPrice());
                ps.setInt(11, trade.entryQty());
                setBigDecimalOrNull(ps, 12, trade.entryValue());
                ps.setTimestamp(13, Timestamp.from(trade.entryTimestamp()));
                ps.setString(14, trade.productType());

                setIntOrNull(ps, 15, trade.entryHtfZone());
                setIntOrNull(ps, 16, trade.entryItfZone());
                setIntOrNull(ps, 17, trade.entryLtfZone());
                ps.setString(18, trade.entryConfluenceType());
                setBigDecimalOrNull(ps, 19, trade.entryConfluenceScore());

                setBigDecimalOrNull(ps, 20, trade.entryHtfLow());
                setBigDecimalOrNull(ps, 21, trade.entryHtfHigh());
                setBigDecimalOrNull(ps, 22, trade.entryItfLow());
                setBigDecimalOrNull(ps, 23, trade.entryItfHigh());
                setBigDecimalOrNull(ps, 24, trade.entryLtfLow());
                setBigDecimalOrNull(ps, 25, trade.entryLtfHigh());
                setBigDecimalOrNull(ps, 26, trade.entryEffectiveFloor());
                setBigDecimalOrNull(ps, 27, trade.entryEffectiveCeiling());

                setBigDecimalOrNull(ps, 28, trade.logLossAtFloor());
                setBigDecimalOrNull(ps, 29, trade.maxLogLossAllowed());

                setBigDecimalOrNull(ps, 30, trade.exitMinProfitPrice());
                setBigDecimalOrNull(ps, 31, trade.exitTargetPrice());
                setBigDecimalOrNull(ps, 32, trade.exitStretchPrice());
                setBigDecimalOrNull(ps, 33, trade.exitPrimaryPrice());

                ps.setString(34, trade.status());
                setBigDecimalOrNull(ps, 35, trade.currentPrice());
                setBigDecimalOrNull(ps, 36, trade.currentLogReturn());
                setBigDecimalOrNull(ps, 37, trade.unrealizedPnl());

                ps.setBoolean(38, trade.trailingActive());
                setBigDecimalOrNull(ps, 39, trade.trailingHighestPrice());
                setBigDecimalOrNull(ps, 40, trade.trailingStopPrice());

                setBigDecimalOrNull(ps, 41, trade.exitPrice());
                setTimestampOrNull(ps, 42, trade.exitTimestamp());
                ps.setString(43, trade.exitTrigger());
                ps.setString(44, trade.exitOrderId());

                setBigDecimalOrNull(ps, 45, trade.realizedPnl());
                setBigDecimalOrNull(ps, 46, trade.realizedLogReturn());
                setIntOrNull(ps, 47, trade.holdingDays());

                ps.setString(48, trade.brokerOrderId());
                ps.setString(49, trade.brokerTradeId());

                ps.setTimestamp(50, Timestamp.from(trade.createdAt()));
                ps.setTimestamp(51, Timestamp.from(trade.updatedAt()));
                ps.setInt(52, currentVersion + 1);

                ps.executeUpdate();
            }

            conn.commit();
            log.info("Trade updated: {} version {} → {}", trade.tradeId(), currentVersion, currentVersion + 1);

        } catch (Exception e) {
            log.error("Failed to update trade: {}", e.getMessage());
            throw new RuntimeException("Failed to update trade", e);
        }
    }

    @Override
    public void delete(String tradeId) {
        // Soft delete: mark as deleted

        String queryVersionSql = """
            SELECT version FROM trades
            WHERE trade_id = ? AND deleted_at IS NULL
            """;

        String softDeleteSql = """
            UPDATE trades
            SET deleted_at = NOW()
            WHERE trade_id = ? AND version = ?
            """;

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            // Get current version
            int currentVersion;
            try (PreparedStatement ps = conn.prepareStatement(queryVersionSql)) {
                ps.setString(1, tradeId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new RuntimeException("Trade not found: " + tradeId);
                    }
                    currentVersion = rs.getInt("version");
                }
            }

            // Soft delete
            try (PreparedStatement ps = conn.prepareStatement(softDeleteSql)) {
                ps.setString(1, tradeId);
                ps.setInt(2, currentVersion);
                int deleted = ps.executeUpdate();
                if (deleted == 0) {
                    throw new RuntimeException("Failed to delete trade: " + tradeId);
                }
            }

            conn.commit();
            log.info("Trade soft-deleted: {} version {}", tradeId, currentVersion);

        } catch (Exception e) {
            log.error("Failed to delete trade: {}", e.getMessage());
            throw new RuntimeException("Failed to delete trade", e);
        }
    }

    @Override
    public List<Trade> findAllVersions(String tradeId) {
        String sql = """
            SELECT * FROM trades
            WHERE trade_id = ?
            ORDER BY version ASC
            """;

        List<Trade> trades = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, tradeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    trades.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find trade versions {}: {}", tradeId, e.getMessage());
            throw new RuntimeException("Failed to find trade versions", e);
        }
        return trades;
    }

    @Override
    public Optional<Trade> findByIdAndVersion(String tradeId, int version) {
        String sql = """
            SELECT * FROM trades
            WHERE trade_id = ? AND version = ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, tradeId);
            ps.setInt(2, version);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find trade {} version {}: {}", tradeId, version, e.getMessage());
            throw new RuntimeException("Failed to find trade version", e);
        }
        return Optional.empty();
    }

    private Trade mapRow(ResultSet rs) throws Exception {
        Timestamp deletedTs = rs.getTimestamp("deleted_at");
        Instant deletedAt = deletedTs != null ? deletedTs.toInstant() : null;

        Timestamp exitTs = rs.getTimestamp("exit_timestamp");
        Instant exitTimestamp = exitTs != null ? exitTs.toInstant() : null;

        Timestamp lastBrokerUpdateTs = rs.getTimestamp("last_broker_update_at");
        Instant lastBrokerUpdateAt = lastBrokerUpdateTs != null ? lastBrokerUpdateTs.toInstant() : null;

        return new Trade(
            rs.getString("trade_id"),
            rs.getString("portfolio_id"),
            rs.getString("user_id"),
            rs.getString("broker_id"),
            rs.getString("user_broker_id"),
            rs.getString("signal_id"),
            rs.getString("intent_id"),
            rs.getString("symbol"),
            rs.getString("direction"),
            rs.getInt("trade_number"),
            rs.getBigDecimal("entry_price"),
            rs.getInt("entry_qty"),
            rs.getBigDecimal("entry_value"),
            rs.getTimestamp("entry_timestamp").toInstant(),
            rs.getString("product_type"),
            (Integer) rs.getObject("entry_htf_zone"),
            (Integer) rs.getObject("entry_itf_zone"),
            (Integer) rs.getObject("entry_ltf_zone"),
            rs.getString("entry_confluence_type"),
            rs.getBigDecimal("entry_confluence_score"),
            rs.getBigDecimal("entry_htf_low"),
            rs.getBigDecimal("entry_htf_high"),
            rs.getBigDecimal("entry_itf_low"),
            rs.getBigDecimal("entry_itf_high"),
            rs.getBigDecimal("entry_ltf_low"),
            rs.getBigDecimal("entry_ltf_high"),
            rs.getBigDecimal("entry_effective_floor"),
            rs.getBigDecimal("entry_effective_ceiling"),
            rs.getBigDecimal("log_loss_at_floor"),
            rs.getBigDecimal("max_log_loss_allowed"),
            rs.getBigDecimal("exit_min_profit_price"),
            rs.getBigDecimal("exit_target_price"),
            rs.getBigDecimal("exit_stretch_price"),
            rs.getBigDecimal("exit_primary_price"),
            rs.getString("status"),
            rs.getBigDecimal("current_price"),
            rs.getBigDecimal("current_log_return"),
            rs.getBigDecimal("unrealized_pnl"),
            rs.getBoolean("trailing_active"),
            rs.getBigDecimal("trailing_highest_price"),
            rs.getBigDecimal("trailing_stop_price"),
            rs.getBigDecimal("exit_price"),
            exitTimestamp,
            rs.getString("exit_trigger"),
            rs.getString("exit_order_id"),
            rs.getBigDecimal("realized_pnl"),
            rs.getBigDecimal("realized_log_return"),
            (Integer) rs.getObject("holding_days"),
            rs.getString("broker_order_id"),
            rs.getString("broker_trade_id"),
            rs.getString("client_order_id"),
            lastBrokerUpdateAt,
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
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

    @Override
    public Trade upsert(Trade trade) {
        // ✅ P0-B: Idempotent upsert using intent_id as unique key
        // If intent_id exists, update; otherwise insert

        String sql = """
            INSERT INTO trades (
                trade_id, portfolio_id, user_id, broker_id, user_broker_id,
                signal_id, intent_id, client_order_id, symbol, direction, trade_number,
                entry_price, entry_qty, entry_value, entry_timestamp, product_type,
                entry_htf_zone, entry_itf_zone, entry_ltf_zone,
                entry_confluence_type, entry_confluence_score,
                entry_htf_low, entry_htf_high, entry_itf_low, entry_itf_high,
                entry_ltf_low, entry_ltf_high, entry_effective_floor, entry_effective_ceiling,
                log_loss_at_floor, max_log_loss_allowed,
                exit_min_profit_price, exit_target_price, exit_stretch_price, exit_primary_price,
                status, current_price, current_log_return, unrealized_pnl,
                trailing_active, trailing_highest_price, trailing_stop_price,
                exit_price, exit_timestamp, exit_trigger, exit_order_id,
                realized_pnl, realized_log_return, holding_days,
                broker_order_id, broker_trade_id,
                created_at, updated_at, last_broker_update_at, version
            ) VALUES (
                ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?,
                ?, ?, ?,
                ?, ?,
                ?, ?, ?, ?,
                ?, ?, ?, ?,
                ?, ?,
                ?, ?, ?, ?,
                ?, ?, ?, ?,
                ?, ?, ?,
                ?, ?, ?, ?,
                ?, ?, ?,
                ?, ?,
                NOW(), NOW(), NOW(), 1
            )
            ON CONFLICT (intent_id) DO UPDATE SET
                broker_order_id = EXCLUDED.broker_order_id,
                client_order_id = EXCLUDED.client_order_id,
                filled_qty = COALESCE(EXCLUDED.entry_qty, trades.entry_qty),
                status = EXCLUDED.status,
                current_price = COALESCE(EXCLUDED.current_price, trades.current_price),
                current_log_return = COALESCE(EXCLUDED.current_log_return, trades.current_log_return),
                unrealized_pnl = COALESCE(EXCLUDED.unrealized_pnl, trades.unrealized_pnl),
                trailing_active = EXCLUDED.trailing_active,
                trailing_highest_price = COALESCE(EXCLUDED.trailing_highest_price, trades.trailing_highest_price),
                trailing_stop_price = COALESCE(EXCLUDED.trailing_stop_price, trades.trailing_stop_price),
                exit_price = COALESCE(EXCLUDED.exit_price, trades.exit_price),
                exit_timestamp = COALESCE(EXCLUDED.exit_timestamp, trades.exit_timestamp),
                exit_trigger = COALESCE(EXCLUDED.exit_trigger, trades.exit_trigger),
                exit_order_id = COALESCE(EXCLUDED.exit_order_id, trades.exit_order_id),
                realized_pnl = COALESCE(EXCLUDED.realized_pnl, trades.realized_pnl),
                realized_log_return = COALESCE(EXCLUDED.realized_log_return, trades.realized_log_return),
                holding_days = COALESCE(EXCLUDED.holding_days, trades.holding_days),
                broker_trade_id = COALESCE(EXCLUDED.broker_trade_id, trades.broker_trade_id),
                last_broker_update_at = NOW(),
                updated_at = NOW()
            RETURNING *
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int idx = 1;
            ps.setString(idx++, trade.tradeId());
            ps.setString(idx++, trade.portfolioId());
            ps.setString(idx++, trade.userId());
            ps.setString(idx++, trade.brokerId());
            ps.setString(idx++, trade.userBrokerId());
            ps.setString(idx++, trade.signalId());
            ps.setString(idx++, trade.intentId());
            ps.setString(idx++, trade.intentId());  // client_order_id = intent_id
            ps.setString(idx++, trade.symbol());
            ps.setString(idx++, trade.direction());
            ps.setInt(idx++, trade.tradeNumber());

            setBigDecimalOrNull(ps, idx++, trade.entryPrice());
            ps.setInt(idx++, trade.entryQty());
            setBigDecimalOrNull(ps, idx++, trade.entryValue());
            ps.setTimestamp(idx++, Timestamp.from(trade.entryTimestamp()));
            ps.setString(idx++, trade.productType());

            setIntOrNull(ps, idx++, trade.entryHtfZone());
            setIntOrNull(ps, idx++, trade.entryItfZone());
            setIntOrNull(ps, idx++, trade.entryLtfZone());
            ps.setString(idx++, trade.entryConfluenceType());
            setBigDecimalOrNull(ps, idx++, trade.entryConfluenceScore());

            setBigDecimalOrNull(ps, idx++, trade.entryHtfLow());
            setBigDecimalOrNull(ps, idx++, trade.entryHtfHigh());
            setBigDecimalOrNull(ps, idx++, trade.entryItfLow());
            setBigDecimalOrNull(ps, idx++, trade.entryItfHigh());
            setBigDecimalOrNull(ps, idx++, trade.entryLtfLow());
            setBigDecimalOrNull(ps, idx++, trade.entryLtfHigh());
            setBigDecimalOrNull(ps, idx++, trade.entryEffectiveFloor());
            setBigDecimalOrNull(ps, idx++, trade.entryEffectiveCeiling());

            setBigDecimalOrNull(ps, idx++, trade.logLossAtFloor());
            setBigDecimalOrNull(ps, idx++, trade.maxLogLossAllowed());

            setBigDecimalOrNull(ps, idx++, trade.exitMinProfitPrice());
            setBigDecimalOrNull(ps, idx++, trade.exitTargetPrice());
            setBigDecimalOrNull(ps, idx++, trade.exitStretchPrice());
            setBigDecimalOrNull(ps, idx++, trade.exitPrimaryPrice());

            ps.setString(idx++, trade.status());
            setBigDecimalOrNull(ps, idx++, trade.currentPrice());
            setBigDecimalOrNull(ps, idx++, trade.currentLogReturn());
            setBigDecimalOrNull(ps, idx++, trade.unrealizedPnl());

            ps.setBoolean(idx++, trade.trailingActive());
            setBigDecimalOrNull(ps, idx++, trade.trailingHighestPrice());
            setBigDecimalOrNull(ps, idx++, trade.trailingStopPrice());

            setBigDecimalOrNull(ps, idx++, trade.exitPrice());
            setTimestampOrNull(ps, idx++, trade.exitTimestamp());
            ps.setString(idx++, trade.exitTrigger());
            ps.setString(idx++, trade.exitOrderId());

            setBigDecimalOrNull(ps, idx++, trade.realizedPnl());
            setBigDecimalOrNull(ps, idx++, trade.realizedLogReturn());
            setIntOrNull(ps, idx++, trade.holdingDays());

            ps.setString(idx++, trade.brokerOrderId());
            ps.setString(idx++, trade.brokerTradeId());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Trade result = mapRow(rs);
                    log.info("Trade upserted: {} (intent_id={})", result.tradeId(), result.intentId());
                    return result;
                }
            }

            throw new RuntimeException("Upsert failed to return result");

        } catch (Exception e) {
            log.error("Failed to upsert trade: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upsert trade", e);
        }
    }

    @Override
    public Trade findByIntentId(String intentId) {
        String sql = """
            SELECT * FROM trades
            WHERE intent_id = ? AND deleted_at IS NULL
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, intentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (Exception e) {
            log.error("Failed to find trade by intent_id {}: {}", intentId, e.getMessage());
            throw new RuntimeException("Failed to find trade by intent_id", e);
        }
        return null;
    }

    @Override
    public Trade findByBrokerOrderId(String brokerOrderId) {
        String sql = """
            SELECT * FROM trades
            WHERE broker_order_id = ? AND deleted_at IS NULL
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, brokerOrderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (Exception e) {
            log.error("Failed to find trade by broker_order_id {}: {}", brokerOrderId, e.getMessage());
            throw new RuntimeException("Failed to find trade by broker_order_id", e);
        }
        return null;
    }

    /**
     * ✅ P0-E: Mark trade as REJECTED by intent ID.
     *
     * Single-writer pattern enforcement:
     * - Trade row created first with status=CREATED
     * - If broker rejects order, mark as REJECTED (this method)
     * - If broker accepts, reconciler updates to PENDING → OPEN
     *
     * Uses UPDATE (not insert) to ensure single writer.
     */
    @Override
    public boolean markRejectedByIntentId(String intentId, String errorCode, String errorMessage) {
        String sql = """
            UPDATE trades
            SET status = 'REJECTED',
                exit_trigger = ?,
                updated_at = NOW()
            WHERE intent_id = ?
              AND deleted_at IS NULL
              AND status = 'CREATED'
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            // exit_trigger stores the rejection reason (error code or message)
            String rejectionReason = errorCode != null ? errorCode : "BROKER_REJECTED";
            ps.setString(1, rejectionReason);
            ps.setString(2, intentId);

            int rowsUpdated = ps.executeUpdate();

            if (rowsUpdated > 0) {
                log.info("✅ P0-E: Trade marked as REJECTED for intent {} (reason: {})",
                    intentId, rejectionReason);
                return true;
            } else {
                log.warn("⚠️ P0-E: No CREATED trade found for intent {} to mark as REJECTED", intentId);
                return false;
            }

        } catch (Exception e) {
            log.error("Failed to mark trade as rejected for intent {}: {}", intentId, e.getMessage());
            throw new RuntimeException("Failed to mark trade as rejected", e);
        }
    }

    @Override
    public void updateExitOrderPlaced(String tradeId, String exitOrderId, Instant placedAt) {
        String sql = """
            UPDATE trades
            SET exit_order_id = ?,
                status = 'EXITING',
                updated_at = NOW()
            WHERE trade_id = ? AND deleted_at IS NULL
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, exitOrderId);
            ps.setString(2, tradeId);

            int updated = ps.executeUpdate();
            if (updated > 0) {
                log.info("✅ Trade exit order placed: {} → exitOrderId={}, status=EXITING",
                    tradeId, exitOrderId);
            } else {
                log.warn("⚠️ No active trade found for trade_id {} to update exit order", tradeId);
            }
        } catch (Exception e) {
            log.error("Failed to update trade {} with exit order ID: {}", tradeId, e.getMessage());
            throw new RuntimeException("Failed to update exit order placed", e);
        }
    }

    // ========================================================================
    // MONITORING METHODS
    // ========================================================================

    @Override
    public long countOpenTrades() {
        String sql = """
            SELECT COUNT(*)
            FROM trades
            WHERE status = 'OPEN'
              AND deleted_at IS NULL
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception e) {
            log.error("Failed to count open trades: {}", e.getMessage());
            throw new RuntimeException("Failed to count open trades", e);
        }
        return 0;
    }

    @Override
    public long countClosedTradesToday() {
        String sql = """
            SELECT COUNT(*)
            FROM trades
            WHERE status = 'CLOSED'
              AND exit_timestamp >= CURRENT_DATE
              AND deleted_at IS NULL
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception e) {
            log.error("Failed to count closed trades today: {}", e.getMessage());
            throw new RuntimeException("Failed to count closed trades today", e);
        }
        return 0;
    }

    @Override
    public java.util.Map<String, Object> getTradeHealthMetrics() {
        String sql = """
            SELECT
                COUNT(*) as total_open_trades,
                SUM(CASE WHEN direction = 'BUY' THEN entry_qty ELSE 0 END) as long_positions,
                SUM(CASE WHEN direction = 'SELL' THEN entry_qty ELSE 0 END) as short_positions,
                SUM(entry_qty * entry_price) as total_exposure_value,
                AVG(EXTRACT(EPOCH FROM (NOW() - entry_timestamp)) / 3600) as avg_holding_hours
            FROM trades
            WHERE status = 'OPEN'
              AND deleted_at IS NULL
            """;

        java.util.Map<String, Object> metrics = new java.util.HashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                metrics.put("total_open_trades", rs.getInt("total_open_trades"));
                metrics.put("long_positions", rs.getInt("long_positions"));
                metrics.put("short_positions", rs.getInt("short_positions"));
                metrics.put("total_exposure_value", rs.getBigDecimal("total_exposure_value"));
                metrics.put("avg_holding_hours", rs.getDouble("avg_holding_hours"));
            }
        } catch (Exception e) {
            log.error("Failed to get trade health metrics: {}", e.getMessage());
            throw new RuntimeException("Failed to get trade health metrics", e);
        }
        return metrics;
    }

    @Override
    public java.util.Map<String, Object> getDailyPerformanceMetrics() {
        String sql = """
            SELECT
                COUNT(*) as trades_closed,
                COUNT(CASE WHEN realized_pnl > 0 THEN 1 END) as winning_trades,
                COUNT(CASE WHEN realized_pnl < 0 THEN 1 END) as losing_trades,
                ROUND(100.0 * COUNT(CASE WHEN realized_pnl > 0 THEN 1 END) / NULLIF(COUNT(*), 0), 2) as win_rate_percent,
                SUM(realized_pnl) as total_pnl,
                AVG(realized_pnl) as avg_pnl_per_trade,
                MAX(realized_pnl) as best_trade,
                MIN(realized_pnl) as worst_trade
            FROM trades
            WHERE status = 'CLOSED'
              AND exit_timestamp >= CURRENT_DATE
              AND deleted_at IS NULL
            """;

        java.util.Map<String, Object> metrics = new java.util.HashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                int tradesClosed = rs.getInt("trades_closed");
                if (tradesClosed > 0) {
                    metrics.put("trades_closed", tradesClosed);
                    metrics.put("winning_trades", rs.getInt("winning_trades"));
                    metrics.put("losing_trades", rs.getInt("losing_trades"));
                    metrics.put("win_rate_percent", rs.getDouble("win_rate_percent"));
                    metrics.put("total_pnl", rs.getBigDecimal("total_pnl"));
                    metrics.put("avg_pnl_per_trade", rs.getBigDecimal("avg_pnl_per_trade"));
                    metrics.put("best_trade", rs.getBigDecimal("best_trade"));
                    metrics.put("worst_trade", rs.getBigDecimal("worst_trade"));
                }
            }
        } catch (Exception e) {
            log.error("Failed to get daily performance metrics: {}", e.getMessage());
            throw new RuntimeException("Failed to get daily performance metrics", e);
        }
        return metrics;
    }
}
