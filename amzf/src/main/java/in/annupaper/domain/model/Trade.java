package in.annupaper.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Trade represents an open or closed position.
 * Tracks entry, MTF context, exit targets, and realized P&L.
 */
public record Trade(
        String tradeId,
        String portfolioId,
        String userId,
        String brokerId,
        String userBrokerId,
        String signalId,
        String intentId,
        String symbol,
        String direction, // BUY (LONG) | SELL (SHORT) - persisted at trade creation
        int tradeNumber,

        // Entry details
        BigDecimal entryPrice,
        int entryQty,
        BigDecimal entryValue,
        Instant entryTimestamp,
        String productType, // CNC | MIS | NRML

        // Entry MTF context
        Integer entryHtfZone,
        Integer entryItfZone,
        Integer entryLtfZone,
        String entryConfluenceType,
        BigDecimal entryConfluenceScore,
        BigDecimal entryHtfLow,
        BigDecimal entryHtfHigh,
        BigDecimal entryItfLow,
        BigDecimal entryItfHigh,
        BigDecimal entryLtfLow,
        BigDecimal entryLtfHigh,
        BigDecimal entryEffectiveFloor,
        BigDecimal entryEffectiveCeiling,

        // Risk management
        BigDecimal logLossAtFloor,
        BigDecimal maxLogLossAllowed,

        // Exit targets
        BigDecimal exitMinProfitPrice,
        BigDecimal exitTargetPrice,
        BigDecimal exitStretchPrice,
        BigDecimal exitPrimaryPrice,

        // Current status
        String status, // OPEN | CLOSED | CANCELLED
        BigDecimal currentPrice,
        BigDecimal currentLogReturn,
        BigDecimal unrealizedPnl,

        // Trailing stop
        boolean trailingActive,
        BigDecimal trailingHighestPrice,
        BigDecimal trailingStopPrice,

        // Exit details
        BigDecimal exitPrice,
        Instant exitTimestamp,
        String exitTrigger, // MIN_PROFIT | TARGET | STRETCH | STOP_LOSS | TRAILING_STOP | MANUAL
        String exitOrderId,
        BigDecimal realizedPnl,
        BigDecimal realizedLogReturn,
        Integer holdingDays,

        // Broker tracking
        String brokerOrderId,
        String brokerTradeId,
        String clientOrderId, // P0-B: Our intentId sent to broker (idempotency)
        Instant lastBrokerUpdateAt, // P0-C: Last time we heard from broker (for timeout)

        // Timestamps
        Instant createdAt,
        Instant updatedAt,

        // Immutable audit trail
        Instant deletedAt,
        int version) {
    public boolean isOpen() {
        return "OPEN".equals(status);
    }

    public boolean isClosed() {
        return "CLOSED".equals(status);
    }

    public boolean isProfitable() {
        return realizedPnl != null && realizedPnl.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean hasExceededMaxLoss() {
        return currentLogReturn != null &&
                maxLogLossAllowed != null &&
                currentLogReturn.compareTo(maxLogLossAllowed) < 0;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Immutable Update Helpers (for TradeManagementService)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Create new Trade with updated status.
     */
    public Trade withStatus(String newStatus) {
        return new Trade(
                tradeId, portfolioId, userId, brokerId, userBrokerId, signalId, intentId, symbol, direction,
                tradeNumber,
                entryPrice, entryQty, entryValue, entryTimestamp, productType,
                entryHtfZone, entryItfZone, entryLtfZone, entryConfluenceType, entryConfluenceScore,
                entryHtfLow, entryHtfHigh, entryItfLow, entryItfHigh, entryLtfLow, entryLtfHigh,
                entryEffectiveFloor, entryEffectiveCeiling,
                logLossAtFloor, maxLogLossAllowed,
                exitMinProfitPrice, exitTargetPrice, exitStretchPrice, exitPrimaryPrice,
                newStatus, currentPrice, currentLogReturn, unrealizedPnl,
                trailingActive, trailingHighestPrice, trailingStopPrice,
                exitPrice, exitTimestamp, exitTrigger, exitOrderId, realizedPnl, realizedLogReturn, holdingDays,
                brokerOrderId, brokerTradeId, clientOrderId, Instant.now(),
                createdAt, Instant.now(), deletedAt, version);
    }

    /**
     * Create new Trade with updated broker order ID.
     */
    public Trade withBrokerOrderId(String newBrokerOrderId) {
        return new Trade(
                tradeId, portfolioId, userId, brokerId, userBrokerId, signalId, intentId, symbol, direction,
                tradeNumber,
                entryPrice, entryQty, entryValue, entryTimestamp, productType,
                entryHtfZone, entryItfZone, entryLtfZone, entryConfluenceType, entryConfluenceScore,
                entryHtfLow, entryHtfHigh, entryItfLow, entryItfHigh, entryLtfLow, entryLtfHigh,
                entryEffectiveFloor, entryEffectiveCeiling,
                logLossAtFloor, maxLogLossAllowed,
                exitMinProfitPrice, exitTargetPrice, exitStretchPrice, exitPrimaryPrice,
                status, currentPrice, currentLogReturn, unrealizedPnl,
                trailingActive, trailingHighestPrice, trailingStopPrice,
                exitPrice, exitTimestamp, exitTrigger, exitOrderId, realizedPnl, realizedLogReturn, holdingDays,
                newBrokerOrderId, brokerTradeId, clientOrderId, lastBrokerUpdateAt,
                createdAt, Instant.now(), deletedAt, version);
    }

    /**
     * Create new Trade with updated lastBrokerUpdateAt.
     */
    public Trade withLastBrokerUpdateAt(Instant newLastBrokerUpdateAt) {
        return new Trade(
                tradeId, portfolioId, userId, brokerId, userBrokerId, signalId, intentId, symbol, direction,
                tradeNumber,
                entryPrice, entryQty, entryValue, entryTimestamp, productType,
                entryHtfZone, entryItfZone, entryLtfZone, entryConfluenceType, entryConfluenceScore,
                entryHtfLow, entryHtfHigh, entryItfLow, entryItfHigh, entryLtfLow, entryLtfHigh,
                entryEffectiveFloor, entryEffectiveCeiling,
                logLossAtFloor, maxLogLossAllowed,
                exitMinProfitPrice, exitTargetPrice, exitStretchPrice, exitPrimaryPrice,
                status, currentPrice, currentLogReturn, unrealizedPnl,
                trailingActive, trailingHighestPrice, trailingStopPrice,
                exitPrice, exitTimestamp, exitTrigger, exitOrderId, realizedPnl, realizedLogReturn, holdingDays,
                brokerOrderId, brokerTradeId, clientOrderId, newLastBrokerUpdateAt,
                createdAt, Instant.now(), deletedAt, version);
    }

    /**
     * Create new Trade with updated entry details (on fill).
     */
    public Trade withEntryPrice(BigDecimal newEntryPrice) {
        return new Trade(
                tradeId, portfolioId, userId, brokerId, userBrokerId, signalId, intentId, symbol, direction,
                tradeNumber,
                newEntryPrice, entryQty, entryValue, entryTimestamp, productType,
                entryHtfZone, entryItfZone, entryLtfZone, entryConfluenceType, entryConfluenceScore,
                entryHtfLow, entryHtfHigh, entryItfLow, entryItfHigh, entryLtfLow, entryLtfHigh,
                entryEffectiveFloor, entryEffectiveCeiling,
                logLossAtFloor, maxLogLossAllowed,
                exitMinProfitPrice, exitTargetPrice, exitStretchPrice, exitPrimaryPrice,
                status, currentPrice, currentLogReturn, unrealizedPnl,
                trailingActive, trailingHighestPrice, trailingStopPrice,
                exitPrice, exitTimestamp, exitTrigger, exitOrderId, realizedPnl, realizedLogReturn, holdingDays,
                brokerOrderId, brokerTradeId, clientOrderId, lastBrokerUpdateAt,
                createdAt, Instant.now(), deletedAt, version);
    }

    /**
     * Create new Trade with updated entry timestamp.
     */
    public Trade withEntryTimestamp(Instant newEntryTimestamp) {
        return new Trade(
                tradeId, portfolioId, userId, brokerId, userBrokerId, signalId, intentId, symbol, direction,
                tradeNumber,
                entryPrice, entryQty, entryValue, newEntryTimestamp, productType,
                entryHtfZone, entryItfZone, entryLtfZone, entryConfluenceType, entryConfluenceScore,
                entryHtfLow, entryHtfHigh, entryItfLow, entryItfHigh, entryLtfLow, entryLtfHigh,
                entryEffectiveFloor, entryEffectiveCeiling,
                logLossAtFloor, maxLogLossAllowed,
                exitMinProfitPrice, exitTargetPrice, exitStretchPrice, exitPrimaryPrice,
                status, currentPrice, currentLogReturn, unrealizedPnl,
                trailingActive, trailingHighestPrice, trailingStopPrice,
                exitPrice, exitTimestamp, exitTrigger, exitOrderId, realizedPnl, realizedLogReturn, holdingDays,
                brokerOrderId, brokerTradeId, clientOrderId, lastBrokerUpdateAt,
                createdAt, Instant.now(), deletedAt, version);
    }

    /**
     * Create new Trade with updated exit details (on close).
     */
    public Trade withExitPrice(BigDecimal newExitPrice) {
        return new Trade(
                tradeId, portfolioId, userId, brokerId, userBrokerId, signalId, intentId, symbol, direction,
                tradeNumber,
                entryPrice, entryQty, entryValue, entryTimestamp, productType,
                entryHtfZone, entryItfZone, entryLtfZone, entryConfluenceType, entryConfluenceScore,
                entryHtfLow, entryHtfHigh, entryItfLow, entryItfHigh, entryLtfLow, entryLtfHigh,
                entryEffectiveFloor, entryEffectiveCeiling,
                logLossAtFloor, maxLogLossAllowed,
                exitMinProfitPrice, exitTargetPrice, exitStretchPrice, exitPrimaryPrice,
                status, currentPrice, currentLogReturn, unrealizedPnl,
                trailingActive, trailingHighestPrice, trailingStopPrice,
                newExitPrice, exitTimestamp, exitTrigger, exitOrderId, realizedPnl, realizedLogReturn, holdingDays,
                brokerOrderId, brokerTradeId, clientOrderId, lastBrokerUpdateAt,
                createdAt, Instant.now(), deletedAt, version);
    }

    /**
     * Create new Trade with updated exit timestamp.
     */
    public Trade withExitTimestamp(Instant newExitTimestamp) {
        return new Trade(
                tradeId, portfolioId, userId, brokerId, userBrokerId, signalId, intentId, symbol, direction,
                tradeNumber,
                entryPrice, entryQty, entryValue, entryTimestamp, productType,
                entryHtfZone, entryItfZone, entryLtfZone, entryConfluenceType, entryConfluenceScore,
                entryHtfLow, entryHtfHigh, entryItfLow, entryItfHigh, entryLtfLow, entryLtfHigh,
                entryEffectiveFloor, entryEffectiveCeiling,
                logLossAtFloor, maxLogLossAllowed,
                exitMinProfitPrice, exitTargetPrice, exitStretchPrice, exitPrimaryPrice,
                status, currentPrice, currentLogReturn, unrealizedPnl,
                trailingActive, trailingHighestPrice, trailingStopPrice,
                exitPrice, newExitTimestamp, exitTrigger, exitOrderId, realizedPnl, realizedLogReturn, holdingDays,
                brokerOrderId, brokerTradeId, clientOrderId, lastBrokerUpdateAt,
                createdAt, Instant.now(), deletedAt, version);
    }

    /**
     * Create new Trade with updated realized P&L.
     */
    public Trade withRealizedPnl(BigDecimal newRealizedPnl) {
        return new Trade(
                tradeId, portfolioId, userId, brokerId, userBrokerId, signalId, intentId, symbol, direction,
                tradeNumber,
                entryPrice, entryQty, entryValue, entryTimestamp, productType,
                entryHtfZone, entryItfZone, entryLtfZone, entryConfluenceType, entryConfluenceScore,
                entryHtfLow, entryHtfHigh, entryItfLow, entryItfHigh, entryLtfLow, entryLtfHigh,
                entryEffectiveFloor, entryEffectiveCeiling,
                logLossAtFloor, maxLogLossAllowed,
                exitMinProfitPrice, exitTargetPrice, exitStretchPrice, exitPrimaryPrice,
                status, currentPrice, currentLogReturn, unrealizedPnl,
                trailingActive, trailingHighestPrice, trailingStopPrice,
                exitPrice, exitTimestamp, exitTrigger, exitOrderId, newRealizedPnl, realizedLogReturn, holdingDays,
                brokerOrderId, brokerTradeId, clientOrderId, lastBrokerUpdateAt,
                createdAt, Instant.now(), deletedAt, version);
    }

    /**
     * Create new Trade with updated holding days.
     */
    public Trade withHoldingDays(int newHoldingDays) {
        return new Trade(
                tradeId, portfolioId, userId, brokerId, userBrokerId, signalId, intentId, symbol, direction,
                tradeNumber,
                entryPrice, entryQty, entryValue, entryTimestamp, productType,
                entryHtfZone, entryItfZone, entryLtfZone, entryConfluenceType, entryConfluenceScore,
                entryHtfLow, entryHtfHigh, entryItfLow, entryItfHigh, entryLtfLow, entryLtfHigh,
                entryEffectiveFloor, entryEffectiveCeiling,
                logLossAtFloor, maxLogLossAllowed,
                exitMinProfitPrice, exitTargetPrice, exitStretchPrice, exitPrimaryPrice,
                status, currentPrice, currentLogReturn, unrealizedPnl,
                trailingActive, trailingHighestPrice, trailingStopPrice,
                exitPrice, exitTimestamp, exitTrigger, exitOrderId, realizedPnl, realizedLogReturn, newHoldingDays,
                brokerOrderId, brokerTradeId, clientOrderId, lastBrokerUpdateAt,
                createdAt, Instant.now(), deletedAt, version);
    }
}
