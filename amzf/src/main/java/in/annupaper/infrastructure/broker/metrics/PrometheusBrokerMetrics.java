package in.annupaper.infrastructure.broker.metrics;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prometheus-based implementation of BrokerMetrics.
 * Records broker operation metrics using Prometheus collectors.
 */
public class PrometheusBrokerMetrics implements BrokerMetrics {

    private final Counter orderSuccessCounter;
    private final Counter orderFailureCounter;
    private final Counter timeoutCounter;
    private final Counter authFailureCounter;
    private final Counter rateLimitCounter;
    private final Histogram orderLatency;

    // Local counters for testing
    private final ConcurrentHashMap<String, Double> successCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> failureCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> timeoutCounts = new ConcurrentHashMap<>();

    public PrometheusBrokerMetrics(CollectorRegistry registry) {
        this.orderSuccessCounter = Counter.build()
            .name("broker_order_success_total")
            .help("Total successful broker orders")
            .labelNames("broker")
            .register(registry);

        this.orderFailureCounter = Counter.build()
            .name("broker_order_failure_total")
            .help("Total failed broker orders")
            .labelNames("broker", "error_type")
            .register(registry);

        this.timeoutCounter = Counter.build()
            .name("broker_timeout_total")
            .help("Total broker timeouts")
            .labelNames("broker")
            .register(registry);

        this.authFailureCounter = Counter.build()
            .name("broker_auth_failure_total")
            .help("Total broker authentication failures")
            .labelNames("broker")
            .register(registry);

        this.rateLimitCounter = Counter.build()
            .name("broker_rate_limit_total")
            .help("Total broker rate limit breaches")
            .labelNames("broker")
            .register(registry);

        this.orderLatency = Histogram.build()
            .name("broker_order_latency_seconds")
            .help("Broker order latency in seconds")
            .labelNames("broker")
            .register(registry);
    }

    @Override
    public void recordOrderSuccess(String brokerCode, Duration latency) {
        orderSuccessCounter.labels(brokerCode).inc();
        orderLatency.labels(brokerCode).observe(latency.toMillis() / 1000.0);
        successCounts.merge(brokerCode, 1.0, Double::sum);
    }

    @Override
    public void recordOrderFailure(String brokerCode, String errorType, Duration latency) {
        orderFailureCounter.labels(brokerCode, errorType).inc();
        orderLatency.labels(brokerCode).observe(latency.toMillis() / 1000.0);
        failureCounts.merge(brokerCode, 1.0, Double::sum);
    }

    @Override
    public void recordTimeout(String brokerCode) {
        timeoutCounter.labels(brokerCode).inc();
        timeoutCounts.merge(brokerCode, 1.0, Double::sum);
    }

    @Override
    public void recordAuthFailure(String brokerCode) {
        authFailureCounter.labels(brokerCode).inc();
    }

    @Override
    public void recordRateLimitBreach(String brokerCode) {
        rateLimitCounter.labels(brokerCode).inc();
    }

    @Override
    public double getSuccessCount(String brokerCode) {
        return successCounts.getOrDefault(brokerCode, 0.0);
    }

    @Override
    public double getFailureCount(String brokerCode) {
        return failureCounts.getOrDefault(brokerCode, 0.0);
    }

    @Override
    public double getTimeoutCount(String brokerCode) {
        return timeoutCounts.getOrDefault(brokerCode, 0.0);
    }
}
