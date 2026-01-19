package in.annupaper.infrastructure.broker.adapters;

import in.annupaper.domain.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Zerodha Kite Connect adapter (stub implementation).
 * 
 * Production implementation would use:
 * - KiteConnect Java SDK: https://github.com/zerodha/javakiteconnect
 * - KiteTicker for WebSocket streaming
 * 
 * API Docs: https://kite.trade/docs/connect/v3/
 */
public class ZerodhaAdapter implements BrokerAdapter {
    private static final Logger log = LoggerFactory.getLogger(ZerodhaAdapter.class);

    private volatile boolean connected = false;
    private String sessionToken;
    private BrokerCredentials credentials;

    private final Map<String, TickListener> tickListeners = new ConcurrentHashMap<>();
    private final ScheduledExecutorService tickSimulator = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> tickTask;

    // Simulated order book
    private final Map<String, BrokerOrderStatus> orders = new ConcurrentHashMap<>();

    @Override
    public String getBrokerCode() {
        return "ZERODHA";
    }

    @Override
    public CompletableFuture<ConnectionResult> connect(BrokerCredentials credentials) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("[ZERODHA] Connecting with apiKey={}", maskKey(credentials.apiKey()));

            this.credentials = credentials;

            // In production: Use KiteConnect to generate session
            // KiteConnect kite = new KiteConnect(apiKey);
            // User user = kite.generateSession(requestToken, apiSecret);

            // Stub: Simulate connection
            try {
                Thread.sleep(100); // Simulate network latency
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Validate credentials (stub)
            if (credentials.apiKey() == null || credentials.apiKey().isEmpty()) {
                return ConnectionResult.ofFailure("Invalid API key", "INVALID_API_KEY");
            }

            this.sessionToken = "zerodha_session_" + UUID.randomUUID().toString().substring(0, 8);
            this.connected = true;

            log.info("[ZERODHA] Connected successfully, session={}", sessionToken);
            return ConnectionResult.ofSuccess(sessionToken);
        });
    }

    @Override
    public void disconnect() {
        log.info("[ZERODHA] Disconnecting...");
        connected = false;
        sessionToken = null;

        if (tickTask != null) {
            tickTask.cancel(false);
        }
        tickListeners.clear();
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public CompletableFuture<OrderResult> placeOrder(BrokerOrderRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected) {
                return OrderResult.ofFailure("Not connected", "NOT_CONNECTED");
            }

            log.info("[ZERODHA] Placing order: {} {} {} qty={} @ {}",
                    request.transactionType(), request.symbol(), request.orderType(),
                    request.quantity(), request.price());

            // KiteConnect: kite.placeOrder("variety", request)
            String orderId = "ZR" + System.currentTimeMillis();

            // Create status
            String status = "MARKET".equals(request.orderType()) ? "COMPLETE" : "OPEN";
            int filled = "MARKET".equals(request.orderType()) ? request.quantity() : 0;
            BigDecimal avgPrice = "MARKET".equals(request.orderType())
                    ? (request.price() != null ? request.price() : new BigDecimal("150.00"))
                    : BigDecimal.ZERO;

            BrokerOrderStatus orderStatus = new BrokerOrderStatus(
                    orderId,
                    request.symbol(),
                    request.exchange(),
                    request.transactionType(),
                    request.orderType(),
                    request.productType(),
                    request.quantity(),
                    filled,
                    request.quantity() - filled,
                    request.price(),
                    avgPrice,
                    request.triggerPrice(),
                    status,
                    "Order accepted",
                    Instant.now().toString(),
                    "EXCH-" + orderId,
                    request.tag());

            orders.put(orderId, orderStatus);

            log.info("[ZERODHA] Order placed: {} status={}", orderId, status);
            return OrderResult.ofSuccess(orderId);
        });
    }

    @Override
    public CompletableFuture<OrderResult> modifyOrder(String orderId, OrderModifyRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected)
                return OrderResult.ofFailure("Not connected", "NOT_CONNECTED");

            BrokerOrderStatus existing = orders.get(orderId);
            if (existing == null)
                return OrderResult.ofFailure("Order not found", "ORDER_NOT_FOUND");

            log.info("[ZERODHA] Modifying order: {}", orderId);

            // In production: kite.modifyOrder(orderId, request)
            BrokerOrderStatus modified = new BrokerOrderStatus(
                    orderId,
                    existing.symbol(),
                    existing.exchange(),
                    existing.transactionType(),
                    request.orderType() != null ? request.orderType() : existing.orderType(),
                    existing.productType(),
                    request.quantity() > 0 ? request.quantity() : existing.quantity(),
                    existing.filledQuantity(),
                    (request.quantity() > 0 ? request.quantity() : existing.quantity()) - existing.filledQuantity(),
                    request.price() != null ? request.price() : existing.price(),
                    existing.averagePrice(),
                    request.triggerPrice() != null ? request.triggerPrice() : existing.triggerPrice(),
                    existing.status(),
                    "Order modified",
                    Instant.now().toString(),
                    existing.exchangeOrderId(),
                    existing.tag());

            orders.put(orderId, modified);
            return OrderResult.ofSuccess(orderId);
        });
    }

    @Override
    public CompletableFuture<OrderResult> cancelOrder(String orderId) {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected)
                return OrderResult.ofFailure("Not connected", "NOT_CONNECTED");

            BrokerOrderStatus existing = orders.get(orderId);
            if (existing == null)
                return OrderResult.ofFailure("Order not found", "ORDER_NOT_FOUND");

            log.info("[ZERODHA] Cancelling order: {}", orderId);

            // In production: kite.cancelOrder(orderId)
            BrokerOrderStatus cancelled = new BrokerOrderStatus(
                    orderId,
                    existing.symbol(),
                    existing.exchange(),
                    existing.transactionType(),
                    existing.orderType(),
                    existing.productType(),
                    existing.quantity(),
                    existing.filledQuantity(),
                    0,
                    existing.price(),
                    existing.averagePrice(),
                    existing.triggerPrice(),
                    "CANCELLED",
                    "Order cancelled by user",
                    Instant.now().toString(),
                    existing.exchangeOrderId(),
                    existing.tag());

            orders.put(orderId, cancelled);
            return OrderResult.ofSuccess(orderId);
        });
    }

    @Override
    public CompletableFuture<BrokerOrderStatus> getOrderStatus(String orderId) {
        return CompletableFuture.supplyAsync(() -> orders.get(orderId));
    }

    @Override
    public CompletableFuture<List<BrokerOrderStatus>> getOpenOrders() {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected)
                return List.of();
            // Filter OPEN or TRIGGER PENDING orders
            return orders.values().stream()
                    .filter(o -> "OPEN".equals(o.status()) || "TRIGGER PENDING".equals(o.status()))
                    .toList();
        });
    }

    @Override
    public CompletableFuture<List<Position>> getPositions() {
        log.debug("[ZERODHA] Fetching positions");
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<List<Holding>> getHoldings() {
        log.debug("[ZERODHA] Fetching holdings");
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<AccountFunds> getFunds() {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected)
                throw new RuntimeException("Not connected");
            // kite.getMargins()
            return new AccountFunds(
                    new BigDecimal("500000"), new BigDecimal("50000"),
                    new BigDecimal("550000"), BigDecimal.ZERO, new BigDecimal("500000"));
        });
    }

    @Override
    public CompletableFuture<BigDecimal> getLtp(String symbol) {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected)
                throw new RuntimeException("Not connected");
            // kite.getLtp(new String[]{"NSE:RELIANCE"})
            return new BigDecimal("2500").add(
                    new BigDecimal(Math.random() * 50).setScale(2, java.math.RoundingMode.HALF_UP));
        });
    }

    @Override
    public void subscribeTicks(List<String> symbols, TickListener listener) {
        log.info("[ZERODHA] Subscribing to ticks (simulated): {}", symbols);
        for (String symbol : symbols)
            tickListeners.put(symbol, listener);

        if (tickTask == null || tickTask.isCancelled()) {
            tickTask = tickSimulator.scheduleAtFixedRate(this::simulateTicks, 0, 1, TimeUnit.SECONDS);
        }
    }

    @Override
    public void unsubscribeTicks(List<String> symbols) {
        log.info("[ZERODHA] Unsubscribing from ticks: {}", symbols);
        for (String symbol : symbols)
            tickListeners.remove(symbol);

        if (tickListeners.isEmpty() && tickTask != null) {
            tickTask.cancel(false);
            tickTask = null;
        }
    }

    private void simulateTicks() {
        if (!connected)
            return;

        for (Map.Entry<String, TickListener> entry : tickListeners.entrySet()) {
            BigDecimal basePrice = new BigDecimal("2500");
            BigDecimal lastPrice = basePrice.add(
                    new BigDecimal(Math.random() * 20 - 10).setScale(2, java.math.RoundingMode.HALF_UP));

            Tick tick = new Tick(
                    entry.getKey(),
                    lastPrice,
                    basePrice,
                    basePrice.add(new BigDecimal("30")), // high
                    basePrice.subtract(new BigDecimal("15")), // low
                    basePrice.subtract(new BigDecimal("5")), // close
                    (long) (Math.random() * 1000000), // volume
                    lastPrice.subtract(new BigDecimal("0.50")), // bid
                    lastPrice.add(new BigDecimal("0.50")), // ask
                    (int) (Math.random() * 2000), // bidQty
                    (int) (Math.random() * 2000), // askQty
                    Instant.now(),
                    "ZERODHA");

            try {
                entry.getValue().onTick(tick);
            } catch (Exception e) {
                log.warn("[ZERODHA] Error in tick listener: {}", e.getMessage());
            }
        }
    }

    private String maskKey(String key) {
        if (key == null || key.length() < 8)
            return "***";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    @Override
    public CompletableFuture<List<HistoricalCandle>> getHistoricalCandles(
            String symbol,
            TimeframeType timeframe,
            long fromEpoch,
            long toEpoch) {
        return CompletableFuture.supplyAsync(() -> {
            if (!connected) {
                throw new RuntimeException("Not connected");
            }

            int interval = timeframe.getInterval();
            log.info("[ZERODHA] Fetching historical candles: {} timeframe={} (interval={}) from={} to={}",
                    symbol, timeframe, interval, fromEpoch, toEpoch);

            // KiteConnect: kite.getHistoricalData(instrumentToken, from, to, interval)

            // Stub: Generate simulated candles
            List<HistoricalCandle> candles = new ArrayList<>();
            long currentTime = fromEpoch;
            long intervalSeconds = interval * 60L;
            BigDecimal currentClose = new BigDecimal("2500");

            while (currentTime <= toEpoch) {
                BigDecimal open = currentClose;
                BigDecimal variation = new BigDecimal(Math.random() * 40 - 20).setScale(2,
                        java.math.RoundingMode.HALF_UP);
                BigDecimal close = open.add(variation);
                BigDecimal high = open.max(close)
                        .add(new BigDecimal(Math.random() * 10).setScale(2, java.math.RoundingMode.HALF_UP));
                BigDecimal low = open.min(close)
                        .subtract(new BigDecimal(Math.random() * 10).setScale(2, java.math.RoundingMode.HALF_UP));
                long volume = (long) (Math.random() * 500000);

                candles.add(new HistoricalCandle(
                        symbol,
                        timeframe,
                        Instant.ofEpochSecond(currentTime),
                        open, high, low, close, volume));
                currentClose = close;
                currentTime += intervalSeconds;
            }

            log.info("[ZERODHA] Fetched {} candles for {}", candles.size(), symbol);
            return candles;
        });
    }

    @Override
    public CompletableFuture<List<BrokerInstrument>> getInstruments() {
        return CompletableFuture.supplyAsync(() -> {
            log.info("[ZERODHA] Downloading master instruments list...");

            String INSTRUMENTS_URL = "https://api.kite.trade/instruments";
            List<BrokerInstrument> instruments = new ArrayList<>();

            try {
                // 1. Download CSV
                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(INSTRUMENTS_URL))
                        .GET()
                        .build();

                java.net.http.HttpResponse<java.io.InputStream> response = client.send(request,
                        java.net.http.HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() != 200) {
                    throw new RuntimeException("HTTP " + response.statusCode());
                }

                // 2. Parse CSV
                // Format:
                // instrument_token,exchange_token,tradingsymbol,name,last_price,expiry,strike,tick_size,lot_size,instrument_type,segment,exchange
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(response.body(), java.nio.charset.StandardCharsets.UTF_8))) {

                    String header = reader.readLine(); // Skip header
                    String line;

                    while ((line = reader.readLine()) != null) {
                        try {
                            // Simple CSV split (Zerodha CSV usually strictly formatted, no quotes escaping
                            // needed typically)
                            String[] parts = line.split(",");
                            if (parts.length < 12)
                                continue;

                            // 0:instrument_token, 1:exchange_token, 2:tradingsymbol, 3:name, 4:last_price,
                            // 5:expiry, 6:strike, 7:tick_size, 8:lot_size, 9:instrument_type, 10:segment,
                            // 11:exchange

                            String token = parts[0];
                            String tradingSymbol = parts[2];
                            String name = parts[3].replace("\"", ""); // Clean quotes if name has them
                            String segment = parts[10];
                            String exchange = parts[11];
                            String type = parts[9];

                            // Filter: Only NSE Equity and NFO Futures/Options for now to save memory?
                            // Or take all. Let's take NSE and NFO.
                            if (!exchange.equals("NSE") && !exchange.equals("NFO") && !exchange.equals("BSE")) {
                                continue;
                            }

                            int lotSize = Integer.parseInt(parts[8]);
                            BigDecimal tickSize = new BigDecimal(parts[7]);

                            instruments.add(new BrokerInstrument(
                                    exchange,
                                    tradingSymbol,
                                    name,
                                    type,
                                    segment,
                                    token,
                                    lotSize,
                                    tickSize,
                                    parts[5].isEmpty() ? null : parts[5], // Expiry
                                    parts[6].isEmpty() || parts[6].equals("0") ? null : new BigDecimal(parts[6]), // Strike
                                    null // Option type not explicit in CSV usually, derived from type or symbol
                            ));

                        } catch (Exception e) {
                            // Skip malformed lines
                        }
                    }
                }

                log.info("[ZERODHA] Downloaded {} instruments", instruments.size());
                return instruments;

            } catch (Exception e) {
                log.error("[ZERODHA] Failed to download instruments: {}", e.getMessage(), e);
                return List.of();
            }
        });
    }
}
