package in.annupaper.broker.adapters;

import in.annupaper.broker.BrokerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Upstox API v2 adapter (stub implementation).
 *
 * API Docs: https://upstox.com/developer/api-documentation
 */
public class UpstoxAdapter implements BrokerAdapter {
    private static final Logger log = LoggerFactory.getLogger(UpstoxAdapter.class);

    private volatile boolean connected = false;
    private String sessionToken;
    private BrokerCredentials credentials;

    private final ScheduledExecutorService tickSimulator = Executors.newScheduledThreadPool(1);
    private final Map<String, TickListener> tickListeners = new ConcurrentHashMap<>();
    private ScheduledFuture<?> tickTask;

    @Override
    public String getBrokerCode() {
        return "UPSTOX";
    }

    @Override
    public CompletableFuture<ConnectionResult> connect(BrokerCredentials credentials) {
        return CompletableFuture.supplyAsync(() -> {
            this.credentials = credentials;
            log.info("[UPSTOX] Connecting with apiKey={}", maskKey(credentials.apiKey()));

            // Upstox OAuth2 flow: POST /login/authorization/token
            // Stub: Simulate connection
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            this.connected = true;
            this.sessionToken = UUID.randomUUID().toString();

            log.info("[UPSTOX] Connected successfully");
            return ConnectionResult.success(sessionToken);
        });
    }

    @Override
    public void disconnect() {
        log.info("[UPSTOX] Disconnecting");
        connected = false;
        tickListeners.clear();
        if (tickTask != null) {
            tickTask.cancel(false);
        }
        tickSimulator.shutdown();
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
            log.info("[UPSTOX] Placing order: {} {} {} @ {}",
                     request.transactionType(), request.quantity(), request.symbol(), request.price());

            // Upstox API: POST /order/place
            String orderId = "UPSTOX-ORD-" + UUID.randomUUID().toString().substring(0, 8);
            return OrderResult.success(orderId);
        });
    }

    @Override
    public CompletableFuture<OrderResult> modifyOrder(String orderId, OrderModifyRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected) {
                return OrderResult.failure("Not connected", "NOT_CONNECTED");
            }
            log.info("[UPSTOX] Modifying order: {}", orderId);
            return OrderResult.success(orderId);
        });
    }

    @Override
    public CompletableFuture<OrderResult> cancelOrder(String orderId) {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected) {
                return OrderResult.failure("Not connected", "NOT_CONNECTED");
            }
            log.info("[UPSTOX] Cancelling order: {}", orderId);
            return OrderResult.success(orderId);
        });
    }

    @Override
    public CompletableFuture<OrderStatus> getOrderStatus(String orderId) {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected) throw new RuntimeException("Not connected");

            return new OrderStatus(
                orderId, "SBIN", "NSE", "BUY", "LIMIT", "CNC",
                10, 10, 0, new BigDecimal("850.00"), new BigDecimal("850.00"),
                null, "COMPLETE", "Order executed",
                String.valueOf(System.currentTimeMillis()), "NSE-" + orderId, null
            );
        });
    }

    @Override
    public CompletableFuture<List<OrderStatus>> getOpenOrders() {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected) return List.of();
            return List.of();
        });
    }

    @Override
    public CompletableFuture<List<Position>> getPositions() {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected) return List.of();
            return List.of();
        });
    }

    @Override
    public CompletableFuture<List<Holding>> getHoldings() {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected) return List.of();
            return List.of();
        });
    }

    @Override
    public CompletableFuture<AccountFunds> getFunds() {
        return CompletableFuture.supplyAsync(() -> new AccountFunds(
            new BigDecimal("450000"), new BigDecimal("45000"),
            new BigDecimal("495000"), BigDecimal.ZERO, new BigDecimal("450000")
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
        log.info("[UPSTOX] Subscribing to ticks: {}", symbols);
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

            log.info("[UPSTOX] Fetching historical candles: {} interval={} from={} to={}",
                     symbol, interval, fromEpoch, toEpoch);

            // Upstox API v2: GET /historical-candle/{instrument_key}/{interval}/{to_date}/{from_date}
            // URL: https://api.upstox.com/v2/historical-candle/{instrument_key}/{interval}/{to_date}/{from_date}
            // interval: 1minute, 5minute, 10minute, 15minute, 30minute, 60minute, 1day

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

            log.info("[UPSTOX] Fetched {} candles for {}", candles.size(), symbol);
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
                log.warn("[Upstox] getInstruments() not yet implemented - returning empty list");
                return List.of();
            } catch (Exception e) {
                log.error("[Upstox] Error fetching instruments: {}", e.getMessage(), e);
                return List.of();
            }
        });
    }
}
