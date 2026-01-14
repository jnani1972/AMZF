package in.annupaper.repository;

import in.annupaper.domain.data.Watchlist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class PostgresWatchlistRepository implements WatchlistRepository {
    private static final Logger log = LoggerFactory.getLogger(PostgresWatchlistRepository.class);

    private final DataSource dataSource;

    public PostgresWatchlistRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Watchlist> findByUserBrokerId(String userBrokerId) {
        String sql = "SELECT * FROM watchlist WHERE user_broker_id = ? AND deleted_at IS NULL ORDER BY added_at";
        List<Watchlist> watchlists = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userBrokerId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    watchlists.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Error finding watchlist by user-broker: {}", e.getMessage(), e);
        }

        return watchlists;
    }

    @Override
    public List<Watchlist> findByUserId(String userId) {
        String sql = """
            SELECT w.* FROM watchlist w
            JOIN user_brokers ub ON w.user_broker_id = ub.user_broker_id AND ub.deleted_at IS NULL
            WHERE ub.user_id = ?
              AND w.deleted_at IS NULL
            ORDER BY w.added_at
            """;
        List<Watchlist> watchlists = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    watchlists.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Error finding watchlist by user: {}", e.getMessage(), e);
        }

        return watchlists;
    }

    // @Override
    // public void insert(Watchlist watchlist) {
    //     String sql = """
    //         INSERT INTO watchlist (user_broker_id, symbol, enabled, version)
    //         VALUES (?, ?, ?, 1)
    //         """;
    //
    //     try (Connection conn = dataSource.getConnection();
    //          PreparedStatement ps = conn.prepareStatement(sql)) {
    //
    //         ps.setString(1, watchlist.userBrokerId());
    //         ps.setString(2, watchlist.symbol());
    //         ps.setBoolean(3, watchlist.enabled());
    //
    //         ps.executeUpdate();
    //         log.info("Watchlist entry inserted: {} for {} version 1", watchlist.symbol(), watchlist.userBrokerId());
    //
    //     } catch (Exception e) {
    //         log.error("Error inserting watchlist: {}", e.getMessage(), e);
    //         throw new RuntimeException("Failed to insert watchlist", e);
    //     }
    // }

    @Override
    public void insert(Watchlist watchlist) {
        // Use ON CONFLICT to "resurrect" soft-deleted records instead of failing
        // Strip -EQ suffix from symbol
        String cleanSymbol = watchlist.symbol().replace("-EQ", "");

        String sql = """
            INSERT INTO watchlist (user_broker_id, symbol, lot_size, tick_size, is_custom, enabled, last_synced_at, version)
            VALUES (?, ?, ?, ?, ?, ?, ?, 1)
            ON CONFLICT (user_broker_id, symbol)
            DO UPDATE SET
                enabled = EXCLUDED.enabled,
                is_custom = EXCLUDED.is_custom,
                lot_size = EXCLUDED.lot_size,
                tick_size = EXCLUDED.tick_size,
                last_synced_at = EXCLUDED.last_synced_at,
                deleted_at = NULL,
                updated_at = NOW(),
                version = watchlist.version + 1
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, watchlist.userBrokerId());
            ps.setString(2, cleanSymbol);
            if (watchlist.lotSize() != null) {
                ps.setInt(3, watchlist.lotSize());
            } else {
                ps.setNull(3, java.sql.Types.INTEGER);
            }
            if (watchlist.tickSize() != null) {
                ps.setBigDecimal(4, watchlist.tickSize());
            } else {
                ps.setNull(4, java.sql.Types.DECIMAL);
            }
            ps.setBoolean(5, watchlist.isCustom());
            ps.setBoolean(6, watchlist.enabled());
            ps.setTimestamp(7, watchlist.lastSyncedAt() != null ? Timestamp.from(watchlist.lastSyncedAt()) : null);

            ps.executeUpdate();
            log.info("Watchlist entry upserted: {} for {} (lot_size={}, tick_size={}, custom={}) version 1",
                cleanSymbol, watchlist.userBrokerId(), watchlist.lotSize(), watchlist.tickSize(), watchlist.isCustom());

        } catch (Exception e) {
            log.error("Error upserting watchlist: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upsert watchlist", e);
        }
    }

    // @Override
    // public void delete(Long id) {
    //     String sql = "DELETE FROM watchlist WHERE id = ?";
    //
    //     try (Connection conn = dataSource.getConnection();
    //          PreparedStatement ps = conn.prepareStatement(sql)) {
    //
    //         ps.setLong(1, id);
    //         ps.executeUpdate();
    //         log.info("Watchlist entry deleted: {}", id);
    //
    //     } catch (Exception e) {
    //         log.error("Error deleting watchlist: {}", e.getMessage(), e);
    //         throw new RuntimeException("Failed to delete watchlist", e);
    //     }
    // }
    @Override
    public void delete(Long id) {
        // Soft delete: mark as deleted
        String queryVersionSql = "SELECT version FROM watchlist WHERE id = ? AND deleted_at IS NULL";
        String softDeleteSql = "UPDATE watchlist SET deleted_at = NOW() WHERE id = ? AND version = ?";

        try (Connection conn = dataSource.getConnection()) {
            int currentVersion;
            try (PreparedStatement ps = conn.prepareStatement(queryVersionSql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        log.warn("Watchlist entry not found or already deleted: {}", id);
                        return;
                    }
                    currentVersion = rs.getInt("version");
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(softDeleteSql)) {
                ps.setLong(1, id);
                ps.setInt(2, currentVersion);
                ps.executeUpdate();
                log.info("Watchlist entry soft deleted: {} version {}", id, currentVersion);
            }

        } catch (Exception e) {
            log.error("Error soft deleting watchlist: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to delete watchlist", e);
        }
    }

    @Override
    public void toggleEnabled(Long id, boolean enabled) {
        String sql = "UPDATE watchlist SET enabled = ?, updated_at = NOW() WHERE id = ? AND deleted_at IS NULL";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setBoolean(1, enabled);
            ps.setLong(2, id);
            int updated = ps.executeUpdate();
            if (updated > 0) {
                log.info("Watchlist entry toggled: {} enabled={}", id, enabled);
            } else {
                log.warn("Watchlist entry not found or already deleted: {}", id);
            }

        } catch (Exception e) {
            log.error("Error toggling watchlist: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to toggle watchlist", e);
        }
    }


    // private Watchlist mapRow(ResultSet rs) throws Exception {
    //     Long id = rs.getLong("id");
    //     String userBrokerId = rs.getString("user_broker_id");
    //     String symbol = rs.getString("symbol");
    //     boolean enabled = rs.getBoolean("enabled");
    //
    //     Timestamp addedTs = rs.getTimestamp("added_at");
    //     Timestamp updatedTs = rs.getTimestamp("updated_at");
    //     Instant addedAt = addedTs != null ? addedTs.toInstant() : Instant.now();
    //     Instant updatedAt = updatedTs != null ? updatedTs.toInstant() : Instant.now();
    //
    //     Timestamp deletedTs = rs.getTimestamp("deleted_at");
    //     Instant deletedAt = deletedTs != null ? deletedTs.toInstant() : null;
    //
    //     int version = rs.getInt("version");
    //
    //     return new Watchlist(id, userBrokerId, symbol, enabled, addedAt, updatedAt, deletedAt, version);
    // }

    @Override
    public void updateLastPrice(String symbol, java.math.BigDecimal lastPrice, Instant lastTickTime) {
        // Update last_price and last_tick_time for all watchlist entries with this symbol
        String sql = "UPDATE watchlist SET last_price = ?, last_tick_time = ?, updated_at = NOW() WHERE symbol = ? AND deleted_at IS NULL";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setBigDecimal(1, lastPrice);
            ps.setTimestamp(2, lastTickTime != null ? Timestamp.from(lastTickTime) : null);
            ps.setString(3, symbol);

            int updated = ps.executeUpdate();
            if (updated > 0) {
                log.debug("Updated last price for symbol {} to {} at {} ({} entries)", symbol, lastPrice, lastTickTime, updated);
            }

        } catch (Exception e) {
            log.error("Error updating last price for symbol {}: {}", symbol, e.getMessage(), e);
            throw new RuntimeException("Failed to update last price for symbol: " + symbol, e);
        }
    }

    private Watchlist mapRow(ResultSet rs) throws Exception {
        Long id = rs.getLong("id");
        String userBrokerId = rs.getString("user_broker_id");
        String symbol = rs.getString("symbol");

        // Get lot_size from database
        Integer lotSize = rs.getObject("lot_size", Integer.class);

        // Get tick_size from database
        java.math.BigDecimal tickSize = rs.getBigDecimal("tick_size");

        boolean isCustom = rs.getBoolean("is_custom");
        boolean enabled = rs.getBoolean("enabled");

        Timestamp addedTs = rs.getTimestamp("added_at");
        Timestamp updatedTs = rs.getTimestamp("updated_at");
        Instant addedAt = addedTs != null ? addedTs.toInstant() : Instant.now();
        Instant updatedAt = updatedTs != null ? updatedTs.toInstant() : Instant.now();

        Timestamp lastSyncedTs = rs.getTimestamp("last_synced_at");
        Instant lastSyncedAt = lastSyncedTs != null ? lastSyncedTs.toInstant() : null;

        Timestamp deletedTs = rs.getTimestamp("deleted_at");
        Instant deletedAt = deletedTs != null ? deletedTs.toInstant() : null;

        int version = rs.getInt("version");

        // FIX: Add last_price and last_tick_time fields for Market Watch LTP display
        java.math.BigDecimal lastPrice = rs.getBigDecimal("last_price");
        Timestamp lastTickTs = rs.getTimestamp("last_tick_time");
        Instant lastTickTime = lastTickTs != null ? lastTickTs.toInstant() : null;

        // OLD: return new Watchlist(id, userBrokerId, symbol, lotSize, tickSize, isCustom, enabled, addedAt, updatedAt, lastSyncedAt, deletedAt, version);
        return new Watchlist(id, userBrokerId, symbol, lotSize, tickSize, isCustom, enabled, addedAt, updatedAt, lastSyncedAt, deletedAt, version, lastPrice, lastTickTime);
    }
}
