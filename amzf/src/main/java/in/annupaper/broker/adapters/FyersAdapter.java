package in.annupaper.broker.adapters;

import in.annupaper.broker.BrokerAdapter;
import in.annupaper.domain.broker.UserBrokerSession;
import in.annupaper.repository.UserBrokerSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fyers API v3 adapter - Real implementation.
 *
 * Uses:
 * - Fyers REST API v3 for historical data
 * - Fyers WebSocket v2 for real-time ticks
 *
 * API Docs: https://myapi.fyers.in/docsv3
 * Session Management: Loads access tokens from user_broker_sessions table
 */
public class FyersAdapter implements BrokerAdapter {
    private static final Logger log = LoggerFactory.getLogger(FyersAdapter.class);

    // Using test API temporarily (production api.fyers.in returns 503)
    // TODO: Switch to https://api.fyers.in when production API is available
    private static final String BASE_URL = "https://api-t1.fyers.in";

    // WebSocket uses production endpoint only (no test server available)
    // Official FYERS API v2 Data WebSocket endpoint with trailing slash
    // Override via FYERS_WS_URL environment variable for network path debugging
    private static final String WS_URL = System.getenv().getOrDefault(
        "FYERS_WS_URL",
        "wss://api.fyers.in/socket/v2/data/"
    );

    static {
        String wsUrlOverride = System.getenv("FYERS_WS_URL");
        if (wsUrlOverride != null) {
            log.info("[FYERS] Using custom WebSocket URL from FYERS_WS_URL: {}", WS_URL);
        } else {
            log.info("[FYERS] Using default WebSocket URL: {}", WS_URL);
        }
    }

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(java.time.Duration.ofSeconds(10))
        .build();

    private final UserBrokerSessionRepository sessionRepo;
    private String userBrokerId;

    private volatile boolean connected = false;
    private String sessionToken;
    private BrokerCredentials credentials;
    private String accessToken;
    private String appId;
    private final AtomicReference<WebSocket> wsRef = new AtomicReference<>(null);

    /**
     * Constructor for FyersAdapter with session management.
     */
    public FyersAdapter(UserBrokerSessionRepository sessionRepo, String userBrokerId) {
        this.sessionRepo = sessionRepo;
        this.userBrokerId = userBrokerId;
    }

    /**
     * Default constructor for backwards compatibility (no session management).
     */
    public FyersAdapter() {
        this.sessionRepo = null;
        this.userBrokerId = null;
    }
    
    // Multiple listeners per symbol (TickCandleBuilder, ExitSignalService, MtfSignalGenerator)
    private final Map<String, List<TickListener>> tickListeners = new ConcurrentHashMap<>();
    private final Map<String, OrderStatus> orders = new ConcurrentHashMap<>();

    // WebSocket connection state
    private enum WsState { DISCONNECTED, CONNECTING, CONNECTED, RECONNECT_REQUIRED }
    private volatile WsState wsState = WsState.DISCONNECTED;

    // Retry state
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong lastConnectAttempt = new AtomicLong(0);
    private final AtomicLong lastSuccessfulTick = new AtomicLong(0);

    // Error tracking for health/status reporting
    private volatile Integer lastHttpStatus = null;
    private volatile String lastErrorMessage = null;

    // Safety: Force READ-ONLY mode if no ticks for > 5 minutes
    private static final long MAX_TICK_SILENCE_MS = 5 * 60 * 1000; // 5 minutes
    private volatile boolean forceReadOnly = false;
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "FyersReconnect");
        t.setDaemon(true);
        return t;
    });
    
    @Override
    public String getBrokerCode() {
        return "FYERS";
    }
    
    @Override
    public CompletableFuture<ConnectionResult> connect(BrokerCredentials credentials) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("[FYERS] Connecting with appId={}", maskKey(credentials.apiKey()));

            this.credentials = credentials;
            this.appId = credentials.apiKey();

            if (appId == null || appId.isEmpty()) {
                return ConnectionResult.failure("Invalid App ID", "INVALID_APP_ID");
            }

            // Load access token from session repository if available
            if (sessionRepo != null && userBrokerId != null) {
                log.info("[FYERS] Loading access token from session repository for userBrokerId={}", userBrokerId);

                Optional<UserBrokerSession> sessionOpt = sessionRepo.findActiveSession(userBrokerId);

                if (sessionOpt.isEmpty()) {
                    log.error("[FYERS] No active session found for userBrokerId={}. Please connect via OAuth.", userBrokerId);
                    return ConnectionResult.failure("No active session. Please connect via OAuth.", "NO_ACTIVE_SESSION");
                }

                UserBrokerSession session = sessionOpt.get();

                // Check if session is active and not expired
                if (!session.isActive()) {
                    log.error("[FYERS] Session {} is not active or expired (status={}, validTill={})",
                             session.sessionId(), session.sessionStatus(), session.tokenValidTill());
                    return ConnectionResult.failure("Session expired. Please reconnect via OAuth.", "SESSION_EXPIRED");
                }

                this.accessToken = session.accessToken();
                log.info("[FYERS] Loaded access token from session {} (valid till {})",
                         session.sessionId(), session.tokenValidTill());

            } else {
                // Fallback: Load from credentials (backwards compatibility)
                log.warn("[FYERS] No session repository configured. Loading access token from credentials (deprecated).");
                this.accessToken = credentials.accessToken();
            }

            if (accessToken == null || accessToken.isEmpty()) {
                log.error("[FYERS] No access token available. Please connect via OAuth.");
                return ConnectionResult.failure("Access token required. Please connect via OAuth.", "NO_ACCESS_TOKEN");
            }

            // Validate access token by making a profile API call
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/v3/profile"))
                    .header("Authorization", appId + ":" + accessToken)
                    .GET()
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode responseJson = objectMapper.readTree(response.body());
                    if (responseJson.has("s") && "ok".equals(responseJson.get("s").asText())) {
                        this.sessionToken = accessToken;
                        this.connected = true;
                        log.info("[FYERS] Connected successfully - Profile: {}", responseJson.get("data"));
                        return ConnectionResult.success(sessionToken);
                    } else {
                        log.error("[FYERS] Profile API failed: {}", responseJson);
                        return ConnectionResult.failure("Authentication failed", "AUTH_FAILED");
                    }
                } else {
                    log.error("[FYERS] Profile API HTTP {}: {}", response.statusCode(), response.body());
                    return ConnectionResult.failure("HTTP error " + response.statusCode(), "HTTP_ERROR");
                }
            } catch (Exception e) {
                log.error("[FYERS] Connection error: {}", e.getMessage());
                return ConnectionResult.failure(e.getMessage(), "CONNECTION_ERROR");
            }
        });
    }
    
    @Override
    public void disconnect() {
        log.info("[FYERS] Disconnecting...");
        connected = false;
        sessionToken = null;
        tickListeners.clear();
        wsState = WsState.DISCONNECTED;

        // Close WebSocket connection
        WebSocket ws = wsRef.getAndSet(null);
        if (ws != null) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "Disconnect");
        }
    }

    /**
     * Safe send - guards against null WebSocket.
     * NEVER call WebSocket.sendText() directly - always use this method.
     */
    private void safeSend(String json) {
        WebSocket ws = wsRef.get();
        if (ws == null) {
            log.warn("[FYERS] send skipped - socket not open yet (state={})", wsState);
            return;
        }
        try {
            ws.sendText(json, true);
        } catch (Exception e) {
            log.error("[FYERS] Failed to send message", e);
            wsState = WsState.RECONNECT_REQUIRED;
            scheduleReconnect(calculateBackoff());
        }
    }

    /**
     * Reload access token and reinitialize WebSocket connection.
     * Called when token is refreshed via OAuth.
     */
    public synchronized void reloadToken(String newAccessToken, String sessionId) {
        log.info("[FYERS] ⚡ Token refresh detected - reloading access token from session: {}", sessionId);

        // Close existing WebSocket
        WebSocket ws = wsRef.getAndSet(null);
        if (ws != null) {
            log.info("[FYERS] Closing existing WebSocket before token reload");
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "Token refresh");
            } catch (Exception e) {
                log.warn("[FYERS] Error closing WebSocket: {}", e.getMessage());
            }
        }

        // Update token
        this.accessToken = newAccessToken;
        log.info("[FYERS] ✅ Access token updated (session: {})", sessionId);

        // Reset failure counter and trigger immediate reconnect
        consecutiveFailures.set(0);
        wsState = WsState.CONNECTING;

        // Trigger immediate reconnect (bypass backoff)
        if (!tickListeners.isEmpty()) {
            log.info("[FYERS] Triggering immediate reconnect with new token for {} symbols", tickListeners.size());
            reconnectScheduler.submit(this::connectWithRetry);
        } else {
            log.info("[FYERS] No active subscriptions - reconnect deferred until next subscribe");
        }
    }
    
    @Override
    public boolean isConnected() {
        // Check for stale feed - if no ticks for > 5 minutes, force READ-ONLY
        long lastTick = lastSuccessfulTick.get();
        if (lastTick > 0) {
            long silenceDuration = System.currentTimeMillis() - lastTick;
            if (silenceDuration > MAX_TICK_SILENCE_MS && !forceReadOnly) {
                log.error("[FYERS] ⚠️  STALE FEED DETECTED: No ticks for {}ms. Forcing READ-ONLY mode.", silenceDuration);
                forceReadOnly = true;
            } else if (silenceDuration <= MAX_TICK_SILENCE_MS && forceReadOnly) {
                log.info("[FYERS] ✅ Feed recovered. Clearing READ-ONLY mode.");
                forceReadOnly = false;
            }
        }
        return connected && !forceReadOnly;
    }

    /**
     * Check if system should accept new orders (not in READ-ONLY mode).
     */
    public boolean canPlaceOrders() {
        if (forceReadOnly) {
            log.warn("[FYERS] ⛔ Order rejected - system in READ-ONLY mode (stale feed)");
            return false;
        }
        return connected && wsState == WsState.CONNECTED;
    }

    /**
     * Check if WebSocket connection is active.
     */
    public boolean isWebSocketConnected() {
        WebSocket ws = wsRef.get();
        return ws != null && !ws.isOutputClosed() && !ws.isInputClosed();
    }

    /**
     * Get current WebSocket connection state for health/status reporting.
     */
    public String getConnectionState() {
        return wsState.name();
    }

    /**
     * Get current retry count (consecutive failures).
     */
    public int getRetryCount() {
        return consecutiveFailures.get();
    }

    /**
     * Get last HTTP status code from failed connection attempts.
     */
    public Integer getLastHttpStatus() {
        return lastHttpStatus;
    }

    /**
     * Get last error message from failed connection attempts.
     */
    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    /**
     * Reconnect WebSocket (used by WatchdogManager for self-healing).
     */
    public void reconnectWebSocket() {
        log.info("[FYERS] Attempting to reconnect WebSocket...");

        // Close existing connection if any
        WebSocket ws = wsRef.getAndSet(null);
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "Reconnect");
            } catch (Exception e) {
                log.debug("[FYERS] Error closing existing WebSocket: {}", e.getMessage());
            }
        }

        // Trigger reconnect
        wsState = WsState.CONNECTING;
        reconnectScheduler.submit(this::connectWithRetry);
    }

    @Override
    public CompletableFuture<OrderResult> placeOrder(OrderRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected) {
                return OrderResult.failure("Not connected", "NOT_CONNECTED");
            }
            
            log.info("[FYERS] Placing order: {} {} {} qty={} @ {}", 
                     request.transactionType(), request.symbol(), request.orderType(),
                     request.quantity(), request.price());
            
            // Fyers uses different symbol format: NSE:SBIN-EQ
            String orderId = "FY" + System.currentTimeMillis();
            
            String status = "MARKET".equals(request.orderType()) ? "COMPLETE" : "OPEN";
            int filledQty = "MARKET".equals(request.orderType()) ? request.quantity() : 0;
            BigDecimal avgPrice = "MARKET".equals(request.orderType()) 
                ? request.price() != null ? request.price() : new BigDecimal("100")
                : BigDecimal.ZERO;
            
            OrderStatus orderStatus = new OrderStatus(
                orderId,
                request.symbol(),
                request.exchange(),
                request.transactionType(),
                request.orderType(),
                request.productType(),
                request.quantity(),
                filledQty,
                request.quantity() - filledQty,
                request.price(),
                avgPrice,
                request.triggerPrice(),
                status,
                "Order placed",
                Instant.now().toString(),
                "FY" + System.currentTimeMillis(),
                request.tag()
            );
            
            orders.put(orderId, orderStatus);
            
            log.info("[FYERS] Order placed: {} status={}", orderId, status);
            return OrderResult.success(orderId);
        });
    }
    
    @Override
    public CompletableFuture<OrderResult> modifyOrder(String orderId, OrderModifyRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected) return OrderResult.failure("Not connected", "NOT_CONNECTED");
            
            OrderStatus existing = orders.get(orderId);
            if (existing == null) return OrderResult.failure("Order not found", "ORDER_NOT_FOUND");
            
            log.info("[FYERS] Modifying order: {}", orderId);
            
            OrderStatus modified = new OrderStatus(
                orderId, existing.symbol(), existing.exchange(), existing.transactionType(),
                request.orderType() != null ? request.orderType() : existing.orderType(),
                existing.productType(),
                request.quantity() > 0 ? request.quantity() : existing.quantity(),
                existing.filledQuantity(), existing.pendingQuantity(),
                request.price() != null ? request.price() : existing.price(),
                existing.averagePrice(),
                request.triggerPrice() != null ? request.triggerPrice() : existing.triggerPrice(),
                existing.status(), "Modified", Instant.now().toString(),
                existing.exchangeOrderId(), existing.tag()
            );
            
            orders.put(orderId, modified);
            return OrderResult.success(orderId);
        });
    }
    
    @Override
    public CompletableFuture<OrderResult> cancelOrder(String orderId) {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected) return OrderResult.failure("Not connected", "NOT_CONNECTED");
            
            OrderStatus existing = orders.get(orderId);
            if (existing == null) return OrderResult.failure("Order not found", "ORDER_NOT_FOUND");
            
            log.info("[FYERS] Cancelling order: {}", orderId);
            
            OrderStatus cancelled = new OrderStatus(
                orderId, existing.symbol(), existing.exchange(), existing.transactionType(),
                existing.orderType(), existing.productType(), existing.quantity(),
                existing.filledQuantity(), 0, existing.price(), existing.averagePrice(),
                existing.triggerPrice(), "CANCELLED", "Cancelled", Instant.now().toString(),
                existing.exchangeOrderId(), existing.tag()
            );
            
            orders.put(orderId, cancelled);
            return OrderResult.success(orderId);
        });
    }
    
    @Override
    public CompletableFuture<OrderStatus> getOrderStatus(String orderId) {
        return CompletableFuture.supplyAsync(() -> orders.get(orderId));
    }
    
    @Override
    public CompletableFuture<List<OrderStatus>> getOpenOrders() {
        return CompletableFuture.supplyAsync(() -> 
            orders.values().stream().filter(o -> "OPEN".equals(o.status())).toList()
        );
    }
    
    @Override
    public CompletableFuture<List<Position>> getPositions() {
        return CompletableFuture.completedFuture(List.of());
    }
    
    @Override
    public CompletableFuture<List<Holding>> getHoldings() {
        return CompletableFuture.completedFuture(List.of());
    }
    
    @Override
    public CompletableFuture<AccountFunds> getFunds() {
        return CompletableFuture.supplyAsync(() -> new AccountFunds(
            new BigDecimal("400000"), new BigDecimal("40000"),
            new BigDecimal("440000"), BigDecimal.ZERO, new BigDecimal("400000")
        ));
    }
    
    @Override
    public CompletableFuture<BigDecimal> getLtp(String symbol) {
        return CompletableFuture.supplyAsync(() -> 
            new BigDecimal("100").add(new BigDecimal(Math.random() * 10).setScale(2, java.math.RoundingMode.HALF_UP))
        );
    }
    
    @Override
    public void subscribeTicks(List<String> symbols, TickListener listener) {
        log.info("[FYERS] Subscribing {} to ticks for {} symbols", listener.getClass().getSimpleName(), symbols.size());

        // Always register listeners first
        for (String symbol : symbols) {
            tickListeners.computeIfAbsent(symbol, k -> new CopyOnWriteArrayList<>()).add(listener);
        }

        log.info("[FYERS] Total tick listeners: {} symbols with {} unique listeners",
            tickListeners.size(),
            tickListeners.values().stream().mapToInt(List::size).max().orElse(0));

        // Start connection loop if not already running
        if (wsState == WsState.DISCONNECTED) {
            wsState = WsState.CONNECTING;
            startWebSocketConnectionLoop();
        }

        // Only attempt subscribe if WebSocket is CONNECTED
        if (wsState == WsState.CONNECTED && wsRef.get() != null) {
            sendSubscribeMessage(symbols);
        } else {
            log.warn("[FYERS] WebSocket not connected (state={}). Subscription will be sent when connection establishes.", wsState);
        }
    }

    /**
     * Send subscribe message for symbols. Guards against null WebSocket.
     */
    private void sendSubscribeMessage(List<String> symbols) {
        if (wsRef.get() == null || wsState != WsState.CONNECTED) {
            log.warn("[FYERS] Cannot subscribe - WebSocket not available (state={})", wsState);
            return;
        }

        try {
            List<String> fyersSymbols = symbols.stream()
                .map(this::convertToFyersSymbol)
                .toList();

            // Fyers WebSocket subscribe message format:
            // {"T":"SUB_L2","L2LIST":["NSE:SBIN-EQ","NSE:RELIANCE-EQ"],"SUB_T":1}
            ObjectNode subscribeMsg = objectMapper.createObjectNode();
            subscribeMsg.put("T", "SUB_L2");
            subscribeMsg.set("L2LIST", objectMapper.valueToTree(fyersSymbols));
            subscribeMsg.put("SUB_T", 1);

            String message = objectMapper.writeValueAsString(subscribeMsg);
            safeSend(message);

            log.info("[FYERS] ✅ WebSocket subscription sent for {} symbols", fyersSymbols.size());
        } catch (Exception e) {
            log.error("[FYERS] Failed to send subscription message", e);
            // Don't throw - let retry logic handle it
            wsState = WsState.RECONNECT_REQUIRED;
            scheduleReconnect(calculateBackoff());
        }
    }

    /**
     * Start WebSocket connection loop with automatic retry.
     */
    private void startWebSocketConnectionLoop() {
        log.info("[FYERS] Starting WebSocket connection loop");
        reconnectScheduler.submit(this::connectWithRetry);
    }

    /**
     * Attempt WebSocket connection with retry logic.
     */
    private void connectWithRetry() {
        while (wsState != WsState.CONNECTED && !Thread.currentThread().isInterrupted()) {
            if (initializeWebSocket()) {
                // Success - subscribe to all pending symbols
                wsState = WsState.CONNECTED;
                consecutiveFailures.set(0);

                if (!tickListeners.isEmpty()) {
                    List<String> allSymbols = new ArrayList<>(tickListeners.keySet());
                    log.info("[FYERS] Connection established - subscribing to {} symbols", allSymbols.size());
                    sendSubscribeMessage(allSymbols);
                }
                break;
            } else {
                // Failed - calculate backoff and retry
                int failures = consecutiveFailures.incrementAndGet();
                long backoffMs = calculateBackoff();

                if (failures >= 10) {
                    // Circuit breaker - pause for 5 minutes after 10 consecutive failures
                    log.error("[FYERS] ⚠️  CIRCUIT BREAKER: {} consecutive failures. FYERS WebSocket appears down. Pausing for 5 minutes.", failures);
                    backoffMs = 300_000; // 5 minutes
                }

                log.warn("[FYERS] Retry #{} in {}ms (state={})", failures, backoffMs, wsState);

                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * Schedule reconnect after a delay.
     */
    private void scheduleReconnect(long delayMs) {
        log.info("[FYERS] Scheduling reconnect in {}ms", delayMs);
        reconnectScheduler.schedule(() -> {
            if (wsState == WsState.RECONNECT_REQUIRED) {
                wsState = WsState.CONNECTING;
                connectWithRetry();
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Calculate exponential backoff with jitter for retry.
     * Retries: 1s, 2s, 4s, 8s, 16s, 32s, 60s (capped)
     */
    private long calculateBackoff() {
        int failures = consecutiveFailures.get();
        long baseBackoff = Math.min((long) Math.pow(2, Math.min(failures, 6)) * 1000, 60_000); // Cap at 60s
        long jitter = (long) (Math.random() * 500); // 0-500ms jitter
        return baseBackoff + jitter;
    }

    /**
     * Initialize WebSocket connection to Fyers.
     * Returns true if successful, false if should retry.
     */
    private boolean initializeWebSocket() {
        try {
            log.info("[FYERS] Initializing WebSocket connection...");

            // FYERS WebSocket v2 requires access token in format: appId:accessToken
            String wsAccessToken = appId + ":" + accessToken;

            // Connect to WebSocket endpoint (NO query parameters - just the base URL with trailing slash)
            String wsUrl = WS_URL;
            log.info("[FYERS] WebSocket URL: {}", wsUrl);
            log.info("[FYERS] Access token format: {}:***", appId);

            CompletableFuture<WebSocket> wsFuture = httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
                    private final StringBuilder messageBuffer = new StringBuilder();

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        log.info("[FYERS] ✅ WebSocket handshake successful - onOpen called");
                        // Store WebSocket reference FIRST
                        wsRef.set(webSocket);
                        // Clear error tracking on successful connection
                        lastHttpStatus = null;
                        lastErrorMessage = null;
                        consecutiveFailures.set(0);
                        // Request to start receiving data
                        webSocket.request(1);
                        log.info("[FYERS] WebSocket now ready for sending messages");
                        WebSocket.Listener.super.onOpen(webSocket);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        messageBuffer.append(data);
                        if (last) {
                            String message = messageBuffer.toString();
                            messageBuffer.setLength(0);
                            processWebSocketMessage(message);
                        }
                        return WebSocket.Listener.super.onText(webSocket, data, last);
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        log.error("[FYERS] WebSocket error: {}", error.getMessage());
                        WebSocket.Listener.super.onError(webSocket, error);
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        log.info("[FYERS] WebSocket closed: {} - {}", statusCode, reason);
                        return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
                    }
                });

            WebSocket ws = wsFuture.get(10, TimeUnit.SECONDS);
            // Note: wsRef is set in onOpen callback, not here
            log.info("[FYERS] ✅ WebSocket future completed (onOpen should have been called)");
            lastConnectAttempt.set(System.currentTimeMillis());
            return true;

        } catch (java.util.concurrent.ExecutionException e) {
            wsRef.set(null);
            lastConnectAttempt.set(System.currentTimeMillis());

            // Extract root cause for better error visibility
            Throwable cause = e.getCause();
            if (cause instanceof java.net.http.WebSocketHandshakeException) {
                java.net.http.WebSocketHandshakeException wsEx = (java.net.http.WebSocketHandshakeException) cause;

                // Extract HTTP response information
                int statusCode = -1;
                try {
                    java.net.http.HttpResponse<?> response = wsEx.getResponse();
                    if (response != null) {
                        statusCode = response.statusCode();
                        // Track error for health reporting
                        lastHttpStatus = statusCode;
                        lastErrorMessage = "WebSocket handshake failed - HTTP " + statusCode;

                        log.error("[FYERS] ❌ WebSocket handshake FAILED - HTTP {}", statusCode);
                        log.error("[FYERS]    URL: {}", WS_URL);
                        log.error("[FYERS]    App ID: {}", appId);
                        log.error("[FYERS]    Response Headers: {}", response.headers().map());
                        log.error("[FYERS]    Response Body: {}", response.body());
                    } else {
                        lastHttpStatus = null;
                        lastErrorMessage = "WebSocket handshake failed - No HTTP response";
                        log.error("[FYERS] ❌ WebSocket handshake FAILED - No HTTP response");
                    }
                } catch (Exception ex) {
                    log.warn("[FYERS]    Could not extract HTTP response details: {}", ex.getMessage());
                }

                // Determine if error is retryable
                boolean shouldRetry = isRetryableStatus(statusCode);

                if (!shouldRetry) {
                    log.error("[FYERS] ⛔ NON-RETRYABLE ERROR: HTTP {}", statusCode);
                    if (statusCode == 401) {
                        log.error("[FYERS]    → Token expired or invalid. Waiting for TokenRefreshWatchdog...");
                    } else if (statusCode == 403) {
                        log.error("[FYERS]    → App lacks streaming permission. Check FYERS dashboard.");
                    } else if (statusCode == 404) {
                        log.error("[FYERS]    → Wrong endpoint URL.");
                    }
                    // For non-retryable errors, pause longer
                    wsState = WsState.DISCONNECTED;
                    return false;
                } else {
                    log.warn("[FYERS] ⟳ RETRYABLE ERROR: HTTP {} (transient failure)", statusCode);
                    return false;
                }

            } else {
                log.error("[FYERS] WebSocket error: {}", cause != null ? cause.toString() : e.toString());
                return false; // Retry on any other error
            }
        } catch (TimeoutException e) {
            wsRef.set(null);
            log.error("[FYERS] WebSocket connection timeout after 10s");
            return false; // Retry on timeout
        } catch (Exception e) {
            wsRef.set(null);
            log.error("[FYERS] WebSocket connection error: {}", e.toString(), e);
            return false; // Retry on any exception
        }
    }

    /**
     * Check if HTTP status code is retryable.
     * Retry on: 503, 502, 504, 429, timeouts, IO errors
     * Don't retry on: 401, 403, 404
     */
    private boolean isRetryableStatus(int statusCode) {
        return statusCode == 503 || statusCode == 502 || statusCode == 504 || statusCode == 429 || statusCode == -1;
    }

    /**
     * Process incoming WebSocket message from Fyers.
     */
    private void processWebSocketMessage(String message) {
        try {
            JsonNode messageJson = objectMapper.readTree(message);

            // Fyers tick data format:
            // {"T":"df","d":{"s":"NSE:SBIN-EQ","lp":750.50,"op":748.00,"hp":752.00,"lp_ltt":1234567890,...}}
            if (messageJson.has("T") && "df".equals(messageJson.get("T").asText())) {
                JsonNode data = messageJson.get("d");
                if (data != null) {
                    String fyersSymbol = data.get("s").asText();
                    String symbol = convertFromFyersSymbol(fyersSymbol);

                    List<TickListener> listeners = tickListeners.get(symbol);
                    if (listeners != null && !listeners.isEmpty()) {
                        BigDecimal lastPrice = new BigDecimal(data.get("lp").asText());
                        BigDecimal open = data.has("op") ? new BigDecimal(data.get("op").asText()) : lastPrice;
                        BigDecimal high = data.has("hp") ? new BigDecimal(data.get("hp").asText()) : lastPrice;
                        BigDecimal low = data.has("lp_t") ? new BigDecimal(data.get("lp_t").asText()) : lastPrice;
                        long volume = data.has("v") ? data.get("v").asLong() : 0;
                        long timestamp = data.has("lp_ltt") ? data.get("lp_ltt").asLong() : System.currentTimeMillis();

                        // Update last successful tick timestamp (for stale feed detection)
                        lastSuccessfulTick.set(System.currentTimeMillis());

                        Tick tick = new Tick(
                            symbol, lastPrice, open, high, low,
                            lastPrice, volume,
                            lastPrice, lastPrice,
                            0, 0, timestamp
                        );

                        // Call all listeners for this symbol
                        for (TickListener listener : listeners) {
                            try {
                                listener.onTick(tick);
                            } catch (Exception e) {
                                log.warn("[FYERS] Tick processing error for {} ({}): {}",
                                    symbol, listener.getClass().getSimpleName(), e.getMessage());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[FYERS] Failed to process WebSocket message: {}", e.getMessage());
        }
    }

    /**
     * Convert Fyers symbol format to AnnuPaper format.
     * Fyers: NSE:RELIANCE-EQ → AnnuPaper: RELIANCE
     */
    private String convertFromFyersSymbol(String fyersSymbol) {
        // NSE:RELIANCE-EQ → RELIANCE
        if (fyersSymbol.contains(":")) {
            String[] parts = fyersSymbol.split(":");
            if (parts.length == 2) {
                String symbolPart = parts[1];
                if (symbolPart.contains("-")) {
                    return symbolPart.split("-")[0];
                }
                return symbolPart;
            }
        }
        return fyersSymbol;
    }
    
    @Override
    public CompletableFuture<List<HistoricalCandle>> getHistoricalCandles(
        String symbol,
        int interval,
        long fromEpoch,
        long toEpoch
    ) {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected) {
                throw new RuntimeException("Not connected");
            }

            log.info("[FYERS] Fetching historical candles: {} interval={} from={} to={}",
                     symbol, interval, fromEpoch, toEpoch);

            try {
                // Convert symbol format: RELIANCE → NSE:RELIANCE-EQ
                String fyersSymbol = convertToFyersSymbol(symbol);

                // Handle custom intervals by aggregating smaller candles
                if (interval == 25) {
                    // 25-min = aggregate 5 × 5-min candles
                    log.info("[FYERS] Building 25-min candles from 5-min candles");
                    List<HistoricalCandle> fiveMinCandles = fetchFyersCandles(fyersSymbol, "5", fromEpoch, toEpoch);
                    return aggregateCandles(fiveMinCandles, 5, 25 * 60);
                } else if (interval == 125) {
                    // 125-min = aggregate 5 × 25-min candles
                    // First build 25-min candles from 5-min
                    log.info("[FYERS] Building 125-min candles from 5-min candles (via 25-min aggregation)");
                    List<HistoricalCandle> fiveMinCandles = fetchFyersCandles(fyersSymbol, "5", fromEpoch, toEpoch);
                    List<HistoricalCandle> twentyFiveMinCandles = aggregateCandles(fiveMinCandles, 5, 25 * 60);
                    return aggregateCandles(twentyFiveMinCandles, 5, 125 * 60);
                } else {
                    // Standard intervals supported by Fyers
                    String resolution = convertToFyersResolution(interval);
                    return fetchFyersCandles(fyersSymbol, resolution, fromEpoch, toEpoch);
                }

            } catch (Exception e) {
                log.error("[FYERS] Error fetching historical candles: {}", e.getMessage());
                throw new RuntimeException("Failed to fetch historical candles", e);
            }
        });
    }

    /**
     * Fetch raw candles from Fyers API.
     */
    private List<HistoricalCandle> fetchFyersCandles(String fyersSymbol, String resolution, long fromEpoch, long toEpoch) {
        try {
            // Build query string
            // Note: Using date_format=0 for epoch timestamps (FYERS seems to return epoch regardless)
            String queryString = String.format(
                "symbol=%s&resolution=%s&date_format=0&range_from=%d&range_to=%d&cont_flag=0",
                java.net.URLEncoder.encode(fyersSymbol, StandardCharsets.UTF_8),
                resolution,
                fromEpoch,
                toEpoch
            );

            // Make HTTP GET request (FYERS History API uses GET, not POST)
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/data/history?" + queryString))
                .header("Authorization", appId + ":" + accessToken)
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("[FYERS] Historical API HTTP {}: {}", response.statusCode(), response.body());
                throw new RuntimeException("HTTP error " + response.statusCode());
            }

            // Parse response
            JsonNode responseJson = objectMapper.readTree(response.body());

            if (!responseJson.has("s") || !"ok".equals(responseJson.get("s").asText())) {
                log.error("[FYERS] Historical API failed: {}", responseJson);
                throw new RuntimeException("API error: " + responseJson.get("message").asText("Unknown error"));
            }

            // Parse candles array
            JsonNode candlesArray = responseJson.get("candles");
            if (candlesArray == null || !candlesArray.isArray()) {
                log.warn("[FYERS] No candles returned");
                return List.of();
            }

            List<HistoricalCandle> candles = new ArrayList<>();
            for (JsonNode candleNode : candlesArray) {
                // Format: [timestamp, open, high, low, close, volume]
                long timestamp = candleNode.get(0).asLong();
                BigDecimal open = new BigDecimal(candleNode.get(1).asText());
                BigDecimal high = new BigDecimal(candleNode.get(2).asText());
                BigDecimal low = new BigDecimal(candleNode.get(3).asText());
                BigDecimal close = new BigDecimal(candleNode.get(4).asText());
                long volume = candleNode.get(5).asLong();

                candles.add(new HistoricalCandle(timestamp, open, high, low, close, volume));
            }

            log.info("[FYERS] Fetched {} candles (resolution={})", candles.size(), resolution);
            return candles;

        } catch (Exception e) {
            log.error("[FYERS] Error fetching candles: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch candles", e);
        }
    }

    /**
     * Aggregate smaller candles into larger timeframe candles.
     *
     * @param sourceCandles List of smaller timeframe candles
     * @param aggregationFactor Number of source candles to combine (e.g., 5 for 5-min → 25-min)
     * @param targetIntervalSeconds Target interval in seconds (e.g., 25*60 for 25-min)
     * @return List of aggregated candles
     */
    private List<HistoricalCandle> aggregateCandles(
        List<HistoricalCandle> sourceCandles,
        int aggregationFactor,
        long targetIntervalSeconds
    ) {
        if (sourceCandles.isEmpty()) {
            return List.of();
        }

        List<HistoricalCandle> aggregated = new ArrayList<>();
        List<HistoricalCandle> batch = new ArrayList<>();

        for (HistoricalCandle candle : sourceCandles) {
            batch.add(candle);

            if (batch.size() == aggregationFactor) {
                // Aggregate this batch
                long timestamp = batch.get(0).timestamp();  // Use first candle's timestamp
                BigDecimal open = batch.get(0).open();      // First candle's open
                BigDecimal close = batch.get(batch.size() - 1).close();  // Last candle's close

                // Find highest high and lowest low
                BigDecimal high = batch.stream()
                    .map(HistoricalCandle::high)
                    .max(BigDecimal::compareTo)
                    .orElse(open);

                BigDecimal low = batch.stream()
                    .map(HistoricalCandle::low)
                    .min(BigDecimal::compareTo)
                    .orElse(open);

                // Sum volumes
                long totalVolume = batch.stream()
                    .mapToLong(HistoricalCandle::volume)
                    .sum();

                aggregated.add(new HistoricalCandle(timestamp, open, high, low, close, totalVolume));
                batch.clear();
            }
        }

        // Handle remaining candles (partial batch at the end)
        if (!batch.isEmpty()) {
            log.debug("[FYERS] Discarding {} incomplete candles at end", batch.size());
        }

        log.info("[FYERS] Aggregated {} candles from {} source candles (factor={})",
                 aggregated.size(), sourceCandles.size(), aggregationFactor);

        return aggregated;
    }

    @Override
    public void unsubscribeTicks(List<String> symbols) {
        for (String symbol : symbols) tickListeners.remove(symbol);
    }
    
    private String maskKey(String key) {
        if (key == null || key.length() < 8) return "***";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    /**
     * Convert AnnuPaper symbol format to Fyers format.
     * AnnuPaper: RELIANCE, TCS, INFY or NSE:RELIANCE, NSE:TCS, NSE:INFY
     * Fyers: NSE:RELIANCE-EQ, NSE:TCS-EQ, NSE:INFY-EQ
     */
    private String convertToFyersSymbol(String symbol) {
        // Handle symbols that already have exchange prefix
        if (symbol.startsWith("NSE:")) {
            // Extract symbol without exchange prefix
            String symbolOnly = symbol.substring(4); // Remove "NSE:"
            return "NSE:" + symbolOnly + "-EQ";
        }
        // Default to NSE equity
        return "NSE:" + symbol + "-EQ";
    }

    /**
     * Convert interval (minutes) to Fyers resolution format.
     * Fyers supports: 1, 2, 3, 5, 10, 15, 20, 30, 60, 120, 240, 1D
     *
     * Note: 25-min and 125-min are NOT mapped here - they are handled
     * by aggregating 5-min candles in getHistoricalCandles().
     */
    private String convertToFyersResolution(int intervalMinutes) {
        return switch (intervalMinutes) {
            case 1 -> "1";
            case 2 -> "2";
            case 3 -> "3";
            case 5 -> "5";
            case 10 -> "10";
            case 15 -> "15";
            case 20 -> "20";
            case 30 -> "30";
            case 60 -> "60";
            case 120 -> "120";
            case 240 -> "240";
            case 1440 -> "1D";  // Daily
            default -> {
                log.warn("[FYERS] Unsupported interval {}, defaulting to 1-min", intervalMinutes);
                yield "1";
            }
        };
    }

    @Override
    public CompletableFuture<List<Instrument>> getInstruments() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("[FYERS] Downloading NSE EQ instruments from Fyers public API...");

                // Download NSE Cash Market symbols master from Fyers public API
                String csvUrl = "https://public.fyers.in/sym_details/NSE_CM.csv";
                java.net.URL url = new java.net.URL(csvUrl);
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(url.openStream())
                );

                List<Instrument> instruments = new ArrayList<>();
                String line;

                while ((line = reader.readLine()) != null) {
                    // CSV format (NO header line):
                    // 0=Token, 1=Company Name, 4=Tick Size, 9=Trading Symbol (NSE:SYMBOL-EQ),
                    // 10=Segment (10=NSE CM), 12=Lot Size, 13=Short Symbol
                    // Example: 1010000000100,AMARA RAJA ENERGY MOB LTD,0,1,0.05,INE885A01032,0915-1530|1815-1915:,2026-01-09,,NSE:ARE&M-EQ,10,10,100,ARE&M,100,-1.0,XX,1010000000100,None,1,3.4

                    String[] fields = line.split(",", -1);

                    if (fields.length < 14) continue;

                    String token = fields[0].trim();
                    String companyName = fields[1].trim();
                    String tickSizeStr = fields[4].trim();
                    String tradingSymbol = fields[9].trim();  // NSE:SYMBOL-EQ
                    String segmentStr = fields[10].trim();    // "10" for NSE CM
                    String lotSizeStr = fields[12].trim();
                    String shortSymbol = fields[13].trim();

                    // Filter for NSE equity instruments only (segment 10 = NSE CM, trading symbol ends with -EQ)
                    if (!"10".equals(segmentStr) || !tradingSymbol.endsWith("-EQ")) {
                        continue;
                    }

                    // Parse lot size and tick size
                    int lotSize = 1;
                    try {
                        if (!lotSizeStr.isEmpty()) {
                            lotSize = Integer.parseInt(lotSizeStr);
                        }
                    } catch (NumberFormatException e) {
                        lotSize = 1;
                    }

                    BigDecimal tickSize = new BigDecimal("0.05");
                    try {
                        if (!tickSizeStr.isEmpty()) {
                            tickSize = new BigDecimal(tickSizeStr);
                        }
                    } catch (NumberFormatException e) {
                        tickSize = new BigDecimal("0.05");
                    }

                    instruments.add(new Instrument(
                        "NSE",             // exchange: NSE
                        tradingSymbol,     // tradingSymbol: NSE:ARE&M-EQ
                        companyName,       // name: AMARA RAJA ENERGY MOB LTD
                        "EQUITY",          // instrumentType: EQUITY
                        "EQUITY",          // segment: EQUITY
                        token,             // token: 1010000000100
                        lotSize,           // lotSize: 100
                        tickSize,          // tickSize: 0.05
                        null,              // expiryDate (null for EQ)
                        null,              // strikePrice (null for EQ)
                        null               // optionType (null for EQ)
                    ));
                }

                reader.close();

                log.info("[FYERS] Downloaded {} NSE EQ instruments from Fyers API", instruments.size());
                return instruments;

            } catch (Exception e) {
                log.error("[FYERS] Error downloading instruments from Fyers API: {}", e.getMessage(), e);
                log.warn("[FYERS] Falling back to hardcoded instrument list");

                // Fallback to hardcoded list if download fails
                return getFallbackInstruments();
            }
        });
    }

    /**
     * Fallback method that returns hardcoded list if API download fails.
     */
    private List<Instrument> getFallbackInstruments() {
        List<Instrument> instruments = new ArrayList<>();

        String[][] stocks = {
            {"RELIANCE", "Reliance Industries Ltd"}, {"TCS", "Tata Consultancy Services Ltd"},
            {"HDFCBANK", "HDFC Bank Ltd"}, {"INFY", "Infosys Ltd"},
            {"ICICIBANK", "ICICI Bank Ltd"}, {"SBIN", "State Bank of India"}
        };

        for (String[] stock : stocks) {
            instruments.add(new Instrument(
                "NSE", "NSE:" + stock[0] + "-EQ", stock[1], "EQ", "EQUITY",
                "NSE_" + stock[0], 1, new BigDecimal("0.05"), null, null, null
            ));
        }

        log.info("[FYERS] Generated {} fallback instruments", instruments.size());
        return instruments;
    }
}
