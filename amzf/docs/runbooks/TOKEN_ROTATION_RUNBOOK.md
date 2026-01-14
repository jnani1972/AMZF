# Token Rotation Runbook

**Last Updated**: January 15, 2026
**Owner**: Security Team
**Frequency**: Every 90 Days
**Severity**: High

## Overview

This runbook describes the procedure for rotating API tokens and secrets for all broker integrations. Regular token rotation is critical for security and compliance.

## Rotation Schedule

| Secret Type | Rotation Frequency | Last Rotated | Next Rotation |
|-------------|-------------------|--------------|---------------|
| Upstox API Key | 90 days | TBD | TBD |
| Zerodha API Key | 90 days | TBD | TBD |
| Fyers API Key | 90 days | TBD | TBD |
| Dhan Client ID/Token | 90 days | TBD | TBD |
| Database Password | 180 days | TBD | TBD |
| SSL Certificates | 365 days | TBD | TBD |

---

## Pre-Rotation Checklist

- [ ] Schedule maintenance window (2-hour window, off-peak hours)
- [ ] Notify team of planned rotation
- [ ] Backup current secrets file
- [ ] Generate new tokens from broker portals
- [ ] Test new tokens in staging environment
- [ ] Prepare rollback plan

---

## Rotation Procedure

### Step 1: Generate New Tokens

#### Upstox

**Portal**: https://developer.upstox.com/

1. Login to Upstox Developer Portal
2. Navigate to "My Apps"
3. Click on your app
4. Click "Regenerate Secret"
5. Copy new API Key and Secret
6. Save to secure location (password manager)

**New Credentials**:
```
UPSTOX_API_KEY=new_key_here
UPSTOX_API_SECRET=new_secret_here
```

---

#### Zerodha

**Portal**: https://developers.kite.trade/

1. Login to Kite Developer Portal
2. Navigate to "My Apps"
3. Select your app
4. Click "Regenerate API Secret"
5. Copy new API Key and Secret

**New Credentials**:
```
ZERODHA_API_KEY=new_key_here
ZERODHA_API_SECRET=new_secret_here
```

---

#### Fyers

**Portal**: https://api-dashboard.fyers.in/

1. Login to Fyers API Dashboard
2. Navigate to "My Apps"
3. Select your app
4. Click "Generate New Credentials"
5. Copy new App ID and Secret

**New Credentials**:
```
FYERS_API_KEY=new_app_id_here
FYERS_API_SECRET=new_secret_here
```

---

#### Dhan

**Portal**: https://dhanhq.com/api-dashboard

1. Login to Dhan API Dashboard
2. Navigate to "Access Tokens"
3. Click "Generate New Token"
4. Copy new Client ID and Access Token

**New Credentials**:
```
DHAN_CLIENT_ID=new_client_id_here
DHAN_ACCESS_TOKEN=new_token_here
```

---

### Step 2: Test New Tokens (Staging)

**Copy secrets to staging**:
```bash
# SSH to staging server
ssh user@staging.amzf.com

# Backup current secrets
sudo cp /secure/secrets.properties /secure/secrets.properties.backup.$(date +%Y%m%d)

# Update with new tokens
sudo nano /secure/secrets.properties

# Update relevant fields:
upstox.api_key=NEW_KEY
upstox.api_secret=NEW_SECRET
zerodha.api_key=NEW_KEY
zerodha.api_secret=NEW_SECRET
fyers.api_key=NEW_KEY
fyers.api_secret=NEW_SECRET
dhan.client_id=NEW_ID
dhan.access_token=NEW_TOKEN

# Secure permissions
sudo chmod 400 /secure/secrets.properties

# Restart application
sudo systemctl restart amzf

# Wait for startup
sleep 30
```

**Verify authentication**:
```bash
# Check logs for successful authentication
sudo journalctl -u amzf --since "1 minute ago" | grep -i auth

# Expected: "Authentication successful" for all brokers

# Check metrics
curl http://localhost:9090/metrics | grep broker_authentications_total

# Check health
curl http://localhost:9090/api/health
```

**Test order placement** (in staging):
```bash
# Place test order (paper trading/mock mode)
curl -X POST http://localhost:9090/api/orders/test \
  -H "Content-Type: application/json" \
  -d '{
    "broker": "UPSTOX",
    "symbol": "NSE:SBIN-EQ",
    "quantity": 1,
    "orderType": "MARKET",
    "transactionType": "BUY"
  }'

# Expected: 200 OK with order ID

# Repeat for all brokers
```

**If staging tests pass**, proceed to production. **If tests fail**, rollback and investigate:
```bash
# Rollback on staging
sudo cp /secure/secrets.properties.backup.$(date +%Y%m%d) /secure/secrets.properties
sudo systemctl restart amzf

# Investigate failure
sudo journalctl -u amzf | grep ERROR
```

---

### Step 3: Update Production Secrets

**Backup current production secrets**:
```bash
# SSH to production
ssh user@prod.amzf.com

# Backup
sudo cp /secure/secrets.properties /backup/secrets.properties.$(date +%Y%m%d_%H%M%S)

# Verify backup
ls -lh /backup/secrets.properties.*
```

**Update secrets file**:
```bash
# Edit secrets
sudo nano /secure/secrets.properties

# Update all rotated tokens
upstox.api_key=NEW_KEY
upstox.api_secret=NEW_SECRET
zerodha.api_key=NEW_KEY
zerodha.api_secret=NEW_SECRET
fyers.api_key=NEW_KEY
fyers.api_secret=NEW_SECRET
dhan.client_id=NEW_ID
dhan.access_token=NEW_TOKEN

# Secure permissions
sudo chmod 400 /secure/secrets.properties
sudo chown amzf:amzf /secure/secrets.properties
```

**Alternatively, use environment variables** (recommended for production):
```bash
# Update systemd service
sudo systemctl edit amzf

# Add environment variables:
Environment="UPSTOX_API_KEY=new_key"
Environment="UPSTOX_API_SECRET=new_secret"
Environment="ZERODHA_API_KEY=new_key"
Environment="ZERODHA_API_SECRET=new_secret"
Environment="FYERS_API_KEY=new_key"
Environment="FYERS_API_SECRET=new_secret"
Environment="DHAN_CLIENT_ID=new_id"
Environment="DHAN_ACCESS_TOKEN=new_token"

# Reload systemd
sudo systemctl daemon-reload
```

---

### Step 4: Restart Production Application

**Graceful restart**:
```bash
# Notify team of restart
curl -X POST https://hooks.slack.com/services/YOUR/WEBHOOK/URL \
  -H 'Content-Type: application/json' \
  -d '{"text":"⚠️ Starting token rotation - Application restarting"}'

# Restart service
sudo systemctl restart amzf

# Monitor startup
sudo journalctl -u amzf -f
```

**Expected startup logs**:
```
[main] INFO  App - ═══════════════════════════════════════
[main] INFO  App - === AnnuPaper v04 Starting ===
[main] INFO  App - ═══════════════════════════════════════
[main] INFO  SecretsManager - Loaded 8 secrets from /secure/secrets.properties
[main] INFO  SecretsManager - Overridden 'upstox.api_key' from environment variable
[main] INFO  UpstoxOrderBroker - Authentication successful
[main] INFO  ZerodhaOrderBroker - Authentication successful
[main] INFO  FyersOrderBroker - Authentication successful
[main] INFO  DhanOrderBroker - Authentication successful
[main] INFO  App - ✓ Loaded 4 brokers
[main] INFO  App - ✓ HTTP server started on port 9090
```

**Wait for full startup** (30-60 seconds)

---

### Step 5: Verify Production

**Health check**:
```bash
# Check health endpoint
curl http://localhost:9090/api/health

# Expected: {"status":"UP","timestamp":"..."}
```

**Check authentication metrics**:
```bash
# Verify all brokers authenticated
curl http://localhost:9090/metrics | grep broker_authentications_total

# Expected output (showing successful auth):
# broker_authentications_total{broker="UPSTOX",status="success"} 1.0
# broker_authentications_total{broker="ZERODHA",status="success"} 1.0
# broker_authentications_total{broker="FYERS",status="success"} 1.0
# broker_authentications_total{broker="DHAN",status="success"} 1.0
```

**Check broker health**:
```bash
# All brokers should be UP (1.0)
curl http://localhost:9090/metrics | grep broker_health_status

# Expected:
# broker_health_status{broker="UPSTOX"} 1.0
# broker_health_status{broker="ZERODHA"} 1.0
# broker_health_status{broker="FYERS"} 1.0
# broker_health_status{broker="DHAN"} 1.0
```

**Test order placement** (in production, use small quantity):
```bash
# Place small test order
curl -X POST http://localhost:9090/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${USER_TOKEN}" \
  -d '{
    "broker": "UPSTOX",
    "symbol": "NSE:SBIN-EQ",
    "quantity": 1,
    "orderType": "LIMIT",
    "price": "500.00",
    "transactionType": "BUY"
  }'

# Check order status
curl http://localhost:9090/api/orders/${ORDER_ID}

# Cancel test order
curl -X DELETE http://localhost:9090/api/orders/${ORDER_ID}
```

**Run quick load test**:
```bash
# Verify system performance
cd load-tests
./quick-load-test.sh

# Expected: >95% success rate, <100ms avg response time
```

---

### Step 6: Monitor for 1 Hour

**Watch metrics closely**:
```bash
# Monitor error rates
watch -n 30 'curl -s http://localhost:9090/metrics | grep broker_orders_total'

# Monitor authentication
watch -n 60 'curl -s http://localhost:9090/metrics | grep broker_authentications_total'

# Monitor broker health
watch -n 30 'curl -s http://localhost:9090/metrics | grep broker_health_status'
```

**Check application logs**:
```bash
# Follow logs for errors
sudo journalctl -u amzf -f | grep -i "error\|exception\|auth"

# Should see no authentication errors
```

**If any issues detected**:
- Check specific broker logs
- Verify token is correct
- Test authentication manually
- Contact broker support if needed

---

### Step 7: Clean Up Old Tokens

**After 24 hours of successful operation**:

1. **Revoke old tokens** from broker portals (if not auto-revoked)
2. **Delete old backups** (keep only last 3 rotations):
```bash
# List backups
ls -lt /backup/secrets.properties.* | head -5

# Delete old backups (keep last 3)
ls -t /backup/secrets.properties.* | tail -n +4 | xargs rm -f
```

3. **Update rotation log**:
```bash
# Document rotation
echo "$(date): Token rotation completed for all brokers" >> /var/log/amzf/token-rotation.log
```

---

## Rollback Procedure

**If production rotation fails**:

### Immediate Rollback (Within 5 Minutes)

```bash
# Stop application
sudo systemctl stop amzf

# Restore old secrets
sudo cp /backup/secrets.properties.TIMESTAMP /secure/secrets.properties

# Secure permissions
sudo chmod 400 /secure/secrets.properties
sudo chown amzf:amzf /secure/secrets.properties

# Start application
sudo systemctl start amzf

# Verify
curl http://localhost:9090/api/health
```

### Verify Rollback

```bash
# Check authentication with old tokens
curl http://localhost:9090/metrics | grep broker_authentications_total

# All should show success
```

### Investigate Failure

```bash
# Check what went wrong
sudo journalctl -u amzf | grep -i "auth.*failed"

# Common issues:
# - Typo in new token
# - Token not yet activated by broker
# - Network connectivity to broker API
# - Insufficient permissions on new token
```

---

## Emergency Token Rotation

**If token compromised, rotate immediately (no staging)**:

### Step 1: Revoke Compromised Token
- Login to broker portal
- Revoke/delete compromised token immediately

### Step 2: Generate New Token
- Create new token in broker portal
- Copy to secure location

### Step 3: Update Production
```bash
# Emergency update (no staging)
sudo nano /secure/secrets.properties

# Update compromised token only
broker.api_key=EMERGENCY_NEW_KEY
broker.api_secret=EMERGENCY_NEW_SECRET

# Restart
sudo systemctl restart amzf
```

### Step 4: Verify
```bash
# Verify authentication
curl http://localhost:9090/metrics | grep "broker_authentications_total{broker=\"BROKER_NAME\""

# Should show successful auth
```

### Step 5: Incident Report
- Document incident
- Investigate how token was compromised
- Implement preventive measures
- Notify security team

---

## Automation (Future Enhancement)

**Automated Token Rotation Script**:
```bash
#!/bin/bash
# automated-token-rotation.sh

# This script will be enhanced to:
# 1. Fetch new tokens from broker APIs (if supported)
# 2. Test in staging automatically
# 3. Update production if tests pass
# 4. Send notifications
# 5. Rollback on failure

# TODO: Implement when broker APIs support automatic rotation
```

---

## Post-Rotation Checklist

- [ ] All brokers authenticated successfully
- [ ] Health check passing
- [ ] Metrics showing no errors
- [ ] Test orders placed successfully
- [ ] Load test passed (>95% success rate)
- [ ] Monitored for 1 hour (no issues)
- [ ] Old tokens revoked (after 24 hours)
- [ ] Rotation documented in log
- [ ] Next rotation scheduled (90 days)
- [ ] Team notified of completion

---

## Token Security Best Practices

1. **Never commit tokens to git**
   - Use .gitignore for secrets.properties
   - Store in environment variables or secrets manager

2. **Rotate regularly**
   - Every 90 days (or more frequently if required)
   - Immediately if compromised

3. **Use environment variables in production**
   - More secure than files
   - Easier to rotate via orchestration tools

4. **Audit access**
   - Log who accesses secrets
   - Restrict access to minimal users

5. **Monitor for suspicious activity**
   - Alert on authentication failures
   - Alert on unusual API usage

6. **Encrypt secrets at rest**
   - Use encrypted file system
   - Or use secrets management service (AWS Secrets Manager, HashiCorp Vault)

---

## Troubleshooting

### Issue: Authentication Failed After Rotation

**Symptoms**: `broker_authentications_total{status="failure"}` increasing

**Check**:
```bash
# Verify token in secrets file
sudo cat /secure/secrets.properties | grep api_key

# Compare with broker portal
# Ensure no typos or whitespace

# Test token manually (curl to broker API)
curl -H "Authorization: Bearer ${TOKEN}" https://api.upstox.com/v2/user/profile
```

**Fix**:
```bash
# Correct token in secrets file
sudo nano /secure/secrets.properties

# Restart
sudo systemctl restart amzf
```

---

### Issue: Token Not Activated Yet

**Symptoms**: Authentication fails immediately after rotation

**Solution**:
- Wait 5-10 minutes for broker to activate new token
- Some brokers have activation delay
- Check broker documentation for activation time

---

### Issue: Old Token Still Working

**Symptoms**: Application works with backup secrets

**Solution**:
- Verify new token is correct
- Check broker portal for activation status
- Contact broker support if persistent

---

## Conclusion

**Token Rotation Checklist**:
- [x] Pre-rotation planning completed
- [x] New tokens generated
- [x] Tested in staging
- [x] Production updated
- [x] Application restarted
- [x] Verification completed
- [x] Monitored for 1 hour
- [x] Old tokens revoked
- [x] Documentation updated
- [x] Next rotation scheduled

**Status**: ✅ **ROTATION COMPLETE**

**Next Rotation**: [DATE + 90 days]
