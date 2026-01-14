package in.annupaper.service.signal;

import in.annupaper.domain.trade.Direction;
import in.annupaper.domain.common.EventType;
import in.annupaper.domain.signal.SignalType;
import in.annupaper.domain.signal.Signal;
import in.annupaper.domain.signal.MtfGlobalConfig;
import in.annupaper.domain.trade.TradeIntent;
import in.annupaper.domain.broker.UserBroker;
import in.annupaper.repository.SignalRepository;
import in.annupaper.repository.UserBrokerRepository;
import in.annupaper.service.candle.CandleStore;
import in.annupaper.service.core.EventService;
import in.annupaper.service.execution.ExecutionOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Signal Service.
 * Generates signals from main DATA broker feed.
 * Signals are GLOBAL - broadcast to all users.
 */
public final class SignalService {
    private static final Logger log = LoggerFactory.getLogger(SignalService.class);

    private final SignalRepository signalRepo;
    private final UserBrokerRepository userBrokerRepo;
    private final EventService eventService;
    private final ExecutionOrchestrator executionOrchestrator;
    private final ConfluenceCalculator confluenceCalculator;
    private final in.annupaper.repository.MtfConfigRepository mtfConfigRepo;
    private final in.annupaper.repository.TradeRepository tradeRepo;
    private final CandleStore candleStore;
    private final in.annupaper.repository.PortfolioRepository portfolioRepo;
    private final SignalManagementService signalManagementService;

    public SignalService(
        SignalRepository signalRepo,
        UserBrokerRepository userBrokerRepo,
        EventService eventService,
        ExecutionOrchestrator executionOrchestrator,
        ConfluenceCalculator confluenceCalculator,
        in.annupaper.repository.MtfConfigRepository mtfConfigRepo,
        in.annupaper.repository.TradeRepository tradeRepo,
        CandleStore candleStore,
        in.annupaper.repository.PortfolioRepository portfolioRepo,
        SignalManagementService signalManagementService
    ) {
        this.signalRepo = signalRepo;
        this.userBrokerRepo = userBrokerRepo;
        this.eventService = eventService;
        this.executionOrchestrator = executionOrchestrator;
        this.confluenceCalculator = confluenceCalculator;
        this.mtfConfigRepo = mtfConfigRepo;
        this.tradeRepo = tradeRepo;
        this.candleStore = candleStore;
        this.portfolioRepo = portfolioRepo;
        this.signalManagementService = signalManagementService;
    }
    
    /**
     * Generate and process a signal.
     * This is the main entry point for signal generation.
     *
     * UPDATED FLOW (Phase 3 - SMS Integration):
     * 1. Verify DATA broker connected
     * 2. Convert SignalInput → SignalCandidate
     * 3. Delegate to SignalManagementService.onSignalDetected()
     * 4. SMS handles: persistence, deliveries, events, fan-out
     *
     * NOTE: Signal-to-delivery-to-intent flow now owned by SMS.
     */
    public Signal generateAndProcess(SignalInput input) {
        // Verify DATA broker is connected
        Optional<UserBroker> dataBroker = userBrokerRepo.findDataBroker();
        if (dataBroker.isEmpty() || !dataBroker.get().connected()) {
            log.warn("DATA broker not connected, cannot generate signals");
            return null;
        }

        // Convert SignalInput to SignalCandidate
        SignalManagementService.SignalCandidate candidate = new SignalManagementService.SignalCandidate(
            input.symbol(),
            input.direction().name(),
            input.signalType().name(),
            input.htfZone(),
            input.itfZone(),
            input.ltfZone(),
            input.confluenceType(),
            input.confluenceScore(),
            input.pWin(),
            input.pFill(),
            input.kelly(),
            input.refPrice(),
            input.refBid(),
            input.refAsk(),
            input.entryLow(),
            input.entryHigh(),
            input.htfLow(),
            input.htfHigh(),
            input.itfLow(),
            input.itfHigh(),
            input.ltfLow(),
            input.ltfHigh(),
            input.effectiveFloor(),
            input.effectiveCeiling(),
            input.confidence(),
            input.reason(),
            input.tags() != null ? input.tags() : List.of(),
            Instant.now(),
            input.expiresAt()
        );

        // Delegate to SMS - it handles everything (persistence, deliveries, events)
        signalManagementService.onSignalDetected(candidate);

        log.info("Signal delegated to SMS: {} {} @ {} (confluence={}, pWin={})",
            input.symbol(), input.direction(), input.refPrice(),
            input.confluenceType(), input.pWin());

        // NOTE: Return null for now since SMS doesn't return Signal object
        // This is fine - callers should listen to events instead of using return value
        return null;
    }
    
    /**
     * Create signal from input data.
     */
    private Signal createSignal(SignalInput input) {
        String signalId = UUID.randomUUID().toString();

        return new Signal(
            signalId,
            input.symbol(),
            input.direction(),
            input.signalType(),
            input.htfZone(),
            input.itfZone(),
            input.ltfZone(),
            input.confluenceType(),
            input.confluenceScore(),
            input.pWin(),
            input.pFill(),
            input.kelly(),
            input.refPrice(),
            input.refBid(),
            input.refAsk(),
            input.entryLow(),
            input.entryHigh(),
            input.htfLow(),
            input.htfHigh(),
            input.itfLow(),
            input.itfHigh(),
            input.ltfLow(),
            input.ltfHigh(),
            input.effectiveFloor(),
            input.effectiveCeiling(),
            input.confidence(),
            input.reason(),
            input.tags() != null ? input.tags() : List.of(),
            Instant.now(),
            input.expiresAt(),
            "ACTIVE",
            null,  // deletedAt
            1      // version
        );
    }
    
    /**
     * Emit SIGNAL_GENERATED event.
     */
    private void emitSignalEvent(Signal signal) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("signalId", signal.signalId());
        payload.put("symbol", signal.symbol());
        payload.put("direction", signal.direction().name());
        payload.put("signalType", signal.signalType().name());
        payload.put("refPrice", signal.refPrice());
        payload.put("confluenceType", signal.confluenceType());
        payload.put("confluenceScore", signal.confluenceScore());
        payload.put("pWin", signal.pWin());
        payload.put("kelly", signal.kelly());
        payload.put("htfZone", signal.htfZone());
        payload.put("itfZone", signal.itfZone());
        payload.put("ltfZone", signal.ltfZone());
        payload.put("effectiveFloor", signal.effectiveFloor());
        payload.put("effectiveCeiling", signal.effectiveCeiling());
        payload.put("confidence", signal.confidence());
        payload.put("reason", signal.reason());
        
        eventService.emitGlobal(EventType.SIGNAL_GENERATED, payload, signal.signalId(), "SYSTEM");
        
        log.info("Signal generated: {} {} {} @ {} (confluence={}, pWin={})",
                 signal.signalId(), signal.symbol(), signal.direction(), 
                 signal.refPrice(), signal.confluenceType(), signal.pWin());
    }
    
    /**
     * Mark signal as expired.
     * Updates signal status in database (immutable update).
     */
    public void expireSignal(String signalId) {
        // Retrieve current signal
        Optional<Signal> currentSignal = signalRepo.findById(signalId);
        if (currentSignal.isEmpty()) {
            log.warn("Cannot expire signal - not found: {}", signalId);
            return;
        }

        // Create updated signal with EXPIRED status
        Signal signal = currentSignal.get();
        Signal expired = new Signal(
            signal.signalId(),
            signal.symbol(),
            signal.direction(),
            signal.signalType(),
            signal.htfZone(),
            signal.itfZone(),
            signal.ltfZone(),
            signal.confluenceType(),
            signal.confluenceScore(),
            signal.pWin(),
            signal.pFill(),
            signal.kelly(),
            signal.refPrice(),
            signal.refBid(),
            signal.refAsk(),
            signal.entryLow(),
            signal.entryHigh(),
            signal.htfLow(),
            signal.htfHigh(),
            signal.itfLow(),
            signal.itfHigh(),
            signal.ltfLow(),
            signal.ltfHigh(),
            signal.effectiveFloor(),
            signal.effectiveCeiling(),
            signal.confidence(),
            signal.reason(),
            signal.tags(),
            signal.generatedAt(),
            signal.expiresAt(),
            "EXPIRED",  // Updated status
            signal.deletedAt(),
            signal.version()
        );

        // Persist update (immutable: soft delete old, insert new version)
        signalRepo.update(expired);
        log.info("Signal expired: {}", signalId);

        // Emit event
        Map<String, Object> payload = Map.of("signalId", signalId, "reason", "EXPIRED");
        eventService.emitGlobal(EventType.SIGNAL_EXPIRED, payload, signalId, "SYSTEM");
    }

    /**
     * Cancel a signal.
     * Updates signal status in database (immutable update).
     */
    public void cancelSignal(String signalId, String reason) {
        // Retrieve current signal
        Optional<Signal> currentSignal = signalRepo.findById(signalId);
        if (currentSignal.isEmpty()) {
            log.warn("Cannot cancel signal - not found: {}", signalId);
            return;
        }

        // Create updated signal with CANCELLED status
        Signal signal = currentSignal.get();
        Signal cancelled = new Signal(
            signal.signalId(),
            signal.symbol(),
            signal.direction(),
            signal.signalType(),
            signal.htfZone(),
            signal.itfZone(),
            signal.ltfZone(),
            signal.confluenceType(),
            signal.confluenceScore(),
            signal.pWin(),
            signal.pFill(),
            signal.kelly(),
            signal.refPrice(),
            signal.refBid(),
            signal.refAsk(),
            signal.entryLow(),
            signal.entryHigh(),
            signal.htfLow(),
            signal.htfHigh(),
            signal.itfLow(),
            signal.itfHigh(),
            signal.ltfLow(),
            signal.ltfHigh(),
            signal.effectiveFloor(),
            signal.effectiveCeiling(),
            signal.confidence(),
            signal.reason(),
            signal.tags(),
            signal.generatedAt(),
            signal.expiresAt(),
            "CANCELLED",  // Updated status
            signal.deletedAt(),
            signal.version()
        );

        // Persist update (immutable: soft delete old, insert new version)
        signalRepo.update(cancelled);
        log.info("Signal cancelled: {} (reason: {})", signalId, reason);

        // Emit event
        Map<String, Object> payload = Map.of("signalId", signalId, "reason", reason);
        eventService.emitGlobal(EventType.SIGNAL_CANCELLED, payload, signalId, "SYSTEM");
    }

    /**
     * Analyze symbol for triple confluence and generate signal if found.
     * This method should be called whenever price updates (on every tick or 1-min candle close).
     *
     * @param symbol Symbol to analyze
     * @param currentPrice Current market price
     * @return Generated signal if triple confluence found, null otherwise
     */
    public Signal analyzeAndGenerateSignal(String symbol, BigDecimal currentPrice) {
        try {
            // Perform confluence analysis
            ConfluenceCalculator.ConfluenceResult analysis =
                confluenceCalculator.analyze(symbol, currentPrice);

            if (analysis == null) {
                log.debug("Cannot analyze {} - insufficient candle data", symbol);
                return null;
            }

            // Check if signal meets minimum confluence requirement
            if (!analysis.isBuySignal()) {
                log.debug("No signal for {}: confluence type {} does not meet requirement",
                    symbol, analysis.minConfluenceTypeMet());
                return null;
            }

            // Extract zone information
            ZoneDetector.Zone htfZone = analysis.zones().htf();
            ZoneDetector.Zone itfZone = analysis.zones().itf();
            ZoneDetector.Zone ltfZone = analysis.zones().ltf();

            // Determine zone indicators based on actual confluence
            int htfZoneIndicator = htfZone.isInBuyZone(currentPrice) ? 1 : 0;
            int itfZoneIndicator = itfZone.isInBuyZone(currentPrice) ? 1 : 0;
            int ltfZoneIndicator = ltfZone.isInBuyZone(currentPrice) ? 1 : 0;

            // Get MTF config for utility gate and risk thresholds
            MtfGlobalConfig config = mtfConfigRepo.getGlobalConfig()
                .orElseThrow(() -> new RuntimeException("MTF global config not found"));

            // ═══════════════════════════════════════════════════════════════════
            // 🔴 CONSTITUTIONAL GATE: UTILITY ASYMMETRY (3× ADVANTAGE)
            // ═══════════════════════════════════════════════════════════════════
            // Signal-level pre-check: Reject signals that don't meet 3× advantage
            // before they're even created or fanned out to users.

            BigDecimal effectiveFloor = htfZone.low();       // Stop loss (HTF low)
            BigDecimal effectiveCeiling = htfZone.high();    // Target (HTF high)

            // Calculate log returns
            // π = ln(ceiling/entry) - upside log return
            BigDecimal upsideLogReturn = BigDecimal.valueOf(
                Math.log(effectiveCeiling.divide(currentPrice, 6, java.math.RoundingMode.HALF_UP).doubleValue())
            );

            // ℓ = ln(floor/entry) - downside log return
            BigDecimal downsideLogReturn = BigDecimal.valueOf(
                Math.log(effectiveFloor.divide(currentPrice, 6, java.math.RoundingMode.HALF_UP).doubleValue())
            );

            // Default probability (should be calculated from historical data)
            BigDecimal pWin = new BigDecimal("0.65");  // 65% win probability

            // Check utility gate if enabled
            if (config.utilityGateEnabled()) {
                boolean passesUtilityGate = UtilityAsymmetryCalculator.passesAdvantageGate(
                    pWin,
                    upsideLogReturn,
                    downsideLogReturn,
                    config
                );

                if (!passesUtilityGate) {
                    // Calculate advantage ratio for diagnostics
                    BigDecimal advantageRatio = UtilityAsymmetryCalculator.calculateAdvantageRatio(
                        pWin, upsideLogReturn, downsideLogReturn, config
                    );

                    log.warn("[UTILITY GATE REJECTION] {} @ {}: Advantage ratio {:.2f}× < {:.2f}× required (π={:.4f}, ℓ={:.4f}, p={:.2f}%)",
                        symbol, currentPrice,
                        advantageRatio.doubleValue(),
                        config.minAdvantageRatio().doubleValue(),
                        upsideLogReturn.doubleValue(),
                        downsideLogReturn.doubleValue(),
                        pWin.multiply(new BigDecimal("100")).doubleValue());

                    return null;  // REJECT - insufficient utility advantage
                }

                // Log successful utility gate pass
                BigDecimal advantageRatio = UtilityAsymmetryCalculator.calculateAdvantageRatio(
                    pWin, upsideLogReturn, downsideLogReturn, config
                );

                log.info("[UTILITY GATE PASS] {} @ {}: Advantage ratio {:.2f}× ≥ {:.2f}× required (π={:.4f}, ℓ={:.4f}, p={:.2f}%)",
                    symbol, currentPrice,
                    advantageRatio.doubleValue(),
                    config.minAdvantageRatio().doubleValue(),
                    upsideLogReturn.doubleValue(),
                    downsideLogReturn.doubleValue(),
                    pWin.multiply(new BigDecimal("100")).doubleValue());
            }

            // Calculate base Kelly (10% of capital as baseline)
            BigDecimal baseKelly = new BigDecimal("0.10");

            // Apply strength multiplier to Kelly sizing
            BigDecimal adjustedKelly = baseKelly.multiply(analysis.strengthMultiplier());

            // Build confluence description
            String confluenceDescription = String.format(
                "%s buy confluence detected - Score: %.2f, Strength: %s, Multiplier: %.2fx",
                analysis.minConfluenceTypeMet(),
                analysis.confluenceScore(),
                analysis.confluenceStrength(),
                analysis.strengthMultiplier()
            );

            // Create signal input with confluence data
            SignalInput input = new SignalInput(
                symbol,
                Direction.BUY,
                SignalType.ENTRY,
                htfZoneIndicator,  // 1 if HTF in buy zone, 0 otherwise
                itfZoneIndicator,  // 1 if ITF in buy zone, 0 otherwise
                ltfZoneIndicator,  // 1 if LTF in buy zone, 0 otherwise
                analysis.minConfluenceTypeMet() + "_BUY",  // TRIPLE_BUY, DOUBLE_BUY, or SINGLE_BUY
                analysis.confluenceScore(),
                new BigDecimal("0.65"),  // pWin: 65% win probability (TODO: calculate from historical data)
                new BigDecimal("0.90"),  // pFill: 90% fill probability
                adjustedKelly,  // Kelly adjusted by strength multiplier
                currentPrice,  // refPrice
                currentPrice.subtract(new BigDecimal("0.05")),  // refBid (mock)
                currentPrice.add(new BigDecimal("0.05")),  // refAsk (mock)
                ltfZone.low(),  // entryLow
                ltfZone.buyZoneTop(),  // entryHigh
                htfZone.low(),
                htfZone.high(),
                itfZone.low(),
                itfZone.high(),
                ltfZone.low(),
                ltfZone.high(),
                htfZone.low(),  // effectiveFloor (use HTF low as stop loss)
                htfZone.high(),  // effectiveCeiling (use HTF high as target)
                new BigDecimal("0.85"),  // confidence: 85%
                confluenceDescription,
                List.of("CONFLUENCE", "BUY_ZONE", "AUTO_GENERATED", analysis.confluenceStrength()),
                Instant.now().plus(15, java.time.temporal.ChronoUnit.MINUTES)  // expires in 15 minutes
            );

            log.info("[SIGNAL VALIDATION] {} @ {}: {} confluence (score={}, strength={}, kelly={})",
                symbol, currentPrice, analysis.minConfluenceTypeMet(),
                analysis.confluenceScore(), analysis.confluenceStrength(), adjustedKelly);

            // Generate and process the signal
            Signal signal = generateAndProcess(input);
            log.info("AUTO-SIGNAL GENERATED: {} {} @ {} (score={}, strength={}, kelly={})",
                signal.signalId(), symbol, currentPrice, analysis.confluenceScore(),
                analysis.confluenceStrength(), adjustedKelly);

            return signal;

        } catch (Exception e) {
            log.error("Failed to analyze and generate signal for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    /**
     * Input for signal generation.
     */
    public record SignalInput(
        String symbol,
        Direction direction,
        SignalType signalType,
        Integer htfZone,
        Integer itfZone,
        Integer ltfZone,
        String confluenceType,
        BigDecimal confluenceScore,
        BigDecimal pWin,
        BigDecimal pFill,
        BigDecimal kelly,
        BigDecimal refPrice,
        BigDecimal refBid,
        BigDecimal refAsk,
        BigDecimal entryLow,
        BigDecimal entryHigh,
        BigDecimal htfLow,
        BigDecimal htfHigh,
        BigDecimal itfLow,
        BigDecimal itfHigh,
        BigDecimal ltfLow,
        BigDecimal ltfHigh,
        BigDecimal effectiveFloor,
        BigDecimal effectiveCeiling,
        BigDecimal confidence,
        String reason,
        List<String> tags,
        Instant expiresAt
    ) {}
}
