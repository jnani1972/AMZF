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
 * Simple integration test for Prometheus /metrics endpoint.
 */
public class SimpleMetricsEndpointTest {

    private static final int TEST_PORT = 19091;
    private Undertow server;
    private PrometheusBrokerMetrics metrics;
    private HttpClient httpClient;

    @BeforeEach
    public void setUp() {
        // Initialize metrics with a new registry
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

        System.out.println("[TEST] Server started on http://localhost:" + TEST_PORT + "/metrics");
    }

    @AfterEach
    public void tearDown() {
        if (server != null) {
            server.stop();
            System.out.println("[TEST] Server stopped");
        }
    }

    @Test
    public void testMetricsEndpointReturns200() throws Exception {
        System.out.println("\n=== TEST: Metrics Endpoint Returns HTTP 200 ===");

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + TEST_PORT + "/metrics"))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        System.out.println("Response Status: " + response.statusCode());
        System.out.println("Content-Type: " + response.headers().firstValue("Content-Type").orElse("none"));

        assertEquals(200, response.statusCode(),
            "Metrics endpoint should return HTTP 200");

        String contentType = response.headers().firstValue("Content-Type").orElse("");
        assertTrue(contentType.contains("text/plain"),
            "Content-Type should be text/plain");

        assertFalse(response.body().isEmpty(),
            "Response body should not be empty");

        System.out.println("✅ Test passed: Endpoint is accessible\n");
    }

    @Test
    public void testMetricsContainPrometheusFormat() throws Exception {
        System.out.println("\n=== TEST: Metrics Contain Prometheus Format ===");

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + TEST_PORT + "/metrics"))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        String body = response.body();

        // Print first 20 lines
        String[] lines = body.split("\n");
        System.out.println("First 20 lines of metrics output:");
        for (int i = 0; i < Math.min(20, lines.length); i++) {
            System.out.println(lines[i]);
        }

        assertTrue(body.contains("# HELP"),
            "Should contain HELP declarations");
        assertTrue(body.contains("# TYPE"),
            "Should contain TYPE declarations");
        assertTrue(body.contains("broker_orders_total"),
            "Should contain broker_orders_total metric");
        assertTrue(body.contains("broker_health_status"),
            "Should contain broker_health_status metric");

        System.out.println("✅ Test passed: Prometheus format is correct\n");
    }

    @Test
    public void testMetricsRecording() throws Exception {
        System.out.println("\n=== TEST: Metrics Recording and Export ===");

        // Record some test metrics
        System.out.println("Recording test metrics...");
        metrics.recordOrderSuccess("UPSTOX", Duration.ofMillis(150));
        metrics.recordOrderSuccess("UPSTOX", Duration.ofMillis(200));
        metrics.recordOrderFailure("UPSTOX", "RATE_LIMIT", Duration.ofMillis(100));

        metrics.recordInstrumentLoad("UPSTOX", Duration.ofSeconds(5), 50000, true);
        metrics.updateHealthStatus("UPSTOX", true, Duration.ofHours(2));
        metrics.updateRateUtilization("UPSTOX", 0.75);

        System.out.println("Recorded:");
        System.out.println("  - 2 successful orders");
        System.out.println("  - 1 failed order");
        System.out.println("  - 50000 instruments loaded");
        System.out.println("  - Health status: healthy");
        System.out.println("  - Rate utilization: 75%");

        // Fetch metrics
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + TEST_PORT + "/metrics"))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        String body = response.body();

        // Print relevant metrics
        System.out.println("\nExported metrics (filtered):");
        String[] lines = body.split("\n");
        for (String line : lines) {
            if (line.contains("broker=\"UPSTOX\"") && !line.startsWith("#")) {
                System.out.println("  " + line);
            }
        }

        // Verify metrics are present
        assertTrue(body.contains("broker=\"UPSTOX\""),
            "Should contain UPSTOX broker label");
        assertTrue(body.contains("status=\"success\""),
            "Should contain success status");
        assertTrue(body.contains("status=\"failure\""),
            "Should contain failure status");

        System.out.println("✅ Test passed: Metrics are recorded and exported\n");
    }

    @Test
    public void testMultipleBrokers() throws Exception {
        System.out.println("\n=== TEST: Multiple Brokers ===");

        // Record metrics for different brokers
        System.out.println("Recording metrics for multiple brokers...");
        metrics.recordOrderSuccess("UPSTOX", Duration.ofMillis(100));
        metrics.recordOrderSuccess("ZERODHA", Duration.ofMillis(120));
        metrics.recordOrderSuccess("FYERS", Duration.ofMillis(80));

        metrics.updateHealthStatus("UPSTOX", true, Duration.ofHours(1));
        metrics.updateHealthStatus("ZERODHA", true, Duration.ofHours(2));
        metrics.updateHealthStatus("FYERS", false, Duration.ZERO);

        System.out.println("  - UPSTOX: healthy");
        System.out.println("  - ZERODHA: healthy");
        System.out.println("  - FYERS: down");

        // Fetch metrics
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + TEST_PORT + "/metrics"))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        String body = response.body();

        // Verify all brokers present
        assertTrue(body.contains("broker=\"UPSTOX\""),
            "Should contain UPSTOX");
        assertTrue(body.contains("broker=\"ZERODHA\""),
            "Should contain ZERODHA");
        assertTrue(body.contains("broker=\"FYERS\""),
            "Should contain FYERS");

        // Print broker health metrics
        System.out.println("\nBroker health metrics:");
        String[] lines = body.split("\n");
        for (String line : lines) {
            if (line.contains("broker_health_status") && !line.startsWith("#")) {
                System.out.println("  " + line);
            }
        }

        System.out.println("✅ Test passed: Multiple brokers tracked\n");
    }

    @Test
    public void testHistogramMetrics() throws Exception {
        System.out.println("\n=== TEST: Histogram Metrics ===");

        // Record latencies in different buckets
        System.out.println("Recording latencies...");
        metrics.recordOrderSuccess("UPSTOX", Duration.ofMillis(10));   // 0.01s bucket
        metrics.recordOrderSuccess("UPSTOX", Duration.ofMillis(50));   // 0.05s bucket
        metrics.recordOrderSuccess("UPSTOX", Duration.ofMillis(150));  // 0.2s bucket
        metrics.recordOrderSuccess("UPSTOX", Duration.ofMillis(500));  // 0.5s bucket

        System.out.println("  - 10ms latency");
        System.out.println("  - 50ms latency");
        System.out.println("  - 150ms latency");
        System.out.println("  - 500ms latency");

        // Fetch metrics
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + TEST_PORT + "/metrics"))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        String body = response.body();

        // Print histogram buckets
        System.out.println("\nHistogram buckets:");
        String[] lines = body.split("\n");
        for (String line : lines) {
            if (line.contains("broker_order_latency_seconds_bucket") && line.contains("UPSTOX")) {
                System.out.println("  " + line);
            }
        }

        // Verify histogram structure
        assertTrue(body.contains("broker_order_latency_seconds_bucket"),
            "Should contain histogram buckets");
        assertTrue(body.contains("broker_order_latency_seconds_sum"),
            "Should contain histogram sum");
        assertTrue(body.contains("broker_order_latency_seconds_count"),
            "Should contain histogram count");

        // Verify bucket labels (le = "less than or equal")
        assertTrue(body.contains("le=\"0.01\""), "Should have 0.01s bucket");
        assertTrue(body.contains("le=\"0.05\""), "Should have 0.05s bucket");
        assertTrue(body.contains("le=\"0.1\""), "Should have 0.1s bucket");
        assertTrue(body.contains("le=\"+Inf\""), "Should have +Inf bucket");

        System.out.println("✅ Test passed: Histogram metrics working\n");
    }
}
