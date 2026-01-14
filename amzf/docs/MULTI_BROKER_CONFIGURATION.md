# Multi-Broker Configuration Guide

## Overview

The trading system now supports **multiple order brokers** for order execution while maintaining the existing data feed architecture. This allows you to choose your preferred broker for order placement without affecting your market data source.

## Supported Brokers

### Order Execution Brokers
- **FYERS** (existing, fully tested)
- **UPSTOX** (newly added)
- **DHAN** (newly added)
- **ZERODHA** (newly added)
- **MOCK** (testing only)

### Data Feed Brokers
- **ZERODHA** (recommended for data)
- **FYERS** (limited support)
- **MOCK** (testing only)

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Trading System                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────────┐              ┌──────────────┐           │
│  │ DataBroker   │              │ OrderBroker  │           │
│  │  (Zerodha)   │              │  (Your Choice)│           │
│  └──────┬───────┘              └──────┬───────┘           │
│         │                             │                    │
│         │ Market Data                 │ Order Execution    │
│         ▼                             ▼                    │
│  ┌──────────────────────────────────────────────────┐     │
│  │          Signal Generation & Trading Logic       │     │
│  └──────────────────────────────────────────────────┘     │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## Configuration

### Environment Variables

Set these environment variables to configure your brokers:

```bash
# Data feed broker (default: ZERODHA)
export DATA_FEED_BROKER=ZERODHA

# Order execution broker (default: FYERS)
export ORDER_BROKER=UPSTOX  # or DHAN, ZERODHA, FYERS

# Broker API credentials
export ZERODHA_API_KEY=your_zerodha_api_key
export FYERS_APP_ID=your_fyers_app_id
export UPSTOX_API_KEY=your_upstox_api_key
export DHAN_CLIENT_ID=your_dhan_client_id
```

### Recommended Configurations

#### Configuration 1: Zerodha Data + FYERS Orders (Default)
```bash
export DATA_FEED_BROKER=ZERODHA
export ORDER_BROKER=FYERS
export ZERODHA_API_KEY=your_key
export FYERS_APP_ID=your_app_id
```

#### Configuration 2: Zerodha Data + Upstox Orders
```bash
export DATA_FEED_BROKER=ZERODHA
export ORDER_BROKER=UPSTOX
export ZERODHA_API_KEY=your_key
export UPSTOX_API_KEY=your_key
```

#### Configuration 3: Zerodha Data + Dhan Orders
```bash
export DATA_FEED_BROKER=ZERODHA
export ORDER_BROKER=DHAN
export ZERODHA_API_KEY=your_key
export DHAN_CLIENT_ID=your_client_id
```

#### Configuration 4: All Zerodha
```bash
export DATA_FEED_BROKER=ZERODHA
export ORDER_BROKER=ZERODHA
export ZERODHA_API_KEY=your_key
```

## Product Types

All brokers support these product types with automatic mapping:

| Product Type | Description | Zerodha | FYERS | Upstox | Dhan |
|--------------|-------------|---------|-------|--------|------|
| CNC | Cash & Carry (Delivery) | CNC | CNC | D | CNC |
| MIS | Margin Intraday Square-off | MIS | INTRADAY | I | INTRADAY |
| NRML | Normal (F&O) | NRML | MARGIN | M | MARGIN |
| MTF | Margin Trade Funding | ❌ | ❌ | MTF | MTF |
| BO | Bracket Order | BO | ❌ | ❌ | ❌ |
| CO | Cover Order | CO | ❌ | ❌ | ❌ |

## Broker-Specific Features

### FYERS
- **Rate Limit:** 10 orders/sec, 5000/day
- **OAuth:** Standard OAuth 2.0 flow
- **Order Updates:** Polling-based
- **Special Features:** None
- **API Docs:** https://myapi.fyers.in/docsv3

### Upstox
- **Rate Limit:** 10 orders/sec, 1000/day
- **OAuth:** Standard OAuth 2.0 flow
- **Order Updates:** WebSocket portfolio stream
- **Special Features:** Supports MTF
- **API Docs:** https://upstox.com/developer/api-documentation

### Dhan
- **Rate Limit:** 10 orders/sec, 5000/day
- **OAuth:** Standard OAuth 2.0 flow
- **Order Updates:** WebSocket order updates
- **Special Features:**
  - Supports MTF
  - **Requires static IP whitelisting**
- **API Docs:** https://dhanhq.co/docs/

### Zerodha
- **Rate Limit:** 10 orders/sec, 3000/day
- **OAuth:** Kite Connect OAuth flow
- **Order Updates:** Polling-based
- **Special Features:**
  - Supports BO, CO
  - Best data feed reliability
- **API Docs:** https://kite.trade/docs/connect/v3/

## Instrument Mapping

The system uses `InstrumentMapper` to convert normalized symbols (e.g., `NSE:SBIN-EQ`) to broker-specific formats:

- **Zerodha:** `exchange` + `tradingsymbol` (e.g., NSE + SBIN-EQ)
- **FYERS:** `symbol` (e.g., NSE:SBIN-EQ)
- **Upstox:** `instrument_token` (e.g., 151064324)
- **Dhan:** `securityId` (e.g., 11915)

The InstrumentMapper automatically handles these conversions.

## OAuth Setup

### Step 1: Register Your App

Visit each broker's developer portal and register your application:

- **Zerodha:** https://developers.kite.trade/
- **FYERS:** https://myapi.fyers.in/
- **Upstox:** https://upstox.com/developer/apps
- **Dhan:** https://dhanhq.co/docs/

### Step 2: Store Credentials

After registering, store your credentials:

```bash
# In your .env file or environment
ZERODHA_API_KEY=xxxxxxxxxxxxx
FYERS_APP_ID=XXXXXXXXX-XXX
UPSTOX_API_KEY=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
DHAN_CLIENT_ID=XXXXXXXXXXX
```

### Step 3: Complete OAuth Flow

The system will guide you through OAuth when you first connect:

1. Start the application
2. Navigate to `/broker/connect`
3. Select your broker
4. Complete OAuth authorization
5. System will store access tokens in `user_broker_sessions` table

## Order Request Format

All brokers accept the same `OrderRequest` format:

```java
OrderRequest request = new OrderRequest(
    "NSE:SBIN-EQ",           // Normalized symbol
    Direction.BUY,           // BUY or SELL
    10,                      // Quantity
    OrderType.LIMIT,         // MARKET, LIMIT, STOP_LOSS
    BigDecimal.valueOf(500), // Limit price
    null,                    // Stop price (optional)
    TimeInForce.DAY,         // DAY, IOC, GTC
    ProductType.CNC,         // CNC, MIS, NRML, MTF, BO, CO
    "intent-123",            // Tag for tracking
    Map.of()                 // Broker-specific extras
);
```

### Broker-Specific Parameters

You can pass broker-specific parameters via `extendedParams`:

```java
// Upstox: Disclosed quantity
Map<String, Object> upstoxParams = Map.of(
    "disclosed_quantity", 5
);

// Dhan: AMO (After Market Order)
Map<String, Object> dhanParams = Map.of(
    "afterMarketOrder", true
);

OrderRequest request = new OrderRequest(
    symbol, direction, quantity, orderType,
    limitPrice, stopPrice, timeInForce, productType,
    tag, upstoxParams  // or dhanParams
);
```

## Rate Limiting

Each broker has rate limits enforced at the BrokerFactory level:

| Broker | Orders/Second | Orders/Day | Burst Limit |
|--------|---------------|------------|-------------|
| FYERS | 10 | 5000 | 20 |
| Upstox | 10 | 1000 | 20 |
| Dhan | 10 | 5000 | 20 |
| Zerodha | 10 | 3000 | 20 |

The system automatically throttles requests to stay within limits.

## Health Monitoring

The `BrokerHealthCheck` service monitors:

- Connection status (authenticated, connected)
- Last successful operation timestamp
- Error rates and counts
- Rate limit utilization

Access health status:
```java
BrokerHealthCheck healthCheck = new BrokerHealthCheck(dataBroker, orderBroker);
boolean isHealthy = healthCheck.isHealthy();
Map<String, Object> status = healthCheck.getHealthStatus();
```

## Testing

### Mock Broker for Testing

Use the MOCK broker for integration tests:

```bash
export DATA_FEED_BROKER=MOCK
export ORDER_BROKER=MOCK
```

The mock broker:
- Generates realistic tick data
- Simulates order fills with configurable delays
- Supports all order types
- No API credentials required

### Integration Tests

Run broker-specific integration tests:

```bash
# Test all brokers (requires credentials)
mvn test -Dtest=BrokerIntegrationTest

# Test specific broker
mvn test -Dtest=UpstoxOrderBrokerTest
```

## Troubleshooting

### Issue: "No active session found"

**Solution:** Complete OAuth flow for your broker at `/broker/connect`

### Issue: "Token verification failed"

**Solution:** Your access token expired. Re-authenticate via OAuth.

### Issue: "Instrument not found"

**Solution:** Ensure instrument master data is loaded:
```java
instrumentMapper.loadInstruments("UPSTOX", instruments);
```

### Issue: Dhan API returns 403

**Solution:** Dhan requires static IP whitelisting. Add your server IP to your Dhan account settings.

### Issue: Rate limit exceeded

**Solution:** Reduce order frequency or implement exponential backoff. Current rate limits are logged in BrokerFactory.

## Migration Guide

### From Single Broker to Multi-Broker

If you're migrating from a single broker setup:

1. **Set environment variables:**
   ```bash
   export ORDER_BROKER=YOUR_NEW_BROKER
   export YOUR_BROKER_API_KEY=xxxxx
   ```

2. **No code changes required** - The system automatically uses the configured broker

3. **Test with MOCK first:**
   ```bash
   export ORDER_BROKER=MOCK
   ```

4. **Switch to production:**
   ```bash
   export ORDER_BROKER=UPSTOX  # or DHAN, ZERODHA
   ```

### Switching Brokers

To switch order brokers:

1. Complete OAuth for the new broker
2. Update environment variable: `export ORDER_BROKER=NEW_BROKER`
3. Restart the application
4. BrokerFactory will automatically create the new broker instance

## Best Practices

1. **Use ZERODHA for data feed** - Most reliable and lowest latency
2. **Choose order broker based on:**
   - Brokerage fees (Zerodha, Upstox, Dhan are discount brokers)
   - Required features (MTF, BO, CO)
   - API rate limits
   - Your existing account

3. **Always test with MOCK broker first** before using real broker

4. **Monitor health checks** - Set up alerts for broker disconnections

5. **Handle rejections gracefully** - All brokers can reject orders due to:
   - Insufficient funds
   - Market closed
   - Invalid parameters
   - Rate limits exceeded

6. **Use order tags** - Track orders back to trade intents using the `tag` field

7. **Enable logging** - Set log level to DEBUG for broker operations during testing

## Security

⚠️ **Never commit API keys to version control**

Store credentials securely:
- Use environment variables
- Use secrets management (AWS Secrets Manager, HashiCorp Vault)
- Restrict access to production credentials
- Rotate tokens regularly

## Support

For broker-specific issues:
- **FYERS:** support@fyers.in
- **Upstox:** support@upstox.com
- **Dhan:** support@dhan.co
- **Zerodha:** support@zerodha.com

For system issues:
- Check logs in `logs/` directory
- Review `BrokerHealthCheck` status
- Verify OAuth tokens in `user_broker_sessions` table

## Future Enhancements

Planned features:
- AngelOne broker support
- 5Paisa broker support
- Kotak Securities support
- Batch order placement optimization
- WebSocket order update support for all brokers
- Automatic instrument master synchronization
- Multi-broker order routing (split orders across brokers)
