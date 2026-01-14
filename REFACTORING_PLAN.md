# AMZF Trading System - Comprehensive Refactoring Plan

**Version**: 1.0
**Date**: 2026-01-14
**Objective**: Restructure the codebase into clean architecture with separated concerns for data feeds and order execution

---

## Executive Summary

This refactoring transforms the AMZF trading system from a mixed-responsibility architecture into a clean, layered design that:
- **Separates data feeds from order execution** (DataBroker vs OrderBroker)
- **Supports multi-broker combinations** (e.g., Zerodha data + FYERS orders)
- **Enforces clear boundaries** between domain, application, infrastructure, and presentation layers
- **Improves maintainability** through consistent patterns and modular organization
- **Enables independent evolution** of each subsystem

**Current State**: ~12,755 lines in flat package structure
**Target State**: Layered architecture with 8 core modules

---

## Table of Contents

1. [Proposed Package Structure](#1-proposed-package-structure)
2. [Broker Abstraction Split](#2-broker-abstraction-split)
3. [Module-by-Module Migration](#3-module-by-module-migration)
4. [Configuration Management](#4-configuration-management)
5. [Execution Roadmap](#5-execution-roadmap)
6. [Risk Mitigation](#6-risk-mitigation)
7. [Success Criteria](#7-success-criteria)
8. [Appendices](#8-appendices)

---

## 1. Proposed Package Structure

### 1.1 Target Architecture

```
in.annupaper/
├── domain/                          # Core business entities (no dependencies)
│   ├── user/
│   │   ├── User.java
│   │   ├── UserRole.java
│   │   ├── UserSession.java
│   │   └── Portfolio.java
│   ├── broker/
│   │   ├── Broker.java
│   │   ├── UserBroker.java
│   │   ├── BrokerRole.java          # DATA | EXEC
│   │   └── BrokerEnvironment.java   # PAPER | LIVE
│   ├── market/
│   │   ├── Tick.java
│   │   ├── Candle.java
│   │   ├── Instrument.java
│   │   ├── Watchlist.java
│   │   ├── WatchlistTemplate.java
│   │   └── TimeframeType.java
│   ├── signal/
│   │   ├── Signal.java
│   │   ├── SignalType.java
│   │   ├── ExitSignal.java
│   │   ├── ConfluenceType.java
│   │   └── ExitReason.java
│   ├── trade/
│   │   ├── Trade.java
│   │   ├── TradeIntent.java
│   │   ├── ExitIntent.java
│   │   ├── TradeEvent.java
│   │   ├── Position.java
│   │   ├── Direction.java
│   │   └── IntentStatus.java
│   ├── order/
│   │   ├── Order.java
│   │   ├── OrderRequest.java
│   │   ├── OrderResponse.java
│   │   └── OrderStatus.java
│   └── common/
│       ├── ValidationResult.java
│       ├── ValidationErrorCode.java
│       └── DomainEvent.java
│
├── application/                     # Use cases & orchestration
│   ├── user/
│   │   ├── AuthService.java
│   │   ├── UserService.java
│   │   ├── PortfolioService.java
│   │   └── TokenRefreshWatchdog.java
│   ├── data/
│   │   ├── MarketDataService.java
│   │   ├── CandleService.java
│   │   ├── TickCandleBuilder.java
│   │   ├── CandleAggregator.java
│   │   ├── HistoryBackfillService.java
│   │   ├── MtfBackfillService.java
│   │   ├── RecoveryManager.java
│   │   ├── InstrumentService.java
│   │   ├── WatchlistService.java
│   │   └── MarketDataCache.java
│   ├── signal/
│   │   ├── SignalGenerationService.java
│   │   ├── MtfSignalGenerator.java
│   │   ├── SignalManagementService.java
│   │   ├── EntrySignalCoordinator.java
│   │   ├── ExitSignalCoordinator.java
│   │   ├── ExitSignalService.java
│   │   ├── ConfluenceCalculator.java
│   │   ├── ZoneDetector.java
│   │   └── BrickMovementTracker.java
│   ├── risk/
│   │   ├── PositionSizingService.java
│   │   ├── MtfPositionSizer.java
│   │   ├── PortfolioRiskCalculator.java
│   │   ├── ATRCalculator.java
│   │   ├── KellyCalculator.java
│   │   ├── LogUtilityCalculator.java
│   │   └── AveragingGateValidator.java
│   ├── trade/
│   │   ├── TradeManagementService.java
│   │   ├── TradeCoordinator.java
│   │   ├── ActiveTradeIndex.java
│   │   ├── TradeClassifier.java
│   │   └── ValidationService.java
│   ├── execution/
│   │   ├── ExecutionOrchestrator.java
│   │   ├── OrderPlacementService.java
│   │   ├── OrderReconciliationService.java
│   │   ├── PendingOrderReconciler.java
│   │   ├── ExitOrderProcessor.java
│   │   └── ExitQualificationService.java
│   ├── reports/
│   │   ├── PerformanceReportService.java
│   │   ├── RiskReportService.java
│   │   ├── HealthMonitoringService.java
│   │   └── TradeAnalyticsService.java
│   └── event/
│       ├── EventService.java
│       └── EventPublisher.java
│
├── infrastructure/                  # External integrations
│   ├── broker/
│   │   ├── data/                    # DataBroker implementations
│   │   │   ├── DataBroker.java      # Interface
│   │   │   ├── FyersDataAdapter.java
│   │   │   ├── ZerodhaDataAdapter.java
│   │   │   ├── DhanDataAdapter.java
│   │   │   ├── UpstoxDataAdapter.java
│   │   │   ├── RelayDataAdapter.java
│   │   │   └── DataBrokerFactory.java
│   │   ├── order/                   # OrderBroker implementations
│   │   │   ├── OrderBroker.java     # Interface
│   │   │   ├── FyersOrderAdapter.java
│   │   │   ├── ZerodhaOrderAdapter.java
│   │   │   ├── DhanOrderAdapter.java
│   │   │   ├── DummyOrderAdapter.java
│   │   │   └── OrderBrokerFactory.java
│   │   └── auth/
│   │       ├── OAuthService.java
│   │       ├── FyersOAuthHandler.java
│   │       ├── ZerodhaOAuthHandler.java
│   │       └── FyersLoginOrchestrator.java
│   ├── persistence/
│   │   ├── repository/              # Repository interfaces
│   │   │   ├── UserRepository.java
│   │   │   ├── BrokerRepository.java
│   │   │   ├── UserBrokerRepository.java
│   │   │   ├── SignalRepository.java
│   │   │   ├── TradeRepository.java
│   │   │   ├── CandleRepository.java
│   │   │   ├── InstrumentRepository.java
│   │   │   └── (... all repository interfaces)
│   │   └── postgres/                # PostgreSQL implementations
│   │       ├── PostgresUserRepository.java
│   │       ├── PostgresBrokerRepository.java
│   │       └── (... all Postgres implementations)
│   ├── config/
│   │   ├── DatabaseConfig.java
│   │   ├── HikariConfig.java
│   │   └── MigrationRunner.java
│   ├── security/
│   │   ├── JwtService.java
│   │   ├── PasswordHasher.java
│   │   └── JwtAuthFilter.java
│   ├── messaging/
│   │   ├── EventBus.java
│   │   └── WebSocketHub.java
│   └── relay/
│       ├── TickRelayServer.java
│       ├── RelayBroadcastTickListener.java
│       └── TickJsonMapper.java
│
├── presentation/                    # User-facing interfaces
│   ├── http/
│   │   ├── controllers/
│   │   │   ├── AuthController.java
│   │   │   ├── UserController.java
│   │   │   ├── BrokerController.java
│   │   │   ├── SignalController.java
│   │   │   ├── TradeController.java
│   │   │   ├── WatchlistController.java
│   │   │   ├── AdminController.java
│   │   │   ├── ReportController.java
│   │   │   └── HealthController.java
│   │   ├── dto/
│   │   │   ├── request/
│   │   │   └── response/
│   │   └── middleware/
│   │       ├── AuthMiddleware.java
│   │       ├── ErrorHandler.java
│   │       └── RequestLogger.java
│   ├── websocket/
│   │   ├── WsConnectionHandler.java
│   │   └── WsMessageRouter.java
│   └── ui/                          # Static assets
│       ├── admin/
│       │   └── trailing-stops-config.html
│       └── monitoring/
│           └── dashboard.html
│
├── config/                          # Configuration management
│   ├── Environment.java
│   ├── AppConfig.java
│   ├── BrokerConfig.java
│   ├── MtfConfig.java
│   ├── TrailingStopsConfig.java
│   └── ConfigLoader.java
│
└── bootstrap/                       # Application startup
    ├── App.java
    ├── StartupValidator.java
    ├── DependencyInjector.java
    └── ServerBootstrap.java
```

### 1.2 Current to Target Mapping

| Current Package | Target Package | Notes |
|----------------|----------------|-------|
| `broker/` | `infrastructure/broker/{data,order,auth}` | Split by responsibility |
| `auth/` | `application/user/` + `infrastructure/security/` | Separate concerns |
| `domain/model/` | `domain/{user,broker,market,signal,trade,order}` | Organize by subdomain |
| `domain/enums/` | Merge into respective domain packages | Co-locate with entities |
| `service/candle/` | `application/data/` | Rename for clarity |
| `service/signal/` | `application/signal/` + `application/risk/` | Split risk management |
| `service/execution/` | `application/execution/` | Keep structure |
| `service/trade/` | `application/trade/` | Keep structure |
| `service/oauth/` | `infrastructure/broker/auth/` | Move to infrastructure |
| `repository/` | `infrastructure/persistence/repository/` | Add interface layer |
| `transport/http/` | `presentation/http/controllers/` | Organize by responsibility |
| `transport/ws/` | `presentation/websocket/` + `infrastructure/messaging/` | Split concerns |
| `util/Env.java` | `config/Environment.java` | Promote to config module |

---

## 2. Broker Abstraction Split

### 2.1 Current Problem

**Current Design**:
```java
// Current monolithic interface
public interface BrokerAdapter {
    // Data feed methods (WebSocket, ticks, candles)
    void connect(UserBroker userBroker);
    void subscribeTicks(String symbol, TickListener listener);
    List<Candle> getHistoricalCandles(String symbol, TimeframeType tf, LocalDate from, LocalDate to);
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

**Limitation**: Cannot mix data providers (e.g., Zerodha data + FYERS orders) because one adapter does both.

### 2.2 Target Solution

**Split into two interfaces**:

#### 2.2.1 DataBroker Interface

```java
package in.annupaper.infrastructure.broker.data;

/**
 * Defines methods for connecting to live market data.
 * Implementations: FYERS, Zerodha, Dhan, Upstox, Relay
 */
public interface DataBroker {

    /**
     * Establish connection to data feed using user credentials
     */
    void connect(UserBroker userBroker);

    /**
     * Disconnect from data feed
     */
    void disconnect();

    /**
     * Subscribe to real-time tick data for a symbol
     * Multiple listeners can subscribe to the same symbol
     */
    void subscribeTicks(String symbol, TickListener listener);

    /**
     * Unsubscribe from tick data
     */
    void unsubscribeTicks(String symbol, TickListener listener);

    /**
     * Fetch historical candles for a symbol and timeframe
     * Returns aggregated candles if broker doesn't support the timeframe
     */
    List<Candle> getHistoricalCandles(
        String symbol,
        TimeframeType timeframe,
        LocalDate fromDate,
        LocalDate toDate
    );

    /**
     * Download instrument master (symbol, token, lot size, etc.)
     */
    List<Instrument> getInstruments();

    /**
     * Check if data feed is connected and receiving data
     */
    boolean isConnected();

    /**
     * Get detailed health status (connection state, last tick time, error count)
     */
    DataFeedHealth getHealthStatus();

    /**
     * Get broker identifier (FYERS, ZERODHA, etc.)
     */
    String getBrokerId();
}
```

#### 2.2.2 OrderBroker Interface

```java
package in.annupaper.infrastructure.broker.order;

/**
 * Defines methods for placing and managing orders.
 * Implementations: FYERS, Zerodha, Dhan, Dummy (paper trading)
 */
public interface OrderBroker {

    /**
     * Establish connection using user credentials
     */
    void connect(UserBroker userBroker);

    /**
     * Disconnect from broker
     */
    void disconnect();

    /**
     * Place a new order
     * @return Broker order ID
     */
    String placeOrder(OrderRequest request);

    /**
     * Modify an existing order (price, quantity)
     */
    void modifyOrder(String brokerOrderId, OrderRequest request);

    /**
     * Cancel an existing order
     */
    void cancelOrder(String brokerOrderId);

    /**
     * Get current status of an order
     */
    OrderStatus getOrderStatus(String brokerOrderId);

    /**
     * Get all open positions
     */
    List<Position> getPositions();

    /**
     * Get today's order history
     */
    List<OrderResponse> getOrderHistory();

    /**
     * Check if broker connection is active
     */
    boolean isConnected();

    /**
     * Get broker identifier (FYERS, ZERODHA, etc.)
     */
    String getBrokerId();
}
```

### 2.3 Adapter Extraction Plan

#### 2.3.1 FyersAdapter Extraction

**Current**: `FyersAdapter` implements `BrokerAdapter` (mixed responsibilities)

**Target**: Split into two classes

**Step 1: Extract FyersDataAdapter**

```java
package in.annupaper.infrastructure.broker.data;

/**
 * FYERS v3 data feed implementation
 * - WebSocket v2 for real-time ticks
 * - REST API v3 for historical candles
 * - Instrument master download
 */
public class FyersDataAdapter implements DataBroker {

    private final UserBrokerSessionRepository sessionRepo;
    private final CandleAggregator aggregator;

    // WebSocket connection
    private WebSocket webSocket;
    private Map<String, List<TickListener>> tickListeners;

    // Health monitoring
    private Instant lastTickTime;
    private int errorCount;
    private CircuitBreaker circuitBreaker;

    @Override
    public void connect(UserBroker userBroker) {
        // Extract from FyersAdapter: OAuth token loading, WebSocket connection
    }

    @Override
    public void subscribeTicks(String symbol, TickListener listener) {
        // Extract from FyersAdapter: WebSocket subscription logic
    }

    @Override
    public List<Candle> getHistoricalCandles(...) {
        // Extract from FyersAdapter: REST API calls + aggregation
    }

    @Override
    public List<Instrument> getInstruments() {
        // Extract from FyersAdapter: Download instrument CSV
    }

    @Override
    public DataFeedHealth getHealthStatus() {
        return new DataFeedHealth(
            isConnected(),
            lastTickTime,
            errorCount,
            circuitBreaker.getState()
        );
    }
}
```

**Step 2: Extract FyersOrderAdapter**

```java
package in.annupaper.infrastructure.broker.order;

/**
 * FYERS v3 order execution implementation
 * - REST API v3 for order placement
 * - Order status tracking
 * - Position management
 */
public class FyersOrderAdapter implements OrderBroker {

    private final UserBrokerSessionRepository sessionRepo;
    private String accessToken;

    @Override
    public void connect(UserBroker userBroker) {
        // Extract from FyersAdapter: OAuth token loading
    }

    @Override
    public String placeOrder(OrderRequest request) {
        // Extract from FyersAdapter: Order placement REST API call
    }

    @Override
    public void modifyOrder(String brokerOrderId, OrderRequest request) {
        // Extract from FyersAdapter: Order modification
    }

    @Override
    public void cancelOrder(String brokerOrderId) {
        // Extract from FyersAdapter: Order cancellation
    }

    @Override
    public OrderStatus getOrderStatus(String brokerOrderId) {
        // Extract from FyersAdapter: Fetch order status
    }

    @Override
    public List<Position> getPositions() {
        // Extract from FyersAdapter: Fetch positions
    }
}
```

#### 2.3.2 ZerodhaDataAdapter (Already Exists)

**Status**: Production-ready implementation at `broker/adapters/ZerodhaDataAdapter.java`

**Action**: Move to `infrastructure/broker/data/ZerodhaDataAdapter.java` and implement `DataBroker` interface

**Key Changes**:
```java
public class ZerodhaDataAdapter implements DataBroker {
    // Existing implementation already matches DataBroker pattern
    // Just needs to implement the interface formally
}
```

#### 2.3.3 ZerodhaOrderAdapter (New)

**Status**: Needs implementation

**Action**: Create new `ZerodhaOrderAdapter` for order execution

```java
package in.annupaper.infrastructure.broker.order;

/**
 * Zerodha Kite Connect v3 order execution
 * - Place/modify/cancel orders via REST API
 * - Position tracking
 * - Order status reconciliation
 */
public class ZerodhaOrderAdapter implements OrderBroker {

    private final UserBrokerSessionRepository sessionRepo;
    private KiteConnect kite;

    @Override
    public void connect(UserBroker userBroker) {
        // Load access token from session
        // Initialize KiteConnect client
    }

    @Override
    public String placeOrder(OrderRequest request) {
        // Map OrderRequest → Zerodha Order params
        // Call kite.placeOrder()
        // Return order ID
    }

    // ... implement other methods
}
```

### 2.4 Factory Pattern

#### 2.4.1 DataBrokerFactory

```java
package in.annupaper.infrastructure.broker.data;

public class DataBrokerFactory {

    private final Map<String, DataBroker> cache = new ConcurrentHashMap<>();

    private final UserBrokerSessionRepository sessionRepo;
    private final CandleAggregator aggregator;
    private final InstrumentRepository instrumentRepo;

    /**
     * Get or create DataBroker for a user-broker link
     * Uses BrokerIds constants for broker identification
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

        return switch (brokerId) {
            case BrokerIds.FYERS -> new FyersDataAdapter(sessionRepo, aggregator);
            case BrokerIds.ZERODHA -> new ZerodhaDataAdapter(sessionRepo, instrumentRepo);
            case BrokerIds.DHAN -> new DhanDataAdapter(sessionRepo);
            case BrokerIds.RELAY -> new RelayDataAdapter();
            default -> throw new UnsupportedOperationException("Unknown data broker: " + brokerId);
        };
    }

    /**
     * Auto-connect all DATA brokers on startup
     */
    public void connectAllDataBrokers(UserBrokerRepository userBrokerRepo) {
        List<UserBroker> dataBrokers = userBrokerRepo.findByRole(BrokerRole.DATA);

        for (UserBroker ub : dataBrokers) {
            if (ub.isEnabled()) {
                DataBroker broker = getDataBroker(ub);
                broker.connect(ub);
            }
        }
    }
}
```

#### 2.4.2 OrderBrokerFactory

```java
package in.annupaper.infrastructure.broker.order;

public class OrderBrokerFactory {

    private final Map<String, OrderBroker> cache = new ConcurrentHashMap<>();

    private final UserBrokerSessionRepository sessionRepo;

    /**
     * Get or create OrderBroker for a user-broker link
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

        // Use dummy broker for PAPER environment
        if (env == BrokerEnvironment.PAPER) {
            return new DummyOrderAdapter();
        }

        return switch (brokerId) {
            case BrokerIds.FYERS -> new FyersOrderAdapter(sessionRepo);
            case BrokerIds.ZERODHA -> new ZerodhaOrderAdapter(sessionRepo);
            case BrokerIds.DHAN -> new DhanOrderAdapter(sessionRepo);
            default -> throw new UnsupportedOperationException("Unknown order broker: " + brokerId);
        };
    }

    /**
     * Auto-connect all EXEC brokers on startup
     */
    public void connectAllOrderBrokers(UserBrokerRepository userBrokerRepo) {
        List<UserBroker> execBrokers = userBrokerRepo.findByRole(BrokerRole.EXEC);

        for (UserBroker ub : execBrokers) {
            if (ub.isEnabled()) {
                OrderBroker broker = getOrderBroker(ub);
                broker.connect(ub);
            }
        }
    }
}
```

### 2.5 Service Updates

#### Update OrderExecutionService

**Before**:
```java
public class OrderExecutionService {
    private final BrokerAdapterFactory brokerFactory;

    public void placeOrder(TradeIntent intent) {
        BrokerAdapter broker = brokerFactory.getAdapter(intent.getUserBroker());
        String orderId = broker.placeOrder(orderRequest);
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
    }
}
```

#### Update TickCandleBuilder

**Before**:
```java
public class TickCandleBuilder implements TickListener {
    // Receives ticks from BrokerAdapter
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
}
```

---

## 3. Module-by-Module Migration

### 3.1 Phase 1: Domain Layer (Week 1)

**Goal**: Extract pure domain entities with zero dependencies

#### 3.1.1 User Subdomain

**Files to Move**:
```
domain/model/User.java → domain/user/User.java
domain/model/UserRole.java → domain/user/UserRole.java
domain/model/UserSession.java → domain/user/UserSession.java
domain/model/Portfolio.java → domain/user/Portfolio.java
```

**New Structure**:
```java
package in.annupaper.domain.user;

public record User(
    UUID userId,
    String email,
    String passwordHash,
    UserRole role,
    Instant createdAt,
    Instant deletedAt
) {}

public enum UserRole {
    USER, ADMIN
}

public record UserSession(
    UUID sessionId,
    UUID userId,
    String jwtToken,
    Instant expiresAt
) {}

public record Portfolio(
    UUID portfolioId,
    UUID userId,
    BigDecimal capitalAllocated,
    BigDecimal maxExposure,
    BigDecimal maxPerTrade
) {}
```

#### 3.1.2 Broker Subdomain

**Files to Move**:
```
domain/model/Broker.java → domain/broker/Broker.java
domain/model/UserBroker.java → domain/broker/UserBroker.java
domain/model/UserBrokerSession.java → domain/broker/UserBrokerSession.java
domain/enums/BrokerRole.java → domain/broker/BrokerRole.java
domain/enums/BrokerEnvironment.java → domain/broker/BrokerEnvironment.java
domain/constants/BrokerIds.java → domain/broker/BrokerIds.java
```

#### 3.1.3 Market Subdomain

**Files to Move**:
```
domain/model/Candle.java → domain/market/Candle.java
service/candle/Tick.java → domain/market/Tick.java
domain/model/Watchlist.java → domain/market/Watchlist.java
domain/model/WatchlistTemplate.java → domain/market/WatchlistTemplate.java
domain/enums/TimeframeType.java → domain/market/TimeframeType.java
```

**New Model**: Create `Instrument.java` if not exists

```java
package in.annupaper.domain.market;

public record Instrument(
    String symbol,
    String name,
    String exchange,
    String instrumentToken,
    int lotSize,
    BigDecimal tickSize,
    String segment
) {}
```

#### 3.1.4 Signal Subdomain

**Files to Move**:
```
domain/model/Signal.java → domain/signal/Signal.java
domain/model/ExitSignal.java → domain/signal/ExitSignal.java
domain/enums/SignalType.java → domain/signal/SignalType.java
domain/enums/ExitReason.java → domain/signal/ExitReason.java
domain/enums/ConfluenceType.java → domain/signal/ConfluenceType.java
```

#### 3.1.5 Trade Subdomain

**Files to Move**:
```
domain/model/Trade.java → domain/trade/Trade.java
domain/model/TradeIntent.java → domain/trade/TradeIntent.java
domain/model/ExitIntent.java → domain/trade/ExitIntent.java
domain/model/TradeEvent.java → domain/trade/TradeEvent.java
domain/enums/Direction.java → domain/trade/Direction.java
domain/enums/IntentStatus.java → domain/trade/IntentStatus.java
domain/enums/ExitIntentStatus.java → domain/trade/ExitIntentStatus.java
```

**New Model**: Extract `Position.java` from Trade domain

```java
package in.annupaper.domain.trade;

public record Position(
    String symbol,
    int quantity,
    Direction direction,
    BigDecimal avgPrice,
    BigDecimal currentPrice,
    BigDecimal unrealizedPnL
) {}
```

#### 3.1.6 Order Subdomain (New)

**Create New Models**:

```java
package in.annupaper.domain.order;

public record OrderRequest(
    String symbol,
    Direction direction,
    int quantity,
    OrderType orderType,
    BigDecimal limitPrice,
    BigDecimal stopPrice,
    TimeInForce timeInForce
) {}

public record OrderResponse(
    String brokerOrderId,
    String symbol,
    OrderStatus status,
    int filledQuantity,
    BigDecimal avgFillPrice,
    Instant orderTime,
    Instant fillTime
) {}

public enum OrderStatus {
    PENDING, PLACED, PARTIAL, FILLED, REJECTED, CANCELLED
}

public enum OrderType {
    MARKET, LIMIT, STOP_LOSS
}

public enum TimeInForce {
    DAY, IOC, GTC
}
```

#### 3.1.7 Migration Steps

1. **Create new packages**:
   ```bash
   mkdir -p src/main/java/in/annupaper/domain/{user,broker,market,signal,trade,order,common}
   ```

2. **Copy files** (don't delete yet):
   ```bash
   # Example for User
   cp src/main/java/in/annupaper/domain/model/User.java \
      src/main/java/in/annupaper/domain/user/User.java
   ```

3. **Update package declarations**:
   ```java
   // Before
   package in.annupaper.domain.model;

   // After
   package in.annupaper.domain.user;
   ```

4. **Update imports across codebase**:
   ```bash
   # Find all files importing old domain classes
   grep -r "import in.annupaper.domain.model.User" src/

   # Replace with new import
   find src/ -type f -name "*.java" -exec sed -i '' \
     's/import in\.annupaper\.domain\.model\.User/import in.annupaper.domain.user.User/g' {} \;
   ```

5. **Compile and test**:
   ```bash
   mvn clean compile
   mvn test
   ```

6. **Delete old files** once tests pass:
   ```bash
   rm src/main/java/in/annupaper/domain/model/User.java
   ```

### 3.2 Phase 2: Infrastructure Layer (Week 2)

#### 3.2.1 Broker Adapters

**Step 1: Create Interface Packages**

```bash
mkdir -p src/main/java/in/annupaper/infrastructure/broker/{data,order,auth}
```

**Step 2: Create DataBroker Interface**

```java
// infrastructure/broker/data/DataBroker.java
// (Full interface shown in Section 2.2.1)
```

**Step 3: Create OrderBroker Interface**

```java
// infrastructure/broker/order/OrderBroker.java
// (Full interface shown in Section 2.2.2)
```

**Step 4: Extract FyersDataAdapter**

1. Copy `broker/adapters/FyersAdapter.java` → `infrastructure/broker/data/FyersDataAdapter.java`
2. Remove all order-related methods:
   - `placeOrder()`
   - `modifyOrder()`
   - `cancelOrder()`
   - `getOrderStatus()`
3. Implement `DataBroker` interface
4. Update class declaration:
   ```java
   public class FyersDataAdapter implements DataBroker {
       // Only data feed methods remain
   }
   ```

**Step 5: Extract FyersOrderAdapter**

1. Copy `broker/adapters/FyersAdapter.java` → `infrastructure/broker/order/FyersOrderAdapter.java`
2. Remove all data-related methods:
   - `subscribeTicks()`
   - `getHistoricalCandles()`
   - `getInstruments()`
   - WebSocket connection code
3. Implement `OrderBroker` interface
4. Keep only:
   - OAuth token loading
   - Order placement REST API calls
   - Order status tracking

**Step 6: Move ZerodhaDataAdapter**

```bash
mv src/main/java/in/annupaper/broker/adapters/ZerodhaDataAdapter.java \
   src/main/java/in/annupaper/infrastructure/broker/data/ZerodhaDataAdapter.java
```

Update to implement `DataBroker` interface.

**Step 7: Create ZerodhaOrderAdapter**

1. Reference: Zerodha Kite Connect SDK documentation
2. Implement `OrderBroker` interface
3. Map `OrderRequest` → Kite Connect order params
4. Handle order placement, status, cancellation

**Step 8: Move Other Adapters**

```bash
# Relay adapter
mv broker/adapters/RelayWebSocketAdapter.java \
   infrastructure/broker/data/RelayDataAdapter.java

# Dummy adapter
mv broker/adapters/DummyAdapter.java \
   infrastructure/broker/order/DummyOrderAdapter.java
```

**Step 9: Create Factories**

```bash
# Create DataBrokerFactory (see Section 2.4.1)
# Create OrderBrokerFactory (see Section 2.4.2)
```

**Step 10: Delete Old BrokerAdapter Interface**

```bash
rm broker/BrokerAdapter.java
rm broker/BrokerAdapterFactory.java
```

#### 3.2.2 OAuth Services

**Move Files**:
```
service/oauth/BrokerOAuthService.java → infrastructure/broker/auth/OAuthService.java
service/oauth/FyersLoginOrchestrator.java → infrastructure/broker/auth/FyersOAuthHandler.java
```

**Create New**:
```java
package in.annupaper.infrastructure.broker.auth;

public class ZerodhaOAuthHandler {
    // Handle Zerodha OAuth flow
    // - Generate login URL
    // - Exchange request token for access token
    // - Store in UserBrokerSession
}
```

#### 3.2.3 Persistence Layer

**Step 1: Create Repository Interfaces**

```bash
mkdir -p src/main/java/in/annupaper/infrastructure/persistence/repository
mkdir -p src/main/java/in/annupaper/infrastructure/persistence/postgres
```

**Step 2: Extract Interfaces**

For each existing repository, extract an interface:

```java
package in.annupaper.infrastructure.persistence.repository;

public interface UserRepository {
    User findById(UUID userId);
    User findByEmail(String email);
    User save(User user);
    void delete(UUID userId);
}
```

**Step 3: Move Implementations**

```bash
mv repository/PostgresUserRepository.java \
   infrastructure/persistence/postgres/PostgresUserRepository.java
```

Update to implement interface:
```java
public class PostgresUserRepository implements UserRepository {
    // Existing implementation
}
```

**Repeat for all repositories**:
- UserRepository / PostgresUserRepository
- BrokerRepository / PostgresBrokerRepository
- UserBrokerRepository / PostgresUserBrokerRepository
- SignalRepository / PostgresSignalRepository
- TradeRepository / PostgresTradeRepository
- CandleRepository / PostgresCandleRepository
- InstrumentRepository / PostgresInstrumentRepository
- (... all others)

#### 3.2.4 Security

**Move Files**:
```
auth/JwtService.java → infrastructure/security/JwtService.java
```

**Create New**:
```java
package in.annupaper.infrastructure.security;

public class PasswordHasher {
    public String hash(String plaintext) {
        return BCrypt.hashpw(plaintext, BCrypt.gensalt());
    }

    public boolean verify(String plaintext, String hash) {
        return BCrypt.checkpw(plaintext, hash);
    }
}

public class JwtAuthFilter {
    // Undertow exchange filter for JWT validation
    // Extract and validate JWT from Authorization header
}
```

#### 3.2.5 Messaging

**Move Files**:
```
transport/ws/WsHub.java → infrastructure/messaging/WebSocketHub.java
service/core/EventService.java → infrastructure/messaging/EventBus.java
```

### 3.3 Phase 3: Application Layer (Week 3)

#### 3.3.1 User Services

**Create Package**:
```bash
mkdir -p src/main/java/in/annupaper/application/user
```

**Move Files**:
```
auth/AuthService.java → application/user/AuthService.java
service/TokenRefreshWatchdog.java → application/user/TokenRefreshWatchdog.java
```

**Create New**:
```java
package in.annupaper.application.user;

public class UserService {
    private final UserRepository userRepo;
    private final PortfolioRepository portfolioRepo;

    public User createUser(String email, String password) {
        // User registration logic
    }

    public Portfolio getPortfolio(UUID userId) {
        // Fetch user portfolio
    }

    public void updateRiskLimits(UUID userId, RiskLimits limits) {
        // Update capital allocation, max exposure, etc.
    }
}

public class PortfolioService {
    // Portfolio management logic
    // - Calculate available capital
    // - Track deployed capital
    // - Enforce exposure limits
}
```

#### 3.3.2 Data Services

**Create Package**:
```bash
mkdir -p src/main/java/in/annupaper/application/data
```

**Move Files**:
```
service/candle/TickCandleBuilder.java → application/data/TickCandleBuilder.java
service/candle/CandleStore.java → application/data/CandleStore.java
service/candle/CandleAggregator.java → application/data/CandleAggregator.java
service/candle/HistoryBackfiller.java → application/data/HistoryBackfillService.java
service/candle/MtfBackfillService.java → application/data/MtfBackfillService.java
service/candle/RecoveryManager.java → application/data/RecoveryManager.java
service/InstrumentService.java → application/data/InstrumentService.java
service/MarketDataCache.java → application/data/MarketDataCache.java
```

**Create New**:
```java
package in.annupaper.application.data;

public class MarketDataService {
    private final DataBrokerFactory dataBrokerFactory;
    private final TickCandleBuilder candleBuilder;
    private final MarketDataCache cache;

    /**
     * Subscribe to market data for a symbol
     * Coordinates between DataBroker, CandleBuilder, and cache
     */
    public void subscribeToSymbol(String symbol) {
        // 1. Get DATA broker for user
        // 2. Subscribe TickCandleBuilder as listener
        // 3. Subscribe MarketDataCache as listener
        // 4. Subscribe MtfSignalGenerator as listener
    }

    /**
     * Unsubscribe from market data
     */
    public void unsubscribeFromSymbol(String symbol) {
        // Remove all listeners
    }
}

public class CandleService {
    private final CandleStore candleStore;
    private final CandleAggregator aggregator;

    /**
     * Get candles for a symbol/timeframe (memory → DB fallback)
     */
    public List<Candle> getCandles(String symbol, TimeframeType tf, int count) {
        return candleStore.getRecentCandles(symbol, tf, count);
    }

    /**
     * Store a new candle (memory + DB)
     */
    public void storeCandle(Candle candle) {
        candleStore.store(candle);
    }
}

public class WatchlistService {
    private final WatchlistRepository watchlistRepo;
    private final WatchlistTemplateRepository templateRepo;

    // Watchlist CRUD operations
}
```

**Update Imports**:
All services that use candle-related classes must update imports:
```java
// Before
import in.annupaper.service.candle.TickCandleBuilder;

// After
import in.annupaper.application.data.TickCandleBuilder;
```

#### 3.3.3 Signal Services

**Create Package**:
```bash
mkdir -p src/main/java/in/annupaper/application/signal
```

**Move Files**:
```
service/signal/SignalService.java → application/signal/SignalGenerationService.java
service/signal/MtfSignalGenerator.java → application/signal/MtfSignalGenerator.java
service/signal/SignalManagementService.java → application/signal/SignalManagementService.java
service/signal/EntrySignalCoordinator.java → application/signal/EntrySignalCoordinator.java
service/signal/ExitSignalCoordinator.java → application/signal/ExitSignalCoordinator.java
service/signal/ExitSignalService.java → application/signal/ExitSignalService.java
service/signal/ConfluenceCalculator.java → application/signal/ConfluenceCalculator.java
service/signal/ZoneDetector.java → application/signal/ZoneDetector.java
service/signal/BrickMovementTracker.java → application/signal/BrickMovementTracker.java
```

**Update Dependencies**:
- `MtfSignalGenerator` uses `TickListener` → Update import to `domain.market.Tick`
- `ExitSignalService` uses `TradeManagementService` → Update import to `application.trade`

#### 3.3.4 Risk Services (New Module)

**Create Package**:
```bash
mkdir -p src/main/java/in/annupaper/application/risk
```

**Move Files**:
```
service/signal/PositionSizingService.java → application/risk/PositionSizingService.java
service/signal/MtfPositionSizer.java → application/risk/MtfPositionSizer.java
service/signal/PortfolioRiskCalculator.java → application/risk/PortfolioRiskCalculator.java
service/signal/ATRCalculator.java → application/risk/ATRCalculator.java
service/signal/KellyCalculator.java → application/risk/KellyCalculator.java
service/signal/LogUtilityCalculator.java → application/risk/LogUtilityCalculator.java
service/signal/AveragingGateValidator.java → application/risk/AveragingGateValidator.java
```

**Rationale**: Risk management is a distinct concern from signal generation. Separate it for clarity.

#### 3.3.5 Trade Services

**Create Package**:
```bash
mkdir -p src/main/java/in/annupaper/application/trade
```

**Move Files**:
```
service/trade/TradeManagementService.java → application/trade/TradeManagementService.java
service/trade/TradeCoordinator.java → application/trade/TradeCoordinator.java
service/trade/ActiveTradeIndex.java → application/trade/ActiveTradeIndex.java
service/trade/TradeClassifier.java → application/trade/TradeClassifier.java
service/validation/ValidationService.java → application/trade/ValidationService.java
service/validation/ExitQualificationService.java → application/trade/ExitQualificationService.java
```

#### 3.3.6 Execution Services

**Create Package**:
```bash
mkdir -p src/main/java/in/annupaper/application/execution
```

**Move Files**:
```
service/execution/ExecutionOrchestrator.java → application/execution/ExecutionOrchestrator.java
service/execution/OrderExecutionService.java → application/execution/OrderPlacementService.java
service/execution/ExitOrderExecutionService.java → application/execution/ExitOrderPlacementService.java
service/execution/PendingOrderReconciler.java → application/execution/PendingOrderReconciler.java
service/execution/ExitOrderProcessor.java → application/execution/ExitOrderProcessor.java
service/execution/ExitOrderReconciler.java → application/execution/ExitOrderReconciler.java
```

**Update Service Code**:

```java
package in.annupaper.application.execution;

public class OrderPlacementService {
    private final OrderBrokerFactory orderBrokerFactory;  // Changed from BrokerAdapterFactory
    private final TradeRepository tradeRepo;

    public void placeOrder(TradeIntent intent) {
        // Get EXEC broker (not DATA broker)
        UserBroker execBroker = intent.getUserBroker();
        OrderBroker broker = orderBrokerFactory.getOrderBroker(execBroker);

        // Convert intent → OrderRequest
        OrderRequest request = buildOrderRequest(intent);

        // Place order
        String brokerOrderId = broker.placeOrder(request);

        // Update trade intent
        intent.setBrokerOrderId(brokerOrderId);
        intent.setStatus(IntentStatus.PLACED);
    }
}
```

#### 3.3.7 Reports Services (New Module)

**Create Package**:
```bash
mkdir -p src/main/java/in/annupaper/application/reports
```

**Create New Services**:

```java
package in.annupaper.application.reports;

public class PerformanceReportService {
    private final TradeRepository tradeRepo;

    /**
     * Generate daily performance summary
     * - Total P&L
     * - Win rate
     * - Avg gain/loss
     * - Max drawdown
     */
    public DailyPerformance getDailyReport(UUID userId, LocalDate date) {
        // Aggregate trades for the day
    }

    /**
     * Generate weekly/monthly reports
     */
    public PeriodicPerformance getPeriodicReport(UUID userId, LocalDate from, LocalDate to) {
        // Aggregate trades for period
    }
}

public class RiskReportService {
    private final TradeRepository tradeRepo;
    private final PortfolioRepository portfolioRepo;

    /**
     * Calculate current portfolio risk metrics
     * - Total exposure
     * - Concentration risk
     * - Max potential loss
     */
    public RiskMetrics getCurrentRisk(UUID userId) {
        // Analyze open positions
    }
}

public class HealthMonitoringService {
    private final DataBrokerFactory dataBrokerFactory;
    private final OrderBrokerFactory orderBrokerFactory;
    private final UserBrokerRepository userBrokerRepo;

    /**
     * Check health of all brokers
     */
    public Map<String, HealthStatus> getSystemHealth() {
        // Check data brokers
        // Check order brokers
        // Check database connection
        // Check candle gaps
    }
}

public class TradeAnalyticsService {
    /**
     * Analyze trade patterns
     * - Best performing symbols
     * - Best performing timeframes
     * - Confluence type effectiveness
     */
    public TradeAnalytics getAnalytics(UUID userId, LocalDate from, LocalDate to) {
        // Deep dive into trade data
    }
}
```

#### 3.3.8 Event Services

**Create Package**:
```bash
mkdir -p src/main/java/in/annupaper/application/event
```

**Move Files**:
```
service/core/EventService.java → application/event/EventService.java
```

**Update to use EventBus**:
```java
package in.annupaper.application.event;

public class EventService {
    private final EventBus eventBus;  // From infrastructure.messaging
    private final WebSocketHub wsHub;  // From infrastructure.messaging

    /**
     * Publish event to all users
     */
    public void publishGlobal(EventType type, Object data) {
        eventBus.publish(new SystemEvent(type, data));
        wsHub.broadcastToAll(type, data);
    }

    /**
     * Publish event to specific user
     */
    public void publishToUser(UUID userId, EventType type, Object data) {
        wsHub.sendToUser(userId, type, data);
    }
}
```

### 3.4 Phase 4: Presentation Layer (Week 4)

#### 3.4.1 HTTP Controllers

**Create Package Structure**:
```bash
mkdir -p src/main/java/in/annupaper/presentation/http/{controllers,dto,middleware}
mkdir -p src/main/java/in/annupaper/presentation/http/dto/{request,response}
```

**Extract Controllers from ApiHandlers.java**:

Current `transport/http/ApiHandlers.java` is a monolithic class with all endpoints. Split into multiple controllers:

```java
package in.annupaper.presentation.http.controllers;

public class AuthController {
    private final AuthService authService;
    private final JwtService jwtService;

    public void handleLogin(HttpServerExchange exchange) {
        // POST /api/auth/login
        // Extract email/password from body
        // Authenticate user
        // Generate JWT token
        // Return response
    }

    public void handleRegister(HttpServerExchange exchange) {
        // POST /api/auth/register
    }

    public void handleLogout(HttpServerExchange exchange) {
        // POST /api/auth/logout
    }
}

public class UserController {
    private final UserService userService;
    private final PortfolioService portfolioService;

    public void getProfile(HttpServerExchange exchange) {
        // GET /api/user/profile
    }

    public void updateProfile(HttpServerExchange exchange) {
        // PUT /api/user/profile
    }

    public void getPortfolio(HttpServerExchange exchange) {
        // GET /api/user/portfolio
    }
}

public class BrokerController {
    private final UserBrokerRepository userBrokerRepo;
    private final OAuthService oauthService;

    public void listBrokers(HttpServerExchange exchange) {
        // GET /api/brokers
    }

    public void linkBroker(HttpServerExchange exchange) {
        // POST /api/brokers/link
    }

    public void initiateOAuth(HttpServerExchange exchange) {
        // GET /api/brokers/{brokerId}/oauth/initiate
    }

    public void handleOAuthCallback(HttpServerExchange exchange) {
        // GET /api/brokers/{brokerId}/oauth/callback
    }
}

public class SignalController {
    private final SignalRepository signalRepo;
    private final SignalManagementService signalService;

    public void listSignals(HttpServerExchange exchange) {
        // GET /api/signals
    }

    public void getSignal(HttpServerExchange exchange) {
        // GET /api/signals/{signalId}
    }
}

public class TradeController {
    private final TradeRepository tradeRepo;
    private final TradeManagementService tradeService;

    public void listTrades(HttpServerExchange exchange) {
        // GET /api/trades
    }

    public void getTrade(HttpServerExchange exchange) {
        // GET /api/trades/{tradeId}
    }

    public void exitTrade(HttpServerExchange exchange) {
        // POST /api/trades/{tradeId}/exit
    }
}

public class WatchlistController {
    private final WatchlistService watchlistService;

    public void listWatchlists(HttpServerExchange exchange) {
        // GET /api/watchlists
    }

    public void createWatchlist(HttpServerExchange exchange) {
        // POST /api/watchlists
    }

    public void updateWatchlist(HttpServerExchange exchange) {
        // PUT /api/watchlists/{watchlistId}
    }

    public void deleteWatchlist(HttpServerExchange exchange) {
        // DELETE /api/watchlists/{watchlistId}
    }
}

public class AdminController {
    private final AdminService adminService;
    private final TrailingStopsConfigService trailingStopsService;

    public void getConfig(HttpServerExchange exchange) {
        // GET /api/admin/config
    }

    public void updateConfig(HttpServerExchange exchange) {
        // PUT /api/admin/config
    }

    public void getTrailingStopsConfig(HttpServerExchange exchange) {
        // GET /api/admin/trailing-stops
    }

    public void updateTrailingStopsConfig(HttpServerExchange exchange) {
        // PUT /api/admin/trailing-stops
    }
}

public class ReportController {
    private final PerformanceReportService performanceService;
    private final RiskReportService riskService;
    private final TradeAnalyticsService analyticsService;

    public void getDailyReport(HttpServerExchange exchange) {
        // GET /api/reports/daily
    }

    public void getPeriodicReport(HttpServerExchange exchange) {
        // GET /api/reports/periodic
    }

    public void getRiskMetrics(HttpServerExchange exchange) {
        // GET /api/reports/risk
    }

    public void getAnalytics(HttpServerExchange exchange) {
        // GET /api/reports/analytics
    }
}

public class HealthController {
    private final HealthMonitoringService healthService;

    public void getSystemHealth(HttpServerExchange exchange) {
        // GET /api/health
    }

    public void getBrokerHealth(HttpServerExchange exchange) {
        // GET /api/health/brokers
    }
}
```

#### 3.4.2 DTOs (Data Transfer Objects)

**Create Request DTOs**:

```java
package in.annupaper.presentation.http.dto.request;

public record LoginRequest(String email, String password) {}

public record RegisterRequest(String email, String password) {}

public record LinkBrokerRequest(
    String brokerId,
    String role,  // DATA | EXEC
    String environment,  // PAPER | LIVE
    Map<String, String> credentials
) {}

public record CreateWatchlistRequest(
    String name,
    List<String> symbols
) {}

public record UpdateConfigRequest(
    Map<String, Object> config
) {}
```

**Create Response DTOs**:

```java
package in.annupaper.presentation.http.dto.response;

public record LoginResponse(
    String token,
    String email,
    String role
) {}

public record SignalResponse(
    UUID signalId,
    String symbol,
    String direction,
    String confluenceType,
    BigDecimal entryPrice,
    Instant generatedAt
) {}

public record TradeResponse(
    UUID tradeId,
    String symbol,
    String direction,
    int quantity,
    BigDecimal entryPrice,
    BigDecimal exitPrice,
    BigDecimal pnl,
    String status
) {}

public record HealthResponse(
    String status,  // HEALTHY | DEGRADED | DOWN
    Map<String, BrokerHealthStatus> brokers,
    Instant timestamp
) {}
```

#### 3.4.3 Middleware

**Create Middleware Classes**:

```java
package in.annupaper.presentation.http.middleware;

public class AuthMiddleware {
    private final JwtService jwtService;

    /**
     * Extract JWT from Authorization header and validate
     * Attach userId to exchange for downstream handlers
     */
    public void authenticate(HttpServerExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.setStatusCode(401);
            exchange.getResponseSender().send("{\"error\":\"Unauthorized\"}");
            return;
        }

        String token = authHeader.substring(7);

        try {
            UUID userId = jwtService.validateToken(token);
            exchange.putAttachment(USER_ID_KEY, userId);
        } catch (Exception e) {
            exchange.setStatusCode(401);
            exchange.getResponseSender().send("{\"error\":\"Invalid token\"}");
        }
    }
}

public class ErrorHandler {
    /**
     * Global error handler for all exceptions
     */
    public void handleError(HttpServerExchange exchange, Throwable throwable) {
        if (throwable instanceof ValidationException) {
            exchange.setStatusCode(400);
        } else if (throwable instanceof NotFoundException) {
            exchange.setStatusCode(404);
        } else {
            exchange.setStatusCode(500);
        }

        String errorJson = String.format(
            "{\"error\":\"%s\",\"message\":\"%s\"}",
            throwable.getClass().getSimpleName(),
            throwable.getMessage()
        );

        exchange.getResponseSender().send(errorJson);
    }
}

public class RequestLogger {
    private static final Logger logger = LoggerFactory.getLogger(RequestLogger.class);

    /**
     * Log all incoming requests
     */
    public void logRequest(HttpServerExchange exchange) {
        logger.info("{} {} - User: {}",
            exchange.getRequestMethod(),
            exchange.getRequestPath(),
            exchange.getAttachment(USER_ID_KEY)
        );
    }
}
```

#### 3.4.4 WebSocket Handler

**Create Package**:
```bash
mkdir -p src/main/java/in/annupaper/presentation/websocket
```

**Move and Refactor**:
```
transport/ws/WsHub.java → presentation/websocket/WsConnectionHandler.java
```

```java
package in.annupaper.presentation.websocket;

public class WsConnectionHandler {
    private final WebSocketHub wsHub;  // From infrastructure.messaging
    private final JwtService jwtService;

    /**
     * Handle WebSocket connection upgrade
     */
    public void handleConnect(WebSocketHttpExchange exchange) {
        // Extract JWT from query param or cookie
        // Validate token
        // Register connection in WebSocketHub
        // Send welcome message
    }

    /**
     * Handle WebSocket disconnect
     */
    public void handleDisconnect(WebSocketChannel channel) {
        // Remove connection from WebSocketHub
    }
}

public class WsMessageRouter {
    /**
     * Route incoming WebSocket messages to handlers
     */
    public void route(WebSocketChannel channel, String message) {
        // Parse message type
        // Dispatch to appropriate handler
    }
}
```

### 3.5 Phase 5: Configuration Module (Week 5)

#### 3.5.1 Create Configuration Package

```bash
mkdir -p src/main/java/in/annupaper/config
```

#### 3.5.2 Environment Configuration

**Move and Enhance**:
```
util/Env.java → config/Environment.java
```

```java
package in.annupaper.config;

public class Environment {

    // Server config
    public static int getServerPort() {
        return getInt("PORT", 9090);
    }

    // Database config
    public static String getDatabaseUrl() {
        return getRequired("DB_URL");
    }

    public static String getDatabaseUser() {
        return getRequired("DB_USER");
    }

    public static String getDatabasePassword() {
        return getRequired("DB_PASS");
    }

    public static int getDatabasePoolSize() {
        return getInt("DB_POOL_SIZE", 10);
    }

    // Broker config
    public static String getDataFeedMode() {
        return get("DATA_FEED_MODE", "FYERS");  // NEW
    }

    public static String getExecutionBroker() {
        return get("EXECUTION_BROKER", "FYERS");  // NEW
    }

    // JWT config
    public static String getJwtSecret() {
        return getRequired("JWT_SECRET");
    }

    public static int getJwtExpirationHours() {
        return getInt("JWT_EXPIRATION_HOURS", 24);
    }

    // WebSocket config
    public static int getWsBatchFlushMs() {
        return getInt("WS_BATCH_FLUSH_MS", 100);
    }

    // Run mode config
    public static String getRunMode() {
        return get("RUN_MODE", "FULL");
    }

    public static int getRelayPort() {
        return getInt("RELAY_PORT", 7071);
    }

    // Helper methods
    private static String get(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null ? value : defaultValue;
    }

    private static String getRequired(String key) {
        String value = System.getenv(key);
        if (value == null) {
            throw new IllegalStateException("Required environment variable not set: " + key);
        }
        return value;
    }

    private static int getInt(String key, int defaultValue) {
        String value = System.getenv(key);
        return value != null ? Integer.parseInt(value) : defaultValue;
    }
}
```

#### 3.5.3 Application Configuration

**Create New**:

```java
package in.annupaper.config;

public class AppConfig {

    // Candle configuration
    public static final int CANDLE_LOOKBACK_DAYS = 60;
    public static final int CANDLE_MEMORY_LIMIT = 200;  // Per symbol/timeframe

    // Signal configuration
    public static final double SIGNAL_PRICE_THRESHOLD = 0.003;  // 0.3%
    public static final int SIGNAL_EXPIRY_MINUTES = 60;

    // Trade configuration
    public static final int MAX_TRADES_PER_USER = 10;
    public static final int PENDING_ORDER_TIMEOUT_MINUTES = 5;

    // Reconciliation configuration
    public static final int RECONCILIATION_INTERVAL_SECONDS = 30;
    public static final int EXIT_RECONCILIATION_OFFSET_SECONDS = 15;

    // Watchdog configuration
    public static final int WATCHDOG_INTERVAL_MINUTES = 2;
    public static final int STALE_FEED_THRESHOLD_MINUTES = 5;

    // Circuit breaker configuration
    public static final int CIRCUIT_BREAKER_FAILURE_THRESHOLD = 10;
    public static final int CIRCUIT_BREAKER_RESET_MINUTES = 5;

    // Backfill configuration
    public static final int BACKFILL_BATCH_SIZE = 100;
    public static final int BACKFILL_RETRY_ATTEMPTS = 3;
}
```

#### 3.5.4 MTF Configuration

**Move and Enhance**:
```
domain/model/MTFConfig.java → config/MtfConfig.java
service/MtfConfigService.java → application/data/MtfConfigService.java
```

**Create Configuration Loader**:

```java
package in.annupaper.config;

public class MtfConfigLoader {
    private final MtfConfigRepository configRepo;

    /**
     * Load MTF configuration for a symbol (with global fallback)
     */
    public MtfConfiguration load(String symbol) {
        // 1. Try symbol-specific config
        MtfConfig symbolConfig = configRepo.findBySymbol(symbol);
        if (symbolConfig != null) {
            return toConfiguration(symbolConfig);
        }

        // 2. Fallback to global config
        MtfConfig globalConfig = configRepo.findGlobal();
        if (globalConfig != null) {
            return toConfiguration(globalConfig);
        }

        // 3. Use default config
        return getDefaultConfiguration();
    }

    private MtfConfiguration getDefaultConfiguration() {
        return new MtfConfiguration(
            TimeframeType.TF_25MIN,  // HTF
            TimeframeType.TF_125MIN,  // ITF
            TimeframeType.DAILY,  // LTF
            0.02,  // Zone threshold (2%)
            0.015,  // Min profit (1.5%)
            0.025,  // Target (2.5%)
            0.035   // Stretch (3.5%)
        );
    }
}
```

#### 3.5.5 Trailing Stops Configuration

**Move**:
```
config/TrailingStopsConfig.java → config/TrailingStopsConfig.java (already in right place)
service/admin/TrailingStopsConfigService.java → application/trade/TrailingStopsConfigService.java
```

### 3.6 Phase 6: Bootstrap Module (Week 6)

#### 3.6.1 Dependency Injection

**Create DependencyInjector**:

```java
package in.annupaper.bootstrap;

/**
 * Manual dependency injection (no framework)
 * Wires all components together
 */
public class DependencyInjector {

    // Infrastructure
    private DataSource dataSource;
    private DataBrokerFactory dataBrokerFactory;
    private OrderBrokerFactory orderBrokerFactory;
    private WebSocketHub wsHub;
    private EventBus eventBus;

    // Repositories
    private UserRepository userRepo;
    private BrokerRepository brokerRepo;
    private UserBrokerRepository userBrokerRepo;
    private SignalRepository signalRepo;
    private TradeRepository tradeRepo;
    private CandleRepository candleRepo;
    // ... all other repositories

    // Application services
    private AuthService authService;
    private MarketDataService marketDataService;
    private SignalGenerationService signalService;
    private TradeManagementService tradeService;
    private OrderPlacementService orderService;
    // ... all other services

    // Presentation
    private AuthController authController;
    private UserController userController;
    private BrokerController brokerController;
    // ... all other controllers

    public DependencyInjector() {
        wireInfrastructure();
        wireRepositories();
        wireApplicationServices();
        wirePresentation();
    }

    private void wireInfrastructure() {
        // Database
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(Environment.getDatabaseUrl());
        hikariConfig.setUsername(Environment.getDatabaseUser());
        hikariConfig.setPassword(Environment.getDatabasePassword());
        hikariConfig.setMaximumPoolSize(Environment.getDatabasePoolSize());
        dataSource = new HikariDataSource(hikariConfig);

        // Messaging
        eventBus = new EventBus();
        wsHub = new WebSocketHub(Environment.getWsBatchFlushMs());

        // Brokers
        dataBrokerFactory = new DataBrokerFactory(/* dependencies */);
        orderBrokerFactory = new OrderBrokerFactory(/* dependencies */);
    }

    private void wireRepositories() {
        userRepo = new PostgresUserRepository(dataSource);
        brokerRepo = new PostgresBrokerRepository(dataSource);
        userBrokerRepo = new PostgresUserBrokerRepository(dataSource);
        // ... all other repositories
    }

    private void wireApplicationServices() {
        // User services
        JwtService jwtService = new JwtService(
            Environment.getJwtSecret(),
            Environment.getJwtExpirationHours()
        );
        authService = new AuthService(userRepo, jwtService);

        // Data services
        TickCandleBuilder candleBuilder = new TickCandleBuilder(/* dependencies */);
        marketDataService = new MarketDataService(
            dataBrokerFactory,
            candleBuilder,
            /* other dependencies */
        );

        // Signal services
        signalService = new SignalGenerationService(/* dependencies */);

        // Trade services
        tradeService = new TradeManagementService(/* dependencies */);

        // Execution services
        orderService = new OrderPlacementService(
            orderBrokerFactory,  // Uses OrderBroker, not BrokerAdapter
            tradeRepo
        );

        // ... all other services
    }

    private void wirePresentation() {
        authController = new AuthController(authService);
        userController = new UserController(/* dependencies */);
        brokerController = new BrokerController(/* dependencies */);
        // ... all other controllers
    }

    // Getters for all components
    public AuthController getAuthController() { return authController; }
    public UserController getUserController() { return userController; }
    // ... all other getters
}
```

#### 3.6.2 Server Bootstrap

**Create ServerBootstrap**:

```java
package in.annupaper.bootstrap;

public class ServerBootstrap {

    private final DependencyInjector injector;
    private Undertow server;

    public ServerBootstrap(DependencyInjector injector) {
        this.injector = injector;
    }

    public void start() {
        // Build Undertow server
        server = Undertow.builder()
            .addHttpListener(Environment.getServerPort(), "0.0.0.0")
            .setHandler(buildRoutes())
            .build();

        server.start();

        logger.info("Server started on port {}", Environment.getServerPort());
    }

    private HttpHandler buildRoutes() {
        RoutingHandler routes = Handlers.routing();

        // Auth routes (no auth required)
        routes.post("/api/auth/login", injector.getAuthController()::handleLogin);
        routes.post("/api/auth/register", injector.getAuthController()::handleRegister);

        // Protected routes (require JWT)
        AuthMiddleware authMiddleware = new AuthMiddleware(injector.getJwtService());

        routes.get("/api/user/profile",
            wrap(authMiddleware, injector.getUserController()::getProfile));

        routes.get("/api/signals",
            wrap(authMiddleware, injector.getSignalController()::listSignals));

        routes.get("/api/trades",
            wrap(authMiddleware, injector.getTradeController()::listTrades));

        // ... all other routes

        // WebSocket route
        routes.get("/ws", Handlers.websocket(
            injector.getWsConnectionHandler()::handleConnect
        ));

        // Static files
        routes.get("/admin/*", Handlers.resource(
            new ClassPathResourceManager(getClass().getClassLoader(), "static/admin")
        ));

        // Error handler
        return Handlers.exceptionHandler(routes)
            .addExceptionHandler(Throwable.class,
                injector.getErrorHandler()::handleError);
    }

    private HttpHandler wrap(AuthMiddleware auth, HttpHandler handler) {
        return exchange -> {
            auth.authenticate(exchange);
            if (!exchange.isResponseStarted()) {
                handler.handleRequest(exchange);
            }
        };
    }

    public void stop() {
        if (server != null) {
            server.stop();
        }
    }
}
```

#### 3.6.3 Startup Validator

**Move and Enhance**:
```
bootstrap/StartupConfigValidator.java → bootstrap/StartupValidator.java
```

```java
package in.annupaper.bootstrap;

public class StartupValidator {

    private final UserBrokerRepository userBrokerRepo;
    private final BrokerRepository brokerRepo;

    /**
     * P0-A validation: Ensure system is correctly configured before startup
     */
    public void validate() {
        logger.info("Running P0-A startup validation...");

        validateBrokerConfiguration();
        validateDatabaseConnection();
        validateRequiredEnvironmentVariables();

        logger.info("P0-A validation passed");
    }

    private void validateBrokerConfiguration() {
        List<UserBroker> dataBrokers = userBrokerRepo.findByRole(BrokerRole.DATA);
        List<UserBroker> execBrokers = userBrokerRepo.findByRole(BrokerRole.EXEC);

        // Must have exactly one DATA broker
        if (dataBrokers.size() != 1) {
            throw new IllegalStateException(
                "Exactly one DATA broker required, found: " + dataBrokers.size()
            );
        }

        // Must have at least one EXEC broker
        if (execBrokers.isEmpty()) {
            throw new IllegalStateException("At least one EXEC broker required");
        }

        // All production brokers must have LIVE environment
        for (UserBroker ub : dataBrokers) {
            if (ub.getEnvironment() != BrokerEnvironment.LIVE) {
                throw new IllegalStateException(
                    "DATA broker must use LIVE environment"
                );
            }
        }

        logger.info("Broker configuration valid: {} DATA, {} EXEC",
            dataBrokers.size(), execBrokers.size());
    }

    private void validateDatabaseConnection() {
        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement().execute("SELECT 1");
            logger.info("Database connection valid");
        } catch (SQLException e) {
            throw new IllegalStateException("Database connection failed", e);
        }
    }

    private void validateRequiredEnvironmentVariables() {
        List<String> required = List.of(
            "DB_URL", "DB_USER", "DB_PASS", "JWT_SECRET"
        );

        List<String> missing = required.stream()
            .filter(key -> System.getenv(key) == null)
            .toList();

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                "Missing required environment variables: " + missing
            );
        }
    }
}
```

#### 3.6.4 Main Application Class

**Update App.java**:

```java
package in.annupaper.bootstrap;

public class App {

    private static final Logger logger = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        try {
            logger.info("Starting AMZF Trading System v0.4");

            // 1. Wire dependencies
            DependencyInjector injector = new DependencyInjector();

            // 2. Validate configuration (P0-A)
            StartupValidator validator = new StartupValidator(
                injector.getUserBrokerRepository(),
                injector.getBrokerRepository(),
                injector.getDataSource()
            );
            validator.validate();

            // 3. Run database migrations
            MigrationRunner migrations = new MigrationRunner(injector.getDataSource());
            migrations.runMigrations();

            // 4. Connect brokers
            injector.getDataBrokerFactory().connectAllDataBrokers(
                injector.getUserBrokerRepository()
            );
            injector.getOrderBrokerFactory().connectAllOrderBrokers(
                injector.getUserBrokerRepository()
            );

            // 5. Start background services
            startBackgroundServices(injector);

            // 6. Start HTTP server
            ServerBootstrap server = new ServerBootstrap(injector);
            server.start();

            // 7. Register shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down...");
                server.stop();
                injector.getDataSource().close();
            }));

            logger.info("AMZF Trading System started successfully");

        } catch (Exception e) {
            logger.error("Failed to start application", e);
            System.exit(1);
        }
    }

    private static void startBackgroundServices(DependencyInjector injector) {
        // Start signal generator
        injector.getMtfSignalGenerator().start();

        // Start exit signal service
        injector.getExitSignalService().start();

        // Start reconcilers
        injector.getPendingOrderReconciler().start();
        injector.getExitOrderReconciler().start();

        // Start exit order processor
        injector.getExitOrderProcessor().start();

        // Start watchdog
        injector.getWatchdogManager().start();

        // Start token refresh watchdog
        injector.getTokenRefreshWatchdog().start();
    }
}
```

---

## 4. Configuration Management

### 4.1 New Environment Variables

Add these new configuration flags to support multi-broker setup:

| Variable | Description | Default | Example |
|----------|-------------|---------|---------|
| `DATA_FEED_MODE` | Data broker identifier | `FYERS` | `ZERODHA`, `FYERS`, `DHAN` |
| `EXECUTION_BROKER` | Primary order broker | `FYERS` | `FYERS`, `ZERODHA` |
| `MULTI_EXEC_ENABLED` | Allow multiple EXEC brokers | `false` | `true`, `false` |

### 4.2 Configuration Consolidation

**Current State**: Configuration scattered across:
- Environment variables (`util/Env.java`)
- Database (`mtf_config` table)
- File-based (`trailing_stops.json`)
- Hardcoded constants

**Target State**: Centralized in `config/` package with clear layers:
1. **Environment variables** (`config/Environment.java`) - deployment-specific
2. **Application defaults** (`config/AppConfig.java`) - global constants
3. **Database config** (`config/MtfConfigLoader.java`) - runtime-adjustable per symbol
4. **File-based config** (`config/TrailingStopsConfig.java`) - complex structured config

### 4.3 Configuration Priority

**Priority Order** (highest to lowest):
1. Environment variables (deployment)
2. Database overrides (per-symbol config)
3. File-based config (trailing stops)
4. Application defaults (AppConfig constants)

---

## 5. Execution Roadmap

### 5.1 Timeline Overview

| Phase | Duration | Work Items | Risk Level |
|-------|----------|------------|------------|
| Phase 1: Domain Layer | Week 1 | Move domain entities to subdomains | Low |
| Phase 2: Infrastructure | Week 2 | Split broker abstractions, create factories | Medium |
| Phase 3: Application | Week 3 | Reorganize services into modules | Medium |
| Phase 4: Presentation | Week 4 | Split controllers, create DTOs | Low |
| Phase 5: Configuration | Week 5 | Centralize config management | Low |
| Phase 6: Bootstrap | Week 6 | Wire dependencies, update startup | Medium |
| **Buffer** | Week 7 | Testing, bug fixes, documentation | Low |

**Total Duration**: 7 weeks

### 5.2 Detailed Execution Steps

#### Week 1: Domain Layer Migration

**Day 1-2**: User & Broker Subdomains
- [ ] Create `domain/user/` package
- [ ] Move User, UserRole, UserSession, Portfolio
- [ ] Create `domain/broker/` package
- [ ] Move Broker, UserBroker, BrokerRole, BrokerEnvironment
- [ ] Update all imports
- [ ] Run tests

**Day 3-4**: Market & Signal Subdomains
- [ ] Create `domain/market/` package
- [ ] Move Tick, Candle, Instrument, Watchlist, TimeframeType
- [ ] Create `domain/signal/` package
- [ ] Move Signal, ExitSignal, SignalType, ConfluenceType
- [ ] Update all imports
- [ ] Run tests

**Day 5**: Trade & Order Subdomains
- [ ] Create `domain/trade/` package
- [ ] Move Trade, TradeIntent, ExitIntent, Direction, IntentStatus
- [ ] Create `domain/order/` package
- [ ] Create OrderRequest, OrderResponse, OrderStatus models
- [ ] Update all imports
- [ ] Run tests

**Milestone**: All domain entities in organized subdomains, tests passing

#### Week 2: Infrastructure Layer

**Day 1-2**: Broker Interface Split
- [ ] Create `infrastructure/broker/data/` package
- [ ] Define `DataBroker` interface
- [ ] Extract `FyersDataAdapter` from `FyersAdapter`
- [ ] Move `ZerodhaDataAdapter` to new location
- [ ] Create `DataBrokerFactory`
- [ ] Run tests

**Day 3-4**: Order Broker Implementation
- [ ] Create `infrastructure/broker/order/` package
- [ ] Define `OrderBroker` interface
- [ ] Extract `FyersOrderAdapter` from `FyersAdapter`
- [ ] Create `ZerodhaOrderAdapter` (new implementation)
- [ ] Create `OrderBrokerFactory`
- [ ] Run tests

**Day 5**: Persistence & Security
- [ ] Create repository interfaces in `infrastructure/persistence/repository/`
- [ ] Move Postgres implementations to `infrastructure/persistence/postgres/`
- [ ] Move JWT service to `infrastructure/security/`
- [ ] Update all service dependencies
- [ ] Run tests

**Milestone**: Broker abstraction split complete, factories working

#### Week 3: Application Layer

**Day 1**: User & Data Services
- [ ] Create `application/user/` package
- [ ] Move AuthService, UserService, TokenRefreshWatchdog
- [ ] Create `application/data/` package
- [ ] Move all candle services (TickCandleBuilder, CandleStore, etc.)
- [ ] Update imports
- [ ] Run tests

**Day 2**: Signal & Risk Services
- [ ] Create `application/signal/` package
- [ ] Move all signal generation services
- [ ] Create `application/risk/` package
- [ ] Move position sizing, Kelly, ATR calculators
- [ ] Update imports
- [ ] Run tests

**Day 3**: Trade & Execution Services
- [ ] Create `application/trade/` package
- [ ] Move TradeManagementService, ValidationService
- [ ] Create `application/execution/` package
- [ ] Move ExecutionOrchestrator, OrderPlacementService, reconcilers
- [ ] Update to use `OrderBrokerFactory` instead of `BrokerAdapterFactory`
- [ ] Run tests

**Day 4**: Reports & Event Services
- [ ] Create `application/reports/` package
- [ ] Create PerformanceReportService, RiskReportService, HealthMonitoringService
- [ ] Create `application/event/` package
- [ ] Move EventService
- [ ] Run tests

**Day 5**: Integration Testing
- [ ] Run full integration tests
- [ ] Fix any wiring issues
- [ ] Verify signal-to-order flow works end-to-end

**Milestone**: All application services organized, broker split functional

#### Week 4: Presentation Layer

**Day 1-2**: Controller Extraction
- [ ] Create `presentation/http/controllers/` package
- [ ] Extract controllers from `ApiHandlers.java`:
  - AuthController
  - UserController
  - BrokerController
  - SignalController
  - TradeController
  - WatchlistController
- [ ] Update route definitions
- [ ] Run tests

**Day 3**: Admin & Reports Controllers
- [ ] Extract AdminController
- [ ] Extract ReportController
- [ ] Extract HealthController
- [ ] Create middleware (AuthMiddleware, ErrorHandler, RequestLogger)
- [ ] Update route definitions
- [ ] Run tests

**Day 4**: DTOs & WebSocket
- [ ] Create DTO request/response classes
- [ ] Update controllers to use DTOs
- [ ] Create `presentation/websocket/` package
- [ ] Move WebSocket handlers
- [ ] Run tests

**Day 5**: Testing & Documentation
- [ ] Test all API endpoints
- [ ] Update API documentation
- [ ] Test WebSocket connections

**Milestone**: Clean controller layer with DTOs, all endpoints working

#### Week 5: Configuration Module

**Day 1-2**: Environment & App Config
- [ ] Create `config/` package
- [ ] Move `util/Env.java` → `config/Environment.java`
- [ ] Add new environment variables (DATA_FEED_MODE, EXECUTION_BROKER)
- [ ] Create `AppConfig.java` with all constants
- [ ] Update all references
- [ ] Run tests

**Day 3-4**: MTF & Trailing Stops Config
- [ ] Create `MtfConfigLoader.java`
- [ ] Move `MtfConfigService` to `application/data/`
- [ ] Move `TrailingStopsConfigService` to `application/trade/`
- [ ] Update all config loading paths
- [ ] Run tests

**Day 5**: Configuration Testing
- [ ] Test config loading priority
- [ ] Test symbol-specific overrides
- [ ] Document configuration system

**Milestone**: Centralized configuration management

#### Week 6: Bootstrap Module

**Day 1-2**: Dependency Injection
- [ ] Create `DependencyInjector.java`
- [ ] Wire infrastructure components
- [ ] Wire repositories
- [ ] Wire application services
- [ ] Wire presentation controllers
- [ ] Test component creation

**Day 3**: Server Bootstrap
- [ ] Create `ServerBootstrap.java`
- [ ] Define all routes
- [ ] Apply middleware
- [ ] Configure WebSocket
- [ ] Test server startup

**Day 4**: Startup Validator
- [ ] Enhance `StartupValidator.java`
- [ ] Add broker configuration validation
- [ ] Add environment variable validation
- [ ] Test validation failures

**Day 5**: Integration & Cleanup
- [ ] Update `App.java` main class
- [ ] Test full startup sequence
- [ ] Delete old unused files
- [ ] Remove deprecated code

**Milestone**: Clean bootstrap process, all components wired

#### Week 7: Buffer & Polish

**Day 1-2**: Comprehensive Testing
- [ ] Run all unit tests
- [ ] Run all integration tests
- [ ] Test Zerodha data + FYERS orders combination
- [ ] Test failure scenarios (broker disconnects, auth failures, etc.)

**Day 3-4**: Documentation
- [ ] Update README with new architecture
- [ ] Document module responsibilities
- [ ] Create developer guide for new package structure
- [ ] Document configuration options

**Day 5**: Deployment Preparation
- [ ] Update deployment scripts
- [ ] Update environment variable templates
- [ ] Create migration guide for existing users
- [ ] Final smoke tests

**Milestone**: Production-ready refactored system

### 5.3 Testing Strategy

#### 5.3.1 Unit Tests

**Update Test Package Structure**:
```
src/test/java/in/annupaper/
├── domain/
│   ├── user/UserTest.java
│   ├── trade/TradeTest.java
│   └── ...
├── application/
│   ├── data/TickCandleBuilderTest.java
│   ├── signal/MtfSignalGeneratorTest.java
│   └── ...
├── infrastructure/
│   ├── broker/data/FyersDataAdapterTest.java
│   ├── broker/order/OrderBrokerFactoryTest.java
│   └── ...
└── presentation/
    └── http/controllers/AuthControllerTest.java
```

**Testing Approach**:
- Domain entities: Test immutability, validation
- Application services: Mock repositories, test business logic
- Infrastructure: Mock external APIs, test error handling
- Presentation: Mock services, test request/response handling

#### 5.3.2 Integration Tests

**Key Integration Test Scenarios**:

1. **Signal-to-Order Flow**:
   ```
   Tick → TickCandleBuilder → MtfSignalGenerator → SignalManagementService
   → ExecutionOrchestrator → ValidationService → OrderPlacementService
   → OrderBroker → Trade
   ```

2. **Multi-Broker Data + Execution**:
   ```
   ZerodhaDataAdapter (ticks) → TickCandleBuilder → Signal
   → FyersOrderAdapter (order placement)
   ```

3. **Exit Flow**:
   ```
   Tick → ExitSignalService → ExitIntent → ExitQualificationService
   → ExitOrderPlacementService → OrderBroker → Trade (closed)
   ```

4. **OAuth & Token Refresh**:
   ```
   Browser login → OAuth callback → UserBrokerSession
   → TokenRefreshWatchdog → Auto-reload adapters
   ```

**Existing Integration Tests**:
- `/Users/jnani/Desktop/AMZF/amzf/src/test/java/in/annupaper/integration/FullTradeLifecycleIntegrationTest.java`
- `/Users/jnani/Desktop/AMZF/amzf/src/test/java/in/annupaper/integration/ExitOrderFlowIntegrationTest.java`

**Action**: Update these tests to use new package structure and broker abstractions.

#### 5.3.3 Manual Testing Checklist

- [ ] User registration and login
- [ ] Broker linking (FYERS, Zerodha)
- [ ] OAuth flows for both brokers
- [ ] Watchlist creation and management
- [ ] Real-time tick reception (Zerodha WebSocket)
- [ ] Candle building from ticks
- [ ] MTF signal generation
- [ ] Order placement (FYERS API)
- [ ] Order reconciliation
- [ ] Exit signal generation
- [ ] Exit order placement
- [ ] Trade P&L calculation
- [ ] WebSocket events delivery
- [ ] Admin configuration updates
- [ ] Health monitoring dashboard

### 5.4 Rollback Plan

**If refactoring fails**, rollback strategy:

1. **Git Branch Strategy**:
   ```bash
   # Create feature branch before starting
   git checkout -b refactor/clean-architecture

   # Keep main branch stable
   # Commit after each phase
   git commit -m "Phase 1: Domain layer migration"

   # If issues arise, revert specific commits
   git revert <commit-hash>
   ```

2. **Database Migrations**:
   - All new migrations are ADDITIVE only (no drops)
   - Can rollback by reverting code without DB changes
   - If schema changes needed, create DOWN migrations

3. **Feature Flags**:
   ```java
   // Use feature flag during transition
   if (Environment.get("USE_NEW_BROKER_ABSTRACTION", "false").equals("true")) {
       // New OrderBroker implementation
   } else {
       // Old BrokerAdapter implementation
   }
   ```

4. **Canary Deployment**:
   - Deploy refactored code to staging first
   - Run for 1 week with production load
   - Monitor for errors, performance issues
   - Only promote to production after validation

---

## 6. Risk Mitigation

### 6.1 Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Breaking existing functionality | Medium | High | Comprehensive test suite, gradual migration |
| Performance degradation | Low | Medium | Benchmark before/after, profile hot paths |
| Import/dependency errors | High | Low | IDE refactoring tools, incremental testing |
| Configuration mistakes | Medium | High | Validation on startup (P0-A), staging deployment |
| Data loss during migration | Low | Critical | No database changes, backup before deployment |
| Broker integration issues | Medium | High | Thorough testing with both Zerodha and FYERS |
| Production downtime | Low | Critical | Canary deployment, rollback plan ready |

### 6.2 Mitigation Strategies

#### 6.2.1 Comprehensive Testing

- **Unit tests**: 80%+ coverage for business logic
- **Integration tests**: Cover all major flows
- **Manual testing**: Full regression test before production
- **Staging environment**: Run refactored code for 1 week before prod

#### 6.2.2 Incremental Migration

- Migrate one module at a time
- Commit after each phase
- Tests must pass before moving to next phase
- Never have broken code in main branch

#### 6.2.3 Monitoring & Observability

**Add Metrics During Refactoring**:
```java
// Track broker adapter usage
metrics.increment("broker.data.ticks_received", tags("broker", "zerodha"));
metrics.increment("broker.order.orders_placed", tags("broker", "fyers"));

// Track refactored service performance
metrics.timer("service.order_placement.duration", duration);
```

**Log Key Events**:
```java
logger.info("Using DataBroker: {} for symbol: {}",
    dataBroker.getBrokerId(), symbol);
logger.info("Placing order via OrderBroker: {} for intent: {}",
    orderBroker.getBrokerId(), intentId);
```

#### 6.2.4 Backward Compatibility

**During transition, support both old and new patterns**:
```java
// Example: Support both BrokerAdapter and OrderBroker
@Deprecated
public void placeOrderLegacy(BrokerAdapter adapter, TradeIntent intent) {
    // Old implementation
}

public void placeOrder(OrderBroker broker, TradeIntent intent) {
    // New implementation
}
```

**Remove deprecated methods only after validation**.

#### 6.2.5 Documentation

- Update docs alongside code changes
- Document migration steps for other developers
- Create architecture decision records (ADRs) for major changes

---

## 7. Success Criteria

### 7.1 Functional Requirements

✅ **Must Have**:
- [ ] All existing features work identically
- [ ] Zerodha data + FYERS orders combination works
- [ ] Signal-to-order flow unchanged (same latency)
- [ ] All existing API endpoints work
- [ ] All integration tests pass
- [ ] No data loss or corruption

✅ **Should Have**:
- [ ] Improved code organization (subjective, but measurable by team feedback)
- [ ] Clearer separation of concerns
- [ ] Easier to add new brokers
- [ ] Better test coverage (>80%)

✅ **Nice to Have**:
- [ ] Performance improvements (faster startup, lower latency)
- [ ] Better logging and observability
- [ ] Improved error messages

### 7.2 Non-Functional Requirements

**Performance**:
- Tick-to-signal latency: <500ms (same as before)
- Order placement latency: <1s (same as before)
- Startup time: <30s (same as before)

**Reliability**:
- Zero data loss
- Zero unplanned downtime
- All reconciliation loops functional

**Maintainability**:
- Code duplication <5%
- Cyclomatic complexity <10 per method
- Clear dependency graph (no cycles)

### 7.3 Acceptance Criteria

**Definition of Done for Refactoring**:
1. All unit tests pass (100%)
2. All integration tests pass (100%)
3. Manual testing checklist completed
4. Code review completed (by 2+ developers)
5. Documentation updated
6. Staging deployment successful (1 week observation)
7. Production deployment successful (no rollback needed)
8. Zero critical bugs in first 2 weeks after deployment

---

## 8. Appendices

### 8.1 File Count Summary

**Before Refactoring**:
- Total files: ~150 Java files
- Packages: 11 top-level packages
- Average files per package: ~13

**After Refactoring**:
- Total files: ~150 Java files (same, just reorganized)
- Packages: 40+ packages (more granular)
- Average files per package: ~4 (better cohesion)

### 8.2 Key Interfaces Summary

**New Interfaces Created**:
1. `DataBroker` - Market data feed abstraction
2. `OrderBroker` - Order execution abstraction
3. `UserRepository` (and all other repository interfaces)
4. `DataFeedHealth` - Health status for data brokers
5. `OrderRequest` / `OrderResponse` - Domain models for orders

**Deleted Interfaces**:
1. `BrokerAdapter` - Replaced by DataBroker + OrderBroker split

### 8.3 Configuration Variables Reference

| Variable | Current | New | Description |
|----------|---------|-----|-------------|
| `PORT` | ✅ | ✅ | HTTP server port |
| `DB_URL` | ✅ | ✅ | PostgreSQL connection URL |
| `DB_USER` | ✅ | ✅ | Database username |
| `DB_PASS` | ✅ | ✅ | Database password |
| `DB_POOL_SIZE` | ✅ | ✅ | HikariCP pool size |
| `JWT_SECRET` | ✅ | ✅ | JWT signing key |
| `JWT_EXPIRATION_HOURS` | ✅ | ✅ | Token expiration |
| `WS_BATCH_FLUSH_MS` | ✅ | ✅ | WebSocket batch interval |
| `RUN_MODE` | ✅ | ✅ | FULL \| FEED_COLLECTOR |
| `RELAY_PORT` | ✅ | ✅ | Relay server port |
| `DATA_FEED_MODE` | ❌ | ✅ | **NEW**: FYERS \| ZERODHA \| DHAN |
| `EXECUTION_BROKER` | ❌ | ✅ | **NEW**: FYERS \| ZERODHA |
| `MULTI_EXEC_ENABLED` | ❌ | ✅ | **NEW**: true \| false |

### 8.4 Migration Checklist

**Pre-Migration**:
- [ ] Backup production database
- [ ] Document current system state
- [ ] Create refactoring branch
- [ ] Set up staging environment

**During Migration**:
- [ ] Follow week-by-week plan
- [ ] Run tests after each phase
- [ ] Commit frequently
- [ ] Update documentation incrementally

**Post-Migration**:
- [ ] Deploy to staging
- [ ] Run for 1 week
- [ ] Monitor metrics
- [ ] Deploy to production
- [ ] Monitor for 2 weeks
- [ ] Collect team feedback

### 8.5 Contact & Support

**For Questions During Refactoring**:
- Technical Lead: [Name]
- Architecture Review: [Name]
- DevOps Support: [Name]

**Documentation**:
- Architecture Diagrams: `/docs/architecture/`
- Migration Guide: `/docs/MIGRATION.md` (to be created)
- API Documentation: `/docs/API.md`

---

## Conclusion

This refactoring plan provides a systematic approach to restructuring the AMZF trading system into a clean, maintainable architecture with separated concerns. By following this plan:

1. **Data and order execution are decoupled**, enabling flexible broker combinations
2. **Domain logic is isolated** from infrastructure concerns
3. **Application services are organized** by business capability
4. **Presentation layer is clean** with proper controllers and DTOs
5. **Configuration is centralized** and easy to manage

The 7-week timeline includes buffer for testing and unexpected issues. The incremental approach minimizes risk, with each phase building on the previous one.

**Next Steps**:
1. Review this plan with the team
2. Get approval from stakeholders
3. Set up staging environment
4. Create refactoring branch
5. Begin Phase 1 (Domain Layer) migration

**Success** will be measured by:
- All existing functionality preserved
- Zerodha data + FYERS orders working
- Cleaner codebase that's easier to extend
- Zero production incidents during/after migration
