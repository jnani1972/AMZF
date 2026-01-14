package in.annupaper.infrastructure.broker.order;

/**
 * Exception thrown when order cancellation fails.
 */
public class OrderCancellationException extends RuntimeException {

    private final String brokerCode;
    private final String userBrokerId;
    private final String brokerOrderId;

    public OrderCancellationException(String brokerCode, String userBrokerId,
                                     String brokerOrderId, String message) {
        super(String.format("[%s:%s] Order cancellation failed for %s: %s",
            brokerCode, userBrokerId, brokerOrderId, message));
        this.brokerCode = brokerCode;
        this.userBrokerId = userBrokerId;
        this.brokerOrderId = brokerOrderId;
    }

    public OrderCancellationException(String brokerCode, String userBrokerId,
                                     String brokerOrderId, String message, Throwable cause) {
        super(String.format("[%s:%s] Order cancellation failed for %s: %s",
            brokerCode, userBrokerId, brokerOrderId, message), cause);
        this.brokerCode = brokerCode;
        this.userBrokerId = userBrokerId;
        this.brokerOrderId = brokerOrderId;
    }

    public String getBrokerCode() {
        return brokerCode;
    }

    public String getUserBrokerId() {
        return userBrokerId;
    }

    public String getBrokerOrderId() {
        return brokerOrderId;
    }
}
