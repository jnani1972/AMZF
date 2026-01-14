package in.annupaper.infrastructure.broker.metrics;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

/**
 * HTTP handler for Prometheus /metrics endpoint.
 *
 * Exposes metrics in Prometheus text format for scraping.
 *
 * Usage with Undertow:
 * <pre>
 * PrometheusBrokerMetrics metrics = new PrometheusBrokerMetrics();
 * PrometheusMetricsHandler handler = new PrometheusMetricsHandler(metrics.getRegistry());
 *
 * server.createContext("/metrics", handler);
 * </pre>
 *
 * Example output:
 * <pre>
 * # HELP broker_orders_total Total number of orders placed
 * # TYPE broker_orders_total counter
 * broker_orders_total{broker="UPSTOX",status="success"} 1234.0
 * broker_orders_total{broker="UPSTOX",status="failure"} 12.0
 *
 * # HELP broker_order_latency_seconds Order placement latency in seconds
 * # TYPE broker_order_latency_seconds histogram
 * broker_order_latency_seconds_bucket{broker="UPSTOX",le="0.01"} 0.0
 * broker_order_latency_seconds_bucket{broker="UPSTOX",le="0.05"} 234.0
 * broker_order_latency_seconds_bucket{broker="UPSTOX",le="0.1"} 1100.0
 * broker_order_latency_seconds_bucket{broker="UPSTOX",le="+Inf"} 1234.0
 * broker_order_latency_seconds_sum{broker="UPSTOX"} 148.2
 * broker_order_latency_seconds_count{broker="UPSTOX"} 1234.0
 * </pre>
 */
public class PrometheusMetricsHandler implements HttpHandler {
    private static final Logger log = LoggerFactory.getLogger(PrometheusMetricsHandler.class);

    private final CollectorRegistry registry;

    public PrometheusMetricsHandler(CollectorRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        try {
            // Set response headers
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, TextFormat.CONTENT_TYPE_004);

            // Write metrics in Prometheus text format
            Writer writer = new StringWriter();
            TextFormat.write004(writer, registry.metricFamilySamples());

            String metricsOutput = writer.toString();

            // Send response
            exchange.setStatusCode(200);
            exchange.getResponseSender().send(metricsOutput);

            log.debug("[PrometheusMetricsHandler] Served metrics ({} bytes)", metricsOutput.length());

        } catch (IOException e) {
            log.error("[PrometheusMetricsHandler] Failed to export metrics: {}", e.getMessage(), e);
            exchange.setStatusCode(500);
            exchange.getResponseSender().send("Error exporting metrics: " + e.getMessage());
        }
    }
}
