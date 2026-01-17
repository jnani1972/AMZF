package in.annupaper.infrastructure.broker.adapters;

import in.annupaper.domain.broker.BrokerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Dhan API adapter (stub implementation).
 * 
 * Production implementation would use:
 * - Dhan HQ API: https://dhanhq.co/docs/v2/
 * - WebSocket for live data
 * 
 * API Docs: https://dhanhq.co/docs/v2/
 */
public class DhanAdapter implements BrokerAdapter {
    private static final Logger log = LoggerFactory.getLogger(DhanAdapter.class);
    
    private volatile boolean connected = false;
    private String sessionToken;
    private BrokerCredentials credentials;
    
    private final Map<String, TickListener> tickListeners = new ConcurrentHashMap<>();
    private final ScheduledExecutorService tickSimulator = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> tickTask;
    private final Map<String, OrderStatus> orders = new ConcurrentHashMap<>();
    
    @Override
    public String getBrokerCode() {
        return "DHAN";
    }
    
    @Override
    public CompletableFuture<ConnectionResult> connect(BrokerCredentials credentials) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("[DHAN] Connecting with clientId={}", maskKey(credentials.userId()));
            
            this.credentials = credentials;
            
            // In production: Use Dhan API
            // DhanAPI dhan = new DhanAPI(clientId, accessToken);
            // Profile profile = dhan.getProfile();
            
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            if (credentials.accessToken() == null || credentials.accessToken().isEmpty()) {
                return ConnectionResult.failure("Invalid access token", "INVALID_TOKEN");
            }
            
            this.sessionToken = "dhan_session_" + UUID.randomUUID().toString().substring(0, 8);
            this.connected = true;
            
            log.info("[DHAN] Connected successfully, session={}", sessionToken);
            return ConnectionResult.success(sessionToken);
        });
    }
    
    @Override
    public void disconnect() {
        log.info("[DHAN] Disconnecting...");
        connected = false;
        sessionToken = null;
        if (tickTask != null) tickTask.cancel(false);
        tickListeners.clear();
    }
    
    @Override
    public boolean isConnected() {
        return connected;
    }
    
    @Override
    public CompletableFuture<OrderResult> placeOrder(OrderRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected) {
                return OrderResult.failure("Not connected", "NOT_CONNECTED");
            }
            
            log.info("[DHAN] Placing order: {} {} {} qty={} @ {}", 
                     request.transactionType(), request.symbol(), request.orderType(),
                     request.quantity(), request.price());
            
            // Dhan uses security ID format
            String orderId = "DH" + System.currentTimeMillis();
            
            String status = "MARKET".equals(request.orderType()) ? "COMPLETE" : "OPEN";
            int filledQty = "MARKET".equals(request.orderType()) ? request.quantity() : 0;
            BigDecimal avgPrice = "MARKET".equals(request.orderType()) 
                ? request.price() != null ? request.price() : new BigDecimal("100")
                : BigDecimal.ZERO;
            
            OrderStatus orderStatus = new OrderStatus(
                orderId,
                request.symbol(),
                request.exchange(),
                request.transactionType(),
                request.orderType(),
                request.productType(),
                request.quantity(),
                filledQty,
                request.quantity() - filledQty,
                request.price(),
                avgPrice,
                request.triggerPrice(),
                status,
                "Order placed",
                Instant.now().toString(),
                "DH" + System.currentTimeMillis(),
                request.tag()
            );
            
            orders.put(orderId, orderStatus);
            
            log.info("[DHAN] Order placed: {} status={}", orderId, status);
            return OrderResult.success(orderId);
        });
    }
    
    @Override
    public CompletableFuture<OrderResult> modifyOrder(String orderId, OrderModifyRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected) return OrderResult.failure("Not connected", "NOT_CONNECTED");
            
            OrderStatus existing = orders.get(orderId);
            if (existing == null) return OrderResult.failure("Order not found", "ORDER_NOT_FOUND");
            
            log.info("[DHAN] Modifying order: {}", orderId);
            
            OrderStatus modified = new OrderStatus(
                orderId, existing.symbol(), existing.exchange(), existing.transactionType(),
                request.orderType() != null ? request.orderType() : existing.orderType(),
                existing.productType(),
                request.quantity() > 0 ? request.quantity() : existing.quantity(),
                existing.filledQuantity(), existing.pendingQuantity(),
                request.price() != null ? request.price() : existing.price(),
                existing.averagePrice(),
                request.triggerPrice() != null ? request.triggerPrice() : existing.triggerPrice(),
                existing.status(), "Modified", Instant.now().toString(),
                existing.exchangeOrderId(), existing.tag()
            );
            
            orders.put(orderId, modified);
            return OrderResult.success(orderId);
        });
    }
    
    @Override
    public CompletableFuture<OrderResult> cancelOrder(String orderId) {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected) return OrderResult.failure("Not connected", "NOT_CONNECTED");
            
            OrderStatus existing = orders.get(orderId);
            if (existing == null) return OrderResult.failure("Order not found", "ORDER_NOT_FOUND");
            
            log.info("[DHAN] Cancelling order: {}", orderId);
            
            OrderStatus cancelled = new OrderStatus(
                orderId, existing.symbol(), existing.exchange(), existing.transactionType(),
                existing.orderType(), existing.productType(), existing.quantity(),
                existing.filledQuantity(), 0, existing.price(), existing.averagePrice(),
                existing.triggerPrice(), "CANCELLED", "Cancelled", Instant.now().toString(),
                existing.exchangeOrderId(), existing.tag()
            );
            
            orders.put(orderId, cancelled);
            return OrderResult.success(orderId);
        });
    }
    
    @Override
    public CompletableFuture<OrderStatus> getOrderStatus(String orderId) {
        return CompletableFuture.supplyAsync(() -> orders.get(orderId));
    }
    
    @Override
    public CompletableFuture<List<OrderStatus>> getOpenOrders() {
        return CompletableFuture.supplyAsync(() -> 
            orders.values().stream().filter(o -> "OPEN".equals(o.status())).toList()
        );
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
        return CompletableFuture.supplyAsync(() -> new AccountFunds(
            new BigDecimal("300000"), new BigDecimal("30000"),
            new BigDecimal("330000"), BigDecimal.ZERO, new BigDecimal("300000")
        ));
    }
    
    @Override
    public CompletableFuture<BigDecimal> getLtp(String symbol) {
        return CompletableFuture.supplyAsync(() -> 
            new BigDecimal("100").add(new BigDecimal(Math.random() * 10).setScale(2, java.math.RoundingMode.HALF_UP))
        );
    }
    
    @Override
    public void subscribeTicks(List<String> symbols, TickListener listener) {
        log.info("[DHAN] Subscribing to ticks: {}", symbols);
        for (String symbol : symbols) tickListeners.put(symbol, listener);
        if (tickTask == null || tickTask.isCancelled()) {
            tickTask = tickSimulator.scheduleAtFixedRate(this::simulateTicks, 0, 1, TimeUnit.SECONDS);
        }
    }
    
    @Override
    public CompletableFuture<List<HistoricalCandle>> getHistoricalCandles(
        String symbol,
        int interval,
        long fromEpoch,
        long toEpoch
    ) {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected) {
                throw new RuntimeException("Not connected");
            }

            log.info("[DHAN] Fetching historical candles: {} interval={} from={} to={}",
                     symbol, interval, fromEpoch, toEpoch);

            // Dhan HQ API: GET /charts/historical
            // URL: https://api.dhan.co/v2/charts/historical
            // Params: symbol_id, exchange_segment, instrument_type, interval (1, 5, 15, 25, 60), from_date, to_date

            // Stub: Generate simulated candles
            List<HistoricalCandle> candles = new ArrayList<>();
            long currentTime = fromEpoch;
            long intervalSeconds = interval * 60L;
            BigDecimal currentClose = new BigDecimal("1000");

            while (currentTime <= toEpoch) {
                BigDecimal open = currentClose;
                BigDecimal variation = new BigDecimal(Math.random() * 20 - 10).setScale(2, java.math.RoundingMode.HALF_UP);
                BigDecimal close = open.add(variation);
                BigDecimal high = open.max(close).add(new BigDecimal(Math.random() * 5).setScale(2, java.math.RoundingMode.HALF_UP));
                BigDecimal low = open.min(close).subtract(new BigDecimal(Math.random() * 5).setScale(2, java.math.RoundingMode.HALF_UP));
                long volume = (long) (Math.random() * 100000);

                candles.add(new HistoricalCandle(currentTime, open, high, low, close, volume));
                currentClose = close;
                currentTime += intervalSeconds;
            }

            log.info("[DHAN] Fetched {} candles for {}", candles.size(), symbol);
            return candles;
        });
    }

    @Override
    public void unsubscribeTicks(List<String> symbols) {
        for (String symbol : symbols) tickListeners.remove(symbol);
        if (tickListeners.isEmpty() && tickTask != null) {
            tickTask.cancel(false);
            tickTask = null;
        }
    }
    
    private void simulateTicks() {
        if (!connected) return;
        for (Map.Entry<String, TickListener> entry : tickListeners.entrySet()) {
            BigDecimal basePrice = new BigDecimal("1000");
            BigDecimal lastPrice = basePrice.add(new BigDecimal(Math.random() * 20 - 10).setScale(2, java.math.RoundingMode.HALF_UP));
            Tick tick = new Tick(entry.getKey(), lastPrice, basePrice,
                basePrice.add(new BigDecimal("15")), basePrice.subtract(new BigDecimal("10")),
                basePrice.add(new BigDecimal("5")), (long)(Math.random() * 100000),
                lastPrice.subtract(new BigDecimal("0.05")), lastPrice.add(new BigDecimal("0.05")),
                (int)(Math.random() * 1000), (int)(Math.random() * 1000), System.currentTimeMillis()
            );
            try { entry.getValue().onTick(tick); } catch (Exception e) { log.warn("Tick error: {}", e.getMessage()); }
        }
    }
    
    private String maskKey(String key) {
        if (key == null || key.length() < 8) return "***";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    @Override
    public CompletableFuture<List<Instrument>> getInstruments() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Dhan provides instruments via API - we'll implement a simplified version
                // Real implementation would download CSV/JSON from Dhan API
                // For now, return empty list with TODO
                log.warn("[DHAN] getInstruments() not yet implemented - returning empty list");
                return List.of();
            } catch (Exception e) {
                log.error("[DHAN] Error fetching instruments: {}", e.getMessage(), e);
                return List.of();
            }
        });
    }
}
