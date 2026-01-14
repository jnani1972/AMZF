package in.annupaper.domain.data;

import java.time.Instant;

/**
 * Symbol in a watchlist template.
 */
public record WatchlistTemplateSymbol(
    long id,
    String templateId,
    String symbol,
    int displayOrder,
    Instant createdAt
) {}
