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
 * Alpaca Markets API adapter (stub implementation).
 *
 * API Docs: https://alpaca.markets/docs/api-references/trading-api/
 */
public class AlpacaAdapter implements BrokerAdapter {
    private static final Logger log = LoggerFactory.getLogger(AlpacaAdapter.class);

    private volatile boolean connected = false;
    private String sessionToken;
    private BrokerCredentials credentials;

    private final ScheduledExecutorService tickSimulator = Executors.newScheduledThreadPool(1);
    private final Map<String, TickListener> tickListeners = new ConcurrentHashMap<>();
    private ScheduledFuture<?> tickTask;

    @Override
    public String getBrokerCode() {
        return "ALPACA";
    }

    @Override
    public CompletableFuture<ConnectionResult> connect(BrokerCredentials credentials) {
        return CompletableFuture.supplyAsync(() -> {
            this.credentials = credentials;
            log.info("[ALPACA] Connecting with apiKey={}", maskKey(credentials.apiKey()));

            // Alpaca uses API Key + Secret for authentication
            // No explicit login - just set headers for all requests
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            this.connected = true;
            this.sessionToken = UUID.randomUUID().toString();

            log.info("[ALPACA] Connected successfully");
            return ConnectionResult.success(sessionToken);
        });
    }

    @Override
    public void disconnect() {
        log.info("[ALPACA] Disconnecting");
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
            log.info("[ALPACA] Placing order: {} {} {} @ {}",
                     request.transactionType(), request.quantity(), request.symbol(), request.price());

            // Alpaca API: POST /v2/orders
            String orderId = "ALPACA-" + UUID.randomUUID().toString();
            return OrderResult.success(orderId);
        });
    }

    @Override
    public CompletableFuture<OrderResult> modifyOrder(String orderId, OrderModifyRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected) {
                return OrderResult.failure("Not connected", "NOT_CONNECTED");
            }
            log.info("[ALPACA] Modifying order: {}", orderId);
            // Alpaca API: PATCH /v2/orders/{order_id}
            return OrderResult.success(orderId);
        });
    }

    @Override
    public CompletableFuture<OrderResult> cancelOrder(String orderId) {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected) {
                return OrderResult.failure("Not connected", "NOT_CONNECTED");
            }
            log.info("[ALPACA] Cancelling order: {}", orderId);
            // Alpaca API: DELETE /v2/orders/{order_id}
            return OrderResult.success(orderId);
        });
    }

    @Override
    public CompletableFuture<OrderStatus> getOrderStatus(String orderId) {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected) throw new RuntimeException("Not connected");

            return new OrderStatus(
                orderId, "AAPL", "NASDAQ", "BUY", "LIMIT", "CASH",
                10, 10, 0, new BigDecimal("180.00"), new BigDecimal("180.00"),
                null, "COMPLETE", "Order filled",
                String.valueOf(System.currentTimeMillis()), "NASDAQ-" + orderId, null
            );
        });
    }

    @Override
    public CompletableFuture<List<OrderStatus>> getOpenOrders() {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected) return List.of();
            // Alpaca API: GET /v2/orders?status=open
            return List.of();
        });
    }

    @Override
    public CompletableFuture<List<Position>> getPositions() {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected) return List.of();
            // Alpaca API: GET /v2/positions
            return List.of();
        });
    }

    @Override
    public CompletableFuture<List<Holding>> getHoldings() {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected) return List.of();
            // Alpaca doesn't distinguish holdings - same as positions
            return List.of();
        });
    }

    @Override
    public CompletableFuture<AccountFunds> getFunds() {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected) throw new RuntimeException("Not connected");
            // Alpaca API: GET /v2/account
            return new AccountFunds(
                new BigDecimal("100000"), new BigDecimal("10000"),
                new BigDecimal("110000"), BigDecimal.ZERO, new BigDecimal("100000")
            );
        });
    }

    @Override
    public CompletableFuture<BigDecimal> getLtp(String symbol) {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected) throw new RuntimeException("Not connected");
            // Alpaca API: GET /v2/stocks/{symbol}/quotes/latest
            return new BigDecimal("180").add(
                new BigDecimal(Math.random() * 10).setScale(2, java.math.RoundingMode.HALF_UP)
            );
        });
    }

    @Override
    public void subscribeTicks(List<String> symbols, TickListener listener) {
        log.info("[ALPACA] Subscribing to ticks: {}", symbols);
        // Alpaca WebSocket: wss://stream.data.alpaca.markets/v2/iex
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

            log.info("[ALPACA] Fetching historical candles: {} interval={} from={} to={}",
                     symbol, interval, fromEpoch, toEpoch);

            // Alpaca Bars API: GET /v2/stocks/{symbol}/bars
            // URL: https://data.alpaca.markets/v2/stocks/{symbol}/bars
            // Params: timeframe (1Min, 5Min, 15Min, 30Min, 1Hour, 1Day), start (RFC3339), end (RFC3339)

            // Stub: Generate simulated candles
            List<HistoricalCandle> candles = new ArrayList<>();
            long currentTime = fromEpoch;
            long intervalSeconds = interval * 60L;
            BigDecimal currentClose = new BigDecimal("180");

            while (currentTime <= toEpoch) {
                BigDecimal open = currentClose;
                BigDecimal variation = new BigDecimal(Math.random() * 4 - 2).setScale(2, java.math.RoundingMode.HALF_UP);
                BigDecimal close = open.add(variation);
                BigDecimal high = open.max(close).add(new BigDecimal(Math.random() * 1).setScale(2, java.math.RoundingMode.HALF_UP));
                BigDecimal low = open.min(close).subtract(new BigDecimal(Math.random() * 1).setScale(2, java.math.RoundingMode.HALF_UP));
                long volume = (long) (Math.random() * 1000000);

                candles.add(new HistoricalCandle(currentTime, open, high, low, close, volume));
                currentClose = close;
                currentTime += intervalSeconds;
            }

            log.info("[ALPACA] Fetched {} candles for {}", candles.size(), symbol);
            return candles;
        });
    }

    @Override
    public void unsubscribeTicks(List<String> symbols) {
        log.info("[ALPACA] Unsubscribing from ticks: {}", symbols);
        for (String symbol : symbols) tickListeners.remove(symbol);
        if (tickListeners.isEmpty() && tickTask != null) {
            tickTask.cancel(false);
            tickTask = null;
        }
    }

    private void simulateTicks() {
        if (!connected) return;
        for (Map.Entry<String, TickListener> entry : tickListeners.entrySet()) {
            BigDecimal basePrice = new BigDecimal("180");
            BigDecimal lastPrice = basePrice.add(new BigDecimal(Math.random() * 4 - 2).setScale(2, java.math.RoundingMode.HALF_UP));
            Tick tick = new Tick(entry.getKey(), lastPrice, basePrice,
                basePrice.add(new BigDecimal("2")), basePrice.subtract(new BigDecimal("2")),
                basePrice.add(new BigDecimal("1")), (long)(Math.random() * 1000000),
                lastPrice.subtract(new BigDecimal("0.01")), lastPrice.add(new BigDecimal("0.01")),
                (int)(Math.random() * 5000), (int)(Math.random() * 5000), System.currentTimeMillis()
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
                log.warn("[Alpaca] getInstruments() not yet implemented - returning empty list");
                return List.of();
            } catch (Exception e) {
                log.error("[Alpaca] Error fetching instruments: {}", e.getMessage(), e);
                return List.of();
            }
        });
    }
}
