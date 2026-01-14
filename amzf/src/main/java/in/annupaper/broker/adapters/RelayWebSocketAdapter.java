package in.annupaper.broker.adapters;

import in.annupaper.broker.BrokerAdapter;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Relay WebSocket Adapter - Connects to Feed Collector VM.
 *
 * This adapter receives ticks from a remote relay server (Feed Collector VM)
 * instead of connecting directly to FYERS. This allows the main app to run
 * anywhere while the VM maintains a stable connection from Mumbai region.
 *
 * Architecture:
 *   VM (Feed Collector) → FYERS WebSocket → broadcasts ticks
 *   Main App (this adapter) → connects to VM → receives ticks
 *
 * Usage:
 *   Set environment variables:
 *   - DATA_FEED_MODE=RELAY
 *   - RELAY_URL=ws://VM_IP:7071/ticks?token=SECRET
 */
public final class RelayWebSocketAdapter implements BrokerAdapter {
    private static final Logger log = LoggerFactory.getLogger(RelayWebSocketAdapter.class);

    private final String userBrokerId;
    private final String relayUrl;
    private final HttpClient httpClient;

    private final AtomicReference<WebSocket> wsRef = new AtomicReference<>(null);
    private final Map<String, List<TickListener>> tickListeners = new ConcurrentHashMap<>();

    private volatile boolean connected = false;
    private volatile long lastTickAt = 0L;
    private static final long MAX_TICK_SILENCE_MS = 5 * 60 * 1000; // 5 minutes

    public RelayWebSocketAdapter(String userBrokerId, String relayUrl) {
        this.userBrokerId = userBrokerId;
        this.relayUrl = relayUrl;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        log.info("[RELAY ADAPTER] Created for userBrokerId={} (relay: {})", userBrokerId, maskUrl(relayUrl));
    }

    @Override
    public String getBrokerCode() {
        return "RELAY";
    }

    @Override
    public CompletableFuture<ConnectionResult> connect(BrokerCredentials credentials) {
        log.info("[RELAY ADAPTER] Connecting to {}", maskUrl(relayUrl));

        CompletableFuture<ConnectionResult> result = new CompletableFuture<>();

        httpClient.newWebSocketBuilder()
            .buildAsync(URI.create(relayUrl), new WebSocket.Listener() {
                private final StringBuilder buf = new StringBuilder();

                @Override
                public void onOpen(WebSocket webSocket) {
                    wsRef.set(webSocket);
                    connected = true;
                    lastTickAt = System.currentTimeMillis();
                    log.info("[RELAY ADAPTER] ✅ Connected to feed collector");
                    webSocket.request(1);
                    result.complete(ConnectionResult.success("relay-connected"));
                }

                @Override
                public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                    buf.append(data);
                    if (last) {
                        String msg = buf.toString();
                        buf.setLength(0);
                        handleTickMessage(msg);
                    }
                    webSocket.request(1);
                    return CompletableFuture.completedFuture(null);
                }

                @Override
                public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                    connected = false;
                    wsRef.set(null);
                    log.warn("[RELAY ADAPTER] Disconnected: {} {}", statusCode, reason);
                    if (!result.isDone()) {
                        result.complete(ConnectionResult.failure("Disconnected during handshake", "DISCONNECTED"));
                    }
                    return CompletableFuture.completedFuture(null);
                }

                @Override
                public void onError(WebSocket webSocket, Throwable error) {
                    connected = false;
                    wsRef.set(null);
                    log.error("[RELAY ADAPTER] WebSocket error", error);
                    if (!result.isDone()) {
                        result.complete(ConnectionResult.failure(error.getMessage(), "ERROR"));
                    }
                }
            });

        return result;
    }

    @Override
    public void disconnect() {
        WebSocket ws = wsRef.getAndSet(null);
        connected = false;
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
            } catch (Exception ignored) {}
        }
        log.info("[RELAY ADAPTER] Disconnected");
    }

    @Override
    public boolean isConnected() {
        // Check if feed is stale (no ticks for 5+ minutes)
        if (lastTickAt > 0) {
            long silenceDuration = System.currentTimeMillis() - lastTickAt;
            if (silenceDuration > MAX_TICK_SILENCE_MS) {
                log.warn("[RELAY ADAPTER] ⚠️ STALE FEED: No ticks for {}ms", silenceDuration);
                return false;
            }
        }
        return connected;
    }

    @Override
    public CompletableFuture<OrderResult> placeOrder(OrderRequest request) {
        log.error("[RELAY ADAPTER] ❌ Order placement not supported (relay is read-only)");
        return CompletableFuture.completedFuture(
            OrderResult.failure("Relay adapter is read-only", "NOT_SUPPORTED")
        );
    }

    @Override
    public CompletableFuture<OrderResult> modifyOrder(String orderId, OrderModifyRequest request) {
        return CompletableFuture.completedFuture(
            OrderResult.failure("Relay adapter is read-only", "NOT_SUPPORTED")
        );
    }

    @Override
    public CompletableFuture<OrderResult> cancelOrder(String orderId) {
        return CompletableFuture.completedFuture(
            OrderResult.failure("Relay adapter is read-only", "NOT_SUPPORTED")
        );
    }

    @Override
    public CompletableFuture<OrderStatus> getOrderStatus(String orderId) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<List<OrderStatus>> getOpenOrders() {
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
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<BigDecimal> getLtp(String symbol) {
        // Could cache last price from ticks, but keeping simple for now
        return CompletableFuture.completedFuture(BigDecimal.ZERO);
    }

    @Override
    public void subscribeTicks(List<String> symbols, TickListener listener) {
        for (String s : symbols) {
            tickListeners.computeIfAbsent(s, k -> new CopyOnWriteArrayList<>()).add(listener);
        }
        log.info("[RELAY ADAPTER] Registered TickListener for {} symbols", symbols.size());
    }

    @Override
    public void unsubscribeTicks(List<String> symbols) {
        for (String s : symbols) {
            tickListeners.remove(s);
        }
        log.info("[RELAY ADAPTER] Unsubscribed from {} symbols", symbols.size());
    }

    @Override
    public CompletableFuture<List<HistoricalCandle>> getHistoricalCandles(
        String symbol, int interval, long fromEpoch, long toEpoch
    ) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<List<Instrument>> getInstruments() {
        return CompletableFuture.completedFuture(List.of());
    }

    /**
     * Handle incoming tick JSON message from relay.
     */
    private void handleTickMessage(String json) {
        try {
            JSONObject o = new JSONObject(json);

            String symbol = o.optString("symbol", null);
            if (symbol == null) {
                log.trace("[RELAY ADAPTER] Tick missing symbol, skipping");
                return;
            }

            Tick tick = new Tick(
                symbol,
                bd(o, "lastPrice"),
                bd(o, "open"),
                bd(o, "high"),
                bd(o, "low"),
                bd(o, "close"),
                o.optLong("volume", 0L),
                bd(o, "bid"),
                bd(o, "ask"),
                o.optInt("bidQty", 0),
                o.optInt("askQty", 0),
                o.optLong("timestamp", System.currentTimeMillis())
            );

            lastTickAt = System.currentTimeMillis();

            // Dispatch to registered listeners
            List<TickListener> listeners = tickListeners.get(symbol);
            if (listeners != null) {
                for (TickListener l : listeners) {
                    try {
                        l.onTick(tick);
                    } catch (Exception e) {
                        log.warn("[RELAY ADAPTER] Tick listener error for {}: {}", symbol, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[RELAY ADAPTER] Failed to parse tick JSON: {}", e.getMessage());
        }
    }

    /**
     * Parse BigDecimal from JSON (handles null and empty strings).
     */
    private static BigDecimal bd(JSONObject o, String key) {
        if (!o.has(key)) return null;
        String v = o.optString(key, null);
        if (v == null || v.isBlank()) return null;
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Mask token in URL for logging.
     */
    private static String maskUrl(String url) {
        if (url == null) return "null";
        int tokenIdx = url.indexOf("token=");
        if (tokenIdx < 0) return url;
        int endIdx = url.indexOf("&", tokenIdx);
        if (endIdx < 0) endIdx = url.length();
        return url.substring(0, tokenIdx + 6) + "***" + url.substring(endIdx);
    }
}
