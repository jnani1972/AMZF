# Phase 4 Roadmap: Operational Excellence & Growth

**Date**: January 15, 2026
**Status**: 📋 **PLANNING**
**Phase 3 Completion**: ✅ 128/128 tests passing, production-ready

---

## Executive Summary

With Phase 3 complete and the core trading system production-ready, Phase 4 focuses on **operational maturity**, **security hardening**, **user experience**, and **scalable growth**. This roadmap transitions the system from "production-ready" to "enterprise-grade" with emphasis on observability, security compliance, and user satisfaction.

### Current State (Phase 3 Complete)
- ✅ 15 Prometheus metrics implemented
- ✅ Chaos engineering validated (8 scenarios)
- ✅ Security hardening (SecretsManager, SecureAuditLogger, InputValidator)
- ✅ Load testing suite with baseline established
- ✅ Operational runbooks (deployment, incidents, token rotation, monitoring)
- ✅ Multi-broker architecture with automatic failover
- ✅ 128/128 tests passing

### Phase 4 Goals
1. **Operational Excellence**: Deploy full observability stack and establish operational rhythms
2. **Security Compliance**: Complete security audit and implement enterprise-grade secrets management
3. **User Experience**: Launch beta program and improve UI/UX based on feedback
4. **Scalability**: Prepare for horizontal scaling and high availability
5. **Broker Expansion**: Add 2-3 additional brokers using proven architecture

---

## Phase 4A: Operational Excellence
**Timeline**: Weeks 1-2
**Priority**: 🔴 Critical
**Owner**: DevOps/SRE Team

### Goal
Deploy production monitoring stack and establish operational procedures to ensure 99.9% uptime.

### Tasks

#### Week 1: Monitoring Stack Deployment

**Task 1.1: Deploy Prometheus in Production**
- **Effort**: 4 hours
- **Prerequisites**: Production server access, systemd
- **Deliverables**:
  - Prometheus deployed and scraping `/metrics` endpoint
  - Data retention configured (15 days minimum)
  - Prometheus UI accessible to ops team

**Steps**:
```bash
# Install Prometheus
wget https://github.com/prometheus/prometheus/releases/download/v2.45.0/prometheus-2.45.0.linux-amd64.tar.gz
tar -xzf prometheus-2.45.0.linux-amd64.tar.gz
sudo mv prometheus-2.45.0.linux-amd64 /opt/prometheus

# Configure scraping (from monitoring runbook)
sudo tee /opt/prometheus/prometheus.yml <<EOF
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'amzf'
    static_configs:
      - targets: ['localhost:9090']
    metrics_path: '/metrics'
EOF

# Create systemd service
sudo tee /etc/systemd/system/prometheus.service <<EOF
[Unit]
Description=Prometheus Monitoring
After=network.target

[Service]
Type=simple
User=prometheus
ExecStart=/opt/prometheus/prometheus \
  --config.file=/opt/prometheus/prometheus.yml \
  --storage.tsdb.path=/var/lib/prometheus \
  --storage.tsdb.retention.time=15d

[Install]
WantedBy=multi-user.target
EOF

# Start Prometheus
sudo systemctl daemon-reload
sudo systemctl enable prometheus
sudo systemctl start prometheus

# Verify
curl http://localhost:9090/api/v1/targets
```

**Success Criteria**:
- [ ] Prometheus scraping AMZF metrics every 15 seconds
- [ ] All 15 metrics visible in Prometheus UI
- [ ] No scrape errors for 1 hour continuous operation

---

**Task 1.2: Deploy Grafana and Import Dashboard**
- **Effort**: 3 hours
- **Prerequisites**: Prometheus running
- **Deliverables**:
  - Grafana deployed with AMZF dashboard
  - Team members have access
  - Dashboard showing real-time broker health

**Steps**:
```bash
# Install Grafana
wget https://dl.grafana.com/oss/release/grafana-10.0.0.linux-amd64.tar.gz
tar -xzf grafana-10.0.0.linux-amd64.tar.gz
sudo mv grafana-10.0.0 /opt/grafana

# Configure data source
sudo tee /opt/grafana/conf/provisioning/datasources/prometheus.yml <<EOF
apiVersion: 1
datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://localhost:9090
    isDefault: true
EOF

# Import AMZF dashboard
sudo cp docs/grafana-broker-dashboard.json /opt/grafana/conf/provisioning/dashboards/

# Start Grafana
sudo systemctl enable grafana-server
sudo systemctl start grafana-server

# Access at http://localhost:3000 (admin/admin)
```

**Success Criteria**:
- [ ] Grafana accessible at configured URL
- [ ] Prometheus data source connected
- [ ] AMZF Broker Dashboard showing all 9 panels
- [ ] Real-time data updating every 15 seconds

---

**Task 1.3: Deploy Alertmanager**
- **Effort**: 4 hours
- **Prerequisites**: Prometheus running
- **Deliverables**:
  - Alertmanager deployed and integrated with Prometheus
  - Alert rules loaded from monitoring runbook
  - Test alerts delivered to Slack/email

**Steps**:
```bash
# Install Alertmanager
wget https://github.com/prometheus/alertmanager/releases/download/v0.26.0/alertmanager-0.26.0.linux-amd64.tar.gz
tar -xzf alertmanager-0.26.0.linux-amd64.tar.gz
sudo mv alertmanager-0.26.0.linux-amd64 /opt/alertmanager

# Configure Alertmanager (from monitoring runbook)
sudo tee /opt/alertmanager/alertmanager.yml <<EOF
global:
  smtp_smarthost: 'smtp.gmail.com:587'
  smtp_from: 'alerts@amzf.com'
  smtp_auth_username: 'alerts@amzf.com'
  smtp_auth_password: '\${SMTP_PASSWORD}'

route:
  receiver: 'team-oncall'
  group_by: ['alertname', 'severity']
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 4h
  routes:
    - match:
        severity: critical
      receiver: 'pagerduty'
      repeat_interval: 1h

receivers:
  - name: 'team-oncall'
    email_configs:
      - to: 'oncall@amzf.com'
    slack_configs:
      - api_url: '\${SLACK_WEBHOOK_URL}'
        channel: '#amzf-alerts'

  - name: 'pagerduty'
    pagerduty_configs:
      - service_key: '\${PAGERDUTY_KEY}'
EOF

# Create alert rules (from monitoring runbook)
sudo tee /opt/prometheus/alerts.yml <<EOF
groups:
  - name: amzf_alerts
    interval: 30s
    rules:
      # Critical Alerts
      - alert: ApplicationDown
        expr: up{job="amzf"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "AMZF application is down"
          description: "The AMZF service has been unreachable for 1 minute"

      - alert: AllBrokersDown
        expr: sum(broker_health_status) == 0
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "All brokers are down"
          description: "No brokers are available - trading halted"

      - alert: HighErrorRate
        expr: |
          sum(rate(broker_orders_total{status="failure"}[5m]))
          /
          sum(rate(broker_orders_total[5m]))
          * 100 > 5
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "High order error rate: {{ \$value }}%"
          description: "Error rate exceeds 5% for 5 minutes"

      # Warning Alerts
      - alert: BrokerDown
        expr: broker_health_status == 0
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Broker {{ \$labels.broker }} is down"
          description: "Broker has been down for 5 minutes - failover active"

      - alert: HighLatency
        expr: |
          histogram_quantile(0.99,
            rate(broker_order_latency_seconds_bucket[5m])
          ) > 1.0
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "High p99 latency: {{ \$value }}s"
          description: "Order latency exceeds 1 second at p99"

      - alert: RateLimitHighUtilization
        expr: broker_rate_limit_utilization > 0.85
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Rate limit at {{ \$value }}% for {{ \$labels.broker }}"
          description: "Approaching rate limit threshold"

      - alert: AuthenticationFailure
        expr: increase(broker_authentications_total{status="failure"}[15m]) > 2
        labels:
          severity: warning
        annotations:
          summary: "Authentication failures for {{ \$labels.broker }}"
          description: "Multiple auth failures - check token expiry"

      # Info Alerts
      - alert: SlowInstrumentLoad
        expr: instrument_load_duration_seconds > 10
        labels:
          severity: info
        annotations:
          summary: "Slow instrument loading: {{ \$value }}s"
          description: "Instrument data taking longer than expected"
EOF

# Update Prometheus to load alerts
sudo tee -a /opt/prometheus/prometheus.yml <<EOF

rule_files:
  - "alerts.yml"

alerting:
  alertmanagers:
    - static_configs:
        - targets: ['localhost:9093']
EOF

# Restart services
sudo systemctl restart prometheus
sudo systemctl start alertmanager
```

**Success Criteria**:
- [ ] Alertmanager running and integrated with Prometheus
- [ ] All 8 alert rules loaded (3 Critical, 4 Warning, 1 Info)
- [ ] Test alert successfully delivered to Slack and email
- [ ] PagerDuty integration working for critical alerts

---

#### Week 2: Centralized Logging & Operational Procedures

**Task 2.1: Deploy Centralized Logging (ELK/Loki)**
- **Effort**: 6 hours
- **Prerequisites**: Sufficient disk space (100GB+)
- **Deliverables**:
  - Log aggregation system deployed
  - Application logs being ingested
  - Search and query interface available

**Option A: Grafana Loki (Recommended - lighter weight)**
```bash
# Install Loki
wget https://github.com/grafana/loki/releases/download/v2.8.0/loki-linux-amd64.zip
unzip loki-linux-amd64.zip
sudo mv loki-linux-amd64 /opt/loki/loki

# Configure Loki
sudo tee /opt/loki/config.yml <<EOF
auth_enabled: false

server:
  http_listen_port: 3100

ingester:
  lifecycler:
    ring:
      kvstore:
        store: inmemory
      replication_factor: 1
  chunk_idle_period: 5m
  chunk_retain_period: 30s

schema_config:
  configs:
    - from: 2020-05-15
      store: boltdb
      object_store: filesystem
      schema: v11
      index:
        prefix: index_
        period: 24h

storage_config:
  boltdb:
    directory: /var/lib/loki/index
  filesystem:
    directory: /var/lib/loki/chunks

limits_config:
  enforce_metric_name: false
  reject_old_samples: true
  reject_old_samples_max_age: 168h

chunk_store_config:
  max_look_back_period: 168h

table_manager:
  retention_deletes_enabled: true
  retention_period: 168h
EOF

# Install Promtail (log shipper)
wget https://github.com/grafana/loki/releases/download/v2.8.0/promtail-linux-amd64.zip
unzip promtail-linux-amd64.zip
sudo mv promtail-linux-amd64 /opt/loki/promtail

# Configure Promtail to ship AMZF logs
sudo tee /opt/loki/promtail-config.yml <<EOF
server:
  http_listen_port: 9080

positions:
  filename: /tmp/positions.yaml

clients:
  - url: http://localhost:3100/loki/api/v1/push

scrape_configs:
  - job_name: amzf
    static_configs:
      - targets:
          - localhost
        labels:
          job: amzf
          __path__: /var/log/amzf/*.log

  - job_name: systemd
    journal:
      max_age: 12h
      labels:
        job: systemd-journal
    relabel_configs:
      - source_labels: ['__journal__systemd_unit']
        target_label: 'unit'
      - source_labels: ['__journal__hostname']
        target_label: 'host'
EOF

# Start Loki and Promtail
sudo systemctl enable loki promtail
sudo systemctl start loki promtail

# Add Loki to Grafana
# Grafana UI > Configuration > Data Sources > Add Loki
# URL: http://localhost:3100
```

**Option B: ELK Stack (Full-featured, heavier)**
```bash
# Install Elasticsearch
wget https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-8.7.0-linux-x86_64.tar.gz
tar -xzf elasticsearch-8.7.0-linux-x86_64.tar.gz
sudo mv elasticsearch-8.7.0 /opt/elasticsearch

# Install Logstash
wget https://artifacts.elastic.co/downloads/logstash/logstash-8.7.0-linux-x86_64.tar.gz
tar -xzf logstash-8.7.0-linux-x86_64.tar.gz
sudo mv logstash-8.7.0 /opt/logstash

# Configure Logstash for AMZF logs
sudo tee /opt/logstash/config/amzf-pipeline.conf <<EOF
input {
  file {
    path => "/var/log/amzf/*.log"
    start_position => "beginning"
    type => "amzf"
  }

  journald {
    path => "/var/log/journal"
    filter => { "_SYSTEMD_UNIT" => "amzf.service" }
    type => "systemd"
  }
}

filter {
  if [type] == "amzf" {
    grok {
      match => { "message" => "\[%{TIMESTAMP_ISO8601:timestamp}\] %{LOGLEVEL:level} %{JAVACLASS:class} - %{GREEDYDATA:message}" }
    }
    date {
      match => [ "timestamp", "ISO8601" ]
    }
  }
}

output {
  elasticsearch {
    hosts => ["localhost:9200"]
    index => "amzf-logs-%{+YYYY.MM.dd}"
  }
}
EOF

# Install Kibana
wget https://artifacts.elastic.co/downloads/kibana/kibana-8.7.0-linux-x86_64.tar.gz
tar -xzf kibana-8.7.0-linux-x86_64.tar.gz
sudo mv kibana-8.7.0 /opt/kibana

# Start ELK stack
sudo systemctl enable elasticsearch logstash kibana
sudo systemctl start elasticsearch logstash kibana

# Access Kibana at http://localhost:5601
```

**Success Criteria**:
- [ ] Logs from AMZF application being ingested in real-time
- [ ] Search interface working (can query last 7 days)
- [ ] Correlation possible between metrics and logs
- [ ] Retention policy configured (7-30 days based on storage)

---

**Task 2.2: Establish Operational Rhythms**
- **Effort**: 2 hours (setup) + ongoing
- **Prerequisites**: Monitoring stack deployed
- **Deliverables**:
  - Daily/weekly/monthly checklist automation
  - On-call rotation schedule
  - Incident response team trained

**Daily Operations Checklist** (Automated via script):
```bash
#!/bin/bash
# daily-health-check.sh

echo "=== AMZF Daily Health Check: $(date) ==="

# 1. Application health
echo -n "Application Status: "
if curl -f -s http://localhost:9090/api/health > /dev/null; then
    echo "✅ UP"
else
    echo "❌ DOWN - CRITICAL"
fi

# 2. Broker health
echo "Broker Health:"
curl -s http://localhost:9090/metrics | grep broker_health_status | while read line; do
    broker=$(echo $line | grep -oP 'broker="\K[^"]+')
    status=$(echo $line | awk '{print $2}')
    if [ "$status" == "1.0" ]; then
        echo "  ✅ $broker: UP"
    else
        echo "  ❌ $broker: DOWN"
    fi
done

# 3. Error rate (last 24 hours)
echo -n "24h Error Rate: "
errors=$(curl -s http://localhost:9090/metrics | grep 'broker_orders_total{.*status="failure"' | awk '{sum+=$2} END {print sum}')
total=$(curl -s http://localhost:9090/metrics | grep 'broker_orders_total' | awk '{sum+=$2} END {print sum}')
if [ "$total" -gt 0 ]; then
    error_pct=$(awk "BEGIN {printf \"%.2f\", ($errors/$total)*100}")
    echo "$error_pct%"
    if (( $(awk "BEGIN {print ($error_pct > 5)}") )); then
        echo "  ⚠️  Warning: Error rate exceeds 5%"
    fi
else
    echo "No orders in last 24h"
fi

# 4. Disk space
echo -n "Disk Usage: "
usage=$(df -h / | awk 'NR==2 {print $5}' | sed 's/%//')
echo "$usage%"
if [ "$usage" -gt 80 ]; then
    echo "  ⚠️  Warning: Disk usage exceeds 80%"
fi

# 5. Recent errors
echo "Recent Errors (last hour):"
sudo journalctl -u amzf --since "1 hour ago" | grep ERROR | tail -5

# 6. Prometheus targets
echo -n "Prometheus Scraping: "
if curl -s http://localhost:9090/api/v1/targets | grep -q '"health":"up"'; then
    echo "✅ Healthy"
else
    echo "❌ Scrape failures detected"
fi

echo ""
echo "=== Daily Check Complete ==="
```

**Weekly Operations** (Sundays, automated):
```bash
#!/bin/bash
# weekly-report.sh

# Generate weekly metrics report
# - Total orders placed
# - Success rate by broker
# - Average latency
# - Failover events
# - Token expiry warnings
# Email to team
```

**Monthly Operations** (1st of month):
```bash
#!/bin/bash
# monthly-maintenance.sh

# 1. Run peak load test (per load testing guide)
# 2. Review and rotate logs older than 30 days
# 3. Check for pending security updates
# 4. Review alert false-positive rate
# 5. Update capacity planning projections
```

**Success Criteria**:
- [ ] Daily health check running automatically (cron)
- [ ] Weekly report emailed to team
- [ ] On-call schedule published for next 4 weeks
- [ ] All team members trained on incident response runbook

---

**Task 2.3: Set Up On-Call Rotation**
- **Effort**: 2 hours
- **Prerequisites**: Alertmanager configured, PagerDuty account
- **Deliverables**:
  - On-call rotation schedule (4 weeks minimum)
  - Escalation procedures documented
  - On-call playbook created

**On-Call Rotation Template**:
```markdown
# AMZF On-Call Schedule

## Week of Jan 15-21, 2026
- Primary: Engineer A
- Secondary: Engineer B
- Manager Escalation: Engineering Lead

## Week of Jan 22-28, 2026
- Primary: Engineer B
- Secondary: Engineer C
- Manager Escalation: Engineering Lead

## Rotation Rules
- On-call engineer must respond to P0 alerts within 15 minutes
- P1 alerts within 30 minutes
- If no response in 15 minutes, escalate to secondary
- All P0/P1 incidents require post-mortem

## On-Call Playbook
1. Receive alert via PagerDuty/Slack
2. Acknowledge alert immediately
3. Follow incident response runbook (docs/runbooks/INCIDENT_RESPONSE_RUNBOOK.md)
4. Update status page if customer-facing
5. Document all actions in incident ticket
6. Create post-mortem for P0/P1 incidents
```

**Success Criteria**:
- [ ] 4-week rotation schedule published
- [ ] PagerDuty integration tested with all on-call engineers
- [ ] Mock incident drill completed successfully
- [ ] On-call playbook accessible 24/7

---

## Phase 4B: Security Audit & Hardening
**Timeline**: Weeks 3-4
**Priority**: 🟡 High
**Owner**: Security Team / External Auditor

### Goal
Achieve enterprise-grade security posture through comprehensive audit, secrets encryption, and compliance validation.

### Tasks

#### Week 3: Security Assessment

**Task 3.1: Internal Security Review**
- **Effort**: 8 hours
- **Prerequisites**: Security team access, code review tools
- **Deliverables**:
  - Security assessment report
  - Identified vulnerabilities with severity ratings
  - Remediation plan

**Review Checklist**:
```markdown
## Authentication & Authorization
- [ ] All API keys stored in SecretsManager (not hardcoded)
- [ ] Environment variables validated and sanitized
- [ ] API endpoints have authentication checks
- [ ] Role-based access control implemented
- [ ] Session management secure (timeout, token rotation)

## Input Validation
- [ ] InputValidator used for all user inputs
- [ ] SQL injection prevention validated
- [ ] XSS prevention validated
- [ ] Command injection prevention validated
- [ ] File upload validation (if applicable)

## Data Protection
- [ ] Sensitive data encrypted at rest
- [ ] TLS 1.3 used for all connections
- [ ] Database credentials encrypted
- [ ] API keys encrypted in secrets file
- [ ] Audit logs protected from tampering (SHA-256 hashing)

## Secure Communication
- [ ] All broker API calls over HTTPS
- [ ] Certificate validation enabled
- [ ] No TLS downgrade attacks possible
- [ ] Websocket connections encrypted (WSS)

## Logging & Monitoring
- [ ] Security events logged via SecureAuditLogger
- [ ] PII not logged in plaintext
- [ ] Log access restricted to authorized users
- [ ] Anomaly detection configured

## Dependencies
- [ ] All dependencies up to date (no known CVEs)
- [ ] Maven dependency check passing
- [ ] Third-party libraries from trusted sources
- [ ] License compliance verified

## Infrastructure
- [ ] Firewall rules restrict unnecessary ports
- [ ] SSH key-based authentication only
- [ ] Root access disabled
- [ ] Regular security patching process
```

**Security Scanning**:
```bash
# Run OWASP Dependency Check
mvn dependency-check:check

# Run static code analysis
# Install SonarQube or use SonarCloud
mvn sonar:sonar \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.login=${SONAR_TOKEN}

# Check for common vulnerabilities
grep -r "password\|api_key\|secret" src/ | grep -v "// "
```

**Success Criteria**:
- [ ] Security assessment report completed
- [ ] Zero high/critical vulnerabilities in dependencies
- [ ] All findings prioritized by severity
- [ ] Remediation plan with timeline created

---

**Task 3.2: Schedule Penetration Testing**
- **Effort**: External engagement (1-2 weeks)
- **Prerequisites**: Security audit vendor selected
- **Deliverables**:
  - Penetration test report
  - Proof of concept exploits (if any)
  - Remediation recommendations

**Scope of Penetration Test**:
```markdown
## In-Scope
- AMZF web application and APIs
- Authentication and authorization mechanisms
- Input validation and injection attacks
- Session management
- Business logic flaws
- Data exposure vulnerabilities

## Out-of-Scope
- Physical security
- Social engineering
- Denial of service attacks
- Third-party broker APIs (Upstox, Zerodha, etc.)

## Testing Methodology
1. Reconnaissance and information gathering
2. Vulnerability scanning
3. Manual exploitation attempts
4. Business logic testing
5. Report generation with severity ratings
```

**Recommended Vendors**:
- Bishop Fox
- NCC Group
- Synack
- HackerOne (bug bounty program)

**Success Criteria**:
- [ ] Penetration test completed by reputable firm
- [ ] All high/critical findings remediated
- [ ] Medium findings have remediation plan
- [ ] Re-test confirms fixes effective

---

**Task 3.3: Implement Enterprise Secrets Management**
- **Effort**: 6 hours
- **Prerequisites**: Secrets management solution selected
- **Deliverables**:
  - Secrets encrypted at rest
  - Access audit trail
  - Automatic rotation configured

**Option A: HashiCorp Vault (Recommended for on-premise)**
```bash
# Install Vault
wget https://releases.hashicorp.com/vault/1.14.0/vault_1.14.0_linux_amd64.zip
unzip vault_1.14.0_linux_amd64.zip
sudo mv vault /usr/local/bin/

# Initialize Vault
vault server -dev &  # Development mode for testing
export VAULT_ADDR='http://127.0.0.1:8200'
vault status

# Store secrets
vault kv put secret/amzf/upstox \
  api_key="${UPSTOX_API_KEY}" \
  api_secret="${UPSTOX_API_SECRET}"

vault kv put secret/amzf/zerodha \
  api_key="${ZERODHA_API_KEY}" \
  api_secret="${ZERODHA_API_SECRET}"

vault kv put secret/amzf/database \
  url="jdbc:postgresql://localhost:5432/amzf_prod" \
  username="amzf_user" \
  password="${DB_PASSWORD}"

# Update SecretsManager to fetch from Vault
# Modify src/main/java/in/annupaper/security/SecretsManager.java
```

**Updated SecretsManager with Vault Integration**:
```java
public class SecretsManager {
    private static final String VAULT_ADDR = System.getenv("VAULT_ADDR");
    private static final String VAULT_TOKEN = System.getenv("VAULT_TOKEN");

    public String getSecret(String key) {
        // Try Vault first
        if (VAULT_ADDR != null && VAULT_TOKEN != null) {
            return fetchFromVault(key);
        }
        // Fallback to file/env vars
        return fetchFromFile(key);
    }

    private String fetchFromVault(String key) {
        // Use Vault Java client
        Vault vault = new Vault(new VaultConfig()
            .address(VAULT_ADDR)
            .token(VAULT_TOKEN)
            .build());

        LogicalResponse response = vault.logical()
            .read("secret/amzf/" + key);

        return response.getData().get("value");
    }
}
```

**Option B: AWS Secrets Manager (for AWS deployments)**
```bash
# Store secrets in AWS Secrets Manager
aws secretsmanager create-secret \
  --name amzf/upstox/api-key \
  --secret-string "${UPSTOX_API_KEY}"

aws secretsmanager create-secret \
  --name amzf/database/password \
  --secret-string "${DB_PASSWORD}"

# Configure automatic rotation (90 days)
aws secretsmanager rotate-secret \
  --secret-id amzf/upstox/api-key \
  --rotation-lambda-arn arn:aws:lambda:us-east-1:123456789:function:rotate-secret \
  --rotation-rules AutomaticallyAfterDays=90
```

**Success Criteria**:
- [ ] All secrets migrated to vault/secrets manager
- [ ] Secrets encrypted at rest with AES-256
- [ ] Access audit trail enabled
- [ ] Automatic 90-day rotation configured
- [ ] Emergency secret rotation procedure tested

---

#### Week 4: Compliance & Documentation

**Task 4.1: Regulatory Compliance Assessment**
- **Effort**: 8 hours
- **Prerequisites**: Legal/compliance team input
- **Deliverables**:
  - Compliance checklist completed
  - Data retention policies documented
  - GDPR/privacy procedures (if applicable)

**Compliance Checklist** (customize based on jurisdiction):
```markdown
## Data Protection & Privacy
- [ ] Personal data inventory completed
- [ ] Data retention policy defined (e.g., 7 years for trade records)
- [ ] Data deletion procedures documented
- [ ] User consent mechanisms implemented (if EU users)
- [ ] Privacy policy published

## Financial Regulations (India - SEBI)
- [ ] Trade records retention: 7 years minimum
- [ ] Audit trail of all trades maintained
- [ ] User identity verification (KYC) integrated with broker
- [ ] Risk disclosure provided to users
- [ ] Complaint handling procedure documented

## Audit & Reporting
- [ ] All trades logged via SecureAuditLogger
- [ ] Logs tamper-proof (SHA-256 hashing)
- [ ] Regulatory reports can be generated
- [ ] Audit log retention: 7 years

## Business Continuity
- [ ] Disaster recovery plan documented
- [ ] RTO (Recovery Time Objective): <4 hours
- [ ] RPO (Recovery Point Objective): <1 hour
- [ ] Backup and restore tested quarterly
```

**Data Retention Policy**:
```markdown
# AMZF Data Retention Policy

## Trade Data (SEBI Requirement)
- **Retention Period**: 7 years
- **Storage**: Encrypted PostgreSQL + daily backups
- **Deletion**: Automated after 7 years + 1 month grace period

## User Data
- **Active Users**: Retained indefinitely
- **Inactive Users**: 3 years after last login
- **Deletion Requests**: Honored within 30 days (GDPR)

## Application Logs
- **Production Logs**: 90 days in hot storage, 1 year in cold storage
- **Audit Logs**: 7 years (regulatory requirement)
- **Error Logs**: 180 days

## Backup Data
- **Full Backups**: Daily, retained for 30 days
- **Incremental Backups**: Hourly, retained for 7 days
- **Offsite Backups**: Weekly, retained for 1 year
```

**Success Criteria**:
- [ ] Compliance checklist completed with legal review
- [ ] Data retention policy approved by compliance team
- [ ] Privacy policy published (if required)
- [ ] Regulatory reporting capability validated

---

**Task 4.2: Security Documentation**
- **Effort**: 4 hours
- **Prerequisites**: All security tasks completed
- **Deliverables**:
  - Security architecture document
  - Threat model documented
  - Incident response procedures updated

**Security Architecture Document**:
```markdown
# AMZF Security Architecture

## Defense in Depth Strategy

### Layer 1: Network Security
- Firewall rules restrict access to ports 9090 (app), 9090 (prometheus), 3000 (grafana)
- TLS 1.3 for all external communication
- VPN required for production server access

### Layer 2: Application Security
- SecretsManager for credential management
- InputValidator for all user inputs
- SecureAuditLogger for security events
- Rate limiting on API endpoints

### Layer 3: Data Security
- Secrets encrypted at rest (Vault/AWS Secrets Manager)
- Database connections over TLS
- Audit logs with SHA-256 tamper detection
- PII tokenization where possible

### Layer 4: Monitoring & Response
- Real-time security event monitoring
- Alerting on authentication failures
- Automated threat detection
- 24/7 on-call for critical security alerts

## Threat Model

### Threat: Credential Theft
- **Impact**: High - unauthorized trading
- **Mitigation**:
  - Secrets in vault, not in code
  - 90-day token rotation
  - MFA for admin access

### Threat: SQL Injection
- **Impact**: High - data breach
- **Mitigation**:
  - InputValidator validates all inputs
  - Parameterized queries throughout
  - Regular penetration testing

### Threat: Man-in-the-Middle
- **Impact**: High - credential interception
- **Mitigation**:
  - TLS 1.3 with certificate pinning
  - No TLS downgrade allowed
  - Certificate validation enforced

### Threat: Insider Threat
- **Impact**: High - data exfiltration
- **Mitigation**:
  - Role-based access control
  - Audit logging of all sensitive operations
  - Least privilege principle
  - Background checks for production access
```

**Success Criteria**:
- [ ] Security architecture documented and reviewed
- [ ] Threat model covers top 10 threats
- [ ] Incident response procedures include security scenarios
- [ ] Team trained on security best practices

---

## Phase 4C: User Experience & Onboarding
**Timeline**: Weeks 1-6 (Parallel with 4A/4B)
**Priority**: 🟢 Medium
**Owner**: Product/UX Team

### Goal
Launch beta program, gather user feedback, and improve UI/UX based on real-world usage.

### Tasks

**Task C.1: Beta User Program**
- **Effort**: 2 hours setup + ongoing management
- **Timeline**: Weeks 1-6
- **Deliverables**:
  - 10-20 beta users recruited
  - Feedback collection mechanism
  - Weekly feedback review sessions

**Beta Program Setup**:
```markdown
# AMZF Beta Program

## Recruitment Criteria
- 5 experienced traders (active trading, multiple brokers)
- 5 new traders (learning, 1 broker)
- 5 portfolio managers (multiple accounts)
- 5 admin users (operations, monitoring)

## Beta Period: 6 Weeks

### Week 1-2: Onboarding
- Provide access to staging environment
- Walk through key features
- Set up broker connections

### Week 3-4: Active Usage
- Daily trading on staging
- Report bugs and issues
- Weekly feedback call

### Week 5-6: Validation
- Test new features based on feedback
- Performance validation
- Final satisfaction survey

## Feedback Collection
- In-app feedback widget
- Weekly survey (SurveyMonkey/Typeform)
- Bi-weekly group call
- Slack channel for async communication

## Metrics to Track
- Time to first trade
- Number of brokers connected
- Feature adoption rate
- Bug reports filed
- Net Promoter Score (NPS)
```

**Success Criteria**:
- [ ] 15+ beta users actively trading
- [ ] Average NPS score >50
- [ ] <5 major bugs reported in week 6
- [ ] 80%+ feature adoption for core workflows

---

**Task C.2: UI/UX Improvements**
- **Effort**: 20 hours (design + implementation)
- **Timeline**: Weeks 2-5
- **Deliverables**:
  - Improved dashboard based on UI design guidelines
  - Mobile-responsive layouts
  - Accessibility improvements (WCAG 2.1 AA)

**Priority UI Improvements** (from earlier discussion):
```markdown
## Dashboard Redesign
- [ ] Clean, uncluttered layout with whitespace
- [ ] Role-specific dashboards (trader vs admin)
- [ ] Real-time broker health indicators
- [ ] Quick action buttons (place order, view positions)
- [ ] Customizable widget layout

## Broker Connection UI
- [ ] Step-by-step wizard for adding brokers
- [ ] Visual status indicators (green/yellow/red)
- [ ] Token expiry warnings (7 days before)
- [ ] One-click reconnect for disconnected brokers

## Order Management
- [ ] Clear order status (pending, filled, rejected)
- [ ] Visual confirmation before order placement
- [ ] Undo/cancel within 5 seconds
- [ ] Order history with filters

## Alerts & Notifications
- [ ] Toast notifications for critical events
- [ ] Customizable alert preferences
- [ ] Notification center with history
- [ ] Email/SMS integration

## Mobile Experience
- [ ] Responsive design for tablets/phones
- [ ] Touch-friendly controls (min 44x44px)
- [ ] Optimized for slow networks
- [ ] Progressive Web App (PWA) support

## Accessibility
- [ ] Keyboard navigation for all features
- [ ] Screen reader compatible (ARIA labels)
- [ ] Sufficient color contrast (4.5:1 minimum)
- [ ] Focus indicators visible
- [ ] Alt text for all images
```

**Implementation**:
```html
<!-- Example: Improved Broker Status Card -->
<div class="broker-card" role="region" aria-label="Zerodha Broker Status">
  <div class="broker-header">
    <img src="/img/zerodha-logo.png" alt="Zerodha" class="broker-logo">
    <h3>Zerodha</h3>
    <span class="status-badge status-up" aria-label="Status: Connected">
      <i class="icon-check"></i> Connected
    </span>
  </div>

  <div class="broker-stats">
    <div class="stat">
      <span class="stat-label">Orders Today</span>
      <span class="stat-value">127</span>
    </div>
    <div class="stat">
      <span class="stat-label">Success Rate</span>
      <span class="stat-value">99.2%</span>
    </div>
    <div class="stat">
      <span class="stat-label">Avg Latency</span>
      <span class="stat-value">45ms</span>
    </div>
  </div>

  <div class="broker-actions">
    <button class="btn btn-primary" onclick="placeOrder('zerodha')">
      Place Order
    </button>
    <button class="btn btn-secondary" onclick="viewPositions('zerodha')">
      View Positions
    </button>
  </div>

  <div class="token-expiry-warning" role="alert" aria-live="polite">
    <i class="icon-warning"></i>
    Token expires in 5 days - <a href="/settings/brokers/zerodha">Rotate Now</a>
  </div>
</div>
```

**Success Criteria**:
- [ ] Dashboard redesign implemented and deployed
- [ ] Mobile responsiveness validated on 3+ devices
- [ ] Accessibility audit passing (WAVE, axe DevTools)
- [ ] Beta users report "much improved" usability

---

**Task C.3: Onboarding & Documentation**
- **Effort**: 8 hours
- **Timeline**: Weeks 4-6
- **Deliverables**:
  - Interactive onboarding tour
  - Video tutorials for key workflows
  - Updated user documentation

**Onboarding Tour** (using Intro.js or Shepherd.js):
```javascript
// Example: First-time user onboarding
const tour = new Shepherd.Tour({
  useModalOverlay: true,
  defaultStepOptions: {
    classes: 'shepherd-theme-amzf',
    scrollTo: true
  }
});

tour.addStep({
  id: 'welcome',
  text: 'Welcome to AMZF! Let\'s get you started with a quick tour.',
  buttons: [
    { text: 'Skip Tour', action: tour.cancel },
    { text: 'Start', action: tour.next }
  ]
});

tour.addStep({
  id: 'add-broker',
  text: 'First, connect your broker account. Click here to add Zerodha, Upstox, Fyers, or Dhan.',
  attachTo: { element: '#add-broker-btn', on: 'bottom' },
  buttons: [
    { text: 'Back', action: tour.back },
    { text: 'Next', action: tour.next }
  ]
});

tour.addStep({
  id: 'dashboard',
  text: 'Your dashboard shows broker health, recent orders, and system metrics.',
  attachTo: { element: '#dashboard', on: 'top' },
  buttons: [
    { text: 'Back', action: tour.back },
    { text: 'Next', action: tour.next }
  ]
});

tour.addStep({
  id: 'place-order',
  text: 'Ready to trade? Click here to place your first order.',
  attachTo: { element: '#place-order-btn', on: 'bottom' },
  buttons: [
    { text: 'Back', action: tour.back },
    { text: 'Finish', action: tour.complete }
  ]
});

tour.start();
```

**Video Tutorials** (3-5 minutes each):
1. "Getting Started with AMZF" - Overview and first broker connection
2. "Placing Your First Order" - Step-by-step order placement
3. "Understanding Broker Failover" - How automatic failover works
4. "Monitoring System Health" - Reading metrics and alerts
5. "Admin Guide: Adding Users and Configuring Alerts"

**Updated Documentation**:
```markdown
# User Guide Outline

## For Traders
1. Getting Started
   - Creating an account
   - Connecting your first broker
   - Placing your first order

2. Day-to-Day Trading
   - Order types supported
   - Viewing positions and P&L
   - Setting up alerts

3. Troubleshooting
   - Broker connection issues
   - Order rejection reasons
   - Contact support

## For Admins
1. System Setup
   - Deploying AMZF
   - Configuring brokers
   - Setting up monitoring

2. Operations
   - Daily health checks
   - Incident response
   - Token rotation

3. Advanced
   - High availability setup
   - Scaling for more users
   - Custom integrations
```

**Success Criteria**:
- [ ] Onboarding tour completed by 80% of new users
- [ ] 5 video tutorials published and linked in app
- [ ] User documentation covers all core workflows
- [ ] Support ticket volume <5 per week during beta

---

## Phase 5: Scale & Expand
**Timeline**: Month 2+
**Priority**: 🔵 Future
**Owner**: Engineering + Product Teams

### Goal
Prepare for production scale, expand broker support, and add value-added features.

### Milestones

**Milestone 5.1: High Availability Deployment**
- **Timeline**: Weeks 7-8
- **Deliverables**:
  - Multi-region deployment
  - Load balancer configured
  - Database replication
  - Zero-downtime deployments

**Architecture**:
```
                    ┌─────────────┐
                    │   DNS/LB    │
                    │  (HAProxy)  │
                    └──────┬──────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
        ┌─────▼────┐ ┌─────▼────┐ ┌─────▼────┐
        │  AMZF    │ │  AMZF    │ │  AMZF    │
        │  Node 1  │ │  Node 2  │ │  Node 3  │
        │  (US-E1) │ │  (US-W1) │ │  (AP-S1) │
        └────┬─────┘ └────┬─────┘ └────┬─────┘
             │            │            │
             └────────────┼────────────┘
                          │
                   ┌──────▼──────┐
                   │  PostgreSQL │
                   │   Cluster   │
                   │ (Primary +  │
                   │  2 Replicas)│
                   └─────────────┘
```

**Tasks**:
- [ ] Set up load balancer (HAProxy/NGINX)
- [ ] Deploy AMZF to 3 nodes (different regions)
- [ ] Configure PostgreSQL replication (primary + 2 read replicas)
- [ ] Implement session affinity for WebSocket connections
- [ ] Test failover scenarios (node failure, region failure)
- [ ] Achieve 99.9% uptime SLA

---

**Milestone 5.2: Capacity Planning & Horizontal Scaling**
- **Timeline**: Weeks 9-10
- **Deliverables**:
  - Capacity model based on load tests
  - Auto-scaling policies
  - Resource optimization

**Capacity Planning**:
```markdown
## Current Baseline (Single Node)
- Throughput: ~280 req/sec
- CPU: 30% utilization at baseline
- Memory: 4GB heap, 6GB total
- Concurrent Users: ~50

## Scaling Projections

### 100 Concurrent Users (Target: Month 3)
- Nodes Required: 2
- Throughput: 500+ req/sec
- Database: Primary + 1 read replica
- Estimated Cost: $500/month

### 500 Concurrent Users (Target: Month 6)
- Nodes Required: 5
- Throughput: 2000+ req/sec
- Database: Primary + 2 read replicas
- CDN for static assets
- Estimated Cost: $2000/month

### 1000+ Concurrent Users (Target: Month 12)
- Nodes Required: 10+
- Throughput: 5000+ req/sec
- Database: Sharding or switch to Postgres Citus
- Redis for caching and session management
- Estimated Cost: $5000+/month
```

**Auto-Scaling (Kubernetes example)**:
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: amzf-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: amzf
  minReplicas: 3
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

**Success Criteria**:
- [ ] Capacity model validated with load tests
- [ ] Auto-scaling triggers within 2 minutes of threshold
- [ ] Horizontal scaling maintains <100ms latency
- [ ] Cost per user decreases with scale

---

**Milestone 5.3: Broker Expansion (2-3 Additional Brokers)**
- **Timeline**: Weeks 11-14
- **Deliverables**:
  - AngelOne broker integration
  - Kotak Securities broker integration
  - IIFL broker integration (optional)

**Broker Priority** (based on Indian market share):
1. **AngelOne** (formerly Angel Broking) - 20% market share
2. **Kotak Securities** - 10% market share
3. **IIFL Securities** - 5% market share

**Implementation Plan** (per broker):
```markdown
## Week 1: Setup & Authentication
- [ ] Sign up for broker developer account
- [ ] Obtain API credentials
- [ ] Study API documentation
- [ ] Implement OrderBroker interface
- [ ] Authentication working

## Week 2: Core Features
- [ ] Place order (market, limit, stop-loss)
- [ ] Cancel order
- [ ] Modify order
- [ ] Get order status
- [ ] Fetch positions

## Week 3: Data Feed & Testing
- [ ] Implement DataBroker interface
- [ ] WebSocket connection for live data
- [ ] Subscribe to instruments
- [ ] Integration tests (20+ tests)
- [ ] Chaos tests

## Week 4: Production Readiness
- [ ] Metrics integration
- [ ] Rate limiting configured
- [ ] Failover tested
- [ ] Documentation updated
- [ ] Beta testing with 5 users
```

**Success Criteria** (per broker):
- [ ] All OrderBroker methods implemented
- [ ] 20+ integration tests passing
- [ ] Chaos tests validate failover
- [ ] Performance matches existing brokers (<100ms latency)
- [ ] Beta users successfully trading

---

**Milestone 5.4: Value-Added Features**
- **Timeline**: Weeks 15-20 (ongoing)
- **Deliverables**:
  - P&L analysis dashboard
  - Risk management tools
  - Basic strategy backtesting

**Feature: P&L Analysis Dashboard**
```markdown
## Functionality
- Daily/weekly/monthly P&L charts
- P&L by broker
- P&L by instrument/symbol
- Winning vs losing trades ratio
- Average holding period
- Sharpe ratio calculation

## Implementation
- New service: PLAnalysisService
- Database: Materialized views for performance
- UI: Charts using Chart.js or D3.js
- Export to CSV/PDF
```

**Feature: Risk Management**
```markdown
## Functionality
- Position size calculator
- Portfolio diversification metrics
- Maximum drawdown tracking
- Risk per trade limits
- Stop-loss enforcement

## Implementation
- New service: RiskManagementService
- Real-time position monitoring
- Alerts when limits exceeded
- Integration with order placement workflow
```

**Feature: Strategy Backtesting** (Basic)
```markdown
## Functionality
- Upload simple strategy (moving average crossover)
- Backtest on historical data (1 year)
- View equity curve and metrics
- Compare strategy vs buy-and-hold

## Implementation
- New service: BacktestingService
- Use historical candle data from DataBroker
- Simple strategy engine (can expand later)
- Results visualization in UI
```

**Success Criteria**:
- [ ] P&L dashboard live for all beta users
- [ ] Risk management alerts functional
- [ ] At least 1 backtestable strategy working
- [ ] User satisfaction score >4.5/5 for new features

---

**Milestone 5.5: CI/CD & Static Analysis**
- **Timeline**: Weeks 8-9 (parallel with 5.1)
- **Deliverables**:
  - Full CI/CD pipeline
  - Static code analysis
  - Automated security scanning

**CI/CD Pipeline (GitHub Actions)**:
```yaml
name: AMZF CI/CD Pipeline

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Run tests
        run: mvn test

      - name: Run integration tests
        run: mvn verify -P integration-tests

      - name: Code coverage
        run: mvn jacoco:report

      - name: Upload coverage to Codecov
        uses: codecov/codecov-action@v3

  static-analysis:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: SonarCloud Scan
        uses: SonarSource/sonarcloud-github-action@master
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}

      - name: OWASP Dependency Check
        run: mvn dependency-check:check

      - name: Security scan
        uses: aquasecurity/trivy-action@master
        with:
          scan-type: 'fs'
          scan-ref: '.'

  load-test:
    runs-on: ubuntu-latest
    needs: test
    steps:
      - uses: actions/checkout@v3

      - name: Build application
        run: mvn clean package -DskipTests

      - name: Start application
        run: |
          java -jar target/annu-undertow-ws-v04-0.4.0.jar &
          sleep 30

      - name: Run quick load test
        run: |
          cd load-tests
          chmod +x quick-load-test.sh
          ./quick-load-test.sh

      - name: Validate success criteria
        run: |
          # Parse results and fail if <95% success rate
          # (Script to be added)

  deploy-staging:
    runs-on: ubuntu-latest
    needs: [test, static-analysis, load-test]
    if: github.ref == 'refs/heads/develop'
    steps:
      - uses: actions/checkout@v3

      - name: Deploy to staging
        run: |
          # SSH to staging server and deploy
          # Follow deployment runbook steps
          echo "Deploying to staging..."

  deploy-production:
    runs-on: ubuntu-latest
    needs: [test, static-analysis, load-test]
    if: github.ref == 'refs/heads/main'
    steps:
      - uses: actions/checkout@v3

      - name: Deploy to production
        run: |
          # SSH to production server and deploy
          # Follow deployment runbook steps
          echo "Deploying to production..."

      - name: Smoke tests
        run: |
          sleep 60  # Wait for startup
          curl -f http://prod.amzf.com/api/health
          cd load-tests && ./quick-load-test.sh
```

**Static Analysis with SonarQube**:
```xml
<!-- Add to pom.xml -->
<properties>
  <sonar.organization>amzf</sonar.organization>
  <sonar.host.url>https://sonarcloud.io</sonar.host.url>
  <sonar.coverage.jacoco.xmlReportPaths>
    target/site/jacoco/jacoco.xml
  </sonar.coverage.jacoco.xmlReportPaths>
</properties>

<build>
  <plugins>
    <plugin>
      <groupId>org.jacoco</groupId>
      <artifactId>jacoco-maven-plugin</artifactId>
      <version>0.8.10</version>
      <executions>
        <execution>
          <goals>
            <goal>prepare-agent</goal>
          </goals>
        </execution>
        <execution>
          <id>report</id>
          <phase>test</phase>
          <goals>
            <goal>report</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

**Quality Gates**:
```markdown
## SonarQube Quality Gate Requirements
- Code Coverage: >80%
- Duplicated Lines: <3%
- Maintainability Rating: A
- Reliability Rating: A
- Security Rating: A
- Security Hotspots: 0
- Bugs: 0
- Vulnerabilities: 0
- Code Smells: <50
```

**Success Criteria**:
- [ ] CI/CD pipeline runs on every commit
- [ ] All tests pass before deployment
- [ ] Static analysis passes quality gates
- [ ] Zero-downtime deployments working
- [ ] Rollback tested and functional

---

## Success Metrics

### Phase 4A: Operational Excellence
- [ ] **Uptime**: 99.9%+ (max 43 minutes downtime per month)
- [ ] **MTTR** (Mean Time To Recovery): <30 minutes for P1 incidents
- [ ] **Alert Noise**: <5% false positive rate
- [ ] **Monitoring Coverage**: 100% of critical services
- [ ] **On-Call Response**: 100% of P0 alerts acknowledged within 15 minutes

### Phase 4B: Security Audit & Hardening
- [ ] **Vulnerability Score**: Zero high/critical CVEs
- [ ] **Penetration Test**: Pass with only low/info findings
- [ ] **Secrets Management**: 100% of secrets in vault
- [ ] **Token Rotation**: 100% compliance with 90-day schedule
- [ ] **Security Training**: 100% of team trained on secure coding

### Phase 4C: User Experience & Onboarding
- [ ] **Net Promoter Score (NPS)**: >50
- [ ] **Time to First Trade**: <10 minutes for new users
- [ ] **Feature Adoption**: 80%+ for core features
- [ ] **Support Tickets**: <5 per week
- [ ] **User Satisfaction**: 4.5+/5 average rating

### Phase 5: Scale & Expand
- [ ] **Horizontal Scaling**: Support 500+ concurrent users
- [ ] **Broker Count**: 6-7 brokers integrated
- [ ] **New Features**: 3+ value-added features live
- [ ] **Cost Efficiency**: <$10 per active user per month
- [ ] **Market Share**: 5%+ of active Indian algo traders

---

## Dependencies & Risks

### Critical Dependencies
- **Broker API Stability**: Reliant on Upstox, Zerodha, Fyers, Dhan APIs
  - **Risk**: API downtime impacts trading
  - **Mitigation**: Multi-broker failover, status page monitoring

- **Monitoring Infrastructure**: Prometheus, Grafana, Alertmanager
  - **Risk**: Monitoring blind spots if tools misconfigured
  - **Mitigation**: Redundant monitoring, external health checks

- **Security Audit Vendor**: Penetration testing timeline
  - **Risk**: Delays in engagement scheduling
  - **Mitigation**: Schedule 4-6 weeks in advance, have backup vendor

### Technical Risks
- **Scaling Challenges**: Unknown performance at 500+ users
  - **Mitigation**: Progressive load testing, horizontal scaling validated

- **Data Loss**: Database failure without proper backups
  - **Mitigation**: Daily backups, tested restore procedures, replication

- **Security Breach**: Despite hardening, vulnerabilities may exist
  - **Mitigation**: Defense in depth, monitoring, incident response

### Business Risks
- **User Adoption**: Beta users may not provide sufficient feedback
  - **Mitigation**: Incentivize participation, multiple feedback channels

- **Regulatory Changes**: SEBI rules may change
  - **Mitigation**: Regular compliance reviews, legal advisor on retainer

---

## Resource Requirements

### Team
- **DevOps/SRE**: 1 FTE for operational excellence (Weeks 1-2)
- **Security Engineer**: 0.5 FTE for security hardening (Weeks 3-4)
- **Product/UX**: 1 FTE for beta program and UI improvements (Weeks 1-6)
- **Backend Engineer**: 1 FTE for broker expansion (Weeks 11-14)
- **QA Engineer**: 0.5 FTE for testing throughout

### Budget
- **Monitoring Stack**: $500/month (Prometheus, Grafana, Alertmanager hosting)
- **Secrets Management**: $200/month (Vault/AWS Secrets Manager)
- **Centralized Logging**: $300/month (Loki/ELK hosting + storage)
- **Penetration Testing**: $5,000-$10,000 (one-time)
- **Beta Program Incentives**: $2,000 (gift cards, discounts)
- **Video Production**: $1,000 (tutorial videos)
- **Total Estimated**: $20,000-$25,000 for Phase 4

### Timeline Summary
```
Month 1:
  Week 1: Monitoring stack deployment
  Week 2: Logging + operational procedures
  Week 3: Security audit
  Week 4: Compliance + documentation

Month 2:
  Week 5: Beta program launch
  Week 6: UI improvements
  Week 7-8: High availability deployment
  Week 9-10: Capacity planning

Month 3+:
  Week 11-14: Broker expansion (2-3 brokers)
  Week 15-20: Value-added features
  Ongoing: CI/CD maturity, monitoring refinement
```

---

## Conclusion

Phase 4 transforms AMZF from "production-ready" to "enterprise-grade" through:
1. **Operational Excellence**: Full observability stack and operational rhythms
2. **Security Compliance**: Enterprise secrets management and audit validation
3. **User Satisfaction**: Beta program feedback driving UI/UX improvements
4. **Scalable Foundation**: High availability and capacity for 500+ users
5. **Market Expansion**: 6-7 broker integrations and value-added features

**Next Steps**:
1. Review and approve Phase 4 roadmap
2. Allocate team and budget
3. Begin Week 1: Deploy monitoring stack
4. Establish success metric tracking

---

**Status**: 📋 **READY FOR EXECUTION**
**Last Updated**: January 15, 2026
**Owner**: Engineering Leadership
