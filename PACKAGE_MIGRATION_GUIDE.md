# Package Migration Guide - AMZF Trading System

**Purpose**: Detailed file-by-file migration instructions with automation scripts

---

## Table of Contents

1. [Migration Matrix](#1-migration-matrix)
2. [Automated Migration Scripts](#2-automated-migration-scripts)
3. [Manual Steps](#3-manual-steps)
4. [Validation Checklist](#4-validation-checklist)

---

## 1. Migration Matrix

### 1.1 Domain Layer Files

| Current Location | Target Location | Dependencies to Update |
|------------------|-----------------|------------------------|
| `domain/model/User.java` | `domain/user/User.java` | AuthService, UserService, repositories |
| `domain/model/UserRole.java` | `domain/user/UserRole.java` | AuthService, User |
| `domain/model/UserSession.java` | `domain/user/UserSession.java` | JwtService, AuthService |
| `domain/model/Portfolio.java` | `domain/user/Portfolio.java` | PortfolioService, TradeManagementService |
| `domain/model/Broker.java` | `domain/broker/Broker.java` | BrokerRepository, UserBroker |
| `domain/model/UserBroker.java` | `domain/broker/UserBroker.java` | All broker services, adapters |
| `domain/model/UserBrokerSession.java` | `domain/broker/UserBrokerSession.java` | OAuth services, adapters |
| `domain/enums/BrokerRole.java` | `domain/broker/BrokerRole.java` | UserBroker, factories |
| `domain/enums/BrokerEnvironment.java` | `domain/broker/BrokerEnvironment.java` | UserBroker, adapters |
| `domain/constants/BrokerIds.java` | `domain/broker/BrokerIds.java` | All broker factories |
| `domain/model/Candle.java` | `domain/market/Candle.java` | All candle services, MTF services |
| `broker/adapters/Tick.java` | `domain/market/Tick.java` | TickListener, TickCandleBuilder |
| `domain/model/Watchlist.java` | `domain/market/Watchlist.java` | WatchlistService, repositories |
| `domain/model/WatchlistTemplate.java` | `domain/market/WatchlistTemplate.java` | WatchlistService, repositories |
| `domain/model/WatchlistSelected.java` | `domain/market/WatchlistSelected.java` | WatchlistService, repositories |
| `domain/enums/TimeframeType.java` | `domain/market/TimeframeType.java` | All candle/MTF services |
| `domain/model/Signal.java` | `domain/signal/Signal.java` | SignalService, repositories |
| `domain/model/ExitSignal.java` | `domain/signal/ExitSignal.java` | ExitSignalService, repositories |
| `domain/enums/SignalType.java` | `domain/signal/SignalType.java` | Signal, SignalService |
| `domain/enums/ConfluenceType.java` | `domain/signal/ConfluenceType.java` | Signal, ConfluenceCalculator |
| `domain/enums/ExitReason.java` | `domain/signal/ExitReason.java` | ExitSignal, ExitSignalService |
| `domain/model/Trade.java` | `domain/trade/Trade.java` | TradeManagementService, repositories |
| `domain/model/TradeIntent.java` | `domain/trade/TradeIntent.java` | ExecutionOrchestrator, OrderService |
| `domain/model/ExitIntent.java` | `domain/trade/ExitIntent.java` | ExitOrderService, repositories |
| `domain/model/TradeEvent.java` | `domain/trade/TradeEvent.java` | TradeManagementService, repositories |
| `domain/enums/Direction.java` | `domain/trade/Direction.java` | Trade, TradeIntent, Signal |
| `domain/enums/IntentStatus.java` | `domain/trade/IntentStatus.java` | TradeIntent, services |
| `domain/enums/ExitIntentStatus.java` | `domain/trade/ExitIntentStatus.java` | ExitIntent, services |
| `domain/model/ValidationResult.java` | `domain/common/ValidationResult.java` | ValidationService |
| `domain/enums/ValidationErrorCode.java` | `domain/common/ValidationErrorCode.java` | ValidationService |
| `domain/enums/EventType.java` | `domain/common/EventType.java` | EventService |
| `domain/model/OAuthState.java` | `domain/broker/OAuthState.java` | OAuth services |
| `domain/model/MTFConfig.java` | `domain/signal/MtfConfig.java` | MtfConfigService |

**New Files to Create**:
- `domain/order/OrderRequest.java` - Extract from current order placement logic
- `domain/order/OrderResponse.java` - Extract from broker responses
- `domain/order/OrderStatus.java` - Enum for order states
- `domain/order/OrderType.java` - MARKET | LIMIT | STOP_LOSS
- `domain/order/TimeInForce.java` - DAY | IOC | GTC
- `domain/trade/Position.java` - Extract from trade/broker logic

### 1.2 Infrastructure Layer Files

#### 1.2.1 Broker Adapters

| Current Location | Target Location | Notes |
|------------------|-----------------|-------|
| `broker/BrokerAdapter.java` | **DELETE** | Split into DataBroker + OrderBroker |
| `broker/BrokerAdapterFactory.java` | **DELETE** | Replace with separate factories |
| `broker/adapters/FyersAdapter.java` | **SPLIT** → `infrastructure/broker/data/FyersDataAdapter.java` + `infrastructure/broker/order/FyersOrderAdapter.java` | Extract data vs order methods |
| `broker/adapters/ZerodhaDataAdapter.java` | `infrastructure/broker/data/ZerodhaDataAdapter.java` | Already implements data-only pattern |
| `broker/adapters/ZerodhaAdapter.java` | **DELETE** | Stub, replaced by ZerodhaDataAdapter |
| `broker/adapters/DhanAdapter.java` | `infrastructure/broker/data/DhanDataAdapter.java` + `infrastructure/broker/order/DhanOrderAdapter.java` | Split when implementing |
| `broker/adapters/UpstoxAdapter.java` | `infrastructure/broker/data/UpstoxDataAdapter.java` + `infrastructure/broker/order/UpstoxOrderAdapter.java` | Split when implementing |
| `broker/adapters/AlpacaAdapter.java` | **DELETE** | Not used |
| `broker/adapters/RelayWebSocketAdapter.java` | `infrastructure/broker/data/RelayDataAdapter.java` | Data-only adapter |
| `broker/adapters/FyersV3SdkAdapter.java` | **DELETE** | Merge into FyersDataAdapter |

**New Files to Create**:
- `infrastructure/broker/data/DataBroker.java` - Interface
- `infrastructure/broker/data/DataBrokerFactory.java` - Factory
- `infrastructure/broker/data/DataFeedHealth.java` - Health status model
- `infrastructure/broker/order/OrderBroker.java` - Interface
- `infrastructure/broker/order/OrderBrokerFactory.java` - Factory
- `infrastructure/broker/order/ZerodhaOrderAdapter.java` - **NEW implementation**
- `infrastructure/broker/order/DummyOrderAdapter.java` - Move from current location

#### 1.2.2 OAuth Services

| Current Location | Target Location | Notes |
|------------------|-----------------|-------|
| `service/oauth/BrokerOAuthService.java` | `infrastructure/broker/auth/OAuthService.java` | Rename + move |
| `service/oauth/FyersLoginOrchestrator.java` | `infrastructure/broker/auth/FyersOAuthHandler.java` | Rename + move |

**New Files to Create**:
- `infrastructure/broker/auth/ZerodhaOAuthHandler.java` - Handle Zerodha OAuth flow

#### 1.2.3 Persistence Layer

| Current Location | Target Location | Notes |
|------------------|-----------------|-------|
| `repository/UserRepository.java` | **SPLIT** → `infrastructure/persistence/repository/UserRepository.java` (interface) + `infrastructure/persistence/postgres/PostgresUserRepository.java` | Extract interface |
| `repository/PostgresUserRepository.java` | `infrastructure/persistence/postgres/PostgresUserRepository.java` | Implementation |
| `repository/BrokerRepository.java` | **SPLIT** (same pattern) | Extract interface |
| `repository/UserBrokerRepository.java` | **SPLIT** (same pattern) | Extract interface |
| `repository/UserBrokerSessionRepository.java` | **SPLIT** (same pattern) | Extract interface |
| `repository/PortfolioRepository.java` | **SPLIT** (same pattern) | Extract interface |
| `repository/WatchlistRepository.java` | **SPLIT** (same pattern) | Extract interface |
| `repository/WatchlistTemplateRepository.java` | **SPLIT** (same pattern) | Extract interface |
| `repository/WatchlistSelectedRepository.java` | **SPLIT** (same pattern) | Extract interface |
| `repository/SignalRepository.java` | **SPLIT** (same pattern) | Extract interface |
| `repository/SignalDeliveryRepository.java` | **SPLIT** (same pattern) | Extract interface |
| `repository/TradeIntentRepository.java` | **SPLIT** (same pattern) | Extract interface |
| `repository/TradeRepository.java` | **SPLIT** (same pattern) | Extract interface |
| `repository/TradeEventRepository.java` | **SPLIT** (same pattern) | Extract interface |
| `repository/ExitSignalRepository.java` | **SPLIT** (same pattern) | Extract interface |
| `repository/ExitIntentRepository.java` | **SPLIT** (same pattern) | Extract interface |
| `repository/CandleRepository.java` | **SPLIT** (same pattern) | Extract interface |
| `repository/InstrumentRepository.java` | **SPLIT** (same pattern) | Extract interface |
| `repository/MtfConfigRepository.java` | **SPLIT** (same pattern) | Extract interface |
| `repository/OAuthStateRepository.java` | **SPLIT** (same pattern) | Extract interface |

**Pattern for All Repositories**:
1. Create interface at `infrastructure/persistence/repository/{Name}Repository.java`
2. Move implementation to `infrastructure/persistence/postgres/Postgres{Name}Repository.java`
3. Update all services to depend on interface, not implementation

#### 1.2.4 Security

| Current Location | Target Location | Notes |
|------------------|-----------------|-------|
| `auth/JwtService.java` | `infrastructure/security/JwtService.java` | Move |

**New Files to Create**:
- `infrastructure/security/PasswordHasher.java` - Extract BCrypt logic from AuthService
- `infrastructure/security/JwtAuthFilter.java` - Undertow filter for JWT validation

#### 1.2.5 Messaging

| Current Location | Target Location | Notes |
|------------------|-----------------|-------|
| `transport/ws/WsHub.java` | `infrastructure/messaging/WebSocketHub.java` | Rename + move |
| `service/core/EventService.java` | **SPLIT** → `infrastructure/messaging/EventBus.java` + `application/event/EventService.java` | Split pub/sub from business logic |

#### 1.2.6 Feed Relay

| Current Location | Target Location | Notes |
|------------------|-----------------|-------|
| `feedrelay/TickRelayServer.java` | `infrastructure/relay/TickRelayServer.java` | Move |
| `feedrelay/RelayBroadcastTickListener.java` | `infrastructure/relay/RelayBroadcastTickListener.java` | Move |
| `feedrelay/TickJsonMapper.java` | `infrastructure/relay/TickJsonMapper.java` | Move |

### 1.3 Application Layer Files

#### 1.3.1 User Services

| Current Location | Target Location | Notes |
|------------------|-----------------|-------|
| `auth/AuthService.java` | `application/user/AuthService.java` | Move |
| `service/TokenRefreshWatchdog.java` | `application/user/TokenRefreshWatchdog.java` | Move |

**New Files to Create**:
- `application/user/UserService.java` - User CRUD operations
- `application/user/PortfolioService.java` - Portfolio management

#### 1.3.2 Data Services

| Current Location | Target Location | Notes |
|------------------|-----------------|-------|
| `service/candle/TickCandleBuilder.java` | `application/data/TickCandleBuilder.java` | Move |
| `service/candle/CandleStore.java` | `application/data/CandleStore.java` | Move |
| `service/candle/CandleAggregator.java` | `application/data/CandleAggregator.java` | Move |
| `service/candle/CandleFetcher.java` | `application/data/CandleFetcher.java` | Move |
| `service/candle/CandleReconciler.java` | `application/data/CandleReconciler.java` | Move |
| `service/candle/HistoryBackfiller.java` | `application/data/HistoryBackfillService.java` | Rename + move |
| `service/candle/MtfBackfillService.java` | `application/data/MtfBackfillService.java` | Move |
| `service/candle/RecoveryManager.java` | `application/data/RecoveryManager.java` | Move |
| `service/candle/SessionClock.java` | `application/data/SessionClock.java` | Move |
| `service/InstrumentService.java` | `application/data/InstrumentService.java` | Move |
| `service/MarketDataCache.java` | `application/data/MarketDataCache.java` | Move |

**New Files to Create**:
- `application/data/MarketDataService.java` - Coordinate data subscriptions
- `application/data/CandleService.java` - Facade for candle operations
- `application/data/WatchlistService.java` - Watchlist CRUD

#### 1.3.3 Signal Services

| Current Location | Target Location | Notes |
|------------------|-----------------|-------|
| `service/signal/SignalService.java` | `application/signal/SignalGenerationService.java` | Rename + move |
| `service/signal/MtfSignalGenerator.java` | `application/signal/MtfSignalGenerator.java` | Move |
| `service/signal/SignalManagementService.java` | `application/signal/SignalManagementService.java` | Move |
| `service/signal/SignalManagementServiceImpl.java` | `application/signal/SignalManagementServiceImpl.java` | Move |
| `service/signal/EntrySignalCoordinator.java` | `application/signal/EntrySignalCoordinator.java` | Move |
| `service/signal/ExitSignalCoordinator.java` | `application/signal/ExitSignalCoordinator.java` | Move |
| `service/signal/ExitSignalService.java` | `application/signal/ExitSignalService.java` | Move |
| `service/signal/ConfluenceCalculator.java` | `application/signal/ConfluenceCalculator.java` | Move |
| `service/signal/ZoneDetector.java` | `application/signal/ZoneDetector.java` | Move |
| `service/signal/BrickMovementTracker.java` | `application/signal/BrickMovementTracker.java` | Move |

#### 1.3.4 Risk Services (Extract from Signal)

| Current Location | Target Location | Notes |
|------------------|-----------------|-------|
| `service/signal/PositionSizingService.java` | `application/risk/PositionSizingService.java` | Move |
| `service/signal/MtfPositionSizer.java` | `application/risk/MtfPositionSizer.java` | Move |
| `service/signal/PortfolioRiskCalculator.java` | `application/risk/PortfolioRiskCalculator.java` | Move |
| `service/signal/ATRCalculator.java` | `application/risk/ATRCalculator.java` | Move |
| `service/signal/KellyCalculator.java` | `application/risk/KellyCalculator.java` | Move |
| `service/signal/LogUtilityCalculator.java` | `application/risk/LogUtilityCalculator.java` | Move |
| `service/signal/UtilityAsymmetryCalculator.java` | `application/risk/UtilityAsymmetryCalculator.java` | Move |
| `service/signal/MaxDropCalculator.java` | `application/risk/MaxDropCalculator.java` | Move |
| `service/signal/VelocityCalculator.java` | `application/risk/VelocityCalculator.java` | Move |
| `service/signal/DistributionAnalyzer.java` | `application/risk/DistributionAnalyzer.java` | Move |
| `service/signal/AveragingGateValidator.java` | `application/risk/AveragingGateValidator.java` | Move |

#### 1.3.5 Trade Services

| Current Location | Target Location | Notes |
|------------------|-----------------|-------|
| `service/trade/TradeManagementService.java` | `application/trade/TradeManagementService.java` | Move |
| `service/trade/TradeCoordinator.java` | `application/trade/TradeCoordinator.java` | Move |
| `service/trade/ActiveTradeIndex.java` | `application/trade/ActiveTradeIndex.java` | Move |
| `service/trade/TradeClassifier.java` | `application/trade/TradeClassifier.java` | Move |
| `service/validation/ValidationService.java` | `application/trade/ValidationService.java` | Move |
| `service/validation/ExitQualificationService.java` | `application/trade/ExitQualificationService.java` | Move |
| `service/admin/TrailingStopsConfigService.java` | `application/trade/TrailingStopsConfigService.java` | Move |

#### 1.3.6 Execution Services

| Current Location | Target Location | Notes |
|------------------|-----------------|-------|
| `service/execution/ExecutionOrchestrator.java` | `application/execution/ExecutionOrchestrator.java` | Move |
| `service/execution/OrderExecutionService.java` | `application/execution/OrderPlacementService.java` | Rename + move |
| `service/execution/ExitOrderExecutionService.java` | `application/execution/ExitOrderPlacementService.java` | Rename + move |
| `service/execution/PendingOrderReconciler.java` | `application/execution/PendingOrderReconciler.java` | Move |
| `service/execution/ExitOrderProcessor.java` | `application/execution/ExitOrderProcessor.java` | Move |
| `service/execution/ExitOrderReconciler.java` | `application/execution/ExitOrderReconciler.java` | Move |

#### 1.3.7 Report Services (New)

**New Files to Create**:
- `application/reports/PerformanceReportService.java` - Daily/weekly/monthly reports
- `application/reports/RiskReportService.java` - Risk metrics
- `application/reports/HealthMonitoringService.java` - System health
- `application/reports/TradeAnalyticsService.java` - Trade analytics

#### 1.3.8 Event Services

| Current Location | Target Location | Notes |
|------------------|-----------------|-------|
| `service/core/EventService.java` | `application/event/EventService.java` | Move (business logic only) |

#### 1.3.9 Admin Services

| Current Location | Target Location | Notes |
|------------------|-----------------|-------|
| `service/admin/AdminService.java` | `application/admin/AdminService.java` | Move |

#### 1.3.10 MTF Services

| Current Location | Target Location | Notes |
|------------------|-----------------|-------|
| `service/mtf/MtfAnalysisService.java` | `application/signal/MtfAnalysisService.java` | Move (belongs to signal domain) |
| `service/MtfConfigService.java` | `application/data/MtfConfigService.java` | Move (config management) |

#### 1.3.11 Watchdog

| Current Location | Target Location | Notes |
|------------------|-----------------|-------|
| `service/WatchdogManager.java` | `application/monitoring/WatchdogManager.java` | Move |

### 1.4 Presentation Layer Files

#### 1.4.1 HTTP Controllers (Extract from ApiHandlers)

| Current Location | Target Location | Notes |
|------------------|-----------------|-------|
| `transport/http/ApiHandlers.java` | **SPLIT into multiple controllers** | Extract all endpoints |
| → Login/Register endpoints | `presentation/http/controllers/AuthController.java` | Extract |
| → User/Profile endpoints | `presentation/http/controllers/UserController.java` | Extract |
| → Broker endpoints | `presentation/http/controllers/BrokerController.java` | Extract |
| → Signal endpoints | `presentation/http/controllers/SignalController.java` | Extract |
| → Trade endpoints | `presentation/http/controllers/TradeController.java` | Extract |
| → Watchlist endpoints | `presentation/http/controllers/WatchlistController.java` | Extract |
| → Admin endpoints | `presentation/http/controllers/AdminController.java` | Extract |
| → Report endpoints | `presentation/http/controllers/ReportController.java` | Extract |
| → Health endpoints | `presentation/http/controllers/HealthController.java` | Extract |

| Current Location | Target Location | Notes |
|------------------|-----------------|-------|
| `transport/http/AdminConfigHandler.java` | `presentation/http/controllers/AdminController.java` | Merge into AdminController |
| `transport/http/MtfConfigHandler.java` | `presentation/http/controllers/MtfConfigController.java` | Rename + move |
| `transport/http/MonitoringHandler.java` | `presentation/http/controllers/MonitoringController.java` | Rename + move |

**New Files to Create**:
- DTOs for all request/response types in `presentation/http/dto/{request,response}/`
- Middleware: `AuthMiddleware.java`, `ErrorHandler.java`, `RequestLogger.java`

#### 1.4.2 WebSocket

| Current Location | Target Location | Notes |
|------------------|-----------------|-------|
| `transport/ws/WsHub.java` | `presentation/websocket/WsConnectionHandler.java` | Rename + refactor |

**New Files to Create**:
- `presentation/websocket/WsMessageRouter.java` - Route incoming messages

### 1.5 Configuration Files

| Current Location | Target Location | Notes |
|------------------|-----------------|-------|
| `util/Env.java` | `config/Environment.java` | Move + add new vars |
| `config/TrailingStopsConfig.java` | `config/TrailingStopsConfig.java` | Keep in place |

**New Files to Create**:
- `config/AppConfig.java` - Application-wide constants
- `config/MtfConfigLoader.java` - Load MTF config with fallbacks
- `config/ConfigLoader.java` - Generic config loader

### 1.6 Bootstrap Files

| Current Location | Target Location | Notes |
|------------------|-----------------|-------|
| `bootstrap/App.java` | `bootstrap/App.java` | Update with new wiring |
| `bootstrap/StartupConfigValidator.java` | `bootstrap/StartupValidator.java` | Rename + enhance |
| `bootstrap/P0DebtRegistry.java` | **DELETE** or move to docs | Technical debt tracker |

**New Files to Create**:
- `bootstrap/DependencyInjector.java` - **CRITICAL**: Wire all components
- `bootstrap/ServerBootstrap.java` - Undertow server setup

---

## 2. Automated Migration Scripts

### 2.1 Package Creation Script

```bash
#!/bin/bash
# create_package_structure.sh
# Creates all new package directories

BASE_DIR="src/main/java/in/annupaper"

# Domain packages
mkdir -p ${BASE_DIR}/domain/{user,broker,market,signal,trade,order,common}

# Infrastructure packages
mkdir -p ${BASE_DIR}/infrastructure/broker/{data,order,auth}
mkdir -p ${BASE_DIR}/infrastructure/persistence/{repository,postgres}
mkdir -p ${BASE_DIR}/infrastructure/{security,messaging,relay,config}

# Application packages
mkdir -p ${BASE_DIR}/application/{user,data,signal,risk,trade,execution,reports,event,admin,monitoring}

# Presentation packages
mkdir -p ${BASE_DIR}/presentation/http/{controllers,dto,middleware}
mkdir -p ${BASE_DIR}/presentation/http/dto/{request,response}
mkdir -p ${BASE_DIR}/presentation/websocket
mkdir -p ${BASE_DIR}/presentation/ui

# Config packages
mkdir -p ${BASE_DIR}/config

# Bootstrap packages (already exists, but ensure)
mkdir -p ${BASE_DIR}/bootstrap

echo "✅ Package structure created"
```

### 2.2 File Move Script (Domain Layer Example)

```bash
#!/bin/bash
# migrate_domain_layer.sh
# Moves domain files and updates package declarations

BASE_DIR="src/main/java/in/annupaper"

# Function to move file and update package
move_and_update() {
    local src=$1
    local dest=$2
    local old_package=$3
    local new_package=$4

    echo "Moving $src → $dest"

    # Copy file
    cp ${BASE_DIR}/${src} ${BASE_DIR}/${dest}

    # Update package declaration in destination
    sed -i '' "s/package ${old_package};/package ${new_package};/" ${BASE_DIR}/${dest}

    echo "✅ Moved and updated package: $dest"
}

# Move User subdomain
move_and_update "domain/model/User.java" "domain/user/User.java" "in.annupaper.domain.model" "in.annupaper.domain.user"
move_and_update "domain/model/UserRole.java" "domain/user/UserRole.java" "in.annupaper.domain.model" "in.annupaper.domain.user"
move_and_update "domain/model/UserSession.java" "domain/user/UserSession.java" "in.annupaper.domain.model" "in.annupaper.domain.user"
move_and_update "domain/model/Portfolio.java" "domain/user/Portfolio.java" "in.annupaper.domain.model" "in.annupaper.domain.user"

# Move Broker subdomain
move_and_update "domain/model/Broker.java" "domain/broker/Broker.java" "in.annupaper.domain.model" "in.annupaper.domain.broker"
move_and_update "domain/model/UserBroker.java" "domain/broker/UserBroker.java" "in.annupaper.domain.model" "in.annupaper.domain.broker"
move_and_update "domain/model/UserBrokerSession.java" "domain/broker/UserBrokerSession.java" "in.annupaper.domain.model" "in.annupaper.domain.broker"
move_and_update "domain/enums/BrokerRole.java" "domain/broker/BrokerRole.java" "in.annupaper.domain.enums" "in.annupaper.domain.broker"
move_and_update "domain/enums/BrokerEnvironment.java" "domain/broker/BrokerEnvironment.java" "in.annupaper.domain.enums" "in.annupaper.domain.broker"
move_and_update "domain/constants/BrokerIds.java" "domain/broker/BrokerIds.java" "in.annupaper.domain.constants" "in.annupaper.domain.broker"

# Move Market subdomain
move_and_update "domain/model/Candle.java" "domain/market/Candle.java" "in.annupaper.domain.model" "in.annupaper.domain.market"
move_and_update "domain/enums/TimeframeType.java" "domain/market/TimeframeType.java" "in.annupaper.domain.enums" "in.annupaper.domain.market"

# Move Signal subdomain
move_and_update "domain/model/Signal.java" "domain/signal/Signal.java" "in.annupaper.domain.model" "in.annupaper.domain.signal"
move_and_update "domain/model/ExitSignal.java" "domain/signal/ExitSignal.java" "in.annupaper.domain.model" "in.annupaper.domain.signal"
move_and_update "domain/enums/SignalType.java" "domain/signal/SignalType.java" "in.annupaper.domain.enums" "in.annupaper.domain.signal"
move_and_update "domain/enums/ConfluenceType.java" "domain/signal/ConfluenceType.java" "in.annupaper.domain.enums" "in.annupaper.domain.signal"
move_and_update "domain/enums/ExitReason.java" "domain/signal/ExitReason.java" "in.annupaper.domain.enums" "in.annupaper.domain.signal"

# Move Trade subdomain
move_and_update "domain/model/Trade.java" "domain/trade/Trade.java" "in.annupaper.domain.model" "in.annupaper.domain.trade"
move_and_update "domain/model/TradeIntent.java" "domain/trade/TradeIntent.java" "in.annupaper.domain.model" "in.annupaper.domain.trade"
move_and_update "domain/model/ExitIntent.java" "domain/trade/ExitIntent.java" "in.annupaper.domain.model" "in.annupaper.domain.trade"
move_and_update "domain/model/TradeEvent.java" "domain/trade/TradeEvent.java" "in.annupaper.domain.model" "in.annupaper.domain.trade"
move_and_update "domain/enums/Direction.java" "domain/trade/Direction.java" "in.annupaper.domain.enums" "in.annupaper.domain.trade"
move_and_update "domain/enums/IntentStatus.java" "domain/trade/IntentStatus.java" "in.annupaper.domain.enums" "in.annupaper.domain.trade"
move_and_update "domain/enums/ExitIntentStatus.java" "domain/trade/ExitIntentStatus.java" "in.annupaper.domain.enums" "in.annupaper.domain.trade"

# Move Common subdomain
move_and_update "domain/model/ValidationResult.java" "domain/common/ValidationResult.java" "in.annupaper.domain.model" "in.annupaper.domain.common"
move_and_update "domain/enums/ValidationErrorCode.java" "domain/common/ValidationErrorCode.java" "in.annupaper.domain.enums" "in.annupaper.domain.common"
move_and_update "domain/enums/EventType.java" "domain/common/EventType.java" "in.annupaper.domain.enums" "in.annupaper.domain.common"

echo "✅ Domain layer migration complete"
```

### 2.3 Import Update Script

```bash
#!/bin/bash
# update_imports.sh
# Updates all import statements across the codebase

SRC_DIR="src/main/java"

# Function to replace imports
replace_import() {
    local old_import=$1
    local new_import=$2

    echo "Replacing: $old_import → $new_import"

    find ${SRC_DIR} -type f -name "*.java" -exec sed -i '' \
        "s|import ${old_import};|import ${new_import};|g" {} \;
}

# Domain imports
replace_import "in.annupaper.domain.model.User" "in.annupaper.domain.user.User"
replace_import "in.annupaper.domain.model.UserRole" "in.annupaper.domain.user.UserRole"
replace_import "in.annupaper.domain.model.Broker" "in.annupaper.domain.broker.Broker"
replace_import "in.annupaper.domain.model.UserBroker" "in.annupaper.domain.broker.UserBroker"
replace_import "in.annupaper.domain.enums.BrokerRole" "in.annupaper.domain.broker.BrokerRole"
replace_import "in.annupaper.domain.model.Candle" "in.annupaper.domain.market.Candle"
replace_import "in.annupaper.domain.model.Signal" "in.annupaper.domain.signal.Signal"
replace_import "in.annupaper.domain.model.Trade" "in.annupaper.domain.trade.Trade"
# ... add all other domain imports

# Service imports (example)
replace_import "in.annupaper.service.candle.TickCandleBuilder" "in.annupaper.application.data.TickCandleBuilder"
replace_import "in.annupaper.service.signal.MtfSignalGenerator" "in.annupaper.application.signal.MtfSignalGenerator"
# ... add all other service imports

echo "✅ Import statements updated"
```

### 2.4 Compilation Check Script

```bash
#!/bin/bash
# check_compilation.sh
# Validates that the project compiles after migration

echo "🔍 Running Maven compilation check..."

mvn clean compile -DskipTests

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful"
else
    echo "❌ Compilation failed - fix errors before proceeding"
    exit 1
fi
```

### 2.5 Test Execution Script

```bash
#!/bin/bash
# run_tests.sh
# Runs all tests to ensure functionality is preserved

echo "🧪 Running unit tests..."
mvn test

if [ $? -eq 0 ]; then
    echo "✅ All unit tests passed"
else
    echo "❌ Unit tests failed - fix errors before proceeding"
    exit 1
fi

echo "🧪 Running integration tests..."
mvn verify -Pintegration-tests

if [ $? -eq 0 ]; then
    echo "✅ All integration tests passed"
else
    echo "❌ Integration tests failed - fix errors before proceeding"
    exit 1
fi

echo "✅ All tests passed successfully"
```

### 2.6 Cleanup Script

```bash
#!/bin/bash
# cleanup_old_files.sh
# Deletes old files after confirming tests pass
# ⚠️ ONLY run this after successful migration and testing

read -p "⚠️  This will delete old files. Are you sure tests pass? (yes/no): " confirm

if [ "$confirm" != "yes" ]; then
    echo "❌ Cleanup cancelled"
    exit 1
fi

BASE_DIR="src/main/java/in/annupaper"

# Delete old domain files
rm -rf ${BASE_DIR}/domain/model/
rm -rf ${BASE_DIR}/domain/enums/
rm -rf ${BASE_DIR}/domain/constants/

# Delete old broker files
rm -rf ${BASE_DIR}/broker/

# Delete old auth files
rm -rf ${BASE_DIR}/auth/

# Delete old repository files (keep only new structure)
rm ${BASE_DIR}/repository/Postgres*.java

# Delete old service files
rm -rf ${BASE_DIR}/service/candle/
rm -rf ${BASE_DIR}/service/signal/
rm -rf ${BASE_DIR}/service/trade/
rm -rf ${BASE_DIR}/service/execution/
rm -rf ${BASE_DIR}/service/validation/
rm -rf ${BASE_DIR}/service/oauth/
rm -rf ${BASE_DIR}/service/admin/
rm -rf ${BASE_DIR}/service/core/
rm -rf ${BASE_DIR}/service/mtf/

# Delete old transport files
rm -rf ${BASE_DIR}/transport/

# Delete old util files
rm ${BASE_DIR}/util/Env.java

echo "✅ Old files cleaned up"
```

---

## 3. Manual Steps

### 3.1 Repository Interface Extraction (Example)

**Before**:
```java
// repository/PostgresUserRepository.java
package in.annupaper.repository;

public class PostgresUserRepository {
    private final DataSource dataSource;

    public User findById(UUID userId) {
        // Implementation
    }

    public User save(User user) {
        // Implementation
    }
}
```

**After**:

**Step 1**: Create interface
```java
// infrastructure/persistence/repository/UserRepository.java
package in.annupaper.infrastructure.persistence.repository;

public interface UserRepository {
    User findById(UUID userId);
    User findByEmail(String email);
    User save(User user);
    void delete(UUID userId);
}
```

**Step 2**: Update implementation
```java
// infrastructure/persistence/postgres/PostgresUserRepository.java
package in.annupaper.infrastructure.persistence.postgres;

import in.annupaper.infrastructure.persistence.repository.UserRepository;

public class PostgresUserRepository implements UserRepository {
    private final DataSource dataSource;

    @Override
    public User findById(UUID userId) {
        // Existing implementation
    }

    @Override
    public User save(User user) {
        // Existing implementation
    }

    // ... other methods
}
```

**Step 3**: Update all service dependencies
```java
// Before
public class AuthService {
    private final PostgresUserRepository userRepo;  // ❌ Depends on implementation
}

// After
public class AuthService {
    private final UserRepository userRepo;  // ✅ Depends on interface
}
```

### 3.2 Broker Adapter Split (Manual Extraction)

**Current FyersAdapter** (~1200 lines):
```java
public class FyersAdapter implements BrokerAdapter {
    // DATA methods
    public void connect(UserBroker ub) { /* WebSocket connection */ }
    public void subscribeTicks(String symbol, TickListener listener) { /* ... */ }
    public List<Candle> getHistoricalCandles(...) { /* REST API call */ }
    public List<Instrument> getInstruments() { /* Download CSV */ }

    // ORDER methods
    public String placeOrder(OrderRequest req) { /* REST API call */ }
    public void modifyOrder(String orderId, OrderRequest req) { /* ... */ }
    public void cancelOrder(String orderId) { /* ... */ }
    public OrderStatus getOrderStatus(String orderId) { /* ... */ }
}
```

**Split into two adapters**:

**FyersDataAdapter** (~800 lines):
```java
package in.annupaper.infrastructure.broker.data;

public class FyersDataAdapter implements DataBroker {

    // Keep ONLY data-related fields
    private WebSocket webSocket;
    private Map<String, List<TickListener>> tickListeners;
    private Instant lastTickTime;
    private CircuitBreaker circuitBreaker;

    // Keep ONLY data-related methods
    @Override
    public void connect(UserBroker ub) {
        // Extract WebSocket connection code
    }

    @Override
    public void subscribeTicks(String symbol, TickListener listener) {
        // Extract tick subscription code
    }

    @Override
    public List<Candle> getHistoricalCandles(...) {
        // Extract historical data REST call
    }

    @Override
    public List<Instrument> getInstruments() {
        // Extract instrument download
    }

    @Override
    public DataFeedHealth getHealthStatus() {
        return new DataFeedHealth(
            isConnected(),
            lastTickTime,
            circuitBreaker.getState()
        );
    }

    // Delete order-related methods
}
```

**FyersOrderAdapter** (~400 lines):
```java
package in.annupaper.infrastructure.broker.order;

public class FyersOrderAdapter implements OrderBroker {

    // Keep ONLY order-related fields
    private String accessToken;
    private final UserBrokerSessionRepository sessionRepo;

    // Keep ONLY order-related methods
    @Override
    public void connect(UserBroker ub) {
        // Load access token from session (no WebSocket)
    }

    @Override
    public String placeOrder(OrderRequest request) {
        // Extract order placement REST call
    }

    @Override
    public void modifyOrder(String brokerOrderId, OrderRequest request) {
        // Extract order modification
    }

    @Override
    public void cancelOrder(String brokerOrderId) {
        // Extract order cancellation
    }

    @Override
    public OrderStatus getOrderStatus(String brokerOrderId) {
        // Extract status check
    }

    // Delete data-related methods
}
```

**Key Changes**:
1. **No shared state** between adapters
2. **DataAdapter**: Manages WebSocket, tick listeners, health checks
3. **OrderAdapter**: Manages REST API calls for orders only
4. **Both load tokens** independently from `UserBrokerSession`

### 3.3 Controller Extraction from ApiHandlers

**Current ApiHandlers.java** (~2000 lines with all endpoints):

**Step 1**: Identify endpoint groups
```java
// Auth endpoints
POST /api/auth/login
POST /api/auth/register

// User endpoints
GET /api/user/profile
PUT /api/user/profile
GET /api/user/portfolio

// Broker endpoints
GET /api/brokers
POST /api/brokers/link
GET /api/brokers/{brokerId}/oauth/initiate
GET /api/brokers/{brokerId}/oauth/callback

// Signal endpoints
GET /api/signals
GET /api/signals/{signalId}

// Trade endpoints
GET /api/trades
GET /api/trades/{tradeId}
POST /api/trades/{tradeId}/exit

// Watchlist endpoints
GET /api/watchlists
POST /api/watchlists
PUT /api/watchlists/{watchlistId}
DELETE /api/watchlists/{watchlistId}

// Admin endpoints
GET /api/admin/config
PUT /api/admin/config

// Health endpoints
GET /api/health
```

**Step 2**: Extract into separate controllers

**AuthController.java**:
```java
package in.annupaper.presentation.http.controllers;

public class AuthController {
    private final AuthService authService;
    private final JwtService jwtService;

    public void handleLogin(HttpServerExchange exchange) {
        // Extract from ApiHandlers
        // 1. Parse JSON body
        // 2. Call authService.authenticate()
        // 3. Generate JWT
        // 4. Return response
    }

    public void handleRegister(HttpServerExchange exchange) {
        // Extract from ApiHandlers
    }
}
```

**UserController.java**:
```java
package in.annupaper.presentation.http.controllers;

public class UserController {
    private final UserService userService;
    private final PortfolioService portfolioService;

    public void getProfile(HttpServerExchange exchange) {
        // Extract from ApiHandlers
        UUID userId = exchange.getAttachment(USER_ID_KEY);
        User user = userService.findById(userId);
        // Return JSON
    }

    public void getPortfolio(HttpServerExchange exchange) {
        // Extract from ApiHandlers
    }
}
```

Repeat for all controller types.

### 3.4 Dependency Injector Wiring

**Critical Manual Step**: Wire all components in `DependencyInjector.java`

```java
package in.annupaper.bootstrap;

public class DependencyInjector {

    // 1. Wire infrastructure layer
    private void wireInfrastructure() {
        // Database
        this.dataSource = createHikariDataSource();

        // Brokers
        this.dataBrokerFactory = new DataBrokerFactory(
            sessionRepo,  // Need to create repo first
            candleAggregator,  // Need to create aggregator first
            instrumentRepo
        );

        this.orderBrokerFactory = new OrderBrokerFactory(
            sessionRepo
        );
    }

    // 2. Wire repositories
    private void wireRepositories() {
        this.userRepo = new PostgresUserRepository(dataSource);
        this.brokerRepo = new PostgresBrokerRepository(dataSource);
        this.userBrokerRepo = new PostgresUserBrokerRepository(dataSource);
        this.sessionRepo = new PostgresUserBrokerSessionRepository(dataSource);
        // ... all other repos
    }

    // 3. Wire application services
    private void wireApplicationServices() {
        // Dependencies must be created in correct order

        // Data services (no dependencies on other app services)
        this.candleAggregator = new CandleAggregator();
        this.candleStore = new CandleStore(candleRepo);
        this.tickCandleBuilder = new TickCandleBuilder(
            candleStore,
            sessionClock,
            marketDataCache
        );

        // Signal services (depend on data services)
        this.confluenceCalculator = new ConfluenceCalculator();
        this.mtfSignalGenerator = new MtfSignalGenerator(
            candleStore,
            confluenceCalculator,
            signalRepo,
            eventService
        );

        // Execution services (depend on broker factories)
        this.orderPlacementService = new OrderPlacementService(
            orderBrokerFactory,  // ✅ Uses OrderBrokerFactory
            tradeRepo,
            eventService
        );

        // Trade services (depend on execution)
        this.tradeManagementService = new TradeManagementService(
            tradeRepo,
            eventService
        );
    }

    // 4. Wire presentation
    private void wirePresentation() {
        this.authController = new AuthController(authService, jwtService);
        this.userController = new UserController(userService, portfolioService);
        // ... all other controllers
    }
}
```

**Dependency Resolution Order**:
1. Infrastructure (data source, factories)
2. Repositories
3. Application services (respect dependencies)
4. Presentation (controllers)

---

## 4. Validation Checklist

### 4.1 Phase 1: Domain Layer

- [ ] All domain files moved to subdomains
- [ ] Package declarations updated
- [ ] All imports updated across codebase
- [ ] Compilation successful (`mvn clean compile`)
- [ ] Unit tests pass (`mvn test`)
- [ ] No references to old domain packages remain

**Validation Command**:
```bash
# Check for old domain imports
grep -r "import in.annupaper.domain.model" src/main/java/
# Should return no results
```

### 4.2 Phase 2: Infrastructure Layer

- [ ] `DataBroker` interface created
- [ ] `OrderBroker` interface created
- [ ] `FyersDataAdapter` extracted and implements `DataBroker`
- [ ] `FyersOrderAdapter` extracted and implements `OrderBroker`
- [ ] `ZerodhaDataAdapter` moved and implements `DataBroker`
- [ ] `ZerodhaOrderAdapter` created (new implementation)
- [ ] `DataBrokerFactory` created and functional
- [ ] `OrderBrokerFactory` created and functional
- [ ] All repository interfaces created
- [ ] All Postgres implementations moved
- [ ] OAuth services moved to `infrastructure/broker/auth/`
- [ ] Security services moved
- [ ] Compilation successful
- [ ] Unit tests pass

**Validation Command**:
```bash
# Check that old BrokerAdapter is deleted
ls src/main/java/in/annupaper/broker/BrokerAdapter.java
# Should not exist

# Check that new interfaces exist
ls src/main/java/in/annupaper/infrastructure/broker/data/DataBroker.java
ls src/main/java/in/annupaper/infrastructure/broker/order/OrderBroker.java
# Should exist
```

### 4.3 Phase 3: Application Layer

- [ ] All candle services moved to `application/data/`
- [ ] All signal services moved to `application/signal/`
- [ ] Risk services extracted to `application/risk/`
- [ ] Trade services moved to `application/trade/`
- [ ] Execution services moved and updated to use `OrderBrokerFactory`
- [ ] Report services created
- [ ] Event services moved
- [ ] All service dependencies resolved
- [ ] Compilation successful
- [ ] Unit tests pass
- [ ] Integration tests pass

**Validation Command**:
```bash
# Check that services use OrderBrokerFactory
grep -r "BrokerAdapterFactory" src/main/java/in/annupaper/application/
# Should return no results

grep -r "OrderBrokerFactory" src/main/java/in/annupaper/application/execution/
# Should return results in OrderPlacementService
```

### 4.4 Phase 4: Presentation Layer

- [ ] All controllers extracted from `ApiHandlers.java`
- [ ] DTOs created for all requests/responses
- [ ] Middleware created (AuthMiddleware, ErrorHandler, etc.)
- [ ] WebSocket handlers moved
- [ ] Routing updated in ServerBootstrap
- [ ] Compilation successful
- [ ] All API endpoints functional (manual testing)

**Validation Commands**:
```bash
# Test login endpoint
curl -X POST http://localhost:9090/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@amzf.in","password":"admin123"}'

# Test protected endpoint with JWT
curl -X GET http://localhost:9090/api/signals \
  -H "Authorization: Bearer <token>"
```

### 4.5 Phase 5: Configuration

- [ ] `Environment.java` created with all variables
- [ ] `AppConfig.java` created with constants
- [ ] `MtfConfigLoader.java` created
- [ ] All services updated to use new config classes
- [ ] New environment variables added (DATA_FEED_MODE, EXECUTION_BROKER)
- [ ] Compilation successful
- [ ] Tests pass

**Validation Command**:
```bash
# Check that old Env.java is deleted
ls src/main/java/in/annupaper/util/Env.java
# Should not exist

# Check that new Environment.java exists
ls src/main/java/in/annupaper/config/Environment.java
# Should exist
```

### 4.6 Phase 6: Bootstrap

- [ ] `DependencyInjector.java` created
- [ ] All components wired correctly
- [ ] `ServerBootstrap.java` created
- [ ] All routes defined
- [ ] `StartupValidator.java` updated
- [ ] `App.java` updated with new bootstrap flow
- [ ] Application starts successfully
- [ ] All background services start
- [ ] Brokers connect successfully
- [ ] Integration tests pass

**Validation Commands**:
```bash
# Start application
java -jar target/amzf-0.4.jar

# Check logs for successful startup
tail -f logs/amzf.log | grep "AMZF Trading System started successfully"

# Check broker connections
curl http://localhost:9090/api/health
# Should show all brokers as connected
```

### 4.7 End-to-End Validation

- [ ] User registration works
- [ ] User login works
- [ ] Broker linking works (FYERS, Zerodha)
- [ ] OAuth flows work
- [ ] Watchlist creation works
- [ ] Real-time ticks received (Zerodha)
- [ ] Candles built from ticks
- [ ] Signals generated (MTF analysis)
- [ ] Orders placed (FYERS)
- [ ] Order reconciliation works
- [ ] Exit signals generated
- [ ] Exit orders placed
- [ ] Trades closed correctly
- [ ] P&L calculated correctly
- [ ] WebSocket events delivered
- [ ] Admin configuration updates work
- [ ] Health monitoring dashboard works

### 4.8 Performance Validation

| Metric | Before Refactoring | After Refactoring | Status |
|--------|-------------------|-------------------|--------|
| Startup time | ~25s | < 30s | ⏱️ Measure |
| Tick-to-signal latency | ~300ms | < 500ms | ⏱️ Measure |
| Order placement latency | ~800ms | < 1s | ⏱️ Measure |
| Memory usage (baseline) | ~250MB | < 300MB | ⏱️ Measure |
| CPU usage (idle) | ~5% | < 10% | ⏱️ Measure |

**Benchmark Command**:
```bash
# Use JMH or custom benchmark
mvn exec:java -Dexec.mainClass="in.annupaper.benchmark.PerformanceBenchmark"
```

### 4.9 Code Quality Validation

- [ ] No circular dependencies (check with dependency analyzer)
- [ ] Code duplication < 5% (check with PMD or SonarQube)
- [ ] Test coverage > 80% (check with JaCoCo)
- [ ] No critical code smells (check with SonarQube)
- [ ] Cyclomatic complexity < 10 per method (check with checkstyle)

**Validation Commands**:
```bash
# Check test coverage
mvn clean test jacoco:report
open target/site/jacoco/index.html

# Check code quality
mvn sonarqube:sonarqube
```

---

## Summary

This migration guide provides:
1. **Complete file mapping** for all 150+ Java files
2. **Automated scripts** for package creation, file moves, import updates
3. **Manual steps** for complex extractions (broker split, controller extraction)
4. **Comprehensive validation** at each phase

**Recommended Approach**:
1. Run automated scripts for bulk operations
2. Perform manual steps for complex splits
3. Validate after each phase (compilation + tests)
4. Only proceed to next phase after validation passes
5. Keep rollback plan ready (git branches)

**Total Migration Time**: 6-7 weeks (as per main plan)

**Next Steps**:
1. Review this guide with the team
2. Make scripts executable: `chmod +x *.sh`
3. Create git branch: `git checkout -b refactor/clean-architecture`
4. Run Phase 1 scripts
5. Validate Phase 1
6. Proceed to Phase 2
