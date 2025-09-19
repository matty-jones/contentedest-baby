
# The Contentedest Baby — Product & Technical Requirements (Android)

**Document status:** v1.0 (PRD + TRD)  
**Prepared for:** Implementation hand‑off  
**Scope:** Android client (offline‑first), lightweight LAN server, single shared household (two devices)

---

## 1. Product Overview

**Objective.** Deliver a privacy‑respecting, offline‑first baby tracker focused on three core event types—**Sleep**, **Feeding**, and **Nappy**—with a **Daily Log** and **Statistics** views. The application operates fully offline and synchronises with a **home‑hosted server** when the device is back on the local network. There is **no account/login** model; two devices pair with a single server and share one database.

**Primary goals**

- Zero‑friction capture of Sleep, Feeding, and Nappy events.
- Clear **Daily Log** with zoom and filters; **Statistics** across day/week/month/custom ranges.
- **Offline‑first** local cache with background synchronisation on LAN reconnect.
- **Two users**, one shared database, no SaaS dependency.
- **Data ownership:** CSV/JSON export and optional printable reports.
- Professional, **dark‑mode‑first** UI with large touch targets and consistent styling.

**Out‑of‑scope (v1)**

- Remote/cloud accounts, invites, multi‑household.
- Device control (e.g., smart bassinets), growth charts, pumping trackers.
- Push notifications from internet services (local reminders permissible).

---

## 2. Competitive Reference (for parity/inspiration)

The design references common patterns from mainstream baby‑tracking apps (e.g., daily timeline/log, filtering by activity type, day zoom, and daily reports). This PRD focuses on **offline‑first** operation and **home‑hosted** synchronisation, which are deliberate differentiators.

---

## 3. Agreed Preferences & Clarifications (from Stakeholder)

1. **Feeding detail.** Include **Left/Right breast timers** with per‑second timing and **one‑tap swap**; support **multiple swaps** within a single feed session. Bottle and solids logging retained with optional amounts/durations.  
2. **Nappy types.** `wet`, `dirty`, `both`.  
3. **Sleep classification.** Infer **nap vs night** from time‑of‑day and duration (no explicit user toggle).  
4. **Statistics.** Provide a broad set of descriptive stats (totals, averages, distributions, longest stretch, intervals). Predictive modelling is **future phase**, not v1.  
5. **Aesthetics.** Dark‑mode only for v1, clean and consistent. Visual language may borrow from existing apps for familiarity.  
6. **Server footprint.** Prefer a **lightweight SQL database on a Raspberry Pi** with a small service to manage sync. If needed, support **Docker on a workstation**. LAN‑only (initially).  
7. **Pairing.** **QR code on the server UI**; scan in app to establish a long‑lived device token.  
8. **Retention.** Keep all data indefinitely; maintain compact storage.  
9. **Reports.** Provide a **Generate Report** feature (e.g., weekly PDF) suitable for sharing with a paediatrician.

---

## 4. User Stories

- As a parent, I can start/stop a **sleep session** instantly (single tap) and add a note after.  
- As a parent, I can log **feeds** (breast with L/R swaps; bottle/solids with amounts) in <5 seconds.  
- As a parent, I can log a **nappy** with type (wet/dirty/both) and optional note.  
- As a parent, I can view a **Daily Log** timeline for today with **zoom** and **filters** (Sleep/Feed/Nappy).  
- As a parent, I can view **Stats** for last 7/30 days and custom ranges.  
- As a parent, I can use the app **offline** and trust it to **sync** when back on my LAN.  
- As a two‑device household, both devices converge on the same dataset without manual conflict resolution.  
- As a data owner, I can **export** CSV/JSON and **generate a report** (PDF) for a specified range.

---

## 5. Functional Requirements

### 5.1 Event Types

**Common fields**: `event_id (UUIDv4)`, `device_id`, `created_ts (UTC)`, `updated_ts (UTC)`, `version (int)`, `deleted (bool)`, `note (optional)`.

- **Sleep**
  - Fields: `start_ts (UTC)`, `end_ts (UTC|null)`.  
  - Derived: duration, classification (**nap**/**night**) via rules, inclusion in daily totals, **longest stretch** per day.
- **Feeding**
  - Breast: one session with multiple **segments**, each segment labelled `left` or `right` with `start_ts`, `end_ts`. Provide **single‑tap swap** during a running feed; timer precision to **1 s**.
  - Bottle: `ts`, `amount_ml (optional)`, `duration_s (optional)`.
  - Solids: `ts`, `amount_g or amount_ml (optional)`, `duration_s (optional)`.
- **Nappy**
  - Fields: `ts`, `type ∈ {wet, dirty, both}`.

### 5.2 Daily Log

- Timeline view for the selected day, with horizontal scroll/paging.  
- Visual encodings: **bars** for sleep spans, **markers** for feeds and nappies.  
- **Zoom** into day; **filter chips** for Sleep/Feed/Nappy (multi‑select).  
- Tap to view details/edit; long‑press to quick‑edit or delete (with undo).

### 5.3 Statistics

- Ranges: Today, last 7/30 days, custom date range.  
- Sleep: total duration/day, **longest stretch**, distribution by hour, inferred nap/night breakdown.  
- Feeding: count/day, bottle and solids **amount totals**, breast segment counts and proportions (L vs R time), feed‑to‑feed **intervals** (median, p90).  
- Nappies: count/day, distribution by type.  
- Visuals: bar/line charts; tap for event lists.

### 5.4 Editing & Deletion

- Edit any event (default **30‑day** unrestricted window; stakeholder prefers no hard limit—keep audit via versioning).  
- Soft delete with **undo**; persistence uses `deleted = true` (event remains for conflict resolution).

### 5.5 Offline‑First Behaviour

- All operations available offline.  
- Local queue of **pending** changes in the client DB.  
- Background sync triggers when: device is on the **trusted LAN** or server is reachable, battery not critical (configurable).

### 5.6 Sync & Conflict Resolution

- **Delta sync** using a server **clock/watermark**.  
- Client push: upsert events changed since the last watermark.  
- Server returns: authoritative merged events changed since client watermark, plus new watermark.  
- **Conflict rule:** compare by `(version, updated_ts, device_id)`; higher version wins; if tie, newer `updated_ts`; if still tie, lexicographically lower `device_id`. Server always replies with the resolved record.  
- Idempotent upserts by `event_id`.  
- Batch size tunables; back‑off on errors.

### 5.7 Pairing & Trust Model

- First‑run: App shows **“Pair with Server”**.  
- Server UI displays **QR** with a **short‑lived pairing token**; the app scans to obtain a **long‑lived device token** bound to `device_id`.  
- All API calls use `Authorization: Bearer <token>`; tokens can be revoked in server UI.

### 5.8 Data Export & Reports

- Client: export **CSV** and **JSON** for a chosen date range; share via Android Share Sheet.  
- Server: export full database as CSV/JSON bundle (admin only).  
- **Generate Report (PDF)**: time range selector → formatted summary (totals, charts, highlights like longest sleep stretch).

### 5.9 Accessibility & UX

- Dark‑mode only (v1); large hit areas; logical focus order; haptics for start/stop.  
- Quick actions: optional notification shade buttons (Start Sleep, Add Feed/Nappy).  
- Optional local reminders (no external push).

---

## 6. Non‑Functional Requirements

- **Performance:** warm launch < 1 s (target), event creation < 100 ms, timeline render < 200 ms post‑load, LAN sync ~2 s/1k events.  
- **Reliability:** no data loss under intermittent connectivity; idempotent sync; recoverable from power loss.  
- **Security:** TLS on LAN (self‑signed CA acceptable); tokens stored in platform keystore; no third‑party trackers.  
- **Privacy:** data remains on devices and home server; explicit export only.  
- **Maintainability:** modular codebase; clear separations (domain, data, UI).  
- **Portability:** server can run on Raspberry Pi or as Docker on a workstation.

---

## 7. Android Client — Technical Architecture

**Language & Frameworks:** Kotlin, Jetpack Compose (Material 3), Navigation‑Compose, Hilt (DI), Room (SQLite), WorkManager, Retrofit/OkHttp, Kotlinx Serialization, java.time (UTC everywhere).

**Architecture:** Clean architecture with MVVM.
- **Domain layer:** use‑cases (create/edit events, compute stats, sync).  
- **Data layer:** repositories (Room + network), conflict resolver, serializers.  
- **UI layer:** Compose screens (Sleep, Feeding, Nappy, Daily Log, Stats, Export, Settings/Pairing).

**Local Database (Room):**
- Tables: `events`, `feed_segments`, `sync_state`, `settings`.  
- Indices on time fields (`start_ts`, `end_ts`, `ts`) for fast timeline queries.  
- FTS (full‑text search) for notes.  
- Migrations tested and documented.

**Background Work:**
- WorkManager tasks for **sync**, **report generation**, and optional **reminders**.  
- Network constraints: unmetered + specific SSID or server reachability probe.  
- ForegroundService (optional) to keep **active timers** precise if the OS is aggressive with background limits.

**Charts:** MPAndroidChart or Compose‑friendly charts.  
**Time:** Store all timestamps in UTC; present in local time via `ZoneId.systemDefault()`.

**Testing:** JUnit & Robolectric; Compose UI tests; repository and conflict resolution unit tests; instrumentation tests simulating offline/online transitions and concurrent edits.

---

## 8. Server — Technical Architecture (LAN‑Hosted)

**Target footprint:** Raspberry Pi (ARM) first; optional Dockerised deployment on a workstation/NAS.

**Stack:**
- **API:** FastAPI (Python) with Pydantic models and auto‑generated OpenAPI.  
- **DB:** SQLite (default) for simplicity and durability on Pi; migrations via Alembic. Optional PostgreSQL when running under Docker.  
- **Auth:** Device tokens minted during pairing; SHA‑256 token hash stored server‑side.  
- **TLS:** Served behind Caddy or nginx (self‑signed certificate acceptable for LAN).  
- **Backup:** Nightly SQLite backups; optional copy to a NAS path.  
- **Admin UI:** minimal web UI (pairing QR, device list/revoke, export, backup status).

**Data Model (SQLite/PostgreSQL):**
- `devices(device_id TEXT PK, name TEXT, created_ts INTEGER, last_seen_ts INTEGER, token_hash TEXT, enabled INTEGER)`  
- `events(event_id TEXT PK, type TEXT, payload JSON, start_ts INTEGER, end_ts INTEGER, ts INTEGER, created_ts INTEGER, updated_ts INTEGER, version INTEGER, deleted INTEGER, device_id TEXT)`  
- `watermarks(device_id TEXT PK, last_clock INTEGER, updated_ts INTEGER)`  
- `server_clock(counter INTEGER)` — monotonic; generated in a transaction per commit.

**Endpoints (initial draft):**
- `POST /pair` → mint device token from short‑lived pairing code (QR).  
- `POST /sync/push` → `[EventDTO]` upsert; returns per‑event status + `server_clock`.  
- `GET /sync/pull?since=<clock>` → changed events since clock + new `server_clock`.  
- `GET /export.(csv|json)` → admin only.  
- `GET /healthz` → liveness.

**Conflict Resolution (server‑side):**
- Compare candidate vs stored by `(version, updated_ts, device_id)`; apply winner; bump `server_clock`; return resolved record to caller.  
- Ensure idempotency by primary key `event_id` and version checks.

**Observability:**
- Structured JSON logs; optional Prometheus metrics (request count, p95 latency, sync batch sizes).  
- Admin UI shows last backup timestamp and recent sync activity.

---

## 9. Algorithms & Rules

**Sleep classification (nap vs night):**  
- Default rules (configurable): any sleep **starting between 19:00–07:00** and **lasting ≥ 2 h** counts toward **night**; otherwise **nap**.  
- Edge cases (crossing midnight) handled by splitting spans for daily stats while preserving one logical event.

**Breast feeding segmentation:**  
- One active feed can contain **N alternating segments** (`left`/`right`).  
- On **swap**, close the current segment (to the second) and open the new one.  
- Session ends when the user stops the feed; derived totals show per‑side durations and proportions.

**Conflict‑safe editing:**  
- Each edit increments `version` and updates `updated_ts`.  
- Deletes mark `deleted = true`.  
- Server responds with the resolved record enabling clients to converge.

---

## 10. Security & Privacy

- **LAN‑only** operation by default. If exposed, enforce HTTPS.  
- **Device tokens** stored in Android Keystore / EncryptedSharedPreferences.  
- **No third‑party analytics** or trackers.  
- Minimal PII: optional infant name and DOB; no demographic data.  
- **Exports** are user‑initiated only.

---

## 11. Deliverables & Milestones

**M1 — Data & Basics (2–3 weeks)**  
- Room schema & repositories; create/edit/delete Sleep/Feed/Nappy; basic list views.

**M2 — Daily Log (2 weeks)**  
- Timeline with zoom & filters; detail sheets; undo for delete.

**M3 — Statistics (2 weeks)**  
- Sleep/Feed/Nappy stats for 7/30 days + custom; charts.

**M4 — Sync (2–3 weeks)**  
- FastAPI server (SQLite); pairing (QR + token); delta sync; conflict resolution; two‑device convergence.

**M5 — Export & Reports (1–2 weeks)**  
- CSV/JSON export; **Generate Report (PDF)**; polish, accessibility pass, soak tests.

---

## 12. Acceptance Criteria

1. Create/stop **Sleep**, create **Feed** (with L/R swaps), create **Nappy**; edit/delete with undo.  
2. **Daily Log** renders today’s events, supports **zoom** and **filters**, opens details on tap.  
3. **Statistics** show totals, longest sleep stretch, feed counts/amounts, breast L/R proportions, nappy counts; ranges: 7/30/custom.  
4. App operates **offline**; upon LAN reconnection, two devices **converge** within 30 s of being on the network.  
5. **Exports** produce valid CSV/JSON; **Generate Report** outputs a readable PDF covering a user‑selected range.  
6. No crashes in a 24‑hour test with random edits and connectivity toggles.

---

## 13. Testing Strategy

- **Unit tests:** repositories, conflict resolution, time calculations, classification rules.  
- **UI tests:** Compose tests for forms, timers, timeline, charts.  
- **Integration:** emulator ↔ local FastAPI; adb reverse for LAN simulation; sync under edit conflicts.  
- **Soak tests:** randomised create/edit/delete + periodic sync; verify set convergence and no duplication.

---

## 14. Deployment & Operations

- **Server on Pi:** systemd service (uvicorn/gunicorn), SQLite on ext4 with journaling; daily `sqlite3 .backup` to NAS path.  
- **Server on Docker:** `docker-compose.yml` with FastAPI + (optional) Postgres + Caddy; volumes for persistent data and certificates.  
- **Client distribution:** side‑loaded APK (internal use).  
- **Config:** trusted SSIDs, server hostname/IP, backup path, TLS certificates.

---

## 15. Future Work (Post‑v1)

- Homescreen **widgets**; Wear OS quick actions.  
- Tags and health notes; extended nappy categories (optional).  
- Multi‑infant support.  
- **Predictive model** for optimal sleep/feed windows (device‑local or server‑hosted).  
- End‑to‑end encrypted local backups and seamless restore.  
- Import from other trackers (CSV mapping tool).

---

## 16. Appendix — Data Shapes (Illustrative)

```jsonc
// Sleep event
{
  "event_id": "uuid",
  "type": "sleep",
  "start_ts": 1737099600,   // UTC epoch seconds
  "end_ts": 1737108600,     // null while running
  "note": "contact nap",
  "device_id": "phone-matty",
  "version": 3,
  "created_ts": 1737099605,
  "updated_ts": 1737108602,
  "deleted": false
}
```

```jsonc
// Feeding event (breast with segments)
{
  "event_id": "uuid",
  "type": "feed",
  "mode": "breast",
  "segments": [
    {"side": "left",  "start_ts": 1737110000, "end_ts": 1737110600},
    {"side": "right", "start_ts": 1737110600, "end_ts": 1737111150},
    {"side": "left",  "start_ts": 1737111150, "end_ts": 1737111600}
  ],
  "note": "",
  "device_id": "phone-matty",
  "version": 1,
  "created_ts": 1737110001,
  "updated_ts": 1737111601,
  "deleted": false
}
```

```jsonc
// Nappy event
{
  "event_id": "uuid",
  "type": "nappy",
  "ts": 1737120000,
  "nappy_type": "both",
  "note": "loose",
  "device_id": "phone-partner",
  "version": 1,
  "created_ts": 1737120002,
  "updated_ts": 1737120002,
  "deleted": false
}
```

```yaml
# /sync/pull response (illustrative)
server_clock: 4821
events:
  - {event_id: "...", type: "sleep", start_ts: 1737099600, end_ts: 1737108600, updated_ts: 1737108602, version: 3, ...}
  - {event_id: "...", type: "feed",  mode: "bottle", ts: 1737118000, amount_ml: 110, updated_ts: 1737118200, version: 2, ...}
```

---

**End of document.**
