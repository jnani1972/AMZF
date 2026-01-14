# Broker Configuration Guide

This document describes the dual-broker architecture configuration for the AMZF trading system.

## Architecture Overview

The system uses a **separation of concerns** approach with two independent broker connections:

- **DataBroker**: Handles all market data operations (ticks, historical data, instruments)
- **OrderBroker**: Handles all order execution operations (place, modify, cancel, track)

This separation allows you to use different brokers for data and orders, optimizing for each broker's strengths.

## Configuration

### Environment Variables

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `DATA_FEED_BROKER` | Broker for market data | `ZERODHA` | No |
| `ORDER_BROKER` | Broker for order execution | `FYERS` | No |
| `ZERODHA_API_KEY` | Zerodha Kite Connect API key | - | If using Zerodha |
| `FYERS_APP_ID` | FYERS v3 App ID | - | If using FYERS |

### Supported Brokers

#### Data Feed Brokers

| Broker | Status | Features |
|--------|--------|----------|
| **ZERODHA** | ✅ Production Ready | Real-time WebSocket ticks, Historical data, Instrument master |
| **FYERS** | ⚠️ Not Yet Implemented | Would support data streaming via FYERS v2 WebSocket |
| **MOCK** | ✅ Testing Only | Simulated ticks, historical data, instruments |

#### Order Execution Brokers

| Broker | Status | Features |
|--------|--------|----------|
| **FYERS** | ✅ Production Ready | Order placement, modification, cancellation via v3 REST API |
| **ZERODHA** | ⚠️ Not Yet Implemented | Would use Kite Connect REST API for orders |
| **MOCK** | ✅ Testing Only | Simulated order fills, tracking |

### Recommended Configurations

#### Production (Recommended)
```bash
DATA_FEED_BROKER=ZERODHA
ORDER_BROKER=FYERS
ZERODHA_API_KEY=your_zerodha_api_key
FYERS_APP_ID=your_fyers_app_id
```

**Why**: Zerodha provides excellent market data (binary protocol, low latency), while FYERS offers robust order execution.

#### Development/Testing
```bash
DATA_FEED_BROKER=MOCK
ORDER_BROKER=MOCK
```

**Why**: No API credentials needed, fast test execution, no quota consumption.

#### Data-Only Mode (Backtesting)
```bash
DATA_FEED_BROKER=ZERODHA
ORDER_BROKER=MOCK
ZERODHA_API_KEY=your_zerodha_api_key
```

**Why**: Real market data for accurate backtesting, simulated orders for safety.

## API Credentials Setup

### Zerodha (Kite Connect)

1. Sign up for Kite Connect: https://kite.trade/
2. Create an app in the Kite Connect dashboard
3. Copy your API key
4. Set environment variable:
   ```bash
   export ZERODHA_API_KEY=your_api_key_here
   ```

**Note**: Zerodha requires OAuth authentication. Users must connect via OAuth flow to generate access tokens. Tokens are stored in `user_broker_sessions` table and automatically loaded by the factory.

### FYERS (v3 API)

1. Sign up for FYERS API: https://myapi.fyers.in/
2. Create an app in the FYERS dashboard
3. Copy your App ID (format: `XXXYYY-100`)
4. Set environment variable:
   ```bash
   export FYERS_APP_ID=your_app_id_here
   ```

**Note**: FYERS also requires OAuth authentication. Users must connect via OAuth flow to generate access tokens. Tokens are stored in `user_broker_sessions` table and automatically loaded by the factory.

## Broker Combination Matrix

| Data Broker | Order Broker | Status | Notes |
|-------------|--------------|--------|-------|
| ZERODHA | FYERS | ✅ Recommended | Best performance, production-ready |
| ZERODHA | MOCK | ✅ Supported | Good for backtesting with real data |
| MOCK | MOCK | ✅ Supported | Fast testing, no credentials needed |
| ZERODHA | ZERODHA | ⚠️ Partial | Data works, orders not yet implemented |
| FYERS | FYERS | ⚠️ Partial | Orders work, data not yet implemented |
| MOCK | FYERS | ✅ Supported | Testing with real order execution |

## Configuration Validation

The `BrokerFactory` validates configuration at startup:

- ✅ Checks if required credentials are configured
- ⚠️ Warns about MOCK usage in production
- 🛡️ Falls back to MOCK brokers if credentials are missing
- 📊 Logs current configuration for debugging

Example startup logs:
```
[BrokerFactory] Initialized with configuration:
[BrokerFactory]   Data Feed Broker: ZERODHA
[BrokerFactory]   Order Broker: FYERS
[BrokerFactory]   Zerodha API Key: configured
[BrokerFactory]   FYERS App ID: configured
[BrokerFactory] ✅ Using recommended configuration: ZERODHA (data) + FYERS (orders)
```

## Usage in Code

### Basic Usage

```java
// Initialize factory with session repository
BrokerFactory factory = new BrokerFactory(sessionRepository);

// Create brokers for a user
DataBroker dataBroker = factory.createDataBroker(userBrokerId);
OrderBroker orderBroker = factory.createOrderBroker(userBrokerId);

// Use brokers
dataBroker.authenticate().join();
dataBroker.connect().join();
dataBroker.subscribe(List.of("RELIANCE", "TCS")).join();

orderBroker.authenticate().join();
OrderResponse response = orderBroker.placeOrder(orderRequest).join();
```

### Dependency Injection

Services should depend on interfaces, not concrete implementations:

```java
public class TradingService {
    private final DataBroker dataBroker;
    private final OrderBroker orderBroker;

    public TradingService(DataBroker dataBroker, OrderBroker orderBroker) {
        this.dataBroker = dataBroker;
        this.orderBroker = orderBroker;
    }

    public void start() {
        dataBroker.onTick(this::processTick);
        dataBroker.subscribe(watchlist).join();
    }

    private void processTick(Tick tick) {
        // Process tick and generate signals
        if (shouldTrade(tick)) {
            orderBroker.placeOrder(buildOrder(tick)).join();
        }
    }
}
```

## Token Refresh

Both Zerodha and FYERS require daily OAuth authentication. The system handles token refresh automatically:

1. User connects via OAuth flow (once per day)
2. Access token stored in `user_broker_sessions` table
3. `BrokerFactory` loads tokens from repository
4. `TokenRefreshWatchdog` monitors token expiry
5. Brokers automatically reconnect when tokens are refreshed

### Token Refresh Flow

```
User OAuth → Session Created → Token Stored
                                    ↓
                          BrokerFactory Loads Token
                                    ↓
                            Broker Authenticates
                                    ↓
                    TokenRefreshWatchdog Monitors
                                    ↓
                        Token Expires → User Re-authenticates
                                    ↓
                        New Token → broker.reloadToken()
                                    ↓
                        Broker Reconnects Automatically
```

## Error Handling

### Graceful Degradation

If a broker fails to initialize (missing credentials, network error), the factory automatically falls back to a MOCK broker:

```
[BrokerFactory] Failed to create DataBroker: API key not configured. Falling back to MOCK.
```

This prevents application crashes but logs warnings for monitoring.

### Read-Only Mode

If a data broker loses connection for >5 minutes, it enters **READ-ONLY mode**:
- Order placement is blocked
- System continues processing existing positions
- Health endpoint reports degraded status
- Operators are alerted to investigate

### Reconnection Logic

Both brokers implement exponential backoff with circuit breakers:
- Retry delays: 1s, 2s, 4s, 8s, 16s, 32s, 60s (max)
- After 10 consecutive failures: 5-minute pause
- Automatic reconnection when network recovers
- Token refresh triggers immediate reconnection

## Monitoring

### Health Check Endpoint

The `/api/health` endpoint reports broker status:

```json
{
  "status": "UP",
  "brokers": {
    "data": {
      "type": "ZERODHA",
      "connected": true,
      "authenticated": true,
      "state": "STREAMING",
      "lastTick": "2024-01-15T10:30:45Z",
      "retryCount": 0
    },
    "order": {
      "type": "FYERS",
      "connected": true,
      "authenticated": true,
      "lastOrderUpdate": "2024-01-15T10:29:12Z",
      "readOnlyMode": false
    }
  }
}
```

### Logging

Both brokers provide detailed logging with prefixes:
- `[ZERODHA_DATA]` - Zerodha data broker logs
- `[FYERS_ORDER]` - FYERS order broker logs
- `[BrokerFactory]` - Factory configuration logs

Set log level to `DEBUG` for detailed connection traces.

## Migration from Legacy Architecture

### Before (Single Broker Interface)

```java
BrokerAdapter broker = new ZerodhaAdapter();
broker.connect(credentials);
broker.subscribeTicks(symbols, listener);
broker.placeOrder(orderRequest);
```

### After (Dual Broker Architecture)

```java
BrokerFactory factory = new BrokerFactory(sessionRepo);
DataBroker dataBroker = factory.createDataBroker(userBrokerId);
OrderBroker orderBroker = factory.createOrderBroker(userBrokerId);

dataBroker.authenticate().join();
dataBroker.connect().join();
dataBroker.subscribe(symbols).join();

orderBroker.authenticate().join();
orderBroker.placeOrder(orderRequest).join();
```

### Migration Checklist

- [ ] Set environment variables (`DATA_FEED_BROKER`, `ORDER_BROKER`)
- [ ] Configure API credentials (`ZERODHA_API_KEY`, `FYERS_APP_ID`)
- [ ] Update service constructors to accept `DataBroker` and `OrderBroker`
- [ ] Replace `BrokerAdapter` references with specific interfaces
- [ ] Test with MOCK brokers first
- [ ] Validate with real brokers in staging
- [ ] Monitor health endpoint during production rollout

## Troubleshooting

### Issue: "ZERODHA_API_KEY not configured"

**Solution**: Set the environment variable:
```bash
export ZERODHA_API_KEY=your_api_key
```

### Issue: "No active session found"

**Solution**: User needs to authenticate via OAuth flow. Direct them to `/oauth/connect` endpoint.

### Issue: "WebSocket connection failed - HTTP 401"

**Solution**: Token expired. User needs to re-authenticate via OAuth.

### Issue: Broker stuck in RECONNECTING state

**Solution**:
1. Check network connectivity
2. Verify API credentials are valid
3. Check broker API status page
4. Clear broker cache: `factory.clearCache(userBrokerId)`

### Issue: Orders rejected with "Not authenticated"

**Solution**: Ensure OrderBroker authentication succeeds before placing orders:
```java
orderBroker.authenticate().join();  // Wait for authentication
orderBroker.placeOrder(request).join();
```

## Future Enhancements

- [ ] FyersDataBroker implementation (data streaming via FYERS)
- [ ] ZerodhaOrderBroker implementation (orders via Kite Connect)
- [ ] Multi-user support (pool connections per user)
- [ ] Broker failover (automatic switch to backup broker)
- [ ] Data feed aggregation (combine multiple data sources)
- [ ] Order routing (split orders across brokers)

## Support

For issues or questions:
- GitHub Issues: https://github.com/your-org/amzf/issues
- Documentation: See `docs/` directory
- API Reference: See JavaDoc in code
