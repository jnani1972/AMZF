package in.annupaper.application.service;

import in.annupaper.domain.model.*;
import in.annupaper.infrastructure.broker.BrokerAdapterFactory;
import in.annupaper.application.port.output.*;
import in.annupaper.application.port.input.TradeManagementService;
import in.annupaper.service.core.EventService;
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
 */
public final class ExitOrderReconciler {
    private static final Logger log = LoggerFactory.getLogger(ExitOrderReconciler.class);

    private final ExitIntentRepository exitIntentRepo;
    private final TradeRepository tradeRepo;
    private final TradeManagementService tradeManagementService;
    private final UserBrokerRepository userBrokerRepo;
    private final BrokerAdapterFactory brokerFactory;
    private final EventService eventService;
    private final ScheduledExecutorService scheduler;

    private final Duration reconcileInterval;
    private final Duration placedTimeout;
    private final int maxConcurrentBrokerCalls;
    private final Semaphore brokerCallSemaphore;

    private long lastReconcileCount = 0;
    private long totalReconciled = 0;
    private long totalUpdates = 0;
    private long totalTimeouts = 0;
    private long totalRateLimited = 0;
    private Instant lastReconcileTime = Instant.now();

    public ExitOrderReconciler(
            ExitIntentRepository exitIntentRepo,
            TradeRepository tradeRepo,
            TradeManagementService tradeManagementService,
            UserBrokerRepository userBrokerRepo,
            BrokerAdapterFactory brokerFactory,
            EventService eventService) {
        this(exitIntentRepo, tradeRepo, tradeManagementService, userBrokerRepo, brokerFactory, eventService,
                Duration.ofSeconds(30),
                Duration.ofMinutes(10),
                5);
    }

    public ExitOrderReconciler(
            ExitIntentRepository exitIntentRepo,
            TradeRepository tradeRepo,
            TradeManagementService tradeManagementService,
            UserBrokerRepository userBrokerRepo,
            BrokerAdapterFactory brokerFactory,
            EventService eventService,
            Duration reconcileInterval,
            Duration placedTimeout,
            int maxConcurrentBrokerCalls) {
        this.exitIntentRepo = exitIntentRepo;
        this.tradeRepo = tradeRepo;
        this.tradeManagementService = tradeManagementService;
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
                });
    }

    public void start() {
        long initialDelay = 15;
        long period = reconcileInterval.toSeconds();
        scheduler.scheduleAtFixedRate(this::reconcilePlacedExitOrders, initialDelay, period, TimeUnit.SECONDS);
        log.info("Exit order reconciler started: interval={}s, timeout={}s", period, placedTimeout.toSeconds());
    }

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
    }

    private void reconcilePlacedExitOrders() {
        try {
            Instant start = Instant.now();
            List<ExitIntent> placed = exitIntentRepo.findByStatus(ExitIntentStatus.PLACED);

            if (placed.isEmpty()) {
                lastReconcileCount = 0;
                lastReconcileTime = Instant.now();
                return;
            }

            int updated = 0;
            for (ExitIntent exitIntent : placed) {
                try {
                    if (reconcileExitIntent(exitIntent)) {
                        updated++;
                    }
                } catch (Exception e) {
                    log.error("Failed to reconcile exit intent {}: {}", exitIntent.exitIntentId(), e.getMessage());
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

    private boolean reconcileExitIntent(ExitIntent exitIntent) {
        if (exitIntent.placedAt() == null)
            return false;

        Duration sincePlaced = Duration.between(exitIntent.placedAt(), Instant.now());
        if (sincePlaced.compareTo(placedTimeout) > 0) {
            exitIntentRepo.markFailed(exitIntent.exitIntentId(), "TIMEOUT", "Exit order timeout");
            emitExitTimeoutEvent(exitIntent);
            totalTimeouts++;
            return true;
        }

        if (!brokerCallSemaphore.tryAcquire()) {
            totalRateLimited++;
            return false;
        }

        try {
            UserBroker userBroker = userBrokerRepo.findById(exitIntent.userBrokerId()).orElse(null);
            if (userBroker == null)
                return false;

            BrokerAdapter broker = brokerFactory.getOrCreate(exitIntent.userBrokerId(), userBroker.brokerId());
            if (broker == null || !broker.isConnected())
                return false;

            String brokerOrderId = exitIntent.brokerOrderId();
            if (brokerOrderId == null || brokerOrderId.startsWith("PENDING_"))
                return false;

            BrokerAdapter.BrokerOrderStatus status = broker.getOrderStatus(brokerOrderId).join();
            if (status == null)
                return false;

            return updateFromBrokerStatus(exitIntent, status);

        } catch (Exception e) {
            log.error("Failed to query broker for exit intent {}: {}", exitIntent.exitIntentId(), e.getMessage());
            return false;
        } finally {
            brokerCallSemaphore.release();
        }
    }

    private boolean updateFromBrokerStatus(ExitIntent exitIntent, BrokerAdapter.BrokerOrderStatus status) {
        String brokerStatus = status.status();

        if ("COMPLETE".equalsIgnoreCase(brokerStatus) || "FILLED".equalsIgnoreCase(brokerStatus)) {
            exitIntentRepo.markFilled(exitIntent.exitIntentId());
            closeTradeOnExitFill(exitIntent, status);
            emitExitFilledEvent(exitIntent, status);
            return true;
        }

        if ("REJECTED".equalsIgnoreCase(brokerStatus)) {
            exitIntentRepo.markFailed(exitIntent.exitIntentId(), "BROKER_REJECTED", status.statusMessage());
            emitExitRejectedEvent(exitIntent, status.statusMessage());
            return true;
        }

        if ("CANCELLED".equalsIgnoreCase(brokerStatus)) {
            exitIntentRepo.markCancelled(exitIntent.exitIntentId());
            emitExitCancelledEvent(exitIntent);
            return true;
        }

        return false;
    }

    private void closeTradeOnExitFill(ExitIntent exitIntent, BrokerAdapter.BrokerOrderStatus status) {
        try {
            BigDecimal exitPrice = status.averagePrice();
            int exitQty = status.filledQuantity();

            tradeManagementService.closeTradeOnExitFill(
                    exitIntent.tradeId(),
                    exitPrice,
                    exitQty,
                    exitIntent.exitReason().name(),
                    Instant.now());
        } catch (Exception e) {
            log.error("Failed to close trade for exit intent {}: {}", exitIntent.exitIntentId(), e.getMessage());
        }
    }

    private void emitExitFilledEvent(ExitIntent exitIntent, BrokerAdapter.BrokerOrderStatus status) {
        try {
            Trade trade = tradeRepo.findById(exitIntent.tradeId()).orElse(null);
            if (trade == null)
                return;

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
                    "EXIT_ORDER_RECONCILER");
        } catch (Exception e) {
            log.error("Failed to emit EXIT_INTENT_FILLED event: {}", e.getMessage());
        }
    }

    private void emitExitRejectedEvent(ExitIntent exitIntent, String reason) {
        try {
            Trade trade = tradeRepo.findById(exitIntent.tradeId()).orElse(null);
            if (trade == null)
                return;

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
                    "EXIT_ORDER_RECONCILER");
        } catch (Exception e) {
            log.error("Failed to emit EXIT_INTENT_FAILED event: {}", e.getMessage());
        }
    }

    private void emitExitCancelledEvent(ExitIntent exitIntent) {
        try {
            Trade trade = tradeRepo.findById(exitIntent.tradeId()).orElse(null);
            if (trade == null)
                return;

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
                    "EXIT_ORDER_RECONCILER");
        } catch (Exception e) {
            log.error("Failed to emit EXIT_INTENT_CANCELLED event: {}", e.getMessage());
        }
    }

    private void emitExitTimeoutEvent(ExitIntent exitIntent) {
        try {
            Trade trade = tradeRepo.findById(exitIntent.tradeId()).orElse(null);
            if (trade == null)
                return;

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
                    "EXIT_ORDER_RECONCILER");
        } catch (Exception e) {
            log.error("Failed to emit EXIT_TIMEOUT event: {}", e.getMessage());
        }
    }

    public ReconcileMetrics getMetrics() {
        return new ReconcileMetrics(
                lastReconcileCount,
                lastReconcileTime,
                totalReconciled,
                totalUpdates,
                totalTimeouts,
                totalRateLimited,
                brokerCallSemaphore.availablePermits());
    }

    public record ReconcileMetrics(
            long lastChecked,
            Instant lastRunTime,
            long totalChecked,
            long totalUpdated,
            long totalTimeouts,
            long totalRateLimited,
            int availablePermits) {
    }
}
