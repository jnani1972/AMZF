# Missing Backend APIs - To Be Implemented

This document lists all backend API endpoints that need to be implemented to support the complete CRUD operations in the frontend admin pages.

## ✅ Frontend Status: COMPLETE

All frontend UI components and API client methods are now implemented and ready. The frontend will make API calls to the endpoints listed below. Once the backend implements these endpoints, the full CRUD functionality will work end-to-end.

## Current Status

✅ **Implemented** - Backend API exists and works
⚠️ **Frontend Ready** - Frontend implemented, backend API needed
❌ **Missing** - Backend API needs to be implemented

---

## 1. Broker Management APIs

### ✅ Implemented
- `GET /api/admin/brokers` - Get all user brokers
- `POST /api/admin/brokers` - Create user broker connection
- `POST /api/admin/brokers/:id/toggle` - Toggle broker enabled status
- `DELETE /api/admin/brokers/:id` - Delete broker connection

### ⚠️ Frontend Ready (Backend Needed)
- `PUT /api/admin/brokers/:id` - **Update broker connection**
  - **Purpose**: Update broker role (DATA/EXEC) and enabled status
  - **Frontend Method**: `apiClient.updateUserBroker(userBrokerId, { role, enabled })`
  - **Frontend Location**: `BrokerManagement.tsx:181-197`
  - **Request Body**:
    ```json
    {
      "role": "DATA" | "EXEC",
      "enabled": boolean
    }
    ```
  - **Response**: Updated UserBroker object
  - **Status**: ✅ Frontend complete, waiting for backend API

---

## 2. Portfolio Management APIs

### ✅ Implemented
- `GET /api/admin/portfolios` - Get all portfolios
- `POST /api/admin/portfolios` - Create portfolio

### ⚠️ Frontend Ready (Backend Needed)
- `PUT /api/admin/portfolios/:id` - **Update portfolio**
  - **Purpose**: Update portfolio name and capital
  - **Frontend Method**: `apiClient.updatePortfolio(portfolioId, { name, capital })`
  - **Frontend Location**: `PortfolioManagement.tsx:160-179`
  - **Request Body**:
    ```json
    {
      "name": "string",
      "capital": number
    }
    ```
  - **Response**: Updated Portfolio object
  - **Status**: ✅ Frontend complete, waiting for backend API

- `DELETE /api/admin/portfolios/:id` - **Delete portfolio**
  - **Purpose**: Delete a portfolio (soft delete recommended)
  - **Frontend Method**: `apiClient.deletePortfolio(portfolioId)`
  - **Frontend Location**: `PortfolioManagement.tsx:184-195`
  - **Response**: Success/error message
  - **Important**: Should validate no active trades before deletion
  - **Status**: ✅ Frontend complete, waiting for backend API

---

## 3. Watchlist Management APIs

### ✅ Implemented
- `GET /api/admin/watchlist` - Get all watchlist items
- `POST /api/admin/watchlist` - Add watchlist item
- `POST /api/admin/watchlist/:id/toggle` - Toggle enabled status
- `DELETE /api/admin/watchlist/:id` - Delete watchlist item

### ⚠️ Frontend Ready (Backend Needed)
- `PUT /api/admin/watchlist/:id` - **Update watchlist item**
  - **Purpose**: Update lot size, tick size, and enabled status
  - **Frontend Method**: `apiClient.updateWatchlistItem(id, { lotSize, tickSize, enabled })`
  - **Frontend Location**: `WatchlistManagement.tsx:185-202`
  - **Request Body**:
    ```json
    {
      "lotSize": number | null,
      "tickSize": number | null,
      "enabled": boolean
    }
    ```
  - **Response**: Updated WatchlistItem object
  - **Status**: ✅ Frontend complete, waiting for backend API

---

## 4. User Management APIs

### ✅ Implemented
- `GET /api/admin/users` - Get all users
- `PUT /api/admin/users/:id` - Update user (displayName, role)
- `POST /api/admin/users/:id/toggle-status` - Toggle user status with reason
- `DELETE /api/admin/users/:id` - Delete user

**Note**: User management already has complete CRUD - no missing APIs! ✅

---

## 🚀 Priority Implementation List (Backend Team)

All frontend code is complete and ready. Implement these backend endpoints in order of priority:

### 🔴 High Priority (Required for Core Functionality)
1. ⚠️ **PUT /api/admin/portfolios/:id** - Portfolio editing is critical for capital management
   - Frontend: ✅ Ready at `api.ts:579-590`
   - UI Handler: ✅ Ready at `PortfolioManagement.tsx:160-179`

2. ⚠️ **DELETE /api/admin/portfolios/:id** - Portfolio deletion needed for cleanup
   - Frontend: ✅ Ready at `api.ts:593-599`
   - UI Handler: ✅ Ready at `PortfolioManagement.tsx:184-195`

3. ⚠️ **PUT /api/admin/watchlist/:id** - Watchlist item editing needed for configuration
   - Frontend: ✅ Ready at `api.ts:643-655`
   - UI Handler: ✅ Ready at `WatchlistManagement.tsx:185-202`

### 🟡 Medium Priority (Important for Flexibility)
4. ⚠️ **PUT /api/admin/brokers/:id** - Full broker update (currently using toggle)
   - Frontend: ✅ Ready at `api.ts:507-521`
   - UI Handler: ✅ Ready at `BrokerManagement.tsx:181-197`

---

## API Implementation Guidelines

### Request/Response Format
All APIs should follow this structure:

**Success Response:**
```json
{
  "success": true,
  "data": {...},
  "message": "Operation completed successfully"
}
```

**Error Response:**
```json
{
  "success": false,
  "error": "Error message",
  "message": "User-friendly error description"
}
```

### Authentication
All admin APIs require:
- `Authorization: Bearer <JWT_TOKEN>` header
- User role must be `ADMIN`

### Validation
- Validate all input data
- Check user permissions
- Verify resource ownership
- Return appropriate HTTP status codes:
  - 200: Success
  - 201: Created
  - 400: Bad Request
  - 401: Unauthorized
  - 403: Forbidden
  - 404: Not Found
  - 500: Server Error

---

## Frontend Implementation Notes

### Broker Management
- Edit modal: `BrokerManagement.tsx:172-196`
- Currently uses toggle endpoint - should use full update when available

### Portfolio Management
- Edit modal: `PortfolioManagement.tsx:149-171`
- Delete handler: `PortfolioManagement.tsx:176-183`
- Both show alerts that APIs are not implemented

### Watchlist Management
- Edit modal: `WatchlistManagement.tsx:176-192`
- Shows alert that update API is not implemented

---

## Testing Checklist (Once APIs Are Implemented)

### For Each Endpoint:
- [ ] Test with valid data
- [ ] Test with invalid data (validation)
- [ ] Test with unauthorized user (non-admin)
- [ ] Test with missing/malformed JWT token
- [ ] Test with non-existent resource ID (404)
- [ ] Test concurrent updates (race conditions)
- [ ] Test database transaction rollback on errors

### Frontend Integration Testing:
- [ ] Verify modal opens correctly
- [ ] Verify form validation works
- [ ] Verify success message appears
- [ ] Verify table refreshes with new data
- [ ] Verify error messages display correctly
- [ ] Verify modal closes after success
- [ ] Verify loading states appear

---

## Example API Implementation (Portfolio Update)

```java
@PutMapping("/admin/portfolios/{id}")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<?> updatePortfolio(
    @PathVariable String id,
    @RequestBody UpdatePortfolioRequest request,
    @AuthenticationPrincipal UserDetails userDetails
) {
    try {
        // Validate input
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "error", "Portfolio name is required"));
        }

        if (request.getCapital() != null && request.getCapital() <= 0) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "error", "Capital must be positive"));
        }

        // Find portfolio
        Portfolio portfolio = portfolioRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found"));

        // Update fields
        if (request.getName() != null) {
            portfolio.setName(request.getName());
        }
        if (request.getCapital() != null) {
            portfolio.setCapital(request.getCapital());
        }

        // Save
        Portfolio updated = portfolioRepository.save(portfolio);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", updated,
            "message", "Portfolio updated successfully"
        ));

    } catch (Exception e) {
        return ResponseEntity.status(500)
            .body(Map.of("success", false, "error", e.getMessage()));
    }
}
```

---

## Contact

For questions about these API requirements, please contact the frontend development team.

---

## 📊 Summary

| Component | Frontend UI | API Client | Backend API | Status |
|-----------|-------------|------------|-------------|---------|
| **User Management** | ✅ Complete | ✅ Complete | ✅ Complete | 🟢 **100% Working** |
| **Broker Management** | ✅ Complete | ✅ Complete | ⚠️ 75% | 🟡 **Waiting for PUT** |
| **Portfolio Management** | ✅ Complete | ✅ Complete | ⚠️ 50% | 🟡 **Waiting for PUT/DELETE** |
| **Watchlist Management** | ✅ Complete | ✅ Complete | ⚠️ 75% | 🟡 **Waiting for PUT** |

---

## ✅ What's Complete

1. **All Frontend UI** ✅
   - Create, Edit, View, Delete modals for all pages
   - Sortable tables with latest-on-top default
   - Table height reduced by 8px
   - Proper error handling and validation
   - Loading states and success messages

2. **All API Client Methods** ✅
   - `updateUserBroker()` - Added at api.ts:507
   - `updatePortfolio()` - Added at api.ts:579
   - `deletePortfolio()` - Added at api.ts:593
   - `updateWatchlistItem()` - Added at api.ts:643

3. **All UI Handlers** ✅
   - BrokerManagement edit handler - Updated
   - PortfolioManagement edit/delete handlers - Updated
   - WatchlistManagement edit handler - Updated

---

## ⚠️ What's Needed (Backend Only)

### 4 Backend API Endpoints to Implement:
1. `PUT /api/admin/user-brokers/:id` - Update broker role/enabled
2. `PUT /api/admin/portfolios/:id` - Update portfolio name/capital
3. `DELETE /api/admin/portfolios/:id` - Delete portfolio
4. `PUT /api/admin/watchlist/:id` - Update watchlist item

**Estimated Backend Work**: 2-4 hours (all 4 endpoints)

---

## 🎯 Next Steps

### For Backend Team:
1. ✅ Review this document and the example implementation
2. 🔴 Implement the 3 High Priority endpoints first
3. 🟡 Then implement the Medium Priority endpoint
4. ✅ Test each endpoint with the frontend
5. ✅ Deploy to staging

### For Frontend Team:
1. ✅ **COMPLETE** - All frontend work is done!
2. ⏸️ Waiting for backend API implementation
3. 🧪 Test full CRUD flows once backend is ready
4. 📝 Update this document when backend is complete

---

**Last Updated**: January 2026
**Document Version**: 2.0 (Frontend Complete)
**Status**: ⚠️ Frontend Ready, Waiting for Backend
