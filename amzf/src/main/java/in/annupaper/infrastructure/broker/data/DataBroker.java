package in.annupaper.infrastructure.broker.data;

import in.annupaper.domain.data.Tick;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * DataBroker interface for market data operations.
 *
 * Responsibilities:
 * - Subscribe/unsubscribe to market data feeds
 * - Dispatch real-time tick data
 * - Manage WebSocket connections for data streaming
 * - Handle authentication and token refresh
 *
 * Error Handling:
 * - All async operations return CompletableFuture
 * - Failures are propagated via CompletableFuture.exceptionally()
 * - Connection failures trigger registered error callbacks
 *
 * Lifecycle:
 * 1. authenticate() - Obtain access tokens
 * 2. connect() - Establish WebSocket connection
 * 3. subscribe() - Subscribe to symbols
 * 4. [receive ticks via callback]
 * 5. unsubscribe() - Unsubscribe from symbols
 * 6. disconnect() - Close connection
 */
public interface DataBroker {

    // ═══════════════════════════════════════════════════════════════════════
    // LIFECYCLE MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Authenticate with the broker to obtain access tokens.
     *
     * @return CompletableFuture that completes when authentication succeeds
     * @throws BrokerAuthenticationException if authentication fails
     */
    CompletableFuture<Void> authenticate();

    /**
     * Establish WebSocket connection to the broker's data feed.
     * Must be called after authenticate().
     *
     * @return CompletableFuture that completes when connection is established
     * @throws BrokerConnectionException if connection fails
     */
    CompletableFuture<Void> connect();

    /**
     * Gracefully disconnect from the broker's data feed.
     * Unsubscribes from all symbols before closing connection.
     *
     * @return CompletableFuture that completes when disconnection is complete
     */
    CompletableFuture<Void> disconnect();

    /**
     * Check if currently connected to the broker's data feed.
     *
     * @return true if WebSocket connection is active and healthy
     */
    boolean isConnected();

    /**
     * Check if authenticated with valid access token.
     *
     * @return true if access token is valid and not expired
     */
    boolean isAuthenticated();

    // ═══════════════════════════════════════════════════════════════════════
    // SUBSCRIPTION MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Subscribe to real-time tick data for specified symbols.
     * Connection must be established before subscribing.
     *
     * @param symbols List of trading symbols (e.g., "NSE:RELIANCE-EQ")
     * @return CompletableFuture that completes when subscription succeeds
     * @throws BrokerSubscriptionException if subscription fails
     */
    CompletableFuture<Void> subscribe(List<String> symbols);

    /**
     * Unsubscribe from real-time tick data for specified symbols.
     *
     * @param symbols List of trading symbols to unsubscribe from
     * @return CompletableFuture that completes when unsubscription succeeds
     */
    CompletableFuture<Void> unsubscribe(List<String> symbols);

    /**
     * Unsubscribe from all currently subscribed symbols.
     *
     * @return CompletableFuture that completes when all symbols are unsubscribed
     */
    CompletableFuture<Void> unsubscribeAll();

    /**
     * Get the set of currently subscribed symbols.
     *
     * @return Set of symbols currently subscribed to
     */
    Set<String> getSubscribedSymbols();

    // ═══════════════════════════════════════════════════════════════════════
    // TICK DATA DISPATCH
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Register callback to receive real-time tick data.
     * Multiple callbacks can be registered; all will be notified.
     *
     * @param tickListener Consumer that processes incoming ticks
     */
    void onTick(Consumer<Tick> tickListener);

    /**
     * Register callback to receive connection errors.
     * Use this to implement reconnection logic.
     *
     * @param errorListener Consumer that handles connection errors
     */
    void onError(Consumer<Throwable> errorListener);

    /**
     * Register callback for connection state changes.
     *
     * @param stateListener Consumer that receives connection state updates
     */
    void onConnectionStateChange(Consumer<ConnectionState> stateListener);

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
     * Connection state enumeration.
     */
    enum ConnectionState {
        DISCONNECTED,
        AUTHENTICATING,
        AUTHENTICATED,
        CONNECTING,
        CONNECTED,
        SUBSCRIBING,
        STREAMING,
        ERROR,
        RECONNECTING
    }
}
