package in.annupaper.infrastructure.broker.adapters;

import in.annupaper.domain.broker.BrokerAdapter;
import in.annupaper.domain.broker.BrokerAdapter.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ✅ FYERS V3 SDK Adapter - Official SDK Wrapper (Primary Adapter)
 *
 * This adapter wraps the official FYERS Java SDK (fyersjavasdk.jar).
 * It is the PRIMARY adapter - BrokerAdapterFactory tries this FIRST.
 *
 * FALLBACK STRATEGY:
 * - If SDK jar is missing → gracefully return null from factory
 * - If SDK init fails → gracefully return null from factory
 * - Factory then falls back to raw FyersAdapter (WebSocket implementation)
 *
 * ADVANTAGES OVER RAW ADAPTER:
 * - Maintained by FYERS (gets updates for API changes)
 * - Less code to maintain on our side
 * - Handles protocol changes automatically
 *
 * TRADE-OFFS:
 * - External dependency (jar must be deployed to local repo)
 * - Less control over retry/reconnect logic
 * - Need to adapt SDK callbacks to our BrokerAdapter interface
 *
 * SETUP REQUIRED:
 * 1. Download fyersjavasdk.jar from FYERS
 * 2. Run: mvn deploy:deploy-file (see FYERS_SDK_SETUP.md)
 * 3. Uncomment SDK dependency in pom.xml
 * 4. Factory will automatically use this adapter
 *
 * See: FYERS_SDK_SETUP.md for installation instructions
 */
public final class FyersV3SdkAdapter implements BrokerAdapter {
    private static final Logger log = LoggerFactory.getLogger(FyersV3SdkAdapter.class);

    private final String userBrokerId;
    private final String appId;
    private volatile String accessToken;
    private volatile String sessionId;

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicLong lastSuccessfulTick = new AtomicLong(0);
    private volatile boolean forceReadOnly = false;
    private static final long MAX_TICK_SILENCE_MS = 5 * 60 * 1000; // 5 minutes

    // Tick listener
    private volatile TickListener tickListener = null;

    // SDK instance (will be initialized when SDK is available)
    // Using Object type to avoid compile-time dependency
    private volatile Object fyersWebSocket = null;
    private final ScheduledExecutorService reconnectScheduler;

    /**
     * Constructor.
     *
     * @param userBrokerId User-broker link ID
     * @param appId        FYERS app ID
     * @param accessToken  OAuth access token
     * @param sessionId    Session ID (for tracking token refreshes)
     */
    public FyersV3SdkAdapter(
        String userBrokerId,
        String appId,
        String accessToken,
        String sessionId
    ) {
        this.userBrokerId = userBrokerId;
        this.appId = appId;
        this.accessToken = accessToken;
        this.sessionId = sessionId;
        this.reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "FyersSDK-Reconnect-" + userBrokerId);
            t.setDaemon(true);
            return t;
        });

        log.info("[FYERS SDK] Adapter created for userBrokerId={} (session={})", userBrokerId, sessionId);
    }

    /**
     * Initialize SDK and connect to WebSocket feed.
     *
     * This method attempts to:
     * 1. Load FYERS SDK classes (via reflection to avoid hard dependency)
     * 2. Create WebSocket instance
     * 3. Set up callbacks for ticks/errors
     * 4. Start connection
     *
     * @return true if SDK is available and initialization succeeded
     */
    public boolean initialize() {
        try {
            // Try to load SDK class (this will fail if jar is not in classpath)
            Class<?> fyersWebSocketClass = Class.forName("com.tts.fyers.websocket.FyersWebSocket");
            log.info("[FYERS SDK] ✅ SDK classes found - attempting initialization");

            // TODO: Initialize SDK WebSocket instance via reflection
            // Example (pseudo-code - actual SDK API may differ):
            //
            // FyersWebSocket ws = new FyersWebSocket();
            // ws.setAccessToken(appId + ":" + accessToken);
            // ws.setOnTickCallback(this::onTick);
            // ws.setOnErrorCallback(this::onError);
            // ws.connect();

            log.warn("[FYERS SDK] ⚠️ SDK initialization not yet implemented - skeleton only");
            log.warn("[FYERS SDK]    Factory will fallback to raw FyersAdapter");
            return false;

        } catch (ClassNotFoundException e) {
            log.debug("[FYERS SDK] SDK jar not found in classpath - factory will use fallback adapter");
            return false;
        } catch (Exception e) {
            log.error("[FYERS SDK] Failed to initialize SDK: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Reload access token (when OAuth token is refreshed).
     *
     * @param newAccessToken New OAuth access token
     * @param newSessionId   New session ID
     */
    public void reloadToken(String newAccessToken, String newSessionId) {
        log.info("[FYERS SDK] ⚡ Token reload requested: session {} → {}",
            this.sessionId, newSessionId);

        this.accessToken = newAccessToken;
        this.sessionId = newSessionId;

        // TODO: Trigger SDK reconnection with new token
        log.warn("[FYERS SDK] ⚠️ Token reload not yet implemented - skeleton only");
    }

    // ========================================
    // BrokerAdapter Interface Implementation
    // ========================================

    @Override
    public String getBrokerCode() {
        return "FYERS";
    }

    @Override
    public CompletableFuture<ConnectionResult> connect(BrokerCredentials credentials) {
        log.info("[FYERS SDK] Connect requested (SDK wrapper)");
        // SDK adapter auto-connects via initialize() method
        if (initialize()) {
            return CompletableFuture.completedFuture(ConnectionResult.success(sessionId));
        } else {
            return CompletableFuture.completedFuture(
                ConnectionResult.failure("SDK initialization failed", "SDK_INIT_FAILED")
            );
        }
    }

    @Override
    public boolean isConnected() {
        // Check if feed is stale (no ticks for 5+ minutes)
        long lastTick = lastSuccessfulTick.get();
        if (lastTick > 0) {
            long silenceDuration = System.currentTimeMillis() - lastTick;
            if (silenceDuration > MAX_TICK_SILENCE_MS && !forceReadOnly) {
                log.error("[FYERS SDK] ⚠️ STALE FEED: No ticks for {}ms. Forcing READ-ONLY mode.", silenceDuration);
                forceReadOnly = true;
            } else if (silenceDuration <= MAX_TICK_SILENCE_MS && forceReadOnly) {
                log.info("[FYERS SDK] ✅ Feed recovered. Clearing READ-ONLY mode.");
                forceReadOnly = false;
            }
        }

        return connected.get() && !forceReadOnly;
    }

    /**
     * Check if system can place orders (feed must be connected AND fresh).
     */
    public boolean canPlaceOrders() {
        if (forceReadOnly) {
            log.warn("[FYERS SDK] ⛔ Order rejected - system in READ-ONLY mode (stale feed)");
            return false;
        }
        return connected.get();
    }

    /**
     * Check raw WebSocket connection status (ignores stale feed flag).
     */
    public boolean isWebSocketConnected() {
        return connected.get();
    }

    /**
     * Get current connection state for health/status reporting.
     */
    public String getConnectionState() {
        return connected.get() ? "CONNECTED" : "DISCONNECTED";
    }

    /**
     * Get current retry count (SDK adapter doesn't expose this, return 0).
     */
    public int getRetryCount() {
        return 0;
    }

    /**
     * Get last HTTP status (SDK adapter doesn't expose this, return null).
     */
    public Integer getLastHttpStatus() {
        return null;
    }

    /**
     * Get last error message (SDK adapter doesn't expose this, return null).
     */
    public String getLastErrorMessage() {
        return null;
    }

    @Override
    public CompletableFuture<OrderResult> placeOrder(OrderRequest request) {
        log.info("[FYERS SDK] 📝 Placing order: {} {} {} @ {}",
            request.transactionType(), request.quantity(), request.symbol(), request.price());

        // TODO: Implement order placement via SDK
        log.error("[FYERS SDK] ❌ Order placement not yet implemented - skeleton only");
        return CompletableFuture.completedFuture(
            OrderResult.failure("SDK adapter skeleton only", "NOT_IMPLEMENTED")
        );
    }

    @Override
    public CompletableFuture<OrderResult> modifyOrder(String orderId, OrderModifyRequest request) {
        log.warn("[FYERS SDK] ⚠️ Order modification not yet implemented - skeleton only");
        return CompletableFuture.completedFuture(
            OrderResult.failure("SDK adapter skeleton only", "NOT_IMPLEMENTED")
        );
    }

    @Override
    public CompletableFuture<OrderResult> cancelOrder(String orderId) {
        log.warn("[FYERS SDK] ⚠️ Order cancellation not yet implemented - skeleton only");
        return CompletableFuture.completedFuture(
            OrderResult.failure("SDK adapter skeleton only", "NOT_IMPLEMENTED")
        );
    }

    @Override
    public CompletableFuture<OrderStatus> getOrderStatus(String orderId) {
        log.warn("[FYERS SDK] ⚠️ Order status not yet implemented - skeleton only");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<List<OrderStatus>> getOpenOrders() {
        log.warn("[FYERS SDK] ⚠️ Open orders not yet implemented - skeleton only");
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public CompletableFuture<List<Position>> getPositions() {
        log.debug("[FYERS SDK] Fetching positions");
        log.warn("[FYERS SDK] ⚠️ Position fetching not yet implemented - skeleton only");
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public CompletableFuture<List<Holding>> getHoldings() {
        log.warn("[FYERS SDK] ⚠️ Holdings not yet implemented - skeleton only");
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public CompletableFuture<AccountFunds> getFunds() {
        log.warn("[FYERS SDK] ⚠️ Funds not yet implemented - skeleton only");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<BigDecimal> getLtp(String symbol) {
        log.warn("[FYERS SDK] ⚠️ LTP not yet implemented - skeleton only");
        return CompletableFuture.completedFuture(BigDecimal.ZERO);
    }

    @Override
    public void subscribeTicks(List<String> symbols, TickListener listener) {
        this.tickListener = listener;
        log.info("[FYERS SDK] Subscribed to {} symbols", symbols.size());

        // TODO: Send subscription message to SDK
        // Example: fyersWebSocket.subscribe(symbols, mode="lite")
        log.warn("[FYERS SDK] ⚠️ Subscription not yet implemented - skeleton only");
    }

    @Override
    public void unsubscribeTicks(List<String> symbols) {
        log.info("[FYERS SDK] Unsubscribed from {} symbols", symbols.size());

        // TODO: Send unsubscribe message to SDK
        log.warn("[FYERS SDK] ⚠️ Unsubscription not yet implemented - skeleton only");
    }

    @Override
    public CompletableFuture<List<HistoricalCandle>> getHistoricalCandles(
        String symbol, int interval, long fromEpoch, long toEpoch
    ) {
        log.warn("[FYERS SDK] ⚠️ Historical candles not yet implemented - skeleton only");
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public CompletableFuture<List<Instrument>> getInstruments() {
        log.warn("[FYERS SDK] ⚠️ Instruments not yet implemented - skeleton only");
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public void disconnect() {
        log.info("[FYERS SDK] Disconnecting WebSocket");

        connected.set(false);

        // TODO: Disconnect SDK WebSocket
        log.warn("[FYERS SDK] ⚠️ Disconnect not yet implemented - skeleton only");

        reconnectScheduler.shutdown();
        log.info("[FYERS SDK] ✅ Disconnected");
    }

    // ========================================
    // SDK Callbacks (to be wired up when SDK is integrated)
    // ========================================

    /**
     * Callback for tick data from SDK.
     * This will be called by SDK when market data arrives.
     */
    private void onTick(Object tickData) {
        try {
            // TODO: Parse SDK tick format and convert to Tick
            // Example pseudo-code:
            //
            // String symbol = (String) tickData.get("symbol");
            // BigDecimal ltp = new BigDecimal(tickData.get("ltp").toString());
            // long timestamp = (long) tickData.get("timestamp");
            //
            // Tick tick = new Tick(
            //     symbol,
            //     ltp,  // lastPrice
            //     BigDecimal.ZERO,  // open
            //     BigDecimal.ZERO,  // high
            //     BigDecimal.ZERO,  // low
            //     BigDecimal.ZERO,  // close
            //     0L,  // volume
            //     BigDecimal.ZERO,  // bid
            //     BigDecimal.ZERO,  // ask
            //     0,  // bidQty
            //     0,  // askQty
            //     timestamp
            // );
            //
            // lastSuccessfulTick.set(System.currentTimeMillis());
            // if (tickListener != null) {
            //     tickListener.onTick(tick);
            // }

            log.debug("[FYERS SDK] Tick received (parse not yet implemented)");

        } catch (Exception e) {
            log.error("[FYERS SDK] Error processing tick: {}", e.getMessage());
        }
    }

    /**
     * Callback for SDK errors.
     */
    private void onError(String errorMessage) {
        log.error("[FYERS SDK] ❌ SDK error: {}", errorMessage);
        connected.set(false);

        // TODO: Trigger reconnection if appropriate
    }

    /**
     * Callback for SDK connection established.
     */
    private void onConnect() {
        log.info("[FYERS SDK] ✅ Connected");
        connected.set(true);
        forceReadOnly = false;

        // TODO: Re-subscribe to all symbols in tickListeners
    }

    /**
     * Callback for SDK connection closed.
     */
    private void onDisconnect(String reason) {
        log.warn("[FYERS SDK] ⚠️ Disconnected: {}", reason);
        connected.set(false);

        // TODO: Trigger reconnection
    }
}
