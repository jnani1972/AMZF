package in.annupaper.infrastructure.broker;

import in.annupaper.infrastructure.broker.common.InstrumentMapper;
import in.annupaper.infrastructure.broker.data.*;
import in.annupaper.infrastructure.broker.order.*;
import in.annupaper.repository.UserBrokerSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BrokerFactory - Central factory for creating and managing broker instances.
 *
 * Supports dual-broker architecture:
 * - DataBroker: Real-time ticks, historical data, instrument master
 * - OrderBroker: Order placement, modification, cancellation, tracking
 *
 * Configuration:
 * - DATA_FEED_BROKER: Broker for market data (ZERODHA, FYERS, MOCK)
 * - ORDER_BROKER: Broker for order execution (FYERS, ZERODHA, MOCK)
 * - Default: ZERODHA for data, FYERS for orders
 *
 * Features:
 * - Instance caching: Reuses connections across services
 * - Configuration validation: Validates broker combinations at startup
 * - Graceful degradation: Falls back to MOCK brokers on failure
 * - Environment variable support: Configurable via env vars
 *
 * Usage:
 * <pre>
 * BrokerFactory factory = new BrokerFactory(sessionRepo);
 * DataBroker dataBroker = factory.createDataBroker(userBrokerId);
 * OrderBroker orderBroker = factory.createOrderBroker(userBrokerId);
 * </pre>
 */
public class BrokerFactory {
    private static final Logger log = LoggerFactory.getLogger(BrokerFactory.class);

    // Configuration keys
    private static final String DATA_FEED_BROKER_KEY = "DATA_FEED_BROKER";
    private static final String ORDER_BROKER_KEY = "ORDER_BROKER";

    // Default broker configuration
    private static final String DEFAULT_DATA_BROKER = "ZERODHA";
    private static final String DEFAULT_ORDER_BROKER = "FYERS";

    // Broker credentials configuration
    private static final String ZERODHA_API_KEY = System.getenv("ZERODHA_API_KEY");
    private static final String FYERS_APP_ID = System.getenv("FYERS_APP_ID");
    private static final String UPSTOX_API_KEY = System.getenv("UPSTOX_API_KEY");
    private static final String DHAN_CLIENT_ID = System.getenv("DHAN_CLIENT_ID");

    private final UserBrokerSessionRepository sessionRepo;
    private final InstrumentMapper instrumentMapper;
    private final in.annupaper.infrastructure.broker.metrics.BrokerMetrics metrics;

    // Instance caches (per userBrokerId)
    private final Map<String, DataBroker> dataBrokerCache = new ConcurrentHashMap<>();
    private final Map<String, OrderBroker> orderBrokerCache = new ConcurrentHashMap<>();

    // Configuration state
    private final String dataFeedBrokerType;
    private final String orderBrokerType;

    /**
     * Constructor with session repository for token management.
     *
     * @param sessionRepo Session repository for loading access tokens
     * @param metrics Metrics collector (nullable)
     */
    public BrokerFactory(UserBrokerSessionRepository sessionRepo, in.annupaper.infrastructure.broker.metrics.BrokerMetrics metrics) {
        this.sessionRepo = sessionRepo;
        this.instrumentMapper = new InstrumentMapper();
        this.metrics = metrics;

        // Load configuration from environment variables
        this.dataFeedBrokerType = System.getenv().getOrDefault(DATA_FEED_BROKER_KEY, DEFAULT_DATA_BROKER);
        this.orderBrokerType = System.getenv().getOrDefault(ORDER_BROKER_KEY, DEFAULT_ORDER_BROKER);

        // Validate configuration at startup
        validateConfiguration();

        log.info("[BrokerFactory] Initialized with configuration:");
        log.info("[BrokerFactory]   Data Feed Broker: {}", dataFeedBrokerType);
        log.info("[BrokerFactory]   Order Broker: {}", orderBrokerType);
        log.info("[BrokerFactory]   Zerodha API Key: {}", ZERODHA_API_KEY != null ? "configured" : "missing");
        log.info("[BrokerFactory]   FYERS App ID: {}", FYERS_APP_ID != null ? "configured" : "missing");
        log.info("[BrokerFactory]   Upstox API Key: {}", UPSTOX_API_KEY != null ? "configured" : "missing");
        log.info("[BrokerFactory]   Dhan Client ID: {}", DHAN_CLIENT_ID != null ? "configured" : "missing");
    }

    /**
     * Create or retrieve cached DataBroker instance for a user.
     *
     * @param userBrokerId UserBroker ID linking user to broker
     * @return DataBroker instance (cached or newly created)
     */
    public DataBroker createDataBroker(String userBrokerId) {
        return dataBrokerCache.computeIfAbsent(userBrokerId, id -> {
            log.info("[BrokerFactory] Creating DataBroker for user {} using {}", id, dataFeedBrokerType);

            try {
                return switch (dataFeedBrokerType.toUpperCase()) {
                    case "ZERODHA" -> createZerodhaDataBroker(id);
                    case "FYERS" -> createFyersDataBroker(id);
                    case "MOCK" -> createMockDataBroker(id);
                    default -> {
                        log.error("[BrokerFactory] Unknown data broker type: {}. Falling back to MOCK.", dataFeedBrokerType);
                        yield createMockDataBroker(id);
                    }
                };
            } catch (Exception e) {
                log.error("[BrokerFactory] Failed to create DataBroker for {}: {}. Falling back to MOCK.",
                         id, e.getMessage());
                return createMockDataBroker(id);
            }
        });
    }

    /**
     * Create or retrieve cached OrderBroker instance for a user.
     *
     * @param userBrokerId UserBroker ID linking user to broker
     * @return OrderBroker instance (cached or newly created)
     */
    public OrderBroker createOrderBroker(String userBrokerId) {
        return orderBrokerCache.computeIfAbsent(userBrokerId, id -> {
            log.info("[BrokerFactory] Creating OrderBroker for user {} using {}", id, orderBrokerType);

            try {
                return switch (orderBrokerType.toUpperCase()) {
                    case "FYERS" -> createFyersOrderBroker(id);
                    case "ZERODHA" -> createZerodhaOrderBroker(id);
                    case "UPSTOX" -> createUpstoxOrderBroker(id);
                    case "DHAN" -> createDhanOrderBroker(id);
                    case "MOCK" -> createMockOrderBroker(id);
                    default -> {
                        log.error("[BrokerFactory] Unknown order broker type: {}. Falling back to MOCK.", orderBrokerType);
                        yield createMockOrderBroker(id);
                    }
                };
            } catch (Exception e) {
                log.error("[BrokerFactory] Failed to create OrderBroker for {}: {}. Falling back to MOCK.",
                         id, e.getMessage());
                return createMockOrderBroker(id);
            }
        });
    }

    /**
     * Clear cached broker instances for a user.
     * Useful when tokens are refreshed or user logs out.
     *
     * @param userBrokerId UserBroker ID
     */
    public void clearCache(String userBrokerId) {
        log.info("[BrokerFactory] Clearing broker cache for user {}", userBrokerId);

        DataBroker dataBroker = dataBrokerCache.remove(userBrokerId);
        if (dataBroker != null) {
            dataBroker.disconnect();
        }

        OrderBroker orderBroker = orderBrokerCache.remove(userBrokerId);
        if (orderBroker != null) {
            orderBroker.disconnect();
        }
    }

    /**
     * Clear all cached broker instances.
     * Useful during application shutdown.
     */
    public void clearAllCaches() {
        log.info("[BrokerFactory] Clearing all broker caches");

        dataBrokerCache.values().forEach(broker -> {
            try {
                broker.disconnect();
            } catch (Exception e) {
                log.warn("[BrokerFactory] Error disconnecting DataBroker: {}", e.getMessage());
            }
        });
        dataBrokerCache.clear();

        orderBrokerCache.values().forEach(broker -> {
            try {
                broker.disconnect();
            } catch (Exception e) {
                log.warn("[BrokerFactory] Error disconnecting OrderBroker: {}", e.getMessage());
            }
        });
        orderBrokerCache.clear();
    }

    /**
     * Get current configuration.
     *
     * @return Map of configuration keys to values
     */
    public Map<String, String> getConfiguration() {
        return Map.of(
            DATA_FEED_BROKER_KEY, dataFeedBrokerType,
            ORDER_BROKER_KEY, orderBrokerType,
            "ZERODHA_API_KEY_CONFIGURED", String.valueOf(ZERODHA_API_KEY != null),
            "FYERS_APP_ID_CONFIGURED", String.valueOf(FYERS_APP_ID != null),
            "UPSTOX_API_KEY_CONFIGURED", String.valueOf(UPSTOX_API_KEY != null),
            "DHAN_CLIENT_ID_CONFIGURED", String.valueOf(DHAN_CLIENT_ID != null)
        );
    }

    /**
     * Check if broker combination is supported.
     *
     * @param dataBrokerType Data broker type
     * @param orderBrokerType Order broker type
     * @return true if combination is supported
     */
    public boolean isCombinationSupported(String dataBrokerType, String orderBrokerType) {
        // All combinations are supported (including MOCK)
        return true;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PRIVATE FACTORY METHODS
    // ═══════════════════════════════════════════════════════════════════════

    private DataBroker createZerodhaDataBroker(String userBrokerId) {
        if (ZERODHA_API_KEY == null || ZERODHA_API_KEY.isEmpty()) {
            log.error("[BrokerFactory] ZERODHA_API_KEY not configured. Cannot create ZerodhaDataBroker.");
            throw new IllegalStateException("ZERODHA_API_KEY environment variable not set");
        }

        log.info("[BrokerFactory] Creating ZerodhaDataBroker for user {}", userBrokerId);
        return new ZerodhaDataBroker(sessionRepo, userBrokerId, ZERODHA_API_KEY);
    }

    private DataBroker createFyersDataBroker(String userBrokerId) {
        // Note: FYERS is primarily for orders, but can also provide data
        // For now, this is a placeholder - would need FyersDataBroker implementation
        log.warn("[BrokerFactory] FyersDataBroker not yet implemented. Using MockDataBroker.");
        return createMockDataBroker(userBrokerId);
    }

    private DataBroker createMockDataBroker(String userBrokerId) {
        log.info("[BrokerFactory] Creating MockDataBroker for user {}", userBrokerId);
        return MockDataBroker.builder()
            .brokerCode("MOCK_DATA")
            .userBrokerId(userBrokerId)
            .tickIntervalMillis(1000)  // 1 tick per second
            .build();
    }

    private OrderBroker createFyersOrderBroker(String userBrokerId) {
        if (FYERS_APP_ID == null || FYERS_APP_ID.isEmpty()) {
            log.error("[BrokerFactory] FYERS_APP_ID not configured. Cannot create FyersOrderBroker.");
            throw new IllegalStateException("FYERS_APP_ID environment variable not set");
        }

        log.info("[BrokerFactory] Creating FyersOrderBroker for user {}", userBrokerId);
        return new FyersOrderBroker(sessionRepo, userBrokerId, FYERS_APP_ID);
    }

    private OrderBroker createZerodhaOrderBroker(String userBrokerId) {
        if (ZERODHA_API_KEY == null || ZERODHA_API_KEY.isEmpty()) {
            log.error("[BrokerFactory] ZERODHA_API_KEY not configured. Cannot create ZerodhaOrderBroker.");
            throw new IllegalStateException("ZERODHA_API_KEY environment variable not set");
        }

        log.info("[BrokerFactory] Creating ZerodhaOrderBroker for user {}", userBrokerId);
        return new ZerodhaOrderBroker(sessionRepo, instrumentMapper, userBrokerId, ZERODHA_API_KEY);
    }

    private OrderBroker createUpstoxOrderBroker(String userBrokerId) {
        if (UPSTOX_API_KEY == null || UPSTOX_API_KEY.isEmpty()) {
            log.error("[BrokerFactory] UPSTOX_API_KEY not configured. Cannot create UpstoxOrderBroker.");
            throw new IllegalStateException("UPSTOX_API_KEY environment variable not set");
        }

        log.info("[BrokerFactory] Creating UpstoxOrderBroker for user {}", userBrokerId);
        return new UpstoxOrderBroker(sessionRepo, instrumentMapper, metrics, userBrokerId, UPSTOX_API_KEY);
    }

    private OrderBroker createDhanOrderBroker(String userBrokerId) {
        if (DHAN_CLIENT_ID == null || DHAN_CLIENT_ID.isEmpty()) {
            log.error("[BrokerFactory] DHAN_CLIENT_ID not configured. Cannot create DhanOrderBroker.");
            throw new IllegalStateException("DHAN_CLIENT_ID environment variable not set");
        }

        log.info("[BrokerFactory] Creating DhanOrderBroker for user {}", userBrokerId);
        return new DhanOrderBroker(sessionRepo, instrumentMapper, userBrokerId, DHAN_CLIENT_ID);
    }

    private OrderBroker createMockOrderBroker(String userBrokerId) {
        log.info("[BrokerFactory] Creating MockOrderBroker for user {}", userBrokerId);
        return MockOrderBroker.builder()
            .brokerCode("MOCK_ORDER")
            .userBrokerId(userBrokerId)
            .fillDelayMillis(100)  // Fast fills for testing
            .build();
    }

    /**
     * Validate configuration at startup.
     * Logs warnings for invalid or unsupported configurations.
     */
    private void validateConfiguration() {
        log.info("[BrokerFactory] Validating broker configuration...");

        // Validate data broker configuration
        if (dataFeedBrokerType.equalsIgnoreCase("ZERODHA") && ZERODHA_API_KEY == null) {
            log.warn("[BrokerFactory] ⚠️  ZERODHA selected for data but ZERODHA_API_KEY not configured.");
            log.warn("[BrokerFactory]    Will fall back to MOCK broker on first use.");
        }

        if (dataFeedBrokerType.equalsIgnoreCase("FYERS") && FYERS_APP_ID == null) {
            log.warn("[BrokerFactory] ⚠️  FYERS selected for data but FYERS_APP_ID not configured.");
            log.warn("[BrokerFactory]    Will fall back to MOCK broker on first use.");
        }

        // Validate order broker configuration
        if (orderBrokerType.equalsIgnoreCase("FYERS") && FYERS_APP_ID == null) {
            log.warn("[BrokerFactory] ⚠️  FYERS selected for orders but FYERS_APP_ID not configured.");
            log.warn("[BrokerFactory]    Will fall back to MOCK broker on first use.");
        }

        if (orderBrokerType.equalsIgnoreCase("ZERODHA") && ZERODHA_API_KEY == null) {
            log.warn("[BrokerFactory] ⚠️  ZERODHA selected for orders but ZERODHA_API_KEY not configured.");
            log.warn("[BrokerFactory]    Will fall back to MOCK broker on first use.");
        }

        if (orderBrokerType.equalsIgnoreCase("UPSTOX") && UPSTOX_API_KEY == null) {
            log.warn("[BrokerFactory] ⚠️  UPSTOX selected for orders but UPSTOX_API_KEY not configured.");
            log.warn("[BrokerFactory]    Will fall back to MOCK broker on first use.");
        }

        if (orderBrokerType.equalsIgnoreCase("DHAN") && DHAN_CLIENT_ID == null) {
            log.warn("[BrokerFactory] ⚠️  DHAN selected for orders but DHAN_CLIENT_ID not configured.");
            log.warn("[BrokerFactory]    Will fall back to MOCK broker on first use.");
        }

        // Log recommended configuration
        if (dataFeedBrokerType.equalsIgnoreCase("ZERODHA") && orderBrokerType.equalsIgnoreCase("FYERS")) {
            log.info("[BrokerFactory] ✅ Using recommended configuration: ZERODHA (data) + FYERS (orders)");
        }

        // Warn about MOCK usage in production
        if (dataFeedBrokerType.equalsIgnoreCase("MOCK")) {
            log.warn("[BrokerFactory] ⚠️  Using MOCK data broker. Not suitable for production!");
        }

        if (orderBrokerType.equalsIgnoreCase("MOCK")) {
            log.warn("[BrokerFactory] ⚠️  Using MOCK order broker. Not suitable for production!");
        }

        log.info("[BrokerFactory] Configuration validation complete.");
    }
}
