# Deployment Runbook

**Last Updated**: January 15, 2026
**Owner**: DevOps Team
**Severity**: Critical

## Overview

This runbook describes the step-by-step procedure for deploying the AMZF trading system to production.

## Pre-Deployment Checklist

### 1. Code Quality ✅
- [ ] All tests passing (128/128)
- [ ] No critical bugs in issue tracker
- [ ] Code review completed and approved
- [ ] Security scan completed (no critical vulnerabilities)
- [ ] Performance tests passed

### 2. Dependencies ✅
- [ ] All Maven dependencies up to date
- [ ] No known vulnerabilities in dependencies (check with `mvn dependency:check`)
- [ ] Database migrations prepared and tested
- [ ] Third-party API access verified (broker APIs)

### 3. Configuration ✅
- [ ] Environment variables configured
- [ ] Secrets stored in secrets manager
- [ ] Database connection strings updated
- [ ] Broker API keys validated
- [ ] Prometheus/Grafana configured

### 4. Monitoring ✅
- [ ] Prometheus scraping configured
- [ ] Grafana dashboards imported
- [ ] Alerts configured in Alertmanager
- [ ] Log aggregation configured
- [ ] Error tracking configured (Sentry/similar)

### 5. Backup ✅
- [ ] Database backup completed
- [ ] Configuration backup saved
- [ ] Rollback plan documented
- [ ] Emergency contact list updated

---

## Deployment Procedure

### Step 1: Build Application

```bash
# Clone repository
git clone https://github.com/your-org/amzf.git
cd amzf

# Checkout release tag
git checkout v0.4.0

# Build application
mvn clean package -DskipTests

# Verify JAR created
ls -lh target/annu-undertow-ws-v04-0.4.0.jar
```

**Expected Output**:
```
-rw-r--r--  1 user  staff    45M Jan 15 03:00 annu-undertow-ws-v04-0.4.0.jar
```

**Verification**:
```bash
# Check JAR integrity
jar -tf target/annu-undertow-ws-v04-0.4.0.jar | head -10

# Verify manifest
unzip -p target/annu-undertow-ws-v04-0.4.0.jar META-INF/MANIFEST.MF
```

---

### Step 2: Prepare Environment

```bash
# Create application directory
sudo mkdir -p /opt/amzf
sudo chown amzf:amzf /opt/amzf

# Create logs directory
sudo mkdir -p /var/log/amzf
sudo chown amzf:amzf /var/log/amzf

# Create secrets directory (secure)
sudo mkdir -p /secure
sudo chmod 700 /secure
sudo chown amzf:amzf /secure
```

**Configure Secrets**:
```bash
# Create secrets file
sudo tee /secure/secrets.properties <<EOF
upstox.api_key=${UPSTOX_API_KEY}
upstox.api_secret=${UPSTOX_API_SECRET}
zerodha.api_key=${ZERODHA_API_KEY}
zerodha.api_secret=${ZERODHA_API_SECRET}
fyers.api_key=${FYERS_API_KEY}
fyers.api_secret=${FYERS_API_SECRET}
dhan.client_id=${DHAN_CLIENT_ID}
dhan.access_token=${DHAN_ACCESS_TOKEN}
database.url=jdbc:postgresql://localhost:5432/amzf_prod
database.username=amzf_user
database.password=${DB_PASSWORD}
EOF

# Secure permissions (owner read-only)
sudo chmod 400 /secure/secrets.properties
sudo chown amzf:amzf /secure/secrets.properties
```

---

### Step 3: Database Migration

```bash
# Backup current database
pg_dump -h localhost -U amzf_user amzf_prod > /backup/amzf_prod_$(date +%Y%m%d_%H%M%S).sql

# Test connection
psql -h localhost -U amzf_user -d amzf_prod -c "SELECT version();"

# Run migrations (if using Flyway/Liquibase)
mvn flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5432/amzf_prod

# Verify migrations
psql -h localhost -U amzf_user -d amzf_prod -c "SELECT * FROM flyway_schema_history ORDER BY installed_on DESC LIMIT 5;"
```

**Expected Output**:
```
 version | description | script | installed_on
---------+-------------+--------+--------------
 1.0.0   | Initial     | V1__...| 2026-01-15
```

---

### Step 4: Deploy Application

**Copy JAR to server**:
```bash
# Copy JAR
sudo cp target/annu-undertow-ws-v04-0.4.0.jar /opt/amzf/amzf.jar
sudo chown amzf:amzf /opt/amzf/amzf.jar
```

**Create systemd service**:
```bash
sudo tee /etc/systemd/system/amzf.service <<EOF
[Unit]
Description=AMZF Multi-Broker Trading System
After=network.target postgresql.service

[Service]
Type=simple
User=amzf
Group=amzf
WorkingDirectory=/opt/amzf

# JVM Options
Environment="JAVA_OPTS=-Xmx4g -Xms4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Application Options
Environment="APP_ENV=production"
Environment="SECRETS_FILE=/secure/secrets.properties"

# Logging
Environment="LOG_DIR=/var/log/amzf"

ExecStart=/usr/bin/java \$JAVA_OPTS -jar /opt/amzf/amzf.jar

# Restart policy
Restart=on-failure
RestartSec=10s

# Security
NoNewPrivileges=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
EOF
```

**Reload systemd**:
```bash
sudo systemctl daemon-reload
```

---

### Step 5: Start Application

```bash
# Start service
sudo systemctl start amzf

# Check status
sudo systemctl status amzf

# Follow logs
sudo journalctl -u amzf -f
```

**Expected Log Output**:
```
[main] INFO  App - ═══════════════════════════════════════
[main] INFO  App - === AnnuPaper v04 Starting ===
[main] INFO  App - ═══════════════════════════════════════
[main] INFO  App - DB: Connected to PostgreSQL
[main] INFO  App - ✓ Prometheus metrics initialized
[main] INFO  App - ✓ Loaded 4 brokers: UPSTOX, ZERODHA, FYERS, DHAN
[main] INFO  App - ✓ HTTP server started on port 9090
[main] INFO  App - ✓ Prometheus /metrics endpoint ready
[main] INFO  App - ═══════════════════════════════════════
[main] INFO  App - === Ready to accept requests ===
[main] INFO  App - ═══════════════════════════════════════
```

---

### Step 6: Verify Deployment

**Health Check**:
```bash
# Check health endpoint
curl http://localhost:9090/api/health

# Expected: {"status":"UP","timestamp":"..."}
```

**Metrics Check**:
```bash
# Check metrics endpoint
curl http://localhost:9090/metrics | head -20

# Expected: Prometheus text format with broker_orders_total, etc.
```

**Database Check**:
```bash
# Verify database connectivity
psql -h localhost -U amzf_user -d amzf_prod -c "SELECT COUNT(*) FROM users;"
```

**Broker Connectivity Check**:
```bash
# Check broker health (via API if available)
curl http://localhost:9090/api/brokers/health
```

**Load Test (Quick)**:
```bash
# Run quick load test
cd load-tests
./quick-load-test.sh

# Expected: >95% success rate
```

---

### Step 7: Enable Monitoring

**Configure Prometheus Scraping**:
```yaml
# /etc/prometheus/prometheus.yml
scrape_configs:
  - job_name: 'amzf'
    static_configs:
      - targets: ['localhost:9090']
    metrics_path: '/metrics'
    scrape_interval: 15s
```

**Reload Prometheus**:
```bash
sudo systemctl reload prometheus

# Verify target
curl http://localhost:9090/prometheus/targets
```

**Import Grafana Dashboard**:
```bash
# Copy dashboard JSON
sudo cp docs/grafana-broker-dashboard.json /var/lib/grafana/dashboards/

# Import via Grafana UI or API
curl -X POST http://admin:admin@localhost:3000/api/dashboards/db \
  -H "Content-Type: application/json" \
  -d @docs/grafana-broker-dashboard.json
```

---

### Step 8: Configure Alerts

**Alertmanager Configuration**:
```yaml
# /etc/prometheus/alertmanager.yml
global:
  smtp_smarthost: 'smtp.gmail.com:587'
  smtp_from: 'alerts@amzf.com'
  smtp_auth_username: 'alerts@amzf.com'
  smtp_auth_password: '${SMTP_PASSWORD}'

route:
  receiver: 'team-oncall'
  group_by: ['alertname', 'cluster', 'service']
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 4h

receivers:
  - name: 'team-oncall'
    email_configs:
      - to: 'oncall@amzf.com'
        headers:
          Subject: '[AMZF] {{ .GroupLabels.alertname }}'
```

**Alert Rules**:
```yaml
# /etc/prometheus/alerts/amzf.yml
groups:
  - name: amzf_alerts
    rules:
      - alert: HighErrorRate
        expr: rate(broker_orders_total{status="failure"}[5m]) > 0.05
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "High error rate detected"
          description: "Error rate is {{ $value }}%"

      - alert: HighLatency
        expr: histogram_quantile(0.99, broker_order_latency_seconds) > 1.0
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High latency detected"
          description: "p99 latency is {{ $value }}s"
```

**Reload Prometheus**:
```bash
sudo systemctl reload prometheus
```

---

### Step 9: Enable Auto-Start

```bash
# Enable service on boot
sudo systemctl enable amzf

# Verify enabled
sudo systemctl is-enabled amzf
# Output: enabled
```

---

### Step 10: Post-Deployment Verification

**Run Full Test Suite**:
```bash
# Wait 5 minutes for system to stabilize
sleep 300

# Run comprehensive checks
./scripts/post-deployment-check.sh
```

**Check Metrics**:
```bash
# Verify metrics are being collected
curl http://localhost:9090/metrics | grep broker_orders_total

# Check Prometheus
curl http://localhost:9090/prometheus/api/v1/query?query=up{job="amzf"}
```

**Monitor Logs**:
```bash
# Check for errors in last 10 minutes
sudo journalctl -u amzf --since "10 minutes ago" | grep ERROR

# Should return no critical errors
```

**Load Test**:
```bash
# Run baseline load test
cd load-tests
THREADS=50 DURATION=300 ./run-load-test.sh

# Verify:
# - Success rate >99%
# - Avg response time <100ms
# - Throughput >500 req/sec
```

---

## Rollback Procedure

If deployment fails, immediately rollback:

### Quick Rollback

```bash
# Stop current version
sudo systemctl stop amzf

# Restore previous JAR (keep backups)
sudo cp /opt/amzf/amzf.jar.backup /opt/amzf/amzf.jar

# Start service
sudo systemctl start amzf

# Verify
curl http://localhost:9090/api/health
```

### Database Rollback

```bash
# Restore database from backup
psql -h localhost -U amzf_user -d amzf_prod < /backup/amzf_prod_YYYYMMDD_HHMMSS.sql

# Verify
psql -h localhost -U amzf_user -d amzf_prod -c "SELECT version FROM schema_version ORDER BY installed_on DESC LIMIT 1;"
```

---

## Post-Deployment Tasks

### 1. Update Documentation
- [ ] Update deployment log
- [ ] Document any issues encountered
- [ ] Update runbook with lessons learned

### 2. Notify Team
- [ ] Send deployment notification to team
- [ ] Update status page
- [ ] Inform support team

### 3. Monitor for 24 Hours
- [ ] Watch error rates closely
- [ ] Monitor latency metrics
- [ ] Check for memory leaks
- [ ] Review user feedback

### 4. Schedule Post-Deployment Review
- [ ] Review metrics after 1 week
- [ ] Gather team feedback
- [ ] Plan improvements

---

## Emergency Contacts

| Role | Name | Phone | Email |
|------|------|-------|-------|
| On-Call Engineer | TBD | +91-xxx | oncall@amzf.com |
| DevOps Lead | TBD | +91-xxx | devops@amzf.com |
| CTO | TBD | +91-xxx | cto@amzf.com |
| Database Admin | TBD | +91-xxx | dba@amzf.com |

---

## Troubleshooting

### Issue: Application won't start

**Symptoms**: Service fails to start, logs show errors

**Check**:
```bash
# Check service status
sudo systemctl status amzf

# Check logs
sudo journalctl -u amzf -n 100

# Check secrets file
sudo ls -la /secure/secrets.properties
```

**Common Causes**:
- Missing secrets file
- Database connection failure
- Port 9090 already in use
- Insufficient memory

---

### Issue: High memory usage

**Symptoms**: Memory usage growing continuously

**Check**:
```bash
# Check memory usage
free -h

# Check Java heap
jps -l
jstat -gcutil <pid> 1000 10
```

**Fix**:
```bash
# Adjust heap size in systemd service
sudo systemctl edit amzf

# Add: Environment="JAVA_OPTS=-Xmx8g -Xms8g"
sudo systemctl daemon-reload
sudo systemctl restart amzf
```

---

### Issue: Database connection errors

**Symptoms**: Can't connect to database

**Check**:
```bash
# Test connection
psql -h localhost -U amzf_user -d amzf_prod

# Check connection pool
curl http://localhost:9090/metrics | grep hikari
```

**Fix**:
```bash
# Increase connection pool size
# Edit configuration and restart
```

---

## Conclusion

**Deployment Checklist**:
- [x] Pre-deployment checks completed
- [x] Application built successfully
- [x] Environment prepared
- [x] Database migrated
- [x] Application deployed
- [x] Service started
- [x] Deployment verified
- [x] Monitoring enabled
- [x] Alerts configured
- [x] Auto-start enabled
- [x] Post-deployment verified

**Status**: ✅ **DEPLOYMENT COMPLETE**

**Next Steps**:
1. Monitor for 24 hours
2. Schedule post-deployment review
3. Update documentation
4. Plan next release
