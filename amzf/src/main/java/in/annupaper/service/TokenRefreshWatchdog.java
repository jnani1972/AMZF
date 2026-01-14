package in.annupaper.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Monitors user_broker_sessions table for token refreshes and triggers automatic reload.
 * When a new access token is detected, notifies registered listeners to reload their connections.
 */
public class TokenRefreshWatchdog {
    private static final Logger log = LoggerFactory.getLogger(TokenRefreshWatchdog.class);

    private final DataSource dataSource;
    private final ScheduledExecutorService scheduler;
    private final Map<String, String> lastKnownSessionIds;
    private final Map<String, TokenRefreshListener> listeners;

    public interface TokenRefreshListener {
        /**
         * Called when a new access token is detected for a user_broker_id.
         * @param userBrokerId the user broker ID
         * @param newAccessToken the new access token
         * @param sessionId the new session ID
         */
        void onTokenRefresh(String userBrokerId, String newAccessToken, String sessionId);
    }

    public TokenRefreshWatchdog(DataSource dataSource) {
        this.dataSource = dataSource;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TokenRefreshWatchdog");
            t.setDaemon(true);
            return t;
        });
        this.lastKnownSessionIds = new ConcurrentHashMap<>();
        this.listeners = new ConcurrentHashMap<>();
    }

    /**
     * Register a listener for token refresh events for a specific user_broker_id.
     */
    public void registerListener(String userBrokerId, TokenRefreshListener listener) {
        listeners.put(userBrokerId, listener);
        log.info("[TOKEN WATCHDOG] Registered listener for user_broker: {}", userBrokerId);
    }

    /**
     * Start monitoring for token refreshes.
     * Checks every 30 seconds for new sessions.
     */
    public void start() {
        log.info("[TOKEN WATCHDOG] Starting token refresh monitoring (check interval: 30s)");

        // Initialize with current sessions
        initializeCurrentSessions();

        // Schedule periodic checks
        scheduler.scheduleAtFixedRate(
            this::checkForTokenRefresh,
            30,  // Initial delay
            30,  // Period
            TimeUnit.SECONDS
        );
    }

    /**
     * Initialize with current active sessions to avoid false positives on startup.
     */
    private void initializeCurrentSessions() {
        String sql = """
            SELECT user_broker_id, session_id, access_token
            FROM user_broker_sessions
            WHERE session_status = 'ACTIVE'
            AND token_valid_till > NOW()
            ORDER BY session_started_at DESC
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            Map<String, String> latestSessions = new ConcurrentHashMap<>();
            while (rs.next()) {
                String userBrokerId = rs.getString("user_broker_id");
                String sessionId = rs.getString("session_id");

                // Keep only the latest session per user_broker_id
                if (!latestSessions.containsKey(userBrokerId)) {
                    latestSessions.put(userBrokerId, sessionId);
                }
            }

            lastKnownSessionIds.putAll(latestSessions);
            log.info("[TOKEN WATCHDOG] Initialized with {} active sessions", latestSessions.size());

        } catch (Exception e) {
            log.error("[TOKEN WATCHDOG] Failed to initialize current sessions", e);
        }
    }

    /**
     * Check for new token refreshes by comparing session IDs.
     */
    private void checkForTokenRefresh() {
        String sql = """
            SELECT user_broker_id, session_id, access_token
            FROM user_broker_sessions
            WHERE session_status = 'ACTIVE'
            AND token_valid_till > NOW()
            ORDER BY session_started_at DESC
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            Map<String, SessionInfo> latestSessions = new ConcurrentHashMap<>();

            while (rs.next()) {
                String userBrokerId = rs.getString("user_broker_id");
                String sessionId = rs.getString("session_id");
                String accessToken = rs.getString("access_token");

                // Keep only the latest session per user_broker_id
                if (!latestSessions.containsKey(userBrokerId)) {
                    latestSessions.put(userBrokerId, new SessionInfo(sessionId, accessToken));
                }
            }

            // Compare with last known sessions
            for (Map.Entry<String, SessionInfo> entry : latestSessions.entrySet()) {
                String userBrokerId = entry.getKey();
                String newSessionId = entry.getValue().sessionId;
                String newAccessToken = entry.getValue().accessToken;

                String lastSessionId = lastKnownSessionIds.get(userBrokerId);

                // Check if session ID changed (token was refreshed)
                if (lastSessionId == null) {
                    // New user_broker_id (maybe added after startup)
                    log.info("[TOKEN WATCHDOG] New broker connection detected: {} (session: {})",
                            userBrokerId, newSessionId);
                    lastKnownSessionIds.put(userBrokerId, newSessionId);

                } else if (!lastSessionId.equals(newSessionId)) {
                    // Token was refreshed!
                    log.info("[TOKEN WATCHDOG] ⚡ Token refresh detected for {}: {} → {}",
                            userBrokerId, lastSessionId, newSessionId);

                    // Update our tracking
                    lastKnownSessionIds.put(userBrokerId, newSessionId);

                    // Notify listener if registered
                    TokenRefreshListener listener = listeners.get(userBrokerId);
                    if (listener != null) {
                        try {
                            log.info("[TOKEN WATCHDOG] Notifying listener for {}", userBrokerId);
                            listener.onTokenRefresh(userBrokerId, newAccessToken, newSessionId);
                        } catch (Exception e) {
                            log.error("[TOKEN WATCHDOG] Listener failed for {}", userBrokerId, e);
                        }
                    } else {
                        log.warn("[TOKEN WATCHDOG] No listener registered for {} (token refresh ignored)",
                                userBrokerId);
                    }
                }
            }

        } catch (Exception e) {
            log.error("[TOKEN WATCHDOG] Failed to check for token refresh", e);
        }
    }

    /**
     * Stop the watchdog service.
     */
    public void stop() {
        log.info("[TOKEN WATCHDOG] Stopping token refresh monitoring");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static class SessionInfo {
        final String sessionId;
        final String accessToken;

        SessionInfo(String sessionId, String accessToken) {
            this.sessionId = sessionId;
            this.accessToken = accessToken;
        }
    }
}
