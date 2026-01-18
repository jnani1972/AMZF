package in.annupaper.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

public record Portfolio(
        String portfolioId,
        String userId,
        String name,
        BigDecimal totalCapital,
        BigDecimal reservedCapital,
        BigDecimal maxPortfolioLogLoss,
        BigDecimal maxSymbolWeight,
        int maxSymbols,
        String allocationMode,
        String status,
        boolean paused,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt,
        int version) {
    public BigDecimal availableCapital() {
        return totalCapital.subtract(reservedCapital);
    }

    public boolean isActive() {
        return "ACTIVE".equals(status) && !paused;
    }
}
