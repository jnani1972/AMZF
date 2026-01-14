package in.annupaper.service.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.annupaper.domain.common.EventScope;
import in.annupaper.domain.common.EventType;
import in.annupaper.domain.trade.TradeEvent;
import in.annupaper.repository.TradeEventRepository;
import in.annupaper.transport.ws.WsHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Event Service.
 * Reliability rule: persist event first (repository/DB), then push to WS.
 */
public final class EventService {
    private static final Logger log = LoggerFactory.getLogger(EventService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    private final TradeEventRepository repo;
    private final WsHub wsHub;
    private final AtomicLong seqFallback = new AtomicLong(0);  // Fallback if no DB
    
    public EventService(TradeEventRepository repo, WsHub wsHub) {
        this.repo = repo;
        this.wsHub = wsHub;
    }
    
    // ═══════════════════════════════════════════════════════════════
    // GLOBAL EVENTS
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * Emit a GLOBAL event (broadcast to all users).
     */
    public TradeEvent emitGlobal(EventType type, Object payloadPojo, String createdBy) {
        JsonNode payload = MAPPER.valueToTree(payloadPojo);
        TradeEvent e = new TradeEvent(
            0, type, EventScope.GLOBAL, null, null, null,
            payload, null, null, null, null, Instant.now(), createdBy
        );
        return persistAndBroadcast(e);
    }
    
    /**
     * Emit a GLOBAL event with correlation.
     */
    public TradeEvent emitGlobal(EventType type, Object payloadPojo, String signalId, String createdBy) {
        JsonNode payload = MAPPER.valueToTree(payloadPojo);
        TradeEvent e = new TradeEvent(
            0, type, EventScope.GLOBAL, null, null, null,
            payload, signalId, null, null, null, Instant.now(), createdBy
        );
        return persistAndBroadcast(e);
    }
    
    // ═══════════════════════════════════════════════════════════════
    // USER-SCOPED EVENTS
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * Emit a USER-scoped event (sent to specific user only).
     */
    public TradeEvent emitUser(EventType type, String userId, Object payloadPojo, String createdBy) {
        JsonNode payload = MAPPER.valueToTree(payloadPojo);
        TradeEvent e = new TradeEvent(
            0, type, EventScope.USER, userId, null, null,
            payload, null, null, null, null, Instant.now(), createdBy
        );
        return persistAndBroadcast(e);
    }
    
    // ═══════════════════════════════════════════════════════════════
    // USER_BROKER-SCOPED EVENTS
    // ═══════════════════════════════════════════════════════════════
    
    /**
     * Emit a USER_BROKER-scoped event (sent to specific user+broker combo).
     */
    public TradeEvent emitUserBroker(EventType type, String userId, String brokerId, String userBrokerId,
                                      Object payloadPojo, String createdBy) {
        JsonNode payload = MAPPER.valueToTree(payloadPojo);
        TradeEvent e = new TradeEvent(
            0, type, EventScope.USER_BROKER, userId, brokerId, userBrokerId,
            payload, null, null, null, null, Instant.now(), createdBy
        );
        return persistAndBroadcast(e);
    }
    
    /**
     * Emit a USER_BROKER-scoped event with full correlation.
     */
    public TradeEvent emitUserBroker(EventType type, String userId, String brokerId, String userBrokerId,
                                      Object payloadPojo, String signalId, String intentId, 
                                      String tradeId, String orderId, String createdBy) {
        JsonNode payload = MAPPER.valueToTree(payloadPojo);
        TradeEvent e = new TradeEvent(
            0, type, EventScope.USER_BROKER, userId, brokerId, userBrokerId,
            payload, signalId, intentId, tradeId, orderId, Instant.now(), createdBy
        );
        return persistAndBroadcast(e);
    }
    
    // ═══════════════════════════════════════════════════════════════
    // INTERNAL
    // ═══════════════════════════════════════════════════════════════
    
    private TradeEvent persistAndBroadcast(TradeEvent e) {
        // Persist first (source of truth)
        TradeEvent persisted = repo.append(e);
        
        // Then broadcast via WebSocket (batched, scoped)
        wsHub.publish(persisted);
        
        log.debug("Event emitted: seq={}, type={}, scope={}, userId={}", 
                  persisted.seq(), persisted.type(), persisted.scope(), persisted.userId());
        
        return persisted;
    }
    
    /**
     * Get current latest sequence number.
     */
    public long currentSeq() {
        return repo.latestSeq();
    }
}
