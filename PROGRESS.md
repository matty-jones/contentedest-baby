# Progress

## 2026-04-13 — Timeline date picker and navigation icons

**Change:** On the Timeline screen, tapping the formatted date (e.g. "Friday, 10th April") opens the Material3 `DatePickerDialog` with the current day selected, plus a "Today" action that jumps to the current date. Previous/next day controls use `IconButton` with `KeyboardArrowLeft` / `KeyboardArrowRight` (same pattern as Daily Log) instead of raw `<` / `>` text.

**Verify:** `./gradlew :app:compileDebugKotlin` from `android/`.

## 2026-04-13 — Refactor Wave 1 baseline

**Scope:** Scripts + server refactor wave started from approved plan. No refactor edits applied yet.

**Environment:** Created repo-local `.venv` and installed `server/requirements.txt`.

**Baseline verify:** From repo root, `source .venv/bin/activate && PYTHONPATH=server pytest -q server/tests`.

**Result:** `15 passed` in ~0.43s, with pre-existing warnings:
- `pytest-asyncio` deprecation about `asyncio_default_fixture_loop_scope` unset.
- FastAPI deprecation for `@app.on_event("startup")` in `server/app/main.py`.

## 2026-04-13 — Refactor Wave 1 implementation

**Tests added before refactor:**
- `server/tests/test_server.py`: characterization coverage for sync adjacent-merge behavior and growth conflict/pull behavior (including `since=0` without category).
- `server/tests/test_script_time_and_timezone.py`: script characterization tests for shared datetime parsing and `fix_timezone_offset.py` end-to-end timestamp shift.

**Server refactor:**
- Split monolithic `server/app/main.py` into focused modules:
  - `server/app/routers/health_admin.py`
  - `server/app/routers/webhook.py`
  - `server/app/routers/sync.py`
  - `server/app/routers/updates.py`
  - `server/app/routers/growth.py`
  - `server/app/seed.py`
- `server/app/main.py` is now app wiring + middleware + startup seed hook.

**Deduplication:**
- Added `server/app/timeparse.py` for shared UTC-7 parsing.
- Updated `import_data.py`, `migrate_database.py`, and `server/app/seed.py` to use shared parsing logic.
- Reduced duplicate upsert field-copy logic in `server/app/crud.py` with shared helpers for resolve paths.

**Cleanup:**
- Removed large diagnostic/debug-only branch from growth pull fallback path, replacing it with direct non-deleted query logic.
- Trimmed verbose historical parsing commentary to concise intent-level docstrings in import/migration paths.

**Bugs found/fixed:** No behavioral bugs detected during this wave.

**Final verify:** `source .venv/bin/activate && PYTHONPATH=server pytest -q server/tests` → `20 passed`, same 2 pre-existing warnings (`pytest-asyncio` loop-scope deprecation and FastAPI `on_event` deprecation).

## 2026-04-13 — Refactor Wave 2 (Android only, no CI changes)

**Baseline status:**
- Ran `source ../.venv/bin/activate && ./gradlew :app:testDebugUnitTest` from `android/`.
- Found 5 failing pre-existing tests in `RoomBasicsTest`, `EventRepositoryTest`, and `NetworkTests`.

**Bugs fixed before new refactor/tests:**
- Added Robolectric runner annotations to Room-based JVM tests:
  - `android/app/src/test/java/com/contentedest/baby/RoomBasicsTest.kt`
  - `android/app/src/test/java/com/contentedest/baby/EventRepositoryTest.kt`
- Fixed mock JSON field names in `android/app/src/test/java/com/contentedest/baby/NetworkTests.kt` to match Moshi model annotations (`server_clock`, `event_id`).

**New characterization tests:**
- Added `android/app/src/test/java/com/contentedest/baby/GrowthPercentileCalculatorTest.kt` covering:
  - median-to-50th-percentile behavior at known LMS medians
  - weight unit consistency (`lb` vs `kg`)
  - percentile round-trip behavior
  - unsupported-unit/category null behavior

**Android cleanup/refactor (no behavior change):**
- `android/app/src/main/java/com/contentedest/baby/data/repo/EventRepository.kt`
  - extracted repeated payload parsing into helper methods (`parseFeedMode`, `payloadNumberToInt`)
  - removed unnecessary fully-qualified type usage and redundant comments
- `android/app/src/main/java/com/contentedest/baby/ui/growth/GrowthPercentileCalculator.kt`
  - trimmed verbose historical/math exposition comments down to concise intent-level comments

**Verification:**
- Re-ran `./gradlew :app:testDebugUnitTest` after each change set.
- Final Android unit-test result: **BUILD SUCCESSFUL** (all unit tests passing).

## 2026-04-08 — Crib webhook sleep policy and adjacent merge

**Goal:** Reduce Frigate/HA noise: ignore very short “sleep” segments from the crib webhook, merge sleep/feed fragments that are within 60s or overlap, and provide DB maintenance scripts.

**Server behavior:** `app/event_policy.py` defines `MIN_CRIB_WEBHOOK_SLEEP_SECONDS` (300), `ADJACENT_MERGE_GAP_SECONDS` (60), and merge helpers. `close_crib_webhook_sleep` soft-deletes closed crib sleeps under 5 minutes; otherwise closes and runs `merge_adjacent_chain`. Sync push runs the same merge after upserting closed sleep or feed rows. Manual timeline / sync paths do not apply the 5-minute discard.

**Webhook:** `POST /webhook/crib` returns `action: "discarded"` with the soft-deleted `event_id` when duration is under 5 minutes; see `CribWebhookResponse` in `app/schemas.py`.

**Scripts (run against DB backup, consolidate before delete-short):** `server/scripts/consolidate_adjacent_events.py` (only rows with `start_ts` on or after 2026-01-01 UTC; live crib/sync merge has no such cutoff; apply mode uses `try_merge_first_mergeable_pair_for_device_type` / `merge_adjacent_sorted_pair` so it does not run the expensive overlap query per merge), `server/scripts/delete_short_sleep_events.py`.

**Tests:** `tests/test_event_policy.py` (merge rules); `tests/test_server.py` uses `httpx.ASGITransport` with `AsyncClient` (httpx 0.28+), and monkeypatches `time.time` for crib close vs discard.

**Verify:** From `server/`, `PYTHONPATH=. pytest tests/test_event_policy.py tests/test_server.py`.

## 2026-04-08 — Snake timeline row connectors

**Issue:** Long sleep (or any) events spanning multiple rows could draw the curved row-to-row connector on the wrong side (e.g. right on an R→L row where the white track turns left).

**Cause:** Connector X was chosen from `touchesPhysicalLeft` / `touchesPhysicalRight` derived from exact `startFrac == 0f` and `endFrac == 1f`. Slight float error or boundary semantics made `touchesRowEnd` false on R→L rows while `touchesRowStart` stayed true, so the code picked `innerRight` (wrong).

**Fix:** In `SnakeTimeline.kt` `computeEventDrawables`, emit a connector only when `continues && segEnd == rowEndSec` (integer row boundary), and set `edgeX` / `cpX` from `goingRight` only (`innerRight` + offset when L→R, else `innerLeft` - offset).

**Verify:** `./gradlew :app:compileDebugKotlin` from `android/`.
