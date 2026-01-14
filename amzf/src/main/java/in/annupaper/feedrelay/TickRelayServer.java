package in.annupaper.feedrelay;

import io.undertow.Undertow;
import io.undertow.server.handlers.PathHandler;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.WebSocketProtocolHandshakeHandler;
import io.undertow.websockets.core.*;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class TickRelayServer {
    private static final Logger log = LoggerFactory.getLogger(TickRelayServer.class);

    private final Set<WebSocketChannel> clients = ConcurrentHashMap.newKeySet();
    private Undertow server;

    // Shared secret token for client authentication (optional - empty = allow all)
    private final String relayToken = System.getenv().getOrDefault("RELAY_TOKEN", "").trim();

    public void start(int port) {
        if (!relayToken.isEmpty()) {
            log.info("[RELAY] Token authentication ENABLED");
        } else {
            log.warn("[RELAY] Token authentication DISABLED - set RELAY_TOKEN env var for production");
        }

        WebSocketConnectionCallback cb = new WebSocketConnectionCallback() {
            @Override
            public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel channel) {
                // Token authentication check
                if (!isAuthorized(exchange)) {
                    log.warn("[RELAY] Unauthorized connection attempt from {}", exchange.getRequestHeader("X-Forwarded-For"));
                    try {
                        channel.sendClose();
                    } catch (java.io.IOException e) {
                        log.warn("[RELAY] Failed to close unauthorized connection: {}", e.getMessage());
                    }
                    return;
                }

                clients.add(channel);
                channel.getCloseSetter().set(c -> clients.remove(channel));

                channel.getReceiveSetter().set(new AbstractReceiveListener() {});
                channel.resumeReceives();

                log.info("[RELAY] Client connected (total: {})", clients.size());
            }
        };

        PathHandler paths = new PathHandler()
            .addExactPath("/ticks", new WebSocketProtocolHandshakeHandler(cb));

        server = Undertow.builder()
            .addHttpListener(port, "0.0.0.0")
            .setHandler(paths)
            .build();

        server.start();
    }

    public void stop() {
        if (server != null) server.stop();
    }

    public void broadcast(String json) {
        for (WebSocketChannel ch : clients) {
            if (ch == null || !ch.isOpen()) continue;
            WebSockets.sendText(json, ch, null);
        }
    }

    /**
     * Parse query string from URI.
     * Example: /ticks?token=abc&foo=bar → {token: abc, foo: bar}
     */
    private static Map<String, String> parseQuery(String uri) {
        int idx = uri.indexOf('?');
        Map<String, String> m = new HashMap<>();
        if (idx < 0 || idx == uri.length() - 1) return m;

        String q = uri.substring(idx + 1);
        for (String part : q.split("&")) {
            if (part.isBlank()) continue;
            String[] kv = part.split("=", 2);
            String k = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String v = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            m.put(k, v);
        }
        return m;
    }

    /**
     * Check if client is authorized to connect.
     * If RELAY_TOKEN is empty, allow all (dev mode).
     * Otherwise, require ?token=RELAY_TOKEN in URL.
     */
    private boolean isAuthorized(WebSocketHttpExchange exchange) {
        // If token not configured, allow all (dev mode)
        if (relayToken.isEmpty()) {
            return true;
        }

        // Extract token from query string
        Map<String, String> query = parseQuery(exchange.getRequestURI());
        String clientToken = query.getOrDefault("token", "");

        // Compare with configured token
        return relayToken.equals(clientToken);
    }
}
