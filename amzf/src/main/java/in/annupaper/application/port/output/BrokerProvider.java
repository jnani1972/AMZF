package in.annupaper.application.port.output;

import in.annupaper.domain.model.BrokerAdapter;

/**
 * Interface to provide access to broker adapters from the application layer.
 * Decouples Use Cases from direct infrastructure dependency on
 * BrokerAdapterFactory.
 */
public interface BrokerProvider {
    /**
     * Get or create a broker adapter for the given user-broker link.
     *
     * @param userBrokerId The unique ID of the user-broker link
     * @param brokerCode   The broker code (e.g., ZERODHA, FYERS)
     * @return The broker adapter instance, or null if unrelated error prevents
     *         creation
     */
    BrokerAdapter getAdapter(String userBrokerId, String brokerCode);
}
