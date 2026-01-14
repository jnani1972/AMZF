# V010 Exit Qualification Symmetry - Remaining Implementation

## ✅ Completed

1. Database Migration (V010)
   - exit_intents table created
   - trades.direction column added
   - Cooldown enforcement in DB (restart-safe)
   - place_exit_order() function for atomic transitions

2. Domain Models
   - ExitIntentStatus enum
   - ExitIntent record

3. Repositories
   - ExitIntentRepository interface
   - PostgresExitIntentRepository implementation

4. Services
   - ExitQualificationService (execution qualification logic)

5. Trade Model
   - Added direction field
   - Updated all with*() helper methods

## ⚠️  Compilation Errors to Fix

### 1. PostgresTradeRepository.mapRow()
**File:** `src/main/java/in/annupaper/repository/PostgresTradeRepository.java:647`
**Fix:** Add direction field when constructing Trade object from ResultSet

```java
// BEFORE (line ~647):
return new Trade(
    rs.getString("trade_id"),
    rs.getString("portfolio_id"),
    ...
    rs.getString("symbol"),
    rs.getInt("trade_number"),
    ...

// AFTER:
return new Trade(
    rs.getString("trade_id"),
    rs.getString("portfolio_id"),
    ...
    rs.getString("symbol"),
    rs.getString("direction"),  // ADD THIS
    rs.getInt("trade_number"),
    ...
```

Also update INSERT statement to include direction field.

### 2. OrderExecutionService Trade Creation
**File:** `src/main/java/in/annupaper/service/execution/OrderExecutionService.java:172`
**Fix:** Add direction (get from signal)

```java
// Get direction from signal
Signal signal = signalRepo.findById(intent.signalId()).orElse(null);
String direction = signal != null ? signal.direction() : "BUY";  // Default to BUY for safety

return new Trade(
    tradeId,
    portfolio.portfolioId(),
    intent.userId(),
    intent.brokerId(),
    intent.userBrokerId(),
    intent.signalId(),
    intent.intentId(),
    signal.symbol(),
    direction,  // ADD THIS
    tradeNumber,
    ...
```

### 3. PendingOrderReconciler Trade Creation (4 locations)
**Files:** Lines 198, 255, 327, 354
**Fix:** Add direction to constructor calls (derive from existing trade or default to "BUY")

```java
// If creating new trade, get from signal
// If updating existing trade, use existing.direction()
```

### 4. TradeManagementServiceImpl Trade Creation
**File:** `src/main/java/in/annupaper/service/trade/TradeManagementServiceImpl.java:169`
**Fix:** Get direction from signal

```java
Signal signal = signalRepo.findById(openRequest.signalId()).orElse(null);
String direction = signal != null ? signal.direction() : "BUY";

return new Trade(
    tradeId,
    portfolio.portfolioId(),
    openRequest.userId(),
    openRequest.brokerId(),
    openRequest.userBrokerId(),
    openRequest.signalId(),
    openRequest.intentId(),
    openRequest.symbol(),
    direction,  // ADD THIS
    tradeNumber,
    ...
```

### 5. ExitQualificationService - portfolio() Method
**File:** `src/main/java/in/annupaper/service/validation/ExitQualificationService.java:109`
**Error:** `cannot find symbol: method portfolio()`
**Fix:** Remove portfolio check or use correct method from UserContext

```java
// OPTION 1: Remove check (allow null userContext)
if (userContext != null && userContext.portfolioFrozen()) {
    builder.addError("PORTFOLIO_FROZEN", "Portfolio is frozen");
    return builder.build();
}

// OPTION 2: Skip portfolio check for exits
// (comment out or remove lines 106-110)
```

## 🚀 Next Steps

1. Fix all 9 compilation errors above
2. Test compilation: `mvn compile -DskipTests`
3. Update SMS to create ExitIntent (add to SMS.onExitDetected())
4. Update ExitSignalService to use persisted direction
5. Handle DB cooldown exceptions in SMS (try/catch around generate_exit_episode())
6. Full compilation test
7. Commit V010 implementation

## 📝 Code Locations

- Trade repository: `src/main/java/in/annupaper/repository/PostgresTradeRepository.java`
- Order execution: `src/main/java/in/annupaper/service/execution/OrderExecutionService.java`
- Reconciler: `src/main/java/in/annupaper/service/execution/PendingOrderReconciler.java`
- TMS: `src/main/java/in/annupaper/service/trade/TradeManagementServiceImpl.java`
- Exit qual: `src/main/java/in/annupaper/service/validation/ExitQualificationService.java`
- SMS: `src/main/java/in/annupaper/service/signal/SignalManagementServiceImpl.java`
