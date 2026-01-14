package in.annupaper.domain.data;

import java.math.BigDecimal;
import java.time.Instant;

// public record Watchlist(
//     Long id,
//     String userBrokerId,
//     String symbol,
//     boolean enabled,
//     Instant addedAt,
//     Instant updatedAt
// ) {
// public record Watchlist(
//     Long id,
//     String userBrokerId,
//     String symbol,
//     boolean enabled,
//     Instant addedAt,
//     Instant updatedAt,
//     Instant deletedAt,
//     int version
// ) {
//     public boolean isActive() {
//         return enabled;
//     }
// }

// OLD: Watchlist record without last_price and last_tick_time
// public record Watchlist(
//     Long id,
//     String userBrokerId,
//     String symbol,
//     Integer lotSize,
//     BigDecimal tickSize,
//     boolean isCustom,
//     boolean enabled,
//     Instant addedAt,
//     Instant updatedAt,
//     Instant lastSyncedAt,
//     Instant deletedAt,
//     int version
// ) {
//     public boolean isActive() {
//         return enabled && deletedAt == null;
//     }
// }

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
    Instant lastTickTime
) {
    public boolean isActive() {
        return enabled && deletedAt == null;
    }
}
