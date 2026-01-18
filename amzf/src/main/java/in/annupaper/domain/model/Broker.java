package in.annupaper.domain.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

/**
 * Broker definition (not per-user).
 * Immutable audit trail: version-based, deleted_at for soft delete.
 */
public record Broker(
        String brokerId,
        String brokerCode, // ZERODHA, FYERS, DHAN, ANGEL, etc.
        String brokerName,
        String adapterClass, // Java adapter class name
        JsonNode config,
        List<String> supportedExchanges, // NSE, BSE, NFO, MCX
        List<String> supportedProducts, // CNC, MIS, NRML
        JsonNode lotSizes, // symbol -> lot size
        JsonNode marginRules,
        JsonNode rateLimits,
        String status, // ACTIVE | DISABLED
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt,
        int version) {
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public boolean supportsExchange(String exchange) {
        return supportedExchanges != null && supportedExchanges.contains(exchange);
    }

    public boolean supportsProduct(String product) {
        return supportedProducts != null && supportedProducts.contains(product);
    }
}
