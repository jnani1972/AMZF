# P0 Gates Completion Checklist

## ✅ Phase 1: CODE COMPLETE (Done!)

- [x] **P0-A: Startup Validation Gate**
  - [x] P0DebtRegistry.java created
  - [x] BrokerEnvironment.java created
  - [x] StartupConfigValidator.java created
  - [x] App.java wired up
  - [x] Test guide created

- [x] **P0-B: DB Uniqueness + Upsert**
  - [x] V007 migration script created
  - [x] TradeRepository.upsert() implemented
  - [x] SignalRepository.upsert() implemented
  - [x] Test guide created

- [x] **P0-C: Broker Reconciliation Loop**
  - [x] PendingOrderReconciler.java created (450+ lines)
  - [x] Trade.java updated (clientOrderId, lastBrokerUpdateAt)
  - [x] App.java wired up and started
  - [x] Test guide created

- [x] **P0-D: Tick Deduplication**
  - [x] TickCandleBuilder.java updated (two-window pattern)
  - [x] Dedupe metrics API added
  - [x] P0DebtRegistry flag set to TRUE
  - [x] Test guide created

- [x] **P0-E: Single-Writer Trade State**
  - [x] OrderExecutionService.java created (345 lines)
  - [x] markRejectedByIntentId() implemented
  - [x] P0DebtRegistry flag set to TRUE
  - [x] Test guide created

- [x] **Position Tracking Fix**
  - [x] ExitSignalService.java refactored (queries DB)
  - [x] HashMap removed
  - [x] P0DebtRegistry flag set to TRUE

---

## ⏳ Phase 2: DATABASE MIGRATION (Next!)

### Pre-Migration

- [ ] **Backup database**
  ```bash
  pg_dump -U postgres -d annupaper -F c -f annupaper_backup_$(date +%Y%m%d_%H%M%S).dump
  ```
  Backup file: ________________

- [ ] **Check for data conflicts**
  ```bash
  psql -U postgres -d annupaper
  # Run queries from V007_MIGRATION_EXECUTION_GUIDE.md
  ```
  Duplicates found: Yes / No

- [ ] **Stop application (recommended)**
  ```bash
  systemctl stop annupaper
  # Or: pkill -f "java.*annupaper"
  ```

### Migration Execution

**Option A: Automated (Recommended)**

- [ ] **Run migration script**
  ```bash
  ./run_v007_migration.sh
  ```
  Status: Success / Failed

  If failed, see troubleshooting section

**Option B: Manual**

- [ ] **Run V007 migration**
  ```bash
  psql -U postgres -d annupaper -f sql/V007__add_idempotency_constraints.sql
  ```
  Output ends with: COMMIT / ROLLBACK

- [ ] **Verify migration**
  ```bash
  psql -U postgres -d annupaper -f sql/verify_v007_migration.sql
  ```
  Result: All checks passed: YES / NO

### Post-Migration

- [ ] **Update P0DebtRegistry.java (lines 26-27)**
  ```java
  "SIGNAL_DB_CONSTRAINTS_APPLIED", true,  // ✅ V007 verified
  "TRADE_IDEMPOTENCY_CONSTRAINTS", true,  // ✅ V007 verified
  ```

- [ ] **Rebuild application**
  ```bash
  mvn clean compile
  mvn test  # Optional but recommended
  ```

---

## 🚀 Phase 3: PROD_READY MODE (Final!)

### Enable Production Mode

- [ ] **Update configuration**
  ```properties
  # In config/application.properties:
  release.readiness=PROD_READY
  ```

  Or:
  ```bash
  export RELEASE_READINESS=PROD_READY
  ```

- [ ] **Start application**
  ```bash
  java -jar target/annupaper-v04.jar
  # Or: systemctl start annupaper
  ```

### Verify Startup

- [ ] **Check logs for success messages**
  ```bash
  tail -f logs/application.log
  ```

  Look for:
  - [ ] ✅ All P0 gates resolved
  - [ ] ✅ PROD_READY mode: Startup validation passed
  - [ ] ✅ Pending order reconciler started
  - [ ] ✅ OrderExecutionService initialized
  - [ ] No errors or warnings about P0 gates

### Smoke Tests

- [ ] **Test trade idempotency**
  - Submit same intent_id twice
  - Verify: Second creates no duplicate

- [ ] **Test signal deduplication**
  - Insert duplicate signal
  - Verify: Upserted, not duplicated

- [ ] **Check tick dedupe metrics**
  ```bash
  curl http://localhost:8080/api/health/tick-dedupe-metrics
  ```
  Metrics available: Yes / No

- [ ] **Verify reconciler running**
  ```bash
  tail -f logs/application.log | grep "Reconciling"
  ```
  Logs every 30 seconds: Yes / No

- [ ] **Check for constraint violations**
  ```bash
  tail -f logs/application.log | grep "constraint"
  ```
  No violations: Yes / No

---

## 🎯 Success Criteria

### All Must Be TRUE:

- [ ] All 7 P0 gates set to TRUE in P0DebtRegistry.java
- [ ] V007 migration ran successfully
- [ ] verify_v007_migration.sql shows "All checks passed: YES"
- [ ] Application starts without P0 gate errors
- [ ] PROD_READY mode enabled
- [ ] No constraint violations in logs
- [ ] Reconciler logs every 30 seconds
- [ ] Dedupe metrics available
- [ ] Smoke tests pass

---

## 📊 Final Status

**Before Migration:**
- Gates Complete: 5/7 (71%)
- Production Ready: NO

**After Migration:**
- Gates Complete: 7/7 (100%) ✅
- Production Ready: YES ✅

---

## 🚨 If Something Goes Wrong

### Migration Failed:

1. **Check error message** in migration output
2. **Review** V007_MIGRATION_EXECUTION_GUIDE.md - Troubleshooting
3. **Rollback** if needed:
   ```bash
   psql -U postgres -d annupaper -f sql/V007_rollback.sql
   ```
4. **Restore** from backup if necessary:
   ```bash
   pg_restore -U postgres -d annupaper -c annupaper_backup_YYYYMMDD_HHMMSS.dump
   ```

### Application Won't Start:

1. **Check** which gate is FALSE
   ```bash
   grep "P0_GATES = Map.of" -A 8 src/main/java/in/annupaper/bootstrap/P0DebtRegistry.java
   ```
2. **Verify** both DB gates are TRUE
3. **Check** migration actually ran:
   ```bash
   psql -U postgres -d annupaper -f sql/verify_v007_migration.sql
   ```

### Constraint Violations:

1. **Check** for duplicate data
2. **Clean up** duplicates
3. **Re-run** migration

---

## 📁 Files to Reference

- **Migration guide:** V007_MIGRATION_EXECUTION_GUIDE.md
- **Quick start:** QUICKSTART_100_PERCENT_PROD_READY.md
- **Verification guide:** P0_GATES_VERIFICATION_GUIDE.md
- **Complete summary:** PHASE1_P0_COMPLETION_SUMMARY.md

---

## ⏱️ Estimated Time

- **Migration:** 2-5 minutes
- **Verification:** 1-2 minutes
- **Code update:** 1 minute
- **Rebuild:** 1-2 minutes
- **Testing:** 5-10 minutes

**Total:** 10-20 minutes to achieve 100% PROD_READY

---

## 🎉 Completion Date

**Phase 1 Code Complete:** 2026-01-13
**V007 Migration Run:** _____________
**100% PROD_READY Achieved:** _____________

**Signed off by:** _____________

---

**Notes:**
