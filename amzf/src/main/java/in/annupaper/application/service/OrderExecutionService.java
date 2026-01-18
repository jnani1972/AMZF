package in.annupaper.application.service;

import in.annupaper.domain.model.*;
import in.annupaper.application.port.input.TradeManagementService;
import in.annupaper.application.port.output.*;
import in.annupaper.infrastructure.broker.BrokerAdapterFactory;
import in.annupaper.service.core.EventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Order Execution Service - Broker Order Placement.
 *
 * Flow:
 * 1. Call TradeManagementService.createTradeForIntent() to create trade row
 * 2. Persist trade row with status=CREATED
 * 3. Call broker adapter to place order
 * 4. If broker rejects immediately: mark trade as REJECTED
 * 5. If broker accepts: reconciler will update to PENDING -> OPEN
 */
public final class OrderExecutionService {
    private static final Logger log = LoggerFactory.getLogger(OrderExecutionService.class);

    // ⚠️ PRODUCTION SAFETY: Trading disabled by default
    private static final boolean TRADING_ENABLED = Boolean.parseBoolean(
            System.getenv().getOrDefault("TRADING_ENABLED", "false"));

    static {
        if (!TRADING_ENABLED) {
            log.warn("⚠️ ⚠️ ⚠️  TRADING DISABLED - Set TRADING_ENABLED=true to enable live orders ⚠️ ⚠️ ⚠️");
        } else {
            log.info("✅ TRADING ENABLED - Live orders will be placed");
        }
    }

    private final TradeManagementService tradeManagementService;
    private final SignalRepository signalRepo;
    private final UserBrokerRepository userBrokerRepo;
    private final BrokerAdapterFactory brokerFactory;
    private final EventService eventService;

    public OrderExecutionService(
            TradeManagementService tradeManagementService,
            SignalRepository signalRepo,
            UserBrokerRepository userBrokerRepo,
            BrokerAdapterFactory brokerFactory,
            EventService eventService) {
        this.tradeManagementService = tradeManagementService;
        this.signalRepo = signalRepo;
        this.userBrokerRepo = userBrokerRepo;
        this.brokerFactory = brokerFactory;
        this.eventService = eventService;
    }

    /**
     * Execute approved trade intent.
     */
    public CompletableFuture<Trade> executeIntent(TradeIntent intent) {
        if (!TRADING_ENABLED) {
            log.error("[ORDER REJECTED] ⛔ TRADING DISABLED globally. Intent: {}", intent.intentId());
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Trading disabled - Set TRADING_ENABLED=true to enable"));
        }

        BrokerAdapter adapter = brokerFactory.get(intent.userBrokerId());

        // Check for READ-ONLY mode (stale feed)
        boolean readOnlyMode = false;
        if (adapter instanceof in.annupaper.infrastructure.broker.adapters.FyersAdapter f) {
            readOnlyMode = !f.canPlaceOrders();
        }

        if (readOnlyMode) {
            log.error("[ORDER REJECTED] ⛔ System in READ-ONLY mode (stale feed). Intent: {}", intent.intentId());
            return CompletableFuture.failedFuture(
                    new IllegalStateException("System in READ-ONLY mode - feed disconnected or stale"));
        }

        if (!intent.validationPassed()) {
            log.warn("Cannot execute rejected intent: {}", intent.intentId());
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Intent did not pass validation"));
        }

        // Get signal for trade details
        Signal signal = signalRepo.findById(intent.signalId()).orElse(null);
        if (signal == null) {
            log.error("Signal not found for intent {}: {}", intent.intentId(), intent.signalId());
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Signal not found: " + intent.signalId()));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                // ✅ STEP 1: Call TMS to create AND PERSIST trade
                Trade createdTrade = tradeManagementService.createTradeForIntent(intent, signal);
                log.info("✅ Trade created AND PERSISTED via TMS: {} for intent {}",
                        createdTrade.tradeId(), intent.intentId());

                // ✅ STEP 2: Get UserBroker to determine brokerCode
                UserBroker userBroker = userBrokerRepo.findById(intent.userBrokerId()).orElse(null);
                if (userBroker == null) {
                    log.error("UserBroker not found: {}", intent.userBrokerId());
                    tradeManagementService.markTradeRejectedByIntentId(intent.intentId(), "NO_USER_BROKER",
                            "UserBroker not found");
                    return createdTrade;
                }

                // ✅ STEP 3: Get broker adapter
                BrokerAdapter broker = brokerFactory.getOrCreate(intent.userBrokerId(), userBroker.brokerId());
                if (broker == null) {
                    log.error("Broker adapter not found for userBrokerId: {}", intent.userBrokerId());
                    tradeManagementService.markTradeRejectedByIntentId(intent.intentId(), "NO_BROKER_ADAPTER",
                            "Broker adapter not found");
                    return createdTrade;
                }

                // Build order request
                BrokerAdapter.BrokerOrderRequest orderRequest = buildOrderRequest(intent, signal);

                // Place order (async)
                CompletableFuture<BrokerAdapter.OrderResult> orderFuture = broker.placeOrder(orderRequest);

                // ✅ STEP 4: Handle broker response
                BrokerAdapter.OrderResult result = orderFuture.get();

                if (result.success()) {
                    log.info("✅ Broker accepted order for intent {}: brokerOrderId={}",
                            intent.intentId(), result.orderId());

                    // Emit ORDER_PLACED event
                    emitOrderPlacedEvent(intent, result.orderId());

                    return createdTrade;

                } else {
                    log.warn("⚠️ Broker rejected order for intent {}: {} - {}",
                            intent.intentId(), result.errorCode(), result.message());

                    tradeManagementService.markTradeRejectedByIntentId(
                            intent.intentId(),
                            result.errorCode(),
                            result.message());

                    // Emit ORDER_REJECTED event
                    emitOrderRejectedEvent(intent, result.errorCode(), result.message());

                    return createdTrade;
                }

            } catch (Exception e) {
                log.error("Failed to execute intent {}: {}", intent.intentId(), e.getMessage());

                tradeManagementService.markTradeRejectedByIntentId(
                        intent.intentId(),
                        "EXECUTION_ERROR",
                        e.getMessage());

                throw new RuntimeException("Failed to execute intent", e);
            }
        });
    }

    /**
     * Build broker order request from intent and signal.
     */
    private BrokerAdapter.BrokerOrderRequest buildOrderRequest(TradeIntent intent, Signal signal) {
        String transactionType = Direction.BUY.equals(signal.direction()) ? "BUY" : "SELL";
        String exchange = "NSE"; // TODO: Get from symbol metadata
        String validity = "DAY"; // TODO: Make configurable

        return new BrokerAdapter.BrokerOrderRequest(
                signal.symbol(),
                exchange,
                transactionType,
                intent.orderType(), // MARKET, LIMIT, SL, SL-M
                intent.productType(), // CNC, MIS, NRML
                intent.calculatedQty(),
                intent.limitPrice(),
                null, // triggerPrice (TODO: support SL orders)
                validity,
                intent.intentId() // tag = intentId (for broker tracking)
        );
    }

    /**
     * Emit ORDER_PLACED event.
     */
    private void emitOrderPlacedEvent(TradeIntent intent, String brokerOrderId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("intentId", intent.intentId());
        payload.put("signalId", intent.signalId());
        payload.put("brokerOrderId", brokerOrderId);
        payload.put("qty", intent.calculatedQty());
        payload.put("orderType", intent.orderType());
        payload.put("limitPrice", intent.limitPrice());

        eventService.emitUserBroker(
                EventType.ORDER_CREATED,
                intent.userId(),
                intent.brokerId(),
                intent.userBrokerId(),
                payload,
                intent.signalId(),
                intent.intentId(),
                null, // tradeId
                brokerOrderId,
                "ORDER_EXECUTION_SERVICE");
    }

    /**
     * Emit ORDER_REJECTED event.
     */
    private void emitOrderRejectedEvent(TradeIntent intent, String errorCode, String errorMessage) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("intentId", intent.intentId());
        payload.put("signalId", intent.signalId());
        payload.put("errorCode", errorCode);
        payload.put("errorMessage", errorMessage);

        eventService.emitUserBroker(
                EventType.ORDER_REJECTED,
                intent.userId(),
                intent.brokerId(),
                intent.userBrokerId(),
                payload,
                intent.signalId(),
                intent.intentId(),
                null, // tradeId
                null, // orderId
                "ORDER_EXECUTION_SERVICE");
    }
}
