# Testing Guide: Trailing Stops & Exit Qualification

## Overview

This document provides manual testing procedures for the trailing stop and exit qualification features implemented in V010.

## Test Infrastructure Added

### Dependencies (pom.xml)
- **JUnit 5.10.1** - Testing framework
- **Mockito 5.8.0** - Mocking framework
- **Maven Surefire 3.2.3** - Test runner plugin

### Why No Automated Tests Yet

The domain models (Trade, UserBroker, MtfGlobalConfig) have 50+ constructor parameters each, making comprehensive unit tests with mocks impractical without:
1. Test data builders/factories
2. Database integration test framework (Testcontainers + PostgreSQL)
3. Significant refactoring for testability

**Recommendation**: Add integration tests in future iteration with proper test infrastructure.

---

## Manual Testing Procedures

### Prerequisites

1. System running with database connected
2. User broker configured and connected
3. MTF Global Config with trailing stops enabled
4. Active watchlist with liquid stocks (RELIANCE, INFY, TCS, etc.)

### Test 1: Trailing Stop Activation (LONG Trade)

**Objective**: Verify trailing stop activates when price moves favorably by threshold percentage.

**Setup**:
```sql
-- Verify config
SELECT use_trailing_stop, trailing_stop_activation_pct, trailing_stop_distance_pct
FROM mtf_global_config;
-- Expected: true, 2.0, 1.0
```

**Steps**:
1. Place LONG (BUY) trade on RELIANCE @ ₹2400
2. Wait for entry fill
3. Observe trade record:
   ```sql
   SELECT trade_id, entry_price, trailing_active, trailing_highest_price, trailing_stop_price
   FROM trades
   WHERE symbol = 'NSE:RELIANCE' AND status = 'OPEN'
   ORDER BY created_at DESC LIMIT 1;
   ```
4. Wait for price to rise to ₹2448 (+2% from ₹2400)
5. Check logs for: `✅ Trailing stop ACTIVATED`
6. Verify trade update:
   ```sql
   -- Should now show:
   -- trailing_active = true
   -- trailing_highest_price = 2448.00
   -- trailing_stop_price = 2423.52 (1% below highest)
   ```

**Expected Result**: Trailing stop activated when favorable move reaches 2%.

---

### Test 2: Trailing Stop Tracking (Price Rises)

**Objective**: Verify stop price moves up as price rises.

**Precondition**: Trailing stop already active from Test 1.

**Steps**:
1. Price rises to ₹2460
2. Check trade record every 5 seconds
3. Verify `trailing_highest_price` updates to ₹2460
4. Verify `trailing_stop_price` updates to ₹2435.40 (1% below ₹2460)
5. Price rises to ₹2480
6. Verify stop moves to ₹2455.20

**Expected Result**: Stop price follows 1% below highest price reached.

---

### Test 3: Trailing Stop NOT Moving Down

**Objective**: Verify stop doesn't move down when price retraces.

**Steps**:
1. Price at ₹2480, stop at ₹2455.20
2. Price drops to ₹2470
3. Verify `trailing_highest_price` stays at ₹2480
4. Verify `trailing_stop_price` stays at ₹2455.20
5. Price drops to ₹2460
6. Stop should still be ₹2455.20 (doesn't move down)

**Expected Result**: Stop price locked at highest point, doesn't retrace.

---

### Test 4: Trailing Stop Exit Trigger

**Objective**: Verify exit when price falls to stop level.

**Steps**:
1. Price at ₹2470, highest ₹2480, stop ₹2455.20
2. Price drops to ₹2455.00 (below stop)
3. Check event log for `EXIT_INTENT_CREATED` with reason `TRAILING_STOP`
4. Check exit qualification:
   ```sql
   SELECT exit_intent_id, trade_id, exit_reason, status, order_type
   FROM exit_intents
   WHERE trade_id = '<trade_id>' AND exit_reason = 'TRAILING_STOP'
   ORDER BY created_at DESC LIMIT 1;
   ```
5. Verify `order_type = 'MARKET'` (urgent exit)
6. Verify order placed with broker
7. Wait for fill
8. Verify trade closes:
   ```sql
   SELECT status, exit_price, exit_trigger, realized_pnl
   FROM trades
   WHERE trade_id = '<trade_id>';
   -- Expected: status = 'CLOSED', exit_trigger = 'TRAILING_STOP'
   ```

**Expected Result**: Trade exits at market when price hits trailing stop.

---

### Test 5: Trailing Stop for SHORT Trade

**Objective**: Verify direction-aware logic for SHORT trades.

**Setup**: Place SHORT (SELL) trade on TCS @ ₹3400

**Steps**:
1. Price falls to ₹3332 (-2% from ₹3400) → Stop should ACTIVATE
2. Verify:
   - `trailing_active = true`
   - `trailing_highest_price = 3332.00` (lowest for SHORT)
   - `trailing_stop_price = 3365.32` (1% ABOVE lowest)
3. Price falls to ₹3320 → Stop moves to ₹3353.20 (1% above)
4. Price rises back to ₹3350 → Stop stays at ₹3353.20
5. Price rises to ₹3354 (above stop) → Exit triggers

**Expected Result**: SHORT trailing stop follows price downward, exits on upward retracement.

---

### Test 6: Exit Qualification - Broker Disconnected

**Objective**: Verify exit rejected when broker offline.

**Steps**:
1. Open LONG trade on HDFC
2. Disconnect broker (stop broker service or kill connection)
3. Manually trigger exit (or wait for target hit)
4. Check exit intent:
   ```sql
   SELECT status, validation_errors
   FROM exit_intents
   WHERE trade_id = '<trade_id>'
   ORDER BY created_at DESC LIMIT 1;
   ```
5. Verify `status = 'REJECTED'`
6. Verify error contains `BROKER_DISCONNECTED`
7. Check event log for `EXIT_INTENT_REJECTED` event

**Expected Result**: Exit rejected with broker disconnection error.

---

### Test 7: Exit Qualification - Direction Mismatch

**Objective**: Verify exit rejected for wrong direction.

**Manual Test** (requires code modification):
1. Open LONG (BUY) trade
2. Modify exit signal to use BUY direction (should be SELL)
3. Attempt exit
4. Verify rejection with `DIRECTION_MISMATCH` error

**Expected Result**: Exit rejected when trying to BUY to close a LONG position.

---

### Test 8: Exit Qualification - Pending Exit Check

**Objective**: Verify duplicate exit prevention.

**Steps**:
1. Open LONG trade on WIPRO
2. Price hits target → Exit intent created (APPROVED)
3. Before order executes, price hits trailing stop
4. Second exit attempt should be rejected
5. Check exit intents:
   ```sql
   SELECT exit_intent_id, exit_reason, status, created_at
   FROM exit_intents
   WHERE trade_id = '<trade_id>'
   ORDER BY created_at;
   ```
6. First should be TARGET_HIT (APPROVED or PLACED)
7. Second should be TRAILING_STOP (REJECTED) with `EXIT_ORDER_ALREADY_PENDING`

**Expected Result**: Only one exit intent can be pending/approved/placed at a time.

---

### Test 9: Exit Cooldown (30-Second Re-arm)

**Objective**: Verify cooldown prevents rapid-fire exits.

**Steps**:
1. Open LONG trade
2. Price briefly touches target, brick filter blocks exit
3. Exit signal generated (EXIT_INTENT_CREATED)
4. 10 seconds later, price touches target again
5. Cooldown check fails → EXIT_INTENT_COOLDOWN_REJECTED
6. Wait 25 more seconds (total 35 seconds since first)
7. Price touches target again
8. Cooldown satisfied → EXIT_INTENT_APPROVED

**Query to verify**:
```sql
SELECT exit_intent_id, exit_reason, episode_id, status, validation_errors, created_at
FROM exit_intents
WHERE trade_id = '<trade_id>' AND exit_reason = 'TARGET_HIT'
ORDER BY created_at;
```

**Expected Result**:
- First attempt: episode_id = 1, APPROVED
- Second attempt (10s later): episode_id = 0, REJECTED, error = "EXIT_COOLDOWN_ACTIVE"
- Third attempt (35s later): episode_id = 2, APPROVED

---

### Test 10: Trailing Stop Configuration via Admin UI

**Objective**: Verify admin panel controls work.

**Steps**:
1. Open admin panel → MTF Configuration → Global Configuration
2. Locate "Entry & Exit Targets" section
3. Verify 3 trailing stop fields exist:
   - **Use Trailing Stop** (checkbox)
   - **Trailing Stop Activation %** (number input)
   - **Trailing Stop Distance %** (number input)
4. Disable trailing stops (uncheck)
5. Save configuration
6. Open new trade
7. Price moves +3% (above threshold)
8. Verify trailing stop does NOT activate
9. Re-enable trailing stops
10. Change activation to 3.0%
11. Change distance to 1.5%
12. Save
13. Open new trade
14. Verify activation at +3% (not +2%)
15. Verify stop is 1.5% below highest (not 1%)

**Expected Result**: Admin UI controls fully functional, config changes take effect immediately.

---

## Observability Checkpoints

### Event Log Verification

Check real-time events during tests:
```sql
SELECT event_type, payload::jsonb, created_at
FROM event_log
WHERE created_at > NOW() - INTERVAL '10 minutes'
  AND event_type LIKE 'EXIT_INTENT%'
ORDER BY created_at DESC
LIMIT 50;
```

**Key Events to Monitor**:
- `EXIT_INTENT_CREATED`
- `EXIT_INTENT_APPROVED`
- `EXIT_INTENT_REJECTED`
- `EXIT_INTENT_COOLDOWN_REJECTED`
- `EXIT_SIGNAL_DETECTED`
- `EXIT_INTENT_PLACED`
- `EXIT_INTENT_FILLED`

### Application Logs

Watch for these log patterns:
```
grep -i "trailing" app.log | tail -50
```

**Key Log Messages**:
- `✅ Trailing stop ACTIVATED: <trade_id> @ <price>`
- `Trailing stop updated: <trade_id> highest=<price> stop=<price>`
- `Exit delegated to SMS: <symbol> <direction> <trade_id> @ <price> (reason: TRAILING_STOP)`

### Database Queries for Debugging

**Find all trades with active trailing stops**:
```sql
SELECT trade_id, symbol, direction, entry_price, trailing_highest_price, trailing_stop_price,
       ROUND((trailing_highest_price - entry_price) / entry_price * 100, 2) AS gain_pct
FROM trades
WHERE status = 'OPEN'
  AND trailing_active = true
ORDER BY created_at DESC;
```

**Find recent trailing stop exits**:
```sql
SELECT t.trade_id, t.symbol, t.direction, t.entry_price, t.exit_price,
       t.trailing_highest_price, t.trailing_stop_price, t.realized_pnl,
       ei.exit_reason, ei.status AS exit_intent_status
FROM trades t
JOIN exit_intents ei ON t.trade_id = ei.trade_id
WHERE t.exit_trigger = 'TRAILING_STOP'
  AND t.created_at > NOW() - INTERVAL '24 hours'
ORDER BY t.exit_timestamp DESC;
```

**Check qualification rejection reasons**:
```sql
SELECT exit_reason, validation_errors, COUNT(*) AS rejection_count
FROM exit_intents
WHERE status = 'REJECTED'
  AND created_at > NOW() - INTERVAL '24 hours'
GROUP BY exit_reason, validation_errors
ORDER BY rejection_count DESC;
```

---

## Common Issues & Troubleshooting

### Issue: Trailing Stop Never Activates

**Possible Causes**:
1. `use_trailing_stop = false` in config
2. Price hasn't moved enough (check `trailing_stop_activation_pct`)
3. Trade has no entry price (`entry_price IS NULL`)
4. Direction field missing or invalid

**Debug**:
```sql
-- Check config
SELECT use_trailing_stop, trailing_stop_activation_pct FROM mtf_global_config;

-- Check trade state
SELECT trade_id, entry_price, direction, status FROM trades WHERE trade_id = '<trade_id>';

-- Check price movement
SELECT symbol, last_price FROM market_data_cache WHERE symbol = '<symbol>';
```

### Issue: Stop Not Moving Up

**Possible Causes**:
1. Price hasn't reached new high
2. Trade state not refreshing (DB-first architecture - check query)
3. TradeManagementService not receiving updates

**Debug**:
- Check logs for `Trailing stop updated` messages
- Verify `updateTrailingStop()` calls with logging
- Query trade record to see if `trailing_highest_price` changed

### Issue: Exit Not Triggering at Stop Price

**Possible Causes**:
1. Brick movement filter blocking exit
2. Price didn't actually touch stop (check precision)
3. Exit qualification failing

**Debug**:
```sql
-- Check if exit intent was created
SELECT * FROM exit_intents WHERE trade_id = '<trade_id>' ORDER BY created_at DESC LIMIT 5;

-- Check brick movement filter
SELECT * FROM brick_movement_log WHERE symbol = '<symbol>' ORDER BY created_at DESC LIMIT 10;
```

### Issue: EXIT_INTENT_COOLDOWN_REJECTED Too Frequently

**Possible Causes**:
1. Brick filter too sensitive (price oscillating around threshold)
2. Multiple exit conditions triggering simultaneously
3. 30-second cooldown too strict for volatile stocks

**Resolution**:
- Review brick movement thresholds in BrickMovementTracker
- Consider increasing cooldown period in `generate_exit_episode()` function
- Adjust exit condition priorities (trailing stop before target)

---

## Performance Metrics

Monitor these metrics during testing:

1. **Exit Intent Processing Time**:
   ```sql
   SELECT exit_reason,
          AVG(EXTRACT(EPOCH FROM (placed_at - created_at))) AS avg_qualification_seconds
   FROM exit_intents
   WHERE status = 'FILLED' AND created_at > NOW() - INTERVAL '24 hours'
   GROUP BY exit_reason;
   ```
   **Target**: < 500ms from intent creation to order placement

2. **Trailing Stop Update Frequency**:
   - Count `updateTrailingStop()` calls per minute
   - **Expected**: 1-2 per tick per open trade with trailing stop active

3. **Qualification Success Rate**:
   ```sql
   SELECT exit_reason,
          COUNT(*) FILTER (WHERE status = 'APPROVED') AS approved,
          COUNT(*) AS total,
          ROUND(100.0 * COUNT(*) FILTER (WHERE status = 'APPROVED') / COUNT(*), 2) AS approval_rate_pct
   FROM exit_intents
   WHERE created_at > NOW() - INTERVAL '24 hours'
   GROUP BY exit_reason;
   ```
   **Target**: > 90% approval rate for normal market conditions

---

## Test Sign-Off Checklist

Before considering trailing stops production-ready:

- [ ] Test 1: LONG trailing stop activation verified
- [ ] Test 2: Stop tracking upward verified
- [ ] Test 3: Stop not moving down verified
- [ ] Test 4: Exit trigger at stop verified
- [ ] Test 5: SHORT trailing stop behavior verified
- [ ] Test 6: Broker disconnection rejection verified
- [ ] Test 7: Direction mismatch rejection verified
- [ ] Test 8: Duplicate exit prevention verified
- [ ] Test 9: Cooldown enforcement verified
- [ ] Test 10: Admin UI controls verified
- [ ] No memory leaks observed (monitor heap over 4+ hours)
- [ ] No database connection leaks (monitor pool usage)
- [ ] Event emission working correctly
- [ ] Logs showing expected messages
- [ ] Performance within acceptable limits

---

## Future Test Automation

**Recommended Approach**:

1. **Test Data Builders**:
   ```java
   public class TradeBuilder {
       public static Trade aLongTrade() { ... }
       public static Trade withTrailingStop(boolean active, BigDecimal highest, BigDecimal stop) { ... }
   }
   ```

2. **Database Integration Tests** (Testcontainers):
   - Spin up PostgreSQL in Docker
   - Run Flyway migrations
   - Test against real database
   - No mocking of repositories

3. **Property-Based Testing** (jqwik):
   - Generate random price sequences
   - Verify trailing stop invariants hold

4. **Contract Tests**:
   - Verify ExitQualificationService contract
   - Ensure TradeManagementService updates are idempotent

---

**Document Version**: 1.0
**Last Updated**: January 13, 2026
**Related**: EXIT_QUALIFICATION_ARCHITECTURE.md, V010__exit_qualification_symmetry.sql
