package in.annupaper.domain.model;

import java.time.Instant;

/**
 * Snapshot of system health at a point in time.
 */
public final class SystemHealthSnapshot {
    private final Instant timestamp;
    private final int totalOpenTrades;
    private final int longPositions;
    private final int shortPositions;
    private final double totalExposureValue;
    private final double avgHoldingHours;
    private final int pendingTradeIntents;
    private final int pendingExitIntents;
    private final int pendingOrders;
    private final int activeBrokers;
    private final int expiredBrokerSessions;
    private final int expiringSoonBrokerSessions;

    public SystemHealthSnapshot(Instant timestamp, int totalOpenTrades, int longPositions, int shortPositions,
            double totalExposureValue, double avgHoldingHours, int pendingTradeIntents,
            int pendingExitIntents, int pendingOrders, int activeBrokers,
            int expiredBrokerSessions, int expiringSoonBrokerSessions) {
        this.timestamp = timestamp;
        this.totalOpenTrades = totalOpenTrades;
        this.longPositions = longPositions;
        this.shortPositions = shortPositions;
        this.totalExposureValue = totalExposureValue;
        this.avgHoldingHours = avgHoldingHours;
        this.pendingTradeIntents = pendingTradeIntents;
        this.pendingExitIntents = pendingExitIntents;
        this.pendingOrders = pendingOrders;
        this.activeBrokers = activeBrokers;
        this.expiredBrokerSessions = expiredBrokerSessions;
        this.expiringSoonBrokerSessions = expiringSoonBrokerSessions;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public int getTotalOpenTrades() {
        return totalOpenTrades;
    }

    public int getLongPositions() {
        return longPositions;
    }

    public int getShortPositions() {
        return shortPositions;
    }

    public double getTotalExposureValue() {
        return totalExposureValue;
    }

    public double getAvgHoldingHours() {
        return avgHoldingHours;
    }

    public int getPendingTradeIntents() {
        return pendingTradeIntents;
    }

    public int getPendingExitIntents() {
        return pendingExitIntents;
    }

    public int getPendingOrders() {
        return pendingOrders;
    }

    public int getActiveBrokers() {
        return activeBrokers;
    }

    public int getExpiredBrokerSessions() {
        return expiredBrokerSessions;
    }

    public int getExpiringSoonBrokerSessions() {
        return expiringSoonBrokerSessions;
    }

    /**
     * Check if system is healthy overall.
     *
     * @return true if no critical issues detected
     */
    public boolean isHealthy() {
        return expiredBrokerSessions == 0
                && pendingExitIntents < 10
                && pendingOrders < 50;
    }
}
