# AnnuPaper Business Process Flow

This document articulates the end-to-end business process of the AnnuPaper trading system (v04), from market data ingestion to trade closure, based on the current codebase architecture.

## 1. High-Level Flow

The system follows a linear, event-driven pipeline:
`Market Data` -> `Signal Detection` -> `Validation (User/Broker)` -> `Trade Intent` -> `Execution` -> `Monitoring` -> `Exit Signal` -> `Exit Execution` -> `Closure`.

---

## 2. Phase-by-Phase Process

### Phase 1: Signal Detection (The "Brain")
*   **Input**: Real-time Tick Data + Historical Candles.
*   **Actor**: `SignalService`, `MtfSignalGenerator`, `ConfluenceCalculator`.
*   **Process**:
    1.  **Tick Ingestion**: `TickCandleBuilder` aggregates ticks into 1-minute candles.
    2.  **MTF Analysis**: `MtfSignalGenerator` runs every minute (scheduler). It triggers `ConfluenceCalculator` to analyze trends across 3 timeframes (HTF, ITF, LTF).
    3.  **Confluence Check**:
        *   Checks alignment of Trends (e.g., all bullish).
        *   Checks Zone overlap (HTF demand zone matched by LTF entry signal).
    4.  **Signal Creation**: If confluence is met, a `SignalCandidate` is generated.
    5.  **Deduplication**: `SignalManagementService` ensures no duplicate signals for the same symbol/zone.
*   **Output**: `Signal` (Status: `PUBLISHED`).

### Phase 2: Fan-Out & Delivery (The "Router")
*   **Actor**: `SignalManagementService`, `SignalDeliveryRepository`.
*   **Process**:
    1.  **Fan-Out**: For a PUBLISHED signal, the system identifies all eligible users/brokers (`UserBroker`).
    2.  **Delivery Creation**: A `SignalDelivery` record is created for each target user-broker (Status: `CREATED`).
    3.  **Notification**: An event (`SIGNAL_DELIVERED`) is emitted to notify the user.

### Phase 3: Validation & Intent (The "Gatekeeper")
*   **Actor**: `ExecutionOrchestrator`, `ValidationService`.
*   **Process**:
    1.  **Polling**: `ExecutionOrchestrator` polls for pending deliveries.
    2.  **Validation**: `ValidationService` checks:
        *   **Risk Rules**: Available capital, max exposure, daily loss limits.
        *   **Broker Constraints**: Margin availability, instrument status.
        *   **Architecture Rules**: Portfolio constraints.
    3.  **Intent Generation**:
        *   **Pass**: Creates `TradeIntent` (Status: `APPROVED`) with calculated quantity and limits.
        *   **Fail**: Creates `TradeIntent` (Status: `REJECTED`) with reasons.
    4.  **Handoff**: Approved intents are strictly forwarded to `TradeManagementService`.

### Phase 4: Entry Execution (The "Actuator")
*   **Actor**: `TradeManagementService` (TMS), `BrokerProvider`.
*   **Process**:
    1.  **Single Writer**: TMS receives the `APPROVED` intent. It is the *sole authority* for creating trades.
    2.  **Trade Creation**: A `Trade` record is created (Status: `CREATED`).
    3.  **Order Placement**: TMS uses `BrokerProvider` to select the correct adapter (Zerodha/Fyers/Dhan) and places the order.
    4.  **State Transition**:
        *   Order Sent -> Trade Status: `PENDING`.
        *   Order Fill ID received -> Updates `Trade` with `brokerOrderId`.

### Phase 5: Fill Reconciliation (The "Reviewer")
*   **Actor**: `PendingOrderReconciler`, `TradeManagementService`.
*   **Process**:
    1.  **Async Updates**: Broker WebSockets send order updates to TMS.
    2.  **Polling Fallback**: `PendingOrderReconciler` periodically checks broker API for status of `PENDING` trades (handling missed packets).
    3.  **Confirmation**:
        *   **Filled**: Trade Status -> `OPEN`. P&L tracking begins.
        *   **Rejected**: Trade Status -> `REJECTED`.

### Phase 6: Trade Monitoring (The "Watchdog")
*   **Actor**: `TradeManagementService`, `ExitOrderProcessor`.
*   **Process**:
    1.  **Tick Monitoring**: Every price tick is checked against open trades.
    2.  **Dynamic Stop Loss**: `TrailingStopsConfigService` logic may update the stop-loss price if the trade moves favorably.
    3.  **P&L Calculation**: Unrealized P&L is updated in real-time for dashboarding.

### Phase 7: Exit Logic (The "Symmetry")
*   **Actor**: `SignalManagementService` (Exit Coordinator), `ExitQualificationService`.
*   **Process**:
    1.  **Trigger**: Exit logic is triggered by:
        *   **Price**: Hit Target or Stop Loss.
        *   **Signal**: `ExitSignalService` detects an opposing technical signal (e.g., Momentum loss).
        *   **Time**: Max holding period exceeded.
    2.  **Exit Detection**: `SignalManagementService.onExitDetected()` is called.
    3.  **Qualification**: `ExitQualificationService` verifies if the exit is valid (checks "Episode" cooldowns to prevent double-exiting).
    4.  **Exit Signal**: If valid, an `ExitSignal` and `ExitIntent` (Status: `APPROVED`) are created.

### Phase 8: Exit Execution & Closure
*   **Actor**: `ExitOrderProcessor`, `ExitOrderExecutionService`, `TradeManagementService`.
*   **Process**:
    1.  **Processor**: `ExitOrderProcessor` polls for `APPROVED` exit intents.
    2.  **Execution**: `ExitOrderExecutionService` places the exit order at the broker.
    3.  **Reconciliation**: `ExitOrderReconciler` confirms the fill.
    4.  **Closure**: `TradeManagementService` closes the trade:
        *   Status -> `CLOSED`.
        *   Realized P&L is calculated and frozen.
        *   Holding days and log returns are recorded.

---

## Architecture Principles Applied
*   **Single Writer**: `TradeManagementService` is the only service allowed to write to the `User_Trades` table.
*   **Ports & Adapters**: Brokers are accessed only via `BrokerProvider` and `BrokerAdapter` interface, never directly.
*   **Event-Driven Handoffs**: Components are decoupled. (e.g., Signal Service doesn't know about Trade Service; it just passes the baton via intents).
