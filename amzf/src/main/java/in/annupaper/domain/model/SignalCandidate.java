package in.annupaper.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Signal candidate from strategy detection.
 * Immutable input to SMS.
 */
public record SignalCandidate(
        String symbol,
        Direction direction,
        SignalType signalType,

        // MTF context
        Integer htfZone,
        Integer itfZone,
        Integer ltfZone,
        String confluenceType, // NONE | SINGLE | DOUBLE | TRIPLE
        BigDecimal confluenceScore,

        // Probabilities
        BigDecimal pWin,
        BigDecimal pFill,
        BigDecimal kelly,

        // Reference prices
        BigDecimal refPrice,
        BigDecimal refBid,
        BigDecimal refAsk,
        BigDecimal entryLow,
        BigDecimal entryHigh,

        // Zone boundaries
        BigDecimal htfLow,
        BigDecimal htfHigh,
        BigDecimal itfLow,
        BigDecimal itfHigh,
        BigDecimal ltfLow,
        BigDecimal ltfHigh,
        BigDecimal effectiveFloor,
        BigDecimal effectiveCeiling,

        // Metadata
        BigDecimal confidence,
        String reason,
        List<String> tags,

        // Timing
        Instant timestamp,
        Instant expiresAt) {
}
