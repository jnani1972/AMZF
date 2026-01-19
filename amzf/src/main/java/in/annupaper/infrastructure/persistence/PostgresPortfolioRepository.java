package in.annupaper.infrastructure.persistence;

import in.annupaper.application.port.output.*;

import in.annupaper.domain.model.Portfolio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PostgresPortfolioRepository implements PortfolioRepository {
    private static final Logger log = LoggerFactory.getLogger(PostgresPortfolioRepository.class);

    private final DataSource dataSource;

    public PostgresPortfolioRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Portfolio> findByUserId(String userId) {
        String sql = "SELECT * FROM portfolios WHERE user_id = ? AND deleted_at IS NULL ORDER BY created_at ASC";
        List<Portfolio> portfolios = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    portfolios.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Error finding portfolios by user: {}", e.getMessage(), e);
        }

        return portfolios;
    }

    @Override
    public List<Portfolio> findAll() {
        String sql = "SELECT * FROM portfolios WHERE deleted_at IS NULL";
        List<Portfolio> portfolios = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    portfolios.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Error finding all portfolios: {}", e.getMessage(), e);
        }

        return portfolios;
    }

    @Override
    public Optional<Portfolio> findById(String portfolioId) {
        String sql = "SELECT * FROM portfolios WHERE portfolio_id = ? AND deleted_at IS NULL";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, portfolioId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Error finding portfolio by ID: {}", e.getMessage(), e);
        }

        return Optional.empty();
    }

    @Override
    public void insert(Portfolio portfolio) {
        String sql = """
                INSERT INTO portfolios (
                    portfolio_id, user_id, name, total_capital, reserved_capital,
                    max_portfolio_log_loss, max_symbol_weight, max_symbols,
                    allocation_mode, status, paused, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, portfolio.portfolioId());
            ps.setString(2, portfolio.userId());
            ps.setString(3, portfolio.name());
            ps.setBigDecimal(4, portfolio.totalCapital());
            ps.setBigDecimal(5, portfolio.reservedCapital());
            ps.setBigDecimal(6, portfolio.maxPortfolioLogLoss());
            ps.setBigDecimal(7, portfolio.maxSymbolWeight());
            ps.setInt(8, portfolio.maxSymbols());
            ps.setString(9, portfolio.allocationMode());
            ps.setString(10, portfolio.status());
            ps.setBoolean(11, portfolio.paused());

            ps.executeUpdate();
            log.info("Portfolio inserted: {} version 1", portfolio.portfolioId());

        } catch (Exception e) {
            log.error("Error inserting portfolio: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to insert portfolio", e);
        }
    }

    // @Override
    // public void update(Portfolio portfolio) {
    // String sql = """
    // UPDATE portfolios SET
    // name = ?,
    // total_capital = ?,
    // reserved_capital = ?,
    // max_portfolio_log_loss = ?,
    // max_symbol_weight = ?,
    // max_symbols = ?,
    // allocation_mode = ?,
    // status = ?,
    // paused = ?,
    // updated_at = NOW()
    // WHERE portfolio_id = ?
    // """;
    //
    // try (Connection conn = dataSource.getConnection();
    // PreparedStatement ps = conn.prepareStatement(sql)) {
    //
    // ps.setString(1, portfolio.name());
    // ps.setBigDecimal(2, portfolio.totalCapital());
    // ps.setBigDecimal(3, portfolio.reservedCapital());
    // ps.setBigDecimal(4, portfolio.maxPortfolioLogLoss());
    // ps.setBigDecimal(5, portfolio.maxSymbolWeight());
    // ps.setInt(6, portfolio.maxSymbols());
    // ps.setString(7, portfolio.allocationMode());
    // ps.setString(8, portfolio.status());
    // ps.setBoolean(9, portfolio.paused());
    // ps.setString(10, portfolio.portfolioId());
    //
    // ps.executeUpdate();
    // log.info("Portfolio updated: {}", portfolio.portfolioId());
    //
    // } catch (Exception e) {
    // log.error("Error updating portfolio: {}", e.getMessage(), e);
    // throw new RuntimeException("Failed to update portfolio", e);
    // }
    // }
    @Override
    public void update(Portfolio portfolio) {
        // Immutable update: soft delete old version, insert new version
        String queryVersionSql = "SELECT version FROM portfolios WHERE portfolio_id = ? AND deleted_at IS NULL";
        String softDeleteSql = "UPDATE portfolios SET deleted_at = NOW() WHERE portfolio_id = ? AND version = ?";
        String insertSql = """
                INSERT INTO portfolios (
                    portfolio_id, user_id, name, total_capital, reserved_capital,
                    max_portfolio_log_loss, max_symbol_weight, max_symbols,
                    allocation_mode, status, paused, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            int currentVersion;
            try (PreparedStatement ps = conn.prepareStatement(queryVersionSql)) {
                ps.setString(1, portfolio.portfolioId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new RuntimeException("Portfolio not found: " + portfolio.portfolioId());
                    }
                    currentVersion = rs.getInt("version");
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(softDeleteSql)) {
                ps.setString(1, portfolio.portfolioId());
                ps.setInt(2, currentVersion);
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    throw new RuntimeException("Failed to soft delete portfolio version: " + portfolio.portfolioId());
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, portfolio.portfolioId());
                ps.setString(2, portfolio.userId());
                ps.setString(3, portfolio.name());
                ps.setBigDecimal(4, portfolio.totalCapital());
                ps.setBigDecimal(5, portfolio.reservedCapital());
                ps.setBigDecimal(6, portfolio.maxPortfolioLogLoss());
                ps.setBigDecimal(7, portfolio.maxSymbolWeight());
                ps.setInt(8, portfolio.maxSymbols());
                ps.setString(9, portfolio.allocationMode());
                ps.setString(10, portfolio.status());
                ps.setBoolean(11, portfolio.paused());
                ps.setInt(12, currentVersion + 1);

                ps.executeUpdate();
            }

            conn.commit();
            log.info("Portfolio updated: {} version {} → {}", portfolio.portfolioId(), currentVersion,
                    currentVersion + 1);

        } catch (Exception e) {
            log.error("Error updating portfolio: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update portfolio", e);
        }
    }

    // } catch (Exception e) {
    // log.error("Error deleting portfolio: {}", e.getMessage(), e);
    // throw new RuntimeException("Failed to delete portfolio", e);
    // }
    // }
    @Override
    public void delete(String portfolioId) {
        // Soft delete: mark as deleted
        String queryVersionSql = "SELECT version FROM portfolios WHERE portfolio_id = ? AND deleted_at IS NULL";
        String softDeleteSql = "UPDATE portfolios SET deleted_at = NOW() WHERE portfolio_id = ? AND version = ?";

        try (Connection conn = dataSource.getConnection()) {
            int currentVersion;
            try (PreparedStatement ps = conn.prepareStatement(queryVersionSql)) {
                ps.setString(1, portfolioId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        log.warn("Portfolio not found or already deleted: {}", portfolioId);
                        return;
                    }
                    currentVersion = rs.getInt("version");
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(softDeleteSql)) {
                ps.setString(1, portfolioId);
                ps.setInt(2, currentVersion);
                ps.executeUpdate();
                log.info("Portfolio soft deleted: {} version {}", portfolioId, currentVersion);
            }

        } catch (Exception e) {
            log.error("Error soft deleting portfolio: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to delete portfolio", e);
        }
    }

    private Portfolio mapRow(ResultSet rs) throws Exception {
        String portfolioId = rs.getString("portfolio_id");
        String userId = rs.getString("user_id");
        String name = rs.getString("name");
        BigDecimal totalCapital = rs.getBigDecimal("total_capital");
        BigDecimal reservedCapital = rs.getBigDecimal("reserved_capital");
        BigDecimal maxPortfolioLogLoss = rs.getBigDecimal("max_portfolio_log_loss");
        BigDecimal maxSymbolWeight = rs.getBigDecimal("max_symbol_weight");
        int maxSymbols = rs.getInt("max_symbols");
        String allocationMode = rs.getString("allocation_mode");
        String status = rs.getString("status");
        boolean paused = rs.getBoolean("paused");

        Timestamp createdTs = rs.getTimestamp("created_at");
        Timestamp updatedTs = rs.getTimestamp("updated_at");
        Instant createdAt = createdTs != null ? createdTs.toInstant() : Instant.now();
        Instant updatedAt = updatedTs != null ? updatedTs.toInstant() : Instant.now();

        Timestamp deletedTs = rs.getTimestamp("deleted_at");
        Instant deletedAt = deletedTs != null ? deletedTs.toInstant() : null;

        int version = rs.getInt("version");

        return new Portfolio(
                portfolioId, userId, name, totalCapital, reservedCapital,
                maxPortfolioLogLoss, maxSymbolWeight, maxSymbols,
                allocationMode, status, paused, createdAt, updatedAt, deletedAt, version);
    }
}
