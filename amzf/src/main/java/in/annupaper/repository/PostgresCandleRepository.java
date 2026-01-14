package in.annupaper.repository;

import in.annupaper.domain.data.TimeframeType;
import in.annupaper.domain.data.Candle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * PostgreSQL implementation of CandleRepository.
 */
public final class PostgresCandleRepository implements CandleRepository {
    private static final Logger log = LoggerFactory.getLogger(PostgresCandleRepository.class);

    private final DataSource dataSource;

    public PostgresCandleRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void insert(Candle candle) {
        String sql = """
            INSERT INTO candles (symbol, timeframe, ts, open, high, low, close, volume, version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1)
            ON CONFLICT (symbol, timeframe, ts)
            DO UPDATE SET
                open = EXCLUDED.open,
                high = EXCLUDED.high,
                low = EXCLUDED.low,
                close = EXCLUDED.close,
                volume = EXCLUDED.volume
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, candle.symbol());
            ps.setString(2, candle.timeframeType().name());
            ps.setTimestamp(3, Timestamp.from(candle.timestamp()));
            ps.setBigDecimal(4, candle.open());
            ps.setBigDecimal(5, candle.high());
            ps.setBigDecimal(6, candle.low());
            ps.setBigDecimal(7, candle.close());
            ps.setLong(8, candle.volume());

            ps.executeUpdate();

        } catch (SQLException e) {
            log.error("Failed to insert candle: {}", e.getMessage());
            throw new RuntimeException("Failed to insert candle", e);
        }
    }

    @Override
    public void insertBatch(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            return;
        }

        String sql = """
            INSERT INTO candles (symbol, timeframe, ts, open, high, low, close, volume, version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1)
            ON CONFLICT (symbol, timeframe, ts)
            DO UPDATE SET
                open = EXCLUDED.open,
                high = EXCLUDED.high,
                low = EXCLUDED.low,
                close = EXCLUDED.close,
                volume = EXCLUDED.volume
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            conn.setAutoCommit(false);

            for (Candle candle : candles) {
                ps.setString(1, candle.symbol());
                ps.setString(2, candle.timeframeType().name());
                ps.setTimestamp(3, Timestamp.from(candle.timestamp()));
                ps.setBigDecimal(4, candle.open());
                ps.setBigDecimal(5, candle.high());
                ps.setBigDecimal(6, candle.low());
                ps.setBigDecimal(7, candle.close());
                ps.setLong(8, candle.volume());
                ps.addBatch();
            }

            ps.executeBatch();
            conn.commit();

            log.debug("Inserted {} candles", candles.size());

        } catch (SQLException e) {
            log.error("Failed to insert batch: {}", e.getMessage());
            throw new RuntimeException("Failed to insert batch", e);
        }
    }

    @Override
    public List<Candle> findBySymbolAndTimeframe(String symbol, TimeframeType timeframe, Instant from, Instant to) {
        String sql = """
            SELECT id, symbol, timeframe, ts, open, high, low, close, volume, created_at, deleted_at, version
            FROM candles
            WHERE symbol = ? AND timeframe = ? AND ts >= ? AND ts <= ? AND deleted_at IS NULL
            ORDER BY ts ASC
            """;

        List<Candle> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, symbol);
            ps.setString(2, timeframe.name());
            ps.setTimestamp(3, Timestamp.from(from));
            ps.setTimestamp(4, Timestamp.from(to));

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(mapRow(rs));
            }

        } catch (SQLException e) {
            log.error("Failed to find candles: {}", e.getMessage());
            throw new RuntimeException("Failed to find candles", e);
        }

        return result;
    }

    @Override
    public Candle findLatest(String symbol, TimeframeType timeframe) {
        String sql = """
            SELECT id, symbol, timeframe, ts, open, high, low, close, volume, created_at, deleted_at, version
            FROM candles
            WHERE symbol = ? AND timeframe = ? AND deleted_at IS NULL
            ORDER BY ts DESC
            LIMIT 1
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, symbol);
            ps.setString(2, timeframe.name());

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return mapRow(rs);
            }

        } catch (SQLException e) {
            log.error("Failed to find latest candle: {}", e.getMessage());
            throw new RuntimeException("Failed to find latest candle", e);
        }

        return null;
    }

    @Override
    public List<Candle> findAll(String symbol, TimeframeType timeframe, int limit) {
        String sql = """
            SELECT id, symbol, timeframe, ts, open, high, low, close, volume, created_at, deleted_at, version
            FROM candles
            WHERE symbol = ? AND timeframe = ? AND deleted_at IS NULL
            ORDER BY ts DESC
            LIMIT ?
            """;

        List<Candle> result = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, symbol);
            ps.setString(2, timeframe.name());
            ps.setInt(3, limit);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(mapRow(rs));
            }

        } catch (SQLException e) {
            log.error("Failed to find all candles: {}", e.getMessage());
            throw new RuntimeException("Failed to find all candles", e);
        }

        return result;
    }

    @Override
    public boolean exists(String symbol, TimeframeType timeframe) {
        String sql = """
            SELECT EXISTS(
                SELECT 1 FROM candles
                WHERE symbol = ? AND timeframe = ? AND deleted_at IS NULL
            )
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, symbol);
            ps.setString(2, timeframe.name());

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getBoolean(1);
            }

        } catch (SQLException e) {
            log.error("Failed to check existence: {}", e.getMessage());
            throw new RuntimeException("Failed to check existence", e);
        }

        return false;
    }

    // @Override
    // public int deleteOlderThan(Instant cutoff) {
    //     String sql = "DELETE FROM candles WHERE ts < ?";
    //
    //     try (Connection conn = dataSource.getConnection();
    //          PreparedStatement ps = conn.prepareStatement(sql)) {
    //
    //         ps.setTimestamp(1, Timestamp.from(cutoff));
    //         int deleted = ps.executeUpdate();
    //
    //         log.info("Deleted {} old candles", deleted);
    //         return deleted;
    //
    //     } catch (SQLException e) {
    //         log.error("Failed to delete old candles: {}", e.getMessage());
    //         throw new RuntimeException("Failed to delete old candles", e);
    //     }
    // }
    @Override
    public int deleteOlderThan(Instant cutoff) {
        // Soft delete old candles
        String sql = "UPDATE candles SET deleted_at = NOW() WHERE ts < ? AND deleted_at IS NULL";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setTimestamp(1, Timestamp.from(cutoff));
            int deleted = ps.executeUpdate();

            log.info("Soft deleted {} old candles", deleted);
            return deleted;

        } catch (SQLException e) {
            log.error("Failed to soft delete old candles: {}", e.getMessage());
            throw new RuntimeException("Failed to soft delete old candles", e);
        }
    }

    private Candle mapRow(ResultSet rs) throws SQLException {
        Long id = rs.getLong("id");
        String symbol = rs.getString("symbol");
        TimeframeType timeframe = TimeframeType.valueOf(rs.getString("timeframe"));
        Instant ts = rs.getTimestamp("ts").toInstant();

        Timestamp createdTs = rs.getTimestamp("created_at");
        Instant createdAt = createdTs != null ? createdTs.toInstant() : Instant.now();

        Timestamp deletedTs = rs.getTimestamp("deleted_at");
        Instant deletedAt = deletedTs != null ? deletedTs.toInstant() : null;

        int version = rs.getInt("version");

        return new Candle(
            id, symbol, timeframe, ts,
            rs.getBigDecimal("open"),
            rs.getBigDecimal("high"),
            rs.getBigDecimal("low"),
            rs.getBigDecimal("close"),
            rs.getLong("volume"),
            createdAt, deletedAt, version
        );
    }
}
