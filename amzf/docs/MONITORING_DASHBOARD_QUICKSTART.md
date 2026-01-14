# Custom Monitoring Dashboard - Quick Start Guide

## Overview

Your custom monitoring dashboard is now fully integrated into the AnnuPaper application. It provides real-time monitoring of:

- System health (active trades, pending operations)
- Performance metrics (daily P&L, win rate, trends)
- Broker connectivity status
- Exit order health and stuck orders
- Risk exposure and concentrations
- Recent errors and active alerts

**Key Features**:
- ✅ No external dependencies (no Grafana/Metabase needed)
- ✅ Auto-refreshes every 30 seconds
- ✅ Beautiful charts using Chart.js
- ✅ Responsive design (works on mobile)
- ✅ Real-time alerts section
- ✅ Integrated with your existing application

---

## Access the Dashboard

### Step 1: Start the Application

```bash
# Compile and start
mvn clean compile
mvn exec:java -Dexec.mainClass="in.annupaper.bootstrap.App"
```

### Step 2: Open Dashboard in Browser

```
http://localhost:8080/monitoring/dashboard.html
```

That's it! The dashboard will automatically start refreshing data every 30 seconds.

---

## Dashboard Sections

### 1. **Key Metrics Cards** (Top Row)

Four metric cards showing:

| Metric | Description | Color Coding |
|--------|-------------|--------------|
| Open Trades | Current open positions (Long/Short) | - |
| Today's P&L | Profit/Loss for current day | Green (profit) / Red (loss) |
| Win Rate | Winning % with W/L breakdown | - |
| Total Exposure | Total capital at risk | - |

### 2. **Active Alerts Section**

Shows critical issues requiring immediate attention:

- **🚨 Critical**: Broker session expired, stuck exit orders
- **⚠️ High**: Session expiring soon, high error rate
- **⚡ Medium**: Slow orders, unusual patterns

When all systems are healthy, displays: **"✓ All systems operational"**

### 3. **Charts** (4 Interactive Charts)

#### Weekly P&L Trend
- Line chart showing daily P&L for last 7 days
- Hover to see exact values
- Quickly identify performance patterns

#### Exit Reasons Distribution
- Doughnut chart showing why trades are closing
- Categories: TARGET_HIT, STOP_LOSS, TRAILING_STOP, etc.
- Helps understand strategy effectiveness

#### Risk Concentration
- Bar chart showing top 10 symbols by exposure
- Percentage of total portfolio per symbol
- Identifies concentration risk

#### Pending Operations
- Bar chart showing backlog in processing pipeline
- Trade Intents, Exit Intents, Orders
- Alerts if operations are stuck

### 4. **Data Tables** (Bottom Row)

#### Broker Connection Status
- Broker name, user, connection status
- Session health (VALID, EXPIRING_SOON, EXPIRED)
- Last connection time

#### Recent Errors (Last 24h)
- Timestamp, source (Trade Intent/Exit Intent/Order)
- Symbol and error message
- Shows last 10 errors

---

## API Endpoints

The dashboard uses these REST APIs (you can also call them directly):

### System Health
```bash
curl http://localhost:8080/api/monitoring/system-health | jq
```

**Response:**
```json
{
  "activeTrades": {
    "totalOpenTrades": 5,
    "longPositions": 3,
    "shortPositions": 2,
    "totalExposure": 250000.00,
    "avgHoldingHours": 2.5
  },
  "pendingOperations": [
    {"type": "Trade Intents", "count": 2, "oldest": "2026-01-14T10:30:00"},
    {"type": "Exit Intents", "count": 0, "oldest": null},
    {"type": "Orders", "count": 1, "oldest": "2026-01-14T10:35:00"}
  ]
}
```

### Performance Metrics
```bash
curl http://localhost:8080/api/monitoring/performance | jq
```

**Response:**
```json
{
  "today": {
    "tradesClosed": 12,
    "winningTrades": 8,
    "losingTrades": 4,
    "winRatePercent": 66.67,
    "totalPnl": 15420.50,
    "avgPnl": 1285.04,
    "bestTrade": 4500.00,
    "worstTrade": -1200.00
  },
  "weeklyTrend": [
    {"date": "2026-01-14", "tradesClosed": 12, "dailyPnl": 15420.50, "winRatePercent": 66.67},
    {"date": "2026-01-13", "tradesClosed": 15, "dailyPnl": 8230.25, "winRatePercent": 60.00}
  ]
}
```

### Broker Status
```bash
curl http://localhost:8080/api/monitoring/broker-status | jq
```

### Exit Order Health
```bash
curl http://localhost:8080/api/monitoring/exit-health | jq
```

### Risk Metrics
```bash
curl http://localhost:8080/api/monitoring/risk | jq
```

### Recent Errors
```bash
curl http://localhost:8080/api/monitoring/errors | jq
```

### Active Alerts
```bash
curl http://localhost:8080/api/monitoring/alerts | jq
```

**Example Alert Response:**
```json
{
  "alerts": [
    {
      "alertType": "BROKER_SESSION_EXPIRED",
      "severity": "CRITICAL",
      "message": "Broker session expired: Zerodha",
      "details": {
        "brokerName": "Zerodha",
        "userBrokerId": "ub-123",
        "username": "john@example.com",
        "expiredAt": "2026-01-14T09:00:00"
      }
    }
  ]
}
```

---

## Customization

### Change Refresh Interval

Edit `dashboard.html` line 432:

```javascript
const REFRESH_INTERVAL = 30000; // 30 seconds (change to desired ms)
```

### Add More Metrics

1. **Add new metric card** (lines 92-100):
```html
<div class="metric-card">
    <div class="metric-label">Your Metric</div>
    <div class="metric-value" id="yourMetric">-</div>
    <div class="metric-subtext" id="yourMetricSubtext">-</div>
</div>
```

2. **Update JavaScript** to fetch and display data:
```javascript
document.getElementById('yourMetric').textContent = data.yourValue;
```

### Add New Chart

1. **Add chart container** (lines 201-207):
```html
<div class="chart-card">
    <h2>Your Chart Title</h2>
    <div class="chart-container">
        <canvas id="yourChart"></canvas>
    </div>
</div>
```

2. **Create chart function** (around line 580):
```javascript
function updateYourChart(data) {
    const ctx = document.getElementById('yourChart').getContext('2d');
    // Chart.js configuration...
}
```

### Modify Colors

Edit CSS variables in `<style>` section:

```css
/* Primary gradient */
background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);

/* Positive/Negative colors */
.metric-value.positive { color: #10b981; }
.metric-value.negative { color: #ef4444; }

/* Alert colors */
.alert-item.critical { border-left-color: #ef4444; }
.alert-item.high { border-left-color: #f59e0b; }
```

---

## Troubleshooting

### Dashboard Shows "Loading..." Forever

**Check:**
1. Application is running: `curl http://localhost:8080/api/health`
2. Database is accessible
3. Browser console for JavaScript errors (F12 → Console)

**Fix:**
```bash
# Restart application
mvn exec:java -Dexec.mainClass="in.annupaper.bootstrap.App"
```

### Charts Not Displaying

**Check:**
1. Chart.js loaded: Open browser console, type `Chart` (should not be undefined)
2. Data is available: `curl http://localhost:8080/api/monitoring/performance`

**Fix:**
- Clear browser cache (Ctrl+Shift+R / Cmd+Shift+R)
- Check if CDN is accessible: https://cdn.jsdelivr.net/npm/chart.js@4.4.0

### "No Data" in Tables

**Possible reasons:**
1. No trades executed yet
2. No brokers configured
3. No errors in last 24 hours (this is good!)

**Verify:**
```sql
-- Check if data exists
SELECT COUNT(*) FROM trades WHERE status = 'OPEN';
SELECT COUNT(*) FROM user_brokers WHERE deleted_at IS NULL;
```

### API Returns 500 Error

**Check application logs:**
```bash
tail -f logs/annupaper.log
```

**Common causes:**
1. Database connection lost
2. Missing table columns (run migrations)
3. Invalid SQL query (check PostgreSQL version compatibility)

---

## Performance Optimization

### For Large Databases (10,000+ trades)

1. **Add database indexes** (if not already present):
```sql
-- Check existing indexes
\di orders
\di trades

-- Add missing indexes
CREATE INDEX IF NOT EXISTS idx_trades_exit_timestamp
ON trades(exit_timestamp DESC) WHERE deleted_at IS NULL;
```

2. **Limit data ranges** in queries:
```sql
-- Change from
WHERE exit_timestamp >= CURRENT_DATE - INTERVAL '7 days'

-- To
WHERE exit_timestamp >= CURRENT_DATE - INTERVAL '3 days'
```

3. **Use materialized views** for expensive queries:
```sql
CREATE MATERIALIZED VIEW daily_pnl_summary AS
SELECT
    DATE(exit_timestamp) as trade_date,
    COUNT(*) as trades_closed,
    SUM(realized_pnl) as daily_pnl
FROM trades
WHERE status = 'CLOSED' AND deleted_at IS NULL
GROUP BY DATE(exit_timestamp);

-- Refresh periodically
REFRESH MATERIALIZED VIEW daily_pnl_summary;
```

---

## Integration with Existing Systems

### Embed Dashboard in Admin Panel

Add link in your admin navigation:

```html
<a href="/monitoring/dashboard.html">📊 Monitoring</a>
```

### Export Data to External Tools

Use API endpoints to feed data to:

**Prometheus:**
```python
# Export metrics in Prometheus format
from prometheus_client import start_http_server, Gauge
import requests

open_trades = Gauge('annupaper_open_trades', 'Number of open trades')

def collect_metrics():
    response = requests.get('http://localhost:8080/api/monitoring/system-health')
    data = response.json()
    open_trades.set(data['activeTrades']['totalOpenTrades'])

if __name__ == '__main__':
    start_http_server(8000)
    while True:
        collect_metrics()
        time.sleep(30)
```

**Slack Bot:**
```python
import requests
import time

SLACK_WEBHOOK = "https://hooks.slack.com/services/YOUR/WEBHOOK"

def send_daily_summary():
    response = requests.get('http://localhost:8080/api/monitoring/performance')
    data = response.json()

    message = {
        "text": f"📊 Daily Summary\n"
                f"Trades: {data['today']['tradesClosed']}\n"
                f"P&L: ₹{data['today']['totalPnl']}\n"
                f"Win Rate: {data['today']['winRatePercent']}%"
    }

    requests.post(SLACK_WEBHOOK, json=message)

# Run at market close (3:30 PM IST)
send_daily_summary()
```

---

## Mobile Access

The dashboard is fully responsive and works on mobile devices.

### Recommended Mobile Browsers:
- **iOS**: Safari, Chrome
- **Android**: Chrome, Firefox

### Tips for Mobile Viewing:
1. Use landscape orientation for better chart visibility
2. Pinch to zoom on charts for details
3. Refresh manually by pulling down (in addition to auto-refresh)

---

## Security Considerations

### Production Deployment

1. **Add authentication** to monitoring endpoints:

```java
// In App.java, before routes registration
TokenValidator tokenValidator = new TokenValidator(jwtService);

// Add auth check
.get("/api/monitoring/system-health", exchange -> {
    if (!tokenValidator.isValid(exchange)) {
        exchange.setStatusCode(401);
        exchange.endExchange();
        return;
    }
    monitoringHandler.getSystemHealth(exchange);
})
```

2. **Restrict IP access** (firewall level):
```bash
# Only allow from office IP
iptables -A INPUT -p tcp --dport 8080 -s 203.0.113.0/24 -j ACCEPT
iptables -A INPUT -p tcp --dport 8080 -j DROP
```

3. **Use HTTPS** in production:
```java
// Add SSL configuration in App.java
Undertow server = Undertow.builder()
    .addHttpsListener(8443, "0.0.0.0", sslContext)
    .setHandler(routes)
    .build();
```

---

## Advanced Features

### Email Alerts Integration

Create scheduled job to check alerts and send emails:

```java
// EmailAlertService.java
public class EmailAlertService implements Runnable {
    private final MonitoringHandler monitoringHandler;
    private final EmailService emailService;

    @Override
    public void run() {
        // Get active alerts
        List<Alert> alerts = monitoringHandler.getActiveAlerts();

        // Send email if critical alerts exist
        alerts.stream()
            .filter(a -> a.severity == Severity.CRITICAL)
            .forEach(alert -> emailService.send(
                "admin@example.com",
                "Critical Alert: " + alert.message,
                alert.details
            ));
    }
}

// Schedule in App.java
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
scheduler.scheduleAtFixedRate(
    new EmailAlertService(monitoringHandler, emailService),
    0, 5, TimeUnit.MINUTES
);
```

### Historical Data Export

Add export endpoint to download historical data:

```java
// In MonitoringHandler.java
public void exportHistoricalData(HttpServerExchange exchange) {
    String format = exchange.getQueryParameters().get("format").getFirst(); // csv, json
    String days = exchange.getQueryParameters().get("days").getFirst(); // 7, 30, 90

    // Query data
    List<Trade> trades = tradeRepo.findClosed(Integer.parseInt(days));

    if ("csv".equals(format)) {
        String csv = convertToCSV(trades);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/csv");
        exchange.getResponseHeaders().put(
            Headers.CONTENT_DISPOSITION,
            "attachment; filename=trades_" + days + "days.csv"
        );
        exchange.getResponseSender().send(csv);
    } else {
        sendJson(exchange, trades);
    }
}
```

---

## Support

### Questions or Issues?

1. **Check logs**: `tail -f logs/annupaper.log`
2. **Test API directly**: `curl http://localhost:8080/api/monitoring/system-health`
3. **Review SQL queries**: `sql/monitoring/dashboard_queries.sql`
4. **Database debugging**: `psql -d annupaper -c "SELECT COUNT(*) FROM trades"`

### Feature Requests

To add custom metrics:
1. Add SQL query in `MonitoringHandler.java`
2. Create new API endpoint
3. Update dashboard HTML/JavaScript to fetch and display

---

## Summary

Your custom monitoring dashboard is now ready!

**Access:** http://localhost:8080/monitoring/dashboard.html

**Features:**
- ✅ Real-time monitoring (30s refresh)
- ✅ 7 REST API endpoints
- ✅ 4 interactive charts
- ✅ Active alerts section
- ✅ Broker status table
- ✅ Recent errors table
- ✅ Mobile responsive
- ✅ No external dependencies

**Next Steps:**
1. Start application: `mvn exec:java -Dexec.mainClass="in.annupaper.bootstrap.App"`
2. Open dashboard: http://localhost:8080/monitoring/dashboard.html
3. Customize as needed (colors, metrics, refresh interval)
4. Add authentication for production
5. Set up email alerts (optional)

Enjoy your new monitoring dashboard! 📊
