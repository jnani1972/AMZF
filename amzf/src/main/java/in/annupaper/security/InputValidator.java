package in.annupaper.security;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Input validator for security and data integrity.
 *
 * Features:
 * - Validates trading symbols, quantities, prices
 * - Prevents injection attacks (SQL, command, script)
 * - Sanitizes user inputs
 * - Enforces business rules (min/max limits)
 * - Type-safe validation methods
 *
 * Usage:
 * <pre>
 * InputValidator validator = new InputValidator();
 *
 * // Validate symbol
 * if (!validator.isValidSymbol("NSE:SBIN-EQ")) {
 *     throw new IllegalArgumentException("Invalid symbol");
 * }
 *
 * // Validate quantity
 * validator.validateQuantity(100);  // Throws if invalid
 *
 * // Sanitize string
 * String clean = validator.sanitize(userInput);
 * </pre>
 *
 * Validation Rules:
 * - Symbols: Alphanumeric with limited special chars (:, -, _)
 * - Quantities: Positive integers, max 100,000
 * - Prices: Positive decimals, max 1,000,000
 * - Strings: Max length 1000, no dangerous patterns
 *
 * Security Features:
 * - Prevents SQL injection
 * - Blocks command injection
 * - Filters XSS attempts
 * - Validates numeric ranges
 * - Sanitizes file paths
 */
public class InputValidator {

    // Regex patterns
    private static final Pattern SYMBOL_PATTERN = Pattern.compile("^[A-Z0-9_:-]+$");
    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private static final Pattern SAFE_STRING_PATTERN = Pattern.compile("^[a-zA-Z0-9\\s.,!?@#$%&*()_+=-]+$");

    // Dangerous patterns (injection attempts)
    private static final Pattern SQL_INJECTION_PATTERN =
        Pattern.compile(".*(--|;|\\bOR\\b|\\bAND\\b|\\bUNION\\b|\\bSELECT\\b|\\bDROP\\b|\\bINSERT\\b|\\bUPDATE\\b|\\bDELETE\\b).*",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern COMMAND_INJECTION_PATTERN =
        Pattern.compile(".*(\\||&|;|`|\\$\\(|\\$\\{|<|>|\\\\|\\n|\\r).*");

    private static final Pattern XSS_PATTERN =
        Pattern.compile(".*(<script|javascript:|onerror=|onload=|<iframe|<object|<embed).*",
            Pattern.CASE_INSENSITIVE);

    // Business limits
    private static final int MAX_QUANTITY = 100000;
    private static final int MAX_STRING_LENGTH = 1000;
    private static final BigDecimal MAX_PRICE = new BigDecimal("1000000");

    /**
     * Validate trading symbol.
     *
     * Valid formats:
     * - NSE:SBIN-EQ
     * - SBIN
     * - NSE_SBIN
     *
     * @param symbol Trading symbol
     * @return true if valid
     */
    public boolean isValidSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return false;
        }

        if (symbol.length() > 50) {
            return false;
        }

        return SYMBOL_PATTERN.matcher(symbol).matches();
    }

    /**
     * Validate quantity.
     *
     * Rules:
     * - Must be positive
     * - Must be <= MAX_QUANTITY (100,000)
     *
     * @param quantity Order quantity
     * @throws IllegalArgumentException if invalid
     */
    public void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive: " + quantity);
        }

        if (quantity > MAX_QUANTITY) {
            throw new IllegalArgumentException("Quantity exceeds maximum (" + MAX_QUANTITY + "): " + quantity);
        }
    }

    /**
     * Validate price.
     *
     * Rules:
     * - Must be non-negative
     * - Must be <= MAX_PRICE (1,000,000)
     * - Scale <= 2 decimal places
     *
     * @param price Order price
     * @throws IllegalArgumentException if invalid
     */
    public void validatePrice(BigDecimal price) {
        if (price == null) {
            throw new IllegalArgumentException("Price cannot be null");
        }

        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Price must be non-negative: " + price);
        }

        if (price.compareTo(MAX_PRICE) > 0) {
            throw new IllegalArgumentException("Price exceeds maximum (" + MAX_PRICE + "): " + price);
        }

        if (price.scale() > 2) {
            throw new IllegalArgumentException("Price scale must be <= 2 decimal places: " + price);
        }
    }

    /**
     * Validate alphanumeric string (user IDs, tags, etc.).
     *
     * Allowed characters: a-z, A-Z, 0-9, _, -
     *
     * @param input Input string
     * @return true if valid
     */
    public boolean isAlphanumeric(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }

        if (input.length() > MAX_STRING_LENGTH) {
            return false;
        }

        return ALPHANUMERIC_PATTERN.matcher(input).matches();
    }

    /**
     * Check if string contains SQL injection patterns.
     *
     * @param input Input string
     * @return true if SQL injection detected
     */
    public boolean containsSqlInjection(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }

        return SQL_INJECTION_PATTERN.matcher(input).matches();
    }

    /**
     * Check if string contains command injection patterns.
     *
     * @param input Input string
     * @return true if command injection detected
     */
    public boolean containsCommandInjection(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }

        return COMMAND_INJECTION_PATTERN.matcher(input).matches();
    }

    /**
     * Check if string contains XSS patterns.
     *
     * @param input Input string
     * @return true if XSS detected
     */
    public boolean containsXss(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }

        return XSS_PATTERN.matcher(input).matches();
    }

    /**
     * Sanitize string by removing dangerous characters.
     *
     * - Trims whitespace
     * - Removes control characters
     * - Limits length to MAX_STRING_LENGTH
     *
     * @param input Input string
     * @return Sanitized string
     */
    public String sanitize(String input) {
        if (input == null) {
            return null;
        }

        // Trim whitespace
        String result = input.trim();

        // Remove control characters (except newline and tab)
        result = result.replaceAll("[\\p{Cntrl}&&[^\n\t]]", "");

        // Limit length
        if (result.length() > MAX_STRING_LENGTH) {
            result = result.substring(0, MAX_STRING_LENGTH);
        }

        return result;
    }

    /**
     * Validate and sanitize string.
     * Throws exception if dangerous patterns detected.
     *
     * @param input Input string
     * @param fieldName Field name for error messages
     * @return Sanitized string
     * @throws SecurityException if dangerous patterns detected
     */
    public String validateAndSanitize(String input, String fieldName) {
        if (input == null || input.isBlank()) {
            return input;
        }

        if (input.length() > MAX_STRING_LENGTH) {
            throw new SecurityException(fieldName + " exceeds maximum length (" + MAX_STRING_LENGTH + ")");
        }

        if (containsSqlInjection(input)) {
            throw new SecurityException(fieldName + " contains SQL injection pattern");
        }

        if (containsCommandInjection(input)) {
            throw new SecurityException(fieldName + " contains command injection pattern");
        }

        if (containsXss(input)) {
            throw new SecurityException(fieldName + " contains XSS pattern");
        }

        return sanitize(input);
    }

    /**
     * Validate broker code.
     *
     * Valid values: UPSTOX, ZERODHA, FYERS, DHAN
     *
     * @param brokerCode Broker code
     * @throws IllegalArgumentException if invalid
     */
    public void validateBrokerCode(String brokerCode) {
        if (brokerCode == null || brokerCode.isBlank()) {
            throw new IllegalArgumentException("Broker code cannot be null or empty");
        }

        if (!brokerCode.matches("^(UPSTOX|ZERODHA|FYERS|DHAN)$")) {
            throw new IllegalArgumentException("Invalid broker code: " + brokerCode);
        }
    }

    /**
     * Validate exchange code.
     *
     * Valid values: NSE, BSE, NFO, BFO, MCX, etc.
     *
     * @param exchange Exchange code
     * @throws IllegalArgumentException if invalid
     */
    public void validateExchange(String exchange) {
        if (exchange == null || exchange.isBlank()) {
            throw new IllegalArgumentException("Exchange cannot be null or empty");
        }

        if (!exchange.matches("^(NSE|BSE|NFO|BFO|MCX|CDS)$")) {
            throw new IllegalArgumentException("Invalid exchange: " + exchange);
        }
    }

    /**
     * Sanitize file path to prevent directory traversal.
     *
     * - Removes ../ sequences
     * - Removes absolute paths
     * - Limits to alphanumeric and basic separators
     *
     * @param path File path
     * @return Sanitized path
     * @throws SecurityException if path is dangerous
     */
    public String sanitizeFilePath(String path) {
        if (path == null || path.isBlank()) {
            return path;
        }

        // Check for directory traversal
        if (path.contains("..") || path.contains("./")) {
            throw new SecurityException("Path contains directory traversal: " + path);
        }

        // Check for absolute paths
        if (path.startsWith("/") || path.matches("^[A-Za-z]:.*")) {
            throw new SecurityException("Absolute paths not allowed: " + path);
        }

        // Remove dangerous characters
        String sanitized = path.replaceAll("[^a-zA-Z0-9._/-]", "");

        return sanitized;
    }

    /**
     * Validate email format (basic check).
     *
     * @param email Email address
     * @return true if valid format
     */
    public boolean isValidEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }

        // Basic email pattern
        Pattern emailPattern = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
        return emailPattern.matcher(email).matches();
    }

    /**
     * Validate numeric range.
     *
     * @param value Value to check
     * @param min Minimum (inclusive)
     * @param max Maximum (inclusive)
     * @param fieldName Field name for error messages
     * @throws IllegalArgumentException if out of range
     */
    public void validateRange(int value, int min, int max, String fieldName) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                fieldName + " must be between " + min + " and " + max + ": " + value);
        }
    }
}
