package in.annupaper.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.annupaper.domain.trade.Direction;
import in.annupaper.domain.signal.SignalType;
import in.annupaper.domain.signal.Signal;
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
 * PostgreSQL implementation of SignalRepository with immutable audit trail.
 */
public final class PostgresSignalRepository implements SignalRepository {
    private static final Logger log = LoggerFactory.getLogger(PostgresSignalRepository.class);

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public PostgresSignalRepository(DataSource dataSource) {
        this.dataSource = dataSource;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public List<Signal> findAll() {
        String sql = """
            SELECT * FROM signals
            WHERE deleted_at IS NULL
            ORDER BY generated_at DESC
            """;

        List<Signal> signals = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                signals.add(mapRow(rs));
            }
        } catch (Exception e) {
            log.error("Failed to find all signals: {}", e.getMessage());
            throw new RuntimeException("Failed to find signals", e);
        }
        return signals;
    }

    @Override
    public Optional<Signal> findById(String signalId) {
        String sql = """
            SELECT * FROM signals
            WHERE signal_id = ? AND deleted_at IS NULL
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, signalId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find signal {}: {}", signalId, e.getMessage());
            throw new RuntimeException("Failed to find signal", e);
        }
        return Optional.empty();
    }

    @Override
    public List<Signal> findBySymbol(String symbol) {
        String sql = """
            SELECT * FROM signals
            WHERE symbol = ? AND deleted_at IS NULL
            ORDER BY generated_at DESC
            """;

        List<Signal> signals = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, symbol);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    signals.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find signals by symbol {}: {}", symbol, e.getMessage());
            throw new RuntimeException("Failed to find signals", e);
        }
        return signals;
    }

    @Override
    public List<Signal> findByStatus(String status) {
        String sql = """
            SELECT * FROM signals
            WHERE status = ? AND deleted_at IS NULL
            ORDER BY generated_at DESC
            """;

        List<Signal> signals = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    signals.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find signals by status {}: {}", status, e.getMessage());
            throw new RuntimeException("Failed to find signals", e);
        }
        return signals;
    }

    @Override
    public void insert(Signal signal) {
        String sql = """
            INSERT INTO signals (
                signal_id, symbol, direction, signal_type,
                htf_zone, itf_zone, ltf_zone,
                confluence_type, confluence_score,
                p_win, p_fill, kelly,
                ref_price, ref_bid, ref_ask, entry_low, entry_high,
                htf_low, htf_high, itf_low, itf_high, ltf_low, ltf_high,
                effective_floor, effective_ceiling,
                confidence, reason, tags,
                generated_at, expires_at, status, version
            ) VALUES (
                ?, ?, ?::direction, ?::signal_type,
                ?, ?, ?,
                ?, ?,
                ?, ?, ?,
                ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?,
                ?, ?,
                ?, ?, ?::jsonb,
                ?, ?, ?, 1
            )
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, signal.signalId());
            ps.setString(2, signal.symbol());
            ps.setString(3, signal.direction().name());
            ps.setString(4, signal.signalType().name());

            setIntOrNull(ps, 5, signal.htfZone());
            setIntOrNull(ps, 6, signal.itfZone());
            setIntOrNull(ps, 7, signal.ltfZone());

            ps.setString(8, signal.confluenceType());
            setBigDecimalOrNull(ps, 9, signal.confluenceScore());

            setBigDecimalOrNull(ps, 10, signal.pWin());
            setBigDecimalOrNull(ps, 11, signal.pFill());
            setBigDecimalOrNull(ps, 12, signal.kelly());

            setBigDecimalOrNull(ps, 13, signal.refPrice());
            setBigDecimalOrNull(ps, 14, signal.refBid());
            setBigDecimalOrNull(ps, 15, signal.refAsk());
            setBigDecimalOrNull(ps, 16, signal.entryLow());
            setBigDecimalOrNull(ps, 17, signal.entryHigh());

            setBigDecimalOrNull(ps, 18, signal.htfLow());
            setBigDecimalOrNull(ps, 19, signal.htfHigh());
            setBigDecimalOrNull(ps, 20, signal.itfLow());
            setBigDecimalOrNull(ps, 21, signal.itfHigh());
            setBigDecimalOrNull(ps, 22, signal.ltfLow());
            setBigDecimalOrNull(ps, 23, signal.ltfHigh());

            setBigDecimalOrNull(ps, 24, signal.effectiveFloor());
            setBigDecimalOrNull(ps, 25, signal.effectiveCeiling());

            setBigDecimalOrNull(ps, 26, signal.confidence());
            ps.setString(27, signal.reason());
            ps.setString(28, objectMapper.writeValueAsString(signal.tags()));

            ps.setTimestamp(29, Timestamp.from(signal.generatedAt()));
            setTimestampOrNull(ps, 30, signal.expiresAt());
            ps.setString(31, signal.status());

            ps.executeUpdate();
            log.info("Signal inserted: {}", signal.signalId());

        } catch (Exception e) {
            log.error("Failed to insert signal: {}", e.getMessage());
            throw new RuntimeException("Failed to insert signal", e);
        }
    }

    @Override
    public void update(Signal signal) {
        // Immutable update: soft delete old version, insert new version

        String queryVersionSql = """
            SELECT version FROM signals
            WHERE signal_id = ? AND deleted_at IS NULL
            """;

        String softDeleteSql = """
            UPDATE signals
            SET deleted_at = NOW()
            WHERE signal_id = ? AND version = ?
            """;

        String insertSql = """
            INSERT INTO signals (
                signal_id, symbol, direction, signal_type,
                htf_zone, itf_zone, ltf_zone,
                confluence_type, confluence_score,
                p_win, p_fill, kelly,
                ref_price, ref_bid, ref_ask, entry_low, entry_high,
                htf_low, htf_high, itf_low, itf_high, ltf_low, ltf_high,
                effective_floor, effective_ceiling,
                confidence, reason, tags,
                generated_at, expires_at, status, version
            ) VALUES (
                ?, ?, ?::direction, ?::signal_type,
                ?, ?, ?,
                ?, ?,
                ?, ?, ?,
                ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?,
                ?, ?,
                ?, ?, ?::jsonb,
                ?, ?, ?, ?
            )
            """;

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            // Step 1: Get current version
            int currentVersion;
            try (PreparedStatement ps = conn.prepareStatement(queryVersionSql)) {
                ps.setString(1, signal.signalId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new RuntimeException("Signal not found: " + signal.signalId());
                    }
                    currentVersion = rs.getInt("version");
                }
            }

            // Step 2: Soft delete current version
            try (PreparedStatement ps = conn.prepareStatement(softDeleteSql)) {
                ps.setString(1, signal.signalId());
                ps.setInt(2, currentVersion);
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    throw new RuntimeException("Failed to soft delete signal: " + signal.signalId());
                }
            }

            // Step 3: Insert new version
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, signal.signalId());
                ps.setString(2, signal.symbol());
                ps.setString(3, signal.direction().name());
                ps.setString(4, signal.signalType().name());

                setIntOrNull(ps, 5, signal.htfZone());
                setIntOrNull(ps, 6, signal.itfZone());
                setIntOrNull(ps, 7, signal.ltfZone());

                ps.setString(8, signal.confluenceType());
                setBigDecimalOrNull(ps, 9, signal.confluenceScore());

                setBigDecimalOrNull(ps, 10, signal.pWin());
                setBigDecimalOrNull(ps, 11, signal.pFill());
                setBigDecimalOrNull(ps, 12, signal.kelly());

                setBigDecimalOrNull(ps, 13, signal.refPrice());
                setBigDecimalOrNull(ps, 14, signal.refBid());
                setBigDecimalOrNull(ps, 15, signal.refAsk());
                setBigDecimalOrNull(ps, 16, signal.entryLow());
                setBigDecimalOrNull(ps, 17, signal.entryHigh());

                setBigDecimalOrNull(ps, 18, signal.htfLow());
                setBigDecimalOrNull(ps, 19, signal.htfHigh());
                setBigDecimalOrNull(ps, 20, signal.itfLow());
                setBigDecimalOrNull(ps, 21, signal.itfHigh());
                setBigDecimalOrNull(ps, 22, signal.ltfLow());
                setBigDecimalOrNull(ps, 23, signal.ltfHigh());

                setBigDecimalOrNull(ps, 24, signal.effectiveFloor());
                setBigDecimalOrNull(ps, 25, signal.effectiveCeiling());

                setBigDecimalOrNull(ps, 26, signal.confidence());
                ps.setString(27, signal.reason());
                ps.setString(28, objectMapper.writeValueAsString(signal.tags()));

                ps.setTimestamp(29, Timestamp.from(signal.generatedAt()));
                setTimestampOrNull(ps, 30, signal.expiresAt());
                ps.setString(31, signal.status());
                ps.setInt(32, currentVersion + 1);

                ps.executeUpdate();
            }

            conn.commit();
            log.info("Signal updated: {} version {} → {}", signal.signalId(), currentVersion, currentVersion + 1);

        } catch (Exception e) {
            log.error("Failed to update signal: {}", e.getMessage());
            throw new RuntimeException("Failed to update signal", e);
        }
    }

    @Override
    public void delete(String signalId) {
        // Soft delete: mark as deleted

        String queryVersionSql = """
            SELECT version FROM signals
            WHERE signal_id = ? AND deleted_at IS NULL
            """;

        String softDeleteSql = """
            UPDATE signals
            SET deleted_at = NOW()
            WHERE signal_id = ? AND version = ?
            """;

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            // Get current version
            int currentVersion;
            try (PreparedStatement ps = conn.prepareStatement(queryVersionSql)) {
                ps.setString(1, signalId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new RuntimeException("Signal not found: " + signalId);
                    }
                    currentVersion = rs.getInt("version");
                }
            }

            // Soft delete
            try (PreparedStatement ps = conn.prepareStatement(softDeleteSql)) {
                ps.setString(1, signalId);
                ps.setInt(2, currentVersion);
                int deleted = ps.executeUpdate();
                if (deleted == 0) {
                    throw new RuntimeException("Failed to delete signal: " + signalId);
                }
            }

            conn.commit();
            log.info("Signal soft-deleted: {} version {}", signalId, currentVersion);

        } catch (Exception e) {
            log.error("Failed to delete signal: {}", e.getMessage());
            throw new RuntimeException("Failed to delete signal", e);
        }
    }

    @Override
    public List<Signal> findAllVersions(String signalId) {
        String sql = """
            SELECT * FROM signals
            WHERE signal_id = ?
            ORDER BY version ASC
            """;

        List<Signal> signals = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, signalId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    signals.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find signal versions {}: {}", signalId, e.getMessage());
            throw new RuntimeException("Failed to find signal versions", e);
        }
        return signals;
    }

    @Override
    public Optional<Signal> findByIdAndVersion(String signalId, int version) {
        String sql = """
            SELECT * FROM signals
            WHERE signal_id = ? AND version = ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, signalId);
            ps.setInt(2, version);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find signal {} version {}: {}", signalId, version, e.getMessage());
            throw new RuntimeException("Failed to find signal version", e);
        }
        return Optional.empty();
    }

    private Signal mapRow(ResultSet rs) throws Exception {
        Timestamp deletedTs = rs.getTimestamp("deleted_at");
        Instant deletedAt = deletedTs != null ? deletedTs.toInstant() : null;

        Timestamp expiresTs = rs.getTimestamp("expires_at");
        Instant expiresAt = expiresTs != null ? expiresTs.toInstant() : null;

        String tagsJson = rs.getString("tags");
        List<String> tags = tagsJson != null
            ? objectMapper.readValue(tagsJson, new TypeReference<List<String>>() {})
            : List.of();

        return new Signal(
            rs.getString("signal_id"),
            rs.getString("symbol"),
            Direction.valueOf(rs.getString("direction")),
            SignalType.valueOf(rs.getString("signal_type")),
            (Integer) rs.getObject("htf_zone"),
            (Integer) rs.getObject("itf_zone"),
            (Integer) rs.getObject("ltf_zone"),
            rs.getString("confluence_type"),
            rs.getBigDecimal("confluence_score"),
            rs.getBigDecimal("p_win"),
            rs.getBigDecimal("p_fill"),
            rs.getBigDecimal("kelly"),
            rs.getBigDecimal("ref_price"),
            rs.getBigDecimal("ref_bid"),
            rs.getBigDecimal("ref_ask"),
            rs.getBigDecimal("entry_low"),
            rs.getBigDecimal("entry_high"),
            rs.getBigDecimal("htf_low"),
            rs.getBigDecimal("htf_high"),
            rs.getBigDecimal("itf_low"),
            rs.getBigDecimal("itf_high"),
            rs.getBigDecimal("ltf_low"),
            rs.getBigDecimal("ltf_high"),
            rs.getBigDecimal("effective_floor"),
            rs.getBigDecimal("effective_ceiling"),
            rs.getBigDecimal("confidence"),
            rs.getString("reason"),
            tags,
            rs.getTimestamp("generated_at").toInstant(),
            expiresAt,
            rs.getString("status"),
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
    public int markSignalsAsStale() {
        // Mark all active signals as STALE where no trades exist yet
        String sql = """
            UPDATE signals
            SET status = 'STALE'
            WHERE deleted_at IS NULL
              AND status = 'ACTIVE'
              AND signal_id NOT IN (
                SELECT DISTINCT signal_id
                FROM trades
                WHERE signal_id IS NOT NULL
              )
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int count = ps.executeUpdate();
            log.info("Marked {} signals as STALE (global config change)", count);
            return count;

        } catch (Exception e) {
            log.error("Failed to mark signals as STALE: {}", e.getMessage());
            throw new RuntimeException("Failed to mark signals as STALE", e);
        }
    }

    @Override
    public int markSignalsAsStaleForSymbol(String symbol) {
        // Mark all active signals for a specific symbol as STALE where no trades exist yet
        String sql = """
            UPDATE signals
            SET status = 'STALE'
            WHERE deleted_at IS NULL
              AND status = 'ACTIVE'
              AND symbol = ?
              AND signal_id NOT IN (
                SELECT DISTINCT signal_id
                FROM trades
                WHERE signal_id IS NOT NULL
              )
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, symbol);
            int count = ps.executeUpdate();
            log.info("Marked {} signals as STALE for symbol {} (symbol config change)", count, symbol);
            return count;

        } catch (Exception e) {
            log.error("Failed to mark signals as STALE for symbol {}: {}", symbol, e.getMessage());
            throw new RuntimeException("Failed to mark signals as STALE", e);
        }
    }

    @Override
    public Signal upsert(Signal signal) {
        // ✅ P0-B: Idempotent upsert using dedupe key
        // (symbol, confluence_type, signal_day, effective_floor, effective_ceiling)
        // If exists, update status; otherwise insert
        // Note: signal_day is auto-generated from generated_at

        String sql = """
            INSERT INTO signals (
                signal_id, symbol, direction, signal_type,
                htf_zone, itf_zone, ltf_zone,
                confluence_type, confluence_score,
                p_win, p_fill, kelly,
                ref_price, ref_bid, ref_ask, entry_low, entry_high,
                htf_low, htf_high, itf_low, itf_high, ltf_low, ltf_high,
                effective_floor, effective_ceiling,
                confidence, reason, tags,
                generated_at, expires_at, status, version
            ) VALUES (
                ?, ?, ?::direction, ?::signal_type,
                ?, ?, ?,
                ?, ?,
                ?, ?, ?,
                ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?,
                ?, ?,
                ?, ?, ?::jsonb,
                ?, ?, ?, 1
            )
            ON CONFLICT (symbol, confluence_type, signal_day, effective_floor, effective_ceiling)
            DO UPDATE SET
                status = 'ACTIVE',
                updated_at = NOW()
            RETURNING *
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int idx = 1;
            ps.setString(idx++, signal.signalId());
            ps.setString(idx++, signal.symbol());
            ps.setString(idx++, signal.direction().name());
            ps.setString(idx++, signal.signalType().name());

            setIntOrNull(ps, idx++, signal.htfZone());
            setIntOrNull(ps, idx++, signal.itfZone());
            setIntOrNull(ps, idx++, signal.ltfZone());

            ps.setString(idx++, signal.confluenceType());
            setBigDecimalOrNull(ps, idx++, signal.confluenceScore());

            setBigDecimalOrNull(ps, idx++, signal.pWin());
            setBigDecimalOrNull(ps, idx++, signal.pFill());
            setBigDecimalOrNull(ps, idx++, signal.kelly());

            setBigDecimalOrNull(ps, idx++, signal.refPrice());
            setBigDecimalOrNull(ps, idx++, signal.refBid());
            setBigDecimalOrNull(ps, idx++, signal.refAsk());
            setBigDecimalOrNull(ps, idx++, signal.entryLow());
            setBigDecimalOrNull(ps, idx++, signal.entryHigh());

            setBigDecimalOrNull(ps, idx++, signal.htfLow());
            setBigDecimalOrNull(ps, idx++, signal.htfHigh());
            setBigDecimalOrNull(ps, idx++, signal.itfLow());
            setBigDecimalOrNull(ps, idx++, signal.itfHigh());
            setBigDecimalOrNull(ps, idx++, signal.ltfLow());
            setBigDecimalOrNull(ps, idx++, signal.ltfHigh());

            // ✅ Round to 2 decimals to match CHECK constraint
            BigDecimal floor = signal.effectiveFloor();
            if (floor != null) {
                floor = floor.setScale(2, java.math.RoundingMode.HALF_UP);
            }
            setBigDecimalOrNull(ps, idx++, floor);

            BigDecimal ceiling = signal.effectiveCeiling();
            if (ceiling != null) {
                ceiling = ceiling.setScale(2, java.math.RoundingMode.HALF_UP);
            }
            setBigDecimalOrNull(ps, idx++, ceiling);

            setBigDecimalOrNull(ps, idx++, signal.confidence());
            ps.setString(idx++, signal.reason());
            ps.setString(idx++, objectMapper.writeValueAsString(signal.tags()));

            ps.setTimestamp(idx++, Timestamp.from(signal.generatedAt()));
            setTimestampOrNull(ps, idx++, signal.expiresAt());
            ps.setString(idx++, signal.status());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Signal result = mapRow(rs);
                    log.info("Signal upserted: {} (symbol={}, confluence={}, floor={}, ceiling={})",
                        result.signalId(), result.symbol(), result.confluenceType(),
                        result.effectiveFloor(), result.effectiveCeiling());
                    return result;
                }
            }

            throw new RuntimeException("Upsert failed to return result");

        } catch (Exception e) {
            log.error("Failed to upsert signal: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upsert signal", e);
        }
    }

    @Override
    public void updateStatus(String signalId, String status) {
        String sql = """
            UPDATE signals
            SET status = ?, updated_at = NOW()
            WHERE signal_id = ? AND deleted_at IS NULL
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status);
            ps.setString(2, signalId);

            int updated = ps.executeUpdate();
            if (updated > 0) {
                log.debug("Signal status updated: {} → {}", signalId, status);
            }
        } catch (Exception e) {
            log.error("Failed to update signal status: {}", e.getMessage());
            throw new RuntimeException("Failed to update signal status", e);
        }
    }

    @Override
    public List<Signal> findBySymbolAndStatus(String symbol, String status) {
        String sql = """
            SELECT * FROM signals
            WHERE symbol = ? AND status = ? AND deleted_at IS NULL
            ORDER BY generated_at DESC
            """;

        List<Signal> signals = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, symbol);
            ps.setString(2, status);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    signals.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find signals by symbol and status: {}", e.getMessage());
            throw new RuntimeException("Failed to find signals", e);
        }
        return signals;
    }

    @Override
    public List<Signal> findExpiringSoon(java.time.Duration window) {
        String sql = """
            SELECT * FROM signals
            WHERE expires_at IS NOT NULL
              AND expires_at <= ?
              AND status = 'PUBLISHED'
              AND deleted_at IS NULL
            ORDER BY expires_at ASC
            """;

        List<Signal> signals = new ArrayList<>();
        Instant threshold = Instant.now().plus(window);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setTimestamp(1, Timestamp.from(threshold));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    signals.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            log.error("Failed to find expiring signals: {}", e.getMessage());
            throw new RuntimeException("Failed to find expiring signals", e);
        }
        return signals;
    }
}
