package in.annupaper.domain.common;

/**
 * Event scope determines who receives the event.
 */
public enum EventScope {
    /**
     * GLOBAL: Broadcast to all connected users.
     * Examples: SIGNAL_GENERATED, MARKET_STATUS, SYSTEM_STATUS
     */
    GLOBAL,
    
    /**
     * USER: Sent only to a specific user (all their broker connections).
     * Examples: PORTFOLIO_UPDATED, CAPITAL_UPDATE, ALERT
     */
    USER,
    
    /**
     * USER_BROKER: Sent only to a specific user+broker combination.
     * Examples: ORDER_FILLED, TRADE_CREATED, VALIDATION_FAILED
     */
    USER_BROKER
}
