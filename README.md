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

```
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000 --app-dir server
```

3) Run tests:

```
. server/.venv/bin/activate
PYTHONPATH=server pytest -q server/tests
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
