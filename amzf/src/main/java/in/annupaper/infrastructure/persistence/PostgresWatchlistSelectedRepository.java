package in.annupaper.infrastructure.persistence;

import in.annupaper.application.port.output.*;

import com.zaxxer.hikari.HikariDataSource;
import in.annupaper.domain.model.WatchlistSelected;
import in.annupaper.domain.model.WatchlistSelectedSymbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * PostgreSQL implementation of WatchlistSelectedRepository.
 */
public class PostgresWatchlistSelectedRepository implements WatchlistSelectedRepository {

    private static final Logger log = LoggerFactory.getLogger(PostgresWatchlistSelectedRepository.class);
    private final HikariDataSource dataSource;

    public PostgresWatchlistSelectedRepository(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<WatchlistSelected> findAllActive() {
        String sql = "SELECT selected_id, name, source_template_id, description, enabled, " +
                "created_at, updated_at, deleted_at, version " +
                "FROM watchlist_selected " +
                "WHERE deleted_at IS NULL AND enabled = true " +
                "ORDER BY name ASC";

        List<WatchlistSelected> selected = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                selected.add(mapSelected(rs));
            }

        } catch (SQLException e) {
            log.error("Error finding active selected watchlists", e);
            throw new RuntimeException("Database error", e);
        }

        return selected;
    }

    @Override
    public Optional<WatchlistSelected> findById(String selectedId) {
        String sql = "SELECT selected_id, name, source_template_id, description, enabled, " +
                "created_at, updated_at, deleted_at, version " +
                "FROM watchlist_selected " +
                "WHERE selected_id = ?";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, selectedId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapSelected(rs));
                }
            }

        } catch (SQLException e) {
            log.error("Error finding selected watchlist by id: {}", selectedId, e);
            throw new RuntimeException("Database error", e);
        }

        return Optional.empty();
    }

    @Override
    public List<WatchlistSelectedSymbol> findSymbolsBySelectedId(String selectedId) {
        String sql = "SELECT id, selected_id, symbol, display_order, created_at " +
                "FROM watchlist_selected_symbols " +
                "WHERE selected_id = ? " +
                "ORDER BY display_order ASC, symbol ASC";

        List<WatchlistSelectedSymbol> symbols = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, selectedId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    symbols.add(mapSelectedSymbol(rs));
                }
            }

        } catch (SQLException e) {
            log.error("Error finding symbols for selected watchlist: {}", selectedId, e);
            throw new RuntimeException("Database error", e);
        }

        return symbols;
    }

    @Override
    public List<String> findMergedDefaultSymbols() {
        // Level 3: Merge all symbols from all active selected watchlists (distinct,
        // sorted)
        String sql = "SELECT DISTINCT symbol " +
                "FROM watchlist_selected_symbols wss " +
                "INNER JOIN watchlist_selected ws ON wss.selected_id = ws.selected_id " +
                "WHERE ws.deleted_at IS NULL AND ws.enabled = true " +
                "ORDER BY symbol ASC";

        List<String> symbols = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                symbols.add(rs.getString("symbol"));
            }

        } catch (SQLException e) {
            log.error("Error finding merged default symbols", e);
            throw new RuntimeException("Database error", e);
        }

        return symbols;
    }

    @Override
    public void insert(WatchlistSelected selected) {
        String sql = "INSERT INTO watchlist_selected " +
                "(selected_id, name, source_template_id, description, enabled, " +
                "created_at, updated_at, version) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, selected.selectedId());
            stmt.setString(2, selected.name());
            stmt.setString(3, selected.sourceTemplateId());
            stmt.setString(4, selected.description());
            stmt.setBoolean(5, selected.enabled());
            stmt.setTimestamp(6, Timestamp.from(selected.createdAt()));
            stmt.setTimestamp(7, Timestamp.from(selected.updatedAt()));
            stmt.setInt(8, selected.version());

            stmt.executeUpdate();
            log.info("Inserted selected watchlist: {}", selected.selectedId());

        } catch (SQLException e) {
            log.error("Error inserting selected watchlist: {}", selected.selectedId(), e);
            throw new RuntimeException("Database error", e);
        }
    }

    @Override
    public void update(WatchlistSelected selected) {
        String sql = "UPDATE watchlist_selected SET " +
                "name = ?, source_template_id = ?, description = ?, enabled = ?, " +
                "updated_at = ?, version = version + 1 " +
                "WHERE selected_id = ?";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, selected.name());
            stmt.setString(2, selected.sourceTemplateId());
            stmt.setString(3, selected.description());
            stmt.setBoolean(4, selected.enabled());
            stmt.setTimestamp(5, Timestamp.from(Instant.now()));
            stmt.setString(6, selected.selectedId());

            stmt.executeUpdate();
            log.info("Updated selected watchlist: {}", selected.selectedId());

        } catch (SQLException e) {
            log.error("Error updating selected watchlist: {}", selected.selectedId(), e);
            throw new RuntimeException("Database error", e);
        }
    }

    @Override
    public void delete(String selectedId) {
        String sql = "UPDATE watchlist_selected SET deleted_at = ?, version = version + 1 " +
                "WHERE selected_id = ?";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setTimestamp(1, Timestamp.from(Instant.now()));
            stmt.setString(2, selectedId);

            stmt.executeUpdate();
            log.info("Soft deleted selected watchlist: {}", selectedId);

        } catch (SQLException e) {
            log.error("Error deleting selected watchlist: {}", selectedId, e);
            throw new RuntimeException("Database error", e);
        }
    }

    @Override
    public void insertSymbol(WatchlistSelectedSymbol symbol) {
        String sql = "INSERT INTO watchlist_selected_symbols " +
                "(selected_id, symbol, display_order, created_at) " +
                "VALUES (?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, symbol.selectedId());
            stmt.setString(2, symbol.symbol());
            stmt.setInt(3, symbol.displayOrder());
            stmt.setTimestamp(4, Timestamp.from(symbol.createdAt()));

            stmt.executeUpdate();
            log.info("Inserted symbol {} into selected watchlist {}", symbol.symbol(), symbol.selectedId());

        } catch (SQLException e) {
            log.error("Error inserting symbol into selected watchlist: {}", symbol.selectedId(), e);
            throw new RuntimeException("Database error", e);
        }
    }

    @Override
    public void deleteSymbol(long symbolId) {
        String sql = "DELETE FROM watchlist_selected_symbols WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, symbolId);

            stmt.executeUpdate();
            log.info("Deleted selected watchlist symbol: {}", symbolId);

        } catch (SQLException e) {
            log.error("Error deleting selected watchlist symbol: {}", symbolId, e);
            throw new RuntimeException("Database error", e);
        }
    }

    @Override
    public void deleteAllSymbols(String selectedId) {
        String sql = "DELETE FROM watchlist_selected_symbols WHERE selected_id = ?";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, selectedId);

            int count = stmt.executeUpdate();
            log.info("Deleted {} symbols from selected watchlist {}", count, selectedId);

        } catch (SQLException e) {
            log.error("Error deleting all symbols from selected watchlist: {}", selectedId, e);
            throw new RuntimeException("Database error", e);
        }
    }

    private WatchlistSelected mapSelected(ResultSet rs) throws SQLException {
        Timestamp deletedAtTs = rs.getTimestamp("deleted_at");
        return new WatchlistSelected(
                rs.getString("selected_id"),
                rs.getString("name"),
                rs.getString("source_template_id"),
                rs.getString("description"),
                rs.getBoolean("enabled"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                deletedAtTs != null ? deletedAtTs.toInstant() : null,
                rs.getInt("version"));
    }

    private WatchlistSelectedSymbol mapSelectedSymbol(ResultSet rs) throws SQLException {
        return new WatchlistSelectedSymbol(
                rs.getLong("id"),
                rs.getString("selected_id"),
                rs.getString("symbol"),
                rs.getInt("display_order"),
                rs.getTimestamp("created_at").toInstant());
    }
}
