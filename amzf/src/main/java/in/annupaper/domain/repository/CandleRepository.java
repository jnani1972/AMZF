package in.annupaper.domain.repository;

import in.annupaper.domain.data.TimeframeType;
import in.annupaper.domain.data.Candle;

import java.time.Instant;
import java.util.List;

/**
 * Repository for candle storage.
 */
public interface CandleRepository {

    /**
     * Insert a candle (upsert on conflict).
     * Uses unique constraint (symbol, timeframe, ts) to handle duplicates.
     * If candle exists, updates OHLCV values.
     */
    void insert(Candle candle);

    /**
     * Insert multiple candles in batch (upsert on conflict).
     * Uses unique constraint (symbol, timeframe, ts) to handle duplicates.
     */
    void insertBatch(List<Candle> candles);

    /**
     * Upsert a candle (explicit insert-or-update).
     * Alias for insert() to make intent clear during backfill operations.
     */
    default void upsert(Candle candle) {
        insert(candle);
    }

    /**
     * Upsert multiple candles in batch.
     * Alias for insertBatch() to make intent clear during backfill operations.
     */
    default void upsertBatch(List<Candle> candles) {
        insertBatch(candles);
    }

    /**
     * Find candles for a symbol and timeframe within a time range.
     */
    List<Candle> findBySymbolAndTimeframe(String symbol, TimeframeType timeframe, Instant from, Instant to);

    /**
     * Find the latest candle for a symbol and timeframe.
     */
    Candle findLatest(String symbol, TimeframeType timeframe);

    /**
     * Find all candles for a symbol and timeframe (ordered by timestamp desc).
     */
    List<Candle> findAll(String symbol, TimeframeType timeframe, int limit);

    /**
     * Check if candles exist for a symbol and timeframe.
     */
    boolean exists(String symbol, TimeframeType timeframe);

    /**
     * Delete old candles (for cleanup).
     */
    int deleteOlderThan(Instant cutoff);
}
