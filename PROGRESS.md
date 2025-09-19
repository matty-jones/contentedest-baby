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
