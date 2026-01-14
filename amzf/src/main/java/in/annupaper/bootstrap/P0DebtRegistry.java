package in.annupaper.bootstrap;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * P0 Debt Registry - Tracks unresolved P0 blockers.
 *
 * When release.readiness=PROD_READY, ALL flags must be true.
 * If any flag is false, system REFUSES to start.
 *
 * This is the single source of truth for production readiness.
 *
 * V2 Enhancement: Hard gate enforcement (not warning).
 * See COMPREHENSIVE_IMPLEMENTATION_PLAN.md Phase 1, P0-A.
 */
public final class P0DebtRegistry {

    // ✅ Mechanical fix: Use boolean flags, not TODO comments
    // When a feature is implemented, set flag to true
    private static final Map<String, Boolean> P0_GATES = Map.of(
        "ORDER_EXECUTION_IMPLEMENTED", true,   // ✅ P0-E: OrderExecutionService with single-writer pattern
        "POSITION_TRACKING_LIVE", true,        // ✅ P0: ExitSignalService queries TradeRepository (not HashMap)
        "BROKER_RECONCILIATION_RUNNING", true,  // ✅ P0-C: PendingOrderReconciler instantiated and started in App.java
        "TICK_DEDUPLICATION_ACTIVE", true,     // ✅ P0-D: Two-window dedupe implemented in TickCandleBuilder
        "SIGNAL_DB_CONSTRAINTS_APPLIED", true,  // ✅ V007 migration verified on 2026-01-13
        "TRADE_IDEMPOTENCY_CONSTRAINTS", true,  // ✅ V007 migration verified on 2026-01-13
        "ASYNC_EVENT_WRITER_IF_PERSIST", true   // If persist.tickEvents=true, async writer enabled
    );

    /**
     * Check if all P0 gates are resolved.
     * @return true if all gates are true, false otherwise
     */
    public static boolean allGatesResolved() {
        return P0_GATES.values().stream().allMatch(v -> v);
    }

    /**
     * Get list of unresolved P0 gates.
     * @return Newline-separated list of unresolved gate names
     */
    public static String getUnresolvedGates() {
        return P0_GATES.entrySet().stream()
            .filter(e -> !e.getValue())
            .map(Map.Entry::getKey)
            .collect(Collectors.joining("\n  - "));
    }

    /**
     * Get all P0 gates with their status.
     * @return Map of gate name to resolved status
     */
    public static Map<String, Boolean> getAllGates() {
        return Map.copyOf(P0_GATES);
    }

    /**
     * Mark a P0 gate as resolved.
     *
     * NOTE: Currently, this requires updating the P0_GATES map in code.
     * Future enhancement: Make this mutable via config or admin API.
     *
     * @param gate Gate name to mark as resolved
     * @throws UnsupportedOperationException Always throws (not yet implemented)
     */
    public static void markResolved(String gate) {
        throw new UnsupportedOperationException(
            "Update P0DebtRegistry.P0_GATES map in code to mark gate resolved: " + gate + "\n" +
            "Set the boolean value to true in the map definition."
        );
    }

    private P0DebtRegistry() {
        // Utility class - no instantiation
    }
}
