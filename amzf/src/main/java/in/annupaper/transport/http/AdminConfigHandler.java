package in.annupaper.transport.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.annupaper.config.TrailingStopsConfig;
import in.annupaper.application.service.TrailingStopsConfigService;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * HTTP handler for admin configuration endpoints.
 *
 * Provides REST API for:
 * - GET /api/admin/trailing-stops/config - Get current configuration
 * - POST /api/admin/trailing-stops/config - Update configuration
 */
public final class AdminConfigHandler {
    private static final Logger log = LoggerFactory.getLogger(AdminConfigHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TrailingStopsConfigService configService;

    public AdminConfigHandler(TrailingStopsConfigService configService) {
        this.configService = configService;
    }

    /**
     * GET /api/admin/trailing-stops/config
     *
     * Returns current trailing stops configuration as JSON.
     */
    public void getTrailingStopsConfig(HttpServerExchange exchange) {
        try {
            TrailingStopsConfig config = configService.getConfig();
            String json = MAPPER.writeValueAsString(config);

            exchange.setStatusCode(StatusCodes.OK);
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
            exchange.getResponseSender().send(json, StandardCharsets.UTF_8);

            log.debug("GET /api/admin/trailing-stops/config → 200 OK");

        } catch (Exception e) {
            log.error("Failed to get trailing stops config: {}", e.getMessage(), e);
            sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to get configuration: " + e.getMessage());
        }
    }

    /**
     * POST /api/admin/trailing-stops/config
     *
     * Updates trailing stops configuration from request body JSON.
     */
    public void updateTrailingStopsConfig(HttpServerExchange exchange) {
        exchange.getRequestReceiver().receiveFullString((exch, requestBody) -> {
            try {
                // Parse request body
                TrailingStopsConfig newConfig = MAPPER.readValue(requestBody, TrailingStopsConfig.class);

                // Validate and save
                configService.updateConfig(newConfig);

                // Return success
                exch.setStatusCode(StatusCodes.OK);
                exch.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
                exch.getResponseSender().send("{\"success\":true,\"message\":\"Configuration updated successfully\"}",
                        StandardCharsets.UTF_8);

                log.info("POST /api/admin/trailing-stops/config → 200 OK (activation={}%, trailing={}%)",
                        newConfig.activationPercent(), newConfig.trailingPercent());

            } catch (IllegalArgumentException e) {
                log.warn("Invalid configuration: {}", e.getMessage());
                sendError(exch, StatusCodes.BAD_REQUEST, "Invalid configuration: " + e.getMessage());

            } catch (IOException e) {
                log.error("Failed to save configuration: {}", e.getMessage(), e);
                sendError(exch, StatusCodes.INTERNAL_SERVER_ERROR, "Failed to save configuration: " + e.getMessage());

            } catch (Exception e) {
                log.error("Unexpected error updating configuration: {}", e.getMessage(), e);
                sendError(exch, StatusCodes.INTERNAL_SERVER_ERROR, "Unexpected error: " + e.getMessage());
            }
        }, StandardCharsets.UTF_8);
    }

    /**
     * Send error response.
     */
    private void sendError(HttpServerExchange exchange, int statusCode, String message) {
        exchange.setStatusCode(statusCode);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
        exchange.getResponseSender().send(message, StandardCharsets.UTF_8);
    }
}
