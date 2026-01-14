package in.annupaper.domain.order;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Order response from broker after order placement or status query.
 */
public record OrderResponse(
    String brokerOrderId,
    String symbol,
    OrderStatus status,
    int filledQuantity,
    BigDecimal avgFillPrice,
    Instant orderTime,
    Instant fillTime
) {}
