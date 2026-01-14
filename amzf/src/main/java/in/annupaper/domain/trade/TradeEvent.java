package in.annupaper.domain.trade;

import com.fasterxml.jackson.databind.JsonNode;
import in.annupaper.domain.common.EventScope;
import in.annupaper.domain.common.EventType;

import java.time.Instant;

/**
 * Trade event with user/broker scoping.
 */
public record TradeEvent(
    long seq,
    EventType type,
    
    // Scoping
    EventScope scope,
    String userId,           // null for GLOBAL
    String brokerId,         // null for GLOBAL and USER
    String userBrokerId,     // null for GLOBAL and USER
    
    // Payload
    JsonNode payload,
    
    // Correlation
    String signalId,
    String intentId,
    String tradeId,
    String orderId,
    
    // Metadata
    Instant ts,
    String createdBy         // user_id of actor
) {
    /**
     * Create a GLOBAL event.
     */
    public static TradeEvent global(long seq, EventType type, JsonNode payload, String createdBy) {
        return new TradeEvent(seq, type, EventScope.GLOBAL, null, null, null, 
                              payload, null, null, null, null, Instant.now(), createdBy);
    }
    
    /**
     * Create a USER-scoped event.
     */
    public static TradeEvent user(long seq, EventType type, String userId, JsonNode payload, String createdBy) {
        return new TradeEvent(seq, type, EventScope.USER, userId, null, null,
                              payload, null, null, null, null, Instant.now(), createdBy);
    }
    
    /**
     * Create a USER_BROKER-scoped event.
     */
    public static TradeEvent userBroker(long seq, EventType type, String userId, String brokerId, 
                                         String userBrokerId, JsonNode payload, String createdBy) {
        return new TradeEvent(seq, type, EventScope.USER_BROKER, userId, brokerId, userBrokerId,
                              payload, null, null, null, null, Instant.now(), createdBy);
    }
    
    /**
     * Check if this event should be sent to a specific user.
     */
    public boolean isVisibleTo(String targetUserId) {
        if (scope == EventScope.GLOBAL) {
            return true;
        }
        return userId != null && userId.equals(targetUserId);
    }
    
    /**
     * Check if this event should be sent to a specific user-broker.
     */
    public boolean isVisibleTo(String targetUserId, String targetUserBrokerId) {
        if (scope == EventScope.GLOBAL) {
            return true;
        }
        if (scope == EventScope.USER) {
            return userId != null && userId.equals(targetUserId);
        }
        // USER_BROKER scope
        return userId != null && userId.equals(targetUserId) &&
               userBrokerId != null && userBrokerId.equals(targetUserBrokerId);
    }
    
    /**
     * Builder with correlation IDs.
     */
    public TradeEvent withCorrelation(String signalId, String intentId, String tradeId, String orderId) {
        return new TradeEvent(seq, type, scope, userId, brokerId, userBrokerId,
                              payload, signalId, intentId, tradeId, orderId, ts, createdBy);
    }
}
