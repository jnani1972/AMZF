package in.annupaper.domain.model;

/**
 * Validation error codes for trade intent validation.
 */
public enum ValidationErrorCode {
    // Capital constraints
    INSUFFICIENT_CAPITAL("Insufficient capital available"),
    EXCEEDS_MAX_PER_TRADE("Trade value exceeds max per trade limit"),
    EXCEEDS_MAX_EXPOSURE("Would exceed maximum exposure limit"),

    // Portfolio constraints
    EXCEEDS_PORTFOLIO_LOG_LOSS("Would exceed portfolio log loss constraint"),
    EXCEEDS_SYMBOL_WEIGHT("Would exceed symbol weight limit"),
    MAX_SYMBOLS_REACHED("Maximum number of symbols reached"),
    MAX_OPEN_TRADES_REACHED("Maximum open trades reached"),

    // Position sizing rejection
    POSITION_SIZER_REJECTED("Rejected by constitutional position sizer"),

    // Trade constraints
    EXCEEDS_TRADE_LOG_LOSS("Trade log loss exceeds maximum allowed"),
    BELOW_MIN_QTY("Quantity below minimum"),
    BELOW_MIN_VALUE("Trade value below minimum"),
    BELOW_MIN_KELLY("Kelly criterion below minimum threshold"),
    BELOW_MIN_WIN_PROB("Win probability below minimum threshold"),

    // Symbol constraints
    SYMBOL_NOT_ALLOWED("Symbol not in allowed list"),
    SYMBOL_BLOCKED("Symbol is blocked"),
    SYMBOL_NOT_TRADEABLE("Symbol not tradeable on this broker"),

    // Product constraints
    PRODUCT_NOT_ALLOWED("Product type not allowed"),
    PRODUCT_NOT_SUPPORTED("Product type not supported by broker"),

    // Broker constraints
    BROKER_NOT_CONNECTED("Broker not connected"),
    BROKER_DISABLED("Broker is disabled"),
    BROKER_PAUSED("Broker is paused"),
    LOT_SIZE_MISMATCH("Quantity not a multiple of lot size"),

    // Risk constraints
    DAILY_LOSS_LIMIT_REACHED("Daily loss limit reached"),
    WEEKLY_LOSS_LIMIT_REACHED("Weekly loss limit reached"),
    IN_COOLDOWN_PERIOD("In cooldown period after loss"),

    // Confluence constraints
    NO_TRIPLE_CONFLUENCE("Triple confluence required but not present"),

    // Zone constraints
    ZONE_ALREADY_TRADED("Already have a trade in this zone"),
    ZONE_ABOVE_CURRENT("Zone is above current price"),

    // Market constraints
    MARKET_CLOSED("Market is closed"),
    OUTSIDE_TRADING_HOURS("Outside trading hours"),

    // System constraints
    PORTFOLIO_PAUSED("Portfolio is paused"),
    USER_SUSPENDED("User is suspended"),
    SYSTEM_PAUSED("System is paused");

    private final String message;

    ValidationErrorCode(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
