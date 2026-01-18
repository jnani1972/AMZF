package in.annupaper.infrastructure.persistence;

import in.annupaper.application.port.output.*;

import com.zaxxer.hikari.HikariDataSource;
import in.annupaper.domain.model.WatchlistTemplate;
import in.annupaper.domain.model.WatchlistTemplateSymbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * PostgreSQL implementation of WatchlistTemplateRepository.
 */
public class PostgresWatchlistTemplateRepository implements WatchlistTemplateRepository {

    private static final Logger log = LoggerFactory.getLogger(PostgresWatchlistTemplateRepository.class);
    private final HikariDataSource dataSource;

    public PostgresWatchlistTemplateRepository(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<WatchlistTemplate> findAllActive() {
        String sql = "SELECT template_id, template_name, description, display_order, enabled, " +
                "created_at, updated_at, deleted_at, version " +
                "FROM watchlist_templates " +
                "WHERE deleted_at IS NULL AND enabled = true " +
                "ORDER BY display_order ASC, template_name ASC";

        List<WatchlistTemplate> templates = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                templates.add(mapTemplate(rs));
            }

        } catch (SQLException e) {
            log.error("Error finding active templates", e);
            throw new RuntimeException("Database error", e);
        }

        return templates;
    }

    @Override
    public Optional<WatchlistTemplate> findById(String templateId) {
        String sql = "SELECT template_id, template_name, description, display_order, enabled, " +
                "created_at, updated_at, deleted_at, version " +
                "FROM watchlist_templates " +
                "WHERE template_id = ?";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, templateId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapTemplate(rs));
                }
            }

        } catch (SQLException e) {
            log.error("Error finding template by id: {}", templateId, e);
            throw new RuntimeException("Database error", e);
        }

        return Optional.empty();
    }

    @Override
    public List<WatchlistTemplateSymbol> findSymbolsByTemplateId(String templateId) {
        String sql = "SELECT id, template_id, symbol, display_order, created_at " +
                "FROM watchlist_template_symbols " +
                "WHERE template_id = ? " +
                "ORDER BY display_order ASC, symbol ASC";

        List<WatchlistTemplateSymbol> symbols = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, templateId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    symbols.add(mapTemplateSymbol(rs));
                }
            }

        } catch (SQLException e) {
            log.error("Error finding symbols for template: {}", templateId, e);
            throw new RuntimeException("Database error", e);
        }

        return symbols;
    }

    @Override
    public void insert(WatchlistTemplate template) {
        String sql = "INSERT INTO watchlist_templates " +
                "(template_id, template_name, description, display_order, enabled, " +
                "created_at, updated_at, version) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, template.templateId());
            stmt.setString(2, template.templateName());
            stmt.setString(3, template.description());
            stmt.setInt(4, template.displayOrder());
            stmt.setBoolean(5, template.enabled());
            stmt.setTimestamp(6, Timestamp.from(template.createdAt()));
            stmt.setTimestamp(7, Timestamp.from(template.updatedAt()));
            stmt.setInt(8, template.version());

            stmt.executeUpdate();
            log.info("Inserted template: {}", template.templateId());

        } catch (SQLException e) {
            log.error("Error inserting template: {}", template.templateId(), e);
            throw new RuntimeException("Database error", e);
        }
    }

    @Override
    public void update(WatchlistTemplate template) {
        String sql = "UPDATE watchlist_templates SET " +
                "template_name = ?, description = ?, display_order = ?, enabled = ?, " +
                "updated_at = ?, version = version + 1 " +
                "WHERE template_id = ?";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, template.templateName());
            stmt.setString(2, template.description());
            stmt.setInt(3, template.displayOrder());
            stmt.setBoolean(4, template.enabled());
            stmt.setTimestamp(5, Timestamp.from(Instant.now()));
            stmt.setString(6, template.templateId());

            stmt.executeUpdate();
            log.info("Updated template: {}", template.templateId());

        } catch (SQLException e) {
            log.error("Error updating template: {}", template.templateId(), e);
            throw new RuntimeException("Database error", e);
        }
    }

    @Override
    public void delete(String templateId) {
        String sql = "UPDATE watchlist_templates SET deleted_at = ?, version = version + 1 " +
                "WHERE template_id = ?";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setTimestamp(1, Timestamp.from(Instant.now()));
            stmt.setString(2, templateId);

            stmt.executeUpdate();
            log.info("Soft deleted template: {}", templateId);

        } catch (SQLException e) {
            log.error("Error deleting template: {}", templateId, e);
            throw new RuntimeException("Database error", e);
        }
    }

    @Override
    public void insertSymbol(WatchlistTemplateSymbol symbol) {
        String sql = "INSERT INTO watchlist_template_symbols " +
                "(template_id, symbol, display_order, created_at) " +
                "VALUES (?, ?, ?, ?)";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, symbol.templateId());
            stmt.setString(2, symbol.symbol());
            stmt.setInt(3, symbol.displayOrder());
            stmt.setTimestamp(4, Timestamp.from(symbol.createdAt()));

            stmt.executeUpdate();
            log.info("Inserted symbol {} into template {}", symbol.symbol(), symbol.templateId());

        } catch (SQLException e) {
            log.error("Error inserting symbol into template: {}", symbol.templateId(), e);
            throw new RuntimeException("Database error", e);
        }
    }

    @Override
    public void deleteSymbol(long symbolId) {
        String sql = "DELETE FROM watchlist_template_symbols WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, symbolId);

            stmt.executeUpdate();
            log.info("Deleted template symbol: {}", symbolId);

        } catch (SQLException e) {
            log.error("Error deleting template symbol: {}", symbolId, e);
            throw new RuntimeException("Database error", e);
        }
    }

    private WatchlistTemplate mapTemplate(ResultSet rs) throws SQLException {
        Timestamp deletedAtTs = rs.getTimestamp("deleted_at");
        return new WatchlistTemplate(
                rs.getString("template_id"),
                rs.getString("template_name"),
                rs.getString("description"),
                rs.getInt("display_order"),
                rs.getBoolean("enabled"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                deletedAtTs != null ? deletedAtTs.toInstant() : null,
                rs.getInt("version"));
    }

    private WatchlistTemplateSymbol mapTemplateSymbol(ResultSet rs) throws SQLException {
        return new WatchlistTemplateSymbol(
                rs.getLong("id"),
                rs.getString("template_id"),
                rs.getString("symbol"),
                rs.getInt("display_order"),
                rs.getTimestamp("created_at").toInstant());
    }
}
