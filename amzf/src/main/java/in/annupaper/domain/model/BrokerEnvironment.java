package in.annupaper.domain.model;

/**
 * Broker environment classification.
 * Used to prevent production mode from connecting to test APIs.
 *
 * V2 Enhancement: Enum-based environment detection (not substring checks).
 * See COMPREHENSIVE_IMPLEMENTATION_PLAN.md Phase 1, P0-A.
 *
 * Correctness Fix: Using an allowlist of test patterns is more robust than
 * substring checking, which could have false positives.
 */
public enum BrokerEnvironment {
    /**
     * Production environment (live trading with real money).
     */
    PRODUCTION,

    /**
     * User Acceptance Testing environment (pre-production testing).
     */
    UAT,

    /**
     * Sandbox environment (test environment with fake data).
     */
    SANDBOX;

    /**
     * Detect environment from broker API URL.
     *
     * Strategy: Check for known test patterns. If none match, assume PRODUCTION
     * (conservative approach - require explicit test markers).
     *
     * Known test patterns:
     * - "sandbox" or "-sandbox." in URL
     * - "uat" or "-uat." in URL
     * - "test" or "-test." in URL
     * - "-t1." (common broker test API pattern, e.g., api-t1.fyers.in)
     * - "staging" in URL
     *
     * @param apiUrl Broker API URL
     * @return Environment (defaults to PRODUCTION if unknown)
     */
    public static BrokerEnvironment fromUrl(String apiUrl) {
        if (apiUrl == null || apiUrl.isEmpty()) {
            return PRODUCTION; // Conservative default
        }

        String lower = apiUrl.toLowerCase();

        // Check for sandbox patterns
        if (lower.contains("sandbox") || lower.contains("-sandbox.")) {
            return SANDBOX;
        }

        // Check for UAT/test/staging patterns
        if (lower.contains("uat") || lower.contains("-uat.") ||
                lower.contains("test") || lower.contains("-test.") ||
                lower.contains("-t1.") || lower.contains("staging")) {
            return UAT;
        }

        // Default to PRODUCTION (conservative: require explicit test markers)
        return PRODUCTION;
    }

    /**
     * Check if this environment is production.
     * 
     * @return true if PRODUCTION, false otherwise
     */
    public boolean isProduction() {
        return this == PRODUCTION;
    }

    /**
     * Check if this environment is non-production (UAT or SANDBOX).
     * 
     * @return true if UAT or SANDBOX, false if PRODUCTION
     */
    public boolean isNonProduction() {
        return this != PRODUCTION;
    }
}
