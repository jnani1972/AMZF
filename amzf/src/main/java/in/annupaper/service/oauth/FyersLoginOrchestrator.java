package in.annupaper.service.oauth;

import in.annupaper.domain.broker.BrokerIds;
import in.annupaper.domain.broker.UserBroker;
import in.annupaper.domain.repository.OAuthStateRepository;
import in.annupaper.domain.repository.UserBrokerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates FYERS OAuth login flow.
 *
 * Features:
 * - Generates login URL with DB-backed state (survives restarts)
 * - Opens browser automatically (Desktop.getDesktop().browse())
 * - Spam guard: throttles browser opening to once per 60s per broker
 * - Returns debug-friendly response (loginUrl, state, redirectUri)
 *
 * Flow:
 * 1. GET /api/brokers/:id/fyers/login-url
 *    → generates state, stores in DB
 *    → returns { loginUrl, state, redirectUri }
 * 2. (Optional) Server can auto-open browser via Desktop.browse()
 * 3. User logs in on FYERS page
 * 4. FYERS redirects to redirectUri with auth_code + state
 * 5. Frontend calls POST /api/fyers/oauth/exchange
 *    → validates state, exchanges token, reconnects
 */
public final class FyersLoginOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(FyersLoginOrchestrator.class);

    private static final String FYERS_AUTH_URL = "https://api-t1.fyers.in/api/v3/generate-authcode";
    private static final int STATE_EXPIRY_MINUTES = 15;
    private static final long BROWSER_THROTTLE_SECONDS = 60;

    private final OAuthStateRepository stateRepo;
    private final UserBrokerRepository userBrokerRepo;
    private final String redirectUri;

    // Browser spam guard: track last login attempt per userBrokerId
    private final Map<String, Instant> lastLoginAttempt = new ConcurrentHashMap<>();

    public FyersLoginOrchestrator(
        OAuthStateRepository stateRepo,
        UserBrokerRepository userBrokerRepo,
        String redirectUri
    ) {
        this.stateRepo = stateRepo;
        this.userBrokerRepo = userBrokerRepo;
        this.redirectUri = redirectUri;
    }

    /**
     * Generate FYERS login URL with state parameter.
     *
     * @param userBrokerId User-broker ID
     * @return LoginUrlResponse with loginUrl, state, redirectUri
     */
    public LoginUrlResponse generateLoginUrl(String userBrokerId) {
        // Get UserBroker to extract FYERS app ID
        Optional<UserBroker> ubOpt = userBrokerRepo.findById(userBrokerId);
        if (ubOpt.isEmpty()) {
            log.error("[FYERS LOGIN] UserBroker not found: {}", userBrokerId);
            throw new IllegalArgumentException("UserBroker not found: " + userBrokerId);
        }

        UserBroker ub = ubOpt.get();
        if (!BrokerIds.FYERS.equalsIgnoreCase(ub.brokerId())) {
            log.error("[FYERS LOGIN] Not a FYERS broker: {}", ub.brokerId());
            throw new IllegalArgumentException("Not a FYERS broker: " + ub.brokerId());
        }

        // Extract FYERS app ID from credentials
        String appId = extractAppId(ub);
        if (appId == null || appId.isEmpty()) {
            log.error("[FYERS LOGIN] No apiKey in credentials for userBrokerId={}", userBrokerId);
            throw new IllegalStateException("No FYERS apiKey configured");
        }

        // Generate state and store in DB (survives restarts)
        String state = stateRepo.generateState(userBrokerId, BrokerIds.FYERS, STATE_EXPIRY_MINUTES);

        // Build FYERS auth URL
        String loginUrl = String.format(
            "%s?client_id=%s&redirect_uri=%s&response_type=code&state=%s",
            FYERS_AUTH_URL,
            appId,
            URLEncoder.encode(redirectUri, StandardCharsets.UTF_8),
            URLEncoder.encode(state, StandardCharsets.UTF_8)
        );

        log.info("[FYERS LOGIN] Generated login URL for userBrokerId={}, state={}", userBrokerId, state);

        return new LoginUrlResponse(loginUrl, state, redirectUri, appId);
    }

    /**
     * Open FYERS login URL in browser (auto-login on startup).
     *
     * Spam guard: only opens browser once per 60s per userBrokerId.
     *
     * @param userBrokerId User-broker ID
     * @return true if browser opened, false if throttled
     */
    public boolean openBrowserLogin(String userBrokerId) {
        // Check if login already in progress (unused, unexpired state exists)
        if (stateRepo.isLoginInProgress(userBrokerId)) {
            log.info("[FYERS LOGIN] Login already in progress for userBrokerId={} (unused OAuth state exists)",
                userBrokerId);
            return false;
        }

        // Browser spam guard (time-based throttle)
        Instant lastAttempt = lastLoginAttempt.get(userBrokerId);
        if (lastAttempt != null) {
            long secondsSince = Instant.now().getEpochSecond() - lastAttempt.getEpochSecond();
            if (secondsSince < BROWSER_THROTTLE_SECONDS) {
                log.warn("[FYERS LOGIN] Browser open throttled for userBrokerId={} ({}s since last attempt)",
                    userBrokerId, secondsSince);
                return false;
            }
        }

        // Generate login URL
        LoginUrlResponse response = generateLoginUrl(userBrokerId);

        // Try to open browser
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().browse(URI.create(response.loginUrl()));
                lastLoginAttempt.put(userBrokerId, Instant.now());
                log.info("[FYERS LOGIN] ✅ Opened browser for userBrokerId={}", userBrokerId);
                return true;
            } catch (IOException e) {
                log.error("[FYERS LOGIN] Failed to open browser for userBrokerId={}: {}",
                    userBrokerId, e.getMessage());
                log.error("[FYERS LOGIN] Please manually open: {}", response.loginUrl());
                return false;
            }
        } else {
            log.warn("[FYERS LOGIN] Desktop not supported (headless environment)");
            log.warn("[FYERS LOGIN] Please manually open: {}", response.loginUrl());
            return false;
        }
    }

    /**
     * Extract FYERS app ID from UserBroker credentials.
     */
    private String extractAppId(UserBroker ub) {
        if (ub.credentials() == null) {
            return null;
        }
        if (ub.credentials().has("apiKey")) {
            return ub.credentials().get("apiKey").asText();
        }
        return null;
    }

    /**
     * Response for login URL generation.
     */
    public record LoginUrlResponse(
        String loginUrl,
        String state,
        String redirectUri,
        String appId
    ) {}
}
