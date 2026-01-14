# Exit Qualification Completion (V010) — Implementation Plan
_Date: 2026-01-13 (Asia/Kolkata)_

This plan completes **EXIT qualification symmetry** so the system becomes:

**ENTRY (already staged):** Signal → Delivery → Validate → Intent → TMS  
**EXIT (to be staged):** Tick(symbol) → Trade qualification → ExitSignal → ExitIntent → (execute) → (fill) → TMS closes

Key invariant to preserve (per your decision): **Persist row first, then emit/broadcast**.

---

## 0) Goals and Non‑Goals

### Goals
- Introduce **ExitIntent** as the **execution qualification** stage for exits.
- Ensure **restart-safe idempotency** via DB constraints and DB-enforced cooldown (no in-memory enforcement).
- Make exit pipeline outcome-trackable (PLACED/FILLED/FAILED) so trade closure is deterministic.
- Keep **TradeManagementService (TMS)** as the **single writer** for trade lifecycle/closure and P&L.

### Non‑Goals (for V010 core)
- Advanced retry orchestration, smart routing of exits across multiple brokers per trade.
- Multi-leg partial exit support (can be added later; schema supports it with extension).
- UI/UX changes (dashboards can be minimal query additions).

---

## 1) Current State (Verified) + Gap

### What exists (good)
- Exit detection is **symbol-driven** and loops open trades on symbol.
- ExitSignal persists episodes (trade_id + reason + episode_id unique).
- TMS is intended to be the single writer for trade closure.

### Gap
- Exit has **no equivalent of TradeIntent**:
  - No per-(trade, user_broker) **execution qualification** stage.
  - No persisted **order outcome** state for exits.
  - In-memory cooldown is **lost on restart**.

---

## 2) Target State (V010)

### Two-stage exit pipeline
**Stage 1 — Trade Qualification (market & trade logic):**
- Tick(symbol) → gather open trades for symbol
- For each trade: evaluate exit condition(s) + confirmation (brick logic)
- If qualifies: produce ExitCandidate(trade_id, reason, detected_price, timestamp, …)

**Stage 2 — Execution Qualification (operational gates):**
- Create ExitSignal (DETECTED → PUBLISHED)
- Create ExitIntent (PENDING → APPROVED/REJECTED)
- If APPROVED: delegate to executor (either OrderExecutionService now, or TMS later—see §6)
- Reconciler updates ExitIntent outcome (PLACED/FILLED/FAILED)
- TMS consumes FILLED exit intent → closes trade with P&L

---

## 3) Deliverable 1 — Database Migration (V010)

Apply SQL: `V010__exit_intents_and_exit_episode_cooldown.sql`

Includes:
- `exit_intents` table + unique idempotency index
- DB-enforced cooldown (recommended: inside episode generator OR via cooldown_until field)
- Supporting indexes for pending scans and broker order lookup

---

## 4) Deliverable 2 — Service Additions and Modifications

### 4.1 New domain + repo
- `ExitIntent` model
- `ExitIntentRepository` (insert, findByUniqueKey, updateStatus, updateBrokerOrderId, markFailed, markFilled, findPendingApproved, etc.)

### 4.2 New service
- `ExitQualificationService`
  - Mirrors ValidationService style but for exits.
  - Produces `ExitQualificationResult`:
    - passed (boolean)
    - errors (list/codes)
    - calculatedQty (typically trade.openQty)
    - orderType, limitPrice, productType

### 4.3 Update: SignalManagementService (SMS)
Where today you do:
- persist ExitSignal
- publish ExitSignal event

You will now do:
1) persist ExitSignal  
2) persist ExitIntent (PENDING + qualification outcome)  
3) emit ExitSignalPublished + ExitIntent{Approved/Rejected} events

**Ordering rule (hard):** DB rows first, events second.

### 4.4 Update: ExitSignalService
Keep trade-qualification logic here. Ensure it outputs **candidates** only.
- Must not mutate trades.
- Must not place orders.

---

## 5) Exit Outcome Tracking Integration

### Minimal integration options
**Option 1 (Recommended for fast correctness):**
- OrderExecutionService executes APPROVED ExitIntents
- PendingOrderReconciler updates ExitIntent → FILLED/FAILED
- TMS listens for FILLED exit intent and closes trade.

**Option 2 (Purist symmetry with TMS):**
- TMS executes all ExitIntents itself (single orchestrator for trade+orders)
- OrderExecutionService becomes a thin broker adapter invoked by TMS.

V010 can ship with Option 1 and later migrate to Option 2 without schema changes.

---

## 6) Cooldown / Re-Arm Correctness (Restart-Safe)

Replace in-memory cooldown with DB enforcement.

### Preferred: enforce inside `generate_exit_episode(...)`
- If cooldown active, function raises exception or returns sentinel.
- Caller treats cooldown as “not eligible yet”.

This ensures:
- No duplicate firing on restart.
- No reliance on instance memory.
- All workers behave consistently.

---

## 7) Direction Handling (Must Fix)

Exit conditions must be direction-aware:
- LONG: target if price ≥ target, stoploss if price ≤ floor
- SHORT: target if price ≤ target, stoploss if price ≥ ceiling (store as effectiveCeiling or define semantics clearly)

Define:
- `Trade.direction` must be available (prefer persisted).
- If not stored today: derive once at trade creation and persist to trade row.

---

## 8) Idempotency + Invariants

### Idempotency keys
- ExitSignal: UNIQUE (trade_id, exit_reason, episode_id)
- ExitIntent: UNIQUE (trade_id, user_broker_id, exit_reason, episode_id) WHERE deleted_at IS NULL

### Must-hold invariants
- No ExitIntent without ExitSignal row.
- No “emit ExitIntentApproved” without ExitIntent persisted.
- No trade close without FILLED exit intent (or broker fill event mapped to it).

---

## 9) Testing Plan (Practical)

### Unit tests
- ExitQualificationService qualifies/rejects under each gate.
- Direction-aware target/SL evaluation.
- Idempotency: repeated onExitDetected creates at most one ExitIntent per unique key.

### Integration tests
- Simulate tick causing exit on symbol with 2 open trades:
  - Both qualify → 2 ExitSignals + 2 ExitIntents
- Simulate restart:
  - Cooldown active → episode generator blocks duplication.
- Simulate broker reject:
  - ExitIntent becomes FAILED with error_code/message; trade remains OPEN.

### Verification queries
- Pending exits: `status IN ('PENDING','APPROVED','PLACED')`
- Duplicate prevention: confirm unique index prevents double insert.

---

## 10) Rollout Plan (Incremental, Safe)

1) Deploy DB migration V010 (no code usage yet).
2) Deploy code that writes ExitIntents but does not execute them (dark mode).
3) Enable execution path for a limited set of symbols/users via config flag.
4) Enable fully once metrics confirm stability.

---

## 11) Operational Metrics (Add counters)

- exit_signals.detected_count
- exit_signals.published_count
- exit_intents.approved_count
- exit_intents.rejected_count
- exit_intents.placed_count
- exit_intents.filled_count
- exit_intents.failed_count
- time_to_fill_ms (placed_at → filled_at)

---

## 12) Definition of Done (V010)

- ExitIntent exists, unique idempotency enforced, created for each qualifying exit.
- ExitQualificationService produces APPROVED/REJECTED deterministically.
- Cooldown enforced in DB (restart-safe).
- Exit order outcomes update ExitIntent.
- TMS closes trade only from FILLED outcomes.
