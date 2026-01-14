#!/bin/bash

# Quick Load Test (No JMeter Required)
# Uses curl and parallel execution for simple load testing

set -e

BASE_URL=${BASE_URL:-"http://localhost:9090"}
REQUESTS=${REQUESTS:-1000}
CONCURRENCY=${CONCURRENCY:-50}

echo "═══════════════════════════════════════════════════════════════"
echo "  Quick Load Test"
echo "═══════════════════════════════════════════════════════════════"
echo ""
echo "Configuration:"
echo "  Base URL: $BASE_URL"
echo "  Total Requests: $REQUESTS"
echo "  Concurrency: $CONCURRENCY"
echo ""

# Check if application is running
echo "Checking if application is running..."
if ! curl -f -s "$BASE_URL/api/health" > /dev/null 2>&1; then
    echo "ERROR: Application not responding at $BASE_URL"
    echo "Please start the application first"
    exit 1
fi
echo "✓ Application is running"
echo ""

# Test /api/health endpoint
echo "Testing /api/health endpoint..."
START=$(date +%s%N)

seq 1 $REQUESTS | xargs -P $CONCURRENCY -I {} \
    curl -s -o /dev/null -w "%{http_code},%{time_total}\n" \
    "$BASE_URL/api/health" > /tmp/health-results.txt

END=$(date +%s%N)
DURATION=$(echo "scale=3; ($END - $START) / 1000000000" | bc)

# Calculate statistics
TOTAL=$(wc -l < /tmp/health-results.txt | tr -d ' ')
SUCCESS=$(grep -c "^200," /tmp/health-results.txt || echo 0)
FAILURES=$((TOTAL - SUCCESS))
SUCCESS_RATE=$(awk "BEGIN {printf \"%.2f\", ($SUCCESS / $TOTAL) * 100}")
THROUGHPUT=$(awk "BEGIN {printf \"%.2f\", $TOTAL / $DURATION}")

# Calculate average response time
AVG_TIME=$(awk -F',' '{sum+=$2; count++} END {printf "%.3f", sum/count}' /tmp/health-results.txt)

echo ""
echo "Results for /api/health:"
echo "  Total Requests: $TOTAL"
echo "  Successful: $SUCCESS"
echo "  Failed: $FAILURES"
echo "  Success Rate: ${SUCCESS_RATE}%"
echo "  Duration: ${DURATION}s"
echo "  Throughput: ${THROUGHPUT} req/sec"
echo "  Avg Response Time: ${AVG_TIME}s"
echo ""

# Test /metrics endpoint
echo "Testing /metrics endpoint..."
START=$(date +%s%N)

seq 1 $REQUESTS | xargs -P $CONCURRENCY -I {} \
    curl -s -o /dev/null -w "%{http_code},%{time_total}\n" \
    "$BASE_URL/metrics" > /tmp/metrics-results.txt

END=$(date +%s%N)
DURATION=$(echo "scale=3; ($END - $START) / 1000000000" | bc)

# Calculate statistics
TOTAL=$(wc -l < /tmp/metrics-results.txt | tr -d ' ')
SUCCESS=$(grep -c "^200," /tmp/metrics-results.txt || echo 0)
FAILURES=$((TOTAL - SUCCESS))
SUCCESS_RATE=$(awk "BEGIN {printf \"%.2f\", ($SUCCESS / $TOTAL) * 100}")
THROUGHPUT=$(awk "BEGIN {printf \"%.2f\", $TOTAL / $DURATION}")
AVG_TIME=$(awk -F',' '{sum+=$2; count++} END {printf "%.3f", sum/count}' /tmp/metrics-results.txt)

echo ""
echo "Results for /metrics:"
echo "  Total Requests: $TOTAL"
echo "  Successful: $SUCCESS"
echo "  Failed: $FAILURES"
echo "  Success Rate: ${SUCCESS_RATE}%"
echo "  Duration: ${DURATION}s"
echo "  Throughput: ${THROUGHPUT} req/sec"
echo "  Avg Response Time: ${AVG_TIME}s"
echo ""

# Cleanup
rm -f /tmp/health-results.txt /tmp/metrics-results.txt

echo "═══════════════════════════════════════════════════════════════"
echo "  Test Complete"
echo "═══════════════════════════════════════════════════════════════"
