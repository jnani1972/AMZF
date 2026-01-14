package in.annupaper.repository;

import in.annupaper.broker.BrokerAdapter;

import java.util.List;

public interface InstrumentRepository {
    /**
     * Save instruments to database.
     */
    void saveInstruments(String brokerId, List<BrokerAdapter.Instrument> instruments);

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

    record InstrumentSearchResult(
        String symbol,
        String name,
        String exchange,
        String instrumentType
    ) {}
}
