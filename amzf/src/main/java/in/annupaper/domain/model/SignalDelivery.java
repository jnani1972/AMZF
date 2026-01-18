package in.annupaper.domain.model;

import java.time.Instant;

/**
 * Signal delivery record.
 */
public record SignalDelivery(
        String deliveryId,
        String signalId,
        String userBrokerId,
        String userId,
        String status, // CREATED | DELIVERED | CONSUMED | EXPIRED | REJECTED
        String intentId, // Set when consumed
        String rejectionReason,
        String userAction, // NULL | SNOOZED | DISMISSED
        Instant createdAt,
        Instant deliveredAt,
        Instant consumedAt,
        Instant userActionAt,
        Instant updatedAt,
        Instant deletedAt,
        int version) {
}
