package in.annupaper.infrastructure.broker.adapters;

import in.annupaper.domain.model.*;
import in.annupaper.application.port.output.UserBrokerSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Zerodha Kite Connect Data Adapter - Production Implementation
 *
 * Uses:
 * - Kite Connect REST API v3 for historical data
 * - Kite Ticker WebSocket for real-time market data
 *
 * This adapter is designed for DATA-only mode. For execution, use a separate
 * EXEC broker.
 *
 * API Docs: https://kite.trade/docs/connect/v3/
 * WebSocket Protocol: https://kite.trade/docs/connect/v3/websocket/
 */
public class ZerodhaDataAdapter implements BrokerAdapter {
    private static final Logger log = LoggerFactory.getLogger(ZerodhaDataAdapter.class);

    private static final String BASE_URL = "https://api.kite.trade";
    private static final String WS_URL = "wss://ws.kite.trade";

    // Kite Ticker binary packet modes
    private static final int MODE_LTP = 1; // Last price only
    private static final int MODE_QUOTE = 2; // LTP + OHLC + volume
    private static final int MODE_FULL = 3; // All fields including depth

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
    private String apiKey;

    private final AtomicReference<WebSocket> wsRef = new AtomicReference<>(null);

    // Multiple listeners per symbol (TickCandleBuilder, ExitSignalService, etc.)
    private final Map<String, List<TickListener>> tickListeners = new ConcurrentHashMap<>();

    // Instrument token mapping: symbol -> instrument_token
    private final Map<String, Long> symbolToToken = new ConcurrentHashMap<>();
    private final Map<Long, String> tokenToSymbol = new ConcurrentHashMap<>();

    // WebSocket connection state
    private enum WsState {
        DISCONNECTED, CONNECTING, CONNECTED, RECONNECT_REQUIRED
    }

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
        Thread t = new Thread(r, "ZerodhaReconnect");
        t.setDaemon(true);
        return t;
    });

    /**
     * Constructor with session management for DATA broker role.
     */
    public ZerodhaDataAdapter(UserBrokerSessionRepository sessionRepo, String userBrokerId) {
        this.sessionRepo = sessionRepo;
        this.userBrokerId = userBrokerId;
    }

    /**
     * Default constructor for backwards compatibility.
     */
    public ZerodhaDataAdapter() {
        this.sessionRepo = null;
        this.userBrokerId = null;
    }

    @Override
    public String getBrokerCode() {
        return "ZERODHA";
    }

    @Override
    public CompletableFuture<ConnectionResult> connect(BrokerCredentials credentials) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("[ZERODHA] Connecting with apiKey={}", maskKey(credentials.apiKey()));

            this.credentials = credentials;
            this.apiKey = credentials.apiKey();

            if (apiKey == null || apiKey.isEmpty()) {
                return ConnectionResult.ofFailure("Invalid API key", "INVALID_API_KEY");
            }

            // Load access token from session repository
            if (sessionRepo != null && userBrokerId != null) {
                log.info("[ZERODHA] Loading access token from session repository for userBrokerId={}", userBrokerId);

                Optional<UserBrokerSession> sessionOpt = sessionRepo.findActiveSession(userBrokerId);

                if (sessionOpt.isEmpty()) {
                    log.error("[ZERODHA] No active session found for userBrokerId={}. Please connect via OAuth.",
                            userBrokerId);
                    return ConnectionResult.ofFailure("No active session. Please connect via OAuth.",
                            "NO_ACTIVE_SESSION");
                }

                UserBrokerSession session = sessionOpt.get();

                if (!session.isActive()) {
                    log.error("[ZERODHA] Session {} is not active or expired (status={}, validTill={})",
                            session.sessionId(), session.sessionStatus(), session.tokenValidTill());
                    return ConnectionResult.ofFailure("Session expired. Please reconnect via OAuth.",
                            "SESSION_EXPIRED");
                }

                this.accessToken = session.accessToken();
                log.info("[ZERODHA] Loaded access token from session {} (valid till {})",
                        session.sessionId(), session.tokenValidTill());
            } else {
                // Fallback: Load from credentials
                log.warn("[ZERODHA] No session repository configured. Loading access token from credentials.");
                this.accessToken = credentials.accessToken();
            }

            if (accessToken == null || accessToken.isEmpty()) {
                log.error("[ZERODHA] No access token available. Please connect via OAuth.");
                return ConnectionResult.ofFailure("Access token required. Please connect via OAuth.",
                        "NO_ACCESS_TOKEN");
            }

            // Validate access token by fetching profile
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/user/profile"))
                        .header("X-Kite-Version", "3")
                        .header("Authorization", "token " + apiKey + ":" + accessToken)
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode responseJson = objectMapper.readTree(response.body());
                    if (responseJson.has("status") && "success".equals(responseJson.get("status").asText())) {
                        this.sessionToken = accessToken;
                        this.connected = true;
                        log.info("[ZERODHA] Connected successfully - Profile: {}", responseJson.get("data"));

                        // Load instrument master SYNCHRONOUSLY before returning
                        // This ensures instruments are available before subscription
                        log.info("[ZERODHA] Loading instruments before completing connection...");
                        loadInstrumentMaster();
                        log.info("[ZERODHA] Instruments loaded, connection complete");

                        return ConnectionResult.ofSuccess(sessionToken);
                    } else {
                        log.error("[ZERODHA] Profile API failed: {}", responseJson);
                        return ConnectionResult.ofFailure("Authentication failed", "AUTH_FAILED");
                    }
                } else {
                    log.error("[ZERODHA] Profile API HTTP {}: {}", response.statusCode(), response.body());
                    return ConnectionResult.ofFailure("HTTP error " + response.statusCode(), "HTTP_ERROR");
                }
            } catch (Exception e) {
                log.error("[ZERODHA] Connection error: {}", e.getMessage());
                return ConnectionResult.ofFailure(e.getMessage(), "CONNECTION_ERROR");
            }
        });
    }

    /**
     * Load instrument master from Kite API and build symbol <-> token mapping.
     */
    private void loadInstrumentMaster() {
        try {
            log.info("[ZERODHA] Loading instrument master...");
            log.info("[ZERODHA] API URL: {}/instruments", BASE_URL);
            log.info("[ZERODHA] Access token: {}", accessToken != null ? maskKey(accessToken) : "NULL");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/instruments"))
                    .header("X-Kite-Version", "3")
                    .header("Authorization", "token " + apiKey + ":" + accessToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("[ZERODHA] Instrument API response code: {}", response.statusCode());

            if (response.statusCode() == 200) {
                // Parse CSV response (instrument_token, exchange_token, tradingsymbol, ...)
                String[] lines = response.body().split("\n");
                int count = 0;
                log.info("[ZERODHA] Parsing {} lines from instrument CSV", lines.length);

                // Log first few lines to understand format
                if (lines.length > 0) {
                    log.info("[ZERODHA] CSV Header: {}", lines[0]);
                }
                if (lines.length > 1) {
                    log.info("[ZERODHA] CSV Sample row: {}", lines[1]);
                }

                int nseCount = 0;
                for (int i = 1; i < lines.length; i++) { // Skip header
                    String[] fields = lines[i].split(",");
                    if (fields.length >= 12) { // Need at least 12 fields to get exchange
                        try {
                            long instrumentToken = Long.parseLong(fields[0].trim());
                            String tradingSymbol = fields[2].trim();
                            String exchange = fields[11].trim(); // Exchange is at index 11

                            // Log first NSE symbol found for debugging
                            if ("NSE".equals(exchange) && nseCount == 0) {
                                log.info("[ZERODHA] Found NSE symbol: {} (token: {})", tradingSymbol, instrumentToken);
                                nseCount++;
                            }

                            // Store NSE equity symbols only
                            if ("NSE".equals(exchange) && !tradingSymbol.isEmpty()) {
                                // Remove -EQ suffix and add NSE: prefix to match watchlist format
                                String cleanSymbol = tradingSymbol.replace("-EQ", "");
                                String symbolWithPrefix = "NSE:" + cleanSymbol;
                                symbolToToken.put(symbolWithPrefix, instrumentToken);
                                tokenToSymbol.put(instrumentToken, symbolWithPrefix);
                                count++;
                            }
                        } catch (Exception e) {
                            // Skip invalid lines
                        }
                    }
                }

                log.info("[ZERODHA] ✅ Loaded {} NSE instruments into mapping cache", count);
            } else {
                log.error("[ZERODHA] Failed to load instruments: HTTP {} - {}",
                    response.statusCode(), response.body().substring(0, Math.min(200, response.body().length())));
            }
        } catch (Exception e) {
            log.error("[ZERODHA] Error loading instrument master", e);
        }
    }

    @Override
    public void disconnect() {
        log.info("[ZERODHA] Disconnecting...");
        connected = false;
        sessionToken = null;
        tickListeners.clear();
        wsState = WsState.DISCONNECTED;

        WebSocket ws = wsRef.getAndSet(null);
        if (ws != null) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "Disconnect");
        }
    }

    /**
     * Reload access token and reinitialize WebSocket connection.
     * Called by TokenRefreshWatchdog when token is refreshed.
     */
    public synchronized void reloadToken(String newAccessToken, String sessionId) {
        log.info("[ZERODHA] ⚡ Token refresh detected - reloading access token from session: {}", sessionId);

        // Close existing WebSocket
        WebSocket ws = wsRef.getAndSet(null);
        if (ws != null) {
            log.info("[ZERODHA] Closing existing WebSocket before token reload");
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "Token refresh");
            } catch (Exception e) {
                log.warn("[ZERODHA] Error closing WebSocket: {}", e.getMessage());
            }
        }

        // Update token
        this.accessToken = newAccessToken;
        log.info("[ZERODHA] ✅ Access token updated (session: {})", sessionId);

        // Reset failure counter and trigger immediate reconnect
        consecutiveFailures.set(0);
        wsState = WsState.CONNECTING;

        // Reload instrument master with new token
        CompletableFuture.runAsync(this::loadInstrumentMaster);

        // Trigger immediate reconnect
        if (!tickListeners.isEmpty()) {
            log.info("[ZERODHA] Triggering immediate reconnect with new token for {} symbols", tickListeners.size());
            reconnectScheduler.submit(this::connectWithRetry);
        } else {
            log.info("[ZERODHA] No active subscriptions - reconnect deferred until next subscribe");
        }
    }

    @Override
    public boolean isConnected() {
        // Check for stale feed
        long lastTick = lastSuccessfulTick.get();
        if (lastTick > 0) {
            long silenceDuration = System.currentTimeMillis() - lastTick;
            if (silenceDuration > MAX_TICK_SILENCE_MS && !forceReadOnly) {
                log.error("[ZERODHA] ⚠️  STALE FEED DETECTED: No ticks for {}ms. Forcing READ-ONLY mode.",
                        silenceDuration);
                forceReadOnly = true;
            } else if (silenceDuration <= MAX_TICK_SILENCE_MS && forceReadOnly) {
                log.info("[ZERODHA] ✅ Feed recovered. Clearing READ-ONLY mode.");
                forceReadOnly = false;
            }
        }
        return connected && !forceReadOnly;
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
     * Get current retry count.
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
     * Get last error message.
     */
    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    @Override
    public void subscribeTicks(List<String> symbols, TickListener listener) {
        log.info("[ZERODHA] Subscribing {} to ticks for {} symbols", listener.getClass().getSimpleName(),
                symbols.size());

        // Register listeners first
        for (String symbol : symbols) {
            tickListeners.computeIfAbsent(symbol, k -> new CopyOnWriteArrayList<>()).add(listener);
        }

        log.info("[ZERODHA] Total tick listeners: {} symbols with {} max listeners per symbol",
                tickListeners.size(),
                tickListeners.values().stream().mapToInt(List::size).max().orElse(0));

        // Start connection loop if not already running
        if (wsState == WsState.DISCONNECTED) {
            wsState = WsState.CONNECTING;
            startWebSocketConnectionLoop();
        }

        // Subscribe if WebSocket is connected
        if (wsState == WsState.CONNECTED && wsRef.get() != null) {
            sendSubscribeMessage(symbols);
        } else {
            log.warn(
                    "[ZERODHA] WebSocket not connected (state={}). Subscription will be sent when connection establishes.",
                    wsState);
        }
    }

    /**
     * Send subscribe message for symbols.
     */
    private void sendSubscribeMessage(List<String> symbols) {
        if (wsRef.get() == null || wsState != WsState.CONNECTED) {
            log.warn("[ZERODHA] Cannot subscribe - WebSocket not available (state={})", wsState);
            return;
        }

        try {
            // Convert symbols to instrument tokens
            List<Long> tokens = new ArrayList<>();
            for (String symbol : symbols) {
                Long token = symbolToToken.get(symbol);
                if (token != null) {
                    tokens.add(token);
                } else {
                    log.warn("[ZERODHA] No instrument token found for symbol: {}", symbol);
                }
            }

            if (tokens.isEmpty()) {
                log.warn("[ZERODHA] No valid tokens to subscribe");
                return;
            }

            // Kite Ticker subscribe message format:
            // {"a":"subscribe","v":[738561,492033]}
            ObjectNode subscribeMsg = objectMapper.createObjectNode();
            subscribeMsg.put("a", "subscribe");
            subscribeMsg.set("v", objectMapper.valueToTree(tokens));

            String message = objectMapper.writeValueAsString(subscribeMsg);
            safeSend(message);

            // Set mode to QUOTE (LTP + OHLC + volume)
            ObjectNode modeMsg = objectMapper.createObjectNode();
            modeMsg.put("a", "mode");
            modeMsg.set("v", objectMapper.valueToTree(new Object[] { MODE_QUOTE, tokens }));

            String modeMessage = objectMapper.writeValueAsString(modeMsg);
            safeSend(modeMessage);

            log.info("[ZERODHA] ✅ WebSocket subscription sent for {} tokens (QUOTE mode)", tokens.size());
        } catch (Exception e) {
            log.error("[ZERODHA] Failed to send subscription message", e);
            wsState = WsState.RECONNECT_REQUIRED;
            scheduleReconnect(calculateBackoff());
        }
    }

    /**
     * Safe send - guards against null WebSocket.
     */
    private void safeSend(String json) {
        WebSocket ws = wsRef.get();
        if (ws == null) {
            log.warn("[ZERODHA] send skipped - socket not open yet (state={})", wsState);
            return;
        }
        try {
            ws.sendText(json, true);
        } catch (Exception e) {
            log.error("[ZERODHA] Failed to send message", e);
            wsState = WsState.RECONNECT_REQUIRED;
            scheduleReconnect(calculateBackoff());
        }
    }

    @Override
    public void unsubscribeTicks(List<String> symbols) {
        log.info("[ZERODHA] Unsubscribing from ticks: {}", symbols);
        for (String symbol : symbols) {
            tickListeners.remove(symbol);
        }

        if (tickListeners.isEmpty() && wsRef.get() != null) {
            disconnect();
        }
    }

    /**
     * Start WebSocket connection loop with automatic retry.
     */
    private void startWebSocketConnectionLoop() {
        log.info("[ZERODHA] Starting WebSocket connection loop");
        reconnectScheduler.submit(this::connectWithRetry);
    }

    /**
     * Attempt WebSocket connection with retry logic.
     */
    private void connectWithRetry() {
        while (wsState != WsState.CONNECTED && !Thread.currentThread().isInterrupted()) {
            if (initializeWebSocket()) {
                wsState = WsState.CONNECTED;
                consecutiveFailures.set(0);

                if (!tickListeners.isEmpty()) {
                    List<String> allSymbols = new ArrayList<>(tickListeners.keySet());
                    log.info("[ZERODHA] Connection established - subscribing to {} symbols", allSymbols.size());
                    sendSubscribeMessage(allSymbols);
                }
                break;
            } else {
                int failures = consecutiveFailures.incrementAndGet();
                long backoffMs = calculateBackoff();

                if (failures >= 10) {
                    log.error("[ZERODHA] ⚠️  CIRCUIT BREAKER: {} consecutive failures. Pausing for 5 minutes.",
                            failures);
                    backoffMs = 300_000; // 5 minutes
                }

                log.warn("[ZERODHA] Retry #{} in {}ms (state={})", failures, backoffMs, wsState);

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
        log.info("[ZERODHA] Scheduling reconnect in {}ms", delayMs);
        reconnectScheduler.schedule(() -> {
            if (wsState == WsState.RECONNECT_REQUIRED) {
                wsState = WsState.CONNECTING;
                connectWithRetry();
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Calculate exponential backoff with jitter.
     */
    private long calculateBackoff() {
        int failures = consecutiveFailures.get();
        long baseBackoff = Math.min((long) Math.pow(2, Math.min(failures, 6)) * 1000, 60_000);
        long jitter = (long) (Math.random() * 500);
        return baseBackoff + jitter;
    }

    /**
     * Initialize WebSocket connection to Kite Ticker.
     */
    private boolean initializeWebSocket() {
        try {
            log.info("[ZERODHA] Initializing WebSocket connection...");

            // Kite Ticker WebSocket URL with access token as query parameter
            String wsUrlWithToken = WS_URL + "?api_key=" + apiKey + "&access_token=" + accessToken;
            log.info("[ZERODHA] WebSocket URL: wss://ws.kite.trade?api_key={}***", apiKey.substring(0, 4));

            CompletableFuture<WebSocket> wsFuture = httpClient.newWebSocketBuilder()
                    .buildAsync(URI.create(wsUrlWithToken), new WebSocket.Listener() {
                        private final ByteBuffer binaryBuffer = ByteBuffer.allocate(65536);

                        @Override
                        public void onOpen(WebSocket webSocket) {
                            log.info("[ZERODHA] ✅ WebSocket handshake successful");
                            wsRef.set(webSocket);
                            lastHttpStatus = null;
                            lastErrorMessage = null;
                            consecutiveFailures.set(0);
                            webSocket.request(1);
                            WebSocket.Listener.super.onOpen(webSocket);
                        }

                        @Override
                        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                            // Text messages are for control (acks, errors)
                            if (last) {
                                log.debug("[ZERODHA] Control message: {}", data);
                            }
                            return WebSocket.Listener.super.onText(webSocket, data, last);
                        }

                        @Override
                        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
                            // Kite Ticker sends tick data as binary packets
                            binaryBuffer.put(data);
                            if (last) {
                                binaryBuffer.flip();
                                processBinaryTickData(binaryBuffer);
                                binaryBuffer.clear();
                            }
                            return WebSocket.Listener.super.onBinary(webSocket, data, last);
                        }

                        @Override
                        public void onError(WebSocket webSocket, Throwable error) {
                            log.error("[ZERODHA] WebSocket error: {}", error.getMessage());
                            WebSocket.Listener.super.onError(webSocket, error);
                        }

                        @Override
                        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                            log.info("[ZERODHA] WebSocket closed: {} - {}", statusCode, reason);
                            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
                        }
                    });

            WebSocket ws = wsFuture.get(10, TimeUnit.SECONDS);
            lastConnectAttempt.set(System.currentTimeMillis());
            return true;

        } catch (Exception e) {
            wsRef.set(null);
            lastConnectAttempt.set(System.currentTimeMillis());
            log.error("[ZERODHA] WebSocket connection error: {}", e.getMessage());
            lastErrorMessage = e.getMessage();
            return false;
        }
    }

    /**
     * Process binary tick data from Kite Ticker.
     *
     * Binary packet format (per Kite Connect v3 docs):
     * - 2 bytes: number of packets
     * - For each packet:
     *   - 2 bytes: packet length
     *   - 4 bytes: instrument token
     *   - 4 bytes: last traded price (int32, divide by 100 for rupees)
     *
     * Mode determined by packet length:
     * - 8 bytes = LTP mode (token + price only)
     * - 44 bytes = QUOTE mode (LTP + OHLC + volume)
     * - 184 bytes = FULL mode (QUOTE + timestamps + market depth)
     */
    private void processBinaryTickData(ByteBuffer buffer) {
        try {
            buffer.order(ByteOrder.BIG_ENDIAN);

            int bufferSize = buffer.remaining();
            if (bufferSize < 2) {
                log.warn("[ZERODHA] Buffer too small for tick data: {} bytes", bufferSize);
                return;
            }

            int numPackets = buffer.getShort() & 0xFFFF;
            if (numPackets > 0 && numPackets < 100) {
                log.info("[ZERODHA] Processing {} tick packets ({} bytes)", numPackets, bufferSize);
            }

            for (int i = 0; i < numPackets; i++) {
                if (buffer.remaining() < 2)
                    break;

                int packetLength = buffer.getShort() & 0xFFFF;
                if (buffer.remaining() < packetLength)
                    break;

                // Instrument token (4 bytes)
                long instrumentToken = buffer.getInt() & 0xFFFFFFFFL;

                // Determine mode by packet length (8=LTP, 44=QUOTE, 184=FULL)
                int mode = MODE_LTP;
                if (packetLength == 44) {
                    mode = MODE_QUOTE;
                } else if (packetLength == 184) {
                    mode = MODE_FULL;
                }

                // Parse fields based on mode
                BigDecimal lastPrice = null;
                BigDecimal open = null;
                BigDecimal high = null;
                BigDecimal low = null;
                BigDecimal close = null;
                long volume = 0;
                long timestamp = System.currentTimeMillis();

                // Last price (4 bytes) - present in all modes
                lastPrice = BigDecimal.valueOf(buffer.getInt() & 0xFFFFFFFFL)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                if (mode == MODE_QUOTE || mode == MODE_FULL) {
                    // QUOTE mode additional fields
                    long lastQty = buffer.getInt() & 0xFFFFFFFFL;
                    long avgPrice = buffer.getInt() & 0xFFFFFFFFL;
                    volume = buffer.getInt() & 0xFFFFFFFFL;
                    long buyQty = buffer.getInt() & 0xFFFFFFFFL;
                    long sellQty = buffer.getInt() & 0xFFFFFFFFL;

                    open = BigDecimal.valueOf(buffer.getInt() & 0xFFFFFFFFL)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    high = BigDecimal.valueOf(buffer.getInt() & 0xFFFFFFFFL).divide(BigDecimal.valueOf(100), 2,
                            RoundingMode.HALF_UP);
                    low = BigDecimal.valueOf(buffer.getInt() & 0xFFFFFFFFL).divide(BigDecimal.valueOf(100), 2,
                            RoundingMode.HALF_UP);
                    close = BigDecimal.valueOf(buffer.getInt() & 0xFFFFFFFFL).divide(BigDecimal.valueOf(100), 2,
                            RoundingMode.HALF_UP);
                }

                // Skip remaining fields for MODE_FULL (timestamps + market depth)
                if (mode == MODE_FULL && buffer.remaining() > 0) {
                    int remainingBytes = packetLength - 44; // 44 bytes read (token + LTP + QUOTE fields)
                    if (remainingBytes > 0 && buffer.remaining() >= remainingBytes) {
                        buffer.position(buffer.position() + remainingBytes);
                    }
                }

                // Find symbol and notify listeners
                String symbol = tokenToSymbol.get(instrumentToken);
                if (symbol != null && lastPrice != null) {
                    List<TickListener> listeners = tickListeners.get(symbol);
                    if (listeners != null && !listeners.isEmpty()) {
                        lastSuccessfulTick.set(System.currentTimeMillis());

                        // Log first tick received to confirm it's working
                        long currentSuccessTime = lastSuccessfulTick.get();
                        if (currentSuccessTime - lastSuccessfulTick.get() < 1000) {
                            log.info("[ZERODHA] ✅ First tick received: {} = {}", symbol, lastPrice);
                        }

                        Tick tick = new Tick(
                                symbol,
                                lastPrice,
                                open != null ? open : lastPrice,
                                high != null ? high : lastPrice,
                                low != null ? low : lastPrice,
                                close != null ? close : lastPrice,
                                volume,
                                lastPrice, lastPrice,
                                0, 0,
                                Instant.ofEpochMilli(timestamp),
                                "ZERODHA");
                        for (TickListener listener : listeners) {
                            try {
                                listener.onTick(tick);
                            } catch (Exception e) {
                                log.warn("[ZERODHA] Tick processing error for {}: {}", symbol, e.getMessage());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("[ZERODHA] Failed to process binary tick data", e);
        }
    }

    // ============================================
    // Order Execution Methods (Stub - Not Implemented)
    // This adapter is designed for DATA-only mode
    // ============================================

    @Override
    public CompletableFuture<OrderResult> placeOrder(BrokerOrderRequest request) {
        return CompletableFuture.completedFuture(
                OrderResult.ofFailure("Order placement not supported in DATA-only adapter", "NOT_SUPPORTED"));
    }

    @Override
    public CompletableFuture<OrderResult> modifyOrder(String orderId, OrderModifyRequest request) {
        return CompletableFuture.completedFuture(
                OrderResult.ofFailure("Order modification not supported in DATA-only adapter", "NOT_SUPPORTED"));
    }

    @Override
    public CompletableFuture<OrderResult> cancelOrder(String orderId) {
        return CompletableFuture.completedFuture(
                OrderResult.ofFailure("Order cancellation not supported in DATA-only adapter", "NOT_SUPPORTED"));
    }

    @Override
    public CompletableFuture<BrokerOrderStatus> getOrderStatus(String orderId) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("Order status not supported in DATA-only adapter"));
    }

    @Override
    public CompletableFuture<List<BrokerOrderStatus>> getOpenOrders() {
        return CompletableFuture.completedFuture(List.of());
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
        return CompletableFuture.completedFuture(
                new AccountFunds(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Override
    public CompletableFuture<BigDecimal> getLtp(String symbol) {
        return CompletableFuture.completedFuture(BigDecimal.ZERO);
    }

    @Override
    public CompletableFuture<List<HistoricalCandle>> getHistoricalCandles(
            String symbol,
            TimeframeType timeframe,
            long fromEpoch,
            long toEpoch) {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected) {
                throw new RuntimeException("Not connected");
            }

            int interval = timeframe.getInterval();
            log.info("[ZERODHA] Fetching historical candles: {} timeframe={} (interval={}) from={} to={}",
                    symbol, timeframe, interval, fromEpoch, toEpoch);

            // TODO: Implement Kite historical data API
            // For now, return empty list
            log.warn("[ZERODHA] Historical candles not yet implemented - returning empty list");
            return List.of();
        });
    }

    @Override
    public CompletableFuture<List<BrokerInstrument>> getInstruments() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("[ZERODHA] Fetching instruments list");
                loadInstrumentMaster();

                // Convert cached instruments to BrokerInstrument records
                List<BrokerInstrument> instruments = new ArrayList<>();
                symbolToToken.forEach((symbol, token) -> {
                    instruments.add(new BrokerInstrument(
                            "NSE", // exchange
                            "NSE:" + symbol + "-EQ", // tradingSymbol
                            symbol, // name
                            "EQ", // instrumentType
                            "EQUITY", // segment
                            token.toString(), // token
                            1, // lotSize
                            BigDecimal.valueOf(0.05), // tickSize
                            null, // expiryDate (null for equity)
                            BigDecimal.ZERO, // strikePrice (0 for equity)
                            null // optionType (null for equity)
                    ));
                });

                log.info("[ZERODHA] Returning {} instruments", instruments.size());
                return instruments;
            } catch (Exception e) {
                log.error("[ZERODHA] Error fetching instruments: {}", e.getMessage(), e);
                return List.of();
            }
        });
    }

    private String maskKey(String key) {
        if (key == null || key.length() < 8)
            return "***";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
