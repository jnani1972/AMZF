package in.annupaper.broker;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Broker adapter interface for order execution and market data.
 */
public interface BrokerAdapter {
    
    /**
     * Get broker code (ZERODHA, FYERS, DHAN, etc.)
     */
    String getBrokerCode();
    
    /**
     * Connect to broker with credentials.
     */
    CompletableFuture<ConnectionResult> connect(BrokerCredentials credentials);
    
    /**
     * Disconnect from broker.
     */
    void disconnect();
    
    /**
     * Check if connected.
     */
    boolean isConnected();
    
    /**
     * Place an order.
     */
    CompletableFuture<OrderResult> placeOrder(OrderRequest request);
    
    /**
     * Modify an existing order.
     */
    CompletableFuture<OrderResult> modifyOrder(String orderId, OrderModifyRequest request);
    
    /**
     * Cancel an order.
     */
    CompletableFuture<OrderResult> cancelOrder(String orderId);
    
    /**
     * Get order status.
     */
    CompletableFuture<OrderStatus> getOrderStatus(String orderId);
    
    /**
     * Get all open orders.
     */
    CompletableFuture<List<OrderStatus>> getOpenOrders();
    
    /**
     * Get positions.
     */
    CompletableFuture<List<Position>> getPositions();
    
    /**
     * Get holdings.
     */
    CompletableFuture<List<Holding>> getHoldings();
    
    /**
     * Get account margins/funds.
     */
    CompletableFuture<AccountFunds> getFunds();
    
    /**
     * Get LTP for symbol.
     */
    CompletableFuture<BigDecimal> getLtp(String symbol);
    
    /**
     * Subscribe to tick data (for DATA broker).
     */
    void subscribeTicks(List<String> symbols, TickListener listener);
    
    /**
     * Unsubscribe from tick data.
     */
    void unsubscribeTicks(List<String> symbols);

    /**
     * Get historical candles for a symbol.
     * @param symbol The symbol to fetch candles for
     * @param interval Candle interval (1, 25, 125 minutes)
     * @param fromEpoch Start timestamp (epoch seconds)
     * @param toEpoch End timestamp (epoch seconds)
     * @return List of historical candles
     */
    CompletableFuture<List<HistoricalCandle>> getHistoricalCandles(
        String symbol,
        int interval,
        long fromEpoch,
        long toEpoch
    );

    /**
     * Get instrument master list from broker.
     * @return List of all tradeable instruments
     */
    CompletableFuture<List<Instrument>> getInstruments();

    // ═══════════════════════════════════════════════════════════════
    // Inner types
    // ═══════════════════════════════════════════════════════════════
    
    record BrokerCredentials(
        String apiKey,
        String apiSecret,
        String accessToken,
        String userId,
        String password,
        String totp
    ) {}
    
    record ConnectionResult(
        boolean success,
        String message,
        String sessionToken,
        String errorCode
    ) {
        public static ConnectionResult success(String sessionToken) {
            return new ConnectionResult(true, "Connected", sessionToken, null);
        }
        public static ConnectionResult failure(String message, String errorCode) {
            return new ConnectionResult(false, message, null, errorCode);
        }
    }
    
    record OrderRequest(
        String symbol,
        String exchange,         // NSE, BSE, NFO
        String transactionType,  // BUY, SELL
        String orderType,        // MARKET, LIMIT, SL, SL-M
        String productType,      // CNC, MIS, NRML
        int quantity,
        BigDecimal price,        // For LIMIT orders
        BigDecimal triggerPrice, // For SL orders
        String validity,         // DAY, IOC
        String tag               // User tag for tracking
    ) {}
    
    record OrderModifyRequest(
        String orderType,
        int quantity,
        BigDecimal price,
        BigDecimal triggerPrice
    ) {}
    
    record OrderResult(
        boolean success,
        String orderId,
        String message,
        String errorCode
    ) {
        public static OrderResult success(String orderId) {
            return new OrderResult(true, orderId, "Order placed", null);
        }
        public static OrderResult failure(String message, String errorCode) {
            return new OrderResult(false, null, message, errorCode);
        }
    }
    
    record OrderStatus(
        String orderId,
        String symbol,
        String exchange,
        String transactionType,
        String orderType,
        String productType,
        int quantity,
        int filledQuantity,
        int pendingQuantity,
        BigDecimal price,
        BigDecimal averagePrice,
        BigDecimal triggerPrice,
        String status,           // OPEN, COMPLETE, CANCELLED, REJECTED
        String statusMessage,
        String timestamp,
        String exchangeOrderId,
        String tag
    ) {}
    
    record Position(
        String symbol,
        String exchange,
        String productType,
        int quantity,
        int buyQuantity,
        int sellQuantity,
        BigDecimal buyValue,
        BigDecimal sellValue,
        BigDecimal averageBuyPrice,
        BigDecimal averageSellPrice,
        BigDecimal lastPrice,
        BigDecimal pnl,
        BigDecimal dayPnl
    ) {}
    
    record Holding(
        String symbol,
        String exchange,
        String isin,
        int quantity,
        int t1Quantity,
        BigDecimal averagePrice,
        BigDecimal lastPrice,
        BigDecimal pnl,
        BigDecimal dayPnl
    ) {}
    
    record AccountFunds(
        BigDecimal availableCash,
        BigDecimal usedMargin,
        BigDecimal totalMargin,
        BigDecimal collateral,
        BigDecimal openingBalance
    ) {}
    
    record Tick(
        String symbol,
        BigDecimal lastPrice,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume,
        BigDecimal bid,
        BigDecimal ask,
        int bidQty,
        int askQty,
        long timestamp
    ) {}
    
    @FunctionalInterface
    interface TickListener {
        void onTick(Tick tick);
    }

    record HistoricalCandle(
        long timestamp,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume
    ) {}

    record Instrument(
        String exchange,           // NSE, BSE, NFO, MCX
        String tradingSymbol,      // e.g., NSE:RELIANCE-EQ
        String name,               // Company/instrument name
        String instrumentType,     // EQ, FUT, CE, PE
        String segment,            // EQUITY, DERIVATIVE, COMMODITY
        String token,              // Broker-specific token/instrument_token
        int lotSize,               // Lot size for derivatives, 1 for equity
        BigDecimal tickSize,       // Minimum price movement
        String expiryDate,         // For derivatives (YYYY-MM-DD)
        BigDecimal strikePrice,    // For options
        String optionType          // CE or PE for options
    ) {}
}
