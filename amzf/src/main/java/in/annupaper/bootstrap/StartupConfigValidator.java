package in.annupaper.bootstrap;

import in.annupaper.domain.broker.BrokerEnvironment;
import in.annupaper.domain.broker.Broker;
import in.annupaper.domain.broker.UserBroker;
import in.annupaper.repository.BrokerRepository;
import in.annupaper.repository.UserBrokerRepository;
import in.annupaper.util.Env;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * Startup configuration validator.
 *
 * Validates configuration at startup before system initializes.
 * Throws IllegalStateException if configuration is invalid.
 *
 * V2 Enhancement: Hard gate enforcement (not warnings).
 * See COMPREHENSIVE_IMPLEMENTATION_PLAN.md Phase 1, P0-A.
 *
 * Corrections applied:
 * - PROD_READY is hard gate (not warning)
 * - P0 debt registry with boolean flags
 * - Broker environment enum (not substring checking)
 */
public final class StartupConfigValidator {
    private static final Logger log = LoggerFactory.getLogger(StartupConfigValidator.class);

    /**
     * Validate configuration at startup.
     *
     * This method should be called from App.main() BEFORE any system initialization.
     * If validation fails, it throws IllegalStateException and system refuses to start.
     *
     * @param brokerRepo Broker repository (for checking broker configs)
     * @param userBrokerRepo User-broker repository (for checking connections)
     * @throws IllegalStateException if configuration is invalid
     */
    public static void validate(BrokerRepository brokerRepo, UserBrokerRepository userBrokerRepo) {
        log.info("════════════════════════════════════════════════════════");
        log.info("Running startup config validation...");
        log.info("════════════════════════════════════════════════════════");

        // Check if production mode is enabled
        boolean productionMode = Env.getBool("PRODUCTION_MODE", false);
        log.info("Production mode: {}", productionMode);

        if (productionMode) {
            validateProductionMode(brokerRepo, userBrokerRepo);
        } else {
            warnNonProductionMode(brokerRepo, userBrokerRepo);
        }

        log.info("✅ Startup config validation passed");
        log.info("════════════════════════════════════════════════════════");
    }

    /**
     * Validate production mode configuration (strict enforcement).
     */
    private static void validateProductionMode(BrokerRepository brokerRepo, UserBrokerRepository userBrokerRepo) {
        log.info("PRODUCTION MODE detected - enforcing strict validation");

        // ✅ CORRECTED: Check 1 - Order execution must be enabled
        boolean orderExecutionEnabled = Env.getBool("ORDER_EXECUTION_ENABLED", false);
        if (!orderExecutionEnabled) {
            throw new IllegalStateException(
                "❌ INVALID CONFIG: PRODUCTION MODE requires ORDER_EXECUTION_ENABLED=true\n" +
                "System refuses to start.\n" +
                "Either:\n" +
                "  1. Enable order execution: set ORDER_EXECUTION_ENABLED=true\n" +
                "  2. Set PRODUCTION_MODE=false for testing/paper trading"
            );
        }
        log.info("✓ Order execution enabled");

        // ✅ CORRECTED: Check 2 - No test broker APIs (use environment enum)
        List<UserBroker> allUserBrokers = userBrokerRepo.findAll();
        for (UserBroker userBroker : allUserBrokers) {
            // Get broker definition
            Broker broker = brokerRepo.findById(userBroker.brokerId()).orElse(null);
            if (broker == null) {
                log.warn("Broker not found: {}", userBroker.brokerId());
                continue;
            }

            // Extract API URL from broker config
            String apiUrl = extractApiUrl(broker);
            if (apiUrl == null || apiUrl.isEmpty()) {
                log.warn("No API URL found for broker: {}", broker.brokerCode());
                continue;
            }

            // Check environment
            BrokerEnvironment env = BrokerEnvironment.fromUrl(apiUrl);
            if (env.isNonProduction()) {
                throw new IllegalStateException(
                    "❌ INVALID CONFIG: PRODUCTION MODE forbids non-production broker APIs\n" +
                    "Broker: " + broker.brokerName() + " (" + broker.brokerCode() + ")\n" +
                    "URL: " + apiUrl + "\n" +
                    "Environment: " + env + "\n" +
                    "Either:\n" +
                    "  1. Use production API URL in broker config\n" +
                    "  2. Set PRODUCTION_MODE=false"
                );
            }
            log.info("✓ Broker {} using production API: {}", broker.brokerCode(), apiUrl);
        }

        // Check 3: Async event writer if tick persistence enabled
        boolean persistTickEvents = Env.getBool("PERSIST_TICK_EVENTS", false);
        boolean asyncEventWriterEnabled = Env.getBool("ASYNC_EVENT_WRITER_ENABLED", false);

        if (persistTickEvents && !asyncEventWriterEnabled) {
            throw new IllegalStateException(
                "❌ INVALID CONFIG: Tick event persistence requires ASYNC_EVENT_WRITER_ENABLED=true\n" +
                "Direct DB writes on broker thread are FORBIDDEN (P0 invariant).\n" +
                "Either:\n" +
                "  1. Enable async writer: set ASYNC_EVENT_WRITER_ENABLED=true\n" +
                "  2. Disable tick persistence: set PERSIST_TICK_EVENTS=false"
            );
        }
        if (persistTickEvents) {
            log.info("✓ Tick persistence enabled with async writer");
        }

        // ✅ CORRECTED: Check 4 - PROD_READY is now a HARD GATE (not warning)
        String releaseReadiness = Env.get("RELEASE_READINESS", "BETA");
        log.info("Release readiness: {}", releaseReadiness);

        if ("PROD_READY".equals(releaseReadiness)) {
            if (!P0DebtRegistry.allGatesResolved()) {
                String unresolved = P0DebtRegistry.getUnresolvedGates();
                throw new IllegalStateException(
                    "❌ PROD_READY GATE FAILED: Unresolved P0 blockers\n" +
                    "The following P0 items are not complete:\n  - " + unresolved + "\n\n" +
                    "Either:\n" +
                    "  1. Resolve all P0 blockers (update P0DebtRegistry flags in code)\n" +
                    "  2. Set RELEASE_READINESS=BETA to bypass gate"
                );
            }
            log.info("✅ PROD_READY gate passed - all P0 blockers resolved");
        } else {
            log.warn("⚠️  Release readiness is {}, not PROD_READY", releaseReadiness);
            log.warn("⚠️  P0 blockers may still exist:");
            String unresolved = P0DebtRegistry.getUnresolvedGates();
            if (!unresolved.isEmpty()) {
                log.warn("  - {}", unresolved);
            }
        }

        log.info("✅ PRODUCTION MODE validation passed");
    }

    /**
     * Warn about non-production mode (informational only).
     */
    private static void warnNonProductionMode(BrokerRepository brokerRepo, UserBrokerRepository userBrokerRepo) {
        log.warn("⚠️  ════════════════════════════════════════════════════════");
        log.warn("⚠️  NON-PRODUCTION MODE detected");
        log.warn("⚠️  ════════════════════════════════════════════════════════");

        boolean orderExecutionEnabled = Env.getBool("ORDER_EXECUTION_ENABLED", false);
        if (!orderExecutionEnabled) {
            log.warn("⚠️  Order execution DISABLED - Paper trading mode active");
        }

        // Check for test APIs
        List<UserBroker> allUserBrokers = userBrokerRepo.findAll();
        for (UserBroker userBroker : allUserBrokers) {
            Broker broker = brokerRepo.findById(userBroker.brokerId()).orElse(null);
            if (broker == null) continue;

            String apiUrl = extractApiUrl(broker);
            if (apiUrl == null || apiUrl.isEmpty()) continue;

            BrokerEnvironment env = BrokerEnvironment.fromUrl(apiUrl);
            if (env.isNonProduction()) {
                log.warn("⚠️  Non-production API: {} - {} ({})",
                    broker.brokerName(), apiUrl, env);
            }
        }

        // Show P0 debt status
        String unresolved = P0DebtRegistry.getUnresolvedGates();
        if (!unresolved.isEmpty()) {
            log.warn("⚠️  Unresolved P0 blockers:");
            log.warn("  - {}", unresolved);
        } else {
            log.info("✓ All P0 blockers resolved (ready for PROD_READY)");
        }

        log.warn("⚠️  System running in non-production mode - features may be limited");
        log.warn("⚠️  ════════════════════════════════════════════════════════");
    }

    /**
     * Extract API URL from broker config JSON.
     *
     * Different brokers store their API URL in different config keys.
     * This method tries common patterns.
     *
     * @param broker Broker definition
     * @return API URL, or null if not found
     */
    private static String extractApiUrl(Broker broker) {
        JsonNode config = broker.config();
        if (config == null) {
            return null;
        }

        // Try common config keys
        String[] keys = {"apiUrl", "api_url", "baseUrl", "base_url", "url"};
        for (String key : keys) {
            if (config.has(key)) {
                JsonNode node = config.get(key);
                if (node != null && node.isTextual()) {
                    return node.asText();
                }
            }
        }

        return null;
    }

    private StartupConfigValidator() {
        // Utility class - no instantiation
    }
}
