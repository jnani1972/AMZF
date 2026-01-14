package in.annupaper.service.signal;

import in.annupaper.domain.signal.ExitSignal;
import in.annupaper.domain.signal.Signal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * SignalManagementService (SMS) - Single Owner of Signal Lifecycle.
 *
 * ENFORCEMENT CONTRACT (CRITICAL):
 * - ONLY this service mutates signals, signal_deliveries, exit_signals
 * - All operations routed through coordinators (actor model)
 * - Entry signals: sequential per symbol (EntryCoordinator)
 * - Exit signals: sequential per trade (ExitCoordinator)
 * - All idempotency enforced at DB level (V009 migration)
 *
 * STATE MACHINES:
 *
 * Entry: DETECTED → PUBLISHED → {EXPIRED | CANCELLED | SUPERSEDED}
 * Delivery: CREATED → DELIVERED → {CONSUMED | EXPIRED | REJECTED}
 * Exit: DETECTED → CONFIRMED → PUBLISHED → {EXECUTED | CANCELLED}
 *
 * INPUTS (event-driven):
 * 1. onSignalDetected() - from strategy detection
 * 2. onPriceUpdate() - from tick stream (expiry/invalidation)
 * 3. onExitDetected() - from exit strategy
 * 4. expireStaleSignals() - from scheduler
 *
 * INVARIANTS (ungameable):
 * - INV-S1: No PUBLISHED without persistence
 * - INV-S2: No duplicate signals (DB-enforced unique index)
 * - INV-S3: No delivery without PUBLISHED signal
 * - INV-S4: No intent without delivery (FK constraint)
 * - INV-S5: No exit without trade OPEN
 * - INV-S6: Episode numbers DB-generated (race-free)
 *
 * See: SignalManagementService Final Architecture Document
 */
public interface SignalManagementService {

    // ═══════════════════════════════════════════════════════════════════════
    // ENTRY SIGNAL LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Process detected signal from strategy.
     *
     * Flow:
     * 1. Route to EntryCoordinator (by symbol)
     * 2. Check dedupe (DB constraint enforces)
     * 3. Persist signal (DETECTED → PUBLISHED)
     * 4. Create deliveries (one per eligible user-broker)
     * 5. Emit SIGNAL_PUBLISHED events
     *
     * Idempotency: DB unique constraint on dedupe key
     * Concurrency: Sequential per symbol via coordinator
     *
     * @param candidate Signal candidate from strategy detection
     */
    void onSignalDetected(SignalCandidate candidate);

    /**
     * Process price update for signal expiry/invalidation.
     *
     * Checks:
     * - Price broke effective_floor (invalidate)
     * - Price broke effective_ceiling (invalidate)
     * - Time expired (EOD)
     *
     * AV-8 FIX: Timestamp guard prevents out-of-order processing
     *
     * @param symbol Symbol that received tick
     * @param price Current LTP
     * @param timestamp Tick timestamp
     */
    void onPriceUpdate(String symbol, BigDecimal price, Instant timestamp);

    /**
     * Manual signal cancellation (admin/strategy override).
     *
     * Transitions:
     * - Signal: → CANCELLED
     * - All deliveries: → CANCELLED
     *
     * @param signalId Signal to cancel
     * @param reason Cancellation reason
     */
    void cancelSignal(String signalId, String reason);

    // ═══════════════════════════════════════════════════════════════════════
    // EXIT SIGNAL LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Process detected exit condition from exit strategy.
     *
     * Flow:
     * 1. Route to ExitCoordinator (by trade_id)
     * 2. Generate episode_id (DB function, AV-2 fix)
     * 3. Check re-arm cooldown (AV-6 fix)
     * 4. Confirm if required (brick movement)
     * 5. Publish to TradeManagementService
     *
     * Episode tracking: Enables re-arm after brick reset
     *
     * @param exitCandidate Exit condition detected
     */
    void onExitDetected(ExitCandidate exitCandidate);

    /**
     * Confirm exit signal after brick movement validation.
     *
     * Transitions: DETECTED → CONFIRMED → PUBLISHED
     *
     * Only PUBLISHED exits consumed by TMS (AV-4 fix)
     *
     * @param exitSignalId Exit signal to confirm
     */
    void confirmExitSignal(String exitSignalId);

    /**
     * Cancel exit signal (brick reversal, manual override).
     *
     * @param exitSignalId Exit signal to cancel
     * @param reason Cancellation reason
     */
    void cancelExitSignal(String exitSignalId, String reason);

    // ═══════════════════════════════════════════════════════════════════════
    // SCHEDULED OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Expire stale signals (scheduled every minute).
     *
     * AV-13 FIX: Routes expiry through EntryCoordinator (maintains single-writer)
     *
     * Expires signals if:
     * - now > expires_at (EOD)
     * - Price invalidated zone
     *
     * AV-11 FIX: Suppresses signal generation 60s before market close
     */
    void expireStaleSignals();

    /**
     * Rebuild delivery index from database (startup).
     *
     * Loads all active deliveries into in-memory index for fast lookup.
     */
    void rebuildDeliveryIndex();

    // ═══════════════════════════════════════════════════════════════════════
    // QUERY OPERATIONS (read-only)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Get active signals for symbol.
     *
     * @param symbol Symbol to query
     * @return Active signals (PUBLISHED, not expired)
     */
    List<Signal> getActiveSignals(String symbol);

    /**
     * Get signal deliveries for user.
     *
     * @param userId User ID
     * @param status Delivery status filter (null = all)
     * @return Signal deliveries
     */
    List<SignalDelivery> getUserDeliveries(String userId, String status);

    /**
     * Get exit signals for trade.
     *
     * @param tradeId Trade ID
     * @return Exit signals (all episodes)
     */
    List<ExitSignal> getTradeExitSignals(String tradeId);

    // ═══════════════════════════════════════════════════════════════════════
    // DATA STRUCTURES
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Signal candidate from strategy detection.
     * Immutable input to SMS.
     */
    record SignalCandidate(
        String symbol,
        String direction,           // BUY | SELL
        String signalType,          // ENTRY

        // MTF context
        Integer htfZone,
        Integer itfZone,
        Integer ltfZone,
        String confluenceType,      // NONE | SINGLE | DOUBLE | TRIPLE
        BigDecimal confluenceScore,

        // Probabilities
        BigDecimal pWin,
        BigDecimal pFill,
        BigDecimal kelly,

        // Reference prices
        BigDecimal refPrice,
        BigDecimal refBid,
        BigDecimal refAsk,
        BigDecimal entryLow,
        BigDecimal entryHigh,

        // Zone boundaries
        BigDecimal htfLow,
        BigDecimal htfHigh,
        BigDecimal itfLow,
        BigDecimal itfHigh,
        BigDecimal ltfLow,
        BigDecimal ltfHigh,
        BigDecimal effectiveFloor,
        BigDecimal effectiveCeiling,

        // Metadata
        BigDecimal confidence,
        String reason,
        List<String> tags,

        // Timing
        Instant timestamp,
        Instant expiresAt
    ) {}

    /**
     * Exit candidate from exit strategy.
     */
    record ExitCandidate(
        String tradeId,
        String symbol,
        String direction,
        String exitReason,          // TARGET_HIT | STOP_LOSS | TRAILING_STOP | ...
        BigDecimal exitPrice,
        BigDecimal brickMovement,
        BigDecimal favorableMovement,
        BigDecimal highestSinceEntry,
        BigDecimal lowestSinceEntry,
        BigDecimal trailingStopPrice,
        boolean trailingActive,
        Instant timestamp
    ) {}

    /**
     * Signal delivery to user-broker.
     */
    record SignalDelivery(
        String deliveryId,
        String signalId,
        String userBrokerId,
        String userId,
        String status,              // CREATED | DELIVERED | CONSUMED | EXPIRED | REJECTED
        String intentId,
        Instant createdAt,
        Instant deliveredAt,
        Instant consumedAt
    ) {}
}
