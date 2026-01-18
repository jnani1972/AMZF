package in.annupaper.application.service;

import in.annupaper.domain.model.*;
import in.annupaper.infrastructure.broker.BrokerAdapterFactory;
import in.annupaper.application.port.output.TradeRepository;
import in.annupaper.application.port.output.UserBrokerRepository;
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
 * Reconciles pending orders with broker reality.
 */
public final class PendingOrderReconciler {
    private static final Logger log = LoggerFactory.getLogger(PendingOrderReconciler.class);

    private final TradeRepository tradeRepository;
    private final UserBrokerRepository userBrokerRepository;
    private final BrokerAdapterFactory brokerFactory;
    private final ScheduledExecutorService scheduler;

    private final Duration reconcileInterval;
    private final Duration pendingTimeout;
    private final int maxConcurrentBrokerCalls;
    private final Semaphore brokerCallSemaphore;

    private long lastReconcileCount = 0;
    private long totalReconciled = 0;
    private long totalUpdates = 0;
    private long totalTimeouts = 0;
    private long totalRateLimited = 0;
    private Instant lastReconcileTime = Instant.now();

    public PendingOrderReconciler(
            TradeRepository tradeRepository,
            UserBrokerRepository userBrokerRepository,
            BrokerAdapterFactory brokerFactory) {
        this(tradeRepository, userBrokerRepository, brokerFactory,
                Duration.ofSeconds(30),
                Duration.ofMinutes(10),
                5);
    }

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
                r -> new Thread(r, "pending-order-reconciler"));
    }

    public void start() {
        long initialDelay = 10;
        long period = reconcileInterval.toSeconds();
        scheduler.scheduleAtFixedRate(this::reconcilePendingOrders, initialDelay, period, TimeUnit.SECONDS);
        log.info("Pending order reconciler started: interval={}s, timeout={}s", period, pendingTimeout.toSeconds());
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

    private void reconcilePendingOrders() {
        try {
            Instant start = Instant.now();
            List<Trade> pending = tradeRepository.findByStatus("PENDING");

            if (pending.isEmpty()) {
                lastReconcileCount = 0;
                lastReconcileTime = Instant.now();
                return;
            }

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

    private boolean reconcileTrade(Trade trade) {
        Instant lastUpdate = trade.lastBrokerUpdateAt() != null ? trade.lastBrokerUpdateAt() : trade.createdAt();
        Duration sinceLastUpdate = Duration.between(lastUpdate, Instant.now());

        if (sinceLastUpdate.compareTo(pendingTimeout) > 0) {
            log.warn("Trade {} pending timeout, marking as REJECTED", trade.tradeId());
            Trade timedOut = trade.withStatus("REJECTED");
            tradeRepository.upsert(timedOut);
            totalTimeouts++;
            return true;
        }

        if (!brokerCallSemaphore.tryAcquire()) {
            totalRateLimited++;
            return false;
        }

        try {
            UserBroker userBroker = userBrokerRepository.findById(trade.userBrokerId()).orElse(null);
            if (userBroker == null)
                return false;

            BrokerAdapter broker = brokerFactory.getOrCreate(trade.userBrokerId(), userBroker.brokerId());
            if (broker == null || !broker.isConnected())
                return false;

            BrokerAdapter.BrokerOrderStatus status = broker.getOrderStatus(trade.brokerOrderId()).join();
            if (status == null)
                return false;

            Trade withTimestamp = trade.withLastBrokerUpdateAt(Instant.now());
            Trade updated = updateFromBrokerStatus(withTimestamp, status);

            if (hasChanged(trade, updated)) {
                tradeRepository.upsert(updated);
                log.info("Reconciled trade {}: {} → {}", trade.tradeId(), trade.status(), updated.status());
                return true;
            }

            tradeRepository.upsert(withTimestamp);
            return false;

        } catch (Exception e) {
            log.error("Failed to query broker for trade {}: {}", trade.tradeId(), e.getMessage());
            return false;
        } finally {
            brokerCallSemaphore.release();
        }
    }

    private boolean hasChanged(Trade before, Trade after) {
        boolean statusChanged = !before.status().equals(after.status());
        boolean qtyChanged = before.entryQty() != after.entryQty();
        boolean priceChanged = (before.entryPrice() == null && after.entryPrice() != null) ||
                (before.entryPrice() != null && after.entryPrice() == null) ||
                (before.entryPrice() != null && after.entryPrice() != null
                        && before.entryPrice().compareTo(after.entryPrice()) != 0);

        return statusChanged || qtyChanged || priceChanged;
    }

    private Trade updateFromBrokerStatus(Trade trade, BrokerAdapter.BrokerOrderStatus status) {
        String brokerStatus = status.status();

        if ("COMPLETE".equalsIgnoreCase(brokerStatus) || "FILLED".equalsIgnoreCase(brokerStatus)) {
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
                    "FILLED",
                    trade.currentPrice(), trade.currentLogReturn(), trade.unrealizedPnl(),
                    trade.trailingActive(), trade.trailingHighestPrice(), trade.trailingStopPrice(),
                    trade.exitPrice(), trade.exitTimestamp(), trade.exitTrigger(), trade.exitOrderId(),
                    trade.realizedPnl(), trade.realizedLogReturn(), trade.holdingDays(),
                    trade.brokerOrderId(), trade.brokerTradeId(), trade.clientOrderId(),
                    trade.lastBrokerUpdateAt(), trade.createdAt(), Instant.now(), trade.deletedAt(),
                    trade.version() + 1);
        }

        if ("REJECTED".equalsIgnoreCase(brokerStatus)) {
            return trade.withStatus("REJECTED");
        }

        if ("CANCELLED".equalsIgnoreCase(brokerStatus)) {
            return trade.withStatus("CANCELLED");
        }

        return trade;
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
