package in.annupaper.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Signal generated from main DATA broker feed.
 * One signal can result in multiple trade intents (fan-out to user-brokers).
 */
public record Signal(
        String signalId,

        // Signal identification
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

        // Reference prices (from DATA broker)
        BigDecimal refPrice,
        BigDecimal refBid,
        BigDecimal refAsk,
        BigDecimal entryLow,
        BigDecimal entryHigh,

        // Boundaries snapshot
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

        // Timestamps
        Instant generatedAt,
        Instant expiresAt,

        // Status
        String status, // ACTIVE | EXPIRED | CANCELLED

        // Immutable audit trail
        Instant deletedAt,
        int version) {
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public boolean isExpired() {
        return "EXPIRED".equals(status) ||
                (expiresAt != null && Instant.now().isAfter(expiresAt));
    }

    public boolean hasTripleConfluence() {
        return "TRIPLE".equals(confluenceType);
    }
}
