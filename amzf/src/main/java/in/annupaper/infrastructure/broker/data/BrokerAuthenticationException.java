package in.annupaper.infrastructure.broker.data;

/**
 * Exception thrown when broker authentication fails.
 */
public class BrokerAuthenticationException extends RuntimeException {

    private final String brokerCode;
    private final String userBrokerId;

    public BrokerAuthenticationException(String brokerCode, String userBrokerId, String message) {
        super(String.format("[%s:%s] %s", brokerCode, userBrokerId, message));
        this.brokerCode = brokerCode;
        this.userBrokerId = userBrokerId;
    }

    public BrokerAuthenticationException(String brokerCode, String userBrokerId, String message, Throwable cause) {
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
