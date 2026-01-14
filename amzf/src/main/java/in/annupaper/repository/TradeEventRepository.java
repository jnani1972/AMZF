package in.annupaper.repository;

import in.annupaper.domain.trade.TradeEvent;

import java.util.List;

/**
 * Repository for trade events (append-only log).
 */
public interface TradeEventRepository {
    /**
     * Append event to log.
     */
    TradeEvent append(TradeEvent e);
    
    /**
     * List events after a given sequence number (all events).
     */
    List<TradeEvent> listAfterSeq(long afterSeq, int limit);
    
    /**
     * List events after a given sequence number, filtered by user.
     * Only returns GLOBAL events and events for the specified user.
     */
    List<TradeEvent> listAfterSeqForUser(long afterSeq, int limit, String userId);
    
    /**
     * List events after a given sequence number, filtered by user-broker.
     * Only returns GLOBAL events, USER events for the user, and USER_BROKER events for the user-broker.
     */
    List<TradeEvent> listAfterSeqForUserBroker(long afterSeq, int limit, String userId, String userBrokerId);
    
    /**
     * Get the latest sequence number.
     */
    long latestSeq();
}
