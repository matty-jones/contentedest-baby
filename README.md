# The Contentedest Baby

Offline-first baby tracker for Sleep, Feeding, Nappy, and Growth tracking with a LAN-hosted sync server and OTA update system.

## Features

### Core Tracking
- **Sleep**: Track sleep sessions with start/stop timers, automatic nap/night classification
- **Feeding**: Breast feeding with left/right side tracking and timers, bottle and solids logging
- **Nappy**: Quick logging of wet, dirty, or mixed diapers
- **Growth**: Track weight, length, and head circumference with WHO percentile calculations

### User Interface
- **Timeline Screen**: Visual snake timeline showing all events for a selected day with zoom and filters
- **Growth Screen**: Growth charts using Vico library with percentile tracking
- **Nursery Screen**: Live camera stream via RTSP/WebView
- **Statistics**: Comprehensive stats for sleep, feeding, and nappy events with customizable date ranges
- **Export**: CSV/JSON export functionality for data backup and sharing
- **Splash Screen**: Branded splash screen on app launch

### Technical Features
- **Offline-First**: All operations work offline; syncs automatically when on LAN
- **LAN Sync**: Background synchronization with home server when device is on local network
- **OTA Updates**: Over-the-air update system for pushing app updates without Play Store
- **Dark Mode**: Dark-mode-first UI design
- **Conflict Resolution**: Automatic conflict resolution using version, timestamp, and device ID

## Repo Layout

- `server/` — FastAPI LAN server (SQLite)
- `android/` — Android app (Kotlin, Jetpack Compose, Room, Hilt)

## Quickstart — Server

### Development Setup

1) Create venv and install deps:

```bash
python3 -m venv server/.venv
. server/.venv/bin/activate
pip install -r server/requirements.txt
```

2) Run API locally:

**Option A: Use the convenience script (recommended)**
```bash
./start_server.sh [-p PORT] [-h HOST]
```

The script supports the following options:
- `-p PORT`: Set the port (default: 8005)
- `-h HOST`: Set the host (default: 0.0.0.0)
- `--help`: Show help information

**Option B: Manual commands**
```bash
cd server
source .venv/bin/activate
uvicorn app.main:app --reload --host 0.0.0.0 --port 8005
```

3) Run tests:

```bash
. server/.venv/bin/activate
PYTHONPATH=server pytest -q server/tests
```

### Production Setup (Systemd Service)

The server can run as a systemd service for permanent operation. See `PROGRESS.md` for detailed setup instructions.

Service file: `contentedest-baby.service`

**Service Management:**
```bash
# Status
sudo systemctl status contentedest-baby.service

# Start/Stop/Restart
sudo systemctl start contentedest-baby.service
sudo systemctl stop contentedest-baby.service
sudo systemctl restart contentedest-baby.service

# View logs
sudo journalctl -u contentedest-baby.service -f
```

**Firewall Configuration:**
```bash
# Allow port 8005 from local network
sudo ufw allow from 192.168.86.0/24 to any port 8005 proto tcp
```

### Data Import

#### Import CSV/JSON Data

Use the import script to merge new data with existing database (non-destructive):

```bash
# Activate venv first
. server/.venv/bin/activate

# Optionally point at a custom DB path (defaults to server/data.db)
export TCB_DB_PATH=/home/blasky/Projects/contentedest-baby/server/data.db

# Run the importer
./import_data.py /absolute/path/to/your.csv

# Verify counts
./query_db.py counts
```

#### Migrate Database from CSV (Destructive)

Use the migration script to replace the SQLite DB from a CSV. **Warning: This is destructive and will overwrite existing data.**

```bash
# Activate venv first
. server/.venv/bin/activate

# Optionally point at a custom DB path (defaults to server/data.db)
export TCB_DB_PATH=/home/blasky/Projects/contentedest-baby/server/data.db

# Run the migrator
./migrate_database.py /absolute/path/to/your.csv --device-id seed_device

# Verify counts
./query_db.py counts
```

### Database Utilities

**Query Database:**
```bash
# List recent events
./query_db.py events --limit 50 --desc

# Filter by type and time window
./query_db.py events --type sleep --since 2025-09-01 --until 2025-09-30 --json

# Point at a specific DB file
./query_db.py --db-path /path/to/data.db counts

# List devices
./query_db.py devices --enabled
```

**Timezone Fix:**
If timestamps are displaying with incorrect offsets, use the timezone fix script:

```bash
# Preview changes (dry run)
./fix_timezone_offset.py --dry-run

# Apply fix with default 7-hour offset
./fix_timezone_offset.py

# Apply with custom offset
./fix_timezone_offset.py --offset-hours 7
```

## Quickstart — Android

### Development Setup

- Open `android/` in Android Studio (Giraffe+). Let it sync Gradle and build.
- The app includes Room schema, repositories, domain rules, and full UI implementation.

### Current Version

- **Version Code**: 32
- **Version Name**: 1.5.2

### App Structure

- **Timeline Tab**: Visual timeline with snake-style event rendering, date picker, filters
- **Growth Tab**: Growth charts with percentile calculations, data entry dialogs
- **Nursery Tab**: Live camera stream (RTSP/WebView)
- **Statistics**: Accessible via top bar menu, shows comprehensive stats
- **Export**: Accessible via top bar menu, exports data as CSV/JSON
- **Settings**: Accessible via top bar menu

### OTA Update System

The app includes an OTA update system that checks for updates on startup.

**Automated Release:**
```bash
./release_application
```

This script automatically:
- Increments version code and version name
- Builds release APK
- Copies APK to `server/apks/latest.apk`
- Updates server version information

**Manual Release:**
1. Update `versionCode` and `versionName` in `android/app/build.gradle.kts`
2. Update `version_code` and `version_name` in `server/app/main.py` `get_update_info()` function
3. Build APK: `cd android && ./gradlew assembleRelease`
4. Copy APK: `cp android/app/build/outputs/apk/release/app-release.apk server/apks/latest.apk`
5. Restart server if running as service: `sudo systemctl restart contentedest-baby.service`

**Troubleshooting:**
- Update dialog doesn't appear: Verify server `version_code` > app `version_code` and `/app/update` endpoint is accessible
- Download fails: Check APK exists at `server/apks/latest.apk` and server logs
- Installation fails: User may need to enable "Install from unknown sources" in Android settings

## Development Notes

- All timestamps stored and synced in UTC (seconds).
- Conflict resolution: (version, updated_ts, device_id) — higher tuple wins.
- Events identified by `event_id` (primary key), not timestamps — timezone migrations won't create duplicates.
- Dark-mode-first UI.
- Sleep classification: 19:00–07:00 and ≥2h duration => Night sleep.
- Server runs on port 8005 by default.
- Base URL configured in `BuildConfig.BASE_URL` (default: `http://192.168.86.3:8005/`).

### Troubleshooting

**Sync Issues:**
- If sync clock gets out of sync after timezone migration, reset it via Statistics screen debug options (if available) or directly in database: `UPDATE sync_state SET last_server_clock = 0 WHERE id = 1;`
- Events are matched by `event_id`, so timezone changes won't create duplicates — server version with higher `updated_ts` will win in conflict resolution.

**Timezone Issues:**
- If timestamps display with incorrect offsets, use `./fix_timezone_offset.py` script (see Database Utilities section).
- Before timezone migration: Ensure all devices sync to server first to preserve any local changes.

## API Endpoints

### Sync
- `POST /sync/push` — Push events to server
- `GET /sync/pull?since=<clock>` — Pull events since server clock

### Growth Data
- `POST /growth` — Push growth data to server
- `GET /growth?category=<category>&since=<clock>` — Pull growth data

### App Updates
- `GET /app/update` — Get latest app version information
- `GET /app/download/{filename}` — Download APK files

### Health
- `GET /health` — Health check endpoint
- `GET /healthz` — Alternative health check endpoint

## License

Proprietary; internal development.
