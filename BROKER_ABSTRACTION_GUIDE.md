# Broker Abstraction Split Guide - AMZF Trading System

**Purpose**: Detailed guide for splitting BrokerAdapter into DataBroker and OrderBroker with complete code examples

---

## Table of Contents

1. [Problem Statement](#1-problem-statement)
2. [Target Architecture](#2-target-architecture)
3. [Interface Definitions](#3-interface-definitions)
4. [Adapter Implementations](#4-adapter-implementations)
5. [Factory Pattern](#5-factory-pattern)
6. [Service Updates](#6-service-updates)
7. [Testing Strategy](#7-testing-strategy)
8. [Migration Checklist](#8-migration-checklist)

---

## 1. Problem Statement

### 1.1 Current Limitation

**Monolithic BrokerAdapter**:
```java
public interface BrokerAdapter {
    // Data feed methods
    void connect(UserBroker userBroker);
    void subscribeTicks(String symbol, TickListener listener);
    List<Candle> getHistoricalCandles(...);
    List<Instrument> getInstruments();

    // Order execution methods
    String placeOrder(OrderRequest req);
    void modifyOrder(String orderId, OrderRequest req);
    void cancelOrder(String orderId);
    OrderStatus getOrderStatus(String orderId);

    // Common methods
    boolean isConnected();
    HealthStatus getHealthStatus();
}
```

**Problem**: Cannot use different brokers for data vs execution
- Want: Zerodha data (cheap) + FYERS orders (reliable)
- Current: Must use same broker for both

### 1.2 Desired Capability

**Multi-Broker Configuration**:
```
User Configuration:
├── DATA Broker: Zerodha
│   ├── Subscribe to NSE:RELIANCE ticks
│   ├── Fetch historical candles
│   └── Download instrument master
└── EXEC Broker: FYERS
    ├── Place buy order for RELIANCE
    ├── Monitor order status
    └── Close position when signal triggers
```

**Benefits**:
- Cost optimization (Zerodha data = ₹2,000/month vs FYERS = ₹10,000/month)
- Reliability (use best broker for each purpose)
- Flexibility (switch brokers independently)
- Risk management (isolate data feed failures from order execution)

---

## 2. Target Architecture

### 2.1 Separation of Concerns

```
┌─────────────────────────────────────────────────────────────┐
│                    Application Layer                         │
│  ┌──────────────────┐      ┌──────────────────┐            │
│  │ TickCandleBuilder│      │OrderPlacementSvc │            │
│  │ MtfSignalGen     │      │TradeManagementSvc│            │
│  └────────┬─────────┘      └────────┬─────────┘            │
└───────────┼──────────────────────────┼──────────────────────┘
            │                          │
            ▼                          ▼
┌───────────────────────┐  ┌───────────────────────┐
│   DataBroker          │  │   OrderBroker         │
│   (Market Data)       │  │   (Order Execution)   │
├───────────────────────┤  ├───────────────────────┤
│ + connect()           │  │ + connect()           │
│ + subscribeTicks()    │  │ + placeOrder()        │
│ + getHistoricalData() │  │ + modifyOrder()       │
│ + getInstruments()    │  │ + cancelOrder()       │
│ + getHealthStatus()   │  │ + getOrderStatus()    │
└───────────────────────┘  └───────────────────────┘
            │                          │
            ├──────────────────────────┤
            │                          │
    ┌───────▼────────┐        ┌───────▼────────┐
    │ ZerodhaData    │        │ FyersOrder     │
    │ Adapter        │        │ Adapter        │
    └────────────────┘        └────────────────┘
         (Cheap)                   (Reliable)
```

### 2.2 Key Principles

1. **Single Responsibility**: Each adapter does ONE thing well
2. **Interface Segregation**: Clients depend only on what they need
3. **Open/Closed**: Easy to add new brokers without changing existing code
4. **Dependency Inversion**: Services depend on interfaces, not implementations

---

## 3. Interface Definitions

### 3.1 DataBroker Interface

```java
package in.annupaper.infrastructure.broker.data;

import in.annupaper.domain.broker.UserBroker;
import in.annupaper.domain.market.Candle;
import in.annupaper.domain.market.Instrument;
import in.annupaper.domain.market.Tick;
import in.annupaper.domain.market.TimeframeType;

import java.time.LocalDate;
import java.util.List;

/**
 * Interface for market data providers.
 *
 * Responsibilities:
 * - Connect to live data feeds (WebSocket/REST)
 * - Subscribe to real-time tick data
 * - Fetch historical candle data
 * - Download instrument master
 * - Provide health status
 *
 * Implementations:
 * - FyersDataAdapter: FYERS v3 data feed
 * - ZerodhaDataAdapter: Zerodha Kite Connect data feed
 * - DhanDataAdapter: Dhan data feed
 * - RelayDataAdapter: Feed relay for distributed setup
 */
public interface DataBroker {

    /**
     * Establish connection to data feed using user credentials.
     *
     * For WebSocket feeds:
     * - Load OAuth access token from UserBrokerSession
     * - Establish WebSocket connection
     * - Set up reconnection logic
     * - Start health monitoring
     *
     * @param userBroker User-broker link with credentials and session
     * @throws ConnectionException if connection fails
     */
    void connect(UserBroker userBroker);

    /**
     * Disconnect from data feed.
     * - Close WebSocket connection
     * - Clear tick listeners
     * - Stop health monitoring
     */
    void disconnect();

    /**
     * Subscribe to real-time tick data for a symbol.
     * Multiple listeners can subscribe to the same symbol.
     *
     * Implementation notes:
     * - Deduplicate subscriptions (don't resubscribe if already subscribed)
     * - Register listener in internal map
     * - Send WebSocket subscription message
     * - Start tick delivery on next tick
     *
     * @param symbol Trading symbol (e.g., "NSE:RELIANCE-EQ")
     * @param listener Callback for tick delivery
     */
    void subscribeTicks(String symbol, TickListener listener);

    /**
     * Unsubscribe from tick data.
     * - Remove listener from internal map
     * - If no listeners remain for symbol, send WebSocket unsubscribe message
     *
     * @param symbol Trading symbol
     * @param listener Listener to remove
     */
    void unsubscribeTicks(String symbol, TickListener listener);

    /**
     * Fetch historical candles for a symbol and timeframe.
     *
     * Behavior:
     * - If broker supports the timeframe natively, fetch directly
     * - If not, fetch smaller timeframe and aggregate up
     *   (e.g., fetch 5-min and aggregate to 25-min)
     *
     * @param symbol Trading symbol
     * @param timeframe Candle timeframe (1MIN, 25MIN, 125MIN, DAILY)
     * @param fromDate Start date (inclusive)
     * @param toDate End date (inclusive)
     * @return List of candles, ordered by time (oldest first)
     * @throws DataFetchException if fetch fails
     */
    List<Candle> getHistoricalCandles(
        String symbol,
        TimeframeType timeframe,
        LocalDate fromDate,
        LocalDate toDate
    );

    /**
     * Download instrument master from broker.
     *
     * Returns all tradeable instruments with metadata:
     * - Symbol (exchange:name format)
     * - Name (company name)
     * - Instrument token (broker-specific ID)
     * - Lot size
     * - Tick size
     * - Exchange (NSE, BSE, etc.)
     * - Segment (EQ, FUT, OPT, etc.)
     *
     * @return List of instruments
     * @throws DataFetchException if download fails
     */
    List<Instrument> getInstruments();

    /**
     * Check if data feed is connected and actively receiving data.
     *
     * @return true if WebSocket connected and ticks flowing, false otherwise
     */
    boolean isConnected();

    /**
     * Get detailed health status of data feed.
     *
     * Health metrics:
     * - Connection state (CONNECTED, DISCONNECTED, RECONNECTING)
     * - Last tick time (for stale feed detection)
     * - Error count (consecutive failures)
     * - Circuit breaker state (CLOSED, OPEN, HALF_OPEN)
     *
     * @return Health status object
     */
    DataFeedHealth getHealthStatus();

    /**
     * Get broker identifier (FYERS, ZERODHA, DHAN, etc.).
     *
     * @return Broker ID from BrokerIds constants
     */
    String getBrokerId();
}
```

### 3.2 OrderBroker Interface

```java
package in.annupaper.infrastructure.broker.order;

import in.annupaper.domain.broker.UserBroker;
import in.annupaper.domain.order.OrderRequest;
import in.annupaper.domain.order.OrderResponse;
import in.annupaper.domain.order.OrderStatus;
import in.annupaper.domain.trade.Position;

import java.util.List;

/**
 * Interface for order execution providers.
 *
 * Responsibilities:
 * - Connect to broker API
 * - Place new orders
 * - Modify existing orders
 * - Cancel orders
 * - Fetch order status
 * - Get current positions
 *
 * Implementations:
 * - FyersOrderAdapter: FYERS v3 order execution
 * - ZerodhaOrderAdapter: Zerodha Kite Connect order execution
 * - DhanOrderAdapter: Dhan order execution
 * - DummyOrderAdapter: Paper trading (simulated orders)
 */
public interface OrderBroker {

    /**
     * Establish connection to broker using user credentials.
     *
     * Implementation:
     * - Load OAuth access token from UserBrokerSession
     * - Validate token (optional: test with account info API call)
     * - Store token for subsequent API calls
     *
     * @param userBroker User-broker link with credentials and session
     * @throws ConnectionException if connection fails
     */
    void connect(UserBroker userBroker);

    /**
     * Disconnect from broker.
     * - Clear access token
     * - Clean up resources
     */
    void disconnect();

    /**
     * Place a new order.
     *
     * Flow:
     * 1. Validate OrderRequest (required fields)
     * 2. Map OrderRequest to broker-specific format
     * 3. Make REST API call to broker
     * 4. Parse response
     * 5. Return broker order ID
     *
     * Error handling:
     * - Insufficient funds → throw InsufficientFundsException
     * - Invalid symbol → throw InvalidSymbolException
     * - API error → throw OrderPlacementException
     *
     * @param request Order details (symbol, qty, price, type, etc.)
     * @return Broker order ID (unique identifier for this order)
     * @throws OrderPlacementException if placement fails
     */
    String placeOrder(OrderRequest request);

    /**
     * Modify an existing order (change price or quantity).
     *
     * Supported modifications:
     * - Limit price (for LIMIT orders)
     * - Stop price (for STOP_LOSS orders)
     * - Quantity (increase or decrease)
     *
     * @param brokerOrderId Broker order ID from placeOrder()
     * @param request Updated order details
     * @throws OrderModificationException if modification fails
     */
    void modifyOrder(String brokerOrderId, OrderRequest request);

    /**
     * Cancel an existing order.
     *
     * Behavior:
     * - If order already filled, throw OrderAlreadyFilledException
     * - If order already cancelled, return silently (idempotent)
     * - Otherwise, send cancel request to broker
     *
     * @param brokerOrderId Broker order ID from placeOrder()
     * @throws OrderCancellationException if cancellation fails
     */
    void cancelOrder(String brokerOrderId);

    /**
     * Get current status of an order.
     *
     * Makes REST API call to broker to fetch order details:
     * - Status (PENDING, PLACED, PARTIAL, FILLED, REJECTED, CANCELLED)
     * - Filled quantity
     * - Average fill price
     * - Order time
     * - Fill time (if filled)
     *
     * @param brokerOrderId Broker order ID from placeOrder()
     * @return Order status object
     * @throws OrderNotFoundException if order not found
     */
    OrderStatus getOrderStatus(String brokerOrderId);

    /**
     * Get all open positions for this user.
     *
     * Position includes:
     * - Symbol
     * - Net quantity (positive for long, negative for short)
     * - Average price
     * - Current price (LTP)
     * - Unrealized P&L
     *
     * @return List of open positions
     */
    List<Position> getPositions();

    /**
     * Get today's order history.
     *
     * Returns all orders placed today:
     * - Order ID
     * - Symbol
     * - Status
     * - Filled quantity
     * - Average fill price
     * - Timestamps
     *
     * @return List of order responses
     */
    List<OrderResponse> getOrderHistory();

    /**
     * Check if broker connection is active.
     *
     * @return true if connected (access token valid), false otherwise
     */
    boolean isConnected();

    /**
     * Get broker identifier (FYERS, ZERODHA, DHAN, etc.).
     *
     * @return Broker ID from BrokerIds constants
     */
    String getBrokerId();
}
```

### 3.3 Supporting Models

#### DataFeedHealth

```java
package in.annupaper.infrastructure.broker.data;

import java.time.Instant;

public record DataFeedHealth(
    ConnectionState state,
    Instant lastTickTime,
    int errorCount,
    CircuitBreakerState circuitBreakerState
) {
    public enum ConnectionState {
        CONNECTED,
        DISCONNECTED,
        RECONNECTING,
        STALE  // Connected but no recent ticks
    }

    public enum CircuitBreakerState {
        CLOSED,   // Normal operation
        OPEN,     // Too many errors, circuit open
        HALF_OPEN // Testing if connection recovered
    }

    /**
     * Check if feed is healthy (connected and fresh)
     */
    public boolean isHealthy() {
        return state == ConnectionState.CONNECTED &&
               circuitBreakerState == CircuitBreakerState.CLOSED &&
               lastTickTime != null &&
               lastTickTime.isAfter(Instant.now().minusSeconds(300)); // 5 min freshness
    }
}
```

#### TickListener

```java
package in.annupaper.infrastructure.broker.data;

import in.annupaper.domain.market.Tick;

@FunctionalInterface
public interface TickListener {
    /**
     * Callback invoked when a new tick is received.
     *
     * Implementation notes:
     * - This method should return quickly (< 10ms)
     * - Heavy processing should be offloaded to background threads
     * - Do not block the WebSocket thread
     *
     * @param tick Real-time tick data
     */
    void onTick(Tick tick);
}
```

---

## 4. Adapter Implementations

### 4.1 FyersDataAdapter (Extract from FyersAdapter)

```java
package in.annupaper.infrastructure.broker.data;

import in.annupaper.domain.broker.BrokerIds;
import in.annupaper.domain.broker.UserBroker;
import in.annupaper.domain.broker.UserBrokerSession;
import in.annupaper.domain.market.Candle;
import in.annupaper.domain.market.Instrument;
import in.annupaper.domain.market.Tick;
import in.annupaper.domain.market.TimeframeType;
import in.annupaper.infrastructure.broker.data.DataFeedHealth.CircuitBreakerState;
import in.annupaper.infrastructure.broker.data.DataFeedHealth.ConnectionState;
import in.annupaper.infrastructure.persistence.repository.UserBrokerSessionRepository;
import in.annupaper.application.data.CandleAggregator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

/**
 * FYERS v3 data feed adapter.
 *
 * Features:
 * - WebSocket v2 for real-time ticks
 * - REST API v3 for historical candles
 * - Instrument master download
 * - Automatic reconnection with exponential backoff
 * - Circuit breaker for repeated failures
 * - Stale feed detection
 */
public class FyersDataAdapter implements DataBroker {

    private static final Logger logger = LoggerFactory.getLogger(FyersDataAdapter.class);

    // Dependencies
    private final UserBrokerSessionRepository sessionRepo;
    private final CandleAggregator candleAggregator;
    private final HttpClient httpClient;

    // WebSocket connection
    private WebSocket webSocket;
    private String accessToken;
    private volatile boolean connected = false;

    // Tick listeners (symbol → list of listeners)
    private final Map<String, List<TickListener>> tickListeners = new ConcurrentHashMap<>();

    // Health monitoring
    private Instant lastTickTime;
    private int consecutiveErrors = 0;
    private static final int CIRCUIT_BREAKER_THRESHOLD = 10;
    private CircuitBreakerState circuitBreakerState = CircuitBreakerState.CLOSED;

    // Reconnection
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor();
    private int reconnectAttempts = 0;

    public FyersDataAdapter(
        UserBrokerSessionRepository sessionRepo,
        CandleAggregator candleAggregator
    ) {
        this.sessionRepo = sessionRepo;
        this.candleAggregator = candleAggregator;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public void connect(UserBroker userBroker) {
        logger.info("Connecting to FYERS data feed for user-broker: {}", userBroker.getUserBrokerId());

        // Load access token from session
        UserBrokerSession session = sessionRepo.findLatestByUserBrokerId(userBroker.getUserBrokerId())
            .orElseThrow(() -> new ConnectionException("No active session found for user-broker"));

        if (session.isExpired()) {
            throw new ConnectionException("Session expired, please re-authenticate");
        }

        this.accessToken = session.getAccessToken();

        // Establish WebSocket connection
        connectWebSocket();
    }

    private void connectWebSocket() {
        try {
            String wsUrl = "wss://api-t1.fyers.in/socket/v2/dataSock?access_token=" + accessToken;

            webSocket = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create(wsUrl), new FyersWebSocketListener())
                .join();

            connected = true;
            reconnectAttempts = 0;
            consecutiveErrors = 0;
            circuitBreakerState = CircuitBreakerState.CLOSED;

            logger.info("FYERS WebSocket connected");

        } catch (Exception e) {
            logger.error("Failed to connect FYERS WebSocket", e);
            handleConnectionError();
        }
    }

    @Override
    public void disconnect() {
        logger.info("Disconnecting from FYERS data feed");

        connected = false;

        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Client disconnect");
        }

        tickListeners.clear();
        reconnectScheduler.shutdown();
    }

    @Override
    public void subscribeTicks(String symbol, TickListener listener) {
        logger.debug("Subscribing to ticks for symbol: {}", symbol);

        // Register listener
        tickListeners.computeIfAbsent(symbol, k -> new CopyOnWriteArrayList<>())
            .add(listener);

        // Send WebSocket subscription message (if not already subscribed)
        if (connected && webSocket != null) {
            String subscribeMsg = String.format(
                "{\"T\":\"SUB_L2\",\"SLIST\":[\"%s\"],\"SUB_T\":1}",
                symbol
            );
            webSocket.sendText(subscribeMsg, true);

            logger.info("Subscribed to FYERS ticks: {}", symbol);
        }
    }

    @Override
    public void unsubscribeTicks(String symbol, TickListener listener) {
        List<TickListener> listeners = tickListeners.get(symbol);
        if (listeners != null) {
            listeners.remove(listener);

            // If no listeners remain, unsubscribe from WebSocket
            if (listeners.isEmpty()) {
                tickListeners.remove(symbol);

                if (connected && webSocket != null) {
                    String unsubscribeMsg = String.format(
                        "{\"T\":\"UNSUB_L2\",\"SLIST\":[\"%s\"]}",
                        symbol
                    );
                    webSocket.sendText(unsubscribeMsg, true);

                    logger.info("Unsubscribed from FYERS ticks: {}", symbol);
                }
            }
        }
    }

    @Override
    public List<Candle> getHistoricalCandles(
        String symbol,
        TimeframeType timeframe,
        LocalDate fromDate,
        LocalDate toDate
    ) {
        logger.debug("Fetching historical candles: symbol={}, timeframe={}, from={}, to={}",
            symbol, timeframe, fromDate, toDate);

        // FYERS supports: 1, 5, 15, 60, 1D
        // For 25-min: fetch 5-min and aggregate
        // For 125-min: fetch 5-min and aggregate

        TimeframeType fetchTimeframe = determineFetchTimeframe(timeframe);
        List<Candle> rawCandles = fetchCandlesFromAPI(symbol, fetchTimeframe, fromDate, toDate);

        // Aggregate if needed
        if (fetchTimeframe != timeframe) {
            return candleAggregator.aggregate(rawCandles, timeframe);
        }

        return rawCandles;
    }

    private TimeframeType determineFetchTimeframe(TimeframeType target) {
        return switch (target) {
            case TF_1MIN -> TimeframeType.TF_1MIN;
            case TF_25MIN, TF_125MIN -> TimeframeType.TF_5MIN; // Aggregate from 5-min
            case DAILY -> TimeframeType.DAILY;
        };
    }

    private List<Candle> fetchCandlesFromAPI(
        String symbol,
        TimeframeType timeframe,
        LocalDate fromDate,
        LocalDate toDate
    ) {
        // REST API call to FYERS
        String url = String.format(
            "https://api-t1.fyers.in/data-rest/v3/history?symbol=%s&resolution=%s&from=%s&to=%s",
            symbol,
            mapTimeframeToFyersResolution(timeframe),
            fromDate.atStartOfDay().toEpochSecond(java.time.ZoneOffset.UTC),
            toDate.atTime(23, 59, 59).toEpochSecond(java.time.ZoneOffset.UTC)
        );

        try {
            // Make HTTP GET request with access token
            // Parse JSON response
            // Map to List<Candle>
            // (Implementation details omitted for brevity)

            return List.of(); // Placeholder

        } catch (Exception e) {
            logger.error("Failed to fetch candles from FYERS API", e);
            throw new DataFetchException("Failed to fetch historical candles", e);
        }
    }

    private String mapTimeframeToFyersResolution(TimeframeType timeframe) {
        return switch (timeframe) {
            case TF_1MIN -> "1";
            case TF_5MIN -> "5";
            case TF_25MIN -> "25";
            case TF_125MIN -> "125";
            case DAILY -> "1D";
        };
    }

    @Override
    public List<Instrument> getInstruments() {
        logger.info("Downloading FYERS instrument master");

        try {
            // Download CSV from FYERS public URL
            String csvUrl = "https://public.fyers.in/sym_details/NSE_EQ.csv";

            // Parse CSV
            // Map to List<Instrument>
            // (Implementation details omitted for brevity)

            return List.of(); // Placeholder

        } catch (Exception e) {
            logger.error("Failed to download FYERS instruments", e);
            throw new DataFetchException("Failed to download instrument master", e);
        }
    }

    @Override
    public boolean isConnected() {
        return connected && webSocket != null && !webSocket.isInputClosed();
    }

    @Override
    public DataFeedHealth getHealthStatus() {
        ConnectionState state = determineConnectionState();

        return new DataFeedHealth(
            state,
            lastTickTime,
            consecutiveErrors,
            circuitBreakerState
        );
    }

    private ConnectionState determineConnectionState() {
        if (!connected || webSocket == null) {
            return ConnectionState.DISCONNECTED;
        }

        if (reconnectAttempts > 0) {
            return ConnectionState.RECONNECTING;
        }

        // Check for stale feed (no ticks in last 5 minutes)
        if (lastTickTime != null &&
            lastTickTime.isBefore(Instant.now().minusSeconds(300))) {
            return ConnectionState.STALE;
        }

        return ConnectionState.CONNECTED;
    }

    @Override
    public String getBrokerId() {
        return BrokerIds.FYERS;
    }

    // WebSocket listener
    private class FyersWebSocketListener implements WebSocket.Listener {

        @Override
        public void onOpen(WebSocket webSocket) {
            logger.info("FYERS WebSocket opened");
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            // Parse tick JSON
            // Distribute to listeners
            handleTickMessage(data.toString());

            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            logger.warn("FYERS WebSocket closed: code={}, reason={}", statusCode, reason);
            connected = false;
            handleConnectionError();
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            logger.error("FYERS WebSocket error", error);
            connected = false;
            handleConnectionError();
        }
    }

    private void handleTickMessage(String json) {
        try {
            // Parse JSON to Tick object
            // Example: {"symbol":"NSE:RELIANCE-EQ","ltp":2450.50,"volume":1234567,...}

            Tick tick = parseTickJson(json);

            // Update last tick time
            lastTickTime = Instant.now();
            consecutiveErrors = 0;

            // Distribute to listeners
            List<TickListener> listeners = tickListeners.get(tick.symbol());
            if (listeners != null) {
                for (TickListener listener : listeners) {
                    try {
                        listener.onTick(tick);
                    } catch (Exception e) {
                        logger.error("Error in tick listener for symbol: {}", tick.symbol(), e);
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Failed to parse tick message: {}", json, e);
            consecutiveErrors++;
        }
    }

    private Tick parseTickJson(String json) {
        // Parse JSON and create Tick record
        // (Implementation omitted for brevity)
        return null; // Placeholder
    }

    private void handleConnectionError() {
        consecutiveErrors++;

        // Circuit breaker logic
        if (consecutiveErrors >= CIRCUIT_BREAKER_THRESHOLD) {
            circuitBreakerState = CircuitBreakerState.OPEN;
            logger.error("Circuit breaker OPEN after {} consecutive errors", consecutiveErrors);
            return;
        }

        // Exponential backoff reconnection
        int delaySeconds = (int) Math.min(Math.pow(2, reconnectAttempts), 300); // Max 5 minutes
        reconnectAttempts++;

        logger.info("Reconnecting FYERS WebSocket in {} seconds (attempt {})", delaySeconds, reconnectAttempts);

        reconnectScheduler.schedule(() -> {
            if (!connected) {
                connectWebSocket();
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }
}
```

### 4.2 FyersOrderAdapter (Extract from FyersAdapter)

```java
package in.annupaper.infrastructure.broker.order;

import in.annupaper.domain.broker.BrokerIds;
import in.annupaper.domain.broker.UserBroker;
import in.annupaper.domain.broker.UserBrokerSession;
import in.annupaper.domain.order.OrderRequest;
import in.annupaper.domain.order.OrderResponse;
import in.annupaper.domain.order.OrderStatus;
import in.annupaper.domain.trade.Position;
import in.annupaper.infrastructure.persistence.repository.UserBrokerSessionRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

/**
 * FYERS v3 order execution adapter.
 *
 * Features:
 * - REST API v3 for order placement
 * - Order status tracking
 * - Position management
 * - Order modification and cancellation
 */
public class FyersOrderAdapter implements OrderBroker {

    private static final Logger logger = LoggerFactory.getLogger(FyersOrderAdapter.class);

    private final UserBrokerSessionRepository sessionRepo;
    private final HttpClient httpClient;

    private String accessToken;

    public FyersOrderAdapter(UserBrokerSessionRepository sessionRepo) {
        this.sessionRepo = sessionRepo;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public void connect(UserBroker userBroker) {
        logger.info("Connecting to FYERS order API for user-broker: {}", userBroker.getUserBrokerId());

        // Load access token from session
        UserBrokerSession session = sessionRepo.findLatestByUserBrokerId(userBroker.getUserBrokerId())
            .orElseThrow(() -> new ConnectionException("No active session found for user-broker"));

        if (session.isExpired()) {
            throw new ConnectionException("Session expired, please re-authenticate");
        }

        this.accessToken = session.getAccessToken();

        logger.info("FYERS order API connected");
    }

    @Override
    public void disconnect() {
        logger.info("Disconnecting from FYERS order API");
        this.accessToken = null;
    }

    @Override
    public String placeOrder(OrderRequest request) {
        logger.info("Placing order: symbol={}, qty={}, price={}, type={}",
            request.symbol(), request.quantity(), request.limitPrice(), request.orderType());

        try {
            // Build request JSON
            String orderJson = buildOrderJson(request);

            // Make REST API call
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://api-t1.fyers.in/api/v3/orders"))
                .header("Authorization", accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(orderJson))
                .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new OrderPlacementException("Order placement failed: " + response.body());
            }

            // Parse response to get order ID
            String brokerOrderId = parseOrderIdFromResponse(response.body());

            logger.info("Order placed successfully: brokerOrderId={}", brokerOrderId);
            return brokerOrderId;

        } catch (Exception e) {
            logger.error("Failed to place order", e);
            throw new OrderPlacementException("Failed to place order", e);
        }
    }

    private String buildOrderJson(OrderRequest request) {
        // Map OrderRequest to FYERS JSON format
        // Example:
        // {
        //   "symbol": "NSE:RELIANCE-EQ",
        //   "qty": 10,
        //   "type": 2,  // LIMIT
        //   "side": 1,  // BUY
        //   "productType": "INTRADAY",
        //   "limitPrice": 2450.00,
        //   "stopPrice": 0,
        //   "disclosedQty": 0,
        //   "validity": "DAY",
        //   "offlineOrder": false
        // }

        return "{}"; // Placeholder
    }

    private String parseOrderIdFromResponse(String json) {
        // Parse JSON response to extract order ID
        // (Implementation omitted for brevity)
        return "ORDER123"; // Placeholder
    }

    @Override
    public void modifyOrder(String brokerOrderId, OrderRequest request) {
        logger.info("Modifying order: brokerOrderId={}", brokerOrderId);

        try {
            // Build modification JSON
            String modifyJson = buildModifyJson(brokerOrderId, request);

            // Make REST API call
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://api-t1.fyers.in/api/v3/orders"))
                .header("Authorization", accessToken)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(modifyJson))
                .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new OrderModificationException("Order modification failed: " + response.body());
            }

            logger.info("Order modified successfully: brokerOrderId={}", brokerOrderId);

        } catch (Exception e) {
            logger.error("Failed to modify order", e);
            throw new OrderModificationException("Failed to modify order", e);
        }
    }

    private String buildModifyJson(String brokerOrderId, OrderRequest request) {
        // Map to FYERS modify JSON format
        return "{}"; // Placeholder
    }

    @Override
    public void cancelOrder(String brokerOrderId) {
        logger.info("Cancelling order: brokerOrderId={}", brokerOrderId);

        try {
            // Make REST API call
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://api-t1.fyers.in/api/v3/orders?id=" + brokerOrderId))
                .header("Authorization", accessToken)
                .DELETE()
                .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new OrderCancellationException("Order cancellation failed: " + response.body());
            }

            logger.info("Order cancelled successfully: brokerOrderId={}", brokerOrderId);

        } catch (Exception e) {
            logger.error("Failed to cancel order", e);
            throw new OrderCancellationException("Failed to cancel order", e);
        }
    }

    @Override
    public OrderStatus getOrderStatus(String brokerOrderId) {
        logger.debug("Fetching order status: brokerOrderId={}", brokerOrderId);

        try {
            // Make REST API call
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://api-t1.fyers.in/api/v3/orders/" + brokerOrderId))
                .header("Authorization", accessToken)
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new OrderNotFoundException("Order not found: " + brokerOrderId);
            }

            // Parse response to OrderStatus
            return parseOrderStatus(response.body());

        } catch (Exception e) {
            logger.error("Failed to fetch order status", e);
            throw new OrderStatusException("Failed to fetch order status", e);
        }
    }

    private OrderStatus parseOrderStatus(String json) {
        // Parse JSON to OrderStatus enum
        // (Implementation omitted for brevity)
        return OrderStatus.PLACED; // Placeholder
    }

    @Override
    public List<Position> getPositions() {
        logger.debug("Fetching positions");

        try {
            // Make REST API call
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://api-t1.fyers.in/api/v3/positions"))
                .header("Authorization", accessToken)
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new PositionFetchException("Failed to fetch positions: " + response.body());
            }

            // Parse response to List<Position>
            return parsePositions(response.body());

        } catch (Exception e) {
            logger.error("Failed to fetch positions", e);
            throw new PositionFetchException("Failed to fetch positions", e);
        }
    }

    private List<Position> parsePositions(String json) {
        // Parse JSON to List<Position>
        // (Implementation omitted for brevity)
        return List.of(); // Placeholder
    }

    @Override
    public List<OrderResponse> getOrderHistory() {
        logger.debug("Fetching order history");

        try {
            // Make REST API call
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://api-t1.fyers.in/api/v3/orders"))
                .header("Authorization", accessToken)
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new OrderHistoryException("Failed to fetch order history: " + response.body());
            }

            // Parse response to List<OrderResponse>
            return parseOrderHistory(response.body());

        } catch (Exception e) {
            logger.error("Failed to fetch order history", e);
            throw new OrderHistoryException("Failed to fetch order history", e);
        }
    }

    private List<OrderResponse> parseOrderHistory(String json) {
        // Parse JSON to List<OrderResponse>
        // (Implementation omitted for brevity)
        return List.of(); // Placeholder
    }

    @Override
    public boolean isConnected() {
        return accessToken != null && !accessToken.isEmpty();
    }

    @Override
    public String getBrokerId() {
        return BrokerIds.FYERS;
    }
}
```

### 4.3 ZerodhaOrderAdapter (New Implementation)

```java
package in.annupaper.infrastructure.broker.order;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;

import in.annupaper.domain.broker.BrokerIds;
import in.annupaper.domain.broker.UserBroker;
import in.annupaper.domain.broker.UserBrokerSession;
import in.annupaper.domain.order.OrderRequest;
import in.annupaper.domain.order.OrderResponse;
import in.annupaper.domain.order.OrderStatus;
import in.annupaper.domain.order.OrderType;
import in.annupaper.domain.trade.Direction;
import in.annupaper.domain.trade.Position;
import in.annupaper.infrastructure.persistence.repository.UserBrokerSessionRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Zerodha Kite Connect v3 order execution adapter.
 *
 * Features:
 * - Order placement via Kite Connect SDK
 * - Order status tracking
 * - Position management
 * - Order modification and cancellation
 */
public class ZerodhaOrderAdapter implements OrderBroker {

    private static final Logger logger = LoggerFactory.getLogger(ZerodhaOrderAdapter.class);

    private final UserBrokerSessionRepository sessionRepo;
    private KiteConnect kite;

    public ZerodhaOrderAdapter(UserBrokerSessionRepository sessionRepo) {
        this.sessionRepo = sessionRepo;
    }

    @Override
    public void connect(UserBroker userBroker) {
        logger.info("Connecting to Zerodha order API for user-broker: {}", userBroker.getUserBrokerId());

        // Load access token from session
        UserBrokerSession session = sessionRepo.findLatestByUserBrokerId(userBroker.getUserBrokerId())
            .orElseThrow(() -> new ConnectionException("No active session found for user-broker"));

        if (session.isExpired()) {
            throw new ConnectionException("Session expired, please re-authenticate");
        }

        // Initialize Kite Connect client
        String apiKey = userBroker.getApiKey(); // From credentials JSON
        kite = new KiteConnect(apiKey);
        kite.setAccessToken(session.getAccessToken());

        logger.info("Zerodha order API connected");
    }

    @Override
    public void disconnect() {
        logger.info("Disconnecting from Zerodha order API");
        this.kite = null;
    }

    @Override
    public String placeOrder(OrderRequest request) {
        logger.info("Placing order: symbol={}, qty={}, price={}, type={}",
            request.symbol(), request.quantity(), request.limitPrice(), request.orderType());

        try {
            // Map OrderRequest to Zerodha OrderParams
            OrderParams orderParams = new OrderParams();
            orderParams.exchange = "NSE";
            orderParams.tradingsymbol = extractTradingSymbol(request.symbol());
            orderParams.transactionType = mapDirection(request.direction());
            orderParams.quantity = request.quantity();
            orderParams.orderType = mapOrderType(request.orderType());
            orderParams.product = "MIS"; // Intraday
            orderParams.validity = "DAY";

            if (request.orderType() == OrderType.LIMIT) {
                orderParams.price = request.limitPrice().doubleValue();
            }

            if (request.orderType() == OrderType.STOP_LOSS) {
                orderParams.triggerPrice = request.stopPrice().doubleValue();
            }

            // Place order via Kite Connect
            Order order = kite.placeOrder(orderParams, "regular");

            logger.info("Order placed successfully: brokerOrderId={}", order.orderId);
            return order.orderId;

        } catch (KiteException e) {
            logger.error("Failed to place order", e);
            throw new OrderPlacementException("Failed to place order: " + e.message, e);
        } catch (Exception e) {
            logger.error("Failed to place order", e);
            throw new OrderPlacementException("Failed to place order", e);
        }
    }

    private String extractTradingSymbol(String fullSymbol) {
        // Extract trading symbol from "NSE:RELIANCE-EQ" → "RELIANCE"
        String[] parts = fullSymbol.split(":");
        if (parts.length > 1) {
            return parts[1].replace("-EQ", "");
        }
        return fullSymbol;
    }

    private String mapDirection(Direction direction) {
        return direction == Direction.BUY ? "BUY" : "SELL";
    }

    private String mapOrderType(OrderType orderType) {
        return switch (orderType) {
            case MARKET -> "MARKET";
            case LIMIT -> "LIMIT";
            case STOP_LOSS -> "SL";
        };
    }

    @Override
    public void modifyOrder(String brokerOrderId, OrderRequest request) {
        logger.info("Modifying order: brokerOrderId={}", brokerOrderId);

        try {
            OrderParams orderParams = new OrderParams();
            orderParams.quantity = request.quantity();

            if (request.orderType() == OrderType.LIMIT) {
                orderParams.price = request.limitPrice().doubleValue();
            }

            if (request.orderType() == OrderType.STOP_LOSS) {
                orderParams.triggerPrice = request.stopPrice().doubleValue();
            }

            kite.modifyOrder(brokerOrderId, orderParams, "regular");

            logger.info("Order modified successfully: brokerOrderId={}", brokerOrderId);

        } catch (KiteException e) {
            logger.error("Failed to modify order", e);
            throw new OrderModificationException("Failed to modify order: " + e.message, e);
        } catch (Exception e) {
            logger.error("Failed to modify order", e);
            throw new OrderModificationException("Failed to modify order", e);
        }
    }

    @Override
    public void cancelOrder(String brokerOrderId) {
        logger.info("Cancelling order: brokerOrderId={}", brokerOrderId);

        try {
            kite.cancelOrder(brokerOrderId, "regular");

            logger.info("Order cancelled successfully: brokerOrderId={}", brokerOrderId);

        } catch (KiteException e) {
            logger.error("Failed to cancel order", e);
            throw new OrderCancellationException("Failed to cancel order: " + e.message, e);
        } catch (Exception e) {
            logger.error("Failed to cancel order", e);
            throw new OrderCancellationException("Failed to cancel order", e);
        }
    }

    @Override
    public OrderStatus getOrderStatus(String brokerOrderId) {
        logger.debug("Fetching order status: brokerOrderId={}", brokerOrderId);

        try {
            List<Order> orders = kite.getOrders();

            Order order = orders.stream()
                .filter(o -> o.orderId.equals(brokerOrderId))
                .findFirst()
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + brokerOrderId));

            return mapZerodhaOrderStatus(order.status);

        } catch (KiteException e) {
            logger.error("Failed to fetch order status", e);
            throw new OrderStatusException("Failed to fetch order status: " + e.message, e);
        } catch (Exception e) {
            logger.error("Failed to fetch order status", e);
            throw new OrderStatusException("Failed to fetch order status", e);
        }
    }

    private OrderStatus mapZerodhaOrderStatus(String zerodhaStatus) {
        return switch (zerodhaStatus) {
            case "OPEN" -> OrderStatus.PLACED;
            case "COMPLETE" -> OrderStatus.FILLED;
            case "CANCELLED" -> OrderStatus.CANCELLED;
            case "REJECTED" -> OrderStatus.REJECTED;
            default -> OrderStatus.PENDING;
        };
    }

    @Override
    public List<Position> getPositions() {
        logger.debug("Fetching positions");

        try {
            com.zerodhatech.models.Position[] zerodhaPositions = kite.getPositions().get("net");

            return Arrays.stream(zerodhaPositions)
                .map(this::mapZerodhaPosition)
                .collect(Collectors.toList());

        } catch (KiteException e) {
            logger.error("Failed to fetch positions", e);
            throw new PositionFetchException("Failed to fetch positions: " + e.message, e);
        } catch (Exception e) {
            logger.error("Failed to fetch positions", e);
            throw new PositionFetchException("Failed to fetch positions", e);
        }
    }

    private Position mapZerodhaPosition(com.zerodhatech.models.Position zp) {
        return new Position(
            "NSE:" + zp.tradingSymbol + "-EQ",
            zp.netQuantity,
            zp.netQuantity > 0 ? Direction.BUY : Direction.SELL,
            BigDecimal.valueOf(zp.averagePrice),
            BigDecimal.valueOf(zp.lastPrice),
            BigDecimal.valueOf(zp.pnl)
        );
    }

    @Override
    public List<OrderResponse> getOrderHistory() {
        logger.debug("Fetching order history");

        try {
            List<Order> orders = kite.getOrders();

            return orders.stream()
                .map(this::mapZerodhaOrder)
                .collect(Collectors.toList());

        } catch (KiteException e) {
            logger.error("Failed to fetch order history", e);
            throw new OrderHistoryException("Failed to fetch order history: " + e.message, e);
        } catch (Exception e) {
            logger.error("Failed to fetch order history", e);
            throw new OrderHistoryException("Failed to fetch order history", e);
        }
    }

    private OrderResponse mapZerodhaOrder(Order order) {
        return new OrderResponse(
            order.orderId,
            "NSE:" + order.tradingSymbol + "-EQ",
            mapZerodhaOrderStatus(order.status),
            order.filledQuantity,
            BigDecimal.valueOf(order.averagePrice),
            order.orderTimestamp.toInstant(),
            order.exchangeUpdateTimestamp != null ? order.exchangeUpdateTimestamp.toInstant() : null
        );
    }

    @Override
    public boolean isConnected() {
        return kite != null && kite.getAccessToken() != null;
    }

    @Override
    public String getBrokerId() {
        return BrokerIds.ZERODHA;
    }
}
```

---

## 5. Factory Pattern

### 5.1 DataBrokerFactory

```java
package in.annupaper.infrastructure.broker.data;

import in.annupaper.domain.broker.BrokerIds;
import in.annupaper.domain.broker.BrokerRole;
import in.annupaper.domain.broker.UserBroker;
import in.annupaper.infrastructure.persistence.repository.UserBrokerRepository;
import in.annupaper.infrastructure.persistence.repository.UserBrokerSessionRepository;
import in.annupaper.infrastructure.persistence.repository.InstrumentRepository;
import in.annupaper.application.data.CandleAggregator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating and caching DataBroker instances.
 *
 * Pattern: Singleton per user-broker link
 * Thread-safe: Uses ConcurrentHashMap for caching
 */
public class DataBrokerFactory {

    private static final Logger logger = LoggerFactory.getLogger(DataBrokerFactory.class);

    private final Map<String, DataBroker> cache = new ConcurrentHashMap<>();

    // Dependencies
    private final UserBrokerSessionRepository sessionRepo;
    private final CandleAggregator candleAggregator;
    private final InstrumentRepository instrumentRepo;

    public DataBrokerFactory(
        UserBrokerSessionRepository sessionRepo,
        CandleAggregator candleAggregator,
        InstrumentRepository instrumentRepo
    ) {
        this.sessionRepo = sessionRepo;
        this.candleAggregator = candleAggregator;
        this.instrumentRepo = instrumentRepo;
    }

    /**
     * Get or create DataBroker for a user-broker link.
     *
     * Caching strategy:
     * - Key: userBrokerId
     * - Value: DataBroker instance
     * - Lifetime: Until factory is destroyed (application shutdown)
     *
     * @param userBroker User-broker link with DATA role
     * @return DataBroker instance
     * @throws IllegalArgumentException if userBroker does not have DATA role
     */
    public DataBroker getDataBroker(UserBroker userBroker) {
        if (userBroker.getRole() != BrokerRole.DATA) {
            throw new IllegalArgumentException("UserBroker must have DATA role");
        }

        String cacheKey = userBroker.getUserBrokerId().toString();
        return cache.computeIfAbsent(cacheKey, k -> createDataBroker(userBroker));
    }

    private DataBroker createDataBroker(UserBroker userBroker) {
        String brokerId = userBroker.getBroker().getBrokerId();

        logger.info("Creating DataBroker: brokerId={}, userBrokerId={}",
            brokerId, userBroker.getUserBrokerId());

        DataBroker broker = switch (brokerId) {
            case BrokerIds.FYERS -> new FyersDataAdapter(sessionRepo, candleAggregator);
            case BrokerIds.ZERODHA -> new ZerodhaDataAdapter(sessionRepo, instrumentRepo);
            case BrokerIds.DHAN -> new DhanDataAdapter(sessionRepo);
            case BrokerIds.RELAY -> new RelayDataAdapter();
            default -> throw new UnsupportedOperationException("Unknown data broker: " + brokerId);
        };

        // Auto-connect
        broker.connect(userBroker);

        return broker;
    }

    /**
     * Auto-connect all DATA brokers on startup.
     *
     * Called from App.java during application bootstrap.
     *
     * @param userBrokerRepo Repository to fetch DATA brokers
     */
    public void connectAllDataBrokers(UserBrokerRepository userBrokerRepo) {
        logger.info("Auto-connecting all DATA brokers...");

        List<UserBroker> dataBrokers = userBrokerRepo.findByRole(BrokerRole.DATA);

        for (UserBroker ub : dataBrokers) {
            if (ub.isEnabled()) {
                try {
                    DataBroker broker = getDataBroker(ub);
                    logger.info("Connected DATA broker: userBrokerId={}, brokerId={}",
                        ub.getUserBrokerId(), ub.getBroker().getBrokerId());
                } catch (Exception e) {
                    logger.error("Failed to connect DATA broker: userBrokerId={}",
                        ub.getUserBrokerId(), e);
                }
            }
        }

        logger.info("DATA broker auto-connect complete: {} brokers", dataBrokers.size());
    }

    /**
     * Disconnect and remove all cached brokers.
     * Called during application shutdown.
     */
    public void disconnectAll() {
        logger.info("Disconnecting all DATA brokers...");

        cache.values().forEach(broker -> {
            try {
                broker.disconnect();
            } catch (Exception e) {
                logger.error("Error disconnecting broker", e);
            }
        });

        cache.clear();
    }
}
```

### 5.2 OrderBrokerFactory

```java
package in.annupaper.infrastructure.broker.order;

import in.annupaper.domain.broker.BrokerIds;
import in.annupaper.domain.broker.BrokerEnvironment;
import in.annupaper.domain.broker.BrokerRole;
import in.annupaper.domain.broker.UserBroker;
import in.annupaper.infrastructure.persistence.repository.UserBrokerRepository;
import in.annupaper.infrastructure.persistence.repository.UserBrokerSessionRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating and caching OrderBroker instances.
 *
 * Pattern: Singleton per user-broker link
 * Thread-safe: Uses ConcurrentHashMap for caching
 */
public class OrderBrokerFactory {

    private static final Logger logger = LoggerFactory.getLogger(OrderBrokerFactory.class);

    private final Map<String, OrderBroker> cache = new ConcurrentHashMap<>();

    // Dependencies
    private final UserBrokerSessionRepository sessionRepo;

    public OrderBrokerFactory(UserBrokerSessionRepository sessionRepo) {
        this.sessionRepo = sessionRepo;
    }

    /**
     * Get or create OrderBroker for a user-broker link.
     *
     * @param userBroker User-broker link with EXEC role
     * @return OrderBroker instance
     * @throws IllegalArgumentException if userBroker does not have EXEC role
     */
    public OrderBroker getOrderBroker(UserBroker userBroker) {
        if (userBroker.getRole() != BrokerRole.EXEC) {
            throw new IllegalArgumentException("UserBroker must have EXEC role");
        }

        String cacheKey = userBroker.getUserBrokerId().toString();
        return cache.computeIfAbsent(cacheKey, k -> createOrderBroker(userBroker));
    }

    private OrderBroker createOrderBroker(UserBroker userBroker) {
        String brokerId = userBroker.getBroker().getBrokerId();
        BrokerEnvironment env = userBroker.getEnvironment();

        logger.info("Creating OrderBroker: brokerId={}, env={}, userBrokerId={}",
            brokerId, env, userBroker.getUserBrokerId());

        // Use dummy broker for PAPER environment
        if (env == BrokerEnvironment.PAPER) {
            logger.info("Using DummyOrderAdapter for PAPER environment");
            return new DummyOrderAdapter();
        }

        OrderBroker broker = switch (brokerId) {
            case BrokerIds.FYERS -> new FyersOrderAdapter(sessionRepo);
            case BrokerIds.ZERODHA -> new ZerodhaOrderAdapter(sessionRepo);
            case BrokerIds.DHAN -> new DhanOrderAdapter(sessionRepo);
            default -> throw new UnsupportedOperationException("Unknown order broker: " + brokerId);
        };

        // Auto-connect
        broker.connect(userBroker);

        return broker;
    }

    /**
     * Auto-connect all EXEC brokers on startup.
     *
     * @param userBrokerRepo Repository to fetch EXEC brokers
     */
    public void connectAllOrderBrokers(UserBrokerRepository userBrokerRepo) {
        logger.info("Auto-connecting all EXEC brokers...");

        List<UserBroker> execBrokers = userBrokerRepo.findByRole(BrokerRole.EXEC);

        for (UserBroker ub : execBrokers) {
            if (ub.isEnabled()) {
                try {
                    OrderBroker broker = getOrderBroker(ub);
                    logger.info("Connected EXEC broker: userBrokerId={}, brokerId={}",
                        ub.getUserBrokerId(), ub.getBroker().getBrokerId());
                } catch (Exception e) {
                    logger.error("Failed to connect EXEC broker: userBrokerId={}",
                        ub.getUserBrokerId(), e);
                }
            }
        }

        logger.info("EXEC broker auto-connect complete: {} brokers", execBrokers.size());
    }

    /**
     * Disconnect and remove all cached brokers.
     */
    public void disconnectAll() {
        logger.info("Disconnecting all EXEC brokers...");

        cache.values().forEach(broker -> {
            try {
                broker.disconnect();
            } catch (Exception e) {
                logger.error("Error disconnecting broker", e);
            }
        });

        cache.clear();
    }
}
```

---

## 6. Service Updates

### 6.1 TickCandleBuilder (Update to use DataBroker)

**Before**:
```java
public class TickCandleBuilder implements TickListener {
    private final BrokerAdapterFactory brokerFactory;

    public void subscribeToSymbol(String symbol, UserBroker dataBroker) {
        BrokerAdapter adapter = brokerFactory.getAdapter(dataBroker);
        adapter.subscribeTicks(symbol, this);
    }
}
```

**After**:
```java
public class TickCandleBuilder implements TickListener {
    private final DataBrokerFactory dataBrokerFactory;

    public void subscribeToSymbol(String symbol, UserBroker dataBroker) {
        DataBroker broker = dataBrokerFactory.getDataBroker(dataBroker);
        broker.subscribeTicks(symbol, this);
    }

    @Override
    public void onTick(Tick tick) {
        // Build candles from ticks (existing logic)
    }
}
```

### 6.2 OrderPlacementService (Update to use OrderBroker)

**Before**:
```java
public class OrderExecutionService {
    private final BrokerAdapterFactory brokerFactory;

    public void placeOrder(TradeIntent intent) {
        BrokerAdapter adapter = brokerFactory.getAdapter(intent.getUserBroker());
        String orderId = adapter.placeOrder(orderRequest);
    }
}
```

**After**:
```java
public class OrderPlacementService {
    private final OrderBrokerFactory orderBrokerFactory;

    public void placeOrder(TradeIntent intent) {
        OrderBroker broker = orderBrokerFactory.getOrderBroker(intent.getUserBroker());
        String orderId = broker.placeOrder(orderRequest);

        // Update trade intent
        intent.setBrokerOrderId(orderId);
        intent.setStatus(IntentStatus.PLACED);
    }
}
```

---

## 7. Testing Strategy

### 7.1 Unit Tests

**Test DataBroker Interface**:
```java
@Test
public void testDataBrokerSubscription() {
    // Mock dependencies
    UserBrokerSessionRepository mockSessionRepo = mock(UserBrokerSessionRepository.class);
    CandleAggregator mockAggregator = mock(CandleAggregator.class);

    // Create adapter
    DataBroker broker = new FyersDataAdapter(mockSessionRepo, mockAggregator);

    // Mock user-broker
    UserBroker userBroker = createMockUserBroker(BrokerRole.DATA);

    // Connect
    broker.connect(userBroker);

    // Subscribe to ticks
    TickListener mockListener = mock(TickListener.class);
    broker.subscribeTicks("NSE:RELIANCE-EQ", mockListener);

    // Verify WebSocket subscription sent
    assertTrue(broker.isConnected());
}
```

**Test OrderBroker Interface**:
```java
@Test
public void testOrderBrokerPlaceOrder() {
    // Mock dependencies
    UserBrokerSessionRepository mockSessionRepo = mock(UserBrokerSessionRepository.class);

    // Create adapter
    OrderBroker broker = new FyersOrderAdapter(mockSessionRepo);

    // Mock user-broker
    UserBroker userBroker = createMockUserBroker(BrokerRole.EXEC);

    // Connect
    broker.connect(userBroker);

    // Place order
    OrderRequest request = new OrderRequest(
        "NSE:RELIANCE-EQ",
        Direction.BUY,
        10,
        OrderType.LIMIT,
        BigDecimal.valueOf(2450.00),
        null,
        TimeInForce.DAY
    );

    String orderId = broker.placeOrder(request);

    // Verify order ID returned
    assertNotNull(orderId);
}
```

### 7.2 Integration Tests

**Test Multi-Broker Flow**:
```java
@Test
public void testZerodhaDataAndFyersOrderExecution() {
    // Setup: DATA=Zerodha, EXEC=FYERS
    UserBroker zerodhaData = createUserBroker(BrokerIds.ZERODHA, BrokerRole.DATA);
    UserBroker fyersExec = createUserBroker(BrokerIds.FYERS, BrokerRole.EXEC);

    // Get brokers from factories
    DataBroker dataBroker = dataBrokerFactory.getDataBroker(zerodhaData);
    OrderBroker orderBroker = orderBrokerFactory.getOrderBroker(fyersExec);

    // Subscribe to Zerodha ticks
    dataBroker.subscribeTicks("NSE:RELIANCE-EQ", tick -> {
        logger.info("Received tick from Zerodha: {}", tick);
    });

    // Place order via FYERS
    OrderRequest request = createBuyOrder("NSE:RELIANCE-EQ", 10);
    String orderId = orderBroker.placeOrder(request);

    // Verify
    assertNotNull(orderId);
    assertTrue(dataBroker.isConnected());
    assertTrue(orderBroker.isConnected());
}
```

---

## 8. Migration Checklist

### 8.1 Pre-Migration

- [ ] Review this guide with team
- [ ] Create git branch: `git checkout -b refactor/broker-abstraction-split`
- [ ] Backup current code: `git commit -m "Checkpoint before broker split"`
- [ ] Run all tests: `mvn clean test` (ensure baseline passes)

### 8.2 Phase 1: Create Interfaces

- [ ] Create `DataBroker.java` interface
- [ ] Create `OrderBroker.java` interface
- [ ] Create `DataFeedHealth.java` record
- [ ] Create `TickListener.java` interface
- [ ] Compile and verify no errors

### 8.3 Phase 2: Extract FyersDataAdapter

- [ ] Copy `FyersAdapter.java` → `FyersDataAdapter.java`
- [ ] Remove all order-related methods
- [ ] Implement `DataBroker` interface
- [ ] Test WebSocket connection
- [ ] Test tick subscription
- [ ] Test historical candle fetch

### 8.4 Phase 3: Extract FyersOrderAdapter

- [ ] Copy `FyersAdapter.java` → `FyersOrderAdapter.java`
- [ ] Remove all data-related methods
- [ ] Implement `OrderBroker` interface
- [ ] Test order placement
- [ ] Test order status fetch
- [ ] Test order cancellation

### 8.5 Phase 4: Implement ZerodhaOrderAdapter

- [ ] Create `ZerodhaOrderAdapter.java`
- [ ] Implement `OrderBroker` interface
- [ ] Use Kite Connect SDK for API calls
- [ ] Test order placement
- [ ] Test order status fetch
- [ ] Test position fetch

### 8.6 Phase 5: Create Factories

- [ ] Create `DataBrokerFactory.java`
- [ ] Implement caching logic
- [ ] Create `OrderBrokerFactory.java`
- [ ] Implement caching logic
- [ ] Test factory methods

### 8.7 Phase 6: Update Services

- [ ] Update `TickCandleBuilder` to use `DataBrokerFactory`
- [ ] Update `OrderPlacementService` to use `OrderBrokerFactory`
- [ ] Update `PendingOrderReconciler` to use `OrderBrokerFactory`
- [ ] Update all other services using brokers
- [ ] Compile and fix any errors

### 8.8 Phase 7: Update Bootstrap

- [ ] Update `App.java` to use new factories
- [ ] Wire `DataBrokerFactory` in `DependencyInjector`
- [ ] Wire `OrderBrokerFactory` in `DependencyInjector`
- [ ] Test application startup

### 8.9 Phase 8: Testing

- [ ] Run all unit tests: `mvn test`
- [ ] Run integration tests: `mvn verify`
- [ ] Manual test: Zerodha data + FYERS orders
- [ ] Manual test: Signal-to-order flow
- [ ] Manual test: Exit order flow

### 8.10 Phase 9: Cleanup

- [ ] Delete old `BrokerAdapter.java` interface
- [ ] Delete old `BrokerAdapterFactory.java`
- [ ] Delete old `FyersAdapter.java` (if fully replaced)
- [ ] Run tests again to ensure nothing broken
- [ ] Commit changes: `git commit -m "Refactor: Split broker abstraction into DataBroker and OrderBroker"`

### 8.11 Phase 10: Documentation

- [ ] Update README with new broker architecture
- [ ] Document how to configure multi-broker setup
- [ ] Update API documentation
- [ ] Create migration guide for existing users

---

## Conclusion

This guide provides a complete blueprint for splitting the monolithic `BrokerAdapter` into separate `DataBroker` and `OrderBroker` interfaces, enabling the AMZF system to use different brokers for market data and order execution.

**Key Benefits**:
- **Cost Optimization**: Use cheap data feeds (Zerodha) with reliable execution (FYERS)
- **Flexibility**: Switch data or execution brokers independently
- **Reliability**: Isolate data feed failures from order execution
- **Maintainability**: Clearer separation of concerns

**Estimated Time**: 2-3 weeks for complete implementation and testing

**Next Steps**:
1. Review this guide with the team
2. Create implementation branch
3. Follow migration checklist step-by-step
4. Test thoroughly at each phase
5. Deploy to staging for validation
6. Roll out to production
