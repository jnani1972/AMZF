package in.annupaper.transport.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import in.annupaper.auth.JwtService;
import in.annupaper.domain.broker.BrokerAdapter;
import in.annupaper.infrastructure.broker.BrokerAdapterFactory;
import in.annupaper.domain.broker.BrokerIds;
import in.annupaper.domain.broker.Broker;
import in.annupaper.domain.broker.BrokerRole;
import in.annupaper.domain.user.Portfolio;
import in.annupaper.domain.trade.TradeEvent;
import in.annupaper.domain.broker.UserBroker;
import in.annupaper.domain.data.Watchlist;
import in.annupaper.domain.broker.UserBrokerSession;
import in.annupaper.domain.repository.TradeEventRepository;
import in.annupaper.service.admin.AdminService;
import in.annupaper.service.oauth.BrokerOAuthService;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * HTTP API handlers with user authentication and scoped event access.
 */
public final class ApiHandlers {
    private static final Logger log = LoggerFactory.getLogger(ApiHandlers.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // JSON Response Keys
    private static final String JSON_SUCCESS = "success";
    private static final String JSON_MESSAGE = "message";
    private static final String JSON_DATA = "data";
    private static final String JSON_ERROR = "error";

    // Common Error Messages
    private static final String ERROR_UPDATE_USER_BROKER = "Failed to update user-broker";
    private static final String ERROR_UPDATE_PORTFOLIO = "Failed to update portfolio";
    private static final String ERROR_DELETE_PORTFOLIO = "Failed to delete portfolio";
    private static final String ERROR_UPDATE_WATCHLIST = "Failed to update watchlist item";

    // Common Success Messages
    private static final String SUCCESS_USER_BROKER_UPDATED = "User-broker updated successfully";
    private static final String SUCCESS_PORTFOLIO_UPDATED = "Portfolio updated successfully";
    private static final String SUCCESS_PORTFOLIO_DELETED = "Portfolio deleted successfully";
    private static final String SUCCESS_WATCHLIST_UPDATED = "Watchlist item updated successfully";

    private final TradeEventRepository eventRepo;
    private final Function<String, String> tokenValidator; // Authorization header -> userId
    private final JwtService jwtService;
    private final AdminService adminService;
    private final BrokerOAuthService oauthService;
    private final in.annupaper.service.oauth.FyersLoginOrchestrator fyersLoginOrchestrator;
    private final BrokerAdapterFactory brokerFactory;
    private final in.annupaper.service.InstrumentService instrumentService;

    // Tick stream dependencies for reconnection
    private final in.annupaper.domain.repository.UserBrokerRepository userBrokerRepo;
    private final in.annupaper.domain.repository.BrokerRepository brokerRepo;
    private final in.annupaper.domain.repository.WatchlistRepository watchlistRepo;
    private final in.annupaper.service.candle.TickCandleBuilder tickCandleBuilder;
    private final in.annupaper.service.signal.ExitSignalService exitSignalService;
    private final in.annupaper.service.candle.RecoveryManager recoveryManager;
    private final in.annupaper.service.candle.MtfBackfillService mtfBackfillService;
    private final in.annupaper.domain.repository.SignalRepository signalRepo;
    private final in.annupaper.domain.repository.TradeRepository tradeRepo;

    // Old constructor
    // public ApiHandlers(TradeEventRepository eventRepo, Function<String, String>
    // tokenValidator) {
    // this.eventRepo = eventRepo;
    // this.tokenValidator = tokenValidator;
    // }

    public ApiHandlers(TradeEventRepository eventRepo, Function<String, String> tokenValidator,
            JwtService jwtService, AdminService adminService, BrokerOAuthService oauthService,
            in.annupaper.service.oauth.FyersLoginOrchestrator fyersLoginOrchestrator,
            BrokerAdapterFactory brokerFactory, in.annupaper.service.InstrumentService instrumentService,
            in.annupaper.domain.repository.UserBrokerRepository userBrokerRepo,
            in.annupaper.domain.repository.BrokerRepository brokerRepo,
            in.annupaper.domain.repository.WatchlistRepository watchlistRepo,
            in.annupaper.service.candle.TickCandleBuilder tickCandleBuilder,
            in.annupaper.service.signal.ExitSignalService exitSignalService,
            in.annupaper.service.candle.RecoveryManager recoveryManager,
            in.annupaper.service.candle.MtfBackfillService mtfBackfillService,
            in.annupaper.domain.repository.SignalRepository signalRepo,
            in.annupaper.domain.repository.TradeRepository tradeRepo) {
        this.eventRepo = eventRepo;
        this.tokenValidator = tokenValidator;
        this.jwtService = jwtService;
        this.adminService = adminService;
        this.oauthService = oauthService;
        this.fyersLoginOrchestrator = fyersLoginOrchestrator;
        this.brokerFactory = brokerFactory;
        this.instrumentService = instrumentService;
        this.userBrokerRepo = userBrokerRepo;
        this.brokerRepo = brokerRepo;
        this.watchlistRepo = watchlistRepo;
        this.tickCandleBuilder = tickCandleBuilder;
        this.exitSignalService = exitSignalService;
        this.recoveryManager = recoveryManager;
        this.mtfBackfillService = mtfBackfillService;
        this.signalRepo = signalRepo;
        this.tradeRepo = tradeRepo;
    }

    /**
     * Reconnect data broker and setup tick stream after OAuth completion.
     * This is called after a successful OAuth callback to ensure the newly
     * authenticated broker adapter is connected and subscribed to ticks.
     */
    private void reconnectDataBrokerAndSetupTickStream(String userBrokerId) {
        try {
            log.info("[OAUTH] Reconnecting data broker {} after OAuth completion", userBrokerId);

            // Remove cached adapter to force recreation with new session
            brokerFactory.remove(userBrokerId);

            // Get data broker details
            java.util.Optional<in.annupaper.domain.broker.UserBroker> dataBrokerOpt = userBrokerRepo
                    .findById(userBrokerId);
            if (dataBrokerOpt.isEmpty()) {
                log.warn("[OAUTH] User broker {} not found, skipping reconnection", userBrokerId);
                return;
            }

            in.annupaper.domain.broker.UserBroker dataBroker = dataBrokerOpt.get();
            java.util.Optional<in.annupaper.domain.broker.Broker> brokerOpt = brokerRepo
                    .findById(dataBroker.brokerId());
            if (brokerOpt.isEmpty()) {
                log.warn("[OAUTH] Broker not found: {}, skipping reconnection", dataBroker.brokerId());
                return;
            }

            String brokerCode = brokerOpt.get().brokerCode();

            // Create new adapter (will auto-connect using new session)
            in.annupaper.domain.broker.BrokerAdapter adapter = brokerFactory.getOrCreate(userBrokerId, brokerCode);
            if (adapter == null || !adapter.isConnected()) {
                log.warn("[OAUTH] Failed to reconnect data broker: {}", brokerCode);
                return;
            }

            log.info("[OAUTH] Data broker {} reconnected successfully", userBrokerId);

            // Get all enabled watchlist symbols
            List<Watchlist> allWatchlists = watchlistRepo.findByUserBrokerId(dataBroker.userBrokerId());

            List<String> symbols = allWatchlists.stream()
                    .filter(w -> w.enabled())
                    .map(w -> w.symbol())
                    .distinct()
                    .toList();

            if (symbols.isEmpty()) {
                log.info("[OAUTH] No watchlist symbols found for tick subscription");
                return;
            }

            log.info("[OAUTH] Found {} symbols to subscribe", symbols.size());

            // Run recovery for all symbols
            log.info("[OAUTH] Running recovery for all symbols...");
            recoveryManager.recoverAll(symbols);
            log.info("[OAUTH] Recovery completed");

            // Subscribe tickCandleBuilder to tick stream
            log.info("[OAUTH] Subscribing TickCandleBuilder to tick stream...");
            adapter.subscribeTicks(symbols, tickCandleBuilder);
            log.info("[OAUTH] TickCandleBuilder subscribed");

            // Subscribe exitSignalService to tick stream
            log.info("[OAUTH] Subscribing ExitSignalService to tick stream...");
            adapter.subscribeTicks(symbols, exitSignalService);
            log.info("[OAUTH] ExitSignalService subscribed");

            log.info("[OAUTH] Tick stream setup complete for {} symbols", symbols.size());

            // Backfill MTF candles for all symbols
            log.info("[OAUTH] Starting MTF backfill for all symbols...");
            List<in.annupaper.service.candle.MtfBackfillService.MtfBackfillResult> backfillResults = mtfBackfillService
                    .backfillAllSymbols(userBrokerId);

            for (var result : backfillResults) {
                if (result.success()) {
                    log.info("[OAUTH] MTF backfill complete for {}: {} candles", result.symbol(),
                            result.candlesBackfilled());
                } else {
                    log.warn("[OAUTH] MTF backfill failed for {}: {}", result.symbol(), result.message());
                }
            }

        } catch (Exception e) {
            log.error("[OAUTH] Error during reconnection: {}", e.getMessage(), e);
        }
    }

    /**
     * GET /api/health
     * Enhanced to show feed status and READ-ONLY mode
     */
    public void health(HttpServerExchange exchange) {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");

        try {
            ObjectNode health = MAPPER.createObjectNode();
            health.put("status", "ok");
            health.put("ts", Instant.now().toString());

            // Add feed status for all connected brokers
            ArrayNode feeds = health.putArray("feeds");
            try {
                List<UserBroker> brokers = userBrokerRepo.findAll().stream()
                        .filter(ub -> ub.deletedAt() == null)
                        .toList();

                for (UserBroker ub : brokers) {
                    BrokerAdapter adapter = brokerFactory.get(ub.userBrokerId());
                    if (adapter != null) {
                        ObjectNode feedStatus = feeds.addObject();
                        feedStatus.put("userBrokerId", ub.userBrokerId());
                        feedStatus.put("broker", ub.brokerId());
                        feedStatus.put("connected", adapter.isConnected());

                        // Add FYERS-specific status (both SDK and raw adapters)
                        if (adapter instanceof in.annupaper.infrastructure.broker.adapters.FyersV3SdkAdapter) {
                            in.annupaper.infrastructure.broker.adapters.FyersV3SdkAdapter fyersAdapter = (in.annupaper.infrastructure.broker.adapters.FyersV3SdkAdapter) adapter;
                            feedStatus.put("adapterType", "SDK");
                            feedStatus.put("wsConnected", fyersAdapter.isWebSocketConnected());
                            feedStatus.put("canPlaceOrders", fyersAdapter.canPlaceOrders());
                            feedStatus.put("readOnlyMode", !fyersAdapter.canPlaceOrders());

                            // Add detailed connection state
                            feedStatus.put("feedStatus", computeFeedStatus(
                                    adapter.isConnected(),
                                    fyersAdapter.isWebSocketConnected(),
                                    fyersAdapter.getConnectionState(),
                                    fyersAdapter.getRetryCount(),
                                    fyersAdapter.getLastHttpStatus()));
                        } else if (adapter instanceof in.annupaper.infrastructure.broker.adapters.FyersAdapter) {
                            in.annupaper.infrastructure.broker.adapters.FyersAdapter fyersAdapter = (in.annupaper.infrastructure.broker.adapters.FyersAdapter) adapter;
                            feedStatus.put("adapterType", "RAW");
                            feedStatus.put("wsConnected", fyersAdapter.isWebSocketConnected());
                            feedStatus.put("canPlaceOrders", fyersAdapter.canPlaceOrders());
                            feedStatus.put("readOnlyMode", !fyersAdapter.canPlaceOrders());

                            // Add detailed connection state
                            String status = computeFeedStatus(
                                    adapter.isConnected(),
                                    fyersAdapter.isWebSocketConnected(),
                                    fyersAdapter.getConnectionState(),
                                    fyersAdapter.getRetryCount(),
                                    fyersAdapter.getLastHttpStatus());
                            feedStatus.put("feedStatus", status);

                            // Add retry info if connecting
                            if (fyersAdapter.getRetryCount() > 0) {
                                feedStatus.put("retryCount", fyersAdapter.getRetryCount());
                            }
                            if (fyersAdapter.getLastHttpStatus() != null) {
                                feedStatus.put("lastHttpStatus", fyersAdapter.getLastHttpStatus());
                            }
                            if (fyersAdapter.getLastErrorMessage() != null) {
                                feedStatus.put("lastError", fyersAdapter.getLastErrorMessage());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Error gathering feed status: {}", e.getMessage());
            }

            exchange.getResponseSender().send(health.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Health check error", e);
            exchange.getResponseSender().send(
                    "{\"status\":\"error\",\"ts\":\"" + Instant.now() + "\"}",
                    StandardCharsets.UTF_8);
        }
    }

    /**
     * Compute feed status string from adapter state.
     * Returns: LOGIN_REQUIRED, CONNECTING, CONNECTED, DOWN_503, DOWN_AUTH, etc.
     */
    private String computeFeedStatus(
            boolean connected,
            boolean wsConnected,
            String connectionState,
            int retryCount,
            Integer lastHttpStatus) {
        // If not connected at all (no session/credentials)
        if (!connected) {
            return "LOGIN_REQUIRED";
        }

        // If WebSocket is connected, we're good
        if (wsConnected && "CONNECTED".equals(connectionState)) {
            return "CONNECTED";
        }

        // If actively connecting with errors
        if ("CONNECTING".equals(connectionState)) {
            if (lastHttpStatus != null) {
                if (lastHttpStatus == 503) {
                    return "DOWN_503";
                } else if (lastHttpStatus == 401 || lastHttpStatus == 403) {
                    return "DOWN_AUTH";
                } else if (lastHttpStatus >= 500) {
                    return "DOWN_SERVER";
                } else if (lastHttpStatus >= 400) {
                    return "DOWN_CLIENT";
                }
            }
            return retryCount > 0 ? "CONNECTING (retry #" + retryCount + ")" : "CONNECTING";
        }

        // If disconnected but session exists
        if ("DISCONNECTED".equals(connectionState)) {
            return "DISCONNECTED";
        }

        // If reconnect required
        if ("RECONNECT_REQUIRED".equals(connectionState)) {
            return "RECONNECT_REQUIRED";
        }

        // Fallback
        return "UNKNOWN";
    }

    /**
     * GET /api/bootstrap
     * Returns user info, portfolio, and trade books.
     * Requires authentication.
     */
    public void bootstrap(HttpServerExchange exchange) {
        String userId = authenticate(exchange);
        if (userId == null) {
            unauthorized(exchange);
            return;
        }

        // Extract token to get role
        String token = null;
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }

        // Decode token to get role
        String role = "USER"; // default
        if (token != null) {
            try {
                var claims = jwtService.getClaims(token);
                if (claims != null) {
                    role = claims.role();
                    if (role == null)
                        role = "USER";
                }
            } catch (Exception e) {
                log.warn("Failed to decode token for role: {}", e.getMessage());
            }
        }

        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");

        // TODO: Fetch real data from repositories
        ObjectNode response = MAPPER.createObjectNode();

        ObjectNode user = MAPPER.createObjectNode();
        user.put("id", userId);
        user.put("name", "User " + userId);
        user.put("role", role); // ✅ Add role to user object
        response.set("user", user);

        ObjectNode portfolio = MAPPER.createObjectNode();
        portfolio.put("id", "P-" + userId);
        portfolio.put("available", 100000);
        portfolio.put("deployed", 0);
        response.set("portfolio", portfolio);

        ArrayNode brokers = MAPPER.createArrayNode();
        // TODO: Fetch user's brokers
        response.set("brokers", brokers);

        ArrayNode tradeBooks = MAPPER.createArrayNode();
        // TODO: Fetch user's trade books
        response.set("tradeBooks", tradeBooks);

        try {
            exchange.getResponseSender().send(MAPPER.writeValueAsString(response), StandardCharsets.UTF_8);
        } catch (Exception e) {
            serverError(exchange, e.getMessage());
        }
    }

    /**
     * GET /api/events?afterSeq=0&limit=200
     * Returns events visible to the authenticated user.
     * Filters: GLOBAL + USER(userId) + USER_BROKER(userId, *)
     */
    public void events(HttpServerExchange exchange) {
        String userId = authenticate(exchange);
        if (userId == null) {
            unauthorized(exchange);
            return;
        }

        // Parse query params
        Deque<String> afterSeqQ = exchange.getQueryParameters().get("afterSeq");
        Deque<String> limitQ = exchange.getQueryParameters().get("limit");
        Deque<String> userBrokerIdQ = exchange.getQueryParameters().get("userBrokerId");

        long afterSeq = afterSeqQ == null ? 0L : Long.parseLong(afterSeqQ.peekFirst());
        int limit = limitQ == null ? 200 : Integer.parseInt(limitQ.peekFirst());
        limit = Math.max(1, Math.min(limit, 2000));
        String userBrokerId = userBrokerIdQ == null ? null : userBrokerIdQ.peekFirst();

        // Fetch events (filtered by user)
        List<TradeEvent> events;
        if (userBrokerId != null) {
            events = eventRepo.listAfterSeqForUserBroker(afterSeq, limit, userId, userBrokerId);
        } else {
            events = eventRepo.listAfterSeqForUser(afterSeq, limit, userId);
        }

        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");

        try {
            ObjectNode response = MAPPER.createObjectNode();
            response.put("afterSeq", afterSeq);
            response.put("latestSeq", eventRepo.latestSeq());
            response.put("userId", userId);
            if (userBrokerId != null) {
                response.put("userBrokerId", userBrokerId);
            }

            ArrayNode eventsArray = MAPPER.createArrayNode();
            for (TradeEvent e : events) {
                ObjectNode eventNode = MAPPER.createObjectNode();
                eventNode.put("seq", e.seq());
                eventNode.put("type", e.type().name());
                eventNode.put("scope", e.scope().name());
                eventNode.set("payload", e.payload());
                eventNode.put("ts", e.ts().toString());
                if (e.signalId() != null)
                    eventNode.put("signalId", e.signalId());
                if (e.intentId() != null)
                    eventNode.put("intentId", e.intentId());
                if (e.tradeId() != null)
                    eventNode.put("tradeId", e.tradeId());
                if (e.orderId() != null)
                    eventNode.put("orderId", e.orderId());
                eventsArray.add(eventNode);
            }
            response.set("events", eventsArray);

            exchange.getResponseSender().send(MAPPER.writeValueAsString(response), StandardCharsets.UTF_8);
        } catch (Exception e) {
            serverError(exchange, e.getMessage());
        }
    }

    /**
     * GET /api/brokers
     * Returns user's broker connections.
     */
    public void brokers(HttpServerExchange exchange) {
        String userId = authenticate(exchange);
        if (userId == null) {
            unauthorized(exchange);
            return;
        }

        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");

        // TODO: Fetch user's brokers from repository
        try {
            ObjectNode response = MAPPER.createObjectNode();
            response.put("userId", userId);
            response.set("brokers", MAPPER.createArrayNode());
            exchange.getResponseSender().send(MAPPER.writeValueAsString(response), StandardCharsets.UTF_8);
        } catch (Exception e) {
            serverError(exchange, e.getMessage());
        }
    }

    /**
     * GET /api/signals?status=PUBLISHED
     * Returns signals, optionally filtered by status.
     * Signals are visible to all authenticated users.
     */
    public void signals(HttpServerExchange exchange) {
        String userId = authenticate(exchange);
        if (userId == null) {
            unauthorized(exchange);
            return;
        }

        try {
            Map<String, Deque<String>> params = exchange.getQueryParameters();
            String statusFilter = params.containsKey("status") ? params.get("status").getFirst() : null;

            List<in.annupaper.domain.signal.Signal> signals;
            if (statusFilter != null) {
                signals = signalRepo.findByStatus(statusFilter);
            } else {
                // Default to PUBLISHED signals only
                signals = signalRepo.findByStatus("PUBLISHED");
            }

            ArrayNode signalsArray = MAPPER.createArrayNode();

            for (in.annupaper.domain.signal.Signal s : signals) {
                ObjectNode sNode = MAPPER.createObjectNode();
                sNode.put("id", s.signalId());
                sNode.put("symbol", s.symbol());
                sNode.put("direction", s.direction().name());
                sNode.put("confluenceType", s.confluenceType());
                if (s.confluenceScore() != null) {
                    sNode.put("confluenceScore", s.confluenceScore().doubleValue());
                }
                if (s.effectiveFloor() != null) {
                    sNode.put("effectiveFloor", s.effectiveFloor().doubleValue());
                }
                if (s.effectiveCeiling() != null) {
                    sNode.put("effectiveCeiling", s.effectiveCeiling().doubleValue());
                }
                if (s.refPrice() != null) {
                    sNode.put("refPrice", s.refPrice().doubleValue());
                }
                if (s.entryLow() != null) {
                    sNode.put("entryLow", s.entryLow().doubleValue());
                }
                if (s.entryHigh() != null) {
                    sNode.put("entryHigh", s.entryHigh().doubleValue());
                }
                sNode.put("status", s.status());
                sNode.put("generatedAt", s.generatedAt().toString());
                if (s.expiresAt() != null) {
                    sNode.put("expiresAt", s.expiresAt().toString());
                }
                signalsArray.add(sNode);
            }

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(signalsArray.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Error fetching signals: {}", e.getMessage(), e);
            serverError(exchange, "Failed to fetch signals: " + e.getMessage());
        }
    }

    /**
     * GET /api/intents?signalId=xxx
     * Returns trade intents for the user.
     */
    public void intents(HttpServerExchange exchange) {
        String userId = authenticate(exchange);
        if (userId == null) {
            unauthorized(exchange);
            return;
        }

        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");

        // TODO: Fetch user's intents from repository
        try {
            ObjectNode response = MAPPER.createObjectNode();
            response.put("userId", userId);
            response.set("intents", MAPPER.createArrayNode());
            exchange.getResponseSender().send(MAPPER.writeValueAsString(response), StandardCharsets.UTF_8);
        } catch (Exception e) {
            serverError(exchange, e.getMessage());
        }
    }

    /**
     * GET /api/portfolios
     * Returns user's portfolios.
     */
    public void portfolios(HttpServerExchange exchange) {
        String userId = authenticate(exchange);
        if (userId == null) {
            unauthorized(exchange);
            return;
        }

        try {
            List<Portfolio> portfolios = adminService.getUserPortfolios(userId);
            ArrayNode portfoliosArray = MAPPER.createArrayNode();

            for (Portfolio p : portfolios) {
                ObjectNode pNode = MAPPER.createObjectNode();
                pNode.put("id", p.portfolioId());
                pNode.put("userId", p.userId());
                pNode.put("name", p.name());
                if (p.totalCapital() != null) {
                    pNode.put("totalCapital", p.totalCapital().doubleValue());
                }
                if (p.reservedCapital() != null) {
                    pNode.put("reservedCapital", p.reservedCapital().doubleValue());
                }
                BigDecimal availableCap = p.availableCapital();
                if (availableCap != null) {
                    pNode.put("availableCapital", availableCap.doubleValue());
                }
                pNode.put("status", p.status());
                pNode.put("paused", p.paused());
                pNode.put("createdAt", p.createdAt().toString());
                if (p.updatedAt() != null) {
                    pNode.put("updatedAt", p.updatedAt().toString());
                }
                portfoliosArray.add(pNode);
            }

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(portfoliosArray.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Error fetching portfolios for user {}: {}", userId, e.getMessage(), e);
            serverError(exchange, "Failed to fetch portfolios: " + e.getMessage());
        }
    }

    /**
     * GET /api/trades?status=open|closed
     * Returns user's trades, optionally filtered by status.
     */
    public void trades(HttpServerExchange exchange) {
        String userId = authenticate(exchange);
        if (userId == null) {
            unauthorized(exchange);
            return;
        }

        try {
            Map<String, Deque<String>> params = exchange.getQueryParameters();
            String statusFilter = params.containsKey("status") ? params.get("status").getFirst() : null;

            List<in.annupaper.domain.trade.Trade> trades;
            if (statusFilter != null) {
                // Filter by user and status
                trades = tradeRepo.findByUserId(userId).stream()
                        .filter(t -> statusFilter.equalsIgnoreCase(t.status()))
                        .toList();
            } else {
                trades = tradeRepo.findByUserId(userId);
            }

            ArrayNode tradesArray = MAPPER.createArrayNode();

            for (in.annupaper.domain.trade.Trade t : trades) {
                ObjectNode tNode = MAPPER.createObjectNode();
                tNode.put("id", t.tradeId());
                tNode.put("portfolioId", t.portfolioId());
                tNode.put("symbol", t.symbol());
                tNode.put("direction", t.direction());
                tNode.put("quantity", t.entryQty());
                if (t.entryPrice() != null) {
                    tNode.put("entryPrice", t.entryPrice().doubleValue());
                }
                if (t.exitPrice() != null) {
                    tNode.put("exitPrice", t.exitPrice().doubleValue());
                }
                if (t.realizedPnl() != null) {
                    tNode.put("pnl", t.realizedPnl().doubleValue());
                } else if (t.unrealizedPnl() != null) {
                    tNode.put("pnl", t.unrealizedPnl().doubleValue());
                }
                tNode.put("status", t.status());
                tNode.put("entryTime", t.entryTimestamp().toString());
                if (t.exitTimestamp() != null) {
                    tNode.put("exitTime", t.exitTimestamp().toString());
                }
                tradesArray.add(tNode);
            }

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(tradesArray.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Error fetching trades for user {}: {}", userId, e.getMessage(), e);
            serverError(exchange, "Failed to fetch trades: " + e.getMessage());
        }
    }

    /**
     * GET /api/watchlists
     * Returns user's watchlists.
     */
    public void watchlists(HttpServerExchange exchange) {
        String userId = authenticate(exchange);
        if (userId == null) {
            unauthorized(exchange);
            return;
        }

        try {
            List<Watchlist> watchlists = watchlistRepo.findByUserId(userId);
            ArrayNode watchlistsArray = MAPPER.createArrayNode();

            for (Watchlist w : watchlists) {
                ObjectNode wNode = MAPPER.createObjectNode();
                if (w.id() != null) {
                    wNode.put("id", w.id().toString());
                }
                wNode.put("symbol", w.symbol());
                wNode.put("enabled", w.enabled());
                if (w.lotSize() != null) {
                    wNode.put("lotSize", w.lotSize());
                }
                if (w.tickSize() != null) {
                    wNode.put("tickSize", w.tickSize().doubleValue());
                }
                if (w.lastPrice() != null) {
                    wNode.put("lastPrice", w.lastPrice().doubleValue());
                }
                if (w.lastTickTime() != null) {
                    wNode.put("lastTickTime", w.lastTickTime().toString());
                }
                if (w.addedAt() != null) {
                    wNode.put("addedAt", w.addedAt().toString());
                }
                if (w.updatedAt() != null) {
                    wNode.put("updatedAt", w.updatedAt().toString());
                }
                watchlistsArray.add(wNode);
            }

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(watchlistsArray.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Error fetching watchlists for user {}: {}", userId, e.getMessage(), e);
            serverError(exchange, "Failed to fetch watchlists: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ADMIN ENDPOINTS
    // ═══════════════════════════════════════════════════════════════

    /**
     * GET /api/admin/users - List all users (admin only).
     */
    public void adminGetUsers(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        try {
            List<AdminService.User> users = adminService.getAllUsers();
            ArrayNode usersArray = MAPPER.createArrayNode();

            for (AdminService.User user : users) {
                ObjectNode userNode = MAPPER.createObjectNode();
                userNode.put("userId", user.userId());
                userNode.put("email", user.email());
                userNode.put("displayName", user.displayName());
                userNode.put("role", user.role());
                userNode.put("status", user.status());
                userNode.put("createdAt", user.createdAt().toString());
                usersArray.add(userNode);
            }

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(usersArray.toString(), StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Error getting users: {}", e.getMessage(), e);
            serverError(exchange, "Failed to get users");
        }
    }

    /**
     * PUT /api/admin/users/:userId - Update user details (admin only).
     */
    public void adminUpdateUser(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        String userId = exchange.getAttachment(io.undertow.util.PathTemplateMatch.ATTACHMENT_KEY)
                .getParameters().get("userId");

        exchange.getRequestReceiver().receiveFullString((ex, body) -> {
            try {
                JsonNode json = MAPPER.readTree(body);
                String displayName = json.get("displayName").asText();
                String role = json.get("role").asText();

                adminService.updateUser(userId, displayName, role);

                ObjectNode response = MAPPER.createObjectNode();
                response.put("success", true);
                response.put("message", "User updated successfully");

                ex.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                ex.getResponseSender().send(response.toString(), StandardCharsets.UTF_8);

            } catch (Exception e) {
                log.error("Error updating user: {}", e.getMessage(), e);
                serverError(ex, "Failed to update user");
            }
        }, StandardCharsets.UTF_8);
    }

    /**
     * POST /api/admin/users/:userId/toggle - Toggle user status between ACTIVE and
     * SUSPENDED (admin only).
     */
    public void adminToggleUserStatus(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        try {
            String userId = exchange.getAttachment(io.undertow.util.PathTemplateMatch.ATTACHMENT_KEY)
                    .getParameters().get("userId");

            String newStatus = adminService.toggleUserStatus(userId);

            ObjectNode response = MAPPER.createObjectNode();
            response.put("success", true);
            response.put("status", newStatus);
            response.put("message", "User status toggled to " + newStatus);

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(response.toString(), StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Error toggling user status: {}", e.getMessage(), e);
            serverError(exchange, "Failed to toggle user status");
        }
    }

    /**
     * DELETE /api/admin/users/:userId - Soft delete user (admin only).
     */
    public void adminDeleteUser(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        try {
            String userId = exchange.getAttachment(io.undertow.util.PathTemplateMatch.ATTACHMENT_KEY)
                    .getParameters().get("userId");

            adminService.deleteUser(userId);

            ObjectNode response = MAPPER.createObjectNode();
            response.put("success", true);
            response.put("message", "User deleted successfully");

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(response.toString(), StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Error deleting user: {}", e.getMessage(), e);
            serverError(exchange, "Failed to delete user");
        }
    }

    /**
     * GET /api/admin/brokers - List all broker definitions (admin only).
     */
    public void adminGetBrokers(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        try {
            List<Broker> brokers = adminService.getAllBrokers();
            ArrayNode brokersArray = MAPPER.createArrayNode();

            for (Broker broker : brokers) {
                ObjectNode brokerNode = MAPPER.createObjectNode();
                brokerNode.put("brokerId", broker.brokerId());
                brokerNode.put("brokerCode", broker.brokerCode());
                brokerNode.put("brokerName", broker.brokerName());
                brokerNode.put("status", broker.status());
                brokersArray.add(brokerNode);
            }

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(brokersArray.toString(), StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Error getting brokers: {}", e.getMessage(), e);
            serverError(exchange, "Failed to get brokers");
        }
    }

    /**
     * GET /api/admin/user-brokers - Get all user-broker combinations (admin only).
     */
    public void adminGetUserBrokers(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        try {
            List<UserBroker> userBrokers = adminService.getAllUserBrokers();
            List<AdminService.User> users = adminService.getAllUsers();
            List<Broker> brokers = adminService.getAllBrokers();

            // Create a map of userId -> displayName for quick lookup
            Map<String, String> userDisplayNames = new HashMap<>();
            for (AdminService.User user : users) {
                userDisplayNames.put(user.userId(), user.displayName());
            }

            // Create a map of brokerId -> brokerName for quick lookup
            Map<String, String> brokerNames = new HashMap<>();
            for (Broker broker : brokers) {
                brokerNames.put(broker.brokerId(), broker.brokerName());
            }

            ArrayNode ubArray = MAPPER.createArrayNode();

            for (UserBroker ub : userBrokers) {
                ObjectNode ubNode = MAPPER.createObjectNode();
                ubNode.put("userBrokerId", ub.userBrokerId());
                ubNode.put("userId", ub.userId());
                ubNode.put("displayName", userDisplayNames.getOrDefault(ub.userId(), "Unknown"));
                ubNode.put("brokerId", ub.brokerId());
                ubNode.put("brokerName", brokerNames.getOrDefault(ub.brokerId(), "Unknown"));
                ubNode.put("role", ub.role().name());
                ubNode.put("connected", ub.connected());
                if (ub.lastConnected() != null) {
                    ubNode.put("lastConnected", ub.lastConnected().toString());
                }
                if (ub.connectionError() != null) {
                    ubNode.put("connectionError", ub.connectionError());
                }
                ubNode.put("enabled", ub.enabled());
                ubNode.put("status", ub.status());
                ubNode.put("createdAt", ub.createdAt().toString());
                ubArray.add(ubNode);
            }

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(ubArray.toString(), StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Error getting user-brokers: {}", e.getMessage(), e);
            serverError(exchange, "Failed to get user-brokers");
        }
    }

    /**
     * DELETE /api/admin/user-brokers/{userBrokerId} - Delete user-broker (admin
     * only).
     */
    public void adminDeleteUserBroker(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        try {
            String userBrokerId = exchange.getAttachment(io.undertow.util.PathTemplateMatch.ATTACHMENT_KEY)
                    .getParameters().get("userBrokerId");

            adminService.deleteUserBroker(userBrokerId);

            ObjectNode response = MAPPER.createObjectNode();
            response.put("success", true);
            response.put("message", "User-broker deleted successfully");

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(response.toString(), StandardCharsets.UTF_8);

        } catch (IllegalArgumentException e) {
            badRequest(exchange, e.getMessage());
        } catch (Exception e) {
            log.error("Error deleting user-broker: {}", e.getMessage(), e);
            serverError(exchange, "Failed to delete user-broker");
        }
    }

    /**
     * PATCH /api/admin/user-brokers/{userBrokerId}/toggle - Toggle enabled status
     * (admin only).
     */
    public void adminToggleUserBroker(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        try {
            String userBrokerId = exchange.getAttachment(io.undertow.util.PathTemplateMatch.ATTACHMENT_KEY)
                    .getParameters().get("userBrokerId");

            adminService.toggleUserBrokerEnabled(userBrokerId);

            ObjectNode response = MAPPER.createObjectNode();
            response.put("success", true);
            response.put("message", "User-broker status toggled successfully");

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(response.toString(), StandardCharsets.UTF_8);

        } catch (IllegalArgumentException e) {
            badRequest(exchange, e.getMessage());
        } catch (Exception e) {
            log.error("Error toggling user-broker: {}", e.getMessage(), e);
            serverError(exchange, "Failed to toggle user-broker");
        }
    }

    /**
     * PUT /api/admin/user-brokers/{userBrokerId} - Update user-broker role and
     * enabled status (admin only).
     */
    public void adminUpdateUserBroker(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        String userBrokerId = exchange.getAttachment(io.undertow.util.PathTemplateMatch.ATTACHMENT_KEY)
                .getParameters().get("userBrokerId");

        exchange.getRequestReceiver().receiveFullString((ex, body) -> {
            try {
                JsonNode json = MAPPER.readTree(body);

                // Parse role and enabled from request
                BrokerRole role = null;
                if (json.has("role") && !json.get("role").isNull()) {
                    role = BrokerRole.valueOf(json.get("role").asText());
                }

                Boolean enabled = null;
                if (json.has("enabled") && !json.get("enabled").isNull()) {
                    enabled = json.get("enabled").asBoolean();
                }

                UserBroker updated = adminService.updateUserBroker(userBrokerId, role, enabled);

                ObjectNode response = MAPPER.createObjectNode();
                response.put(JSON_SUCCESS, true);
                response.set(JSON_DATA, MAPPER.valueToTree(updated));
                response.put(JSON_MESSAGE, SUCCESS_USER_BROKER_UPDATED);

                ex.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                ex.getResponseSender().send(response.toString(), StandardCharsets.UTF_8);

            } catch (IllegalArgumentException e) {
                badRequest(ex, e.getMessage());
            } catch (Exception e) {
                log.error("Error updating user-broker: {}", e.getMessage(), e);
                serverError(ex, ERROR_UPDATE_USER_BROKER);
            }
        }, StandardCharsets.UTF_8);
    }

    /**
     * POST /api/admin/user-brokers - Link broker to user (admin only).
     */
    public void adminCreateUserBroker(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        exchange.getRequestReceiver().receiveFullString((ex, body) -> {
            try {
                JsonNode json = MAPPER.readTree(body);
                String userId = json.get("userId").asText();
                String brokerId = json.get("brokerId").asText();
                JsonNode credentials = json.get("credentials");
                boolean isDataBroker = json.has("isDataBroker") && json.get("isDataBroker").asBoolean();

                String userBrokerId = adminService.createUserBroker(userId, brokerId, credentials, isDataBroker);

                ObjectNode response = MAPPER.createObjectNode();
                response.put("success", true);
                response.put("userBrokerId", userBrokerId);

                ex.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                ex.getResponseSender().send(response.toString(), StandardCharsets.UTF_8);

            } catch (Exception e) {
                log.error("Error creating user-broker: {}", e.getMessage(), e);
                serverError(ex, "Failed to create user-broker");
            }
        });
    }

    /**
     * POST /api/admin/portfolios - Create user portfolio (admin only).
     */
    public void adminCreatePortfolio(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        exchange.getRequestReceiver().receiveFullString((ex, body) -> {
            try {
                JsonNode json = MAPPER.readTree(body);
                String userId = json.get("userId").asText();
                String name = json.has("name") ? json.get("name").asText() : "Main Portfolio";
                BigDecimal totalCapital = new BigDecimal(json.get("totalCapital").asText());

                String portfolioId = adminService.createPortfolio(userId, name, totalCapital);

                ObjectNode response = MAPPER.createObjectNode();
                response.put("success", true);
                response.put("portfolioId", portfolioId);

                ex.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                ex.getResponseSender().send(response.toString(), StandardCharsets.UTF_8);

            } catch (Exception e) {
                log.error("Error creating portfolio: {}", e.getMessage(), e);
                serverError(ex, "Failed to create portfolio");
            }
        });
    }

    /**
     * GET /api/admin/portfolios?userId=X - Get user portfolios (admin only).
     */
    public void adminGetPortfolios(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        try {
            Deque<String> userIdParam = exchange.getQueryParameters().get("userId");
            if (userIdParam == null || userIdParam.isEmpty()) {
                exchange.setStatusCode(400);
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                exchange.getResponseSender().send("{\"error\":\"userId parameter required\"}", StandardCharsets.UTF_8);
                return;
            }

            String userId = userIdParam.peekFirst();
            List<Portfolio> portfolios = adminService.getUserPortfolios(userId);
            ArrayNode portfoliosArray = MAPPER.createArrayNode();

            for (Portfolio portfolio : portfolios) {
                ObjectNode portfolioNode = MAPPER.createObjectNode();
                portfolioNode.put("portfolioId", portfolio.portfolioId());
                portfolioNode.put("name", portfolio.name());
                portfolioNode.put("totalCapital", portfolio.totalCapital().toString());
                portfolioNode.put("availableCapital", portfolio.availableCapital().toString());
                portfolioNode.put("status", portfolio.status());
                portfoliosArray.add(portfolioNode);
            }

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(portfoliosArray.toString(), StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Error getting portfolios: {}", e.getMessage(), e);
            serverError(exchange, "Failed to get portfolios");
        }
    }

    /**
     * PUT /api/admin/portfolios/{portfolioId} - Update portfolio (admin only).
     */
    public void adminUpdatePortfolio(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        String portfolioId = exchange.getAttachment(io.undertow.util.PathTemplateMatch.ATTACHMENT_KEY)
                .getParameters().get("portfolioId");

        exchange.getRequestReceiver().receiveFullString((ex, body) -> {
            try {
                JsonNode json = MAPPER.readTree(body);

                // Parse name and capital from request
                String name = json.has("name") && !json.get("name").isNull() ? json.get("name").asText() : null;
                BigDecimal capital = json.has("capital") && !json.get("capital").isNull()
                        ? new BigDecimal(json.get("capital").asText())
                        : null;

                Portfolio updated = adminService.updatePortfolio(portfolioId, name, capital);

                ObjectNode response = MAPPER.createObjectNode();
                response.put(JSON_SUCCESS, true);
                response.set(JSON_DATA, MAPPER.valueToTree(updated));
                response.put(JSON_MESSAGE, SUCCESS_PORTFOLIO_UPDATED);

                ex.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                ex.getResponseSender().send(response.toString(), StandardCharsets.UTF_8);

            } catch (IllegalArgumentException e) {
                badRequest(ex, e.getMessage());
            } catch (Exception e) {
                log.error("Error updating portfolio: {}", e.getMessage(), e);
                serverError(ex, ERROR_UPDATE_PORTFOLIO);
            }
        }, StandardCharsets.UTF_8);
    }

    /**
     * DELETE /api/admin/portfolios/{portfolioId} - Soft delete portfolio (admin
     * only).
     */
    public void adminDeletePortfolio(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        try {
            String portfolioId = exchange.getAttachment(io.undertow.util.PathTemplateMatch.ATTACHMENT_KEY)
                    .getParameters().get("portfolioId");

            adminService.deletePortfolio(portfolioId);

            ObjectNode response = MAPPER.createObjectNode();
            response.put(JSON_SUCCESS, true);
            response.put(JSON_MESSAGE, SUCCESS_PORTFOLIO_DELETED);

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(response.toString(), StandardCharsets.UTF_8);

        } catch (IllegalArgumentException e) {
            badRequest(exchange, e.getMessage());
        } catch (Exception e) {
            log.error("Error deleting portfolio: {}", e.getMessage(), e);
            serverError(exchange, ERROR_DELETE_PORTFOLIO);
        }
    }

    /**
     * POST /api/admin/watchlist - Add watchlist symbol (admin only).
     * Accepts userId or userBrokerId in request body.
     */
    public void adminAddWatchlist(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        exchange.getRequestReceiver().receiveFullString((ex, body) -> {
            try {
                JsonNode json = MAPPER.readTree(body);

                // Accept either userId or userBrokerId
                String userBrokerId = null;
                String userId = null;

                if (json.has("userBrokerId")) {
                    userBrokerId = json.get("userBrokerId").asText();
                    // Get userId from userBrokerId for validation
                    var userBrokerOpt = userBrokerRepo.findById(userBrokerId);
                    if (userBrokerOpt.isPresent()) {
                        userId = userBrokerOpt.get().userId();
                    }
                } else if (json.has("userId")) {
                    userId = json.get("userId").asText();
                    // Find a userBrokerId for this userId
                    var userBrokers = userBrokerRepo.findByUserId(userId);
                    if (!userBrokers.isEmpty()) {
                        userBrokerId = userBrokers.get(0).userBrokerId();
                    } else {
                        ex.setStatusCode(400);
                        ex.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                        ex.getResponseSender().send("{\"error\":\"User has no brokers configured\"}",
                                StandardCharsets.UTF_8);
                        return;
                    }
                }

                if (userBrokerId == null || userId == null) {
                    ex.setStatusCode(400);
                    ex.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                    ex.getResponseSender().send("{\"error\":\"userId or userBrokerId required\"}",
                            StandardCharsets.UTF_8);
                    return;
                }

                // Validate user is ACTIVE before adding watchlist item
                adminService.validateUserIsActive(userId);

                String symbol = json.get("symbol").asText();
                adminService.addWatchlistSymbol(userBrokerId, symbol);

                ObjectNode response = MAPPER.createObjectNode();
                response.put("success", true);

                ex.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                ex.getResponseSender().send(response.toString(), StandardCharsets.UTF_8);

            } catch (Exception e) {
                log.error("Error adding watchlist: {}", e.getMessage(), e);
                serverError(ex, "Failed to add watchlist");
            }
        });
    }

    /**
     * GET /api/admin/watchlist?userId=X - Get user watchlist (admin only).
     * If userId parameter is not provided, returns all watchlists.
     */
    public void adminGetWatchlist(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        try {
            Deque<String> userIdParam = exchange.getQueryParameters().get("userId");
            List<Watchlist> watchlist;

            if (userIdParam == null || userIdParam.isEmpty()) {
                // No userId provided - return all watchlists
                watchlist = adminService.getAllWatchlists();
            } else {
                // userId provided - return watchlists for that user
                String userId = userIdParam.peekFirst();
                watchlist = adminService.getUserWatchlist(userId);
            }

            ArrayNode watchlistArray = MAPPER.createArrayNode();

            for (Watchlist w : watchlist) {
                ObjectNode wNode = MAPPER.createObjectNode();
                wNode.put("id", w.id());
                wNode.put("userBrokerId", w.userBrokerId());
                wNode.put("symbol", w.symbol());
                wNode.put("lotSize", w.lotSize() != null ? w.lotSize() : 1);
                wNode.put("tickSize", w.tickSize() != null ? w.tickSize().doubleValue() : 0.05);
                wNode.put("isCustom", w.isCustom());
                wNode.put("enabled", w.enabled());
                watchlistArray.add(wNode);
            }

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(watchlistArray.toString(), StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Error getting watchlist: {}", e.getMessage(), e);
            serverError(exchange, "Failed to get watchlist");
        }
    }

    /**
     * DELETE /api/admin/watchlist/{id} - Delete watchlist item (admin only).
     */
    public void adminDeleteWatchlistItem(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        try {
            String idStr = exchange.getAttachment(io.undertow.util.PathTemplateMatch.ATTACHMENT_KEY)
                    .getParameters().get("id");
            Long id = Long.parseLong(idStr);

            adminService.deleteWatchlistItem(id);

            ObjectNode response = MAPPER.createObjectNode();
            response.put("success", true);
            response.put("message", "Watchlist item deleted successfully");

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(response.toString(), StandardCharsets.UTF_8);

        } catch (NumberFormatException e) {
            badRequest(exchange, "Invalid watchlist ID");
        } catch (Exception e) {
            log.error("Error deleting watchlist item: {}", e.getMessage(), e);
            serverError(exchange, "Failed to delete watchlist item");
        }
    }

    /**
     * PUT /api/admin/watchlist/{id} - Update watchlist item (admin only).
     */
    public void adminUpdateWatchlistItem(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        String idStr = exchange.getAttachment(io.undertow.util.PathTemplateMatch.ATTACHMENT_KEY)
                .getParameters().get("id");

        exchange.getRequestReceiver().receiveFullString((ex, body) -> {
            try {
                Long id = Long.parseLong(idStr);
                JsonNode json = MAPPER.readTree(body);

                // Parse lotSize, tickSize, and enabled from request
                Integer lotSize = json.has("lotSize") && !json.get("lotSize").isNull()
                        ? json.get("lotSize").asInt()
                        : null;

                BigDecimal tickSize = json.has("tickSize") && !json.get("tickSize").isNull()
                        ? new BigDecimal(json.get("tickSize").asText())
                        : null;

                Boolean enabled = json.has("enabled") && !json.get("enabled").isNull()
                        ? json.get("enabled").asBoolean()
                        : null;

                Watchlist updated = adminService.updateWatchlistItem(id, lotSize, tickSize, enabled);

                ObjectNode response = MAPPER.createObjectNode();
                response.put(JSON_SUCCESS, true);
                response.set(JSON_DATA, MAPPER.valueToTree(updated));
                response.put(JSON_MESSAGE, SUCCESS_WATCHLIST_UPDATED);

                ex.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                ex.getResponseSender().send(response.toString(), StandardCharsets.UTF_8);

            } catch (NumberFormatException e) {
                badRequest(ex, "Invalid watchlist ID");
            } catch (IllegalArgumentException e) {
                badRequest(ex, e.getMessage());
            } catch (Exception e) {
                log.error("Error updating watchlist item: {}", e.getMessage(), e);
                serverError(ex, ERROR_UPDATE_WATCHLIST);
            }
        }, StandardCharsets.UTF_8);
    }

    /**
     * POST /api/admin/watchlist/{id}/toggle - Toggle watchlist item enabled status
     * (admin only).
     * Auto-toggles the current enabled state (no request body needed).
     */
    public void adminToggleWatchlistItem(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        try {
            String idStr = exchange.getAttachment(io.undertow.util.PathTemplateMatch.ATTACHMENT_KEY)
                    .getParameters().get("id");
            Long id = Long.parseLong(idStr);

            // Get current state and toggle it
            var watchlists = watchlistRepo.findAll();
            var watchlistOpt = watchlists.stream().filter(w -> w.id().equals(id)).findFirst();

            if (watchlistOpt.isEmpty()) {
                exchange.setStatusCode(404);
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                exchange.getResponseSender().send("{\"error\":\"Watchlist item not found\"}", StandardCharsets.UTF_8);
                return;
            }

            boolean currentEnabled = watchlistOpt.get().enabled();
            boolean newEnabled = !currentEnabled; // Toggle

            adminService.toggleWatchlistItem(id, newEnabled);

            ObjectNode response = MAPPER.createObjectNode();
            response.put("success", true);
            response.put("message", "Watchlist item toggled successfully");
            response.put("enabled", newEnabled);

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(response.toString(), StandardCharsets.UTF_8);

        } catch (NumberFormatException e) {
            badRequest(exchange, "Invalid watchlist ID");
        } catch (Exception e) {
            log.error("Error toggling watchlist item: {}", e.getMessage(), e);
            serverError(exchange, "Failed to toggle watchlist item");
        }
    }

    /**
     * GET /api/admin/data-broker - Get system-wide data broker configuration (admin
     * only).
     */
    public void adminGetDataBroker(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        try {
            AdminService.DataBrokerInfo dataBroker = adminService.getDataBroker();

            if (dataBroker == null) {
                exchange.setStatusCode(404);
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                exchange.getResponseSender().send("{\"error\":\"No data broker configured\"}", StandardCharsets.UTF_8);
                return;
            }

            ObjectNode response = MAPPER.createObjectNode();
            response.put("userBrokerId", dataBroker.userBrokerId());
            response.put("userId", dataBroker.userId());
            response.put("brokerId", dataBroker.brokerId());
            response.put("brokerName", dataBroker.brokerName());
            response.put("connected", dataBroker.connected());
            if (dataBroker.lastConnected() != null) {
                response.put("lastConnected", dataBroker.lastConnected().toString());
            }
            if (dataBroker.connectionError() != null) {
                response.put("connectionError", dataBroker.connectionError());
            }
            response.put("status", dataBroker.status());

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(response.toString(), StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Error getting data broker: {}", e.getMessage(), e);
            serverError(exchange, "Failed to get data broker");
        }
    }

    /**
     * POST /api/admin/data-broker - Configure system-wide data broker (admin only).
     */
    public void adminConfigureDataBroker(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        exchange.getRequestReceiver().receiveFullString((ex, body) -> {
            try {
                JsonNode json = MAPPER.readTree(body);
                String brokerId = json.get("brokerId").asText();
                JsonNode credentials = json.get("credentials");

                String userBrokerId = adminService.configureDataBroker(auth.userId(), brokerId, credentials);

                ObjectNode response = MAPPER.createObjectNode();
                response.put("success", true);
                response.put("userBrokerId", userBrokerId);
                response.put("message", "Data broker configured successfully");

                ex.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                ex.getResponseSender().send(response.toString(), StandardCharsets.UTF_8);

            } catch (Exception e) {
                log.error("Error configuring data broker: {}", e.getMessage(), e);
                serverError(ex, "Failed to configure data broker");
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════
    // OAuth Endpoints
    // ═══════════════════════════════════════════════════════════════

    /**
     * GET /api/admin/brokers/{userBrokerId}/oauth-url
     * Generate OAuth authorization URL for broker connection (admin only).
     */
    public void adminGetOAuthUrl(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        try {
            String pathTemplate = exchange.getAttachment(io.undertow.util.PathTemplateMatch.ATTACHMENT_KEY)
                    .getParameters().get("userBrokerId");
            String userBrokerId = pathTemplate;

            String oauthUrl = oauthService.generateFyersOAuthUrl(userBrokerId);

            ObjectNode response = MAPPER.createObjectNode();
            response.put("success", true);
            response.put("oauthUrl", oauthUrl);
            response.put("userBrokerId", userBrokerId);

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(response.toString(), StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Error generating OAuth URL: {}", e.getMessage(), e);
            serverError(exchange, "Failed to generate OAuth URL: " + e.getMessage());
        }
    }

    /**
     * GET /api/admin/brokers/oauth-callback?auth_code=xxx&state=yyy
     * Handle OAuth callback - exchange auth code for access token (admin only).
     * Note: Fyers API v3 returns auth code in 'auth_code' parameter, not 'code'.
     */
    public void adminOAuthCallback(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        try {
            // Fyers API v3 returns auth code in 'auth_code' parameter
            Deque<String> authCodeParam = exchange.getQueryParameters().get("auth_code");
            Deque<String> stateParam = exchange.getQueryParameters().get("state");

            // Fallback to 'code' parameter for backwards compatibility
            if (authCodeParam == null || authCodeParam.isEmpty()) {
                authCodeParam = exchange.getQueryParameters().get("code");
            }

            if (authCodeParam == null || authCodeParam.isEmpty()) {
                log.error("OAuth callback missing auth code. Query params: {}", exchange.getQueryString());
                badRequest(exchange, "Missing 'auth_code' or 'code' parameter");
                return;
            }

            if (stateParam == null || stateParam.isEmpty()) {
                badRequest(exchange, "Missing 'state' parameter");
                return;
            }

            String authCode = authCodeParam.peekFirst();
            String state = stateParam.peekFirst();

            log.info("OAuth callback received: state={}, authCode length={}", state, authCode.length());

            // Exchange code for token and create session
            UserBrokerSession session = oauthService.handleFyersCallback(authCode, state);

            // Reconnect data broker and setup tick stream with new session
            reconnectDataBrokerAndSetupTickStream(session.userBrokerId());

            ObjectNode response = MAPPER.createObjectNode();
            response.put("success", true);
            response.put("sessionId", session.sessionId());
            response.put("userBrokerId", session.userBrokerId());
            response.put("validTill", session.tokenValidTill().toString());
            response.put("message", "Broker connected successfully");

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(response.toString(), StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Error handling OAuth callback: {}", e.getMessage(), e);
            serverError(exchange, "Failed to complete OAuth flow: " + e.getMessage());
        }
    }

    /**
     * GET /api/admin/brokers/{userBrokerId}/session
     * Get current session for user broker (admin only).
     */
    public void adminGetSession(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        try {
            String pathTemplate = exchange.getAttachment(io.undertow.util.PathTemplateMatch.ATTACHMENT_KEY)
                    .getParameters().get("userBrokerId");
            String userBrokerId = pathTemplate;

            java.util.Optional<UserBrokerSession> sessionOpt = oauthService.getCurrentSession(userBrokerId);

            ObjectNode response = MAPPER.createObjectNode();
            if (sessionOpt.isPresent()) {
                UserBrokerSession session = sessionOpt.get();
                response.put("success", true);
                response.put("hasSession", true);
                response.put("sessionId", session.sessionId());
                response.put("status", session.sessionStatus().name());
                response.put("validTill",
                        session.tokenValidTill() != null ? session.tokenValidTill().toString() : null);
                response.put("isActive", session.isActive());
            } else {
                response.put("success", true);
                response.put("hasSession", false);
            }

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(response.toString(), StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Error getting session: {}", e.getMessage(), e);
            serverError(exchange, "Failed to get session");
        }
    }

    /**
     * POST /api/admin/brokers/{userBrokerId}/disconnect
     * Revoke current session (disconnect broker) (admin only).
     */
    public void adminDisconnectBroker(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        try {
            String pathTemplate = exchange.getAttachment(io.undertow.util.PathTemplateMatch.ATTACHMENT_KEY)
                    .getParameters().get("userBrokerId");
            String userBrokerId = pathTemplate;

            oauthService.revokeSession(userBrokerId);

            ObjectNode response = MAPPER.createObjectNode();
            response.put("success", true);
            response.put("message", "Broker disconnected successfully");

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(response.toString(), StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Error disconnecting broker: {}", e.getMessage(), e);
            serverError(exchange, "Failed to disconnect broker");
        }
    }

    /**
     * POST /api/admin/brokers/{userBrokerId}/test-connection
     * Test broker connection and fetch profile (admin only).
     */
    public void adminTestConnection(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        try {
            String pathTemplate = exchange.getAttachment(io.undertow.util.PathTemplateMatch.ATTACHMENT_KEY)
                    .getParameters().get("userBrokerId");
            String userBrokerId = pathTemplate;

            // Get user broker from database
            java.util.Optional<UserBroker> userBrokerOpt = adminService.getUserBrokerById(userBrokerId);
            if (userBrokerOpt.isEmpty()) {
                badRequest(exchange, "User broker not found");
                return;
            }

            UserBroker userBroker = userBrokerOpt.get();

            // Get broker code from broker repository
            in.annupaper.domain.broker.Broker broker = adminService.getBrokerById(userBroker.brokerId())
                    .orElseThrow(() -> new IllegalStateException("Broker not found: " + userBroker.brokerId()));

            // Create broker adapter
            BrokerAdapter adapter = brokerFactory.create(broker.brokerCode(), userBrokerId);

            // Get credentials from user broker
            JsonNode credentialsJson = userBroker.credentials();
            BrokerAdapter.BrokerCredentials credentials = new BrokerAdapter.BrokerCredentials(
                    credentialsJson.has("apiKey") ? credentialsJson.get("apiKey").asText() : null,
                    credentialsJson.has("apiSecret") ? credentialsJson.get("apiSecret").asText() : null,
                    credentialsJson.has("accessToken") ? credentialsJson.get("accessToken").asText() : null,
                    credentialsJson.has("userId") ? credentialsJson.get("userId").asText() : null,
                    credentialsJson.has("password") ? credentialsJson.get("password").asText() : null,
                    credentialsJson.has("totp") ? credentialsJson.get("totp").asText() : null);

            // Connect and get profile
            BrokerAdapter.ConnectionResult result = adapter.connect(credentials).get();

            if (!result.success()) {
                ObjectNode response = MAPPER.createObjectNode();
                response.put("success", false);
                response.put("error", result.message());
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                exchange.getResponseSender().send(response.toString(), StandardCharsets.UTF_8);
                return;
            }

            // Get profile from adapter (broker-specific implementation)
            // For now, return success with session token
            ObjectNode response = MAPPER.createObjectNode();
            response.put("success", true);
            response.put("message", "Connection successful");

            // Add profile data if available (this will be implemented per broker)
            ObjectNode profileData = MAPPER.createObjectNode();
            profileData.put("sessionToken", result.sessionToken());
            profileData.put("broker", broker.brokerCode());
            profileData.put("connected", true);
            response.set("profile", profileData);

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(response.toString(), StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Error testing connection: {}", e.getMessage(), e);
            serverError(exchange, "Failed to test connection: " + e.getMessage());
        }
    }

    /**
     * POST /api/admin/brokers/{userBrokerId}/save-connection
     * Save connection with profile data (admin only).
     */
    public void adminSaveConnection(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        exchange.getRequestReceiver().receiveFullString((ex, body) -> {
            try {
                JsonNode requestJson = MAPPER.readTree(body);

                String pathTemplate = exchange.getAttachment(io.undertow.util.PathTemplateMatch.ATTACHMENT_KEY)
                        .getParameters().get("userBrokerId");
                String userBrokerId = pathTemplate;

                // Get user broker from database
                java.util.Optional<UserBroker> userBrokerOpt = adminService.getUserBrokerById(userBrokerId);
                if (userBrokerOpt.isEmpty()) {
                    badRequest(exchange, "User broker not found");
                    return;
                }

                UserBroker userBroker = userBrokerOpt.get();

                // Extract profile data from request
                JsonNode profileData = requestJson.get("profile");

                // Merge profile data into credentials
                ObjectNode updatedCredentials = (ObjectNode) userBroker.credentials().deepCopy();
                if (profileData != null) {
                    profileData.fields().forEachRemaining(entry -> {
                        updatedCredentials.set(entry.getKey(), entry.getValue());
                    });
                }

                // Update user broker with connected status and timestamp
                UserBroker updatedBroker = new UserBroker(
                        userBroker.userBrokerId(),
                        userBroker.userId(),
                        userBroker.brokerId(),
                        userBroker.role(),
                        updatedCredentials,
                        true, // connected
                        java.time.Instant.now(), // lastConnected
                        null, // connectionError
                        userBroker.capitalAllocated(),
                        userBroker.maxExposure(),
                        userBroker.maxPerTrade(),
                        userBroker.maxOpenTrades(),
                        userBroker.allowedSymbols(),
                        userBroker.blockedSymbols(),
                        userBroker.allowedProducts(),
                        userBroker.maxDailyLoss(),
                        userBroker.maxWeeklyLoss(),
                        userBroker.cooldownMinutes(),
                        userBroker.status(),
                        userBroker.enabled(),
                        userBroker.createdAt(),
                        java.time.Instant.now(), // updatedAt
                        userBroker.deletedAt(),
                        userBroker.version());

                adminService.updateUserBroker(updatedBroker);

                ObjectNode response = MAPPER.createObjectNode();
                response.put("success", true);
                response.put("message", "Connection saved successfully");

                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                exchange.getResponseSender().send(response.toString(), StandardCharsets.UTF_8);

            } catch (Exception e) {
                log.error("Error saving connection: {}", e.getMessage(), e);
                serverError(exchange, "Failed to save connection: " + e.getMessage());
            }
        }, StandardCharsets.UTF_8);
    }

    // ═══════════════════════════════════════════════════════════════
    // Authentication helpers
    // ═══════════════════════════════════════════════════════════════

    private record AuthContext(String userId, String role) {
    }

    private AuthContext authenticateWithRole(HttpServerExchange exchange) {
        String token = extractToken(exchange);
        if (token == null) {
            return null;
        }

        JwtService.TokenClaims claims = jwtService.getClaims(token);
        if (claims == null) {
            return null;
        }

        return new AuthContext(claims.userId(), claims.role());
    }

    private String extractToken(HttpServerExchange exchange) {
        // Check Authorization header: Bearer <token>
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null) {
            // Also check query param: ?token=xxx
            Deque<String> tokenParam = exchange.getQueryParameters().get("token");
            if (tokenParam != null && !tokenParam.isEmpty()) {
                return tokenParam.peekFirst();
            }
            return null;
        }

        if (authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        return null;
    }

    private boolean requireAdmin(HttpServerExchange exchange, AuthContext auth) {
        if (auth == null) {
            log.info("requireAdmin: auth is null");
            exchange.setStatusCode(403);
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send("{\"error\":\"Admin access required\"}", StandardCharsets.UTF_8);
            return false;
        }

        if (!"ADMIN".equals(auth.role())) {
            log.info("requireAdmin: role is not ADMIN, got: {}", auth.role());
            exchange.setStatusCode(403);
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send("{\"error\":\"Admin access required\"}", StandardCharsets.UTF_8);
            return false;
        }

        log.info("requireAdmin: access granted to user {}", auth.userId());
        return true;
    }

    // Old authenticate method (kept for backward compatibility)
    private String authenticate(HttpServerExchange exchange) {
        // Check Authorization header: Bearer <token>
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null) {
            // Also check query param: ?token=xxx
            Deque<String> tokenParam = exchange.getQueryParameters().get("token");
            if (tokenParam != null && !tokenParam.isEmpty()) {
                return tokenValidator.apply(tokenParam.peekFirst());
            }
            return null;
        }

        if (authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return tokenValidator.apply(token);
        }

        return null;
    }

    // ============================================================
    // Watchlist Template Management Endpoints
    // ============================================================

    /**
     * GET /api/admin/watchlist-templates - Get all active watchlist templates
     * (admin only).
     */
    public void adminGetWatchlistTemplates(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        try {
            var templates = adminService.getAllTemplates();
            ArrayNode templatesArray = MAPPER.createArrayNode();

            for (var template : templates) {
                ObjectNode templateNode = MAPPER.createObjectNode();
                templateNode.put("templateId", template.templateId());
                templateNode.put("templateName", template.templateName());
                templateNode.put("description", template.description());
                templateNode.put("displayOrder", template.displayOrder());
                templateNode.put("enabled", template.enabled());
                templatesArray.add(templateNode);
            }

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(templatesArray.toString(), StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Error getting watchlist templates: {}", e.getMessage(), e);
            serverError(exchange, "Failed to get watchlist templates");
        }
    }

    /**
     * GET /api/admin/watchlist-templates/{templateId}/symbols - Get symbols for a
     * template (admin only).
     */
    public void adminGetTemplateSymbols(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        try {
            String templateId = exchange.getAttachment(io.undertow.util.PathTemplateMatch.ATTACHMENT_KEY)
                    .getParameters().get("templateId");

            var symbols = adminService.getTemplateSymbols(templateId);
            ArrayNode symbolsArray = MAPPER.createArrayNode();

            for (var symbol : symbols) {
                ObjectNode symbolNode = MAPPER.createObjectNode();
                symbolNode.put("id", symbol.id());
                symbolNode.put("symbol", symbol.symbol());
                symbolNode.put("displayOrder", symbol.displayOrder());

                // FIX: Add lot_size and tick_size from instruments table
                String cleanSymbol = symbol.symbol().replace("-EQ", "");
                Integer lotSize = adminService.getLotSizeFromInstruments(BrokerIds.FYERS, cleanSymbol);
                java.math.BigDecimal tickSize = adminService.getTickSizeFromInstruments(BrokerIds.FYERS, cleanSymbol);
                symbolNode.put("lotSize", lotSize);
                symbolNode.put("tickSize", tickSize.toString());

                symbolsArray.add(symbolNode);
            }

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(symbolsArray.toString(), StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Error getting template symbols: {}", e.getMessage(), e);
            serverError(exchange, "Failed to get template symbols");
        }
    }

    /**
     * POST /api/admin/watchlist-templates - Create a new watchlist template (admin
     * only).
     */
    public void adminCreateTemplate(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        exchange.getRequestReceiver().receiveFullString((ex, body) -> {
            try {
                JsonNode json = MAPPER.readTree(body);
                String templateName = json.get("templateName").asText();
                String description = json.has("description") ? json.get("description").asText() : "";
                int displayOrder = json.has("displayOrder") ? json.get("displayOrder").asInt() : 1;

                String templateId = adminService.createTemplate(templateName, description, displayOrder);

                ObjectNode response = MAPPER.createObjectNode();
                response.put("success", true);
                response.put("templateId", templateId);
                response.put("message", "Template created successfully");

                ex.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                ex.getResponseSender().send(response.toString(), StandardCharsets.UTF_8);

            } catch (Exception e) {
                log.error("Error creating template: {}", e.getMessage(), e);
                serverError(ex, "Failed to create template: " + e.getMessage());
            }
        }, StandardCharsets.UTF_8);
    }

    /**
     * POST /api/admin/watchlist-templates/{templateId}/symbols - Add symbol to
     * template (admin only).
     */
    public void adminAddSymbolToTemplate(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        exchange.getRequestReceiver().receiveFullString((ex, body) -> {
            try {
                String templateId = ex.getAttachment(io.undertow.util.PathTemplateMatch.ATTACHMENT_KEY)
                        .getParameters().get("templateId");

                JsonNode json = MAPPER.readTree(body);
                String symbol = json.get("symbol").asText();
                int displayOrder = json.has("displayOrder") ? json.get("displayOrder").asInt() : 1;

                adminService.addSymbolToTemplate(templateId, symbol, displayOrder);

                ObjectNode response = MAPPER.createObjectNode();
                response.put("success", true);
                response.put("message", "Symbol added to template");

                ex.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                ex.getResponseSender().send(response.toString(), StandardCharsets.UTF_8);

            } catch (Exception e) {
                log.error("Error adding symbol to template: {}", e.getMessage(), e);
                serverError(ex, "Failed to add symbol: " + e.getMessage());
            }
        }, StandardCharsets.UTF_8);
    }

    /**
     * DELETE /api/admin/watchlist-templates/symbols/{symbolId} - Delete symbol from
     * template (admin only).
     */
    public void adminDeleteSymbolFromTemplate(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        try {
            String symbolIdStr = exchange.getAttachment(io.undertow.util.PathTemplateMatch.ATTACHMENT_KEY)
                    .getParameters().get("symbolId");
            long symbolId = Long.parseLong(symbolIdStr);

            adminService.deleteSymbolFromTemplate(symbolId);

            ObjectNode response = MAPPER.createObjectNode();
            response.put("success", true);
            response.put("message", "Symbol deleted from template");

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(response.toString(), StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Error deleting symbol from template: {}", e.getMessage(), e);
            serverError(exchange, "Failed to delete symbol: " + e.getMessage());
        }
    }

    /**
     * DELETE /api/admin/watchlist-templates/{templateId} - Delete a template (admin
     * only).
     */
    public void adminDeleteTemplate(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        try {
            String templateId = exchange.getAttachment(io.undertow.util.PathTemplateMatch.ATTACHMENT_KEY)
                    .getParameters().get("templateId");

            adminService.deleteTemplate(templateId);

            ObjectNode response = MAPPER.createObjectNode();
            response.put("success", true);
            response.put("message", "Template deleted");

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(response.toString(), StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Error deleting template: {}", e.getMessage(), e);
            serverError(exchange, "Failed to delete template: " + e.getMessage());
        }
    }

    /**
     * POST /api/admin/watchlist-selected - Create a selected watchlist from
     * template (admin only).
     */
    public void adminCreateSelectedWatchlist(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        exchange.getRequestReceiver().receiveFullString((ex, body) -> {
            try {
                JsonNode json = MAPPER.readTree(body);
                String sourceTemplateId = json.get("sourceTemplateId").asText();

                List<String> selectedSymbols = new java.util.ArrayList<>();
                JsonNode symbolsNode = json.get("symbols");
                if (symbolsNode != null && symbolsNode.isArray()) {
                    for (JsonNode symbolNode : symbolsNode) {
                        selectedSymbols.add(symbolNode.asText());
                    }
                }

                String selectedId = adminService.createSelectedWatchlist(sourceTemplateId, selectedSymbols);

                // Auto-sync to all user-brokers after creating
                adminService.syncDefaultToAllUserBrokers();

                ObjectNode response = MAPPER.createObjectNode();
                response.put("success", true);
                response.put("selectedId", selectedId);
                response.put("message", "Selected watchlist created and synced to all users");

                ex.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
                ex.getResponseSender().send(response.toString(), StandardCharsets.UTF_8);

            } catch (Exception e) {
                log.error("Error creating selected watchlist: {}", e.getMessage(), e);
                serverError(ex, "Failed to create selected watchlist: " + e.getMessage());
            }
        }, StandardCharsets.UTF_8);
    }

    /**
     * GET /api/admin/watchlist-selected - Get all active selected watchlists (admin
     * only).
     */
    public void adminGetSelectedWatchlists(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        try {
            var selected = adminService.getAllSelectedWatchlists();
            ArrayNode selectedArray = MAPPER.createArrayNode();

            for (var sel : selected) {
                ObjectNode selNode = MAPPER.createObjectNode();
                selNode.put("selectedId", sel.selectedId());
                selNode.put("name", sel.name());
                selNode.put("sourceTemplateId", sel.sourceTemplateId());
                selNode.put("description", sel.description());
                selNode.put("enabled", sel.enabled());
                selectedArray.add(selNode);
            }

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(selectedArray.toString(), StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Error getting selected watchlists: {}", e.getMessage(), e);
            serverError(exchange, "Failed to get selected watchlists");
        }
    }

    /**
     * GET /api/admin/watchlist-selected/{selectedId}/symbols - Get symbols for a
     * selected watchlist (admin only).
     */
    public void adminGetSelectedSymbols(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        try {
            String selectedId = exchange.getAttachment(io.undertow.util.PathTemplateMatch.ATTACHMENT_KEY)
                    .getParameters().get("selectedId");

            var symbols = adminService.getSelectedWatchlistSymbols(selectedId);
            ArrayNode symbolsArray = MAPPER.createArrayNode();

            for (var symbol : symbols) {
                ObjectNode symbolNode = MAPPER.createObjectNode();
                symbolNode.put("id", symbol.id());
                symbolNode.put("symbol", symbol.symbol());
                symbolNode.put("displayOrder", symbol.displayOrder());

                // FIX: Add lot_size and tick_size from instruments table
                String cleanSymbol = symbol.symbol().replace("-EQ", "");
                Integer lotSize = adminService.getLotSizeFromInstruments(BrokerIds.FYERS, cleanSymbol);
                java.math.BigDecimal tickSize = adminService.getTickSizeFromInstruments(BrokerIds.FYERS, cleanSymbol);
                symbolNode.put("lotSize", lotSize);
                symbolNode.put("tickSize", tickSize.toString());

                symbolsArray.add(symbolNode);
            }

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(symbolsArray.toString(), StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Error getting selected watchlist symbols: {}", e.getMessage(), e);
            serverError(exchange, "Failed to get selected watchlist symbols");
        }
    }

    /**
     * DELETE /api/admin/watchlist-selected/{selectedId} - Delete a selected
     * watchlist (admin only).
     */
    public void adminDeleteSelectedWatchlist(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        try {
            String selectedId = exchange.getAttachment(io.undertow.util.PathTemplateMatch.ATTACHMENT_KEY)
                    .getParameters().get("selectedId");

            adminService.deleteSelectedWatchlist(selectedId);

            // Auto-sync to all user-brokers after deleting
            adminService.syncDefaultToAllUserBrokers();

            ObjectNode response = MAPPER.createObjectNode();
            response.put("success", true);
            response.put("message", "Selected watchlist deleted and users synced");

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(response.toString(), StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Error deleting selected watchlist: {}", e.getMessage(), e);
            serverError(exchange, "Failed to delete selected watchlist");
        }
    }

    /**
     * GET /api/admin/watchlist-default - Get merged default watchlist (Level 3)
     * (admin only).
     */
    public void adminGetDefaultWatchlist(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        try {
            List<String> symbols = adminService.getMergedDefaultWatchlist();
            ArrayNode symbolsArray = MAPPER.createArrayNode();

            for (String symbol : symbols) {
                // OLD: symbolsArray.add(symbol);
                // FIX: Return objects with lot_size and tick_size instead of just strings
                ObjectNode symbolNode = MAPPER.createObjectNode();
                symbolNode.put("symbol", symbol);

                String cleanSymbol = symbol.replace("-EQ", "");
                Integer lotSize = adminService.getLotSizeFromInstruments(BrokerIds.FYERS, cleanSymbol);
                java.math.BigDecimal tickSize = adminService.getTickSizeFromInstruments(BrokerIds.FYERS, cleanSymbol);
                symbolNode.put("lotSize", lotSize);
                symbolNode.put("tickSize", tickSize.toString());

                symbolsArray.add(symbolNode);
            }

            ObjectNode response = MAPPER.createObjectNode();
            response.put("count", symbols.size());
            response.set("symbols", symbolsArray);

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(response.toString(), StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Error getting default watchlist: {}", e.getMessage(), e);
            serverError(exchange, "Failed to get default watchlist");
        }
    }

    /**
     * POST /api/admin/watchlist-sync - Manually trigger sync to all user-brokers
     * (admin only).
     */
    public void adminSyncWatchlists(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (!requireAdmin(exchange, auth))
            return;

        try {
            adminService.syncDefaultToAllUserBrokers();

            ObjectNode response = MAPPER.createObjectNode();
            response.put("success", true);
            response.put("message", "Watchlist synced to all user-brokers");

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(response.toString(), StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Error syncing watchlists: {}", e.getMessage(), e);
            serverError(exchange, "Failed to sync watchlists");
        }
    }

    /**
     * GET /api/instruments/search?q=<query> - Search instruments for autocomplete.
     */
    public void searchInstruments(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (auth == null)
            return;

        try {
            String query = exchange.getQueryParameters().get("q").getFirst().toUpperCase();

            // Search instruments from database
            var searchResults = instrumentService.search(query);

            ArrayNode results = MAPPER.createArrayNode();
            for (var result : searchResults) {
                ObjectNode item = MAPPER.createObjectNode();
                item.put("symbol", result.symbol());
                item.put("name", result.name());
                item.put("exchange", result.exchange());
                item.put("type", result.instrumentType());
                results.add(item);
            }

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(results.toString(), StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Error searching instruments: {}", e.getMessage(), e);
            serverError(exchange, "Failed to search instruments");
        }
    }

    /**
     * GET /api/market-watch - Get watchlist with LTP for Market Watch page.
     * Regular users see only their symbols, admin sees superset of all users'
     * watchlists.
     */
    public void marketWatch(HttpServerExchange exchange) {
        AuthContext auth = authenticateWithRole(exchange);
        if (auth == null) {
            unauthorized(exchange);
            return;
        }

        try {
            List<AdminService.MarketWatchEntry> watchlist;

            // Admin sees superset of all users' watchlists, regular users see only their
            // own
            // Returns comprehensive market data: LTP, 52-week high/low, daily OHLC+Volume
            if ("admin".equalsIgnoreCase(auth.role())) {
                watchlist = adminService.getMarketWatchEntriesForAdmin();
            } else {
                watchlist = adminService.getMarketWatchEntriesForUser(auth.userId());
            }

            ArrayNode watchlistArray = MAPPER.createArrayNode();

            for (AdminService.MarketWatchEntry w : watchlist) {
                ObjectNode wNode = MAPPER.createObjectNode();
                wNode.put("id", w.id());
                wNode.put("symbol", w.symbol());
                wNode.put("lotSize", w.lotSize() != null ? w.lotSize() : 1);
                wNode.put("tickSize", w.tickSize() != null ? w.tickSize().doubleValue() : 0.05);

                // Last price and tick time for real-time display
                if (w.lastPrice() != null) {
                    wNode.put("lastPrice", w.lastPrice().doubleValue());
                }
                if (w.lastTickTime() != null) {
                    wNode.put("lastTickTime", w.lastTickTime().toString());
                }

                // 52-week high/low (for trend analysis and price context)
                if (w.weekHigh52() != null) {
                    wNode.put("weekHigh52", w.weekHigh52().doubleValue());
                }
                if (w.weekLow52() != null) {
                    wNode.put("weekLow52", w.weekLow52().doubleValue());
                }

                // Today's daily OHLC + Volume
                if (w.dailyOpen() != null) {
                    wNode.put("dailyOpen", w.dailyOpen().doubleValue());
                }
                if (w.dailyHigh() != null) {
                    wNode.put("dailyHigh", w.dailyHigh().doubleValue());
                }
                if (w.dailyLow() != null) {
                    wNode.put("dailyLow", w.dailyLow().doubleValue());
                }
                if (w.dailyClose() != null) {
                    wNode.put("dailyClose", w.dailyClose().doubleValue());
                }
                if (w.dailyVolume() != null) {
                    wNode.put("dailyVolume", w.dailyVolume());
                }

                // Overnight change (today's open - yesterday's close)
                if (w.overnightChange() != null) {
                    wNode.put("overnightChange", w.overnightChange().doubleValue());
                }
                if (w.overnightChangePercent() != null) {
                    wNode.put("overnightChangePercent", w.overnightChangePercent().doubleValue());
                }

                watchlistArray.add(wNode);
            }

            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
            exchange.getResponseSender().send(watchlistArray.toString(), StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Error getting market watch: {}", e.getMessage(), e);
            serverError(exchange, "Failed to get market watch");
        }
    }

    // ============================================================
    // Helper methods
    // ============================================================
    // FYERS OAuth Endpoints
    // ============================================================

    /**
     * GET /api/brokers/:userBrokerId/fyers/login-url
     * Generate FYERS OAuth login URL with state parameter.
     *
     * Response:
     * {
     * "loginUrl": "https://api-t1.fyers.in/api/v3/generate-authcode?...",
     * "state": "uuid-state",
     * "redirectUri": "http://localhost:4000/admin/oauth-callback",
     * "appId": "NZT2TDYT0T-100"
     * }
     */
    public void fyersLoginUrl(HttpServerExchange exchange) {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");

        try {
            // Extract userBrokerId from path parameter
            String path = exchange.getRequestPath();
            String[] parts = path.split("/");
            if (parts.length < 4) {
                badRequest(exchange, "Missing userBrokerId");
                return;
            }
            String userBrokerId = parts[3]; // /api/brokers/{userBrokerId}/fyers/login-url

            log.info("[FYERS LOGIN URL] Request for userBrokerId={}", userBrokerId);

            // Generate login URL
            in.annupaper.service.oauth.FyersLoginOrchestrator.LoginUrlResponse response = fyersLoginOrchestrator
                    .generateLoginUrl(userBrokerId);

            // Build JSON response
            ObjectNode json = MAPPER.createObjectNode();
            json.put("loginUrl", response.loginUrl());
            json.put("state", response.state());
            json.put("redirectUri", response.redirectUri());
            json.put("appId", response.appId());

            exchange.getResponseSender().send(json.toString(), StandardCharsets.UTF_8);

        } catch (IllegalArgumentException e) {
            log.error("[FYERS LOGIN URL] Bad request: {}", e.getMessage());
            badRequest(exchange, e.getMessage());
        } catch (Exception e) {
            log.error("[FYERS LOGIN URL] Error generating login URL", e);
            serverError(exchange, "Failed to generate login URL: " + e.getMessage());
        }
    }

    /**
     * POST /api/fyers/oauth/exchange
     * Exchange auth_code for access_token (callback handler).
     *
     * Request body:
     * {
     * "authCode": "xyz123",
     * "state": "uuid-state"
     * }
     *
     * Response:
     * {
     * "ok": true,
     * "alreadyDone": false,
     * "userBrokerId": "UB_DATA_E7DE4B",
     * "sessionId": "SESSION_12345678",
     * "message": "Token exchanged successfully"
     * }
     */
    public void fyersOAuthExchange(HttpServerExchange exchange) {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");

        try {
            // Read request body
            exchange.getRequestReceiver().receiveFullString((ex, body) -> {
                try {
                    JsonNode requestJson = MAPPER.readTree(body);

                    if (!requestJson.has("authCode") || !requestJson.has("state")) {
                        badRequest(ex, "Missing authCode or state");
                        return;
                    }

                    String authCode = requestJson.get("authCode").asText();
                    String state = requestJson.get("state").asText();

                    log.info("[FYERS OAUTH EXCHANGE] Received callback: state={}", state);

                    // Exchange token (with idempotency and state validation)
                    BrokerOAuthService.ExchangeResult result = oauthService.exchangeAuthCodeWithState(authCode, state);

                    if (!result.success()) {
                        log.error("[FYERS OAUTH EXCHANGE] Exchange failed: {} - {}",
                                result.errorCode(), result.errorMessage());
                        badRequest(ex, result.errorMessage());
                        return;
                    }

                    // Trigger immediate reconnect (don't wait for watchdog)
                    if (result.session() != null) {
                        String userBrokerId = result.userBrokerId();
                        String accessToken = result.accessToken();
                        String sessionId = result.sessionId();

                        log.info("[FYERS OAUTH EXCHANGE] ✅ Token exchanged successfully: userBrokerId={}, session={}",
                                userBrokerId, sessionId);

                        // Immediate reconnect via factory
                        try {
                            // Reload token (triggers reconnect if there are active subscriptions)
                            brokerFactory.reloadToken(userBrokerId, accessToken, sessionId);

                            // Also ensure adapter is connected (in case no subscriptions yet)
                            BrokerAdapter adapter = brokerFactory.get(userBrokerId);
                            if (adapter != null && !adapter.isConnected()) {
                                log.info("[FYERS OAUTH EXCHANGE] ⚡ Triggering explicit connect for userBrokerId={}",
                                        userBrokerId);
                                // Trigger connect (async, non-blocking)
                                BrokerAdapter.BrokerCredentials dummyCreds = new BrokerAdapter.BrokerCredentials(
                                        null, null, accessToken, null, null, null);
                                adapter.connect(dummyCreds);
                            }

                            log.info("[FYERS OAUTH EXCHANGE] ✅ Triggered immediate reconnect for userBrokerId={}",
                                    userBrokerId);
                        } catch (Exception e) {
                            log.error("[FYERS OAUTH EXCHANGE] Failed to trigger reconnect: {}", e.getMessage());
                            // Don't fail the request - token is saved, watchdog will pick it up
                        }
                    }

                    // Build success response
                    ObjectNode json = MAPPER.createObjectNode();
                    json.put("ok", true);
                    json.put("alreadyDone", result.alreadyDone());
                    json.put("userBrokerId", result.userBrokerId());
                    json.put("sessionId", result.sessionId());
                    json.put("message", result.alreadyDone() ? "Token already exchanged (idempotent)"
                            : "Token exchanged successfully");

                    ex.getResponseSender().send(json.toString(), StandardCharsets.UTF_8);

                } catch (Exception e) {
                    log.error("[FYERS OAUTH EXCHANGE] Error processing exchange", e);
                    serverError(ex, "Failed to exchange token: " + e.getMessage());
                }
            }, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("[FYERS OAUTH EXCHANGE] Error", e);
            serverError(exchange, "Failed to process request: " + e.getMessage());
        }
    }

    // ============================================================

    private void unauthorized(HttpServerExchange exchange) {
        exchange.setStatusCode(401);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
        exchange.getResponseSender().send("{\"error\":\"Unauthorized\"}", StandardCharsets.UTF_8);
    }

    private void badRequest(HttpServerExchange exchange, String message) {
        exchange.setStatusCode(400);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
        exchange.getResponseSender().send("{\"error\":\"" + message + "\"}", StandardCharsets.UTF_8);
    }

    private void serverError(HttpServerExchange exchange, String message) {
        exchange.setStatusCode(500);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json; charset=utf-8");
        exchange.getResponseSender().send("{\"error\":\"" + message + "\"}", StandardCharsets.UTF_8);
    }
}
