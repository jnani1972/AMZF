# Fyers Integration - Quick Start

## Current Status

✅ **IMPLEMENTATION COMPLETE**
⚠️ **ACCESS TOKEN REQUIRED** for testing

## Credentials Configured

```
App ID: NZT2TDYT0T-100
Secret ID: 4K4483OQU3
Access Token: ❌ NOT SET (required)
```

## Generate Access Token (3 Steps)

### Step 1: Get Auth Code

Open this URL in browser:
```
https://api.fyers.in/api/v2/generate-authcode?client_id=NZT2TDYT0T-100&redirect_uri=https://trade.fyers.in/api-login/redirect-uri/index.html&response_type=code&state=sample
```

Login and copy the `auth_code` from redirect URL.

### Step 2: Generate appIdHash

```bash
echo -n "NZT2TDYT0T-100:4K4483OQU3" | shasum -a 256
```

Copy the hash output.

### Step 3: Get Access Token

```bash
curl -X POST "https://api.fyers.in/api/v2/validate-authcode" \
  -H "Content-Type: application/json" \
  -d '{
    "grant_type": "authorization_code",
    "appIdHash": "<HASH_FROM_STEP_2>",
    "code": "<AUTH_CODE_FROM_STEP_1>"
  }'
```

Copy the `access_token` from response.

### Step 4: Update Database

```bash
psql -U jnani -d annupaper -c "
UPDATE user_brokers
SET credentials = jsonb_set(
  credentials,
  '{accessToken}',
  '\"<ACCESS_TOKEN_FROM_STEP_3>\"'::jsonb
)
WHERE user_broker_id = 'UB_DATA_E7DE4B';
"
```

## Test Connection

```bash
cd /tmp/annu-v04
PORT=9090 DB_USER=jnani DB_PASS="" java -jar target/annu-undertow-ws-v04-0.4.0.jar
```

**Expected Logs:**
```
[FYERS] Connecting with appId=NZT2****YT0T
[FYERS] Connected successfully - Profile: {...}
[FYERS] WebSocket connection established
```

## Features

✅ Real authentication (Fyers Profile API)
✅ Historical candles (Fyers REST API v3)
✅ Real-time ticks (Fyers WebSocket v2)
✅ Symbol conversion (RELIANCE ↔ NSE:RELIANCE-EQ)
✅ Candle aggregation (25-min = 5×5-min, 125-min = 5×25-min)

## Interval Support

**Direct from Fyers:**
- 1, 5, 10, 15, 30, 60, 120, 1440 minutes

**Built via Aggregation:**
- 25-min (from 5-min candles)
- 125-min (from 5-min → 25-min → 125-min)

## Troubleshooting

**Error: "Access token required"**
→ Follow steps above to generate token

**Error: "Authentication failed"**
→ Token expired (24h validity), regenerate

**WebSocket connection failed**
→ System auto-falls back to simulated ticks

## Documentation

- **Setup Guide:** `/tmp/annu-v04/docs/FYERS_SETUP_GUIDE.md`
- **Implementation:** `/tmp/annu-v04/docs/FYERS_IMPLEMENTATION_SUMMARY.md`
- **Code:** `/tmp/annu-v04/src/main/java/in/annupaper/broker/adapters/FyersAdapter.java`

## Important Notes

⚠️ Access tokens expire after 24 hours
⚠️ Market data only available during market hours
⚠️ Rate limits apply (check Fyers docs)
⚠️ Requires active Fyers account

## API Endpoints Used

- Profile: `GET https://api-t1.fyers.in/api/v3/profile`
- History: `POST https://api-t1.fyers.in/data-rest/v3/history`
- WebSocket: `wss://api.fyers.in/socket/v2/dataSock`

## Next Steps

1. ✅ Generate access token
2. ✅ Update database
3. ✅ Start server
4. ✅ Test historical candles
5. ✅ Test real-time ticks
6. ✅ Monitor logs for errors
7. ✅ Set up daily token refresh

---

**Status:** Ready for production testing
**Version:** v04 (January 2026)
