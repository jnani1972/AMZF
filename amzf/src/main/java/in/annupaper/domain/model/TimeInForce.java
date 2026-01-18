package in.annupaper.domain.model;

/**
 * Time in force enum for orders.
 */
public enum TimeInForce {
    DAY, // Valid for the trading day
    IOC, // Immediate or Cancel
    GTC // Good Till Cancelled
}
