package in.annupaper.service.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.annupaper.config.TrailingStopsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Service for managing trailing stops configuration.
 *
 * Stores configuration in a JSON file for persistence.
 * Thread-safe for concurrent access.
 */
public final class TrailingStopsConfigService {
    private static final Logger log = LoggerFactory.getLogger(TrailingStopsConfigService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path configFilePath;
    private volatile TrailingStopsConfig currentConfig;

    public TrailingStopsConfigService(String configDir) {
        this.configFilePath = Paths.get(configDir, "trailing-stops-config.json");
        this.currentConfig = loadConfig();
    }

    /**
     * Get current configuration.
     * Never returns null - returns defaults if no config file exists.
     */
    public TrailingStopsConfig getConfig() {
        return currentConfig;
    }

    /**
     * Update configuration and persist to disk.
     *
     * @param newConfig New configuration to save
     * @throws IllegalArgumentException if config is invalid
     * @throws IOException if save fails
     */
    public void updateConfig(TrailingStopsConfig newConfig) throws IOException {
        if (newConfig == null) {
            throw new IllegalArgumentException("Configuration cannot be null");
        }

        if (!newConfig.isValid()) {
            throw new IllegalArgumentException("Invalid configuration values");
        }

        // Save to file
        saveConfig(newConfig);

        // Update in-memory config (volatile ensures visibility)
        this.currentConfig = newConfig;

        log.info("✅ Trailing stops configuration updated: activation={}%, trailing={}%, frequency={}",
            newConfig.activationPercent(), newConfig.trailingPercent(), newConfig.updateFrequency());
    }

    /**
     * Load configuration from file, or return defaults if file doesn't exist.
     */
    private TrailingStopsConfig loadConfig() {
        try {
            if (Files.exists(configFilePath)) {
                String json = Files.readString(configFilePath);
                TrailingStopsConfig config = MAPPER.readValue(json, TrailingStopsConfig.class);
                log.info("✅ Loaded trailing stops config from: {}", configFilePath);
                return config;
            } else {
                log.info("No config file found, using defaults: {}", configFilePath);
                return TrailingStopsConfig.defaults();
            }
        } catch (IOException e) {
            log.error("Failed to load config file, using defaults: {}", e.getMessage());
            return TrailingStopsConfig.defaults();
        }
    }

    /**
     * Save configuration to file.
     */
    private void saveConfig(TrailingStopsConfig config) throws IOException {
        // Ensure config directory exists
        File configDir = configFilePath.getParent().toFile();
        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        // Write to file (pretty-printed)
        String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(config);
        Files.writeString(configFilePath, json);

        log.info("✅ Configuration saved to: {}", configFilePath);
    }
}
