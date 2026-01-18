# Gap Analysis - 20250118
**Date:** January 18, 2026
**Scope:** Full Stack Analysis (Backend Layers + Frontend Features)

## 1. Architectural Overview
The AMZF Application implements a **Hexagonal Architecture (Ports & Adapters)** designed for high resilience and auditability. It enforces a strict separation of concerns where the Domain Layer is isolated, and all external interactions are mediated through Ports.

### Layered Dependency Graph
```mermaid
graph TD
    API[Transport Layer<br/>(ApiHandlers)] -->|Calls| App[Application Layer<br/>(Services)]
    App -->|Uses| Domain[Domain Layer<br/>(Models)]
    Infra[Infrastructure Layer<br/>(Persistence/Brokers)] -->|Implements| App
    Infra -->|Uses| Domain
```

---

## 2. Backend Layer Analysis (`src/main/java/in/annupaper`)

### 2.1. Transport Layer (The "Input")
**Duty:** Handles external communication (HTTP/WebSocket), input validation, and user authentication.
**Key Files:**
- `transport/http/ApiHandlers.java`: The massive centralization of all HTTP endpoints.
    - **Input:** JSON payloads (e.g., `LoginRequest`), HTTP Headers (`Authorization`).
    - **Processing:** 
        - Validates JWT tokens using `JwtService`.
        - Marshals JSON to internal DTOs/Entities.
        - Routes requests to specific Services (e.g., `adminService`, `instrumentService`).
    - **Output:** JSON Responses (`ApiResponse`), HTTP Status Codes.
- `transport/websocket/WsSession.java`: Manages stateful WebSocket connections for real-time data pushing.

### 2.2. Application Layer (The "Brain")
**Duty:** Orchestrates domain logic, manages transactions, and coordinates complex flows.
**Key Files:**
- `application/service/SignalManagementServiceImpl.java`:
    - **Duty:** The "Single Source of Truth" for Signal lifecycle.
    - **Input:** `SignalCandidate` (from market data) or manual administrative overrides.
    - **Processing:**
        - **Actor Model:** Uses `EntrySignalCoordinator` to serialize processing per symbol, preventing race conditions.
        - **Deduplication:** Checks against `SignalDeliveryIndex` to ensure no duplicate signals are fired.
        - **Upsert Logic:** Enforces idempotency via DB constraints.
    - **Output:** Persisted `Signal` (Active), `SignalDelivery` (for users), and `SignalEvent` (for WebSocket).
- `application/service/TradeManagementServiceImpl.java`:
    - **Duty:** Manages the state machine of User Trades.
    - **Processing:** Uses `TradeCoordinator` to force sequential updates for each `tradeId`. Handles `PENDING` -> `OPEN` -> `CLOSED` transitions based on Broker updates.

### 2.3. Domain Layer (The "Core")
**Duty:** Defines Pure Business Logic and Data Structures (Records).
**Key Files:**
- `domain/model/Signal.java`: Immutable Record.
    - **Fields:** `symbol`, `direction`, `confluenceScore`, `effectiveFloor`, `effectiveCeiling`.
    - **Duty:** Carries the "DNA" of a trade setup.
- `domain/model/Trade.java`: Immutable Record.
    - **Fields:** `tradeId`, `entryPrice`, `quantity`, `pnl`, `status`.
    - **Duty:** Represents the financial reality of a user's position.
- **Processing:** Domain objects contain validation logic (e.g., `isValid()`) but no side effects (no DB calls).

### 2.4. Infrastructure Layer (The "Output" & "Support")
**Duty:** Implements interfaces defined in Application Layer; talks to "dirty" external worlds (DB, Broker APIs).
**Key Files:**
- `infrastructure/persistence/PostgresSignalRepository.java`:
    - **Duty:** Persist Signals with strict audit trails.
    - **Input:** `Signal` entity.
    - **Processing:**
        - **Versioning:** Implements "Soft Delete + Insert" pattern. Updates increment the `version` column, keeping history of all changes.
        - **Raw JDBC:** Uses `PreparedStatement` for maximum throughput (avoiding ORM overhead).
    - **Output:** SQL `INSERT`/`UPDATE` commands to PostgreSQL.
- `infrastructure/broker/BrokerAdapterFactory.java`:
    - **Duty:** Abstract away the differences between Zerodha, Fyers, and Upstox APIs.

---

## 3. Frontend Critical Analysis (`frontend/src`)

### 3.1. Structure & Organization
The frontend uses a **Feature-Based** directory structure, grouping logic by business domain rather than technical type.

### 3.2. Key Features
- **Admin (`features/admin`)**:
    - **Duty:** System config, User management, Broker monitoring.
    - **Files:** `AdminDashboard.tsx`, `UserManagement.tsx`.
    - **Inputs:** User clicks, Form data.
    - **Processing:** Calls `api.ts` to mutate state on backend.
- **Market Watch (`features/market-watch`)**:
    - **Duty:** Real-time symbol tracking.
    - **Files:** `MarketWatch.tsx`.
    - **Processing:** Subscribes to `TICK` events via WebSocket. Updates React state at 60fps (throttled).

### 3.3. Core Infrastructure (`lib`)
- `lib/api.ts`:
    - **Duty:** REST Client.
    - **Processing:** Centralizes `fetch` calls, adds Auth headers, handles 401/403 errors globally.
- `lib/websocket.ts`:
    - **Duty:** Real-time bi-directional link.
    - **Processing:** Manages heartbeats (`PING`/`PONG`), auto-reconnection, and providing a generic `subscribe()` interface for React components.

---

## 4. Cross-Cutting Concerns & Data Flow

### 4.1. The "Signal" Data Flow
1.  **Ingest**: `TickCandleBuilder` forms a Candle.
2.  **Detect (App)**: `MtfSignalGenerator` scans Candle for setups.
3.  **Coordinate (App)**: `SignalManagementServiceImpl` takes lock on Symbol.
4.  **Persist (Infra)**: `PostgresSignalRepository` writes to DB (`signals` table).
5.  **Broadcast (Transport)**: `WxHub` pushes `SIGNAL_DETECTED` to Frontend.
6.  **Visualize (Frontend)**: `LiveDashboard` flashes green/red row.

### 4.2. Critical Design Decisions
- **No ORM**: The use of raw JDBC in `infrastructure/persistence` indicates a deliberate choice for **Performance** and **Control** over SQL generation, critical for high-frequency trading.
- **Actor Model**: The use of `*Coordinator` classes in the Service layer reveals a **Concurrency Safety** first approach, trading raw parallelism for data integrity guarantees (Single Writer Principle).
- **Versioning**: The repository pattern of `version` increments ensures that no trade/signal state is ever truly lost, providing a forensic-grade audit trail.

---

## 5. Gap Analysis (Startup & Broker Flow)
This section compares User Requirements against Current Implementation to verify alignment.

| Requirement | Implementation | Status | Notes |
| :--- | :--- | :--- | :--- |
| **Admin Login Priority** | `Login.tsx` redirects `isAdmin ? '/admin' : '/dashboard'`. | ✅ **MATCH** | Enforced by `AuthProvider` context. |
| **Broker Roles (Data vs Exec)** | `BrokerRole.java` defines `DATA` and `EXEC`. | ✅ **MATCH** | Strict separation in data structures. |
| **Admin Connects Data Broker** | Admin manually initiates OAuth for Data Broker. | ✅ **MATCH** | Via `BrokerManagement.tsx` -> `FyersLoginOrchestrator`. |
| **Auto-Subscribe on Connect** | Connection trigger auto-subscribes to Watchlist ticks. | ✅ **MATCH** | Logic in `ApiHandlers.reconnectDataBrokerAndSetupTickStream`. |
| **History Lookback from Config** | Backfill logic uses strategy parameters. | ⚠️ **PARTIAL** | `MtfBackfillService` uses **Hardcoded 60 Days** instead of dynamic calculation from `MtfGlobalConfig` params (`htfCandleCount`). |

---

## 6. Key Interaction Flows

### Flow 1: Startup & Broker Connection (The "Ignition")
This flow ensures the system has the necessary data fuel (Ticks & History) to operate.

1.  **Admin Login**: Admin authenticates via `Login.tsx` -> `ApiHandlers`.
2.  **Data Broker Connection**:
    *   **Action**: Admin clicks "Connect" on the Data Broker (e.g., Fyers) in `BrokerManagement.tsx`.
    *   **Process**:
        *   `FyersLoginOrchestrator` generates OAuth URL.
        *   Admin completes login on Broker portal.
        *   Callback hits `ApiHandlers.handleFyersCallback`.
    *   **Trigger**: `reconnectDataBrokerAndSetupTickStream` is executed.
        *   **Subscription**: `BrokerAdapter` subscribes to Real-time Ticks for all enabled Watchlist symbols.
        *   **Backfill**: `MtfBackfillService` immediately runs to fetch Historical Candles (Lookback: ~60 days for HTF) to prime the strategy.

### Flow 2: Signal to Execution
1.  **Detection**: `SignalService` identifies a setup and calls `SMS.onSignalDetected()`.
2.  **Publication**: SMS persists the Signal and creates `SignalDelivery` records for users.
3.  **Delivery**: `ExecutionOrchestrator` picks up the delivery and requests Validation.
4.  **Validation**: `ValidationService` approves the logic constraints.
5.  **Intent**: An approved `TradeIntent` is created.
6.  **Creation**: `TMS` creates a `Trade` in `CREATED` state.
7.  **Order**: TMS sends `OrderRequest` to `BrokerAdapter`.
8.  **Pending**: Trade moves to `PENDING`.

### Flow 3: Real-time Feedback Loop
1.  **Fill**: Broker sends an execution report (Websocket/Postback).
2.  **Ingest**: `BrokerAdapter` normalizes this to `BrokerOrderUpdate`.
3.  **Update**: TMS receives the update. `TradeCoordinator` schedules the task.
4.  **State Change**: Trade moves `PENDING` -> `OPEN`.
5.  **Event**: TMS emits `TRADE_UPDATED` event.
6.  **Broadcast**: `WsHub` pushes this event to the Frontend.
7.  **UI**: `LiveDashboard` hook triggers, updating the P&L display instantly.
