package in.annupaper.infrastructure.broker.order;

import in.annupaper.domain.order.OrderRequest;
import in.annupaper.domain.order.OrderResponse;
import in.annupaper.domain.order.OrderStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * OrderBroker interface for order execution operations.
 *
 * Responsibilities:
 * - Place, modify, and cancel orders
 * - Poll order status and execution details
 * - Handle order lifecycle events
 * - Manage authentication for order execution API
 *
 * Error Handling:
 * - All async operations return CompletableFuture
 * - Order rejection details included in OrderResponse
 * - Rate limiting handled via backoff and retry
 *
 * Lifecycle:
 * 1. authenticate() - Obtain order execution tokens
 * 2. connect() - Initialize order API connection (if needed)
 * 3. placeOrder() - Submit orders
 * 4. getOrderStatus() - Poll order status
 * 5. modifyOrder() / cancelOrder() - Manage orders
 * 6. disconnect() - Clean up resources
 */
public interface OrderBroker {

    // ═══════════════════════════════════════════════════════════════════════
    // LIFECYCLE MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Authenticate with the broker to obtain order execution access.
     *
     * @return CompletableFuture that completes when authentication succeeds
     * @throws OrderBrokerAuthenticationException if authentication fails
     */
    CompletableFuture<Void> authenticate();

    /**
     * Initialize connection to the broker's order API.
     * May be a no-op for REST-based brokers.
     *
     * @return CompletableFuture that completes when connection is ready
     */
    CompletableFuture<Void> connect();

    /**
     * Disconnect from the broker's order API and clean up resources.
     *
     * @return CompletableFuture that completes when disconnection is complete
     */
    CompletableFuture<Void> disconnect();

    /**
     * Check if authenticated with valid order execution token.
     *
     * @return true if access token is valid and not expired
     */
    boolean isAuthenticated();

    /**
     * Check if connection to order API is active.
     *
     * @return true if order API is ready to accept requests
     */
    boolean isConnected();

    // ═══════════════════════════════════════════════════════════════════════
    // ORDER PLACEMENT
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Place a new order with the broker.
     *
     * @param request Order details (symbol, direction, quantity, type, price)
     * @return CompletableFuture with OrderResponse containing broker order ID
     * @throws OrderPlacementException if order placement fails
     */
    CompletableFuture<OrderResponse> placeOrder(OrderRequest request);

    /**
     * Place multiple orders in a batch (if supported by broker).
     * Falls back to sequential placement if batch not supported.
     *
     * @param requests List of order requests
     * @return CompletableFuture with list of OrderResponses
     */
    CompletableFuture<List<OrderResponse>> placeOrders(List<OrderRequest> requests);

    // ═══════════════════════════════════════════════════════════════════════
    // ORDER MODIFICATION
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Modify an existing order's price and/or quantity.
     *
     * @param brokerOrderId Broker's order ID
     * @param newPrice New limit price (null to keep existing)
     * @param newQuantity New quantity (null to keep existing)
     * @return CompletableFuture with updated OrderResponse
     * @throws OrderModificationException if modification fails
     */
    CompletableFuture<OrderResponse> modifyOrder(String brokerOrderId,
                                                  BigDecimal newPrice,
                                                  Integer newQuantity);

    /**
     * Cancel an existing order.
     *
     * @param brokerOrderId Broker's order ID
     * @return CompletableFuture with cancellation result
     * @throws OrderCancellationException if cancellation fails
     */
    CompletableFuture<OrderResponse> cancelOrder(String brokerOrderId);

    // ═══════════════════════════════════════════════════════════════════════
    // ORDER STATUS POLLING
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Get current status of a specific order.
     *
     * @param brokerOrderId Broker's order ID
     * @return CompletableFuture with current OrderStatus
     */
    CompletableFuture<OrderStatus> getOrderStatus(String brokerOrderId);

    /**
     * Get details of a specific order including fills.
     *
     * @param brokerOrderId Broker's order ID
     * @return CompletableFuture with complete OrderResponse
     */
    CompletableFuture<OrderResponse> getOrder(String brokerOrderId);

    /**
     * Get all orders for the current trading day.
     *
     * @return CompletableFuture with list of all orders
     */
    CompletableFuture<List<OrderResponse>> getAllOrders();

    /**
     * Get all open (pending) orders.
     *
     * @return CompletableFuture with list of pending orders
     */
    CompletableFuture<List<OrderResponse>> getOpenOrders();

    // ═══════════════════════════════════════════════════════════════════════
    // ORDER EVENT CALLBACKS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Register callback for order execution updates.
     * Some brokers support order update push notifications.
     *
     * @param orderUpdateListener Consumer that receives order updates
     */
    void onOrderUpdate(Consumer<OrderResponse> orderUpdateListener);

    /**
     * Register callback for order rejection events.
     *
     * @param rejectionListener Consumer that handles order rejections
     */
    void onOrderRejection(Consumer<OrderRejection> rejectionListener);

    /**
     * Register callback for order errors.
     *
     * @param errorListener Consumer that handles order errors
     */
    void onError(Consumer<Throwable> errorListener);

    // ═══════════════════════════════════════════════════════════════════════
    // METADATA
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Get the broker identifier (e.g., "ZERODHA", "FYERS").
     *
     * @return Broker code
     */
    String getBrokerCode();

    /**
     * Get the user-broker identifier for this connection.
     *
     * @return UserBroker ID
     */
    String getUserBrokerId();

    /**
     * Check if broker supports batch order placement.
     *
     * @return true if placeOrders() is optimized for batching
     */
    boolean supportsBatchOrders();

    /**
     * Get rate limit information for order placement.
     *
     * @return RateLimitInfo describing limits
     */
    RateLimitInfo getRateLimitInfo();

    // ═══════════════════════════════════════════════════════════════════════
    // VALUE OBJECTS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Order rejection details.
     */
    record OrderRejection(
        String brokerOrderId,
        OrderRequest originalRequest,
        String rejectionReason,
        String rejectionCode,
        long timestamp
    ) {}

    /**
     * Rate limit information.
     */
    record RateLimitInfo(
        int maxOrdersPerSecond,
        int maxOrdersPerMinute,
        int burstLimit
    ) {}
}
