package in.annupaper.domain.order;

import in.annupaper.domain.trade.Direction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Order response from broker after order placement or status query.
 */
public record OrderResponse(
    String brokerOrderId,
    String symbol,
    OrderStatus status,
    Direction direction,
    OrderType orderType,
    ProductType productType,
    int quantity,
    int filledQuantity,
    int pendingQuantity,
    BigDecimal orderPrice,
    BigDecimal avgFillPrice,
    Instant orderTime,
    Instant fillTime,
    String statusMessage,
    String tag,
    Map<String, Object> extendedData
) {}
