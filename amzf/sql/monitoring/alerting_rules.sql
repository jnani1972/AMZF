-- ============================================================================
-- ALERTING RULES
-- ============================================================================
-- Purpose: SQL queries that return non-empty results when alert conditions are met.
--          Integrate with alerting systems (PagerDuty, Slack, Email, etc.)
-- Usage: Run these queries periodically (e.g., every 1-5 minutes)
--        Alert when query returns any rows
-- ============================================================================

-- ============================================================================
-- CRITICAL ALERTS (P0) - Immediate Action Required
-- ============================================================================

-- ALERT: Broker Session Expired
-- Triggers when broker session has expired and trading is blocked
-- Severity: CRITICAL
-- Action: Re-authenticate with broker immediately
SELECT
    'BROKER_SESSION_EXPIRED' as alert_type,
    'CRITICAL' as severity,
    b.broker_name,
    ub.user_broker_id,
    u.username,
    ub.session_expiry_at as expired_at,
    EXTRACT(EPOCH FROM (NOW() - ub.session_expiry_at)) / 60 as expired_minutes_ago,
    'Broker session expired. Re-authentication required.' as message
FROM user_brokers ub
JOIN users u ON ub.user_id = u.user_id
JOIN brokers b ON ub.broker_id = b.broker_id
WHERE ub.is_active = true
  AND ub.session_expiry_at < NOW()
  AND ub.deleted_at IS NULL;

-- ALERT: Stuck Exit Orders
-- Triggers when exit orders haven't been filled for more than 10 minutes
-- Severity: CRITICAL
-- Action: Investigate broker connectivity, manually reconcile if needed
SELECT
    'STUCK_EXIT_ORDER' as alert_type,
    'CRITICAL' as severity,
    ei.exit_intent_id,
    ei.trade_id,
    t.symbol,
    ei.exit_reason,
    ei.status,
    EXTRACT(EPOCH FROM (NOW() - ei.created_at)) / 60 as stuck_minutes,
    'Exit order stuck for more than 10 minutes. Check broker connectivity.' as message,
    ei.error_message
FROM exit_intents ei
JOIN trades t ON ei.trade_id = t.trade_id
WHERE ei.status::text IN ('PENDING', 'APPROVED', 'PLACED')
  AND ei.created_at < NOW() - INTERVAL '10 minutes'
  AND ei.deleted_at IS NULL
ORDER BY ei.created_at ASC;

-- ALERT: Trade Reconciliation Failures
-- Triggers when order reconciliation fails repeatedly
-- Severity: CRITICAL
-- Action: Manual reconciliation required, check broker API status
SELECT
    'RECONCILIATION_FAILURE' as alert_type,
    'CRITICAL' as severity,
    o.order_id,
    o.order_type,
    o.symbol,
    o.broker_order_id,
    o.reconcile_attempt_count,
    o.reconcile_status,
    EXTRACT(EPOCH FROM (NOW() - o.last_reconcile_at)) / 60 as minutes_since_last_attempt,
    'Order reconciliation failed after multiple attempts.' as message,
    o.error_message
FROM orders o
WHERE o.reconcile_status = 'FAILED'
  AND o.reconcile_attempt_count >= 3
  AND o.deleted_at IS NULL
ORDER BY o.reconcile_attempt_count DESC, o.last_reconcile_at ASC;

-- ALERT: Database Connection Lost
-- Triggers when database hasn't been updated recently (data freshness check)
-- Severity: CRITICAL
-- Action: Check database connectivity and application health
SELECT
    'DATABASE_STALE' as alert_type,
    'CRITICAL' as severity,
    MAX(updated_at) as last_update,
    EXTRACT(EPOCH FROM (NOW() - MAX(updated_at))) / 60 as stale_minutes,
    'No database updates in last 5 minutes. System may be down.' as message
FROM (
    SELECT updated_at FROM trades WHERE deleted_at IS NULL
    UNION ALL
    SELECT updated_at FROM trade_intents WHERE deleted_at IS NULL
    UNION ALL
    SELECT updated_at FROM exit_intents WHERE deleted_at IS NULL
    UNION ALL
    SELECT updated_at FROM orders WHERE deleted_at IS NULL
) all_updates
HAVING MAX(updated_at) < NOW() - INTERVAL '5 minutes';

-- ============================================================================
-- HIGH PRIORITY ALERTS (P1) - Action Required Soon
-- ============================================================================

-- ALERT: High Error Rate
-- Triggers when error rate exceeds 20% in last hour
-- Severity: HIGH
-- Action: Investigate error patterns, check broker API health
WITH recent_operations AS (
    SELECT COUNT(*) as total_ops FROM trade_intents WHERE created_at >= NOW() - INTERVAL '1 hour' AND deleted_at IS NULL
    UNION ALL
    SELECT COUNT(*) FROM exit_intents WHERE created_at >= NOW() - INTERVAL '1 hour' AND deleted_at IS NULL
    UNION ALL
    SELECT COUNT(*) FROM orders WHERE created_at >= NOW() - INTERVAL '1 hour' AND deleted_at IS NULL
),
recent_errors AS (
    SELECT COUNT(*) as error_count FROM trade_intents WHERE status::text = 'REJECTED' AND created_at >= NOW() - INTERVAL '1 hour' AND deleted_at IS NULL
    UNION ALL
    SELECT COUNT(*) FROM exit_intents WHERE status::text = 'REJECTED' AND created_at >= NOW() - INTERVAL '1 hour' AND deleted_at IS NULL
    UNION ALL
    SELECT COUNT(*) FROM orders WHERE status::text IN ('REJECTED', 'CANCELLED') AND created_at >= NOW() - INTERVAL '1 hour' AND deleted_at IS NULL
)
SELECT
    'HIGH_ERROR_RATE' as alert_type,
    'HIGH' as severity,
    SUM(error_count) as total_errors,
    (SELECT SUM(total_ops) FROM recent_operations) as total_operations,
    ROUND(100.0 * SUM(error_count) / NULLIF((SELECT SUM(total_ops) FROM recent_operations), 0), 2) as error_rate_pct,
    'Error rate exceeds 20% in last hour.' as message
FROM recent_errors
HAVING ROUND(100.0 * SUM(error_count) / NULLIF((SELECT SUM(total_ops) FROM recent_operations), 0), 2) > 20;

-- ALERT: Session Expiring Soon
-- Triggers when broker session expires within 1 hour
-- Severity: HIGH
-- Action: Schedule re-authentication before expiry
SELECT
    'BROKER_SESSION_EXPIRING' as alert_type,
    'HIGH' as severity,
    b.broker_name,
    ub.user_broker_id,
    u.username,
    ub.session_expiry_at,
    EXTRACT(EPOCH FROM (ub.session_expiry_at - NOW())) / 60 as expires_in_minutes,
    'Broker session expiring within 1 hour. Re-authenticate to prevent interruption.' as message
FROM user_brokers ub
JOIN users u ON ub.user_id = u.user_id
JOIN brokers b ON ub.broker_id = b.broker_id
WHERE ub.is_active = true
  AND ub.session_expiry_at > NOW()
  AND ub.session_expiry_at < NOW() + INTERVAL '1 hour'
  AND ub.deleted_at IS NULL;

-- ALERT: Large Drawdown Detected
-- Triggers when daily drawdown exceeds 5%
-- Severity: HIGH
-- Action: Review trading strategy, consider reducing exposure
WITH today_pnl AS (
    SELECT SUM(realized_pnl) as total_pnl
    FROM trades
    WHERE status = 'CLOSED'
      AND DATE(exit_timestamp) = CURRENT_DATE
      AND deleted_at IS NULL
),
peak_pnl AS (
    SELECT MAX(SUM(realized_pnl)) OVER () as peak
    FROM trades
    WHERE status = 'CLOSED'
      AND exit_timestamp >= CURRENT_DATE - INTERVAL '30 days'
      AND deleted_at IS NULL
    GROUP BY DATE(exit_timestamp)
)
SELECT
    'LARGE_DRAWDOWN' as alert_type,
    'HIGH' as severity,
    t.total_pnl as current_daily_pnl,
    p.peak as recent_peak_daily_pnl,
    t.total_pnl - p.peak as drawdown_amount,
    ROUND(100.0 * (t.total_pnl - p.peak) / NULLIF(p.peak, 0), 2) as drawdown_pct,
    'Daily P&L drawdown exceeds 5% from recent peak.' as message
FROM today_pnl t, peak_pnl p
WHERE p.peak > 0
  AND t.total_pnl < p.peak * 0.95;  -- 5% drawdown threshold

-- ALERT: Win Rate Dropping
-- Triggers when win rate drops below 40% over last 50 trades
-- Severity: HIGH
-- Action: Review strategy effectiveness, adjust parameters
WITH recent_trades AS (
    SELECT
        COUNT(*) as total_trades,
        COUNT(CASE WHEN realized_pnl > 0 THEN 1 END) as winning_trades
    FROM (
        SELECT realized_pnl
        FROM trades
        WHERE status = 'CLOSED'
          AND deleted_at IS NULL
        ORDER BY exit_timestamp DESC
        LIMIT 50
    ) last_50
)
SELECT
    'LOW_WIN_RATE' as alert_type,
    'HIGH' as severity,
    winning_trades,
    total_trades,
    ROUND(100.0 * winning_trades / NULLIF(total_trades, 0), 2) as win_rate_pct,
    'Win rate dropped below 40% over last 50 trades.' as message
FROM recent_trades
WHERE total_trades >= 50
  AND ROUND(100.0 * winning_trades / NULLIF(total_trades, 0), 2) < 40;

-- ALERT: Excessive Risk Concentration
-- Triggers when single symbol exposure exceeds 30% of total
-- Severity: HIGH
-- Action: Reduce position sizes, diversify
WITH total_exposure AS (
    SELECT SUM(entry_qty * entry_price) as total
    FROM trades
    WHERE status = 'OPEN' AND deleted_at IS NULL
)
SELECT
    'EXCESSIVE_CONCENTRATION' as alert_type,
    'HIGH' as severity,
    t.symbol,
    COUNT(*) as open_trades,
    SUM(t.entry_qty * t.entry_price) as symbol_exposure,
    te.total as total_exposure,
    ROUND(100.0 * SUM(t.entry_qty * t.entry_price) / NULLIF(te.total, 0), 2) as concentration_pct,
    'Single symbol exposure exceeds 30% of total portfolio.' as message
FROM trades t, total_exposure te
WHERE t.status = 'OPEN'
  AND t.deleted_at IS NULL
GROUP BY t.symbol, te.total
HAVING ROUND(100.0 * SUM(t.entry_qty * t.entry_price) / NULLIF(te.total, 0), 2) > 30
ORDER BY concentration_pct DESC;

-- ============================================================================
-- MEDIUM PRIORITY ALERTS (P2) - Review and Monitor
-- ============================================================================

-- ALERT: Stale Data Broker Connection
-- Triggers when data broker hasn't been used in 2 hours
-- Severity: MEDIUM
-- Action: Verify data feed is working, check for tick updates
SELECT
    'STALE_DATA_BROKER' as alert_type,
    'MEDIUM' as severity,
    ub.user_broker_id,
    b.broker_name,
    ub.last_connection_at,
    EXTRACT(EPOCH FROM (NOW() - ub.last_connection_at)) / 3600 as hours_since_connection,
    'Data broker connection appears stale (no activity for 2+ hours).' as message
FROM user_brokers ub
JOIN brokers b ON ub.broker_id = b.broker_id
WHERE ub.is_active = true
  AND ub.is_data_broker = true
  AND ub.last_connection_at < NOW() - INTERVAL '2 hours'
  AND ub.deleted_at IS NULL;

-- ALERT: Slow Order Placement
-- Triggers when average order placement latency exceeds 2 seconds
-- Severity: MEDIUM
-- Action: Investigate broker API performance, network latency
WITH placement_latency AS (
    SELECT
        AVG(EXTRACT(EPOCH FROM (placed_at - created_at))) as avg_latency_sec,
        COUNT(*) as order_count
    FROM exit_intents
    WHERE created_at >= NOW() - INTERVAL '1 hour'
      AND placed_at IS NOT NULL
      AND deleted_at IS NULL
)
SELECT
    'SLOW_ORDER_PLACEMENT' as alert_type,
    'MEDIUM' as severity,
    ROUND(avg_latency_sec, 2) as avg_latency_seconds,
    order_count as orders_in_last_hour,
    'Average order placement latency exceeds 2 seconds.' as message
FROM placement_latency
WHERE avg_latency_sec > 2.0
  AND order_count >= 5;

-- ALERT: Unusual Rejection Pattern
-- Triggers when same error code appears 5+ times in last hour
-- Severity: MEDIUM
-- Action: Investigate root cause, check broker requirements
WITH error_counts AS (
    SELECT error_code, COUNT(*) as error_count
    FROM (
        SELECT error_code FROM trade_intents WHERE status::text = 'REJECTED' AND created_at >= NOW() - INTERVAL '1 hour' AND deleted_at IS NULL
        UNION ALL
        SELECT error_code FROM exit_intents WHERE status::text = 'REJECTED' AND created_at >= NOW() - INTERVAL '1 hour' AND deleted_at IS NULL
        UNION ALL
        SELECT error_code FROM orders WHERE status::text IN ('REJECTED', 'CANCELLED') AND created_at >= NOW() - INTERVAL '1 hour' AND deleted_at IS NULL
    ) all_errors
    WHERE error_code IS NOT NULL
    GROUP BY error_code
)
SELECT
    'UNUSUAL_REJECTION_PATTERN' as alert_type,
    'MEDIUM' as severity,
    error_code,
    error_count as occurrences_last_hour,
    'Same error code repeated 5+ times in last hour.' as message
FROM error_counts
WHERE error_count >= 5
ORDER BY error_count DESC;

-- ALERT: No Trades Today
-- Triggers when no trades have been closed today (market hours only: 9:15 AM - 3:30 PM IST)
-- Severity: MEDIUM
-- Action: Verify strategy is generating signals, check data feed
SELECT
    'NO_TRADES_TODAY' as alert_type,
    'MEDIUM' as severity,
    CURRENT_DATE as date,
    EXTRACT(HOUR FROM NOW()) as current_hour_ist,
    'No trades closed today during market hours.' as message
WHERE NOT EXISTS (
    SELECT 1 FROM trades
    WHERE status = 'CLOSED'
      AND DATE(exit_timestamp) = CURRENT_DATE
      AND deleted_at IS NULL
)
AND EXTRACT(HOUR FROM NOW() AT TIME ZONE 'Asia/Kolkata') >= 11  -- After 11 AM IST (market open for 2 hours)
AND EXTRACT(HOUR FROM NOW() AT TIME ZONE 'Asia/Kolkata') < 15;  -- Before 3 PM IST

-- ALERT: Open Trades Held Overnight
-- Triggers when intraday (MIS) positions are still open after market close
-- Severity: MEDIUM
-- Action: Verify auto-squareoff is working, manually close if needed
SELECT
    'OVERNIGHT_MIS_POSITION' as alert_type,
    'MEDIUM' as severity,
    t.trade_id,
    t.symbol,
    t.product_type,
    t.entry_timestamp,
    EXTRACT(HOUR FROM (NOW() - t.entry_timestamp)) as hours_held,
    'Intraday (MIS) position held beyond market hours.' as message
FROM trades t
WHERE t.status = 'OPEN'
  AND t.product_type::text = 'MIS'
  AND t.entry_timestamp < CURRENT_DATE  -- Entered before today
  AND t.deleted_at IS NULL;

-- ============================================================================
-- LOW PRIORITY ALERTS (P3) - Informational
-- ============================================================================

-- ALERT: Low Activity Day
-- Triggers when signal generation is low
-- Severity: LOW
-- Action: Review strategy parameters, check if market conditions changed
WITH today_signals AS (
    SELECT COUNT(*) as signal_count
    FROM signals
    WHERE DATE(generated_at) = CURRENT_DATE
      AND deleted_at IS NULL
)
SELECT
    'LOW_ACTIVITY_DAY' as alert_type,
    'LOW' as severity,
    signal_count as signals_today,
    'Fewer than 10 signals generated today.' as message
FROM today_signals
WHERE signal_count < 10
  AND EXTRACT(HOUR FROM NOW() AT TIME ZONE 'Asia/Kolkata') >= 14;  -- After 2 PM IST

-- ALERT: Trailing Stop Not Used
-- Triggers when no trailing stop exits in last 7 days but feature is enabled
-- Severity: LOW
-- Action: Verify trailing stop configuration is correct
SELECT
    'TRAILING_STOP_UNUSED' as alert_type,
    'LOW' as severity,
    COUNT(*) as total_exits_last_7_days,
    0 as trailing_stop_exits,
    'No trailing stop exits in last 7 days. Verify configuration.' as message
FROM trades
WHERE status = 'CLOSED'
  AND exit_timestamp >= CURRENT_DATE - INTERVAL '7 days'
  AND deleted_at IS NULL
GROUP BY 1, 2, 4
HAVING COUNT(*) >= 20  -- Only alert if significant number of trades
  AND NOT EXISTS (
      SELECT 1 FROM trades
      WHERE status = 'CLOSED'
        AND exit_reason::text = 'TRAILING_STOP'
        AND exit_timestamp >= CURRENT_DATE - INTERVAL '7 days'
        AND deleted_at IS NULL
  );

-- ============================================================================
-- USAGE NOTES
-- ============================================================================

/*
Integration Examples:

1. Cron Job with Email Alerts:
```bash
#!/bin/bash
# Run every 5 minutes
psql -d annupaper -f /path/to/alerting_rules.sql | \
  mail -s "AnnuPaper Alerts" admin@example.com
```

2. Grafana Alerting:
- Create dashboard panels with these queries
- Configure alert rules on each panel
- Set notification channels (Email, Slack, PagerDuty)

3. Custom Monitoring Script:
```python
import psycopg2
import requests

conn = psycopg2.connect("dbname=annupaper user=postgres")
cur = conn.cursor()

# Run each alert query
cur.execute(open('alerting_rules.sql').read())
alerts = cur.fetchall()

for alert in alerts:
    # Send to Slack, PagerDuty, etc.
    requests.post(SLACK_WEBHOOK, json={
        'text': f"{alert['severity']}: {alert['message']}"
    })
```

4. Prometheus + AlertManager:
- Export metrics using postgres_exporter
- Define alert rules in AlertManager
- Route to appropriate notification channels

Recommended Alert Frequencies:
- CRITICAL (P0): Every 1 minute
- HIGH (P1): Every 5 minutes
- MEDIUM (P2): Every 15 minutes
- LOW (P3): Every 1 hour
*/
