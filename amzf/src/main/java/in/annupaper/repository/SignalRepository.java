package in.annupaper.repository;

import in.annupaper.domain.signal.Signal;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Signal entity with immutable audit trail.
 */
public interface SignalRepository {
    /**
     * Find all active signals.
     * Only returns records where deleted_at IS NULL.
     */
    List<Signal> findAll();

    /**
     * Find active signal by ID.
     * Only returns record where deleted_at IS NULL.
     */
    Optional<Signal> findById(String signalId);

    /**
     * Find active signals by symbol.
     */
    List<Signal> findBySymbol(String symbol);

    /**
     * Find active signals by symbol and status.
     * Used by SMS for price invalidation and supersede logic.
     */
    List<Signal> findBySymbolAndStatus(String symbol, String status);

    /**
     * Find active signals by status.
     */
    List<Signal> findByStatus(String status);

    /**
     * Find signals expiring soon (within time window).
     * Used by SMS expiry scheduler (AV-13).
     *
     * @param window Time window (e.g., Duration.ofMinutes(1))
     * @return Signals expiring within window
     */
    List<Signal> findExpiringSoon(java.time.Duration window);

    /**
     * Update signal status (SMS state transitions).
     *
     * @param signalId Signal ID
     * @param status New status (PUBLISHED | EXPIRED | CANCELLED | SUPERSEDED)
     */
    void updateStatus(String signalId, String status);

    /**
     * Insert new signal with version=1.
     */
    void insert(Signal signal);

    /**
     * Update signal using immutable pattern:
     * 1. Soft delete current version (set deleted_at)
     * 2. Insert new version (increment version)
     */
    void update(Signal signal);

    /**
     * Soft delete signal.
     * Sets deleted_at = NOW() on current version.
     */
    void delete(String signalId);

    /**
     * Mark all active signals as STALE (where no trades exist yet).
     * Used when global MTF config changes.
     * @return Number of signals marked as STALE
     */
    int markSignalsAsStale();

    /**
     * Mark all active signals for a specific symbol as STALE (where no trades exist yet).
     * Used when symbol-specific MTF config changes.
     * @param symbol Symbol to mark signals for
     * @return Number of signals marked as STALE
     */
    int markSignalsAsStaleForSymbol(String symbol);

    /**
     * Find all versions of a signal (including deleted).
     * For audit trail queries.
     */
    List<Signal> findAllVersions(String signalId);

    /**
     * Find specific version of a signal.
     * For point-in-time queries.
     */
    Optional<Signal> findByIdAndVersion(String signalId, int version);

    /**
     * Upsert signal (idempotent insert/update).
     * Uses (symbol, confluence_type, signal_day, effective_floor, effective_ceiling) as unique key.
     * If exists, updates status; if not, inserts.
     *
     * P0-B: Signal deduplication enforcement.
     * See: COMPREHENSIVE_IMPLEMENTATION_PLAN.md Phase 1, P0-B
     *
     * Note: signal_day is auto-generated from generated_at, no need to provide it.
     *
     * @param signal Signal to upsert
     * @return Persisted signal (with DB-generated fields if new)
     */
    Signal upsert(Signal signal);
}
