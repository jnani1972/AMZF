package in.annupaper.domain.order;

import in.annupaper.domain.trade.Direction;

import java.math.BigDecimal;

/**
 * Order request for placing orders with brokers.
 * Used by OrderBroker implementations.
 */
public record OrderRequest(
    String symbol,
    Direction direction,
    int quantity,
    OrderType orderType,
    BigDecimal limitPrice,
    BigDecimal stopPrice,
    TimeInForce timeInForce
) {
    public OrderRequest {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }
        if (direction == null) {
            throw new IllegalArgumentException("Direction cannot be null");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (orderType == null) {
            throw new IllegalArgumentException("Order type cannot be null");
        }
        if (timeInForce == null) {
            throw new IllegalArgumentException("Time in force cannot be null");
        }
    }
}
