# Timezone Migration - Sync Behavior Analysis

## Summary

**Good news**: The sync system will NOT create duplicates. Events are identified by `event_id` (primary key), not timestamps, so the migration is safe.

## How Sync Works

### Event Identification
- Events are identified by `event_id` (primary key), **not by timestamps**
- Both server and Android use `event_id` to match events

### Conflict Resolution
- Server uses tuple comparison: `(version, updated_ts, device_id)`
- Higher tuple wins (lexicographic comparison)
- Room database uses `OnConflictStrategy.REPLACE` - replaces by primary key

## What Happens After Migration

### Scenario: Event with `event_id="abc123"`

**Before Migration:**
- Server: `event_id="abc123"`, `updated_ts=1000`, `start_ts=1000`
- Phone: `event_id="abc123"`, `updated_ts=1000`, `start_ts=1000`

**After Migration (server only):**
- Server: `event_id="abc123"`, `updated_ts=26200` (1000 + 25200), `start_ts=26200`
- Phone: `event_id="abc123"`, `updated_ts=1000`, `start_ts=1000` (unchanged)

### Next Sync: Push (Phone → Server)

1. Phone pushes event with `event_id="abc123"`, `updated_ts=1000`
2. Server looks up existing event by `event_id="abc123"` ✓ Found
3. Server compares:
   - Existing: `(version, 26200, device_id)`
   - Incoming: `(version, 1000, device_id)`
   - Result: `(version, 26200, device_id) > (version, 1000, device_id)` → **Server version wins**
4. Server keeps its corrected timestamps ✓
5. **No duplicate created** ✓

### Next Sync: Pull (Server → Phone)

1. Server sends event with `event_id="abc123"`, `updated_ts=26200`, `start_ts=26200`
2. Phone receives event and calls `upsertEvent()`
3. Room looks up by `event_id="abc123"` ✓ Found
4. Room replaces existing event with server version (due to `OnConflictStrategy.REPLACE`)
5. Phone now has corrected timestamps ✓
6. **No duplicate created** ✓

## Why This Works

1. **Primary Key Matching**: Events are matched by `event_id`, not timestamps
2. **Server Wins After Migration**: Because `updated_ts` is higher on server, server version always wins in conflict resolution
3. **Room Replace Strategy**: Android Room replaces existing events by primary key, not creates duplicates

## Potential Edge Cases

### Edge Case 1: Phone Has Local Changes
If the phone has made local changes (e.g., added a note) after migration but before sync:
- Phone pushes: Server compares tuples, server wins (because `updated_ts` is higher)
- **Result**: Phone's local changes (like notes) would be lost if they weren't synced before migration
- **Mitigation**: Ensure all devices sync before running migration, or manually preserve notes

### Edge Case 2: New Events Created on Phone
If the phone creates new events after migration but before sync:
- New events have new `event_id` values
- They will sync normally (no conflict)
- **No issue**

## Recommendation

1. **Before Migration**: Ensure all devices sync to server (push any local changes)
2. **Run Migration**: Apply timezone fix on server
3. **After Migration**: Devices will pull corrected timestamps on next sync
4. **No duplicates will be created** because events are identified by `event_id`

## Verification

To verify no duplicates after migration:
```sql
-- Check for duplicate event_ids (should return 0)
SELECT event_id, COUNT(*) as count 
FROM events 
GROUP BY event_id 
HAVING COUNT(*) > 1;
```

