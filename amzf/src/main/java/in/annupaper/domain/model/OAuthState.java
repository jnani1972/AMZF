package in.annupaper.domain.model;

import java.time.Instant;

/**
 * OAuth state parameter for callback validation.
 *
 * Purpose:
 * - CSRF protection (state must match between request and callback)
 * - Survives server restarts (DB-backed, not in-memory)
 * - Idempotency (used_at prevents duplicate token exchanges)
 * - Auto-expiry (states expire after 15 minutes)
 */
public record OAuthState(
        String state, // Random UUID
        String userBrokerId, // Which user-broker combo
        String brokerId, // Which broker (FYERS, ZERODHA, etc.)
        Instant createdAt, // When generated
        Instant expiresAt, // When expires (createdAt + 15 minutes)
        Instant usedAt, // When callback consumed (null = not used yet)
        Instant deletedAt // Soft delete
) {
    /**
     * Check if state is valid for use.
     */
    public boolean isValid() {
        return deletedAt == null
                && usedAt == null
                && expiresAt.isAfter(Instant.now());
    }

    /**
     * Check if state was already used (idempotency).
     */
    public boolean isAlreadyUsed() {
        return usedAt != null;
    }

    /**
     * Check if state is expired.
     */
    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }
}
