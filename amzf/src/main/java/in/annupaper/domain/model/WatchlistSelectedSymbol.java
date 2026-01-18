package in.annupaper.domain.model;

import java.time.Instant;

/**
 * Symbol in an admin-selected watchlist.
 */
public record WatchlistSelectedSymbol(
        long id,
        String selectedId,
        String symbol,
        int displayOrder,
        Instant createdAt) {
}
