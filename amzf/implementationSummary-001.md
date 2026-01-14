# Annu V04 Implementation Summary

## Completed Components (12 files)

### Database Layer
- ✅ `sql/V002__candles_watchlist_exit_signals.sql`
  - Tables: candles, watchlist, exit_signals
  - Append-only triggers on exit_signals
  - Audit table for exit_signals

### Repository Layer
- ✅ `CandleRepository` interface
- ✅ `PostgresCandleRepository` implementation
  - insert(), insertBatch(), findBySymbolAndTimeframe()
  - findLatest(), findAll(), exists(), deleteOlderThan()

### Domain Model Updates
- ✅ `Candle` model - Added `TimeframeType` field

### Broker Layer (All 5 Brokers)
- ✅ `BrokerAdapter` interface - Added `getHistoricalCandles()` method
- ✅ `ZerodhaAdapter` - Kite Connect historical API (stub)
- ✅ `FyersAdapter` - Fyers v3 historical API (stub)
- ✅ `DhanAdapter` - Dhan HQ historical API (stub)
- ✅ `UpstoxAdapter` - Upstox v2 historical API (NEW, stub)
- ✅ `AlpacaAdapter` - Alpaca Bars API (NEW, stub)
- ✅ `BrokerAdapterFactory` - Added UPSTOX, ALPACA cases

### Candle Services
- ✅ `CandleStore` - Dual storage (in-memory + PostgreSQL)
  - addIntraday(), addBatch(), getFromMemory()
  - getFromPostgres(), getRange(), getLatest()
  - warmup(), cleanup(), clearMemory()

---

## Remaining Components (15 files)

### Candle Services (3 files - PENDING)

#### 1. `service/candle/CandleFetcher.java`
**Purpose:** Orchestrates historical candle fetching from brokers

**Key Methods:**
```java
public CompletableFuture<Void> fetchHistorical(String symbol, TimeframeType tf, Instant from, Instant to)
public void fetchForWatchlist(List<String> symbols)
```

**Implementation Pattern:**
- Accepts symbol + timeframe + date range
- Calls broker adapter's getHistoricalCandles()
- Converts HistoricalCandle → Candle (with TimeframeType)
- Stores via CandleStore.addBatch()
- Handles errors (retry logic, fallback)

---

#### 2. `service/candle/CandleReconciler.java`
**Purpose:** One-time backfill of missing candles on startup

**Key Methods:**
```java
public void reconcile()
private void reconcileSymbol(String symbol, TimeframeType tf)
```

**Implementation Pattern:**
- Query watchlist for all active symbols
- For each symbol + timeframe:
  - Check CandleStore.exists()
  - If missing or gaps detected:
    - Calculate date range (e.g., last 30 days)
    - Call CandleFetcher.fetchHistorical()
- Log summary (symbols reconciled, candles fetched)

---

#### 3. `service/candle/TickCandleBuilder.java`
**Purpose:** Build 1min/25min/125min candles from incoming ticks

**Key Methods:**
```java
public void onTick(Tick tick)
private void closeCandle(String symbol, TimeframeType tf, Candle candle)
```

**Implementation Pattern:**
- Maintain in-memory state: symbol → timeframe → partial candle
- On each tick:
  - Update HTF/ITF/LTF partial candles (open, high, low, close, volume)
  - Check if candle interval complete (e.g., 1min boundary)
  - If complete: finalize candle → CandleStore.addIntraday() → emit CANDLE event
- Use ScheduledExecutorService for time-based closes

---

### Exit Signal Components (5 files - PENDING)

#### 4. `domain/enums/ExitReason.java`
```java
public enum ExitReason {
    TARGET_HIT,
    STOP_LOSS,
    TRAILING_STOP,
    TIME_BASED,
    MANUAL,
    RISK_BREACH
}
```

---

#### 5. `domain/model/ExitSignal.java`
```java
public record ExitSignal(
    String exitSignalId,
    String tradeId,
    String signalId,
    String symbol,
    Direction direction,
    ExitReason exitReason,
    BigDecimal exitPrice,
    BigDecimal brickMovement,
    BigDecimal favorableMovement,
    Instant timestamp
) {}
```

---

#### 6. `domain/enums/EventType.java` (MODIFY)
**Add to existing enum:**
```java
// Exit signals
SIGNAL_EXIT,
EXIT_EXECUTED,
EXIT_REJECTED
```

---

#### 7. `service/signal/BrickMovementTracker.java`
**Purpose:** Track last exit price per symbol, enforce minimum brick movement

**Key Methods:**
```java
public boolean shouldAllowExit(String symbol, Direction direction, BigDecimal currentPrice)
private BigDecimal getMinBrickMovement(String symbol)
```

**Implementation Pattern:**
- Store: symbol → direction → last exit price
- On exit check:
  - Calculate movement since last exit
  - Compare against minimum brick (e.g., 0.5% or symbol-specific)
  - Return true/false
- Update last exit price on successful exit

---

#### 8. `service/signal/ExitSignalService.java`
**Purpose:** Monitor open trades, detect exit conditions on every tick

**Key Methods:**
```java
public void onTick(Tick tick)
private void checkExits(Tick tick, List<OpenTrade> openTrades)
private void emitExitSignal(ExitSignal signal)
```

**Implementation Pattern:**
- Subscribe to tick events
- For each tick:
  - Get all open trades for symbol
  - For each trade:
    - Check target hit (price >= target)
    - Check stop loss (price <= stop)
    - Check time-based exit (max hold time)
    - Check risk breach (portfolio limits)
    - If exit condition met:
      - Check BrickMovementTracker.shouldAllowExit()
      - If allowed: create ExitSignal → persist → emit SIGNAL_EXIT event

---

### Frontend (5 files - PENDING)

#### 9. `frontend/package.json`
```json
{
  "name": "annu-pyramid-dashboard",
  "version": "0.4.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "react": "^18.3.1",
    "react-dom": "^18.3.1"
  },
  "devDependencies": {
    "@types/react": "^18.3.3",
    "@types/react-dom": "^18.3.0",
    "@vitejs/plugin-react": "^4.3.3",
    "vite": "^5.4.11"
  }
}
```

---

#### 10. `frontend/vite.config.js`
```javascript
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:9090',
        changeOrigin: true
      },
      '/ws': {
        target: 'ws://localhost:9090',
        ws: true
      }
    }
  }
})
```

---

#### 11. `frontend/index.html`
```html
<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Annu Pyramid Dashboard</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/PyramidDashboardV04.jsx"></script>
  </body>
</html>
```

---

#### 12. `frontend/.env.development`
```env
VITE_API_BASE_URL=http://localhost:9090
VITE_WS_BASE_URL=ws://localhost:9090
```

---

#### 13. `frontend/.env.production`
```env
VITE_API_BASE_URL=https://your-production-domain.com
VITE_WS_BASE_URL=wss://your-production-domain.com
```

---

#### 14. `frontend/PyramidDashboardV04.jsx` (MODIFY)
**Changes needed:**
```javascript
// Replace hardcoded URLs with env variables
// const API_BASE_URL = "http://localhost:8080";
// const WS_BASE_URL = "ws://localhost:8080";
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;
const WS_BASE_URL = import.meta.env.VITE_WS_BASE_URL;
```

---

### Wiring & Documentation (2 files - PENDING)

#### 15. `bootstrap/App.java` (MODIFY)
**Services to wire:**
```java
// Candle services
CandleStore candleStore = new CandleStore(candleRepo);
CandleFetcher candleFetcher = new CandleFetcher(brokerAdapterFactory, candleStore);
CandleReconciler candleReconciler = new CandleReconciler(watchlistRepo, candleFetcher, candleStore);
TickCandleBuilder tickCandleBuilder = new TickCandleBuilder(candleStore, eventService);

// Exit services
BrickMovementTracker brickTracker = new BrickMovementTracker();
ExitSignalService exitSignalService = new ExitSignalService(tradeRepo, brickTracker, eventService);

// Initialize
candleReconciler.reconcile();  // One-time backfill on startup
tickCandleBuilder.start();     // Subscribe to tick stream
exitSignalService.start();     // Subscribe to tick stream
```

**Changes:**
1. Add CandleRepository initialization
2. Create and wire all candle services
3. Create and wire exit signal services
4. Subscribe TickCandleBuilder to DATA broker tick stream
5. Subscribe ExitSignalService to DATA broker tick stream
6. Update MtfAnalysisService constructor to use CandleStore

---

#### 16. `README.md` (MODIFY)
**Add frontend setup section:**

```markdown
## Frontend Setup

### Prerequisites
- Node.js 18+
- npm or yarn

### Development

\```bash
cd frontend

# Install dependencies
npm install

# Start dev server (with hot reload)
npm run dev

# Open browser: http://localhost:3000
\```

### Production Build

\```bash
cd frontend

# Build for production
npm run build

# Output: frontend/dist/

# Serve static files (option 1: using vite preview)
npm run preview

# Serve static files (option 2: using nginx/apache)
# Copy dist/ folder to web server root
\```

### Environment Configuration

Edit `.env.development` or `.env.production` to change API/WS URLs.

```

---

## Build & Run Instructions

### 1. Apply Database Migration

```bash
psql -d annupaper -U postgres -f /tmp/annu-v04/sql/V002__candles_watchlist_exit_signals.sql
```

### 2. Build Backend

```bash
cd /tmp/annu-v04
mvn clean package -DskipTests
```

Expected: `BUILD SUCCESS`, JAR created at `target/annu-undertow-ws-v04-0.4.0.jar`

### 3. Run Backend

```bash
export DB_URL="jdbc:postgresql://localhost:5432/annupaper"
export DB_USER="postgres"
export DB_PASS="postgres"
export PORT=9090
export JWT_SECRET="your-secret-key-min-32-chars-long-for-security"

java -jar target/annu-undertow-ws-v04-0.4.0.jar
```

### 4. Run Frontend (after completing frontend files)

```bash
cd /tmp/annu-v04/frontend
npm install
npm run dev
```

Open browser: `http://localhost:3000`

---

## Testing

### API Health Check
```bash
curl http://localhost:9090/api/health
```

### Register User
```bash
curl -X POST http://localhost:9090/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"test123","displayName":"Test User"}'
```

### Login
```bash
curl -X POST http://localhost:9090/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"test123"}'
```

### Bootstrap (with token)
```bash
curl -H "Authorization: Bearer <token>" http://localhost:9090/api/bootstrap
```

### WebSocket (with wscat)
```bash
wscat -c "ws://localhost:9090/ws?token=<token>"
> {"action":"subscribe","topics":["SIGNAL_GENERATED","INTENT_APPROVED"]}
```

---

## Architecture

**Completed:**
- ✅ Multi-broker support (Zerodha, Fyers, Dhan, Upstox, Alpaca)
- ✅ Historical candle fetching from all brokers
- ✅ Candle storage (dual: in-memory + PostgreSQL)
- ✅ Database schema for candles, watchlist, exit signals

**Pending (16 files):**
- Candle fetcher, reconciler, tick builder
- Exit signal detection with brick movement filter
- Frontend Vite configuration
- Full wiring in App.java

---

## Next Steps

1. Implement remaining 3 candle services (Fetcher, Reconciler, TickBuilder)
2. Implement exit signal services (ExitSignalService, BrickMovementTracker)
3. Setup frontend with Vite
4. Wire all services in App.java
5. Test end-to-end flow

---

## Known Limitations

1. All broker adapters return simulated data (stubs) - real API integration pending
2. MTF analysis uses CandleStore but needs full integration testing
3. No real-time tick feed - simulators generate data
4. Exit signal brick movement thresholds hardcoded (should be configurable per symbol)

---

_Generated: 2026-01-08_
_Status: 12/27 files complete (44%)_
_Port: 9090 (as requested)_
