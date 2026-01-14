package in.annupaper.infrastructure.broker.data;

import java.util.List;

/**
 * Exception thrown when symbol subscription/unsubscription fails.
 */
public class BrokerSubscriptionException extends RuntimeException {

    private final String brokerCode;
    private final String userBrokerId;
    private final List<String> symbols;

    public BrokerSubscriptionException(String brokerCode, String userBrokerId,
                                      List<String> symbols, String message) {
        super(String.format("[%s:%s] Failed for %d symbols: %s",
            brokerCode, userBrokerId, symbols.size(), message));
        this.brokerCode = brokerCode;
        this.userBrokerId = userBrokerId;
        this.symbols = symbols;
    }

    public BrokerSubscriptionException(String brokerCode, String userBrokerId,
                                      List<String> symbols, String message, Throwable cause) {
        super(String.format("[%s:%s] Failed for %d symbols: %s",
            brokerCode, userBrokerId, symbols.size(), message), cause);
        this.brokerCode = brokerCode;
        this.userBrokerId = userBrokerId;
        this.symbols = symbols;
    }

    public String getBrokerCode() {
        return brokerCode;
    }

    public String getUserBrokerId() {
        return userBrokerId;
    }

    public List<String> getSymbols() {
        return symbols;
    }
}
