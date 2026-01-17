-- ============================================================================
-- V011: Unified Orders Table
-- ============================================================================
-- Purpose: Create unified orders table for both entry and exit orders
-- Date: 2026-01-13
--
-- MOTIVATION:
-- Currently, entry orders tracked in trades.broker_order_id and exit orders
-- in exit_intents.broker_order_id. This creates:
-- - Duplicate reconciliation logic (PendingOrderReconciler + ExitOrderReconciler)
-- - No unified view of all orders
-- - Harder to audit order history
--
-- SOLUTION:
-- Unified `orders` table that tracks ALL broker orders (entry + exit)
-- with proper lifecycle management and reconciliation tracking.
-- ============================================================================

BEGIN;

-- ============================================================================
-- DOMAIN TYPES: Eliminate String Literal Duplication (SonarQube Compliance)
-- ============================================================================

DO $$
BEGIN
    -- Order Type
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'order_type_type') THEN
        CREATE DOMAIN order_type_type AS VARCHAR(10)
        CHECK (VALUE IN ('ENTRY', 'EXIT'));
    END IF;

    -- Product Type
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'product_type_type') THEN
        CREATE DOMAIN product_type_type AS VARCHAR(20)
        CHECK (VALUE IN ('MIS', 'CNC', 'NRML'));
    END IF;

    -- Price Type
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'price_type_type') THEN
        CREATE DOMAIN price_type_type AS VARCHAR(20)
        CHECK (VALUE IN ('MARKET', 'LIMIT', 'SL', 'SL-M'));
    END IF;

    -- Order Status Type
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'order_status_type') THEN
        CREATE DOMAIN order_status_type AS VARCHAR(20) DEFAULT 'PENDING'
        CHECK (VALUE IN ('PENDING', 'PLACED', 'OPEN', 'COMPLETE', 'REJECTED', 'CANCELLED', 'EXPIRED'));
    END IF;
END $$;

-- ============================================================================
-- 1. UNIFIED ORDERS TABLE
-- ============================================================================

CREATE TABLE IF NOT EXISTS orders (
    -- Identity
    order_id VARCHAR(36) PRIMARY KEY,           -- Internal order ID (UUID)

    -- Order classification
    order_type order_type_type NOT NULL,        -- ENTRY | EXIT
    trade_id VARCHAR(36),                       -- Reference to trade (NULL for entry before trade created)
    intent_id VARCHAR(36),                      -- Reference to trade_intent (for entry)
    exit_intent_id VARCHAR(36),                 -- Reference to exit_intent (for exit)
    signal_id VARCHAR(36),                      -- Original signal (entry) or exit_signal (exit)

    -- User-Broker binding
    user_id VARCHAR(36) NOT NULL,
    broker_id VARCHAR(36) NOT NULL,
    user_broker_id VARCHAR(36) NOT NULL,  -- FK removed: user_brokers has composite PK (user_broker_id, version)

    -- Order details
    symbol VARCHAR(50) NOT NULL,
    exchange VARCHAR(20) NOT NULL DEFAULT 'NSE',
    direction trade_direction_type NOT NULL,    -- BUY | SELL
    transaction_type trade_direction_type NOT NULL, -- BUY | SELL (same as direction for clarity)
    product_type product_type_type NOT NULL,    -- MIS | CNC | NRML
    order_variety VARCHAR(20) NOT NULL DEFAULT 'REGULAR', -- REGULAR | AMO | ICEBERG

    -- Pricing
    price_type price_type_type NOT NULL,        -- MARKET | LIMIT | SL | SL-M
    limit_price NUMERIC(20,2),
    trigger_price NUMERIC(20,2),
    disclosed_qty INTEGER,

    -- Quantity
    ordered_qty INTEGER NOT NULL,
    filled_qty INTEGER DEFAULT 0,
    pending_qty INTEGER,                        -- ordered_qty - filled_qty
    cancelled_qty INTEGER DEFAULT 0,

    -- Pricing (fill details)
    avg_fill_price NUMERIC(20,2),
    fill_value NUMERIC(20,2),                   -- avg_fill_price * filled_qty

    -- Broker tracking
    broker_order_id VARCHAR(100),               -- Broker's order ID
    broker_trade_id VARCHAR(100),               -- Broker's trade/fill ID
    client_order_id VARCHAR(100) UNIQUE,        -- Our intent_id/exit_intent_id (idempotency)

    -- Order lifecycle
    status order_status_type NOT NULL,          -- PENDING | PLACED | OPEN | COMPLETE | REJECTED | CANCELLED | EXPIRED
    validity VARCHAR(20) NOT NULL DEFAULT 'DAY', -- DAY | IOC | GTT

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    placed_at TIMESTAMP,                        -- When order submitted to broker
    acknowledged_at TIMESTAMP,                  -- When broker confirmed receipt
    completed_at TIMESTAMP,                     -- When order fully filled
    cancelled_at TIMESTAMP,
    expired_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_broker_update_at TIMESTAMP,           -- For reconciliation timeout

    -- Rejection/Error details
    rejection_reason TEXT,
    error_code VARCHAR(50),
    error_message TEXT,

    -- Reconciliation tracking
    reconcile_attempt_count INTEGER DEFAULT 0,
    last_reconcile_at TIMESTAMP,
    reconcile_status VARCHAR(20) DEFAULT 'PENDING', -- PENDING | IN_SYNC | OUT_OF_SYNC | FAILED

    -- Execution context
    execution_source VARCHAR(50),               -- ORDER_EXECUTION_SERVICE | EXIT_ORDER_EXECUTION_SERVICE
    execution_reason TEXT,                      -- e.g., "TARGET_HIT", "STOP_LOSS", "MANUAL"

    -- Metadata
    tags TEXT[],
    notes TEXT,

    -- Audit trail
    deleted_at TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 1,

    -- Constraints (domain types enforce enum values)
    CHECK (ordered_qty > 0),
    CHECK (filled_qty >= 0),
    CHECK (filled_qty <= ordered_qty),
    CHECK (pending_qty = ordered_qty - filled_qty OR pending_qty IS NULL)
);

COMMENT ON TABLE orders IS E'Unified orders table tracking both entry and exit broker orders.\nReplaces fragmented tracking in trades.broker_order_id and exit_intents.broker_order_id.\nSingle source of truth for all order lifecycle and reconciliation.';

-- ============================================================================
-- 2. INDEXES FOR PERFORMANCE
-- ============================================================================

-- Fast lookups by broker order ID (reconciliation)
CREATE UNIQUE INDEX idx_orders_broker_order_id
    ON orders(broker_order_id)
    WHERE broker_order_id IS NOT NULL AND deleted_at IS NULL;

-- Fast lookups by client order ID (idempotency)
CREATE UNIQUE INDEX idx_orders_client_order_id
    ON orders(client_order_id)
    WHERE client_order_id IS NOT NULL AND deleted_at IS NULL;

-- Fast queries for pending orders (reconciliation loop)
CREATE INDEX idx_orders_reconcile
    ON orders(status, last_broker_update_at, updated_at)
    WHERE status::text IN ('PLACED', 'OPEN') AND deleted_at IS NULL;

-- Fast queries by trade (order history)
CREATE INDEX idx_orders_trade_id
    ON orders(trade_id, order_type, created_at DESC)
    WHERE trade_id IS NOT NULL AND deleted_at IS NULL;

-- Fast queries by user-broker (user view)
CREATE INDEX idx_orders_user_broker
    ON orders(user_broker_id, status, created_at DESC)
    WHERE deleted_at IS NULL;

-- Fast queries by symbol (market view)
CREATE INDEX idx_orders_symbol_status
    ON orders(symbol, status, created_at DESC)
    WHERE deleted_at IS NULL;

-- Fast queries for intent/exit_intent reference
CREATE INDEX idx_orders_intent_id
    ON orders(intent_id)
    WHERE intent_id IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX idx_orders_exit_intent_id
    ON orders(exit_intent_id)
    WHERE exit_intent_id IS NOT NULL AND deleted_at IS NULL;

-- ============================================================================
-- 3. ORDER FILLS TABLE (OPTIONAL - FOR PARTIAL FILLS TRACKING)
-- ============================================================================

CREATE TABLE IF NOT EXISTS order_fills (
    fill_id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL REFERENCES orders(order_id),
    trade_id VARCHAR(36),                       -- Reference to trade

    -- Fill details
    fill_qty INTEGER NOT NULL,
    fill_price NUMERIC(20,2) NOT NULL,
    fill_value NUMERIC(20,2) NOT NULL,          -- fill_qty * fill_price
    fill_timestamp TIMESTAMP NOT NULL,

    -- Broker tracking
    broker_fill_id VARCHAR(100),                -- Broker's fill/execution ID
    exchange_order_id VARCHAR(100),             -- Exchange order ID
    exchange_timestamp TIMESTAMP,

    -- Fill metadata
    fill_type VARCHAR(20),                      -- PARTIAL | COMPLETE
    execution_venue VARCHAR(50),                -- NSE | BSE

    -- Audit
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CHECK (fill_qty > 0)
);

COMMENT ON TABLE order_fills IS E'Granular fill tracking for partial fills. Each broker fill creates one row.\nAllows precise reconstruction of order execution history.';

CREATE INDEX idx_fills_order_id ON order_fills(order_id, fill_timestamp DESC);
CREATE INDEX idx_fills_trade_id ON order_fills(trade_id, fill_timestamp DESC) WHERE trade_id IS NOT NULL;

-- ============================================================================
-- 4. MIGRATION HELPERS - POPULATE FROM EXISTING DATA
-- ============================================================================

-- Populate orders table from existing trades (entry orders)
INSERT INTO orders (
    order_id,
    order_type,
    trade_id,
    intent_id,
    signal_id,
    user_id,
    broker_id,
    user_broker_id,
    symbol,
    direction,
    transaction_type,
    product_type,
    price_type,
    limit_price,
    ordered_qty,
    filled_qty,
    avg_fill_price,
    broker_order_id,
    broker_trade_id,
    client_order_id,
    status,
    created_at,
    placed_at,
    completed_at,
    updated_at,
    last_broker_update_at,
    execution_source,
    version
)
SELECT
    gen_random_uuid(),                          -- order_id (new)
    'ENTRY',                                    -- order_type
    trade_id,
    intent_id,
    signal_id,
    user_id,
    broker_id,
    user_broker_id,
    symbol,
    direction,
    direction,                                  -- transaction_type same as direction
    product_type,
    'LIMIT',                                    -- Assume LIMIT (most common)
    entry_price,
    entry_qty,
    entry_qty,                                  -- filled_qty (assume full fill)
    entry_price,
    broker_order_id,
    broker_trade_id,
    client_order_id,
    CASE status
        WHEN 'OPEN' THEN 'COMPLETE'
        WHEN 'CLOSED' THEN 'COMPLETE'
        WHEN 'CREATED' THEN 'PENDING'
        WHEN 'PENDING' THEN 'OPEN'
        WHEN 'REJECTED' THEN 'REJECTED'
        ELSE status
    END,
    created_at,
    entry_timestamp,                            -- placed_at
    entry_timestamp,                            -- completed_at (assume immediate fill)
    updated_at,
    last_broker_update_at,
    'ORDER_EXECUTION_SERVICE',
    1
FROM trades
WHERE broker_order_id IS NOT NULL
  AND deleted_at IS NULL
  AND status::text IN ('OPEN', 'CLOSED')
ON CONFLICT (client_order_id) DO NOTHING;      -- Idempotent (if run multiple times)

-- TODO: Populate orders table from existing exit_intents (exit orders)
-- This requires exit_intents.broker_order_id to be populated
-- Run after exit order placement is operational

-- ============================================================================
-- 5. HELPER FUNCTIONS
-- ============================================================================

-- Function to get order by broker order ID
CREATE OR REPLACE FUNCTION get_order_by_broker_id(p_broker_order_id VARCHAR)
RETURNS orders AS $$
BEGIN
    RETURN (
        SELECT * FROM orders
        WHERE broker_order_id = p_broker_order_id
          AND deleted_at IS NULL
        LIMIT 1
    );
END;
$$ LANGUAGE plpgsql;

-- Function to update order status with reconciliation tracking
CREATE OR REPLACE FUNCTION update_order_status(
    p_order_id VARCHAR,
    p_new_status VARCHAR,
    p_filled_qty INTEGER DEFAULT NULL,
    p_avg_price NUMERIC DEFAULT NULL,
    p_broker_timestamp TIMESTAMP DEFAULT NULL
) RETURNS BOOLEAN AS $$
DECLARE
    v_row_count INTEGER;
BEGIN
    UPDATE orders
    SET status = p_new_status,
        filled_qty = COALESCE(p_filled_qty, filled_qty),
        pending_qty = ordered_qty - COALESCE(p_filled_qty, filled_qty),
        avg_fill_price = COALESCE(p_avg_price, avg_fill_price),
        fill_value = COALESCE(p_avg_price, avg_fill_price) * COALESCE(p_filled_qty, filled_qty),
        completed_at = CASE WHEN p_new_status = 'COMPLETE' THEN NOW() ELSE completed_at END,
        cancelled_at = CASE WHEN p_new_status = 'CANCELLED' THEN NOW() ELSE cancelled_at END,
        last_broker_update_at = COALESCE(p_broker_timestamp, NOW()),
        updated_at = NOW(),
        reconcile_status = 'IN_SYNC',
        last_reconcile_at = NOW()
    WHERE order_id = p_order_id
      AND deleted_at IS NULL;

    GET DIAGNOSTICS v_row_count = ROW_COUNT;
    RETURN v_row_count > 0;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- 6. VERIFICATION QUERIES
-- ============================================================================

-- Verify migration succeeded
DO $$
BEGIN
    RAISE NOTICE '✅ V011 Migration Verification';
    RAISE NOTICE '  Tables created: orders, order_fills';
    RAISE NOTICE '  Indexes created: %', (
        SELECT COUNT(*) FROM pg_indexes
        WHERE tablename IN ('orders', 'order_fills')
    );
    RAISE NOTICE '  Entry orders migrated: %', (
        SELECT COUNT(*) FROM orders WHERE order_type::text = 'ENTRY'
    );
    RAISE NOTICE '  Functions created: get_order_by_broker_id, update_order_status';
    RAISE NOTICE '';
    RAISE NOTICE '✅ Unified orders table ready for production';
END $$;

COMMIT;

-- ============================================================================
-- USAGE NOTES
-- ============================================================================

/*
Entry Order Creation Pattern:
```java
String orderId = UUID.randomUUID().toString();
Order order = new Order(
    orderId,
    "ENTRY",
    null, // tradeId (set after trade created)
    intentId,
    null, // exitIntentId
    signalId,
    userId,
    brokerId,
    userBrokerId,
    symbol,
    "BUY",
    "BUY",
    "MIS",
    "LIMIT",
    limitPrice,
    qty,
    intentId // client_order_id (idempotency)
);
orderRepo.insert(order);
```

Exit Order Creation Pattern:
```java
String orderId = UUID.randomUUID().toString();
Order order = new Order(
    orderId,
    "EXIT",
    tradeId,
    null, // intentId
    exitIntentId,
    exitSignalId,
    userId,
    brokerId,
    userBrokerId,
    symbol,
    "SELL", // opposite of entry
    "SELL",
    "MIS",
    "MARKET",
    null, // limit_price (market order)
    qty,
    exitIntentId // client_order_id (idempotency)
);
orderRepo.insert(order);
```

Reconciliation Pattern:
```java
// Query pending orders
List<Order> pending = orderRepo.findByStatus("PLACED");

for (Order order : pending) {
    // Query broker
    OrderStatus status = broker.getOrderStatus(order.brokerOrderId());

    // Update order
    if ("COMPLETE".equals(status.status())) {
        orderRepo.updateOrderStatus(
            order.orderId(),
            "COMPLETE",
            status.filledQty(),
            status.avgPrice(),
            status.timestamp()
        );
    }
}
```
*/
