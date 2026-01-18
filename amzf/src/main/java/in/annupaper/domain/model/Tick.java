package in.annupaper.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Real-time market tick data.
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
                Instant timestamp,
                String brokerCode) {
}
