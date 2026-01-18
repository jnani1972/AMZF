package in.annupaper.domain.model;

/**
 * Order type enum.
 */
public enum OrderType {
    MARKET, // Market order (execute at current price)
    LIMIT, // Limit order (execute at specified price or better)
    STOP_LOSS // Stop loss order (trigger at stop price)
}
