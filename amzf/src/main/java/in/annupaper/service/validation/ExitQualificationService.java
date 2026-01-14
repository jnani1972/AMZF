package in.annupaper.service.validation;

import in.annupaper.domain.trade.Direction;
import in.annupaper.domain.trade.Trade;
import in.annupaper.domain.broker.UserBroker;
import in.annupaper.repository.ExitIntentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Exit Qualification Service.
 *
 * Validates execution readiness for exit signals.
 * Completes entry/exit symmetry: entry has ValidationService, exit has ExitQualificationService.
 *
 * Qualification checks:
 * - Broker operational (active, connected)
 * - Trade state valid (OPEN, not already exiting)
 * - Direction consistency
 * - Market hours / exit window
 * - Portfolio operational state
 */
public final class ExitQualificationService {
    private static final Logger log = LoggerFactory.getLogger(ExitQualificationService.class);

    private final ExitIntentRepository exitIntentRepo;

    // Market hours (IST)
    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 15);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 30);
    private static final int MARKET_CLOSE_BUFFER_MINUTES = 5;

    public ExitQualificationService(ExitIntentRepository exitIntentRepo) {
        this.exitIntentRepo = exitIntentRepo;
    }

    /**
     * Qualify exit for execution.
     *
     * @param exitSignalDirection Direction from exit signal (BUY signal = close LONG)
     * @param exitReason Exit reason
     * @param exitPrice Exit price
     * @param trade Trade to exit
     * @param userBroker User-broker for execution
     * @param userContext User context (portfolio, etc.)
     * @return Qualification result
     */
    public ExitQualificationResult qualify(
        String exitSignalDirection,
        String exitReason,
        BigDecimal exitPrice,
        Trade trade,
        UserBroker userBroker,
        ValidationService.UserContext userContext
    ) {
        ExitQualificationResult.Builder builder = new ExitQualificationResult.Builder();

        // 1. Broker operational checks
        if (!userBroker.isActive()) {
            builder.addError("BROKER_DISABLED", "Broker is not active");
            return builder.build();
        }

        if (!userBroker.connected()) {
            builder.addError("BROKER_DISCONNECTED", "Broker is not connected");
            return builder.build();
        }

        // 2. Trade state verification
        if (!trade.isOpen()) {
            builder.addError("TRADE_ALREADY_CLOSED", "Trade is not in OPEN state");
            return builder.build();
        }

        if (!trade.userBrokerId().equals(userBroker.userBrokerId())) {
            builder.addError("TRADE_BROKER_MISMATCH", "Trade user_broker_id does not match");
            return builder.build();
        }

        // 3. Direction consistency check
        Direction exitDirection = getExitDirection(exitSignalDirection);
        if (!isDirectionValid(trade, exitDirection)) {
            builder.addError("DIRECTION_MISMATCH",
                String.format("Exit direction %s does not match trade direction %s",
                    exitDirection, trade.direction()));
            return builder.build();
        }

        // 4. Check for existing pending exit
        if (hasPendingExitOrder(trade.tradeId())) {
            builder.addError("EXIT_ORDER_ALREADY_PENDING", "Another exit order is already pending for this trade");
            return builder.build();
        }

        // 5. Market hours / order placement window
        if (!isExitWindowOpen(exitReason)) {
            builder.addError("OUTSIDE_EXIT_WINDOW", "Exit orders cannot be placed at this time");
            return builder.build();
        }

        // 6. Portfolio freeze/lock state
        // TODO: Add frozen flag to UserContext if portfolio freezing is needed
        // if (userContext != null && userContext.frozen()) {
        //     builder.addError("PORTFOLIO_FROZEN", "Portfolio is frozen");
        //     return builder.build();
        // }

        // 7. Quantity calculation (should match trade qty)
        Integer exitQty = trade.entryQty();  // Exit full position
        if (exitQty == null || exitQty <= 0) {
            builder.addError("INVALID_QUANTITY", "Trade quantity is invalid");
            return builder.build();
        }
        builder.calculatedQty(exitQty);

        // 8. Order type determination
        String orderType = determineExitOrderType(exitReason);
        builder.orderType(orderType);

        // 9. Limit price (if applicable)
        if ("LIMIT".equals(orderType)) {
            BigDecimal limitPrice = calculateExitLimitPrice(exitReason, exitPrice, exitDirection);
            builder.limitPrice(limitPrice);
        }

        // 10. Product type (match entry)
        builder.productType(trade.productType());

        return builder.build();
    }

    /**
     * Get exit direction (opposite of trade direction).
     * BUY signal → SELL exit (close LONG)
     * SELL signal → BUY exit (close SHORT)
     */
    private Direction getExitDirection(String signalDirection) {
        return "BUY".equals(signalDirection) ? Direction.SELL : Direction.BUY;
    }

    /**
     * Validate direction consistency.
     */
    private boolean isDirectionValid(Trade trade, Direction exitDirection) {
        // Trade direction BUY (LONG) requires SELL exit
        // Trade direction SELL (SHORT) requires BUY exit
        if (trade.direction() == null) {
            log.warn("Trade {} has no direction field, assuming valid", trade.tradeId());
            return true;  // Allow for backward compatibility
        }

        String tradeDirection = trade.direction();
        return ("BUY".equals(tradeDirection) && exitDirection == Direction.SELL)
            || ("SELL".equals(tradeDirection) && exitDirection == Direction.BUY);
    }

    /**
     * Check if any pending exit order exists for trade.
     */
    private boolean hasPendingExitOrder(String tradeId) {
        return exitIntentRepo.findByTradeId(tradeId).stream()
            .anyMatch(intent -> intent.isPending() || intent.isApproved() || intent.isPlaced());
    }

    /**
     * Check if exit window is open.
     */
    private boolean isExitWindowOpen(String exitReason) {
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Kolkata"));

        // Stop loss exits allowed anytime during market hours
        if ("STOP_LOSS".equals(exitReason) || "TRAILING_STOP".equals(exitReason)) {
            return now.isAfter(MARKET_OPEN) && now.isBefore(MARKET_CLOSE);
        }

        // Target/manual exits: avoid last 5 minutes
        LocalTime closeBuffer = MARKET_CLOSE.minusMinutes(MARKET_CLOSE_BUFFER_MINUTES);
        return now.isAfter(MARKET_OPEN) && now.isBefore(closeBuffer);
    }

    /**
     * Determine exit order type based on reason.
     */
    private String determineExitOrderType(String exitReason) {
        return switch(exitReason) {
            case "STOP_LOSS", "TRAILING_STOP" -> "MARKET";  // Urgent exits
            case "TARGET_HIT" -> "LIMIT";  // Protect profit
            case "TIME_BASED" -> "LIMIT";  // No rush
            case "MANUAL" -> "MARKET";  // User intent
            default -> "MARKET";
        };
    }

    /**
     * Calculate limit price for exit.
     */
    private BigDecimal calculateExitLimitPrice(String exitReason, BigDecimal exitPrice, Direction exitDirection) {
        // For target hits, use detected price as limit
        // For time-based, give slight buffer
        if ("TARGET_HIT".equals(exitReason)) {
            return exitPrice;
        }

        if ("TIME_BASED".equals(exitReason)) {
            // Give 0.1% buffer for fills
            BigDecimal buffer = exitPrice.multiply(new BigDecimal("0.001"));
            return exitDirection == Direction.SELL
                ? exitPrice.subtract(buffer)  // SELL: lower limit for fill
                : exitPrice.add(buffer);       // BUY: higher limit for fill
        }

        return exitPrice;
    }

    /**
     * Exit qualification result.
     */
    public static class ExitQualificationResult {
        private final boolean passed;
        private final List<String> errors;
        private final Integer calculatedQty;
        private final String orderType;
        private final BigDecimal limitPrice;
        private final String productType;

        private ExitQualificationResult(Builder builder) {
            this.passed = builder.errors.isEmpty();
            this.errors = List.copyOf(builder.errors);
            this.calculatedQty = builder.calculatedQty;
            this.orderType = builder.orderType;
            this.limitPrice = builder.limitPrice;
            this.productType = builder.productType;
        }

        public boolean passed() { return passed; }
        public List<String> errors() { return errors; }
        public Integer calculatedQty() { return calculatedQty; }
        public String orderType() { return orderType; }
        public BigDecimal limitPrice() { return limitPrice; }
        public String productType() { return productType; }

        public static class Builder {
            private final List<String> errors = new ArrayList<>();
            private Integer calculatedQty;
            private String orderType;
            private BigDecimal limitPrice;
            private String productType;

            public Builder addError(String code, String message) {
                errors.add(code + ": " + message);
                return this;
            }

            public Builder calculatedQty(Integer qty) {
                this.calculatedQty = qty;
                return this;
            }

            public Builder orderType(String type) {
                this.orderType = type;
                return this;
            }

            public Builder limitPrice(BigDecimal price) {
                this.limitPrice = price;
                return this;
            }

            public Builder productType(String type) {
                this.productType = type;
                return this;
            }

            public ExitQualificationResult build() {
                return new ExitQualificationResult(this);
            }
        }
    }
}
