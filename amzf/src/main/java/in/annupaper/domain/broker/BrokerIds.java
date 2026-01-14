package in.annupaper.domain.broker;

/**
 * Centralized broker ID constants.
 *
 * IMPORTANT: These IDs must match the broker_id column in the brokers table.
 *
 * Single source of truth for broker identification across:
 * - OAuth flows
 * - Adapter factory lookups
 * - Token refresh checks
 * - Auto-login triggers
 *
 * DO NOT hardcode broker ID strings anywhere else in the codebase.
 */
public final class BrokerIds {

    /** Fyers Securities broker ID */
    public static final String FYERS = "B_FYERS";

    /** Dhan broker ID */
    public static final String DHAN = "B_DHAN";

    /** Upstox broker ID */
    public static final String UPSTOX = "B_UPSTOX";

    /** Zerodha broker ID */
    public static final String ZERODHA = "B_ZERODHA";

    private BrokerIds() {
        // Prevent instantiation
    }
}
