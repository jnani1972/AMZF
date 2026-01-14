# AnnuPaper v04 - System Architecture

**Last Updated:** January 12, 2026
**Version:** 0.4.0

---

## Table of Contents

1. [Overview](#overview)
2. [Threading Model](#threading-model)
3. [Concurrent Data Structures](#concurrent-data-structures)
4. [Market Data Architecture](#market-data-architecture)
5. [Timeframe System](#timeframe-system)
6. [WebSocket Architecture](#websocket-architecture)
7. [Thread Safety Patterns](#thread-safety-patterns)
8. [Performance Considerations](#performance-considerations)

---

## Overview

AnnuPaper v04 is a high-performance, multi-broker trading system built on pure Java (no Spring Framework). The system emphasizes:

- **Lock-free concurrency** using ConcurrentHashMap and immutable objects
- **Zero DB writes during market hours** for hot-path operations (ticks, candles)
- **Real-time WebSocket updates** with batching (100ms flush interval)
- **Multi-timeframe analysis** (DAILY, HTF, ITF, LTF)
- **Per-user-broker isolation** for strategies and risk management

**Technology Stack:**
- **Server:** Undertow (embedded, non-blocking I/O)
- **Database:** PostgreSQL with HikariCP connection pool
- **Concurrency:** java.util.concurrent (CHM, AtomicReference, volatile)
- **Serialization:** Jackson for JSON
- **Build:** Maven with maven-shade-plugin

---

## Threading Model

### Thread Types

| Thread Type | Count | Purpose | Key Operations |
|-------------|-------|---------|----------------|
| **Undertow I/O Threads** | 2× CPU cores | Handle HTTP requests & WebSocket connections | Accept connections, parse messages, route to handlers |
| **Broker WS Threads** | 1 per broker adapter | Process incoming ticks from broker WebSocket | Update MarketDataCache, build candles, publish events |
| **WS Batch Flusher** | 1 daemon thread | Batch and broadcast WebSocket events | Drain queue, filter by scope/subscription, broadcast |
| **DB Writer Pool** | HikariCP pool (10) | Execute database queries | Persist events, candles, user data |

### Threading Flow

```
┌─────────────────────────────────────────────────────────────────┐
│ Broker WS Thread (e.g., FYERS)                                  │
│   ├─ onTick() receives tick from broker                         │
│   ├─ MarketDataCache.updateTick(symbol, price, ts)             │
│   │    └─ CHM.put(symbol, new TickData(price, ts)) [atomic]    │
│   ├─ EventService.emitGlobal(TICK)                             │
│   │    ├─ DB write (sync on broker thread) ⚠️                   │
│   │    └─ wsHub.publish() [enqueue to BlockingQueue]          │
│   └─ TickCandleBuilder.updateCandle() [in-memory]              │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ Undertow I/O Thread (HTTP Request)                              │
│   ├─ ApiHandlers.marketWatch()                                  │
│   ├─ AdminService.getMarketWatchForAdmin()                     │
│   │    ├─ Query PostgreSQL for watchlist structure             │
│   │    └─ enrichWithLatestPrice() for each symbol              │
│   │         ├─ [Tier 1] Check MarketDataCache (O(1))           │
│   │         ├─ [Tier 2] Query DB for DAILY close (if miss)     │
│   │         └─ [Tier 3] Return null                             │
│   └─ Serialize JSON response                                    │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ WS Batch Flusher Thread (every 100ms)                           │
│   ├─ Drain batchQueue (up to 2000 events)                      │
│   ├─ For each WsSession in activeSessions:                     │
│   │    ├─ Read session.topics (ConcurrentHashMap.newKeySet)    │
│   │    ├─ Read session.lastActivity (volatile Instant)         │
│   │    ├─ Filter events by scope (GLOBAL/USER/USER_BROKER)     │
│   │    └─ Build BATCH message                                   │
│   └─ WebSockets.sendText() to all relevant clients             │
└─────────────────────────────────────────────────────────────────┘
```

---

## Concurrent Data Structures

### HashMap vs ConcurrentHashMap Decision Matrix

| Data Structure | Thread Safety | When to Use |
|----------------|---------------|-------------|
| **HashMap** | ❌ Not thread-safe | Single-threaded executor, request-scoped data, immutable after construction |
| **ConcurrentHashMap** | ✅ Thread-safe map ops | Multi-threaded access (broker thread + HTTP threads) |
| **CHM.newKeySet()** | ✅ Thread-safe set | Concurrent set in map value (subscriptions, channels) |
| **AtomicReference** | ✅ Thread-safe updates | Immutable snapshot replacement (if needed) |
| **volatile** | ✅ Visibility guarantee | Immutable objects (Instant, String) updated by multiple threads |

### Critical Maps in AnnuPaper

#### 1. **WsHub - WebSocket Management**

```java
// activeSessions: Undertow I/O threads write, flusher reads
private final ConcurrentMap<WebSocketChannel, WsSession> activeSessions = new ConcurrentHashMap<>();

// userChannels: I/O threads modify, flusher reads
private final ConcurrentMap<String, Set<WebSocketChannel>> userChannels = new ConcurrentHashMap<>();
// IMPORTANT: Set value MUST be concurrent
userChannels.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(channel);
```

**Thread Safety:**
- ✅ CHM for map operations
- ✅ CHM.newKeySet() for concurrent set values
- ✅ WsSession.topics/brokerIds use CHM.newKeySet()
- ✅ WsSession.lastActivity is `volatile Instant` (visibility)

#### 2. **MarketDataCache - Tick Price Cache**

```java
// latestTicks: Broker thread writes, HTTP threads read
private final ConcurrentHashMap<String, TickData> latestTicks = new ConcurrentHashMap<>();

// TickData is immutable (Java record)
public record TickData(BigDecimal lastPrice, Instant timestamp) {}
```

**Thread Safety:**
- ✅ CHM for O(1) symbol lookup
- ✅ TickData is immutable → replace-entire-object pattern
- ✅ No need for AtomicReference (record already immutable)
- ✅ CHM.put() is atomic → readers see old or new, no torn writes

**Update Pattern:**
```java
// Broker thread writes
marketDataCache.updateTick(symbol, price, ts);  // CHM.put(symbol, new TickData(...))

// HTTP threads read
TickData tick = marketDataCache.getLatestTick(symbol);  // CHM.get(symbol) → immutable snapshot
```

#### 3. **TickCandleBuilder - Partial Candle State**

```java
// partialCandles: Only broker thread writes, others may read
private final Map<String, Map<TimeframeType, PartialCandle>> partialCandles = new ConcurrentHashMap<>();
```

**Thread Safety:**
- ✅ CHM for outer map (symbol → timeframe map)
- ✅ Inner map is CHM for thread safety
- ⚠️ PartialCandle fields are mutable (open/high/low/close/volume)
- ✅ SAFE: Only broker thread mutates PartialCandle

**Update Flow:**
```java
// 1. Broker thread updates partial candle (in-place mutation, single-threaded)
partial.high = partial.high.max(price);
partial.low = partial.low.min(price);
partial.close = price;
partial.volume += volume;

// 2. On candle close, convert to immutable Candle and persist
Candle candle = Candle.of(symbol, timeframe, partial.startTime, ...);
candleStore.addIntraday(candle);  // Persisted to DB
```

#### 4. **WsSession - Per-Client State**

```java
public final class WsSession {
    private final String sessionId;               // ✅ final (immutable)
    private final String userId;                  // ✅ final (immutable)
    private final Set<String> userBrokerIds;     // ✅ CHM.newKeySet() (thread-safe)
    private final Set<String> topics;            // ✅ CHM.newKeySet() (thread-safe)
    private final Instant connectedAt;           // ✅ final (immutable)
    private volatile Instant lastActivity;        // ✅ volatile (visibility)
}
```

**Thread Safety:**
- ✅ Final fields → immutable, no synchronization needed
- ✅ Sets use CHM.newKeySet() → concurrent add/remove
- ✅ lastActivity is volatile → multiple I/O threads write via touch()

---

## Market Data Architecture

### 3-Tier LTP Fallback Strategy

The system provides Last Traded Price (LTP) using a 3-tier fallback:

#### Tier 1: Real-Time Cache (Primary)
```java
MarketDataCache.TickData latestTick = marketDataCache.getLatestTick(symbol);
if (latestTick != null) {
    return latestTick.lastPrice();  // From today's session (real-time)
}
```
- **Source:** In-memory ConcurrentHashMap
- **When:** Market is open AND ticks are flowing
- **Performance:** O(1) lookup, zero DB query
- **Thread Safety:** CHM + immutable TickData

#### Tier 2: Previous Day Close (Fallback)
```java
PreviousClose prevClose = getLastClosePrice(symbol);
if (prevClose != null) {
    return prevClose.closePrice();  // From last DAILY candle
}
```
- **Source:** PostgreSQL query
```sql
SELECT close, ts FROM candles
WHERE symbol = ? AND timeframe = 'DAILY'
ORDER BY ts DESC LIMIT 1
```
- **When:** Market closed OR no ticks yet (pre-market, after-hours)
- **Performance:** ~1-5ms database query (indexed)

#### Tier 3: Null Fallback
```java
return w;  // Return watchlist entry with null lastPrice
```
- **When:** No historical candles available (new symbol)
- **Behavior:** API returns symbol info without price fields

### Data Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│ MARKET OPEN: Broker WebSocket Streaming                         │
│                                                                  │
│ Broker → Tick → TickCandleBuilder.onTick()                     │
│                       ├─ MarketDataCache.updateTick()           │
│                       │    └─ CHM.put(symbol, TickData)         │
│                       ├─ EventService.emitGlobal(TICK)          │
│                       │    └─ WebSocket broadcast (optional)    │
│                       └─ Build DAILY/HTF/ITF/LTF candles        │
│                                                                  │
│ HTTP /api/market-watch → Tier 1 Cache → Returns real-time LTP  │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ MARKET CLOSED: Historical Data Fallback                         │
│                                                                  │
│ HTTP /api/market-watch                                          │
│   ├─ Tier 1 Cache MISS (no ticks)                              │
│   └─ Tier 2 DB Query → DAILY candle close                      │
│        └─ Returns previous trading day's close price            │
└─────────────────────────────────────────────────────────────────┘
```

---

## Timeframe System

### Timeframe Types

| Timeframe | Interval | Lookback | Confluence Weight | Purpose |
|-----------|----------|----------|-------------------|---------|
| **DAILY** | 1 day (375 trading mins) | 250 candles (1 year) | 0.0 (not used in MTF) | Historical analysis, LTP fallback |
| **HTF** | 125 minutes | 175 candles | 0.50 (50%) | Higher timeframe trend |
| **ITF** | 25 minutes | 75 candles | 0.30 (30%) | Intermediate timeframe |
| **LTF** | 1 minute | 375 candles | 0.20 (20%) | Lower timeframe precision |

### Candle Building Strategy

**TickCandleBuilder** builds candles in real-time from incoming ticks:

```java
@Override
public void onTick(BrokerAdapter.Tick tick) {
    // Update all timeframes (including DAILY for EOD tracking)
    updateCandle(symbol, TimeframeType.DAILY, price, volume, timestamp);
    updateCandle(symbol, TimeframeType.LTF, price, volume, timestamp);
    updateCandle(symbol, TimeframeType.ITF, price, volume, timestamp);
    updateCandle(symbol, TimeframeType.HTF, price, volume, timestamp);
}
```

**Candle Start Time Calculation:**

- **DAILY:** Truncate to start of day (00:00:00 UTC) → 1 candle per calendar day
- **Intraday (HTF/ITF/LTF):** Truncate to interval boundary
  ```java
  long candleStartSeconds = (epochSeconds / intervalSeconds) * intervalSeconds;
  ```

**Candle Close Logic:**
```java
if (partial != null && !candleStart.equals(partial.startTime)) {
    // Time boundary crossed → close previous candle
    closeCandle(symbol, timeframe, partial);
    candleStore.addIntraday(candle);  // Persist to DB
    eventService.emitGlobal(EventType.CANDLE, payload, "TICK_BUILDER");
}
```

### Historical Candle Fetching

**CandleFetcher** downloads historical candles from broker APIs for backfill:

```java
// Fetch for all timeframes
fetchHistorical(userBrokerId, brokerCode, symbol, TimeframeType.DAILY, from, to);
fetchHistorical(userBrokerId, brokerCode, symbol, TimeframeType.HTF, from, to);
fetchHistorical(userBrokerId, brokerCode, symbol, TimeframeType.ITF, from, to);
fetchHistorical(userBrokerId, brokerCode, symbol, TimeframeType.LTF, from, to);
```

**Broker API Interval Mapping:**
```java
private int mapTimeframeToInterval(TimeframeType timeframe) {
    return switch (timeframe) {
        case DAILY -> 1440;  // 24 hours in minutes
        case LTF -> 1;
        case ITF -> 25;
        case HTF -> 125;
    };
}
```

---

## WebSocket Architecture

### WsHub Design

**Core Components:**

```java
public final class WsHub {
    // Active sessions: WebSocketChannel → WsSession
    private final ConcurrentMap<WebSocketChannel, WsSession> sessions = new ConcurrentHashMap<>();

    // User channels: userId → Set<WebSocketChannel>
    private final ConcurrentMap<String, Set<WebSocketChannel>> userChannels = new ConcurrentHashMap<>();

    // Batching queue (100K capacity)
    private final BlockingQueue<TradeEvent> batchQueue = new LinkedBlockingQueue<>(100_000);

    // Single scheduler thread for batching
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(...);
}
```

### Event Scopes

| Scope | Visibility | Example Use Cases |
|-------|------------|-------------------|
| **GLOBAL** | All authenticated users | System status, market open/close, TICK events |
| **USER** | Specific user only | Portfolio updates, P&L, user-specific alerts |
| **USER_BROKER** | Specific user+broker combo | Order updates, trade fills, broker-specific events |

### Batching Flow

```java
// 1. Business logic publishes event (any thread)
wsHub.publish(tradeEvent);  // Enqueue to BlockingQueue (non-blocking)

// 2. Flusher thread drains queue every 100ms
private void flushBatch() {
    List<TradeEvent> drained = new ArrayList<>(1024);
    batchQueue.drainTo(drained, 2000);  // Max 2000 events per batch

    // 3. Filter and broadcast to each session
    for (Map.Entry<WebSocketChannel, WsSession> entry : sessions.entrySet()) {
        List<ObjectNode> relevantEvents = filterForSession(drained, entry.getValue());
        sendBatchMessage(entry.getKey(), relevantEvents);
    }
}
```

**Filtering Logic:**
```java
public boolean shouldReceive(TradeEvent event) {
    // Check topic subscription
    if (!topics.isEmpty() && !topics.contains(event.type().name())) {
        return false;
    }

    // Check scope
    switch (event.scope()) {
        case GLOBAL -> return true;
        case USER -> return event.userId().equals(userId);
        case USER_BROKER -> {
            if (!event.userId().equals(userId)) return false;
            if (!userBrokerIds.isEmpty() && event.userBrokerId() != null) {
                return userBrokerIds.contains(event.userBrokerId());
            }
            return true;
        }
    }
}
```

### Connection Lifecycle

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. Client connects: ws://localhost:9091/ws?token=<jwt>         │
│    ├─ Undertow I/O thread handles connection                    │
│    ├─ Token validated via JwtService                            │
│    ├─ WsSession created with userId                             │
│    └─ Added to activeSessions & userChannels maps               │
├─────────────────────────────────────────────────────────────────┤
│ 2. Client subscribes: {"action":"subscribe","topics":["TICK"]} │
│    ├─ I/O thread handles message                                │
│    └─ Updates session.topics (CHM.newKeySet())                  │
├─────────────────────────────────────────────────────────────────┤
│ 3. Server publishes events: Flusher thread broadcasts           │
│    ├─ Every 100ms, drain batchQueue                             │
│    ├─ Filter by session.shouldReceive()                         │
│    └─ WebSockets.sendText() to client                           │
├─────────────────────────────────────────────────────────────────┤
│ 4. Client disconnects or error                                  │
│    ├─ Remove from activeSessions                                │
│    ├─ Remove from userChannels                                  │
│    └─ Close WebSocket channel                                   │
└─────────────────────────────────────────────────────────────────┘
```

---

## Thread Safety Patterns

### Pattern 1: Immutable Object Replacement

**Used in:** MarketDataCache, TickData

```java
// ✅ CORRECT: Replace entire immutable object
marketDataCache.updateTick(symbol, new BigDecimal("1234.50"), Instant.now());
// Internally: CHM.put(symbol, new TickData(price, ts))

// ❌ WRONG: In-place mutation of mutable object
tickData.setLastPrice(newPrice);  // Race condition!
```

**Why it works:**
- TickData is immutable (Java record)
- CHM.put() is atomic
- Readers get either old snapshot or new snapshot (no torn writes)

### Pattern 2: Volatile for Immutable References

**Used in:** WsSession.lastActivity

```java
private volatile Instant lastActivity;  // ✅ Visibility guaranteed

public void touch() {
    this.lastActivity = Instant.now();  // Multiple I/O threads write
}
```

**Why it works:**
- Instant is immutable
- volatile ensures visibility across threads
- No need for AtomicReference (cheaper than atomic operations)

### Pattern 3: ConcurrentHashMap for Set Values

**Used in:** WsHub.userChannels, WsSession.topics

```java
// ✅ CORRECT: Concurrent set in map value
ConcurrentMap<String, Set<WebSocketChannel>> userChannels = new ConcurrentHashMap<>();
Set<WebSocketChannel> channels = userChannels.computeIfAbsent(
    userId,
    k -> ConcurrentHashMap.newKeySet()  // Thread-safe set
);
channels.add(channel);  // Multiple threads can add/remove

// ❌ WRONG: Non-thread-safe set
userChannels.computeIfAbsent(userId, k -> new HashSet<>());  // Race condition!
```

### Pattern 4: Single-Writer, Multiple-Reader

**Used in:** TickCandleBuilder.partialCandles

```java
// partialCandles: Only broker thread writes
private final Map<String, Map<TimeframeType, PartialCandle>> partialCandles = new ConcurrentHashMap<>();

// ✅ SAFE: Only broker thread mutates PartialCandle fields
partial.high = partial.high.max(price);
partial.close = price;

// ✅ SAFE: On close, publish immutable snapshot
Candle candle = Candle.of(symbol, timeframe, partial.startTime, ...);
candleStore.addIntraday(candle);
```

**Why it works:**
- PartialCandle is mutable BUT only broker thread writes
- On candle close, converted to immutable Candle for persistence
- Other threads read persisted Candles (immutable snapshots)

---

## Performance Considerations

### Bottlenecks Identified

#### 1. DB Write on Broker Tick Thread ⚠️

**Current Issue:**
```java
private TradeEvent persistAndBroadcast(TradeEvent e) {
    TradeEvent persisted = repo.append(e);  // ⚠️ BLOCKS broker thread
    wsHub.publish(persisted);               // Fast (enqueue)
    return persisted;
}
```

**Impact:**
- Blocks broker thread during DB I/O (1-10ms per tick)
- Can cause tick backpressure
- Distorts candle timing

**Future Fix Pattern:**
```java
// Broker thread: enqueue only
eventQueue.offer(event);

// Separate DB writer thread: persist + publish
while (running) {
    TradeEvent event = eventQueue.take();
    TradeEvent persisted = repo.append(event);
    wsHub.publish(persisted);
}
```

#### 2. Single WS Flusher Thread ⚠️

**Current Limitation:**
- Single thread flushes to ALL clients every 100ms
- Becomes CPU-bound at ~10K concurrent clients

**Scale Pattern:**
```java
// Partition events by shard
int shard = event.hashCode() % NUM_FLUSHERS;
batchQueues[shard].offer(event);

// N flusher threads (e.g., 4 flushers for 40K clients)
for (int i = 0; i < NUM_FLUSHERS; i++) {
    Flusher flusher = new Flusher(batchQueues[i], sessionShards[i]);
    flushers.add(flusher);
}
```

### Performance Wins ✅

1. **Zero DB Writes for Ticks During Trading:**
   - MarketDataCache is in-memory only
   - DB write only on candle close (~every 125 min for HTF)

2. **O(1) Market Data Lookup:**
   - ConcurrentHashMap for symbol → TickData
   - No locks, no DB query

3. **Batched WebSocket Broadcasts:**
   - 100ms interval reduces overhead vs per-event send
   - Up to 2000 events per batch

4. **Immutable Snapshots:**
   - Zero copying needed
   - Safe to share across threads

---

## Future Enhancements

### 1. Async DB Writer Pool
- Move event persistence off broker thread
- Use bounded BlockingQueue + dedicated writer thread(s)
- Maintain "DB is source of truth before WS" semantics

### 2. Partitioned WS Flushers
- Shard sessions across N flusher threads
- Scale to 40K+ concurrent clients
- Maintain per-shard batching

### 3. Strategy Runtime Isolation
- `ConcurrentHashMap<UserBrokerKey, StrategyRuntime>`
- Per-user-broker position/order/risk state
- Thread-safe access from broker thread + HTTP API

### 4. Event Dedupe with Bounded Cache
- `ConcurrentHashMap<UserBrokerKey, ConcurrentHashMap<String, Long>>` for seenEventIds
- Periodic cleanup to prevent memory leak
- Or use Caffeine for bounded LRU cache

---

## Summary

AnnuPaper v04 achieves high-performance, low-latency trading operations through:

1. **Lock-free concurrency** with ConcurrentHashMap and immutable objects
2. **Zero hot-path DB writes** using in-memory MarketDataCache
3. **Proper thread isolation** (broker thread, I/O threads, flusher)
4. **Batched WebSocket broadcasting** (100ms flush interval)
5. **3-tier LTP fallback** (cache → DB DAILY close → null)
6. **Multi-timeframe candle building** (DAILY, HTF, ITF, LTF)

**Thread Safety Score:**
- ✅ WsHub activeSessions: CHM + CHM.newKeySet()
- ✅ MarketDataCache: CHM + immutable TickData
- ✅ WsSession.lastActivity: volatile Instant
- ✅ TickCandleBuilder: Single-writer pattern

**Production Readiness:**
- ✅ Thread-safe concurrent maps
- ✅ Real-time price caching
- ⚠️ DB write on tick thread (known bottleneck, fix planned)
- ⚠️ Single flusher thread (scales to ~10K clients)

---

**Document Version:** 1.0
**Architecture Frozen:** January 12, 2026
**Next Review:** After production load testing (40K+ symbols, 1000+ clients)
