package in.annupaper.infrastructure.broker.metrics;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prometheus implementation of BrokerMetrics interface.
 *
 * Exposes broker metrics in Prometheus format for scraping.
 * Metrics are exposed at /metrics endpoint.
 *
 * Key Metrics:
 * - broker_orders_total{broker, status} - Order success/failure counts
 * - broker_order_latency_seconds{broker, quantile} - Order latency distribution
 * - broker_rate_limit_hits_total{broker, limit_type} - Rate limit hits
 * - broker_health_status{broker} - Current health (1=healthy, 0=down)
 * - broker_uptime_seconds{broker} - Time since last status change
 * - instrument_load_duration_seconds{broker} - Instrument load time
 * - instrument_count{broker} - Number of instruments loaded
 *
 * Usage:
 * <pre>
 * PrometheusBrokerMetrics metrics = new PrometheusBrokerMetrics();
 * orderBroker.setMetrics(metrics);
 *
 * // Expose at /metrics endpoint
 * server.createContext("/metrics", new MetricsHandler(metrics.getRegistry()));
 * </pre>
 */
public class PrometheusBrokerMetrics implements BrokerMetrics {
    private static final Logger log = LoggerFactory.getLogger(PrometheusBrokerMetrics.class);

    private final CollectorRegistry registry;

    // Order metrics
    private final Counter orderCounter;
    private final Histogram orderLatency;
    private final Counter orderModificationCounter;
    private final Counter orderCancellationCounter;

    // Rate limit metrics
    private final Counter rateLimitHitCounter;
    private final Gauge currentRate;
    private final Gauge rateUtilization;

    // Retry metrics
    private final Counter retryCounter;
    private final Histogram retryAttempts;

    // Authentication metrics
    private final Counter authCounter;
    private final Histogram authLatency;

    // Connection metrics
    private final Counter connectionEventCounter;
    private final Gauge connectionStatus;

    // WebSocket metrics
    private final Counter websocketMessageCounter;

    // Health metrics
    private final Gauge healthStatus;
    private final Gauge uptimeSeconds;

    // Instrument loader metrics
    private final Histogram instrumentLoadDuration;
    private final Gauge instrumentCount;

    // In-memory state for aggregations
    private final Map<String, MetricsState> stateMap = new ConcurrentHashMap<>();

    public PrometheusBrokerMetrics() {
        this(CollectorRegistry.defaultRegistry);
    }

    public PrometheusBrokerMetrics(CollectorRegistry registry) {
        this.registry = registry;

        // Initialize all metrics
        this.orderCounter = Counter.build()
            .name("broker_orders_total")
            .help("Total number of orders placed")
            .labelNames("broker", "status")
            .register(registry);

        this.orderLatency = Histogram.build()
            .name("broker_order_latency_seconds")
            .help("Order placement latency in seconds")
            .labelNames("broker")
            .buckets(0.01, 0.05, 0.1, 0.2, 0.5, 1.0, 2.0, 5.0)
            .register(registry);

        this.orderModificationCounter = Counter.build()
            .name("broker_order_modifications_total")
            .help("Total number of order modifications")
            .labelNames("broker", "status")
            .register(registry);

        this.orderCancellationCounter = Counter.build()
            .name("broker_order_cancellations_total")
            .help("Total number of order cancellations")
            .labelNames("broker", "status")
            .register(registry);

        this.rateLimitHitCounter = Counter.build()
            .name("broker_rate_limit_hits_total")
            .help("Total number of rate limit hits")
            .labelNames("broker", "limit_type")
            .register(registry);

        this.currentRate = Gauge.build()
            .name("broker_current_rate")
            .help("Current request rate per second")
            .labelNames("broker")
            .register(registry);

        this.rateUtilization = Gauge.build()
            .name("broker_rate_utilization")
            .help("Rate limit utilization (0-1)")
            .labelNames("broker")
            .register(registry);

        this.retryCounter = Counter.build()
            .name("broker_retries_total")
            .help("Total number of retry attempts")
            .labelNames("broker", "reason")
            .register(registry);

        this.retryAttempts = Histogram.build()
            .name("broker_retry_attempts")
            .help("Number of retry attempts per operation")
            .labelNames("broker")
            .buckets(1, 2, 3, 5, 10)
            .register(registry);

        this.authCounter = Counter.build()
            .name("broker_authentications_total")
            .help("Total number of authentication attempts")
            .labelNames("broker", "status")
            .register(registry);

        this.authLatency = Histogram.build()
            .name("broker_authentication_latency_seconds")
            .help("Authentication latency in seconds")
            .labelNames("broker")
            .buckets(0.1, 0.5, 1.0, 2.0, 5.0, 10.0)
            .register(registry);

        this.connectionEventCounter = Counter.build()
            .name("broker_connection_events_total")
            .help("Total number of connection events")
            .labelNames("broker", "event")
            .register(registry);

        this.connectionStatus = Gauge.build()
            .name("broker_connection_status")
            .help("Current connection status (1=connected, 0=disconnected)")
            .labelNames("broker")
            .register(registry);

        this.websocketMessageCounter = Counter.build()
            .name("broker_websocket_messages_total")
            .help("Total number of WebSocket messages")
            .labelNames("broker", "message_type")
            .register(registry);

        this.healthStatus = Gauge.build()
            .name("broker_health_status")
            .help("Current health status (1=healthy, 0=unhealthy)")
            .labelNames("broker")
            .register(registry);

        this.uptimeSeconds = Gauge.build()
            .name("broker_uptime_seconds")
            .help("Time since last status change in seconds")
            .labelNames("broker")
            .register(registry);

        this.instrumentLoadDuration = Histogram.build()
            .name("instrument_load_duration_seconds")
            .help("Instrument loading duration in seconds")
            .labelNames("broker")
            .buckets(1, 5, 10, 30, 60, 120, 300)
            .register(registry);

        this.instrumentCount = Gauge.build()
            .name("instrument_count")
            .help("Number of instruments loaded")
            .labelNames("broker")
            .register(registry);

        log.info("[PrometheusBrokerMetrics] Initialized with {} collectors",
            registry.metricFamilySamples().asIterator().hasNext() ? "multiple" : "0");
    }

    @Override
    public void recordOrderSuccess(String brokerCode, Duration latency) {
        orderCounter.labels(brokerCode, "success").inc();
        orderLatency.labels(brokerCode).observe(latency.toMillis() / 1000.0);

        // Update state
        getState(brokerCode).recordOrderSuccess();
    }

    @Override
    public void recordOrderFailure(String brokerCode, String errorType, Duration latency) {
        orderCounter.labels(brokerCode, "failure").inc();
        orderLatency.labels(brokerCode).observe(latency.toMillis() / 1000.0);

        // Update state
        getState(brokerCode).recordOrderFailure(errorType);
    }

    @Override
    public void recordOrderModification(String brokerCode, boolean success, Duration latency) {
        orderModificationCounter.labels(brokerCode, success ? "success" : "failure").inc();
        orderLatency.labels(brokerCode).observe(latency.toMillis() / 1000.0);
    }

    @Override
    public void recordOrderCancellation(String brokerCode, boolean success, Duration latency) {
        orderCancellationCounter.labels(brokerCode, success ? "success" : "failure").inc();
        orderLatency.labels(brokerCode).observe(latency.toMillis() / 1000.0);
    }

    @Override
    public void recordRateLimitHit(String brokerCode, int currentRateValue, RateLimitType limitType) {
        rateLimitHitCounter.labels(brokerCode, limitType.name()).inc();
        currentRate.labels(brokerCode).set(currentRateValue);

        // Update state
        getState(brokerCode).recordRateLimitHit(limitType);
    }

    @Override
    public void recordRetry(String brokerCode, int attemptNumber, String retryReason) {
        retryCounter.labels(brokerCode, retryReason).inc();
        retryAttempts.labels(brokerCode).observe(attemptNumber);

        // Update state
        getState(brokerCode).recordRetry(attemptNumber);
    }

    @Override
    public void recordAuthentication(String brokerCode, boolean success, Duration latency) {
        authCounter.labels(brokerCode, success ? "success" : "failure").inc();
        authLatency.labels(brokerCode).observe(latency.toMillis() / 1000.0);

        // Update state
        getState(brokerCode).recordAuthentication(success);
    }

    @Override
    public void recordConnectionEvent(String brokerCode, ConnectionEvent event) {
        connectionEventCounter.labels(brokerCode, event.name()).inc();

        // Update connection status gauge
        if (event == ConnectionEvent.CONNECTED) {
            connectionStatus.labels(brokerCode).set(1);
        } else if (event == ConnectionEvent.DISCONNECTED || event == ConnectionEvent.ERROR) {
            connectionStatus.labels(brokerCode).set(0);
        }

        // Update state
        getState(brokerCode).recordConnectionEvent(event);
    }

    @Override
    public void recordWebSocketMessage(String brokerCode, String messageType) {
        websocketMessageCounter.labels(brokerCode, messageType).inc();

        // Update state
        getState(brokerCode).recordWebSocketMessage();
    }

    @Override
    public void recordOrderUpdate(String brokerCode, String status, Duration latency) {
        // Order updates are essentially WebSocket messages
        websocketMessageCounter.labels(brokerCode, "order_update").inc();

        // Update state
        getState(brokerCode).recordOrderUpdate(status);
    }

    /**
     * Record instrument load metrics.
     * Called by AsyncInstrumentLoader.
     */
    public void recordInstrumentLoad(String brokerCode, Duration loadTime, int count, boolean success) {
        instrumentLoadDuration.labels(brokerCode).observe(loadTime.toMillis() / 1000.0);
        instrumentCount.labels(brokerCode).set(count);

        log.debug("[PrometheusBrokerMetrics] Recorded instrument load for {}: {} instruments in {}s",
            brokerCode, count, loadTime.toSeconds());
    }

    /**
     * Update health status.
     * Called by BrokerFailoverManager.
     */
    public void updateHealthStatus(String brokerCode, boolean isHealthy, Duration uptime) {
        healthStatus.labels(brokerCode).set(isHealthy ? 1 : 0);
        uptimeSeconds.labels(brokerCode).set(uptime.toSeconds());
    }

    /**
     * Update rate utilization.
     * Called by OrderThrottleManager.
     */
    public void updateRateUtilization(String brokerCode, double utilization) {
        rateUtilization.labels(brokerCode).set(utilization);
    }

    @Override
    public Map<String, Object> getMetrics(String brokerCode) {
        MetricsState state = stateMap.get(brokerCode);
        if (state == null) {
            return new HashMap<>();
        }

        return state.toMap();
    }

    @Override
    public Map<String, Map<String, Object>> getAllMetrics() {
        Map<String, Map<String, Object>> allMetrics = new HashMap<>();
        for (Map.Entry<String, MetricsState> entry : stateMap.entrySet()) {
            allMetrics.put(entry.getKey(), entry.getValue().toMap());
        }
        return allMetrics;
    }

    @Override
    public void reset(String brokerCode) {
        stateMap.remove(brokerCode);
        log.info("[PrometheusBrokerMetrics] Reset metrics for {}", brokerCode);
    }

    @Override
    public void resetAll() {
        stateMap.clear();
        log.info("[PrometheusBrokerMetrics] Reset all metrics");
    }

    /**
     * Get Prometheus CollectorRegistry for /metrics endpoint.
     */
    public CollectorRegistry getRegistry() {
        return registry;
    }

    /**
     * Get or create metrics state for a broker.
     */
    private MetricsState getState(String brokerCode) {
        return stateMap.computeIfAbsent(brokerCode, k -> new MetricsState(brokerCode));
    }

    /**
     * In-memory state for aggregated metrics.
     */
    private static class MetricsState {
        private final String brokerCode;
        private long totalOrders = 0;
        private long successfulOrders = 0;
        private long failedOrders = 0;
        private long rateLimitHits = 0;
        private long authenticationFailures = 0;
        private long connectionDrops = 0;
        private long totalRetries = 0;
        private long webSocketMessages = 0;

        public MetricsState(String brokerCode) {
            this.brokerCode = brokerCode;
        }

        public void recordOrderSuccess() {
            totalOrders++;
            successfulOrders++;
        }

        public void recordOrderFailure(String errorType) {
            totalOrders++;
            failedOrders++;
        }

        public void recordRateLimitHit(RateLimitType limitType) {
            rateLimitHits++;
        }

        public void recordRetry(int attemptNumber) {
            totalRetries++;
        }

        public void recordAuthentication(boolean success) {
            if (!success) {
                authenticationFailures++;
            }
        }

        public void recordConnectionEvent(ConnectionEvent event) {
            if (event == ConnectionEvent.DISCONNECTED || event == ConnectionEvent.ERROR) {
                connectionDrops++;
            }
        }

        public void recordWebSocketMessage() {
            webSocketMessages++;
        }

        public void recordOrderUpdate(String status) {
            // Tracked as WebSocket messages
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("brokerCode", brokerCode);
            map.put("totalOrders", totalOrders);
            map.put("successfulOrders", successfulOrders);
            map.put("failedOrders", failedOrders);
            map.put("successRate", totalOrders > 0 ? (double) successfulOrders / totalOrders : 0.0);
            map.put("rateLimitHits", rateLimitHits);
            map.put("authenticationFailures", authenticationFailures);
            map.put("connectionDrops", connectionDrops);
            map.put("totalRetries", totalRetries);
            map.put("avgRetriesPerOrder", totalOrders > 0 ? (double) totalRetries / totalOrders : 0.0);
            map.put("webSocketMessages", webSocketMessages);
            return map;
        }
    }
}
