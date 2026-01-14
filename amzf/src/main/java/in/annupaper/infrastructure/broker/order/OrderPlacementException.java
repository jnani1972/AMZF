package in.annupaper.infrastructure.broker.order;

import in.annupaper.domain.order.OrderRequest;

/**
 * Exception thrown when order placement fails.
 */
public class OrderPlacementException extends RuntimeException {

    private final String brokerCode;
    private final String userBrokerId;
    private final OrderRequest orderRequest;

    public OrderPlacementException(String brokerCode, String userBrokerId,
                                  OrderRequest orderRequest, String message) {
        super(String.format("[%s:%s] Order placement failed for %s: %s",
            brokerCode, userBrokerId, orderRequest.symbol(), message));
        this.brokerCode = brokerCode;
        this.userBrokerId = userBrokerId;
        this.orderRequest = orderRequest;
    }

    public OrderPlacementException(String brokerCode, String userBrokerId,
                                  OrderRequest orderRequest, String message, Throwable cause) {
        super(String.format("[%s:%s] Order placement failed for %s: %s",
            brokerCode, userBrokerId, orderRequest.symbol(), message), cause);
        this.brokerCode = brokerCode;
        this.userBrokerId = userBrokerId;
        this.orderRequest = orderRequest;
    }

    public String getBrokerCode() {
        return brokerCode;
    }

    public String getUserBrokerId() {
        return userBrokerId;
    }

    public OrderRequest getOrderRequest() {
        return orderRequest;
    }
}
