package in.annupaper.domain.trade;

import in.annupaper.domain.trade.ExitIntentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Exit intent: Execution qualification result for an exit signal.
 * Mirrors TradeIntent for entry, completing exit qualification symmetry.
 *
 * Lifecycle: PENDING → APPROVED/REJECTED → PLACED → FILLED/FAILED/CANCELLED
 *
 * One exit signal → one exit intent per (trade, user_broker, reason, episode)
 */
public record ExitIntent(
    String exitIntentId,
    String exitSignalId,
    String tradeId,
    String userBrokerId,

    // Exit context
    String exitReason,       // TARGET_HIT | STOP_LOSS | TRAILING_STOP | TIME_BASED | MANUAL
    int episodeId,

    // Qualification outcome
    ExitIntentStatus status,
    boolean validationPassed,
    List<String> validationErrors,

    // Execution details (if APPROVED)
    Integer calculatedQty,
    String orderType,        // MARKET | LIMIT
    BigDecimal limitPrice,
    String productType,      // CNC | MIS | NRML

    // Broker execution tracking
    String brokerOrderId,    // Set when PLACED
    Instant placedAt,
    Instant filledAt,
    Instant cancelledAt,

    // Failure tracking
    String errorCode,
    String errorMessage,
    int retryCount,

    // Standard audit
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt,
    int version
) {
    /**
     * Check if intent is pending qualification.
     */
    public boolean isPending() {
        return status == ExitIntentStatus.PENDING;
    }

    /**
     * Check if intent passed qualification.
     */
    public boolean isApproved() {
        return status == ExitIntentStatus.APPROVED;
    }

    /**
     * Check if intent failed qualification.
     */
    public boolean isRejected() {
        return status == ExitIntentStatus.REJECTED;
    }

    /**
     * Check if order has been placed with broker.
     */
    public boolean isPlaced() {
        return status == ExitIntentStatus.PLACED;
    }

    /**
     * Check if order has been filled.
     */
    public boolean isFilled() {
        return status == ExitIntentStatus.FILLED;
    }

    /**
     * Check if order placement/execution failed.
     */
    public boolean isFailed() {
        return status == ExitIntentStatus.FAILED;
    }

    /**
     * Check if intent was cancelled.
     */
    public boolean isCancelled() {
        return status == ExitIntentStatus.CANCELLED;
    }

    /**
     * Check if intent is in a terminal state.
     */
    public boolean isTerminal() {
        return status == ExitIntentStatus.FILLED
            || status == ExitIntentStatus.REJECTED
            || status == ExitIntentStatus.CANCELLED;
    }

    /**
     * Check if intent can be retried (FAILED state).
     */
    public boolean canRetry() {
        return status == ExitIntentStatus.FAILED;
    }
}
