package in.annupaper.domain.model;

import java.time.Instant;

/**
 * Level 1: Watchlist Template - Predefined symbol lists.
 */
public record WatchlistTemplate(
        String templateId,
        String templateName,
        String description,
        int displayOrder,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt,
        int version) {
    public boolean isActive() {
        return enabled && deletedAt == null;
    }
}
