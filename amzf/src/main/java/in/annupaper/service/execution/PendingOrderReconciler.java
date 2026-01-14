package in.annupaper.service.execution;

import in.annupaper.broker.BrokerAdapter;
import in.annupaper.broker.BrokerAdapterFactory;
import in.annupaper.domain.trade.Trade;
import in.annupaper.domain.broker.UserBroker;
import in.annupaper.repository.TradeRepository;
import in.annupaper.repository.UserBrokerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Reconciles pending orders with broker reality (CORRECTED).
 *
 * V2 #7: 3-Layer Idempotency (Layer 3 - Reconciliation)
 * See: COMPREHENSIVE_IMPLEMENTATION_PLAN.md Phase 1, P0-C
 *
 * Corrections applied:
 * - Use field comparison (not reference comparison) to detect changes
 * - Use last_broker_update_at (not created_at) for timeout
 * - Add rate limiting to prevent broker API hammering
 */
public final class PendingOrderReconciler {
    private static final Logger log = LoggerFactory.getLogger(PendingOrderReconciler.class);

    private final TradeRepository tradeRepository;
    private final UserBrokerRepository userBrokerRepository;
    private final BrokerAdapterFactory brokerFactory;
    private final ScheduledExecutorService scheduler;

    private final Duration reconcileInterval;
    private final Duration pendingTimeout;
    private final int maxConcurrentBrokerCalls;  // ✅ NEW: Rate limiting

    // ✅ NEW: Rate limiter (prevent API hammering)
    private final Semaphore brokerCallSemaphore;

    // Metrics
    private long lastReconcileCount = 0;
    private long totalReconciled = 0;
    private long totalUpdates = 0;
    private long totalTimeouts = 0;
    private long totalRateLimited = 0;
    private Instant lastReconcileTime = Instant.now();

    /**
     * Create reconciler with default settings.
     *
     * @param tradeRepository Trade repository
     * @param userBrokerRepository User-broker repository
     * @param brokerFactory Broker adapter factory
     */
    public PendingOrderReconciler(
            TradeRepository tradeRepository,
            UserBrokerRepository userBrokerRepository,
            BrokerAdapterFactory brokerFactory) {
        this(tradeRepository, userBrokerRepository, brokerFactory,
            Duration.ofSeconds(30),  // Reconcile every 30 seconds
            Duration.ofMinutes(10),  // Timeout after 10 minutes
            5);                      // Max 5 concurrent broker calls
    }

    /**
     * Create reconciler with custom settings.
     *
     * @param tradeRepository Trade repository
     * @param userBrokerRepository User-broker repository
     * @param brokerFactory Broker adapter factory
     * @param reconcileInterval How often to reconcile
     * @param pendingTimeout When to timeout pending orders
     * @param maxConcurrentBrokerCalls Max concurrent broker API calls
     */
    public PendingOrderReconciler(
            TradeRepository tradeRepository,
            UserBrokerRepository userBrokerRepository,
            BrokerAdapterFactory brokerFactory,
            Duration reconcileInterval,
            Duration pendingTimeout,
            int maxConcurrentBrokerCalls) {
        this.tradeRepository = tradeRepository;
        this.userBrokerRepository = userBrokerRepository;
        this.brokerFactory = brokerFactory;
        this.reconcileInterval = reconcileInterval;
        this.pendingTimeout = pendingTimeout;
        this.maxConcurrentBrokerCalls = maxConcurrentBrokerCalls;
        this.brokerCallSemaphore = new Semaphore(maxConcurrentBrokerCalls);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "pending-order-reconciler")
        );
    }

    /**
     * Start the reconciler (scheduled job).
     */
    public void start() {
        long initialDelay = 10;
        long period = reconcileInterval.toSeconds();

        scheduler.scheduleAtFixedRate(
            this::reconcilePendingOrders,
            initialDelay,
            period,
            TimeUnit.SECONDS
        );

        log.info("Pending order reconciler started: interval={}s, timeout={}s, maxConcurrent={}",
            period, pendingTimeout.toSeconds(), maxConcurrentBrokerCalls);
    }

    /**
     * Stop the reconciler.
     */
    public void stop() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Pending order reconciler stopped");
    }

    /**
     * Reconcile all pending orders (scheduled job).
     */
    private void reconcilePendingOrders() {
        try {
            Instant start = Instant.now();
            List<Trade> pending = tradeRepository.findByStatus("PENDING");

            if (pending.isEmpty()) {
                log.debug("No pending orders to reconcile");
                lastReconcileCount = 0;
                lastReconcileTime = Instant.now();
                return;
            }

            log.info("Reconciling {} pending orders...", pending.size());
            int updated = 0;

            for (Trade trade : pending) {
                try {
                    if (reconcileTrade(trade)) {
                        updated++;
                    }
                } catch (Exception e) {
                    log.error("Failed to reconcile trade {}: {}", trade.tradeId(), e.getMessage());
                }
            }

            Duration elapsed = Duration.between(start, Instant.now());
            lastReconcileCount = pending.size();
            lastReconcileTime = Instant.now();
            totalReconciled += pending.size();
            totalUpdates += updated;

            log.info("Reconciliation complete: checked={}, updated={}, elapsed={}ms",
                pending.size(), updated, elapsed.toMillis());

        } catch (Exception e) {
            log.error("Reconciliation job failed", e);
        }
    }

    /**
     * Reconcile a single trade with broker.
     *
     * @param trade Trade to reconcile
     * @return true if trade was updated
     */
    private boolean reconcileTrade(Trade trade) {
        // ✅ CORRECTED: Check timeout using last_broker_update_at (not created_at)
        // Reason: created_at could be days old (after restart), but last_broker_update_at
        //         reflects when we last heard from broker
        Instant lastUpdate = trade.lastBrokerUpdateAt() != null
            ? trade.lastBrokerUpdateAt()
            : trade.createdAt();  // Fallback for legacy rows

        Duration sinceLastUpdate = Duration.between(lastUpdate, Instant.now());

        if (sinceLastUpdate.compareTo(pendingTimeout) > 0) {
            log.warn("Trade {} pending timeout ({}s since last broker update), marking as TIMEOUT",
                trade.tradeId(), sinceLastUpdate.toSeconds());

            // Update trade to TIMEOUT status (or REJECTED)
            Trade timedOut = new Trade(
                trade.tradeId(), trade.portfolioId(), trade.userId(), trade.brokerId(),
                trade.userBrokerId(), trade.signalId(), trade.intentId(), trade.symbol(),
                trade.direction(), trade.tradeNumber(), trade.entryPrice(), trade.entryQty(), trade.entryValue(),
                trade.entryTimestamp(), trade.productType(), trade.entryHtfZone(),
                trade.entryItfZone(), trade.entryLtfZone(), trade.entryConfluenceType(),
                trade.entryConfluenceScore(), trade.entryHtfLow(), trade.entryHtfHigh(),
                trade.entryItfLow(), trade.entryItfHigh(), trade.entryLtfLow(),
                trade.entryLtfHigh(), trade.entryEffectiveFloor(), trade.entryEffectiveCeiling(),
                trade.logLossAtFloor(), trade.maxLogLossAllowed(), trade.exitMinProfitPrice(),
                trade.exitTargetPrice(), trade.exitStretchPrice(), trade.exitPrimaryPrice(),
                "REJECTED",  // Mark as REJECTED (timeout)
                trade.currentPrice(), trade.currentLogReturn(), trade.unrealizedPnl(),
                trade.trailingActive(), trade.trailingHighestPrice(), trade.trailingStopPrice(),
                trade.exitPrice(), trade.exitTimestamp(), "TIMEOUT", trade.exitOrderId(),
                trade.realizedPnl(), trade.realizedLogReturn(), trade.holdingDays(),
                trade.brokerOrderId(), trade.brokerTradeId(), trade.clientOrderId(),
                Instant.now(),  // Update last_broker_update_at
                trade.createdAt(), Instant.now(), trade.deletedAt(), trade.version()
            );

            tradeRepository.upsert(timedOut);
            totalTimeouts++;
            return true;
        }

        // ✅ NEW: Rate limiting (prevent API hammering with 100 pending trades)
        if (!brokerCallSemaphore.tryAcquire()) {
            log.debug("Rate limit reached, skipping trade {} reconciliation this cycle", trade.tradeId());
            totalRateLimited++;
            return false;
        }

        try {
            // Get broker adapter
            UserBroker userBroker = userBrokerRepository.findById(trade.userBrokerId()).orElse(null);
            if (userBroker == null) {
                log.warn("UserBroker not found for trade {}: {}", trade.tradeId(), trade.userBrokerId());
                return false;
            }

            BrokerAdapter broker = brokerFactory.getOrCreate(trade.userBrokerId(), userBroker.brokerId());
            if (broker == null || !broker.isConnected()) {
                log.debug("Broker not available for reconciliation: {}", userBroker.brokerId());
                return false;
            }

            // Query broker for order status
            BrokerAdapter.OrderStatus status = broker.getOrderStatus(trade.brokerOrderId()).join();

            if (status == null) {
                log.debug("Broker returned no status for trade {}", trade.tradeId());
                return false;
            }

            // ✅ CORRECTED: Update last_broker_update_at timestamp
            // (Even if status unchanged, we heard from broker)
            Trade withTimestamp = new Trade(
                trade.tradeId(), trade.portfolioId(), trade.userId(), trade.brokerId(),
                trade.userBrokerId(), trade.signalId(), trade.intentId(), trade.symbol(),
                trade.direction(), trade.tradeNumber(), trade.entryPrice(), trade.entryQty(), trade.entryValue(),
                trade.entryTimestamp(), trade.productType(), trade.entryHtfZone(),
                trade.entryItfZone(), trade.entryLtfZone(), trade.entryConfluenceType(),
                trade.entryConfluenceScore(), trade.entryHtfLow(), trade.entryHtfHigh(),
                trade.entryItfLow(), trade.entryItfHigh(), trade.entryLtfLow(),
                trade.entryLtfHigh(), trade.entryEffectiveFloor(), trade.entryEffectiveCeiling(),
                trade.logLossAtFloor(), trade.maxLogLossAllowed(), trade.exitMinProfitPrice(),
                trade.exitTargetPrice(), trade.exitStretchPrice(), trade.exitPrimaryPrice(),
                trade.status(), trade.currentPrice(), trade.currentLogReturn(), trade.unrealizedPnl(),
                trade.trailingActive(), trade.trailingHighestPrice(), trade.trailingStopPrice(),
                trade.exitPrice(), trade.exitTimestamp(), trade.exitTrigger(), trade.exitOrderId(),
                trade.realizedPnl(), trade.realizedLogReturn(), trade.holdingDays(),
                trade.brokerOrderId(), trade.brokerTradeId(), trade.clientOrderId(),
                Instant.now(),  // Update last_broker_update_at
                trade.createdAt(), trade.updatedAt(), trade.deletedAt(), trade.version()
            );

            // Update trade based on broker response
            Trade updated = updateFromBrokerStatus(withTimestamp, status);

            // ✅ CORRECTED: Field comparison (not reference comparison)
            // Reason: `updated != trade` compares references, not content
            //         Use equals() or compare specific fields
            if (hasChanged(trade, updated)) {
                tradeRepository.upsert(updated);
                log.info("Reconciled trade {}: {} → {}", trade.tradeId(), trade.status(), updated.status());
                return true;
            }

            // No change, but update timestamp
            tradeRepository.upsert(withTimestamp);
            return false;

        } catch (Exception e) {
            log.error("Failed to query broker for trade {}: {}", trade.tradeId(), e.getMessage());
            return false;
        } finally {
            brokerCallSemaphore.release();
        }
    }

    /**
     * ✅ CORRECTED: Check if trade changed (field comparison).
     */
    private boolean hasChanged(Trade before, Trade after) {
        // Compare relevant fields (status, filled_qty, avg_fill_price)
        boolean statusChanged = !before.status().equals(after.status());
        boolean qtyChanged = before.entryQty() != after.entryQty();

        boolean priceChanged = false;
        if (before.entryPrice() != null && after.entryPrice() != null) {
            priceChanged = before.entryPrice().compareTo(after.entryPrice()) != 0;
        } else if (before.entryPrice() != after.entryPrice()) {
            priceChanged = true;  // One is null, other is not
        }

        return statusChanged || qtyChanged || priceChanged;
    }

    /**
     * Update trade from broker status.
     */
    private Trade updateFromBrokerStatus(Trade trade, BrokerAdapter.OrderStatus status) {
        String brokerStatus = status.status();

        if ("COMPLETE".equalsIgnoreCase(brokerStatus) || "FILLED".equalsIgnoreCase(brokerStatus)) {
            log.info("Trade {} filled by broker: qty={}, price={}",
                trade.tradeId(), status.filledQuantity(), status.averagePrice());

            return new Trade(
                trade.tradeId(), trade.portfolioId(), trade.userId(), trade.brokerId(),
                trade.userBrokerId(), trade.signalId(), trade.intentId(), trade.symbol(),
                trade.direction(), trade.tradeNumber(),
                status.averagePrice() != null ? status.averagePrice() : trade.entryPrice(),
                status.filledQuantity(),
                trade.entryValue(),
                trade.entryTimestamp(), trade.productType(), trade.entryHtfZone(),
                trade.entryItfZone(), trade.entryLtfZone(), trade.entryConfluenceType(),
                trade.entryConfluenceScore(), trade.entryHtfLow(), trade.entryHtfHigh(),
                trade.entryItfLow(), trade.entryItfHigh(), trade.entryLtfLow(),
                trade.entryLtfHigh(), trade.entryEffectiveFloor(), trade.entryEffectiveCeiling(),
                trade.logLossAtFloor(), trade.maxLogLossAllowed(), trade.exitMinProfitPrice(),
                trade.exitTargetPrice(), trade.exitStretchPrice(), trade.exitPrimaryPrice(),
                "FILLED",  // Update status
                trade.currentPrice(), trade.currentLogReturn(), trade.unrealizedPnl(),
                trade.trailingActive(), trade.trailingHighestPrice(), trade.trailingStopPrice(),
                trade.exitPrice(), trade.exitTimestamp(), trade.exitTrigger(), trade.exitOrderId(),
                trade.realizedPnl(), trade.realizedLogReturn(), trade.holdingDays(),
                trade.brokerOrderId(), trade.brokerTradeId(), trade.clientOrderId(),
                trade.lastBrokerUpdateAt(), trade.createdAt(), Instant.now(), trade.deletedAt(), trade.version()
            );
        }

        if ("REJECTED".equalsIgnoreCase(brokerStatus)) {
            log.warn("Trade {} rejected by broker: {}", trade.tradeId(), status.statusMessage());

            return new Trade(
                trade.tradeId(), trade.portfolioId(), trade.userId(), trade.brokerId(),
                trade.userBrokerId(), trade.signalId(), trade.intentId(), trade.symbol(),
                trade.direction(), trade.tradeNumber(), trade.entryPrice(), trade.entryQty(), trade.entryValue(),
                trade.entryTimestamp(), trade.productType(), trade.entryHtfZone(),
                trade.entryItfZone(), trade.entryLtfZone(), trade.entryConfluenceType(),
                trade.entryConfluenceScore(), trade.entryHtfLow(), trade.entryHtfHigh(),
                trade.entryItfLow(), trade.entryItfHigh(), trade.entryLtfLow(),
                trade.entryLtfHigh(), trade.entryEffectiveFloor(), trade.entryEffectiveCeiling(),
                trade.logLossAtFloor(), trade.maxLogLossAllowed(), trade.exitMinProfitPrice(),
                trade.exitTargetPrice(), trade.exitStretchPrice(), trade.exitPrimaryPrice(),
                "REJECTED",  // Update status
                trade.currentPrice(), trade.currentLogReturn(), trade.unrealizedPnl(),
                trade.trailingActive(), trade.trailingHighestPrice(), trade.trailingStopPrice(),
                trade.exitPrice(), trade.exitTimestamp(), status.statusMessage(), trade.exitOrderId(),
                trade.realizedPnl(), trade.realizedLogReturn(), trade.holdingDays(),
                trade.brokerOrderId(), trade.brokerTradeId(), trade.clientOrderId(),
                trade.lastBrokerUpdateAt(), trade.createdAt(), Instant.now(), trade.deletedAt(), trade.version()
            );
        }

        if ("CANCELLED".equalsIgnoreCase(brokerStatus)) {
            log.info("Trade {} cancelled by broker", trade.tradeId());

            return new Trade(
                trade.tradeId(), trade.portfolioId(), trade.userId(), trade.brokerId(),
                trade.userBrokerId(), trade.signalId(), trade.intentId(), trade.symbol(),
                trade.direction(), trade.tradeNumber(), trade.entryPrice(), trade.entryQty(), trade.entryValue(),
                trade.entryTimestamp(), trade.productType(), trade.entryHtfZone(),
                trade.entryItfZone(), trade.entryLtfZone(), trade.entryConfluenceType(),
                trade.entryConfluenceScore(), trade.entryHtfLow(), trade.entryHtfHigh(),
                trade.entryItfLow(), trade.entryItfHigh(), trade.entryLtfLow(),
                trade.entryLtfHigh(), trade.entryEffectiveFloor(), trade.entryEffectiveCeiling(),
                trade.logLossAtFloor(), trade.maxLogLossAllowed(), trade.exitMinProfitPrice(),
                trade.exitTargetPrice(), trade.exitStretchPrice(), trade.exitPrimaryPrice(),
                "CANCELLED",  // Update status
                trade.currentPrice(), trade.currentLogReturn(), trade.unrealizedPnl(),
                trade.trailingActive(), trade.trailingHighestPrice(), trade.trailingStopPrice(),
                trade.exitPrice(), trade.exitTimestamp(), trade.exitTrigger(), trade.exitOrderId(),
                trade.realizedPnl(), trade.realizedLogReturn(), trade.holdingDays(),
                trade.brokerOrderId(), trade.brokerTradeId(), trade.clientOrderId(),
                trade.lastBrokerUpdateAt(), trade.createdAt(), Instant.now(), trade.deletedAt(), trade.version()
            );
        }

        // Status unchanged (PENDING, OPEN, TRIGGER_PENDING)
        log.debug("Trade {} still pending at broker", trade.tradeId());
        return trade;
    }

    /**
     * Get reconciliation metrics.
     */
    public ReconcileMetrics getMetrics() {
        return new ReconcileMetrics(
            lastReconcileCount,
            lastReconcileTime,
            totalReconciled,
            totalUpdates,
            totalTimeouts,
            totalRateLimited,
            brokerCallSemaphore.availablePermits()
        );
    }

    /**
     * Reconciliation metrics.
     */
    public record ReconcileMetrics(
        long lastChecked,
        Instant lastRunTime,
        long totalChecked,
        long totalUpdated,
        long totalTimeouts,
        long totalRateLimited,
        int availablePermits
    ) {}
}
