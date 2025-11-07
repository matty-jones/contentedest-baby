# Progress Log

Date: 2025-09-19

## Completed

- Server (FastAPI): pairing endpoint, sync push/pull, conflict resolution; tests passing.
- Android: Gradle/Compose/Hilt/Room scaffold; entities/DAOs; repository; domain rules (sleep classification, stats) with tests.
- UI: Minimal Daily Log screen and ViewModel wiring via Hilt.

## Next Up

- Android networking: Retrofit client, auth token storage, and pairing flow.
- Sync worker: schedule LAN-only background sync; integrate with server endpoints.
- Daily Log: quick-add actions, edit and delete with undo.
- Exports and reports: CSV/JSON export; report generation stub.

## Notes

- All timestamps are UTC seconds. Sleep classification per PRD (19:00–07:00 and ≥2h => Night).
- Conflict rule: (version, updated_ts, device_id).

## Recent Fixes

- Fixed server_clock issue: Seeded events now have proper server_clock values (1-4193) so they can be synced to Android app via /sync/pull endpoint.
- Fixed Android authentication: Added auth interceptor to OkHttp client to automatically include Bearer token in API requests.
- Fixed Android pairing persistence: Updated MainActivity to properly check pairing state on app startup.
- Fixed Android startup sync: Added sync trigger for already paired devices on app startup to pull existing events.
 - Configured Android networking base URL and cleartext (2025-10-06):
   - Added `BuildConfig.BASE_URL` in `android/app/build.gradle.kts` pointing to LAN host (`http://192.168.86.3:8005/`).
   - Updated `NetworkModule.provideRetrofit()` to use `BuildConfig.BASE_URL` and ensure trailing slash.
   - Allowed cleartext HTTP for `192.168.86.3` in `res/xml/network_security_config.xml` (Android 9+).
   - Scanned codebase for hardcoded URLs; consolidated root HTTP calls under Retrofit with the single base URL.
 - Updated server URL to port 8005 (2025-10-10):
   - Changed `BASE_URL` in all build types (default, debug, release) from port 8088 to 8005 to match server configuration.
   - Server accessible at `http://192.168.86.3:8005/` on local network workstation.
- Added debug logging to SyncWorker and EventRepository to trace sync flow and identify where events are being lost.
- Fixed SyncWorker constructor: Changed from @AssistedInject to proper HiltWorker constructor to fix WorkManager instantiation error.
- Fixed SyncWorker params reference: Changed params.inputData to inputData after constructor change.
- Fixed SyncWorker constructor: Added back @AssistedInject and @Assisted annotations as required by @HiltWorker annotation.
- Fixed SyncWorker constructor pattern: Reverted to correct @AssistedInject with @Assisted annotations as required by @HiltWorker annotation.

### Android UI Timeline Fix (2025-10-04)

- **Problem**: Build failed with unresolved reference `nativeCanvas` and wrong property `id` in `SnakeTimeline.kt`.
- **Solution**:
  - Added missing import `androidx.compose.ui.graphics.nativeCanvas`.
  - Replaced `it.event.id` with `it.event.event_id` when grouping segments.
- **Impact**: Android module now compiles successfully; timeline labels render via `drawIntoCanvas` without removing functionality.

### Snake Timeline Rendering Fix (2025-10-04)

- **Problem**: Snake timeline displayed no events despite repository returning events for the selected day.
- **Root Cause**: Bridging cap logic used `geom.rowCenters[(i % rows) + 1]`, which could index out-of-bounds or produce invalid Y positions depending on segment sorting, disrupting draw state.
- **Solution**:
  - Group segments by `event_id` (already fixed earlier).
  - Render bridge caps using each segment's rect centerY for Y positions instead of indexing `rowCenters`.
- **Files Updated**: `android/app/src/main/java/com/contentedest/baby/ui/timeline/SnakeTimeline.kt`
- **Result**: Timeline renders segments reliably. Build passes.

### Timeline Layout Fix (2025-10-04)

- **Problem**: Timeline content appeared as a 1px strip at the top; the canvas had effectively zero/incorrect height due to being inside a `verticalScroll` container.
- **Solution**: Removed `verticalScroll` wrapper and gave the timeline container `weight(1f)` to occupy remaining screen space under the header.
- **Files Updated**: `android/app/src/main/java/com/contentedest/baby/ui/timeline/TimelineScreen.kt`
- **Result**: `SnakeTimeline` receives a finite height and renders full-size.

### Live Event Add (2025-10-05)

- Added FloatingActionButton to `TimelineScreen` to start live event creation.
- Implemented `LiveEventPickerSheet` for selecting Sleep, Feeding, or Diaper.
- Implemented `LiveSleepDialog`: play/pause timer, details (Crib/Arms/Stroller), shows start/end/duration, saves a sleep span.
- Implemented `LiveFeedDialog`: two play/pause controls (Left/Right) with exclusive activation, builds segments list, shows totals, saves breast feed with segments.
- Implemented `LiveNappyDialog`: quick selection of Wet/Dirty/Mixed, saves point event at current time.
- Added repository helpers `insertSleepSpan` and `insertBreastFeed` to persist live events and segments.
- Wired `deviceId` from `MainActivity` into `TimelineScreen` and down to dialogs for correct attribution.
- All changes compile and pass lints locally.

### Snake Timeline Subtle Rounding (2025-10-06)

- **Problem**: Event end-caps scaled with bar size, appearing too large or too small depending on event length/height.
- **Solution**:
  - Render event segments with a fixed, slight corner radius (4dp) and draw only the visible bar height (exclude hit slop) to keep rounding consistent.
  - Render connectors with flat ends (stroke cap Butt) to avoid oversized rounded caps.
- **Files Updated**: `android/app/src/main/java/com/contentedest/baby/ui/timeline/SnakeTimeline.kt`
- **Result**: Events now have a subtle, consistent rounding regardless of size, improving visual consistency.

### Connector Arc Tangents (2025-10-06)

- **Problem**: Connector quadratic curves met the track at ~45°/135°; with flat caps this looked misaligned.
- **Solution**: Replaced quadratic curve with a true semicircular arc between row centers so end tangents are horizontal (0°/180°), keeping flat caps.
- **Files Updated**: `android/app/src/main/java/com/contentedest/baby/ui/timeline/SnakeTimeline.kt`
- **Result**: Connectors now visually integrate with the track; end-caps align cleanly with the path.

### Connector Quadratic + Plugs (2025-10-06)

- **Problem**: True semicircle arc fixed cap tangents but no longer matched the snake's quadratic curve.
- **Solution**: Restored quadratic connector to match the path and added short rectangular overlays at both row ends to create horizontal end caps and remove gaps.
- **Files Updated**: `android/app/src/main/java/com/contentedest/baby/ui/timeline/SnakeTimeline.kt`
- **Result**: Retains the space-efficient quadratic curve while achieving clean 0°/180° visual end-caps with no gaps.

### Connector Wedge Seam Fill (2025-10-10)

- **Problem**: Rectangular plugs left visible seams because the connector begins curving immediately off the edge.
- **Solution**: Replaced plugs with wedge-shaped fills computed from the connector tangent at each end to perfectly fill the triangular gap between the horizontal event bar and the quadratic path.
- **Files Updated**: `android/app/src/main/java/com/contentedest/baby/ui/timeline/SnakeTimeline.kt`
- **Result**: Seamless join at both ends; no visible 45°/135° seams while preserving the matching quadratic curve and flat caps.

### Right-edge Wedge Fix + Overdraw (2025-10-10)

- **Problem**: Wedge computation assumed left edge; right-edge connectors still showed seam lines, plus hairline gaps on some densities.
- **Solution**: Determined edge side per connector and over-drew wedge triangles slightly into the event bar (±0.75dp) to eliminate subpixel gaps.
- **Files Updated**: `android/app/src/main/java/com/contentedest/baby/ui/timeline/SnakeTimeline.kt`
- **Result**: Clean joins on both left and right edges across densities; gaps removed.

### Wedge Tangent Correction (2025-10-10)

- **Problem**: Overdraw reintroduced seams on the left; tangent not evaluated correctly at both ends.
- **Solution**: Removed overdraw and computed wedges using true quadratic tangents: t=0 → 2(cp−start); t=1 → 2(end−cp). Applies symmetrically to left/right edges.
- **Files Updated**: `android/app/src/main/java/com/contentedest/baby/ui/timeline/SnakeTimeline.kt`
- **Result**: Seam fills align with the connector direction on both sides without overdraw.

### Wedge Robustness Fix (2025-10-10)

- **Problem**: One wedge sometimes failed to render per side due to winding/self-intersection in the quad fill.
- **Solution**: Changed each wedge into two simple triangles (top and bottom) meeting at the center point, using the correct tangent-normal at each end.
- **Files Updated**: `android/app/src/main/java/com/contentedest/baby/ui/timeline/SnakeTimeline.kt`
- **Result**: All four cases render consistently (left/right, top/bottom) with no missing wedges.

### Start-Top / End-Bottom Wedge Logic (2025-10-10)

- **Problem**: Right-side start and left-side end were still wrong; triangles were joining the incorrect edges (left/top vs right/top, etc.).
- **Solution**: Compute curve top/bottom via tangent normal and only draw the start TOP triangle and the end BOTTOM triangle, which matches the four required cases across both sides.
- **Files Updated**: `android/app/src/main/java/com/contentedest/baby/ui/timeline/SnakeTimeline.kt`
- **Result**: Clean joins for right-start top and left-end bottom, matching the snake curve and flat caps.

## Tooling

- Added `query_db.py` at repo root to inspect SQLite data used by the server/Android app.
  - Usage examples:
    - List recent events (default DB path):
      - `./query_db.py events --limit 50 --desc`
    - Filter by type and time window (ISO or epoch):
      - `./query_db.py events --type sleep --since 2025-09-01 --until 2025-09-30 --json`
    - Point at a specific DB file:
      - `./query_db.py --db-path /home/blasky/Projects/contentedest-baby/server/data.db counts`
    - List devices:
      - `./query_db.py devices --enabled`
  - Honors `TCB_DB_PATH` environment variable (same as server). Prints counts and DB URL.

- Added `migrate_database.py` to import a CSV and force-overwrite the current SQLite database.
  - Destructive: drops and recreates tables, deduplicates rows by (Date, Start, End, Type, Details, Raw_Text)
  - Maps Type values to server types (sleep|feed|nappy) and preserves `Details` into the `details` column
  - Usage:
    - `. server/.venv/bin/activate`
    - `export TCB_DB_PATH=/home/blasky/Projects/contentedest-baby/server/data.db  # optional`
    - `./migrate_database.py /abs/path/to/your.csv --device-id seed_device`
    - `./query_db.py counts` to verify

## Database Schema Update (2025-09-29)

- **Problem**: CSV data had 1,527 duplicate records out of 4,311 total rows, and the `Details` field was not being stored in the database.
- **Solution**: 
  - Created `migrate_database.py` script that wipes and recreates the database with unique records only
  - Added `details` field to both server and Android schemas to store the Details column from CSV
  - Updated server models, schemas, and API endpoints to handle the new field
  - Updated Android Room entities, DAOs, and repository to match the new schema
  - Database version bumped from 1 to 2 for Android Room
- **Results**: 
  - Database now contains 2,673 unique events (down from 4,193 with duplicates)
  - Details field properly stores values like "Crib", "SNOO", "L", "R", "Wet", etc.
  - No more duplicate data issues
  - Both server and Android app now handle the complete CSV schema

## Android UI Fix (2025-09-29)

- **Problem**: Feed events showed "Type: Feed (Unknown)" and missing details like "L&R*" in the Android UI
- **Root Cause**: 
  - Server was correctly sending `details: "L&R*"` in EventDTO
  - Android conversion logic was only looking in `payload` field for feed mode
  - UI was only checking `feed_mode` field, not the `details` field
- **Solution**:
  - Updated `EventRepository.toEntity()` to check `details` field first, then fall back to payload
  - Updated `formatEventType()` in both TimelineScreen and DailyLogScreen to use `details` field when `feed_mode` is null
  - Now properly displays "Type: Feed (Breast)" and "Details: L&R*" for synced events
- **Files Updated**: EventRepository.kt, TimelineScreen.kt, DailyLogScreen.kt

## Debug Menu Addition (2025-10-04)

- **Problem**: User experiencing 401 Unauthorized errors and no data in app, suspected local database was cleared and needed way to force re-pairing and re-sync.
- **Root Cause**: 
  - Server has device "test" in database but Android app token may be invalid/expired
  - Auth interceptor clears token storage on 401 responses, but no easy way to manually trigger re-pairing
  - No immediate sync option for debugging
- **Solution**:
  - Added debug section to StatisticsScreen with "Force Re-pair" and "Force Sync" buttons
  - Force Re-pair: Clears token storage and returns to pairing screen
  - Force Sync: Triggers immediate sync using new `SyncWorker.triggerImmediateSync()` method
  - Added `triggerImmediateSync()` method to SyncWorker for one-time sync operations
  - Debug options only appear when functions are provided (clean UI for production)
- **Files Updated**: StatisticsScreen.kt, MainActivity.kt, SyncWorker.kt
- **Usage**: Access via menu (three dots) → Statistics → Debug Options section

### Bottom Navigation + New Pages (2025-10-12)

- Added bottom navigation bar with three tabs: Timeline, Growth, Nursery.
- Implemented `GrowthScreen` placeholder; future plots/statistics will live here.
- Implemented `NurseryScreen` with Media3 ExoPlayer RTSP playback.
- Derive RTSP URL from `BuildConfig.BASE_URL` host: `rtsp://<host>:8554/stream`.
- Refactored `MainActivity` to use `Scaffold` with `TopAppBar` and `NavigationBar`.
- Preserved full-screen Timeline by giving it `Modifier.fillMaxSize()` within Scaffold content and avoiding scroll containers.
- Added dependencies: `media3-exoplayer`, `media3-exoplayer-rtsp`, `media3-ui`, and `material-icons-extended`.
- All modified files pass lints.

### Removed Pairing System (2025-10-10)

- **Rationale**: Pairing was unnecessary for a local network app used only by two people. Network access control (home network/VPN) provides sufficient security.
- **Server Changes**:
  - Removed authentication requirement from `/sync/push` and `/sync/pull` endpoints.
  - Sync endpoints now work without Bearer tokens or device authentication.
- **Android Changes**:
  - Removed all pairing UI and logic from `MainActivity`.
  - Removed auth interceptor from `NetworkModule` - no more token handling.
  - Updated `SyncWorker` to work without token checks.
  - App now goes directly to main screen on startup.
  - Device ID is generated from Android ID for event attribution.
- **Nursery Screen Update**:
  - Changed from RTSP ExoPlayer to WebView for HTML stream.
  - Stream URL: `http://192.168.86.3:1984/stream.html?src=hubble_android`.
  - Removed Media3 RTSP dependencies (can be removed from build.gradle if desired).
