package in.annupaper.domain.common;

/**
 * Event types for multi-user, multi-broker Annu system.
 * Events are scoped: GLOBAL (all users), USER (specific user), USER_BROKER (specific user+broker).
 */
public enum EventType {
    // ═══════════════════════════════════════════════════════════════
    // GLOBAL EVENTS (broadcast to all users)
    // ═══════════════════════════════════════════════════════════════
    
    // Market data (from DATA broker)
    TICK,
    CANDLE,
    MARKET_STATUS,
    
    // Signals (generated from main DATA broker feed)
    SIGNAL_GENERATED,
    SIGNAL_EXPIRED,
    SIGNAL_CANCELLED,
    
    // System
    SYSTEM_STATUS,
    HEALTH_CHECK,
    BROKER_CONNECTED,
    BROKER_DISCONNECTED,
    
    // ═══════════════════════════════════════════════════════════════
    // USER-SCOPED EVENTS (sent only to specific user)
    // ═══════════════════════════════════════════════════════════════
    
    // Portfolio
    PORTFOLIO_UPDATED,
    CAPITAL_UPDATE,
    LOG_EXPOSURE_UPDATE,
    
    // User alerts
    ALERT,
    
    // ═══════════════════════════════════════════════════════════════
    // USER_BROKER-SCOPED EVENTS (sent to specific user+broker combo)
    // ═══════════════════════════════════════════════════════════════
    
    // Trade intents (entry validation results)
    INTENT_CREATED,
    INTENT_VALIDATED,
    INTENT_APPROVED,
    INTENT_REJECTED,
    INTENT_EXECUTED,
    INTENT_FAILED,

    // Exit intents (exit qualification results - V010)
    EXIT_INTENT_CREATED,
    EXIT_INTENT_QUALIFIED,
    EXIT_INTENT_APPROVED,
    EXIT_INTENT_REJECTED,
    EXIT_INTENT_COOLDOWN_REJECTED,
    EXIT_INTENT_PLACED,
    EXIT_INTENT_FILLED,
    EXIT_INTENT_FAILED,
    EXIT_INTENT_CANCELLED,
    
    // Orders
    ORDER_CREATED,
    ORDER_PLACED,
    ORDER_FILLED,
    ORDER_PARTIALLY_FILLED,
    ORDER_REJECTED,
    ORDER_CANCELLED,
    
    // Trades
    TRADE_CREATED,
    TRADE_UPDATED,
    TRADE_CLOSED,
    TRADE_CANCELLED,

    // Exit signals
    SIGNAL_EXIT,
    EXIT_SIGNAL_DETECTED,
    EXIT_SIGNAL_CONFIRMED,
    EXIT_SIGNAL_CANCELLED,
    EXIT_EXECUTED,
    EXIT_REJECTED,

    // Validation
    VALIDATION_PASSED,
    VALIDATION_FAILED,
    
    // ═══════════════════════════════════════════════════════════════
    // WEBSOCKET PROTOCOL
    // ═══════════════════════════════════════════════════════════════
    ACK,
    PONG,
    BATCH,
    ERROR,
    
    // ═══════════════════════════════════════════════════════════════
    // LEGACY (backward compatibility)
    // ═══════════════════════════════════════════════════════════════
    TRADEEVENT
}
