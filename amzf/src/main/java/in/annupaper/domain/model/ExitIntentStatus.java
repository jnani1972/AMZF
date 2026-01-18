package in.annupaper.domain.model;

/**
 * Exit intent status lifecycle.
 *
 * Flow: PENDING → APPROVED/REJECTED → PLACED → FILLED/FAILED/CANCELLED
 */
public enum ExitIntentStatus {
    PENDING, // Created, awaiting qualification
    APPROVED, // Passed qualification, ready for execution
    REJECTED, // Failed qualification
    PLACED, // Order placed with broker
    FILLED, // Order filled, trade can close
    FAILED, // Order placement/execution failed
    CANCELLED // Manually cancelled or superseded
}
