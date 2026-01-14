package in.annupaper.service.execution;

import in.annupaper.broker.BrokerAdapter;
import in.annupaper.broker.BrokerAdapterFactory;
import in.annupaper.domain.common.EventType;
import in.annupaper.domain.trade.ExitIntent;
import in.annupaper.domain.trade.ExitIntentStatus;
import in.annupaper.domain.trade.Trade;
import in.annupaper.domain.broker.UserBroker;
import in.annupaper.repository.ExitIntentRepository;
import in.annupaper.repository.TradeRepository;
import in.annupaper.repository.UserBrokerRepository;
import in.annupaper.service.core.EventService;
import in.annupaper.service.trade.TradeManagementService;  // ✅ P0 fix: single-writer enforcement
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * ExitOrderReconciler - Tracks exit orders to completion.
 *
 * Mirrors PendingOrderReconciler pattern for exits:
 * 1. Poll exit_intents with status=PLACED every 30 seconds
 * 2. Query broker for exit order status
 * 3. Update exit intent status: PLACED → FILLED/REJECTED/CANCELLED
 * 4. When FILLED: close trade (OPEN → CLOSED)
 * 5. Handle timeouts (10 minutes) and rate limiting
 *
 * CLOSES ARCHITECTURE GAP: "Exit Order Reconciler Missing"
 * See: ARCHITECTURE_STATUS_RESPONSE.md, EXIT_QUALIFICATION_ARCHITECTURE.md
 *
 * Features:
 * - Rate limiting (max 5 concurrent broker calls)
 * - Timeout handling (10 min default)
 * - Field-based change detection
 * - Comprehensive metrics
 * - Event emission (EXIT_INTENT_FILLED, EXIT_INTENT_FAILED, EXIT_INTENT_CANCELLED)
 */
public final class ExitOrderReconciler {
    private static final Logger log = LoggerFactory.getLogger(ExitOrderReconciler.class);

    private final ExitIntentRepository exitIntentRepo;
    private final TradeRepository tradeRepo;  // Read-only: for fetching trade details in events
    private final TradeManagementService tradeManagementService;  // ✅ P0 fix: single-writer for trades
    private final UserBrokerRepository userBrokerRepo;
    private final BrokerAdapterFactory brokerFactory;
    private final EventService eventService;
    private final ScheduledExecutorService scheduler;

    private final Duration reconcileInterval;
    private final Duration placedTimeout;
    private final int maxConcurrentBrokerCalls;

    // Rate limiter (prevent API hammering)
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
     */
    public ExitOrderReconciler(
        ExitIntentRepository exitIntentRepo,
        TradeRepository tradeRepo,
        TradeManagementService tradeManagementService,  // ✅ P0 fix
        UserBrokerRepository userBrokerRepo,
        BrokerAdapterFactory brokerFactory,
        EventService eventService
    ) {
        this(exitIntentRepo, tradeRepo, tradeManagementService, userBrokerRepo, brokerFactory, eventService,
            Duration.ofSeconds(30),  // Reconcile every 30 seconds
            Duration.ofMinutes(10),  // Timeout after 10 minutes
            5);                      // Max 5 concurrent broker calls
    }

    /**
     * Create reconciler with custom settings.
     */
    public ExitOrderReconciler(
        ExitIntentRepository exitIntentRepo,
        TradeRepository tradeRepo,
        TradeManagementService tradeManagementService,  // ✅ P0 fix
        UserBrokerRepository userBrokerRepo,
        BrokerAdapterFactory brokerFactory,
        EventService eventService,
        Duration reconcileInterval,
        Duration placedTimeout,
        int maxConcurrentBrokerCalls
    ) {
        this.exitIntentRepo = exitIntentRepo;
        this.tradeRepo = tradeRepo;
        this.tradeManagementService = tradeManagementService;  // ✅ P0 fix
        this.userBrokerRepo = userBrokerRepo;
        this.brokerFactory = brokerFactory;
        this.eventService = eventService;
        this.reconcileInterval = reconcileInterval;
        this.placedTimeout = placedTimeout;
        this.maxConcurrentBrokerCalls = maxConcurrentBrokerCalls;
        this.brokerCallSemaphore = new Semaphore(maxConcurrentBrokerCalls);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> {
                Thread t = new Thread(r, "exit-order-reconciler");
                t.setDaemon(true);
                return t;
            }
        );
    }

    /**
     * Start the reconciler (scheduled job).
     */
    public void start() {
        long initialDelay = 15;  // Offset from entry reconciler
        long period = reconcileInterval.toSeconds();

        scheduler.scheduleAtFixedRate(
            this::reconcilePlacedExitOrders,
            initialDelay,
            period,
            TimeUnit.SECONDS
        );

        log.info("Exit order reconciler started: interval={}s, timeout={}s, maxConcurrent={}",
            period, placedTimeout.toSeconds(), maxConcurrentBrokerCalls);
    }

    /**
     * Stop the reconciler.
     */
    public void stop() {
        log.info("Stopping exit order reconciler...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            log.info("Exit order reconciler stopped");
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Reconcile all placed exit orders (scheduled job).
     */
    private void reconcilePlacedExitOrders() {
        try {
            Instant start = Instant.now();

            // Query for PLACED exit intents (exit orders in flight)
            List<ExitIntent> placed = exitIntentRepo.findByStatus(
                ExitIntentStatus.PLACED
            );

            if (placed.isEmpty()) {
                log.debug("No placed exit orders to reconcile");
                lastReconcileCount = 0;
                lastReconcileTime = Instant.now();
                return;
            }

            log.info("Reconciling {} placed exit orders...", placed.size());
            int updated = 0;

            for (ExitIntent exitIntent : placed) {
                try {
                    if (reconcileExitIntent(exitIntent)) {
                        updated++;
                    }
                } catch (Exception e) {
                    log.error("Failed to reconcile exit intent {}: {}",
                        exitIntent.exitIntentId(), e.getMessage());
                }
            }

            Duration elapsed = Duration.between(start, Instant.now());
            lastReconcileCount = placed.size();
            lastReconcileTime = Instant.now();
            totalReconciled += placed.size();
            totalUpdates += updated;

            log.info("Exit reconciliation complete: checked={}, updated={}, elapsed={}ms",
                placed.size(), updated, elapsed.toMillis());

        } catch (Exception e) {
            log.error("Exit reconciliation job failed", e);
        }
    }

    /**
     * Reconcile a single exit intent with broker.
     *
     * @param exitIntent Exit intent to reconcile
     * @return true if exit intent was updated
     */
    private boolean reconcileExitIntent(ExitIntent exitIntent) {
        // Check timeout (10 minutes since placed_at)
        if (exitIntent.placedAt() == null) {
            log.warn("Exit intent {} has no placed_at timestamp, skipping", exitIntent.exitIntentId());
            return false;
        }

        Duration sincePlaced = Duration.between(exitIntent.placedAt(), Instant.now());

        if (sincePlaced.compareTo(placedTimeout) > 0) {
            log.warn("Exit intent {} timeout ({}s since placed), marking as FAILED",
                exitIntent.exitIntentId(), sincePlaced.toSeconds());

            exitIntentRepo.markFailed(exitIntent.exitIntentId(),
                "TIMEOUT", "Exit order timeout after " + sincePlaced.toSeconds() + "s");

            emitExitTimeoutEvent(exitIntent);
            totalTimeouts++;
            return true;
        }

        // Rate limiting
        if (!brokerCallSemaphore.tryAcquire()) {
            log.debug("Rate limit reached, skipping exit intent {} this cycle",
                exitIntent.exitIntentId());
            totalRateLimited++;
            return false;
        }

        try {
            // Get broker adapter
            UserBroker userBroker = userBrokerRepo.findById(exitIntent.userBrokerId()).orElse(null);
            if (userBroker == null) {
                log.warn("UserBroker not found for exit intent {}: {}",
                    exitIntent.exitIntentId(), exitIntent.userBrokerId());
                return false;
            }

            BrokerAdapter broker = brokerFactory.getOrCreate(
                exitIntent.userBrokerId(),
                userBroker.brokerId()
            );

            if (broker == null || !broker.isConnected()) {
                log.debug("Broker not available for exit reconciliation: {}", userBroker.brokerId());
                return false;
            }

            // Query broker for exit order status
            String brokerOrderId = exitIntent.brokerOrderId();
            if (brokerOrderId == null || brokerOrderId.startsWith("PENDING_")) {
                log.debug("Exit intent {} has no real broker order ID yet", exitIntent.exitIntentId());
                return false;
            }

            BrokerAdapter.OrderStatus status = broker.getOrderStatus(brokerOrderId).join();

            if (status == null) {
                log.debug("Broker returned no status for exit order {}", brokerOrderId);
                return false;
            }

            // Update exit intent based on broker status
            return updateFromBrokerStatus(exitIntent, status);

        } catch (Exception e) {
            log.error("Failed to query broker for exit intent {}: {}",
                exitIntent.exitIntentId(), e.getMessage());
            return false;
        } finally {
            brokerCallSemaphore.release();
        }
    }

    /**
     * Update exit intent from broker status.
     *
     * @param exitIntent Current exit intent
     * @param status Broker status
     * @return true if updated
     */
    private boolean updateFromBrokerStatus(ExitIntent exitIntent, BrokerAdapter.OrderStatus status) {
        String brokerStatus = status.status();

        if ("COMPLETE".equalsIgnoreCase(brokerStatus) || "FILLED".equalsIgnoreCase(brokerStatus)) {
            log.info("✅ Exit order FILLED: intent={}, brokerOrderId={}, qty={}, price={}",
                exitIntent.exitIntentId(), exitIntent.brokerOrderId(),
                status.filledQuantity(), status.averagePrice());

            // Mark exit intent as FILLED
            exitIntentRepo.markFilled(exitIntent.exitIntentId());

            // Close the trade
            closeTradeOnExitFill(exitIntent, status);

            // Emit EXIT_INTENT_FILLED event
            emitExitFilledEvent(exitIntent, status);

            return true;
        }

        if ("REJECTED".equalsIgnoreCase(brokerStatus)) {
            log.warn("⚠️ Exit order REJECTED: intent={}, reason={}",
                exitIntent.exitIntentId(), status.statusMessage());

            exitIntentRepo.markFailed(exitIntent.exitIntentId(),
                "BROKER_REJECTED", status.statusMessage());

            emitExitRejectedEvent(exitIntent, status.statusMessage());

            return true;
        }

        if ("CANCELLED".equalsIgnoreCase(brokerStatus)) {
            log.info("Exit order CANCELLED: intent={}", exitIntent.exitIntentId());

            exitIntentRepo.markCancelled(exitIntent.exitIntentId());

            emitExitCancelledEvent(exitIntent);

            return true;
        }

        // Status unchanged (PENDING, OPEN, TRIGGER_PENDING)
        log.debug("Exit order {} still pending at broker", exitIntent.brokerOrderId());
        return false;
    }

    /**
     * Close trade when exit order fills.
     * ✅ P0 fix: Delegate to TradeManagementService (single-writer enforcement)
     */
    private void closeTradeOnExitFill(ExitIntent exitIntent, BrokerAdapter.OrderStatus status) {
        try {
            // ❌ REMOVED - P0 fix: Trade creation logic moved to TradeManagementService
            // Old code: Created entire Trade object with P&L calculations
            // New code: Delegate to single-writer (TradeManagementService)

            Trade trade = tradeRepo.findById(exitIntent.tradeId()).orElse(null);
            if (trade == null) {
                log.error("Trade not found for exit intent {}: {}",
                    exitIntent.exitIntentId(), exitIntent.tradeId());
                return;
            }

            BigDecimal exitPrice = status.averagePrice() != null
                ? status.averagePrice()
                : trade.currentPrice();  // Fallback

            int exitQty = status.filledQuantity();

            // ✅ P0 fix: Delegate to TradeManagementService (single-writer)
            tradeManagementService.closeTradeOnExitFill(
                exitIntent.tradeId(),
                exitPrice,
                exitQty,
                exitIntent.exitReason(),
                Instant.now()
            );

            log.info("✅ Trade CLOSED via TMS: {} exitPrice={} qty={} reason={}",
                exitIntent.tradeId(), exitPrice, exitQty, exitIntent.exitReason());

        } catch (Exception e) {
            log.error("Failed to close trade for exit intent {}: {}",
                exitIntent.exitIntentId(), e.getMessage(), e);
        }
    }

    // ❌ REMOVED - P0 fix: calculatePnL() and calculateLogReturn() methods
    // These are now in TradeManagementService (single-writer owns all trade logic)

    /**
     * Emit EXIT_INTENT_FILLED event.
     */
    private void emitExitFilledEvent(ExitIntent exitIntent, BrokerAdapter.OrderStatus status) {
        try {
            Trade trade = tradeRepo.findById(exitIntent.tradeId()).orElse(null);
            if (trade == null) return;

            Map<String, Object> payload = new HashMap<>();
            payload.put("exitIntentId", exitIntent.exitIntentId());
            payload.put("tradeId", exitIntent.tradeId());
            payload.put("exitReason", exitIntent.exitReason());
            payload.put("brokerOrderId", exitIntent.brokerOrderId());
            payload.put("filledQty", status.filledQuantity());
            payload.put("avgPrice", status.averagePrice());
            payload.put("symbol", trade.symbol());

            eventService.emitUserBroker(
                EventType.EXIT_INTENT_FILLED,
                trade.userId(),
                trade.brokerId(),
                exitIntent.userBrokerId(),
                payload,
                null, null,
                trade.tradeId(),
                exitIntent.brokerOrderId(),
                "EXIT_ORDER_RECONCILER"
            );
        } catch (Exception e) {
            log.error("Failed to emit EXIT_INTENT_FILLED event: {}", e.getMessage());
        }
    }

    /**
     * Emit EXIT_INTENT_FAILED event (rejection).
     */
    private void emitExitRejectedEvent(ExitIntent exitIntent, String reason) {
        try {
            Trade trade = tradeRepo.findById(exitIntent.tradeId()).orElse(null);
            if (trade == null) return;

            Map<String, Object> payload = new HashMap<>();
            payload.put("exitIntentId", exitIntent.exitIntentId());
            payload.put("tradeId", exitIntent.tradeId());
            payload.put("exitReason", exitIntent.exitReason());
            payload.put("errorCode", "BROKER_REJECTED");
            payload.put("errorMessage", reason);
            payload.put("symbol", trade.symbol());

            eventService.emitUserBroker(
                EventType.EXIT_INTENT_FAILED,
                trade.userId(),
                trade.brokerId(),
                exitIntent.userBrokerId(),
                payload,
                null, null,
                trade.tradeId(),
                exitIntent.brokerOrderId(),
                "EXIT_ORDER_RECONCILER"
            );
        } catch (Exception e) {
            log.error("Failed to emit EXIT_INTENT_FAILED event: {}", e.getMessage());
        }
    }

    /**
     * Emit EXIT_INTENT_CANCELLED event.
     */
    private void emitExitCancelledEvent(ExitIntent exitIntent) {
        try {
            Trade trade = tradeRepo.findById(exitIntent.tradeId()).orElse(null);
            if (trade == null) return;

            Map<String, Object> payload = new HashMap<>();
            payload.put("exitIntentId", exitIntent.exitIntentId());
            payload.put("tradeId", exitIntent.tradeId());
            payload.put("exitReason", exitIntent.exitReason());
            payload.put("brokerOrderId", exitIntent.brokerOrderId());
            payload.put("symbol", trade.symbol());

            eventService.emitUserBroker(
                EventType.EXIT_INTENT_CANCELLED,
                trade.userId(),
                trade.brokerId(),
                exitIntent.userBrokerId(),
                payload,
                null, null,
                trade.tradeId(),
                exitIntent.brokerOrderId(),
                "EXIT_ORDER_RECONCILER"
            );
        } catch (Exception e) {
            log.error("Failed to emit EXIT_INTENT_CANCELLED event: {}", e.getMessage());
        }
    }

    /**
     * Emit EXIT_TIMEOUT event.
     */
    private void emitExitTimeoutEvent(ExitIntent exitIntent) {
        try {
            Trade trade = tradeRepo.findById(exitIntent.tradeId()).orElse(null);
            if (trade == null) return;

            Map<String, Object> payload = new HashMap<>();
            payload.put("exitIntentId", exitIntent.exitIntentId());
            payload.put("tradeId", exitIntent.tradeId());
            payload.put("exitReason", exitIntent.exitReason());
            payload.put("brokerOrderId", exitIntent.brokerOrderId());
            payload.put("errorCode", "TIMEOUT");
            payload.put("errorMessage", "Exit order timeout");
            payload.put("symbol", trade.symbol());

            eventService.emitUserBroker(
                EventType.EXIT_INTENT_FAILED,
                trade.userId(),
                trade.brokerId(),
                exitIntent.userBrokerId(),
                payload,
                null, null,
                trade.tradeId(),
                exitIntent.brokerOrderId(),
                "EXIT_ORDER_RECONCILER"
            );
        } catch (Exception e) {
            log.error("Failed to emit EXIT_TIMEOUT event: {}", e.getMessage());
        }
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
