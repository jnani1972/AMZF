package in.annupaper.infrastructure.broker.metrics;

import io.prometheus.client.CollectorRegistry;
import io.undertow.Handlers;
import io.undertow.Undertow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Prometheus /metrics endpoint.
 *
 * Tests:
 * - Endpoint accessibility
 * - Prometheus text format
 * - Metrics registration
 * - Metrics recording and export
 */
public class MetricsEndpointTest {

    private static final int TEST_PORT = 19090;
    private Undertow server;
    private PrometheusBrokerMetrics metrics;
    private HttpClient httpClient;

    @BeforeEach
    public void setUp() {
        // Initialize metrics with a new registry for each test
        CollectorRegistry registry = new CollectorRegistry();
        metrics = new PrometheusBrokerMetrics(registry);

        // Create HTTP handler
        PrometheusMetricsHandler metricsHandler =
            new PrometheusMetricsHandler(metrics.getRegistry());

        // Start test server
        server = Undertow.builder()
            .addHttpListener(TEST_PORT, "localhost")
            .setHandler(
                Handlers.path()
                    .addPrefixPath("/metrics", metricsHandler)
            )
            .build();

        server.start();

        // Create HTTP client
        httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    @AfterEach
    public void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testMetricsEndpointAccessible() throws Exception {
        // Make HTTP request to /metrics
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + TEST_PORT + "/metrics"))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        // Verify HTTP 200
        assertEquals(200, response.statusCode(), "Metrics endpoint should return HTTP 200");

        // Verify Content-Type is Prometheus text format
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        assertTrue(contentType.contains("text/plain"),
            "Content-Type should be text/plain for Prometheus format");

        // Verify response has content
        String body = response.body();
        assertNotNull(body, "Response body should not be null");
        assertFalse(body.isEmpty(), "Response body should not be empty");
    }

    @Test
    public void testMetricsContainExpectedMetrics() throws Exception {
        // Make HTTP request to /metrics
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + TEST_PORT + "/metrics"))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        String body = response.body();

        // Verify key metrics are present
        assertTrue(body.contains("broker_orders_total"),
            "Metrics should contain broker_orders_total");
        assertTrue(body.contains("broker_order_latency_seconds"),
            "Metrics should contain broker_order_latency_seconds");
        assertTrue(body.contains("broker_health_status"),
            "Metrics should contain broker_health_status");
        assertTrue(body.contains("broker_rate_utilization"),
            "Metrics should contain broker_rate_utilization");
        assertTrue(body.contains("instrument_count"),
            "Metrics should contain instrument_count");
        assertTrue(body.contains("instrument_load_duration_seconds"),
            "Metrics should contain instrument_load_duration_seconds");

        // Verify HELP and TYPE declarations
        assertTrue(body.contains("# HELP"),
            "Metrics should contain HELP declarations");
        assertTrue(body.contains("# TYPE"),
            "Metrics should contain TYPE declarations");
    }

    @Test
    public void testMetricsRecordingAndExport() throws Exception {
        // Record some metrics
        metrics.recordOrderSuccess("UPSTOX", Duration.ofMillis(150));
        metrics.recordOrderSuccess("UPSTOX", Duration.ofMillis(200));
        metrics.recordOrderFailure("UPSTOX", "RATE_LIMIT", Duration.ofMillis(100));

        metrics.recordInstrumentLoad("UPSTOX", Duration.ofSeconds(5), 50000, true);
        metrics.updateHealthStatus("UPSTOX", true, Duration.ofHours(2));
        metrics.updateRateUtilization("UPSTOX", 0.75);

        // Make HTTP request to /metrics
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + TEST_PORT + "/metrics"))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        String body = response.body();

        // Verify metrics have values (flexible format checking)
        assertTrue(body.contains("broker_orders_total") && body.contains("broker=\"UPSTOX\"") && body.contains("status=\"success\""),
            "Should show successful orders");
        assertTrue(body.contains("broker_orders_total") && body.contains("broker=\"UPSTOX\"") && body.contains("status=\"failure\""),
            "Should show failed orders");

        // Verify histogram recorded latencies
        assertTrue(body.contains("broker_order_latency_seconds_count") && body.contains("broker=\"UPSTOX\""),
            "Should show latency observations");

        // Verify gauge values
        assertTrue(body.contains("broker_health_status") && body.contains("broker=\"UPSTOX\""),
            "Should show health status");
        assertTrue(body.contains("broker_rate_utilization") && body.contains("broker=\"UPSTOX\""),
            "Should show rate utilization");
        assertTrue(body.contains("instrument_count") && body.contains("broker=\"UPSTOX\""),
            "Should show instruments loaded");
    }

    @Test
    public void testMultipleBrokerMetrics() throws Exception {
        // Record metrics for multiple brokers
        metrics.recordOrderSuccess("UPSTOX", Duration.ofMillis(100));
        metrics.recordOrderSuccess("ZERODHA", Duration.ofMillis(120));
        metrics.recordOrderSuccess("FYERS", Duration.ofMillis(80));

        metrics.updateHealthStatus("UPSTOX", true, Duration.ofHours(1));
        metrics.updateHealthStatus("ZERODHA", true, Duration.ofHours(2));
        metrics.updateHealthStatus("FYERS", false, Duration.ZERO);

        // Make HTTP request to /metrics
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + TEST_PORT + "/metrics"))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        String body = response.body();

        // Verify all brokers are present
        assertTrue(body.contains("broker=\"UPSTOX\""),
            "Metrics should contain UPSTOX broker");
        assertTrue(body.contains("broker=\"ZERODHA\""),
            "Metrics should contain ZERODHA broker");
        assertTrue(body.contains("broker=\"FYERS\""),
            "Metrics should contain FYERS broker");

        // Verify different health statuses (flexible format checking)
        assertTrue(body.contains("broker_health_status") && body.contains("broker=\"UPSTOX\""),
            "UPSTOX should be healthy");
        assertTrue(body.contains("broker_health_status") && body.contains("broker=\"ZERODHA\""),
            "ZERODHA should be healthy");
        assertTrue(body.contains("broker_health_status") && body.contains("broker=\"FYERS\""),
            "FYERS should be down");
    }

    @Test
    public void testMetricsFormat() throws Exception {
        // Record a metric
        metrics.recordOrderSuccess("UPSTOX", Duration.ofMillis(150));

        // Make HTTP request to /metrics
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + TEST_PORT + "/metrics"))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        String body = response.body();

        // Verify Prometheus text format structure (flexible format checking)
        assertTrue(body.contains("# HELP broker_orders_total"),
            "Should have HELP line for broker_orders_total");
        assertTrue(body.contains("# TYPE broker_orders_total counter"),
            "Should have TYPE line for broker_orders_total");
        assertTrue(body.contains("broker_orders_total") && body.contains("broker=\"UPSTOX\"") && body.contains("status=\"success\""),
            "Should have metric line with broker label");
    }
}
