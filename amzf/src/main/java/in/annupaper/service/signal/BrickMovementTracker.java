package in.annupaper.service.signal;

import in.annupaper.domain.trade.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BrickMovementTracker - Tracks last exit price per symbol to enforce minimum brick movement.
 */
public final class BrickMovementTracker {
    private static final Logger log = LoggerFactory.getLogger(BrickMovementTracker.class);
    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);

    // Default minimum brick movement (0.5% = 0.005)
    private static final BigDecimal DEFAULT_MIN_BRICK_PCT = new BigDecimal("0.005");

    // Track last exit: symbol -> direction -> last exit price
    private final Map<String, Map<Direction, BigDecimal>> lastExitPrices = new ConcurrentHashMap<>();

    /**
     * Check if exit should be allowed based on minimum brick movement.
     */
    public boolean shouldAllowExit(String symbol, Direction direction, BigDecimal currentPrice) {
        Map<Direction, BigDecimal> directionMap = lastExitPrices.get(symbol);

        // If no previous exit, allow
        if (directionMap == null) {
            log.debug("First exit for {} {} - allowed", symbol, direction);
            return true;
        }

        BigDecimal lastPrice = directionMap.get(direction);

        // If no previous exit for this direction, allow
        if (lastPrice == null) {
            log.debug("First exit for {} {} - allowed", symbol, direction);
            return true;
        }

        // Calculate movement
        BigDecimal movement = calculateMovement(lastPrice, currentPrice, direction);
        BigDecimal minBrick = getMinBrickMovement(symbol);

        boolean allowed = movement.compareTo(minBrick) >= 0;

        if (!allowed) {
            log.debug("Exit blocked for {} {}: movement={} < minBrick={}",
                     symbol, direction, movement, minBrick);
        }

        return allowed;
    }

    /**
     * Calculate movement percentage in favorable direction.
     */
    private BigDecimal calculateMovement(BigDecimal lastPrice, BigDecimal currentPrice, Direction direction) {
        if (lastPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal priceDiff = currentPrice.subtract(lastPrice);
        BigDecimal movementPct = priceDiff.divide(lastPrice, MC).abs();

        // For BUY/LONG: favorable movement is upward (positive)
        // For SELL/SHORT: favorable movement is downward (negative, but we abs() it)

        if (direction == Direction.BUY && priceDiff.compareTo(BigDecimal.ZERO) > 0) {
            return movementPct;
        } else if (direction == Direction.SELL && priceDiff.compareTo(BigDecimal.ZERO) < 0) {
            return movementPct;
        } else {
            // Movement is not in favorable direction
            return BigDecimal.ZERO;
        }
    }

    /**
     * Update last exit price for a symbol and direction.
     */
    public void recordExit(String symbol, Direction direction, BigDecimal exitPrice) {
        lastExitPrices.computeIfAbsent(symbol, k -> new ConcurrentHashMap<>())
                      .put(direction, exitPrice);

        log.debug("Recorded exit: {} {} @ {}", symbol, direction, exitPrice);
    }

    /**
     * Get minimum brick movement for a symbol.
     * Can be overridden per symbol in future (from config/database).
     */
    private BigDecimal getMinBrickMovement(String symbol) {
        // TODO: Load from configuration or database per symbol
        return DEFAULT_MIN_BRICK_PCT;
    }

    /**
     * Get last exit price for a symbol and direction (for testing/debugging).
     */
    public BigDecimal getLastExitPrice(String symbol, Direction direction) {
        Map<Direction, BigDecimal> directionMap = lastExitPrices.get(symbol);
        if (directionMap == null) return null;
        return directionMap.get(direction);
    }

    /**
     * Clear history for a symbol (useful for testing or manual reset).
     */
    public void clear(String symbol) {
        lastExitPrices.remove(symbol);
        log.info("Cleared exit history for {}", symbol);
    }

    /**
     * Clear all history.
     */
    public void clearAll() {
        lastExitPrices.clear();
        log.info("Cleared all exit history");
    }
}
