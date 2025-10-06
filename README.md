# The Contentedest Baby

Offline-first baby tracker for Sleep, Feeding, and Nappy with a LAN-hosted sync server.

## Repo Layout

- `The_Contentedest_Baby_PRD.md` — Product & Technical Requirements
- `server/` — FastAPI LAN server (SQLite)
- `android/` — Android app (Kotlin, Jetpack Compose, Room, Hilt)

## Quickstart — Server

1) Create venv and install deps:

```
python3 -m venv server/.venv
. server/.venv/bin/activate
pip install -r server/requirements.txt
```

2) Run API locally:

**Option A: Use the convenience script (recommended)**
```
./start_server.sh [-p PORT] [-h HOST]
```

The script supports the following options:
- `-p PORT`: Set the port (default: 8005)
- `-h HOST`: Set the host (default: 0.0.0.0)
- `--help`: Show help information

**Option B: Manual commands**
```
cd server
source .venv/bin/activate
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

3) Run tests:

```
. server/.venv/bin/activate
PYTHONPATH=server pytest -q server/tests
```

### Import a CSV and overwrite the database

Use the migration script to replace the SQLite DB from a CSV. This is destructive.

```
# Activate venv first
. server/.venv/bin/activate

# Optionally point at a custom DB path (defaults to server/data.db)
export TCB_DB_PATH=/home/blasky/Projects/contentedest-baby/server/data.db

# Run the importer
./migrate_database.py /absolute/path/to/your.csv --device-id seed_device

# Verify counts
./query_db.py counts
```

## Quickstart — Android

- Open `android/` in Android Studio (Giraffe+). Let it sync Gradle and build.
- The app includes Room schema, basic repositories, domain rules, and a minimal Daily Log UI shell.

## Development Notes

- All timestamps stored and synced in UTC (seconds).
- Conflict resolution: (version, updated_ts, device_id) — higher tuple wins.
- Dark-mode-first UI.

## License

Proprietary; internal development.
