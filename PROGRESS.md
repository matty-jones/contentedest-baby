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
- Added debug logging to SyncWorker and EventRepository to trace sync flow and identify where events are being lost.
- Fixed SyncWorker constructor: Changed from @AssistedInject to proper HiltWorker constructor to fix WorkManager instantiation error.
- Fixed SyncWorker params reference: Changed params.inputData to inputData after constructor change.
- Fixed SyncWorker constructor: Added back @AssistedInject and @Assisted annotations as required by @HiltWorker annotation.
- Fixed SyncWorker constructor pattern: Reverted to correct @AssistedInject with @Assisted annotations as required by @HiltWorker annotation.

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

## Android Timeline compile fixes (2025-09-30)

- Fixed unresolved `toPx` usage in `SmoothTimeline.kt` by making `createSmoothTimelinePath` a `Density`-scoped function and invoking it with `LocalDensity.current` from the composable. Added required import.
- Fixed invalid `dp.toSp()` in `PolishedTimeline.kt` by switching to `10.sp`.
- Verified other `toPx` usages occur within `Canvas`/`DrawScope` contexts where `Dp.toPx()` is valid.
- Resolved build environment issues:
  - Installed Java 17 via `brew install --cask temurin@17`
  - Updated Gradle wrapper from 8.7 to 8.9 (required by AGP 8.7.2)
  - Added Java toolchain configuration to app module's `build.gradle.kts`
  - Build now succeeds: `./gradlew :app:assembleDebug` completes successfully
- All `toPx` compilation errors resolved, Android app builds successfully.

## Server bootstrap + CSV fallback (2025-10-01)

- Implemented database bootstrap on startup: if active DB has 0 events, copy from committed `server/initial_data.db`.
- Added CSV fallback: if still empty, seed from CSV resolved by `TCB_SEED_CSV` or first CSV under `TEMP/`.
- Removed hardcoded external CSV path. Startup now robust across machines.
- Created `server/initial_data.db` with 4,193 events (1,313 sleep, 2,426 feed, 454 nappy) from TEMP CSV.
- Server conflict resolution: uses (version, updated_ts, device_id) comparison to prevent data loss.

## Android immediate sync (2025-10-01)

- Added `SyncWorker.scheduleImmediateSync()` for instant data loading on app open.
- Updated `MainActivity` to trigger both immediate and periodic sync on startup/pairing.
- Immediate sync uses `NetworkType.CONNECTED` constraint (less restrictive than periodic).
- Server-side conflict resolution ensures Android events with higher version/timestamp win over initial data.
- Sync flow: Android pushes local events → Server resolves conflicts → Android pulls server events.
