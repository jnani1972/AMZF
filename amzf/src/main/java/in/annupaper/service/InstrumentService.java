package in.annupaper.service;

import in.annupaper.domain.model.BrokerInstrument;
import in.annupaper.domain.model.InstrumentSearchResult;
import in.annupaper.infrastructure.broker.BrokerAdapterFactory;
import in.annupaper.application.port.output.InstrumentRepository;
import in.annupaper.domain.model.BrokerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Service for downloading and caching broker instrument masters.
 */
public class InstrumentService { // Removed 'final'
    private static final Logger log = LoggerFactory.getLogger(InstrumentService.class);

    private final BrokerAdapterFactory brokerAdapterFactory; // Renamed field
    private final InstrumentRepository instrumentRepo;

    public InstrumentService(BrokerAdapterFactory brokerAdapterFactory, InstrumentRepository instrumentRepo) { // Reordered
                                                                                                               // and
                                                                                                               // renamed
                                                                                                               // parameters
        this.brokerAdapterFactory = brokerAdapterFactory;
        this.instrumentRepo = instrumentRepo;
    }

    // New method added
    public void syncInstruments(String brokerCode, String userBrokerId) {
        BrokerAdapter adapter = brokerAdapterFactory.getOrCreate(userBrokerId, brokerCode);
        try {
            List<BrokerInstrument> instruments = adapter.getInstruments().get();
            instrumentRepo.saveInstruments(brokerCode, instruments);
        } catch (Exception e) {
            log.error("Failed to sync instruments", e);
        }
    }

    /**
     * Download instruments from a broker and save to database.
     * 
     * @param brokerId Broker code (B_FYERS, B_DHAN, etc.)
     * @return Number of instruments downloaded
     */
    public int downloadInstruments(String brokerId) {
        log.info("[INSTRUMENTS] Starting download for broker: {}", brokerId);
        Instant start = Instant.now();

        try {
            // Create temporary adapter for instrument download (no user context needed)
            String brokerCode = brokerId.replace("B_", ""); // Convert B_FYERS -> FYERS
            BrokerAdapter adapter = brokerAdapterFactory.create(brokerCode, "SYSTEM_INSTRUMENT_DOWNLOAD");
            if (adapter == null) {
                log.error("[INSTRUMENTS] No adapter found for broker: {}", brokerId);
                return 0;
            }

            // Download instruments
            List<BrokerInstrument> instruments = adapter.getInstruments()
                    .get(java.util.concurrent.TimeUnit.MINUTES.toMillis(2), java.util.concurrent.TimeUnit.MILLISECONDS);

            if (instruments == null || instruments.isEmpty()) {
                log.warn("[INSTRUMENTS] No instruments returned from broker: {}", brokerId);
                return 0;
            }

            // Clear old data and save new
            instrumentRepo.clearBroker(brokerId);
            instrumentRepo.saveInstruments(brokerId, instruments);

            Duration elapsed = Duration.between(start, Instant.now());
            log.info("[INSTRUMENTS] ✓ Downloaded {} instruments from {} in {}ms",
                    instruments.size(), brokerId, elapsed.toMillis());

            return instruments.size();

        } catch (Exception e) {
            Duration elapsed = Duration.between(start, Instant.now());
            log.error("[INSTRUMENTS] ✗ Failed to download from {} after {}ms: {}",
                    brokerId, elapsed.toMillis(), e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Download instruments from all configured brokers.
     */
    public void downloadAllInstruments() {
        log.info("[INSTRUMENTS] ════════════════════════════════════════════════════════");
        log.info("[INSTRUMENTS] Starting instrument download for all brokers");
        log.info("[INSTRUMENTS] ════════════════════════════════════════════════════════");

        Instant start = Instant.now();
        int totalInstruments = 0;

        // Download from Fyers (primary)
        totalInstruments += downloadInstruments("B_FYERS"); // Using String literal to avoid missing enum dependency if
                                                            // any

        Duration elapsed = Duration.between(start, Instant.now());
        log.info("[INSTRUMENTS] ════════════════════════════════════════════════════════");
        log.info("[INSTRUMENTS] ✓ COMPLETED: {} total instruments in {}ms ({} seconds)",
                totalInstruments, elapsed.toMillis(), elapsed.toSeconds());
        log.info("[INSTRUMENTS] ════════════════════════════════════════════════════════");
    }

    /**
     * Search instruments by query.
     */
    public List<InstrumentSearchResult> search(String query) {
        return instrumentRepo.search(query, 20);
    }
}
