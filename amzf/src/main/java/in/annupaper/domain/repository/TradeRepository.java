package in.annupaper.domain.repository;

import in.annupaper.domain.trade.Trade;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Trade entity with immutable audit trail.
 */
public interface TradeRepository {
    /**
     * Find all active trades.
     * Only returns records where deleted_at IS NULL.
     */
    List<Trade> findAll();

    /**
     * Find active trade by ID.
     * Only returns record where deleted_at IS NULL.
     */
    Optional<Trade> findById(String tradeId);

    /**
     * Find active trades by portfolio ID.
     */
    List<Trade> findByPortfolioId(String portfolioId);

    /**
     * Find active trades by user ID.
     */
    List<Trade> findByUserId(String userId);

    /**
     * Find active trades by symbol.
     */
    List<Trade> findBySymbol(String symbol);

    /**
     * Find active trades by user ID and symbol.
     * Used by TradeClassifier to count existing positions for NEWBUY/REBUY classification.
     */
    List<Trade> findByUserAndSymbol(String userId, String symbol);

    /**
     * Find active trades by status.
     */
    List<Trade> findByStatus(String status);

    /**
     * Find active trades by signal ID.
     */
    List<Trade> findBySignalId(String signalId);

    /**
     * Find open trades (status = 'OPEN').
     */
    List<Trade> findOpenTrades();

    /**
     * Find open trades for a specific user.
     */
    List<Trade> findOpenTradesByUserId(String userId);

    /**
     * Insert new trade with version=1.
     */
    void insert(Trade trade);

    /**
     * Update trade using immutable pattern:
     * 1. Soft delete current version (set deleted_at)
     * 2. Insert new version (increment version)
     */
    void update(Trade trade);

    /**
     * Soft delete trade.
     * Sets deleted_at = NOW() on current version.
     */
    void delete(String tradeId);

    /**
     * Find all versions of a trade (including deleted).
     * For audit trail queries.
     */
    List<Trade> findAllVersions(String tradeId);

    /**
     * Find specific version of a trade.
     * For point-in-time queries.
     */
    Optional<Trade> findByIdAndVersion(String tradeId, int version);

    /**
     * Upsert trade (idempotent insert/update).
     * Uses intent_id as unique key - if exists, updates; if not, inserts.
     *
     * P0-B: Idempotency enforcement.
     * See: COMPREHENSIVE_IMPLEMENTATION_PLAN.md Phase 1, P0-B
     *
     * @param trade Trade to upsert
     * @return Persisted trade (with DB-generated fields if new)
     */
    Trade upsert(Trade trade);

    /**
     * Find trade by intent ID (idempotency lookup).
     *
     * P0-B: Used by reconciler and rejection path.
     *
     * @param intentId Intent ID (unique per signal × user-broker)
     * @return Trade if exists, null otherwise
     */
    Trade findByIntentId(String intentId);

    /**
     * Find trade by broker order ID (broker idempotency).
     *
     * P0-C: Used by reconciliation loop and broker callbacks.
     *
     * @param brokerOrderId Broker's order ID
     * @return Trade if exists, null otherwise
     */
    Trade findByBrokerOrderId(String brokerOrderId);

    /**
     * Mark trade as REJECTED by intent ID (order placement rejection path).
     *
     * P0-E: Single-writer trade state enforcement.
     * Used when order placement fails - marks the CREATED trade as REJECTED.
     *
     * Pattern: Trade row created first with status=CREATED, then:
     * - If broker accepts: status → PENDING (via reconciler or callback)
     * - If broker rejects: status → REJECTED (via this method)
     *
     * This ensures:
     * - Trade row always exists before broker call
     * - Rejection path doesn't create duplicate trades
     * - Single writer: only initial insert creates trade
     *
     * @param intentId Intent ID (unique per signal × user-broker)
     * @param errorCode Error code from broker rejection
     * @param errorMessage Error message from broker rejection
     * @return true if trade found and marked rejected, false if not found
     */
    boolean markRejectedByIntentId(String intentId, String errorCode, String errorMessage);

    /**
     * Update trade with exit order ID when exit order is placed.
     * Updates exit_order_id and optionally transitions status to EXITING.
     *
     * @param tradeId Trade ID
     * @param exitOrderId Broker exit order ID
     * @param placedAt Timestamp when exit order was placed
     */
    void updateExitOrderPlaced(String tradeId, String exitOrderId, java.time.Instant placedAt);

    // ========================================================================
    // MONITORING METHODS
    // ========================================================================

    /**
     * Count open trades.
     *
     * @return Count of OPEN trades
     */
    long countOpenTrades();

    /**
     * Count trades closed today.
     *
     * @return Count of CLOSED trades with exit timestamp today
     */
    long countClosedTradesToday();

    /**
     * Get system health snapshot with trade metrics.
     * Includes open trades count, long/short positions, total exposure.
     *
     * @return Map with metrics: total_open_trades, long_positions, short_positions, total_exposure_value, avg_holding_hours
     */
    java.util.Map<String, Object> getTradeHealthMetrics();

    /**
     * Get daily performance metrics for today.
     * Includes trades closed, win rate, total P&L, best/worst trades.
     *
     * @return Map with daily performance metrics, empty if no trades closed today
     */
    java.util.Map<String, Object> getDailyPerformanceMetrics();
}
