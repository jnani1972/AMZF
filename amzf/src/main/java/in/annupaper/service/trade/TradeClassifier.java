package in.annupaper.service.trade;

import in.annupaper.domain.model.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * TradeClassifier - Determines NEWBUY vs REBUY classification.
 *
 * PURPOSE:
 * First position in a symbol = NEWBUY (full risk budget)
 * Additional positions = REBUY (averaging, reduced risk)
 *
 * CLASSIFICATION RULES:
 * - tradeNumber = 1 → NEWBUY (first entry into symbol)
 * - tradeNumber > 1 → REBUY (averaging/adding to position)
 *
 * CALCULATION:
 * Count existing non-terminal trades for (userId, symbol):
 * - Include: CREATED, PENDING, OPEN, EXITING
 * - Exclude: CLOSED, REJECTED, CANCELLED, ERROR
 *
 * tradeNumber = count + 1
 *
 * PURE FUNCTION:
 * No side effects, no state. Thread-safe.
 */
public final class TradeClassifier {
    private static final Logger log = LoggerFactory.getLogger(TradeClassifier.class);

    /**
     * Calculate trade number for a new trade.
     *
     * @param userId User identifier
     * @param symbol Symbol (e.g., "NSE:INFY")
     * @param existingTrades List of existing trades for this user + symbol
     * @return TradeClassification with tradeNumber and entry kind
     */
    public static TradeClassification classify(
        String userId,
        String symbol,
        List<Trade> existingTrades
    ) {
        // Count non-terminal trades
        long activeCount = existingTrades.stream()
            .filter(TradeClassifier::isNonTerminal)
            .count();

        int tradeNumber = (int) activeCount + 1;
        EntryKind entryKind = determineEntryKind(tradeNumber, existingTrades);

        log.debug("Trade classification: user={} symbol={} tradeNumber={} entryKind={} (existing={})",
            userId, symbol, tradeNumber, entryKind, activeCount);

        return new TradeClassification(tradeNumber, entryKind);
    }

    /**
     * Determine entry kind based on trade number and existing trades.
     *
     * NEWBUY: First entry into symbol (tradeNumber == 1)
     * REBUY: Additional entry (tradeNumber > 1)
     * AVERAGE: REBUY that's averaging down (price < existing avg)
     * PYRAMID: REBUY that's adding to winners (price > existing avg)
     *
     * @param tradeNumber Calculated trade number
     * @param existingTrades Existing trades for context
     * @return Entry kind classification
     */
    private static EntryKind determineEntryKind(int tradeNumber, List<Trade> existingTrades) {
        if (tradeNumber == 1) {
            return EntryKind.NEWBUY;
        }

        // TODO: Future enhancement - distinguish AVERAGE vs PYRAMID
        // For now, all rebuys are classified as REBUY
        // To implement:
        // 1. Calculate average entry price of OPEN trades
        // 2. Compare new entry price to average
        // 3. Below average → AVERAGE (averaging down)
        // 4. Above average → PYRAMID (adding to winner)

        return EntryKind.REBUY;
    }

    /**
     * Check if trade is non-terminal (should count toward trade number).
     *
     * Non-terminal states: CREATED, PENDING, OPEN, EXITING
     * Terminal states: CLOSED, REJECTED, CANCELLED, ERROR
     *
     * @param trade Trade to check
     * @return true if trade is non-terminal
     */
    private static boolean isNonTerminal(Trade trade) {
        String status = trade.status();
        return switch (status) {
            case "CREATED", "PENDING", "OPEN", "EXITING" -> true;
            case "CLOSED", "REJECTED", "CANCELLED", "ERROR" -> false;
            default -> {
                log.warn("Unknown trade status: {} for trade {}", status, trade.tradeId());
                yield false;  // Treat unknown as terminal to be safe
            }
        };
    }

    /**
     * Trade classification result.
     *
     * @param tradeNumber Sequential trade number for this user + symbol (1, 2, 3, ...)
     * @param entryKind Entry kind classification (NEWBUY, REBUY, etc.)
     */
    public record TradeClassification(
        int tradeNumber,
        EntryKind entryKind
    ) {}

    /**
     * Entry kind classification.
     */
    public enum EntryKind {
        /**
         * NEWBUY - First entry into symbol for this user.
         * Full risk budget available.
         */
        NEWBUY,

        /**
         * REBUY - Additional entry into symbol (generic).
         * Check averaging gate, reduced risk allocation.
         */
        REBUY,

        /**
         * AVERAGE - REBUY with entry price below existing average.
         * Averaging down into a losing position.
         * Requires stricter validation (utility gate, etc.)
         */
        AVERAGE,

        /**
         * PYRAMID - REBUY with entry price above existing average.
         * Adding to a winning position.
         * Different risk profile than averaging.
         */
        PYRAMID
    }
}
