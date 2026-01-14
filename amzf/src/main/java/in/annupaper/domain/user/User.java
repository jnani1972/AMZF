package in.annupaper.domain.user;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * User entity.
 * Immutable audit trail: version-based, deleted_at for soft delete.
 */
public record User(
    String userId,
    String email,
    String displayName,
    String passwordHash,
    String role,          // USER | ADMIN
    String status,        // ACTIVE | SUSPENDED | DELETED
    JsonNode preferences,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt,
    int version
) {
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}
