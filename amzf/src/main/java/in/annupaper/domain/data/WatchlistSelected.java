package in.annupaper.domain.data;

import java.time.Instant;

/**
 * Level 2: Admin Selected Watchlist - Curated from templates.
 */
public record WatchlistSelected(
    String selectedId,
    String name,
    String sourceTemplateId,
    String description,
    boolean enabled,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt,
    int version
) {
    public boolean isActive() {
        return enabled && deletedAt == null;
    }
}
