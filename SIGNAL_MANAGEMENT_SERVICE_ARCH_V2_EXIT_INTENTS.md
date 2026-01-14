# SignalManagementService Architecture (V2) — Signal Qualification Completed
_Date: 2026-01-13 (Asia/Kolkata)_

This document updates the existing SignalManagementService architecture to include **complete qualification** for both **ENTRY** and **EXIT** in a way that is consistent with **TradeManagementService (TMS)**.

---

## 1) Terminology

- **Signal (ENTRY)**: a *global symbol fact* (market condition) that may be actionable for many user-brokers.
- **ExitSignal (EXIT)**: a *trade-level fact* derived from symbol ticks + trade state (“this trade meets exit condition X”).
- **Delivery**: fan-out record per user-broker for entry signals.
- **Intent**: persisted qualification outcome (APPROVED/REJECTED) + execution tracking (PLACED/FILLED/FAILED).

**Hard rule:** Persist state first, emit second.

---

## 2) Lifecycle Overview (Entry vs Exit Symmetry)

### ENTRY (Symbol → User-Broker qualification)
1. Detect symbol condition → `SignalCandidate`
2. **SMS persists** `signals` (DETECTED → PUBLISHED)
3. **SMS persists** `signal_deliveries` per user-broker
4. ValidationService processes deliveries → persists `trade_intents` (APPROVED/REJECTED)
5. TMS consumes approved intents → creates trades → places entry orders → reconciles fills

### EXIT (Symbol → Trade qualification → User-Broker execution qualification)
1. Tick(symbol) triggers ExitSignalService
2. ExitSignalService fetches open trades on symbol and evaluates exit conditions
3. For each qualifying trade → emits `ExitCandidate(trade_id, reason, …)`
4. **SMS persists** `exit_signals` (DETECTED → PUBLISHED, with episode_id)
5. **SMS persists** `exit_intents` (PENDING → APPROVED/REJECTED)
6. Executor places exit order for APPROVED intents → updates intent to PLACED/FAILED
7. Reconciler updates intent to FILLED (or FAILED)
8. **TMS closes trade** on FILLED, calculates P&L, persists closure

---

## 3) Signal Qualification Boundaries (The key compliance concept)

### 3.1 ENTRY Qualification
- **Detection scope:** symbol-level (global)
- **Qualification scope:** user-broker-level (capital + exposure + broker state)
- **Record of qualification:** `trade_intents`

ENTRY question answered by `trade_intents`:
> “Should user-broker U take this symbol signal S, and with what qty?”

### 3.2 EXIT Qualification (Two stages)
#### Stage A — Trade Qualification (market + trade state)
- symbol tick generates candidates
- qualifies *per trade*:
  - open
  - direction-aware target/SL/time condition
  - confirmation filters (brick tracker)
  - DB-enforced re-arm/cooldown & episode generation

Exit question at Stage A:
> “Does trade T meet exit condition R right now?”

#### Stage B — Execution Qualification (operational)
- qualifies *per user-broker that owns trade*:
  - broker enabled & connected
  - no existing pending exit
  - exit window open (market hours policy)
  - portfolio lock/freeze checks if any
  - qty and order type/limit derived

Exit question at Stage B (ExitIntent):
> “Is it operationally permitted to place the exit now, and how?”

---

## 4) Data Model

### 4.1 ENTRY tables (existing)
- `signals` (global)
- `signal_deliveries` (per user-broker)
- `trade_intents` (qualification outcome)
- `trades` (owned by TMS)

### 4.2 EXIT tables (existing + new)
- `exit_signals` (per trade/reason/episode)
- **NEW:** `exit_intents` (per trade/user_broker/reason/episode)

### 4.3 Idempotency keys
- `signals`: UNIQUE (symbol, direction, confluence_type, signal_day, effective_floor, effective_ceiling)
- `signal_deliveries`: UNIQUE (signal_id, user_broker_id)
- `trade_intents`: UNIQUE as per your existing model (or per delivery consumption)
- `exit_signals`: UNIQUE (trade_id, exit_reason, episode_id)
- `exit_intents`: UNIQUE (trade_id, user_broker_id, exit_reason, episode_id)

---

## 5) Routing / Concurrency

### 5.1 Entry routing (symbol partitions)
- EntryCoordinator routes by `symbol`
- ensures sequential processing per symbol

### 5.2 Exit routing
- Detection is symbol-driven (tick), but processing is trade-based.
- Recommended hybrid:
  - ExitDetectionCoordinator by symbol (collect open trades quickly)
  - ExitIntentCoordinator by trade_id (serialize intent creation per trade)

Minimum acceptable:
- one coordinator keyed by `trade_id` for SMS onExitDetected, because idempotency is enforced by DB anyway.

---

## 6) Event Flow (Persist then Emit)

### Entry
1) Insert `signals`
2) Insert `signal_deliveries`
3) Emit SIGNAL_PUBLISHED (signal_id)
4) Emit SIGNAL_DELIVERY_CREATED (delivery_ids)

### Exit
1) DB episode generation (with cooldown enforcement)
2) Insert `exit_signals`
3) Insert `exit_intents`
4) Emit EXIT_SIGNAL_PUBLISHED (exit_signal_id)
5) Emit EXIT_INTENT_APPROVED or EXIT_INTENT_REJECTED (exit_intent_id)

---

## 7) Ownership Rules (Single Writer)

- **SMS** is single-writer for:
  - `signals`, `signal_deliveries`
  - `exit_signals`, `exit_intents`

- **TMS** is single-writer for:
  - `trades` lifecycle and closure (including P&L)

- **OrderExecution / Reconciler** may update:
  - `exit_intents` execution statuses (PLACED/FILLED/FAILED)
  - (but must NOT close trades)

---

## 8) Failure Modes & Guarantees

### Must be correct on restart
- Cooldown enforcement in DB (no reliance on instance memory).
- Unique indexes prevent duplicates.
- Pending intents can be replayed by scanning:
  - `exit_intents` WHERE status IN ('PENDING','APPROVED').

### If broker unavailable
- ExitIntent becomes REJECTED (qualification) or FAILED (execution), trade remains OPEN.
- Optional: backoff retry policy later.

---

## 9) Recommended Next Refactors

1) Make Trade.direction persisted and reliable.
2) Ensure exit detection checks direction-aware conditions.
3) Route execution solely via ExitIntents; remove any direct “exit order” calls that bypass intents.
4) Add a small “ExitIntentDispatcher” that is equivalent to delivery processor for exits.

---

## 10) Summary

- ENTRY pipeline is already staged.
- EXIT pipeline becomes staged by adding ExitIntent + ExitQualificationService + DB cooldown enforcement.
- Persist-first then emit is enforced.
- TMS remains trade lifecycle authority.
