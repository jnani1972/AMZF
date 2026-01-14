# Future Implementation Guide: Immutable Repository Pattern

**Date:** 2026-01-09
**Status:** Implementation guide for future repository development

---

## ⚠️ Important: Pending Repository Implementations

The following tables exist in the database with immutability support (deleted_at, version columns and composite PKs), but **DO NOT have full Java repository implementations yet:**

### 1. Signals Table
- ✅ Database schema: Immutability columns added, PK changed to (signal_id, version)
- ✅ Domain model: `Signal.java` updated with `deletedAt` and `version`
- ✅ Service: `SignalService.java` creates signals
- ❌ **MISSING:** `SignalRepository` interface and `PostgresSignalRepository` implementation

### 2. Trade Intents Table
- ✅ Database schema: Immutability columns added, PK changed to (intent_id, version)
- ✅ Domain model: `TradeIntent.java` updated with `deletedAt` and `version`
- ✅ Usage: Created in `ExecutionOrchestrator.java`
- ❌ **MISSING:** `TradeIntentRepository` interface and `PostgresTradeIntentRepository` implementation

### 3. Trades Table
- ✅ Database schema: Immutability columns added, PK changed to (trade_id, version)
- ❌ **MISSING:** `Trade.java` domain model
- ❌ **MISSING:** `TradeRepository` interface and `PostgresTradeRepository` implementation
- ❌ **MISSING:** Service layer

---

## 📋 Implementation Checklist

When implementing repositories for these tables, **YOU MUST** follow the immutable pattern established in the existing repositories.

### Step 1: Create Domain Model (if not exists)

**Example:** `Trade.java`
```java
package in.annupaper.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

public record Trade(
    String tradeId,
    String userId,
    String brokerId,
    String userBrokerId,
    String signalId,
    String intentId,
    String portfolioId,
    String symbol,
    String side,           // BUY/SELL
    Integer qty,
    BigDecimal avgPrice,
    BigDecimal totalValue,
    String orderType,
    String productType,
    String status,         // PENDING/FILLED/REJECTED/CANCELLED
    String orderId,        // Broker order ID
    String exchangeOrderId,
    Instant placedAt,
    Instant filledAt,
    Instant cancelledAt,
    String errorCode,
    String errorMessage,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt,     // IMMUTABILITY: Soft delete timestamp
    int version            // IMMUTABILITY: Version number
) {}
```

**Key Requirements:**
- ✅ Must include `Instant deletedAt` field
- ✅ Must include `int version` field
- ✅ Use Java `record` (immutable by design)
- ✅ Constructor signature matches table columns exactly

### Step 2: Create Repository Interface

**Example:** `SignalRepository.java`
```java
package in.annupaper.repository;

import in.annupaper.domain.model.Signal;
import java.util.List;
import java.util.Optional;

public interface SignalRepository {
    // Query methods - MUST include deleted_at IS NULL
    List<Signal> findAll();
    Optional<Signal> findById(String signalId);
    List<Signal> findBySymbol(String symbol);
    List<Signal> findByStatus(String status);

    // Insert method - MUST set version=1
    void insert(Signal signal);

    // Update method - MUST use immutable pattern
    void update(Signal signal);

    // Delete method - MUST use soft delete
    void delete(String signalId);

    // History methods (optional but recommended)
    List<Signal> findAllVersions(String signalId);
    Optional<Signal> findByIdAndVersion(String signalId, int version);
}
```

### Step 3: Implement PostgreSQL Repository

**Example:** `PostgresSignalRepository.java`

See `/tmp/annu-v04/src/main/java/in/annupaper/repository/PostgresBrokerRepository.java` as reference.

**Critical Implementation Requirements:**

#### 3.1 Query Methods (SELECT)
```java
@Override
public List<Signal> findAll() {
    String sql = """
        SELECT * FROM signals
        WHERE deleted_at IS NULL
        ORDER BY created_at DESC
        """;
    // CRITICAL: Always include WHERE deleted_at IS NULL
}

@Override
public Optional<Signal> findById(String signalId) {
    String sql = """
        SELECT * FROM signals
        WHERE signal_id = ? AND deleted_at IS NULL
        """;
    // CRITICAL: Always check deleted_at IS NULL for active record
}
```

#### 3.2 Insert Method
```java
@Override
public void insert(Signal signal) {
    String sql = """
        INSERT INTO signals (
            signal_id, symbol, direction, signal_type, ...,
            status, created_at, updated_at, version
        ) VALUES (?, ?, ?, ?, ..., ?, NOW(), NOW(), 1)
        """;

    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setString(1, signal.signalId());
        ps.setString(2, signal.symbol());
        // ... set all parameters ...
        // CRITICAL: version is always 1 for new records
        // CRITICAL: deleted_at is NOT set (defaults to NULL)

        ps.executeUpdate();
        log.info("Signal inserted: {}", signal.signalId());
    }
}
```

#### 3.3 Update Method (IMMUTABLE PATTERN)
```java
@Override
public void update(Signal signal) {
    // CRITICAL: Immutable update - soft delete old, insert new version

    String queryVersionSql = """
        SELECT version FROM signals
        WHERE signal_id = ? AND deleted_at IS NULL
        """;

    String softDeleteSql = """
        UPDATE signals
        SET deleted_at = NOW()
        WHERE signal_id = ? AND version = ?
        """;

    String insertSql = """
        INSERT INTO signals (
            signal_id, symbol, direction, signal_type, ...,
            status, created_at, updated_at, version
        ) VALUES (?, ?, ?, ?, ..., ?, NOW(), NOW(), ?)
        """;

    try (Connection conn = dataSource.getConnection()) {
        conn.setAutoCommit(false);

        // Step 1: Get current version
        int currentVersion;
        try (PreparedStatement ps = conn.prepareStatement(queryVersionSql)) {
            ps.setString(1, signal.signalId());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new RuntimeException("Signal not found: " + signal.signalId());
                }
                currentVersion = rs.getInt("version");
            }
        }

        // Step 2: Soft delete current version
        try (PreparedStatement ps = conn.prepareStatement(softDeleteSql)) {
            ps.setString(1, signal.signalId());
            ps.setInt(2, currentVersion);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new RuntimeException("Failed to soft delete signal: " + signal.signalId());
            }
        }

        // Step 3: Insert new version
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, signal.signalId());
            ps.setString(2, signal.symbol());
            // ... set all parameters ...
            ps.setInt(?, currentVersion + 1);  // New version
            ps.executeUpdate();
        }

        conn.commit();
        log.info("Signal updated: {} version {} → {}",
                 signal.signalId(), currentVersion, currentVersion + 1);

    } catch (Exception e) {
        log.error("Failed to update signal: {}", e.getMessage());
        throw new RuntimeException("Signal update failed", e);
    }
}
```

#### 3.4 Delete Method (SOFT DELETE)
```java
@Override
public void delete(String signalId) {
    // CRITICAL: Soft delete - never physically DELETE

    String queryVersionSql = """
        SELECT version FROM signals
        WHERE signal_id = ? AND deleted_at IS NULL
        """;

    String softDeleteSql = """
        UPDATE signals
        SET deleted_at = NOW()
        WHERE signal_id = ? AND version = ?
        """;

    try (Connection conn = dataSource.getConnection()) {
        conn.setAutoCommit(false);

        // Get current version
        int currentVersion;
        try (PreparedStatement ps = conn.prepareStatement(queryVersionSql)) {
            ps.setString(1, signalId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new RuntimeException("Signal not found: " + signalId);
                }
                currentVersion = rs.getInt("version");
            }
        }

        // Soft delete
        try (PreparedStatement ps = conn.prepareStatement(softDeleteSql)) {
            ps.setString(1, signalId);
            ps.setInt(2, currentVersion);
            int deleted = ps.executeUpdate();
            if (deleted == 0) {
                throw new RuntimeException("Failed to delete signal: " + signalId);
            }
        }

        conn.commit();
        log.info("Signal soft-deleted: {} version {}", signalId, currentVersion);
    }
}
```

#### 3.5 MapRow Method
```java
private Signal mapRow(ResultSet rs) throws Exception {
    // CRITICAL: Must read deletedAt and version columns

    Timestamp deletedTs = rs.getTimestamp("deleted_at");
    Instant deletedAt = deletedTs != null ? deletedTs.toInstant() : null;
    int version = rs.getInt("version");

    return new Signal(
        rs.getString("signal_id"),
        rs.getString("symbol"),
        Direction.valueOf(rs.getString("direction")),
        SignalType.valueOf(rs.getString("signal_type")),
        // ... all other fields ...
        rs.getString("status"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant(),
        deletedAt,     // CRITICAL: Include deletedAt
        version        // CRITICAL: Include version
    );
}
```

#### 3.6 History Query Methods (Optional)
```java
@Override
public List<Signal> findAllVersions(String signalId) {
    String sql = """
        SELECT * FROM signals
        WHERE signal_id = ?
        ORDER BY version ASC
        """;
    // NOTE: No deleted_at filter - returns ALL versions
}

@Override
public Optional<Signal> findByIdAndVersion(String signalId, int version) {
    String sql = """
        SELECT * FROM signals
        WHERE signal_id = ? AND version = ?
        """;
    // NOTE: No deleted_at filter - returns specific version
}
```

---

## ✅ Validation Checklist

Before committing repository implementation, verify:

### Database Schema
- [ ] Table has `deleted_at TIMESTAMP WITH TIME ZONE` column
- [ ] Table has `version INTEGER NOT NULL DEFAULT 1` column
- [ ] Primary key is composite: `(id, version)`
- [ ] Partial unique index exists: `WHERE deleted_at IS NULL`
- [ ] Old unique constraints dropped (broker_code, email, etc.)

### Domain Model
- [ ] Java record includes `Instant deletedAt` field
- [ ] Java record includes `int version` field
- [ ] Constructor signature matches table columns
- [ ] No additional business logic (keep records pure data)

### Repository Interface
- [ ] All query methods documented
- [ ] Update method signature clear
- [ ] Delete method signature clear
- [ ] History methods included (optional)

### Repository Implementation
- [ ] All SELECT queries include `WHERE deleted_at IS NULL`
- [ ] INSERT always sets `version = 1`
- [ ] UPDATE uses immutable pattern (soft delete + insert new version)
- [ ] DELETE uses soft delete (never physical DELETE)
- [ ] MapRow method reads `deleted_at` and `version` columns
- [ ] Transaction handling for multi-step operations
- [ ] Error logging with context
- [ ] Connection auto-close via try-with-resources

### Service Layer
- [ ] All constructor calls include `deletedAt` (null) and `version` (1)
- [ ] Service methods use repository, not direct SQL
- [ ] No UPDATE statements in service code

### Testing
- [ ] Insert test: Record created with version=1
- [ ] Query test: Only active records returned
- [ ] Update test: Old version soft-deleted, new version created
- [ ] Delete test: Record soft-deleted (deleted_at set)
- [ ] History test: All versions retrievable
- [ ] Concurrent update test: Optimistic locking on version

---

## 🚫 Common Mistakes to Avoid

### ❌ WRONG: In-place UPDATE
```java
// NEVER DO THIS
String sql = "UPDATE signals SET status = ? WHERE signal_id = ?";
```

### ✅ CORRECT: Immutable update
```java
// Always soft delete + insert new version
String deleteSql = "UPDATE signals SET deleted_at = NOW() WHERE signal_id = ? AND version = ?";
String insertSql = "INSERT INTO signals (..., version) VALUES (..., ?)";
```

### ❌ WRONG: Physical DELETE
```java
// NEVER DO THIS
String sql = "DELETE FROM signals WHERE signal_id = ?";
```

### ✅ CORRECT: Soft delete
```java
// Always use soft delete
String sql = "UPDATE signals SET deleted_at = NOW() WHERE signal_id = ? AND version = ?";
```

### ❌ WRONG: Query without deleted_at check
```java
// WRONG - Returns deleted records
String sql = "SELECT * FROM signals WHERE signal_id = ?";
```

### ✅ CORRECT: Query with deleted_at check
```java
// CORRECT - Returns only active record
String sql = "SELECT * FROM signals WHERE signal_id = ? AND deleted_at IS NULL";
```

### ❌ WRONG: Constructor without deletedAt/version
```java
// WRONG - Constructor signature mismatch
Signal signal = new Signal(
    signalId, symbol, direction, signalType,
    // ... other fields ...
    status, createdAt, updatedAt  // Missing deletedAt and version!
);
```

### ✅ CORRECT: Constructor with deletedAt/version
```java
// CORRECT - Full signature
Signal signal = new Signal(
    signalId, symbol, direction, signalType,
    // ... other fields ...
    status, createdAt, updatedAt,
    null,  // deletedAt
    1      // version
);
```

---

## 📚 Reference Implementations

Study these existing immutable repositories as examples:

1. **Best Example:** `/tmp/annu-v04/src/main/java/in/annupaper/repository/PostgresBrokerRepository.java`
   - Complete CRUD with immutable pattern
   - Soft delete implementation
   - Version tracking
   - Transaction handling

2. **User Authentication:** `/tmp/annu-v04/src/main/java/in/annupaper/auth/AuthService.java`
   - Users table with immutability
   - Login query with `deleted_at IS NULL`
   - Registration with `version = 1`

3. **Complex Relationships:** `/tmp/annu-v04/src/main/java/in/annupaper/repository/PostgresUserBrokerRepository.java`
   - Junction table with immutability
   - Status updates using immutable pattern
   - Connection state tracking

---

## 🎯 Implementation Priority

When you're ready to implement these repositories, suggested order:

### Priority 1: SignalRepository
- **Reason:** Signals are created by SignalService, need persistence
- **Impact:** Enable signal history tracking for strategy backtesting
- **Complexity:** Low (simple entity, no complex relationships)

### Priority 2: TradeIntentRepository
- **Reason:** Intents created by ExecutionOrchestrator, need persistence
- **Impact:** Enable validation audit trail
- **Complexity:** Medium (references signals, users, brokers)

### Priority 3: TradeRepository
- **Reason:** Final execution records need persistence
- **Impact:** Complete trading audit trail
- **Complexity:** High (references signals, intents, portfolios, users, brokers)

---

## 📝 Migration Status

Current database migration status:

```sql
-- Check immutability columns exist
SELECT
    table_name,
    COUNT(*) FILTER (WHERE column_name = 'deleted_at') AS has_deleted_at,
    COUNT(*) FILTER (WHERE column_name = 'version') AS has_version
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name IN ('signals', 'trade_intents', 'trades')
GROUP BY table_name;
```

Expected result (all complete):
```
table_name    | has_deleted_at | has_version
--------------+----------------+-------------
signals       |              1 |           1
trade_intents |              1 |           1
trades        |              1 |           1
```

---

## 🔧 Development Workflow

When implementing a new repository:

1. **Verify database schema** (columns, PK, indexes)
2. **Create/verify domain model** (with deletedAt, version)
3. **Create repository interface** (CRUD methods)
4. **Implement PostgreSQL repository** (following immutable pattern)
5. **Update service layer** (constructor calls with deletedAt/version)
6. **Add to App.java** (wire repositories, inject to services)
7. **Test compilation** (`mvn clean package -DskipTests`)
8. **Test runtime** (insert, query, update, delete operations)
9. **Verify audit trail** (check multiple versions exist)
10. **Document in code** (add javadocs explaining immutability)

---

## ⚠️ Critical Reminders

1. **NEVER use physical DELETE** - Always soft delete with `deleted_at = NOW()`
2. **NEVER use in-place UPDATE** - Always soft delete + insert new version
3. **ALWAYS query with `deleted_at IS NULL`** - To get active records only
4. **ALWAYS set `version = 1`** - For new inserts
5. **ALWAYS increment version** - For updates (version = current + 1)
6. **ALWAYS use transactions** - For multi-step operations (soft delete + insert)
7. **ALWAYS include deletedAt/version** - In constructor calls

---

## 📞 Questions or Issues?

If you encounter problems implementing the immutable pattern:

1. Review existing implementations (PostgresBrokerRepository, PostgresUserBrokerRepository)
2. Check migration script: `/tmp/annu-v04/migrations/001_add_immutability.sql`
3. Read summary: `/tmp/annu-v04/migrations/IMMUTABILITY_IMPLEMENTATION_SUMMARY.md`
4. Verify MMMP compliance: No refactoring, in-situ edits, comment old code

---

**Status:** Ready for future implementation
**Pattern:** Established and tested
**Documentation:** Complete
**Examples:** Available in existing code

---

**Last Updated:** 2026-01-09
**Implementation:** Pending - Follow this guide when ready
