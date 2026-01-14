package in.annupaper.domain.user;

import in.annupaper.domain.trade.TradeEvent;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authenticated WebSocket session.
 */
public final class WsSession {
    private final String sessionId;
    private final String userId;
    private final Set<String> userBrokerIds;  // User's subscribed broker connections
    private final Set<String> topics;         // Subscribed event types
    private final Instant connectedAt;
    private volatile Instant lastActivity;    // volatile: written by multiple I/O threads, read by flusher

    public WsSession(String sessionId, String userId) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.userBrokerIds = ConcurrentHashMap.newKeySet();
        this.topics = ConcurrentHashMap.newKeySet();
        this.connectedAt = Instant.now();
        this.lastActivity = Instant.now();
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public Set<String> getUserBrokerIds() {
        return userBrokerIds;
    }

    public Set<String> getTopics() {
        return topics;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }

    public Instant getLastActivity() {
        return lastActivity;
    }

    public void touch() {
        this.lastActivity = Instant.now();
    }

    public void subscribeTopics(Set<String> newTopics) {
        topics.addAll(newTopics);
        touch();
    }

    public void unsubscribeTopics(Set<String> removeTopics) {
        topics.removeAll(removeTopics);
        touch();
    }

    public void subscribeBrokers(Set<String> brokerIds) {
        userBrokerIds.addAll(brokerIds);
        touch();
    }

    public void unsubscribeBrokers(Set<String> brokerIds) {
        userBrokerIds.removeAll(brokerIds);
        touch();
    }

    /**
     * Check if this session should receive an event.
     */
    public boolean shouldReceive(TradeEvent event) {
        // Check topic subscription
        if (!topics.isEmpty() && !topics.contains(event.type().name())) {
            return false;
        }

        // Check visibility based on scope
        switch (event.scope()) {
            case GLOBAL:
                return true;
            case USER:
                return event.userId() != null && event.userId().equals(userId);
            case USER_BROKER:
                if (event.userId() == null || !event.userId().equals(userId)) {
                    return false;
                }
                // If user subscribed to specific brokers, check if this event's broker is in the list
                if (!userBrokerIds.isEmpty() && event.userBrokerId() != null) {
                    return userBrokerIds.contains(event.userBrokerId());
                }
                return true;
            default:
                return false;
        }
    }

    /**
     * Check if session is authenticated (has a user ID).
     */
    public boolean isAuthenticated() {
        return userId != null && !userId.isEmpty();
    }
}
