package in.annupaper.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Broker order update data structure.
 */
public record BrokerOrderUpdate(
        String orderId, // Broker's order ID
        String clientOrderId, // Our intent ID (idempotency key)
        String status, // Order status (PENDING, COMPLETE, REJECTED, etc.)
        BigDecimal filledQty, // Quantity filled
        BigDecimal avgPrice, // Average fill price
        BigDecimal pendingQty, // Quantity still pending
        String rejectReason, // Rejection reason (if rejected)
        Instant updateTimestamp, // Broker update timestamp
        String brokerFillId // Broker's fill ID (for trade_fills table)
) {
}
