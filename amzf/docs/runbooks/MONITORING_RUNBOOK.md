# Monitoring & Alerting Runbook

**Last Updated**: January 15, 2026
**Owner**: SRE Team
**Review Frequency**: Monthly

## Overview

This runbook describes the monitoring strategy, key metrics, alerting rules, and daily operational procedures for the AMZF trading system.

## Monitoring Stack

| Component | Purpose | URL |
|-----------|---------|-----|
| **Prometheus** | Metrics collection | http://localhost:9090 |
| **Grafana** | Visualization | http://localhost:3000 |
| **Alertmanager** | Alert routing | http://localhost:9093 |
| **Application Logs** | System logs | `/var/log/amzf/app.log` |
| **Postgres Logs** | Database logs | `/var/log/postgresql/` |

---

## Key Metrics to Monitor

### 1. Application Health

**Metric**: `broker_health_status`
**Type**: Gauge
**Labels**: `broker`
**Values**: 1.0 (UP) or 0.0 (DOWN)

**Good**: All brokers showing 1.0
**Warning**: One broker at 0.0 (failover working)
**Critical**: Multiple brokers at 0.0

**Query**:
```promql
broker_health_status
```

**Dashboard Panel**: "Broker Health Status" (gauge visualization)

---

### 2. Order Success Rate

**Metric**: `broker_orders_total`
**Type**: Counter
**Labels**: `broker`, `status`
**Values**: success, failure

**Good**: >99% success rate
**Warning**: 95-99% success rate
**Critical**: <95% success rate

**Query**:
```promql
# Success rate over last 5 minutes
sum(rate(broker_orders_total{status="success"}[5m]))
/
sum(rate(broker_orders_total[5m]))
* 100
```

**Alert Threshold**: <95% for 5 minutes

---

### 3. Order Latency

**Metric**: `broker_order_latency_seconds`
**Type**: Histogram
**Labels**: `broker`

**Good**: p99 <200ms
**Warning**: p99 200-500ms
**Critical**: p99 >500ms

**Query**:
```promql
# p99 latency
histogram_quantile(0.99,
  rate(broker_order_latency_seconds_bucket[5m])
)

# p95 latency
histogram_quantile(0.95,
  rate(broker_order_latency_seconds_bucket[5m])
)

# p50 latency (median)
histogram_quantile(0.50,
  rate(broker_order_latency_seconds_bucket[5m])
)
```

**Alert Threshold**: p99 >1 second for 5 minutes

---

### 4. Throughput (Requests/Second)

**Metric**: `broker_orders_total`
**Type**: Counter

**Good**: >100 req/sec during market hours
**Warning**: 50-100 req/sec
**Critical**: <50 req/sec

**Query**:
```promql
# Total orders per second
sum(rate(broker_orders_total[1m]))

# Per broker
sum(rate(broker_orders_total[1m])) by (broker)
```

---

### 5. Rate Limit Utilization

**Metric**: `broker_rate_utilization`
**Type**: Gauge
**Labels**: `broker`
**Values**: 0.0 (0%) to 1.0 (100%)

**Good**: <70% utilization
**Warning**: 70-85% utilization
**Critical**: >85% utilization

**Query**:
```promql
broker_rate_utilization
```

**Alert Threshold**: >0.85 for 5 minutes

---

### 6. Rate Limit Hits

**Metric**: `broker_rate_limit_hits_total`
**Type**: Counter
**Labels**: `broker`, `limit_type`

**Good**: 0 hits
**Warning**: 1-10 hits/hour
**Critical**: >10 hits/hour

**Query**:
```promql
# Rate limit hits per hour
sum(increase(broker_rate_limit_hits_total[1h])) by (broker)
```

**Alert Threshold**: >10 hits in 1 hour

---

### 7. Authentication Success/Failure

**Metric**: `broker_authentications_total`
**Type**: Counter
**Labels**: `broker`, `status`

**Good**: All success, 0 failures
**Warning**: 1-2 failures
**Critical**: >2 failures

**Query**:
```promql
# Authentication failures
sum(increase(broker_authentications_total{status="failure"}[1h])) by (broker)
```

**Alert Threshold**: >1 failure in 1 hour

---

### 8. Instrument Load Performance

**Metric**: `instrument_load_duration_seconds`
**Type**: Histogram
**Labels**: `broker`

**Good**: <5 seconds
**Warning**: 5-10 seconds
**Critical**: >10 seconds

**Query**:
```promql
# Last instrument load duration
instrument_load_duration_seconds_sum / instrument_load_duration_seconds_count
```

---

### 9. System Resources

**CPU Usage**:
```promql
# CPU usage (node_exporter)
100 - (avg by(instance)(irate(node_cpu_seconds_total{mode="idle"}[5m])) * 100)
```

**Memory Usage**:
```promql
# Memory usage (node_exporter)
(1 - (node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes)) * 100
```

**Disk Usage**:
```promql
# Disk usage (node_exporter)
(1 - (node_filesystem_avail_bytes{mountpoint="/"} / node_filesystem_size_bytes{mountpoint="/"})) * 100
```

---

## Alert Rules

### Critical Alerts (P0)

**Alert**: ApplicationDown
```yaml
- alert: ApplicationDown
  expr: up{job="amzf"} == 0
  for: 1m
  labels:
    severity: critical
  annotations:
    summary: "AMZF application is down"
    description: "Application has been down for 1 minute"
```

**Alert**: HighErrorRate
```yaml
- alert: HighErrorRate
  expr: |
    (
      sum(rate(broker_orders_total{status="failure"}[5m]))
      /
      sum(rate(broker_orders_total[5m]))
    ) > 0.05
  for: 5m
  labels:
    severity: critical
  annotations:
    summary: "High error rate detected"
    description: "Error rate is {{ $value | humanizePercentage }}"
```

**Alert**: AllBrokersDown
```yaml
- alert: AllBrokersDown
  expr: sum(broker_health_status) == 0
  for: 1m
  labels:
    severity: critical
  annotations:
    summary: "All brokers are down"
    description: "No brokers are available for trading"
```

---

### Warning Alerts (P1)

**Alert**: BrokerDown
```yaml
- alert: BrokerDown
  expr: broker_health_status == 0
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "Broker {{ $labels.broker }} is down"
    description: "Broker has been down for 5 minutes"
```

**Alert**: HighLatency
```yaml
- alert: HighLatency
  expr: |
    histogram_quantile(0.99,
      rate(broker_order_latency_seconds_bucket[5m])
    ) > 1.0
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "High latency detected"
    description: "p99 latency is {{ $value }}s"
```

**Alert**: HighRateUtilization
```yaml
- alert: HighRateUtilization
  expr: broker_rate_utilization > 0.85
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "High rate limit utilization for {{ $labels.broker }}"
    description: "Utilization is {{ $value | humanizePercentage }}"
```

**Alert**: AuthenticationFailures
```yaml
- alert: AuthenticationFailures
  expr: |
    sum(increase(broker_authentications_total{status="failure"}[1h])) by (broker) > 1
  labels:
    severity: warning
  annotations:
    summary: "Authentication failures for {{ $labels.broker }}"
    description: "{{ $value }} failures in last hour"
```

---

### Info Alerts (P2/P3)

**Alert**: ModerateLatency
```yaml
- alert: ModerateLatency
  expr: |
    histogram_quantile(0.95,
      rate(broker_order_latency_seconds_bucket[5m])
    ) > 0.5
  for: 10m
  labels:
    severity: info
  annotations:
    summary: "Moderate latency detected"
    description: "p95 latency is {{ $value }}s"
```

**Alert**: RateLimitHits
```yaml
- alert: RateLimitHits
  expr: |
    sum(increase(broker_rate_limit_hits_total[1h])) by (broker) > 10
  labels:
    severity: info
  annotations:
    summary: "Rate limit hits for {{ $labels.broker }}"
    description: "{{ $value }} hits in last hour"
```

---

## Grafana Dashboards

### Main Dashboard: "AMZF Trading System"

**Panels**:

1. **Broker Health Status** (Stat panel)
   - Shows current health of all brokers
   - Green = UP, Red = DOWN

2. **Order Success Rate** (Gauge)
   - Current success rate (last 5 min)
   - Thresholds: Green >99%, Yellow 95-99%, Red <95%

3. **Orders Per Second** (Graph)
   - Time series of order throughput
   - Per broker breakdown

4. **Order Latency (p50, p95, p99)** (Graph)
   - Time series of latency percentiles
   - Separate lines for each percentile

5. **Rate Limit Utilization** (Bar gauge)
   - Per-broker utilization
   - Color-coded: Green <70%, Yellow 70-85%, Red >85%

6. **Rate Limit Hits** (Graph)
   - Time series of rate limit hits
   - Per broker

7. **Authentication Success/Failure** (Stat panel)
   - Total authentications
   - Success vs failure count

8. **Instrument Count** (Stat panel)
   - Total instruments loaded per broker

9. **System Resources** (Graphs)
   - CPU, Memory, Disk usage
   - Network I/O

10. **Error Log Stream** (Logs panel)
    - Real-time error logs from application

---

## Daily Monitoring Procedures

### Morning Check (09:00 - Market Open)

**Duration**: 10 minutes

```bash
# 1. Check application health
curl http://localhost:9090/api/health

# 2. Check all brokers are UP
curl http://localhost:9090/metrics | grep broker_health_status

# 3. Check for errors in last 12 hours
sudo journalctl -u amzf --since "12 hours ago" | grep ERROR | wc -l

# 4. Check Prometheus targets
curl http://localhost:9090/prometheus/api/v1/targets | jq '.data.activeTargets[] | select(.health=="down")'

# 5. Review Grafana dashboard
# Open: http://localhost:3000/d/amzf-main/amzf-trading-system

# 6. Check for pending alerts
curl http://localhost:9093/api/v1/alerts | jq '.data[] | select(.state=="pending")'
```

**Expected Results**:
- Health: UP
- All brokers: 1.0 (UP)
- Errors: <10 in last 12 hours
- Prometheus targets: All UP
- Active alerts: 0

---

### Mid-Day Check (14:00 - During Market Hours)

**Duration**: 5 minutes

```bash
# 1. Check throughput
curl http://localhost:9090/metrics | grep broker_orders_total

# 2. Check success rate
# (Calculate from metrics)

# 3. Check latency
curl http://localhost:9090/metrics | grep broker_order_latency_seconds

# 4. Check rate limit utilization
curl http://localhost:9090/metrics | grep broker_rate_utilization

# 5. Quick log check
sudo journalctl -u amzf --since "1 hour ago" | grep ERROR
```

**Expected Results**:
- Throughput: >100 req/sec during active trading
- Success rate: >99%
- Latency p99: <200ms
- Rate utilization: <70%
- Recent errors: 0

---

### End-of-Day Check (16:00 - After Market Close)

**Duration**: 15 minutes

```bash
# 1. Review day's metrics
# Open Grafana dashboard and review:
# - Total orders placed
# - Success rate for the day
# - Peak throughput
# - Average latency

# 2. Check for any incidents
# Review Alertmanager history

# 3. Generate daily report
curl http://localhost:9090/api/v1/query?query='sum(increase(broker_orders_total[1d])) by (broker,status)'

# 4. Check logs for patterns
sudo journalctl -u amzf --since "1 day ago" | grep WARNING | sort | uniq -c | sort -nr

# 5. Database health check
psql -h localhost -U amzf_user -d amzf_prod -c "
  SELECT
    schemaname,
    tablename,
    n_live_tup as row_count,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as size
  FROM pg_stat_user_tables
  ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC
  LIMIT 10;
"

# 6. Backup verification
ls -lh /backup/ | tail -5
```

**Document**:
- Total orders: [NUMBER]
- Success rate: [PERCENTAGE]
- Issues encountered: [LIST or NONE]
- Actions taken: [LIST or N/A]

---

## Weekly Monitoring Tasks

### Every Monday Morning

**Duration**: 30 minutes

1. **Review Last Week's Metrics**
   - Total orders placed
   - Average success rate
   - Incidents count
   - Downtime (if any)

2. **Check Disk Usage Growth**
```bash
# Database size trend
psql -h localhost -U amzf_user -d amzf_prod -c "
  SELECT pg_size_pretty(pg_database_size('amzf_prod'));
"

# Log files size
du -sh /var/log/amzf/

# Application directory
du -sh /opt/amzf/
```

3. **Review and Rotate Logs**
```bash
# Archive old logs (>30 days)
find /var/log/amzf/ -name "*.log.*" -mtime +30 -exec gzip {} \;

# Delete very old logs (>90 days)
find /var/log/amzf/ -name "*.log.*.gz" -mtime +90 -delete
```

4. **Check Certificate Expiry**
```bash
# SSL certificate check
echo | openssl s_client -servername amzf.com -connect amzf.com:443 2>/dev/null | openssl x509 -noout -dates
```

5. **Run Quick Load Test**
```bash
cd load-tests
./quick-load-test.sh

# Document results
echo "$(date): Load test passed - $(grep 'Success Rate' /tmp/load-test-results.txt)" >> /var/log/amzf/weekly-checks.log
```

6. **Update Runbooks** (if needed)
   - Document any new procedures
   - Update contact information
   - Revise based on recent incidents

---

## Monthly Monitoring Tasks

### First Monday of Month

**Duration**: 2 hours

1. **Performance Review**
   - Analyze 30-day trends
   - Identify performance bottlenecks
   - Plan optimizations

2. **Capacity Planning**
```bash
# Calculate growth rate
# - Orders per day growth
# - Database growth rate
# - Memory usage trend

# Predict when scaling needed
```

3. **Update Dashboards**
   - Add new metrics if needed
   - Improve visualizations
   - Create new panels

4. **Alert Rule Review**
   - Check false positive rate
   - Adjust thresholds if needed
   - Add new alert rules

5. **Security Audit**
   - Review access logs
   - Check for suspicious activity
   - Verify secrets rotation schedule

6. **Dependency Updates**
   - Check for Maven dependency updates
   - Security vulnerability scan
   - Plan updates

---

## Troubleshooting Common Issues

### Metric Not Appearing in Prometheus

**Check**:
```bash
# 1. Verify metric exists
curl http://localhost:9090/metrics | grep metric_name

# 2. Check Prometheus scraping
curl http://localhost:9090/prometheus/api/v1/targets

# 3. Check Prometheus logs
sudo journalctl -u prometheus -n 100

# 4. Verify scrape configuration
cat /etc/prometheus/prometheus.yml | grep amzf
```

---

### Dashboard Not Showing Data

**Check**:
```bash
# 1. Verify Prometheus data source in Grafana
curl http://localhost:3000/api/datasources

# 2. Check query in panel
# Edit panel, check query syntax

# 3. Verify time range
# Check dashboard time picker

# 4. Check Grafana logs
sudo journalctl -u grafana -n 100
```

---

### Alert Not Firing

**Check**:
```bash
# 1. Test alert expression
curl 'http://localhost:9090/api/v1/query?query=YOUR_ALERT_EXPRESSION'

# 2. Check alert rules loaded
curl http://localhost:9090/api/v1/rules

# 3. Check Alertmanager
curl http://localhost:9093/api/v1/alerts

# 4. Check alert routing
cat /etc/prometheus/alertmanager.yml
```

---

## Useful Queries

### Top 10 Slowest Requests (Last Hour)
```promql
topk(10,
  histogram_quantile(0.99,
    rate(broker_order_latency_seconds_bucket{broker="UPSTOX"}[1h])
  )
)
```

### Error Rate by Broker
```promql
sum(rate(broker_orders_total{status="failure"}[5m])) by (broker)
/
sum(rate(broker_orders_total[5m])) by (broker)
* 100
```

### Requests Per Broker
```promql
sum(increase(broker_orders_total[1h])) by (broker)
```

### Memory Usage Trend
```promql
process_resident_memory_bytes
```

### GC Time Percentage
```promql
rate(jvm_gc_collection_seconds_sum[5m]) * 100
```

---

## Conclusion

**Daily Monitoring Checklist**:
- [x] Morning health check (09:00)
- [x] Mid-day performance check (14:00)
- [x] End-of-day review (16:00)
- [x] Logs reviewed for errors
- [x] Metrics reviewed for anomalies
- [x] Incidents documented

**Weekly Tasks**:
- [x] Metrics review
- [x] Disk usage check
- [x] Log rotation
- [x] Certificate expiry check
- [x] Load test
- [x] Runbook updates

**Monthly Tasks**:
- [x] Performance review
- [x] Capacity planning
- [x] Dashboard updates
- [x] Alert rule review
- [x] Security audit
- [x] Dependency updates

**Remember**:
- Monitor proactively, don't wait for alerts
- Trends are as important as current values
- Document all incidents and resolutions
- Continuously improve monitoring coverage

**Status**: ✅ **MONITORING OPERATIONAL**
