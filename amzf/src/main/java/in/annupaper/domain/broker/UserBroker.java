package in.annupaper.domain.broker;

import com.fasterxml.jackson.databind.JsonNode;
import in.annupaper.domain.broker.BrokerRole;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * User-Broker link with credentials and limits.
 * Immutable audit trail: version-based, deleted_at for soft delete.
 */
// public record UserBroker(
//     String userBrokerId,
//     String userId,
//     String brokerId,
//
//     // Role
//     BrokerRole role,          // DATA | EXEC
//
//     // Credentials (encrypted JSON)
//     JsonNode credentials,
//
//     // Connection state
//     boolean connected,
//     Instant lastConnected,
//     String connectionError,
//
//     // Capital/limits
//     BigDecimal capitalAllocated,
//     BigDecimal maxExposure,
//     BigDecimal maxPerTrade,
//     int maxOpenTrades,
//     List<String> allowedSymbols,    // empty = all
//     List<String> blockedSymbols,
//     List<String> allowedProducts,   // empty = all
//
//     // Risk parameters
//     BigDecimal maxDailyLoss,
//     BigDecimal maxWeeklyLoss,
//     int cooldownMinutes,
//
//     // State
//     String status,           // ACTIVE | PAUSED | DISABLED
//     boolean enabled,
//
//     Instant createdAt,
//     Instant updatedAt
// ) {
public record UserBroker(
    String userBrokerId,
    String userId,
    String brokerId,

    // Role
    BrokerRole role,          // DATA | EXEC

    // Credentials (encrypted JSON)
    JsonNode credentials,

    // Connection state
    boolean connected,
    Instant lastConnected,
    String connectionError,

    // Capital/limits
    BigDecimal capitalAllocated,
    BigDecimal maxExposure,
    BigDecimal maxPerTrade,
    int maxOpenTrades,
    List<String> allowedSymbols,    // empty = all
    List<String> blockedSymbols,
    List<String> allowedProducts,   // empty = all

    // Risk parameters
    BigDecimal maxDailyLoss,
    BigDecimal maxWeeklyLoss,
    int cooldownMinutes,

    // State
    String status,           // ACTIVE | PAUSED | DISABLED
    boolean enabled,

    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt,
    int version
) {
    public boolean isDataBroker() {
        return role == BrokerRole.DATA;
    }
    
    public boolean isExecBroker() {
        return role == BrokerRole.EXEC;
    }
    
    public boolean isActive() {
        return enabled && "ACTIVE".equals(status);
    }
    
    public boolean isSymbolAllowed(String symbol) {
        if (blockedSymbols != null && blockedSymbols.contains(symbol)) {
            return false;
        }
        if (allowedSymbols == null || allowedSymbols.isEmpty()) {
            return true;  // Empty = all allowed
        }
        return allowedSymbols.contains(symbol);
    }
    
    public boolean isProductAllowed(String product) {
        if (allowedProducts == null || allowedProducts.isEmpty()) {
            return true;  // Empty = all allowed
        }
        return allowedProducts.contains(product);
    }
}
