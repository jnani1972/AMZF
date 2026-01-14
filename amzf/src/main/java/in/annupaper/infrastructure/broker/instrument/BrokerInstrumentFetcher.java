package in.annupaper.infrastructure.broker.instrument;

import in.annupaper.broker.BrokerAdapter;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for fetching instrument data from brokers.
 *
 * Each broker provides instrument lists in different formats:
 * - Upstox: CSV file via HTTP
 * - Dhan: CSV file via HTTP
 * - Zerodha: JSON file via HTTP
 * - FYERS: Proprietary format
 *
 * This interface abstracts the fetching and parsing logic.
 */
public interface BrokerInstrumentFetcher {

    /**
     * Fetch all instruments from the broker.
     *
     * @return CompletableFuture with list of instruments
     */
    CompletableFuture<List<BrokerAdapter.Instrument>> fetchAll();

    /**
     * Fetch instruments with delta support (if broker supports it).
     * Only returns instruments that changed since lastUpdateHash.
     *
     * @param lastUpdateHash Hash from previous fetch (for delta updates)
     * @return CompletableFuture with list of changed instruments
     */
    CompletableFuture<FetchResult> fetchDelta(String lastUpdateHash);

    /**
     * Get broker code.
     */
    String getBrokerCode();

    /**
     * Check if broker supports delta updates.
     */
    boolean supportsDeltaUpdates();

    /**
     * Fetch result with metadata.
     */
    record FetchResult(
        List<BrokerAdapter.Instrument> instruments,
        String updateHash,        // Hash for next delta fetch
        boolean isDelta,          // Was this a delta fetch?
        long totalCount,          // Total instruments fetched
        long bytesFetched         // Size of data downloaded
    ) {
        /**
         * Create full fetch result.
         */
        public static FetchResult full(List<BrokerAdapter.Instrument> instruments, String updateHash, long bytes) {
            return new FetchResult(instruments, updateHash, false, instruments.size(), bytes);
        }

        /**
         * Create delta fetch result.
         */
        public static FetchResult delta(List<BrokerAdapter.Instrument> instruments, String updateHash, long bytes) {
            return new FetchResult(instruments, updateHash, true, instruments.size(), bytes);
        }
    }
}
