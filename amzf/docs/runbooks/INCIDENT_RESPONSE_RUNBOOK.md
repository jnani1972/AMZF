# Incident Response Runbook

**Last Updated**: January 15, 2026
**Owner**: SRE Team
**Severity**: Critical

## Overview

This runbook provides step-by-step procedures for responding to incidents in the AMZF trading system. Follow these procedures to minimize downtime and restore service quickly.

## Incident Severity Levels

| Severity | Description | Response Time | Example |
|----------|-------------|---------------|---------|
| **P0 - Critical** | Complete service outage | <15 minutes | Application down, database failure |
| **P1 - High** | Significant degradation | <30 minutes | High error rate, broker API failures |
| **P2 - Medium** | Partial degradation | <2 hours | Single broker down, slow responses |
| **P3 - Low** | Minor issues | <24 hours | Non-critical errors, low priority bugs |

---

## Incident Response Flow

```
Incident Detected
    ↓
Assess Severity (P0-P3)
    ↓
Notify On-Call Team
    ↓
Investigate Root Cause
    ↓
Implement Fix
    ↓
Verify Resolution
    ↓
Post-Mortem (P0/P1 only)
```

---

## P0: Critical - Application Down

### Symptoms
- Health check endpoint not responding
- All requests timing out
- Database connection failures
- Application crash/restart loop

### Immediate Actions (Within 5 Minutes)

**1. Verify Outage**:
```bash
# Check health endpoint
curl -f http://localhost:9090/api/health || echo "DOWN"

# Check if process is running
ps aux | grep java | grep amzf

# Check service status
sudo systemctl status amzf
```

**2. Check Logs**:
```bash
# Check last 100 lines
sudo journalctl -u amzf -n 100

# Look for errors
sudo journalctl -u amzf | grep -i "error\|exception\|fatal"
```

**3. Notify Team**:
```bash
# Send alert to Slack/PagerDuty
curl -X POST https://hooks.slack.com/services/YOUR/WEBHOOK/URL \
  -H 'Content-Type: application/json' \
  -d '{"text":"🚨 P0 INCIDENT: AMZF application DOWN"}'
```

### Investigation (Within 10 Minutes)

**Common Causes**:

**A. Database Connection Failure**
```bash
# Test database connectivity
psql -h localhost -U amzf_user -d amzf_prod -c "SELECT 1;"

# Check database status
sudo systemctl status postgresql

# Check connection pool
curl http://localhost:9090/metrics | grep hikari_connections
```

**Fix**:
```bash
# Restart database if needed
sudo systemctl restart postgresql

# Restart application
sudo systemctl restart amzf
```

---

**B. Out of Memory**
```bash
# Check memory usage
free -h
df -h

# Check Java heap
jps -l
jstat -gcutil <pid>

# Check for OutOfMemoryError
sudo journalctl -u amzf | grep OutOfMemoryError
```

**Fix**:
```bash
# Increase heap size
sudo systemctl edit amzf
# Add: Environment="JAVA_OPTS=-Xmx8g -Xms8g"

sudo systemctl daemon-reload
sudo systemctl restart amzf
```

---

**C. Port Already in Use**
```bash
# Check if port 9090 is in use
sudo lsof -i :9090

# Kill conflicting process
sudo kill -9 <pid>

# Restart application
sudo systemctl restart amzf
```

---

**D. Missing Secrets File**
```bash
# Check secrets file
sudo ls -la /secure/secrets.properties

# Verify permissions
sudo stat /secure/secrets.properties
# Should be: -r-------- (400)
```

**Fix**:
```bash
# Restore secrets from backup
sudo cp /backup/secrets.properties /secure/

# Set permissions
sudo chmod 400 /secure/secrets.properties
sudo chown amzf:amzf /secure/secrets.properties

# Restart
sudo systemctl restart amzf
```

---

### Recovery (Within 15 Minutes)

**1. Restart Application**:
```bash
sudo systemctl restart amzf

# Wait 30 seconds for startup
sleep 30

# Verify health
curl http://localhost:9090/api/health
```

**2. Verify Recovery**:
```bash
# Check metrics
curl http://localhost:9090/metrics | grep broker_health_status

# Run quick load test
cd load-tests
./quick-load-test.sh
```

**3. Notify Resolution**:
```bash
# Send recovery notification
curl -X POST https://hooks.slack.com/services/YOUR/WEBHOOK/URL \
  -H 'Content-Type: application/json' \
  -d '{"text":"✅ P0 INCIDENT RESOLVED: AMZF application restored"}'
```

---

## P1: High - High Error Rate

### Symptoms
- Success rate <90%
- Frequent 500 errors
- Multiple broker failures
- Database timeout errors

### Immediate Actions (Within 30 Minutes)

**1. Check Error Rate**:
```bash
# Check metrics
curl http://localhost:9090/metrics | grep broker_orders_total

# Calculate error rate
# errors / (success + errors) * 100
```

**2. Identify Failing Component**:
```bash
# Check broker health
curl http://localhost:9090/metrics | grep broker_health_status

# Check recent errors
sudo journalctl -u amzf --since "30 minutes ago" | grep ERROR
```

**3. Common Causes**:

**A. Broker API Failures**
```bash
# Check broker connectivity
curl http://localhost:9090/api/brokers/health

# Check rate limits
curl http://localhost:9090/metrics | grep broker_rate_limit_hits
```

**Mitigation**:
```bash
# Failover to backup broker
# (Automatic if BrokerFailoverManager configured)

# Manually disable failing broker if needed
# (via admin API or configuration)
```

---

**B. Database Slow Queries**
```bash
# Check slow queries
psql -h localhost -U amzf_user -d amzf_prod -c \
  "SELECT query, query_start, state FROM pg_stat_activity WHERE state = 'active';"

# Check connection pool exhaustion
curl http://localhost:9090/metrics | grep hikari_connections_active
```

**Fix**:
```bash
# Kill long-running queries
psql -h localhost -U amzf_user -d amzf_prod -c \
  "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE query_start < now() - interval '5 minutes';"

# Increase connection pool size (temporary)
# Restart with larger pool
```

---

**C. High Latency from External APIs**
```bash
# Check latency metrics
curl http://localhost:9090/metrics | grep broker_order_latency_seconds

# Check if specific broker is slow
curl http://localhost:9090/metrics | grep "broker_order_latency_seconds_bucket{broker=\"UPSTOX\""
```

**Mitigation**:
```bash
# Increase timeout if transient issue
# Failover to faster broker
# Contact broker support if persistent
```

---

### Recovery

**1. Implement Fix**:
- Address root cause identified above
- Apply temporary workaround if needed
- Schedule permanent fix

**2. Verify Error Rate Decreased**:
```bash
# Monitor for 10 minutes
watch -n 10 'curl -s http://localhost:9090/metrics | grep broker_orders_total'

# Should see error rate decreasing
```

**3. Document Incident**:
```bash
# Create incident ticket
# Document root cause
# Document fix applied
```

---

## P2: Medium - Single Broker Down

### Symptoms
- One broker showing status = 0 (down)
- Failover to backup broker working
- Orders still being placed successfully
- Degraded performance for specific broker

### Actions (Within 2 Hours)

**1. Identify Failed Broker**:
```bash
# Check broker health
curl http://localhost:9090/metrics | grep broker_health_status

# Example output:
# broker_health_status{broker="UPSTOX"} 0.0   ← DOWN
# broker_health_status{broker="ZERODHA"} 1.0  ← UP
```

**2. Investigate Broker Failure**:
```bash
# Check authentication status
curl http://localhost:9090/metrics | grep broker_authentications_total

# Check rate limit hits
curl http://localhost:9090/metrics | grep "broker_rate_limit_hits_total{broker=\"UPSTOX\""

# Check logs for broker errors
sudo journalctl -u amzf | grep "UPSTOX" | grep ERROR
```

**3. Common Causes**:

**A. Token Expired**
```bash
# Check token expiry in logs
sudo journalctl -u amzf | grep "token.*expired"

# Rotate token (see TOKEN_ROTATION_RUNBOOK.md)
```

**B. Broker API Maintenance**
```bash
# Check broker status page
curl https://upstox.com/status

# Temporary: Failover to backup
# Wait for broker to come back online
```

**C. Rate Limit Exceeded**
```bash
# Check rate limit metrics
curl http://localhost:9090/metrics | grep broker_rate_limit_hits

# Reduce request rate
# Increase delay between requests
```

**4. Recovery**:
```bash
# Once issue resolved, broker should auto-recover
# BrokerFailoverManager will detect and transition to UP

# Verify recovery
curl http://localhost:9090/metrics | grep "broker_health_status{broker=\"UPSTOX\""
# Should show: 1.0 (UP)
```

---

## P3: Low - Non-Critical Issues

### Examples
- Isolated order failures
- Slow response times (within SLA)
- Minor logging errors
- Non-urgent bugs

### Actions (Within 24 Hours)

**1. Document Issue**:
```bash
# Create Jira ticket
# Capture relevant logs
# Add to backlog
```

**2. Monitor**:
```bash
# Watch metrics to ensure not escalating
# Set alert if issue worsens
```

**3. Schedule Fix**:
- Plan fix in next sprint
- Low priority unless pattern emerges

---

## Post-Incident Procedures

### For P0 and P1 Incidents

**1. Create Post-Mortem Document** (Within 24 Hours):

Template:
```markdown
# Incident Post-Mortem

**Date**: YYYY-MM-DD
**Severity**: P0/P1
**Duration**: XX minutes
**Impact**: X users affected, Y orders failed

## Timeline
- HH:MM - Incident detected
- HH:MM - Team notified
- HH:MM - Root cause identified
- HH:MM - Fix applied
- HH:MM - Service restored

## Root Cause
[Detailed analysis of what caused the incident]

## Resolution
[What was done to fix the issue]

## Action Items
- [ ] Permanent fix scheduled
- [ ] Monitoring improved
- [ ] Documentation updated
- [ ] Team trained

## Lessons Learned
[What we learned and how to prevent recurrence]
```

**2. Schedule Post-Mortem Meeting** (Within 48 Hours):
- Invite all stakeholders
- Review timeline and root cause
- Discuss prevention strategies
- Assign action items

**3. Implement Preventive Measures**:
- Add monitoring for early detection
- Improve alerting rules
- Update runbooks
- Automate recovery where possible

---

## Escalation Path

### Level 1: On-Call Engineer
- Initial response
- Investigation
- Standard fixes

### Level 2: Team Lead
- Escalate if >30 minutes
- Complex issues
- Need for additional resources

### Level 3: CTO / VP Engineering
- Escalate if >1 hour
- Critical business impact
- Need for vendor escalation

### Level 4: CEO
- Escalate if >2 hours
- Major outage
- Public communication needed

---

## Emergency Contacts

| Level | Role | Name | Phone | Slack |
|-------|------|------|-------|-------|
| L1 | On-Call SRE | TBD | +91-xxx | @oncall |
| L2 | Engineering Lead | TBD | +91-xxx | @eng-lead |
| L3 | CTO | TBD | +91-xxx | @cto |
| L4 | CEO | TBD | +91-xxx | @ceo |

**External Contacts**:
- **Upstox Support**: support@upstox.com, +91-xxx
- **Zerodha Support**: support@zerodha.com, +91-xxx
- **Fyers Support**: support@fyers.in, +91-xxx
- **Dhan Support**: support@dhan.co, +91-xxx
- **AWS Support**: (if using AWS)
- **Database Admin**: dba@company.com

---

## Incident Communication Templates

### Internal Alert (Slack)
```
🚨 **P0 INCIDENT**: [Brief Description]

**Status**: Investigating
**Impact**: [Users/Orders affected]
**Started**: HH:MM
**ETA**: Investigating

**Updates**: Will provide updates every 15 minutes

cc: @oncall @eng-lead
```

### Status Page Update
```
[INVESTIGATING] Service Disruption

We are investigating reports of service disruption.
Our team is working to identify and resolve the issue.

Started: HH:MM IST
Next Update: HH:MM IST
```

### Resolution Message
```
✅ **INCIDENT RESOLVED**: [Brief Description]

**Duration**: XX minutes
**Root Cause**: [Brief explanation]
**Fix Applied**: [What was done]

**Next Steps**: Post-mortem scheduled for [date/time]

Thank you for your patience.
```

---

## Useful Commands Reference

### Check Application Health
```bash
# Health endpoint
curl http://localhost:9090/api/health

# Metrics endpoint
curl http://localhost:9090/metrics | head -20

# Service status
sudo systemctl status amzf

# Process check
ps aux | grep amzf
```

### Check Logs
```bash
# Last 100 lines
sudo journalctl -u amzf -n 100

# Follow logs
sudo journalctl -u amzf -f

# Errors only
sudo journalctl -u amzf | grep ERROR

# Since timestamp
sudo journalctl -u amzf --since "10 minutes ago"
```

### Check Resources
```bash
# Memory
free -h

# Disk
df -h

# CPU
top -bn1 | head -20

# Network
netstat -an | grep 9090
```

### Database
```bash
# Connection test
psql -h localhost -U amzf_user -d amzf_prod -c "SELECT 1;"

# Active connections
psql -h localhost -U amzf_user -d amzf_prod -c "SELECT count(*) FROM pg_stat_activity;"

# Long queries
psql -h localhost -U amzf_user -d amzf_prod -c "SELECT pid, query, query_start FROM pg_stat_activity WHERE state = 'active' AND query_start < now() - interval '1 minute';"
```

### Restart Application
```bash
# Graceful restart
sudo systemctl restart amzf

# Force kill and restart
sudo systemctl kill -s SIGKILL amzf
sudo systemctl restart amzf

# Reload without restart (if supported)
sudo systemctl reload amzf
```

---

## Conclusion

**Incident Response Checklist**:
- [x] Incident severity determined
- [x] Team notified
- [x] Root cause identified
- [x] Fix applied
- [x] Service verified
- [x] Incident documented
- [x] Post-mortem scheduled (P0/P1)
- [x] Preventive measures planned

**Remember**:
1. **Stay Calm** - Panic doesn't help
2. **Communicate** - Keep team and users informed
3. **Document** - Capture logs and timeline
4. **Learn** - Every incident is a learning opportunity
5. **Improve** - Implement preventive measures

**Status**: ✅ **RUNBOOK READY**
