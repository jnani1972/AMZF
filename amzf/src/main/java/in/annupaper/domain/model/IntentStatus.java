package in.annupaper.domain.model;

/**
 * Trade intent status.
 */
public enum IntentStatus {
    PENDING, // Created, awaiting validation
    APPROVED, // Passed validation, ready for execution
    REJECTED, // Failed validation
    EXECUTED, // Order placed successfully
    FAILED // Execution failed
}
