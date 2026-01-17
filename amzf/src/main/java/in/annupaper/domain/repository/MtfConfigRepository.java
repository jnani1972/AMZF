package in.annupaper.domain.repository;

import in.annupaper.domain.signal.MtfGlobalConfig;
import in.annupaper.domain.signal.MtfSymbolConfig;

import java.util.List;
import java.util.Optional;

/**
 * Repository for MTF configuration with global defaults and symbol-specific overrides.
 */
public interface MtfConfigRepository {
    /**
     * Get global configuration (single row).
     */
    Optional<MtfGlobalConfig> getGlobalConfig();

    /**
     * Update global configuration.
     */
    void updateGlobalConfig(MtfGlobalConfig config);

    /**
     * Get symbol-specific configuration for a symbol + user_broker.
     */
    Optional<MtfSymbolConfig> getSymbolConfig(String symbol, String userBrokerId);

    /**
     * Get all symbol-specific configurations.
     */
    List<MtfSymbolConfig> getAllSymbolConfigs();

    /**
     * Upsert symbol-specific configuration.
     */
    void upsertSymbolConfig(MtfSymbolConfig config);

    /**
     * Delete symbol-specific configuration.
     */
    void deleteSymbolConfig(String symbol, String userBrokerId);

    /**
     * Get effective configuration for a symbol (global + symbol override merged).
     */
    MtfGlobalConfig getEffectiveConfig(String symbol, String userBrokerId);
}
