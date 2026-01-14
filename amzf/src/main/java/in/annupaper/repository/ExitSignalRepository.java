package in.annupaper.repository;

import in.annupaper.domain.signal.ExitSignal;

import java.util.List;

/**
 * Repository for exit_signals table.
 *
 * OWNERSHIP:
 * Only SignalManagementService may write to this table.
 *
 * ENFORCEMENT:
 * - Unique constraint: (trade_id, exit_reason, episode_id)
 * - AV-2: generate_exit_episode() function for race-free episode generation
 */
public interface ExitSignalRepository {

    /**
     * Find exit signal by ID.
     *
     * @param exitSignalId Exit signal ID
     * @return ExitSignal or null
     */
    ExitSignal findById(String exitSignalId);

    /**
     * Find all exit signals for trade.
     *
     * Ordered by episode_id descending (latest first).
     *
     * @param tradeId Trade ID
     * @return List of exit signals (all episodes)
     */
    List<ExitSignal> findByTradeId(String tradeId);

    /**
     * Find exit signals for trade and reason.
     *
     * Ordered by episode_id descending.
     *
     * @param tradeId Trade ID
     * @param exitReason Exit reason
     * @return List of exit signals for this reason
     */
    List<ExitSignal> findByTradeAndReason(String tradeId, String exitReason);

    /**
     * Find latest exit signal for trade and reason.
     *
     * @param tradeId Trade ID
     * @param exitReason Exit reason
     * @return Latest exit signal or null
     */
    ExitSignal findLatestByTradeAndReason(String tradeId, String exitReason);

    /**
     * Generate next episode number for trade and reason (AV-2 fix).
     *
     * Calls DB function generate_exit_episode() with pessimistic lock.
     * Ensures race-free episode sequence generation.
     *
     * @param tradeId Trade ID
     * @param exitReason Exit reason
     * @return Next episode number (1, 2, 3, ...)
     */
    int generateEpisode(String tradeId, String exitReason);

    /**
     * Insert new exit signal.
     *
     * @param exitSignal Exit signal to insert
     */
    void insert(ExitSignal exitSignal);

    /**
     * Update exit signal status.
     *
     * @param exitSignalId Exit signal ID
     * @param status New status
     */
    void updateStatus(String exitSignalId, String status);

    /**
     * Cancel exit signal.
     *
     * Sets status to CANCELLED and records timestamp.
     *
     * @param exitSignalId Exit signal ID
     */
    void cancel(String exitSignalId);
}
