# Exit Qualification Architecture (V010)

## Overview

V010 achieves **entry/exit symmetry** by completing the exit qualification flow to mirror the entry qualification system. Both flows now enforce execution readiness checks, create audit trails, and provide complete observability.

## Architecture Symmetry

### Entry Flow
```
Signal Detection → Signal Delivery → ValidationService → TradeIntent → Order → Trade (OPEN)
```

### Exit Flow (V010)
```
Exit Detection → ExitQualificationService → ExitIntent → Order → Trade (CLOSED)
```

## Core Components

### 1. Database Schema (V010__exit_qualification_symmetry.sql)

#### exit_intents Table
Tracks execution qualification and order outcomes for exits.

**Key Fields:**
- `exit_intent_id` - Unique intent ID
- `exit_signal_id` - Reference to exit signal (nullable if rejected before signal creation)
- `trade_id` - Trade being exited
- `user_broker_id` - Execution broker
- `exit_reason` - TARGET_HIT | STOP_LOSS | TRAILING_STOP | TIME_BASED | MANUAL
- `episode_id` - Episode number (for re-arming after brick reset)
- `status` - PENDING | APPROVED | REJECTED | PLACED | FILLED | FAILED | CANCELLED
- `validation_passed` - Boolean qualification result
- `validation_errors` - Array of error messages
- `calculated_qty` - Quantity to exit (from qualification)
- `order_type` - MARKET | LIMIT (from qualification)
- `limit_price` - Limit price if applicable
- `broker_order_id` - Set when order placed
- `placed_at`, `filled_at`, `cancelled_at` - Lifecycle timestamps
- `retry_count` - For failed order retries

**Unique Constraint:**
```sql
UNIQUE (trade_id, user_broker_id, exit_reason, episode_id) WHERE deleted_at IS NULL
```
Enforces idempotency - one exit intent per trade/reason/episode.

#### trades.direction Column
Added `VARCHAR(10)` column to persist direction (BUY | SELL) at trade creation.

**Why Important:**
- LONG (BUY): Target at ceiling, stop loss at floor
- SHORT (SELL): Target at floor, stop loss at ceiling
- Exit logic is direction-dependent

### 2. DB Functions

#### generate_exit_episode(trade_id, exit_reason)
**Purpose:** Generate monotonic episode ID with cooldown enforcement

**Behavior:**
- Checks if 30 seconds have elapsed since last exit signal for this trade/reason
- Raises `EXIT_COOLDOWN_ACTIVE` exception if cooldown period not elapsed
- Returns next episode number (1, 2, 3...) if cooldown satisfied
- Thread-safe: DB serialization handles races

**Cooldown Parameters:**
- Duration: 30 seconds (configurable in function)
- Scope: Per (trade_id, exit_reason) pair
- Enforcement: DB-level (restart-safe, no in-memory state)

#### place_exit_order(exit_intent_id, broker_order_id)
**Purpose:** Atomically transition APPROVED → PLACED

**Behavior:**
- Updates status to PLACED
- Sets broker_order_id and placed_at timestamp
- Uses optimistic locking (checks current status = APPROVED)
- Returns boolean success indicator
- Prevents duplicate order placement (idempotent)

### 3. Domain Models

#### ExitIntentStatus Enum
```java
public enum ExitIntentStatus {
    PENDING,      // Created, awaiting qualification
    APPROVED,     // Passed qualification, ready for execution
    REJECTED,     // Failed qualification
    PLACED,       // Order placed with broker
    FILLED,       // Order filled, trade can close
    FAILED,       // Order placement/execution failed
    CANCELLED     // Manually cancelled or superseded
}
```

#### ExitIntent Record
Immutable record with 24 fields tracking complete exit intent lifecycle.

**Key Methods:**
- `isPending()`, `isApproved()`, `isRejected()` - Status checks
- `isPlaced()`, `isFilled()`, `isFailed()` - Order lifecycle
- `isTerminal()` - Returns true for FILLED, REJECTED, CANCELLED
- `canRetry()` - Returns true for FAILED status

### 4. ExitQualificationService

**Purpose:** Validates execution readiness for exit signals (mirrors ValidationService for entry)

**Location:** `src/main/java/in/annupaper/service/validation/ExitQualificationService.java`

**Qualification Checks:**
1. **Broker Operational** - Active and connected
2. **Trade State Valid** - OPEN status, correct user-broker
3. **Direction Consistency** - Exit direction matches trade direction
   - BUY trade requires SELL exit
   - SELL trade requires BUY exit
4. **No Pending Exit** - Check for existing exit orders
5. **Market Hours** - Exit window validation
   - Stop loss: Anytime during market hours (9:15 AM - 3:30 PM IST)
   - Target/manual: Avoid last 5 minutes before close
6. **Portfolio State** - Not frozen (if applicable)
7. **Quantity Calculation** - Exit full position (trade.entryQty)
8. **Order Type Determination**:
   - STOP_LOSS, TRAILING_STOP → MARKET (urgent)
   - TARGET_HIT → LIMIT (protect profit)
   - TIME_BASED → LIMIT (no rush)
   - MANUAL → MARKET (user intent)
9. **Limit Price Calculation** - If LIMIT order, calculate appropriate price

**Returns:** `ExitQualificationResult` with:
- `passed` - Boolean success/failure
- `errors` - List of validation errors
- `calculatedQty` - Quantity to exit
- `orderType` - MARKET or LIMIT
- `limitPrice` - Price if LIMIT order
- `productType` - Match entry product type

### 5. Exit Flow Implementation

#### SignalManagementServiceImpl.handleExitDetected()

**Flow:**
```
1. Fetch trade and user-broker from DB
2. Call ExitQualificationService.qualify()
3. Try to generate episode_id (DB function with cooldown check)
   - If cooldown active: Create REJECTED ExitIntent, emit cooldown event, return
4. Create ExitIntent with qualification results
   - Status: APPROVED if passed, REJECTED if failed
5. Emit lifecycle events (CREATED, APPROVED/REJECTED/COOLDOWN_REJECTED)
6. If qualification passed:
   - Create ExitSignal
   - Emit EXIT_SIGNAL_DETECTED event
   - Update re-arm tracking (in-memory cache)
```

**Cooldown Handling:**
```java
try {
    episodeId = exitSignalRepo.generateEpisode(tradeId, exitReason);
} catch (Exception e) {
    if (e.getMessage().contains("EXIT_COOLDOWN_ACTIVE")) {
        // Create REJECTED ExitIntent with cooldown error
        createExitIntent(..., false, List.of("EXIT_COOLDOWN_ACTIVE: " + msg), ...);
        return;  // Stop processing
    }
    throw e;  // Re-throw other exceptions
}
```

#### ExitSignalService (Direction-Aware Exit Conditions)

**Target Checking:**
```java
private boolean shouldExitAtTarget(Trade trade, BigDecimal currentPrice) {
    Direction direction = Direction.valueOf(trade.direction());
    if (direction == Direction.BUY) {
        // LONG: exit when price reaches or exceeds target
        return currentPrice.compareTo(trade.exitTargetPrice()) >= 0;
    } else {
        // SHORT: exit when price reaches or falls below target
        return currentPrice.compareTo(trade.exitTargetPrice()) <= 0;
    }
}
```

**Stop Loss Checking:**
```java
private boolean shouldExitAtStopLoss(Trade trade, BigDecimal currentPrice) {
    Direction direction = Direction.valueOf(trade.direction());
    if (direction == Direction.BUY) {
        // LONG: exit when price falls to or below floor
        return currentPrice.compareTo(trade.entryEffectiveFloor()) <= 0;
    } else {
        // SHORT: exit when price rises to or above ceiling
        return currentPrice.compareTo(trade.entryEffectiveCeiling()) >= 0;
    }
}
```

## Event-Driven Observability

### Exit Intent Events (V010)

**Event Types:**
- `EXIT_INTENT_CREATED` - Intent record created
- `EXIT_INTENT_QUALIFIED` - Qualification check completed
- `EXIT_INTENT_APPROVED` - Passed all checks, ready for execution
- `EXIT_INTENT_REJECTED` - Failed qualification checks
- `EXIT_INTENT_COOLDOWN_REJECTED` - Rejected due to 30s cooldown
- `EXIT_INTENT_PLACED` - Order placed with broker
- `EXIT_INTENT_FILLED` - Order filled, trade can close
- `EXIT_INTENT_FAILED` - Order placement/execution failed

**Event Payloads:**

**EXIT_INTENT_CREATED:**
```json
{
  "exitIntentId": "uuid",
  "tradeId": "uuid",
  "userBrokerId": "uuid",
  "exitReason": "TARGET_HIT",
  "episodeId": 1,
  "symbol": "RELIANCE",
  "direction": "BUY"
}
```

**EXIT_INTENT_APPROVED:**
```json
{
  "exitIntentId": "uuid",
  "tradeId": "uuid",
  "userBrokerId": "uuid",
  "exitReason": "TARGET_HIT",
  "episodeId": 1,
  "calculatedQty": 10,
  "orderType": "LIMIT",
  "limitPrice": 2500.00,
  "symbol": "RELIANCE"
}
```

**EXIT_INTENT_REJECTED:**
```json
{
  "exitIntentId": "uuid",
  "tradeId": "uuid",
  "userBrokerId": "uuid",
  "exitReason": "STOP_LOSS",
  "episodeId": 1,
  "errors": ["BROKER_DISCONNECTED: Broker is not connected"],
  "symbol": "RELIANCE"
}
```

**EXIT_INTENT_COOLDOWN_REJECTED:**
```json
{
  "exitIntentId": "uuid",
  "tradeId": "uuid",
  "userBrokerId": "uuid",
  "exitReason": "TARGET_HIT",
  "episodeId": 0,
  "symbol": "RELIANCE",
  "cooldownSeconds": 30
}
```

## Operational Queries

### Find All Exit Intents for a Trade
```sql
SELECT * FROM exit_intents
WHERE trade_id = '<trade_id>'
  AND deleted_at IS NULL
ORDER BY created_at DESC;
```

### Find Stuck Exit Intents (Pending > 5 minutes)
```sql
SELECT * FROM exit_intents
WHERE status IN ('PENDING', 'APPROVED')
  AND created_at < NOW() - INTERVAL '5 minutes'
  AND deleted_at IS NULL;
```

### Find Qualification Failure Rate by Reason
```sql
SELECT
    exit_reason,
    COUNT(*) FILTER (WHERE status = 'REJECTED') AS rejected_count,
    COUNT(*) AS total_count,
    ROUND(100.0 * COUNT(*) FILTER (WHERE status = 'REJECTED') / COUNT(*), 2) AS rejection_rate_pct
FROM exit_intents
WHERE deleted_at IS NULL
GROUP BY exit_reason
ORDER BY rejection_rate_pct DESC;
```

### Find Cooldown Rejections in Last Hour
```sql
SELECT * FROM exit_intents
WHERE status = 'REJECTED'
  AND validation_errors::text LIKE '%EXIT_COOLDOWN_ACTIVE%'
  AND created_at > NOW() - INTERVAL '1 hour'
  AND deleted_at IS NULL
ORDER BY created_at DESC;
```

### Monitor Exit Intent Processing Time
```sql
SELECT
    exit_reason,
    AVG(EXTRACT(EPOCH FROM (placed_at - created_at))) AS avg_qualification_seconds,
    AVG(EXTRACT(EPOCH FROM (filled_at - placed_at))) AS avg_execution_seconds
FROM exit_intents
WHERE status = 'FILLED'
  AND deleted_at IS NULL
  AND created_at > NOW() - INTERVAL '24 hours'
GROUP BY exit_reason;
```

## Debugging Guide

### Issue: Exit Intent Stuck in APPROVED
**Symptoms:** Intent approved but never placed with broker

**Check:**
1. Order execution service running?
2. Broker connection active?
3. Check error logs for order placement failures
4. Query: `SELECT * FROM exit_intents WHERE status = 'APPROVED' AND created_at < NOW() - INTERVAL '5 minutes'`

**Resolution:**
- Retry order placement manually
- Check broker API limits
- Verify user-broker credentials

### Issue: High Cooldown Rejection Rate
**Symptoms:** Many EXIT_COOLDOWN_REJECTED events

**Check:**
1. Is brick movement filter too sensitive?
2. Are multiple exit conditions triggering simultaneously?
3. Query cooldown rejections: See "Find Cooldown Rejections" query above

**Resolution:**
- Review brick movement thresholds
- Adjust exit condition priorities
- Consider increasing cooldown period (modify DB function)

### Issue: Qualification Always Fails for Specific Trade
**Symptoms:** Repeated REJECTED status for same trade

**Check:**
1. Trade direction field populated?
2. User-broker active and connected?
3. Trade status is OPEN?
4. Check validation_errors array in exit_intent record

**Resolution:**
- Fix data issue (direction, broker status, etc.)
- Check ExitQualificationService logic for false positives

## Performance Considerations

### Database Indexes
Ensure these indexes exist for optimal performance:

```sql
-- exit_intents
CREATE INDEX idx_exit_intents_trade_id ON exit_intents(trade_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_exit_intents_status ON exit_intents(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_exit_intents_user_broker ON exit_intents(user_broker_id) WHERE deleted_at IS NULL;

-- trades
CREATE INDEX idx_trades_direction ON trades(direction) WHERE deleted_at IS NULL;
CREATE INDEX idx_trades_symbol_status ON trades(symbol, status) WHERE deleted_at IS NULL;
```

### Cooldown Performance
- DB function `generate_exit_episode()` executes in <1ms
- Cooldown check is a simple timestamp comparison
- No network calls, no external dependencies
- Restart-safe: state persists in exit_signals table

### Event Emission Performance
- Events are fire-and-forget (non-blocking)
- EventService queues events for async delivery
- No performance impact on exit qualification flow
- Consider batching if >1000 events/sec

## Testing Scenarios

### 1. Normal Exit Flow
```
1. Open LONG trade (BUY) on RELIANCE @ 2400
2. Price rises to 2500 (target hit)
3. ExitSignalService detects TARGET_HIT
4. ExitQualificationService approves exit
5. ExitIntent created with APPROVED status
6. Order placed with broker (LIMIT @ 2500)
7. Order fills
8. Trade closes
```

**Expected Events:**
- EXIT_INTENT_CREATED
- EXIT_INTENT_APPROVED
- EXIT_SIGNAL_DETECTED
- EXIT_INTENT_PLACED
- EXIT_INTENT_FILLED

### 2. Cooldown Rejection
```
1. Open LONG trade on RELIANCE @ 2400
2. Price briefly touches 2500 (target), then drops to 2490
3. Exit signal generated, qualified, but not executed (brick filter)
4. Price rises to 2500 again after 10 seconds
5. Exit signal generated again
6. Cooldown check fails (30s not elapsed)
7. ExitIntent created with REJECTED status
```

**Expected Events:**
- EXIT_INTENT_CREATED (first attempt)
- EXIT_INTENT_APPROVED (first attempt)
- EXIT_INTENT_CREATED (second attempt, 10s later)
- EXIT_INTENT_COOLDOWN_REJECTED (second attempt)

### 3. Broker Disconnection
```
1. Open trade on RELIANCE
2. Broker disconnects mid-session
3. Price hits target
4. ExitQualificationService checks broker status
5. Qualification fails: BROKER_DISCONNECTED
6. ExitIntent created with REJECTED status
```

**Expected Events:**
- EXIT_INTENT_CREATED
- EXIT_INTENT_REJECTED (error: "BROKER_DISCONNECTED")

## Future Enhancements

### Partial Exits
Support exiting portion of position (e.g., 50% at target, 50% at stretch).

**Required Changes:**
- Update ExitIntent.calculatedQty to support fractional exits
- Add position tracking for remaining quantity
- Create multiple ExitIntents per trade for staged exits

### Dynamic Cooldown
Adjust cooldown period based on volatility or time of day.

**Implementation:**
- Add cooldown_seconds parameter to generate_exit_episode()
- Calculate based on ATR or recent volatility
- Store per-symbol cooldown config

### Exit Intent Retry Queue
Automatic retry for failed order placements.

**Implementation:**
- Background job queries exit_intents WHERE status = 'FAILED' AND retry_count < max_retries
- Exponential backoff between retries
- Emit EXIT_INTENT_RETRY_ATTEMPTED event

### Exit Intent Analytics Dashboard
Real-time monitoring of exit qualification metrics.

**Metrics:**
- Qualification success rate by exit reason
- Average time from approval to fill
- Cooldown rejection frequency
- Broker-specific failure rates

---

**Document Version:** 1.0
**Last Updated:** January 13, 2026
**Related:** V010_EXIT_QUALIFICATION_SYMMETRY.sql, ExitQualificationService.java, SignalManagementServiceImpl.java
