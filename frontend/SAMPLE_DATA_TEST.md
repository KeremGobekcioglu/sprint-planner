# Sample Data Loading - Testing Guide

## Issue Fixed
The "Load Samples" button now properly loads sample data by:
1. Calling `loadSampleData()` which stores data in localStorage
2. Using `queryClient.invalidateQueries()` to refresh React Query cache
3. This forces the UI to re-fetch and display the new data

## How to Test

### Test 1: Load Sample Teams
1. Open browser DevTools (F12)
2. Go to Team Setup page
3. Click the "⚡ Load Samples" button
4. Check browser console for logs:
   - ✅ Loaded 2 sample teams: PayFlow Team, ShopHub Team
   - ✅ Loaded 10 sample developers
5. The two sample teams should appear below in the teams list

### Test 2: Load Sample Epics
1. Select "PayFlow Team" (or "ShopHub Team")
2. Click "Go to Planning"
3. Click the "⚡ Load Samples" button next to "Epics"
4. The epics text field should auto-fill with 5 domain-specific epics
5. Sprint goal should also auto-fill
6. Ready to click "Generate Plan"

### Test 3: Verify Data Persistence
After loading samples:
1. Open DevTools → Application → Local Storage
2. Look for key: `mock-teams` and `mock-developers`
3. Should see JSON with PayFlow Team, ShopHub Team, and 10 developers

### Test 4: Idempotency
1. Click "Load Samples" again
2. Console should show: "ℹ️ All sample teams already loaded"
3. No duplicate teams should be created

## Data Structure

### Sample Teams
- **PayFlow Team** (Fintech, 10 day sprint)
  - 5 developers, velocity 5-13
- **ShopHub Team** (E-Commerce, 14 day sprint)
  - 5 developers, velocity 6-11

### Sample Epics (Domain-Specific)
- **PayFlow**: Multi-currency, fraud detection, settlements, webhooks, compliance
- **ShopHub**: Inventory, recommendations, checkout, reviews, analytics

## Troubleshooting

If button still doesn't work:
1. Check browser console (F12) for error messages
2. Verify localStorage is enabled in browser
3. Ensure `USE_REAL_API` is not set to 'true' in environment
4. Clear browser cache and try again
