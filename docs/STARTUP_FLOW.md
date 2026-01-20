# AnnuPaper Application Startup & Broker Connection Flow

## 1. System Boot
- **Entry Point**: `in.annupaper.bootstrap.App.main()`
- **Configuration Loading**: Loads environment variables (DB credentials, API ports).
- **Database Migration**: Flyway runs SQL migrations to ensure schema is up-to-date.
- **Service Initialization**:
  - `UserBrokerRepository`, `BrokerRepository` initialized.
  - `BrokerOAuthService` initialized (injecting repositories).
  - `ApiHandlers` initialized with `oauthService`.
- **Server Start**: Undertow HTTP server starts on port `9090`.

## 2. Route Registration
The following key routes are registered for Broker Connectivity:
- `GET /api/admin/brokers/{userBrokerId}/oauth-url`: Generates broker-specific login URL.
- `GET /zerodha/callback`: **(NEW)** Handles Zerodha OAuth redirects.
- `GET /api/admin/brokers/oauth-callback`: Handles Fyers OAuth redirects.

## 3. Automation & Schedulers
- **Startup Auto-Login**: **DISABLED**
  - Previously, the app attempted to auto-connect brokers on startup.
  - *Current Behavior*: The app starts cleanly without initiating any broker connections. Users must manually connect via the UI.
- **Historic Data Reconciliation**: Runs on a schedule (if critical data is missing).
- **Trading Engine**: Initializes but waits for broker connection to receive live ticks.

## 4. Manual Broker Connection Flow (User Initiated)

### A. The Trigger
1. User logs into **Admin Dashboard**.
2. Navigates to **Brokers** tab.
3. Clicks **"Connect"** button on a disconnected broker (e.g., Zerodha).

### B. The Handshake
1. **Frontend**: Calls `GET /api/admin/brokers/{id}/oauth-url`.
2. **Backend**: `BrokerOAuthService` determines the broker type (Zerodha/Fyers).
   - Generates the vendor-specific OAuth URL (e.g., `kite.trade/connect/login?api_key=...`).
3. **Frontend**: Redirects browser to the generated URL.

### C. Authentication (Vendor Side)
1. User logs in at the Broker's page (Zerodha/Fyers).
2. User authorizes the application.

### D. The Callback (Critical Path)
1. **Broker Redirect**: Broker redirects user back to the registered callback URL.
   - *Zerodha*: `http://localhost:9090/zerodha/callback?request_token=...&status=success`
   - *Fyers*: `http://localhost:9090/api/admin/brokers/oauth-callback?auth_code=...`
2. **Backend Processing** (`ApiHandlers.java`):
   - Captures the token/code.
   - **Validation**: Checks `status=success`.
   - **Exchange**: Calls `BrokerOAuthService.handleOAuthCallback()`.
     - Validates UserBroker.
     - Exchanges `request_token` for `access_token` (SHA256 checksum validation for Zerodha).
     - Creates/Updates `UserBrokerSession` in DB.
3. **Reconnection**:
   - `reconnectDataBrokerAndSetupTickStream(id)` is triggered immediately.
   - The system initializes the WebSocket connection to the broker using the new session.

### E. Completion
1. **Redirect**: Backend redirects browser to `/admin/dashboard?status=connected`.
2. **UI Update**: Dashboard refreshes, showing the broker status as **CONNECTED**.

## 5. Failure Modes & Recovery
- **Invalid Token**: Logs error, redirects to dashboard with `error` param.
- **Duplicate Session**: Old session is revoked, new session takes precedence.
- **Connection Loss**: System attempts auto-reconnect using valid session. If session expired, status becomes DISCONNECTED, requiring manual re-auth.
