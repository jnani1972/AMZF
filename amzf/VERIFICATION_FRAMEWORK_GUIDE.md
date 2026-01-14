# VERIFICATION FRAMEWORK GUIDE
# AnnuPaper v04 - How to Use the Complete Verification System

**Date:** January 13, 2026
**Purpose:** Master guide for using all verification documents together

---

## WHAT YOU NOW HAVE

You have a **4-document verification framework** that proves system completeness:

### 1. **SYSTEM_CLASS_METHOD_MASTER.md** (25KB)
**Your architectural constitution**
- Complete class inventory (114 Java files)
- State ownership matrix
- Thread safety patterns
- Critical invariants
- Forbidden couplings

### 2. **COMPLETENESS_VERIFICATION_ADDENDUM.md** (30KB)
**Enhanced sections for completeness proof**
- Engine completeness cards (entry points, outputs, reachability, failures)
- Production TODO gate (P0/P1/P2 with release blockers)
- Single source of truth map (SSOT enforcement)
- End-to-end completion checklist (Order→Trade→Exit)
- Consistency issue resolutions
- Risk profile bundles (Conservative/Balanced/Aggressive)
- 7-line completeness questionnaire

### 3. **VERIFICATION_WALKTHROUGH_CHECKLIST.md** (18KB)
**Practical code review tool**
- 29 sections with checkbox format
- Per-engine verification questions
- Expected vs actual validation
- Scorecard (0-10 per engine)

### 4. **VERIFICATION_FRAMEWORK_GUIDE.md** (this document)
**How to use everything together**

---

## WHO USES WHAT (AND WHEN)

### For **Architecture Review** (Quarterly)
**Use:** SYSTEM_CLASS_METHOD_MASTER.md + COMPLETENESS_VERIFICATION_ADDENDUM.md

**Process:**
1. Review Section 8.1 (Engine Completeness Cards)
2. Verify all entry points still wired
3. Check SSOT map (Section 8.3) - no duplicates added?
4. Review P0 TODO gate (Section 8.2) - any new blockers?
5. Update forbidden couplings if architecture changed

---

### For **Code Walkthrough** (Weekly/Sprint Review)
**Use:** VERIFICATION_WALKTHROUGH_CHECKLIST.md

**Process:**
1. Pick one engine (e.g., Market Data)
2. Ask developer to trace one complete flow
3. Check boxes as verified
4. Score engine (0-10)
5. Document gaps in notes column

---

### For **Code Review** (Daily/PR Review)
**Use:** COMPLETENESS_VERIFICATION_ADDENDUM.md Section 15 (7-Line Questionnaire)

**Process:**
For each new/modified important method:
1. Ask developer the 7 lines:
   - Entry (who calls it)
   - Inputs (what happens if missing)
   - Reads (what state)
   - Writes (what state)
   - Events (who consumes)
   - Idempotency (dedupe mechanism)
   - Failure (fallback mode)
2. Score 0-14 points (2 per line)
3. Accept PR if score >= 11/14

---

### For **Onboarding New Developers**
**Use:** All documents in order

**Day 1-2: System Overview**
- Read SYSTEM_CLASS_METHOD_MASTER.md Section 1 (System Overview)
- Study Section 2 (Engine-wise Class Index)
- Understand Section 6 (Event Flow Index)

**Day 3-4: Deep Dive**
- Read Section 3 (Class Responsibility Cards)
- Study Section 10 (Critical Invariants)
- Review Section 7 (Forbidden Couplings)

**Day 5: Verification Practice**
- Use VERIFICATION_WALKTHROUGH_CHECKLIST.md
- Pick Market Data engine
- Trace tick → candle → aggregation
- Verify understanding with 7-line questionnaire

---

### For **Production Release** (Gate)
**Use:** COMPLETENESS_VERIFICATION_ADDENDUM.md Section 8.2 (TODO Gate)

**Release Criteria:**
```
GATE: All P0 TODOs must be resolved OR explicitly guarded

P0 Blockers (as of Jan 13, 2026):
❌ P0-1: ExecutionOrchestrator.executeIntent() - Order placement not implemented
❌ P0-2: ExitSignalService - Uses mock HashMap (not DB-backed)
❌ P0-3: Signal deduplication - Missing unique constraint

Status: 🔴 NOT PRODUCTION READY (3 of 3 P0 blockers remain)

Next Review: After P0 fixes implemented
```

---

## VERIFICATION WORKFLOWS

### Workflow 1: Prove No Duplicate Logic

**Goal:** Verify single source of truth for all rules

**Steps:**
1. Open COMPLETENESS_VERIFICATION_ADDENDUM.md Section 8.3 (SSOT Map)
2. For each rule family, verify:
   - ✅ Exactly one owner class
   - ✅ All callers listed
   - ✅ No forbidden duplicates
3. If adding new calculation logic:
   - Add row to SSOT map
   - Identify single owner
   - Document callers
4. **Enforcement:** PR review MUST check SSOT map

**Red Flags:**
- Two classes implement same formula (e.g., ATR in two places)
- Same logic copy-pasted with slight variations
- Broker adapters re-implement shared logic instead of calling shared service

---

### Workflow 2: Prove No Partial Wiring

**Goal:** Verify all components are actually called (no dead code)

**Steps:**
1. Open COMPLETENESS_VERIFICATION_ADDENDUM.md Section 8.1 (Engine Completeness Cards)
2. For each engine, verify:
   - **Entry points:** Who calls it? (file:line references)
   - **Outputs:** Who consumes it? (exact consumers listed)
   - **Reachability:** How does App bootstrap reach this component?
3. If entry point missing:
   - ❌ Component is dead code (never executed)
   - Decision: Wire it OR delete it

**Red Flags:**
- "Expected: Should be called by..." (means not wired)
- "❌ NOT CALLED" in entry points
- No consumers listed for outputs

---

### Workflow 3: Prove E2E Completion (Tick → Order)

**Goal:** Verify critical path is fully implemented

**Steps:**
1. Open COMPLETENESS_VERIFICATION_ADDENDUM.md Section 8.4 (E2E Checklist)
2. Verify each stage:
   - ✅ Order Execution: Is executeIntent() implemented?
   - ✅ Fill Callbacks: How does broker callback reach system?
   - ✅ Trade State Machine: Are transitions wired?
   - ✅ Position Tracking: Is DB-backed or mock?
   - ✅ Exit Monitoring: DB load on startup?
3. For each ❌ INCOMPLETE:
   - Create P0 TODO
   - Add to release gate

**Red Flags:**
- "TODO" in critical path methods
- "Mock HashMap" for production data
- "Not wired" for callbacks

---

### Workflow 4: Prove Thread Safety

**Goal:** Verify no race conditions, no torn writes

**Steps:**
1. Open SYSTEM_CLASS_METHOD_MASTER.md Section 9 (Thread Safety Patterns)
2. For each shared state, verify pattern:
   - **Pattern 1:** Immutable object replacement (CHM + record)
   - **Pattern 2:** Volatile for immutable references
   - **Pattern 3:** CHM.newKeySet() for concurrent sets
   - **Pattern 4:** Single-writer, multiple-reader
3. Check Section 5.1 (State Ownership Matrix):
   - Each state has exactly one writer
   - All readers listed
   - Thread safety mechanism documented

**Red Flags:**
- `new HashMap<>()` in hot path (should be `ConcurrentHashMap`)
- Mutable object shared across threads without synchronization
- In-place mutation from multiple threads

---

### Workflow 5: Verify Consistency (No Contradictions)

**Goal:** Ensure documentation matches implementation

**Steps:**
1. Open COMPLETENESS_VERIFICATION_ADDENDUM.md Section 13 (Consistency Issues)
2. Review all 5 resolved issues:
   - 13.1: "Zero hot-path DB writes" (clarified: tick prices only)
   - 13.2: "10-point gating" (corrected: 12-point gating)
   - 13.3: ExitSignalService mock (acknowledged P0 blocker)
   - 13.4: Signal dedupe (acknowledged P0 blocker)
   - 13.5: Idempotency (acknowledged P0 blocker)
3. When documenting new features:
   - Check for contradictions with existing docs
   - Update all 4 verification documents
   - Add consistency note if ambiguous

---

## DOCUMENT UPDATE PROTOCOL

### When to Update Each Document

**SYSTEM_CLASS_METHOD_MASTER.md:**
- New engine/class added
- State ownership changes
- Thread safety model changes
- Critical invariants modified
- Forbidden couplings violated (requires review)

**COMPLETENESS_VERIFICATION_ADDENDUM.md:**
- New entry point registered
- New output consumer added
- P0 TODO resolved
- SSOT map changes (new rule owner)
- Risk profile parameters changed

**VERIFICATION_WALKTHROUGH_CHECKLIST.md:**
- New verification questions discovered
- Flow changes require new checks
- Scorecard thresholds adjusted

**VERIFICATION_FRAMEWORK_GUIDE.md (this doc):**
- New workflow added
- Document usage changed
- Onboarding process updated

---

## QUICK REFERENCE CARDS

### Card 1: "Is My Code Complete?" (Developer Self-Check)

Before submitting PR, answer:

1. ☐ Entry point wired? (Someone calls my method)
2. ☐ Output consumed? (Someone uses my result)
3. ☐ State ownership clear? (One writer, listed in matrix)
4. ☐ Thread-safe? (Matches one of 4 patterns)
5. ☐ Idempotent? (Retry-safe mechanism)
6. ☐ Fail-safe? (Failure mode documented)
7. ☐ No duplication? (Single source of truth)
8. ☐ No TODO in critical path? (Or guarded by flag)

**If all ☐ checked:** ✅ Code is complete
**If any ☐ unchecked:** ⚠️ Address before PR

---

### Card 2: "Can This Ship?" (Release Manager Check)

Before production release, verify:

1. ☐ All P0 TODOs resolved (Section 8.2)
2. ☐ E2E flow complete (Section 8.4)
3. ☐ No mock data in critical path (Section 8.1)
4. ☐ All engines scored >= 7/10 (VERIFICATION_WALKTHROUGH_CHECKLIST.md)
5. ☐ SSOT map clean (no duplicate logic) (Section 8.3)
6. ☐ Thread safety verified (Section 9)
7. ☐ Consistency issues resolved (Section 13)
8. ☐ Risk profiles documented (Section 14)

**If all ☐ checked:** ✅ Production-ready
**If any ☐ unchecked:** 🔴 NOT ready - fix blockers

---

### Card 3: "What's Broken?" (Debugging Checklist)

When production issue occurs:

1. **Trace Event Flow** (MASTER.md Section 6)
   - Find event type in flow diagram
   - Trace upstream (where did it come from?)
   - Trace downstream (who should consume it?)

2. **Check State Ownership** (MASTER.md Section 5)
   - Find state variable in ownership matrix
   - Who writes it? (only one owner)
   - Who reads it? (all consumers listed)

3. **Verify Completeness** (ADDENDUM.md Section 8.1)
   - Find engine in completeness cards
   - Check: Are entry points wired?
   - Check: Are outputs consumed?
   - Check: Failure mode defined?

4. **Check Thread Safety** (MASTER.md Section 9)
   - Is state shared across threads?
   - Which pattern applies? (1-4)
   - Is pattern correctly implemented?

5. **Review Failure Modes** (ADDENDUM.md Section 8.1)
   - What is expected fallback?
   - Is fallback implemented?
   - Is failure logged/alerted?

---

## EXAMPLES: USING THE FRAMEWORK

### Example 1: Code Review - New Method Added

**Scenario:** Developer added `PortfolioValueTracker.calculateCurrentValue()`

**Reviewer Process:**
1. Open ADDENDUM.md Section 15 (7-Line Questionnaire)
2. Ask developer:
   ```
   1. ENTRY: Who calls this? (file:line)
   2. INPUTS: What if parameters are null?
   3. READS: What state do you read?
   4. WRITES: What state do you write?
   5. EVENTS: Do you emit events? Who consumes?
   6. IDEMPOTENCY: Can I call this twice safely?
   7. FAILURE: What if DB is down?
   ```
3. Score answers (0-14 points)
4. If score >= 11: ✅ Approve PR
5. If score < 11: ⚠️ Request changes

---

### Example 2: Architecture Review - Quarterly

**Scenario:** Quarterly architecture health check

**Process:**
1. Open ADDENDUM.md Section 8.1 (Engine Completeness Cards)
2. For each of 7 engines:
   - Verify entry points still wired
   - Verify outputs still consumed
   - Verify reachability proof valid
   - Verify failure modes documented
3. Open ADDENDUM.md Section 8.3 (SSOT Map)
4. Check: Any duplicate logic added?
5. Open ADDENDUM.md Section 8.2 (TODO Gate)
6. Check: Any new P0 TODOs?
7. Document findings in architecture review report

---

### Example 3: Onboarding - New Developer

**Scenario:** New developer joins team, needs to understand system

**Day 1: System Overview**
- Read MASTER.md Section 1 (System Overview)
- Understand: Architecture type, critical pipeline, invariants
- Read MASTER.md Section 2 (Engine-wise Class Index)
- Understand: 7 engines, their roles

**Day 2: Deep Dive**
- Read MASTER.md Section 3 (Class Responsibility Cards)
- Pick one engine (e.g., Signal Generation)
- Understand: Each class role, state ownership, forbidden actions

**Day 3: Code Reading**
- Read MASTER.md Section 6 (Event Flow Index)
- Trace: Tick → Signal flow (file:line references)
- Read actual code in IDE following flow

**Day 4: Verification Practice**
- Open VERIFICATION_WALKTHROUGH_CHECKLIST.md
- Pick Market Data engine
- Walk through all checkboxes
- Ask senior developer to verify understanding

**Day 5: Small Task**
- Assigned small bug fix
- Before submitting: Use 7-line questionnaire to self-check
- Submit PR with answers to 7 questions

---

### Example 4: Production Incident - ExitSignalService Lost Trades

**Scenario:** System restarted, exit monitoring lost open trades

**Debug Process:**
1. Open ADDENDUM.md Section 8.1.5 (Position Tracking Engine)
2. Read: "ExitSignalService uses in-memory HashMap"
3. Read failure mode: "Trades lost on restart ❌ P0 blocker"
4. Open ADDENDUM.md Section 8.4.4 (Exit Monitoring Completion)
5. Read: "Where do open trades come from on startup? ❌ NOT IMPLEMENTED"
6. Root cause identified: Mock HashMap not DB-backed
7. Fix: Implement DB load on startup (as documented in resolution)

---

## MAINTENANCE SCHEDULE

### Daily (PR Reviews)
- Use 7-line questionnaire for new methods
- Check SSOT map if calculation logic added
- Verify thread safety for concurrent code

### Weekly (Sprint Review)
- Use VERIFICATION_WALKTHROUGH_CHECKLIST.md
- Pick 1-2 engines to verify
- Score and document gaps

### Monthly (Release)
- Check TODO gate (Section 8.2)
- Verify E2E completion (Section 8.4)
- Run "Can This Ship?" checklist

### Quarterly (Architecture Review)
- Review all engine completeness cards (Section 8.1)
- Verify SSOT map clean (Section 8.3)
- Check consistency issues (Section 13)
- Update forbidden couplings if needed

---

## SUCCESS METRICS

### Completeness Score (Per Engine)

**Score = (Entry Points Wired + Outputs Consumed + Reachability Proven + Failures Defined) / 4**

- **10/10:** ✅ Production-grade (all wired, all consumed, all failures handled)
- **7-9:** ⚠️ Usable but has gaps (some TODOs, some missing consumers)
- **4-6:** ⚠️ Partial implementation (entry points missing, outputs not consumed)
- **0-3:** ❌ Incomplete / unsafe (dead code, no consumers, no failure handling)

**Target:** All engines >= 7/10

---

### SSOT Compliance

**Score = (Rule Families with Single Owner) / (Total Rule Families)**

**Target:** 100% (every rule has exactly one owner)

---

### TODO Gate Compliance

**Score = (P0 TODOs Resolved) / (Total P0 TODOs)**

**Target:** 100% for production release (all P0 resolved or guarded)

---

### Thread Safety Score

**Score = (States with Clear Safety Pattern) / (Total Shared States)**

**Target:** 100% (every shared state matches one of 4 patterns)

---

## TROUBLESHOOTING

### "I can't find the entry point for my class"
→ Open ADDENDUM.md Section 8.1, find your engine
→ Check: Entry points section
→ If not listed: Your class may be dead code (not wired)

### "I found duplicate logic, what do I do?"
→ Open ADDENDUM.md Section 8.3 (SSOT Map)
→ Find the rule family
→ Check: Who is the designated owner?
→ Refactor: Move logic to owner, callers should call owner

### "My code has TODO but I need to ship"
→ Open ADDENDUM.md Section 8.2.2 (TODO Guard Requirements)
→ Choose: Feature flag, config gate, or explicit exception
→ Document: Why TODO exists, when it will be fixed
→ Release: With explicit guard (no silent TODOs)

### "I'm not sure if my code is thread-safe"
→ Open MASTER.md Section 9 (Thread Safety Patterns)
→ Identify: Which pattern applies? (1-4)
→ Verify: Implementation matches pattern
→ If unsure: Ask in code review

### "Documentation says X but code does Y"
→ Open ADDENDUM.md Section 13 (Consistency Issues)
→ Check: Is this a known consistency issue?
→ If yes: Follow resolution plan
→ If no: Report as new consistency issue, update all 4 docs

---

## FINAL CHECKLIST: "Am I Using the Framework Correctly?"

☐ I have all 4 documents accessible
☐ I know which document to use for which purpose
☐ I use 7-line questionnaire in all PR reviews
☐ I check SSOT map when adding calculation logic
☐ I verify E2E completion before releases
☐ I update all 4 docs when architecture changes
☐ I run engine verification walkthrough monthly
☐ I check TODO gate before every release
☐ I enforce "no silent TODOs" rule
☐ I score engines and track improvement

**If all ☐ checked:** ✅ You're using the framework correctly!

---

## CONTACT & QUESTIONS

**For questions about:**
- **System architecture:** Read SYSTEM_CLASS_METHOD_MASTER.md first
- **Completeness verification:** Read COMPLETENESS_VERIFICATION_ADDENDUM.md
- **Code walkthrough:** Use VERIFICATION_WALKTHROUGH_CHECKLIST.md
- **How to use framework:** Read this guide (VERIFICATION_FRAMEWORK_GUIDE.md)

**For updates:**
- Create PR with changes to all affected documents
- Update "Last Updated" date
- Add change note in document header

---

**Document Version:** 1.0
**Last Updated:** January 13, 2026
**Framework Status:** ✅ Complete - All 4 documents ready for use
**Next Review:** After P0 blockers resolved (order execution, position tracking)
