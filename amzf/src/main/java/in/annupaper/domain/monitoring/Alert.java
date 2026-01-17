package in.annupaper.domain.monitoring;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a system alert with severity level and contextual details.
 */
public final class Alert {
    private final String alertType;
    private final AlertLevel level;
    private final String message;
    private final Instant timestamp;
    private final Map<String, Object> details;

    private Alert(String alertType, AlertLevel level, String message, Instant timestamp, Map<String, Object> details) {
        this.alertType = alertType;
        this.level = level;
        this.message = message;
        this.timestamp = timestamp;
        this.details = new HashMap<>(details);
    }

    public String getAlertType() {
        return alertType;
    }

    public AlertLevel getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Map<String, Object> getDetails() {
        return new HashMap<>(details);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String alertType;
        private AlertLevel level;
        private String message;
        private Instant timestamp = Instant.now();
        private Map<String, Object> details = new HashMap<>();

        public Builder alertType(String alertType) {
            this.alertType = alertType;
            return this;
        }

        public Builder level(AlertLevel level) {
            this.level = level;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder detail(String key, Object value) {
            this.details.put(key, value);
            return this;
        }

        public Builder details(Map<String, Object> details) {
            this.details.putAll(details);
            return this;
        }

        public Alert build() {
            if (alertType == null || level == null || message == null) {
                throw new IllegalStateException("alertType, level, and message are required");
            }
            return new Alert(alertType, level, message, timestamp, details);
        }
    }
}
