package in.annupaper.domain.monitoring;

/**
 * Alert severity levels for monitoring system.
 */
public enum AlertLevel {
    CRITICAL,  // Immediate action required (P0)
    HIGH,      // Urgent attention needed (P1)
    MEDIUM,    // Should be addressed soon (P2)
    LOW,       // Minor issue (P3)
    INFO       // Informational only
}
