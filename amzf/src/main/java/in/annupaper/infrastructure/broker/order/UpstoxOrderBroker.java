package in.annupaper.infrastructure.broker.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import in.annupaper.domain.broker.UserBrokerSession;
import in.annupaper.domain.order.*;
import in.annupaper.domain.trade.Direction;
import in.annupaper.infrastructure.broker.common.InstrumentMapper;
import in.annupaper.infrastructure.broker.metrics.BrokerMetrics;
import in.annupaper.repository.UserBrokerSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Upstox Order Broker - Production Implementation
 *
 * Implements OrderBroker interface using Upstox API v2 for order execution.
 *
 * Features:
 * - Place, modify, cancel orders via Upstox REST API
 * - Order status polling and order book queries
 * - Token refresh support from session repository
 * - Product types: D (Delivery/CNC), I (Intraday/MIS), M (Margin/NRML)
 * - Instrument mapping via InstrumentMapper
 *
 * API Docs: https://upstox.com/developer/api-documentation
 * Order API: https://api.upstox.com/v2/order/place
 *
 * Rate Limits:
 * - 10 orders per second
 * - 1000 orders per day
 */
public class UpstoxOrderBroker implements OrderBroker {
    private static final Logger log = LoggerFactory.getLogger(UpstoxOrderBroker.class);

    private static final String BASE_URL = "https://api.upstox.com";
    private static final int RATE_LIMIT_PER_SECOND = 10;
    private static final int RATE_LIMIT_PER_DAY = 1000;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(java.time.Duration.ofSeconds(10))
        .build();

    private final UserBrokerSessionRepository sessionRepo;
    private final InstrumentMapper instrumentMapper;
    private final BrokerMetrics metrics;
    private final String userBrokerId;
    private final String apiKey;

    private volatile boolean authenticated = false;
    private volatile boolean connected = false;
    private String accessToken;

    // Order tracking
    private final Map<String, OrderResponse> orders = new ConcurrentHashMap<>();

    // Event listeners
    private Consumer<OrderResponse> orderUpdateListener;
    private Consumer<OrderRejection> rejectionListener;
    private Consumer<Throwable> errorListener;

    /**
     * Constructor with session and instrument mapping.
     *
     * @param sessionRepo Session repository for token management
     * @param instrumentMapper Instrument mapper for symbol conversion
     * @param metrics Metrics collector (nullable)
     * @param userBrokerId UserBroker ID
     * @param apiKey Upstox API key
     */
    public UpstoxOrderBroker(
        UserBrokerSessionRepository sessionRepo,
        InstrumentMapper instrumentMapper,
        BrokerMetrics metrics,
        String userBrokerId,
        String apiKey
    ) {
        this.sessionRepo = sessionRepo;
        this.instrumentMapper = instrumentMapper;
        this.metrics = metrics;
        this.userBrokerId = userBrokerId;
        this.apiKey = apiKey;
    }

    @Override
    public CompletableFuture<Void> authenticate() {
        return CompletableFuture.runAsync(() -> {
            Instant startTime = Instant.now();

            try {
                log.info("[UPSTOX_ORDER] Authenticating for userBrokerId={}", userBrokerId);

                if (apiKey == null || apiKey.isEmpty()) {
                    throw new OrderBrokerAuthenticationException(getBrokerCode(), userBrokerId,
                        "Invalid API key");
                }

                // Load access token from session repository
                if (sessionRepo != null && userBrokerId != null) {
                    Optional<UserBrokerSession> sessionOpt = sessionRepo.findActiveSession(userBrokerId);

                    if (sessionOpt.isEmpty()) {
                        log.error("[UPSTOX_ORDER] No active session found for userBrokerId={}", userBrokerId);
                        throw new OrderBrokerAuthenticationException(getBrokerCode(), userBrokerId,
                            "No active session. Please connect via OAuth.");
                    }

                    UserBrokerSession session = sessionOpt.get();
                    this.accessToken = session.accessToken();

                    log.info("[UPSTOX_ORDER] Access token loaded successfully");
                } else {
                    log.error("[UPSTOX_ORDER] Session repository or userBrokerId is null");
                    throw new OrderBrokerAuthenticationException(getBrokerCode(), userBrokerId,
                        "Session repository not configured");
                }

                // Verify token by fetching profile
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/v2/user/profile"))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Accept", "application/json")
                        .GET()
                        .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 200) {
                        authenticated = true;
                        connected = true;

                        // Record successful authentication
                        Duration latency = Duration.between(startTime, Instant.now());
                        if (metrics != null) {
                            metrics.recordAuthentication(getBrokerCode(), true, latency);
                        }

                        log.info("[UPSTOX_ORDER] Authentication successful");
                    } else {
                        log.error("[UPSTOX_ORDER] Authentication failed: HTTP {}", response.statusCode());
                        throw new OrderBrokerAuthenticationException(getBrokerCode(), userBrokerId,
                            "Token verification failed: HTTP " + response.statusCode());
                    }
                } catch (Exception e) {
                    log.error("[UPSTOX_ORDER] Authentication error", e);
                    throw new OrderBrokerAuthenticationException(getBrokerCode(), userBrokerId,
                        "Connection error", e);
                }
            } catch (OrderBrokerAuthenticationException e) {
                // Record failed authentication
                Duration latency = Duration.between(startTime, Instant.now());
                if (metrics != null) {
                    metrics.recordAuthentication(getBrokerCode(), false, latency);
                }
                throw e;
            }
        });
    }

    @Override
    public CompletableFuture<Void> connect() {
        // REST-based broker - connection handled per request
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> disconnect() {
        return CompletableFuture.runAsync(() -> {
            log.info("[UPSTOX_ORDER] Disconnecting...");
            connected = false;
            authenticated = false;
            orders.clear();
        });
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public CompletableFuture<OrderResponse> placeOrder(OrderRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            Instant startTime = Instant.now();

            if (!authenticated) {
                Duration latency = Duration.between(startTime, Instant.now());
                if (metrics != null) {
                    metrics.recordOrderFailure(getBrokerCode(), "NOT_AUTHENTICATED", latency);
                }
                throw new OrderPlacementException(getBrokerCode(), userBrokerId, request,
                    "Not authenticated");
            }

            try {
                log.info("[UPSTOX_ORDER] Placing order: {} {} {} qty={} @ {}",
                         request.direction(), request.symbol(), request.orderType(),
                         request.quantity(), request.limitPrice());

                // Get instrument key from mapper
                InstrumentMapper.BrokerInstrument instrument = instrumentMapper
                    .getBrokerInstrument("UPSTOX", request.symbol())
                    .orElseThrow(() -> new OrderPlacementException(getBrokerCode(), userBrokerId, request,
                        "Instrument not found: " + request.symbol()));

                // Build Upstox order request
                // API: POST /v2/order/place
                ObjectNode orderPayload = objectMapper.createObjectNode();
                orderPayload.put("quantity", request.quantity());
                orderPayload.put("product", convertProductType(request.productType()));
                orderPayload.put("validity", convertTimeInForce(request.timeInForce()));
                orderPayload.put("price", request.limitPrice() != null ? request.limitPrice().doubleValue() : 0);
                orderPayload.put("tag", request.tag() != null ? request.tag() : "");
                orderPayload.put("instrument_token", instrument.instrumentToken());
                orderPayload.put("order_type", convertOrderType(request.orderType()));
                orderPayload.put("transaction_type", request.direction() == Direction.BUY ? "BUY" : "SELL");
                orderPayload.put("disclosed_quantity", 0);
                orderPayload.put("trigger_price", request.stopPrice() != null ? request.stopPrice().doubleValue() : 0);
                orderPayload.put("is_amo", false);

                String payload = objectMapper.writeValueAsString(orderPayload);

                HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/v2/order/place"))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    log.error("[UPSTOX_ORDER] Order placement HTTP {}: {}", response.statusCode(), response.body());
                    throw new OrderPlacementException(getBrokerCode(), userBrokerId, request,
                        "HTTP error " + response.statusCode());
                }

                // Parse response
                JsonNode responseJson = objectMapper.readTree(response.body());

                if (responseJson.has("status") && "error".equals(responseJson.get("status").asText())) {
                    String errorMessage = responseJson.has("message") ? responseJson.get("message").asText() : "Unknown error";
                    log.error("[UPSTOX_ORDER] Order placement failed: {}", errorMessage);

                    // Notify rejection listener
                    if (rejectionListener != null) {
                        OrderRejection rejection = new OrderRejection(
                            null, request, errorMessage,
                            responseJson.has("error_code") ? responseJson.get("error_code").asText() : "UNKNOWN",
                            System.currentTimeMillis()
                        );
                        rejectionListener.accept(rejection);
                    }

                    throw new OrderPlacementException(getBrokerCode(), userBrokerId, request, errorMessage);
                }

                // Extract order ID from response
                String orderId = responseJson.has("data") && responseJson.get("data").has("order_id")
                    ? responseJson.get("data").get("order_id").asText()
                    : null;

                if (orderId == null) {
                    throw new OrderPlacementException(getBrokerCode(), userBrokerId, request,
                        "No order ID in response");
                }

                // Create OrderResponse
                OrderResponse orderResponse = OrderResponse.of(
                    orderId,
                    request.symbol(),
                    OrderStatus.PENDING,
                    request.direction(),
                    request.orderType(),
                    request.productType(),
                    request.quantity(),
                    0,  // filledQuantity
                    request.limitPrice(),
                    BigDecimal.ZERO,  // avgFillPrice
                    Instant.now()
                );

                orders.put(orderId, orderResponse);
                log.info("[UPSTOX_ORDER] Order placed successfully: orderId={}", orderId);

                // Record successful order
                Duration latency = Duration.between(startTime, Instant.now());
                if (metrics != null) {
                    metrics.recordOrderSuccess(getBrokerCode(), latency);
                }

                // Notify update listener
                if (orderUpdateListener != null) {
                    orderUpdateListener.accept(orderResponse);
                }

                return orderResponse;

            } catch (OrderPlacementException e) {
                // Record failed order
                Duration latency = Duration.between(startTime, Instant.now());
                if (metrics != null) {
                    metrics.recordOrderFailure(getBrokerCode(), "ORDER_PLACEMENT_FAILED", latency);
                }
                throw e;
            } catch (Exception e) {
                // Record unexpected error
                Duration latency = Duration.between(startTime, Instant.now());
                if (metrics != null) {
                    metrics.recordOrderFailure(getBrokerCode(), "UNEXPECTED_ERROR", latency);
                }
                log.error("[UPSTOX_ORDER] Order placement error", e);
                throw new OrderPlacementException(getBrokerCode(), userBrokerId, request,
                    "Unexpected error: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<List<OrderResponse>> placeOrders(List<OrderRequest> requests) {
        // Upstox doesn't support batch orders - place sequentially
        return CompletableFuture.supplyAsync(() -> {
            List<OrderResponse> responses = new ArrayList<>();
            for (OrderRequest request : requests) {
                try {
                    responses.add(placeOrder(request).join());
                } catch (Exception e) {
                    log.error("[UPSTOX_ORDER] Batch order failed for {}", request.symbol(), e);
                    // Continue with remaining orders
                }
            }
            return responses;
        });
    }

    @Override
    public CompletableFuture<OrderResponse> modifyOrder(String brokerOrderId, BigDecimal newPrice, Integer newQuantity) {
        return CompletableFuture.supplyAsync(() -> {
            Instant startTime = Instant.now();

            if (!authenticated) {
                Duration latency = Duration.between(startTime, Instant.now());
                if (metrics != null) {
                    metrics.recordOrderModification(getBrokerCode(), false, latency);
                }
                throw new OrderModificationException(getBrokerCode(), userBrokerId, brokerOrderId,
                    "Not authenticated");
            }

            try {
                log.info("[UPSTOX_ORDER] Modifying order: orderId={} price={} qty={}",
                         brokerOrderId, newPrice, newQuantity);

                // Build modification request
                ObjectNode modifyPayload = objectMapper.createObjectNode();
                if (newQuantity != null) {
                    modifyPayload.put("quantity", newQuantity);
                }
                if (newPrice != null) {
                    modifyPayload.put("price", newPrice.doubleValue());
                }
                modifyPayload.put("order_id", brokerOrderId);
                modifyPayload.put("validity", "DAY");
                modifyPayload.put("order_type", "LIMIT");

                String payload = objectMapper.writeValueAsString(modifyPayload);

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/v2/order/modify"))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    log.error("[UPSTOX_ORDER] Order modification HTTP {}: {}", response.statusCode(), response.body());
                    throw new OrderModificationException(getBrokerCode(), userBrokerId, brokerOrderId,
                        "HTTP error " + response.statusCode());
                }

                log.info("[UPSTOX_ORDER] Order modified successfully: orderId={}", brokerOrderId);

                // Record successful modification
                Duration latency = Duration.between(startTime, Instant.now());
                if (metrics != null) {
                    metrics.recordOrderModification(getBrokerCode(), true, latency);
                }

                // Fetch updated order details
                return getOrder(brokerOrderId).join();

            } catch (OrderModificationException e) {
                // Record failed modification
                Duration latency = Duration.between(startTime, Instant.now());
                if (metrics != null) {
                    metrics.recordOrderModification(getBrokerCode(), false, latency);
                }
                throw e;
            } catch (Exception e) {
                // Record unexpected error
                Duration latency = Duration.between(startTime, Instant.now());
                if (metrics != null) {
                    metrics.recordOrderModification(getBrokerCode(), false, latency);
                }
                log.error("[UPSTOX_ORDER] Order modification error", e);
                throw new OrderModificationException(getBrokerCode(), userBrokerId, brokerOrderId,
                    "Unexpected error: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<OrderResponse> cancelOrder(String brokerOrderId) {
        return CompletableFuture.supplyAsync(() -> {
            Instant startTime = Instant.now();

            if (!authenticated) {
                Duration latency = Duration.between(startTime, Instant.now());
                if (metrics != null) {
                    metrics.recordOrderCancellation(getBrokerCode(), false, latency);
                }
                throw new OrderCancellationException(getBrokerCode(), userBrokerId, brokerOrderId,
                    "Not authenticated");
            }

            try {
                log.info("[UPSTOX_ORDER] Cancelling order: orderId={}", brokerOrderId);

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/v2/order/cancel?order_id=" + brokerOrderId))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/json")
                    .DELETE()
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    log.error("[UPSTOX_ORDER] Order cancellation HTTP {}: {}", response.statusCode(), response.body());
                    throw new OrderCancellationException(getBrokerCode(), userBrokerId, brokerOrderId,
                        "HTTP error " + response.statusCode());
                }

                log.info("[UPSTOX_ORDER] Order cancelled successfully: orderId={}", brokerOrderId);

                // Record successful cancellation
                Duration latency = Duration.between(startTime, Instant.now());
                if (metrics != null) {
                    metrics.recordOrderCancellation(getBrokerCode(), true, latency);
                }

                // Create cancelled order response
                OrderResponse existing = orders.get(brokerOrderId);
                if (existing != null) {
                    OrderResponse cancelled = new OrderResponse(
                        existing.brokerOrderId(),
                        existing.symbol(),
                        OrderStatus.CANCELLED,
                        existing.direction(),
                        existing.orderType(),
                        existing.productType(),
                        existing.quantity(),
                        existing.filledQuantity(),
                        existing.pendingQuantity(),
                        existing.orderPrice(),
                        existing.avgFillPrice(),
                        existing.orderTime(),
                        Instant.now(),
                        "Cancelled by user",
                        existing.tag(),
                        existing.extendedData()
                    );
                    orders.put(brokerOrderId, cancelled);

                    if (orderUpdateListener != null) {
                        orderUpdateListener.accept(cancelled);
                    }

                    return cancelled;
                }

                return getOrder(brokerOrderId).join();

            } catch (OrderCancellationException e) {
                // Record failed cancellation
                Duration latency = Duration.between(startTime, Instant.now());
                if (metrics != null) {
                    metrics.recordOrderCancellation(getBrokerCode(), false, latency);
                }
                throw e;
            } catch (Exception e) {
                // Record unexpected error
                Duration latency = Duration.between(startTime, Instant.now());
                if (metrics != null) {
                    metrics.recordOrderCancellation(getBrokerCode(), false, latency);
                }
                log.error("[UPSTOX_ORDER] Order cancellation error", e);
                throw new OrderCancellationException(getBrokerCode(), userBrokerId, brokerOrderId,
                    "Unexpected error: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<OrderStatus> getOrderStatus(String brokerOrderId) {
        return getOrder(brokerOrderId).thenApply(OrderResponse::status);
    }

    @Override
    public CompletableFuture<OrderResponse> getOrder(String brokerOrderId) {
        return CompletableFuture.supplyAsync(() -> {
            if (!authenticated) {
                throw new RuntimeException("Not authenticated");
            }

            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/v2/order/details?order_id=" + brokerOrderId))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    throw new RuntimeException("HTTP error " + response.statusCode());
                }

                JsonNode responseJson = objectMapper.readTree(response.body());

                if (responseJson.has("data")) {
                    return parseOrderResponse(responseJson.get("data"));
                }

                throw new RuntimeException("No order data in response");

            } catch (Exception e) {
                log.error("[UPSTOX_ORDER] Get order error", e);
                throw new RuntimeException("Error fetching order: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<List<OrderResponse>> getAllOrders() {
        return CompletableFuture.supplyAsync(() -> {
            if (!authenticated) {
                throw new RuntimeException("Not authenticated");
            }

            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/v2/order/retrieve-all"))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    throw new RuntimeException("HTTP error " + response.statusCode());
                }

                JsonNode responseJson = objectMapper.readTree(response.body());
                List<OrderResponse> allOrders = new ArrayList<>();

                if (responseJson.has("data")) {
                    for (JsonNode orderNode : responseJson.get("data")) {
                        allOrders.add(parseOrderResponse(orderNode));
                    }
                }

                return allOrders;

            } catch (Exception e) {
                log.error("[UPSTOX_ORDER] Get all orders error", e);
                throw new RuntimeException("Error fetching orders: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public CompletableFuture<List<OrderResponse>> getOpenOrders() {
        return getAllOrders().thenApply(orders -> orders.stream()
            .filter(order -> order.status() == OrderStatus.PENDING || order.status() == OrderStatus.PLACED)
            .toList()
        );
    }

    @Override
    public void onOrderUpdate(Consumer<OrderResponse> orderUpdateListener) {
        this.orderUpdateListener = orderUpdateListener;
    }

    @Override
    public void onOrderRejection(Consumer<OrderRejection> rejectionListener) {
        this.rejectionListener = rejectionListener;
    }

    @Override
    public void onError(Consumer<Throwable> errorListener) {
        this.errorListener = errorListener;
    }

    @Override
    public String getBrokerCode() {
        return "UPSTOX";
    }

    @Override
    public String getUserBrokerId() {
        return userBrokerId;
    }

    @Override
    public boolean supportsBatchOrders() {
        return false;
    }

    @Override
    public RateLimitInfo getRateLimitInfo() {
        return new RateLimitInfo(RATE_LIMIT_PER_SECOND, RATE_LIMIT_PER_DAY, 20);
    }

    // ════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ════════════════════════════════════════════════════════════════════════

    private String convertProductType(ProductType productType) {
        if (productType == null) productType = ProductType.CNC;

        return switch (productType) {
            case CNC -> "D";           // Delivery
            case MIS -> "I";           // Intraday
            case NRML -> "M";          // Margin
            case MTF -> "MTF";         // Margin Trade Funding
            default -> "D";
        };
    }

    private String convertOrderType(OrderType orderType) {
        return switch (orderType) {
            case MARKET -> "MARKET";
            case LIMIT -> "LIMIT";
            case STOP_LOSS -> "SL";
        };
    }

    private String convertTimeInForce(TimeInForce timeInForce) {
        return switch (timeInForce) {
            case DAY -> "DAY";
            case IOC -> "IOC";
            case GTC -> "DAY";  // Upstox doesn't support GTC, default to DAY
        };
    }

    private OrderStatus convertUpstoxStatus(String status) {
        return switch (status.toUpperCase()) {
            case "PUT ORDER REQ RECEIVED", "VALIDATION PENDING", "OPEN PENDING" -> OrderStatus.PENDING;
            case "TRIGGER PENDING", "OPEN" -> OrderStatus.PLACED;
            case "COMPLETE" -> OrderStatus.FILLED;
            case "REJECTED" -> OrderStatus.REJECTED;
            case "CANCELLED" -> OrderStatus.CANCELLED;
            case "AFTER MARKET ORDER REQ RECEIVED" -> OrderStatus.PENDING;
            default -> OrderStatus.PENDING;
        };
    }

    private OrderResponse parseOrderResponse(JsonNode orderNode) {
        String orderId = orderNode.has("order_id") ? orderNode.get("order_id").asText() : null;
        String symbol = orderNode.has("trading_symbol") ? orderNode.get("trading_symbol").asText() : "";
        String statusStr = orderNode.has("status") ? orderNode.get("status").asText() : "PENDING";
        int quantity = orderNode.has("quantity") ? orderNode.get("quantity").asInt() : 0;
        int filledQty = orderNode.has("filled_quantity") ? orderNode.get("filled_quantity").asInt() : 0;

        double price = orderNode.has("price") ? orderNode.get("price").asDouble() : 0.0;
        double avgPrice = orderNode.has("average_price") ? orderNode.get("average_price").asDouble() : 0.0;

        return new OrderResponse(
            orderId,
            symbol,
            convertUpstoxStatus(statusStr),
            null,  // direction not in response
            null,  // orderType not in response
            null,  // productType not in response
            quantity,
            filledQty,
            quantity - filledQty,
            BigDecimal.valueOf(price),
            BigDecimal.valueOf(avgPrice),
            Instant.now(),
            filledQty == quantity ? Instant.now() : null,
            orderNode.has("rejection_reason") ? orderNode.get("rejection_reason").asText() : null,
            orderNode.has("tag") ? orderNode.get("tag").asText() : null,
            Map.of()
        );
    }
}
