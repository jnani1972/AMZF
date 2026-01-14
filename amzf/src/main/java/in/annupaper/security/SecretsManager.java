package in.annupaper.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Secure secrets manager for handling sensitive credentials.
 *
 * Features:
 * - Never logs secrets or sensitive data
 * - Loads secrets from secure file outside codebase
 * - Supports environment variable overrides
 * - Validates secret presence before use
 * - Provides masked display for debugging
 *
 * Usage:
 * <pre>
 * SecretsManager secrets = new SecretsManager();
 * secrets.loadFromFile("/secure/secrets.properties");
 *
 * String apiKey = secrets.getRequired("upstox.api_key");
 * String optional = secrets.getOptional("feature.flag", "default");
 * </pre>
 *
 * Security Best Practices:
 * 1. Store secrets.properties outside project directory
 * 2. Use environment variables for secrets in production
 * 3. Never commit secrets.properties to git
 * 4. Rotate secrets regularly
 * 5. Use different secrets per environment
 */
public class SecretsManager {
    private static final Logger log = LoggerFactory.getLogger(SecretsManager.class);

    private final Map<String, String> secrets = new HashMap<>();
    private boolean loaded = false;

    /**
     * Load secrets from a file.
     * Environment variables override file values.
     *
     * @param filePath Path to secrets file (e.g., "/secure/secrets.properties")
     * @throws IOException if file cannot be read
     */
    public void loadFromFile(String filePath) throws IOException {
        Path path = Paths.get(filePath);

        if (!Files.exists(path)) {
            log.warn("[SecretsManager] Secrets file not found: {}", filePath);
            log.warn("[SecretsManager] Will use environment variables only");
            loaded = true;
            return;
        }

        Properties props = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            props.load(input);
        }

        // Load secrets from file
        for (String key : props.stringPropertyNames()) {
            String value = props.getProperty(key);
            if (value != null && !value.isBlank()) {
                secrets.put(key, value);
            }
        }

        // Override with environment variables (for production)
        for (String key : secrets.keySet()) {
            String envKey = key.toUpperCase().replace(".", "_");
            String envValue = System.getenv(envKey);
            if (envValue != null && !envValue.isBlank()) {
                secrets.put(key, envValue);
                log.info("[SecretsManager] Overridden '{}' from environment variable '{}'",
                    key, envKey);
            }
        }

        loaded = true;
        log.info("[SecretsManager] Loaded {} secrets from {}", secrets.size(), filePath);
    }

    /**
     * Load secrets from environment variables only.
     * Useful for containerized deployments.
     *
     * @param keys List of expected secret keys
     */
    public void loadFromEnvironment(String... keys) {
        for (String key : keys) {
            String envKey = key.toUpperCase().replace(".", "_");
            String value = System.getenv(envKey);
            if (value != null && !value.isBlank()) {
                secrets.put(key, value);
                log.info("[SecretsManager] Loaded '{}' from environment", key);
            }
        }
        loaded = true;
        log.info("[SecretsManager] Loaded {} secrets from environment", secrets.size());
    }

    /**
     * Get a required secret. Throws exception if not found.
     *
     * @param key Secret key
     * @return Secret value
     * @throws IllegalStateException if secret not found or manager not loaded
     */
    public String getRequired(String key) {
        if (!loaded) {
            throw new IllegalStateException("SecretsManager not loaded. Call loadFromFile() or loadFromEnvironment() first.");
        }

        String value = secrets.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required secret not found: " + key);
        }

        return value;
    }

    /**
     * Get an optional secret with default value.
     *
     * @param key Secret key
     * @param defaultValue Default value if secret not found
     * @return Secret value or default
     */
    public String getOptional(String key, String defaultValue) {
        if (!loaded) {
            log.warn("[SecretsManager] Not loaded, returning default for '{}'", key);
            return defaultValue;
        }

        String value = secrets.get(key);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }

    /**
     * Check if a secret exists.
     *
     * @param key Secret key
     * @return true if secret exists and is not blank
     */
    public boolean has(String key) {
        String value = secrets.get(key);
        return value != null && !value.isBlank();
    }

    /**
     * Get number of loaded secrets.
     *
     * @return Count of secrets
     */
    public int count() {
        return secrets.size();
    }

    /**
     * Get masked version of secret for logging.
     * Shows first 4 characters and length.
     *
     * Example: "abcd****" (8 chars)
     *
     * @param key Secret key
     * @return Masked secret or null if not found
     */
    public String getMasked(String key) {
        String value = secrets.get(key);
        if (value == null || value.isBlank()) {
            return null;
        }

        if (value.length() <= 4) {
            return "****";
        }

        String prefix = value.substring(0, 4);
        int length = value.length();
        return String.format("%s**** (%d chars)", prefix, length);
    }

    /**
     * Clear all secrets from memory.
     * Call this on shutdown for security.
     */
    public void clear() {
        secrets.clear();
        loaded = false;
        log.info("[SecretsManager] Cleared all secrets from memory");
    }

    /**
     * Validate that all required secrets are present.
     *
     * @param requiredKeys List of required secret keys
     * @throws IllegalStateException if any required secret is missing
     */
    public void validateRequired(String... requiredKeys) {
        if (!loaded) {
            throw new IllegalStateException("SecretsManager not loaded");
        }

        StringBuilder missing = new StringBuilder();
        for (String key : requiredKeys) {
            if (!has(key)) {
                if (missing.length() > 0) missing.append(", ");
                missing.append(key);
            }
        }

        if (missing.length() > 0) {
            throw new IllegalStateException("Missing required secrets: " + missing);
        }

        log.info("[SecretsManager] Validated {} required secrets", requiredKeys.length);
    }
}
