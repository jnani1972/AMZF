package in.annupaper.bootstrap;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import in.annupaper.auth.AuthService;
import in.annupaper.auth.JwtService;
import in.annupaper.broker.BrokerAdapterFactory;
import in.annupaper.infrastructure.broker.BrokerFactory;
import in.annupaper.domain.broker.BrokerIds;
import in.annupaper.domain.common.EventType;
import in.annupaper.domain.data.TimeframeType;
import in.annupaper.domain.data.Candle;
import in.annupaper.domain.data.Watchlist;
import in.annupaper.domain.trade.Trade;
import in.annupaper.domain.user.Portfolio;
import in.annupaper.repository.BrokerRepository;
import in.annupaper.repository.CandleRepository;
import in.annupaper.repository.PortfolioRepository;
import in.annupaper.repository.PostgresBrokerRepository;
import in.annupaper.repository.PostgresCandleRepository;
import in.annupaper.repository.PostgresPortfolioRepository;
import in.annupaper.repository.PostgresSignalRepository;
import in.annupaper.repository.PostgresSignalDeliveryRepository;
import in.annupaper.repository.PostgresExitSignalRepository;
import in.annupaper.repository.PostgresTradeEventRepository;
import in.annupaper.repository.PostgresTradeIntentRepository;
import in.annupaper.repository.PostgresTradeRepository;
import in.annupaper.repository.PostgresUserBrokerRepository;
import in.annupaper.repository.PostgresWatchlistRepository;
import in.annupaper.repository.SignalRepository;
import in.annupaper.repository.SignalDeliveryRepository;
import in.annupaper.repository.ExitSignalRepository;
import in.annupaper.repository.TradeEventRepository;
import in.annupaper.repository.TradeIntentRepository;
import in.annupaper.repository.TradeRepository;
import in.annupaper.repository.UserBrokerRepository;
import in.annupaper.repository.WatchlistRepository;
import in.annupaper.service.admin.AdminService;
import in.annupaper.service.candle.CandleFetcher;
import in.annupaper.service.candle.CandleReconciler;
import in.annupaper.service.candle.CandleStore;
import in.annupaper.service.candle.TickCandleBuilder;
import in.annupaper.service.core.EventService;
import in.annupaper.service.MarketDataCache;
import in.annupaper.service.execution.ExecutionOrchestrator;
import in.annupaper.service.mtf.MtfAnalysisService;
import in.annupaper.service.signal.BrickMovementTracker;
import in.annupaper.service.signal.ExitSignalService;
import in.annupaper.service.signal.SignalService;
import in.annupaper.service.trade.ActiveTradeIndex;
import in.annupaper.service.trade.TradeCoordinator;
import in.annupaper.service.trade.TradeManagementService;
import in.annupaper.service.trade.TradeManagementServiceImpl;
import in.annupaper.service.signal.EntrySignalCoordinator;
import in.annupaper.service.signal.ExitSignalCoordinator;
import in.annupaper.service.signal.SignalDeliveryIndex;
import in.annupaper.service.signal.SignalManagementService;
import in.annupaper.service.signal.SignalManagementServiceImpl;
import in.annupaper.service.validation.ValidationService;
import in.annupaper.transport.http.ApiHandlers;
import in.annupaper.transport.ws.WsHub;
import in.annupaper.util.Env;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.RoutingHandler;
import io.undertow.util.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Core Java entry point (NO Spring).
 * 
 * v04: Multi-user, multi-broker architecture with:
 * - JWT authentication
 * - PostgreSQL repositories
 * - Broker adapters (Zerodha, Fyers, Dhan)
 * - MTF analysis service
 * - Signal fan-out to user-brokers
 * - Per-user-broker validation
 */
public final class App {
    private static final Logger log = LoggerFactory.getLogger(App.class);
    
    public static void main(String[] args) {
        log.info("═══════════════════════════════════════════════════════════════");
        log.info("=== AnnuPaper v04 Starting ===");
        log.info("═══════════════════════════════════════════════════════════════");

        // Check if running in Feed Collector mode (relay ticks only, no HTTP API)
        String runMode = System.getenv().getOrDefault("RUN_MODE", "FULL");
        boolean collectorMode = "FEED_COLLECTOR".equalsIgnoreCase(runMode);

        int port = Env.getInt("PORT", 9090);
        int wsBatchFlushMs = Env.getInt("WS_BATCH_FLUSH_MS", 100);
        String jwtSecret = Env.get("JWT_SECRET", "annu-paper-secret-key-change-in-production");
        long jwtExpirationMs = Env.getInt("JWT_EXPIRATION_HOURS", 24) * 3600000L;

        // ═══════════════════════════════════════════════════════════════
        // Database
        // ═══════════════════════════════════════════════════════════════
        DataSource dataSource = createDataSource();

        // ═══════════════════════════════════════════════════════════════
        // Prometheus Metrics (Production Monitoring)
        // ═══════════════════════════════════════════════════════════════
        in.annupaper.infrastructure.broker.metrics.PrometheusBrokerMetrics metrics =
            new in.annupaper.infrastructure.broker.metrics.PrometheusBrokerMetrics();
        log.info("✓ Prometheus metrics initialized");

        // ═══════════════════════════════════════════════════════════════
        // MTF Config Migration (runs on startup)
        // ═══════════════════════════════════════════════════════════════
        in.annupaper.migration.MtfConfigMigration mtfMigration =
            new in.annupaper.migration.MtfConfigMigration(dataSource);
        mtfMigration.migrate();

        // ═══════════════════════════════════════════════════════════════
        // JWT Service
        // ═══════════════════════════════════════════════════════════════
        JwtService jwtService = new JwtService(jwtSecret, jwtExpirationMs);
        
        // Token validator function
        java.util.function.Function<String, String> tokenValidator = token -> {
            if (token == null) return null;
            return jwtService.validateAndGetUserId(token);
        };
        
        // ═══════════════════════════════════════════════════════════════
        // Auth Service
        // ═══════════════════════════════════════════════════════════════
        AuthService authService = new AuthService(dataSource, jwtService);

        // Ensure admin user exists
        ensureAdminExists(authService);
        
        // ═══════════════════════════════════════════════════════════════
        // Transport: WS hub (user-scoped)
        // ═══════════════════════════════════════════════════════════════
        WsHub wsHub = new WsHub(tokenValidator);
        wsHub.setFlushMs(wsBatchFlushMs);
        wsHub.start();
        
        // ═══════════════════════════════════════════════════════════════
        // Repository layer
        // ═══════════════════════════════════════════════════════════════
        TradeEventRepository eventRepo = new PostgresTradeEventRepository(dataSource);
        UserBrokerRepository userBrokerRepo = new PostgresUserBrokerRepository(dataSource);
        CandleRepository candleRepo = new PostgresCandleRepository(dataSource);
        BrokerRepository brokerRepo = new PostgresBrokerRepository(dataSource);
        PortfolioRepository portfolioRepo = new PostgresPortfolioRepository(dataSource);
        WatchlistRepository watchlistRepo = new PostgresWatchlistRepository(dataSource);
        SignalRepository signalRepo = new PostgresSignalRepository(dataSource);
        TradeIntentRepository tradeIntentRepo = new PostgresTradeIntentRepository(dataSource);
        TradeRepository tradeRepo = new PostgresTradeRepository(dataSource);
        in.annupaper.repository.UserBrokerSessionRepository sessionRepo =
            new in.annupaper.repository.PostgresUserBrokerSessionRepository(dataSource);
        in.annupaper.repository.InstrumentRepository instrumentRepo =
            new in.annupaper.repository.PostgresInstrumentRepository(dataSource);
        in.annupaper.repository.MtfConfigRepository mtfConfigRepo =
            new in.annupaper.repository.PostgresMtfConfigRepository(dataSource);
        SignalDeliveryRepository signalDeliveryRepo = new PostgresSignalDeliveryRepository(dataSource);
        ExitSignalRepository exitSignalRepo = new PostgresExitSignalRepository(dataSource);
        in.annupaper.repository.ExitIntentRepository exitIntentRepo =
            new in.annupaper.repository.PostgresExitIntentRepository(dataSource);

        // ═══════════════════════════════════════════════════════════════
        // ✅ STARTUP VALIDATION GATE (P0-A)
        // ═══════════════════════════════════════════════════════════════
        // Validates configuration before system starts.
        // Throws IllegalStateException if config is invalid (production mode violations).
        // See: COMPREHENSIVE_IMPLEMENTATION_PLAN.md Phase 1, P0-A
        try {
            StartupConfigValidator.validate(brokerRepo, userBrokerRepo);
        } catch (IllegalStateException e) {
            log.error("❌ STARTUP VALIDATION FAILED", e);
            System.err.println("\n" + e.getMessage() + "\n");
            System.exit(1);
        }

        // ═══════════════════════════════════════════════════════════════
        // Broker adapters (with session repository for OAuth tokens)
        // ═══════════════════════════════════════════════════════════════
        BrokerAdapterFactory legacyBrokerFactory = new BrokerAdapterFactory(sessionRepo, userBrokerRepo);

        // ✅ Phase 2: New BrokerFactory for dual-broker architecture
        BrokerFactory brokerFactory = new BrokerFactory(sessionRepo, metrics);

        // ═══════════════════════════════════════════════════════════════
        // Token Refresh Watchdog (Auto-reload tokens on refresh)
        // ═══════════════════════════════════════════════════════════════
        in.annupaper.service.TokenRefreshWatchdog tokenWatchdog =
            new in.annupaper.service.TokenRefreshWatchdog(dataSource);

        // Get all active user_broker_ids to register listeners
        try {
            java.util.List<in.annupaper.domain.broker.UserBroker> userBrokers =
                userBrokerRepo.findAll().stream()
                    .filter(ub -> ub.deletedAt() == null)
                    .toList();

            for (in.annupaper.domain.broker.UserBroker ub : userBrokers) {
                tokenWatchdog.registerListener(ub.userBrokerId(),
                    (userBrokerId, newToken, sessionId) -> {
                        log.info("⚡ Token refresh event: userBrokerId={}, session={}",
                                userBrokerId, sessionId);
                        legacyBrokerFactory.reloadToken(userBrokerId, newToken, sessionId);
                    });
            }

            tokenWatchdog.start();
            log.info("✓ Token refresh watchdog started ({} listeners registered)",
                    userBrokers.size());
        } catch (Exception e) {
            log.error("Failed to start token watchdog", e);
        }

        // ═══════════════════════════════════════════════════════════════
        // Service layer (moved up - needed by candle services)
        // ═══════════════════════════════════════════════════════════════
        EventService eventService = new EventService(eventRepo, wsHub);
        in.annupaper.service.InstrumentService instrumentService =
            new in.annupaper.service.InstrumentService(instrumentRepo, brokerFactory, legacyBrokerFactory);

        // ═══════════════════════════════════════════════════════════════
        // Startup: Download instruments from all brokers
        // ═══════════════════════════════════════════════════════════════
        log.info("Triggering instrument download on startup...");
        instrumentService.downloadAllInstruments();

        // ═══════════════════════════════════════════════════════════════
        // Market Data Cache (in-memory cache for latest tick prices)
        // ═══════════════════════════════════════════════════════════════
        MarketDataCache marketDataCache = new MarketDataCache();

        // ═══════════════════════════════════════════════════════════════
        // Candle Services (with backfill and aggregation)
        // ═══════════════════════════════════════════════════════════════
        CandleStore candleStore = new CandleStore(candleRepo);
        CandleFetcher candleFetcher = new CandleFetcher(brokerFactory, legacyBrokerFactory, candleStore);
        CandleReconciler candleReconciler = new CandleReconciler(candleFetcher, candleStore);

        // HistoryBackfiller dynamically fetches data broker adapter
        in.annupaper.service.candle.HistoryBackfiller historyBackfiller =
            new in.annupaper.service.candle.HistoryBackfiller(
                candleStore,
                legacyBrokerFactory,
                userBrokerRepo,
                brokerRepo
            );

        in.annupaper.service.candle.CandleAggregator candleAggregator =
            new in.annupaper.service.candle.CandleAggregator(candleStore, eventService);

        in.annupaper.service.candle.RecoveryManager recoveryManager =
            new in.annupaper.service.candle.RecoveryManager(candleStore, historyBackfiller, candleAggregator);

        // MTF Backfill Service for ensuring sufficient historical candles
        in.annupaper.service.candle.MtfBackfillService mtfBackfillService =
            new in.annupaper.service.candle.MtfBackfillService(historyBackfiller, candleAggregator, watchlistRepo);

        // ═══════════════════════════════════════════════════════════════
        // Watchdog Manager (Self-healing system monitor)
        // ═══════════════════════════════════════════════════════════════
        // Note: exitSignalService needs to be created first, so we'll instantiate
        // WatchdogManager later after all dependencies are available

        // TickCandleBuilder now requires HistoryBackfiller and CandleAggregator
        TickCandleBuilder tickCandleBuilder = new TickCandleBuilder(
            candleStore,
            eventService,
            marketDataCache,
            historyBackfiller,
            candleAggregator
        );

        // ═══════════════════════════════════════════════════════════════
        // MTF Analysis Service (updated to use CandleStore)
        // ═══════════════════════════════════════════════════════════════
        // MtfAnalysisService mtfService = new MtfAnalysisService((symbol, tfType) -> {
        //     // Stub: Generate mock candles (replace with real data from DATA broker)
        //     return generateMockCandles(symbol, tfType);
        // });
        MtfAnalysisService mtfService = new MtfAnalysisService((symbol, tfType) -> {
            // Use CandleStore to get candles (from memory or PostgreSQL)
            List<Candle> candles = candleStore.getFromMemory(symbol, tfType);
            if (candles.isEmpty()) {
                // Fallback to PostgreSQL
                candles = candleStore.getFromPostgres(symbol, tfType, 100);
            }
            if (candles.isEmpty()) {
                // Final fallback: Generate mock candles for testing
                return generateMockCandles(symbol, tfType);
            }
            return candles;
        });

        // ═══════════════════════════════════════════════════════════════
        // Validation and Execution Services
        // ═══════════════════════════════════════════════════════════════
        // Position Sizing Service (constitutional engine)
        in.annupaper.service.signal.PositionSizingService positionSizingService =
            new in.annupaper.service.signal.PositionSizingService(
                candleStore, portfolioRepo, tradeRepo, mtfConfigRepo);

        ValidationService validationService = new ValidationService(positionSizingService);

        // User context provider
        java.util.function.Function<String, ValidationService.UserContext> userContextProvider = userId -> {
            // Fetch portfolio for user
            List<Portfolio> portfolios = portfolioRepo.findByUserId(userId);

            if (portfolios.isEmpty()) {
                log.warn("No portfolio found for user {}", userId);
                return null;
            }

            Portfolio portfolio = portfolios.get(0);  // Use first portfolio

            // Fetch open trades for this portfolio
            List<Trade> openTrades = tradeRepo.findByPortfolioId(portfolio.portfolioId()).stream()
                .filter(t -> "OPEN".equals(t.status()))
                .toList();

            // Calculate current exposure
            BigDecimal currentExposure = openTrades.stream()
                .map(t -> t.entryPrice().multiply(new BigDecimal(t.entryQty())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Calculate current log exposure
            BigDecimal currentLogExposure = openTrades.stream()
                .map(t -> t.currentLogReturn() != null ? t.currentLogReturn() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            return new ValidationService.UserContext(
                portfolio.portfolioId(),
                portfolio.totalCapital(),
                portfolio.availableCapital(),
                currentExposure,
                currentLogExposure,
                openTrades.size(),
                10,  // maxPyramidLevel - TODO: get from config
                BigDecimal.ZERO,  // dailyLoss - TODO: calculate
                BigDecimal.ZERO,  // weeklyLoss - TODO: calculate
                false,  // inCooldown - TODO: calculate
                portfolio.paused()
            );
        };

        // ═══════════════════════════════════════════════════════════════
        // Trade Management Service (Phase 2 - Core Execution)
        // ═══════════════════════════════════════════════════════════════
        // Single owner of trade lifecycle: CREATED → PENDING → OPEN → EXITING → CLOSED
        // Actor model with partitioned executors for race-free trade updates
        BrickMovementTracker brickTracker = new BrickMovementTracker();
        TradeManagementService tradeManagementService = new TradeManagementServiceImpl(
            tradeRepo,
            signalRepo,
            userBrokerRepo,
            legacyBrokerFactory,
            eventService,
            brickTracker
        );

        // Initialize active trade index from database (all OPEN trades)
        log.info("Rebuilding active trade index from database...");
        tradeManagementService.rebuildActiveIndex();
        log.info("✓ Active trade index rebuilt");

        // ═══════════════════════════════════════════════════════════════
        // Execution Orchestrator (Phase 3B - Delivery Processing)
        // ═══════════════════════════════════════════════════════════════
        // Processes signal deliveries, validates, creates trade intents
        ExecutionOrchestrator executionOrchestrator = new ExecutionOrchestrator(
            tradeIntentRepo, userBrokerRepo, validationService, eventService, userContextProvider,
            signalDeliveryRepo, signalRepo);

        // ═══════════════════════════════════════════════════════════════
        // Exit Qualification Service (V010 - Exit Qualification Symmetry)
        // ═══════════════════════════════════════════════════════════════
        // Validates execution readiness for exit signals (mirrors ValidationService for entry)
        in.annupaper.service.validation.ExitQualificationService exitQualificationService =
            new in.annupaper.service.validation.ExitQualificationService(exitIntentRepo);

        // ═══════════════════════════════════════════════════════════════
        // Exit Order Execution Service (Exit Order Placement)
        // ═══════════════════════════════════════════════════════════════
        // Converts APPROVED exit intents into broker exit orders (mirrors OrderExecutionService for exits)
        // Pattern: Read APPROVED intent → Place order → Mark PLACED/FAILED
        // ✅ P0 fix: Added tradeManagementService for single-writer enforcement
        in.annupaper.service.execution.ExitOrderExecutionService exitOrderExecutionService =
            new in.annupaper.service.execution.ExitOrderExecutionService(
                exitIntentRepo, tradeRepo, tradeManagementService, userBrokerRepo, legacyBrokerFactory, eventService);

        // ═══════════════════════════════════════════════════════════════
        // Exit Order Processor (Polls for APPROVED exit intents)
        // ═══════════════════════════════════════════════════════════════
        // Scheduled task that processes approved exit intents every 5 seconds
        // Finds APPROVED exit intents and calls ExitOrderExecutionService
        in.annupaper.service.execution.ExitOrderProcessor exitOrderProcessor =
            new in.annupaper.service.execution.ExitOrderProcessor(
                exitIntentRepo, exitOrderExecutionService);
        exitOrderProcessor.start();  // Starts background polling thread

        // ═══════════════════════════════════════════════════════════════
        // Signal Management Service (Phase 2 - Signal Lifecycle)
        // ═══════════════════════════════════════════════════════════════
        // Single owner of signal lifecycle: DETECTED → PUBLISHED → {EXPIRED | CANCELLED | SUPERSEDED}
        // Actor model with partitioned executors for race-free signal updates
        SignalManagementService signalManagementService = new SignalManagementServiceImpl(
            signalRepo,
            signalDeliveryRepo,
            exitSignalRepo,
            exitIntentRepo,
            tradeRepo,
            userBrokerRepo,
            eventService,
            executionOrchestrator,
            exitQualificationService
        );

        // Initialize delivery index from database (all active deliveries)
        log.info("Rebuilding signal delivery index from database...");
        signalManagementService.rebuildDeliveryIndex();
        log.info("✓ Signal delivery index rebuilt");

        // ═══════════════════════════════════════════════════════════════
        // ✅ P0-C: Pending Order Reconciler (broker reconciliation loop)
        // ═══════════════════════════════════════════════════════════════
        // Reconciles pending orders with broker reality every 30 seconds
        // See: COMPREHENSIVE_IMPLEMENTATION_PLAN.md Phase 1, P0-C
        in.annupaper.service.execution.PendingOrderReconciler pendingOrderReconciler =
            new in.annupaper.service.execution.PendingOrderReconciler(
                tradeRepo, userBrokerRepo, legacyBrokerFactory);

        // ═══════════════════════════════════════════════════════════════
        // Exit Order Reconciler (tracks exit orders to completion)
        // ═══════════════════════════════════════════════════════════════
        // Reconciles placed exit orders with broker every 30 seconds
        // Closes trades when exit orders fill
        // CLOSES ARCHITECTURE GAP: "Exit Order Reconciler Missing"
        // ✅ P0 fix: Added tradeManagementService for single-writer enforcement
        in.annupaper.service.execution.ExitOrderReconciler exitOrderReconciler =
            new in.annupaper.service.execution.ExitOrderReconciler(
                exitIntentRepo, tradeRepo, tradeManagementService, userBrokerRepo, legacyBrokerFactory, eventService);

        // ═══════════════════════════════════════════════════════════════
        // MTF Config Service
        // ═══════════════════════════════════════════════════════════════
        in.annupaper.service.MtfConfigService mtfConfigService =
            new in.annupaper.service.MtfConfigService(mtfConfigRepo, signalRepo);

        // ═══════════════════════════════════════════════════════════════
        // Signal Generation with Confluence Analysis
        // ═══════════════════════════════════════════════════════════════
        in.annupaper.service.signal.ConfluenceCalculator confluenceCalculator =
            new in.annupaper.service.signal.ConfluenceCalculator(candleStore, mtfConfigRepo);

        SignalService signalService = new SignalService(
            signalRepo, userBrokerRepo, eventService, executionOrchestrator, confluenceCalculator, mtfConfigRepo, tradeRepo,
            candleStore, portfolioRepo, signalManagementService);

        // MTF Signal Generator (scheduled signal analysis)
        in.annupaper.service.signal.MtfSignalGenerator mtfSignalGenerator =
            new in.annupaper.service.signal.MtfSignalGenerator(signalService, watchlistRepo, marketDataCache, userBrokerRepo);

        // ═══════════════════════════════════════════════════════════════
        // Admin Service
        // ═══════════════════════════════════════════════════════════════
        // Old: AdminService adminService = new AdminService(brokerRepo, portfolioRepo, watchlistRepo, userBrokerRepo, dataSource);

        // Watchlist Template repositories (Level 1 & Level 2)
        in.annupaper.repository.WatchlistTemplateRepository watchlistTemplateRepo =
            new in.annupaper.repository.PostgresWatchlistTemplateRepository((com.zaxxer.hikari.HikariDataSource) dataSource);
        in.annupaper.repository.WatchlistSelectedRepository watchlistSelectedRepo =
            new in.annupaper.repository.PostgresWatchlistSelectedRepository((com.zaxxer.hikari.HikariDataSource) dataSource);

        // OLD: AdminService adminService = new AdminService(
        //     brokerRepo, portfolioRepo, watchlistRepo, userBrokerRepo,
        //     watchlistTemplateRepo, watchlistSelectedRepo, dataSource
        // );

        // FIX: Add MarketDataCache for in-memory LTP access (Market Watch feature)
        // FIX: Add CandleFetcher for historical data fetching when symbols added to watchlist
        AdminService adminService = new AdminService(
            brokerRepo, portfolioRepo, watchlistRepo, userBrokerRepo,
            watchlistTemplateRepo, watchlistSelectedRepo, dataSource,
            marketDataCache, candleFetcher
        );

        // ═══════════════════════════════════════════════════════════════
        // OAuth Services
        // ═══════════════════════════════════════════════════════════════
        String oauthRedirectUri = "http://localhost:4000/admin/oauth-callback";  // Frontend callback route

        // OAuth state repository (DB-backed state for CSRF protection)
        in.annupaper.repository.OAuthStateRepository oauthStateRepo =
            new in.annupaper.repository.OAuthStateRepository(dataSource);

        // OAuth service (token exchange)
        in.annupaper.service.oauth.BrokerOAuthService oauthService =
            new in.annupaper.service.oauth.BrokerOAuthService(userBrokerRepo, sessionRepo, oauthStateRepo, oauthRedirectUri);

        // FYERS login orchestrator (generates login URLs, opens browser)
        in.annupaper.service.oauth.FyersLoginOrchestrator fyersLoginOrchestrator =
            new in.annupaper.service.oauth.FyersLoginOrchestrator(oauthStateRepo, userBrokerRepo, oauthRedirectUri);
        log.info("✓ OAuth services initialized (redirect URI: {})", oauthRedirectUri);

        // ═══════════════════════════════════════════════════════════════
        // Exit Signal Services
        // ═══════════════════════════════════════════════════════════════
        ExitSignalService exitSignalService = new ExitSignalService(
            tradeRepo, brickTracker, eventService, signalManagementService,
            tradeManagementService, mtfConfigService);

        // ═══════════════════════════════════════════════════════════════
        // Watchdog Manager (Self-healing system monitor)
        // ═══════════════════════════════════════════════════════════════
        in.annupaper.service.WatchdogManager watchdogManager = new in.annupaper.service.WatchdogManager(
            dataSource,
            legacyBrokerFactory,
            userBrokerRepo,
            brokerRepo,
            watchlistRepo,
            sessionRepo,
            candleStore,
            tickCandleBuilder,
            exitSignalService,
            mtfSignalGenerator,
            recoveryManager,
            mtfBackfillService
        );

        // Wire tickCandleBuilder to watchdog for tick tracking
        tickCandleBuilder.setWatchdogManager(watchdogManager);

        // ═══════════════════════════════════════════════════════════════
        // Startup: Historical DAILY candles reconciliation
        // ═══════════════════════════════════════════════════════════════
        reconcileHistoricalData(dataSource, candleFetcher, userBrokerRepo, brokerRepo, watchlistRepo);

        // ═══════════════════════════════════════════════════════════════
        // Tick Stream Subscription & Recovery
        // ═══════════════════════════════════════════════════════════════
        setupTickStreamAndRecovery(
            collectorMode,
            userBrokerRepo,
            brokerRepo,
            watchlistRepo,
            legacyBrokerFactory,
            tickCandleBuilder,
            exitSignalService,
            recoveryManager,
            mtfBackfillService,
            mtfSignalGenerator
        );

        // ═══════════════════════════════════════════════════════════════
        // Scheduler: Time-based candle finalizer
        // ═══════════════════════════════════════════════════════════════
        startCandleFinalizerScheduler(tickCandleBuilder);

        // ═══════════════════════════════════════════════════════════════
        // Scheduler: Watchdog health check (self-healing)
        // ═══════════════════════════════════════════════════════════════
        startWatchdogScheduler(watchdogManager);

        // ═══════════════════════════════════════════════════════════════
        // Scheduler: MTF signal generation (every minute during market hours)
        // ═══════════════════════════════════════════════════════════════
        startMtfSignalScheduler(mtfSignalGenerator);

        // ═══════════════════════════════════════════════════════════════
        // Scheduler: OAuth state cleanup (every 10 minutes)
        // ═══════════════════════════════════════════════════════════════
        startOAuthStateCleanupScheduler(oauthStateRepo);

        // ═══════════════════════════════════════════════════════════════
        // ✅ P0-C: Start Pending Order Reconciler (every 30 seconds)
        // ═══════════════════════════════════════════════════════════════
        pendingOrderReconciler.start();
        log.info("✅ Pending order reconciler started (P0-C)");

        // ═══════════════════════════════════════════════════════════════
        // Start Exit Order Reconciler (every 30 seconds, offset +15s)
        // ═══════════════════════════════════════════════════════════════
        exitOrderReconciler.start();
        log.info("✅ Exit order reconciler started");

        // ═══════════════════════════════════════════════════════════════
        // Trailing Stops Configuration Service
        // ═══════════════════════════════════════════════════════════════
        String configDir = Env.get("CONFIG_DIR", "./config");
        in.annupaper.service.admin.TrailingStopsConfigService trailingStopsConfigService =
            new in.annupaper.service.admin.TrailingStopsConfigService(configDir);
        log.info("✓ Trailing stops config service initialized: {}", configDir);

        // ═══════════════════════════════════════════════════════════════
        // HTTP handlers (skipped in collector mode)
        // ═══════════════════════════════════════════════════════════════
        if (!collectorMode) {
            // ApiHandlers api = new ApiHandlers(eventRepo, tokenValidator);
            ApiHandlers api = new ApiHandlers(eventRepo, tokenValidator, jwtService, adminService, oauthService,
                    fyersLoginOrchestrator, legacyBrokerFactory, instrumentService,
                    userBrokerRepo, brokerRepo, watchlistRepo, tickCandleBuilder, exitSignalService, recoveryManager, mtfBackfillService);
        in.annupaper.transport.http.MtfConfigHandler mtfConfigHandler =
            new in.annupaper.transport.http.MtfConfigHandler(mtfConfigService, tokenValidator);
        in.annupaper.transport.http.AdminConfigHandler adminConfigHandler =
            new in.annupaper.transport.http.AdminConfigHandler(trailingStopsConfigService);
        in.annupaper.transport.http.MonitoringHandler monitoringHandler =
            new in.annupaper.transport.http.MonitoringHandler(dataSource);
        log.info("✓ Monitoring handler initialized");

        // Prometheus metrics endpoint
        in.annupaper.infrastructure.broker.metrics.PrometheusMetricsHandler metricsHandler =
            new in.annupaper.infrastructure.broker.metrics.PrometheusMetricsHandler(metrics.getRegistry());
        log.info("✓ Prometheus /metrics endpoint ready");

        RoutingHandler routes = Handlers.routing()
            .get("/metrics", metricsHandler)
            .get("/api/health", api::health)
            .post("/api/auth/login", exchange -> handleLogin(exchange, authService))
            .post("/api/auth/register", exchange -> handleRegister(exchange, authService))
            .get("/api/bootstrap", api::bootstrap)
            .get("/api/events", api::events)
            .get("/api/brokers", api::brokers)
            .get("/api/signals", api::signals)
            .get("/api/intents", api::intents)
            .get("/api/admin/users", api::adminGetUsers)
            .get("/api/admin/brokers", api::adminGetBrokers)
            .get("/api/admin/user-brokers", api::adminGetUserBrokers)
            .post("/api/admin/user-brokers", api::adminCreateUserBroker)
            .delete("/api/admin/user-brokers/{userBrokerId}", api::adminDeleteUserBroker)
            .post("/api/admin/user-brokers/{userBrokerId}/toggle", api::adminToggleUserBroker)
            .post("/api/admin/portfolios", api::adminCreatePortfolio)
            .get("/api/admin/portfolios", api::adminGetPortfolios)
            .post("/api/admin/watchlist", api::adminAddWatchlist)
            .get("/api/admin/watchlist", api::adminGetWatchlist)
            .delete("/api/admin/watchlist/{id}", api::adminDeleteWatchlistItem)
            .post("/api/admin/watchlist/{id}/toggle", api::adminToggleWatchlistItem)
            .get("/api/admin/data-broker", api::adminGetDataBroker)
            .post("/api/admin/data-broker", api::adminConfigureDataBroker)
            .get("/api/admin/brokers/{userBrokerId}/oauth-url", api::adminGetOAuthUrl)
            .get("/api/admin/brokers/oauth-callback", api::adminOAuthCallback)
            .get("/api/admin/brokers/{userBrokerId}/session", api::adminGetSession)
            .post("/api/admin/brokers/{userBrokerId}/disconnect", api::adminDisconnectBroker)
            .post("/api/admin/brokers/{userBrokerId}/test-connection", api::adminTestConnection)
            .post("/api/admin/brokers/{userBrokerId}/save-connection", api::adminSaveConnection)
            // FYERS OAuth v3 endpoints (auto-login flow)
            .get("/api/brokers/{userBrokerId}/fyers/login-url", api::fyersLoginUrl)
            .post("/api/fyers/oauth/exchange", api::fyersOAuthExchange)
            // Watchlist Template Management routes
            .get("/api/admin/watchlist-templates", api::adminGetWatchlistTemplates)
            .post("/api/admin/watchlist-templates", api::adminCreateTemplate)
            .delete("/api/admin/watchlist-templates/{templateId}", api::adminDeleteTemplate)
            .get("/api/admin/watchlist-templates/{templateId}/symbols", api::adminGetTemplateSymbols)
            .post("/api/admin/watchlist-templates/{templateId}/symbols", api::adminAddSymbolToTemplate)
            .delete("/api/admin/watchlist-templates/symbols/{symbolId}", api::adminDeleteSymbolFromTemplate)
            .post("/api/admin/watchlist-selected", api::adminCreateSelectedWatchlist)
            .get("/api/admin/watchlist-selected", api::adminGetSelectedWatchlists)
            .get("/api/admin/watchlist-selected/{selectedId}/symbols", api::adminGetSelectedSymbols)
            .delete("/api/admin/watchlist-selected/{selectedId}", api::adminDeleteSelectedWatchlist)
            .get("/api/admin/watchlist-default", api::adminGetDefaultWatchlist)
            .post("/api/admin/watchlist-sync", api::adminSyncWatchlists)
            // Instruments search
            .get("/api/instruments/search", api::searchInstruments)
            // MTF Config Management
            .get("/api/admin/mtf-config", mtfConfigHandler::getGlobalConfig)
            .put("/api/admin/mtf-config", mtfConfigHandler::updateGlobalConfig)
            .get("/api/admin/mtf-config/symbols", mtfConfigHandler::getAllSymbolConfigs)
            .get("/api/admin/mtf-config/symbols/{symbol}", mtfConfigHandler::getSymbolConfig)
            .put("/api/admin/mtf-config/symbols/{symbol}", mtfConfigHandler::upsertSymbolConfig)
            .delete("/api/admin/mtf-config/symbols/{symbol}", mtfConfigHandler::deleteSymbolConfig)
            // Trailing Stops Config Management
            .get("/api/admin/trailing-stops/config", adminConfigHandler::getTrailingStopsConfig)
            .post("/api/admin/trailing-stops/config", adminConfigHandler::updateTrailingStopsConfig)
            // Monitoring Dashboard API
            .get("/api/monitoring/system-health", monitoringHandler::getSystemHealth)
            .get("/api/monitoring/performance", monitoringHandler::getPerformance)
            .get("/api/monitoring/broker-status", monitoringHandler::getBrokerStatus)
            .get("/api/monitoring/exit-health", monitoringHandler::getExitHealth)
            .get("/api/monitoring/risk", monitoringHandler::getRisk)
            .get("/api/monitoring/errors", monitoringHandler::getErrors)
            .get("/api/monitoring/alerts", monitoringHandler::getAlerts)
            // Market Watch - accessible to all users
            .get("/api/market-watch", api::marketWatch)
            .get("/ws", wsHub.websocketHandler())
            .setFallbackHandler(exchange -> {
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain; charset=utf-8");
                exchange.getResponseSender().send(
                    "AnnuPaper Undertow v04\n\n" +
                    "Auth: POST /api/auth/login, /api/auth/register\n" +
                    "API:  GET /api/health, /api/bootstrap, /api/events, /api/brokers\n" +
                    "WS:   ws://localhost:" + port + "/ws?token=<jwt>\n"
                );
            });
        
        // CORS Handler
        io.undertow.server.HttpHandler corsHandler = exchange -> {
            exchange.getResponseHeaders()
                .put(io.undertow.util.HttpString.tryFromString("Access-Control-Allow-Origin"), "*")
                .put(io.undertow.util.HttpString.tryFromString("Access-Control-Allow-Methods"), "GET, POST, PUT, DELETE, OPTIONS")
                .put(io.undertow.util.HttpString.tryFromString("Access-Control-Allow-Headers"), "Content-Type, Authorization")
                .put(io.undertow.util.HttpString.tryFromString("Access-Control-Max-Age"), "3600");

            if (exchange.getRequestMethod().toString().equals("OPTIONS")) {
                exchange.setStatusCode(200);
                exchange.endExchange();
            } else {
                routes.handleRequest(exchange);
            }
        };

            Undertow server = Undertow.builder()
                .addHttpListener(port, "0.0.0.0")
                .setHandler(corsHandler)
                .build();

            server.start();
            log.info("AnnuPaper v04 started on http://localhost:{}/", port);

            Map<String, Object> demoPayload = new HashMap<>();
            demoPayload.put("message", "AnnuPaper v04 started");
            demoPayload.put("features", List.of("JWT", "brokers", "MTF", "fan-out"));
            eventService.emitGlobal(EventType.SYSTEM_STATUS, demoPayload, "SYSTEM");

            log.info("✓ HTTP API server started on port {}", port);
        } else {
            log.info("[RELAY] ⏭️ Skipping HTTP API server (collector mode - relay only)");
        }

        // ═══════════════════════════════════════════════════════════════
        // Startup Auto-Login: Check token validity and auto-open browser
        // ═══════════════════════════════════════════════════════════════
        // IMPORTANT: Runs AFTER server started (so callback endpoint works)
        // In collector mode: OAuth still needed for FYERS connection
        if (!collectorMode) {
            checkTokensAndAutoLogin(userBrokerRepo, sessionRepo, legacyBrokerFactory, fyersLoginOrchestrator);
        } else {
            log.info("[RELAY] Skipping auto-login check (not needed in collector mode)");
        }
    }
    
    private static void handleLogin(io.undertow.server.HttpServerExchange exchange, AuthService authService) {
        exchange.getRequestReceiver().receiveFullString((ex, body) -> {
            try {
                String email = extractJsonField(body, "email");
                String password = extractJsonField(body, "password");
                AuthService.LoginResult result = authService.login(email, password);
                
                ex.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                if (result.success()) {
                    ex.getResponseSender().send(String.format(
                        "{\"success\":true,\"token\":\"%s\",\"userId\":\"%s\",\"displayName\":\"%s\",\"role\":\"%s\"}",
                        result.token(), result.userId(), result.displayName(), result.role()
                    ));
                } else {
                    ex.setStatusCode(401);
                    ex.getResponseSender().send("{\"success\":false,\"error\":\"" + result.error() + "\"}");
                }
            } catch (Exception e) {
                ex.setStatusCode(400);
                ex.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
                ex.getResponseSender().send("{\"error\":\"Invalid request\"}");
            }
        });
    }
    
    private static void handleRegister(io.undertow.server.HttpServerExchange exchange, AuthService authService) {
        exchange.getRequestReceiver().receiveFullString((ex, body) -> {
            try {
                String email = extractJsonField(body, "email");
                String password = extractJsonField(body, "password");
                String displayName = extractJsonField(body, "displayName");
                AuthService.RegisterResult result = authService.register(email, password, displayName);
                
                ex.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                if (result.success()) {
                    ex.getResponseSender().send(String.format(
                        "{\"success\":true,\"token\":\"%s\",\"userId\":\"%s\"}", result.token(), result.userId()
                    ));
                } else {
                    ex.setStatusCode(400);
                    ex.getResponseSender().send("{\"success\":false,\"error\":\"" + result.error() + "\"}");
                }
            } catch (Exception e) {
                ex.setStatusCode(400);
                ex.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
                ex.getResponseSender().send("{\"error\":\"Invalid request\"}");
            }
        });
    }
    
    private static String extractJsonField(String json, String field) {
        String search = "\"" + field + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return null;
        return json.substring(start, end);
    }
    
    private static DataSource createDataSource() {
        String url = Env.get("DB_URL", "jdbc:postgresql://localhost:5432/annupaper");
        String user = Env.get("DB_USER", "postgres");
        String pass = Env.get("DB_PASS", "postgres");
        int maxPool = Env.getInt("DB_POOL_SIZE", 10);
        
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(pass);
        config.setMaximumPoolSize(maxPool);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(5000);
        config.setPoolName("annu-hikari");
        
        log.info("DB: url={}, user={}, pool={}", url, user, maxPool);
        return new HikariDataSource(config);
    }
    
    private static List<Candle> generateMockCandles(String symbol, TimeframeType tfType) {
        List<Candle> candles = new ArrayList<>();
        Random random = new Random(symbol.hashCode());
        BigDecimal basePrice = new BigDecimal("1000");
        int count = tfType.getLookback();
        long intervalMs = tfType.getCandleMinutes() * 60000L;
        long startTime = System.currentTimeMillis() - (count * intervalMs);
        
        for (int i = 0; i < count; i++) {
            double v = (random.nextDouble() - 0.5) * 0.04;
            BigDecimal open = basePrice.multiply(BigDecimal.valueOf(1 + v));
            BigDecimal close = basePrice.multiply(BigDecimal.valueOf(1 + (random.nextDouble() - 0.5) * 0.04));
            BigDecimal high = open.max(close).multiply(BigDecimal.valueOf(1 + random.nextDouble() * 0.01));
            BigDecimal low = open.min(close).multiply(BigDecimal.valueOf(1 - random.nextDouble() * 0.01));
            candles.add(Candle.of(symbol, tfType, Instant.ofEpochMilli(startTime + (i * intervalMs)),
                open.doubleValue(), high.doubleValue(), low.doubleValue(), close.doubleValue(), (long)(random.nextDouble() * 100000)));
            basePrice = close;
        }
        return candles;
    }

    private static void ensureAdminExists(AuthService authService) {
        if (!authService.adminExists()) {
            AuthService.RegisterResult result = authService.createAdminUser(
                "admin@annupaper.com",
                "admin123",
                "Administrator"
            );
            if (result.success()) {
                log.info("Admin user created: admin@annupaper.com ({})", result.userId());
            } else {
                log.error("Failed to create admin user: {}", result.error());
            }
        } else {
            log.info("Admin user already exists");
        }
    }

    /**
     * Reconcile historical DAILY candles for all watchlist symbols at startup.
     * Checks each symbol and fetches 252 trading days (~365 calendar days) of DAILY candles if missing.
     */
    private static void reconcileHistoricalData(
            DataSource dataSource,
            in.annupaper.service.candle.CandleFetcher candleFetcher,
            in.annupaper.repository.UserBrokerRepository userBrokerRepo,
            in.annupaper.repository.BrokerRepository brokerRepo,
            in.annupaper.repository.WatchlistRepository watchlistRepo) {

        log.info("[RECONCILIATION] ════════════════════════════════════════════════════════");
        log.info("[RECONCILIATION] Starting historical DAILY candles reconciliation");
        log.info("[RECONCILIATION] ════════════════════════════════════════════════════════");

        try {
            // Get data broker
            java.util.Optional<in.annupaper.domain.broker.UserBroker> dataBrokerOpt = userBrokerRepo.findDataBroker();
            if (dataBrokerOpt.isEmpty()) {
                log.warn("[RECONCILIATION] No data broker configured, skipping reconciliation");
                return;
            }

            in.annupaper.domain.broker.UserBroker dataBroker = dataBrokerOpt.get();
            java.util.Optional<in.annupaper.domain.broker.Broker> brokerOpt = brokerRepo.findById(dataBroker.brokerId());
            if (brokerOpt.isEmpty()) {
                log.warn("[RECONCILIATION] Broker not found: {}, skipping reconciliation", dataBroker.brokerId());
                return;
            }

            String brokerCode = brokerOpt.get().brokerCode();
            String userBrokerId = dataBroker.userBrokerId();

            // Get all unique watchlist symbols
            String symbolsSql = "SELECT DISTINCT symbol FROM watchlist WHERE deleted_at IS NULL AND enabled = true";
            java.util.List<String> allSymbols = new java.util.ArrayList<>();

            try (java.sql.Connection conn = dataSource.getConnection();
                 java.sql.PreparedStatement ps = conn.prepareStatement(symbolsSql);
                 java.sql.ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    allSymbols.add(rs.getString("symbol"));
                }
            }

            if (allSymbols.isEmpty()) {
                log.info("[RECONCILIATION] No watchlist symbols found, skipping reconciliation");
                return;
            }

            log.info("[RECONCILIATION] Found {} watchlist symbols to check", allSymbols.size());

            // Check which symbols are missing DAILY candles
            String checkSql = """
                SELECT symbol FROM (VALUES (?)) AS symbols(symbol)
                WHERE NOT EXISTS (
                    SELECT 1 FROM candles
                    WHERE candles.symbol = symbols.symbol
                    AND candles.timeframe = 'DAILY'
                    LIMIT 1
                )
                """;

            java.util.List<String> missingSymbols = new java.util.ArrayList<>();

            try (java.sql.Connection conn = dataSource.getConnection()) {
                for (String symbol : allSymbols) {
                    try (java.sql.PreparedStatement ps = conn.prepareStatement(checkSql)) {
                        ps.setString(1, symbol);
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                missingSymbols.add(symbol);
                            }
                        }
                    }
                }
            }

            if (missingSymbols.isEmpty()) {
                log.info("[RECONCILIATION] ✓ All symbols have DAILY candles, no fetch needed");
                log.info("[RECONCILIATION] ════════════════════════════════════════════════════════");
                return;
            }

            log.info("[RECONCILIATION] Found {} symbols missing DAILY candles", missingSymbols.size());
            log.info("[RECONCILIATION] Missing symbols: {}", missingSymbols);

            // Fetch historical DAILY candles for missing symbols
            // Fyers API limit: max 365 days for DAILY resolution
            java.time.Instant to = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS)
                    .minus(1, java.time.temporal.ChronoUnit.DAYS);
            java.time.Instant from = to.minus(365, java.time.temporal.ChronoUnit.DAYS);

            log.info("[RECONCILIATION] Fetching DAILY candles from {} to {} via {}", from, to, brokerCode);

            int successCount = 0;
            int failCount = 0;

            for (String symbol : missingSymbols) {
                try {
                    log.info("[RECONCILIATION] Fetching DAILY candles for {} (target: 252 trading days)", symbol);
                    candleFetcher.fetchHistorical(
                        userBrokerId,
                        brokerCode,
                        symbol,
                        TimeframeType.DAILY,
                        from,
                        to
                    ).join(); // Wait for completion
                    successCount++;
                    log.info("[RECONCILIATION] ✓ Successfully fetched DAILY candles for {}", symbol);
                } catch (Exception e) {
                    failCount++;
                    log.error("[RECONCILIATION] ✗ Failed to fetch DAILY candles for {}: {}", symbol, e.getMessage());
                }
            }

            log.info("[RECONCILIATION] ════════════════════════════════════════════════════════");
            log.info("[RECONCILIATION] ✓ COMPLETED: {} successful, {} failed", successCount, failCount);
            log.info("[RECONCILIATION] ════════════════════════════════════════════════════════");

        } catch (Exception e) {
            log.error("[RECONCILIATION] Fatal error during reconciliation: {}", e.getMessage(), e);
        }
    }

    /**
     * Setup tick stream subscription and recovery on startup.
     */
    private static void setupTickStreamAndRecovery(
            boolean collectorMode,
            in.annupaper.repository.UserBrokerRepository userBrokerRepo,
            in.annupaper.repository.BrokerRepository brokerRepo,
            in.annupaper.repository.WatchlistRepository watchlistRepo,
            BrokerAdapterFactory legacyBrokerFactory,
            TickCandleBuilder tickCandleBuilder,
            ExitSignalService exitSignalService,
            in.annupaper.service.candle.RecoveryManager recoveryManager,
            in.annupaper.service.candle.MtfBackfillService mtfBackfillService,
            in.annupaper.service.signal.MtfSignalGenerator mtfSignalGenerator) {

        log.info("[TICK STREAM] ════════════════════════════════════════════════════════");
        log.info("[TICK STREAM] Setting up tick stream subscription and recovery");
        log.info("[TICK STREAM] ════════════════════════════════════════════════════════");

        try {
            // Get data broker
            java.util.Optional<in.annupaper.domain.broker.UserBroker> dataBrokerOpt = userBrokerRepo.findDataBroker();
            if (dataBrokerOpt.isEmpty()) {
                log.warn("[TICK STREAM] No data broker configured, skipping tick subscription");
                return;
            }

            in.annupaper.domain.broker.UserBroker dataBroker = dataBrokerOpt.get();
            java.util.Optional<in.annupaper.domain.broker.Broker> brokerOpt = brokerRepo.findById(dataBroker.brokerId());
            if (brokerOpt.isEmpty()) {
                log.warn("[TICK STREAM] Broker not found: {}, skipping", dataBroker.brokerId());
                return;
            }

            String brokerCode = brokerOpt.get().brokerCode();
            String userBrokerId = dataBroker.userBrokerId();

            // Get broker adapter
            in.annupaper.broker.BrokerAdapter adapter = legacyBrokerFactory.getOrCreate(userBrokerId, brokerCode);
            if (adapter == null || !adapter.isConnected()) {
                log.warn("[TICK STREAM] Data broker not connected: {}", brokerCode);
                return;
            }

            // Get all enabled watchlist symbols from all users
            List<Watchlist> allWatchlists =
                watchlistRepo.findByUserBrokerId(dataBroker.userBrokerId());

            List<String> symbols = allWatchlists.stream()
                .filter(w -> w.enabled())
                .map(w -> w.symbol())
                .distinct()
                .toList();

            if (symbols.isEmpty()) {
                log.info("[TICK STREAM] No watchlist symbols found");
                return;
            }

            log.info("[TICK STREAM] Found {} symbols to subscribe", symbols.size());

            // Run recovery for all symbols
            log.info("[TICK STREAM] Running recovery for all symbols...");
            recoveryManager.recoverAll(symbols);
            log.info("[TICK STREAM] ✓ Recovery completed");

            // Check if running in Feed Collector mode (relay ticks to remote clients)
            if (collectorMode) {
                // FEED COLLECTOR MODE: Broadcast ticks to remote clients via WebSocket relay
                int relayPort = Integer.parseInt(System.getenv().getOrDefault("RELAY_PORT", "7071"));

                in.annupaper.feedrelay.TickRelayServer relayServer = new in.annupaper.feedrelay.TickRelayServer();
                relayServer.start(relayPort);
                log.info("[RELAY] ════════════════════════════════════════════════════════");
                log.info("[RELAY] FEED COLLECTOR MODE ACTIVE");
                log.info("[RELAY] Broadcasting ticks on ws://0.0.0.0:{}/ticks", relayPort);
                log.info("[RELAY] ════════════════════════════════════════════════════════");

                adapter.subscribeTicks(symbols, new in.annupaper.feedrelay.RelayBroadcastTickListener(relayServer));

                // Skip trading/candles/signals on VM (relay only)
                log.info("[RELAY] Skipping candle builder / signals / exits (collector mode)");
                log.info("[RELAY] ✓ Relay tick listener subscribed for {} symbols", symbols.size());
            } else {
                // FULL MODE: Normal operation with all services
                log.info("[TICK STREAM] Subscribing TickCandleBuilder to tick stream...");
                adapter.subscribeTicks(symbols, tickCandleBuilder);
                log.info("[TICK STREAM] ✓ TickCandleBuilder subscribed");

                log.info("[TICK STREAM] Subscribing ExitSignalService to tick stream...");
                adapter.subscribeTicks(symbols, exitSignalService);
                log.info("[TICK STREAM] ✓ ExitSignalService subscribed");

                log.info("[TICK STREAM] Subscribing MtfSignalGenerator to tick stream...");
                adapter.subscribeTicks(symbols, mtfSignalGenerator);
                log.info("[TICK STREAM] ✓ MtfSignalGenerator subscribed for near real-time signal analysis");
            }

            log.info("[TICK STREAM] ════════════════════════════════════════════════════════");
            log.info("[TICK STREAM] ✓ Tick stream setup complete for {} symbols", symbols.size());
            log.info("[TICK STREAM] ════════════════════════════════════════════════════════");

            // Backfill MTF candles (skip in collector mode - relay only)
            if (!collectorMode) {
                log.info("[TICK STREAM] ════════════════════════════════════════════════════════");
                log.info("[TICK STREAM] Starting MTF backfill for all symbols...");
                log.info("[TICK STREAM] ════════════════════════════════════════════════════════");

                try {
                    java.util.List<in.annupaper.service.candle.MtfBackfillService.MtfBackfillResult> backfillResults =
                        mtfBackfillService.backfillAllSymbols(userBrokerId);

                    int successCount = 0;
                    int failCount = 0;

                    for (var result : backfillResults) {
                        if (result.success()) {
                            log.info("[MTF BACKFILL] ✓ {}: {} candles backfilled", result.symbol(), result.candlesBackfilled());
                            successCount++;
                        } else {
                            log.warn("[MTF BACKFILL] ✗ {}: {}", result.symbol(), result.message());
                            failCount++;
                        }
                    }

                    log.info("[TICK STREAM] ════════════════════════════════════════════════════════");
                    log.info("[TICK STREAM] ✓ MTF backfill complete: {} success, {} failed", successCount, failCount);
                    log.info("[TICK STREAM] ════════════════════════════════════════════════════════");

                } catch (Exception backfillError) {
                    log.error("[TICK STREAM] MTF backfill failed: {}", backfillError.getMessage(), backfillError);
                }
            } else {
                log.info("[RELAY] Skipping MTF backfill (collector mode - relay only)");
            }

        } catch (Exception e) {
            log.error("[TICK STREAM] Fatal error during setup: {}", e.getMessage(), e);
        }
    }

    /**
     * Start scheduler for time-based candle finalizer.
     * Calls finalizeStaleCandles() every 2 seconds.
     */
    private static void startCandleFinalizerScheduler(TickCandleBuilder tickCandleBuilder) {
        log.info("[SCHEDULER] Starting candle finalizer (every 2 seconds)");

        java.util.concurrent.ScheduledExecutorService scheduler =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "candle-finalizer");
                t.setDaemon(true);
                return t;
            });

        scheduler.scheduleAtFixedRate(() -> {
            try {
                tickCandleBuilder.finalizeStaleCandles();
            } catch (Exception e) {
                log.error("[SCHEDULER] Error in candle finalizer: {}", e.getMessage());
            }
        }, 2, 2, java.util.concurrent.TimeUnit.SECONDS);

        log.info("[SCHEDULER] ✓ Candle finalizer scheduler started");
    }

    /**
     * Start scheduler for watchdog health checks.
     * Performs comprehensive health check and auto-healing every 2 minutes.
     */
    private static void startWatchdogScheduler(in.annupaper.service.WatchdogManager watchdogManager) {
        log.info("[WATCHDOG] Starting health check scheduler (every 2 minutes)");

        java.util.concurrent.ScheduledExecutorService scheduler =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "watchdog-health-check");
                t.setDaemon(true);
                return t;
            });

        scheduler.scheduleAtFixedRate(() -> {
            try {
                watchdogManager.performHealthCheck();
            } catch (Exception e) {
                log.error("[WATCHDOG] Error in health check: {}", e.getMessage(), e);
            }
        }, 2, 2, java.util.concurrent.TimeUnit.MINUTES);  // First run after 2 min, then every 2 min

        log.info("[WATCHDOG] ✓ Health check scheduler started");
    }

    /**
     * Start scheduler for MTF signal generation.
     * Analyzes all watchlist symbols for MTF confluence every minute during market hours.
     */
    private static void startMtfSignalScheduler(in.annupaper.service.signal.MtfSignalGenerator mtfSignalGenerator) {
        log.info("[MTF SCHEDULER] Starting signal generation scheduler (every 1 minute)");

        java.util.concurrent.ScheduledExecutorService scheduler =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "mtf-signal-generator");
                t.setDaemon(true);
                return t;
            });

        scheduler.scheduleAtFixedRate(() -> {
            try {
                mtfSignalGenerator.performSignalAnalysis();
            } catch (Exception e) {
                log.error("[MTF SCHEDULER] Error in signal generation: {}", e.getMessage(), e);
            }
        }, 1, 1, java.util.concurrent.TimeUnit.MINUTES);  // First run after 1 min, then every 1 min

        log.info("[MTF SCHEDULER] ✓ Signal generation scheduler started");
    }

    /**
     * Start OAuth state cleanup scheduler (every 10 minutes).
     * Removes expired oauth_states rows to prevent table growth.
     */
    private static void startOAuthStateCleanupScheduler(in.annupaper.repository.OAuthStateRepository oauthStateRepo) {
        java.util.concurrent.ScheduledExecutorService scheduler =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "OAuthStateCleanup");
                t.setDaemon(true);
                return t;
            });

        scheduler.scheduleAtFixedRate(() -> {
            try {
                int deleted = oauthStateRepo.cleanupExpired();
                if (deleted > 0) {
                    log.info("[OAUTH CLEANUP] Cleaned up {} expired OAuth states", deleted);
                }
            } catch (Exception e) {
                log.error("[OAUTH CLEANUP] Error cleaning up expired states: {}", e.getMessage(), e);
            }
        }, 10, 10, java.util.concurrent.TimeUnit.MINUTES);  // First run after 10 min, then every 10 min

        log.info("[OAUTH CLEANUP] ✓ OAuth state cleanup scheduler started (every 10 minutes)");
    }

    /**
     * Check token validity for all FYERS brokers and auto-open browser for login if needed.
     *
     * For each FYERS user_broker_id:
     * 1. Load latest session
     * 2. If token missing OR expires soon (< 60s):
     *    - Set broker state = LOGIN_REQUIRED (READ-ONLY mode)
     *    - Auto-open browser to FYERS login URL
     *    - Do NOT attempt WebSocket connect yet
     * 3. Else:
     *    - Adapter already connected in setupTickStreamAndRecovery
     *
     * IMPORTANT: Runs AFTER server started (so callback endpoint is available)
     */
    private static void checkTokensAndAutoLogin(
        in.annupaper.repository.UserBrokerRepository userBrokerRepo,
        in.annupaper.repository.UserBrokerSessionRepository sessionRepo,
        in.annupaper.broker.BrokerAdapterFactory legacyBrokerFactory,
        in.annupaper.service.oauth.FyersLoginOrchestrator fyersLoginOrchestrator
    ) {
        log.info("[STARTUP AUTO-LOGIN] Checking token validity for all FYERS brokers...");

        try {
            // Get all FYERS user-broker combinations
            java.util.List<in.annupaper.domain.broker.UserBroker> fyersBrokers =
                userBrokerRepo.findAll().stream()
                    .filter(ub -> ub.deletedAt() == null)
                    .filter(ub -> BrokerIds.FYERS.equalsIgnoreCase(ub.brokerId()))
                    .toList();

            if (fyersBrokers.isEmpty()) {
                log.info("[STARTUP AUTO-LOGIN] No FYERS brokers configured");
                return;
            }

            log.info("[STARTUP AUTO-LOGIN] Found {} FYERS broker(s) to check", fyersBrokers.size());

            for (in.annupaper.domain.broker.UserBroker ub : fyersBrokers) {
                String userBrokerId = ub.userBrokerId();

                // Load latest session
                java.util.Optional<in.annupaper.domain.broker.UserBrokerSession> sessionOpt =
                    sessionRepo.findActiveSession(userBrokerId);

                if (sessionOpt.isEmpty()) {
                    // No session exists - need login
                    log.warn("[STARTUP AUTO-LOGIN] No session found for userBrokerId={} → triggering login", userBrokerId);
                    triggerLogin(userBrokerId, fyersLoginOrchestrator);
                    continue;
                }

                in.annupaper.domain.broker.UserBrokerSession session = sessionOpt.get();

                // Check if token is missing or expires soon (< 60s)
                java.time.Instant now = java.time.Instant.now();
                java.time.Instant expiresIn60s = now.plusSeconds(60);

                if (session.tokenValidTill() == null || session.tokenValidTill().isBefore(expiresIn60s)) {
                    // Token expired or expires soon - need login
                    log.warn("[STARTUP AUTO-LOGIN] Token expired/expiring for userBrokerId={} (validTill={}) → triggering login",
                        userBrokerId, session.tokenValidTill());
                    triggerLogin(userBrokerId, fyersLoginOrchestrator);
                    continue;
                }

                // Token is valid - adapter should already be connected
                log.info("[STARTUP AUTO-LOGIN] ✅ Token valid for userBrokerId={} (expires: {})",
                    userBrokerId, session.tokenValidTill());
            }

        } catch (Exception e) {
            log.error("[STARTUP AUTO-LOGIN] Error checking tokens", e);
        }
    }

    /**
     * Trigger login for a user-broker by opening browser to FYERS login page.
     */
    private static void triggerLogin(
        String userBrokerId,
        in.annupaper.service.oauth.FyersLoginOrchestrator fyersLoginOrchestrator
    ) {
        try {
            log.info("[STARTUP AUTO-LOGIN] 🌐 Opening browser for FYERS login: userBrokerId={}", userBrokerId);

            boolean opened = fyersLoginOrchestrator.openBrowserLogin(userBrokerId);

            if (opened) {
                log.info("[STARTUP AUTO-LOGIN] ✅ Browser opened successfully for userBrokerId={}", userBrokerId);
                log.info("[STARTUP AUTO-LOGIN]    Please log in to FYERS. After login, callback will handle token exchange.");
            } else {
                // Browser open failed or throttled
                log.warn("[STARTUP AUTO-LOGIN] ⚠️ Browser open failed/throttled for userBrokerId={}", userBrokerId);

                // Generate login URL for manual copy-paste
                try {
                    in.annupaper.service.oauth.FyersLoginOrchestrator.LoginUrlResponse response =
                        fyersLoginOrchestrator.generateLoginUrl(userBrokerId);
                    log.warn("[STARTUP AUTO-LOGIN] 📋 MANUAL LOGIN URL:");
                    log.warn("[STARTUP AUTO-LOGIN]    {}", response.loginUrl());
                } catch (Exception e) {
                    log.error("[STARTUP AUTO-LOGIN] Failed to generate login URL: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("[STARTUP AUTO-LOGIN] Error triggering login for userBrokerId={}: {}",
                userBrokerId, e.getMessage());
        }
    }

    private App() {}
}
