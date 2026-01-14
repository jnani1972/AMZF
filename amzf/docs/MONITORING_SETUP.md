# Monitoring & Alerting Setup Guide

## Overview

This guide explains how to set up production monitoring and alerting for the AnnuPaper trading system using the provided SQL queries.

## Files

- **`sql/monitoring/dashboard_queries.sql`** - Pre-built queries for monitoring dashboards (Grafana, Metabase, etc.)
- **`sql/monitoring/alerting_rules.sql`** - Alert condition queries that return rows when action is needed

---

## Dashboard Setup

### Option 1: Grafana (Recommended)

**1. Install Grafana**
```bash
# Ubuntu/Debian
sudo apt-get install -y grafana

# macOS
brew install grafana
```

**2. Configure PostgreSQL Data Source**
- Navigate to Configuration → Data Sources → Add data source
- Select PostgreSQL
- Configure connection:
  - Host: `localhost:5432`
  - Database: `annupaper`
  - User: `postgres`
  - SSL Mode: `disable` (for local development)

**3. Import Dashboard Queries**
- Create new dashboard
- Add panels using queries from `dashboard_queries.sql`
- Recommended panels:

| Panel Name | Query Section | Refresh |
|------------|---------------|---------|
| System Health | 1.1 Active Trades Summary | 30s |
| Pending Operations | 1.2 Pending Operations by Type | 1m |
| Broker Status | 1.3 Broker Connection Status | 1m |
| Daily P&L | 2.1 Daily P&L Summary | 5m |
| Weekly Trend | 2.2 Weekly Performance Trend | 15m |
| Symbol Performance | 2.3 Symbol Performance Leaderboard | 15m |
| Exit Reasons | 3.1 Exit Reason Breakdown | 5m |
| Risk Exposure | 5.1 Current Risk Exposure | 1m |
| Recent Errors | 6.1 Recent Errors and Rejections | 2m |

**4. Configure Auto-Refresh**
- Set dashboard refresh to 30 seconds for real-time monitoring
- Use variables for date ranges (e.g., `$__timeFrom()`, `$__timeTo()`)

### Option 2: Metabase

**1. Install Metabase**
```bash
# Download and run
wget https://downloads.metabase.com/latest/metabase.jar
java -jar metabase.jar
```

**2. Add Database Connection**
- Admin Settings → Databases → Add Database
- Select PostgreSQL
- Enter connection details

**3. Create Dashboard**
- Create new dashboard
- Add questions using SQL from `dashboard_queries.sql`
- Enable auto-refresh (Settings → Auto-refresh every 1 minute)

### Option 3: Custom Web Dashboard

Use the queries directly in your custom dashboard application:

```javascript
// Example: Node.js + PostgreSQL
const { Pool } = require('pg');
const pool = new Pool({
  host: 'localhost',
  database: 'annupaper',
  user: 'postgres',
  password: 'your_password'
});

// Fetch real-time metrics
async function getSystemHealth() {
  const result = await pool.query(`
    SELECT * FROM (
      -- Query from 1.1 Active Trades Summary
    ) AS system_health
  `);
  return result.rows[0];
}

// Refresh every 30 seconds
setInterval(async () => {
  const health = await getSystemHealth();
  console.log(health);
}, 30000);
```

---

## Alerting Setup

### Alert Priorities

| Priority | Severity | Response Time | Examples |
|----------|----------|---------------|----------|
| P0 | CRITICAL | Immediate | Broker session expired, stuck exit orders |
| P1 | HIGH | < 15 minutes | High error rate, large drawdown |
| P2 | MEDIUM | < 1 hour | Slow orders, unusual patterns |
| P3 | LOW | < 4 hours | Low activity, informational |

### Option 1: Grafana Alerts

**1. Configure Notification Channels**
- Navigate to Alerting → Notification channels
- Add channels:
  - **Email** - For all priority levels
  - **Slack** - For P0/P1 alerts
  - **PagerDuty** - For P0 alerts only

**2. Create Alert Rules**

For each query in `alerting_rules.sql`:

1. Create dashboard panel with the query
2. Edit panel → Alert tab
3. Configure conditions:
   - **Name**: Alert type (e.g., "BROKER_SESSION_EXPIRED")
   - **Evaluate**: Set frequency based on priority
     - P0: Every 1 minute
     - P1: Every 5 minutes
     - P2: Every 15 minutes
     - P3: Every 1 hour
   - **Condition**: WHEN `count()` IS ABOVE `0` (any rows returned = alert)
4. Add notification channel
5. Configure message template:
```
Alert: {{ .RuleName }}
Severity: {{ .Tags.severity }}
Message: {{ .ValueString }}
Details: Check dashboard for full information
Time: {{ .TimeRange }}
```

**Example Alert Configuration:**

```yaml
# CRITICAL: Broker Session Expired
Alert Rule: BROKER_SESSION_EXPIRED
Evaluate: Every 1 minute for 1 minute
Conditions: count() IS ABOVE 0
Notifications:
  - PagerDuty (Critical)
  - Slack (#trading-alerts)
  - Email (admin@example.com)
Message: "🚨 CRITICAL: Broker session expired. Re-authenticate immediately."
```

### Option 2: Cron Job + Email

**1. Create Alert Script**

```bash
#!/bin/bash
# /usr/local/bin/annupaper-alerts.sh

ALERT_FILE="/tmp/annupaper-alerts-$(date +%Y%m%d-%H%M).txt"
PSQL="psql -U postgres -d annupaper -t -A"

# Run all critical alerts
echo "=== AnnuPaper Alerts ===" > $ALERT_FILE
echo "Generated: $(date)" >> $ALERT_FILE
echo "" >> $ALERT_FILE

# Extract each alert type and run separately
grep -A 20 "-- ALERT:" /path/to/alerting_rules.sql | \
while IFS= read -r line; do
  if [[ $line == "-- ALERT:"* ]]; then
    echo "$line" >> $ALERT_FILE
  fi
done

# Run combined query
$PSQL -f /path/to/alerting_rules.sql >> $ALERT_FILE 2>&1

# Send email if alerts found
if grep -q "CRITICAL\|HIGH" $ALERT_FILE; then
  mail -s "AnnuPaper ALERTS - Action Required" admin@example.com < $ALERT_FILE

  # Send SMS for critical alerts via Twilio (optional)
  if grep -q "CRITICAL" $ALERT_FILE; then
    curl -X POST "https://api.twilio.com/2010-04-01/Accounts/$TWILIO_SID/Messages.json" \
      --data-urlencode "To=$ADMIN_PHONE" \
      --data-urlencode "From=$TWILIO_PHONE" \
      --data-urlencode "Body=CRITICAL ALERT: Check AnnuPaper system immediately" \
      -u "$TWILIO_SID:$TWILIO_TOKEN"
  fi
fi

# Cleanup old alert files
find /tmp -name "annupaper-alerts-*.txt" -mtime +7 -delete
```

**2. Configure Cron**

```bash
# Edit crontab
crontab -e

# Add entries based on priority
# Every 1 minute - Critical alerts only
*/1 * * * * /usr/local/bin/annupaper-alerts.sh --critical

# Every 5 minutes - High priority
*/5 * * * * /usr/local/bin/annupaper-alerts.sh --high

# Every 15 minutes - Medium priority
*/15 * * * * /usr/local/bin/annupaper-alerts.sh --medium

# Every hour - Low priority (during market hours only: 9 AM - 4 PM IST)
0 9-16 * * 1-5 /usr/local/bin/annupaper-alerts.sh --low
```

### Option 3: Slack Webhooks

**1. Create Incoming Webhook**
- Go to Slack → Apps → Incoming Webhooks
- Create webhook URL
- Choose channel (e.g., `#trading-alerts`)

**2. Create Alert Script**

```python
#!/usr/bin/env python3
# /usr/local/bin/annupaper-slack-alerts.py

import psycopg2
import requests
import sys
from datetime import datetime

SLACK_WEBHOOK = "https://hooks.slack.com/services/YOUR/WEBHOOK/URL"
DB_CONFIG = {
    "host": "localhost",
    "database": "annupaper",
    "user": "postgres",
    "password": "your_password"
}

SEVERITY_EMOJI = {
    "CRITICAL": "🚨",
    "HIGH": "⚠️",
    "MEDIUM": "⚡",
    "LOW": "ℹ️"
}

def send_slack_alert(alert):
    emoji = SEVERITY_EMOJI.get(alert['severity'], "📢")
    color = {
        "CRITICAL": "danger",
        "HIGH": "warning",
        "MEDIUM": "#FFA500",
        "LOW": "#36a64f"
    }.get(alert['severity'], "#808080")

    message = {
        "username": "AnnuPaper Monitoring",
        "icon_emoji": ":chart_with_upwards_trend:",
        "attachments": [{
            "color": color,
            "title": f"{emoji} {alert['alert_type'].replace('_', ' ').title()}",
            "text": alert['message'],
            "fields": [
                {"title": "Severity", "value": alert['severity'], "short": True},
                {"title": "Time", "value": datetime.now().strftime("%Y-%m-%d %H:%M:%S"), "short": True}
            ],
            "footer": "AnnuPaper Trading System",
            "footer_icon": "https://platform.slack-edge.com/img/default_application_icon.png"
        }]
    }

    response = requests.post(SLACK_WEBHOOK, json=message)
    return response.status_code == 200

def run_alerts(severity_filter=None):
    conn = psycopg2.connect(**DB_CONFIG)
    cur = conn.cursor()

    # Read and execute alerting rules
    with open('/path/to/alerting_rules.sql', 'r') as f:
        sql = f.read()

    cur.execute(sql)
    alerts = cur.fetchall()

    for alert in alerts:
        alert_dict = {
            "alert_type": alert[0],
            "severity": alert[1],
            "message": alert[-1]  # Last column is always message
        }

        # Filter by severity if specified
        if severity_filter and alert_dict['severity'] not in severity_filter:
            continue

        # Send to Slack
        send_slack_alert(alert_dict)
        print(f"Alert sent: {alert_dict['alert_type']}")

    cur.close()
    conn.close()

if __name__ == "__main__":
    severity = sys.argv[1] if len(sys.argv) > 1 else None
    filter_list = [severity.upper()] if severity else None
    run_alerts(filter_list)
```

**3. Schedule Execution**

```bash
# Crontab entries
*/1 * * * * /usr/local/bin/annupaper-slack-alerts.py CRITICAL
*/5 * * * * /usr/local/bin/annupaper-slack-alerts.py HIGH
*/15 * * * * /usr/local/bin/annupaper-slack-alerts.py MEDIUM
0 * * * * /usr/local/bin/annupaper-slack-alerts.py LOW
```

---

## Monitoring Best Practices

### 1. Dashboard Organization

Create separate dashboards for different roles:

- **Trading Dashboard** (Real-time, 30s refresh)
  - Active trades
  - Current P&L
  - Pending operations
  - Recent errors

- **Performance Dashboard** (5-15m refresh)
  - Daily/weekly P&L trends
  - Win rate analysis
  - Symbol performance
  - Strategy effectiveness

- **Operations Dashboard** (1-5m refresh)
  - Broker connectivity
  - Order execution health
  - Reconciliation status
  - System errors

### 2. Alert Escalation

Configure multi-tier alerting:

```
P0 (CRITICAL):
  → Immediate: PagerDuty + SMS
  → 5 min: If not acknowledged, escalate to manager
  → 15 min: If not resolved, escalate to team

P1 (HIGH):
  → Immediate: Slack + Email
  → 30 min: If not acknowledged, escalate

P2 (MEDIUM):
  → Immediate: Email only
  → Daily digest at end of day

P3 (LOW):
  → Daily digest at market close
```

### 3. Alert Fatigue Prevention

- **Use Appropriate Thresholds**: Don't alert on normal variations
- **Implement Snoozing**: Allow temporary suppression of known issues
- **Alert Grouping**: Combine related alerts (e.g., all rejections from same error code)
- **Time-Based Alerting**: Different thresholds for market hours vs after-hours

### 4. Regular Reviews

- **Weekly**: Review alert history, adjust thresholds
- **Monthly**: Analyze false positive rate, remove noisy alerts
- **Quarterly**: Review dashboard effectiveness, add new metrics

---

## Key Metrics to Monitor

### Real-Time (< 1 minute latency)

1. **Open Trades Count** - Should match expected strategy behavior
2. **Broker Connection Status** - Must be "Connected" during market hours
3. **Pending Exit Orders** - Should be near zero (orders fill quickly)
4. **Recent Errors** - Should be minimal (< 5% error rate)

### Near Real-Time (1-5 minutes)

1. **Daily P&L** - Track cumulative profit/loss
2. **Order Execution Latency** - Should be < 2 seconds average
3. **Exit Reason Distribution** - Understand why trades close
4. **Risk Concentration** - No single symbol > 30% exposure

### Periodic (15 minutes - 1 hour)

1. **Win Rate** - Should be stable and above strategy baseline
2. **Symbol Performance** - Identify profitable/unprofitable instruments
3. **Strategy Effectiveness** - Compare different signal types
4. **Drawdown Analysis** - Monitor maximum adverse movement

---

## Troubleshooting

### Issue: No Data in Dashboard

**Check:**
1. Database connection is active
2. Queries have proper WHERE clauses (e.g., `deleted_at IS NULL`)
3. Date range filters are correct
4. Timezone settings match your database

### Issue: Too Many Alerts

**Solutions:**
1. Increase alert thresholds (e.g., 5 minutes → 10 minutes for stuck orders)
2. Add market hours filters (don't alert outside trading hours)
3. Implement alert deduplication
4. Use rate limiting (max N alerts per hour)

### Issue: False Positive Alerts

**Debug:**
1. Run alert query manually: `psql -d annupaper -f alerting_rules.sql`
2. Check if returned rows are actually problems
3. Adjust WHERE conditions to filter out known edge cases
4. Add business logic (e.g., ignore low-volume test trades)

---

## Production Checklist

Before going live, ensure:

- [ ] Dashboard accessible to all team members
- [ ] Alerts configured for all P0/P1 conditions
- [ ] Notification channels tested (email, Slack, PagerDuty)
- [ ] Alert escalation policy documented
- [ ] On-call rotation established
- [ ] Runbooks created for each alert type
- [ ] Backup monitoring system configured
- [ ] Monitoring data retention policy set (30+ days recommended)
- [ ] Regular review schedule established

---

## Example: Complete Grafana Setup

**1. Create Main Dashboard**

```json
{
  "dashboard": {
    "title": "AnnuPaper Trading Monitor",
    "refresh": "30s",
    "panels": [
      {
        "title": "Open Trades",
        "type": "stat",
        "datasource": "PostgreSQL",
        "targets": [{
          "rawSql": "SELECT COUNT(*) FROM trades WHERE status = 'OPEN' AND deleted_at IS NULL"
        }]
      },
      {
        "title": "Today's P&L",
        "type": "stat",
        "datasource": "PostgreSQL",
        "targets": [{
          "rawSql": "SELECT COALESCE(SUM(realized_pnl), 0) FROM trades WHERE status = 'CLOSED' AND DATE(exit_timestamp) = CURRENT_DATE AND deleted_at IS NULL"
        }]
      },
      {
        "title": "Broker Status",
        "type": "table",
        "datasource": "PostgreSQL",
        "targets": [{
          "rawSql": "-- Query from 1.3 Broker Connection Status"
        }]
      }
    ]
  }
}
```

**2. Configure Alert Rules**

```yaml
# grafana/provisioning/alerting/rules.yaml
groups:
  - name: trading_alerts
    interval: 1m
    rules:
      - alert: BrokerSessionExpired
        expr: count(broker_session_expired_query) > 0
        for: 0m
        labels:
          severity: critical
        annotations:
          summary: "Broker session expired"
          description: "Re-authenticate with broker immediately"
```

---

## Support

For questions or issues with monitoring setup:
- Review query comments in `dashboard_queries.sql` and `alerting_rules.sql`
- Check application logs for data freshness issues
- Verify database indexes are optimal for query performance
- Consider adding custom queries for business-specific metrics
