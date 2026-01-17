package in.annupaper.service.candle;

import in.annupaper.domain.data.TimeframeType;
import in.annupaper.domain.data.Candle;
import in.annupaper.domain.repository.CandleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Candle Store - Dual storage (in-memory + PostgreSQL).
 *
 * Intraday candles: Stored in memory for fast access + PostgreSQL for persistence.
 * Historical candles: PostgreSQL only.
 */
public final class CandleStore {
    private static final Logger log = LoggerFactory.getLogger(CandleStore.class);

    private final CandleRepository candleRepo;

    // In-memory cache: symbol -> timeframe -> candles (sorted by timestamp desc)
    private final Map<String, Map<TimeframeType, CopyOnWriteArrayList<Candle>>> cache = new ConcurrentHashMap<>();

    // Max candles to keep in memory per (symbol, timeframe)
    private static final int MAX_MEMORY_CANDLES = 500;

    public CandleStore(CandleRepository candleRepo) {
        this.candleRepo = candleRepo;
    }

    /**
     * Add a candle (intraday).
     * Stores in both memory and PostgreSQL.
     */
    public void addIntraday(Candle candle) {
        // Memory
        cache.computeIfAbsent(candle.symbol(), k -> new ConcurrentHashMap<>())
             .computeIfAbsent(candle.timeframeType(), k -> new CopyOnWriteArrayList<>())
             .add(0, candle);  // Add at front (most recent)

        // Limit memory size
        List<Candle> list = cache.get(candle.symbol()).get(candle.timeframeType());
        if (list.size() > MAX_MEMORY_CANDLES) {
            list.remove(list.size() - 1);  // Remove oldest
        }

        // PostgreSQL (async)
        try {
            candleRepo.insert(candle);
        } catch (Exception e) {
            log.error("Failed to persist candle: {}", e.getMessage());
        }
    }

    /**
     * Add multiple candles (batch insert).
     * Used for historical backfill - PostgreSQL only, no memory cache.
     */
    public void addBatch(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) return;

        try {
            candleRepo.insertBatch(candles);
            log.info("Persisted {} candles", candles.size());
        } catch (Exception e) {
            log.error("Failed to persist candle batch: {}", e.getMessage());
        }
    }

    /**
     * Upsert a candle (insert or update on conflict).
     * Stores in both memory and PostgreSQL.
     * Useful for backfill operations where duplicates may occur.
     */
    public void upsert(Candle candle) {
        // Memory - check if exists first, update if found
        Map<TimeframeType, CopyOnWriteArrayList<Candle>> tfMap =
            cache.computeIfAbsent(candle.symbol(), k -> new ConcurrentHashMap<>());
        CopyOnWriteArrayList<Candle> list =
            tfMap.computeIfAbsent(candle.timeframeType(), k -> new CopyOnWriteArrayList<>());

        // Remove existing candle with same timestamp if present
        list.removeIf(c -> c.timestamp().equals(candle.timestamp()));

        // Add new candle at front (most recent)
        list.add(0, candle);

        // Limit memory size
        if (list.size() > MAX_MEMORY_CANDLES) {
            list.remove(list.size() - 1);  // Remove oldest
        }

        // PostgreSQL (upsert)
        try {
            candleRepo.upsert(candle);
        } catch (Exception e) {
            log.error("Failed to upsert candle: {}", e.getMessage());
        }
    }

    /**
     * Upsert multiple candles in batch (insert or update on conflict).
     * Used for historical backfill - PostgreSQL only, no memory cache.
     */
    public void upsertBatch(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) return;

        try {
            candleRepo.upsertBatch(candles);
            log.info("Upserted {} candles", candles.size());
        } catch (Exception e) {
            log.error("Failed to upsert candle batch: {}", e.getMessage());
        }
    }

    /**
     * Get candles from memory (intraday).
     */
    public List<Candle> getFromMemory(String symbol, TimeframeType timeframe) {
        Map<TimeframeType, CopyOnWriteArrayList<Candle>> tfMap = cache.get(symbol);
        if (tfMap == null) return List.of();

        List<Candle> candles = tfMap.get(timeframe);
        return candles != null ? List.copyOf(candles) : List.of();
    }

    /**
     * Get candles from PostgreSQL (historical or all).
     */
    public List<Candle> getFromPostgres(String symbol, TimeframeType timeframe, int limit) {
        log.debug("getFromPostgres: symbol={}, timeframe={}, limit={}", symbol, timeframe, limit);
        List<Candle> result = candleRepo.findAll(symbol, timeframe, limit);
        log.info("getFromPostgres: Found {} candles for {} {}", result.size(), symbol, timeframe);
        return result;
    }

    /**
     * Get candles within a time range (PostgreSQL).
     */
    public List<Candle> getRange(String symbol, TimeframeType timeframe, Instant from, Instant to) {
        return candleRepo.findBySymbolAndTimeframe(symbol, timeframe, from, to);
    }

    /**
     * Get the latest candle (checks memory first, then PostgreSQL).
     */
    public Candle getLatest(String symbol, TimeframeType timeframe) {
        // Check memory first
        List<Candle> memoryCandles = getFromMemory(symbol, timeframe);
        if (!memoryCandles.isEmpty()) {
            return memoryCandles.get(0);  // Most recent
        }

        // Fallback to PostgreSQL
        return candleRepo.findLatest(symbol, timeframe);
    }

    /**
     * Check if candles exist for a symbol+timeframe.
     */
    public boolean exists(String symbol, TimeframeType timeframe) {
        return candleRepo.exists(symbol, timeframe);
    }

    /**
     * Load initial candles from PostgreSQL into memory (on startup).
     */
    public void warmup(String symbol, TimeframeType timeframe) {
        List<Candle> candles = candleRepo.findAll(symbol, timeframe, MAX_MEMORY_CANDLES);
        if (!candles.isEmpty()) {
            cache.computeIfAbsent(symbol, k -> new ConcurrentHashMap<>())
                 .put(timeframe, new CopyOnWriteArrayList<>(candles));

            log.info("Warmed up {} candles for {} {}", candles.size(), symbol, timeframe);
        }
    }

    /**
     * Cleanup old candles from PostgreSQL.
     */
    public int cleanup(Instant cutoff) {
        return candleRepo.deleteOlderThan(cutoff);
    }

    /**
     * Clear memory cache.
     */
    public void clearMemory() {
        cache.clear();
        log.info("Memory cache cleared");
    }

    /**
     * Get cache stats.
     */
    public String getCacheStats() {
        int totalSymbols = cache.size();
        int totalCandles = cache.values().stream()
            .flatMap(m -> m.values().stream())
            .mapToInt(List::size)
            .sum();

        return String.format("Symbols: %d, Total candles in memory: %d", totalSymbols, totalCandles);
    }
}
