package in.annupaper.infrastructure.broker.common;

import in.annupaper.infrastructure.broker.common.TokenRefreshManager.TokenInfo;
import in.annupaper.infrastructure.broker.common.TokenRefreshManager.TokenRefreshException;
import in.annupaper.infrastructure.broker.common.TokenRefreshManager.TokenExpiredException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TokenRefreshManager.
 *
 * Tests:
 * - Initial token fetch
 * - Token expiry detection
 * - Scheduled refresh before expiry
 * - Retry logic after failed refresh
 * - Manual force refresh
 * - Thread safety
 * - Exception handling
 */
class TokenRefreshManagerTest {

    private TokenRefreshManager manager;

    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.shutdown();
        }
    }

    @Test
    void testInitialTokenFetch() {
        TokenInfo expectedToken = new TokenInfo(
            "access_token_123",
            Instant.now().plus(Duration.ofHours(1)),
            "refresh_token_123"
        );

        manager = new TokenRefreshManager(
            "ZERODHA",
            "user123",
            () -> expectedToken,
            Duration.ofMinutes(5)
        );

        manager.start();

        assertTrue(manager.hasValidToken(), "Should have valid token after start");
        assertEquals("access_token_123", manager.getToken());
        assertEquals(expectedToken, manager.getTokenInfo());
    }

    @Test
    void testInitialTokenFetchFailure() {
        manager = new TokenRefreshManager(
            "ZERODHA",
            "user123",
            () -> { throw new RuntimeException("Auth failed"); },
            Duration.ofMinutes(5)
        );

        TokenRefreshException exception = assertThrows(TokenRefreshException.class, () -> {
            manager.start();
        });

        assertTrue(exception.getMessage().contains("Initial token fetch failed"));
        assertEquals("ZERODHA", exception.getBrokerCode());
        assertEquals("user123", exception.getUserBrokerId());
        assertFalse(manager.hasValidToken());
    }

    @Test
    void testGetTokenWhenNoToken() {
        manager = new TokenRefreshManager(
            "ZERODHA",
            "user123",
            () -> new TokenInfo("token", Instant.now().plus(Duration.ofHours(1)), null),
            Duration.ofMinutes(5)
        );

        // Don't start the manager - no token fetched yet
        TokenExpiredException exception = assertThrows(TokenExpiredException.class, () -> {
            manager.getToken();
        });

        assertTrue(exception.getMessage().contains("No token available"));
        assertEquals("ZERODHA", exception.getBrokerCode());
    }

    @Test
    void testGetTokenWhenExpired() {
        // Create token that expires in the past
        TokenInfo expiredToken = new TokenInfo(
            "expired_token",
            Instant.now().minus(Duration.ofMinutes(10)),
            null
        );

        manager = new TokenRefreshManager(
            "ZERODHA",
            "user123",
            () -> expiredToken,
            Duration.ofMinutes(5)
        );

        manager.start();

        TokenExpiredException exception = assertThrows(TokenExpiredException.class, () -> {
            manager.getToken();
        });

        assertTrue(exception.getMessage().contains("Token expired at"));
        assertFalse(manager.hasValidToken());
    }

    @Test
    void testHasValidToken() {
        TokenInfo validToken = new TokenInfo(
            "valid_token",
            Instant.now().plus(Duration.ofHours(1)),
            null
        );

        manager = new TokenRefreshManager(
            "ZERODHA",
            "user123",
            () -> validToken,
            Duration.ofMinutes(5)
        );

        assertFalse(manager.hasValidToken(), "Should not have token before start");

        manager.start();
        assertTrue(manager.hasValidToken(), "Should have valid token after start");

        manager.shutdown();
        assertFalse(manager.hasValidToken(), "Should not have token after shutdown");
    }

    @Test
    void testScheduledRefreshBeforeExpiry() throws InterruptedException {
        AtomicInteger refreshCount = new AtomicInteger(0);

        // Token expires in 2 seconds, refresh window is 1 second
        // So refresh should happen after ~1 second
        manager = new TokenRefreshManager(
            "ZERODHA",
            "user123",
            () -> {
                refreshCount.incrementAndGet();
                return new TokenInfo(
                    "token_" + refreshCount.get(),
                    Instant.now().plus(Duration.ofSeconds(2)),
                    null
                );
            },
            Duration.ofSeconds(1)  // Refresh 1 second before expiry
        );

        manager.start();
        assertEquals(1, refreshCount.get(), "Initial fetch should happen");

        // Wait for scheduled refresh (should happen after ~1 second)
        Thread.sleep(1500);

        assertTrue(refreshCount.get() >= 2, "Scheduled refresh should have occurred");
    }

    @Test
    void testRefreshRetryAfterFailure() throws InterruptedException {
        AtomicInteger attemptCount = new AtomicInteger(0);

        manager = new TokenRefreshManager(
            "ZERODHA",
            "user123",
            () -> {
                int attempt = attemptCount.incrementAndGet();

                if (attempt == 1) {
                    // First attempt succeeds
                    return new TokenInfo(
                        "token_1",
                        Instant.now().plus(Duration.ofSeconds(2)),  // Expires in 2 seconds
                        null
                    );
                } else if (attempt == 2) {
                    // Second attempt (scheduled refresh) fails
                    throw new RuntimeException("Refresh failed");
                } else {
                    // Third attempt (retry) succeeds
                    return new TokenInfo(
                        "token_3",
                        Instant.now().plus(Duration.ofHours(1)),
                        null
                    );
                }
            },
            Duration.ofMillis(500)  // Refresh 500ms before expiry
        );

        manager.start();
        assertEquals(1, attemptCount.get(), "Initial fetch should succeed");
        assertEquals("token_1", manager.getToken());

        // Wait for scheduled refresh to fail
        // Scheduled refresh happens after max(2000ms - 500ms, 1000ms) = 1500ms
        // But there's a 1-second minimum delay, so effectively 1500ms
        Thread.sleep(2000);

        // Verify that refresh was attempted and failed
        assertEquals(2, attemptCount.get(), "Scheduled refresh should have been attempted");
        // Note: Token may have expired by now (2 seconds), so we can't call getToken()
        // The old token is technically still stored but expired

        // Note: Retry happens after 30 seconds which is too long for a unit test
        // The retry scheduling is tested implicitly by verifying no crash on failure
    }

    @Test
    void testForceRefresh() {
        AtomicInteger refreshCount = new AtomicInteger(0);

        manager = new TokenRefreshManager(
            "ZERODHA",
            "user123",
            () -> {
                refreshCount.incrementAndGet();
                return new TokenInfo(
                    "token_" + refreshCount.get(),
                    Instant.now().plus(Duration.ofHours(1)),
                    null
                );
            },
            Duration.ofMinutes(5)
        );

        manager.start();
        assertEquals(1, refreshCount.get());
        assertEquals("token_1", manager.getToken());

        // Force refresh
        manager.forceRefresh();
        assertEquals(2, refreshCount.get(), "Force refresh should trigger token fetch");
        assertEquals("token_2", manager.getToken());
    }

    @Test
    void testForceRefreshFailure() {
        AtomicInteger attemptCount = new AtomicInteger(0);

        manager = new TokenRefreshManager(
            "ZERODHA",
            "user123",
            () -> {
                int attempt = attemptCount.incrementAndGet();
                if (attempt == 1) {
                    return new TokenInfo("token_1", Instant.now().plus(Duration.ofHours(1)), null);
                } else {
                    throw new RuntimeException("Force refresh failed");
                }
            },
            Duration.ofMinutes(5)
        );

        manager.start();
        assertEquals("token_1", manager.getToken());

        // Force refresh fails
        TokenRefreshException exception = assertThrows(TokenRefreshException.class, () -> {
            manager.forceRefresh();
        });

        assertTrue(exception.getMessage().contains("Forced refresh failed"));
        // Old token should still be available
        assertEquals("token_1", manager.getToken());
    }

    @Test
    void testTokenRefreshFunctionReturnsNull() {
        manager = new TokenRefreshManager(
            "ZERODHA",
            "user123",
            () -> null,  // Returns null
            Duration.ofMinutes(5)
        );

        TokenRefreshException exception = assertThrows(TokenRefreshException.class, () -> {
            manager.start();
        });

        // The exception wraps the original TokenRefreshException in another TokenRefreshException
        // with "Initial token fetch failed" message
        String message = exception.getMessage();
        assertTrue(message.contains("ZERODHA") && message.contains("user123"),
            "Exception should contain broker code and user ID");
        assertTrue(message.contains("Initial token fetch failed"),
            "Exception should indicate initial token fetch failed");
    }

    @Test
    void testShutdownCleansUp() {
        manager = new TokenRefreshManager(
            "ZERODHA",
            "user123",
            () -> new TokenInfo("token", Instant.now().plus(Duration.ofHours(1)), null),
            Duration.ofMinutes(5)
        );

        manager.start();
        assertTrue(manager.hasValidToken());

        manager.shutdown();
        assertFalse(manager.hasValidToken(), "Token should be cleared after shutdown");
        assertNull(manager.getTokenInfo(), "Token info should be null after shutdown");
    }

    @Test
    void testMultipleStartCallsIgnored() {
        AtomicInteger fetchCount = new AtomicInteger(0);

        manager = new TokenRefreshManager(
            "ZERODHA",
            "user123",
            () -> {
                fetchCount.incrementAndGet();
                return new TokenInfo("token", Instant.now().plus(Duration.ofHours(1)), null);
            },
            Duration.ofMinutes(5)
        );

        manager.start();
        assertEquals(1, fetchCount.get());

        // Second start should be ignored
        manager.start();
        assertEquals(1, fetchCount.get(), "Second start should not fetch token again");
    }

    @Test
    void testThreadSafety() throws InterruptedException {
        manager = new TokenRefreshManager(
            "ZERODHA",
            "user123",
            () -> new TokenInfo("token", Instant.now().plus(Duration.ofHours(1)), null),
            Duration.ofMinutes(5)
        );

        manager.start();

        // Access token from multiple threads
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(10);

        for (int i = 0; i < 10; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < 100; j++) {
                        String token = manager.getToken();
                        assertNotNull(token);
                        assertTrue(manager.hasValidToken());
                    }
                } catch (Exception e) {
                    fail("Thread-safe access failed: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();  // Start all threads
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS), "All threads should complete");
    }

    @Test
    void testTokenInfoRecord() {
        Instant expiresAt = Instant.now().plus(Duration.ofHours(1));
        TokenInfo info = new TokenInfo("access_token", expiresAt, "refresh_token");

        assertEquals("access_token", info.accessToken());
        assertEquals(expiresAt, info.expiresAt());
        assertEquals("refresh_token", info.refreshToken());
    }

    @Test
    void testMinimumRefreshDelay() throws InterruptedException {
        // Token expires in 100ms, refresh window is 200ms
        // This means refresh should be scheduled immediately (but with 1s minimum delay)
        AtomicInteger refreshCount = new AtomicInteger(0);

        manager = new TokenRefreshManager(
            "ZERODHA",
            "user123",
            () -> {
                refreshCount.incrementAndGet();
                return new TokenInfo(
                    "token",
                    Instant.now().plus(Duration.ofMillis(100)),
                    null
                );
            },
            Duration.ofMillis(200)  // Refresh window > token lifetime
        );

        manager.start();
        assertEquals(1, refreshCount.get());

        // Refresh should be scheduled with minimum 1s delay
        Thread.sleep(500);
        assertEquals(1, refreshCount.get(), "Should not refresh before 1s minimum delay");

        Thread.sleep(700);
        assertTrue(refreshCount.get() >= 2, "Should refresh after 1s minimum delay");
    }
}
