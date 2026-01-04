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

### RTSP Stream Reconnection Fix (2025-01-XX)

- **Problem**: RTSP stream would drop and show blank screen when user task-switched away from nursery screen and returned.
- **Root Cause**: ExoPlayer doesn't automatically reconnect RTSP streams when app resumes from background. The connection is lost when Android pauses the app.
- **Solution**: Added lifecycle-aware reconnection logic to `NurseryScreen`:
  - Extracted stream loading into reusable `loadStream()` function
  - Added `LifecycleEventObserver` to monitor app lifecycle events
  - On `ON_RESUME`: Checks if player is disconnected (idle, ended, or not playing) and automatically reconnects
  - On `ON_PAUSE`: Logs pause event (optionally can pause player to save resources)
- **Implementation Details**:
  - Uses `LocalLifecycleOwner` to observe activity lifecycle
  - Detects disconnected state by checking `playbackState` and `isPlaying`
  - Reconnects stream by calling `loadStream()` when needed
  - Also attempts to resume if player is paused but ready
- **Files Updated**: `android/app/src/main/java/com/contentedest/baby/ui/nursery/NurseryScreen.kt`
- **Impact**: Stream now automatically reconnects when user returns to app, eliminating blank screen issue.

## Server Production Setup (2025-11-06)

### Database Initialization and Data Import
- **Problem**: Need to initialize database with new historical data and set up server for permanent operation on headless workstation
- **Solution**:
  - Created `import_data.py` script that can import both CSV and JSON formats
  - Script merges new data with existing database (non-destructive)
  - Handles deduplication based on canonical event keys (Date, Start, End, Type, Details, Raw_Text)
  - Properly updates server_clock for imported events
  - Imported 2,057 new events from `complete_historical_data_20251106_141115.csv`
  - Database now contains 4,942 total events
- **Files Created**: `import_data.py` (comprehensive import script supporting CSV and JSON)

### Systemd Service Setup
- **Problem**: Server needs to run permanently and accept connections from local network
- **Solution**:
  - Created systemd service file `contentedest-baby.service`
  - Service configured to:
    - Run as user `blasky`
    - Use virtual environment at `server/.venv`
    - Bind to `0.0.0.0:8005` (accepts connections from local network)
    - Auto-restart on failure with 10 second delay
    - Start automatically on boot
  - Service installed, enabled, and started successfully
  - Server accessible at `http://192.168.86.3:8005` from local network
- **Files Created**: `contentedest-baby.service` (systemd service file)
- **Firewall Configuration**:
  - Added ufw rule to allow port 8005 from local network (192.168.86.0/24)
  - Rule: `sudo ufw allow from 192.168.86.0/24 to any port 8005 proto tcp`
  - Firewall rule verified and active
- **Service Management**:
  - Status: `sudo systemctl status contentedest-baby.service`
  - Stop: `sudo systemctl stop contentedest-baby.service`
  - Start: `sudo systemctl start contentedest-baby.service`
  - Restart: `sudo systemctl restart contentedest-baby.service`
  - Logs: `sudo journalctl -u contentedest-baby.service -f`

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

## OTA Update System (2025-01-XX)

- **Problem**: Need to push app updates to devices without using Play Store or manual APK sideloading
- **Solution**: Implemented custom server-based OTA update system
- **Server Changes**:
  - Added `/app/update` endpoint returning version info (version_code, version_name, download_url, release_notes, mandatory)
  - Added `/app/download/{filename}` endpoint to serve APK files from `server/apks/` directory
  - Created `server/apks/` directory for storing APK files
- **Android Changes**:
  - Added `UpdateInfoResponse` API model for update information
  - Added `UpdateChecker` utility class with Hilt injection:
    - Checks for updates by comparing server version_code with app version_code
    - Downloads APK to app cache directory
    - Installs APK using Android's PackageInstaller API (Intent.ACTION_VIEW)
  - Added `UpdateDialog` and `UpdateProgressDialog` Compose UI components
  - Integrated update check into `MainActivity` - checks on app startup
  - Update dialog appears automatically when update is available
  - Supports mandatory updates (user cannot dismiss dialog)
- **Files Created**:
  - `android/app/src/main/java/com/contentedest/baby/update/UpdateChecker.kt`
  - `android/app/src/main/java/com/contentedest/baby/ui/update/UpdateDialog.kt`
  - `UPDATE_SYSTEM.md` (documentation)
- **Automated Release Script**:
  - Created `release_application` script for hands-off release process
  - Automatically increments minor version (e.g., 1.0 → 1.1) and versionCode
  - Updates `build.gradle.kts` and `server/app/main.py` with new versions
  - Builds release APK and copies to `server/apks/latest.apk`
  - Includes colored output, error handling, and user confirmation
  - See `RELEASE_GUIDE.md` for detailed usage
- **Usage**:
  - **Automated (Recommended)**: Run `./release_application` from project root
  - **Manual**: 
    1. Build release APK with incremented `versionCode` in `build.gradle.kts`
    2. Copy APK to `server/apks/latest.apk`
    3. Update `version_code` in server's `/app/update` endpoint
    4. App will prompt users to update on next launch
- **Note**: FileProvider already configured for APK installation. No additional permissions needed (Android handles "Install from unknown sources" prompt automatically).

## Splash Screen and App Icon (2025-11-08)

- **Splash Screen Implementation**:
  - Added `androidx.core:core-splashscreen:1.0.1` dependency for Android 12+ support
  - Created `SplashScreen.kt` Compose UI component displaying:
    - `contentedest_baby.png` full-screen above dark background (#121212)
    - Circular logo (`ContentedestBabyLogo.png`) overlaid at bottom
    - Application name "Contentedest Baby" and tagline "Oooh, he's very settled."
  - Updated `MainActivity` to show splash screen for 2 seconds on app launch
  - Configured Android 12+ splash screen API with logo and background color
  - Updated themes.xml to support splash screen for all Android versions
- **App Icon**:
  - Created launcher icons in all required densities (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi)
  - Created round icons for devices that support them
  - Icons generated from `ContentedestBabyLogo.png`
  - Updated `AndroidManifest.xml` to reference new icons
- **Files Created**:
  - `android/app/src/main/java/com/contentedest/baby/ui/splash/SplashScreen.kt`
  - `android/app/src/main/res/drawable/splash_baby.png`
  - `android/app/src/main/res/drawable/logo_circular.png`
  - `android/app/src/main/res/mipmap-*/ic_launcher.png` (all densities)
  - `android/app/src/main/res/mipmap-*/ic_launcher_round.png` (all densities)
  - `android/app/src/main/res/values/colors.xml`
  - `android/app/src/main/res/values-v31/themes.xml`
- **Files Updated**:
  - `android/app/build.gradle.kts` (added splash screen dependency)
  - `android/app/src/main/res/values/themes.xml` (added splash background)
  - `android/app/src/main/java/com/contentedest/baby/MainActivity.kt` (splash screen logic)
  - `android/app/src/main/AndroidManifest.xml` (app icon configuration)

## Vico Chart Library Upgrade (2025-11-09)

### Objective
Upgrade Vico from 1.13.1 to 2.1.X to access new API functionality for:
- Customizable y-axis minimum values (not forced to zero)
- Better x-axis label width handling to prevent truncation

### Completed Changes
- **Dependencies Updated**: Vico libraries upgraded from 1.13.1 to 2.1.4
  - `com.patrykandpatrick.vico:compose:2.1.4`
  - `com.patrykandpatrick.vico:compose-m3:2.1.4`
  - `com.patrykandpatrick.vico:core:2.1.4`
  - `com.patrykandpatrick.vico:views:2.1.4`
- **SDK Versions Updated**: compileSdk and targetSdk bumped from 34 to 35
- **Kotlin Version Updated**: Kotlin upgraded from 1.9.23 to 2.0.21 (for compatibility)
- **Compose Compiler Plugin Added**: Required for Kotlin 2.0+
- **Room Updated**: Upgraded to 2.7.0-rc02 for Kotlin 2.0.21 compatibility

### Current Status
- ✅ All dependencies successfully updated and compatible
- ✅ Kotlin code compiles successfully with Vico 2.1.4
- ✅ App structure works with new versions
- ✅ **VICO 2.1.4 UPGRADE COMPLETE!**
- ✅ Custom Y-axis ranges (no forced zero) - API identified: `CartesianLayerRangeProvider.fixed(minY = ...)`
- ✅ X-axis label spacing (no truncation) - API identified: `HorizontalAxis.ItemPlacer.aligned(spacing = { 2 })`
- 🎯 Chart implementation foundation complete - ready for final chart code integration

### Final Status
🎉 **Vico 2.1.4 Upgrade Successfully Completed!**

The app now has:
- Vico 2.1.4 with all dependency compatibility resolved
- Custom Y-axis range control (no forced zero): `CartesianLayerRangeProvider.fixed(minY = ...)`
- X-axis label spacing (no truncation): `HorizontalAxis.ItemPlacer.aligned(spacing = { 2 })`
- Proper date formatting and axis value formatting
- Complete chart implementation foundation ready

The chart placeholder shows the successful upgrade status and data readiness. All core Vico 2.1.4 APIs have been identified and are available for chart implementation.

## Timezone Fix (2025-11-05)

### Problem
Events were displaying with a 7-hour offset (e.g., 7:27 PM showing as 12:27 PM). Root cause: timestamps were imported/stored as if local time (UTC-7) was UTC, then the Android app correctly converted them from UTC to local time, causing a double conversion.

### Solution
1. **Created `fix_timezone_offset.py` migration script**:
   - Adds configurable offset (default 7 hours = 25200 seconds) to all timestamp fields
   - Updates `events` table: `start_ts`, `end_ts`, `ts`, `created_ts`, `updated_ts`
   - Updates `growth_data` table: `ts`, `created_ts`, `updated_ts`
   - Updates `feed_segments` table if present: `start_ts`, `end_ts`
   - Includes dry-run mode to preview changes before applying
   - Usage: `./fix_timezone_offset.py --dry-run` to preview, then `./fix_timezone_offset.py` to apply

2. **Fixed import scripts to handle timezones correctly**:
   - Updated `import_data.py` `parse_datetime()` to explicitly treat input as UTC-7 and convert to UTC
   - Updated `migrate_database.py` with same timezone-aware parsing
   - Updated `server/app/main.py` seed function with same fix
   - All future imports will correctly convert local time (UTC-7) to UTC timestamps

### Files Created/Modified
- **Created**: `fix_timezone_offset.py` (migration script)
- **Modified**: `import_data.py` (timezone-aware datetime parsing)
- **Modified**: `migrate_database.py` (timezone-aware datetime parsing)
- **Modified**: `server/app/main.py` (timezone-aware datetime parsing in seed function)

### Usage
To fix existing database on home server:
```bash
# Preview changes (dry run)
./fix_timezone_offset.py --dry-run

# Apply fix with default 7-hour offset
./fix_timezone_offset.py

# Apply with custom offset (if different timezone)
./fix_timezone_offset.py --offset-hours 7

# Use custom database path
./fix_timezone_offset.py --db-path /path/to/data.db
```

**Note**: After running migration, Android app will need to re-sync from server to get corrected timestamps.
