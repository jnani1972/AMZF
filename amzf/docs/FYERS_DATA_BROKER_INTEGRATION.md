# Fyers as Main DATA Broker Integration Guide

**Date:** 2026-01-09
**Purpose:** Configure Fyers as the main DATA broker for market data feed
**Status:** Configuration Guide

---

## Overview

The AnnuPaper system distinguishes between two broker roles:
- **DATA Broker:** Provides market data (quotes, candles, ticks) - ONE per system
- **EXEC Brokers:** Execute trades for specific users - MULTIPLE allowed

**Fyers** is configured as the DATA broker and provides:
1. ✅ **WebSocket** - Real-time tick data (LTP, bid/ask, volume)
2. ✅ **Historical API** - Historical candles (1min, 25min, 125min)

---

## Current Configuration

### Database Check

```sql
-- Check DATA broker configuration
SELECT user_broker_id, user_id, broker_id, role, connected, status
FROM user_brokers
WHERE role = 'DATA' AND deleted_at IS NULL;

user_broker_id  | user_id    | broker_id | role | connected | status
----------------|------------|-----------|------|-----------|--------
UB_DATA_E7DE4B  | UADMIN37A0 | B_FYERS   | DATA | false     | ACTIVE
```

✅ **Fyers is already configured as DATA broker**

---

## Fyers API v3 Overview

### API Documentation

While the direct URL https://myapi.fyers.in/docsv3 requires JavaScript, Fyers API v3 provides:

**Base URLs:**
- **Market Data API:** `https://api-t1.fyers.in/data/` (test) or `https://api.fyers.in/data/` (production)
- **WebSocket:** `wss://api-t1.fyers.in/data-ws/v2` (test) or `wss://api.fyers.in/data-ws/v2` (production)

**Key Endpoints:**
1. **Historical Candles:** `POST /data/history`
2. **Quotes (LTP):** `GET /data/quotes`
3. **Market Depth:** `GET /data/depth`
4. **WebSocket:** Real-time tick streaming

### Authentication Flow

```
1. User logs in to Fyers portal → Gets auth_code
2. Exchange auth_code for access_token
3. Use access_token for all API calls
4. Use access_token for WebSocket connection
```

---

## Fyers Adapter Implementation

### Current Status

**File:** `/tmp/annu-v04/src/main/java/in/annupaper/broker/adapters/FyersAdapter.java`

**Current State:** ✅ Stub implementation (simulated data)
**Production Required:** Real Fyers API integration

### Key Methods for DATA Broker

#### 1. Historical Candles (Already Implemented - Stub)

```java
@Override
public CompletableFuture<List<HistoricalCandle>> getHistoricalCandles(
    String symbol,
    int interval,
    long fromEpoch,
    long toEpoch
) {
    // Fyers API v3: POST /data/history
    // URL: https://api-t1.fyers.in/data/history
    // Body: {
    //   "symbol": "NSE:SBIN-EQ",
    //   "resolution": "1",          // 1, 2, 3, 5, 10, 15, 20, 30, 60, 120, 240, 1D
    //   "date_format": "1",         // Unix epoch
    //   "range_from": "1640000000",
    //   "range_to": "1640100000"
    // }

    // Currently: Simulates data
    // Production: Call real Fyers API
}
```

**Interval Mapping:**
- `1` → 1 minute (LTF)
- `25` → 25 minutes (ITF) - **Note: Fyers doesn't have 25min, use 30min**
- `125` → 125 minutes (HTF) - **Note: Fyers doesn't have 125min, use 120min (2H)**

#### 2. WebSocket Tick Streaming (Already Implemented - Stub)

```java
@Override
public void subscribeTicks(List<String> symbols, TickListener listener) {
    // Fyers WebSocket v2
    // URL: wss://api-t1.fyers.in/data-ws/v2
    // Protocol:
    // 1. Connect with access_token
    // 2. Send subscribe message: {"T":"SUB_L2","L2_SCRIPS":["NSE:SBIN-EQ"]}
    // 3. Receive tick data: {"symbol":"NSE:SBIN-EQ","ltp":500.50,"..."}

    // Currently: Simulates 1-second ticks
    // Production: Connect to real Fyers WebSocket
}
```

**Tick Data Structure:**
```json
{
  "symbol": "NSE:SBIN-EQ",
  "ltp": 500.50,           // Last Traded Price
  "open": 498.00,
  "high": 502.00,
  "low": 497.00,
  "close": 499.50,
  "volume": 1500000,
  "bid": 500.45,
  "ask": 500.55,
  "bid_qty": 1000,
  "ask_qty": 800,
  "timestamp": 1640000000000
}
```

---

## Production Implementation Steps

### Step 1: Get Fyers API Credentials

1. **Sign up** at https://myapi.fyers.in/
2. **Create an App:**
   - App Name: AnnuPaper Data Feed
   - Redirect URL: http://localhost:8080/fyers/callback (or your domain)
3. **Get Credentials:**
   - App ID (Client ID)
   - Secret Key
   - Redirect URL

### Step 2: Add Fyers Java SDK

**Maven Dependency (if available):**
```xml
<dependency>
    <groupId>com.fyers</groupId>
    <artifactId>fyers-api-java</artifactId>
    <version>3.0.0</version>
</dependency>
```

**Alternative:** Use HTTP client (OkHttp, Apache HttpClient, or Java 11+ HttpClient)

### Step 3: Implement Real Fyers API

#### Historical Candles Implementation

```java
@Override
public CompletableFuture<List<HistoricalCandle>> getHistoricalCandles(
    String symbol,
    int interval,
    long fromEpoch,
    long toEpoch
) {
    return CompletableFuture.supplyAsync(() -> {
        try {
            // Convert symbol format: RELIANCE → NSE:RELIANCE-EQ
            String fyersSymbol = convertToFyersSymbol(symbol);

            // Map interval to Fyers resolution
            String resolution = mapIntervalToResolution(interval);

            // Prepare request
            String url = "https://api.fyers.in/data/history";
            JSONObject requestBody = new JSONObject();
            requestBody.put("symbol", fyersSymbol);
            requestBody.put("resolution", resolution);
            requestBody.put("date_format", "1");  // Unix epoch
            requestBody.put("range_from", String.valueOf(fromEpoch));
            requestBody.put("range_to", String.valueOf(toEpoch));

            // Make HTTP POST request
            HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", credentials.accessToken())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );

            // Parse response
            JSONObject json = new JSONObject(response.body());
            JSONArray candles = json.getJSONArray("candles");

            // Convert to HistoricalCandle objects
            List<HistoricalCandle> result = new ArrayList<>();
            for (int i = 0; i < candles.length(); i++) {
                JSONArray candle = candles.getJSONArray(i);
                result.add(new HistoricalCandle(
                    candle.getLong(0),                        // timestamp
                    new BigDecimal(candle.getString(1)),      // open
                    new BigDecimal(candle.getString(2)),      // high
                    new BigDecimal(candle.getString(3)),      // low
                    new BigDecimal(candle.getString(4)),      // close
                    candle.getLong(5)                         // volume
                ));
            }

            log.info("[FYERS] Fetched {} candles for {}", result.size(), symbol);
            return result;

        } catch (Exception e) {
            log.error("[FYERS] Failed to fetch candles: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch historical candles", e);
        }
    });
}

private String convertToFyersSymbol(String symbol) {
    // Convert: RELIANCE → NSE:RELIANCE-EQ
    // Assumptions:
    // - All symbols are NSE equity
    // - Add exchange and instrument type
    return "NSE:" + symbol + "-EQ";
}

private String mapIntervalToResolution(int interval) {
    // Map AnnuPaper intervals to Fyers resolutions
    // Fyers supports: 1, 2, 3, 5, 10, 15, 20, 30, 60, 120, 240, 1D
    return switch (interval) {
        case 1 -> "1";      // LTF: 1 minute
        case 25 -> "30";    // ITF: Use 30 minutes (closest to 25)
        case 125 -> "120";  // HTF: Use 120 minutes/2H (closest to 125)
        default -> "1";     // Default to 1 minute
    };
}
```

#### WebSocket Implementation

```java
@Override
public void subscribeTicks(List<String> symbols, TickListener listener) {
    log.info("[FYERS] Subscribing to ticks: {}", symbols);

    try {
        // Connect to Fyers WebSocket
        String wsUrl = "wss://api.fyers.in/data-ws/v2";
        WebSocketClient client = new WebSocketClient(new URI(wsUrl)) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                log.info("[FYERS] WebSocket connected");

                // Subscribe to symbols
                JSONObject subscribeMsg = new JSONObject();
                subscribeMsg.put("T", "SUB_L2");

                JSONArray scrips = new JSONArray();
                for (String symbol : symbols) {
                    scrips.put(convertToFyersSymbol(symbol));
                }
                subscribeMsg.put("L2_SCRIPS", scrips);

                send(subscribeMsg.toString());
                log.info("[FYERS] Subscribed to {} symbols", symbols.size());
            }

            @Override
            public void onMessage(String message) {
                try {
                    JSONObject tick = new JSONObject(message);

                    // Parse Fyers tick data
                    String fyersSymbol = tick.getString("symbol");
                    String symbol = convertFromFyersSymbol(fyersSymbol);

                    Tick tickData = new Tick(
                        symbol,
                        new BigDecimal(tick.getString("ltp")),
                        new BigDecimal(tick.getString("open")),
                        new BigDecimal(tick.getString("high")),
                        new BigDecimal(tick.getString("low")),
                        new BigDecimal(tick.getString("close")),
                        tick.getLong("volume"),
                        new BigDecimal(tick.getString("bid")),
                        new BigDecimal(tick.getString("ask")),
                        tick.getInt("bid_qty"),
                        tick.getInt("ask_qty"),
                        tick.getLong("timestamp")
                    );

                    // Notify listener
                    listener.onTick(tickData);

                } catch (Exception e) {
                    log.warn("[FYERS] Failed to parse tick: {}", e.getMessage());
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                log.warn("[FYERS] WebSocket closed: {} - {}", code, reason);
                // Implement reconnection logic
            }

            @Override
            public void onError(Exception ex) {
                log.error("[FYERS] WebSocket error: {}", ex.getMessage());
            }
        };

        client.connect();
        this.wsClient = client;

    } catch (Exception e) {
        log.error("[FYERS] Failed to connect WebSocket: {}", e.getMessage());
        throw new RuntimeException("Failed to subscribe to ticks", e);
    }
}

private String convertFromFyersSymbol(String fyersSymbol) {
    // Convert: NSE:RELIANCE-EQ → RELIANCE
    if (fyersSymbol.contains(":") && fyersSymbol.contains("-")) {
        String[] parts = fyersSymbol.split(":");
        String symbolPart = parts[1];
        return symbolPart.split("-")[0];
    }
    return fyersSymbol;
}
```

---

## Configuration Steps

### Step 1: Update Broker Credentials

**Via Admin API:**
```bash
curl -X POST http://localhost:9090/api/admin/user-brokers \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "UADMIN37A0",
    "brokerId": "B_FYERS",
    "credentials": {
      "appId": "YOUR_FYERS_APP_ID",
      "secretKey": "YOUR_FYERS_SECRET_KEY",
      "accessToken": "YOUR_FYERS_ACCESS_TOKEN",
      "redirectUrl": "http://localhost:8080/fyers/callback"
    },
    "isDataBroker": true
  }'
```

**Or directly in database:**
```sql
UPDATE user_brokers
SET credentials = jsonb_build_object(
  'appId', 'YOUR_FYERS_APP_ID',
  'secretKey', 'YOUR_FYERS_SECRET_KEY',
  'accessToken', 'YOUR_FYERS_ACCESS_TOKEN'
)
WHERE user_broker_id = 'UB_DATA_E7DE4B';
```

### Step 2: Connect DATA Broker

**On Application Startup:**
The system automatically connects to the DATA broker:

```java
// In App.java (already implemented)
Optional<UserBroker> dataBroker = userBrokerRepo.findDataBroker();
if (dataBroker.isPresent()) {
    BrokerAdapter adapter = brokerFactory.getOrCreate(
        dataBroker.get().userBrokerId(),
        dataBroker.get().brokerId()
    );

    // Connect
    adapter.connect(/* credentials from user_brokers.credentials */)
        .thenAccept(result -> {
            if (result.success()) {
                log.info("DATA broker connected: {}", dataBroker.get().brokerId());
            }
        });
}
```

### Step 3: Add Watchlist Symbols

**The DATA broker will stream ticks for watchlist symbols:**

```sql
-- Add symbols to watchlist (admin API or direct DB)
INSERT INTO watchlist (user_broker_id, symbol, exchange, enabled)
VALUES
  ('UB_DATA_E7DE4B', 'RELIANCE', 'NSE', true),
  ('UB_DATA_E7DE4B', 'TCS', 'NSE', true),
  ('UB_DATA_E7DE4B', 'INFY', 'NSE', true),
  ('UB_DATA_E7DE4B', 'HDFC', 'NSE', true),
  ('UB_DATA_E7DE4B', 'SBIN', 'NSE', true);
```

**System will automatically subscribe to these symbols via WebSocket.**

---

## Data Flow

### Historical Candles Flow

```
1. Startup: CandleReconciler checks for missing candles
2. CandleFetcher.fetchHistorical() called
3. BrokerAdapter.getHistoricalCandles() → Fyers API
4. Candles stored in database (candles table)
5. CandleStore maintains in-memory + PostgreSQL
```

### Real-Time Tick Flow

```
1. Startup: System subscribes to watchlist symbols
2. Fyers WebSocket sends tick updates (every ~1 second)
3. TickCandleBuilder.onTick() processes each tick
4. Builds 1min/25min/125min candles incrementally
5. On candle close: persisted to database
6. Emit CANDLE event via EventService (WebSocket to UI)
```

---

## Rate Limits

**Fyers API v3 Typical Limits:**
- **Historical API:** ~1 request/second
- **WebSocket:** Unlimited ticks (within connection limits)
- **Max Symbols:** ~1000 symbols per WebSocket connection

**Recommendation:**
- Use ONE WebSocket connection for all DATA needs
- Batch historical candle fetches with delays
- Cache candles to avoid repeated API calls

---

## Testing

### 1. Test Historical Candles

```java
// In a test or via API endpoint
FyersAdapter adapter = new FyersAdapter();
adapter.connect(credentials).get();

List<BrokerAdapter.HistoricalCandle> candles = adapter.getHistoricalCandles(
    "RELIANCE",
    1,  // 1 minute
    Instant.now().minus(Duration.ofHours(6)).getEpochSecond(),
    Instant.now().getEpochSecond()
).get();

System.out.println("Fetched " + candles.size() + " candles");
```

### 2. Test WebSocket Ticks

```java
FyersAdapter adapter = new FyersAdapter();
adapter.connect(credentials).get();

adapter.subscribeTicks(
    List.of("RELIANCE", "TCS", "INFY"),
    tick -> {
        System.out.println("Tick: " + tick.symbol() + " LTP=" + tick.lastPrice());
    }
);

// Wait for ticks...
Thread.sleep(10000);
```

---

## Migration from Stub to Production

**Current:** Stub implementation (simulated data)
**Production:** Real Fyers API

**Steps:**
1. ✅ Already has DATA broker configured
2. ✅ Already has FyersAdapter structure
3. ⏳ **TODO:** Replace stub methods with real Fyers API calls
4. ⏳ **TODO:** Add Fyers SDK or HTTP client
5. ⏳ **TODO:** Implement WebSocket connection
6. ⏳ **TODO:** Add error handling and reconnection logic
7. ⏳ **TODO:** Test with real Fyers credentials

---

## Advantages of Fyers as DATA Broker

✅ **Comprehensive API:** Historical + Real-time
✅ **WebSocket Support:** Low-latency tick streaming
✅ **Multiple Timeframes:** 1min to daily candles
✅ **Reliable Infrastructure:** Fyers is a registered broker
✅ **Good Documentation:** API v3 well-documented
✅ **Rate Limits:** Sufficient for retail trading systems

---

## Alternative: Multiple DATA Sources

If you need redundancy or multiple data feeds:

```sql
-- Add multiple DATA brokers (not currently supported, but possible)
INSERT INTO user_brokers (user_broker_id, user_id, broker_id, role, ...)
VALUES
  ('UB_DATA_FYERS', 'UADMIN37A0', 'B_FYERS', 'DATA_PRIMARY', ...),
  ('UB_DATA_ZERODHA', 'UADMIN37A0', 'B_ZERODHA', 'DATA_BACKUP', ...);
```

**Would require code changes to support multiple DATA brokers with fallback logic.**

---

## Summary

**Current Setup:**
- ✅ Fyers configured as DATA broker
- ✅ Adapter structure ready
- ✅ Historical candles method exists (stub)
- ✅ WebSocket tick streaming exists (stub)
- ⏳ **Production implementation pending**

**For Production Use:**
1. Get Fyers API credentials (App ID, Secret Key)
2. Generate access token via OAuth flow
3. Implement real API calls in FyersAdapter
4. Test with real market data
5. Deploy and monitor

**Both Required:**
- ✅ **Historical API** - For backfilling candles on startup
- ✅ **WebSocket** - For real-time tick data during market hours

---

**Status:** Configuration Guide Complete
**Next Step:** Implement real Fyers API integration in FyersAdapter
**Documentation:** https://myapi.fyers.in/docsv3 (requires JavaScript-enabled browser)

---

**Last Updated:** 2026-01-09
**Created By:** Claude Code (Sonnet 4.5)

**Note:** The Fyers API documentation URL https://myapi.fyers.in/docsv3 requires a JavaScript-enabled browser to view. For detailed API reference, log in to the Fyers API portal directly.
