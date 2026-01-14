package in.annupaper.service.oauth;

import in.annupaper.domain.broker.OAuthState;
import in.annupaper.domain.broker.UserBroker;
import in.annupaper.domain.broker.UserBrokerSession;
import in.annupaper.repository.OAuthStateRepository;
import in.annupaper.repository.UserBrokerRepository;
import in.annupaper.repository.UserBrokerSessionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for handling broker OAuth flows.
 * Currently supports Fyers OAuth v3.
 */
public class BrokerOAuthService {
    private static final Logger log = LoggerFactory.getLogger(BrokerOAuthService.class);

    private final UserBrokerRepository userBrokerRepo;
    private final UserBrokerSessionRepository sessionRepo;
    private final OAuthStateRepository oauthStateRepo;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String redirectUri;

    public BrokerOAuthService(
        UserBrokerRepository userBrokerRepo,
        UserBrokerSessionRepository sessionRepo,
        OAuthStateRepository oauthStateRepo,
        String redirectUri
    ) {
        this.userBrokerRepo = userBrokerRepo;
        this.sessionRepo = sessionRepo;
        this.oauthStateRepo = oauthStateRepo;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();
        this.redirectUri = redirectUri;
    }

    /**
     * Generate OAuth authorization URL for Fyers.
     */
    public String generateFyersOAuthUrl(String userBrokerId) {
        Optional<UserBroker> userBrokerOpt = userBrokerRepo.findById(userBrokerId);
        if (userBrokerOpt.isEmpty()) {
            throw new IllegalArgumentException("User broker not found: " + userBrokerId);
        }

        UserBroker userBroker = userBrokerOpt.get();
        JsonNode credentials = userBroker.credentials();

        // Support both apiKey/apiSecret and appId/secretId formats
        if (!credentials.has("apiKey") && !credentials.has("appId")) {
            throw new IllegalStateException("apiKey/appId not configured for user broker: " + userBrokerId);
        }

        String appId = credentials.has("apiKey") ? credentials.get("apiKey").asText() : credentials.get("appId").asText();
        String state = userBrokerId; // Use userBrokerId as state to track which broker connection

        // Fyers OAuth v3 URL (Using test API since production is currently unavailable)
        // TODO: Switch to https://api.fyers.in when production API is available
        String authUrl = String.format(
            "https://api-t1.fyers.in/api/v3/generate-authcode?client_id=%s&redirect_uri=%s&response_type=code&state=%s",
            URLEncoder.encode(appId, StandardCharsets.UTF_8),
            URLEncoder.encode(redirectUri, StandardCharsets.UTF_8),
            URLEncoder.encode(state, StandardCharsets.UTF_8)
        );

        log.info("Generated Fyers OAuth v3 URL for user_broker={}", userBrokerId);
        return authUrl;
    }

    /**
     * Handle OAuth callback - exchange auth code for access token.
     */
    public UserBrokerSession handleFyersCallback(String authCode, String state) {
        String userBrokerId = state;

        Optional<UserBroker> userBrokerOpt = userBrokerRepo.findById(userBrokerId);
        if (userBrokerOpt.isEmpty()) {
            throw new IllegalArgumentException("User broker not found: " + userBrokerId);
        }

        UserBroker userBroker = userBrokerOpt.get();
        JsonNode credentials = userBroker.credentials();

        // Support multiple credential field name formats: apiKey/apiSecret, appId/secretId, appId/secretKey
        String appId = credentials.has("apiKey") ? credentials.get("apiKey").asText() : credentials.get("appId").asText();
        String secretId = credentials.has("apiSecret") ? credentials.get("apiSecret").asText()
                        : credentials.has("secretId") ? credentials.get("secretId").asText()
                        : credentials.get("secretKey").asText();

        // Generate appIdHash = SHA256(appId:secretId)
        String appIdHash = generateSHA256Hash(appId + ":" + secretId);

        // Exchange auth code for access token
        String accessToken = exchangeAuthCodeForToken(appId, appIdHash, authCode);

        // Create session
        Instant validTill = Instant.now().plus(24, ChronoUnit.HOURS); // Fyers tokens valid for 24 hours
        String sessionId = "SESSION_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        UserBrokerSession session = UserBrokerSession.create(
            sessionId,
            userBrokerId,
            accessToken,
            validTill
        );

        // Revoke any existing active sessions
        Optional<UserBrokerSession> existingSession = sessionRepo.findActiveSession(userBrokerId);
        if (existingSession.isPresent()) {
            UserBrokerSession revoked = existingSession.get().withStatus(UserBrokerSession.SessionStatus.REVOKED);
            sessionRepo.update(revoked);
            log.info("Revoked existing session {} for user_broker={}", existingSession.get().sessionId(), userBrokerId);
        }

        // Save new session
        sessionRepo.insert(session);

        log.info("Created new session {} for user_broker={}, valid till {}", sessionId, userBrokerId, validTill);

        return session;
    }

    /**
     * Exchange Fyers auth code for access token.
     */
    private String exchangeAuthCodeForToken(String appId, String appIdHash, String authCode) {
        try {
            // Build request body
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("grant_type", "authorization_code");
            requestBody.put("appIdHash", appIdHash);
            requestBody.put("code", authCode);

            String requestBodyJson = objectMapper.writeValueAsString(requestBody);

            // Make HTTP POST request to Fyers API v3 (Using test API temporarily)
            // TODO: Switch to https://api.fyers.in when production API is available
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api-t1.fyers.in/api/v3/validate-authcode"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Fyers token exchange failed: HTTP {} - {}", response.statusCode(), response.body());
                throw new RuntimeException("Failed to exchange auth code: HTTP " + response.statusCode());
            }

            // Parse response
            JsonNode responseJson = objectMapper.readTree(response.body());

            if (!responseJson.has("s") || !"ok".equals(responseJson.get("s").asText())) {
                String errorMsg = responseJson.has("message") ? responseJson.get("message").asText() : "Unknown error";
                log.error("Fyers token exchange error: {}", errorMsg);
                throw new RuntimeException("Token exchange failed: " + errorMsg);
            }

            if (!responseJson.has("access_token")) {
                log.error("Fyers response missing access_token: {}", responseJson);
                throw new RuntimeException("Response missing access_token");
            }

            String accessToken = responseJson.get("access_token").asText();
            log.info("Successfully exchanged auth code for access token (appId={})", maskKey(appId));

            return accessToken;

        } catch (Exception e) {
            log.error("Error exchanging auth code for token: {}", e.getMessage());
            throw new RuntimeException("Failed to exchange auth code", e);
        }
    }

    /**
     * Generate SHA256 hash.
     */
    private String generateSHA256Hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate SHA256 hash", e);
        }
    }

    /**
     * Get current session for user broker.
     */
    public Optional<UserBrokerSession> getCurrentSession(String userBrokerId) {
        return sessionRepo.findActiveSession(userBrokerId);
    }

    /**
     * Revoke current session.
     */
    public void revokeSession(String userBrokerId) {
        Optional<UserBrokerSession> sessionOpt = sessionRepo.findActiveSession(userBrokerId);
        if (sessionOpt.isPresent()) {
            UserBrokerSession revoked = sessionOpt.get().withStatus(UserBrokerSession.SessionStatus.REVOKED);
            sessionRepo.update(revoked);
            log.info("Revoked session {} for user_broker={}", sessionOpt.get().sessionId(), userBrokerId);
        }
    }

    /**
     * Exchange FYERS auth code for access token (with state validation and idempotency).
     *
     * This is the NEW callback handler that:
     * 1. Validates state exists, not expired, not used (CSRF protection)
     * 2. Checks idempotency (if already used, returns {ok:true, alreadyDone:true})
     * 3. Exchanges auth_code → access_token
     * 4. Saves session to DB
     * 5. Returns result (caller will trigger immediate reconnect)
     *
     * @param authCode FYERS auth code from callback
     * @param state State parameter from callback
     * @return Exchange result with session info
     */
    public ExchangeResult exchangeAuthCodeWithState(String authCode, String state) {
        log.info("[OAUTH EXCHANGE] Received callback: state={}", state);

        // 1. Validate state exists
        Optional<OAuthState> stateOpt = oauthStateRepo.findByState(state);
        if (stateOpt.isEmpty()) {
            log.error("[OAUTH EXCHANGE] State not found: {}", state);
            return ExchangeResult.failure("Invalid state parameter", "STATE_NOT_FOUND");
        }

        OAuthState oauthState = stateOpt.get();

        // 2. Check if state is expired
        if (oauthState.isExpired()) {
            log.error("[OAUTH EXCHANGE] State expired: {} (expiresAt={})", state, oauthState.expiresAt());
            return ExchangeResult.failure("State expired", "STATE_EXPIRED");
        }

        // 3. Check idempotency: if already used, return success (don't re-exchange)
        if (oauthState.isAlreadyUsed()) {
            log.info("[OAUTH EXCHANGE] ✅ State already used (idempotent): {}", state);
            // Return existing session
            Optional<UserBrokerSession> existingSession = sessionRepo.findActiveSession(oauthState.userBrokerId());
            if (existingSession.isPresent()) {
                return ExchangeResult.successAlreadyDone(existingSession.get());
            } else {
                log.warn("[OAUTH EXCHANGE] State marked used but no active session found for userBrokerId={}",
                    oauthState.userBrokerId());
                return ExchangeResult.failure("State already used but session not found", "SESSION_NOT_FOUND");
            }
        }

        // 4. Mark state as used BEFORE exchange (prevents race conditions on page refresh)
        boolean marked = oauthStateRepo.markUsed(state);
        if (!marked) {
            log.warn("[OAUTH EXCHANGE] Failed to mark state as used (race condition?): {}", state);
            return ExchangeResult.failure("Failed to mark state as used", "STATE_ALREADY_USED");
        }

        // 5. Get UserBroker to extract credentials
        Optional<UserBroker> userBrokerOpt = userBrokerRepo.findById(oauthState.userBrokerId());
        if (userBrokerOpt.isEmpty()) {
            log.error("[OAUTH EXCHANGE] UserBroker not found: {}", oauthState.userBrokerId());
            return ExchangeResult.failure("UserBroker not found", "USER_BROKER_NOT_FOUND");
        }

        UserBroker userBroker = userBrokerOpt.get();
        JsonNode credentials = userBroker.credentials();

        // 6. Extract FYERS credentials
        String appId = credentials.has("apiKey") ? credentials.get("apiKey").asText()
                     : credentials.get("appId").asText();
        String secretId = credentials.has("apiSecret") ? credentials.get("apiSecret").asText()
                        : credentials.has("secretId") ? credentials.get("secretId").asText()
                        : credentials.get("secretKey").asText();

        // 7. Generate appIdHash = SHA256(appId:secretId)
        String appIdHash = generateSHA256Hash(appId + ":" + secretId);

        // 8. Exchange auth code for access token
        String accessToken;
        try {
            accessToken = exchangeAuthCodeForToken(appId, appIdHash, authCode);
        } catch (Exception e) {
            log.error("[OAUTH EXCHANGE] Token exchange failed: {}", e.getMessage());
            return ExchangeResult.failure("Token exchange failed: " + e.getMessage(), "TOKEN_EXCHANGE_FAILED");
        }

        // 9. Create session
        Instant validTill = Instant.now().plus(24, ChronoUnit.HOURS); // Fyers tokens valid for 24 hours
        String sessionId = "SESSION_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        UserBrokerSession session = UserBrokerSession.create(
            sessionId,
            oauthState.userBrokerId(),
            accessToken,
            validTill
        );

        // 10. Revoke any existing active sessions
        Optional<UserBrokerSession> existingSession = sessionRepo.findActiveSession(oauthState.userBrokerId());
        if (existingSession.isPresent()) {
            UserBrokerSession revoked = existingSession.get().withStatus(UserBrokerSession.SessionStatus.REVOKED);
            sessionRepo.update(revoked);
            log.info("[OAUTH EXCHANGE] Revoked existing session {} for userBrokerId={}",
                existingSession.get().sessionId(), oauthState.userBrokerId());
        }

        // 11. Save new session
        sessionRepo.insert(session);

        log.info("[OAUTH EXCHANGE] ✅ Created new session {} for userBrokerId={}, valid till {}",
            sessionId, oauthState.userBrokerId(), validTill);

        return ExchangeResult.success(session);
    }

    /**
     * Mask sensitive key for logging.
     */
    private String maskKey(String key) {
        if (key == null || key.length() < 8) return "***";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    /**
     * Result of OAuth token exchange.
     */
    public record ExchangeResult(
        boolean success,
        boolean alreadyDone,       // true if idempotent (state already used)
        String errorMessage,
        String errorCode,
        UserBrokerSession session,
        String userBrokerId,
        String sessionId,
        String accessToken
    ) {
        public static ExchangeResult success(UserBrokerSession session) {
            return new ExchangeResult(
                true, false, null, null,
                session, session.userBrokerId(), session.sessionId(), session.accessToken()
            );
        }

        public static ExchangeResult successAlreadyDone(UserBrokerSession session) {
            return new ExchangeResult(
                true, true, null, null,
                session, session.userBrokerId(), session.sessionId(), session.accessToken()
            );
        }

        public static ExchangeResult failure(String errorMessage, String errorCode) {
            return new ExchangeResult(
                false, false, errorMessage, errorCode,
                null, null, null, null
            );
        }
    }
}
