# Custom Monitoring Dashboard - Quick Start Guide (CORRECTED)

## ✅ Architecture

**Backend API**: Port 9090 (Java/Undertow)
**Frontend UI**: Port 4000 (React/Vite)
**Proxy**: Vite dev server proxies `/api/*` requests to port 9090

This is the correct setup for your application!

---

## 🚀 Quick Start (3 Steps)

### Step 1: Start Backend (Port 9090)

```bash
# Terminal 1: Start Java backend
cd /Users/jnani/Desktop/AnnuPaper/annu-v04
mvn exec:java -Dexec.mainClass="in.annupaper.bootstrap.App"
```

You should see:
```
=== AnnuPaper v04 Starting ===
✓ Monitoring handler initialized
🚀 Server started on http://0.0.0.0:9090
```

### Step 2: Start Frontend (Port 4000)

```bash
# Terminal 2: Start React frontend
cd /Users/jnani/Desktop/AnnuPaper/annu-v04/frontend
npm run dev
```

You should see:
```
VITE v5.4.11  ready in 500 ms

  ➜  Local:   http://localhost:4000/
  ➜  Network: use --host to expose
  ➜  press h + enter to show help
```

### Step 3: Access Dashboard

```bash
# Open in browser
open http://localhost:4000
```

**Navigation:**
1. Login with your credentials
2. Click the **green "Monitoring" button** in the top navigation bar
3. Dashboard will load with real-time data and auto-refresh every 30 seconds

---

## 📡 API Endpoints

The backend provides 7 monitoring endpoints on port 9090:

| Endpoint | Purpose |
|----------|---------|
| `http://localhost:9090/api/monitoring/system-health` | Active trades, pending operations |
| `http://localhost:9090/api/monitoring/performance` | Daily P&L, win rate, trends |
| `http://localhost:9090/api/monitoring/broker-status` | Broker connectivity |
| `http://localhost:9090/api/monitoring/exit-health` | Exit order metrics |
| `http://localhost:9090/api/monitoring/risk` | Risk exposure |
| `http://localhost:9090/api/monitoring/errors` | Recent errors |
| `http://localhost:9090/api/monitoring/alerts` | Active alerts |

### Test API Directly

```bash
# Test from command line (backend must be running on port 9090)
curl http://localhost:9090/api/monitoring/system-health | jq
curl http://localhost:9090/api/monitoring/performance | jq
curl http://localhost:9090/api/monitoring/alerts | jq
```

### Test from React App (port 4000)

When you access the API from the React app running on port 4000, Vite automatically proxies the requests to port 9090:

```javascript
// In your React app, this:
fetch('/api/monitoring/system-health')

// Is automatically proxied to:
fetch('http://localhost:9090/api/monitoring/system-health')
```

This is configured in `frontend/vite.config.js`:
```javascript
proxy: {
  '/api': {
    target: 'http://localhost:9090',
    changeOrigin: true
  }
}
```

---

## 🎨 Dashboard Features

### Real-Time Metrics Cards
- **Open Trades**: Current positions with Long/Short breakdown
- **Today's P&L**: Profit/loss with color coding (green/red)
- **Win Rate**: Percentage with W/L ratio
- **Total Exposure**: Capital at risk

### Active Alerts Section
Shows critical issues requiring attention:
- 🚨 **CRITICAL**: Broker session expired, stuck exit orders
- ⚠️ **HIGH**: Session expiring soon, high error rate
- ⚡ **MEDIUM**: Slow orders, unusual patterns
- ✓ **All systems operational** (when healthy)

### Interactive Charts (4)
1. **Weekly P&L Trend**: Line chart of last 7 days
2. **Exit Reasons Distribution**: Pie chart showing why trades closed
3. **Risk Concentration**: Bar chart of top 10 symbols by exposure
4. **Pending Operations**: Bar chart of pipeline backlog

### Data Tables (2)
1. **Broker Connection Status**: Session health, expiry times
2. **Recent Errors (Last 24h)**: Error log with source and messages

### Auto-Refresh
- Automatically refreshes every 30 seconds
- Last updated timestamp displayed in header
- Manual refresh on demand

---

## 🔧 Configuration

### Change Backend Port

Edit `src/main/java/in/annupaper/bootstrap/App.java` line 89:
```java
int port = Env.getInt("PORT", 9090); // Change default here
```

Or set environment variable:
```bash
export PORT=9090
mvn exec:java -Dexec.mainClass="in.annupaper.bootstrap.App"
```

### Change Frontend Port

Edit `frontend/vite.config.js` line 7:
```javascript
server: {
  port: 4000, // Change here
  proxy: {
    '/api': {
      target: 'http://localhost:9090', // Update backend port if needed
      changeOrigin: true
    }
  }
}
```

### Change Auto-Refresh Interval

Edit `frontend/components/MonitoringDashboard.jsx` line 14:
```javascript
const REFRESH_INTERVAL = 30000; // 30 seconds (change to desired ms)
```

---

## 🐛 Troubleshooting

### Issue: Dashboard shows "Loading..." forever

**Diagnosis:**
```bash
# Check if backend is running
curl http://localhost:9090/api/health

# Check if backend monitoring API responds
curl http://localhost:9090/api/monitoring/system-health

# Check if frontend is running
curl http://localhost:4000
```

**Common Causes:**
1. Backend not started (port 9090)
2. Frontend not started (port 4000)
3. Database not accessible
4. CORS issues (shouldn't happen with Vite proxy)

**Solutions:**
```bash
# Restart backend
mvn exec:java -Dexec.mainClass="in.annupaper.bootstrap.App"

# Restart frontend
cd frontend && npm run dev

# Check browser console (F12) for errors
```

### Issue: "Failed to fetch monitoring data"

**Check browser console (F12):**
- Look for 404 errors (endpoint not found)
- Look for 500 errors (backend error)
- Look for network errors (connection refused)

**Verify backend routes:**
```bash
# Should return monitoring data
curl http://localhost:9090/api/monitoring/system-health

# Should return 200 OK
curl -I http://localhost:9090/api/monitoring/performance
```

**Check application logs:**
```bash
tail -f logs/annupaper.log
```

### Issue: Charts not displaying

**Check:**
1. Data is being fetched: Open browser Network tab (F12) and verify API calls
2. Data format is correct: Check API response structure
3. Recharts library loaded: Should see no console errors

**Solutions:**
```bash
# Reinstall frontend dependencies
cd frontend
rm -rf node_modules
npm install
npm run dev
```

### Issue: Wrong port in API calls

**If you see errors like "Connection refused on port 8080":**

This means somewhere the wrong port is configured. Check:

1. **Backend default port** (`App.java` line 89): Should be `9090`
2. **React API base URL** (`PyramidDashboardV04.jsx` lines 28-34): Should be `http://localhost:9090`
3. **Vite proxy target** (`vite.config.js` line 10): Should be `http://localhost:9090`

---

## 📂 Files Modified

### Backend (Java)
1. `src/main/java/in/annupaper/bootstrap/App.java`
   - Line 89: Changed default port from 8080 to 9090
   - Lines 547-549: Added MonitoringHandler initialization
   - Lines 606-612: Added 7 monitoring API routes

2. `src/main/java/in/annupaper/transport/http/MonitoringHandler.java` (NEW)
   - 670 lines
   - 7 REST API endpoints
   - Database queries for real-time metrics

### Frontend (React)
1. `frontend/components/MonitoringDashboard.jsx` (NEW)
   - 480 lines
   - React component with Recharts integration
   - Auto-refresh functionality
   - Responsive design with Tailwind CSS

2. `frontend/PyramidDashboardV04.jsx`
   - Line 15: Added MonitoringDashboard import
   - Lines 774, 824-832: Added Monitoring button to Header
   - Line 1021: Added showMonitoring state
   - Lines 1044-1046: Added MonitoringDashboard render
   - Lines 869, 938, 1066-1069, 1088: Wired monitoring navigation

3. `frontend/vite.config.js`
   - Already configured correctly:
     - Line 7: Frontend port 4000
     - Line 10: Backend proxy to port 9090

---

## ✅ Verification Checklist

Before using the dashboard, verify:

- [ ] Backend starts on port 9090
- [ ] Frontend starts on port 4000
- [ ] API responds: `curl http://localhost:9090/api/monitoring/system-health`
- [ ] React app loads: `http://localhost:4000`
- [ ] Login works
- [ ] Monitoring button visible in navigation
- [ ] Dashboard loads with data
- [ ] Charts render correctly
- [ ] Auto-refresh works (check timestamp)
- [ ] No errors in browser console (F12)
- [ ] No errors in backend logs

---

## 🎯 Quick Reference

### Start Everything

```bash
# Terminal 1: Backend (port 9090)
cd /Users/jnani/Desktop/AnnuPaper/annu-v04
mvn exec:java -Dexec.mainClass="in.annupaper.bootstrap.App"

# Terminal 2: Frontend (port 4000)
cd /Users/jnani/Desktop/AnnuPaper/annu-v04/frontend
npm run dev

# Browser: Open
http://localhost:4000
```

### Access Monitoring

1. Login at `http://localhost:4000`
2. Click **green "Monitoring" button** in top nav
3. View real-time metrics and charts
4. Auto-refreshes every 30 seconds

### Test API

```bash
# Backend health check
curl http://localhost:9090/api/health

# Monitoring endpoints
curl http://localhost:9090/api/monitoring/system-health | jq
curl http://localhost:9090/api/monitoring/performance | jq
curl http://localhost:9090/api/monitoring/alerts | jq
```

---

## 🚀 What's Next

### Production Deployment

1. **Build frontend for production:**
```bash
cd frontend
npm run build
# Output: frontend/dist/
```

2. **Serve frontend via backend:**
   - Copy `frontend/dist/` to `src/main/resources/static/`
   - Or use Nginx reverse proxy

3. **Configure production ports:**
```bash
export PORT=9090
export NODE_ENV=production
```

4. **Enable HTTPS** (recommended for production)

5. **Add authentication** to monitoring endpoints

### Enhancements

- Email/Slack alerts for critical issues
- Historical data export (CSV/JSON)
- Custom metrics based on your strategy
- Real-time notifications via WebSocket
- Mobile app integration

---

## 📚 Additional Documentation

- **Complete Setup**: `docs/MONITORING_SETUP.md`
- **Enhancements Summary**: `docs/ENHANCEMENTS_SUMMARY.md`
- **Implementation Details**: `docs/IMPLEMENTATION_COMPLETE.md`

---

## ✨ Summary

Your monitoring dashboard is now correctly configured:

✅ **Backend**: Java/Undertow on port 9090
✅ **Frontend**: React/Vite on port 4000
✅ **Integration**: Vite proxy handles API routing
✅ **Features**: Real-time monitoring, auto-refresh, alerts
✅ **Navigation**: Green "Monitoring" button in app

**Start both servers and access:** http://localhost:4000

Enjoy your real-time monitoring dashboard! 📊📈
