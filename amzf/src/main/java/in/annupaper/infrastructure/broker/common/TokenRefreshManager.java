package in.annupaper.infrastructure.broker.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Token refresh manager with automatic refresh scheduling.
 *
 * Features:
 * - Automatic token refresh before expiry
 * - Configurable refresh window (e.g., refresh 5 minutes before expiry)
 * - Retry logic for failed refresh attempts
 * - Thread-safe token storage
 * - Expiry tracking and validation
 *
 * Usage:
 * <pre>
 * TokenRefreshManager manager = new TokenRefreshManager(
 *     "ZERODHA",
 *     "user123",
 *     () -> fetchNewTokenFromBroker(),
 *     Duration.ofMinutes(5)  // Refresh 5 min before expiry
 * );
 *
 * manager.start();
 * String token = manager.getToken();  // Get current valid token
 * manager.shutdown();
 * </pre>
 */
public class TokenRefreshManager {

    private static final Logger log = LoggerFactory.getLogger(TokenRefreshManager.class);

    private final String brokerCode;
    private final String userBrokerId;
    private final Supplier<TokenInfo> tokenRefreshFunction;
    private final Duration refreshWindow;

    private final ScheduledExecutorService scheduler;
    private volatile TokenInfo currentToken;
    private volatile ScheduledFuture<?> refreshTask;
    private volatile boolean running = false;

    public TokenRefreshManager(String brokerCode, String userBrokerId,
                              Supplier<TokenInfo> tokenRefreshFunction,
                              Duration refreshWindow) {
        this.brokerCode = brokerCode;
        this.userBrokerId = userBrokerId;
        this.tokenRefreshFunction = tokenRefreshFunction;
        this.refreshWindow = refreshWindow;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TokenRefresh-" + brokerCode + "-" + userBrokerId);
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Start the token refresh manager.
     * Performs initial token fetch and schedules automatic refresh.
     */
    public synchronized void start() {
        if (running) {
            log.warn("[{}:{}] Token refresh manager already running", brokerCode, userBrokerId);
            return;
        }

        log.info("[{}:{}] Starting token refresh manager", brokerCode, userBrokerId);
        running = true;

        // Fetch initial token
        try {
            refreshToken();
        } catch (Exception e) {
            log.error("[{}:{}] Failed to fetch initial token", brokerCode, userBrokerId, e);
            throw new TokenRefreshException(brokerCode, userBrokerId, "Initial token fetch failed", e);
        }
    }

    /**
     * Stop the token refresh manager and cancel scheduled refresh.
     */
    public synchronized void shutdown() {
        if (!running) {
            return;
        }

        log.info("[{}:{}] Shutting down token refresh manager", brokerCode, userBrokerId);
        running = false;

        if (refreshTask != null) {
            refreshTask.cancel(false);
            refreshTask = null;
        }

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        currentToken = null;
    }

    /**
     * Get the current valid access token.
     *
     * @return Current access token
     * @throws TokenExpiredException if token is expired
     */
    public String getToken() {
        TokenInfo token = currentToken;
        if (token == null) {
            throw new TokenExpiredException(brokerCode, userBrokerId, "No token available");
        }

        if (isExpired(token)) {
            throw new TokenExpiredException(brokerCode, userBrokerId,
                "Token expired at " + token.expiresAt());
        }

        return token.accessToken();
    }

    /**
     * Get current token info including expiry.
     *
     * @return TokenInfo or null if no token available
     */
    public TokenInfo getTokenInfo() {
        return currentToken;
    }

    /**
     * Check if current token is valid (not null and not expired).
     *
     * @return true if token is valid
     */
    public boolean hasValidToken() {
        TokenInfo token = currentToken;
        return token != null && !isExpired(token);
    }

    /**
     * Manually trigger token refresh.
     * Useful for forcing refresh after authentication errors.
     */
    public synchronized void forceRefresh() {
        log.info("[{}:{}] Forcing token refresh", brokerCode, userBrokerId);
        try {
            refreshToken();
        } catch (Exception e) {
            log.error("[{}:{}] Forced token refresh failed", brokerCode, userBrokerId, e);
            throw new TokenRefreshException(brokerCode, userBrokerId, "Forced refresh failed", e);
        }
    }

    /**
     * Refresh the token and schedule next refresh.
     */
    private void refreshToken() {
        log.debug("[{}:{}] Refreshing token", brokerCode, userBrokerId);

        try {
            TokenInfo newToken = tokenRefreshFunction.get();
            if (newToken == null) {
                throw new TokenRefreshException(brokerCode, userBrokerId,
                    "Token refresh function returned null");
            }

            currentToken = newToken;
            log.info("[{}:{}] Token refreshed successfully, expires at {}",
                brokerCode, userBrokerId, newToken.expiresAt());

            // Schedule next refresh
            scheduleNextRefresh(newToken);

        } catch (Exception e) {
            log.error("[{}:{}] Token refresh failed", brokerCode, userBrokerId, e);
            // Schedule retry after 30 seconds
            scheduleRefreshRetry();
            throw e;
        }
    }

    /**
     * Schedule next token refresh before expiry.
     */
    private void scheduleNextRefresh(TokenInfo token) {
        if (!running) {
            return;
        }

        // Cancel existing refresh task
        if (refreshTask != null) {
            refreshTask.cancel(false);
        }

        // Calculate refresh time: expiresAt - refreshWindow
        Instant refreshAt = token.expiresAt().minus(refreshWindow);
        long delayMillis = Duration.between(Instant.now(), refreshAt).toMillis();

        // Ensure minimum delay of 1 second
        delayMillis = Math.max(delayMillis, 1000);

        log.debug("[{}:{}] Scheduling next token refresh in {} seconds",
            brokerCode, userBrokerId, delayMillis / 1000);

        refreshTask = scheduler.schedule(() -> {
            try {
                refreshToken();
            } catch (Exception e) {
                log.error("[{}:{}] Scheduled token refresh failed", brokerCode, userBrokerId, e);
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Schedule retry after failed refresh.
     */
    private void scheduleRefreshRetry() {
        if (!running) {
            return;
        }

        log.info("[{}:{}] Scheduling token refresh retry in 30 seconds",
            brokerCode, userBrokerId);

        refreshTask = scheduler.schedule(() -> {
            try {
                refreshToken();
            } catch (Exception e) {
                log.error("[{}:{}] Token refresh retry failed", brokerCode, userBrokerId, e);
            }
        }, 30, TimeUnit.SECONDS);
    }

    /**
     * Check if token is expired or about to expire.
     */
    private boolean isExpired(TokenInfo token) {
        return Instant.now().isAfter(token.expiresAt());
    }

    /**
     * Token information record.
     */
    public record TokenInfo(
        String accessToken,
        Instant expiresAt,
        String refreshToken  // Optional, may be null
    ) {}

    /**
     * Exception thrown when token refresh fails.
     */
    public static class TokenRefreshException extends RuntimeException {
        private final String brokerCode;
        private final String userBrokerId;

        public TokenRefreshException(String brokerCode, String userBrokerId, String message) {
            super(String.format("[%s:%s] %s", brokerCode, userBrokerId, message));
            this.brokerCode = brokerCode;
            this.userBrokerId = userBrokerId;
        }

        public TokenRefreshException(String brokerCode, String userBrokerId,
                                    String message, Throwable cause) {
            super(String.format("[%s:%s] %s", brokerCode, userBrokerId, message), cause);
            this.brokerCode = brokerCode;
            this.userBrokerId = userBrokerId;
        }

        public String getBrokerCode() {
            return brokerCode;
        }

        public String getUserBrokerId() {
            return userBrokerId;
        }
    }

    /**
     * Exception thrown when token is expired.
     */
    public static class TokenExpiredException extends RuntimeException {
        private final String brokerCode;
        private final String userBrokerId;

        public TokenExpiredException(String brokerCode, String userBrokerId, String message) {
            super(String.format("[%s:%s] %s", brokerCode, userBrokerId, message));
            this.brokerCode = brokerCode;
            this.userBrokerId = userBrokerId;
        }

        public String getBrokerCode() {
            return brokerCode;
        }

        public String getUserBrokerId() {
            return userBrokerId;
        }
    }
}
