package in.annupaper.application.service;

import in.annupaper.domain.model.*;
import in.annupaper.application.port.output.TradeIntentRepository;
import in.annupaper.application.port.output.UserBrokerRepository;
import in.annupaper.application.port.output.SignalDeliveryRepository;
import in.annupaper.application.port.output.SignalRepository;
import in.annupaper.service.core.EventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * Execution Orchestrator.
 * Handles signal fan-out: one signal -> validation per (user, broker) -> trade
 * intents.
 */
public final class ExecutionOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(ExecutionOrchestrator.class);

    private final TradeIntentRepository tradeIntentRepo;
    private final UserBrokerRepository userBrokerRepo;
    private final ValidationService validationService;
    private final EventService eventService;
    private final Function<String, ValidationService.UserContext> userContextProvider;
    private final SignalDeliveryRepository signalDeliveryRepo;
    private final SignalRepository signalRepo;
    private final in.annupaper.application.port.input.TradeManagementService tradeManagementService;

    // Executor for parallel validation
    private final ExecutorService executor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(),
            r -> {
                Thread t = new Thread(r, "exec-orchestrator");
                t.setDaemon(true);
                return t;
            });

    public ExecutionOrchestrator(
            TradeIntentRepository tradeIntentRepo,
            UserBrokerRepository userBrokerRepo,
            ValidationService validationService,
            EventService eventService,
            Function<String, ValidationService.UserContext> userContextProvider,
            SignalDeliveryRepository signalDeliveryRepo,
            SignalRepository signalRepo,
            in.annupaper.application.port.input.TradeManagementService tradeManagementService) {
        this.tradeIntentRepo = tradeIntentRepo;
        this.userBrokerRepo = userBrokerRepo;
        this.validationService = validationService;
        this.eventService = eventService;
        this.userContextProvider = userContextProvider;
        this.signalDeliveryRepo = signalDeliveryRepo;
        this.signalRepo = signalRepo;
        this.tradeManagementService = tradeManagementService;
    }

    /**
     * ✅ P0: Process pending signal deliveries and create trade intents.
     */
    public List<TradeIntent> processPendingDeliveries() {
        log.info("Processing pending signal deliveries");

        // Query for pending deliveries (CREATED status)
        List<SignalDelivery> pendingDeliveries = signalDeliveryRepo.findPendingDeliveries();

        if (pendingDeliveries.isEmpty()) {
            log.debug("No pending deliveries to process");
            return List.of();
        }

        log.info("Processing {} pending deliveries", pendingDeliveries.size());

        // Validate in parallel
        List<CompletableFuture<TradeIntent>> futures = new ArrayList<>();

        for (SignalDelivery delivery : pendingDeliveries) {
            CompletableFuture<TradeIntent> future = CompletableFuture.supplyAsync(
                    () -> processDelivery(delivery),
                    executor);
            futures.add(future);
        }

        // Collect results
        List<TradeIntent> intents = new ArrayList<>();
        for (CompletableFuture<TradeIntent> future : futures) {
            try {
                TradeIntent intent = future.get(5, TimeUnit.SECONDS);
                if (intent != null) {
                    intents.add(intent);
                    emitIntentEvent(intent);
                }
            } catch (Exception e) {
                log.error("Failed to process intent: {}", e.getMessage());
            }
        }

        // Summary
        long passed = intents.stream().filter(TradeIntent::validationPassed).count();
        long failed = intents.size() - passed;
        log.info("Delivery processing complete: {} passed, {} rejected", passed, failed);

        return intents;
    }

    /**
     * ✅ P0: Process a single delivery - validate, create intent, consume
     * atomically.
     */
    private TradeIntent processDelivery(SignalDelivery delivery) {
        String intentId = UUID.randomUUID().toString();

        try {
            // Get signal from repository
            Signal signal = signalRepo.findById(delivery.signalId()).orElse(null);
            if (signal == null) {
                log.warn("Signal not found for delivery {}: {}", delivery.deliveryId(), delivery.signalId());
                // Mark delivery as rejected
                signalDeliveryRepo.updateStatus(delivery.deliveryId(), "REJECTED");
                return null;
            }

            // Get user-broker from repository
            UserBroker userBroker = userBrokerRepo.findById(delivery.userBrokerId()).orElse(null);
            if (userBroker == null) {
                log.warn("UserBroker not found for delivery {}: {}", delivery.deliveryId(), delivery.userBrokerId());
                // Mark delivery as rejected
                signalDeliveryRepo.updateStatus(delivery.deliveryId(), "REJECTED");
                return null;
            }

            // Get user context
            ValidationService.UserContext userContext = userContextProvider.apply(userBroker.userId());
            if (userContext == null) {
                log.warn("No user context for {}", userBroker.userId());
                // Create rejected intent and mark delivery as consumed
                TradeIntent rejectedIntent = createRejectedIntent(intentId, signal, userBroker, "No user context");
                tradeIntentRepo.insert(rejectedIntent);
                signalDeliveryRepo.consumeDelivery(delivery.deliveryId(), intentId);
                return rejectedIntent;
            }

            // Validate
            ValidationResult result = validationService.validate(signal, userBroker, userContext);

            // Create intent
            IntentStatus status = result.passed() ? IntentStatus.APPROVED : IntentStatus.REJECTED;

            TradeIntent intent = new TradeIntent(
                    intentId,
                    signal.signalId(),
                    userBroker.userId(),
                    userBroker.brokerId(),
                    userBroker.userBrokerId(),
                    result.passed(),
                    result.errors(),
                    result.calculatedQty(),
                    result.calculatedValue(),
                    result.orderType(),
                    result.limitPrice(),
                    result.productType(),
                    result.logImpact(),
                    result.portfolioExposureAfter(),
                    status,
                    null, // orderId
                    null, // tradeId
                    Instant.now(),
                    Instant.now(),
                    null, // executedAt
                    null, // errorCode
                    null, // errorMessage
                    null, // deletedAt
                    1 // version
            );

            // Persist trade intent
            tradeIntentRepo.insert(intent);

            // ✅ P0: Consume delivery atomically (AV-5 enforcement)
            boolean consumed = signalDeliveryRepo.consumeDelivery(delivery.deliveryId(), intentId);
            if (!consumed) {
                log.warn("Failed to consume delivery {} (race condition or already consumed)", delivery.deliveryId());
            }

            log.debug("Processed delivery {}: intent {} ({})",
                    delivery.deliveryId(), intentId, result.passed() ? "APPROVED" : "REJECTED");

            // ✅ P0-E: Forward APPROVED intent to TradeManagementService
            if (intent.validationPassed()) {
                tradeManagementService.onIntentApproved(intent);
            }

            return intent;

        } catch (Exception e) {
            log.error("Delivery processing error for {}: {}", delivery.deliveryId(), e.getMessage());
            // Mark delivery as rejected
            signalDeliveryRepo.updateStatus(delivery.deliveryId(), "REJECTED");
            return null;
        }
    }

    private TradeIntent createRejectedIntent(String intentId, Signal signal, UserBroker userBroker, String error) {
        return new TradeIntent(
                intentId,
                signal.signalId(),
                userBroker.userId(),
                userBroker.brokerId(),
                userBroker.userBrokerId(),
                false,
                List.of(),
                0, BigDecimal.ZERO, null, null, null, null, null,
                IntentStatus.REJECTED,
                null, null,
                Instant.now(), Instant.now(), null,
                "VALIDATION_ERROR", error,
                null, 1);
    }

    /**
     * Emit event for trade intent.
     */
    private void emitIntentEvent(TradeIntent intent) {
        EventType eventType = intent.validationPassed()
                ? EventType.INTENT_APPROVED
                : EventType.INTENT_REJECTED;

        Map<String, Object> payload = new HashMap<>();
        payload.put("intentId", intent.intentId());
        payload.put("signalId", intent.signalId());
        payload.put("passed", intent.validationPassed());
        payload.put("status", intent.status().name());

        if (intent.validationPassed()) {
            payload.put("qty", intent.calculatedQty());
            payload.put("value", intent.calculatedValue());
            payload.put("orderType", intent.orderType());
            payload.put("productType", intent.productType());
        } else {
            payload.put("errors", intent.validationErrors());
        }

        eventService.emitUserBroker(
                eventType,
                intent.userId(),
                intent.brokerId(),
                intent.userBrokerId(),
                payload,
                intent.signalId(),
                intent.intentId(),
                null, // tradeId
                null, // orderId
                "SYSTEM");
    }

    /**
     * Shutdown executor.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}
