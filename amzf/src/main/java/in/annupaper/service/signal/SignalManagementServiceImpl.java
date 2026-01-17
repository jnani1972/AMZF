package in.annupaper.service.signal;

import in.annupaper.domain.broker.BrokerRole;
import in.annupaper.domain.common.EventType;
import in.annupaper.domain.signal.ExitSignal;
import in.annupaper.domain.signal.Signal;
import in.annupaper.domain.signal.SignalType;
import in.annupaper.domain.trade.Direction;
import in.annupaper.domain.trade.ExitIntent;
import in.annupaper.domain.trade.ExitIntentStatus;
import in.annupaper.domain.trade.ExitReason;
import in.annupaper.domain.trade.Trade;
import in.annupaper.domain.broker.UserBroker;
import in.annupaper.domain.repository.SignalRepository;
import in.annupaper.domain.repository.SignalDeliveryRepository;
import in.annupaper.domain.repository.ExitSignalRepository;
import in.annupaper.domain.repository.ExitIntentRepository;
import in.annupaper.domain.repository.TradeRepository;
import in.annupaper.domain.repository.UserBrokerRepository;
import in.annupaper.service.core.EventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * SignalManagementServiceImpl - Single Owner of Signal Lifecycle.
 *
 * ENFORCEMENT CONTRACT (CRITICAL):
 * - ONLY this service mutates signals, signal_deliveries, exit_signals
 * - All operations routed through coordinators (actor model)
 * - Entry: sequential per symbol (EntrySignalCoordinator)
 * - Exit: sequential per trade (ExitSignalCoordinator)
 * - All idempotency enforced at DB level
 *
 * See: SignalManagementService Final Architecture Document
 */
public final class SignalManagementServiceImpl implements SignalManagementService {
    private static final Logger log = LoggerFactory.getLogger(SignalManagementServiceImpl.class);

    // Core infrastructure
    private final EntrySignalCoordinator entryCoordinator;
    private final ExitSignalCoordinator exitCoordinator;
    private final SignalDeliveryIndex deliveryIndex;

    // Repositories
    private final SignalRepository signalRepo;
    private final SignalDeliveryRepository signalDeliveryRepo;
    private final ExitSignalRepository exitSignalRepo;
    private final ExitIntentRepository exitIntentRepo;
    private final TradeRepository tradeRepo;
    private final UserBrokerRepository userBrokerRepo;
    private final EventService eventService;
    private final in.annupaper.service.execution.ExecutionOrchestrator executionOrchestrator;

    // Exit qualification
    private final in.annupaper.service.validation.ExitQualificationService exitQualificationService;

    // Timestamp tracking (AV-8)
    private final ConcurrentHashMap<String, Instant> lastProcessedTimes = new ConcurrentHashMap<>();

    // Exit re-arm cooldown (AV-6)
    private final ConcurrentHashMap<ExitKey, Instant> lastExitTimes = new ConcurrentHashMap<>();

    // Configuration
    private static final int MARKET_CLOSE_BUFFER_SECONDS = 60;
    private static final int EXIT_REARM_COOLDOWN_SECONDS = 30;

    // Scheduled tasks
    private final ScheduledExecutorService expiryScheduler;

    public SignalManagementServiceImpl(
        SignalRepository signalRepo,
        SignalDeliveryRepository signalDeliveryRepo,
        ExitSignalRepository exitSignalRepo,
        ExitIntentRepository exitIntentRepo,
        TradeRepository tradeRepo,
        UserBrokerRepository userBrokerRepo,
        EventService eventService,
        in.annupaper.service.execution.ExecutionOrchestrator executionOrchestrator,
        in.annupaper.service.validation.ExitQualificationService exitQualificationService
    ) {
        this.entryCoordinator = new EntrySignalCoordinator();
        this.exitCoordinator = new ExitSignalCoordinator();
        this.deliveryIndex = new SignalDeliveryIndex();

        this.signalRepo = signalRepo;
        this.signalDeliveryRepo = signalDeliveryRepo;
        this.exitSignalRepo = exitSignalRepo;
        this.exitIntentRepo = exitIntentRepo;
        this.tradeRepo = tradeRepo;
        this.userBrokerRepo = userBrokerRepo;
        this.eventService = eventService;
        this.executionOrchestrator = executionOrchestrator;
        this.exitQualificationService = exitQualificationService;

        // AV-13 FIX: SMS-owned expiry scheduler
        this.expiryScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "signal-expiry-scheduler");
            t.setDaemon(true);
            return t;
        });

        expiryScheduler.scheduleAtFixedRate(
            this::expireStaleSignals,
            1, 1, TimeUnit.MINUTES
        );

        log.info("SignalManagementService initialized with {} entry partitions, {} exit partitions",
            entryCoordinator.getPartitionCount(), exitCoordinator.getPartitionCount());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ENTRY SIGNAL LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    public void onSignalDetected(SignalCandidate candidate) {
        // AV-11 FIX: Suppress near market close
        if (isMarketClosingSoon(MARKET_CLOSE_BUFFER_SECONDS)) {
            log.info("Suppressing signal (market closing soon): {}", candidate.symbol());
            return;
        }

        // AV-8 FIX: Timestamp guard
        if (isOutOfOrder(candidate.symbol(), candidate.timestamp())) {
            log.warn("Ignoring out-of-order signal: {} at {}",
                candidate.symbol(), candidate.timestamp());
            return;
        }

        // Route to EntryCoordinator
        entryCoordinator.execute(candidate.symbol(), () -> {
            handleSignalDetected(candidate);
        });
    }

    /**
     * Handle signal detection (runs in symbol's partition).
     */
    private void handleSignalDetected(SignalCandidate candidate) {
        log.debug("Processing signal: {} {} ({})",
            candidate.symbol(), candidate.direction(), candidate.confluenceType());

        // 1. AV-10: Check for overlapping zones (auto-supersede)
        supersede(candidate);

        // 2. Create signal
        Signal signal = createSignal(candidate);

        // 3. Persist (DB dedupe constraint enforces uniqueness)
        try {
            signalRepo.insert(signal);
            log.info("Signal persisted: {} ({})", signal.signalId(), signal.symbol());
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("idx_signal_dedupe")) {
                log.info("Duplicate signal rejected by DB: {} {} (dedupe)",
                    signal.symbol(), signal.confluenceType());
                return;  // AV-1: DB enforces idempotency
            }
            throw e;
        }

        // 4. Transition to PUBLISHED
        Signal published = signal; // TODO: Add withStatus helper
        signalRepo.updateStatus(published.signalId(), "PUBLISHED");

        // 5. Create deliveries (fan-out to user-brokers)
        createDeliveries(published);

        // 6. Process deliveries (validate and create intents)
        executionOrchestrator.processPendingDeliveries();

        // 7. Emit SIGNAL_PUBLISHED event
        emitSignalPublished(published);

        // 7. Update timestamp tracking
        lastProcessedTimes.put(candidate.symbol(), candidate.timestamp());
    }

    @Override
    public void onPriceUpdate(String symbol, BigDecimal price, Instant timestamp) {
        // AV-8 FIX: Timestamp guard
        if (isOutOfOrder(symbol, timestamp)) {
            return;
        }

        entryCoordinator.execute(symbol, () -> {
            checkPriceInvalidation(symbol, price);
            lastProcessedTimes.put(symbol, timestamp);
        });
    }

    @Override
    public void cancelSignal(String signalId, String reason) {
        Signal signal = signalRepo.findById(signalId).orElse(null);
        if (signal == null) {
            log.warn("Cannot cancel non-existent signal: {}", signalId);
            return;
        }

        entryCoordinator.execute(signal.symbol(), () -> {
            handleCancelSignal(signalId, reason);
        });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // EXIT SIGNAL LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    public void onExitDetected(ExitCandidate candidate) {
        // AV-12 FIX: Suppress exits outside market hours
        if (!isMarketOpen()) {
            log.debug("Suppressing exit signal (market closed): {} {}",
                candidate.tradeId(), candidate.exitReason());
            return;
        }

        // AV-6 FIX: Check re-arm cooldown
        ExitKey key = new ExitKey(candidate.tradeId(), candidate.exitReason());
        Instant lastExit = lastExitTimes.get(key);
        if (lastExit != null) {
            long secondsSince = Duration.between(lastExit, Instant.now()).getSeconds();
            if (secondsSince < EXIT_REARM_COOLDOWN_SECONDS) {
                log.debug("Exit suppressed (cooldown): {} {} ({}s since last)",
                    candidate.tradeId(), candidate.exitReason(), secondsSince);
                return;
            }
        }

        exitCoordinator.execute(candidate.tradeId(), () -> {
            handleExitDetected(candidate);
        });
    }

    @Override
    public void confirmExitSignal(String exitSignalId) {
        ExitSignal exitSignal = exitSignalRepo.findById(exitSignalId);
        if (exitSignal == null) {
            log.warn("Cannot confirm non-existent exit signal: {}", exitSignalId);
            return;
        }

        // Update status to CONFIRMED
        exitSignalRepo.updateStatus(exitSignalId, "CONFIRMED");

        log.info("Exit signal confirmed: {} for trade {}", exitSignalId, exitSignal.tradeId());

        // Emit EXIT_CONFIRMED event
        Map<String, Object> payload = new HashMap<>();
        payload.put("exitSignalId", exitSignalId);
        payload.put("tradeId", exitSignal.tradeId());
        payload.put("exitReason", exitSignal.exitReason().name());
        payload.put("exitPrice", exitSignal.exitPrice());
        eventService.emitGlobal(EventType.EXIT_SIGNAL_CONFIRMED, payload, "SMS");
    }

    @Override
    public void cancelExitSignal(String exitSignalId, String reason) {
        ExitSignal exitSignal = exitSignalRepo.findById(exitSignalId);
        if (exitSignal == null) {
            log.warn("Cannot cancel non-existent exit signal: {}", exitSignalId);
            return;
        }

        // Cancel exit signal
        exitSignalRepo.cancel(exitSignalId);

        log.info("Exit signal cancelled: {} for trade {} (reason: {})",
            exitSignalId, exitSignal.tradeId(), reason);

        // Emit EXIT_CANCELLED event
        Map<String, Object> payload = new HashMap<>();
        payload.put("exitSignalId", exitSignalId);
        payload.put("tradeId", exitSignal.tradeId());
        payload.put("reason", reason);
        eventService.emitGlobal(EventType.EXIT_SIGNAL_CANCELLED, payload, "SMS");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SCHEDULED OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    public void expireStaleSignals() {
        // AV-13 FIX: Routes through EntryCoordinator
        List<Signal> expiring = signalRepo.findExpiringSoon(Duration.ofMinutes(1));

        for (Signal signal : expiring) {
            entryCoordinator.execute(signal.symbol(), () -> {
                expireSignal(signal.signalId(), "EOD");
            });
        }
    }

    @Override
    public void rebuildDeliveryIndex() {
        log.info("Rebuilding delivery index from database...");

        // Fetch all active deliveries from DB
        List<SignalDeliveryIndex.DeliveryIndexEntry> activeDeliveries =
            signalDeliveryRepo.findAllActiveForIndex();

        // Rebuild index
        deliveryIndex.rebuild(activeDeliveries);

        log.info("✓ Delivery index rebuilt: {} active deliveries", activeDeliveries.size());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // QUERY OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    public List<Signal> getActiveSignals(String symbol) {
        return signalRepo.findBySymbolAndStatus(symbol, "PUBLISHED");
    }

    @Override
    public List<SignalDelivery> getUserDeliveries(String userId, String status) {
        List<SignalDeliveryRepository.SignalDelivery> deliveries =
            signalDeliveryRepo.findByUserId(userId, status);

        return deliveries.stream()
            .map(d -> new SignalDelivery(
                d.deliveryId(),
                d.signalId(),
                d.userBrokerId(),
                d.userId(),
                d.status(),
                d.intentId(),
                d.createdAt(),
                d.deliveredAt(),
                d.consumedAt()
            ))
            .toList();
    }

    @Override
    public List<ExitSignal> getTradeExitSignals(String tradeId) {
        return exitSignalRepo.findByTradeId(tradeId);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════

    private Signal createSignal(SignalCandidate candidate) {
        String signalId = UUID.randomUUID().toString();

        return new Signal(
            signalId,
            candidate.symbol(),
            Direction.valueOf(candidate.direction()),
            SignalType.ENTRY,
            candidate.htfZone(),
            candidate.itfZone(),
            candidate.ltfZone(),
            candidate.confluenceType(),
            candidate.confluenceScore(),
            candidate.pWin(),
            candidate.pFill(),
            candidate.kelly(),
            candidate.refPrice(),
            candidate.refBid(),
            candidate.refAsk(),
            candidate.entryLow(),
            candidate.entryHigh(),
            candidate.htfLow(),
            candidate.htfHigh(),
            candidate.itfLow(),
            candidate.itfHigh(),
            candidate.ltfLow(),
            candidate.ltfHigh(),
            candidate.effectiveFloor(),
            candidate.effectiveCeiling(),
            candidate.confidence(),
            candidate.reason(),
            candidate.tags() != null ? candidate.tags() : List.of(),
            candidate.timestamp(),
            candidate.expiresAt(),
            "DETECTED",
            null,  // deletedAt
            1      // version
        );
    }

    private void supersede(SignalCandidate candidate) {
        // AV-10: Auto-supersede overlapping zones
        List<Signal> existing = signalRepo.findBySymbolAndStatus(
            candidate.symbol(), "PUBLISHED");

        for (Signal signal : existing) {
            if (zonesOverlap(signal, candidate)) {
                log.info("Superseding signal {}: overlapping zone detected",
                    signal.signalId());
                signalRepo.updateStatus(signal.signalId(), "SUPERSEDED");
            }
        }
    }

    private boolean zonesOverlap(Signal existing, SignalCandidate candidate) {
        // Check if zones overlap
        boolean floorOverlap = candidate.effectiveFloor().compareTo(existing.effectiveCeiling()) <= 0;
        boolean ceilingOverlap = candidate.effectiveCeiling().compareTo(existing.effectiveFloor()) >= 0;
        boolean sameDirection = existing.direction().name().equals(candidate.direction());

        return floorOverlap && ceilingOverlap && sameDirection;
    }

    private void createDeliveries(Signal signal) {
        // Get all enabled execution brokers
        List<UserBroker> executionBrokers = userBrokerRepo.findAll().stream()
            .filter(ub -> ub.enabled() && BrokerRole.EXEC.equals(ub.role()))
            .toList();

        log.debug("Creating deliveries for signal {}: {} user-brokers",
            signal.signalId(), executionBrokers.size());

        for (UserBroker userBroker : executionBrokers) {
            createDelivery(signal, userBroker);
        }
    }

    private void createDelivery(Signal signal, UserBroker userBroker) {
        String deliveryId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        // Create delivery record
        SignalDeliveryRepository.SignalDelivery delivery =
            new SignalDeliveryRepository.SignalDelivery(
                deliveryId,
                signal.signalId(),
                userBroker.userBrokerId(),
                userBroker.userId(),
                "CREATED",           // status
                null,                // intentId (set when consumed)
                null,                // rejectionReason
                null,                // userAction
                now,                 // createdAt
                null,                // deliveredAt (set when delivered to user)
                null,                // consumedAt (set when intent created)
                null,                // userActionAt
                now,                 // updatedAt
                null,                // deletedAt
                1                    // version
            );

        // Persist to DB
        signalDeliveryRepo.insert(delivery);

        // Add to in-memory index for fast lookups
        deliveryIndex.addDelivery(deliveryId, signal.signalId(), userBroker.userBrokerId());

        log.info("Delivery created: {} → {} for user-broker {}",
            deliveryId, signal.signalId(), userBroker.userBrokerId());

        // Emit SIGNAL_DELIVERED event (user-scoped)
        emitSignalDelivered(signal, userBroker.userId());
    }

    private void checkPriceInvalidation(String symbol, BigDecimal price) {
        List<Signal> activeSignals = signalRepo.findBySymbolAndStatus(symbol, "PUBLISHED");

        for (Signal signal : activeSignals) {
            if (price.compareTo(signal.effectiveFloor()) < 0) {
                expireSignal(signal.signalId(), "PRICE_BELOW_FLOOR");
            } else if (price.compareTo(signal.effectiveCeiling()) > 0) {
                expireSignal(signal.signalId(), "PRICE_ABOVE_CEILING");
            }
        }
    }

    private void expireSignal(String signalId, String reason) {
        log.info("Expiring signal {}: {}", signalId, reason);
        signalRepo.updateStatus(signalId, "EXPIRED");

        // Expire all associated deliveries
        signalDeliveryRepo.expireAllForSignal(signalId);

        // Remove from delivery index
        deliveryIndex.removeBySignal(signalId);

        // Emit SIGNAL_EXPIRED event
        Map<String, Object> payload = Map.of(
            "signalId", signalId,
            "reason", reason
        );
        eventService.emitGlobal(EventType.SIGNAL_EXPIRED, payload, "SMS");
    }

    private void handleCancelSignal(String signalId, String reason) {
        log.info("Cancelling signal {}: {}", signalId, reason);
        signalRepo.updateStatus(signalId, "CANCELLED");

        // Cancel all associated deliveries
        signalDeliveryRepo.cancelAllForSignal(signalId);

        // Remove from delivery index
        deliveryIndex.removeBySignal(signalId);

        // Emit SIGNAL_CANCELLED event
        Map<String, Object> payload = Map.of(
            "signalId", signalId,
            "reason", reason
        );
        eventService.emitGlobal(EventType.SIGNAL_CANCELLED, payload, "SMS");
    }

    private void handleExitDetected(ExitCandidate candidate) {
        log.info("Exit detected: {} {} @ {}",
            candidate.tradeId(), candidate.exitReason(), candidate.exitPrice());

        // 1. Fetch trade
        Trade trade = tradeRepo.findById(candidate.tradeId()).orElse(null);
        if (trade == null) {
            log.warn("Cannot create exit signal: trade not found {}", candidate.tradeId());
            return;
        }

        // 2. Fetch user-broker
        in.annupaper.domain.broker.UserBroker userBroker = userBrokerRepo.findById(trade.userBrokerId()).orElse(null);
        if (userBroker == null) {
            log.warn("Cannot create exit signal: user-broker not found {}", trade.userBrokerId());
            return;
        }

        // 3. Qualify exit for execution
        in.annupaper.service.validation.ExitQualificationService.ExitQualificationResult qualResult =
            exitQualificationService.qualify(
                candidate.direction(),
                candidate.exitReason(),
                candidate.exitPrice(),
                trade,
                userBroker,
                null  // userContext (portfolio checks - not needed for now)
            );

        // 4. Generate episode_id (DB enforces cooldown)
        int episodeId;
        try {
            // AV-2 + V010: DB function enforces 30s cooldown
            episodeId = exitSignalRepo.generateEpisode(candidate.tradeId(), candidate.exitReason());
        } catch (Exception e) {
            // Cooldown active - log and reject
            if (e.getMessage() != null && e.getMessage().contains("EXIT_COOLDOWN_ACTIVE")) {
                log.debug("Exit cooldown active: {} {} - {}",
                    candidate.tradeId(), candidate.exitReason(), e.getMessage());

                // Create REJECTED ExitIntent for tracking
                createExitIntent(
                    null,  // no exitSignalId yet
                    trade,
                    candidate.exitReason(),
                    0,  // episode 0 for cooldown rejections
                    false,  // not qualified
                    java.util.List.of("EXIT_COOLDOWN_ACTIVE: " + e.getMessage()),
                    null, null, null, null
                );
                return;
            }
            throw e;  // Re-throw other exceptions
        }

        // 5. Create ExitIntent with qualification results
        String exitIntentId = createExitIntent(
            null,  // no exitSignalId yet
            trade,
            candidate.exitReason(),
            episodeId,
            qualResult.passed(),
            qualResult.errors(),
            qualResult.calculatedQty(),
            qualResult.orderType(),
            qualResult.limitPrice(),
            qualResult.productType()
        );

        // 6. If qualification passed, create ExitSignal
        if (qualResult.passed()) {
            // Parse direction from candidate
            Direction direction;
            try {
                direction = Direction.valueOf(candidate.direction());
            } catch (IllegalArgumentException e) {
                log.error("Invalid direction in exit candidate: {}", candidate.direction());
                return;
            }

            // Create exit signal
            String exitSignalId = UUID.randomUUID().toString();
            ExitSignal exitSignal = new ExitSignal(
                exitSignalId,
                candidate.tradeId(),
                trade.signalId(),
                candidate.symbol(),
                direction,
                ExitReason.valueOf(candidate.exitReason()),
                candidate.exitPrice(),
                candidate.brickMovement(),
                candidate.favorableMovement(),
                candidate.timestamp()
            );

            // Persist exit signal
            exitSignalRepo.insert(exitSignal);

            log.info("✅ Exit signal created: {} for trade {} (episode {}, intent {})",
                exitSignalId, candidate.tradeId(), episodeId, exitIntentId);

            // Emit EXIT_DETECTED event
            Map<String, Object> payload = new HashMap<>();
            payload.put("exitSignalId", exitSignalId);
            payload.put("exitIntentId", exitIntentId);
            payload.put("tradeId", candidate.tradeId());
            payload.put("exitReason", candidate.exitReason());
            payload.put("exitPrice", candidate.exitPrice());
            payload.put("episodeId", episodeId);
            eventService.emitGlobal(EventType.EXIT_SIGNAL_DETECTED, payload, "SMS");

            // Update re-arm tracking
            ExitKey key = new ExitKey(candidate.tradeId(), candidate.exitReason());
            lastExitTimes.put(key, Instant.now());
        } else {
            log.warn("❌ Exit qualification failed: {} {} - {}",
                candidate.tradeId(), candidate.exitReason(), qualResult.errors());
        }
    }

    /**
     * Create ExitIntent record.
     * Returns exitIntentId.
     */
    private String createExitIntent(
        String exitSignalId,
        Trade trade,
        String exitReason,
        int episodeId,
        boolean passed,
        java.util.List<String> errors,
        Integer calculatedQty,
        String orderType,
        java.math.BigDecimal limitPrice,
        String productType
    ) {
        String exitIntentId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        ExitIntent exitIntent = new ExitIntent(
            exitIntentId,
            exitSignalId,  // null if qualification failed before signal creation
            trade.tradeId(),
            trade.userBrokerId(),
            exitReason,
            episodeId,
            passed ? ExitIntentStatus.APPROVED
                   : ExitIntentStatus.REJECTED,
            passed,
            errors != null ? errors : java.util.List.of(),
            calculatedQty,
            orderType,
            limitPrice,
            productType,
            null,  // brokerOrderId (set when placed)
            null,  // placedAt
            null,  // filledAt
            null,  // cancelledAt
            null,  // errorCode
            null,  // errorMessage
            0,     // retryCount
            now,   // createdAt
            now,   // updatedAt
            null,  // deletedAt
            1      // version
        );

        exitIntentRepo.insert(exitIntent);

        log.info("ExitIntent created: {} for trade {} (status: {})",
            exitIntentId, trade.tradeId(), exitIntent.status());

        // Emit EXIT_INTENT_CREATED event
        emitExitIntentCreated(exitIntent, trade);

        // Emit qualification result event
        if (passed) {
            emitExitIntentApproved(exitIntent, trade);
        } else {
            // Check if rejection was due to cooldown
            boolean isCooldown = errors != null && errors.stream()
                .anyMatch(e -> e.contains("EXIT_COOLDOWN_ACTIVE"));

            if (isCooldown) {
                emitExitIntentCooldownRejected(exitIntent, trade);
            } else {
                emitExitIntentRejected(exitIntent, trade, errors);
            }
        }

        return exitIntentId;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // EVENT EMISSION
    // ═══════════════════════════════════════════════════════════════════════

    private void emitSignalPublished(Signal signal) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("signalId", signal.signalId());
        payload.put("symbol", signal.symbol());
        payload.put("direction", signal.direction().name());
        payload.put("confluenceType", signal.confluenceType());
        payload.put("refPrice", signal.refPrice());

        eventService.emitGlobal(EventType.SIGNAL_GENERATED, payload, "SMS");
    }

    private void emitSignalDelivered(Signal signal, String userId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("signalId", signal.signalId());
        payload.put("symbol", signal.symbol());
        payload.put("direction", signal.direction().name());

        // TODO: Use eventService.emitToUser() when available
        // For now, use global with userId filter
        eventService.emitGlobal(EventType.SIGNAL_GENERATED, payload, "SMS");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // EXIT INTENT EVENT EMISSION (V010 Observability)
    // ═══════════════════════════════════════════════════════════════════════

    private void emitExitIntentCreated(ExitIntent exitIntent,
                                       Trade trade) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("exitIntentId", exitIntent.exitIntentId());
        payload.put("tradeId", exitIntent.tradeId());
        payload.put("userBrokerId", exitIntent.userBrokerId());
        payload.put("exitReason", exitIntent.exitReason());
        payload.put("episodeId", exitIntent.episodeId());
        payload.put("symbol", trade.symbol());
        payload.put("direction", trade.direction());

        eventService.emitGlobal(EventType.EXIT_INTENT_CREATED, payload, "SMS");
    }

    private void emitExitIntentApproved(ExitIntent exitIntent,
                                        Trade trade) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("exitIntentId", exitIntent.exitIntentId());
        payload.put("tradeId", exitIntent.tradeId());
        payload.put("userBrokerId", exitIntent.userBrokerId());
        payload.put("exitReason", exitIntent.exitReason());
        payload.put("episodeId", exitIntent.episodeId());
        payload.put("calculatedQty", exitIntent.calculatedQty());
        payload.put("orderType", exitIntent.orderType());
        payload.put("limitPrice", exitIntent.limitPrice());
        payload.put("symbol", trade.symbol());

        eventService.emitGlobal(EventType.EXIT_INTENT_APPROVED, payload, "SMS");
        log.info("✅ Exit intent approved: {} (episode {})", exitIntent.exitIntentId(), exitIntent.episodeId());
    }

    private void emitExitIntentRejected(ExitIntent exitIntent,
                                        Trade trade,
                                        java.util.List<String> errors) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("exitIntentId", exitIntent.exitIntentId());
        payload.put("tradeId", exitIntent.tradeId());
        payload.put("userBrokerId", exitIntent.userBrokerId());
        payload.put("exitReason", exitIntent.exitReason());
        payload.put("episodeId", exitIntent.episodeId());
        payload.put("errors", errors);
        payload.put("symbol", trade.symbol());

        eventService.emitGlobal(EventType.EXIT_INTENT_REJECTED, payload, "SMS");
        log.warn("❌ Exit intent rejected: {} - {}", exitIntent.exitIntentId(), errors);
    }

    private void emitExitIntentCooldownRejected(ExitIntent exitIntent,
                                                 Trade trade) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("exitIntentId", exitIntent.exitIntentId());
        payload.put("tradeId", exitIntent.tradeId());
        payload.put("userBrokerId", exitIntent.userBrokerId());
        payload.put("exitReason", exitIntent.exitReason());
        payload.put("episodeId", exitIntent.episodeId());
        payload.put("symbol", trade.symbol());
        payload.put("cooldownSeconds", 30);  // From DB function

        eventService.emitGlobal(EventType.EXIT_INTENT_COOLDOWN_REJECTED, payload, "SMS");
        log.debug("⏸ Exit intent cooldown rejected: {} (re-arm pending)", exitIntent.exitIntentId());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // GUARDS & VALIDATION
    // ═══════════════════════════════════════════════════════════════════════

    private boolean isOutOfOrder(String symbol, Instant timestamp) {
        // AV-8 FIX: Prevent processing older ticks after newer ones
        Instant lastProcessed = lastProcessedTimes.get(symbol);
        return lastProcessed != null && timestamp.isBefore(lastProcessed);
    }

    private boolean isMarketClosingSoon(int bufferSeconds) {
        // AV-11 FIX: Check if market closes within buffer
        // NSE market hours: 9:15 AM - 3:30 PM IST
        java.time.ZoneId istZone = java.time.ZoneId.of("Asia/Kolkata");
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(istZone);

        int hour = now.getHour();
        int minute = now.getMinute();
        int totalMinutes = hour * 60 + minute;

        // Market close time: 15:30 (3:30 PM)
        int closeTime = 15 * 60 + 30;
        int bufferMinutes = bufferSeconds / 60;

        // Check if within buffer window before close
        return totalMinutes >= (closeTime - bufferMinutes) && totalMinutes < closeTime;
    }

    private boolean isMarketOpen() {
        // AV-12 FIX: Check if market is currently open
        // NSE market hours: 9:15 AM - 3:30 PM IST (Monday-Friday, excluding holidays)
        java.time.ZoneId istZone = java.time.ZoneId.of("Asia/Kolkata");
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(istZone);

        // Check day of week (1=Monday, 7=Sunday)
        int dayOfWeek = now.getDayOfWeek().getValue();
        if (dayOfWeek >= 6) {  // Saturday or Sunday
            return false;
        }

        int hour = now.getHour();
        int minute = now.getMinute();
        int totalMinutes = hour * 60 + minute;

        // Market hours: 9:15 AM (555 min) to 3:30 PM (930 min)
        int openTime = 9 * 60 + 15;
        int closeTime = 15 * 60 + 30;

        return totalMinutes >= openTime && totalMinutes < closeTime;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // INTERNAL DATA STRUCTURES
    // ═══════════════════════════════════════════════════════════════════════

    private record ExitKey(String tradeId, String exitReason) {}
}
