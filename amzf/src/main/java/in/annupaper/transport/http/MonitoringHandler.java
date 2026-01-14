package in.annupaper.transport.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

/**
 * HTTP handler for monitoring dashboard endpoints.
 *
 * Provides REST API for real-time monitoring metrics:
 * - GET /api/monitoring/system-health - Active trades, pending operations
 * - GET /api/monitoring/performance - Daily P&L, win rate, trends
 * - GET /api/monitoring/broker-status - Broker connectivity health
 * - GET /api/monitoring/exit-health - Exit order execution metrics
 * - GET /api/monitoring/risk - Current exposure and concentrations
 * - GET /api/monitoring/errors - Recent errors and rejections
 * - GET /api/monitoring/alerts - Active alert conditions
 */
public final class MonitoringHandler {
    private static final Logger log = LoggerFactory.getLogger(MonitoringHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DataSource dataSource;

    public MonitoringHandler(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * GET /api/monitoring/system-health
     *
     * Returns active trades summary and pending operations.
     */
    public void getSystemHealth(HttpServerExchange exchange) {
        try (Connection conn = dataSource.getConnection()) {
            Map<String, Object> health = new HashMap<>();

            // Active trades summary
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

            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> activeTrades = new HashMap<>();
                    activeTrades.put("totalOpenTrades", rs.getInt("total_open_trades"));
                    activeTrades.put("longPositions", rs.getInt("long_positions"));
                    activeTrades.put("shortPositions", rs.getInt("short_positions"));
                    activeTrades.put("totalExposure", rs.getBigDecimal("total_exposure_value"));
                    activeTrades.put("avgHoldingHours", rs.getDouble("avg_holding_hours"));
                    health.put("activeTrades", activeTrades);
                }
            }

            // Pending operations
            List<Map<String, Object>> pendingOps = new ArrayList<>();

            String[] tables = {
                "SELECT 'Trade Intents' as type, COUNT(*) as count, MIN(created_at) as oldest FROM trade_intents WHERE status IN ('PENDING', 'APPROVED') AND deleted_at IS NULL",
                "SELECT 'Exit Intents' as type, COUNT(*) as count, MIN(created_at) as oldest FROM exit_intents WHERE status IN ('PENDING', 'APPROVED', 'PLACED') AND deleted_at IS NULL",
                "SELECT 'Orders' as type, COUNT(*) as count, MIN(created_at) as oldest FROM orders WHERE status IN ('PENDING', 'PLACED', 'OPEN') AND deleted_at IS NULL"
            };

            for (String query : tables) {
                try (PreparedStatement stmt = conn.prepareStatement(query);
                     ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> op = new HashMap<>();
                        op.put("type", rs.getString("type"));
                        op.put("count", rs.getInt("count"));
                        op.put("oldest", rs.getTimestamp("oldest"));
                        pendingOps.add(op);
                    }
                }
            }
            health.put("pendingOperations", pendingOps);

            sendJson(exchange, health);

        } catch (Exception e) {
            log.error("Failed to get system health: {}", e.getMessage(), e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to get system health: " + e.getMessage());
        }
    }

    /**
     * GET /api/monitoring/performance
     *
     * Returns daily P&L, win rate, and weekly trends.
     */
    public void getPerformance(HttpServerExchange exchange) {
        try (Connection conn = dataSource.getConnection()) {
            Map<String, Object> performance = new HashMap<>();

            // Today's P&L
            String todaySql = """
                SELECT
                    COUNT(*) as trades_closed,
                    COUNT(CASE WHEN realized_pnl > 0 THEN 1 END) as winning_trades,
                    COUNT(CASE WHEN realized_pnl < 0 THEN 1 END) as losing_trades,
                    ROUND(100.0 * COUNT(CASE WHEN realized_pnl > 0 THEN 1 END) / NULLIF(COUNT(*), 0), 2) as win_rate_pct,
                    SUM(realized_pnl) as total_pnl,
                    AVG(realized_pnl) as avg_pnl_per_trade,
                    MAX(realized_pnl) as best_trade,
                    MIN(realized_pnl) as worst_trade
                FROM trades
                WHERE status = 'CLOSED'
                  AND exit_timestamp >= CURRENT_DATE
                  AND deleted_at IS NULL
                """;

            try (PreparedStatement stmt = conn.prepareStatement(todaySql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> today = new HashMap<>();
                    today.put("tradesClosed", rs.getInt("trades_closed"));
                    today.put("winningTrades", rs.getInt("winning_trades"));
                    today.put("losingTrades", rs.getInt("losing_trades"));
                    today.put("winRatePercent", rs.getDouble("win_rate_pct"));
                    today.put("totalPnl", rs.getBigDecimal("total_pnl"));
                    today.put("avgPnl", rs.getBigDecimal("avg_pnl_per_trade"));
                    today.put("bestTrade", rs.getBigDecimal("best_trade"));
                    today.put("worstTrade", rs.getBigDecimal("worst_trade"));
                    performance.put("today", today);
                }
            }

            // Weekly trend (last 7 days)
            String weeklySql = """
                SELECT
                    DATE(exit_timestamp) as trade_date,
                    COUNT(*) as trades_closed,
                    SUM(realized_pnl) as daily_pnl,
                    ROUND(100.0 * COUNT(CASE WHEN realized_pnl > 0 THEN 1 END) / NULLIF(COUNT(*), 0), 2) as win_rate_pct
                FROM trades
                WHERE status = 'CLOSED'
                  AND exit_timestamp >= CURRENT_DATE - INTERVAL '7 days'
                  AND deleted_at IS NULL
                GROUP BY DATE(exit_timestamp)
                ORDER BY trade_date DESC
                """;

            List<Map<String, Object>> weeklyTrend = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(weeklySql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> day = new HashMap<>();
                    day.put("date", rs.getDate("trade_date").toString());
                    day.put("tradesClosed", rs.getInt("trades_closed"));
                    day.put("dailyPnl", rs.getBigDecimal("daily_pnl"));
                    day.put("winRatePercent", rs.getDouble("win_rate_pct"));
                    weeklyTrend.add(day);
                }
            }
            performance.put("weeklyTrend", weeklyTrend);

            sendJson(exchange, performance);

        } catch (Exception e) {
            log.error("Failed to get performance metrics: {}", e.getMessage(), e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to get performance: " + e.getMessage());
        }
    }

    /**
     * GET /api/monitoring/broker-status
     *
     * Returns broker connection health.
     */
    public void getBrokerStatus(HttpServerExchange exchange) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = """
                SELECT
                    b.broker_name,
                    ub.user_broker_id,
                    u.email as username,
                    ub.is_active,
                    ub.connection_status,
                    ub.session_expiry_at,
                    CASE
                        WHEN ub.session_expiry_at < NOW() THEN 'EXPIRED'
                        WHEN ub.session_expiry_at < NOW() + INTERVAL '1 hour' THEN 'EXPIRING_SOON'
                        ELSE 'VALID'
                    END as session_health,
                    ub.last_connection_at
                FROM user_brokers ub
                JOIN users u ON ub.user_id = u.user_id
                JOIN brokers b ON ub.broker_id = b.broker_id
                WHERE ub.deleted_at IS NULL
                ORDER BY ub.is_active DESC, ub.last_connection_at DESC
                """;

            List<Map<String, Object>> brokers = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> broker = new HashMap<>();
                    broker.put("brokerName", rs.getString("broker_name"));
                    broker.put("userBrokerId", rs.getString("user_broker_id"));
                    broker.put("username", rs.getString("username"));
                    broker.put("isActive", rs.getBoolean("is_active"));
                    broker.put("connectionStatus", rs.getString("connection_status"));
                    broker.put("sessionExpiryAt", rs.getTimestamp("session_expiry_at"));
                    broker.put("sessionHealth", rs.getString("session_health"));
                    broker.put("lastConnectionAt", rs.getTimestamp("last_connection_at"));
                    brokers.add(broker);
                }
            }

            sendJson(exchange, Map.of("brokers", brokers));

        } catch (Exception e) {
            log.error("Failed to get broker status: {}", e.getMessage(), e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to get broker status: " + e.getMessage());
        }
    }

    /**
     * GET /api/monitoring/exit-health
     *
     * Returns exit order execution metrics.
     */
    public void getExitHealth(HttpServerExchange exchange) {
        try (Connection conn = dataSource.getConnection()) {
            Map<String, Object> exitHealth = new HashMap<>();

            // Exit reason breakdown
            String reasonSql = """
                SELECT
                    exit_reason,
                    COUNT(*) as exit_count,
                    ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (), 2) as percentage,
                    AVG(realized_pnl) as avg_pnl,
                    ROUND(100.0 * COUNT(CASE WHEN realized_pnl > 0 THEN 1 END) / NULLIF(COUNT(*), 0), 2) as win_rate_pct
                FROM trades
                WHERE status = 'CLOSED'
                  AND exit_timestamp >= CURRENT_DATE - INTERVAL '7 days'
                  AND deleted_at IS NULL
                GROUP BY exit_reason
                ORDER BY exit_count DESC
                """;

            List<Map<String, Object>> exitReasons = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(reasonSql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> reason = new HashMap<>();
                    reason.put("exitReason", rs.getString("exit_reason"));
                    reason.put("count", rs.getInt("exit_count"));
                    reason.put("percentage", rs.getDouble("percentage"));
                    reason.put("avgPnl", rs.getBigDecimal("avg_pnl"));
                    reason.put("winRatePercent", rs.getDouble("win_rate_pct"));
                    exitReasons.add(reason);
                }
            }
            exitHealth.put("exitReasons", exitReasons);

            // Stuck exit orders (older than 5 minutes)
            String stuckSql = """
                SELECT
                    ei.exit_intent_id,
                    ei.trade_id,
                    t.symbol,
                    ei.exit_reason,
                    ei.status,
                    EXTRACT(EPOCH FROM (NOW() - ei.created_at)) / 60 as age_minutes
                FROM exit_intents ei
                JOIN trades t ON ei.trade_id = t.trade_id
                WHERE ei.status IN ('PENDING', 'APPROVED', 'PLACED')
                  AND ei.created_at < NOW() - INTERVAL '5 minutes'
                  AND ei.deleted_at IS NULL
                ORDER BY ei.created_at ASC
                """;

            List<Map<String, Object>> stuckOrders = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(stuckSql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> stuck = new HashMap<>();
                    stuck.put("exitIntentId", rs.getString("exit_intent_id"));
                    stuck.put("tradeId", rs.getString("trade_id"));
                    stuck.put("symbol", rs.getString("symbol"));
                    stuck.put("exitReason", rs.getString("exit_reason"));
                    stuck.put("status", rs.getString("status"));
                    stuck.put("ageMinutes", rs.getDouble("age_minutes"));
                    stuckOrders.add(stuck);
                }
            }
            exitHealth.put("stuckOrders", stuckOrders);

            sendJson(exchange, exitHealth);

        } catch (Exception e) {
            log.error("Failed to get exit health: {}", e.getMessage(), e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to get exit health: " + e.getMessage());
        }
    }

    /**
     * GET /api/monitoring/risk
     *
     * Returns current risk exposure and concentrations.
     */
    public void getRisk(HttpServerExchange exchange) {
        try (Connection conn = dataSource.getConnection()) {
            Map<String, Object> risk = new HashMap<>();

            // Overall exposure
            String exposureSql = """
                SELECT
                    COUNT(*) as open_trades,
                    SUM(entry_qty * entry_price) as total_notional_value,
                    AVG(entry_qty * entry_price) as avg_position_size,
                    MAX(entry_qty * entry_price) as largest_position,
                    COUNT(DISTINCT symbol) as unique_symbols
                FROM trades
                WHERE status = 'OPEN'
                  AND deleted_at IS NULL
                """;

            try (PreparedStatement stmt = conn.prepareStatement(exposureSql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> exposure = new HashMap<>();
                    exposure.put("openTrades", rs.getInt("open_trades"));
                    exposure.put("totalNotionalValue", rs.getBigDecimal("total_notional_value"));
                    exposure.put("avgPositionSize", rs.getBigDecimal("avg_position_size"));
                    exposure.put("largestPosition", rs.getBigDecimal("largest_position"));
                    exposure.put("uniqueSymbols", rs.getInt("unique_symbols"));
                    risk.put("exposure", exposure);
                }
            }

            // Top concentrations
            String concentrationSql = """
                SELECT
                    symbol,
                    COUNT(*) as open_trades,
                    SUM(entry_qty * entry_price) as total_exposure,
                    ROUND(100.0 * SUM(entry_qty * entry_price) / (
                        SELECT SUM(entry_qty * entry_price)
                        FROM trades
                        WHERE status = 'OPEN' AND deleted_at IS NULL
                    ), 2) as pct_of_total_exposure
                FROM trades
                WHERE status = 'OPEN'
                  AND deleted_at IS NULL
                GROUP BY symbol
                ORDER BY total_exposure DESC
                LIMIT 10
                """;

            List<Map<String, Object>> concentrations = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(concentrationSql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> concentration = new HashMap<>();
                    concentration.put("symbol", rs.getString("symbol"));
                    concentration.put("openTrades", rs.getInt("open_trades"));
                    concentration.put("totalExposure", rs.getBigDecimal("total_exposure"));
                    concentration.put("percentOfTotal", rs.getDouble("pct_of_total_exposure"));
                    concentrations.add(concentration);
                }
            }
            risk.put("topConcentrations", concentrations);

            sendJson(exchange, risk);

        } catch (Exception e) {
            log.error("Failed to get risk metrics: {}", e.getMessage(), e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to get risk metrics: " + e.getMessage());
        }
    }

    /**
     * GET /api/monitoring/errors
     *
     * Returns recent errors and rejections (last 24 hours).
     */
    public void getErrors(HttpServerExchange exchange) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = """
                SELECT
                    'Trade Intent' as source,
                    ti.intent_id as id,
                    ti.symbol,
                    ti.status,
                    ti.error_code,
                    ti.error_message,
                    ti.created_at
                FROM trade_intents ti
                WHERE ti.status = 'REJECTED'
                  AND ti.created_at >= CURRENT_DATE - INTERVAL '24 hours'
                  AND ti.deleted_at IS NULL
                UNION ALL
                SELECT
                    'Exit Intent' as source,
                    ei.exit_intent_id as id,
                    t.symbol,
                    ei.status,
                    ei.error_code,
                    ei.error_message,
                    ei.created_at
                FROM exit_intents ei
                JOIN trades t ON ei.trade_id = t.trade_id
                WHERE ei.status = 'REJECTED'
                  AND ei.created_at >= CURRENT_DATE - INTERVAL '24 hours'
                  AND ei.deleted_at IS NULL
                UNION ALL
                SELECT
                    'Order' as source,
                    o.order_id as id,
                    o.symbol,
                    o.status,
                    o.error_code,
                    o.error_message,
                    o.created_at
                FROM orders o
                WHERE o.status IN ('REJECTED', 'CANCELLED')
                  AND o.created_at >= CURRENT_DATE - INTERVAL '24 hours'
                  AND o.deleted_at IS NULL
                ORDER BY created_at DESC
                LIMIT 50
                """;

            List<Map<String, Object>> errors = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> error = new HashMap<>();
                    error.put("source", rs.getString("source"));
                    error.put("id", rs.getString("id"));
                    error.put("symbol", rs.getString("symbol"));
                    error.put("status", rs.getString("status"));
                    error.put("errorCode", rs.getString("error_code"));
                    error.put("errorMessage", rs.getString("error_message"));
                    error.put("createdAt", rs.getTimestamp("created_at"));
                    errors.add(error);
                }
            }

            sendJson(exchange, Map.of("errors", errors));

        } catch (Exception e) {
            log.error("Failed to get errors: {}", e.getMessage(), e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to get errors: " + e.getMessage());
        }
    }

    /**
     * GET /api/monitoring/alerts
     *
     * Returns active alert conditions (critical issues requiring attention).
     */
    public void getAlerts(HttpServerExchange exchange) {
        try (Connection conn = dataSource.getConnection()) {
            List<Map<String, Object>> alerts = new ArrayList<>();

            // Alert 1: Broker session expired
            String expiredSessionSql = """
                SELECT
                    'BROKER_SESSION_EXPIRED' as alert_type,
                    'CRITICAL' as severity,
                    b.broker_name,
                    ub.user_broker_id,
                    u.email as username,
                    ub.session_expiry_at as expired_at
                FROM user_brokers ub
                JOIN users u ON ub.user_id = u.user_id
                JOIN brokers b ON ub.broker_id = b.broker_id
                WHERE ub.is_active = true
                  AND ub.session_expiry_at < NOW()
                  AND ub.deleted_at IS NULL
                """;

            try (PreparedStatement stmt = conn.prepareStatement(expiredSessionSql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> alert = new HashMap<>();
                    alert.put("alertType", rs.getString("alert_type"));
                    alert.put("severity", rs.getString("severity"));
                    alert.put("message", "Broker session expired: " + rs.getString("broker_name"));
                    alert.put("details", Map.of(
                        "brokerName", rs.getString("broker_name"),
                        "userBrokerId", rs.getString("user_broker_id"),
                        "username", rs.getString("username"),
                        "expiredAt", rs.getTimestamp("expired_at")
                    ));
                    alerts.add(alert);
                }
            }

            // Alert 2: Stuck exit orders
            String stuckOrdersSql = """
                SELECT COUNT(*) as stuck_count
                FROM exit_intents ei
                WHERE ei.status IN ('PENDING', 'APPROVED', 'PLACED')
                  AND ei.created_at < NOW() - INTERVAL '10 minutes'
                  AND ei.deleted_at IS NULL
                """;

            try (PreparedStatement stmt = conn.prepareStatement(stuckOrdersSql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt("stuck_count") > 0) {
                    Map<String, Object> alert = new HashMap<>();
                    alert.put("alertType", "STUCK_EXIT_ORDER");
                    alert.put("severity", "CRITICAL");
                    alert.put("message", rs.getInt("stuck_count") + " exit orders stuck for more than 10 minutes");
                    alert.put("details", Map.of("count", rs.getInt("stuck_count")));
                    alerts.add(alert);
                }
            }

            // Alert 3: Session expiring soon
            String expiringSoonSql = """
                SELECT COUNT(*) as expiring_count
                FROM user_brokers ub
                WHERE ub.is_active = true
                  AND ub.session_expiry_at > NOW()
                  AND ub.session_expiry_at < NOW() + INTERVAL '1 hour'
                  AND ub.deleted_at IS NULL
                """;

            try (PreparedStatement stmt = conn.prepareStatement(expiringSoonSql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt("expiring_count") > 0) {
                    Map<String, Object> alert = new HashMap<>();
                    alert.put("alertType", "BROKER_SESSION_EXPIRING");
                    alert.put("severity", "HIGH");
                    alert.put("message", rs.getInt("expiring_count") + " broker session(s) expiring within 1 hour");
                    alert.put("details", Map.of("count", rs.getInt("expiring_count")));
                    alerts.add(alert);
                }
            }

            sendJson(exchange, Map.of("alerts", alerts));

        } catch (Exception e) {
            log.error("Failed to get alerts: {}", e.getMessage(), e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to get alerts: " + e.getMessage());
        }
    }

    private void sendJson(HttpServerExchange exchange, Object data) throws Exception {
        String json = MAPPER.writeValueAsString(data);
        exchange.setStatusCode(StatusCodes.OK);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
        exchange.getResponseSender().send(json, StandardCharsets.UTF_8);
    }

    private void sendError(HttpServerExchange exchange, int statusCode, String message) {
        exchange.setStatusCode(statusCode);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
        exchange.getResponseSender().send(message, StandardCharsets.UTF_8);
    }
}
