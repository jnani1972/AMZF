# Feed Collector Setup Guide

## Overview

The Feed Collector runs the same application in relay mode, connecting to FYERS and broadcasting ticks to remote clients via WebSocket.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│ AWS VM (ap-south-1)                                         │
│                                                              │
│  ┌───────────────────────────────────────────────┐          │
│  │ Your App (RUN_MODE=FEED_COLLECTOR)            │          │
│  │                                                │          │
│  │  ┌─────────────┐         ┌──────────────┐    │          │
│  │  │ FyersAdapter│────────>│RelayBroadcast│    │          │
│  │  │ (WebSocket) │  ticks  │TickListener  │    │          │
│  │  └─────────────┘         └──────┬───────┘    │          │
│  │        ▲                         │            │          │
│  │        │                         ▼            │          │
│  │    FYERS API           ┌──────────────┐      │          │
│  │  wss://api.fyers.in    │TickRelayServer│     │          │
│  │                        │ :7071/ticks  │      │          │
│  └────────────────────────┴──────┬───────┴──────┘          │
│                                   │                         │
└───────────────────────────────────┼─────────────────────────┘
                                    │ JSON ticks via WS
                                    ▼
                        ┌────────────────────────┐
                        │ Your Main App (local)  │
                        │ RelayWebSocketAdapter  │
                        │ (future implementation)│
                        └────────────────────────┘
```

## Components

### 1. **TickJsonMapper** (`in.annupaper.feedrelay.TickJsonMapper`)
- Serializes `BrokerAdapter.Tick` to JSON
- Uses `toPlainString()` for BigDecimal to preserve precision
- Maps all 12 tick fields (symbol, lastPrice, open, high, low, close, volume, bid, ask, bidQty, askQty, timestamp)

### 2. **TickRelayServer** (`in.annupaper.feedrelay.TickRelayServer`)
- Undertow WebSocket server listening on :7071/ticks
- Maintains set of connected clients
- Broadcasts JSON to all clients when ticks arrive
- No authentication yet (TODO: add token validation)

### 3. **RelayBroadcastTickListener** (`in.annupaper.feedrelay.RelayBroadcastTickListener`)
- Implements `BrokerAdapter.TickListener`
- Called by FyersAdapter when ticks arrive
- Converts tick to JSON and broadcasts to all clients

## Running on AWS VM

### 1. Launch EC2 Instance

**Region:** ap-south-1 (Mumbai) - closest to FYERS infrastructure

**Instance Type:** t3.micro (2 vCPU, 1GB RAM) - sufficient for tick relay

**Security Group:**
```
Inbound Rules:
- Port 22 (SSH) - Your IP only
- Port 7071 (WebSocket) - Your main app IP only
- Port 8080 (Optional: HTTP API) - Your IP only
```

**AMI:** Ubuntu 22.04 LTS or Amazon Linux 2023

### 2. Install Dependencies

```bash
# Connect to VM
ssh -i your-key.pem ubuntu@<VM_PUBLIC_IP>

# Install Java 17
sudo apt update
sudo apt install -y openjdk-17-jdk

# Verify
java -version  # Should show 17.x
```

### 3. Deploy Application

```bash
# From your local machine, build the jar
mvn clean package -DskipTests

# Copy to VM
scp -i your-key.pem target/annu-undertow-ws-v04-0.4.0.jar ubuntu@<VM_PUBLIC_IP>:~/

# Copy DB credentials (if using RDS)
# Or set up PostgreSQL locally on VM
```

### 4. Configure Environment

Create `.env` file on VM:
```bash
# Database (use RDS or local PostgreSQL)
DB_HOST=your-rds-endpoint.ap-south-1.rds.amazonaws.com
DB_PORT=5432
DB_NAME=annupaper
DB_USER=annupaper_user
DB_PASS=your_secure_password

# Feed Collector Mode
RUN_MODE=FEED_COLLECTOR
RELAY_PORT=7071

# Trading disabled on relay VM
TRADING_ENABLED=false

# Optional: FYERS WebSocket URL override (for testing)
# FYERS_WS_URL=wss://api.fyers.in/socket/v2/data/
```

### 5. Run Feed Collector

```bash
# Load environment
export $(cat .env | xargs)

# Run in background with nohup
nohup java -jar annu-undertow-ws-v04-0.4.0.jar > feedcollector.log 2>&1 &

# Or use systemd service (recommended for production)
```

### 6. Create Systemd Service (Recommended)

Create `/etc/systemd/system/feedcollector.service`:
```ini
[Unit]
Description=AnnuPaper Feed Collector
After=network.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/home/ubuntu
EnvironmentFile=/home/ubuntu/.env
ExecStart=/usr/bin/java -jar /home/ubuntu/annu-undertow-ws-v04-0.4.0.jar
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=feedcollector

[Install]
WantedBy=multi-user.target
```

Enable and start:
```bash
sudo systemctl daemon-reload
sudo systemctl enable feedcollector
sudo systemctl start feedcollector

# Check status
sudo systemctl status feedcollector

# View logs
sudo journalctl -u feedcollector -f
```

## Testing the Relay

### 1. Check Logs
```bash
sudo journalctl -u feedcollector -f
```

Expected output:
```
[RELAY] ════════════════════════════════════════════════════════
[RELAY] FEED COLLECTOR MODE ACTIVE
[RELAY] Broadcasting ticks on ws://0.0.0.0:7071/ticks
[RELAY] ════════════════════════════════════════════════════════
[RELAY] Skipping candle builder / signals / exits (collector mode)
[RELAY] ✓ Relay tick listener subscribed for 50 symbols
[RELAY] Skipping MTF backfill (collector mode - relay only)
[FYERS] ✅ WebSocket handshake successful - onOpen called
```

### 2. Test with wscat

From your laptop:
```bash
# Install wscat
npm install -g wscat

# Connect to relay
wscat -c ws://<VM_PUBLIC_IP>:7071/ticks
```

Expected output (JSON ticks):
```json
{"symbol":"NSE:SBIN-EQ","timestamp":1705234567890,"lastPrice":"625.50","open":"623.00","high":"627.80","low":"622.50","close":"625.50","volume":1234567,"bid":"625.45","ask":"625.55","bidQty":500,"askQty":750}
{"symbol":"NSE:RELIANCE-EQ","timestamp":1705234568100,"lastPrice":"2450.30","open":"2445.00","high":"2455.00","low":"2442.00","close":"2450.30","volume":987654,"bid":"2450.25","ask":"2450.35","bidQty":200,"askQty":300}
```

### 3. Monitor Performance

Check VM metrics:
```bash
# CPU usage
top

# Memory usage
free -h

# Network throughput
iftop -i eth0

# WebSocket connections
ss -tn | grep :7071 | wc -l
```

## Expected Behavior

### Feed Collector Mode (RUN_MODE=FEED_COLLECTOR)
✅ OAuth auto-login works (logs URL, you open on laptop once)
✅ FYERS WebSocket connects with retry/backoff
✅ Tick relay server starts on :7071/ticks
✅ Ticks broadcast as JSON to all connected clients
✅ Watchdog monitors FYERS connection health
❌ TickCandleBuilder NOT subscribed (skip candle building)
❌ ExitSignalService NOT subscribed (skip exit monitoring)
❌ MtfSignalGenerator NOT subscribed (skip signal analysis)
❌ MTF backfill NOT run (skip historical data)

### Full Mode (RUN_MODE=FULL, default)
✅ All services run normally (existing behavior)
✅ TickCandleBuilder builds candles
✅ ExitSignalService monitors exits
✅ MtfSignalGenerator generates signals
✅ MTF backfill runs on startup

## Cost Estimate

**AWS t3.micro in ap-south-1:**
- Instance: ~$7.50/month
- Data transfer out: ~$0.09/GB (estimate 10GB/month = $0.90)
- **Total: ~$8.50/month**

## Security Considerations (TODO)

Current implementation has **no authentication**. Before production:

1. **Add token validation** in TickRelayServer.onConnect():
   ```java
   String query = exchange.getRequestURI();
   String token = extractToken(query); // Extract ?token=SECRET
   if (!isValidToken(token)) {
       channel.close();
       return;
   }
   ```

2. **Use TLS/SSL** for WebSocket (wss:// instead of ws://)
   - Add nginx reverse proxy with Let's Encrypt certificate

3. **IP whitelist** in security group (only your main app IP)

4. **Rate limiting** to prevent abuse

## Next Step: RelayWebSocketAdapter

To consume relay ticks in your main app, implement:

**RelayWebSocketAdapter** (implements `BrokerAdapter`)
- `connect()` → connects to `ws://<VM_IP>:7071/ticks`
- `onTextMessage(json)` → parse JSON → create `Tick` → call listeners
- `subscribeTicks(symbols, listener)` → register listener (no subscription message needed)
- All other methods → return empty/null (relay is read-only)

Replace FyersAdapter with RelayWebSocketAdapter in your main app, and your entire pipeline continues unchanged.

## Troubleshooting

### Feed not connecting
```bash
# Check FYERS WebSocket status
sudo journalctl -u feedcollector -f | grep FYERS

# Check if oauth_states table exists
psql -U annupaper_user -d annupaper -c "SELECT COUNT(*) FROM oauth_states;"
```

### No ticks received
```bash
# Check if symbols are subscribed
sudo journalctl -u feedcollector -f | grep "subscribed for"

# Check WebSocket connection
sudo journalctl -u feedcollector -f | grep "onOpen\|CONNECTED"
```

### High latency
- Check network path: VM → FYERS (should be <50ms in Mumbai region)
- Monitor CPU/memory usage on VM
- Consider upgrading to t3.small if CPU is saturated

## Files Created

```
src/main/java/in/annupaper/feedrelay/
├── TickJsonMapper.java              # Tick → JSON serializer
├── TickRelayServer.java             # Undertow WebSocket server
└── RelayBroadcastTickListener.java  # Tick listener that broadcasts

src/main/java/in/annupaper/bootstrap/
└── App.java                         # Updated with RUN_MODE switch
```

## Summary

The Feed Collector is a **lightweight relay** that:
- Reuses your existing FyersAdapter (OAuth, retry, watchdog)
- Adds one more TickListener (relay broadcaster)
- Skips all trading/signal/candle logic
- Broadcasts raw ticks to remote clients via WebSocket

This architecture decouples feed reliability from your local network, allowing your main app to run anywhere while the VM maintains a stable connection to FYERS from Mumbai region.
