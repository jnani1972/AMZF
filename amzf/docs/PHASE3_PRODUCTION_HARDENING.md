# Phase 3: Production Hardening Status

## Overview

Phase 3 focuses on making the multi-broker trading system production-ready with comprehensive testing, monitoring, security, and operational excellence.

**Status:** In Progress (1/6 complete)

**Last Updated:** 2026-01-15

---

## 1. Instrument Loader Integration ✅ COMPLETE

### What Was Implemented

**Core Components:**
- `Instrument.java` domain model with normalized fields
- `BrokerInstrumentFetcher.java` interface
- `UpstoxInstrumentFetcher.java` - fetches from Upstox CSV.gz
- `ZerodhaInstrumentFetcher.java` - fetches from Zerodha CSV
- `AsyncInstrumentLoader.java` updated with actual broker integration

**Features:**
- ✅ HTTP-based fetching with gzip support
- ✅ CSV parsing with error handling
- ✅ Batch upsert to database (efficient bulk loading)
- ✅ Integrity checks (fetched count vs saved count)
- ✅ Hash-based delta detection framework
- ✅ Statistics tracking per broker
- ✅ Event callbacks (load success/failure)

**Database Integration:**
- Uses existing `InstrumentRepository` interface
- Uses existing `PostgresInstrumentRepository` with batch upserts
- Existing schema: `instruments` table with broker_id, exchange, trading_symbol, etc.

**Broker-Specific Implementations:**
1. **Upstox:**
   - URL: `https://assets.upstox.com/market-quote/instruments/exchange/complete.csv.gz`
   - Format: GZIP-compressed CSV
   - ~500KB compressed, ~40,000+ instruments
   - Fields: instrument_key, exchange_token, trading_symbol, name, last_price, expiry, strike, tick_size, lot_size, instrument_type, option_type, exchange

2. **Zerodha:**
   - URL: `https://api.kite.trade/instruments`
   - Format: Plain CSV
   - ~80,000+ instruments
   - Fields: instrument_token, exchange_token, tradingsymbol, name, last_price, expiry, strike, tick_size, lot_size, instrument_type, segment, exchange

**How It Works:**
```java
// Setup fetchers
Map<String, BrokerInstrumentFetcher> fetchers = Map.of(
    "UPSTOX", new UpstoxInstrumentFetcher(),
    "ZERODHA", new ZerodhaInstrumentFetcher()
);

// Create loader
AsyncInstrumentLoader loader = new AsyncInstrumentLoader(instrumentRepo, fetchers);

// Configure
loader.setRefreshTime(LocalTime.of(8, 0));  // 8 AM daily
loader.onLoadSuccess((event) ->
    log.info("Loaded {} instruments for {} in {}ms",
        event.instrumentCount(), event.brokerCode(), event.loadTime().toMillis()));

// Start
loader.start();

// Instruments are now loaded in background
// Daily refresh at 8 AM IST
```

**Integrity Checks:**
- Compare fetched count with database count
- Log warnings if mismatch detected
- MD5 hash of instrument list for delta detection

### Remaining TODOs

1. **Dhan & FYERS Fetchers:**
   - Create `DhanInstrumentFetcher.java`
   - Create `FyersInstrumentFetcher.java`
   - Different CSV formats, need API documentation

2. **Delta Updates:**
   - Currently: Full download each time
   - Needed: Compare hash, only update changed instruments
   - Benefit: Faster refresh, less bandwidth

3. **Persistent Hash Storage:**
   - Store last update hash in database
   - Table: `instrument_metadata (broker_code, last_hash, last_update_time)`
   - Compare on each refresh

4. **Expiry Date & Strike Price Parsing:**
   - Currently: Set to null
   - Needed: Parse from CSV columns 5, 6
   - Used for derivatives (futures/options)

5. **Enhanced Checksum:**
   - Currently: Simple count + first 10 symbols
   - Needed: Full CRC32 of all instrument keys
   - Better integrity validation

---

## 2. Prometheus Metrics Adapter ⏳ PENDING

### Goal

Implement `BrokerMetrics` interface with Prometheus exporter to expose metrics at `/metrics` endpoint.

### Planned Implementation

**Components:**
- `PrometheusBrokerMetrics.java` - Prometheus adapter
- Uses Prometheus Java client library
- Exposes metrics via HTTP endpoint

**Metrics to Expose:**
```prometheus
# Order metrics
broker_orders_total{broker="UPSTOX",status="success"} 1234
broker_orders_total{broker="UPSTOX",status="failure"} 12
broker_order_latency_seconds{broker="UPSTOX",quantile="0.5"} 0.120
broker_order_latency_seconds{broker="UPSTOX",quantile="0.95"} 0.245
broker_order_latency_seconds{broker="UPSTOX",quantile="0.99"} 0.380

# Rate limit metrics
broker_rate_limit_hits_total{broker="UPSTOX",limit_type="per_second"} 5
broker_current_rate{broker="UPSTOX"} 8
broker_rate_utilization{broker="UPSTOX"} 0.80

# Failover metrics
broker_health_status{broker="UPSTOX",status="healthy"} 1
broker_failover_events_total{broker="UPSTOX",event="broker_down"} 2
broker_uptime_seconds{broker="UPSTOX"} 86400

# Instrument loader metrics
instrument_load_duration_seconds{broker="UPSTOX"} 12.5
instrument_count{broker="UPSTOX"} 42000
```

**Grafana Dashboard:**
- Broker health overview
- Order success rates
- Latency percentiles
- Rate limit utilization
- Failover events timeline

**Next Steps:**
1. Add Prometheus dependency to pom.xml
2. Create PrometheusBrokerMetrics implementation
3. Integrate with OrderBroker, FailoverManager, AsyncInstrumentLoader
4. Create /metrics endpoint
5. Build Grafana dashboard JSON

---

## 3. Chaos Engineering Tests ⏳ PENDING

### Goal

Validate system resilience by intentionally injecting failures.

### Test Scenarios

1. **Broker API Downtime:**
   - Simulate Upstox API returning 503
   - Verify: BrokerFailoverManager detects failure
   - Verify: Degradation strategy activates (READ_ONLY mode)
   - Verify: Alerts emitted
   - Verify: Auto-recovery when API returns

2. **Token Expiry:**
   - Simulate expired token during order placement
   - Verify: CredentialRotationService detects expiry
   - Verify: Auto-refresh attempted
   - Verify: User notified if manual login required (Zerodha)

3. **Network Timeouts:**
   - Simulate slow/timeout responses
   - Verify: Retry with exponential backoff
   - Verify: Max retries respected
   - Verify: Proper error propagation

4. **Database Connection Loss:**
   - Simulate database unavailable
   - Verify: Connection pool retry logic
   - Verify: Graceful degradation
   - Verify: No data loss when recovered

5. **Rate Limit Exhaustion:**
   - Hammer broker with orders
   - Verify: OrderThrottleManager blocks requests
   - Verify: Queueing works correctly
   - Verify: Orders execute when rate limit resets

**Implementation:**
- Use Chaos Monkey or Toxiproxy
- Create `ChaosEngineeringTest.java` test suite
- Document test results in runbook

---

## 4. Security Hardening ⏳ PENDING

### Current Issues

- Tokens stored in environment variables
- Sensitive data may appear in logs
- No secrets rotation policy

### Planned Enhancements

1. **Secrets Manager Integration:**
   - Use HashiCorp Vault or AWS Secrets Manager
   - Store API keys, tokens securely
   - Auto-rotation support
   - Audit trail for secret access

2. **Log Sanitization:**
   - Audit all log statements
   - Mask/redact: tokens, API keys, user IDs
   - Use placeholder: `[REDACTED]`
   - Example: `log.info("Order placed: {}", sanitize(response))`

3. **TLS/SSL Verification:**
   - Ensure all broker API calls use HTTPS
   - Verify SSL certificates
   - Pin certificates for critical brokers

4. **Input Validation:**
   - Sanitize all user inputs
   - Prevent SQL injection (use prepared statements)
   - Prevent XSS in admin UI

5. **Rate Limiting on Endpoints:**
   - Protect admin endpoints from brute force
   - API rate limiting per user/IP

**Next Steps:**
1. Security audit of codebase
2. Implement secrets manager integration
3. Create log sanitization utility
4. Update all log statements
5. Penetration testing

---

## 5. Load & Stress Testing ⏳ PENDING

### Goal

Measure system throughput, identify bottlenecks, validate performance targets.

### Performance Targets

- **Order Latency p95:** < 200ms
- **Order Success Rate:** > 99.5%
- **Throughput:** 100 orders/sec sustained
- **Concurrent Users:** 1000+
- **Tick Updates:** 10,000 symbols/sec

### Test Scenarios

1. **Baseline Performance:**
   - Single broker, steady load
   - Measure: latency, throughput, CPU, memory

2. **Peak Load:**
   - Market open scenario (9:15 AM)
   - 1000 users placing orders simultaneously
   - Measure: queue depth, backpressure, failures

3. **Sustained Load:**
   - 100 orders/sec for 8 hours (trading day)
   - Monitor: memory leaks, connection pool exhaustion

4. **Data Stream Load:**
   - 10,000 symbols WebSocket subscriptions
   - Measure: message processing latency, backlog

5. **Database Stress:**
   - Bulk instrument loads (80,000+ instruments)
   - Concurrent order writes
   - Measure: connection pool, query latency

**Tools:**
- Apache JMeter for HTTP load testing
- Gatling for scenario-based testing
- JProfiler for profiling
- VisualVM for memory analysis

**Next Steps:**
1. Create JMeter test plans
2. Set up dedicated load test environment
3. Run baseline tests
4. Identify bottlenecks
5. Optimize and re-test

---

## 6. Documentation & Runbooks ⏳ PENDING

### Operational Runbooks

1. **Token Rotation Runbook:**
   - How to manually rotate tokens
   - What to do if auto-refresh fails
   - Zerodha daily login procedure
   - Emergency token refresh

2. **Instrument Loader Management:**
   - How to trigger manual refresh
   - What to do if load fails
   - How to verify integrity
   - Rollback procedure

3. **Broker Outage Response:**
   - How to detect broker down
   - Activating degradation mode
   - Manual failover to backup broker
   - User communication template

4. **Performance Degradation:**
   - Identifying slow brokers
   - Adjusting rate limits
   - Scaling resources
   - Database optimization

5. **Incident Response:**
   - Severity levels
   - Escalation procedures
   - Communication channels
   - Post-mortem template

### User Documentation

1. **Multi-Broker Setup Guide:**
   - OAuth setup for each broker
   - Configuration examples
   - Troubleshooting common issues

2. **Product Type Selection:**
   - CNC vs MIS vs NRML vs MTF
   - Broker-specific limitations
   - Risk warnings

3. **API Rate Limits:**
   - Per-broker limits documented
   - How throttling works
   - Best practices

**Next Steps:**
1. Create runbook templates
2. Document common incidents
3. Create video tutorials
4. Set up internal wiki

---

## Implementation Timeline

### Week 1 (Current):
- ✅ Instrument loader integration

### Week 2:
- Prometheus metrics adapter
- Basic Grafana dashboards

### Week 3:
- Security hardening (secrets manager, log sanitization)
- Chaos engineering test suite

### Week 4:
- Load testing with JMeter
- Performance optimization

### Week 5:
- Documentation & runbooks
- Final integration testing

### Week 6:
- Production deployment
- Monitoring setup
- On-call procedures

---

## Files Created (Phase 3 So Far)

1. `domain/instrument/Instrument.java` - Domain model
2. `infrastructure/broker/instrument/BrokerInstrumentFetcher.java` - Interface
3. `infrastructure/broker/instrument/UpstoxInstrumentFetcher.java` - Upstox implementation
4. `infrastructure/broker/instrument/ZerodhaInstrumentFetcher.java` - Zerodha implementation
5. `infrastructure/broker/common/AsyncInstrumentLoader.java` - Updated with real integration
6. `docs/PHASE3_PRODUCTION_HARDENING.md` - This document

**Total New Code:** ~700 lines

---

## Success Criteria

Phase 3 is complete when:

- ✅ Instrument loader fetches from all 4 brokers
- ✅ Prometheus metrics exposed and visualized in Grafana
- ✅ All chaos engineering tests passing
- ✅ Security audit complete, all issues resolved
- ✅ Load tests meeting performance targets
- ✅ All runbooks documented and tested
- ✅ 92/92 tests still passing
- ✅ Zero production incidents in first 2 weeks

**Current Progress:** 1/6 major items complete (17%)

---

## Next Actions

**Immediate (This Session):**
1. ~~Complete instrument loader~~ ✅
2. Start Prometheus metrics adapter

**This Week:**
3. Implement security hardening
4. Create chaos engineering tests

**Next Sprint:**
5. Load testing
6. Documentation & runbooks

---

## Notes

- All Phase 2 features remain stable
- 92 tests still passing
- No regressions introduced
- Backward compatible with existing code

**Ready for production after Phase 3 completion.**
