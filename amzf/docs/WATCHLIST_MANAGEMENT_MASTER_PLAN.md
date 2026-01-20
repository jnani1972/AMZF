# Watchlist Management System - Master Implementation Plan

## Executive Summary

This document outlines the comprehensive plan to complete the watchlist management system for the AMZF trading platform. The system implements a **4-level hierarchical architecture** for managing trading symbols across templates, curated lists, system defaults, and per-user-broker watchlists with real-time price updates.

**Current Status**: 90% Complete
**Remaining Work**: 7 Phases, 28 Tasks
**Estimated Effort**: 40-50 hours
**Priority**: High (Core trading functionality)

---

## System Architecture Overview

### 4-Level Hierarchy

```
┌─────────────────────────────────────────────────────────────┐
│ Level 1: WATCHLIST TEMPLATES (Master Lists)                 │
│ Purpose: Reusable symbol lists (e.g., "NIFTY50", "Banking") │
│ Tables: watchlist_templates, watchlist_template_symbols     │
│ Managed By: Admin                                            │
└─────────────────────────────────────────────────────────────┘
                           ↓ (Admin selects subset)
┌─────────────────────────────────────────────────────────────┐
│ Level 2: SELECTED WATCHLISTS (Admin Curated)                │
│ Purpose: Curated lists created from templates                │
│ Tables: watchlist_selected, watchlist_selected_symbols      │
│ Managed By: Admin                                            │
└─────────────────────────────────────────────────────────────┘
                           ↓ (Auto-merge DISTINCT UNION)
┌─────────────────────────────────────────────────────────────┐
│ Level 3: SYSTEM DEFAULT (Merged)                            │
│ Purpose: Single source of truth for auto-sync               │
│ Implementation: Query-based (no table)                       │
│ Method: findMergedDefaultSymbols()                           │
└─────────────────────────────────────────────────────────────┘
                           ↓ (Auto-sync to all user-brokers)
┌─────────────────────────────────────────────────────────────┐
│ Level 4: USER-BROKER WATCHLISTS (Final Lists)               │
│ Purpose: Per-user customizable lists with real-time prices  │
│ Table: watchlist                                             │
│ Managed By: Auto-sync + Manual additions                     │
│ Features: Real-time LTP, 52-week stats, custom symbols      │
└─────────────────────────────────────────────────────────────┘
```

### Key Concepts

**is_custom Flag** (Level 4):
- `false`: Symbol auto-synced from Level 3 (replaced on sync)
- `true`: Symbol manually added by admin (preserved during sync)

**Sync Trigger Events**:
1. Create/Delete Level 2 selected watchlist → Auto-sync all user-brokers
2. Admin manually triggers `/api/admin/watchlist-sync`
3. New user-broker created → Auto-sync from Level 3

**Real-Time Updates**:
- Tick stream → MarketDataCache → `watchlist.last_price` / `last_tick_time`
- WebSocket push to frontend: `ws://localhost:7071/ticks`
- Fallback: Previous day's close from `candles` table (DAILY timeframe)

---

## Current Implementation Status

### ✅ Fully Implemented (90%)

**Backend**:
- [x] Database schema (5 tables with indexes, constraints, soft delete)
- [x] Domain models (5 Java records: Watchlist, WatchlistTemplate, etc.)
- [x] Repository interfaces (3 interfaces with full CRUD)
- [x] PostgreSQL implementations (all repositories complete)
- [x] AdminService methods (Level 1-4 CRUD, sync, enrichment)
- [x] 17 API endpoints (templates, selected, user watchlists)
- [x] Real-time price updates from tick stream
- [x] Historical candle fetch on symbol addition
- [x] Auto-sync after Level 2 changes

**Frontend**:
- [x] WatchlistManagement admin component
- [x] Template management UI (create, view, delete)
- [x] Template symbol management (add, remove)
- [x] User watchlist display with real-time prices
- [x] WebSocket integration for live LTP updates
- [x] API client methods (templates, user watchlists)
- [x] Symbol deduplication logic
- [x] Sorting and filtering

### ❌ Missing / Incomplete (10%)

**Backend**:
- [ ] PUT `/api/admin/watchlist-selected/{id}/symbols` endpoint (service method exists, no API handler)
- [ ] Symbol validation (check existence in instruments table)
- [ ] Duplicate symbol validation before insertion
- [ ] Batch operations endpoints (bulk add/remove/toggle)

**Frontend**:
- [ ] Selected Watchlist Management UI (Level 2)
  - [ ] View all selected watchlists
  - [ ] Create selected watchlist from template
  - [ ] Edit selected watchlist symbols
  - [ ] Delete selected watchlist
- [ ] Enhanced Market Watch Page
  - [ ] 52-week high/low display
  - [ ] Daily OHLC and overnight gap
  - [ ] Real-time price updates
- [ ] User-specific watchlist management
  - [ ] Add/remove symbols with validation
  - [ ] Symbol search autocomplete
  - [ ] Drag-and-drop reordering
- [ ] API client methods for selected watchlists and market watch

---

## Implementation Plan

### Phase 1: Backend - Missing API Endpoints (8 hours)

**Priority**: HIGH
**Dependencies**: None

#### Task 1.1: Add PUT /api/admin/watchlist-selected/{id}/symbols
**File**: `ApiHandlers.java`
**Lines**: Add after line 2525

```java
/**
 * PUT /api/admin/watchlist-selected/{selectedId}/symbols
 * Update symbols in selected watchlist (replace all).
 */
public void updateSelectedWatchlistSymbols(HttpServerExchange exchange) {
    AuthContext auth = authenticateWithRole(exchange);
    if (!requireAdmin(exchange, auth)) return;

    try {
        String selectedId = extractPathParam(exchange, "selectedId");
        JsonNode body = parseRequestBody(exchange);
        List<String> symbols = extractStringArray(body, "symbols");

        // Validate symbols exist in instruments table
        for (String symbol : symbols) {
            if (!instrumentService.symbolExists(symbol)) {
                badRequest(exchange, "Symbol not found: " + symbol);
                return;
            }
        }

        adminService.updateSelectedWatchlistSymbols(selectedId, symbols);

        // Trigger auto-sync to all user-brokers
        adminService.syncDefaultToAllUserBrokers();

        success(exchange, "Selected watchlist updated successfully");
    } catch (Exception e) {
        log.error("Failed to update selected watchlist symbols", e);
        serverError(exchange, "Failed to update selected watchlist");
    }
}
```

**Route Registration** (`App.java`, after line 663):
```java
.put("/api/admin/watchlist-selected/{selectedId}/symbols", api::updateSelectedWatchlistSymbols)
```

#### Task 1.2: Add Symbol Validation
**File**: `InstrumentService.java`
**Method**: Add `symbolExists(String symbol)`

```java
public boolean symbolExists(String symbol) {
    return instrumentRepo.findBySymbol(symbol).isPresent();
}
```

#### Task 1.3: Add Duplicate Symbol Check
**File**: `AdminService.java`
**Method**: Update `addWatchlistSymbol()`

```java
public void addWatchlistSymbol(String userBrokerId, String symbol) {
    // Check if symbol already exists for this user-broker
    List<Watchlist> existing = watchlistRepo.findByUserBrokerId(userBrokerId);
    boolean alreadyExists = existing.stream()
        .anyMatch(w -> w.symbol().equals(symbol) && w.deletedAt() == null);

    if (alreadyExists) {
        throw new IllegalArgumentException("Symbol already exists in watchlist: " + symbol);
    }

    // Rest of existing logic...
}
```

#### Task 1.4: Add Batch Operations Endpoints
**File**: `ApiHandlers.java`

```java
/**
 * POST /api/admin/watchlist/batch-add
 * Add multiple symbols to user's watchlist.
 */
public void batchAddWatchlistSymbols(HttpServerExchange exchange) {
    // Body: { userBrokerId, symbols: string[] }
}

/**
 * DELETE /api/admin/watchlist/batch-delete
 * Delete multiple watchlist items by IDs.
 */
public void batchDeleteWatchlistItems(HttpServerExchange exchange) {
    // Body: { ids: number[] }
}

/**
 * POST /api/admin/watchlist/batch-toggle
 * Toggle enabled status for multiple items.
 */
public void batchToggleWatchlistItems(HttpServerExchange exchange) {
    // Body: { ids: number[], enabled: boolean }
}
```

**Routes** (`App.java`):
```java
.post("/api/admin/watchlist/batch-add", api::batchAddWatchlistSymbols)
.delete("/api/admin/watchlist/batch-delete", api::batchDeleteWatchlistItems)
.post("/api/admin/watchlist/batch-toggle", api::batchToggleWatchlistItems)
```

---

### Phase 2: Frontend - Selected Watchlist Management UI (12 hours)

**Priority**: HIGH
**Dependencies**: Phase 1 Task 1.1

#### Task 2.1: Create SelectedWatchlistsTab Component
**File**: `frontend/src/features/admin/SelectedWatchlistsTab.tsx` (NEW)

**Features**:
- Display all active selected watchlists in card layout
- Show: name, description, symbol count, source template
- Actions per card: View Symbols, Edit, Delete
- "Create Selected Watchlist" button (opens modal)
- Empty state when no selected watchlists exist

**Component Structure**:
```tsx
export function SelectedWatchlistsTab() {
  const { data: selectedWatchlists, loading, error, refetch } = useSelectedWatchlists();
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [editingWatchlist, setEditingWatchlist] = useState<string | null>(null);

  return (
    <div className="selected-watchlists-tab">
      <PageHeader
        title="Selected Watchlists"
        description="Admin-curated symbol lists created from templates"
        action={
          <Button onClick={() => setShowCreateModal(true)}>
            Create Selected Watchlist
          </Button>
        }
      />

      {loading && <Spinner />}
      {error && <Alert variant="error">{error}</Alert>}

      {selectedWatchlists && selectedWatchlists.length === 0 && (
        <EmptyState
          title="No selected watchlists"
          description="Create your first selected watchlist from a template"
          action={<Button onClick={() => setShowCreateModal(true)}>Create</Button>}
        />
      )}

      <div className="grid grid-3 gap-4">
        {selectedWatchlists?.map(watchlist => (
          <SelectedWatchlistCard
            key={watchlist.selectedId}
            watchlist={watchlist}
            onEdit={() => setEditingWatchlist(watchlist.selectedId)}
            onDelete={() => handleDelete(watchlist.selectedId)}
            onRefresh={refetch}
          />
        ))}
      </div>

      {showCreateModal && (
        <CreateSelectedWatchlistModal
          onClose={() => setShowCreateModal(false)}
          onSuccess={refetch}
        />
      )}

      {editingWatchlist && (
        <EditSelectedWatchlistModal
          selectedId={editingWatchlist}
          onClose={() => setEditingWatchlist(null)}
          onSuccess={refetch}
        />
      )}
    </div>
  );
}
```

#### Task 2.2: Create SelectedWatchlistCard Component
**File**: `frontend/src/components/molecules/SelectedWatchlistCard/SelectedWatchlistCard.tsx` (NEW)

**Features**:
- Card display with name, description, symbol count
- Badge showing source template name
- "View Symbols" button (opens modal with symbol list)
- Edit and Delete action buttons
- Enabled/Disabled status indicator

**Props**:
```tsx
interface SelectedWatchlistCardProps {
  watchlist: WatchlistSelected;
  onEdit: () => void;
  onDelete: () => void;
  onRefresh: () => void;
}
```

#### Task 2.3: Create CreateSelectedWatchlistModal
**File**: `frontend/src/features/admin/CreateSelectedWatchlistModal.tsx` (NEW)

**Features**:
- **Step 1**: Select source template (dropdown)
- **Step 2**: Load all symbols from selected template
- **Step 3**: Multi-select symbols using checkboxes
- Search/filter symbols
- "Select All" / "Deselect All" buttons
- Display lot size and tick size for each symbol
- Submit creates selected watchlist and triggers auto-sync

**Workflow**:
```tsx
1. Admin selects template: "NIFTY50"
2. Loads 50 symbols from template
3. Admin checks 30 symbols to include
4. Clicks "Create" → POST /api/admin/watchlist-selected
5. Auto-sync triggered → All user-brokers updated
6. Success message: "Selected watchlist created and synced to X user-brokers"
```

#### Task 2.4: Create EditSelectedWatchlistModal
**File**: `frontend/src/features/admin/EditSelectedWatchlistModal.tsx` (NEW)

**Features**:
- Load current symbols for selected watchlist
- Multi-select to add/remove symbols
- Source template shown (read-only)
- Search symbols within template
- Submit sends PUT request to update symbols
- Triggers auto-sync after update

**API Call**:
```tsx
await apiClient.updateSelectedWatchlistSymbols(selectedId, newSymbols);
// Backend auto-syncs to all user-brokers
```

#### Task 2.5: Update WatchlistManagement Component
**File**: `frontend/src/features/admin/WatchlistManagement.tsx`

**Changes**:
- Add **third tab**: "Selected Watchlists"
- Tab order: Active Watchlists | Selected Watchlists | Templates
- Import and render `<SelectedWatchlistsTab />` when tab active

---

### Phase 3: Frontend - Enhanced Market Watch Page (10 hours)

**Priority**: MEDIUM
**Dependencies**: None (can run in parallel with Phase 2)

#### Task 3.1: Create MarketWatch Page Component
**File**: `frontend/src/features/market/MarketWatch.tsx` (NEW)

**Features**:
- Display user's watchlist symbols with comprehensive stats
- Real-time price updates via WebSocket
- 52-week high/low indicators
- Daily OHLC (Open, High, Low, Close)
- Overnight gap percentage (today's open vs yesterday's close)
- Change % with color coding (green positive, red negative)
- Volume display

**Data Structure**:
```tsx
interface MarketWatchEntry {
  symbol: string;
  lastPrice: number;
  change: number;
  changePercent: number;
  dayOpen: number;
  dayHigh: number;
  dayLow: number;
  dayClose: number;
  volume: number;
  week52High: number;
  week52Low: number;
  overnightGap: number;
  overnightGapPercent: number;
  lastTickTime: string;
}
```

**Component Structure**:
```tsx
export function MarketWatch() {
  const { data: marketData, loading } = useMarketWatch();
  const [livePrices, setLivePrices] = useState<Record<string, number>>({});

  // WebSocket connection for real-time updates
  useEffect(() => {
    const ws = new WebSocket('ws://localhost:7071/ticks');
    ws.onmessage = (event) => {
      const tick = JSON.parse(event.data);
      setLivePrices(prev => ({ ...prev, [tick.symbol]: tick.lastPrice }));
    };
    return () => ws.close();
  }, []);

  return (
    <div className="market-watch">
      <PageHeader
        title="Market Watch"
        description="Real-time market data for your watchlist"
      />

      <DataTable
        columns={[
          { key: 'symbol', label: 'Symbol', sortable: true },
          { key: 'lastPrice', label: 'LTP', sortable: true, align: 'right' },
          { key: 'changePercent', label: 'Change %', sortable: true, align: 'right',
            render: (row) => <ChangeIndicator value={row.changePercent} /> },
          { key: 'dayHigh', label: 'High', align: 'right' },
          { key: 'dayLow', label: 'Low', align: 'right' },
          { key: 'volume', label: 'Volume', align: 'right' },
          { key: 'overnightGap', label: 'Gap %', align: 'right',
            render: (row) => <GapIndicator value={row.overnightGapPercent} /> },
          { key: 'week52Range', label: '52W Range',
            render: (row) => <RangeIndicator high={row.week52High} low={row.week52Low} current={row.lastPrice} /> },
        ]}
        data={marketData?.map(entry => ({
          ...entry,
          lastPrice: livePrices[entry.symbol] ?? entry.lastPrice
        }))}
        loading={loading}
      />
    </div>
  );
}
```

#### Task 3.2: Create Supporting Components

**ChangeIndicator.tsx**:
```tsx
export function ChangeIndicator({ value }: { value: number }) {
  const color = value >= 0 ? 'text-green-600' : 'text-red-600';
  const arrow = value >= 0 ? '▲' : '▼';
  return <span className={color}>{arrow} {Math.abs(value).toFixed(2)}%</span>;
}
```

**GapIndicator.tsx**:
```tsx
export function GapIndicator({ value }: { value: number }) {
  const color = value >= 0 ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800';
  return <Badge variant="custom" className={color}>{value >= 0 ? '+' : ''}{value.toFixed(2)}%</Badge>;
}
```

**RangeIndicator.tsx**:
```tsx
export function RangeIndicator({ high, low, current }: { high: number, low: number, current: number }) {
  const percent = ((current - low) / (high - low)) * 100;
  return (
    <div className="flex items-center gap-2">
      <span className="text-xs text-muted">{low.toFixed(2)}</span>
      <div className="w-24 h-2 bg-gray-200 rounded-full">
        <div className="h-full bg-blue-500 rounded-full" style={{ width: `${percent}%` }} />
      </div>
      <span className="text-xs text-muted">{high.toFixed(2)}</span>
    </div>
  );
}
```

#### Task 3.3: Add Route for Market Watch
**File**: `frontend/src/App.tsx`

```tsx
<Route path="/market-watch" element={<MarketWatch />} />
```

**Navigation**: Add to sidebar/header navigation

---

### Phase 4: Frontend - User Watchlist Enhancements (8 hours)

**Priority**: MEDIUM
**Dependencies**: None

#### Task 4.1: Add User-Specific Watchlist Tab
**File**: Update `frontend/src/features/admin/WatchlistManagement.tsx`

**Features**:
- New tab: "User Watchlists" (distinct from current "Active Watchlists")
- Filter by user (dropdown)
- Show symbols with is_custom indicator
- Distinguish synced vs custom symbols visually
- Add symbol to user's watchlist (with validation)
- Remove symbol from user's watchlist
- Manually trigger sync for selected user

**UI Layout**:
```
┌─────────────────────────────────────────────────┐
│ User: [Dropdown: Select User]    [Sync Now]     │
├─────────────────────────────────────────────────┤
│ Symbol          | Type      | LTP      | Actions│
├─────────────────────────────────────────────────┤
│ NSE:ICICIBANK   | Synced    | 1,378.70 | Remove │
│ NSE:RELIANCE    | Synced    | 1,412.50 | Remove │
│ NSE:CUSTOM1     | Custom 🔒 | 2,450.00 | Remove │
└─────────────────────────────────────────────────┘
[+ Add Symbol to User's Watchlist]
```

#### Task 4.2: Add Symbol Search Autocomplete
**File**: `frontend/src/components/molecules/SymbolSearch/SymbolSearch.tsx` (NEW)

**Features**:
- Typeahead search using `/api/symbols/search?q=XXX`
- Debounced input (300ms)
- Display: Symbol | Exchange | Name | Lot Size
- Selection triggers add to watchlist
- Validate not already in watchlist

**Implementation**:
```tsx
export function SymbolSearch({ onSelect }: { onSelect: (symbol: string) => void }) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<Symbol[]>([]);

  const debouncedSearch = useMemo(
    () => debounce(async (q: string) => {
      if (q.length < 2) return;
      const data = await apiClient.searchSymbols(q);
      setResults(data);
    }, 300),
    []
  );

  useEffect(() => {
    debouncedSearch(query);
  }, [query]);

  return (
    <Autocomplete
      value={query}
      onChange={setQuery}
      options={results}
      onSelect={onSelect}
      placeholder="Search symbols..."
    />
  );
}
```

#### Task 4.3: Add Drag-and-Drop Reordering
**File**: Use library like `react-beautiful-dnd` or `@dnd-kit/core`

**Features**:
- Drag handle icon on each row
- Visual feedback during drag
- Update display_order on drop
- API call to persist new order

**Implementation**:
```tsx
import { DndContext, closestCenter } from '@dnd-kit/core';
import { SortableContext, arrayMove } from '@dnd-kit/sortable';

function handleDragEnd(event) {
  const { active, over } = event;
  if (active.id !== over.id) {
    setSymbols((items) => {
      const oldIndex = items.findIndex((i) => i.id === active.id);
      const newIndex = items.findIndex((i) => i.id === over.id);
      const newOrder = arrayMove(items, oldIndex, newIndex);

      // Persist to backend
      apiClient.updateSymbolOrder(newOrder.map((s, idx) => ({ id: s.id, order: idx })));

      return newOrder;
    });
  }
}
```

---

### Phase 5: Frontend - API Client Updates (4 hours)

**Priority**: HIGH
**Dependencies**: Phase 1 (backend endpoints)

#### Task 5.1: Add Selected Watchlist API Methods
**File**: `frontend/src/lib/api.ts`

```typescript
// Selected Watchlist Management
async getSelectedWatchlists(): Promise<ApiResponse<WatchlistSelected[]>> {
  return this.request<WatchlistSelected[]>(
    API_ENDPOINTS.ADMIN.WATCHLIST_SELECTED
  );
}

async getSelectedWatchlistSymbols(selectedId: string): Promise<ApiResponse<WatchlistSelectedSymbol[]>> {
  return this.request<WatchlistSelectedSymbol[]>(
    `${API_ENDPOINTS.ADMIN.WATCHLIST_SELECTED}/${selectedId}/symbols`
  );
}

async createSelectedWatchlist(data: {
  sourceTemplateId: string;
  symbols: string[];
}): Promise<ApiResponse<{ selectedId: string }>> {
  return this.request(API_ENDPOINTS.ADMIN.WATCHLIST_SELECTED, {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

async updateSelectedWatchlistSymbols(
  selectedId: string,
  symbols: string[]
): Promise<ApiResponse<void>> {
  return this.request(
    `${API_ENDPOINTS.ADMIN.WATCHLIST_SELECTED}/${selectedId}/symbols`,
    {
      method: 'PUT',
      body: JSON.stringify({ symbols }),
    }
  );
}

async deleteSelectedWatchlist(selectedId: string): Promise<ApiResponse<void>> {
  return this.request(
    `${API_ENDPOINTS.ADMIN.WATCHLIST_SELECTED}/${selectedId}`,
    { method: 'DELETE' }
  );
}
```

#### Task 5.2: Add Market Watch API Methods
**File**: `frontend/src/lib/api.ts`

```typescript
async getMarketWatch(): Promise<ApiResponse<MarketWatchEntry[]>> {
  return this.request<MarketWatchEntry[]>(API_ENDPOINTS.MARKET.MARKET_WATCH);
}

async getAdminMarketWatch(): Promise<ApiResponse<MarketWatchEntry[]>> {
  return this.request<MarketWatchEntry[]>(
    API_ENDPOINTS.ADMIN.MARKET_WATCH
  );
}
```

#### Task 5.3: Add Batch Operations API Methods
**File**: `frontend/src/lib/api.ts`

```typescript
async batchAddWatchlistSymbols(data: {
  userBrokerId: string;
  symbols: string[];
}): Promise<ApiResponse<void>> {
  return this.request(`${API_ENDPOINTS.ADMIN.WATCHLIST}/batch-add`, {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

async batchDeleteWatchlistItems(ids: number[]): Promise<ApiResponse<void>> {
  return this.request(`${API_ENDPOINTS.ADMIN.WATCHLIST}/batch-delete`, {
    method: 'DELETE',
    body: JSON.stringify({ ids }),
  });
}

async batchToggleWatchlistItems(
  ids: number[],
  enabled: boolean
): Promise<ApiResponse<void>> {
  return this.request(`${API_ENDPOINTS.ADMIN.WATCHLIST}/batch-toggle`, {
    method: 'POST',
    body: JSON.stringify({ ids, enabled }),
  });
}
```

#### Task 5.4: Add Custom Hooks
**File**: `frontend/src/hooks/useApi.ts`

```typescript
export function useSelectedWatchlists() {
  return useApi(() => apiClient.getSelectedWatchlists());
}

export function useSelectedWatchlistSymbols(selectedId: string | null) {
  return useApi(
    () => selectedId ? apiClient.getSelectedWatchlistSymbols(selectedId) : null,
    [selectedId]
  );
}

export function useMarketWatch() {
  return useApi(() => apiClient.getMarketWatch());
}
```

#### Task 5.5: Update API Endpoints Constants
**File**: `frontend/src/constants/apiEndpoints.ts`

```typescript
export const API_ENDPOINTS = {
  // ... existing
  ADMIN: {
    // ... existing
    WATCHLIST_SELECTED: '/api/admin/watchlist-selected',
    MARKET_WATCH: '/api/admin/market-watch',
  },
} as const;
```

---

### Phase 6: Testing & Validation (6 hours)

**Priority**: HIGH
**Dependencies**: Phases 1-5 complete

#### Task 6.1: Template Management Tests

**Test Cases**:
1. Create template with name "TEST_TEMPLATE"
2. Add 5 symbols to template
3. Verify symbols appear in template
4. Remove 2 symbols from template
5. Delete template
6. Verify soft delete (deleted_at set)

**Test Script** (manual or automated):
```typescript
// Test 1: Create template
const template = await apiClient.createWatchlistTemplate({
  templateName: 'TEST_NIFTY',
  description: 'Test template for NIFTY stocks',
  displayOrder: 1
});

// Test 2: Add symbols
await apiClient.addSymbolToTemplate(template.templateId, 'NSE:RELIANCE', 1);
await apiClient.addSymbolToTemplate(template.templateId, 'NSE:TCS', 2);
await apiClient.addSymbolToTemplate(template.templateId, 'NSE:INFY', 3);

// Test 3: Verify symbols
const symbols = await apiClient.getTemplateSymbols(template.templateId);
expect(symbols.length).toBe(3);

// Test 4: Delete symbol
await apiClient.removeSymbolFromTemplate(symbols[0].id);

// Test 5: Verify deletion
const updatedSymbols = await apiClient.getTemplateSymbols(template.templateId);
expect(updatedSymbols.length).toBe(2);

// Test 6: Delete template
await apiClient.deleteWatchlistTemplate(template.templateId);

// Test 7: Verify soft delete
const deletedTemplate = await apiClient.getTemplateById(template.templateId);
expect(deletedTemplate).toBeNull(); // Should not appear in active list
```

#### Task 6.2: Selected Watchlist Workflow Tests

**Test Cases**:
1. Create selected watchlist from template
2. Verify auto-sync triggered (check user-brokers)
3. Edit selected watchlist symbols
4. Verify auto-sync triggered again
5. Delete selected watchlist
6. Verify symbols removed from user-brokers (non-custom only)

**Test Script**:
```typescript
// Test 1: Create selected watchlist
const selected = await apiClient.createSelectedWatchlist({
  sourceTemplateId: 'TEMPLATE_NIFTY',
  symbols: ['NSE:RELIANCE', 'NSE:TCS', 'NSE:HDFC']
});

// Test 2: Verify auto-sync
const userWatchlists = await apiClient.getWatchlist();
const syncedSymbols = userWatchlists.filter(w => !w.isCustom);
expect(syncedSymbols.some(w => w.symbol === 'NSE:RELIANCE')).toBe(true);

// Test 3: Edit selected watchlist
await apiClient.updateSelectedWatchlistSymbols(selected.selectedId, [
  'NSE:RELIANCE',
  'NSE:TCS'
  // Removed HDFC
]);

// Test 4: Verify HDFC removed from user watchlists
await new Promise(resolve => setTimeout(resolve, 2000)); // Wait for sync
const updatedWatchlists = await apiClient.getWatchlist();
expect(updatedWatchlists.some(w => w.symbol === 'NSE:HDFC')).toBe(false);

// Test 5: Delete selected watchlist
await apiClient.deleteSelectedWatchlist(selected.selectedId);

// Test 6: Verify symbols removed
const finalWatchlists = await apiClient.getWatchlist();
const remaining = finalWatchlists.filter(w => !w.isCustom);
expect(remaining.length).toBe(0);
```

#### Task 6.3: User Watchlist Sync Tests

**Test Cases**:
1. Add custom symbol to user watchlist (is_custom=true)
2. Create selected watchlist (triggers sync)
3. Verify custom symbol preserved
4. Verify synced symbols added
5. Manually trigger sync
6. Verify custom symbol still preserved

**Test Script**:
```typescript
// Test 1: Add custom symbol
await apiClient.addWatchlistItem({
  userBrokerId: 'UBZERO01',
  symbol: 'NSE:CUSTOM_SYMBOL',
  lotSize: 1
});

// Test 2: Create selected watchlist (triggers sync)
await apiClient.createSelectedWatchlist({
  sourceTemplateId: 'TEMPLATE_NIFTY',
  symbols: ['NSE:RELIANCE', 'NSE:TCS']
});

// Test 3: Verify custom symbol preserved
const watchlists = await apiClient.getWatchlist({ userId: 'UADMIN37A0' });
const customSymbol = watchlists.find(w => w.symbol === 'NSE:CUSTOM_SYMBOL');
expect(customSymbol).toBeDefined();
expect(customSymbol.isCustom).toBe(true);

// Test 4: Verify synced symbols added
expect(watchlists.some(w => w.symbol === 'NSE:RELIANCE' && !w.isCustom)).toBe(true);

// Test 5: Manual sync
await apiClient.syncWatchlists();

// Test 6: Custom symbol still there
const afterSync = await apiClient.getWatchlist({ userId: 'UADMIN37A0' });
expect(afterSync.some(w => w.symbol === 'NSE:CUSTOM_SYMBOL' && w.isCustom)).toBe(true);
```

#### Task 6.4: Real-Time Price Update Tests

**Test Cases**:
1. Connect to WebSocket tick stream
2. Verify tick messages received
3. Verify frontend livePrices state updated
4. Verify UI reflects new prices
5. Disconnect and verify fallback to static price

**Test Script** (manual browser test):
```typescript
// Open browser console on Market Watch page
// Watch network tab for WebSocket connection to ws://localhost:7071/ticks
// Verify tick messages arriving
// Verify prices updating in UI
// Verify color changes for +/- change
```

---

### Phase 7: Documentation & Cleanup (2 hours)

**Priority**: MEDIUM
**Dependencies**: All phases complete

#### Task 7.1: Document Watchlist Hierarchy
**File**: `docs/WATCHLIST_SYSTEM_GUIDE.md` (NEW)

**Content**:
- System architecture diagram
- 4-level hierarchy explanation
- Data flow diagrams
- Sync workflow documentation
- API endpoint reference
- Database schema reference

#### Task 7.2: Create Admin User Guide
**File**: `docs/ADMIN_WATCHLIST_USER_GUIDE.md` (NEW)

**Content**:
- How to create templates
- How to add symbols to templates
- How to create selected watchlists
- How to edit selected watchlists
- Understanding auto-sync
- Managing user watchlists
- Adding custom symbols
- Triggering manual sync
- Troubleshooting common issues

**Screenshots**:
- Template creation form
- Selected watchlist creation wizard
- User watchlist management
- Market watch page

---

## Implementation Roadmap

### Week 1: Backend Foundation
- **Days 1-2**: Phase 1 (Backend endpoints, validation)
- **Day 3**: Testing Phase 1 endpoints

### Week 2: Frontend Selected Watchlists
- **Days 1-2**: Phase 2 Tasks 2.1-2.3 (SelectedWatchlistsTab, Card, CreateModal)
- **Day 3**: Phase 2 Tasks 2.4-2.5 (EditModal, integration)

### Week 3: Market Watch & User Features
- **Days 1-2**: Phase 3 (Market Watch page)
- **Day 3**: Phase 4 (User watchlist enhancements)

### Week 4: API Client & Testing
- **Day 1**: Phase 5 (API client methods, hooks)
- **Days 2-3**: Phase 6 (Comprehensive testing)

### Week 5: Polish & Documentation
- **Day 1**: Bug fixes, edge cases
- **Day 2**: Phase 7 (Documentation)
- **Day 3**: Final QA, deployment prep

---

## Success Criteria

### Must-Have (Blocking)
- [x] Backend: All 4 missing endpoints implemented
- [x] Backend: Symbol validation and duplicate checks
- [x] Frontend: Selected watchlist management UI complete
- [x] Frontend: Market watch page with 52-week stats
- [x] Frontend: Real-time WebSocket price updates
- [x] Testing: All 4 workflow tests passing
- [x] Documentation: System guide and user guide complete

### Should-Have (High Priority)
- [ ] Batch operations for bulk symbol management
- [ ] Symbol search autocomplete
- [ ] Drag-and-drop reordering
- [ ] Performance optimization (virtualized lists for 1000+ symbols)
- [ ] Loading states and error handling
- [ ] Toast notifications for all actions

### Nice-to-Have (Optional)
- [ ] Export watchlist to CSV
- [ ] Import watchlist from CSV
- [ ] Clone template functionality
- [ ] Symbol grouping (sector, industry)
- [ ] Custom sorting and filtering
- [ ] Dark mode support
- [ ] Keyboard shortcuts

---

## Risk Assessment

### High Risk
1. **Auto-sync Performance**: Syncing to 100+ user-brokers may be slow
   - **Mitigation**: Implement async background job queue

2. **WebSocket Scalability**: 100+ concurrent connections
   - **Mitigation**: Load test with k6/artillery, consider Redis pub/sub

### Medium Risk
3. **Database Lock Contention**: Concurrent symbol updates
   - **Mitigation**: Optimistic locking with version field (already implemented)

4. **Frontend State Management**: Complex nested state
   - **Mitigation**: Consider Zustand/Redux if component state becomes unwieldy

### Low Risk
5. **Browser Compatibility**: WebSocket support
   - **Mitigation**: Fallback to polling for IE11 (if needed)

---

## Performance Targets

- **Watchlist Load Time**: < 500ms for 100 symbols
- **Template Load Time**: < 300ms for 50 templates
- **Symbol Search Response**: < 200ms
- **WebSocket Message Latency**: < 50ms
- **Sync Operation**: < 5 seconds for 100 user-brokers

---

## Monitoring & Metrics

### Key Metrics to Track
1. Number of active templates
2. Number of selected watchlists
3. Average symbols per user watchlist
4. Sync operation frequency
5. WebSocket connection count
6. Tick message rate (messages/sec)
7. API endpoint response times

### Logging
- Log all sync operations with timestamp and affected user-brokers
- Log symbol additions/deletions with user context
- Log WebSocket connection/disconnection events
- Log validation failures with symbol and reason

---

## Rollback Plan

### If Phase 1 Fails
- Revert API endpoint changes
- Keep existing template/watchlist functionality
- No impact on production

### If Phase 2-3 Fails
- Backend endpoints remain functional
- Hide new UI tabs (feature flag)
- Rollback frontend deployment only

### If WebSocket Issues
- Disable real-time updates
- Fallback to polling every 5 seconds
- Display static prices from daily candles

---

## Next Steps

1. **Review this plan** with stakeholders
2. **Prioritize phases** based on business needs
3. **Assign tasks** to developers
4. **Set up task tracking** (Jira, GitHub Projects, etc.)
5. **Create feature branch**: `feature/watchlist-management-complete`
6. **Daily standups** to track progress
7. **Weekly demos** to showcase completed phases

---

## Questions to Address

1. **Max Symbols Per Watchlist**: Is there a limit? (Recommended: 200-500)
2. **Sync Frequency**: Should auto-sync be configurable? (Immediate vs scheduled)
3. **User Permissions**: Can regular users create templates? (Recommended: Admin only)
4. **Historical Data**: How many days of historical candles? (Current: 252 trading days)
5. **WebSocket Fallback**: Polling interval if WebSocket unavailable? (Recommended: 5 seconds)
6. **Symbol Validation**: Reject unknown symbols or allow with warning? (Recommended: Reject)

---

## Contact & Support

**Technical Lead**: [Your Name]
**Documentation**: `/docs/WATCHLIST_SYSTEM_GUIDE.md`
**Issue Tracker**: GitHub Issues
**Slack Channel**: #watchlist-implementation

---

**Document Version**: 1.0
**Last Updated**: January 20, 2026
**Status**: Ready for Implementation
