package in.annupaper.repository;

import in.annupaper.domain.trade.TradeIntent;
import in.annupaper.domain.trade.IntentStatus;
import java.util.List;
import java.util.Optional;

/**
 * Repository for TradeIntent entity with immutable audit trail.
 */
public interface TradeIntentRepository {
    /**
     * Find all active trade intents.
     * Only returns records where deleted_at IS NULL.
     */
    List<TradeIntent> findAll();

    /**
     * Find active trade intent by ID.
     * Only returns record where deleted_at IS NULL.
     */
    Optional<TradeIntent> findById(String intentId);

    /**
     * Find active trade intents by signal ID.
     */
    List<TradeIntent> findBySignalId(String signalId);

    /**
     * Find active trade intents by user ID.
     */
    List<TradeIntent> findByUserId(String userId);

    /**
     * Find active trade intents by user-broker ID.
     */
    List<TradeIntent> findByUserBrokerId(String userBrokerId);

    /**
     * Find active trade intents by status.
     */
    List<TradeIntent> findByStatus(IntentStatus status);

    /**
     * Insert new trade intent with version=1.
     */
    void insert(TradeIntent intent);

    /**
     * Update trade intent using immutable pattern:
     * 1. Soft delete current version (set deleted_at)
     * 2. Insert new version (increment version)
     */
    void update(TradeIntent intent);

    /**
     * Soft delete trade intent.
     * Sets deleted_at = NOW() on current version.
     */
    void delete(String intentId);

    /**
     * Find all versions of a trade intent (including deleted).
     * For audit trail queries.
     */
    List<TradeIntent> findAllVersions(String intentId);

    /**
     * Find specific version of a trade intent.
     * For point-in-time queries.
     */
    Optional<TradeIntent> findByIdAndVersion(String intentId, int version);
}
