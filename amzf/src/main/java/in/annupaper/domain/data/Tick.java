package in.annupaper.domain.data;

import java.math.BigDecimal;

/**
 * Real-time market tick data.
 * Extracted from BrokerAdapter to domain layer.
 */
public record Tick(
    String symbol,
    BigDecimal lastPrice,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    long volume,
    BigDecimal bid,
    BigDecimal ask,
    int bidQty,
    int askQty,
    long timestamp
) {}
