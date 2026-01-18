package in.annupaper.infrastructure.persistence;

import in.annupaper.application.port.output.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.annupaper.domain.model.EventScope;
import in.annupaper.domain.model.EventType;
import in.annupaper.domain.model.TradeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * PostgreSQL implementation of TradeEventRepository.
 */
public final class PostgresTradeEventRepository implements TradeEventRepository {
    private static final Logger log = LoggerFactory.getLogger(PostgresTradeEventRepository.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DataSource dataSource;

    public PostgresTradeEventRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public TradeEvent append(TradeEvent e) {
        String sql = """
                INSERT INTO trade_events (
                    event_type, scope, user_id, broker_id, user_broker_id,
                    payload, signal_id, intent_id, trade_id, order_id, created_by
                ) VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)
                RETURNING seq, created_at
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, e.type().name());
            ps.setString(2, e.scope().name());
            ps.setString(3, e.userId());
            ps.setString(4, e.brokerId());
            ps.setString(5, e.userBrokerId());
            ps.setString(6, MAPPER.writeValueAsString(e.payload()));
            ps.setString(7, e.signalId());
            ps.setString(8, e.intentId());
            ps.setString(9, e.tradeId());
            ps.setString(10, e.orderId());
            ps.setString(11, e.createdBy());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long seq = rs.getLong("seq");
                    Instant createdAt = rs.getTimestamp("created_at").toInstant();
                    return new TradeEvent(seq, e.type(), e.scope(), e.userId(), e.brokerId(), e.userBrokerId(),
                            e.payload(), e.signalId(), e.intentId(), e.tradeId(), e.orderId(),
                            createdAt, e.createdBy());
                }
            }
        } catch (Exception ex) {
            log.error("Failed to append event: {}", ex.getMessage(), ex);
            throw new RuntimeException("Failed to append event", ex);
        }

        return e;
    }

    @Override
    public List<TradeEvent> listAfterSeq(long afterSeq, int limit) {
        String sql = """
                SELECT seq, event_type, scope, user_id, broker_id, user_broker_id,
                       payload, signal_id, intent_id, trade_id, order_id, created_at, created_by
                FROM trade_events
                WHERE seq > ?
                ORDER BY seq ASC
                LIMIT ?
                """;

        return queryEvents(sql, afterSeq, limit);
    }

    @Override
    public List<TradeEvent> listAfterSeqForUser(long afterSeq, int limit, String userId) {
        String sql = """
                SELECT seq, event_type, scope, user_id, broker_id, user_broker_id,
                       payload, signal_id, intent_id, trade_id, order_id, created_at, created_by
                FROM trade_events
                WHERE seq > ?
                  AND (scope = 'GLOBAL' OR user_id = ?)
                ORDER BY seq ASC
                LIMIT ?
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, afterSeq);
            ps.setString(2, userId);
            ps.setInt(3, limit);

            return executeQuery(ps);
        } catch (Exception ex) {
            log.error("Failed to list events for user: {}", ex.getMessage(), ex);
            throw new RuntimeException("Failed to list events", ex);
        }
    }

    @Override
    public List<TradeEvent> listAfterSeqForUserBroker(long afterSeq, int limit, String userId, String userBrokerId) {
        String sql = """
                SELECT seq, event_type, scope, user_id, broker_id, user_broker_id,
                       payload, signal_id, intent_id, trade_id, order_id, created_at, created_by
                FROM trade_events
                WHERE seq > ?
                  AND (scope = 'GLOBAL'
                       OR (scope = 'USER' AND user_id = ?)
                       OR (scope = 'USER_BROKER' AND user_id = ? AND user_broker_id = ?))
                ORDER BY seq ASC
                LIMIT ?
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, afterSeq);
            ps.setString(2, userId);
            ps.setString(3, userId);
            ps.setString(4, userBrokerId);
            ps.setInt(5, limit);

            return executeQuery(ps);
        } catch (Exception ex) {
            log.error("Failed to list events for user-broker: {}", ex.getMessage(), ex);
            throw new RuntimeException("Failed to list events", ex);
        }
    }

    @Override
    public long latestSeq() {
        String sql = "SELECT COALESCE(MAX(seq), 0) FROM trade_events";

        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0L;
        } catch (Exception ex) {
            log.error("Failed to get latest seq: {}", ex.getMessage(), ex);
            return 0L;
        }
    }

    private List<TradeEvent> queryEvents(String sql, long afterSeq, int limit) {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, afterSeq);
            ps.setInt(2, limit);

            return executeQuery(ps);
        } catch (Exception ex) {
            log.error("Failed to list events: {}", ex.getMessage(), ex);
            throw new RuntimeException("Failed to list events", ex);
        }
    }

    private List<TradeEvent> executeQuery(PreparedStatement ps) throws Exception {
        List<TradeEvent> events = new ArrayList<>();

        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                events.add(mapRow(rs));
            }
        }

        return events;
    }

    private TradeEvent mapRow(ResultSet rs) throws Exception {
        long seq = rs.getLong("seq");
        EventType type = EventType.valueOf(rs.getString("event_type"));
        EventScope scope = EventScope.valueOf(rs.getString("scope"));
        String userId = rs.getString("user_id");
        String brokerId = rs.getString("broker_id");
        String userBrokerId = rs.getString("user_broker_id");
        JsonNode payload = MAPPER.readTree(rs.getString("payload"));
        String signalId = rs.getString("signal_id");
        String intentId = rs.getString("intent_id");
        String tradeId = rs.getString("trade_id");
        String orderId = rs.getString("order_id");
        Instant ts = rs.getTimestamp("created_at").toInstant();
        String createdBy = rs.getString("created_by");

        return new TradeEvent(seq, type, scope, userId, brokerId, userBrokerId,
                payload, signalId, intentId, tradeId, orderId, ts, createdBy);
    }
}
