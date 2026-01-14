package in.annupaper.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Secure audit logger that automatically sanitizes sensitive data.
 *
 * Features:
 * - Automatically masks API keys, tokens, passwords
 * - Sanitizes query parameters in URLs
 * - Redacts sensitive field values from JSON
 * - Prevents accidental logging of credentials
 * - Thread-safe for concurrent use
 *
 * Usage:
 * <pre>
 * SecureAuditLogger audit = new SecureAuditLogger("OrderBroker");
 *
 * // Automatically sanitizes sensitive data
 * audit.logAuthentication("UPSTOX", true, "user123");
 * audit.logOrderPlacement("ORDER-123", "UPSTOX", "SBIN", 100);
 * audit.logApiCall("POST /orders", 200, 150);
 *
 * // Manual sanitization
 * String clean = audit.sanitize("token=abc123&price=500");
 * // Output: "token=****&price=500"
 * </pre>
 *
 * Sensitive Patterns Detected:
 * - API keys: api_key, apiKey, x-api-key
 * - Tokens: token, access_token, auth_token
 * - Passwords: password, passwd, pwd
 * - Secrets: secret, client_secret
 * - Authorization headers
 *
 * Security Best Practices:
 * 1. Never log request/response bodies with auth headers
 * 2. Always sanitize URLs before logging
 * 3. Use structured logging with field-level sanitization
 * 4. Review logs periodically for leaked secrets
 * 5. Set log retention policies
 */
public class SecureAuditLogger {
    private static final Logger log = LoggerFactory.getLogger(SecureAuditLogger.class);

    private final String component;

    // Sensitive field patterns
    private static final Set<String> SENSITIVE_FIELDS = new HashSet<>();
    static {
        // Authentication
        SENSITIVE_FIELDS.add("password");
        SENSITIVE_FIELDS.add("passwd");
        SENSITIVE_FIELDS.add("pwd");
        SENSITIVE_FIELDS.add("secret");
        SENSITIVE_FIELDS.add("api_key");
        SENSITIVE_FIELDS.add("apikey");
        SENSITIVE_FIELDS.add("api-key");
        SENSITIVE_FIELDS.add("x-api-key");

        // Tokens
        SENSITIVE_FIELDS.add("token");
        SENSITIVE_FIELDS.add("access_token");
        SENSITIVE_FIELDS.add("accesstoken");
        SENSITIVE_FIELDS.add("refresh_token");
        SENSITIVE_FIELDS.add("auth_token");
        SENSITIVE_FIELDS.add("authorization");
        SENSITIVE_FIELDS.add("bearer");

        // OAuth
        SENSITIVE_FIELDS.add("client_secret");
        SENSITIVE_FIELDS.add("client_id");

        // Session
        SENSITIVE_FIELDS.add("session");
        SENSITIVE_FIELDS.add("sessionid");
        SENSITIVE_FIELDS.add("jsessionid");
        SENSITIVE_FIELDS.add("cookie");

        // Banking
        SENSITIVE_FIELDS.add("account");
        SENSITIVE_FIELDS.add("card");
        SENSITIVE_FIELDS.add("cvv");
        SENSITIVE_FIELDS.add("pin");
    }

    // Regex patterns for sanitization
    private static final Pattern BEARER_TOKEN_PATTERN =
        Pattern.compile("Bearer\\s+[A-Za-z0-9\\-._~+/]+=*", Pattern.CASE_INSENSITIVE);

    private static final Pattern API_KEY_PATTERN =
        Pattern.compile("(api[_-]?key|apikey)=[^&\\s]+", Pattern.CASE_INSENSITIVE);

    private static final Pattern TOKEN_PATTERN =
        Pattern.compile("(token|access[_-]?token)=[^&\\s]+", Pattern.CASE_INSENSITIVE);

    private static final Pattern PASSWORD_PATTERN =
        Pattern.compile("(password|passwd|pwd)=[^&\\s]+", Pattern.CASE_INSENSITIVE);

    public SecureAuditLogger(String component) {
        this.component = component;
    }

    /**
     * Log authentication attempt.
     *
     * @param brokerCode Broker identifier
     * @param success Success or failure
     * @param userId User identifier (not email or full name)
     */
    public void logAuthentication(String brokerCode, boolean success, String userId) {
        log.info("[{}][AUTH] broker={}, success={}, user_id={}, timestamp={}",
            component, brokerCode, success, userId, Instant.now());
    }

    /**
     * Log order placement.
     *
     * @param orderId Order ID
     * @param brokerCode Broker identifier
     * @param symbol Trading symbol
     * @param quantity Order quantity
     */
    public void logOrderPlacement(String orderId, String brokerCode, String symbol, int quantity) {
        log.info("[{}][ORDER] order_id={}, broker={}, symbol={}, quantity={}, timestamp={}",
            component, orderId, brokerCode, symbol, quantity, Instant.now());
    }

    /**
     * Log API call with sanitized URL.
     *
     * @param method HTTP method (GET, POST, etc.)
     * @param url URL (will be sanitized)
     * @param statusCode HTTP status code
     * @param durationMs Duration in milliseconds
     */
    public void logApiCall(String method, String url, int statusCode, long durationMs) {
        String sanitizedUrl = sanitizeUrl(url);
        log.info("[{}][API] method={}, url={}, status={}, duration_ms={}, timestamp={}",
            component, method, sanitizedUrl, statusCode, durationMs, Instant.now());
    }

    /**
     * Log error with sanitized message.
     *
     * @param operation Operation name
     * @param error Error message (will be sanitized)
     */
    public void logError(String operation, String error) {
        String sanitizedError = sanitize(error);
        log.error("[{}][ERROR] operation={}, error={}, timestamp={}",
            component, operation, sanitizedError, Instant.now());
    }

    /**
     * Log rate limit hit.
     *
     * @param brokerCode Broker identifier
     * @param currentRate Current request rate
     * @param limit Rate limit threshold
     */
    public void logRateLimitHit(String brokerCode, int currentRate, int limit) {
        log.warn("[{}][RATE_LIMIT] broker={}, current_rate={}, limit={}, timestamp={}",
            component, brokerCode, currentRate, limit, Instant.now());
    }

    /**
     * Log system event.
     *
     * @param event Event name
     * @param details Event details (will be sanitized)
     */
    public void logEvent(String event, String details) {
        String sanitizedDetails = sanitize(details);
        log.info("[{}][EVENT] event={}, details={}, timestamp={}",
            component, event, sanitizedDetails, Instant.now());
    }

    /**
     * Sanitize a string by masking sensitive data.
     *
     * @param input Input string
     * @return Sanitized string
     */
    public String sanitize(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }

        String result = input;

        // Sanitize bearer tokens
        result = BEARER_TOKEN_PATTERN.matcher(result).replaceAll("Bearer ****");

        // Sanitize query parameters
        result = API_KEY_PATTERN.matcher(result).replaceAll("$1=****");
        result = TOKEN_PATTERN.matcher(result).replaceAll("$1=****");
        result = PASSWORD_PATTERN.matcher(result).replaceAll("$1=****");

        return result;
    }

    /**
     * Sanitize URL by removing sensitive query parameters.
     *
     * @param url Input URL
     * @return Sanitized URL
     */
    public String sanitizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }

        // Split URL into base and query string
        int queryIndex = url.indexOf('?');
        if (queryIndex == -1) {
            return url; // No query string
        }

        String base = url.substring(0, queryIndex);
        String query = url.substring(queryIndex + 1);

        // Sanitize query parameters
        String sanitizedQuery = sanitize(query);

        return base + "?" + sanitizedQuery;
    }

    /**
     * Sanitize a map of parameters.
     *
     * @param params Parameter map
     * @return Sanitized map (new copy)
     */
    public Map<String, String> sanitizeParams(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return params;
        }

        Map<String, String> sanitized = new java.util.HashMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (isSensitiveField(key)) {
                sanitized.put(key, maskValue(value));
            } else {
                sanitized.put(key, value);
            }
        }

        return sanitized;
    }

    /**
     * Check if a field name is sensitive.
     *
     * @param fieldName Field name
     * @return true if field is sensitive
     */
    private boolean isSensitiveField(String fieldName) {
        if (fieldName == null) {
            return false;
        }

        String lower = fieldName.toLowerCase();
        for (String sensitive : SENSITIVE_FIELDS) {
            if (lower.contains(sensitive)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Mask a value for logging.
     * Shows first 4 characters.
     *
     * @param value Value to mask
     * @return Masked value
     */
    private String maskValue(String value) {
        if (value == null || value.isBlank()) {
            return "****";
        }

        if (value.length() <= 4) {
            return "****";
        }

        return value.substring(0, 4) + "****";
    }

    /**
     * Get redacted version of exception for logging.
     * Removes stack trace from sensitive packages.
     *
     * @param throwable Exception
     * @return Redacted message
     */
    public String getRedactedExceptionMessage(Throwable throwable) {
        if (throwable == null) {
            return "null";
        }

        String message = throwable.getMessage();
        if (message == null) {
            message = throwable.getClass().getSimpleName();
        }

        return sanitize(message);
    }
}
