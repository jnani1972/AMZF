package in.annupaper.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ActiveTradeIndex - Fast lookup for exit monitoring.
 *
 * PURPOSE:
 * On every tick for a symbol, we need to find all OPEN trades for that symbol
 * to check exit conditions. Without an index, this would require a DB query
 * per tick (expensive).
 *
 * STRUCTURE:
 * - Map<symbol, Set<tradeId>> for OPEN trades
 * - Built from DB on startup
 * - Updated on state transitions
 *
 * THREAD-SAFETY:
 * ConcurrentHashMap + ConcurrentHashMap.newKeySet() for thread-safe updates
 * from multiple executor partitions.
 *
 * LIFECYCLE:
 * 1. Rebuild on startup from DB (all OPEN trades)
 * 2. Add when trade transitions to OPEN
 * 3. Remove when trade transitions to EXITING/CLOSED/REJECTED
 */
public final class ActiveTradeIndex {
    private static final Logger log = LoggerFactory.getLogger(ActiveTradeIndex.class);

    // symbol → Set<tradeId> of OPEN trades
    private final Map<String, Set<String>> symbolToTrades;

    // tradeId → symbol for reverse lookup (cleanup)
    private final Map<String, String> tradeToSymbol;

    public ActiveTradeIndex() {
        this.symbolToTrades = new ConcurrentHashMap<>();
        this.tradeToSymbol = new ConcurrentHashMap<>();
    }

    /**
     * Rebuild index from list of open trades (called on startup).
     *
     * @param openTrades List of (tradeId, symbol) pairs
     */
    public void rebuild(List<TradeSymbolPair> openTrades) {
        symbolToTrades.clear();
        tradeToSymbol.clear();

        for (TradeSymbolPair pair : openTrades) {
            addTrade(pair.tradeId(), pair.symbol());
        }

        log.info("ActiveTradeIndex rebuilt: {} symbols, {} open trades",
            symbolToTrades.size(), tradeToSymbol.size());
    }

    /**
     * Add a trade to the index (when it transitions to OPEN).
     *
     * @param tradeId Trade identifier
     * @param symbol Symbol (e.g., "NSE:INFY")
     */
    public void addTrade(String tradeId, String symbol) {
        symbolToTrades.computeIfAbsent(symbol, k -> ConcurrentHashMap.newKeySet())
            .add(tradeId);
        tradeToSymbol.put(tradeId, symbol);

        log.debug("Trade added to index: {} → {}", tradeId, symbol);
    }

    /**
     * Remove a trade from the index (when it exits or closes).
     *
     * @param tradeId Trade identifier
     */
    public void removeTrade(String tradeId) {
        String symbol = tradeToSymbol.remove(tradeId);
        if (symbol != null) {
            Set<String> trades = symbolToTrades.get(symbol);
            if (trades != null) {
                trades.remove(tradeId);
                // Clean up empty sets to avoid memory leaks
                if (trades.isEmpty()) {
                    symbolToTrades.remove(symbol);
                }
            }
            log.debug("Trade removed from index: {} (was {})", tradeId, symbol);
        }
    }

    /**
     * Get all open trade IDs for a symbol.
     *
     * Returns empty set if no open trades for symbol.
     * The returned set is a snapshot (safe to iterate).
     *
     * @param symbol Symbol (e.g., "NSE:INFY")
     * @return Set of trade IDs (may be empty, never null)
     */
    public Set<String> getOpenTrades(String symbol) {
        Set<String> trades = symbolToTrades.get(symbol);
        return trades != null ? new HashSet<>(trades) : Collections.emptySet();
    }

    /**
     * Check if a trade is in the index (is OPEN).
     *
     * @param tradeId Trade identifier
     * @return true if trade is in index (OPEN state)
     */
    public boolean contains(String tradeId) {
        return tradeToSymbol.containsKey(tradeId);
    }

    /**
     * Get total count of open trades.
     *
     * @return Number of open trades across all symbols
     */
    public int size() {
        return tradeToSymbol.size();
    }

    /**
     * Get count of symbols with open trades.
     *
     * @return Number of symbols with at least one open trade
     */
    public int symbolCount() {
        return symbolToTrades.size();
    }

    /**
     * Get statistics for monitoring/debugging.
     *
     * @return Index statistics
     */
    public IndexStats getStats() {
        int totalTrades = tradeToSymbol.size();
        int totalSymbols = symbolToTrades.size();

        int maxTradesPerSymbol = symbolToTrades.values().stream()
            .mapToInt(Set::size)
            .max()
            .orElse(0);

        double avgTradesPerSymbol = totalSymbols > 0
            ? (double) totalTrades / totalSymbols
            : 0.0;

        return new IndexStats(totalTrades, totalSymbols, maxTradesPerSymbol, avgTradesPerSymbol);
    }

    /**
     * Clear the entire index (for testing).
     */
    public void clear() {
        symbolToTrades.clear();
        tradeToSymbol.clear();
        log.info("ActiveTradeIndex cleared");
    }

    /**
     * Simple pair for rebuild operation.
     */
    public record TradeSymbolPair(String tradeId, String symbol) {}

    /**
     * Index statistics for monitoring.
     */
    public record IndexStats(
        int totalTrades,
        int totalSymbols,
        int maxTradesPerSymbol,
        double avgTradesPerSymbol
    ) {}
}
