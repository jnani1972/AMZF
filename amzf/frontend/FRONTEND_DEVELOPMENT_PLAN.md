# AMZF Frontend Development Plan
## Comprehensive Frontend-Backend Sync Strategy

**Last Updated:** 2026-01-17
**Status:** In Progress
**CONSTITUTION:** Only reusable components | All CSS in separate files | No inline styles

---

## Table of Contents
1. [Backend API Audit](#backend-api-audit)
2. [Component Architecture](#component-architecture)
3. [CSS Organization](#css-organization)
4. [Missing Functionality](#missing-functionality)
5. [Implementation Roadmap](#implementation-roadmap)

---

## Backend API Audit

### ✅ Implemented APIs (Full Frontend Support)

#### Authentication
- ✅ `POST /api/auth/login` - Login.tsx
- ✅ `POST /api/auth/register` - Register.tsx
- ✅ `GET /api/bootstrap` - AuthProvider.tsx

#### User & Portfolio
- ✅ `GET /api/user` - useAuth hook
- ✅ `GET /api/portfolios` - Portfolio.tsx
- ✅ `GET /api/portfolios/{id}` - Portfolio.tsx

#### Trading Signals
- ✅ `GET /api/signals` - Dashboard.tsx
- ✅ `GET /api/signals/{id}` - Dashboard.tsx
- ✅ `POST /api/trade-intents` - Dashboard.tsx

#### Trades
- ✅ `GET /api/trades` - Orders.tsx, Dashboard.tsx
- ✅ `GET /api/trades/{id}` - Orders.tsx
- ✅ `POST /api/trades/{id}/close` - Orders.tsx

#### Orders
- ✅ `POST /api/orders` - Orders.tsx
- ✅ `GET /api/orders` - Orders.tsx
- ✅ `POST /api/orders/{id}/cancel` - Orders.tsx

#### Market Data
- ✅ `GET /api/market-watch` - MarketWatch.tsx
- ✅ `GET /api/quotes/{symbol}` - MarketWatch.tsx
- ✅ `GET /api/symbols/search` - MarketWatch.tsx

#### Admin - Users
- ✅ `GET /api/admin/users` - UserManagement.tsx
- ✅ `PUT /api/admin/users/{id}` - UserManagement.tsx
- ✅ `POST /api/admin/users/{id}/toggle` - UserManagement.tsx
- ✅ `DELETE /api/admin/users/{id}` - UserManagement.tsx

#### Admin - Brokers
- ✅ `GET /api/admin/user-brokers` - BrokerManagement.tsx
- ✅ `POST /api/admin/user-brokers` - BrokerManagement.tsx
- ✅ `DELETE /api/admin/user-brokers/{id}` - BrokerManagement.tsx
- ✅ `POST /api/admin/user-brokers/{id}/toggle` - BrokerManagement.tsx

#### Admin - Portfolios
- ✅ `GET /api/admin/portfolios` - PortfolioManagement.tsx
- ✅ `POST /api/admin/portfolios` - PortfolioManagement.tsx

### ⚠️ Partially Implemented APIs (Limited Frontend Support)

#### Watchlist (User-level) - **NEEDS FRONTEND**
- ⚠️ `GET /api/watchlists` - API exists, NO UI
- ⚠️ `POST /api/watchlists` - API exists, NO UI
- ⚠️ `PUT /api/watchlists/{id}` - API exists, NO UI
- ⚠️ `DELETE /api/watchlists/{id}` - API exists, NO UI

#### Admin - Watchlist (Symbol-level only)
- ⚠️ `GET /api/admin/watchlist` - WatchlistManagement.tsx (only symbols, not full watchlists)
- ⚠️ `POST /api/admin/watchlist` - WatchlistManagement.tsx (add symbol only)
- ⚠️ `DELETE /api/admin/watchlist/{id}` - WatchlistManagement.tsx
- ⚠️ `POST /api/admin/watchlist/{id}/toggle` - WatchlistManagement.tsx

#### Broker Management
- ⚠️ `POST /api/admin/brokers/{id}/test-connection` - API exists, NO UI
- ⚠️ `POST /api/admin/brokers/{id}/disconnect` - API exists, NO UI
- ⚠️ `GET /api/admin/brokers/{id}/session` - API exists, NO UI
- ⚠️ `GET /api/admin/brokers/{id}/oauth-url` - API exists, NO UI

#### MTF Configuration
- ⚠️ `GET /api/mtf-config` - API exists, NO UI
- ⚠️ `PUT /api/mtf-config` - API exists, NO UI
- ⚠️ `GET /api/mtf-config/global` - API exists, NO UI (admin)
- ⚠️ `PUT /api/mtf-config/global` - API exists, NO UI (admin)

#### Data Broker
- ⚠️ `GET /api/admin/data-broker` - API exists, NO UI
- ⚠️ `POST /api/admin/data-broker` - API exists, NO UI

#### System Health
- ⚠️ `GET /api/health` - API exists, NO UI

### ❌ Missing Backend APIs (Need Implementation)

#### Admin - Enhanced Watchlist Management
- ❌ `POST /api/admin/watchlists` - Create named watchlist
- ❌ `PUT /api/admin/watchlists/{id}` - Update watchlist name/symbols
- ❌ `DELETE /api/admin/watchlists/{id}` - Delete entire watchlist
- ❌ `GET /api/admin/watchlists` - Get all user watchlists
- ❌ `POST /api/admin/watchlists/{id}/symbols` - Add symbol to watchlist
- ❌ `DELETE /api/admin/watchlists/{id}/symbols/{symbol}` - Remove symbol from watchlist
- ❌ `POST /api/admin/watchlists/{id}/assign` - Assign watchlist to user
- ❌ `DELETE /api/admin/watchlists/{id}/unassign` - Unassign watchlist from user

#### Admin - Portfolio Management (CRUD missing)
- ❌ `PUT /api/admin/portfolios/{id}` - Update portfolio details
- ❌ `DELETE /api/admin/portfolios/{id}` - Delete portfolio
- ❌ `POST /api/admin/portfolios/{id}/add-capital` - Add capital
- ❌ `POST /api/admin/portfolios/{id}/withdraw-capital` - Withdraw capital

#### Admin - User History
- ❌ `GET /api/admin/users/{id}/status-history` - Get status change history
- ❌ `GET /api/admin/users/{id}/activity` - Get user activity log

#### Admin - System Monitoring
- ❌ `GET /api/admin/metrics/overview` - System metrics dashboard
- ❌ `GET /api/admin/metrics/brokers` - Broker health metrics
- ❌ `GET /api/admin/metrics/trades` - Trading metrics
- ❌ `GET /api/admin/logs` - System logs
- ❌ `GET /api/admin/events` - Event stream for monitoring

#### User Settings
- ❌ `GET /api/user/settings` - Get user preferences
- ❌ `PUT /api/user/settings` - Update user preferences
- ❌ `PUT /api/user/password` - Change password
- ❌ `PUT /api/user/profile` - Update profile

---

## Component Architecture

### CONSTITUTION: Component Development Rules

1. **NO duplicate components** - If similar functionality exists, extend it
2. **ALL components must be reusable** - Generic props, no hardcoded values
3. **NO inline styles** - All styling via CSS files
4. **Atomic Design Pattern** - atoms → molecules → organisms → templates

### Current Component Library

#### ✅ Atoms (Fully Reusable)
```
/components/atoms/
  ├── Button/          ✅ Variants: primary, secondary, ghost, buy, sell
  ├── Input/           ✅ Types: text, number, search, password
  ├── Badge/           ✅ Variants: success, error, warning, info, profit, loss
  ├── Text/            ✅ Typography variants
  ├── Card/            ✅ Container component
  ├── Spinner/         ✅ Loading indicator
  ├── Alert/           ✅ Notification component
  └── (All following CONSTITUTION)
```

#### ✅ Molecules (Fully Reusable)
```
/components/molecules/
  ├── EmptyState/      ✅ Empty state with icon + CTA
  ├── BrokerStatusBadge/ ✅ Broker health indicator
  └── (All following CONSTITUTION)
```

#### ✅ Organisms (Fully Reusable)
```
/components/organisms/
  ├── Header/          ✅ Top navigation (legacy - being replaced)
  ├── Sidebar/         ✅ NEW: Left sidebar navigation
  ├── PageHeader/      ✅ NEW: Consistent page headers
  ├── SummaryCards/    ✅ NEW: 4-column metric grids
  ├── DataTable/       ✅ NEW: Generic data tables
  ├── SlideModal/      ✅ NEW: Notification-style modals
  └── MetricsGrid/     ✅ Metric cards layout
```

### ⚠️ Components Needing Refactoring (Not Following CONSTITUTION)

#### Features with Inline CSS or Non-Reusable Code
```
/features/
  ├── admin/
  │   ├── AdminDashboard.tsx      ⚠️ Needs PageHeader
  │   ├── Settings.tsx            ⚠️ Needs PageHeader
  │   ├── WatchlistManagement.tsx ⚠️ Needs complete rewrite
  │   └── (Others refactored ✅)
  ├── auth/
  │   ├── Login.tsx               ⚠️ Modal not using SlideModal
  │   └── Register.tsx            ⚠️ Modal not using SlideModal
  ├── dashboard/
  │   └── Dashboard.tsx           ⚠️ Check for inline styles
  ├── market-watch/
  │   └── MarketWatch.tsx         ⚠️ Check for inline styles
  └── portfolio/
      └── Portfolio.tsx           ⚠️ Check for inline styles
```

### 🆕 Components to Create (Following CONSTITUTION)

#### Priority 1: Admin Features
```
/components/organisms/
  ├── WatchlistManager/         - CRUD for watchlists (not just symbols)
  │   ├── WatchlistManager.tsx
  │   └── WatchlistManager.css
  ├── SymbolPicker/             - Search & select symbols
  │   ├── SymbolPicker.tsx
  │   └── SymbolPicker.css
  ├── UserSelector/             - User dropdown with search
  │   ├── UserSelector.tsx
  │   └── UserSelector.css
  ├── BrokerConnectionPanel/    - Broker OAuth flow UI
  │   ├── BrokerConnectionPanel.tsx
  │   └── BrokerConnectionPanel.css
  ├── MTFConfigPanel/           - MTF settings UI
  │   ├── MTFConfigPanel.tsx
  │   └── MTFConfigPanel.css
  └── SystemMetricsPanel/       - Admin dashboard metrics
      ├── SystemMetricsPanel.tsx
      └── SystemMetricsPanel.css
```

#### Priority 2: User Features
```
/components/organisms/
  ├── OrderEntryPanel/          - Order placement form
  ├── TradeHistoryPanel/        - Trade history table
  ├── PortfolioSummaryPanel/    - Portfolio overview
  └── SignalIntentPanel/        - Create trade intents from signals
```

#### Priority 3: Shared Utilities
```
/components/molecules/
  ├── SearchInput/              - Autocomplete search
  ├── DateRangePicker/          - Date range selection
  ├── StatusIndicator/          - Generic status badge
  └── ConfirmDialog/            - Reusable confirmation modal
```

---

## CSS Organization

### Current Structure (Needs Consolidation)
```
/styles/
  ├── theme.css                 ✅ CSS variables + utilities
  ├── index.css                 ✅ Global styles
  └── globals.css               ⚠️ May have duplicates

/components/
  ├── atoms/*/[Component].css   ✅ Component-specific
  ├── molecules/*/[Component].css ✅ Component-specific
  └── organisms/*/[Component].css ✅ Component-specific
```

### Target Structure (CONSTITUTION Compliant)
```
/styles/
  ├── theme.css                 - CSS variables + theme tokens
  ├── index.css                 - Global resets + base styles
  ├── utilities.css             - NEW: Utility classes
  ├── layout.css                - NEW: Layout utilities
  └── animations.css            - NEW: Animation classes

/components/atoms/*/[Component].css     - Scoped component styles
/components/molecules/*/[Component].css - Scoped component styles
/components/organisms/*/[Component].css - Scoped component styles
```

### ✅ Completed CSS Refactoring
- Extracted all inline styles from:
  - SummaryCards → `.summary-grid`
  - SlideModal → `.modal-slide-right`
  - Admin pages → `.page-transition`
  - All modals → Removed inline positioning

### ⚠️ Remaining CSS Tasks
1. Extract remaining inline styles from:
   - Login/Register modals
   - Dashboard.tsx
   - MarketWatch.tsx
   - Portfolio.tsx
   - Orders.tsx

2. Create utility classes for common patterns:
   - Form layouts → `.form-spacing`, `.form-actions`
   - Grid layouts → `.grid-2`, `.grid-3`, `.grid-4`
   - Flex layouts → `.flex-center`, `.flex-between`, `.flex-start`
   - Spacing → `.gap-sm`, `.gap-md`, `.gap-lg`

3. Create layout.css for:
   - Page containers → `.page-container`, `.content-area`
   - Sidebar layouts → `.sidebar-layout`, `.main-content`
   - Responsive breakpoints

---

## Missing Functionality

### High Priority (Backend API Needed)

#### 1. Comprehensive Watchlist Management
**Status:** Backend API MISSING
**User Request:** "add create watchlist, edit watchlist, delete watchlist, Add/delete symbol to watchlist, add/delete watchlist to user, add/delete symbol to user"

**Required Backend APIs:**
```typescript
POST   /api/admin/watchlists              // Create named watchlist
PUT    /api/admin/watchlists/{id}         // Update watchlist
DELETE /api/admin/watchlists/{id}         // Delete watchlist
GET    /api/admin/watchlists              // Get all watchlists
POST   /api/admin/watchlists/{id}/symbols // Add symbol
DELETE /api/admin/watchlists/{id}/symbols/{symbol} // Remove symbol
POST   /api/admin/watchlists/{id}/assign  // Assign to user
DELETE /api/admin/watchlists/{id}/unassign // Unassign from user
```

**Frontend Components Needed:**
- `WatchlistManager.tsx` - Main CRUD interface
- `SymbolPicker.tsx` - Symbol search & selection
- `UserSelector.tsx` - User assignment

#### 2. MTF Configuration UI
**Status:** Backend API EXISTS, Frontend UI MISSING

**Required Frontend Components:**
- `MTFConfigPanel.tsx` - Settings interface
- Uses existing APIs:
  - `GET /api/mtf-config` (user-level)
  - `PUT /api/mtf-config` (user-level)
  - `GET /api/mtf-config/global` (admin)
  - `PUT /api/mtf-config/global` (admin)

#### 3. Broker Connection Management
**Status:** Backend API EXISTS, Frontend UI INCOMPLETE

**Existing APIs:**
```typescript
POST /api/admin/brokers/{id}/test-connection ✅
POST /api/admin/brokers/{id}/disconnect      ✅
GET  /api/admin/brokers/{id}/session         ✅
GET  /api/admin/brokers/{id}/oauth-url       ✅
```

**Frontend Components Needed:**
- `BrokerConnectionPanel.tsx` - OAuth flow + status
- Update `BrokerManagement.tsx` to use panel

#### 4. Portfolio CRUD (Admin)
**Status:** Backend API PARTIAL (Create exists, Update/Delete missing)

**Missing Backend APIs:**
```typescript
PUT    /api/admin/portfolios/{id}              // Update portfolio
DELETE /api/admin/portfolios/{id}              // Delete portfolio
POST   /api/admin/portfolios/{id}/add-capital  // Add capital
POST   /api/admin/portfolios/{id}/withdraw     // Withdraw capital
```

**Frontend Updates Needed:**
- Add Edit/Delete buttons to `PortfolioManagement.tsx`
- Create `PortfolioEditModal.tsx` component

### Medium Priority (Backend API Needed)

#### 5. User Settings Page
**Status:** Backend API MISSING, Frontend Page EMPTY

**Required Backend APIs:**
```typescript
GET /api/user/settings      // Get preferences
PUT /api/user/settings      // Update preferences
PUT /api/user/password      // Change password
PUT /api/user/profile       // Update profile
```

**Frontend Components Needed:**
- `UserSettingsPanel.tsx` - Preferences UI
- `PasswordChangePanel.tsx` - Password form
- `ProfileEditPanel.tsx` - Profile editor

#### 6. System Monitoring Dashboard
**Status:** Backend API MISSING

**Required Backend APIs:**
```typescript
GET /api/admin/metrics/overview  // System overview
GET /api/admin/metrics/brokers   // Broker health
GET /api/admin/metrics/trades    // Trading metrics
GET /api/admin/logs              // System logs
GET /api/admin/events            // Event stream
```

**Frontend Components Needed:**
- `SystemMetricsPanel.tsx` - Metrics dashboard
- `EventLogPanel.tsx` - Real-time events
- `SystemHealthPanel.tsx` - Health indicators

### Low Priority (Enhancement)

#### 7. User Activity History
**Status:** Backend API MISSING

**Required Backend APIs:**
```typescript
GET /api/admin/users/{id}/status-history  // Status changes
GET /api/admin/users/{id}/activity        // Activity log
```

**Frontend Updates:**
- Enhance `UserManagement.tsx` history modal
- Currently shows placeholder data

---

## Implementation Roadmap

### Phase 1: CSS Consolidation (Week 1)
**Goal:** Eliminate ALL inline styles, organize CSS files

**Tasks:**
1. ✅ Create utility classes in `theme.css`
   - ✅ `.summary-grid`
   - ✅ `.modal-slide-right` variants
   - ✅ `.page-transition`

2. ⏳ Create `utilities.css`
   - Form utilities
   - Grid/Flex utilities
   - Spacing utilities

3. ⏳ Create `layout.css`
   - Page container styles
   - Sidebar layout (260px left, flex-1 content)
   - Responsive breakpoints

4. ⏳ Audit & extract inline styles from:
   - Login.tsx → Form styles
   - Register.tsx → Form styles
   - Dashboard.tsx → Layout styles
   - MarketWatch.tsx → Table styles
   - Portfolio.tsx → Card styles
   - Orders.tsx → Table styles

### Phase 2: Sidebar Layout Adjustments (Week 1)
**Goal:** Proper sizing for new left sidebar design

**Tasks:**
1. ⏳ Update content area width
   - Add left margin: 260px
   - Ensure responsive behavior

2. ⏳ Adjust page containers
   - Update padding/margins
   - Fix table widths
   - Adjust modal positions

3. ⏳ Test all admin pages
   - UserManagement
   - BrokerManagement
   - PortfolioManagement
   - WatchlistManagement
   - Settings
   - AdminDashboard

### Phase 3: Reusable Component Library (Week 2)
**Goal:** Create missing reusable components

**Tasks:**
1. Create shared molecules:
   - `SearchInput.tsx` - Autocomplete search
   - `DateRangePicker.tsx` - Date selection
   - `ConfirmDialog.tsx` - Confirmation modal
   - `StatusIndicator.tsx` - Status badge

2. Create admin organisms:
   - `SymbolPicker.tsx` - Symbol selection
   - `UserSelector.tsx` - User dropdown
   - `PortfolioEditModal.tsx` - Portfolio editor

### Phase 4: Watchlist Management (Week 2-3)
**Goal:** Implement comprehensive watchlist CRUD

**Tasks:**
1. ⚠️ **BACKEND:** Implement missing APIs
   - `POST /api/admin/watchlists`
   - `PUT /api/admin/watchlists/{id}`
   - `DELETE /api/admin/watchlists/{id}`
   - `GET /api/admin/watchlists`
   - Symbol add/remove endpoints
   - User assignment endpoints

2. Create `WatchlistManager.tsx`:
   - List all watchlists
   - Create watchlist modal
   - Edit watchlist modal
   - Delete confirmation
   - Symbol management panel
   - User assignment panel

3. Update API client:
   - Add new watchlist methods
   - Update types

### Phase 5: MTF & Broker Configuration (Week 3-4)
**Goal:** Implement configuration UIs for existing APIs

**Tasks:**
1. Create `MTFConfigPanel.tsx`:
   - Timeframe configuration
   - Confluence settings
   - Signal parameters
   - Global vs user-level config

2. Create `BrokerConnectionPanel.tsx`:
   - OAuth flow UI
   - Test connection button
   - Session info display
   - Disconnect button

3. Update admin pages:
   - Add MTF config page
   - Enhance BrokerManagement

### Phase 6: Portfolio & User Management (Week 4-5)
**Goal:** Complete CRUD for portfolios and user settings

**Tasks:**
1. ⚠️ **BACKEND:** Implement missing APIs
   - `PUT /api/admin/portfolios/{id}`
   - `DELETE /api/admin/portfolios/{id}`
   - Capital add/withdraw endpoints
   - User settings endpoints

2. Enhance `PortfolioManagement.tsx`:
   - Edit portfolio modal
   - Delete confirmation
   - Capital management

3. Create user settings page:
   - Settings form
   - Password change
   - Profile editor

### Phase 7: System Monitoring (Week 5-6)
**Goal:** Admin dashboard with real-time metrics

**Tasks:**
1. ⚠️ **BACKEND:** Implement monitoring APIs
   - System metrics endpoints
   - Logs endpoint
   - Event stream

2. Create monitoring components:
   - `SystemMetricsPanel.tsx`
   - `EventLogPanel.tsx`
   - `SystemHealthPanel.tsx`

3. Enhance `AdminDashboard.tsx`:
   - Add metrics panels
   - Real-time updates via WebSocket

### Phase 8: Testing & Documentation (Week 6)
**Goal:** Ensure quality and maintainability

**Tasks:**
1. Component testing:
   - Unit tests for all atoms
   - Integration tests for organisms

2. Documentation:
   - Storybook stories for all components
   - Component usage examples
   - API integration guide

3. Performance optimization:
   - Code splitting
   - Bundle size analysis
   - Lazy loading

---

## Success Metrics

### Code Quality
- ✅ Zero inline styles (CONSTITUTION)
- ✅ 100% reusable components (CONSTITUTION)
- ✅ All CSS in separate files (CONSTITUTION)
- ⏳ TypeScript coverage > 95%
- ⏳ Component test coverage > 80%

### User Experience
- ✅ Sidebar navigation (260px left)
- ⏳ Responsive design (mobile/tablet/desktop)
- ⏳ Smooth page transitions
- ⏳ Consistent component styling

### Backend Sync
- ✅ 60% APIs implemented
- ⏳ 85% APIs with frontend UI (target)
- ⏳ 100% critical APIs implemented

---

## Notes for Backend Team

### Critical Missing APIs (Blocking Frontend)
1. **Watchlist CRUD** - Highest priority
2. **Portfolio Update/Delete** - High priority
3. **User Settings** - Medium priority
4. **System Monitoring** - Medium priority

### API Design Requests
1. Consistent response format:
   ```typescript
   { success: boolean, data?: T, error?: string }
   ```

2. Pagination support for large datasets:
   ```typescript
   { data: T[], total: number, page: number, limit: number }
   ```

3. WebSocket events for real-time updates:
   - Broker connection status
   - System health
   - Trade updates

---

## CONSTITUTION Reminders

**Every developer MUST follow:**
1. ❌ **NO inline styles** - Use CSS files only
2. ❌ **NO duplicate components** - Extend existing ones
3. ✅ **ONLY reusable components** - Generic, configurable
4. ✅ **ALL CSS in /styles/** - Organized, documented
5. ✅ **Component-scoped CSS** - Co-located with components

**Code Review Checklist:**
- [ ] No `style={{}}` in JSX
- [ ] Component has reusable props
- [ ] CSS file exists in same folder
- [ ] No hardcoded values (use props/theme)
- [ ] Component documented with JSDoc
- [ ] Storybook story created

---

**End of Development Plan**
