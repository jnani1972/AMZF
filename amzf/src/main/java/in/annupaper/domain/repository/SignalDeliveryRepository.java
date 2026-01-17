package in.annupaper.domain.repository;

import in.annupaper.service.signal.SignalDeliveryIndex;

import java.util.List;

/**
 * Repository for signal_deliveries table.
 *
 * OWNERSHIP:
 * Only SignalManagementService may write to this table.
 *
 * ENFORCEMENT:
 * - Unique constraint: (signal_id, user_broker_id)
 * - FK constraint: References signals(signal_id)
 * - AV-5: consume_delivery() function for atomic consumption
 */
public interface SignalDeliveryRepository {

    /**
     * Find delivery by ID.
     *
     * @param deliveryId Delivery ID
     * @return SignalDelivery or null
     */
    SignalDelivery findById(String deliveryId);

    /**
     * Find delivery by signal and user-broker.
     *
     * Used to check if delivery already exists (idempotency).
     *
     * @param signalId Signal ID
     * @param userBrokerId User-broker ID
     * @return SignalDelivery or null
     */
    SignalDelivery findBySignalAndUserBroker(String signalId, String userBrokerId);

    /**
     * Find all active deliveries for user.
     *
     * @param userId User ID
     * @param status Status filter (null = all active)
     * @return List of deliveries
     */
    List<SignalDelivery> findByUserId(String userId, String status);

    /**
     * Find all deliveries for signal (fan-out tracking).
     *
     * @param signalId Signal ID
     * @return List of deliveries
     */
    List<SignalDelivery> findBySignalId(String signalId);

    /**
     * Find all active deliveries for index rebuild.
     *
     * @return List of (deliveryId, signalId, userBrokerId) tuples
     */
    List<SignalDeliveryIndex.DeliveryIndexEntry> findAllActiveForIndex();

    /**
     * Find all pending deliveries (CREATED or DELIVERED status).
     *
     * Used by ExecutionOrchestrator to process pending deliveries.
     *
     * @return List of pending deliveries
     */
    List<SignalDelivery> findPendingDeliveries();

    /**
     * Insert new delivery.
     *
     * @param delivery Delivery to insert
     */
    void insert(SignalDelivery delivery);

    /**
     * Consume delivery atomically (AV-5 fix).
     *
     * Uses DB function consume_delivery() with optimistic lock.
     * Returns true if delivery was successfully marked CONSUMED.
     * Returns false if already consumed or not in DELIVERED state.
     *
     * @param deliveryId Delivery ID
     * @param intentId Intent ID to link
     * @return true if consumed successfully
     */
    boolean consumeDelivery(String deliveryId, String intentId);

    /**
     * Update delivery status.
     *
     * @param deliveryId Delivery ID
     * @param status New status
     */
    void updateStatus(String deliveryId, String status);

    /**
     * Expire all deliveries for signal.
     *
     * Called when signal expires.
     *
     * @param signalId Signal ID
     */
    void expireAllForSignal(String signalId);

    /**
     * Cancel all deliveries for signal.
     *
     * Called when signal is cancelled.
     *
     * @param signalId Signal ID
     */
    void cancelAllForSignal(String signalId);

    /**
     * Signal delivery record.
     */
    record SignalDelivery(
        String deliveryId,
        String signalId,
        String userBrokerId,
        String userId,
        String status,              // CREATED | DELIVERED | CONSUMED | EXPIRED | REJECTED
        String intentId,            // Set when consumed
        String rejectionReason,
        String userAction,          // NULL | SNOOZED | DISMISSED
        java.time.Instant createdAt,
        java.time.Instant deliveredAt,
        java.time.Instant consumedAt,
        java.time.Instant userActionAt,
        java.time.Instant updatedAt,
        java.time.Instant deletedAt,
        int version
    ) {}
}
