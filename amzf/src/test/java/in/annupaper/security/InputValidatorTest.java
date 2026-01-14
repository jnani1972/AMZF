package in.annupaper.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for InputValidator security validations.
 */
@DisplayName("Input Validator Security Tests")
public class InputValidatorTest {

    private InputValidator validator;

    @BeforeEach
    public void setUp() {
        validator = new InputValidator();
    }

    @Test
    @DisplayName("Valid symbols pass validation")
    public void testValidSymbols() {
        assertTrue(validator.isValidSymbol("NSE:SBIN-EQ"));
        assertTrue(validator.isValidSymbol("SBIN"));
        assertTrue(validator.isValidSymbol("NSE_SBIN"));
        assertTrue(validator.isValidSymbol("BANKNIFTY23JAN24C45000"));
    }

    @Test
    @DisplayName("Invalid symbols fail validation")
    public void testInvalidSymbols() {
        assertFalse(validator.isValidSymbol(null));
        assertFalse(validator.isValidSymbol(""));
        assertFalse(validator.isValidSymbol("   "));
        assertFalse(validator.isValidSymbol("SBIN<script>"));
        assertFalse(validator.isValidSymbol("SBIN'; DROP TABLE--"));
    }

    @Test
    @DisplayName("Valid quantities pass")
    public void testValidQuantities() {
        assertDoesNotThrow(() -> validator.validateQuantity(1));
        assertDoesNotThrow(() -> validator.validateQuantity(100));
        assertDoesNotThrow(() -> validator.validateQuantity(10000));
        assertDoesNotThrow(() -> validator.validateQuantity(100000));
    }

    @Test
    @DisplayName("Invalid quantities throw exception")
    public void testInvalidQuantities() {
        assertThrows(IllegalArgumentException.class, () -> validator.validateQuantity(0));
        assertThrows(IllegalArgumentException.class, () -> validator.validateQuantity(-1));
        assertThrows(IllegalArgumentException.class, () -> validator.validateQuantity(100001));
    }

    @Test
    @DisplayName("Valid prices pass")
    public void testValidPrices() {
        assertDoesNotThrow(() -> validator.validatePrice(BigDecimal.ZERO));
        assertDoesNotThrow(() -> validator.validatePrice(new BigDecimal("100.50")));
        assertDoesNotThrow(() -> validator.validatePrice(new BigDecimal("999999.99")));
    }

    @Test
    @DisplayName("Invalid prices throw exception")
    public void testInvalidPrices() {
        assertThrows(IllegalArgumentException.class,
            () -> validator.validatePrice(new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class,
            () -> validator.validatePrice(new BigDecimal("1000001")));
        assertThrows(IllegalArgumentException.class,
            () -> validator.validatePrice(new BigDecimal("100.123"))); // 3 decimal places
    }

    @Test
    @DisplayName("SQL injection patterns detected")
    public void testSqlInjectionDetection() {
        assertTrue(validator.containsSqlInjection("'; DROP TABLE users--"));
        assertTrue(validator.containsSqlInjection("1 OR 1=1"));
        assertTrue(validator.containsSqlInjection("UNION SELECT * FROM"));
        assertTrue(validator.containsSqlInjection("admin'--"));

        assertFalse(validator.containsSqlInjection("normal text"));
        assertFalse(validator.containsSqlInjection("SBIN"));
    }

    @Test
    @DisplayName("Command injection patterns detected")
    public void testCommandInjectionDetection() {
        assertTrue(validator.containsCommandInjection("test | ls"));
        assertTrue(validator.containsCommandInjection("test && rm -rf /"));
        assertTrue(validator.containsCommandInjection("test; cat /etc/passwd"));
        assertTrue(validator.containsCommandInjection("test`whoami`"));
        assertTrue(validator.containsCommandInjection("test$(ls)"));

        assertFalse(validator.containsCommandInjection("normal text"));
        assertFalse(validator.containsCommandInjection("SBIN"));
    }

    @Test
    @DisplayName("XSS patterns detected")
    public void testXssDetection() {
        assertTrue(validator.containsXss("<script>alert('XSS')</script>"));
        assertTrue(validator.containsXss("javascript:alert(1)"));
        assertTrue(validator.containsXss("<img onerror='alert(1)'>"));
        assertTrue(validator.containsXss("<iframe src='evil.com'>"));

        assertFalse(validator.containsXss("normal text"));
        assertFalse(validator.containsXss("SBIN"));
    }

    @Test
    @DisplayName("String sanitization works correctly")
    public void testSanitization() {
        assertEquals("hello", validator.sanitize("hello"));
        assertEquals("hello world", validator.sanitize("  hello world  "));
        assertEquals("hello", validator.sanitize("hello\u0000"));  // Remove null chars

        // Length limiting
        String longString = "a".repeat(2000);
        String sanitized = validator.sanitize(longString);
        assertEquals(1000, sanitized.length());
    }

    @Test
    @DisplayName("Validate and sanitize throws on dangerous input")
    public void testValidateAndSanitizeThrows() {
        assertThrows(SecurityException.class,
            () -> validator.validateAndSanitize("'; DROP TABLE--", "test"));

        assertThrows(SecurityException.class,
            () -> validator.validateAndSanitize("<script>alert(1)</script>", "test"));

        assertThrows(SecurityException.class,
            () -> validator.validateAndSanitize("test | ls", "test"));
    }

    @Test
    @DisplayName("Validate and sanitize accepts clean input")
    public void testValidateAndSanitizeAccepts() {
        assertDoesNotThrow(() -> validator.validateAndSanitize("normal text", "test"));
        assertDoesNotThrow(() -> validator.validateAndSanitize("SBIN", "symbol"));
        assertDoesNotThrow(() -> validator.validateAndSanitize("user@example.com", "email"));
    }

    @Test
    @DisplayName("Broker code validation works")
    public void testBrokerCodeValidation() {
        assertDoesNotThrow(() -> validator.validateBrokerCode("UPSTOX"));
        assertDoesNotThrow(() -> validator.validateBrokerCode("ZERODHA"));
        assertDoesNotThrow(() -> validator.validateBrokerCode("FYERS"));
        assertDoesNotThrow(() -> validator.validateBrokerCode("DHAN"));

        assertThrows(IllegalArgumentException.class, () -> validator.validateBrokerCode("INVALID"));
        assertThrows(IllegalArgumentException.class, () -> validator.validateBrokerCode(null));
    }

    @Test
    @DisplayName("Exchange validation works")
    public void testExchangeValidation() {
        assertDoesNotThrow(() -> validator.validateExchange("NSE"));
        assertDoesNotThrow(() -> validator.validateExchange("BSE"));
        assertDoesNotThrow(() -> validator.validateExchange("NFO"));

        assertThrows(IllegalArgumentException.class, () -> validator.validateExchange("INVALID"));
        assertThrows(IllegalArgumentException.class, () -> validator.validateExchange(null));
    }

    @Test
    @DisplayName("File path sanitization prevents directory traversal")
    public void testFilePathSanitization() {
        assertThrows(SecurityException.class,
            () -> validator.sanitizeFilePath("../../etc/passwd"));

        assertThrows(SecurityException.class,
            () -> validator.sanitizeFilePath("/etc/passwd"));

        assertThrows(SecurityException.class,
            () -> validator.sanitizeFilePath("C:\\Windows\\System32"));

        assertDoesNotThrow(() -> validator.sanitizeFilePath("documents/file.txt"));
    }

    @Test
    @DisplayName("Email validation works")
    public void testEmailValidation() {
        assertTrue(validator.isValidEmail("user@example.com"));
        assertTrue(validator.isValidEmail("test.user@domain.co.in"));
        assertTrue(validator.isValidEmail("user+tag@example.com"));

        assertFalse(validator.isValidEmail("invalid"));
        assertFalse(validator.isValidEmail("@example.com"));
        assertFalse(validator.isValidEmail("user@"));
        assertFalse(validator.isValidEmail(null));
    }

    @Test
    @DisplayName("Range validation works")
    public void testRangeValidation() {
        assertDoesNotThrow(() -> validator.validateRange(5, 1, 10, "test"));
        assertDoesNotThrow(() -> validator.validateRange(1, 1, 10, "test"));
        assertDoesNotThrow(() -> validator.validateRange(10, 1, 10, "test"));

        assertThrows(IllegalArgumentException.class,
            () -> validator.validateRange(0, 1, 10, "test"));
        assertThrows(IllegalArgumentException.class,
            () -> validator.validateRange(11, 1, 10, "test"));
    }

    @Test
    @DisplayName("Alphanumeric validation works")
    public void testAlphanumericValidation() {
        assertTrue(validator.isAlphanumeric("user123"));
        assertTrue(validator.isAlphanumeric("test-user_123"));

        assertFalse(validator.isAlphanumeric("user@123"));
        assertFalse(validator.isAlphanumeric("user 123"));
        assertFalse(validator.isAlphanumeric(null));
    }
}
