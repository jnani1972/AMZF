package in.annupaper.application.service;

import in.annupaper.domain.model.*;
import in.annupaper.infrastructure.broker.BrokerAdapterFactory;
import in.annupaper.application.port.output.*;
import in.annupaper.application.port.input.TradeManagementService;
import in.annupaper.service.core.EventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * ExitOrderExecutionService - Converts APPROVED exit intents into broker exit
 * orders.
 */
public final class ExitOrderExecutionService {
    private static final Logger log = LoggerFactory.getLogger(ExitOrderExecutionService.class);

    private final ExitIntentRepository exitIntentRepo;
    private final TradeRepository tradeRepo;
    private final TradeManagementService tradeManagementService;
    private final UserBrokerRepository userBrokerRepo;
    private final BrokerAdapterFactory brokerFactory;
    private final EventService eventService;

    public ExitOrderExecutionService(
            ExitIntentRepository exitIntentRepo,
            TradeRepository tradeRepo,
            TradeManagementService tradeManagementService,
            UserBrokerRepository userBrokerRepo,
            BrokerAdapterFactory brokerFactory,
            EventService eventService) {
        this.exitIntentRepo = exitIntentRepo;
        this.tradeRepo = tradeRepo;
        this.tradeManagementService = tradeManagementService;
        this.userBrokerRepo = userBrokerRepo;
        this.brokerFactory = brokerFactory;
        this.eventService = eventService;
    }

    public CompletableFuture<ExitIntent> executeExitIntent(ExitIntent intent) {
        if (!intent.isApproved()) {
            log.warn("Cannot execute non-approved exit intent: {} (status: {})",
                    intent.exitIntentId(), intent.status());
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Exit intent is not approved: " + intent.status()));
        }

        Trade trade = tradeRepo.findById(intent.tradeId()).orElse(null);
        if (trade == null) {
            log.error("Trade not found for exit intent {}: {}", intent.exitIntentId(), intent.tradeId());
            markExitIntentFailed(intent.exitIntentId(), "TRADE_NOT_FOUND", "Trade not found");
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Trade not found: " + intent.tradeId()));
        }

        if (!trade.isOpen()) {
            log.warn("Trade is not OPEN for exit intent {}: trade {} status={}",
                    intent.exitIntentId(), trade.tradeId(), trade.status());
            markExitIntentFailed(intent.exitIntentId(), "TRADE_NOT_OPEN",
                    "Trade status is " + trade.status());
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Trade is not OPEN: " + trade.status()));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String tempBrokerOrderId = "PENDING_" + System.currentTimeMillis();
                boolean transitioned = exitIntentRepo.placeExitOrder(
                        intent.exitIntentId(),
                        tempBrokerOrderId);

                if (!transitioned) {
                    log.warn("Exit intent {} already placed or not in APPROVED state", intent.exitIntentId());
                    return intent;
                }

                log.info("✅ Exit intent transitioned to PLACED: {} for trade {}",
                        intent.exitIntentId(), trade.tradeId());

                UserBroker userBroker = userBrokerRepo.findById(intent.userBrokerId()).orElse(null);
                if (userBroker == null) {
                    log.error("UserBroker not found: {}", intent.userBrokerId());
                    markExitIntentFailed(intent.exitIntentId(), "NO_USER_BROKER", "UserBroker not found");
                    return intent;
                }

                BrokerAdapter broker = brokerFactory.getOrCreate(
                        intent.userBrokerId(),
                        userBroker.brokerId());
                if (broker == null) {
                    log.error("Broker adapter not found for userBrokerId: {}", intent.userBrokerId());
                    markExitIntentFailed(intent.exitIntentId(), "NO_BROKER_ADAPTER",
                            "Broker adapter not found");
                    return intent;
                }

                BrokerAdapter.BrokerOrderRequest orderRequest = buildExitOrderRequest(intent, trade);

                CompletableFuture<BrokerAdapter.OrderResult> orderFuture = broker.placeOrder(orderRequest);

                BrokerAdapter.OrderResult result = orderFuture.get();

                if (result.success()) {
                    exitIntentRepo.updateBrokerOrderId(intent.exitIntentId(), result.orderId());
                    log.info("✅ Broker accepted exit order for intent {}: brokerOrderId={}",
                            intent.exitIntentId(), result.orderId());

                    emitExitOrderPlacedEvent(intent, trade, result.orderId());
                    updateTradeStatusToExiting(trade.tradeId(), result.orderId());

                    return intent;

                } else {
                    log.warn("⚠️ Broker rejected exit order for intent {}: {} - {}",
                            intent.exitIntentId(), result.errorCode(), result.message());

                    markExitIntentFailed(intent.exitIntentId(), result.errorCode(), result.message());
                    emitExitOrderRejectedEvent(intent, trade, result.errorCode(), result.message());

                    return intent;
                }

            } catch (Exception e) {
                log.error("Failed to execute exit intent {}: {}", intent.exitIntentId(), e.getMessage(), e);
                markExitIntentFailed(intent.exitIntentId(), "EXECUTION_ERROR", e.getMessage());
                throw new RuntimeException("Failed to execute exit intent", e);
            }
        });
    }

    private BrokerAdapter.BrokerOrderRequest buildExitOrderRequest(ExitIntent intent, Trade trade) {
        String transactionType = "BUY".equals(trade.direction()) ? "SELL" : "BUY";
        String exchange = "NSE"; // TODO: Get from trade metadata
        String validity = "DAY";

        return new BrokerAdapter.BrokerOrderRequest(
                trade.symbol(),
                exchange,
                transactionType,
                intent.orderType(),
                intent.productType(),
                intent.calculatedQty(),
                intent.limitPrice(),
                null,
                validity,
                intent.exitIntentId());
    }

    private void markExitIntentFailed(String exitIntentId, String errorCode, String errorMessage) {
        try {
            exitIntentRepo.updateStatus(exitIntentId, "FAILED", errorCode, errorMessage);
            log.info("Exit intent {} marked as FAILED: {} - {}", exitIntentId, errorCode, errorMessage);
        } catch (Exception e) {
            log.error("Failed to update exit intent {} status to FAILED: {}",
                    exitIntentId, e.getMessage());
        }
    }

    private void updateTradeStatusToExiting(String tradeId, String exitOrderId) {
        try {
            tradeManagementService.updateTradeExitOrderPlaced(tradeId, exitOrderId, Instant.now());
            log.debug("Trade {} updated with exit order ID: {}", tradeId, exitOrderId);
        } catch (Exception e) {
            log.error("Failed to update trade {} with exit order ID: {}",
                    tradeId, e.getMessage());
        }
    }

    private void emitExitOrderPlacedEvent(ExitIntent intent, Trade trade, String brokerOrderId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("exitIntentId", intent.exitIntentId());
        payload.put("tradeId", intent.tradeId());
        payload.put("exitReason", intent.exitReason());
        payload.put("brokerOrderId", brokerOrderId);
        payload.put("qty", intent.calculatedQty());
        payload.put("orderType", intent.orderType());
        payload.put("limitPrice", intent.limitPrice());
        payload.put("symbol", trade.symbol());

        eventService.emitUserBroker(
                EventType.EXIT_INTENT_PLACED,
                trade.userId(),
                trade.brokerId(),
                intent.userBrokerId(),
                payload,
                null,
                null,
                trade.tradeId(),
                brokerOrderId,
                "EXIT_ORDER_EXECUTION_SERVICE");
    }

    private void emitExitOrderRejectedEvent(ExitIntent intent, Trade trade,
            String errorCode, String errorMessage) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("exitIntentId", intent.exitIntentId());
        payload.put("tradeId", intent.tradeId());
        payload.put("exitReason", intent.exitReason());
        payload.put("errorCode", errorCode);
        payload.put("errorMessage", errorMessage);
        payload.put("symbol", trade.symbol());

        eventService.emitUserBroker(
                EventType.EXIT_INTENT_FAILED,
                trade.userId(),
                trade.brokerId(),
                intent.userBrokerId(),
                payload,
                null,
                null,
                trade.tradeId(),
                null,
                "EXIT_ORDER_EXECUTION_SERVICE");
    }
}
