package in.annupaper.application.monitoring;

import in.annupaper.domain.monitoring.Alert;
import in.annupaper.domain.monitoring.AlertLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Alert notification service.
 *
 * Sends alerts to configured channels (logs, Slack, email, PagerDuty, etc.).
 * Currently logs to SLF4J - can be extended to integrate with external services.
 */
public final class AlertService {
    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    /**
     * Send alert to configured channels.
     *
     * @param alert Alert to send
     */
    public void sendAlert(Alert alert) {
        // Log alert based on severity
        switch (alert.getLevel()) {
            case CRITICAL:
                log.error("[ALERT-CRITICAL] {} - {}",
                    alert.getAlertType(), alert.getMessage());
                break;
            case HIGH:
                log.warn("[ALERT-HIGH] {} - {}",
                    alert.getAlertType(), alert.getMessage());
                break;
            case MEDIUM:
                log.warn("[ALERT-MEDIUM] {} - {}",
                    alert.getAlertType(), alert.getMessage());
                break;
            case LOW:
            case INFO:
                log.info("[ALERT-INFO] {} - {}",
                    alert.getAlertType(), alert.getMessage());
                break;
        }

        // Log details if present
        if (!alert.getDetails().isEmpty()) {
            log.info("[ALERT-DETAILS] {}", alert.getDetails());
        }

        // TODO: Future integrations
        // - sendToSlack(alert) for team notifications
        // - sendToPagerDuty(alert) for on-call alerts (CRITICAL/HIGH only)
        // - sendToEmail(alert) for admin notifications
        // - publishToKafka(alert) for event streaming
    }

    /**
     * Send CRITICAL level alert.
     *
     * @param alertType Alert type identifier
     * @param message Alert message
     */
    public void sendCriticalAlert(String alertType, String message) {
        sendAlert(Alert.builder()
            .alertType(alertType)
            .level(AlertLevel.CRITICAL)
            .message(message)
            .build());
    }

    /**
     * Send HIGH level alert.
     *
     * @param alertType Alert type identifier
     * @param message Alert message
     */
    public void sendHighAlert(String alertType, String message) {
        sendAlert(Alert.builder()
            .alertType(alertType)
            .level(AlertLevel.HIGH)
            .message(message)
            .build());
    }

    /**
     * Send INFO level alert.
     *
     * @param alertType Alert type identifier
     * @param message Alert message
     */
    public void sendInfoAlert(String alertType, String message) {
        sendAlert(Alert.builder()
            .alertType(alertType)
            .level(AlertLevel.INFO)
            .message(message)
            .build());
    }

    // ========================================================================
    // FUTURE INTEGRATION EXAMPLES
    // ========================================================================

    /*
    private void sendToSlack(Alert alert) {
        // Slack WebHook integration
        // if (alert.getLevel() == AlertLevel.CRITICAL || alert.getLevel() == AlertLevel.HIGH) {
        //     slackClient.send(alert.getMessage());
        // }
    }

    private void sendToPagerDuty(Alert alert) {
        // PagerDuty API integration for on-call alerts
        // if (alert.getLevel() == AlertLevel.CRITICAL) {
        //     pagerDutyClient.trigger(alert);
        // }
    }

    private void sendToEmail(Alert alert) {
        // Email notification for admins
        // mailService.send(adminEmails, alert.getMessage(), alert.getDetails());
    }
    */
}
