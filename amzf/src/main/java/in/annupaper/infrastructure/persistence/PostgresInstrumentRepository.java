package in.annupaper.infrastructure.persistence;

import in.annupaper.application.port.output.*;
import in.annupaper.domain.model.BrokerInstrument;
import in.annupaper.domain.model.InstrumentSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public final class PostgresInstrumentRepository implements InstrumentRepository {
    private static final Logger log = LoggerFactory.getLogger(PostgresInstrumentRepository.class);
    private final DataSource dataSource;

    public PostgresInstrumentRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void saveInstruments(String brokerCode, List<BrokerInstrument> instruments) {
        String sql = """
                INSERT INTO instruments (broker_id, exchange, trading_symbol, name, instrument_type, token, lot_size, tick_size)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (broker_id, exchange, trading_symbol) DO UPDATE SET
                    name = EXCLUDED.name,
                    instrument_type = EXCLUDED.instrument_type,
                    token = EXCLUDED.token,
                    lot_size = EXCLUDED.lot_size,
                    tick_size = EXCLUDED.tick_size,
                    updated_at = NOW()
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            conn.setAutoCommit(false);
            int count = 0;

            for (BrokerInstrument inst : instruments) {
                ps.setString(1, brokerCode);
                ps.setString(2, inst.exchange());
                ps.setString(3, inst.tradingSymbol());
                ps.setString(4, inst.name());
                ps.setString(5, inst.instrumentType());
                ps.setString(6, null); // segment not in BrokerInstrument
                ps.setInt(7, inst.lotSize());
                ps.setBigDecimal(8, inst.tickSize());

                ps.addBatch();
                count++;

                if (count % 1000 == 0) {
                    ps.executeBatch();
                    conn.commit();
                    log.debug("Saved {} instruments for {}", count, brokerCode);
                }
            }

            ps.executeBatch();
            conn.commit();
            log.info("Saved total {} instruments for {}", count, brokerCode);

        } catch (Exception e) {
            log.error("Error saving instruments: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save instruments", e);
        }
    }

    @Override
    public List<InstrumentSearchResult> search(String query, int limit) {
        String sql = """
                SELECT trading_symbol, name, exchange, instrument_type,
                  CASE WHEN UPPER(trading_symbol) LIKE ? THEN 0 ELSE 1 END AS rank
                FROM instruments
                WHERE (UPPER(trading_symbol) LIKE ? OR UPPER(name) LIKE ?)
                ORDER BY rank ASC, trading_symbol ASC
                LIMIT ?
                """;

        List<InstrumentSearchResult> results = new ArrayList<>();
        String searchPattern = "%" + query.toUpperCase() + "%";
        String exactPattern = query.toUpperCase() + "%";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, exactPattern); // For CASE WHEN rank
            ps.setString(2, searchPattern); // For trading_symbol LIKE
            ps.setString(3, searchPattern); // For name LIKE
            ps.setInt(4, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new InstrumentSearchResult(
                            rs.getString("trading_symbol"),
                            rs.getString("name"),
                            rs.getString("exchange"),
                            rs.getString("instrument_type")));
                }
            }

        } catch (Exception e) {
            log.error("Error searching instruments: {}", e.getMessage(), e);
        }

        return results;
    }

    @Override
    public void clearBroker(String brokerId) {
        String sql = "DELETE FROM instruments WHERE broker_id = ?";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, brokerId);
            int deleted = ps.executeUpdate();
            log.info("Cleared {} instruments for {}", deleted, brokerId);

        } catch (Exception e) {
            log.error("Error clearing instruments: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to clear instruments", e);
        }
    }

    @Override
    public int getCount(String brokerId) {
        String sql = "SELECT COUNT(*) FROM instruments WHERE broker_id = ?";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, brokerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }

        } catch (Exception e) {
            log.error("Error counting instruments: {}", e.getMessage(), e);
        }

        return 0;
    }

    @Override
    public void shutdown() {
        // No-op for now
    }
}
