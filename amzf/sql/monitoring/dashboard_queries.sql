-- ============================================================================
-- MONITORING DASHBOARD QUERIES
-- ============================================================================
-- Purpose: Production-ready SQL queries for monitoring trading system health,
--          performance, and identifying issues requiring attention.
-- Usage: Use these queries in your monitoring dashboard (Grafana, Metabase, etc.)
-- ============================================================================

-- ============================================================================
-- 1. REAL-TIME SYSTEM HEALTH
-- ============================================================================

-- 1.1 Active Trades Summary
-- Shows current open trades with P&L and duration
SELECT
    COUNT(*) as total_open_trades,
    SUM(CASE WHEN direction::text = 'BUY' THEN entry_qty ELSE 0 END) as long_positions,
    SUM(CASE WHEN direction::text = 'SELL' THEN entry_qty ELSE 0 END) as short_positions,
    SUM(entry_qty * entry_price) as total_exposure_value,
    AVG(EXTRACT(EPOCH FROM (NOW() - entry_timestamp)) / 3600) as avg_holding_hours
FROM trades
WHERE status = 'OPEN'
  AND deleted_at IS NULL;

-- 1.2 Pending Operations by Type
-- Identifies backlog in order processing pipeline
SELECT
    'Trade Intents' as operation_type,
    COUNT(*) as pending_count,
    MIN(created_at) as oldest_pending,
    EXTRACT(EPOCH FROM (NOW() - MIN(created_at))) / 60 as oldest_age_minutes
FROM trade_intents
WHERE status IN ('PENDING', 'APPROVED')
  AND deleted_at IS NULL
UNION ALL
SELECT
    'Exit Intents' as operation_type,
    COUNT(*) as pending_count,
    MIN(created_at) as oldest_pending,
    EXTRACT(EPOCH FROM (NOW() - MIN(created_at))) / 60 as oldest_age_minutes
FROM exit_intents
WHERE status::text IN ('PENDING', 'APPROVED', 'PLACED')
  AND deleted_at IS NULL
UNION ALL
SELECT
    'Pending Orders' as operation_type,
    COUNT(*) as pending_count,
    MIN(created_at) as oldest_pending,
    EXTRACT(EPOCH FROM (NOW() - MIN(created_at))) / 60 as oldest_age_minutes
FROM orders
WHERE status::text IN ('PENDING', 'PLACED', 'OPEN')
  AND deleted_at IS NULL;

-- 1.3 Broker Connection Status
-- Monitors broker connectivity health
SELECT
    b.broker_name,
    ub.user_broker_id,
    u.username,
    ub.is_active,
    ub.connection_status,
    ub.session_expiry_at,
    CASE
        WHEN ub.session_expiry_at < NOW() THEN 'EXPIRED'
        WHEN ub.session_expiry_at < NOW() + INTERVAL '1 hour' THEN 'EXPIRING_SOON'
        ELSE 'VALID'
    END as session_health,
    ub.last_connection_at,
    EXTRACT(EPOCH FROM (NOW() - ub.last_connection_at)) / 60 as minutes_since_last_connection
FROM user_brokers ub
JOIN users u ON ub.user_id = u.user_id
JOIN brokers b ON ub.broker_id = b.broker_id
WHERE ub.deleted_at IS NULL
ORDER BY ub.is_active DESC, ub.last_connection_at DESC;

-- ============================================================================
-- 2. TRADE PERFORMANCE METRICS
-- ============================================================================

-- 2.1 Daily P&L Summary (Today)
-- Real-time P&L tracking for current trading day
SELECT
    DATE(exit_timestamp) as trade_date,
    COUNT(*) as trades_closed,
    COUNT(CASE WHEN realized_pnl > 0 THEN 1 END) as winning_trades,
    COUNT(CASE WHEN realized_pnl < 0 THEN 1 END) as losing_trades,
    ROUND(100.0 * COUNT(CASE WHEN realized_pnl > 0 THEN 1 END) / NULLIF(COUNT(*), 0), 2) as win_rate_pct,
    SUM(realized_pnl) as total_pnl,
    AVG(realized_pnl) as avg_pnl_per_trade,
    MAX(realized_pnl) as best_trade,
    MIN(realized_pnl) as worst_trade,
    STDDEV(realized_pnl) as pnl_volatility
FROM trades
WHERE status = 'CLOSED'
  AND exit_timestamp >= CURRENT_DATE
  AND deleted_at IS NULL
GROUP BY DATE(exit_timestamp);

-- 2.2 Weekly Performance Trend
-- Last 7 days performance comparison
SELECT
    DATE(exit_timestamp) as trade_date,
    COUNT(*) as trades_closed,
    SUM(realized_pnl) as daily_pnl,
    SUM(SUM(realized_pnl)) OVER (ORDER BY DATE(exit_timestamp)) as cumulative_pnl,
    AVG(realized_pnl) as avg_pnl,
    ROUND(100.0 * COUNT(CASE WHEN realized_pnl > 0 THEN 1 END) / NULLIF(COUNT(*), 0), 2) as win_rate_pct
FROM trades
WHERE status = 'CLOSED'
  AND exit_timestamp >= CURRENT_DATE - INTERVAL '7 days'
  AND deleted_at IS NULL
GROUP BY DATE(exit_timestamp)
ORDER BY trade_date DESC;

-- 2.3 Symbol Performance Leaderboard
-- Best and worst performing symbols
SELECT
    symbol,
    COUNT(*) as total_trades,
    COUNT(CASE WHEN realized_pnl > 0 THEN 1 END) as wins,
    COUNT(CASE WHEN realized_pnl < 0 THEN 1 END) as losses,
    ROUND(100.0 * COUNT(CASE WHEN realized_pnl > 0 THEN 1 END) / NULLIF(COUNT(*), 0), 2) as win_rate_pct,
    SUM(realized_pnl) as total_pnl,
    AVG(realized_pnl) as avg_pnl,
    MAX(realized_pnl) as best_trade,
    MIN(realized_pnl) as worst_trade,
    AVG(holding_days) as avg_holding_days
FROM trades
WHERE status = 'CLOSED'
  AND exit_timestamp >= CURRENT_DATE - INTERVAL '30 days'
  AND deleted_at IS NULL
GROUP BY symbol
HAVING COUNT(*) >= 3  -- Minimum 3 trades for statistical relevance
ORDER BY total_pnl DESC
LIMIT 20;

-- 2.4 Strategy Performance by Entry Signal Type
-- Analyze which signal types are most profitable
SELECT
    s.signal_type,
    s.strategy_name,
    COUNT(t.trade_id) as total_trades,
    SUM(CASE WHEN t.realized_pnl > 0 THEN 1 ELSE 0 END) as winning_trades,
    ROUND(100.0 * SUM(CASE WHEN t.realized_pnl > 0 THEN 1 ELSE 0 END) / NULLIF(COUNT(t.trade_id), 0), 2) as win_rate_pct,
    SUM(t.realized_pnl) as total_pnl,
    AVG(t.realized_pnl) as avg_pnl,
    AVG(t.realized_log_return) as avg_log_return,
    AVG(t.holding_days) as avg_holding_days
FROM trades t
JOIN signals s ON t.signal_id = s.signal_id
WHERE t.status = 'CLOSED'
  AND t.exit_timestamp >= CURRENT_DATE - INTERVAL '30 days'
  AND t.deleted_at IS NULL
GROUP BY s.signal_type, s.strategy_name
ORDER BY total_pnl DESC;

-- ============================================================================
-- 3. EXIT ORDER MONITORING
-- ============================================================================

-- 3.1 Exit Reason Breakdown
-- Which exit conditions are triggering most often
SELECT
    exit_reason,
    COUNT(*) as exit_count,
    ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (), 2) as percentage,
    AVG(realized_pnl) as avg_pnl,
    SUM(realized_pnl) as total_pnl,
    ROUND(100.0 * COUNT(CASE WHEN realized_pnl > 0 THEN 1 END) / NULLIF(COUNT(*), 0), 2) as win_rate_pct
FROM trades
WHERE status = 'CLOSED'
  AND exit_timestamp >= CURRENT_DATE - INTERVAL '7 days'
  AND deleted_at IS NULL
GROUP BY exit_reason
ORDER BY exit_count DESC;

-- 3.2 Exit Order Execution Health
-- Monitor exit order placement and fill latency
SELECT
    ei.exit_reason,
    COUNT(*) as total_exits,
    AVG(EXTRACT(EPOCH FROM (ei.placed_at - ei.created_at))) as avg_placement_latency_sec,
    MAX(EXTRACT(EPOCH FROM (ei.placed_at - ei.created_at))) as max_placement_latency_sec,
    COUNT(CASE WHEN ei.status::text = 'REJECTED' THEN 1 END) as rejected_count,
    COUNT(CASE WHEN ei.status::text = 'FILLED' THEN 1 END) as filled_count,
    COUNT(CASE WHEN ei.status::text IN ('PENDING', 'APPROVED', 'PLACED') THEN 1 END) as still_pending
FROM exit_intents ei
WHERE ei.created_at >= CURRENT_DATE
  AND ei.deleted_at IS NULL
GROUP BY ei.exit_reason
ORDER BY total_exits DESC;

-- 3.3 Stuck Exit Orders
-- Identify exit orders that haven't been filled in reasonable time
SELECT
    ei.exit_intent_id,
    ei.trade_id,
    t.symbol,
    ei.exit_reason,
    ei.status,
    ei.created_at,
    EXTRACT(EPOCH FROM (NOW() - ei.created_at)) / 60 as age_minutes,
    ei.error_message
FROM exit_intents ei
JOIN trades t ON ei.trade_id = t.trade_id
WHERE ei.status::text IN ('PENDING', 'APPROVED', 'PLACED')
  AND ei.created_at < NOW() - INTERVAL '5 minutes'
  AND ei.deleted_at IS NULL
ORDER BY ei.created_at ASC;

-- ============================================================================
-- 4. TRAILING STOPS EFFECTIVENESS
-- ============================================================================

-- 4.1 Trailing Stop Performance
-- Compare trailing stop exits vs other exit types
SELECT
    CASE
        WHEN exit_reason::text = 'TRAILING_STOP' THEN 'Trailing Stop'
        WHEN exit_reason::text IN ('STOP_LOSS', 'MAX_LOSS') THEN 'Stop Loss'
        WHEN exit_reason::text IN ('TARGET_HIT', 'PROFIT_LOCK') THEN 'Profit Target'
        ELSE 'Other'
    END as exit_category,
    COUNT(*) as trade_count,
    AVG(realized_pnl) as avg_pnl,
    SUM(realized_pnl) as total_pnl,
    AVG(realized_log_return * 100) as avg_return_pct,
    AVG(holding_days) as avg_holding_days,
    ROUND(100.0 * COUNT(CASE WHEN realized_pnl > 0 THEN 1 END) / NULLIF(COUNT(*), 0), 2) as win_rate_pct
FROM trades
WHERE status = 'CLOSED'
  AND exit_timestamp >= CURRENT_DATE - INTERVAL '30 days'
  AND deleted_at IS NULL
GROUP BY exit_category
ORDER BY total_pnl DESC;

-- 4.2 Trades That Could Benefit from Trailing Stops
-- Identify trades where price moved significantly in favor but didn't hit trailing stop
SELECT
    t.trade_id,
    t.symbol,
    t.entry_price,
    t.exit_price,
    t.exit_reason,
    t.realized_pnl,
    t.realized_log_return * 100 as return_pct,
    -- Estimate potential if trailing stop was used
    CASE
        WHEN t.direction::text = 'BUY' THEN
            ROUND((t.exit_price / t.entry_price - 1) * 100, 2)
        WHEN t.direction::text = 'SELL' THEN
            ROUND((1 - t.exit_price / t.entry_price) * 100, 2)
    END as price_move_pct
FROM trades t
WHERE t.status = 'CLOSED'
  AND t.exit_reason::text NOT IN ('TRAILING_STOP', 'PROFIT_LOCK')
  AND t.exit_timestamp >= CURRENT_DATE - INTERVAL '7 days'
  AND t.deleted_at IS NULL
  AND (
      (t.direction::text = 'BUY' AND t.exit_price / t.entry_price > 1.02)  -- More than 2% profit
      OR (t.direction::text = 'SELL' AND t.entry_price / t.exit_price > 1.02)
  )
ORDER BY t.exit_timestamp DESC
LIMIT 20;

-- ============================================================================
-- 5. RISK MONITORING
-- ============================================================================

-- 5.1 Current Risk Exposure
-- Calculate total exposure and risk metrics for open trades
SELECT
    COUNT(*) as open_trades,
    SUM(entry_qty * entry_price) as total_notional_value,
    AVG(entry_qty * entry_price) as avg_position_size,
    MAX(entry_qty * entry_price) as largest_position,
    COUNT(DISTINCT symbol) as unique_symbols,
    COUNT(DISTINCT user_id) as active_users
FROM trades
WHERE status = 'OPEN'
  AND deleted_at IS NULL;

-- 5.2 Top Risk Concentrations
-- Identify largest positions by symbol
SELECT
    symbol,
    COUNT(*) as open_trades,
    SUM(entry_qty) as total_qty,
    SUM(entry_qty * entry_price) as total_exposure,
    AVG(entry_price) as avg_entry_price,
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
LIMIT 10;

-- 5.3 Drawdown Analysis
-- Track maximum drawdown and recovery
WITH daily_pnl AS (
    SELECT
        DATE(exit_timestamp) as trade_date,
        SUM(realized_pnl) as daily_pnl
    FROM trades
    WHERE status = 'CLOSED'
      AND exit_timestamp >= CURRENT_DATE - INTERVAL '30 days'
      AND deleted_at IS NULL
    GROUP BY DATE(exit_timestamp)
),
cumulative_pnl AS (
    SELECT
        trade_date,
        daily_pnl,
        SUM(daily_pnl) OVER (ORDER BY trade_date) as cumulative_pnl,
        MAX(SUM(daily_pnl)) OVER (ORDER BY trade_date) as peak_pnl
    FROM daily_pnl
)
SELECT
    trade_date,
    daily_pnl,
    cumulative_pnl,
    peak_pnl,
    cumulative_pnl - peak_pnl as drawdown,
    CASE
        WHEN peak_pnl > 0 THEN ROUND(100.0 * (cumulative_pnl - peak_pnl) / peak_pnl, 2)
        ELSE 0
    END as drawdown_pct
FROM cumulative_pnl
ORDER BY trade_date DESC;

-- ============================================================================
-- 6. ERROR AND REJECTION MONITORING
-- ============================================================================

-- 6.1 Recent Errors and Rejections
-- Track failed operations for troubleshooting
SELECT
    'Trade Intent' as source,
    ti.intent_id as id,
    ti.symbol,
    ti.status,
    ti.error_code,
    ti.error_message,
    ti.created_at,
    ti.updated_at
FROM trade_intents ti
WHERE ti.status::text = 'REJECTED'
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
    ei.created_at,
    ei.updated_at
FROM exit_intents ei
JOIN trades t ON ei.trade_id = t.trade_id
WHERE ei.status::text = 'REJECTED'
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
    o.created_at,
    o.updated_at
FROM orders o
WHERE o.status::text IN ('REJECTED', 'CANCELLED')
  AND o.created_at >= CURRENT_DATE - INTERVAL '24 hours'
  AND o.deleted_at IS NULL
ORDER BY created_at DESC;

-- 6.2 Error Rate by Type
-- Aggregate error patterns for alerting
SELECT
    error_code,
    COUNT(*) as error_count,
    MIN(error_message) as sample_message,
    MIN(created_at) as first_seen,
    MAX(created_at) as last_seen
FROM (
    SELECT error_code, error_message, created_at FROM trade_intents WHERE status::text = 'REJECTED' AND deleted_at IS NULL
    UNION ALL
    SELECT error_code, error_message, created_at FROM exit_intents WHERE status::text = 'REJECTED' AND deleted_at IS NULL
    UNION ALL
    SELECT error_code, error_message, created_at FROM orders WHERE status::text IN ('REJECTED', 'CANCELLED') AND deleted_at IS NULL
) errors
WHERE created_at >= CURRENT_DATE - INTERVAL '24 hours'
GROUP BY error_code
ORDER BY error_count DESC;

-- ============================================================================
-- 7. RECONCILIATION HEALTH
-- ============================================================================

-- 7.1 Order Reconciliation Status
-- Monitor orders that need reconciliation with broker
SELECT
    o.status,
    o.reconcile_status,
    COUNT(*) as order_count,
    MIN(o.last_broker_update_at) as oldest_update,
    MAX(o.last_broker_update_at) as newest_update,
    AVG(EXTRACT(EPOCH FROM (NOW() - o.last_broker_update_at)) / 60) as avg_age_minutes
FROM orders o
WHERE o.status::text IN ('PLACED', 'OPEN')
  AND o.deleted_at IS NULL
GROUP BY o.status, o.reconcile_status;

-- 7.2 Stale Orders Needing Reconciliation
-- Identify orders with no broker update for extended period
SELECT
    o.order_id,
    o.order_type,
    o.symbol,
    o.status,
    o.broker_order_id,
    o.reconcile_status,
    o.reconcile_attempt_count,
    o.last_broker_update_at,
    EXTRACT(EPOCH FROM (NOW() - o.last_broker_update_at)) / 60 as minutes_since_update,
    o.error_message
FROM orders o
WHERE o.status::text IN ('PLACED', 'OPEN')
  AND o.last_broker_update_at < NOW() - INTERVAL '10 minutes'
  AND o.deleted_at IS NULL
ORDER BY o.last_broker_update_at ASC;
