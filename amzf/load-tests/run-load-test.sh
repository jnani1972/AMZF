#!/bin/bash

# AMZF Load Testing Script
# Runs JMeter load tests and generates reports

set -e

# Configuration
JMETER_HOME=${JMETER_HOME:-"/opt/apache-jmeter"}
TEST_PLAN="api-load-test.jmx"
RESULTS_DIR="results/$(date +%Y%m%d_%H%M%S)"
BASE_URL=${BASE_URL:-"http://localhost:9090"}
THREADS=${THREADS:-50}
RAMP_UP=${RAMP_UP:-10}
DURATION=${DURATION:-60}

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "═══════════════════════════════════════════════════════════════"
echo "  AMZF Load Testing Suite"
echo "═══════════════════════════════════════════════════════════════"
echo ""

# Check if JMeter is installed
if [ ! -d "$JMETER_HOME" ]; then
    echo -e "${RED}ERROR: JMeter not found at $JMETER_HOME${NC}"
    echo ""
    echo "Please install JMeter:"
    echo "  brew install jmeter (macOS)"
    echo "  apt-get install jmeter (Ubuntu)"
    echo "  Or download from: https://jmeter.apache.org/download_jmeter.cgi"
    echo ""
    echo "Then set JMETER_HOME environment variable:"
    echo "  export JMETER_HOME=/path/to/apache-jmeter"
    exit 1
fi

# Check if application is running
echo -e "${YELLOW}Checking if application is running at $BASE_URL...${NC}"
if ! curl -f -s "$BASE_URL/api/health" > /dev/null; then
    echo -e "${RED}ERROR: Application not responding at $BASE_URL${NC}"
    echo ""
    echo "Please start the application first:"
    echo "  java -jar target/annu-undertow-ws-v04-0.4.0.jar"
    exit 1
fi
echo -e "${GREEN}✓ Application is running${NC}"
echo ""

# Create results directory
mkdir -p "$RESULTS_DIR"

echo "Test Configuration:"
echo "  Base URL: $BASE_URL"
echo "  Threads: $THREADS"
echo "  Ramp-up: $RAMP_UP seconds"
echo "  Duration: $DURATION seconds"
echo "  Results: $RESULTS_DIR"
echo ""

# Run JMeter test
echo -e "${YELLOW}Running load test...${NC}"
"$JMETER_HOME/bin/jmeter" -n -t "$TEST_PLAN" \
    -JBASE_URL="$BASE_URL" \
    -JTHREADS="$THREADS" \
    -JRAMP_UP="$RAMP_UP" \
    -JDURATION="$DURATION" \
    -l "$RESULTS_DIR/results.jtl" \
    -e -o "$RESULTS_DIR/html-report"

echo ""
echo -e "${GREEN}✓ Load test completed${NC}"
echo ""

# Display summary
echo "═══════════════════════════════════════════════════════════════"
echo "  Test Summary"
echo "═══════════════════════════════════════════════════════════════"

# Parse results (simplified)
if [ -f "$RESULTS_DIR/results.jtl" ]; then
    TOTAL=$(grep -c "^[0-9]" "$RESULTS_DIR/results.jtl" || echo 0)
    SUCCESS=$(grep -c ",true," "$RESULTS_DIR/results.jtl" || echo 0)
    FAILURES=$((TOTAL - SUCCESS))

    echo "Total Requests: $TOTAL"
    echo "Successful: $SUCCESS"
    echo "Failed: $FAILURES"

    if [ $TOTAL -gt 0 ]; then
        SUCCESS_RATE=$(awk "BEGIN {printf \"%.2f\", ($SUCCESS / $TOTAL) * 100}")
        echo "Success Rate: ${SUCCESS_RATE}%"

        if (( $(echo "$SUCCESS_RATE >= 95" | bc -l) )); then
            echo -e "${GREEN}✓ SUCCESS: Test passed (≥95% success rate)${NC}"
        else
            echo -e "${RED}✗ FAILED: Test failed (<95% success rate)${NC}"
        fi
    fi
fi

echo ""
echo "Detailed report: $RESULTS_DIR/html-report/index.html"
echo ""

# Open HTML report (macOS)
if [[ "$OSTYPE" == "darwin"* ]]; then
    echo -e "${YELLOW}Opening HTML report in browser...${NC}"
    open "$RESULTS_DIR/html-report/index.html"
fi

echo "═══════════════════════════════════════════════════════════════"
