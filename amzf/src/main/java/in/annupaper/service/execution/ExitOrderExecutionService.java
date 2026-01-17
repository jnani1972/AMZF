package in.annupaper.service.execution;

import in.annupaper.domain.broker.BrokerAdapter;
import in.annupaper.infrastructure.broker.BrokerAdapterFactory;
import in.annupaper.domain.common.EventType;
import in.annupaper.domain.trade.ExitIntent;
import in.annupaper.domain.trade.Trade;
import in.annupaper.domain.repository.ExitIntentRepository;
import in.annupaper.domain.repository.TradeRepository;
import in.annupaper.domain.repository.UserBrokerRepository;
import in.annupaper.service.core.EventService;
import in.annupaper.service.trade.TradeManagementService;  // ✅ P0 fix: single-writer enforcement
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * ExitOrderExecutionService - Converts APPROVED exit intents into broker exit orders.
 *
 * Mirrors OrderExecutionService pattern for exits:
 * 1. Read APPROVED exit intent from DB
 * 2. Use DB function place_exit_order() to atomically transition to PLACED
 * 3. Call broker adapter to place exit order
 * 4. If broker rejects immediately: mark exit intent as FAILED
 * 5. If broker accepts: reconciler will update to FILLED → trade closes
 *
 * ENFORCEMENT CONTRACT:
 * - ONLY this service places exit orders with broker
 * - Uses DB function for atomic state transitions (prevents duplicate orders)
 * - Always updates intent status BEFORE calling broker (idempotent)
 * - Rejection path updates exit_intent to FAILED status
 *
 * See: EXIT_QUALIFICATION_ARCHITECTURE.md, ARCHITECTURE_STATUS_RESPONSE.md
 */
public final class ExitOrderExecutionService {
    private static final Logger log = LoggerFactory.getLogger(ExitOrderExecutionService.class);

    private final ExitIntentRepository exitIntentRepo;
    private final TradeRepository tradeRepo;  // Read-only: for fetching trade details
    private final TradeManagementService tradeManagementService;  // ✅ P0 fix: single-writer for trades
    private final UserBrokerRepository userBrokerRepo;
    private final BrokerAdapterFactory brokerFactory;
    private final EventService eventService;

    public ExitOrderExecutionService(
        ExitIntentRepository exitIntentRepo,
        TradeRepository tradeRepo,
        TradeManagementService tradeManagementService,  // ✅ P0 fix: single-writer enforcement
        UserBrokerRepository userBrokerRepo,
        BrokerAdapterFactory brokerFactory,
        EventService eventService
    ) {
        this.exitIntentRepo = exitIntentRepo;
        this.tradeRepo = tradeRepo;
        this.tradeManagementService = tradeManagementService;  // ✅ P0 fix
        this.userBrokerRepo = userBrokerRepo;
        this.brokerFactory = brokerFactory;
        this.eventService = eventService;
    }

    /**
     * Execute approved exit intent.
     *
     * Pattern: Update exit_intent status to PLACED FIRST (via DB function), THEN call broker.
     * This ensures idempotency - if this method is called twice, second call will fail at DB level.
     */
    public CompletableFuture<ExitIntent> executeExitIntent(ExitIntent intent) {
        if (!intent.isApproved()) {
            log.warn("Cannot execute non-approved exit intent: {} (status: {})",
                intent.exitIntentId(), intent.status());
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Exit intent is not approved: " + intent.status())
            );
        }

        // Get trade for exit details
        Trade trade = tradeRepo.findById(intent.tradeId()).orElse(null);
        if (trade == null) {
            log.error("Trade not found for exit intent {}: {}", intent.exitIntentId(), intent.tradeId());
            markExitIntentFailed(intent.exitIntentId(), "TRADE_NOT_FOUND", "Trade not found");
            return CompletableFuture.failedFuture(
                new IllegalStateException("Trade not found: " + intent.tradeId())
            );
        }

        if (!trade.isOpen()) {
            log.warn("Trade is not OPEN for exit intent {}: trade {} status={}",
                intent.exitIntentId(), trade.tradeId(), trade.status());
            markExitIntentFailed(intent.exitIntentId(), "TRADE_NOT_OPEN",
                "Trade status is " + trade.status());
            return CompletableFuture.failedFuture(
                new IllegalStateException("Trade is not OPEN: " + trade.status())
            );
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                // STEP 1: Atomically transition exit_intent from APPROVED → PLACED
                // This is idempotent - if already PLACED, DB function returns false
                String tempBrokerOrderId = "PENDING_" + System.currentTimeMillis();
                boolean transitioned = exitIntentRepo.placeExitOrder(
                    intent.exitIntentId(),
                    tempBrokerOrderId
                );

                if (!transitioned) {
                    log.warn("Exit intent {} already placed or not in APPROVED state", intent.exitIntentId());
                    return intent;  // Already processed, return as-is
                }

                log.info("✅ Exit intent transitioned to PLACED: {} for trade {}",
                    intent.exitIntentId(), trade.tradeId());

                // STEP 2: Get UserBroker to determine brokerCode
                in.annupaper.domain.broker.UserBroker userBroker =
                    userBrokerRepo.findById(intent.userBrokerId()).orElse(null);
                if (userBroker == null) {
                    log.error("UserBroker not found: {}", intent.userBrokerId());
                    markExitIntentFailed(intent.exitIntentId(), "NO_USER_BROKER", "UserBroker not found");
                    return intent;
                }

                // STEP 3: Get broker adapter
                BrokerAdapter broker = brokerFactory.getOrCreate(
                    intent.userBrokerId(),
                    userBroker.brokerId()
                );
                if (broker == null) {
                    log.error("Broker adapter not found for userBrokerId: {}", intent.userBrokerId());
                    markExitIntentFailed(intent.exitIntentId(), "NO_BROKER_ADAPTER",
                        "Broker adapter not found");
                    return intent;
                }

                // Build exit order request
                BrokerAdapter.OrderRequest orderRequest = buildExitOrderRequest(intent, trade);

                // Place exit order (async)
                CompletableFuture<BrokerAdapter.OrderResult> orderFuture = broker.placeOrder(orderRequest);

                // STEP 4: Handle broker response
                BrokerAdapter.OrderResult result = orderFuture.get();

                if (result.success()) {
                    // Broker accepted: update with real broker order ID
                    exitIntentRepo.updateBrokerOrderId(intent.exitIntentId(), result.orderId());
                    log.info("✅ Broker accepted exit order for intent {}: brokerOrderId={}",
                        intent.exitIntentId(), result.orderId());

                    // Emit EXIT_INTENT_PLACED event
                    emitExitOrderPlacedEvent(intent, trade, result.orderId());

                    // Update trade status to EXITING (optional - indicates exit order is placed)
                    updateTradeStatusToExiting(trade.tradeId(), result.orderId());

                    return intent;

                } else {
                    // Broker rejected: mark exit intent as FAILED
                    log.warn("⚠️ Broker rejected exit order for intent {}: {} - {}",
                        intent.exitIntentId(), result.errorCode(), result.message());

                    markExitIntentFailed(intent.exitIntentId(), result.errorCode(), result.message());

                    // Emit EXIT_INTENT_FAILED event
                    emitExitOrderRejectedEvent(intent, trade, result.errorCode(), result.message());

                    return intent;
                }

            } catch (Exception e) {
                log.error("Failed to execute exit intent {}: {}", intent.exitIntentId(), e.getMessage(), e);

                // Mark as FAILED on exception
                markExitIntentFailed(intent.exitIntentId(), "EXECUTION_ERROR", e.getMessage());

                throw new RuntimeException("Failed to execute exit intent", e);
            }
        });
    }

    /**
     * Build broker exit order request from exit intent and trade.
     */
    private BrokerAdapter.OrderRequest buildExitOrderRequest(ExitIntent intent, Trade trade) {
        // Determine transaction type - OPPOSITE of entry
        // If trade is BUY (LONG), exit is SELL
        // If trade is SELL (SHORT), exit is BUY
        String transactionType = "BUY".equals(trade.direction()) ? "SELL" : "BUY";

        // Determine exchange (default NSE for now)
        String exchange = "NSE";  // TODO: Get from trade.symbol() parsing

        // Determine validity (default DAY)
        String validity = "DAY";  // TODO: Make configurable

        return new BrokerAdapter.OrderRequest(
            trade.symbol(),
            exchange,
            transactionType,
            intent.orderType(),             // MARKET or LIMIT (from qualification)
            intent.productType(),           // Match entry product type
            intent.calculatedQty(),         // Quantity to exit
            intent.limitPrice(),            // Limit price if LIMIT order
            null,                           // triggerPrice (not used for exits)
            validity,
            intent.exitIntentId()           // tag = exitIntentId (for broker tracking)
        );
    }

    /**
     * Mark exit intent as FAILED.
     */
    private void markExitIntentFailed(String exitIntentId, String errorCode, String errorMessage) {
        try {
            exitIntentRepo.updateStatus(exitIntentId, "FAILED", errorCode, errorMessage);
            log.info("Exit intent {} marked as FAILED: {} - {}", exitIntentId, errorCode, errorMessage);
        } catch (Exception e) {
            log.error("Failed to update exit intent {} status to FAILED: {}",
                exitIntentId, e.getMessage());
        }
    }

    /**
     * Update trade status to EXITING (indicates exit order is in flight).
     */
    private void updateTradeStatusToExiting(String tradeId, String exitOrderId) {
        try {
            // ❌ REMOVED - P0 fix: single-writer enforcement
            // tradeRepo.updateExitOrderPlaced(tradeId, exitOrderId, Instant.now());

            // ✅ P0 fix: delegate to TradeManagementService (single-writer)
            tradeManagementService.updateTradeExitOrderPlaced(tradeId, exitOrderId, Instant.now());

            log.debug("Trade {} updated with exit order ID: {}", tradeId, exitOrderId);
        } catch (Exception e) {
            log.error("Failed to update trade {} with exit order ID: {}",
                tradeId, e.getMessage());
            // Non-fatal: reconciler will heal this
        }
    }

    /**
     * Emit EXIT_INTENT_PLACED event.
     */
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
            null,  // signalId (no entry signal for exits)
            null,  // intentId (entry intent)
            trade.tradeId(),
            brokerOrderId,
            "EXIT_ORDER_EXECUTION_SERVICE"
        );
    }

    /**
     * Emit EXIT_INTENT_FAILED event.
     */
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
            "EXIT_ORDER_EXECUTION_SERVICE"
        );
    }
}
