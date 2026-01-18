package in.annupaper.domain.model;

import in.annupaper.domain.model.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for broker adapters (Fyers, Zerodha, etc.).
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
        CompletableFuture<OrderResult> placeOrder(BrokerOrderRequest request);

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
        CompletableFuture<BrokerOrderStatus> getOrderStatus(String orderId);

        /**
         * Get all open orders.
         */
        CompletableFuture<List<BrokerOrderStatus>> getOpenOrders();

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
         * Fetch historical candles.
         */
        CompletableFuture<List<HistoricalCandle>> getHistoricalCandles(
                        String symbol,
                        TimeframeType timeframe,
                        long fromEpoch,
                        long toEpoch);

        /**
         * Get instrument master list from broker.
         * 
         * @return List of all tradeable instruments
         */
        CompletableFuture<List<BrokerInstrument>> getInstruments();

        // ═══════════════════════════════════════════════════════════════
        // Inner types
        // ═══════════════════════════════════════════════════════════════

        record BrokerCredentials(
                        String apiKey,
                        String apiSecret,
                        String accessToken,
                        String userId,
                        String password,
                        String totp) {
        }

        record ConnectionResult(
                        boolean success,
                        String message,
                        String sessionToken,
                        String errorCode) {
                public static ConnectionResult ofSuccess(String sessionToken) {
                        return new ConnectionResult(true, "Connected", sessionToken, null);
                }

                public static ConnectionResult ofFailure(String message, String errorCode) {
                        return new ConnectionResult(false, message, null, errorCode);
                }
        }

        record BrokerOrderRequest(
                        String symbol,
                        String exchange, // NSE, BSE, NFO
                        String transactionType, // BUY, SELL
                        String orderType, // MARKET, LIMIT, SL, SL-M
                        String productType, // CNC, MIS, NRML
                        int quantity,
                        BigDecimal price, // For LIMIT orders
                        BigDecimal triggerPrice, // For SL orders
                        String validity, // DAY, IOC
                        String tag // User tag for tracking
        ) {
        }

        record OrderModifyRequest(
                        String orderType,
                        int quantity,
                        BigDecimal price,
                        BigDecimal triggerPrice) {
        }

        record OrderResult(
                        boolean success,
                        String orderId,
                        String message,
                        String errorCode) {
                public static OrderResult ofSuccess(String orderId) {
                        return new OrderResult(true, orderId, "Order placed", null);
                }

                public static OrderResult ofFailure(String message, String errorCode) {
                        return new OrderResult(false, null, message, errorCode);
                }
        }

        record BrokerOrderStatus(
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
                        String status, // OPEN, COMPLETE, CANCELLED, REJECTED
                        String statusMessage,
                        String timestamp,
                        String exchangeOrderId,
                        String tag) {
        }

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
                        BigDecimal realizedPnl,
                        BigDecimal unrealizedPnl) {
        }

        record Holding(
                        String symbol,
                        String exchange,
                        String isin,
                        int quantity,
                        int t1Quantity,
                        int realQuantity,
                        BigDecimal averagePrice,
                        BigDecimal lastPrice,
                        BigDecimal pnl) {
        }

        record AccountFunds(
                        BigDecimal collateral,
                        BigDecimal used,
                        BigDecimal available,
                        BigDecimal realizedPnl,
                        BigDecimal net) {
        }

        interface TickListener {
                void onTick(Tick tick);

                void onError(Throwable error);
        }

}
