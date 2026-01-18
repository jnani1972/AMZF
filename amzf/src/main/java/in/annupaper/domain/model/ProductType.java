package in.annupaper.domain.model;

/**
 * Product type for order placement.
 *
 * Different brokers use different terminology, but these are the common types:
 * - CNC (Cash and Carry): Delivery trading
 * - MIS (Margin Intraday Square-off): Intraday with leverage
 * - NRML (Normal): F&O normal orders
 */
public enum ProductType {
    /**
     * Cash and Carry - Delivery trading (no leverage, held overnight).
     * - Zerodha: CNC
     * - FYERS: CNC
     * - Upstox: D (Delivery)
     * - Dhan: CNC
     */
    CNC,

    /**
     * Margin Intraday Square-off - Intraday with leverage (auto square-off at EOD).
     * - Zerodha: MIS
     * - FYERS: INTRADAY
     * - Upstox: I (Intraday)
     * - Dhan: INTRADAY
     */
    MIS,

    /**
     * Normal - F&O orders with overnight holding.
     * - Zerodha: NRML
     * - FYERS: MARGIN
     * - Upstox: M (Margin)
     * - Dhan: MARGIN
     */
    NRML,

    /**
     * Bracket Order - Advanced order type with target and stop-loss.
     * - Zerodha: BO (not available in all brokers)
     */
    BO,

    /**
     * Cover Order - Order with mandatory stop-loss.
     * - Zerodha: CO (not available in all brokers)
     */
    CO,

    /**
     * Margin Trade Funding - Delivery trading with broker-provided margin.
     * Interest is charged on the margin used. Positions can be held overnight.
     * - Upstox: MTF
     * - Dhan: MTF
     * - Zerodha: Not supported
     * - FYERS: Not supported
     */
    MTF
}
