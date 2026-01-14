#!/bin/bash
# ============================================================================
# V007 Migration - Quick Start Script
# ============================================================================
# Purpose: Execute V007 migration and verify success
# Usage: ./run_v007_migration.sh
# ============================================================================

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo ""
echo "========================================"
echo "V007 Migration - Quick Start"
echo "========================================"
echo ""

# Configuration (modify as needed)
DB_USER="${POSTGRES_USER:-postgres}"
DB_NAME="${POSTGRES_DB:-annupaper}"
BACKUP_DIR="./backups"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# ============================================================================
# Pre-flight checks
# ============================================================================

echo -e "${BLUE}[1/6] Pre-flight checks...${NC}"

# Check if psql is available
if ! command -v psql &> /dev/null; then
    echo -e "${RED}❌ ERROR: psql not found. Please install PostgreSQL client.${NC}"
    exit 1
fi

# Check if we can connect to database
if ! psql -U "$DB_USER" -d "$DB_NAME" -c "SELECT 1;" &> /dev/null; then
    echo -e "${RED}❌ ERROR: Cannot connect to database $DB_NAME${NC}"
    echo "Please check:"
    echo "  - Database is running"
    echo "  - Database name is correct: $DB_NAME"
    echo "  - User has permissions: $DB_USER"
    exit 1
fi

echo -e "${GREEN}✅ Database connection successful${NC}"

# ============================================================================
# Backup database
# ============================================================================

echo ""
echo -e "${BLUE}[2/6] Backing up database...${NC}"

mkdir -p "$BACKUP_DIR"
BACKUP_FILE="$BACKUP_DIR/annupaper_backup_$TIMESTAMP.dump"

echo "Creating backup: $BACKUP_FILE"
if pg_dump -U "$DB_USER" -d "$DB_NAME" -F c -f "$BACKUP_FILE" 2>/dev/null; then
    BACKUP_SIZE=$(du -h "$BACKUP_FILE" | cut -f1)
    echo -e "${GREEN}✅ Backup created: $BACKUP_FILE ($BACKUP_SIZE)${NC}"
else
    echo -e "${RED}❌ Backup failed. Aborting migration.${NC}"
    exit 1
fi

# ============================================================================
# Check for data conflicts
# ============================================================================

echo ""
echo -e "${BLUE}[3/6] Checking for data conflicts...${NC}"

# Check for duplicate intent_ids
DUPLICATE_INTENTS=$(psql -U "$DB_USER" -d "$DB_NAME" -t -c "
    SELECT COUNT(*)
    FROM (
        SELECT intent_id, COUNT(*)
        FROM trades
        WHERE intent_id IS NOT NULL
        GROUP BY intent_id
        HAVING COUNT(*) > 1
    ) duplicates;
" 2>/dev/null)

if [ "$DUPLICATE_INTENTS" -gt 0 ]; then
    echo -e "${RED}❌ WARNING: Found $DUPLICATE_INTENTS duplicate intent_ids in trades table${NC}"
    echo "Please clean up duplicates before running migration."
    echo "See: V007_MIGRATION_EXECUTION_GUIDE.md - Pre-Migration Checklist"
    exit 1
fi

# Check for duplicate signals
DUPLICATE_SIGNALS=$(psql -U "$DB_USER" -d "$DB_NAME" -t -c "
    SELECT COUNT(*)
    FROM (
        SELECT symbol, confluence_type, DATE(generated_at) as signal_day,
               effective_floor, effective_ceiling, COUNT(*)
        FROM signals
        GROUP BY symbol, confluence_type, DATE(generated_at),
                 effective_floor, effective_ceiling
        HAVING COUNT(*) > 1
    ) duplicates;
" 2>/dev/null)

if [ "$DUPLICATE_SIGNALS" -gt 0 ]; then
    echo -e "${RED}❌ WARNING: Found $DUPLICATE_SIGNALS duplicate signals${NC}"
    echo "Please clean up duplicates before running migration."
    echo "See: V007_MIGRATION_EXECUTION_GUIDE.md - Pre-Migration Checklist"
    exit 1
fi

echo -e "${GREEN}✅ No data conflicts found${NC}"

# ============================================================================
# Run migration
# ============================================================================

echo ""
echo -e "${BLUE}[4/6] Running V007 migration...${NC}"
echo ""

if [ ! -f "sql/V007__add_idempotency_constraints.sql" ]; then
    echo -e "${RED}❌ ERROR: Migration file not found: sql/V007__add_idempotency_constraints.sql${NC}"
    exit 1
fi

# Run migration
if psql -U "$DB_USER" -d "$DB_NAME" -f sql/V007__add_idempotency_constraints.sql; then
    echo ""
    echo -e "${GREEN}✅ Migration completed successfully${NC}"
else
    echo ""
    echo -e "${RED}❌ Migration failed!${NC}"
    echo ""
    echo "To rollback, run:"
    echo "  psql -U $DB_USER -d $DB_NAME -f sql/V007_rollback.sql"
    echo ""
    echo "To restore from backup:"
    echo "  pg_restore -U $DB_USER -d $DB_NAME -c $BACKUP_FILE"
    exit 1
fi

# ============================================================================
# Verify migration
# ============================================================================

echo ""
echo -e "${BLUE}[5/6] Verifying migration...${NC}"
echo ""

if [ ! -f "sql/verify_v007_migration.sql" ]; then
    echo -e "${YELLOW}⚠️  WARNING: Verification script not found: sql/verify_v007_migration.sql${NC}"
    echo "Skipping automated verification."
else
    # Run verification
    VERIFICATION_OUTPUT=$(psql -U "$DB_USER" -d "$DB_NAME" -f sql/verify_v007_migration.sql 2>&1)

    if echo "$VERIFICATION_OUTPUT" | grep -q "All checks passed: YES"; then
        echo -e "${GREEN}✅ All verification checks passed!${NC}"
    else
        echo -e "${RED}❌ Verification failed!${NC}"
        echo ""
        echo "$VERIFICATION_OUTPUT"
        echo ""
        echo "Please review the output above and check:"
        echo "  - All constraints created"
        echo "  - All indexes created"
        echo "  - All columns added"
        exit 1
    fi
fi

# ============================================================================
# Update instructions
# ============================================================================

echo ""
echo -e "${BLUE}[6/6] Next steps...${NC}"
echo ""
echo -e "${GREEN}✅ V007 migration completed successfully!${NC}"
echo ""
echo "To complete P0 gates (achieve 100% PROD_READY):"
echo ""
echo "1. Update P0DebtRegistry.java (lines 26-27):"
echo "   ${YELLOW}vim src/main/java/in/annupaper/bootstrap/P0DebtRegistry.java${NC}"
echo ""
echo "   Change:"
echo '   "SIGNAL_DB_CONSTRAINTS_APPLIED", false,'
echo '   "TRADE_IDEMPOTENCY_CONSTRAINTS", false,'
echo ""
echo "   To:"
echo '   "SIGNAL_DB_CONSTRAINTS_APPLIED", true,  // ✅ V007 verified'
echo '   "TRADE_IDEMPOTENCY_CONSTRAINTS", true,  // ✅ V007 verified'
echo ""
echo "2. Rebuild application:"
echo "   ${YELLOW}mvn clean compile${NC}"
echo ""
echo "3. Enable PROD_READY mode:"
echo "   ${YELLOW}vim config/application.properties${NC}"
echo "   Set: release.readiness=PROD_READY"
echo ""
echo "4. Start application:"
echo "   ${YELLOW}java -jar target/annupaper-v04.jar${NC}"
echo ""
echo "5. Verify startup:"
echo "   ${YELLOW}tail -f logs/application.log${NC}"
echo "   Look for: \"✅ All P0 gates resolved\""
echo ""
echo "========================================"
echo -e "${GREEN}Migration Complete!${NC}"
echo "========================================"
echo ""
echo "Backup saved to: $BACKUP_FILE"
echo "For troubleshooting, see: V007_MIGRATION_EXECUTION_GUIDE.md"
echo ""
