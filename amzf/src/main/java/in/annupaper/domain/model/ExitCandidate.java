package in.annupaper.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Exit candidate from exit strategy.
 */
public record ExitCandidate(
        String tradeId,
        String symbol,
        Direction direction,
        ExitReason exitReason,
        BigDecimal exitPrice,
        BigDecimal brickMovement,
        BigDecimal favorableMovement,
        BigDecimal highestSinceEntry,
        BigDecimal lowestSinceEntry,
        BigDecimal trailingStopPrice,
        boolean trailingActive,
        Instant timestamp) {
}
