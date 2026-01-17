package in.annupaper.domain.monitoring;

/**
 * Alert severity levels for system monitoring
 */
public enum AlertLevel {
    /**
     * CRITICAL (P0) - Immediate action required
     * Examples: Broker session expired, stuck exit orders
     */
    CRITICAL,

    /**
     * HIGH (P1) - Action required soon
     * Examples: High error rate, session expiring soon
     */
    HIGH,

    /**
     * MEDIUM (P2) - Review and monitor
     * Examples: Stale data broker, slow order placement
     */
    MEDIUM,

    /**
     * LOW (P3) - Informational
     * Examples: Low activity day, trailing stop unused
     */
    LOW,

    /**
     * INFO - General information
     */
    INFO
}
