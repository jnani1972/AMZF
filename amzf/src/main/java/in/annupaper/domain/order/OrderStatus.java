package in.annupaper.domain.order;

/**
 * Order status enum.
 */
public enum OrderStatus {
    PENDING,    // Order created but not yet placed
    PLACED,     // Order placed with broker
    PARTIAL,    // Partially filled
    FILLED,     // Completely filled
    REJECTED,   // Rejected by broker
    CANCELLED   // Cancelled by user or system
}
