package in.annupaper.transport.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import in.annupaper.domain.model.*;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.WebSocketProtocolHandshakeHandler;
import io.undertow.websockets.core.*;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Undertow-native WebSocket hub with:
 * - Token-based authentication (?token=xxx)
 * - User-scoped event delivery (GLOBAL to all, USER/USER_BROKER to specific
 * users)
 * - Topic subscriptions
 * - Batching (flush interval configurable)
 */
public final class WsHub {
    private static final Logger log = LoggerFactory.getLogger(WsHub.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Channel -> Session
    private final ConcurrentMap<WebSocketChannel, WsSession> sessions = new ConcurrentHashMap<>();

    // UserId -> Channels (for targeted delivery)
    private final ConcurrentMap<String, Set<WebSocketChannel>> userChannels = new ConcurrentHashMap<>();

    // Batching queue
    private final BlockingQueue<TradeEvent> batchQueue = new LinkedBlockingQueue<>(100_000);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ws-batch-flusher");
        t.setDaemon(true);
        return t;
    });

    private final AtomicLong wsSeq = new AtomicLong(0);
    private volatile int flushMs = 100;

    // Token validator: token -> userId (null if invalid)
    private final Function<String, String> tokenValidator;

    public WsHub(Function<String, String> tokenValidator) {
        this.tokenValidator = tokenValidator;
    }

    public void setFlushMs(int flushMs) {
        this.flushMs = Math.max(10, flushMs);
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::flushBatch, flushMs, flushMs, TimeUnit.MILLISECONDS);
        log.info("WsHub started with {}ms batch flush interval", flushMs);
    }

    public WebSocketProtocolHandshakeHandler websocketHandler() {
        return new WebSocketProtocolHandshakeHandler(new WebSocketConnectionCallback() {
            @Override
            public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel channel) {
                // Extract token from query string: ?token=xxx
                String query = exchange.getQueryString();
                String token = extractToken(query);
                String userId = null;

                if (token != null) {
                    userId = tokenValidator.apply(token);
                }

                if (userId == null) {
                    log.warn("WS connection rejected: invalid token from {}", channel.getSourceAddress());
                    sendError(channel, "Invalid or missing token");
                    try {
                        channel.close();
                    } catch (Exception ignored) {
                    }
                    return;
                }

                // Create authenticated session
                String sessionId = UUID.randomUUID().toString();
                WsSession session = new WsSession(sessionId, userId);
                sessions.put(channel, session);

                // Track user channels
                userChannels.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(channel);

                log.info("WS connected: {} (user={}, session={})", channel.getSourceAddress(), userId, sessionId);

                channel.getReceiveSetter().set(new AbstractReceiveListener() {
                    @Override
                    protected void onFullTextMessage(WebSocketChannel ch, BufferedTextMessage message) {
                        handleClientMessage(ch, message.getData());
                    }

                    @Override
                    protected void onCloseMessage(CloseMessage cm, WebSocketChannel ch) {
                        cleanup(ch);
                        super.onCloseMessage(cm, ch);
                    }

                    @Override
                    protected void onError(WebSocketChannel ch, Throwable error) {
                        log.warn("WS error: {}", error.toString());
                        cleanup(ch);
                    }
                });

                channel.resumeReceives();
                sendAck(channel, "connect", session);
            }
        });
    }

    private String extractToken(String query) {
        if (query == null)
            return null;
        for (String param : query.split("&")) {
            if (param.startsWith("token=")) {
                return param.substring(6);
            }
        }
        return null;
    }

    private void handleClientMessage(WebSocketChannel channel, String raw) {
        WsSession session = sessions.get(channel);
        if (session == null) {
            sendError(channel, "Not authenticated");
            return;
        }

        session.touch();

        try {
            ClientMessage msg = MAPPER.readValue(raw, ClientMessage.class);
            if (msg.action == null) {
                sendError(channel, "Missing 'action'");
                return;
            }

            switch (msg.action) {
                case "subscribe" -> {
                    if (msg.topics != null) {
                        session.subscribeTopics(new HashSet<>(msg.topics));
                    }
                    if (msg.brokers != null) {
                        session.subscribeBrokers(new HashSet<>(msg.brokers));
                    }
                    sendAck(channel, "subscribe", session);
                }
                case "unsubscribe" -> {
                    if (msg.topics != null) {
                        session.unsubscribeTopics(new HashSet<>(msg.topics));
                    }
                    if (msg.brokers != null) {
                        session.unsubscribeBrokers(new HashSet<>(msg.brokers));
                    }
                    sendAck(channel, "unsubscribe", session);
                }
                case "ping" -> {
                    ObjectNode payload = MAPPER.createObjectNode();
                    payload.put("nonce", msg.nonce == null ? "" : msg.nonce);
                    payload.put("pong", true);
                    sendDirect(channel,
                            new ServerMessage(EventType.PONG.name(), payload, Instant.now().toString(), nextWsSeq()));
                }
                default -> sendError(channel, "Unknown action: " + msg.action);
            }
        } catch (Exception e) {
            sendError(channel, "Invalid JSON: " + e.getMessage());
        }
    }

    private void sendAck(WebSocketChannel channel, String action, WsSession session) {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("action", action);
        payload.put("userId", session.getUserId());
        payload.put("sessionId", session.getSessionId());
        payload.set("topics", MAPPER.valueToTree(session.getTopics()));
        payload.set("brokers", MAPPER.valueToTree(session.getUserBrokerIds()));
        sendDirect(channel, new ServerMessage(EventType.ACK.name(), payload, Instant.now().toString(), nextWsSeq()));
    }

    private void sendError(WebSocketChannel channel, String error) {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("error", error);
        sendDirect(channel, new ServerMessage(EventType.ERROR.name(), payload, Instant.now().toString(), nextWsSeq()));
    }

    private long nextWsSeq() {
        return wsSeq.incrementAndGet();
    }

    private void sendDirect(WebSocketChannel channel, ServerMessage msg) {
        try {
            String json = MAPPER.writeValueAsString(msg);
            WebSockets.sendText(json, channel, null);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize WS message: {}", e.toString());
        }
    }

    private void cleanup(WebSocketChannel channel) {
        WsSession session = sessions.remove(channel);
        if (session != null) {
            String userId = session.getUserId();
            Set<WebSocketChannel> channels = userChannels.get(userId);
            if (channels != null) {
                channels.remove(channel);
                if (channels.isEmpty()) {
                    userChannels.remove(userId);
                }
            }
            log.info("WS disconnected: {} (user={}, session={})",
                    channel.getSourceAddress(), userId, session.getSessionId());
        }
        try {
            channel.close();
        } catch (Exception ignored) {
        }
    }

    /**
     * Publish event by enqueuing it for batching.
     * Events are filtered by scope during flush.
     */
    public void publish(TradeEvent e) {
        if (!batchQueue.offer(e)) {
            batchQueue.poll();
            batchQueue.offer(e);
        }
    }

    private void flushBatch() {
        try {
            List<TradeEvent> drained = new ArrayList<>(1024);
            batchQueue.drainTo(drained, 2000);
            if (drained.isEmpty())
                return;

            // Group events by target (optimization for scoped delivery)
            // For simplicity, we'll iterate and filter per channel

            for (Map.Entry<WebSocketChannel, WsSession> entry : sessions.entrySet()) {
                WebSocketChannel channel = entry.getKey();
                WsSession session = entry.getValue();

                // Filter events for this session
                List<ObjectNode> relevantEvents = new ArrayList<>();
                for (TradeEvent e : drained) {
                    if (session.shouldReceive(e)) {
                        relevantEvents.add(eventToJson(e));
                    }
                }

                if (relevantEvents.isEmpty())
                    continue;

                // Build BATCH message
                ObjectNode payload = MAPPER.createObjectNode();
                ArrayNode events = MAPPER.createArrayNode();
                for (ObjectNode ev : relevantEvents) {
                    events.add(ev);
                }
                payload.set("events", events);

                ServerMessage batchMsg = new ServerMessage(
                        EventType.BATCH.name(),
                        payload,
                        Instant.now().toString(),
                        nextWsSeq());

                sendDirect(channel, batchMsg);
            }
        } catch (Exception e) {
            log.warn("WS flushBatch error: {}", e.toString());
        }
    }

    private ObjectNode eventToJson(TradeEvent e) {
        ObjectNode obj = MAPPER.createObjectNode();
        obj.put("type", e.type().name());
        obj.put("scope", e.scope().name());
        obj.set("payload", e.payload());
        obj.put("ts", e.ts().toString());
        obj.put("seq", e.seq());
        if (e.signalId() != null)
            obj.put("signalId", e.signalId());
        if (e.intentId() != null)
            obj.put("intentId", e.intentId());
        if (e.tradeId() != null)
            obj.put("tradeId", e.tradeId());
        if (e.orderId() != null)
            obj.put("orderId", e.orderId());
        return obj;
    }

    /**
     * Get connected user count.
     */
    public int getUserCount() {
        return userChannels.size();
    }

    /**
     * Get total connection count.
     */
    public int getConnectionCount() {
        return sessions.size();
    }

    // Message models
    public static final class ClientMessage {
        public String action;
        public List<String> topics; // Event types to subscribe
        public List<String> brokers; // UserBroker IDs to filter
        public String nonce;
    }

    public static final class ServerMessage {
        public String type;
        public JsonNode payload;
        public String ts;
        public long seq;

        public ServerMessage() {
        }

        public ServerMessage(String type, JsonNode payload, String ts, long seq) {
            this.type = type;
            this.payload = payload;
            this.ts = ts;
            this.seq = seq;
        }
    }
}
