package in.annupaper.application.port.output;

import in.annupaper.domain.model.BrokerInstrument;
import in.annupaper.domain.model.InstrumentSearchResult;

import java.util.List;

public interface InstrumentRepository {
    /**
     * Save instruments to database.
     */
    void saveInstruments(String brokerId, List<BrokerInstrument> instruments);

    /**
     * Search instruments by symbol or name.
     */
    List<InstrumentSearchResult> search(String query, int limit);

    /**
     * Clear all instruments for a broker.
     */
    void clearBroker(String brokerId);

    /**
     * Get instrument count for a broker.
     */
    int getCount(String brokerId);

    /**
     * Shutdown the repository.
     */
    void shutdown();
}
