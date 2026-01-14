# P0-D: Tick Deduplication - Testing Guide

## Overview

This document provides comprehensive testing guidance for **P0-D: Tick Deduplication**.

**Goal:** Ensure tick deduplication works correctly with two-window pattern, fallback keys, and bounded memory.

**Implementation Files:**
- `src/main/java/in/annupaper/service/candle/TickCandleBuilder.java` (lines 48-64, 93-229, 377-409)
- `src/main/java/in/annupaper/bootstrap/P0DebtRegistry.java` (line 25)

---

## Test Scenarios

### Test 1: Basic Duplicate Detection with Exchange Timestamp

**Objective:** Verify that duplicate ticks (same symbol, exchange timestamp, price, volume) are rejected.

**Setup:**
1. Start system with WebSocket tick stream enabled
2. Connect to broker (UAT or sandbox)
3. Subscribe to 2-3 symbols (e.g., INFY, TCS, RELIANCE)

**Test Steps:**
1. Observe incoming ticks in logs (should see "Tick received" messages)
2. Manually send duplicate tick via broker mock/simulator:
   ```
   Tick 1: INFY|1673456789000|1500.50|100
   Tick 2: INFY|1673456789000|1500.50|100  (exact duplicate)
   ```
3. Check logs for dedupe message:
   ```
   TRACE ... Duplicate tick detected: INFY (key: INFY|1673456789000|1500.50|100)
   ```

**Acceptance Criteria:**
- ✅ First tick is processed (candle updated, TICK event emitted)
- ✅ Second tick is rejected with TRACE log message
- ✅ Metrics show `duplicateTicks` incremented by 1
- ✅ Window size increases by 1 (not 2)

**How to Check Metrics:**
```bash
# Call getDedupeMetrics() via JMX or health endpoint
curl http://localhost:8080/api/health/tick-dedupe-metrics
# Expected output:
# {
#   "totalTicks": 2,
#   "duplicateTicks": 1,
#   "missingExchangeTimestamp": 0,
#   "currentWindowSize": 1,
#   "previousWindowSize": 0,
#   "dedupeRatePercent": 50
# }
```

---

### Test 2: Fallback Dedupe Key (Missing Exchange Timestamp)

**Objective:** Verify that ticks without exchange timestamp use fallback dedupe key.

**Setup:**
1. Start system with WebSocket tick stream
2. Mock broker adapter to send ticks with `timestamp = 0`

**Test Steps:**
1. Send tick with missing exchange timestamp:
   ```java
   Tick tick = new Tick("INFY", new BigDecimal("1500.50"), ..., 0L);  // timestamp=0
   ```
2. Send duplicate tick within same second:
   ```java
   Tick tick2 = new Tick("INFY", new BigDecimal("1500.50"), ..., 0L);  // timestamp=0
   ```
3. Check logs for fallback dedupe key:
   ```
   TRACE ... Duplicate tick detected: INFY (key: INFY|SYS:1673456789|1500.50|100)
   ```

**Acceptance Criteria:**
- ✅ First tick is processed
- ✅ Second tick (within same second) is rejected
- ✅ Metrics show `missingExchangeTimestamp` incremented by 2
- ✅ Fallback dedupe key uses format: `SYMBOL|SYS:epochSeconds|price|volume`

**Edge Case:**
- Send duplicate tick 2 seconds later (should be processed as new tick)
- Fallback key changes due to different system time second

---

### Test 3: Window Rotation After 60 Seconds

**Objective:** Verify that dedupe windows rotate every 60 seconds without blocking tick processing.

**Setup:**
1. Start system with WebSocket tick stream
2. Subscribe to symbols with moderate tick frequency (10-20 ticks/sec)

**Test Steps:**
1. Observe ticks being processed for 60 seconds
2. At 60-second mark, check logs for window rotation message:
   ```
   INFO ... ✅ Dedupe windows rotated: previous=1234 keys discarded, current=567 keys moved to previous
   ```
3. Send duplicate tick from 50 seconds ago (should be rejected if in previous window)
4. Wait another 60 seconds for second rotation
5. Send same duplicate tick (should be processed now, as it's >120 seconds old)

**Acceptance Criteria:**
- ✅ Window rotation happens every 60 seconds
- ✅ Current window is swapped to previous window
- ✅ New current window is created (empty)
- ✅ Old previous window is discarded (bounded memory!)
- ✅ Ticks in previous window (60-120 seconds old) are still deduplicated
- ✅ Ticks >120 seconds old are processed as new ticks

**How to Verify Bounded Memory:**
```bash
# Check heap usage before and after rotation
jstat -gc <pid> 1000 10

# Or use VisualVM to monitor heap
# Expected: No memory leak, heap stabilizes after rotation
```

---

### Test 4: High-Frequency Tick Stream (Performance)

**Objective:** Verify that deduplication handles high-frequency ticks (1000+ ticks/sec) without performance degradation.

**Setup:**
1. Start system with WebSocket tick stream
2. Subscribe to 50+ symbols (or use mock broker to simulate high frequency)

**Test Steps:**
1. Generate 1000 ticks/second for 5 minutes
2. Monitor tick processing latency
3. Check dedupe metrics every 60 seconds
4. Verify no memory growth or GC pressure

**Acceptance Criteria:**
- ✅ Tick processing latency < 5ms at p99
- ✅ No memory leak (heap stabilizes after rotation)
- ✅ Dedupe window size bounded (< 100K keys per window)
- ✅ No blocking during window rotation (lock is non-blocking)

**Performance Baseline:**
- Dedupe check: O(1) hash lookup
- Window rotation: O(1) reference swap (not O(n) removeIf!)

---

### Test 5: Concurrent Tick Processing (Thread Safety)

**Objective:** Verify that concurrent ticks are deduplicated correctly without race conditions.

**Setup:**
1. Start system with multiple WebSocket connections (if supported)
2. Or use mock broker to send concurrent ticks

**Test Steps:**
1. Send 10 identical ticks concurrently from different threads:
   ```java
   ExecutorService executor = Executors.newFixedThreadPool(10);
   for (int i = 0; i < 10; i++) {
       executor.submit(() -> tickCandleBuilder.onTick(duplicateTick));
   }
   ```
2. Check metrics: only 1 tick should be processed, 9 should be rejected

**Acceptance Criteria:**
- ✅ Only 1 tick is processed (candle updated once)
- ✅ 9 ticks are rejected as duplicates
- ✅ No race conditions (ConcurrentHashMap thread-safe)
- ✅ No duplicate keys in dedupe window

---

### Test 6: Window Rotation During High-Frequency Ticks

**Objective:** Verify that window rotation doesn't block tick processing.

**Setup:**
1. Start system with WebSocket tick stream
2. Subscribe to symbols with high tick frequency (100+ ticks/sec)

**Test Steps:**
1. Generate high-frequency ticks for 55 seconds
2. At 60-second mark, window rotation should occur
3. Verify that ticks continue to be processed during rotation (no blocking)
4. Check logs for rotation message and verify tick processing continues immediately after

**Acceptance Criteria:**
- ✅ Window rotation uses `tryLock()` (non-blocking)
- ✅ If another thread is already rotating, current thread skips rotation
- ✅ Tick processing is not blocked during rotation
- ✅ No tick loss during rotation

---

### Test 7: Dedupe Metrics API

**Objective:** Verify that dedupe metrics are exposed correctly for monitoring.

**Setup:**
1. Start system with WebSocket tick stream
2. Process ticks for 2-3 minutes
3. Query metrics endpoint

**Test Steps:**
1. Call `getDedupeMetrics()` method:
   ```java
   Map<String, Long> metrics = tickCandleBuilder.getDedupeMetrics();
   ```
2. Verify metric values:
   ```json
   {
     "totalTicks": 12345,
     "duplicateTicks": 234,
     "missingExchangeTimestamp": 56,
     "currentWindowSize": 1234,
     "previousWindowSize": 567,
     "dedupeRatePercent": 1
   }
   ```

**Acceptance Criteria:**
- ✅ `totalTicks` = total ticks received (including duplicates)
- ✅ `duplicateTicks` = number of ticks rejected
- ✅ `missingExchangeTimestamp` = ticks without exchange timestamp
- ✅ `currentWindowSize` = number of keys in current window
- ✅ `previousWindowSize` = number of keys in previous window
- ✅ `dedupeRatePercent` = (duplicates / total) * 100

---

### Test 8: Shutdown Cleanup

**Objective:** Verify that dedupe windows are cleared on shutdown.

**Setup:**
1. Start system and process ticks for 2-3 minutes
2. Shutdown system gracefully

**Test Steps:**
1. Call `clearDedupeWindows()` method during shutdown
2. Check logs for cleanup message:
   ```
   INFO ... Clearing dedupe windows: current=1234 keys, previous=567 keys
   ```
3. Verify that both windows are empty after cleanup

**Acceptance Criteria:**
- ✅ Both dedupe windows are cleared
- ✅ Memory is released
- ✅ Metrics reset to 0

---

## Debugging Tips

### How to Enable TRACE Logging for Dedupe

Add to `logback.xml`:
```xml
<logger name="in.annupaper.service.candle.TickCandleBuilder" level="TRACE"/>
```

### How to Simulate Duplicate Ticks

Use broker mock/simulator to send duplicate ticks:
```java
Tick tick = new Tick(
    "INFY",
    new BigDecimal("1500.50"),
    new BigDecimal("1495.00"),
    new BigDecimal("1505.00"),
    new BigDecimal("1490.00"),
    new BigDecimal("1500.50"),
    100L,
    new BigDecimal("1500.45"),
    new BigDecimal("1500.55"),
    50,
    50,
    System.currentTimeMillis()
);

// Send twice
tickCandleBuilder.onTick(tick);
tickCandleBuilder.onTick(tick);  // Should be rejected
```

### How to Check Memory Usage

Use VisualVM or JConsole to monitor:
- Heap usage (should stabilize after window rotation)
- GC activity (should be minimal)
- Thread activity (no blocking during rotation)

---

## Success Criteria

**P0-D is considered complete when:**

1. ✅ **Basic Dedupe Works:** Duplicate ticks (same symbol, timestamp, price, volume) are rejected
2. ✅ **Fallback Key Works:** Ticks without exchange timestamp use fallback dedupe key
3. ✅ **Window Rotation Works:** Windows rotate every 60 seconds without blocking
4. ✅ **Bounded Memory:** Memory usage stabilizes (no unbounded growth)
5. ✅ **Performance:** Handles 1000+ ticks/sec without degradation
6. ✅ **Thread-Safe:** No race conditions with concurrent ticks
7. ✅ **Metrics API:** Dedupe metrics exposed for monitoring
8. ✅ **P0DebtRegistry:** TICK_DEDUPLICATION_ACTIVE flag set to `true`

---

## Next Steps

After verifying P0-D, proceed to:
- **P0-E: Single-Writer Trade State** (see COMPREHENSIVE_IMPLEMENTATION_PLAN.md Phase 1)

---

## Implementation Notes

**Key Design Decisions:**

1. **Two-Window Pattern:** Avoids O(n) removeIf cleanup during tick processing
   - Current window: actively checked
   - Previous window: grace period (60-120 seconds)
   - Memory bounded: old previous window discarded on rotation

2. **Fallback Dedupe Key:** Handles missing exchange timestamps gracefully
   - Primary key: `SYMBOL|exchangeTimestamp|price|volume`
   - Fallback key: `SYMBOL|SYS:systemTimeSeconds|price|volume`
   - Fallback provides ~1 second dedupe window

3. **Non-Blocking Rotation:** Window rotation uses `tryLock()` to prevent blocking
   - If another thread is rotating, current thread skips
   - All ticks eventually rotated (no strict 60-second requirement)

4. **String Dedupe Keys:** Uses string keys instead of full tick objects
   - Lower memory footprint
   - Fast hash lookup
   - No object retention

**References:**
- `TickCandleBuilder.java:93-229` - Dedupe logic
- `P0DebtRegistry.java:25` - Gate flag
- `COMPREHENSIVE_IMPLEMENTATION_PLAN.md` - Phase 1, P0-D
