package in.annupaper.service;

import in.annupaper.domain.model.MtfGlobalConfig;
import in.annupaper.domain.model.MtfSymbolConfig;
import in.annupaper.application.port.output.MtfConfigRepository;
import in.annupaper.application.port.output.SignalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing MTF configuration with signal staleness handling.
 */
public final class MtfConfigService {
    private static final Logger log = LoggerFactory.getLogger(MtfConfigService.class);

    private final MtfConfigRepository mtfConfigRepo;
    private final SignalRepository signalRepo;

    public MtfConfigService(MtfConfigRepository mtfConfigRepo, SignalRepository signalRepo) {
        this.mtfConfigRepo = mtfConfigRepo;
        this.signalRepo = signalRepo;
    }

    /**
     * Get global configuration.
     */
    public Optional<MtfGlobalConfig> getGlobalConfig() {
        return mtfConfigRepo.getGlobalConfig();
    }

    /**
     * Update global configuration and mark all signals as STALE.
     */
    public void updateGlobalConfig(MtfGlobalConfig config) {
        mtfConfigRepo.updateGlobalConfig(config);

        // Mark all signals as STALE (where no trades exist)
        int markedCount = signalRepo.markSignalsAsStale();
        log.info("Marked {} signals as STALE after global config change", markedCount);
    }

    /**
     * Get symbol-specific configuration.
     */
    public Optional<MtfSymbolConfig> getSymbolConfig(String symbol, String userBrokerId) {
        return mtfConfigRepo.getSymbolConfig(symbol, userBrokerId);
    }

    /**
     * Get all symbol-specific configurations.
     */
    public List<MtfSymbolConfig> getAllSymbolConfigs() {
        return mtfConfigRepo.getAllSymbolConfigs();
    }

    /**
     * Upsert symbol-specific configuration and mark signals for that symbol as
     * STALE.
     */
    public void upsertSymbolConfig(MtfSymbolConfig config) {
        mtfConfigRepo.upsertSymbolConfig(config);

        // Mark signals for this symbol as STALE (where no trades exist)
        int markedCount = signalRepo.markSignalsAsStaleForSymbol(config.symbol());
        log.info("Marked {} signals as STALE for symbol {} after config change",
                markedCount, config.symbol());
    }

    /**
     * Delete symbol-specific configuration and mark signals for that symbol as
     * STALE.
     */
    public void deleteSymbolConfig(String symbol, String userBrokerId) {
        mtfConfigRepo.deleteSymbolConfig(symbol, userBrokerId);

        // Mark signals for this symbol as STALE (where no trades exist)
        int markedCount = signalRepo.markSignalsAsStaleForSymbol(symbol);
        log.info("Marked {} signals as STALE for symbol {} after config deletion",
                markedCount, symbol);
    }

    /**
     * Get effective configuration for a symbol (global + symbol overrides merged).
     */
    public MtfGlobalConfig getEffectiveConfig(String symbol, String userBrokerId) {
        return mtfConfigRepo.getEffectiveConfig(symbol, userBrokerId);
    }
}
