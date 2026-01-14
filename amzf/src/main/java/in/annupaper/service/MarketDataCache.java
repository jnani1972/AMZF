package in.annupaper.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache for latest tick prices.
 * Provides fast access to current market data without DB queries.
 */
public final class MarketDataCache {
    private static final Logger log = LoggerFactory.getLogger(MarketDataCache.class);

    private final ConcurrentHashMap<String, TickData> latestTicks = new ConcurrentHashMap<>();

    /**
     * Update latest tick price for a symbol.
     */
    public void updateTick(String symbol, BigDecimal lastPrice, Instant timestamp) {
        latestTicks.put(symbol, new TickData(lastPrice, timestamp));
        log.debug("Updated tick cache: {} = {} @ {}", symbol, lastPrice, timestamp);
    }

    /**
     * Get latest tick for a symbol.
     */
    public TickData getLatestTick(String symbol) {
        return latestTicks.get(symbol);
    }

    /**
     * Get all latest ticks (for debugging/monitoring).
     */
    public Map<String, TickData> getAllTicks() {
        return Map.copyOf(latestTicks);
    }

    /**
     * Get latest ticks for specific symbols.
     */
    public Map<String, TickData> getLatestTicks(Iterable<String> symbols) {
        Map<String, TickData> result = new ConcurrentHashMap<>();
        for (String symbol : symbols) {
            TickData tick = latestTicks.get(symbol);
            if (tick != null) {
                result.put(symbol, tick);
            }
        }
        return result;
    }

    /**
     * Clear all cached ticks.
     */
    public void clear() {
        latestTicks.clear();
        log.info("Market data cache cleared");
    }

    /**
     * Get cache size.
     */
    public int size() {
        return latestTicks.size();
    }

    /**
     * Immutable tick data.
     */
    public record TickData(BigDecimal lastPrice, Instant timestamp) {
        public TickData {
            if (lastPrice == null || timestamp == null) {
                throw new IllegalArgumentException("lastPrice and timestamp cannot be null");
            }
        }
    }
}
