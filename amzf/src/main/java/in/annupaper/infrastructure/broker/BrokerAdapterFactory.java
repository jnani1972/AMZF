package in.annupaper.infrastructure.broker;

import in.annupaper.infrastructure.broker.adapters.*;
import in.annupaper.domain.model.*;
import in.annupaper.application.port.output.UserBrokerRepository;
import in.annupaper.application.port.output.UserBrokerSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating and managing broker adapters.
 */
public final class BrokerAdapterFactory {
    private static final Logger log = LoggerFactory.getLogger(BrokerAdapterFactory.class);

    // Cached adapter instances per user-broker
    private final Map<String, BrokerAdapter> adapterCache = new ConcurrentHashMap<>();

    // Session repository for loading OAuth tokens
    private final UserBrokerSessionRepository sessionRepo;

    // User-broker repository for loading credentials
    private final UserBrokerRepository userBrokerRepo;

    /**
     * Constructor with repositories for OAuth token and credentials management.
     */
    public BrokerAdapterFactory(UserBrokerSessionRepository sessionRepo, UserBrokerRepository userBrokerRepo) {
        this.sessionRepo = sessionRepo;
        this.userBrokerRepo = userBrokerRepo;
    }

    /**
     * Create FYERS adapter with SDK-first, fallback-to-raw strategy.
     *
     * STRATEGY:
     * 1. Try FyersV3SdkAdapter (official SDK wrapper)
     * 2. If SDK not available or init fails → fallback to FyersAdapter (raw
     * WebSocket)
     *
     * @param userBrokerId User-broker link ID
     * @return FYERS adapter instance (SDK or raw)
     */
    private BrokerAdapter createFyersAdapter(String userBrokerId) {
        // Load credentials to get appId and session
        String appId = null;
        String accessToken = null;
        String sessionId = null;

        try {
            Optional<UserBroker> ubOpt = userBrokerRepo.findById(userBrokerId);
            if (ubOpt.isPresent() && ubOpt.get().credentials() != null) {
                com.fasterxml.jackson.databind.JsonNode creds = ubOpt.get().credentials();
                appId = creds.has("apiKey") ? creds.get("apiKey").asText() : null;
            }

            // Load active session
            Optional<UserBrokerSession> sessionOpt = sessionRepo.findActiveSession(userBrokerId);
            if (sessionOpt.isPresent()) {
                accessToken = sessionOpt.get().accessToken();
                sessionId = sessionOpt.get().sessionId();
            }
        } catch (Exception e) {
            log.warn("[FACTORY] Failed to load FYERS credentials for {}: {}", userBrokerId, e.getMessage());
        }

        // STRATEGY: Try SDK first, fallback to raw
        if (appId != null && accessToken != null && sessionId != null) {
            try {
                log.info("[FACTORY] 🎯 Attempting FYERS SDK adapter (PRIMARY) for {}", userBrokerId);
                FyersV3SdkAdapter sdkAdapter = new FyersV3SdkAdapter(userBrokerId, appId, accessToken, sessionId);

                if (sdkAdapter.initialize()) {
                    log.info("[FACTORY] ✅ Using FYERS SDK adapter (userBrokerId={})", userBrokerId);
                    return sdkAdapter;
                } else {
                    log.info("[FACTORY] ⚠️ SDK adapter init failed - falling back to raw WebSocket adapter");
                }
            } catch (NoClassDefFoundError e) {
                log.debug("[FACTORY] SDK classes not found - falling back to raw adapter: {}", e.getMessage());
            } catch (Exception e) {
                log.warn("[FACTORY] SDK adapter creation failed - falling back to raw adapter: {}", e.getMessage());
            }
        }

        // FALLBACK: Use raw WebSocket adapter
        log.info("[FACTORY] 🔧 Using FYERS raw WebSocket adapter (FALLBACK) for {}", userBrokerId);
        return new FyersAdapter(sessionRepo, userBrokerId);
    }

    /**
     * Create a new adapter instance for a broker code.
     * For Fyers, creates adapter with session repository to load OAuth tokens.
     */
    public BrokerAdapter create(String brokerCode, String userBrokerId) {
        BrokerAdapter adapter = switch (brokerCode.toUpperCase()) {
            case "ZERODHA", "KITE" -> new ZerodhaDataAdapter(sessionRepo, userBrokerId);
            case "FYERS" -> createFyersAdapter(userBrokerId);
            case "DHAN" -> new DhanAdapter();
            case "UPSTOX" -> new UpstoxAdapter();
            case "ALPACA" -> new AlpacaAdapter();
            default -> throw new IllegalArgumentException("Unknown broker: " + brokerCode);
        };

        // For adapters with session management, auto-connect using stored OAuth session
        if ((adapter.getBrokerCode().equals("FYERS") || adapter.getBrokerCode().equals("ZERODHA"))
                && sessionRepo != null && userBrokerRepo != null && userBrokerId != null) {
            try {
                // Load credentials from user_brokers table
                Optional<UserBroker> ubOpt = userBrokerRepo.findById(userBrokerId);

                if (ubOpt.isPresent() && ubOpt.get().credentials() != null) {
                    com.fasterxml.jackson.databind.JsonNode creds = ubOpt.get().credentials();
                    String apiKey = creds.has("apiKey") ? creds.get("apiKey").asText() : null;

                    if (apiKey != null && !apiKey.isEmpty()) {
                        // Attempt to connect using stored session (wait for completion)
                        BrokerAdapter.ConnectionResult result = adapter.connect(
                                new BrokerAdapter.BrokerCredentials(apiKey, null, null, null, null, null)).get();

                        if (result.success()) {
                            log.info("Auto-connected {} adapter for userBrokerId={}", brokerCode, userBrokerId);
                        } else {
                            log.warn("Failed to auto-connect {} adapter for userBrokerId={}: {}",
                                    brokerCode, userBrokerId, result.message());
                        }
                    } else {
                        log.warn("Cannot auto-connect {} adapter for userBrokerId={}: No apiKey in credentials",
                                brokerCode, userBrokerId);
                    }
                } else {
                    log.warn("Cannot auto-connect {} adapter for userBrokerId={}: No credentials found",
                            brokerCode, userBrokerId);
                }
            } catch (Exception e) {
                log.warn("Exception during auto-connect of {} adapter for userBrokerId={}: {}",
                        brokerCode, userBrokerId, e.getMessage());
            }
        }

        return adapter;
    }

    /**
     * Get or create adapter for a user-broker combination.
     * Cached for connection reuse.
     */
    public BrokerAdapter getOrCreate(String userBrokerId, String brokerCode) {
        return adapterCache.computeIfAbsent(userBrokerId, k -> {
            log.info("Creating adapter for userBrokerId={}, broker={}", userBrokerId, brokerCode);
            return create(brokerCode, userBrokerId);
        });
    }

    /**
     * Get cached adapter for user-broker.
     */
    public BrokerAdapter get(String userBrokerId) {
        return adapterCache.get(userBrokerId);
    }

    /**
     * Remove adapter from cache (on disconnect).
     */
    public void remove(String userBrokerId) {
        BrokerAdapter adapter = adapterCache.remove(userBrokerId);
        if (adapter != null) {
            adapter.disconnect();
        }
    }

    /**
     * Disconnect all adapters.
     */
    public void disconnectAll() {
        adapterCache.forEach((id, adapter) -> {
            try {
                adapter.disconnect();
            } catch (Exception e) {
                log.warn("Error disconnecting {}: {}", id, e.getMessage());
            }
        });
        adapterCache.clear();
    }

    /**
     * Reload access token for a specific user-broker adapter.
     * Called when token is refreshed via OAuth.
     * Supports FYERS (SDK and raw) and Zerodha adapters.
     */
    public void reloadToken(String userBrokerId, String newAccessToken, String sessionId) {
        BrokerAdapter adapter = adapterCache.get(userBrokerId);
        if (adapter == null) {
            log.warn("[FACTORY] No cached adapter found for userBrokerId={} - token reload skipped", userBrokerId);
            return;
        }

        // Handle both SDK and raw FYERS adapters
        if (adapter instanceof FyersV3SdkAdapter) {
            log.info("[FACTORY] ⚡ Reloading token for FYERS SDK adapter (userBrokerId={}, session={})",
                    userBrokerId, sessionId);
            ((FyersV3SdkAdapter) adapter).reloadToken(newAccessToken, sessionId);
        } else if (adapter instanceof in.annupaper.infrastructure.broker.adapters.FyersAdapter) {
            log.info("[FACTORY] ⚡ Reloading token for FYERS raw adapter (userBrokerId={}, session={})",
                    userBrokerId, sessionId);
            ((in.annupaper.infrastructure.broker.adapters.FyersAdapter) adapter).reloadToken(newAccessToken, sessionId);
        } else if (adapter instanceof ZerodhaDataAdapter) {
            log.info("[FACTORY] ⚡ Reloading token for Zerodha DATA adapter (userBrokerId={}, session={})",
                    userBrokerId, sessionId);
            ((ZerodhaDataAdapter) adapter).reloadToken(newAccessToken, sessionId);
        } else {
            log.debug("[FACTORY] Adapter for {} is {}, does not support token reload",
                    userBrokerId, adapter.getClass().getSimpleName());
        }
    }

    /**
     * Get supported broker codes.
     */

    // }
    public static String[] getSupportedBrokers() {
        return new String[] { "ZERODHA", "FYERS", "DHAN", "UPSTOX", "ALPACA" };
    }
}
