package in.annupaper.domain.broker;

/**
 * Role of a broker in the system.
 */
public enum BrokerRole {
    /**
     * DATA: Market data source (admin-owned).
     * - Provides ticks, candles, LTP
     * - Used for indicator calculations
     * - Used for signal generation
     * - Only ONE data broker should be active system-wide
     */
    DATA,
    
    /**
     * EXEC: Trade execution broker (per-user).
     * - Used for order placement
     * - Used for order tracking
     * - Used for position management
     * - Multiple EXEC brokers per user allowed
     */
    EXEC
}
