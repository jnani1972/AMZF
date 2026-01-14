# Fyers DATA Broker Setup Guide

## Overview

AnnuPaper v04 now supports Fyers as the DATA broker for real-time market data and historical candles. This guide explains how to configure and use Fyers integration.

## Current Status

✅ **Implemented Features:**
- Real authentication via Fyers Profile API
- Historical candles via Fyers REST API v3
- Real-time tick streaming via Fyers WebSocket v2
- Symbol format conversion (RELIANCE ↔ NSE:RELIANCE-EQ)
- Automatic interval mapping (1min, 25min→30min, 125min→120min)

✅ **Database Configuration:**
- Fyers credentials stored in `user_brokers` table
- User: `UADMIN37A0`
- Broker: `B_FYERS`
- Role: `DATA`
- Credentials: `{"appId": "NZT2TDYT0T-100", "secretId": "4K4483OQU3"}`

## Required Credentials

You need three pieces of information from Fyers:

1. **App ID**: `NZT2TDYT0T-100` (already configured)
2. **Secret ID**: `4K4483OQU3` (already configured)
3. **Access Token**: *Required* - Needs to be generated

## How to Generate Access Token

The access token is required for authentication with Fyers API. Follow these steps:

### Step 1: Generate Authorization Code

1. Open this URL in your browser (replace `{APP_ID}` with your App ID):

```
https://api.fyers.in/api/v2/generate-authcode?client_id=NZT2TDYT0T-100&redirect_uri=https://trade.fyers.in/api-login/redirect-uri/index.html&response_type=code&state=sample
```

2. Log in to your Fyers account
3. Authorize the application
4. You'll be redirected to a URL like:
```
https://trade.fyers.in/api-login/redirect-uri/index.html?auth_code=XXXXXXXXXXXXX&state=sample
```
5. Copy the `auth_code` value from the URL

### Step 2: Exchange Auth Code for Access Token

Use the following curl command to exchange the auth code for an access token:

```bash
curl -X POST "https://api.fyers.in/api/v2/validate-authcode" \
  -H "Content-Type: application/json" \
  -d '{
    "grant_type": "authorization_code",
    "appIdHash": "<SHA256_HASH_OF_APPID_SECRETID>",
    "code": "<AUTH_CODE_FROM_STEP_1>"
  }'
```

**To generate appIdHash:**
```bash
echo -n "NZT2TDYT0T-100:4K4483OQU3" | shasum -a 256
```

**Example Response:**
```json
{
  "s": "ok",
  "code": 200,
  "access_token": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9..."
}
```

Copy the `access_token` value.

### Step 3: Store Access Token in Database

Update the `user_brokers` table with the access token:

```sql
UPDATE user_brokers
SET credentials = jsonb_set(
  credentials,
  '{accessToken}',
  '"eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9..."'::jsonb
)
WHERE user_broker_id = 'UB_DATA_E7DE4B';
```

**Verify the update:**
```sql
SELECT user_broker_id, credentials
FROM user_brokers
WHERE user_broker_id = 'UB_DATA_E7DE4B';
```

Expected result:
```json
{
  "appId": "NZT2TDYT0T-100",
  "secretId": "4K4483OQU3",
  "accessToken": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9..."
}
```

## Testing the Connection

### Step 1: Start AnnuPaper Server

```bash
cd /tmp/annu-v04
PORT=9090 DB_USER=jnani DB_PASS="" java -jar target/annu-undertow-ws-v04-0.4.0.jar
```

### Step 2: Check Logs

Look for Fyers connection logs:
```
[FYERS] Connecting with appId=NZT2****YT0T
[FYERS] Connected successfully - Profile: {...}
[FYERS] WebSocket connection established
```

If access token is missing, you'll see:
```
[FYERS] No access token provided. Generate it manually:
[FYERS] 1. Generate auth code: https://api.fyers.in/api/v2/generate-authcode?...
```

### Step 3: Test Historical Data

The system will automatically fetch historical candles for watchlist symbols during startup.

Expected logs:
```
[FYERS] Fetching historical candles: RELIANCE interval=1 from=... to=...
[FYERS] Fetched 375 candles for RELIANCE
```

### Step 4: Test Real-Time Ticks

WebSocket will automatically subscribe to watchlist symbols and stream real-time tick data.

Expected logs:
```
[FYERS] WebSocket opened
[FYERS] WebSocket authentication sent
[FYERS] Subscribing to ticks: [RELIANCE, TCS, INFY]
[FYERS] WebSocket subscription sent for 3 symbols
```

## API Details

### Fyers REST API v3

**Base URL:** `https://api-t1.fyers.in`

**Historical Candles Endpoint:**
- Method: `POST /data-rest/v3/history`
- Headers: `Authorization: {appId}:{accessToken}`
- Request Body:
```json
{
  "symbol": "NSE:RELIANCE-EQ",
  "resolution": "1",
  "date_format": "1",
  "range_from": "1640000000",
  "range_to": "1640100000",
  "cont_flag": "1"
}
```
- Response:
```json
{
  "s": "ok",
  "candles": [
    [1640000060, 2450.50, 2452.00, 2448.00, 2451.00, 125000],
    ...
  ]
}
```

### Fyers WebSocket v2

**WebSocket URL:** `wss://api.fyers.in/socket/v2/dataSock`

**Authentication Message:**
```json
{
  "T": "CONNECT",
  "CHANNEL": "{appId}:{accessToken}"
}
```

**Subscribe Message:**
```json
{
  "T": "SUB_L2",
  "L2LIST": ["NSE:RELIANCE-EQ", "NSE:TCS-EQ"],
  "SUB_T": 1
}
```

**Tick Data Message:**
```json
{
  "T": "df",
  "d": {
    "s": "NSE:RELIANCE-EQ",
    "lp": 2450.50,
    "op": 2448.00,
    "hp": 2455.00,
    "lp_t": 2447.00,
    "v": 125000,
    "lp_ltt": 1640000060000
  }
}
```

## Symbol Format Conversion

AnnuPaper uses simple symbols (e.g., `RELIANCE`), while Fyers uses exchange-prefixed symbols (e.g., `NSE:RELIANCE-EQ`).

**Conversion Rules:**
- `RELIANCE` → `NSE:RELIANCE-EQ`
- `TCS` → `NSE:TCS-EQ`
- `INFY` → `NSE:INFY-EQ`
- `NSE:RELIANCE-EQ` → `RELIANCE`

## Interval Mapping and Candle Aggregation

AnnuPaper uses custom intervals (25-min and 125-min) that Fyers doesn't support directly. The adapter handles this by fetching smaller candles and aggregating them:

### Direct Mapping (Fyers Native Intervals)

| AnnuPaper Interval | Fyers Resolution | Description |
|-------------------|------------------|-------------|
| 1 min | 1 | LTF (Low Time Frame) |
| 5 min | 5 | Base for aggregation |
| 10 min | 10 | 10 minutes |
| 15 min | 15 | 15 minutes |
| 30 min | 30 | 30 minutes |
| 60 min | 60 | 1 hour |
| 120 min | 120 | 2 hours |
| 1440 min | 1D | Daily |

### Aggregated Intervals (Built from Smaller Candles)

| AnnuPaper Interval | Aggregation Strategy | Description |
|-------------------|---------------------|-------------|
| 25 min | Aggregate 5 × 5-min candles | ITF (Intermediate Time Frame) |
| 125 min | Aggregate 5 × 25-min candles (built from 5-min) | HTF (High Time Frame) |

**How Aggregation Works:**

1. **25-min candles:**
   - Fetch 5-min candles from Fyers
   - Group every 5 consecutive 5-min candles
   - Aggregate: Open = first candle's open, Close = last candle's close, High = max high, Low = min low, Volume = sum

2. **125-min candles:**
   - Fetch 5-min candles from Fyers
   - First aggregate into 25-min candles (5 × 5-min)
   - Then aggregate into 125-min candles (5 × 25-min)

**Example:**
```
Request: 25-min candles for RELIANCE from 09:15 to 15:30

Step 1: Fetch 5-min candles from Fyers (9:15, 9:20, 9:25, ..., 15:30)
Step 2: Aggregate every 5 candles:
  - 9:15-9:35 → 1 × 25-min candle
  - 9:40-10:00 → 1 × 25-min candle
  - ...

Result: ~15 × 25-min candles covering the full trading session
```

## Troubleshooting

### Error: "Access token required"

**Cause:** Access token not configured in database.

**Solution:** Follow "How to Generate Access Token" section above.

### Error: "Authentication failed"

**Cause:** Invalid or expired access token.

**Solution:**
1. Access tokens expire after 24 hours
2. Generate a new access token using the auth code flow
3. Update the database with the new token

### Error: "HTTP error 401"

**Cause:** Invalid appId or accessToken combination.

**Solution:**
1. Verify appId and secretId are correct
2. Ensure access token was generated using the same appId
3. Check that access token hasn't expired

### WebSocket Connection Failed

**Cause:** Network issues or invalid credentials.

**Solution:**
1. Check internet connection
2. Verify firewall allows WebSocket connections
3. Check access token is valid
4. System will fallback to simulated ticks if WebSocket fails

## Access Token Expiry

⚠️ **Important:** Fyers access tokens expire after 24 hours.

**Recommended Approach:**
1. Store refresh token (if provided by Fyers)
2. Implement automatic token refresh logic
3. For now, manually regenerate token daily

**Future Enhancement:**
- Automatic token refresh using refresh token
- Token expiry notification via events
- Admin UI for token management

## Production Checklist

Before using in production:

- [ ] Generate valid access token
- [ ] Update credentials in database
- [ ] Test historical data fetching
- [ ] Test WebSocket connection
- [ ] Set up daily token refresh process
- [ ] Monitor connection logs
- [ ] Configure watchlist symbols
- [ ] Test with live market data

## Code Reference

**FyersAdapter Implementation:**
- Location: `/tmp/annu-v04/src/main/java/in/annupaper/broker/adapters/FyersAdapter.java`
- Authentication: Lines 60-114
- Historical Candles (Main): Lines 430-473
- Historical Candles (Fetch): Lines 475-541
- Candle Aggregation Logic: Lines 543-602
- WebSocket: Lines 272-428
- Symbol Conversion: Lines 632-669

**Key Methods:**
- `getHistoricalCandles()` - Main entry point, handles 25-min and 125-min via aggregation
- `fetchFyersCandles()` - Fetches raw candles from Fyers REST API
- `aggregateCandles()` - Aggregates smaller candles into larger timeframes
- `convertToFyersSymbol()` - Converts RELIANCE → NSE:RELIANCE-EQ
- `convertFromFyersSymbol()` - Converts NSE:RELIANCE-EQ → RELIANCE
- `convertToFyersResolution()` - Maps interval minutes to Fyers resolution string

## Support

For Fyers API documentation:
- API Docs: https://myapi.fyers.in/docsv3
- Support: https://fyers.in/support

For AnnuPaper issues:
- Check application logs
- Review database credentials
- Verify network connectivity
