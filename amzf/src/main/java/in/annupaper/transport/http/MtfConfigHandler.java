package in.annupaper.transport.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.annupaper.service.MtfConfigService;
import io.undertow.server.HttpServerExchange;
import in.annupaper.application.port.output.MtfConfigRepository;
import in.annupaper.domain.model.MtfGlobalConfig;
import in.annupaper.domain.model.MtfSymbolConfig;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * HTTP handler for MTF configuration management.
 * Provides REST API for global and symbol-specific MTF configuration.
 */
public final class MtfConfigHandler {
    private static final Logger log = LoggerFactory.getLogger(MtfConfigHandler.class);

    private final MtfConfigService mtfConfigService;
    private final Function<String, String> tokenValidator;
    private final ObjectMapper objectMapper;

    public MtfConfigHandler(MtfConfigService mtfConfigService, Function<String, String> tokenValidator) {
        this.mtfConfigService = mtfConfigService;
        this.tokenValidator = tokenValidator;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * GET /api/admin/mtf-config - Get global configuration
     */
    public void getGlobalConfig(HttpServerExchange exchange) {
        if (!isAuthorized(exchange)) {
            sendUnauthorized(exchange);
            return;
        }

        try {
            MtfGlobalConfig config = mtfConfigService.getGlobalConfig()
                    .orElseThrow(() -> new RuntimeException("Global config not found"));

            Map<String, Object> response = buildGlobalConfigResponse(config);
            sendJson(exchange, response);

        } catch (Exception e) {
            log.error("Failed to get global config: {}", e.getMessage());
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to get global config");
        }
    }

    /**
     * PUT /api/admin/mtf-config - Update global configuration
     */
    public void updateGlobalConfig(HttpServerExchange exchange) {
        if (!isAuthorized(exchange)) {
            sendUnauthorized(exchange);
            return;
        }

        exchange.getRequestReceiver().receiveFullBytes((ex, data) -> {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> request = objectMapper.readValue(data, Map.class);

                MtfGlobalConfig existing = mtfConfigService.getGlobalConfig()
                        .orElseThrow(() -> new RuntimeException("Global config not found"));

                MtfGlobalConfig updated = buildGlobalConfigFromRequest(existing, request);
                mtfConfigService.updateGlobalConfig(updated);

                sendJson(ex, Map.of(
                        "success", true,
                        "message", "Global config updated successfully"));

            } catch (Exception e) {
                log.error("Failed to update global config: {}", e.getMessage());
                sendError(ex, StatusCodes.BAD_REQUEST, "Failed to update global config: " + e.getMessage());
            }
        });
    }

    /**
     * GET /api/admin/mtf-config/symbols - Get all symbol configurations
     */
    public void getAllSymbolConfigs(HttpServerExchange exchange) {
        if (!isAuthorized(exchange)) {
            sendUnauthorized(exchange);
            return;
        }

        try {
            List<MtfSymbolConfig> configs = mtfConfigService.getAllSymbolConfigs();
            List<Map<String, Object>> response = configs.stream()
                    .map(this::buildSymbolConfigResponse)
                    .toList();

            sendJson(exchange, Map.of("symbols", response));

        } catch (Exception e) {
            log.error("Failed to get all symbol configs: {}", e.getMessage());
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to get symbol configs");
        }
    }

    /**
     * GET /api/admin/mtf-config/symbols/{symbol} - Get symbol configuration
     */
    public void getSymbolConfig(HttpServerExchange exchange) {
        if (!isAuthorized(exchange)) {
            sendUnauthorized(exchange);
            return;
        }

        String symbol = exchange.getQueryParameters().get("symbol").getFirst();
        String userBrokerId = exchange.getQueryParameters().get("userBrokerId").getFirst();

        try {
            MtfSymbolConfig config = mtfConfigService.getSymbolConfig(symbol, userBrokerId)
                    .orElse(null);

            if (config == null) {
                sendJson(exchange, Map.of("exists", false));
            } else {
                Map<String, Object> response = buildSymbolConfigResponse(config);
                response.put("exists", true);
                sendJson(exchange, response);
            }

        } catch (Exception e) {
            log.error("Failed to get symbol config: {}", e.getMessage());
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to get symbol config");
        }
    }

    /**
     * PUT /api/admin/mtf-config/symbols/{symbol} - Upsert symbol configuration
     */
    public void upsertSymbolConfig(HttpServerExchange exchange) {
        if (!isAuthorized(exchange)) {
            sendUnauthorized(exchange);
            return;
        }

        String symbol = exchange.getQueryParameters().get("symbol").getFirst();

        exchange.getRequestReceiver().receiveFullBytes((ex, data) -> {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> request = objectMapper.readValue(data, Map.class);

                String userBrokerId = (String) request.get("userBrokerId");
                MtfSymbolConfig config = buildSymbolConfigFromRequest(symbol, userBrokerId, request);

                mtfConfigService.upsertSymbolConfig(config);

                sendJson(ex, Map.of(
                        "success", true,
                        "message", "Symbol config saved successfully"));

            } catch (Exception e) {
                log.error("Failed to upsert symbol config: {}", e.getMessage());
                sendError(ex, StatusCodes.BAD_REQUEST, "Failed to save symbol config: " + e.getMessage());
            }
        });
    }

    /**
     * DELETE /api/admin/mtf-config/symbols/{symbol} - Delete symbol configuration
     */
    public void deleteSymbolConfig(HttpServerExchange exchange) {
        if (!isAuthorized(exchange)) {
            sendUnauthorized(exchange);
            return;
        }

        String symbol = exchange.getQueryParameters().get("symbol").getFirst();
        String userBrokerId = exchange.getQueryParameters().get("userBrokerId").getFirst();

        try {
            mtfConfigService.deleteSymbolConfig(symbol, userBrokerId);

            sendJson(exchange, Map.of(
                    "success", true,
                    "message", "Symbol config deleted successfully"));

        } catch (Exception e) {
            log.error("Failed to delete symbol config: {}", e.getMessage());
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to delete symbol config");
        }
    }

    // Helper methods
    private boolean isAuthorized(HttpServerExchange exchange) {
        String token = extractToken(exchange);
        String userId = tokenValidator.apply(token);
        return userId != null;
    }

    private String extractToken(HttpServerExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    private void sendJson(HttpServerExchange exchange, Object data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
            exchange.getResponseSender().send(json);
        } catch (Exception e) {
            log.error("Failed to send JSON response: {}", e.getMessage());
            exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
            exchange.getResponseSender().send("{\"error\":\"Internal server error\"}");
        }
    }

    private void sendUnauthorized(HttpServerExchange exchange) {
        exchange.setStatusCode(StatusCodes.UNAUTHORIZED);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
        exchange.getResponseSender().send("{\"error\":\"Unauthorized\"}");
    }

    private void sendError(HttpServerExchange exchange, int statusCode, String message) {
        exchange.setStatusCode(statusCode);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
        exchange.getResponseSender().send(String.format("{\"error\":\"%s\"}", message));
    }

    private Map<String, Object> buildGlobalConfigResponse(MtfGlobalConfig config) {
        Map<String, Object> map = new HashMap<>();
        map.put("configId", config.configId());
        map.put("htfCandleCount", config.htfCandleCount());
        map.put("htfCandleMinutes", config.htfCandleMinutes());
        map.put("htfWeight", config.htfWeight());
        map.put("itfCandleCount", config.itfCandleCount());
        map.put("itfCandleMinutes", config.itfCandleMinutes());
        map.put("itfWeight", config.itfWeight());
        map.put("ltfCandleCount", config.ltfCandleCount());
        map.put("ltfCandleMinutes", config.ltfCandleMinutes());
        map.put("ltfWeight", config.ltfWeight());
        map.put("buyZonePct", config.buyZonePct());
        map.put("minConfluenceType", config.minConfluenceType());
        map.put("strengthThresholdVeryStrong", config.strengthThresholdVeryStrong());
        map.put("strengthThresholdStrong", config.strengthThresholdStrong());
        map.put("strengthThresholdModerate", config.strengthThresholdModerate());
        map.put("multiplierVeryStrong", config.multiplierVeryStrong());
        map.put("multiplierStrong", config.multiplierStrong());
        map.put("multiplierModerate", config.multiplierModerate());
        map.put("multiplierWeak", config.multiplierWeak());
        map.put("maxPositionLogLoss", config.maxPositionLogLoss());
        map.put("maxPortfolioLogLoss", config.maxPortfolioLogLoss());
        map.put("kellyFraction", config.kellyFraction());
        map.put("maxKellyMultiplier", config.maxKellyMultiplier());
        map.put("useLimitOrders", config.useLimitOrders());
        map.put("entryOffsetPct", config.entryOffsetPct());
        map.put("minProfitPct", config.minProfitPct());
        map.put("targetRMultiple", config.targetRMultiple());
        map.put("stretchRMultiple", config.stretchRMultiple());
        map.put("useTrailingStop", config.useTrailingStop());
        map.put("trailingStopActivationPct", config.trailingStopActivationPct());
        map.put("trailingStopDistancePct", config.trailingStopDistancePct());
        return map;
    }

    private Map<String, Object> buildSymbolConfigResponse(MtfSymbolConfig config) {
        Map<String, Object> map = new HashMap<>();
        map.put("symbolConfigId", config.symbolConfigId());
        map.put("symbol", config.symbol());
        map.put("userBrokerId", config.userBrokerId());
        map.put("htfCandleCount", config.htfCandleCount());
        map.put("htfCandleMinutes", config.htfCandleMinutes());
        map.put("htfWeight", config.htfWeight());
        map.put("itfCandleCount", config.itfCandleCount());
        map.put("itfCandleMinutes", config.itfCandleMinutes());
        map.put("itfWeight", config.itfWeight());
        map.put("ltfCandleCount", config.ltfCandleCount());
        map.put("ltfCandleMinutes", config.ltfCandleMinutes());
        map.put("ltfWeight", config.ltfWeight());
        map.put("buyZonePct", config.buyZonePct());
        map.put("minConfluenceType", config.minConfluenceType());
        map.put("strengthThresholdVeryStrong", config.strengthThresholdVeryStrong());
        map.put("strengthThresholdStrong", config.strengthThresholdStrong());
        map.put("strengthThresholdModerate", config.strengthThresholdModerate());
        map.put("multiplierVeryStrong", config.multiplierVeryStrong());
        map.put("multiplierStrong", config.multiplierStrong());
        map.put("multiplierModerate", config.multiplierModerate());
        map.put("multiplierWeak", config.multiplierWeak());
        map.put("maxPositionLogLoss", config.maxPositionLogLoss());
        map.put("maxPortfolioLogLoss", config.maxPortfolioLogLoss());
        map.put("kellyFraction", config.kellyFraction());
        map.put("maxKellyMultiplier", config.maxKellyMultiplier());
        map.put("useLimitOrders", config.useLimitOrders());
        map.put("entryOffsetPct", config.entryOffsetPct());
        map.put("minProfitPct", config.minProfitPct());
        map.put("targetRMultiple", config.targetRMultiple());
        map.put("stretchRMultiple", config.stretchRMultiple());
        map.put("useTrailingStop", config.useTrailingStop());
        map.put("trailingStopActivationPct", config.trailingStopActivationPct());
        map.put("trailingStopDistancePct", config.trailingStopDistancePct());
        return map;
    }

    private MtfGlobalConfig buildGlobalConfigFromRequest(MtfGlobalConfig existing, Map<String, Object> request) {
        return new MtfGlobalConfig(
                existing.configId(),
                getIntOrDefault(request, "htfCandleCount", existing.htfCandleCount()),
                getIntOrDefault(request, "htfCandleMinutes", existing.htfCandleMinutes()),
                getBigDecimalOrDefault(request, "htfWeight", existing.htfWeight()),
                getIntOrDefault(request, "itfCandleCount", existing.itfCandleCount()),
                getIntOrDefault(request, "itfCandleMinutes", existing.itfCandleMinutes()),
                getBigDecimalOrDefault(request, "itfWeight", existing.itfWeight()),
                getIntOrDefault(request, "ltfCandleCount", existing.ltfCandleCount()),
                getIntOrDefault(request, "ltfCandleMinutes", existing.ltfCandleMinutes()),
                getBigDecimalOrDefault(request, "ltfWeight", existing.ltfWeight()),
                getBigDecimalOrDefault(request, "buyZonePct", existing.buyZonePct()),
                getBigDecimalOrDefault(request, "htfBuyZonePct", existing.htfBuyZonePct()),
                getBigDecimalOrDefault(request, "itfBuyZonePct", existing.itfBuyZonePct()),
                getBigDecimalOrDefault(request, "ltfBuyZonePct", existing.ltfBuyZonePct()),
                getStringOrDefault(request, "minConfluenceType", existing.minConfluenceType()),
                getBigDecimalOrDefault(request, "strengthThresholdVeryStrong", existing.strengthThresholdVeryStrong()),
                getBigDecimalOrDefault(request, "strengthThresholdStrong", existing.strengthThresholdStrong()),
                getBigDecimalOrDefault(request, "strengthThresholdModerate", existing.strengthThresholdModerate()),
                getBigDecimalOrDefault(request, "multiplierVeryStrong", existing.multiplierVeryStrong()),
                getBigDecimalOrDefault(request, "multiplierStrong", existing.multiplierStrong()),
                getBigDecimalOrDefault(request, "multiplierModerate", existing.multiplierModerate()),
                getBigDecimalOrDefault(request, "multiplierWeak", existing.multiplierWeak()),
                getBigDecimalOrDefault(request, "maxPositionLogLoss", existing.maxPositionLogLoss()),
                getBigDecimalOrDefault(request, "maxPortfolioLogLoss", existing.maxPortfolioLogLoss()),
                getBigDecimalOrDefault(request, "maxSymbolLogLoss", existing.maxSymbolLogLoss()),
                getBigDecimalOrDefault(request, "kellyFraction", existing.kellyFraction()),
                getBigDecimalOrDefault(request, "maxKellyMultiplier", existing.maxKellyMultiplier()),
                getBooleanOrDefault(request, "useLimitOrders", existing.useLimitOrders()),
                getBigDecimalOrDefault(request, "entryOffsetPct", existing.entryOffsetPct()),
                getBigDecimalOrDefault(request, "minProfitPct", existing.minProfitPct()),
                getBigDecimalOrDefault(request, "targetRMultiple", existing.targetRMultiple()),
                getBigDecimalOrDefault(request, "stretchRMultiple", existing.stretchRMultiple()),
                getBooleanOrDefault(request, "useTrailingStop", existing.useTrailingStop()),
                getBigDecimalOrDefault(request, "trailingStopActivationPct", existing.trailingStopActivationPct()),
                getBigDecimalOrDefault(request, "trailingStopDistancePct", existing.trailingStopDistancePct()),
                getBigDecimalOrDefault(request, "minReentrySpacingAtrMultiplier",
                        existing.minReentrySpacingAtrMultiplier()),
                getBigDecimalOrDefault(request, "rangeAtrThresholdWide", existing.rangeAtrThresholdWide()),
                getBigDecimalOrDefault(request, "rangeAtrThresholdHealthy", existing.rangeAtrThresholdHealthy()),
                getBigDecimalOrDefault(request, "rangeAtrThresholdTight", existing.rangeAtrThresholdTight()),
                getBigDecimalOrDefault(request, "velocityMultiplierWide", existing.velocityMultiplierWide()),
                getBigDecimalOrDefault(request, "velocityMultiplierHealthy", existing.velocityMultiplierHealthy()),
                getBigDecimalOrDefault(request, "velocityMultiplierTight", existing.velocityMultiplierTight()),
                getBigDecimalOrDefault(request, "velocityMultiplierCompressed",
                        existing.velocityMultiplierCompressed()),
                getBigDecimalOrDefault(request, "bodyRatioThresholdLow", existing.bodyRatioThresholdLow()),
                getBigDecimalOrDefault(request, "bodyRatioThresholdCritical", existing.bodyRatioThresholdCritical()),
                getBigDecimalOrDefault(request, "bodyRatioPenaltyLow", existing.bodyRatioPenaltyLow()),
                getBigDecimalOrDefault(request, "bodyRatioPenaltyCritical", existing.bodyRatioPenaltyCritical()),
                getIntOrDefault(request, "rangeLookbackBars", existing.rangeLookbackBars()),
                getBooleanOrDefault(request, "stressThrottleEnabled", existing.stressThrottleEnabled()),
                getBigDecimalOrDefault(request, "maxStressDrawdown", existing.maxStressDrawdown()),
                getBigDecimalOrDefault(request, "utilityAlpha", existing.utilityAlpha()),
                getBigDecimalOrDefault(request, "utilityBeta", existing.utilityBeta()),
                getBigDecimalOrDefault(request, "utilityLambda", existing.utilityLambda()),
                getBigDecimalOrDefault(request, "minAdvantageRatio", existing.minAdvantageRatio()),
                getBooleanOrDefault(request, "utilityGateEnabled", existing.utilityGateEnabled()),
                existing.createdAt(),
                Instant.now());
    }

    private MtfSymbolConfig buildSymbolConfigFromRequest(String symbol, String userBrokerId,
            Map<String, Object> request) {
        return new MtfSymbolConfig(
                UUID.randomUUID().toString(),
                symbol,
                userBrokerId,
                getIntOrNull(request, "htfCandleCount"),
                getIntOrNull(request, "htfCandleMinutes"),
                getBigDecimalOrNull(request, "htfWeight"),
                getIntOrNull(request, "itfCandleCount"),
                getIntOrNull(request, "itfCandleMinutes"),
                getBigDecimalOrNull(request, "itfWeight"),
                getIntOrNull(request, "ltfCandleCount"),
                getIntOrNull(request, "ltfCandleMinutes"),
                getBigDecimalOrNull(request, "ltfWeight"),
                getBigDecimalOrNull(request, "buyZonePct"),
                getStringOrNull(request, "minConfluenceType"),
                getBigDecimalOrNull(request, "strengthThresholdVeryStrong"),
                getBigDecimalOrNull(request, "strengthThresholdStrong"),
                getBigDecimalOrNull(request, "strengthThresholdModerate"),
                getBigDecimalOrNull(request, "multiplierVeryStrong"),
                getBigDecimalOrNull(request, "multiplierStrong"),
                getBigDecimalOrNull(request, "multiplierModerate"),
                getBigDecimalOrNull(request, "multiplierWeak"),
                getBigDecimalOrNull(request, "maxPositionLogLoss"),
                getBigDecimalOrNull(request, "maxPortfolioLogLoss"),
                getBigDecimalOrNull(request, "kellyFraction"),
                getBigDecimalOrNull(request, "maxKellyMultiplier"),
                getBooleanOrNull(request, "useLimitOrders"),
                getBigDecimalOrNull(request, "entryOffsetPct"),
                getBigDecimalOrNull(request, "minProfitPct"),
                getBigDecimalOrNull(request, "targetRMultiple"),
                getBigDecimalOrNull(request, "stretchRMultiple"),
                getBooleanOrNull(request, "useTrailingStop"),
                getBigDecimalOrNull(request, "trailingStopActivationPct"),
                getBigDecimalOrNull(request, "trailingStopDistancePct"),
                Instant.now(),
                Instant.now());
    }

    private int getIntOrDefault(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        return value != null ? ((Number) value).intValue() : defaultValue;
    }

    private Integer getIntOrNull(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? ((Number) value).intValue() : null;
    }

    private BigDecimal getBigDecimalOrDefault(Map<String, Object> map, String key, BigDecimal defaultValue) {
        Object value = map.get(key);
        return value != null ? new BigDecimal(value.toString()) : defaultValue;
    }

    private BigDecimal getBigDecimalOrNull(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? new BigDecimal(value.toString()) : null;
    }

    private boolean getBooleanOrDefault(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        return value != null ? (Boolean) value : defaultValue;
    }

    private Boolean getBooleanOrNull(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? (Boolean) value : null;
    }

    private String getStringOrDefault(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private String getStringOrNull(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
}
