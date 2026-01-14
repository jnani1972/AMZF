package in.annupaper.repository;

import in.annupaper.domain.trade.ExitIntent;
import in.annupaper.domain.trade.ExitIntentStatus;

import java.util.List;
import java.util.Optional;

/**
 * Repository for exit_intents table.
 *
 * Exit intents track execution qualification and order outcomes for exits.
 * Completes entry/exit symmetry: entry has TradeIntent, exit has ExitIntent.
 *
 * Lifecycle: PENDING → APPROVED/REJECTED → PLACED → FILLED/FAILED/CANCELLED
 */
public interface ExitIntentRepository {

    /**
     * Find exit intent by ID.
     *
     * @param exitIntentId Exit intent ID
     * @return ExitIntent or empty
     */
    Optional<ExitIntent> findById(String exitIntentId);

    /**
     * Find all exit intents for an exit signal.
     *
     * @param exitSignalId Exit signal ID
     * @return List of exit intents
     */
    List<ExitIntent> findByExitSignalId(String exitSignalId);

    /**
     * Find all exit intents for a trade.
     *
     * @param tradeId Trade ID
     * @return List of exit intents
     */
    List<ExitIntent> findByTradeId(String tradeId);

    /**
     * Find exit intents by status.
     *
     * @param status Exit intent status
     * @return List of exit intents
     */
    List<ExitIntent> findByStatus(ExitIntentStatus status);

    /**
     * Find pending exit intents (PENDING or APPROVED).
     * Used by execution service to find intents ready for processing.
     *
     * @return List of pending/approved exit intents
     */
    List<ExitIntent> findPendingIntents();

    /**
     * Find failed exit intents (for retry queue).
     *
     * @return List of failed exit intents
     */
    List<ExitIntent> findFailedIntents();

    /**
     * Find exit intent by broker order ID (for reconciliation).
     *
     * @param brokerOrderId Broker order ID
     * @return ExitIntent or empty
     */
    Optional<ExitIntent> findByBrokerOrderId(String brokerOrderId);

    /**
     * Insert new exit intent.
     *
     * @param intent Exit intent to insert
     */
    void insert(ExitIntent intent);

    /**
     * Update exit intent status.
     *
     * @param exitIntentId Exit intent ID
     * @param status New status
     */
    void updateStatus(String exitIntentId, ExitIntentStatus status);

    /**
     * Update exit intent status with error details (for FAILED status).
     *
     * @param exitIntentId Exit intent ID
     * @param status New status as string
     * @param errorCode Error code
     * @param errorMessage Error message
     */
    void updateStatus(String exitIntentId, String status, String errorCode, String errorMessage);

    /**
     * Update broker order ID after successful placement.
     *
     * @param exitIntentId Exit intent ID
     * @param brokerOrderId Real broker order ID
     */
    void updateBrokerOrderId(String exitIntentId, String brokerOrderId);

    /**
     * Place exit order atomically (APPROVED → PLACED).
     * Uses DB function for atomic transition with optimistic lock.
     *
     * @param exitIntentId Exit intent ID
     * @param brokerOrderId Broker order ID
     * @return true if transition successful
     */
    boolean placeExitOrder(String exitIntentId, String brokerOrderId);

    /**
     * Mark exit intent as filled.
     *
     * @param exitIntentId Exit intent ID
     */
    void markFilled(String exitIntentId);

    /**
     * Mark exit intent as failed.
     *
     * @param exitIntentId Exit intent ID
     * @param errorCode Error code
     * @param errorMessage Error message
     */
    void markFailed(String exitIntentId, String errorCode, String errorMessage);

    /**
     * Mark exit intent as cancelled.
     *
     * @param exitIntentId Exit intent ID
     */
    void markCancelled(String exitIntentId);

    /**
     * Increment retry count for failed intent.
     *
     * @param exitIntentId Exit intent ID
     * @return New retry count
     */
    int incrementRetryCount(String exitIntentId);
}
