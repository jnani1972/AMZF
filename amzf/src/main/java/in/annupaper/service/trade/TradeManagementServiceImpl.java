package in.annupaper.service.trade;

import in.annupaper.domain.broker.BrokerAdapter;
import in.annupaper.infrastructure.broker.BrokerAdapterFactory;
import in.annupaper.domain.common.EventType;
import in.annupaper.domain.trade.ExitReason;
import in.annupaper.domain.signal.Signal;
import in.annupaper.domain.trade.Trade;
import in.annupaper.domain.trade.TradeIntent;
import in.annupaper.domain.broker.UserBroker;
import in.annupaper.domain.repository.SignalRepository;
import in.annupaper.domain.repository.TradeRepository;
import in.annupaper.domain.repository.UserBrokerRepository;
import in.annupaper.service.core.EventService;
import in.annupaper.service.signal.BrickMovementTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * TradeManagementServiceImpl - Single Owner of Trade Lifecycle.
 *
 * ENFORCEMENT CONTRACT (CRITICAL):
 * - ONLY this service mutates trade state
 * - All operations routed through TradeCoordinator (actor model)
 * - Each trade processed sequentially (prevents race conditions)
 * - All broker interactions owned here
 * - All state transitions owned here
 *
 * STATE MACHINE:
 * CREATED → PENDING → OPEN → EXITING → CLOSED
 *    ↓         ↓       ↓        ↓         ↓
 * Entry    Entry   Exit    Exit      Final
 * Order    Fill    Order   Fill      P&L
 *
 * INPUTS (event-driven):
 * 1. onIntentApproved() - from validation layer
 * 2. onBrokerOrderUpdate() - from WebSocket/reconciler
 * 3. onPriceUpdate() - from tick stream
 * 4. reconcilePendingTrades() - from scheduler
 *
 * See: TRADE_MANAGEMENT_ARCHITECTURE.md
 */
public final class TradeManagementServiceImpl implements TradeManagementService {
    private static final Logger log = LoggerFactory.getLogger(TradeManagementServiceImpl.class);

    // Core infrastructure
    private final TradeCoordinator coordinator;
    private final ActiveTradeIndex activeIndex;

    // Repositories (persistence adapters)
    private final TradeRepository tradeRepo;
    private final SignalRepository signalRepo;
    private final UserBrokerRepository userBrokerRepo;

    // Providers (read-only services)
    private final BrokerAdapterFactory brokerFactory;
    private final EventService eventService;
    private final BrickMovementTracker brickTracker;

    // Configuration
    private final int maxHoldingDays;

    public TradeManagementServiceImpl(
        TradeRepository tradeRepo,
        SignalRepository signalRepo,
        UserBrokerRepository userBrokerRepo,
        BrokerAdapterFactory brokerFactory,
        EventService eventService,
        BrickMovementTracker brickTracker
    ) {
        this.coordinator = new TradeCoordinator();
        this.activeIndex = new ActiveTradeIndex();

        this.tradeRepo = tradeRepo;
        this.signalRepo = signalRepo;
        this.userBrokerRepo = userBrokerRepo;
        this.brokerFactory = brokerFactory;
        this.eventService = eventService;
        this.brickTracker = brickTracker;

        this.maxHoldingDays = 30;  // TODO: Make configurable

        log.info("TradeManagementService initialized with {} executor partitions",
            coordinator.getPartitionCount());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ENTRY FLOW: Intent → Trade → Order
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    public Trade createTradeForIntent(TradeIntent intent, Signal signal) {
        // ✅ SINGLE WRITER ENFORCEMENT: Only TMS creates trades
        // This method is the ONLY entry point for trade creation

        // Calculate NEWBUY/REBUY classification
        List<Trade> existingTrades = tradeRepo.findByUserAndSymbol(
            intent.userId(),
            signal.symbol()
        );

        TradeClassifier.TradeClassification classification =
            TradeClassifier.classify(intent.userId(), signal.symbol(), existingTrades);

        // Generate trade ID
        String tradeId = UUID.randomUUID().toString();

        // Create trade row (status=CREATED)
        Trade trade = createTradeRow(tradeId, intent, signal, classification);

        // ✅ PERSIST TO DATABASE (single-writer enforcement - P0 fix)
        tradeRepo.insert(trade);

        log.info("✅ TMS: Trade created AND PERSISTED: {} ({}#{} for {})",
            tradeId, classification.entryKind(), classification.tradeNumber(), signal.symbol());

        return trade;
    }

    @Override
    public void onIntentApproved(TradeIntent intent) {
        // Route to trade's executor partition (not intent ID, use future trade ID)
        String futureTradeId = UUID.randomUUID().toString();

        coordinator.execute(futureTradeId, () -> {
            try {
                handleIntentApproved(intent, futureTradeId);
            } catch (Exception e) {
                log.error("Failed to handle approved intent: {} - {}",
                    intent.intentId(), e.getMessage(), e);
            }
        });
    }

    /**
     * Handle approved intent (runs on coordinator executor).
     *
     * Steps:
     * 1. Get signal for trade details
     * 2. Calculate NEWBUY/REBUY classification
     * 3. Create trade row (status=CREATED)
     * 4. Place entry order at broker
     * 5. Update trade: brokerOrderId, status=PENDING
     */
    private void handleIntentApproved(TradeIntent intent, String tradeId) {
        log.info("Handling approved intent: {} → trade {}", intent.intentId(), tradeId);

        // Step 1: Get signal
        Signal signal = signalRepo.findById(intent.signalId()).orElse(null);
        if (signal == null) {
            log.error("Signal not found for intent: {}", intent.signalId());
            return;
        }

        // Step 2: Calculate trade classification (NEWBUY vs REBUY)
        List<Trade> existingTrades = tradeRepo.findByUserAndSymbol(
            intent.userId(),
            signal.symbol()
        );

        TradeClassifier.TradeClassification classification =
            TradeClassifier.classify(intent.userId(), signal.symbol(), existingTrades);

        // Step 3: Create trade row (status=CREATED)
        Trade trade = createTradeRow(tradeId, intent, signal, classification);
        tradeRepo.upsert(trade);

        log.info("Trade created: {} ({}#{} for {})",
            tradeId, classification.entryKind(), classification.tradeNumber(), signal.symbol());

        // Step 4: Place entry order
        placeEntryOrder(trade, intent, signal);
    }

    /**
     * Create trade row with all fields initialized.
     *
     * @param tradeId Generated trade ID
     * @param intent Approved intent
     * @param signal Original signal
     * @param classification NEWBUY/REBUY classification
     * @return New Trade instance (status=CREATED)
     */
    private Trade createTradeRow(
        String tradeId,
        TradeIntent intent,
        Signal signal,
        TradeClassifier.TradeClassification classification
    ) {
        Instant now = Instant.now();

        return new Trade(
            tradeId,
            intent.userId(),               // portfolioId = userId
            intent.userId(),
            intent.brokerId(),
            intent.userBrokerId(),
            signal.signalId(),
            intent.intentId(),
            signal.symbol(),
            signal.direction().name(),     // direction (BUY | SELL) from signal
            classification.tradeNumber(),  // ✅ Calculated (not hardcoded!)

            // Entry details (pending fill)
            intent.limitPrice(),           // entryPrice (will update on fill)
            intent.calculatedQty(),
            intent.calculatedValue(),
            null,                          // entryTimestamp (null until filled)
            intent.productType(),

            // Entry MTF context (from signal)
            signal.htfZone(),
            signal.itfZone(),
            signal.ltfZone(),
            signal.confluenceType(),
            signal.confluenceScore(),
            signal.htfLow(),
            signal.htfHigh(),
            signal.itfLow(),
            signal.itfHigh(),
            signal.ltfLow(),
            signal.ltfHigh(),
            signal.effectiveFloor(),
            signal.effectiveCeiling(),

            // Risk management (TODO: calculate if needed)
            null,  // logLossAtFloor
            null,  // maxLogLossAllowed

            // Exit targets
            null,  // exitMinProfitPrice
            null,  // exitTargetPrice
            null,  // exitStretchPrice
            signal.effectiveCeiling(),  // exitPrimaryPrice

            // Current status
            "CREATED",                     // ✅ Initial state
            null,                          // currentPrice
            null,                          // currentLogReturn
            null,                          // unrealizedPnl

            // Trailing stop
            false,                         // trailingActive
            null,                          // trailingHighestPrice
            null,                          // trailingStopPrice

            // Exit details (null for new trade)
            null,                          // exitPrice
            null,                          // exitTimestamp
            null,                          // exitTrigger
            null,                          // exitOrderId
            null,                          // realizedPnl
            null,                          // realizedLogReturn
            null,                          // holdingDays

            // Broker tracking
            null,                          // brokerOrderId (set after order placed)
            null,                          // brokerTradeId
            intent.intentId(),             // clientOrderId (idempotency key)
            now,                           // lastBrokerUpdateAt

            // Timestamps
            now,                           // createdAt
            now,                           // updatedAt
            null,                          // deletedAt
            1                              // version
        );
    }

    /**
     * Place entry order at broker.
     *
     * @param trade Trade row (already created)
     * @param intent Approved intent
     * @param signal Original signal
     */
    private void placeEntryOrder(Trade trade, TradeIntent intent, Signal signal) {
        // Get UserBroker for broker details
        UserBroker userBroker = userBrokerRepo.findById(intent.userBrokerId()).orElse(null);
        if (userBroker == null) {
            log.error("UserBroker not found: {}", intent.userBrokerId());
            markTradeRejected(trade.tradeId(), "NO_USER_BROKER", "UserBroker not found");
            return;
        }

        // Get broker adapter
        BrokerAdapter broker = brokerFactory.getOrCreate(
            intent.userBrokerId(),
            userBroker.brokerId()
        );

        if (broker == null || !broker.isConnected()) {
            log.error("Broker not available: {}", userBroker.brokerId());
            markTradeRejected(trade.tradeId(), "BROKER_UNAVAILABLE", "Broker not connected");
            return;
        }

        // Build order request
        BrokerAdapter.OrderRequest orderRequest = buildEntryOrderRequest(intent, signal);

        // Place order (async)
        broker.placeOrder(orderRequest)
            .thenAccept(result -> {
                // Handle on same executor partition
                coordinator.execute(trade.tradeId(), () -> {
                    handleEntryOrderResult(trade, result);
                });
            })
            .exceptionally(ex -> {
                coordinator.execute(trade.tradeId(), () -> {
                    log.error("Entry order placement failed: {} - {}",
                        trade.tradeId(), ex.getMessage());
                    markTradeRejected(trade.tradeId(), "ORDER_FAILED", ex.getMessage());
                });
                return null;
            });
    }

    /**
     * Build broker order request for entry.
     */
    private BrokerAdapter.OrderRequest buildEntryOrderRequest(TradeIntent intent, Signal signal) {
        String transactionType = "LONG".equals(signal.direction()) ? "BUY" : "SELL";
        String exchange = "NSE";  // TODO: Get from symbol metadata
        String validity = "DAY";

        return new BrokerAdapter.OrderRequest(
            signal.symbol(),
            exchange,
            transactionType,
            intent.orderType(),
            intent.productType(),
            intent.calculatedQty(),
            intent.limitPrice(),
            null,  // triggerPrice (TODO: support SL orders)
            validity,
            intent.intentId()  // tag = intentId for idempotency
        );
    }

    /**
     * Handle entry order result from broker.
     */
    private void handleEntryOrderResult(Trade trade, BrokerAdapter.OrderResult result) {
        if (result.success()) {
            // Order accepted
            Trade updated = trade.withBrokerOrderId(result.orderId())
                .withStatus("PENDING")
                .withLastBrokerUpdateAt(Instant.now());

            tradeRepo.upsert(updated);

            log.info("Entry order accepted: {} → orderId={}", trade.tradeId(), result.orderId());

            emitEvent(EventType.ORDER_CREATED, trade, Map.of(
                "brokerOrderId", result.orderId(),
                "status", "PENDING"
            ));

        } else {
            // Order rejected
            markTradeRejected(trade.tradeId(), result.errorCode(), result.message());
        }
    }

    /**
     * Mark trade as REJECTED.
     */
    private void markTradeRejected(String tradeId, String errorCode, String errorMessage) {
        Trade trade = tradeRepo.findById(tradeId).orElse(null);
        if (trade == null) {
            log.warn("Trade not found for rejection: {}", tradeId);
            return;
        }

        Trade updated = trade.withStatus("REJECTED")
            .withLastBrokerUpdateAt(Instant.now());

        tradeRepo.upsert(updated);

        log.warn("Trade rejected: {} - {} ({})", tradeId, errorCode, errorMessage);

        emitEvent(EventType.ORDER_REJECTED, trade, Map.of(
            "errorCode", errorCode,
            "errorMessage", errorMessage
        ));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // BROKER UPDATES: Fill Detection & State Transitions
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    public void onBrokerOrderUpdate(BrokerOrderUpdate update) {
        // Find trade by broker order ID or client order ID
        Trade trade = findTradeByOrderId(update);

        if (trade == null) {
            log.warn("Trade not found for broker update: orderId={} clientOrderId={}",
                update.orderId(), update.clientOrderId());
            return;
        }

        // Route to trade's executor partition
        coordinator.execute(trade.tradeId(), () -> {
            handleBrokerOrderUpdate(trade, update);
        });
    }

    /**
     * Find trade by broker order ID or client order ID.
     */
    private Trade findTradeByOrderId(BrokerOrderUpdate update) {
        // Try broker order ID first
        if (update.orderId() != null) {
            Trade trade = tradeRepo.findByBrokerOrderId(update.orderId());
            if (trade != null) return trade;
        }

        // Fall back to client order ID (intent ID)
        if (update.clientOrderId() != null) {
            return tradeRepo.findByIntentId(update.clientOrderId());
        }

        return null;
    }

    /**
     * Handle broker order update (fill, rejection, etc.).
     */
    private void handleBrokerOrderUpdate(Trade trade, BrokerOrderUpdate update) {
        log.debug("Broker update for trade {}: status={} filled={}/{}",
            trade.tradeId(), update.status(), update.filledQty(), trade.entryQty());

        String newStatus = mapBrokerStatus(update.status(), trade);

        // Handle based on new status
        switch (newStatus) {
            case "OPEN" -> handleEntryFill(trade, update);
            case "CLOSED" -> handleExitFill(trade, update);
            case "REJECTED" -> markTradeRejected(trade.tradeId(),
                update.rejectReason(), "Broker rejected");
            default -> log.debug("Trade {} remains in status {}", trade.tradeId(), newStatus);
        }
    }

    /**
     * Map broker status to our trade status.
     */
    private String mapBrokerStatus(String brokerStatus, Trade trade) {
        return switch (brokerStatus.toUpperCase()) {
            case "COMPLETE", "FILLED" -> trade.status().equals("EXITING") ? "CLOSED" : "OPEN";
            case "REJECTED", "CANCELLED" -> "REJECTED";
            case "PENDING", "OPEN" -> "PENDING";
            default -> "PENDING";
        };
    }

    /**
     * Handle entry order fill.
     */
    private void handleEntryFill(Trade trade, BrokerOrderUpdate update) {
        Trade updated = trade.withStatus("OPEN")
            .withEntryPrice(update.avgPrice())
            .withEntryTimestamp(update.updateTimestamp())
            .withLastBrokerUpdateAt(Instant.now());

        tradeRepo.upsert(updated);

        // Add to active index for exit monitoring
        activeIndex.addTrade(trade.tradeId(), trade.symbol());

        log.info("Trade filled and OPEN: {} @ {} (qty={})",
            trade.tradeId(), update.avgPrice(), update.filledQty());

        emitEvent(EventType.TRADE_UPDATED, trade, Map.of(
            "entryPrice", update.avgPrice(),
            "entryQty", update.filledQty()
        ));
    }

    /**
     * Handle exit order fill.
     */
    private void handleExitFill(Trade trade, BrokerOrderUpdate update) {
        // Calculate P&L
        BigDecimal entryValue = trade.entryPrice().multiply(BigDecimal.valueOf(trade.entryQty()));
        BigDecimal exitValue = update.avgPrice().multiply(update.filledQty());
        BigDecimal realizedPnl = exitValue.subtract(entryValue);

        // Calculate holding days
        long holdingDays = Duration.between(trade.entryTimestamp(), Instant.now()).toDays();

        Trade updated = trade.withStatus("CLOSED")
            .withExitPrice(update.avgPrice())
            .withExitTimestamp(update.updateTimestamp())
            .withRealizedPnl(realizedPnl)
            .withHoldingDays((int) holdingDays)
            .withLastBrokerUpdateAt(Instant.now());

        tradeRepo.upsert(updated);

        // Remove from active index
        activeIndex.removeTrade(trade.tradeId());

        log.info("Trade CLOSED: {} P&L={} days={}",
            trade.tradeId(), realizedPnl, holdingDays);

        emitEvent(EventType.TRADE_CLOSED, trade, Map.of(
            "exitPrice", update.avgPrice(),
            "realizedPnl", realizedPnl,
            "holdingDays", holdingDays
        ));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // EXIT MONITORING: Price Updates & Exit Conditions
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    public void onPriceUpdate(String symbol, BigDecimal ltp, Instant timestamp) {
        // Get all open trades for this symbol
        Set<String> openTradeIds = activeIndex.getOpenTrades(symbol);

        if (openTradeIds.isEmpty()) {
            return;  // No open trades for this symbol
        }

        // Check exit conditions for each trade
        for (String tradeId : openTradeIds) {
            coordinator.execute(tradeId, () -> {
                checkAndPlaceExitOrder(tradeId, ltp);
            });
        }
    }

    /**
     * Check exit conditions for a trade and place exit order if qualified.
     */
    private void checkAndPlaceExitOrder(String tradeId, BigDecimal currentPrice) {
        Trade trade = tradeRepo.findById(tradeId).orElse(null);

        if (trade == null || !"OPEN".equals(trade.status())) {
            // Trade may have closed in meantime
            activeIndex.removeTrade(tradeId);
            return;
        }

        // Evaluate exit conditions
        ExitReason exitReason = evaluateExitConditions(trade, currentPrice);

        if (exitReason != null) {
            placeExitOrder(trade, currentPrice, exitReason);
        }
    }

    /**
     * Evaluate exit conditions for a trade.
     *
     * @return ExitReason if exit should trigger, null otherwise
     */
    private ExitReason evaluateExitConditions(Trade trade, BigDecimal currentPrice) {
        // 1. Check target hit
        if (trade.exitPrimaryPrice() != null &&
            currentPrice.compareTo(trade.exitPrimaryPrice()) >= 0) {
            return ExitReason.TARGET_HIT;
        }

        // 2. Check stop loss
        if (trade.entryEffectiveFloor() != null &&
            currentPrice.compareTo(trade.entryEffectiveFloor()) <= 0) {
            return ExitReason.STOP_LOSS;
        }

        // 3. Check time exit
        if (isMaxHoldTimeExceeded(trade)) {
            return ExitReason.TIME_BASED;
        }

        return null;  // No exit condition met
    }

    /**
     * Check if max holding time exceeded.
     */
    private boolean isMaxHoldTimeExceeded(Trade trade) {
        if (trade.entryTimestamp() == null) {
            return false;
        }

        Duration holdTime = Duration.between(trade.entryTimestamp(), Instant.now());
        return holdTime.toDays() >= maxHoldingDays;
    }

    /**
     * Place exit order at broker.
     */
    private void placeExitOrder(Trade trade, BigDecimal exitPrice, ExitReason exitReason) {
        log.info("Placing exit order: {} @ {} (reason: {})",
            trade.tradeId(), exitPrice, exitReason);

        // TODO: Implement exit order placement
        // 1. Mark trade EXITING
        // 2. Build exit order (reverse direction)
        // 3. Place at broker
        // 4. Store exit order ID

        log.warn("⚠️ Exit order placement not yet implemented - emitting event only");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TRAILING STOP: Dynamic Stop Loss Updates
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    public void updateTrailingStop(String tradeId, BigDecimal highestPrice, BigDecimal stopPrice, boolean activate) {
        coordinator.execute(tradeId, () -> {
            Trade trade = tradeRepo.findById(tradeId).orElse(null);

            if (trade == null || !"OPEN".equals(trade.status())) {
                return;  // Trade closed or doesn't exist
            }

            // Only update if new highest price or activation
            boolean shouldUpdate = activate ||
                (trade.trailingHighestPrice() != null &&
                 highestPrice.compareTo(trade.trailingHighestPrice()) > 0);

            if (!shouldUpdate) {
                return;
            }

            // Create updated trade with new trailing stop values
            Trade updated = new Trade(
                trade.tradeId(), trade.portfolioId(), trade.userId(), trade.brokerId(),
                trade.userBrokerId(), trade.signalId(), trade.intentId(), trade.symbol(),
                trade.direction(), trade.tradeNumber(),
                trade.entryPrice(), trade.entryQty(), trade.entryValue(), trade.entryTimestamp(),
                trade.productType(),
                trade.entryHtfZone(), trade.entryItfZone(), trade.entryLtfZone(),
                trade.entryConfluenceType(), trade.entryConfluenceScore(),
                trade.entryHtfLow(), trade.entryHtfHigh(), trade.entryItfLow(),
                trade.entryItfHigh(), trade.entryLtfLow(), trade.entryLtfHigh(),
                trade.entryEffectiveFloor(), trade.entryEffectiveCeiling(),
                trade.logLossAtFloor(), trade.maxLogLossAllowed(),
                trade.exitMinProfitPrice(), trade.exitTargetPrice(), trade.exitStretchPrice(),
                trade.exitPrimaryPrice(),
                trade.status(), trade.currentPrice(), trade.currentLogReturn(), trade.unrealizedPnl(),
                activate || trade.trailingActive(),  // Activate if requested
                highestPrice,                         // Update highest price
                stopPrice,                            // Update stop price
                trade.exitPrice(), trade.exitTimestamp(), trade.exitTrigger(), trade.exitOrderId(),
                trade.realizedPnl(), trade.realizedLogReturn(), trade.holdingDays(),
                trade.brokerOrderId(), trade.brokerTradeId(), trade.clientOrderId(),
                trade.lastBrokerUpdateAt(),
                trade.createdAt(), Instant.now(), trade.deletedAt(), trade.version()
            );

            tradeRepo.upsert(updated);

            if (activate) {
                log.info("✅ Trailing stop ACTIVATED: {} highest={} stop={}",
                    tradeId, highestPrice, stopPrice);
            } else {
                log.debug("Trailing stop updated: {} highest={} stop={}",
                    tradeId, highestPrice, stopPrice);
            }
        });
    }

    @Override
    public void updateTradeExitOrderPlaced(String tradeId, String exitOrderId, Instant placedAt) {
        coordinator.execute(tradeId, () -> {
            Trade trade = tradeRepo.findById(tradeId).orElse(null);

            if (trade == null) {
                log.warn("Cannot update exit order - trade not found: {}", tradeId);
                return;
            }

            Trade updated = new Trade(
                trade.tradeId(), trade.portfolioId(), trade.userId(), trade.brokerId(),
                trade.userBrokerId(), trade.signalId(), trade.intentId(), trade.symbol(),
                trade.direction(), trade.tradeNumber(),
                trade.entryPrice(), trade.entryQty(), trade.entryValue(), trade.entryTimestamp(),
                trade.productType(),
                trade.entryHtfZone(), trade.entryItfZone(), trade.entryLtfZone(),
                trade.entryConfluenceType(), trade.entryConfluenceScore(),
                trade.entryHtfLow(), trade.entryHtfHigh(), trade.entryItfLow(),
                trade.entryItfHigh(), trade.entryLtfLow(), trade.entryLtfHigh(),
                trade.entryEffectiveFloor(), trade.entryEffectiveCeiling(),
                trade.logLossAtFloor(), trade.maxLogLossAllowed(),
                trade.exitMinProfitPrice(), trade.exitTargetPrice(), trade.exitStretchPrice(),
                trade.exitPrimaryPrice(),
                "EXITING",
                trade.currentPrice(), trade.currentLogReturn(), trade.unrealizedPnl(),
                trade.trailingActive(), trade.trailingHighestPrice(), trade.trailingStopPrice(),
                trade.exitPrice(), trade.exitTimestamp(), trade.exitTrigger(),
                exitOrderId,
                trade.realizedPnl(), trade.realizedLogReturn(), trade.holdingDays(),
                trade.brokerOrderId(), trade.brokerTradeId(), trade.clientOrderId(),
                trade.lastBrokerUpdateAt(),
                trade.createdAt(), Instant.now(), trade.deletedAt(), trade.version()
            );

            tradeRepo.upsert(updated);
            log.info("✅ TMS: Trade exit order placed: {} orderId={}", tradeId, exitOrderId);
        });
    }

    @Override
    public void closeTradeOnExitFill(String tradeId, BigDecimal exitPrice, Integer exitQty, String exitReason, Instant exitTimestamp) {
        coordinator.execute(tradeId, () -> {
            Trade trade = tradeRepo.findById(tradeId).orElse(null);

            if (trade == null) {
                log.warn("Cannot close trade - trade not found: {}", tradeId);
                return;
            }

            BigDecimal realizedPnl = calculatePnL(trade, exitPrice, exitQty);
            BigDecimal realizedLogReturn = calculateLogReturn(trade, exitPrice);
            Integer holdingDays = calculateHoldingDays(trade, exitTimestamp);

            Trade closedTrade = new Trade(
                trade.tradeId(), trade.portfolioId(), trade.userId(), trade.brokerId(),
                trade.userBrokerId(), trade.signalId(), trade.intentId(), trade.symbol(),
                trade.direction(), trade.tradeNumber(),
                trade.entryPrice(), trade.entryQty(), trade.entryValue(), trade.entryTimestamp(),
                trade.productType(),
                trade.entryHtfZone(), trade.entryItfZone(), trade.entryLtfZone(),
                trade.entryConfluenceType(), trade.entryConfluenceScore(),
                trade.entryHtfLow(), trade.entryHtfHigh(), trade.entryItfLow(),
                trade.entryItfHigh(), trade.entryLtfLow(), trade.entryLtfHigh(),
                trade.entryEffectiveFloor(), trade.entryEffectiveCeiling(),
                trade.logLossAtFloor(), trade.maxLogLossAllowed(),
                trade.exitMinProfitPrice(), trade.exitTargetPrice(), trade.exitStretchPrice(),
                trade.exitPrimaryPrice(),
                "CLOSED",
                trade.currentPrice(), trade.currentLogReturn(), trade.unrealizedPnl(),
                trade.trailingActive(), trade.trailingHighestPrice(), trade.trailingStopPrice(),
                exitPrice, exitTimestamp, exitReason, trade.exitOrderId(),
                realizedPnl, realizedLogReturn, holdingDays,
                trade.brokerOrderId(), trade.brokerTradeId(), trade.clientOrderId(),
                trade.lastBrokerUpdateAt(),
                trade.createdAt(), Instant.now(), trade.deletedAt(), trade.version()
            );

            tradeRepo.upsert(closedTrade);
            log.info("✅ TMS: Trade CLOSED: {} exitPrice={} P&L={} reason={}",
                tradeId, exitPrice, realizedPnl, exitReason);
        });
    }

    private BigDecimal calculatePnL(Trade trade, BigDecimal exitPrice, Integer exitQty) {
        if ("BUY".equals(trade.direction())) {
            return exitPrice.subtract(trade.entryPrice()).multiply(BigDecimal.valueOf(exitQty));
        } else {
            return trade.entryPrice().subtract(exitPrice).multiply(BigDecimal.valueOf(exitQty));
        }
    }

    private BigDecimal calculateLogReturn(Trade trade, BigDecimal exitPrice) {
        if (trade.entryPrice() == null || trade.entryPrice().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        if ("BUY".equals(trade.direction())) {
            return BigDecimal.valueOf(Math.log(exitPrice.divide(trade.entryPrice(), 6, java.math.RoundingMode.HALF_UP).doubleValue()));
        } else {
            return BigDecimal.valueOf(Math.log(trade.entryPrice().divide(exitPrice, 6, java.math.RoundingMode.HALF_UP).doubleValue()));
        }
    }

    private Integer calculateHoldingDays(Trade trade, Instant exitTimestamp) {
        if (trade.entryTimestamp() == null) {
            return null;
        }
        long days = java.time.Duration.between(trade.entryTimestamp(), exitTimestamp).toDays();
        return (int) days;  // Safe cast: holding period unlikely to exceed Integer.MAX_VALUE
    }

    @Override
    public void markTradeRejectedByIntentId(String intentId, String errorCode, String errorMessage) {
        Trade trade = tradeRepo.findByIntentId(intentId);
        if (trade == null) {
            log.warn("Cannot mark trade rejected - no trade found for intentId: {}", intentId);
            return;
        }

        coordinator.execute(trade.tradeId(), () -> {
            Trade current = tradeRepo.findById(trade.tradeId()).orElse(null);
            if (current == null) {
                return;
            }

            Trade rejected = new Trade(
                current.tradeId(), current.portfolioId(), current.userId(), current.brokerId(),
                current.userBrokerId(), current.signalId(), current.intentId(), current.symbol(),
                current.direction(), current.tradeNumber(),
                current.entryPrice(), current.entryQty(), current.entryValue(), current.entryTimestamp(),
                current.productType(),
                current.entryHtfZone(), current.entryItfZone(), current.entryLtfZone(),
                current.entryConfluenceType(), current.entryConfluenceScore(),
                current.entryHtfLow(), current.entryHtfHigh(), current.entryItfLow(),
                current.entryItfHigh(), current.entryLtfLow(), current.entryLtfHigh(),
                current.entryEffectiveFloor(), current.entryEffectiveCeiling(),
                current.logLossAtFloor(), current.maxLogLossAllowed(),
                current.exitMinProfitPrice(), current.exitTargetPrice(), current.exitStretchPrice(),
                current.exitPrimaryPrice(),
                "REJECTED",
                current.currentPrice(), current.currentLogReturn(), current.unrealizedPnl(),
                current.trailingActive(), current.trailingHighestPrice(), current.trailingStopPrice(),
                current.exitPrice(), current.exitTimestamp(), errorMessage, current.exitOrderId(),
                current.realizedPnl(), current.realizedLogReturn(), current.holdingDays(),
                current.brokerOrderId(), current.brokerTradeId(), current.clientOrderId(),
                current.lastBrokerUpdateAt(),
                current.createdAt(), Instant.now(), current.deletedAt(), current.version()
            );

            tradeRepo.upsert(rejected);
            log.info("✅ TMS: Trade REJECTED: {} error={} message={}",
                trade.tradeId(), errorCode, errorMessage);
        });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // RECONCILIATION: Scheduled State Healing
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    public void reconcilePendingTrades() {
        // TODO: Implement reconciliation
        // Query all PENDING/EXITING trades
        // For each: query broker for status
        // Update state based on broker reality

        log.debug("Reconciliation not yet implemented");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // STARTUP: Index Rebuild
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    public void rebuildActiveIndex() {
        List<Trade> openTrades = tradeRepo.findByStatus("OPEN");

        List<ActiveTradeIndex.TradeSymbolPair> pairs = openTrades.stream()
            .map(t -> new ActiveTradeIndex.TradeSymbolPair(t.tradeId(), t.symbol()))
            .toList();

        activeIndex.rebuild(pairs);

        log.info("Active trade index rebuilt: {} open trades", pairs.size());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Emit event for trade lifecycle change.
     */
    private void emitEvent(EventType eventType, Trade trade, Map<String, Object> additionalPayload) {
        Map<String, Object> payload = new HashMap<>(additionalPayload);
        payload.put("tradeId", trade.tradeId());
        payload.put("symbol", trade.symbol());

        eventService.emitUserBroker(
            eventType,
            trade.userId(),
            trade.brokerId(),
            trade.userBrokerId(),
            payload,
            trade.signalId(),
            trade.intentId(),
            trade.tradeId(),
            trade.brokerOrderId(),
            "TRADE_MANAGEMENT_SERVICE"
        );
    }

    @Override
    public void shutdown() {
        log.info("Shutting down TradeManagementService");
        coordinator.shutdown();
    }
}
