#!/bin/bash
# ============================================================================
# Admin API Test Script
# ============================================================================
# Purpose: Test Admin configuration endpoints to verify they work correctly
# Usage: ./scripts/test-admin-api.sh [base_url]
# Example: ./scripts/test-admin-api.sh http://localhost:8080
# ============================================================================

set -e  # Exit on error

# Configuration
BASE_URL="${1:-http://localhost:8080}"
API_ENDPOINT="${BASE_URL}/api/admin/trailing-stops/config"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test counter
TESTS_RUN=0
TESTS_PASSED=0
TESTS_FAILED=0

# Helper functions
log_test() {
    echo -e "\n${YELLOW}[TEST]${NC} $1"
    TESTS_RUN=$((TESTS_RUN + 1))
}

log_pass() {
    echo -e "${GREEN}[PASS]${NC} $1"
    TESTS_PASSED=$((TESTS_PASSED + 1))
}

log_fail() {
    echo -e "${RED}[FAIL]${NC} $1"
    TESTS_FAILED=$((TESTS_FAILED + 1))
}

log_info() {
    echo -e "${YELLOW}[INFO]${NC} $1"
}

# ============================================================================
# Test 1: GET /api/admin/trailing-stops/config (Get Default Config)
# ============================================================================
log_test "GET ${API_ENDPOINT} - Retrieve configuration"

RESPONSE=$(curl -s -w "\n%{http_code}" "${API_ENDPOINT}")
HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 200 ]; then
    log_pass "HTTP 200 OK received"

    # Verify JSON structure
    if echo "$BODY" | jq -e '.activationPercent' > /dev/null 2>&1; then
        log_pass "Response contains 'activationPercent' field"
    else
        log_fail "Response missing 'activationPercent' field"
    fi

    if echo "$BODY" | jq -e '.trailingPercent' > /dev/null 2>&1; then
        log_pass "Response contains 'trailingPercent' field"
    else
        log_fail "Response missing 'trailingPercent' field"
    fi

    if echo "$BODY" | jq -e '.updateFrequency' > /dev/null 2>&1; then
        log_pass "Response contains 'updateFrequency' field"
    else
        log_fail "Response missing 'updateFrequency' field"
    fi

    # Display current config
    log_info "Current Configuration:"
    echo "$BODY" | jq '.'
else
    log_fail "Expected HTTP 200, got HTTP $HTTP_CODE"
    log_info "Response: $BODY"
fi

# ============================================================================
# Test 2: POST /api/admin/trailing-stops/config (Update Config - Valid)
# ============================================================================
log_test "POST ${API_ENDPOINT} - Update with valid configuration"

NEW_CONFIG='{
  "activationPercent": 1.5,
  "trailingPercent": 0.75,
  "updateFrequency": "BRICK",
  "minMovePercent": 0.2,
  "maxLossPercent": 2.5,
  "lockProfitPercent": 4.0
}'

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${API_ENDPOINT}" \
    -H "Content-Type: application/json" \
    -d "$NEW_CONFIG")
HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 200 ]; then
    log_pass "HTTP 200 OK received"

    # Verify success response
    if echo "$BODY" | jq -e '.success' | grep -q "true"; then
        log_pass "Response indicates success"
    else
        log_fail "Response does not indicate success"
    fi
else
    log_fail "Expected HTTP 200, got HTTP $HTTP_CODE"
    log_info "Response: $BODY"
fi

# ============================================================================
# Test 3: GET /api/admin/trailing-stops/config (Verify Update)
# ============================================================================
log_test "GET ${API_ENDPOINT} - Verify configuration was updated"

RESPONSE=$(curl -s -w "\n%{http_code}" "${API_ENDPOINT}")
HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 200 ]; then
    log_pass "HTTP 200 OK received"

    # Verify updated values
    ACTIVATION=$(echo "$BODY" | jq -r '.activationPercent')
    TRAILING=$(echo "$BODY" | jq -r '.trailingPercent')
    FREQUENCY=$(echo "$BODY" | jq -r '.updateFrequency')

    if [ "$ACTIVATION" = "1.5" ]; then
        log_pass "activationPercent updated correctly (1.5)"
    else
        log_fail "activationPercent not updated (expected 1.5, got $ACTIVATION)"
    fi

    if [ "$TRAILING" = "0.75" ]; then
        log_pass "trailingPercent updated correctly (0.75)"
    else
        log_fail "trailingPercent not updated (expected 0.75, got $TRAILING)"
    fi

    if [ "$FREQUENCY" = "BRICK" ]; then
        log_pass "updateFrequency updated correctly (BRICK)"
    else
        log_fail "updateFrequency not updated (expected BRICK, got $FREQUENCY)"
    fi

    log_info "Updated Configuration:"
    echo "$BODY" | jq '.'
else
    log_fail "Expected HTTP 200, got HTTP $HTTP_CODE"
fi

# ============================================================================
# Test 4: POST /api/admin/trailing-stops/config (Invalid - Out of Range)
# ============================================================================
log_test "POST ${API_ENDPOINT} - Reject invalid configuration (out of range)"

INVALID_CONFIG='{
  "activationPercent": 150.0,
  "trailingPercent": 0.5,
  "updateFrequency": "TICK",
  "minMovePercent": 0.1,
  "maxLossPercent": 2.0,
  "lockProfitPercent": 3.0
}'

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${API_ENDPOINT}" \
    -H "Content-Type: application/json" \
    -d "$INVALID_CONFIG")
HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 400 ]; then
    log_pass "HTTP 400 Bad Request received for invalid config"
    log_info "Error message: $BODY"
else
    log_fail "Expected HTTP 400, got HTTP $HTTP_CODE (should reject values > 100)"
fi

# ============================================================================
# Test 5: POST /api/admin/trailing-stops/config (Invalid - Bad Frequency)
# ============================================================================
log_test "POST ${API_ENDPOINT} - Reject invalid updateFrequency"

INVALID_CONFIG='{
  "activationPercent": 1.0,
  "trailingPercent": 0.5,
  "updateFrequency": "INVALID",
  "minMovePercent": 0.1,
  "maxLossPercent": 2.0,
  "lockProfitPercent": 3.0
}'

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${API_ENDPOINT}" \
    -H "Content-Type: application/json" \
    -d "$INVALID_CONFIG")
HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 400 ]; then
    log_pass "HTTP 400 Bad Request received for invalid frequency"
    log_info "Error message: $BODY"
else
    log_fail "Expected HTTP 400, got HTTP $HTTP_CODE (should reject invalid frequency)"
fi

# ============================================================================
# Test 6: POST /api/admin/trailing-stops/config (Restore Defaults)
# ============================================================================
log_test "POST ${API_ENDPOINT} - Restore default configuration"

DEFAULT_CONFIG='{
  "activationPercent": 1.0,
  "trailingPercent": 0.5,
  "updateFrequency": "TICK",
  "minMovePercent": 0.1,
  "maxLossPercent": 2.0,
  "lockProfitPercent": 3.0
}'

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${API_ENDPOINT}" \
    -H "Content-Type: application/json" \
    -d "$DEFAULT_CONFIG")
HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 200 ]; then
    log_pass "HTTP 200 OK - Defaults restored"
else
    log_fail "Expected HTTP 200, got HTTP $HTTP_CODE"
fi

# ============================================================================
# Test Summary
# ============================================================================
echo ""
echo "============================================"
echo "Test Summary"
echo "============================================"
echo "Total Tests:  $TESTS_RUN"
echo -e "Passed:       ${GREEN}$TESTS_PASSED${NC}"
echo -e "Failed:       ${RED}$TESTS_FAILED${NC}"
echo "============================================"

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "${GREEN}✓ All tests passed!${NC}"
    exit 0
else
    echo -e "${RED}✗ Some tests failed${NC}"
    exit 1
fi
