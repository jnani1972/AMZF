# annu-undertow-ws-v04

Multi-user, multi-broker Annu Pyramid Trading System.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         MARKET DATA (Single Source)                      │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │  DATA Broker (Admin-owned) - Zerodha/Fyers/etc                   │   │
│  │  • Ticks/LTP                                                      │   │
│  │  • Candles (HTF 125min, ITF 25min, LTF 1min)                     │   │
│  │  • Market Status                                                  │   │
│  └───────────────────────────────┬──────────────────────────────────┘   │
└──────────────────────────────────┼──────────────────────────────────────┘
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         SIGNAL GENERATION                                │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │  SignalService                                                    │   │
│  │  • MTF Analysis (HTF/ITF/LTF zones)                              │   │
│  │  • Confluence Detection (TRIPLE required)                         │   │
│  │  • P(win), Kelly calculation                                      │   │
│  │  • Output: SIGNAL_GENERATED event (GLOBAL)                        │   │
│  └───────────────────────────────┬──────────────────────────────────┘   │
└──────────────────────────────────┼──────────────────────────────────────┘
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      SIGNAL FAN-OUT (Per User-Broker)                    │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │  ExecutionOrchestrator                                            │   │
│  │                                                                   │   │
│  │  Signal ──┬─► User1-BrokerA ─► ValidationService ─► TradeIntent  │   │
│  │           ├─► User1-BrokerB ─► ValidationService ─► TradeIntent  │   │
│  │           ├─► User2-BrokerC ─► ValidationService ─► TradeIntent  │   │
│  │           └─► User3-BrokerA ─► ValidationService ─► TradeIntent  │   │
│  │                                                                   │   │
│  │  Same signal → Different decisions per user-broker combo          │   │
│  └───────────────────────────────┬──────────────────────────────────┘   │
└──────────────────────────────────┼──────────────────────────────────────┘
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      TRADE EXECUTION (Per Broker)                        │
│  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐            │
│  │  Zerodha       │  │  Fyers         │  │  Dhan          │            │
│  │  Adapter       │  │  Adapter       │  │  Adapter       │            │
│  └───────┬────────┘  └───────┬────────┘  └───────┬────────┘            │
│          │                   │                   │                      │
│          ▼                   ▼                   ▼                      │
│  ┌────────────────────────────────────────────────────────────────┐    │
│  │  ORDER_FILLED / TRADE_CREATED events (USER_BROKER scoped)      │    │
│  └────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────┘
```

## Event Scoping Model

| Scope       | Description                          | Examples                                |
|-------------|--------------------------------------|-----------------------------------------|
| GLOBAL      | Broadcast to ALL connected users     | SIGNAL_GENERATED, MARKET_STATUS, TICK   |
| USER        | Sent to specific user only           | PORTFOLIO_UPDATED, CAPITAL_UPDATE       |
| USER_BROKER | Sent to specific user+broker combo   | ORDER_FILLED, TRADE_CREATED, VALIDATION_FAILED |

## Broker Roles

| Role | Description | Count | Owner |
|------|-------------|-------|-------|
| DATA | Market data source (ticks, candles, LTP) | Exactly 1 | Admin |
| EXEC | Trade execution (order placement, fills) | 1..N per user | User |

## Prerequisites

- Java 17+
- PostgreSQL 12+
- Apply migrations: `psql -d annupaper -f sql/V001__multi_user_broker_schema.sql`

## Configuration (Environment Variables)

```bash
# Database
export DB_URL="jdbc:postgresql://localhost:5432/annupaper"
export DB_USER="postgres"
export DB_PASS="postgres"
export DB_POOL_SIZE=10

# Server
export PORT=8080
export WS_BATCH_FLUSH_MS=100
```

## Build & Run

```bash
# Build fat JAR
mvn -q -DskipTests clean package

# Run
java -jar target/annu-undertow-ws-v04-0.4.0.jar
```

## API Endpoints

All endpoints (except /api/health) require authentication via:
- Header: `Authorization: Bearer <token>`
- Query param: `?token=<token>`

### Demo Tokens (for testing)
- `demo-token-user1` → User U1
- `demo-token-user2` → User U2
- `demo-token-admin` → Admin

### Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | /api/health | No | Health check |
| GET | /api/bootstrap | Yes | User info, portfolio, brokers |
| GET | /api/events?afterSeq=0&limit=200 | Yes | Events (filtered by user scope) |
| GET | /api/events?afterSeq=0&userBrokerId=UB1 | Yes | Events for specific broker |
| GET | /api/brokers | Yes | User's broker connections |
| GET | /api/signals | Yes | Recent signals |
| GET | /api/intents?signalId=xxx | Yes | User's trade intents |
| WS | /ws?token=xxx | Yes | WebSocket (scoped event stream) |

## WebSocket Protocol

### Connection
```
ws://localhost:8080/ws?token=demo-token-user1
```

### Client → Server Messages

```json
// Subscribe to event types
{"action":"subscribe","topics":["SIGNAL_GENERATED","TRADE_CREATED"]}

// Subscribe to specific brokers (filter USER_BROKER events)
{"action":"subscribe","brokers":["UB1","UB2"]}

// Unsubscribe
{"action":"unsubscribe","topics":["TICK"]}

// Ping
{"action":"ping","nonce":"12345"}
```

### Server → Client Messages

```json
// ACK (connection/subscription confirmed)
{
  "type": "ACK",
  "payload": {
    "action": "subscribe",
    "userId": "U1",
    "sessionId": "abc-123",
    "topics": ["SIGNAL_GENERATED","TRADE_CREATED"],
    "brokers": ["UB1"]
  },
  "ts": "2025-01-08T10:00:00Z",
  "seq": 1
}

// PONG
{
  "type": "PONG",
  "payload": {"nonce":"12345","pong":true},
  "ts": "...",
  "seq": 2
}

// BATCH (events delivered in batches)
{
  "type": "BATCH",
  "payload": {
    "events": [
      {
        "type": "SIGNAL_GENERATED",
        "scope": "GLOBAL",
        "payload": {"signalId":"S1","symbol":"SBIN","direction":"BUY",...},
        "ts": "...",
        "seq": 100,
        "signalId": "S1"
      },
      {
        "type": "INTENT_APPROVED",
        "scope": "USER_BROKER",
        "payload": {"intentId":"I1","qty":50,"value":"42500.00",...},
        "ts": "...",
        "seq": 101,
        "signalId": "S1",
        "intentId": "I1"
      }
    ]
  },
  "ts": "...",
  "seq": 3
}
```

## Database Schema

### Core Tables

| Table | Description |
|-------|-------------|
| users | User accounts |
| brokers | Broker definitions (Zerodha, Fyers, Dhan, etc.) |
| user_brokers | User-broker links with credentials, limits, role |
| portfolios | Per-user portfolios |
| signals | Generated signals (from DATA broker) |
| trade_intents | Per user-broker validation results |
| trades | Executed trades |
| trade_events | Append-only event log (source of truth) |
| trade_events_audit | Audit trail + immutability guards |

### Event Schema

```sql
CREATE TABLE trade_events (
    seq             BIGSERIAL PRIMARY KEY,
    event_type      TEXT NOT NULL,
    scope           TEXT NOT NULL DEFAULT 'GLOBAL',  -- GLOBAL | USER | USER_BROKER
    user_id         TEXT,                            -- null for GLOBAL
    broker_id       TEXT,                            -- null for GLOBAL/USER
    user_broker_id  TEXT,                            -- null for GLOBAL/USER
    payload         JSONB NOT NULL,
    signal_id       TEXT,                            -- correlation
    intent_id       TEXT,
    trade_id        TEXT,
    order_id        TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      TEXT
);
```

## Signal Flow

```
1. DATA Broker tick arrives
   │
2. SignalService analyzes MTF (HTF/ITF/LTF)
   │
3. If conditions met (triple confluence, buy zone):
   │  └─► SIGNAL_GENERATED event (GLOBAL)
   │
4. ExecutionOrchestrator.fanOutSignal()
   │  └─► For each active EXEC user-broker:
   │       │
   │       5. ValidationService.validate()
   │          │  • Check capital
   │          │  • Check exposure limits
   │          │  • Check symbol allowed
   │          │  • Check product eligibility
   │          │  • Check log constraints
   │          │  • Calculate qty, value
   │          │
   │          ├─► PASS: TradeIntent(APPROVED)
   │          │   └─► INTENT_APPROVED event (USER_BROKER)
   │          │
   │          └─► FAIL: TradeIntent(REJECTED)
   │              └─► INTENT_REJECTED event (USER_BROKER)
   │
6. Execute approved intents
   └─► ORDER_CREATED → ORDER_FILLED → TRADE_CREATED
       (all USER_BROKER scoped)
```

## Validation Error Codes

| Code | Description |
|------|-------------|
| INSUFFICIENT_CAPITAL | Not enough capital available |
| EXCEEDS_MAX_PER_TRADE | Trade value exceeds max per trade limit |
| EXCEEDS_MAX_EXPOSURE | Would exceed maximum exposure limit |
| EXCEEDS_PORTFOLIO_LOG_LOSS | Would exceed portfolio log loss constraint |
| MAX_OPEN_TRADES_REACHED | Maximum open trades reached |
| SYMBOL_NOT_ALLOWED | Symbol not in allowed list |
| SYMBOL_BLOCKED | Symbol is blocked |
| NO_TRIPLE_CONFLUENCE | Triple confluence required but not present |
| BELOW_MIN_WIN_PROB | Win probability below minimum threshold |
| BELOW_MIN_KELLY | Kelly criterion below minimum threshold |
| BROKER_NOT_CONNECTED | Broker not connected |
| DAILY_LOSS_LIMIT_REACHED | Daily loss limit reached |
| IN_COOLDOWN_PERIOD | In cooldown period after loss |

## Project Structure

```
annu-undertow-ws-v04/
├── pom.xml
├── README.md
├── sql/
│   └── V001__multi_user_broker_schema.sql
└── src/main/java/in/annupaper/
    ├── bootstrap/
    │   └── App.java                    # Entry point
    ├── domain/
    │   ├── enums/
    │   │   ├── BrokerRole.java         # DATA | EXEC
    │   │   ├── Direction.java          # BUY | SELL
    │   │   ├── EventScope.java         # GLOBAL | USER | USER_BROKER
    │   │   ├── EventType.java          # All event types
    │   │   ├── IntentStatus.java       # PENDING | APPROVED | REJECTED | EXECUTED
    │   │   ├── SignalType.java         # ENTRY | EXIT | SCALE_IN | SCALE_OUT
    │   │   └── ValidationErrorCode.java
    │   └── model/
    │       ├── Broker.java
    │       ├── Signal.java
    │       ├── TradeEvent.java         # With scope, userId, brokerId
    │       ├── TradeIntent.java        # Per user-broker validation result
    │       ├── User.java
    │       ├── UserBroker.java         # User-broker link with role
    │       ├── ValidationResult.java
    │       └── WsSession.java          # Authenticated WS session
    ├── repository/
    │   ├── PostgresTradeEventRepository.java
    │   ├── TradeEventRepository.java
    │   └── UserBrokerRepository.java
    ├── service/
    │   ├── core/
    │   │   └── EventService.java       # Scoped event emission
    │   ├── execution/
    │   │   └── ExecutionOrchestrator.java  # Signal fan-out
    │   ├── signal/
    │   │   └── SignalService.java      # Signal generation
    │   └── validation/
    │       └── ValidationService.java  # Per user-broker validation
    ├── transport/
    │   ├── http/
    │   │   └── ApiHandlers.java        # REST API with auth + scoping
    │   └── ws/
    │       └── WsHub.java              # WS with auth + scoped delivery
    └── util/
        └── Env.java
```

## Key Guarantees

| Guarantee | Implementation |
|-----------|----------------|
| No event leaks | Events filtered by scope before delivery |
| Append-only events | PostgreSQL triggers block UPDATE/DELETE |
| Audit trail | All events logged to trade_events_audit |
| Token auth | WS and HTTP require valid token |
| Signal consistency | Single DATA broker → single signal source |
| Independent validation | Each user-broker validated independently |

## Testing with curl

```bash
# Health check
curl http://localhost:8080/api/health

# Bootstrap (with auth)
curl -H "Authorization: Bearer demo-token-user1" http://localhost:8080/api/bootstrap

# Events (filtered by user)
curl -H "Authorization: Bearer demo-token-user1" "http://localhost:8080/api/events?afterSeq=0"

# Events for specific broker
curl -H "Authorization: Bearer demo-token-user1" "http://localhost:8080/api/events?afterSeq=0&userBrokerId=UB1"
```

## Testing with wscat

```bash
# Connect as User1
wscat -c "ws://localhost:8080/ws?token=demo-token-user1"

# Subscribe to signals
> {"action":"subscribe","topics":["SIGNAL_GENERATED","INTENT_APPROVED","INTENT_REJECTED"]}

# Ping
> {"action":"ping","nonce":"test123"}
```

## Reliability Rule

```
1. Persist event to PostgreSQL (source of truth)
2. Then broadcast via WebSocket (batched, scoped)

UI can always resync: GET /api/events?afterSeq=<lastSeen>
```

## Next Steps (Not Implemented)

1. **Real broker adapters** - Zerodha, Fyers, Dhan API integration
2. **JWT authentication** - Replace stub tokens with proper JWT
3. **MTF Analysis Service** - Full multi-timeframe candle analysis
4. **Exit signal service** - Exit condition evaluation
5. **Real-time price updates** - WebSocket feed from DATA broker
6. **Dashboard integration** - Connect React dashboard

## License

Proprietary - AnnuPaper Trading System

---

## v04 Complete Features

### 1. JWT Authentication
```bash
# Register
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"password123","displayName":"Test User"}'

# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"password123"}'

# Use token
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/bootstrap
```

### 2. Broker Adapters (Stubs)
- **ZerodhaAdapter** - Kite Connect compatible
- **FyersAdapter** - Fyers API v3 compatible  
- **DhanAdapter** - Dhan HQ API compatible

Each adapter implements:
- `connect()` / `disconnect()`
- `placeOrder()` / `modifyOrder()` / `cancelOrder()`
- `getOrderStatus()` / `getOpenOrders()`
- `getPositions()` / `getHoldings()` / `getFunds()`
- `getLtp()` / `subscribeTicks()` / `unsubscribeTicks()`

### 3. MTF Analysis Service
```java
MtfAnalysisService mtfService = new MtfAnalysisService(candleProvider);
MTFConfig config = mtfService.analyze("SBIN", currentPrice);

// Check confluence
config.hasTripleConfluence();    // true if all 3 TFs in buy zone
config.confluenceType();          // NONE, SINGLE, DOUBLE, TRIPLE
config.confluenceStrength();      // NONE, WEAK, MODERATE, STRONG, VERY_STRONG
config.confluenceScore();         // 0.0 (best) to 1.0 (worst)

// Get boundaries
config.effectiveFloor();          // max(htfLow, itfLow, ltfLow)
config.effectiveCeiling();        // min(htfHigh, itfHigh, ltfHigh)
```

### 4. PostgreSQL Repositories
- `PostgresTradeEventRepository` - Event sourcing with scoped queries
- `PostgresUserBrokerRepository` - User-broker links with credentials

### 5. React Dashboard Integration
The `frontend/PyramidDashboard.jsx` component connects to:
- WebSocket at `ws://localhost:8080/ws?token=<jwt>`
- REST API at `http://localhost:8080/api/*`

Update the dashboard config:
```javascript
const API_BASE_URL = "http://localhost:8080";
const WS_BASE_URL = "ws://localhost:8080";
```

## Environment Variables

```bash
# Server
PORT=9090
WS_BATCH_FLUSH_MS=100

# Database
DB_URL=jdbc:postgresql://localhost:5432/annupaper
DB_USER=postgres
DB_PASS=postgres
DB_POOL_SIZE=10

# JWT
JWT_SECRET=your-secret-key-min-32-chars
JWT_EXPIRATION_HOURS=24
```

## Frontend Setup

### Prerequisites
- Node.js 18+ or Node.js 20+
- npm or yarn

### Development

```bash
cd frontend

# Install dependencies
npm install

# Start dev server (with hot reload)
npm run dev

# Open browser: http://localhost:3000
```

The Vite dev server will proxy API requests to the backend at `localhost:9090`.

### Production Build

```bash
cd frontend

# Build for production
npm run build

# Output: frontend/dist/

# Preview production build (optional)
npm run preview

# Deploy dist/ folder to:
# - Nginx
# - Apache
# - CDN (Cloudflare, AWS S3 + CloudFront, etc.)
```

### Environment Configuration

**Development** (`.env.development`):
```env
VITE_API_BASE_URL=http://localhost:9090
VITE_WS_BASE_URL=ws://localhost:9090
```

**Production** (`.env.production`):
```env
VITE_API_BASE_URL=https://your-production-domain.com
VITE_WS_BASE_URL=wss://your-production-domain.com
```

The dashboard automatically reads these environment variables. Fallback is `localhost:9090`.

### Deployment

**Option 1: Static File Server**
```bash
cd frontend
npm run build

# Serve dist/ with nginx/apache
cp -r dist/* /var/www/html/
```

**Option 2: Docker**
```dockerfile
FROM node:20-alpine
WORKDIR /app
COPY frontend/package*.json ./
RUN npm install
COPY frontend/ ./
RUN npm run build
FROM nginx:alpine
COPY --from=0 /app/dist /usr/share/nginx/html
```

**Option 3: Vite Preview (Development Only)**
```bash
npm run preview
# Access at: http://localhost:4173
```

## Complete File List

```
annu-v04/
├── pom.xml
├── README.md
├── sql/
│   └── V001__multi_user_broker_schema.sql
├── frontend/
│   └── PyramidDashboard.jsx
└── src/main/java/in/annupaper/
    ├── auth/
    │   ├── AuthService.java
    │   └── JwtService.java
    ├── bootstrap/
    │   └── App.java
    ├── broker/
    │   ├── BrokerAdapter.java
    │   ├── BrokerAdapterFactory.java
    │   └── adapters/
    │       ├── DhanAdapter.java
    │       ├── FyersAdapter.java
    │       └── ZerodhaAdapter.java
    ├── domain/
    │   ├── enums/
    │   │   ├── BrokerRole.java
    │   │   ├── ConfluenceStrength.java
    │   │   ├── ConfluenceType.java
    │   │   ├── Direction.java
    │   │   ├── EventScope.java
    │   │   ├── EventType.java
    │   │   ├── IntentStatus.java
    │   │   ├── SignalType.java
    │   │   ├── TimeframeType.java
    │   │   └── ValidationErrorCode.java
    │   └── model/
    │       ├── Broker.java
    │       ├── Candle.java
    │       ├── MTFConfig.java
    │       ├── Signal.java
    │       ├── TimeframeAnalysis.java
    │       ├── TradeEvent.java
    │       ├── TradeIntent.java
    │       ├── User.java
    │       ├── UserBroker.java
    │       ├── ValidationResult.java
    │       └── WsSession.java
    ├── repository/
    │   ├── PostgresTradeEventRepository.java
    │   ├── PostgresUserBrokerRepository.java
    │   ├── TradeEventRepository.java
    │   └── UserBrokerRepository.java
    ├── service/
    │   ├── core/
    │   │   └── EventService.java
    │   ├── execution/
    │   │   └── ExecutionOrchestrator.java
    │   ├── mtf/
    │   │   └── MtfAnalysisService.java
    │   ├── signal/
    │   │   └── SignalService.java
    │   └── validation/
    │       └── ValidationService.java
    ├── transport/
    │   ├── http/
    │   │   └── ApiHandlers.java
    │   └── ws/
    │       └── WsHub.java
    └── util/
        └── Env.java
```
