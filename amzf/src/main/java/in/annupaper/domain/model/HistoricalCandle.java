package in.annupaper.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Historical candle data (unified domain model).
 */
public record HistoricalCandle(
                String symbol,
                TimeframeType timeframe,
                Instant timestamp,
                BigDecimal open,
                BigDecimal high,
                BigDecimal low,
                BigDecimal close,
                long volume) {
}
