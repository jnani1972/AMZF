package in.annupaper.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record SystemStatusDTO(
                @JsonProperty("broker") BrokerStatus broker,
                @JsonProperty("readiness") ReadinessStatus readiness,
                @JsonProperty("watchlist") WatchlistStatus watchlist,
                @JsonProperty("trades") TradeStatus trades) {
        public record BrokerStatus(
                        String name,
                        String userId,
                        String userBrokerId, // Added for OAuth
                        boolean connected,
                        String message) {
        }

        public record ReadinessStatus(
                        boolean historicalCandles,
                        boolean ltpStream,
                        String message) {
        }

        public record WatchlistStatus(
                        String name,
                        int symbolCount) {
        }

        public record TradeStatus(
                        int activeCount,
                        int userCount,
                        int symbolCount,
                        List<String> userIds,
                        List<String> symbols) {
        }
}
