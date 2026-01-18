package in.annupaper.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Level 4: User-Broker Watchlist - Final customizable watchlist.
 * is_custom: false = synced from Level 3 default, true = user added
 * last_synced_at: timestamp of last sync from Level 3
 * last_price: Last traded price (LTP) from most recent tick
 * last_tick_time: Timestamp when last tick was received
 */
public record Watchlist(
        Long id,
        String userBrokerId,
        String symbol,
        Integer lotSize,
        BigDecimal tickSize,
        boolean isCustom,
        boolean enabled,
        Instant addedAt,
        Instant updatedAt,
        Instant lastSyncedAt,
        Instant deletedAt,
        int version,
        BigDecimal lastPrice,
        Instant lastTickTime) {
    public boolean isActive() {
        return enabled && deletedAt == null;
    }
}
