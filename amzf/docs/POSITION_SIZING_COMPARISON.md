# Position Sizing Comparison: Current Implementation vs. Constitutional Document

## Executive Summary

This document compares:
- **CURRENT**: Implemented in `MtfPositionSizer.java`, `KellyCalculator.java`, `LogUtilityCalculator.java`
- **DESIRED**: Your "Averaging-Only Re-Entry & Log-Loss Risk Constitution" document

---

## 1. NEW BUY (Initial Entry) - Comparison

### CURRENT IMPLEMENTATION (`MtfPositionSizer.calculatePositionSize()`)

**Inputs:**
- `zonePrice` - Entry price
- `effectiveFloor` - Stop loss (HTF low)
- `effectiveCeiling` - Target (HTF high)
- `pWin` - Probability of win (calculated from zones)
- `pFill` - Probability of fill (0.90 default)
- `kelly` - Full Kelly fraction
- `confluenceMultiplier` - Based on signal strength (0.5x to 1.2x)
- `capSym` - Symbol capital allocation
- `existingQty` = 0 (for NEW BUY)
- `existingAvg` = 0 (for NEW BUY)

**4 Constraints Applied (minimum wins):**
```
1. LOG_SAFE_POSITION:
   - Uses LogUtilityCalculator.calculateMaxLogSafeQty()
   - Enforces: new_avg_cost <= floor * e^(-max_position_log_loss)
   - Binary search to find max qty

2. KELLY_SIZED:
   kelly_qty = (capital × kelly × kelly_fraction × confluence_multiplier × max_kelly_multiplier) / price

3. FILL_WEIGHTED:
   fill_weighted_qty = kelly_qty × pFill

4. CAPITAL_AVAILABLE:
   max_capital_qty = remaining_capital / price
```

**Final Quantity:**
```java
finalQty = min(LOG_SAFE, KELLY_SIZED, FILL_WEIGHTED, CAPITAL_AVAILABLE)
```

**Kelly Calculation (`KellyCalculator`):**
```
P(win) = zones_to_ceiling / (zones_to_floor + zones_to_ceiling)
kelly = (P_win × win_ratio - P_loss) / win_ratio
where win_ratio = (ceiling - price) / (price - floor)
```

---

### YOUR DOCUMENT (Constitutional Approach)

**Exposure Planning:**
```
e_plan = K × V

Where:
  K = Signal/Kelly weight [0,1]
  V = V_base × max(V_min, (1 - stress)^γ)

  stress = clip[0,1](R_port / L_port)
  V_base = lookupVelocity(Range_ATR)
```

**Final Allowed Exposure:**
```
e_new = max(0, min(
    e_plan,
    (L_port - R_port) / ℓ_new,    // Portfolio headroom
    (L_sym - R_s) / ℓ_new          // Symbol headroom
))

Where:
  ℓ_new = ln(S_new / P_new) < 0     // Per-leg log loss
```

**Quantity:**
```
Qty = floor(C × e_new / P_new)
```

**Decision Gates (ALL must pass):**
```
1. ℓ_new >= L_max                    // Per-leg constitution
2. e_new > 0                         // Risk budgets have room
3. Qty >= 1                          // Tradeable size
```

---

### GAPS IN CURRENT vs DESIRED (NEW BUY)

| Feature | Current | Desired | Gap |
|---------|---------|---------|-----|
| **Log-Loss Space** | ✅ Position-level only | ✅ **Per-leg + Portfolio + Symbol** | ❌ **Missing portfolio & symbol budgets** |
| **Dynamic Velocity** | ❌ None | ✅ **Stress-based throttle** | ❌ **Missing velocity system** |
| **Stress Calculation** | ❌ None | ✅ `stress = R_port / L_port` | ❌ **Missing stress metric** |
| **Velocity Table** | ❌ None | ✅ `V_base = lookupVelocity(Range_ATR)` | ❌ **Missing velocity lookup** |
| **Exposure Framing** | Kelly-based | Exposure weight [0,1] | Different paradigms |
| **Portfolio Budget** | ❌ None | ✅ `(L_port - R_port) / ℓ` | ❌ **CRITICAL MISSING** |
| **Symbol Budget** | ❌ None | ✅ `(L_sym - R_s) / ℓ` | ❌ **CRITICAL MISSING** |
| **Utility Asymmetry** | ❌ None | ✅ 3× advantage check | ❌ **Missing (optional)** |

---

## 2. REBUY (Averaging Down) - Comparison

### CURRENT IMPLEMENTATION (`MtfPositionSizer.calculateAddSize()`)

**Same as NEW BUY but with:**
```java
// Reduced confluence multiplier for averaging
confluenceMultiplier = 0.75 (hardcoded)

// Higher fill probability
pFill = 0.95

// Recalculated P(win) and Kelly with new price
pWin = calculatePWin(zonePrice, effectiveFloor, effectiveCeiling, maxDrop)
kelly = calculateKelly(zonePrice, effectiveFloor, effectiveCeiling, pWin)
```

**NO GATES FOR:**
- ❌ Price structure (can add anywhere, including pyramiding)
- ❌ Minimum spacing (2×ATR requirement)
- ❌ Portfolio stress checks

---

### YOUR DOCUMENT (Constitutional Averaging-Only)

**STRUCTURAL GATES (Price-Based):**
```
Gate 1 - Averaging Only (No Pyramids):
  P_new <= P_near

Gate 2 - Minimum Spacing (Anti Death Spiral):
  P_near - P_new >= 2 × ATR

COMBINED:
  P_new <= P_near - 2×ATR

If FAIL → REJECT immediately (no sizing calculation)
```

**Where:**
```
P_near = argmin |P_i - P_mkt|   // Nearest existing entry
```

**Log-Loss Bounds (same as NEW BUY):**
```
Bound A — Per-Leg:
  ℓ_new = ln(S_new / P_new) >= L_max

Bound B — Portfolio Headroom:
  e_new <= (L_port - R_port) / ℓ_new

Bound C — Symbol Headroom:
  e_new <= (L_sym - R_s) / ℓ_new
```

**Final Decision:**
```
ALLOW REBUY if ALL pass:
  1. P_new <= P_near - 2×ATR        // Price gate
  2. ℓ_new >= L_max                  // Per-leg log-loss
  3. e_new > 0                       // Risk budgets
  4. Qty >= 1                        // Tradeable size
```

---

### GAPS IN CURRENT vs DESIRED (REBUY)

| Feature | Current | Desired | Gap |
|---------|---------|---------|-----|
| **Averaging-Only Gate** | ❌ Can add anywhere | ✅ **P_new ≤ P_near** | ❌ **CRITICAL MISSING** |
| **Minimum Spacing** | ❌ None | ✅ **2×ATR requirement** | ❌ **CRITICAL MISSING** |
| **Pyramiding Prevention** | ❌ Allowed | ✅ **Explicitly disallowed** | ❌ **CRITICAL DESIGN GAP** |
| **P_near Detection** | ❌ Not tracked | ✅ **argmin \|P_i - P_mkt\|** | ❌ **Missing logic** |
| **ATR-Based Spacing** | ❌ None | ✅ **2×ATR gate** | ❌ **Missing** |
| **Portfolio Budget Check** | ❌ None | ✅ **(L_port - R_port) / ℓ** | ❌ **CRITICAL MISSING** |
| **Symbol Budget Check** | ❌ None | ✅ **(L_sym - R_s) / ℓ** | ❌ **CRITICAL MISSING** |
| **Confluence Multiplier** | 0.75x hardcoded | Stress-driven velocity | Different approach |

---

## 3. Utility Asymmetry (3× Advantage) - NEW REQUIREMENT

### YOUR DOCUMENT

**Piecewise Utility Function:**
```
U(r) = {
    a × r^α             if r >= 0  (concave profits, α < 1)
    -λ × a × (-r)^β     if r < 0   (convex losses, β > 1)
}

Where:
  α = 0.6   (concave gains)
  β = 1.4   (convex losses)
  λ = 3     (loss weight multiplier)
```

**Two Enforcement Options:**

**Option A - Deterministic 3× (simpler):**
```
U(π_new) >= 3 × |U(ℓ_new)|

Expands to:
  π_new^α >= 3 × (d_new)^β

Where:
  π_new = ln(T_new / P_new)    // Profit log return to target
  d_new = -ℓ_new                // Loss magnitude (positive)
```

**Option B - Expected Utility (signal-aware):**
```
E[U] = p × U(π_new) + (1-p) × U(ℓ_new) >= 0

Expands to:
  p × π_new^α - (1-p) × 3 × (d_new)^β >= 0

Solve for minimum required probability:
  p >= (3 × d_new^β) / (π_new^α + 3 × d_new^β)
```

**CURRENT IMPLEMENTATION:**
- ❌ **NOT IMPLEMENTED AT ALL**
- No utility asymmetry checks
- No 3× advantage validation
- No concave/convex utility modeling

---

## 4. Dynamic Velocity & Stress Management

### YOUR DOCUMENT

**Stress Metric:**
```
stress = clip[0,1](R_port / L_port)

Where:
  R_port = Σ(s,j) e_s,j × ℓ_s,j     // Current portfolio log loss
  L_port = -0.05                      // Portfolio budget (-5%)
```

**Base Velocity (Structure-Driven):**
```
V_base = lookupVelocity(Range_ATR)

Example table:
  Range/ATR < 2.0  → V_base = 1.0
  Range/ATR 2-3    → V_base = 0.8
  Range/ATR 3-5    → V_base = 0.6
  Range/ATR > 5    → V_base = 0.4
```

**Stress Throttle (Convex):**
```
V = V_base × max(V_min, (1 - stress)^γ)

Recommended:
  γ = 2
  V_min = 0.10
```

**Example Impact:**
```
stress = 0.0  → V = V_base × 1.00
stress = 0.3  → V = V_base × 0.49
stress = 0.5  → V = V_base × 0.25
stress = 0.7  → V = V_base × 0.09
stress = 1.0  → V = V_base × 0.10 (floor)
```

**CURRENT IMPLEMENTATION:**
- ❌ **NOT IMPLEMENTED**
- No stress calculation
- No velocity system
- No throttling under portfolio stress
- Fixed confluence multipliers (0.5x to 1.2x) based on signal strength only

---

## 5. BEST OF BOTH WORLDS - Recommended Hybrid

### **What to KEEP from Current:**

✅ **LogUtilityCalculator.java** - Already excellent:
- Per-position log-loss enforcement
- Binary search for max safe qty
- `max_avg_cost = floor × e^(-max_log_loss)` formula
- Well-tested, production-ready

✅ **KellyCalculator.java** - Good foundation:
- P(win) calculation based on zones
- Kelly formula with win/loss ratio
- Fractional Kelly support
- Can be integrated with velocity system

✅ **4 Constraint System** - Good safety layers:
- LOG_SAFE_POSITION
- KELLY_SIZED
- FILL_WEIGHTED
- CAPITAL_AVAILABLE

### **What to ADD from Your Document:**

🔴 **CRITICAL ADDITIONS (Survival Layer):**

1. **Portfolio & Symbol Log-Loss Budgets:**
```java
// New method in LogUtilityCalculator
public static BigDecimal calculateMaxExposureFromBudget(
    BigDecimal portfolioBudget,      // L_port = -0.05
    BigDecimal currentPortfolioRisk,  // R_port (sum of all position risks)
    BigDecimal perLegLogLoss          // ℓ_new for this leg
) {
    BigDecimal headroom = portfolioBudget.subtract(currentPortfolioRisk);
    if (headroom.compareTo(BigDecimal.ZERO) <= 0) {
        return BigDecimal.ZERO;
    }
    // e_max = headroom / ℓ_new
    return headroom.divide(perLegLogLoss, 6, RoundingMode.DOWN);
}
```

2. **Averaging-Only Price Gates:**
```java
// New method in MtfPositionSizer
public static boolean passesAveragingGates(
    BigDecimal newPrice,
    BigDecimal nearestEntry,    // P_near
    BigDecimal atr              // Symbol ATR
) {
    // Gate 1: Must be below nearest entry (no pyramids)
    if (newPrice.compareTo(nearestEntry) > 0) {
        return false;
    }

    // Gate 2: Must be at least 2×ATR below (anti death spiral)
    BigDecimal minSpacing = atr.multiply(new BigDecimal("2"));
    BigDecimal actualSpacing = nearestEntry.subtract(newPrice);

    return actualSpacing.compareTo(minSpacing) >= 0;
}
```

3. **Dynamic Stress & Velocity:**
```java
// New class: StressVelocityCalculator
public static BigDecimal calculateVelocity(
    BigDecimal currentPortfolioLogLoss,
    BigDecimal portfolioBudget,
    BigDecimal rangeOverATR,
    BigDecimal baseVelocity
) {
    // stress = clip[0,1](R_port / L_port)
    BigDecimal stress = currentPortfolioLogLoss
        .divide(portfolioBudget, 6, RoundingMode.HALF_UP)
        .max(BigDecimal.ZERO)
        .min(BigDecimal.ONE);

    // V_base from lookup table
    BigDecimal vBase = lookupVelocity(rangeOverATR);

    // V = V_base × max(0.10, (1 - stress)^2)
    double stressThrottle = Math.pow(1.0 - stress.doubleValue(), 2.0);
    BigDecimal throttle = BigDecimal.valueOf(Math.max(0.10, stressThrottle));

    return vBase.multiply(throttle);
}
```

4. **Utility Asymmetry (3× Advantage):**
```java
// New method in KellyCalculator (or new UtilityCalculator)
public static boolean passes3xAdvantage(
    BigDecimal profitLogReturn,   // π_new = ln(T/P)
    BigDecimal lossLogReturn,     // ℓ_new = ln(S/P)
    BigDecimal alpha,             // 0.6 (concave gains)
    BigDecimal beta,              // 1.4 (convex losses)
    BigDecimal lambda             // 3.0 (loss weight)
) {
    // π_new^α >= λ × |ℓ_new|^β
    double profitUtility = Math.pow(profitLogReturn.doubleValue(), alpha.doubleValue());
    double lossUtility = Math.pow(Math.abs(lossLogReturn.doubleValue()), beta.doubleValue());

    return profitUtility >= lambda.doubleValue() * lossUtility;
}
```

---

## 6. RECOMMENDED HYBRID IMPLEMENTATION PLAN

### Phase 1: Critical Risk Guardrails (MUST HAVE)

**A. Portfolio & Symbol Budgets** (HIGHEST PRIORITY)
```
File: LogUtilityCalculator.java
Add:
  - calculatePortfolioHeadroom(currentRisk, budget, perLegLoss)
  - calculateSymbolHeadroom(symbolRisk, symbolBudget, perLegLoss)

File: MtfPositionSizer.java
Update calculatePositionSize():
  Add two new constraints:
    5. PORTFOLIO_BUDGET
    6. SYMBOL_BUDGET

  finalQty = min(
      LOG_SAFE_POSITION,
      KELLY_SIZED,
      FILL_WEIGHTED,
      CAPITAL_AVAILABLE,
      PORTFOLIO_BUDGET,    // NEW
      SYMBOL_BUDGET        // NEW
  )
```

**B. Averaging-Only Price Gates** (CRITICAL FOR REBUY)
```
File: MtfPositionSizer.java
Update calculateAddSize():

  // STEP 1: Check price gates FIRST (before any sizing)
  if (!passesAveragingGates(zonePrice, nearestEntry, atr)) {
      return REJECTED_RESULT;  // Don't even calculate size
  }

  // STEP 2: Then proceed with sizing (if gates pass)
  ... existing logic ...
```

**C. Nearest Entry Tracking**
```
File: Trade.java (domain model)
Add method:
  - getNearestEntry(currentMarketPrice) → returns P_near

File: ExecutionOrchestrator or TradeService
Track all open legs per symbol:
  - Map<String, List<BigDecimal>> openEntriesBySymbol
```

### Phase 2: Dynamic Velocity System (HIGH VALUE)

**A. Stress Calculation**
```
New File: StressCalculator.java

calculateStress():
  - Query all open trades
  - Sum portfolio log loss: R_port = Σ e × ℓ
  - stress = clip[0,1](R_port / L_port)
  - Return stress [0,1]
```

**B. Velocity Lookup Table**
```
New File: VelocityTable.java

lookupVelocity(rangeOverATR):
  - Range/ATR < 2.0  → 1.0
  - Range/ATR 2-3    → 0.8
  - Range/ATR 3-5    → 0.6
  - Range/ATR > 5    → 0.4

  (Make configurable in mtf_global_config table)
```

**C. Integration**
```
File: MtfPositionSizer.java
Replace:
  confluenceMultiplier

With:
  velocityMultiplier = calculateVelocity(stress, rangeOverATR)

In KELLY_SIZED constraint:
  kelly_qty = (capital × kelly × kelly_fraction × velocityMultiplier × max_kelly_multiplier) / price
```

### Phase 3: Utility Asymmetry (NICE TO HAVE)

**A. Utility Calculator**
```
New File: UtilityCalculator.java

passes3xAdvantage():
  - Calculate π_new (profit log return to target)
  - Calculate ℓ_new (loss log return to stop)
  - Apply utility formula: π^α >= λ × |ℓ|^β
  - Return boolean
```

**B. Integration**
```
File: MtfPositionSizer.java
In calculatePositionSize() and calculateAddSize():

  // Add as pre-check before sizing
  if (!passes3xAdvantage(profitLogReturn, lossLogReturn, 0.6, 1.4, 3.0)) {
      return REJECTED_RESULT;
  }
```

---

## 7. Configuration Schema Extensions

### New Columns for `mtf_global_config`

```sql
-- Portfolio & Symbol Budgets
ALTER TABLE mtf_global_config
ADD COLUMN max_symbol_log_loss NUMERIC(6,4) DEFAULT -0.1000,  -- -10% max per symbol

-- Velocity System
ADD COLUMN velocity_gamma NUMERIC(3,2) DEFAULT 2.00,          -- Stress throttle exponent
ADD COLUMN velocity_min NUMERIC(3,2) DEFAULT 0.10,            -- Minimum velocity (10%)

-- Utility Asymmetry
ADD COLUMN utility_alpha NUMERIC(3,2) DEFAULT 0.60,           -- Profit concavity
ADD COLUMN utility_beta NUMERIC(3,2) DEFAULT 1.40,            -- Loss convexity
ADD COLUMN utility_lambda NUMERIC(3,2) DEFAULT 3.00,          -- Loss weight (3×)

-- Averaging Gates
ADD COLUMN min_reentry_spacing_atr NUMERIC(3,2) DEFAULT 2.00; -- 2×ATR minimum
```

### New Table: `velocity_lookup`

```sql
CREATE TABLE velocity_lookup (
    range_atr_min NUMERIC(4,2) NOT NULL,
    range_atr_max NUMERIC(4,2) NOT NULL,
    velocity_base NUMERIC(4,2) NOT NULL,
    PRIMARY KEY (range_atr_min, range_atr_max)
);

INSERT INTO velocity_lookup VALUES
    (0.00, 2.00, 1.00),
    (2.00, 3.00, 0.80),
    (3.00, 5.00, 0.60),
    (5.00, 999.99, 0.40);
```

---

## 8. Summary: What's Missing vs What's Good

### ✅ CURRENTLY WELL-IMPLEMENTED

1. **Per-Position Log-Loss Enforcement** - LogUtilityCalculator is excellent
2. **Kelly Calculation** - Sound mathematical foundation
3. **4-Constraint Safety Net** - Multiple layers of protection
4. **Binary Search for Max Safe Qty** - Efficient, correct

### ❌ CRITICAL GAPS (MUST FIX)

1. **Portfolio & Symbol Log-Loss Budgets** - Missing entirely (risk blowup)
2. **Averaging-Only Price Gates** - Can pyramid (dangerous)
3. **2×ATR Minimum Spacing** - No anti-death-spiral protection
4. **Dynamic Stress/Velocity System** - No throttling under stress
5. **Nearest Entry Tracking** - No P_near detection

### 🟡 NICE-TO-HAVE ENHANCEMENTS

1. **Utility Asymmetry (3× Advantage)** - Adds extra quality filter
2. **Range/ATR Velocity Lookup** - Market-aware sizing
3. **Configurable Utility Parameters** - α, β, λ tuning

---

## 9. Testing Strategy for Hybrid Implementation

### Unit Tests Required

```java
// LogUtilityCalculator
testPortfolioHeadroomCalculation()
testSymbolHeadroomCalculation()
testZeroHeadroomReturnsZero()

// MtfPositionSizer
testAveragingGateRejectsHigherPrice()
testAveragingGateRejectsTightSpacing()
testPortfolioBudgetConstraint()
testSymbolBudgetConstraint()

// StressCalculator
testStressCalculationWithNoPositions()
testStressCalculationAt50Percent()
testStressClipsAt100Percent()

// VelocityCalculator
testVelocityThrottleAtHighStress()
testVelocityLookupTable()

// UtilityCalculator
test3xAdvantagePass()
test3xAdvantageReject()
```

### Integration Tests

```java
// Scenario: NEW BUY with low stress
testNewBuyWithCleanPortfolio()

// Scenario: REBUY below 2×ATR (should reject)
testRebuyTooCloseToPreviousEntry()

// Scenario: REBUY with portfolio at 80% budget (should throttle)
testRebuyWithHighPortfolioStress()

// Scenario: REBUY exceeding symbol budget (should reject)
testRebuyExceedingSymbolBudget()

// Scenario: Poor 3× advantage (should reject)
testUtilityAsymmetryRejectsWeakSetup()
```

---

## 10. Execution Roadmap

### Week 1: Critical Survival Layer
- [ ] Implement portfolio headroom calculation
- [ ] Implement symbol headroom calculation
- [ ] Add PORTFOLIO_BUDGET constraint
- [ ] Add SYMBOL_BUDGET constraint
- [ ] Unit test all 4 new components

### Week 2: Averaging-Only Gates
- [ ] Implement nearest entry tracking (P_near)
- [ ] Implement 2×ATR spacing check
- [ ] Add price gates to calculateAddSize()
- [ ] Integration test REBUY scenarios
- [ ] Update database schema

### Week 3: Stress & Velocity
- [ ] Implement stress calculation
- [ ] Create velocity lookup table
- [ ] Integrate velocity into Kelly sizing
- [ ] Test velocity throttling under stress
- [ ] Add configuration UI for velocity table

### Week 4: Utility Asymmetry (Optional)
- [ ] Implement utility calculator
- [ ] Add 3× advantage pre-check
- [ ] Make α, β, λ configurable
- [ ] Backtest with/without utility filter
- [ ] Document expected signal reduction

---

## Conclusion

**Your constitutional document is SUPERIOR in risk management structure.**

**Current implementation is GOOD for:**
- Single-position log-loss enforcement
- Kelly-based sizing foundation

**Current implementation LACKS:**
- Portfolio-level risk budgeting (CRITICAL)
- Symbol-level risk budgeting (CRITICAL)
- Averaging-only structural gates (CRITICAL)
- Dynamic stress response (HIGH VALUE)
- Utility asymmetry (NICE-TO-HAVE)

**Recommendation: Hybrid approach keeping current Kelly/log-utility core, adding your constitutional risk layers.**

This gives you:
1. ✅ Proven log-utility math (already working)
2. ✅ Portfolio & symbol budget protection (from your doc)
3. ✅ Averaging-only discipline (from your doc)
4. ✅ Stress-aware throttling (from your doc)
5. ✅ Optional 3× quality filter (from your doc)

**Result: Best of both worlds = Survival-focused, mathematically sound, operationally disciplined.**
