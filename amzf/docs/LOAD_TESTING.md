# Load Testing Guide

**Date**: January 15, 2026
**Status**: ✅ **READY FOR EXECUTION**
**Test Suites**: JMeter + Quick Load Test

## Overview

This document describes the load testing strategy and implementation for the AMZF multi-broker trading system. Load tests validate system performance under expected and peak loads.

## Load Testing Tools

### 1. JMeter Load Test Suite ✅

**File**: `load-tests/api-load-test.jmx`
**Script**: `load-tests/run-load-test.sh`

**Features**:
- Full JMeter test plan with multiple thread groups
- Tests health endpoint and metrics endpoint
- Configurable threads, ramp-up, duration
- Generates HTML reports with graphs
- CSV results for analysis
- Response time assertions
- HTTP status code validation

**Test Scenarios**:
1. Health Check Load (50 threads, 60 seconds)
2. Metrics Endpoint Load (50 threads, 60 seconds)

**Metrics Collected**:
- Total requests
- Success/failure counts
- Response times (min, max, avg, p50, p90, p95, p99)
- Throughput (requests/second)
- Error rate
- Bandwidth usage

### 2. Quick Load Test ✅

**File**: `load-tests/quick-load-test.sh`

**Features**:
- No JMeter installation required
- Uses curl and xargs for parallel execution
- Quick smoke test (1000 requests, 50 concurrent)
- Tests health and metrics endpoints
- Calculates success rate and throughput
- Runs in <30 seconds

**Use Cases**:
- Quick validation after deployment
- CI/CD pipeline integration
- Developer smoke testing
- Pre-JMeter verification

---

## Installation

### Install JMeter (for full load tests)

**macOS (Homebrew)**:
```bash
brew install jmeter
export JMETER_HOME=/opt/homebrew/Cellar/jmeter/5.5/libexec
```

**Ubuntu/Debian**:
```bash
sudo apt-get update
sudo apt-get install jmeter
export JMETER_HOME=/usr/share/jmeter
```

**Manual Installation**:
```bash
# Download from https://jmeter.apache.org/download_jmeter.cgi
wget https://dlcdn.apache.org//jmeter/binaries/apache-jmeter-5.5.tgz
tar -xzf apache-jmeter-5.5.tgz
export JMETER_HOME=$(pwd)/apache-jmeter-5.5
```

### Quick Load Test (No installation needed)

**Requirements**: `curl`, `bc`, `xargs` (pre-installed on macOS/Linux)

```bash
# Make executable
chmod +x load-tests/quick-load-test.sh
```

---

## Running Load Tests

### Quick Load Test (Recommended for CI/CD)

**Basic Usage**:
```bash
# Start application first
java -jar target/annu-undertow-ws-v04-0.4.0.jar

# Run quick load test (default: 1000 requests, 50 concurrent)
cd load-tests
./quick-load-test.sh
```

**Custom Configuration**:
```bash
# Custom requests and concurrency
REQUESTS=5000 CONCURRENCY=100 ./quick-load-test.sh

# Custom base URL (for staging/production)
BASE_URL=https://api.amzf.com ./quick-load-test.sh
```

**Expected Output**:
```
═══════════════════════════════════════════════════════════════
  Quick Load Test
═══════════════════════════════════════════════════════════════

Configuration:
  Base URL: http://localhost:9090
  Total Requests: 1000
  Concurrency: 50

✓ Application is running

Testing /api/health endpoint...

Results for /api/health:
  Total Requests: 1000
  Successful: 1000
  Failed: 0
  Success Rate: 100.00%
  Duration: 0.856s
  Throughput: 1168.22 req/sec
  Avg Response Time: 0.042s

Testing /metrics endpoint...

Results for /metrics:
  Total Requests: 1000
  Successful: 1000
  Failed: 0
  Success Rate: 100.00%
  Duration: 1.234s
  Throughput: 810.37 req/sec
  Avg Response Time: 0.061s
```

---

### JMeter Load Test (Full analysis)

**Basic Usage**:
```bash
# Start application first
java -jar target/annu-undertow-ws-v04-0.4.0.jar

# Run JMeter load test
cd load-tests
chmod +x run-load-test.sh
./run-load-test.sh
```

**Custom Configuration**:
```bash
# Custom parameters
THREADS=100 DURATION=120 ./run-load-test.sh

# Different environment
BASE_URL=https://staging.amzf.com THREADS=50 ./run-load-test.sh
```

**Parameters**:
| Parameter | Default | Description |
|-----------|---------|-------------|
| BASE_URL | http://localhost:9090 | API base URL |
| THREADS | 50 | Number of concurrent threads |
| RAMP_UP | 10 | Ramp-up time in seconds |
| DURATION | 60 | Test duration in seconds |

**Generated Reports**:
- `results/YYYYMMDD_HHMMSS/results.jtl` - Raw results (CSV)
- `results/YYYYMMDD_HHMMSS/html-report/index.html` - Interactive HTML report

---

## Performance Benchmarks

### Expected Performance (Baseline)

Based on chaos engineering tests and system architecture:

| Metric | Target | Acceptable | Critical |
|--------|--------|------------|----------|
| Throughput | >1000 req/sec | >500 req/sec | <500 req/sec |
| Avg Response Time | <50ms | <100ms | >200ms |
| p95 Response Time | <100ms | <200ms | >500ms |
| p99 Response Time | <200ms | <500ms | >1000ms |
| Success Rate | >99% | >95% | <95% |
| Error Rate | <1% | <5% | >5% |

### Endpoint-Specific Benchmarks

**Health Endpoint (`/api/health`)**:
- **Throughput**: >2000 req/sec (lightweight endpoint)
- **Avg Response Time**: <20ms
- **p99 Response Time**: <50ms
- **Expected**: This is a simple health check, should be very fast

**Metrics Endpoint (`/metrics`)**:
- **Throughput**: >500 req/sec (heavier payload)
- **Avg Response Time**: <100ms
- **p99 Response Time**: <200ms
- **Expected**: Prometheus metrics collection is more expensive

**WebSocket Connections**:
- **Concurrent Connections**: >1000
- **Connection Setup Time**: <100ms
- **Message Latency**: <10ms
- **Reconnection Time**: <1 second

---

## Load Test Scenarios

### 1. Smoke Test (Quick validation)

**Purpose**: Quick check after deployment
**Tool**: `quick-load-test.sh`
**Duration**: <1 minute
**Load**: 1000 requests, 50 concurrent
**Success Criteria**: >95% success rate

```bash
./quick-load-test.sh
```

---

### 2. Baseline Load Test

**Purpose**: Establish performance baseline
**Tool**: JMeter
**Duration**: 5 minutes
**Load**: 50 concurrent users
**Success Criteria**:
- Success rate >99%
- Avg response time <100ms
- Throughput >500 req/sec

```bash
THREADS=50 DURATION=300 ./run-load-test.sh
```

---

### 3. Peak Load Test

**Purpose**: Test system under expected peak load
**Tool**: JMeter
**Duration**: 10 minutes
**Load**: 100 concurrent users
**Success Criteria**:
- Success rate >95%
- Avg response time <200ms
- Throughput >800 req/sec

```bash
THREADS=100 DURATION=600 ./run-load-test.sh
```

---

### 4. Stress Test

**Purpose**: Find breaking point
**Tool**: JMeter
**Duration**: 15 minutes
**Load**: 200+ concurrent users (gradually increase)
**Success Criteria**:
- System remains stable
- Graceful degradation (no crashes)
- Error messages are clear

```bash
THREADS=200 DURATION=900 ./run-load-test.sh
```

---

### 5. Endurance Test (Soak Test)

**Purpose**: Test for memory leaks and stability over time
**Tool**: JMeter
**Duration**: 2+ hours
**Load**: 50 concurrent users (sustained)
**Success Criteria**:
- No memory leaks
- Consistent performance
- No degradation over time

```bash
THREADS=50 DURATION=7200 ./run-load-test.sh
```

---

### 6. Spike Test

**Purpose**: Test sudden traffic spikes
**Tool**: JMeter (manual configuration)
**Duration**: 10 minutes
**Load**: Sudden spike from 10 to 200 users
**Success Criteria**:
- System handles spike without crashes
- Performance degrades gracefully
- Recovery after spike

---

## Analyzing Results

### JMeter HTML Report

**Key Sections**:

1. **Dashboard**
   - APDEX (Application Performance Index)
   - Overall statistics (min, max, avg, p90, p95, p99)
   - Success/error rate

2. **Over Time Graphs**
   - Response times over time
   - Active threads over time
   - Transactions per second
   - Response times percentiles

3. **Throughput**
   - Requests per second
   - Bytes received per second
   - Transactions per second

4. **Response Times**
   - Response time distribution
   - Response time percentiles
   - Response time vs. threads

**Red Flags**:
- ❌ Success rate <95%
- ❌ Avg response time >200ms
- ❌ p99 response time >1000ms
- ❌ Throughput degradation over time
- ❌ Increasing error rate over time
- ❌ Memory usage continuously increasing

**Green Flags**:
- ✅ Success rate >99%
- ✅ Avg response time <100ms
- ✅ Stable throughput over time
- ✅ Consistent memory usage
- ✅ Fast error recovery

---

### Quick Load Test Analysis

**Metrics to Check**:

```bash
Results for /api/health:
  Total Requests: 1000
  Successful: 1000        # ✅ Should be close to total
  Failed: 0               # ✅ Should be 0 or very low
  Success Rate: 100.00%   # ✅ Should be >95%
  Duration: 0.856s        # Time to complete all requests
  Throughput: 1168.22 req/sec  # ✅ Should be >500
  Avg Response Time: 0.042s    # ✅ Should be <0.100s
```

**Interpreting Results**:

| Success Rate | Interpretation | Action |
|--------------|----------------|--------|
| 100% | ✅ Perfect | System healthy |
| 95-99% | ⚠️ Good but investigate | Check error logs |
| 90-95% | ⚠️ Concerning | Review errors, may need scaling |
| <90% | ❌ Critical | System under stress, needs attention |

| Throughput | Interpretation | Action |
|------------|----------------|--------|
| >1000 req/sec | ✅ Excellent | System performing well |
| 500-1000 req/sec | ✅ Good | Expected for standard load |
| 100-500 req/sec | ⚠️ Moderate | May need optimization |
| <100 req/sec | ❌ Poor | Investigate bottlenecks |

| Avg Response Time | Interpretation | Action |
|-------------------|----------------|--------|
| <50ms | ✅ Excellent | Very responsive |
| 50-100ms | ✅ Good | Acceptable performance |
| 100-200ms | ⚠️ Moderate | Consider optimization |
| >200ms | ❌ Slow | Investigate latency sources |

---

## Performance Optimization

### If Throughput is Low (<500 req/sec)

**Check**:
1. Database connection pool size (increase to 20-50)
2. Thread pool size in Undertow (increase worker threads)
3. Network latency (use local testing)
4. Logging overhead (reduce to INFO level)
5. GC pauses (tune JVM heap size)

**Optimize**:
```bash
# Increase Undertow worker threads
java -Xmx4g -Xms4g \
  -Dio.undertow.io-threads=16 \
  -Dio.undertow.worker-threads=200 \
  -jar target/annu-undertow-ws-v04-0.4.0.jar
```

---

### If Response Time is High (>200ms avg)

**Check**:
1. Database query performance (add indexes)
2. External API latency (broker APIs)
3. Lock contention (use profiler)
4. Serialization overhead (use efficient formats)
5. Network I/O (use connection pooling)

**Profile**:
```bash
# Enable JVM profiling
java -agentlib:hprof=cpu=samples,depth=10 \
  -jar target/annu-undertow-ws-v04-0.4.0.jar
```

---

### If Error Rate is High (>5%)

**Check**:
1. Application logs for errors
2. Database connection pool exhaustion
3. Rate limiting from broker APIs
4. Memory issues (OutOfMemoryError)
5. Thread pool exhaustion

**Debug**:
```bash
# Check application logs
tail -f /var/log/amzf/app.log

# Check Prometheus metrics
curl http://localhost:9090/metrics | grep error
```

---

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Load Test

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  load-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Build application
        run: mvn clean package -DskipTests

      - name: Start application
        run: |
          java -jar target/annu-undertow-ws-v04-0.4.0.jar &
          sleep 30  # Wait for startup

      - name: Run quick load test
        run: |
          cd load-tests
          chmod +x quick-load-test.sh
          ./quick-load-test.sh

      - name: Check success criteria
        run: |
          # Add assertions on test results
          # Fail if success rate <95%
          echo "Load test passed"
```

---

### Jenkins Pipeline Example

```groovy
pipeline {
    agent any

    stages {
        stage('Build') {
            steps {
                sh 'mvn clean package'
            }
        }

        stage('Start Application') {
            steps {
                sh '''
                    java -jar target/annu-undertow-ws-v04-0.4.0.jar &
                    echo $! > app.pid
                    sleep 30
                '''
            }
        }

        stage('Load Test') {
            steps {
                sh '''
                    cd load-tests
                    chmod +x quick-load-test.sh
                    ./quick-load-test.sh
                '''
            }
        }

        stage('Analyze Results') {
            steps {
                script {
                    // Parse results and fail if criteria not met
                    def successRate = sh(
                        script: "grep 'Success Rate' /tmp/load-test-results.txt | awk '{print \$3}'",
                        returnStdout: true
                    ).trim()

                    if (successRate.toFloat() < 95.0) {
                        error("Load test failed: Success rate ${successRate}% < 95%")
                    }
                }
            }
        }
    }

    post {
        always {
            sh 'kill $(cat app.pid) || true'
        }
    }
}
```

---

## Monthly Load Testing Schedule

### Weekly (Every Monday)
- ✅ Quick load test (1000 requests, 50 concurrent)
- ✅ Check success rate >95%
- ✅ Verify avg response time <100ms
- **Duration**: 5 minutes

### Bi-Weekly (1st and 15th)
- ✅ Baseline load test (5 minutes, 50 users)
- ✅ Review JMeter HTML report
- ✅ Compare with previous baseline
- **Duration**: 15 minutes

### Monthly (1st of month)
- ✅ Peak load test (10 minutes, 100 users)
- ✅ Stress test (15 minutes, 200 users)
- ✅ Generate performance report
- ✅ Update capacity planning
- **Duration**: 1 hour

### Quarterly (Jan, Apr, Jul, Oct)
- ✅ Endurance test (2 hours, 50 users)
- ✅ Spike test (sudden traffic increase)
- ✅ Full performance audit
- ✅ Optimize bottlenecks
- **Duration**: Half day

---

## Troubleshooting

### Common Issues

**Issue**: Application not responding
```bash
# Check if application is running
ps aux | grep java

# Check logs
tail -f /var/log/amzf/app.log

# Check port binding
lsof -i :9090
```

**Issue**: JMeter not found
```bash
# Check JMeter installation
which jmeter
echo $JMETER_HOME

# Install JMeter
brew install jmeter  # macOS
```

**Issue**: Low throughput
```bash
# Increase file descriptor limit
ulimit -n 10000

# Check system resources
top
vmstat 1

# Monitor network
netstat -an | grep 9090
```

**Issue**: High error rate
```bash
# Check application errors
grep ERROR /var/log/amzf/app.log

# Check database connections
psql -c "SELECT count(*) FROM pg_stat_activity"

# Check Prometheus metrics
curl http://localhost:9090/metrics | grep -E "error|failure"
```

---

## Conclusion

✅ **Load testing suite ready for execution**
✅ **Two test tools: JMeter (full analysis) + Quick test (fast validation)**
✅ **Performance benchmarks defined**
✅ **CI/CD integration examples provided**
✅ **Monthly testing schedule established**

**Next Steps**:
1. Run quick load test to establish baseline
2. Run JMeter load test for detailed analysis
3. Integrate quick load test into CI/CD pipeline
4. Schedule monthly load testing
5. Monitor performance trends over time

**Last Updated**: January 15, 2026
**Status**: ✅ **READY FOR TESTING**
