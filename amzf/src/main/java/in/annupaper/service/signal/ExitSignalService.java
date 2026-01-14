package in.annupaper.service.signal;

import in.annupaper.broker.BrokerAdapter;
import in.annupaper.domain.trade.Direction;
import in.annupaper.domain.common.EventType;
import in.annupaper.domain.trade.ExitReason;
import in.annupaper.domain.signal.ExitSignal;
import in.annupaper.domain.signal.MtfGlobalConfig;
import in.annupaper.domain.trade.Trade;
import in.annupaper.repository.TradeRepository;
import in.annupaper.service.MtfConfigService;
import in.annupaper.service.core.EventService;
import in.annupaper.service.trade.TradeManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ✅ P0: ExitSignalService - Monitor open trades and generate exit signals on every tick.
 *
 * POSITION_TRACKING_LIVE enforcement:
 * - Queries TradeRepository for open trades (not HashMap)
 * - Each tick queries DB for symbol's open trades
 * - No in-memory trade state (DB is single source of truth)
 */
public final class ExitSignalService implements BrokerAdapter.TickListener {
    private static final Logger log = LoggerFactory.getLogger(ExitSignalService.class);

    private final TradeRepository tradeRepo;
    private final BrickMovementTracker brickTracker;
    private final EventService eventService;
    private final SignalManagementService signalManagementService;
    private final TradeManagementService tradeManagementService;
    private final MtfConfigService mtfConfigService;

    public ExitSignalService(
        TradeRepository tradeRepo,
        BrickMovementTracker brickTracker,
        EventService eventService,
        SignalManagementService signalManagementService,
        TradeManagementService tradeManagementService,
        MtfConfigService mtfConfigService
    ) {
        this.tradeRepo = tradeRepo;
        this.brickTracker = brickTracker;
        this.eventService = eventService;
        this.signalManagementService = signalManagementService;
        this.tradeManagementService = tradeManagementService;
        this.mtfConfigService = mtfConfigService;
    }

    /**
     * ✅ P0: Process incoming tick and check exit conditions.
     *
     * Queries TradeRepository for open trades (not HashMap).
     * DB is single source of truth for position tracking.
     */
    @Override
    public void onTick(BrokerAdapter.Tick tick) {
        String symbol = tick.symbol();
        BigDecimal price = tick.lastPrice();

        // ✅ P0: Query DB for open trades on this symbol (not HashMap!)
        List<Trade> openTrades = tradeRepo.findBySymbol(symbol).stream()
            .filter(Trade::isOpen)
            .toList();

        if (openTrades.isEmpty()) {
            return;  // No open trades for this symbol
        }

        // Update trailing stops and check exit conditions for each open trade
        for (Trade trade : openTrades) {
            // Update trailing stop if enabled in config
            updateTrailingStopIfNeeded(trade, price);

            // Check exit conditions
            checkExitConditions(trade, price);
        }
    }

    /**
     * Check all exit conditions for a trade.
     */
    private void checkExitConditions(Trade trade, BigDecimal currentPrice) {
        ExitReason exitReason = null;

        // 1. Check trailing stop (highest priority if active)
        if (trade.trailingActive() && trade.trailingStopPrice() != null && shouldExitAtTrailingStop(trade, currentPrice)) {
            exitReason = ExitReason.TRAILING_STOP;
        }

        // 2. Check target hit
        if (exitReason == null && trade.exitTargetPrice() != null && shouldExitAtTarget(trade, currentPrice)) {
            exitReason = ExitReason.TARGET_HIT;
        }

        // 3. Check stop loss (effectiveFloor is stop loss for LONG trades)
        if (exitReason == null && trade.entryEffectiveFloor() != null && shouldExitAtStopLoss(trade, currentPrice)) {
            exitReason = ExitReason.STOP_LOSS;
        }

        // 4. Check time-based exit (max holding days)
        if (exitReason == null && isMaxHoldTimeExceeded(trade)) {
            exitReason = ExitReason.TIME_BASED;
        }

        // If exit condition met, check brick movement
        if (exitReason != null) {
            // ✅ V010: Use persisted direction from trade
            Direction direction;
            try {
                direction = Direction.valueOf(trade.direction());
            } catch (Exception e) {
                log.warn("Invalid direction in trade {}: {}, defaulting to BUY",
                    trade.tradeId(), trade.direction());
                direction = Direction.BUY;
            }

            if (brickTracker.shouldAllowExit(trade.symbol(), direction, currentPrice)) {
                emitExitSignal(trade, currentPrice, exitReason);
            } else {
                log.debug("Exit blocked by brick movement filter: {} @ {}",
                         trade.symbol(), currentPrice);
            }
        }
    }

    /**
     * Check if target is hit.
     * ✅ V010: Direction-aware target checking
     * - LONG (BUY): price >= target
     * - SHORT (SELL): price <= target
     */
    private boolean shouldExitAtTarget(Trade trade, BigDecimal currentPrice) {
        try {
            Direction direction = Direction.valueOf(trade.direction());
            if (direction == Direction.BUY) {
                // LONG: exit when price reaches or exceeds target
                return currentPrice.compareTo(trade.exitTargetPrice()) >= 0;
            } else {
                // SHORT: exit when price reaches or falls below target
                return currentPrice.compareTo(trade.exitTargetPrice()) <= 0;
            }
        } catch (Exception e) {
            // Default to LONG behavior
            return currentPrice.compareTo(trade.exitTargetPrice()) >= 0;
        }
    }

    /**
     * Check if stop loss is hit.
     * ✅ V010: Direction-aware stop loss checking
     * - LONG (BUY): price <= floor (effectiveFloor is stop loss)
     * - SHORT (SELL): price >= ceiling (effectiveCeiling is stop loss)
     */
    private boolean shouldExitAtStopLoss(Trade trade, BigDecimal currentPrice) {
        try {
            Direction direction = Direction.valueOf(trade.direction());
            if (direction == Direction.BUY) {
                // LONG: exit when price falls to or below floor
                return currentPrice.compareTo(trade.entryEffectiveFloor()) <= 0;
            } else {
                // SHORT: exit when price rises to or above ceiling
                return currentPrice.compareTo(trade.entryEffectiveCeiling()) >= 0;
            }
        } catch (Exception e) {
            // Default to LONG behavior
            return currentPrice.compareTo(trade.entryEffectiveFloor()) <= 0;
        }
    }

    /**
     * Check if max hold time is exceeded.
     */
    private boolean isMaxHoldTimeExceeded(Trade trade) {
        Instant now = Instant.now();
        Instant entryTime = trade.entryTimestamp();

        if (entryTime == null) {
            return false;  // Trade not filled yet
        }

        // TODO: Make max holding days configurable
        // For now, use 30 days as default
        int maxHoldingDays = 30;
        Duration maxHoldTime = Duration.ofDays(maxHoldingDays);

        return now.isAfter(entryTime.plus(maxHoldTime));
    }

    /**
     * Update trailing stop if conditions are met.
     * Tracks highest price since entry and activates/updates trailing stop per config.
     */
    private void updateTrailingStopIfNeeded(Trade trade, BigDecimal currentPrice) {
        // Get global config to check if trailing stops are enabled
        MtfGlobalConfig config = mtfConfigService.getGlobalConfig().orElse(null);
        if (config == null || !config.useTrailingStop()) {
            return;  // Trailing stops not enabled
        }

        BigDecimal entryPrice = trade.entryPrice();
        if (entryPrice == null || entryPrice.compareTo(BigDecimal.ZERO) == 0) {
            return;  // No entry price yet
        }

        // Parse direction
        Direction direction;
        try {
            direction = Direction.valueOf(trade.direction());
        } catch (Exception e) {
            log.warn("Invalid direction in trade {}: {}, skipping trailing stop",
                trade.tradeId(), trade.direction());
            return;
        }

        // Calculate favorable movement since entry
        BigDecimal favorableMovementPct;
        if (direction == Direction.BUY) {
            // LONG: favorable = (current - entry) / entry
            favorableMovementPct = currentPrice.subtract(entryPrice)
                .divide(entryPrice, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        } else {
            // SHORT: favorable = (entry - current) / entry
            favorableMovementPct = entryPrice.subtract(currentPrice)
                .divide(entryPrice, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        }

        // Check if we should activate trailing stop
        if (!trade.trailingActive()) {
            // Check if activation threshold reached
            if (favorableMovementPct.compareTo(config.trailingStopActivationPct()) >= 0) {
                // Activate trailing stop
                BigDecimal stopPrice = calculateTrailingStopPrice(currentPrice, direction, config.trailingStopDistancePct());
                tradeManagementService.updateTrailingStop(trade.tradeId(), currentPrice, stopPrice, true);
                log.info("✅ Trailing stop ACTIVATED: {} @ {} (favorable move: {}%)",
                    trade.tradeId(), currentPrice, favorableMovementPct);
            }
            return;  // Not active yet, no need to update
        }

        // Trailing stop is already active - check if we need to update highest price
        BigDecimal currentHighest = trade.trailingHighestPrice();
        if (currentHighest == null) {
            // Initialize highest price
            BigDecimal stopPrice = calculateTrailingStopPrice(currentPrice, direction, config.trailingStopDistancePct());
            tradeManagementService.updateTrailingStop(trade.tradeId(), currentPrice, stopPrice, false);
            return;
        }

        // Check if current price is new highest (direction-aware)
        boolean isNewHighest;
        if (direction == Direction.BUY) {
            // LONG: higher price is better
            isNewHighest = currentPrice.compareTo(currentHighest) > 0;
        } else {
            // SHORT: lower price is better
            isNewHighest = currentPrice.compareTo(currentHighest) < 0;
        }

        if (isNewHighest) {
            // Update trailing stop with new highest
            BigDecimal stopPrice = calculateTrailingStopPrice(currentPrice, direction, config.trailingStopDistancePct());
            tradeManagementService.updateTrailingStop(trade.tradeId(), currentPrice, stopPrice, false);
            log.debug("Trailing stop updated: {} highest={} stop={}",
                trade.tradeId(), currentPrice, stopPrice);
        }
    }

    /**
     * Calculate trailing stop price based on direction and distance percentage.
     */
    private BigDecimal calculateTrailingStopPrice(BigDecimal currentPrice, Direction direction, BigDecimal distancePct) {
        BigDecimal distance = currentPrice.multiply(distancePct)
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        if (direction == Direction.BUY) {
            // LONG: stop below current price
            return currentPrice.subtract(distance);
        } else {
            // SHORT: stop above current price
            return currentPrice.add(distance);
        }
    }

    /**
     * Check if trailing stop is hit.
     * ✅ Direction-aware trailing stop checking
     * - LONG (BUY): price <= trailing stop
     * - SHORT (SELL): price >= trailing stop
     */
    private boolean shouldExitAtTrailingStop(Trade trade, BigDecimal currentPrice) {
        try {
            Direction direction = Direction.valueOf(trade.direction());
            if (direction == Direction.BUY) {
                // LONG: exit when price falls to or below trailing stop
                return currentPrice.compareTo(trade.trailingStopPrice()) <= 0;
            } else {
                // SHORT: exit when price rises to or above trailing stop
                return currentPrice.compareTo(trade.trailingStopPrice()) >= 0;
            }
        } catch (Exception e) {
            // Default to LONG behavior
            return currentPrice.compareTo(trade.trailingStopPrice()) <= 0;
        }
    }

    /**
     * Emit exit signal event.
     *
     * UPDATED FLOW (Phase 3 - SMS Integration):
     * Delegates to SignalManagementService.onExitDetected()
     * SMS handles: episode generation, persistence, event emission
     */
    private void emitExitSignal(Trade trade, BigDecimal exitPrice, ExitReason exitReason) {
        // ✅ V010: Use persisted direction from trade
        Direction direction;
        try {
            direction = Direction.valueOf(trade.direction());
        } catch (Exception e) {
            log.warn("Invalid direction in trade {}: {}, defaulting to BUY",
                trade.tradeId(), trade.direction());
            direction = Direction.BUY;
        }

        // Calculate brick movement
        BigDecimal lastExitPrice = brickTracker.getLastExitPrice(trade.symbol(), direction);
        BigDecimal brickMovement = null;
        if (lastExitPrice != null && lastExitPrice.compareTo(BigDecimal.ZERO) > 0) {
            brickMovement = exitPrice.subtract(lastExitPrice).divide(lastExitPrice, 4, java.math.RoundingMode.HALF_UP);
        }

        // Calculate favorable movement
        BigDecimal entryPrice = trade.entryPrice();
        BigDecimal favorableMovement = null;
        if (entryPrice != null && entryPrice.compareTo(BigDecimal.ZERO) > 0) {
            favorableMovement = exitPrice.subtract(entryPrice)
                .divide(entryPrice, 4, java.math.RoundingMode.HALF_UP);
        }

        // Record exit in brick tracker
        brickTracker.recordExit(trade.symbol(), direction, exitPrice);

        // Convert to ExitCandidate and delegate to SMS
        SignalManagementService.ExitCandidate candidate = new SignalManagementService.ExitCandidate(
            trade.tradeId(),
            trade.symbol(),
            direction.name(),
            exitReason.name(),
            exitPrice,
            brickMovement,
            favorableMovement,
            trade.trailingHighestPrice(),  // highestSinceEntry - from trade
            null,  // lowestSinceEntry - TODO: implement for SHORT trades
            trade.trailingStopPrice(),     // trailingStopPrice - from trade
            trade.trailingActive(),        // trailingActive - from trade
            Instant.now()
        );

        // Delegate to SMS - it handles everything (episode generation, persistence, events)
        signalManagementService.onExitDetected(candidate);

        log.info("Exit delegated to SMS: {} {} {} @ {} (reason: {})",
                 trade.symbol(), direction, trade.tradeId(), exitPrice, exitReason);
    }
}
