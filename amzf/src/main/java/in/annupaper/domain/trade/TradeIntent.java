package in.annupaper.domain.trade;

import in.annupaper.domain.trade.IntentStatus;
import in.annupaper.domain.common.ValidationErrorCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Trade intent: Per (user, broker) validation result for a signal.
 * One signal → multiple trade intents (fan-out).
 */
public record TradeIntent(
    String intentId,
    String signalId,
    String userId,
    String brokerId,
    String userBrokerId,
    
    // Validation result
    boolean validationPassed,
    List<ValidationError> validationErrors,
    
    // Calculated values (if passed)
    Integer calculatedQty,
    BigDecimal calculatedValue,
    String orderType,          // MARKET | LIMIT
    BigDecimal limitPrice,
    String productType,        // CNC | MIS | NRML
    
    // Risk calculations
    BigDecimal logImpact,
    BigDecimal portfolioExposureAfter,
    
    // Execution tracking
    IntentStatus status,
    String orderId,            // Broker order ID if executed
    String tradeId,            // Our trade ID if created
    
    // Timestamps
    Instant createdAt,
    Instant validatedAt,
    Instant executedAt,

    // Error tracking
    String errorCode,
    String errorMessage,

    // Immutable audit trail
    Instant deletedAt,
    int version
) {
    /**
     * Validation error detail.
     */
    public record ValidationError(
        ValidationErrorCode code,
        String message,
        String field,
        Object expected,
        Object actual
    ) {
        public static ValidationError of(ValidationErrorCode code) {
            return new ValidationError(code, code.getMessage(), null, null, null);
        }
        
        public static ValidationError of(ValidationErrorCode code, String field, Object expected, Object actual) {
            return new ValidationError(code, code.getMessage(), field, expected, actual);
        }
    }
    
    public boolean isPending() {
        return status == IntentStatus.PENDING;
    }
    
    public boolean isApproved() {
        return status == IntentStatus.APPROVED;
    }
    
    public boolean isRejected() {
        return status == IntentStatus.REJECTED;
    }
    
    public boolean isExecuted() {
        return status == IntentStatus.EXECUTED;
    }
}
