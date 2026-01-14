package in.annupaper.domain.signal;

import in.annupaper.domain.trade.Direction;
import in.annupaper.domain.trade.ExitReason;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Exit signal for closing a trade.
 */
public record ExitSignal(
    String exitSignalId,
    String tradeId,
    String signalId,
    String symbol,
    Direction direction,
    ExitReason exitReason,
    BigDecimal exitPrice,
    BigDecimal brickMovement,          // Movement since last exit (null if first)
    BigDecimal favorableMovement,       // Movement in favorable direction
    Instant timestamp
) {
    /**
     * Check if brick movement is sufficient.
     */
    public boolean hasMinimumBrickMovement(BigDecimal minBrick) {
        if (brickMovement == null) return true;  // First exit always allowed
        return brickMovement.abs().compareTo(minBrick) >= 0;
    }
}
