package in.annupaper.service.signal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SignalDeliveryIndex - Fast lookup for active signal deliveries.
 *
 * PURPOSE:
 * Track which signals have been delivered to which user-brokers for:
 * 1. Fast validation ("has user already seen this signal?")
 * 2. Delivery status tracking
 * 3. Metrics (deliveries per user, consumption rate)
 *
 * STRUCTURE:
 * - Map<userBrokerId, Set<signalId>> for DELIVERED/ACTIVE deliveries
 * - Built from DB on startup
 * - Updated on delivery state transitions
 *
 * THREAD-SAFETY:
 * ConcurrentHashMap + ConcurrentHashMap.newKeySet() for thread-safe updates
 * from multiple coordinator partitions.
 *
 * LIFECYCLE:
 * 1. Rebuild on startup from DB (all active deliveries)
 * 2. Add when delivery transitions to DELIVERED
 * 3. Remove when delivery transitions to CONSUMED/EXPIRED/REJECTED
 *
 * Mirrors: ActiveTradeIndex pattern exactly
 */
public final class SignalDeliveryIndex {
    private static final Logger log = LoggerFactory.getLogger(SignalDeliveryIndex.class);

    // userBrokerId → Set<signalId> of active deliveries
    private final Map<String, Set<String>> userBrokerToSignals;

    // signalId → Set<userBrokerId> for reverse lookup (fan-out tracking)
    private final Map<String, Set<String>> signalToUserBrokers;

    // deliveryId → (signalId, userBrokerId) for cleanup
    private final Map<String, DeliveryKey> deliveryToKey;

    public SignalDeliveryIndex() {
        this.userBrokerToSignals = new ConcurrentHashMap<>();
        this.signalToUserBrokers = new ConcurrentHashMap<>();
        this.deliveryToKey = new ConcurrentHashMap<>();
    }

    /**
     * Rebuild index from list of active deliveries (called on startup).
     *
     * @param activeDeliveries List of (deliveryId, signalId, userBrokerId) tuples
     */
    public void rebuild(List<DeliveryIndexEntry> activeDeliveries) {
        userBrokerToSignals.clear();
        signalToUserBrokers.clear();
        deliveryToKey.clear();

        for (DeliveryIndexEntry entry : activeDeliveries) {
            addDelivery(entry.deliveryId(), entry.signalId(), entry.userBrokerId());
        }

        log.info("SignalDeliveryIndex rebuilt: {} user-brokers, {} active deliveries",
            userBrokerToSignals.size(), deliveryToKey.size());
    }

    /**
     * Add a delivery to the index (when it transitions to DELIVERED).
     *
     * @param deliveryId Delivery identifier
     * @param signalId Signal identifier
     * @param userBrokerId User-broker identifier
     */
    public void addDelivery(String deliveryId, String signalId, String userBrokerId) {
        userBrokerToSignals.computeIfAbsent(userBrokerId, k -> ConcurrentHashMap.newKeySet())
            .add(signalId);

        signalToUserBrokers.computeIfAbsent(signalId, k -> ConcurrentHashMap.newKeySet())
            .add(userBrokerId);

        deliveryToKey.put(deliveryId, new DeliveryKey(signalId, userBrokerId));

        log.debug("Delivery added to index: {} → {} for {}", deliveryId, signalId, userBrokerId);
    }

    /**
     * Remove a delivery from the index (when it's consumed/expired/rejected).
     *
     * @param deliveryId Delivery identifier
     */
    public void removeDelivery(String deliveryId) {
        DeliveryKey key = deliveryToKey.remove(deliveryId);
        if (key != null) {
            // Remove from userBroker → signals
            Set<String> userSignals = userBrokerToSignals.get(key.userBrokerId());
            if (userSignals != null) {
                userSignals.remove(key.signalId());
                if (userSignals.isEmpty()) {
                    userBrokerToSignals.remove(key.userBrokerId());
                }
            }

            // Remove from signal → userBrokers
            Set<String> signalUsers = signalToUserBrokers.get(key.signalId());
            if (signalUsers != null) {
                signalUsers.remove(key.userBrokerId());
                if (signalUsers.isEmpty()) {
                    signalToUserBrokers.remove(key.signalId());
                }
            }

            log.debug("Delivery removed from index: {} (was {} → {})",
                deliveryId, key.signalId(), key.userBrokerId());
        }
    }

    /**
     * Check if signal has been delivered to user-broker.
     *
     * @param signalId Signal identifier
     * @param userBrokerId User-broker identifier
     * @return true if delivery exists
     */
    public boolean hasDelivery(String signalId, String userBrokerId) {
        Set<String> userSignals = userBrokerToSignals.get(userBrokerId);
        return userSignals != null && userSignals.contains(signalId);
    }

    /**
     * Get all active signal IDs for a user-broker.
     *
     * Returns empty set if no active deliveries.
     * The returned set is a snapshot (safe to iterate).
     *
     * @param userBrokerId User-broker identifier
     * @return Set of signal IDs (may be empty, never null)
     */
    public Set<String> getActiveSignals(String userBrokerId) {
        Set<String> signals = userBrokerToSignals.get(userBrokerId);
        return signals != null ? new HashSet<>(signals) : Collections.emptySet();
    }

    /**
     * Get all user-brokers who received a signal.
     *
     * Returns empty set if signal not delivered to anyone.
     * The returned set is a snapshot (safe to iterate).
     *
     * @param signalId Signal identifier
     * @return Set of user-broker IDs (may be empty, never null)
     */
    public Set<String> getSignalRecipients(String signalId) {
        Set<String> users = signalToUserBrokers.get(signalId);
        return users != null ? new HashSet<>(users) : Collections.emptySet();
    }

    /**
     * Get total count of active deliveries.
     *
     * @return Number of active deliveries across all user-brokers
     */
    public int size() {
        return deliveryToKey.size();
    }

    /**
     * Get count of user-brokers with active deliveries.
     *
     * @return Number of user-brokers with at least one active delivery
     */
    public int userBrokerCount() {
        return userBrokerToSignals.size();
    }

    /**
     * Get statistics for monitoring/debugging.
     *
     * @return Index statistics
     */
    public IndexStats getStats() {
        int totalDeliveries = deliveryToKey.size();
        int totalUserBrokers = userBrokerToSignals.size();

        int maxDeliveriesPerUser = userBrokerToSignals.values().stream()
            .mapToInt(Set::size)
            .max()
            .orElse(0);

        double avgDeliveriesPerUser = totalUserBrokers > 0
            ? (double) totalDeliveries / totalUserBrokers
            : 0.0;

        return new IndexStats(totalDeliveries, totalUserBrokers, maxDeliveriesPerUser, avgDeliveriesPerUser);
    }

    /**
     * Remove all deliveries for a signal (when signal expires/cancels).
     *
     * @param signalId Signal identifier
     */
    public void removeBySignal(String signalId) {
        Set<String> userBrokers = signalToUserBrokers.remove(signalId);
        if (userBrokers != null) {
            // Remove this signal from all user-broker mappings
            for (String userBrokerId : userBrokers) {
                Set<String> userSignals = userBrokerToSignals.get(userBrokerId);
                if (userSignals != null) {
                    userSignals.remove(signalId);
                    if (userSignals.isEmpty()) {
                        userBrokerToSignals.remove(userBrokerId);
                    }
                }
            }

            // Remove all delivery keys for this signal
            deliveryToKey.entrySet().removeIf(entry -> entry.getValue().signalId().equals(signalId));

            log.debug("Removed all deliveries for signal {} ({} user-brokers)", signalId, userBrokers.size());
        }
    }

    /**
     * Clear the entire index (for testing).
     */
    public void clear() {
        userBrokerToSignals.clear();
        signalToUserBrokers.clear();
        deliveryToKey.clear();
        log.info("SignalDeliveryIndex cleared");
    }

    /**
     * Simple tuple for rebuild operation.
     */
    public record DeliveryIndexEntry(String deliveryId, String signalId, String userBrokerId) {}

    /**
     * Internal delivery key for reverse lookup.
     */
    private record DeliveryKey(String signalId, String userBrokerId) {}

    /**
     * Index statistics for monitoring.
     */
    public record IndexStats(
        int totalDeliveries,
        int totalUserBrokers,
        int maxDeliveriesPerUser,
        double avgDeliveriesPerUser
    ) {}
}
