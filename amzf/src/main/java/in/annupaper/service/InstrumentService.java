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

    private final BrokerAdapterFactory brokerAdapterFactory;
    private final InstrumentRepository instrumentRepo;
    private final in.annupaper.application.port.output.UserBrokerRepository userBrokerRepo;

    public InstrumentService(BrokerAdapterFactory brokerAdapterFactory,
            InstrumentRepository instrumentRepo,
            in.annupaper.application.port.output.UserBrokerRepository userBrokerRepo) {
        this.brokerAdapterFactory = brokerAdapterFactory;
        this.instrumentRepo = instrumentRepo;
        this.userBrokerRepo = userBrokerRepo;
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
     * Download instruments from the configured DATA broker.
     */
    public void downloadAllInstruments() {
        log.info("[INSTRUMENTS] ════════════════════════════════════════════════════════");
        log.info("[INSTRUMENTS] Starting instrument download from configured DATA broker");
        log.info("[INSTRUMENTS] ════════════════════════════════════════════════════════");

        try {
            var dataBrokerOpt = userBrokerRepo.findDataBroker();

            if (dataBrokerOpt.isPresent()) {
                var dataBroker = dataBrokerOpt.get();
                log.info("[INSTRUMENTS] Found configured DATA broker: {} (UserBrokerId: {})",
                        dataBroker.brokerId(), dataBroker.userBrokerId());

                // Use the brokerId from the configured data broker (e.g., "B_ZERODHA")
                int count = downloadInstruments(dataBroker.brokerId());

                if (count > 0) {
                    log.info("[INSTRUMENTS] Successfully initialized {} instruments from {}", count,
                            dataBroker.brokerId());
                } else {
                    log.warn("[INSTRUMENTS] Download finished but 0 instruments were saved.");
                }
            } else {
                log.warn("[INSTRUMENTS] No DATA broker configured! Skipping instrument download.");
            }
        } catch (Exception e) {
            log.error("[INSTRUMENTS] Failed to execute downloadAllInstruments: {}", e.getMessage(), e);
        }
    }

    /**
     * Search instruments by query.
     */
    public List<InstrumentSearchResult> search(String query) {
        return instrumentRepo.search(query, 20);
    }
}
