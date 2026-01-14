package in.annupaper.service.validation;

import in.annupaper.domain.common.ValidationErrorCode;
import in.annupaper.domain.common.ValidationResult;
import in.annupaper.domain.signal.Signal;
import in.annupaper.domain.broker.UserBroker;
import in.annupaper.domain.user.Portfolio;
import in.annupaper.service.signal.MtfPositionSizer;
import in.annupaper.service.signal.PositionSizingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Validation Service.
 * Validates signals against user-broker constraints.
 */
public final class ValidationService {
    private static final Logger log = LoggerFactory.getLogger(ValidationService.class);
    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);

    // Default constraints (should come from config)
    private static final BigDecimal MIN_WIN_PROB = new BigDecimal("0.35");
    private static final BigDecimal MIN_KELLY = new BigDecimal("0.02");
    private static final BigDecimal MAX_TRADE_LOG_LOSS = new BigDecimal("-0.08");
    private static final BigDecimal MAX_PORTFOLIO_LOG_LOSS = new BigDecimal("-0.05");
    private static final int MIN_QTY = 1;
    private static final BigDecimal MIN_VALUE = new BigDecimal("1000");

    private final PositionSizingService positionSizingService;

    public ValidationService(PositionSizingService positionSizingService) {
        this.positionSizingService = positionSizingService;
    }

    /**
     * Validate a signal for a specific user-broker.
     */
    public ValidationResult validate(Signal signal, UserBroker userBroker, UserContext userContext) {
        ValidationResult.Builder builder = new ValidationResult.Builder();
        
        // ═══════════════════════════════════════════════════════════════
        // 1. Basic checks
        // ═══════════════════════════════════════════════════════════════
        
        if (!userBroker.isActive()) {
            builder.addError(ValidationErrorCode.BROKER_DISABLED);
            return builder.build();
        }
        
        if (!userBroker.connected()) {
            builder.addError(ValidationErrorCode.BROKER_NOT_CONNECTED);
            return builder.build();
        }
        
        if (userContext.portfolioPaused()) {
            builder.addError(ValidationErrorCode.PORTFOLIO_PAUSED);
            return builder.build();
        }
        
        // ═══════════════════════════════════════════════════════════════
        // 2. Symbol constraints
        // ═══════════════════════════════════════════════════════════════
        
        if (!userBroker.isSymbolAllowed(signal.symbol())) {
            builder.addError(ValidationErrorCode.SYMBOL_NOT_ALLOWED, "symbol", 
                           userBroker.allowedSymbols(), signal.symbol());
            return builder.build();
        }
        
        // ═══════════════════════════════════════════════════════════════
        // 3. Confluence requirement
        // ═══════════════════════════════════════════════════════════════
        
        if (!signal.hasTripleConfluence()) {
            builder.addError(ValidationErrorCode.NO_TRIPLE_CONFLUENCE);
            return builder.build();
        }
        
        // ═══════════════════════════════════════════════════════════════
        // 4. Probability thresholds
        // ═══════════════════════════════════════════════════════════════
        
        if (signal.pWin() != null && signal.pWin().compareTo(MIN_WIN_PROB) < 0) {
            builder.addError(ValidationErrorCode.BELOW_MIN_WIN_PROB, "pWin", MIN_WIN_PROB, signal.pWin());
        }
        
        if (signal.kelly() != null && signal.kelly().compareTo(MIN_KELLY) < 0) {
            builder.addError(ValidationErrorCode.BELOW_MIN_KELLY, "kelly", MIN_KELLY, signal.kelly());
        }
        
        if (builder.hasErrors()) {
            return builder.build();
        }
        
        // ═══════════════════════════════════════════════════════════════
        // 5. Constitutional Position Sizing
        // ═══════════════════════════════════════════════════════════════

        // Get confluence multiplier
        BigDecimal confluenceMultiplier = getConfluenceMultiplier(signal.confluenceScore());
        BigDecimal pWin = signal.pWin() != null ? signal.pWin() : new BigDecimal("0.65");
        BigDecimal pFill = signal.pFill() != null ? signal.pFill() : BigDecimal.ONE;
        BigDecimal kelly = signal.kelly() != null ? signal.kelly() : new BigDecimal("0.10");

        // Call constitutional position sizer
        MtfPositionSizer.PositionSizeResult sizeResult = positionSizingService.calculatePositionSize(
            signal.symbol(),
            signal.refPrice(),                  // zonePrice
            signal.effectiveFloor(),            // effectiveFloor
            signal.effectiveCeiling(),          // effectiveCeiling
            pWin,
            pFill,
            kelly,
            confluenceMultiplier,
            userContext.portfolioId()
        );

        // Check if position sizing rejected the trade
        if (!sizeResult.isValid()) {
            log.warn("[CONSTITUTIONAL REJECTION] {} @ {}: {}",
                signal.symbol(), signal.refPrice(), sizeResult.limitingConstraint());
            builder.addError(ValidationErrorCode.POSITION_SIZER_REJECTED,
                "positionSizer", sizeResult.limitingConstraint(), 0);
            return builder.build();
        }

        int qty = sizeResult.quantity();
        BigDecimal tradeValue = signal.refPrice().multiply(BigDecimal.valueOf(qty));

        log.info("[CONSTITUTIONAL SIZING] {} @ {}: qty={} - {}",
            signal.symbol(), signal.refPrice(), qty, sizeResult.getSummary());
        
        // ═══════════════════════════════════════════════════════════════
        // 6. Quantity/value constraints
        // ═══════════════════════════════════════════════════════════════
        
        if (qty < MIN_QTY) {
            builder.addError(ValidationErrorCode.BELOW_MIN_QTY, "qty", MIN_QTY, qty);
        }
        
        if (tradeValue.compareTo(MIN_VALUE) < 0) {
            builder.addError(ValidationErrorCode.BELOW_MIN_VALUE, "value", MIN_VALUE, tradeValue);
        }
        
        if (tradeValue.compareTo(userBroker.maxPerTrade()) > 0) {
            builder.addError(ValidationErrorCode.EXCEEDS_MAX_PER_TRADE, "value", userBroker.maxPerTrade(), tradeValue);
        }
        
        // ═══════════════════════════════════════════════════════════════
        // 7. Capital constraints
        // ═══════════════════════════════════════════════════════════════
        
        if (tradeValue.compareTo(userContext.availableCapital()) > 0) {
            builder.addError(ValidationErrorCode.INSUFFICIENT_CAPITAL, "value", 
                           userContext.availableCapital(), tradeValue);
        }
        
        BigDecimal newExposure = userContext.currentExposure().add(tradeValue);
        if (newExposure.compareTo(userBroker.maxExposure()) > 0) {
            builder.addError(ValidationErrorCode.EXCEEDS_MAX_EXPOSURE, "exposure", 
                           userBroker.maxExposure(), newExposure);
        }
        
        // ═══════════════════════════════════════════════════════════════
        // 8. Open trades constraint
        // ═══════════════════════════════════════════════════════════════
        
        if (userContext.openTradeCount() >= userBroker.maxOpenTrades()) {
            builder.addError(ValidationErrorCode.MAX_OPEN_TRADES_REACHED);
        }
        
        // ═══════════════════════════════════════════════════════════════
        // 9. Log constraints
        // ═══════════════════════════════════════════════════════════════
        
        BigDecimal logImpact = BigDecimal.ZERO;
        if (signal.effectiveFloor() != null && signal.refPrice().compareTo(BigDecimal.ZERO) > 0) {
            logImpact = BigDecimal.valueOf(
                Math.log(signal.effectiveFloor().divide(signal.refPrice(), MC).doubleValue()));
        }
        
        if (logImpact.compareTo(MAX_TRADE_LOG_LOSS) < 0) {
            builder.addError(ValidationErrorCode.EXCEEDS_TRADE_LOG_LOSS, "logImpact", 
                           MAX_TRADE_LOG_LOSS, logImpact);
        }
        
        BigDecimal tradeWeight = userContext.totalCapital().compareTo(BigDecimal.ZERO) > 0
            ? tradeValue.divide(userContext.totalCapital(), MC)
            : BigDecimal.ZERO;
        BigDecimal portfolioExposureAfter = userContext.currentLogExposure()
            .add(tradeWeight.multiply(logImpact));
        
        if (portfolioExposureAfter.compareTo(MAX_PORTFOLIO_LOG_LOSS) < 0) {
            builder.addError(ValidationErrorCode.EXCEEDS_PORTFOLIO_LOG_LOSS, "portfolioExposure",
                           MAX_PORTFOLIO_LOG_LOSS, portfolioExposureAfter);
        }
        
        // ═══════════════════════════════════════════════════════════════
        // 10. Risk constraints (daily/weekly loss, cooldown)
        // ═══════════════════════════════════════════════════════════════
        
        if (userBroker.maxDailyLoss().compareTo(BigDecimal.ZERO) > 0 &&
            userContext.dailyLoss().abs().compareTo(userBroker.maxDailyLoss()) >= 0) {
            builder.addError(ValidationErrorCode.DAILY_LOSS_LIMIT_REACHED);
        }
        
        if (userBroker.maxWeeklyLoss().compareTo(BigDecimal.ZERO) > 0 &&
            userContext.weeklyLoss().abs().compareTo(userBroker.maxWeeklyLoss()) >= 0) {
            builder.addError(ValidationErrorCode.WEEKLY_LOSS_LIMIT_REACHED);
        }
        
        if (userContext.inCooldown()) {
            builder.addError(ValidationErrorCode.IN_COOLDOWN_PERIOD);
        }
        
        // ═══════════════════════════════════════════════════════════════
        // Build result
        // ═══════════════════════════════════════════════════════════════
        
        if (builder.hasErrors()) {
            return builder.build();
        }
        
        // Determine order type and product
        String orderType = "MARKET";  // Default to market for active zone
        BigDecimal limitPrice = null;
        if (signal.entryLow() != null && signal.refPrice().compareTo(signal.entryLow()) > 0) {
            // Price is above entry zone, use limit order
            orderType = "LIMIT";
            limitPrice = signal.entryHigh();
        }
        
        String productType = determineProductType(userBroker, signal);
        
        return builder
            .qty(qty)
            .value(tradeValue)
            .orderType(orderType)
            .limitPrice(limitPrice)
            .productType(productType)
            .logImpact(logImpact)
            .exposureAfter(portfolioExposureAfter)
            .build();
    }
    
    private BigDecimal getConfluenceMultiplier(BigDecimal score) {
        if (score == null) return BigDecimal.ONE;
        double s = score.doubleValue();
        if (s <= 0.25) return new BigDecimal("1.2");  // VERY_STRONG
        if (s <= 0.45) return BigDecimal.ONE;          // STRONG
        if (s <= 0.65) return new BigDecimal("0.8");  // MODERATE
        if (s <= 0.85) return new BigDecimal("0.6");  // WEAK
        return BigDecimal.ZERO;
    }
    
    private String determineProductType(UserBroker userBroker, Signal signal) {
        // Default product selection logic
        if (userBroker.allowedProducts() != null && !userBroker.allowedProducts().isEmpty()) {
            // Prefer MIS for intraday, CNC for positional
            if (userBroker.allowedProducts().contains("MIS")) return "MIS";
            if (userBroker.allowedProducts().contains("CNC")) return "CNC";
            return userBroker.allowedProducts().get(0);
        }
        return "CNC";  // Default to CNC (delivery)
    }
    
    /**
     * User context for validation.
     */
    public record UserContext(
        String portfolioId,
        BigDecimal totalCapital,
        BigDecimal availableCapital,
        BigDecimal currentExposure,
        BigDecimal currentLogExposure,
        int openTradeCount,
        int maxPyramidLevel,
        BigDecimal dailyLoss,
        BigDecimal weeklyLoss,
        boolean inCooldown,
        boolean portfolioPaused
    ) {}
}
