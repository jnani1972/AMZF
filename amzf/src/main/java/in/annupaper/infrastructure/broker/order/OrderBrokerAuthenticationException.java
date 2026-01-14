package in.annupaper.infrastructure.broker.order;

/**
 * Exception thrown when order broker authentication fails.
 */
public class OrderBrokerAuthenticationException extends RuntimeException {

    private final String brokerCode;
    private final String userBrokerId;

    public OrderBrokerAuthenticationException(String brokerCode, String userBrokerId, String message) {
        super(String.format("[%s:%s] %s", brokerCode, userBrokerId, message));
        this.brokerCode = brokerCode;
        this.userBrokerId = userBrokerId;
    }

    public OrderBrokerAuthenticationException(String brokerCode, String userBrokerId, String message, Throwable cause) {
        super(String.format("[%s:%s] %s", brokerCode, userBrokerId, message), cause);
        this.brokerCode = brokerCode;
        this.userBrokerId = userBrokerId;
    }

    public String getBrokerCode() {
        return brokerCode;
    }

    public String getUserBrokerId() {
        return userBrokerId;
    }
}
