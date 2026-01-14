package in.annupaper.repository;

import in.annupaper.domain.broker.BrokerRole;
import in.annupaper.domain.broker.UserBroker;

import java.util.List;
import java.util.Optional;

/**
 * Repository for user-broker links.
 */
public interface UserBrokerRepository {
    /**
     * Find user-broker by ID.
     */
    Optional<UserBroker> findById(String userBrokerId);
    
    /**
     * Find all user-brokers for a user.
     */
    List<UserBroker> findByUserId(String userId);
    
    /**
     * Find all active EXEC brokers for a user.
     */
    List<UserBroker> findActiveExecBrokersByUserId(String userId);
    
    /**
     * Find all active EXEC brokers (for signal fan-out).
     */
    List<UserBroker> findAllActiveExecBrokers();

    /**
     * Find all user-brokers (admin only).
     */
    List<UserBroker> findAll();

    /**
     * Find the DATA broker (admin only, should be exactly one).
     */
    Optional<UserBroker> findDataBroker();
    
    /**
     * Find user-broker by user and broker.
     */
    Optional<UserBroker> findByUserAndBroker(String userId, String brokerId);
    
    /**
     * Save user-broker.
     */
    UserBroker save(UserBroker userBroker);
    
    /**
     * Update connection status.
     */
    void updateConnectionStatus(String userBrokerId, boolean connected, String errorMessage);
}
