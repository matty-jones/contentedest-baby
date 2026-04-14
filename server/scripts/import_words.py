#!/usr/bin/env python3
"""
Import baby words into the server SQLite database (baby_words table).

Each entry is one first-use date per word for a device. Re-running with the same
word+date+device yields the same row id (UUID5), so you can safely re-import after edits
if you bump version logic is unchanged; conflicting rows follow normal upsert rules.

JSON format (array of objects):
  [
    {"word": "mama", "date": "2026-01-10"},
    {"word": "dada", "date": "2026-02-01"}
  ]

Dates are YYYY-MM-DD and are stored as epoch seconds at 00:00:00 UTC for that calendar day.

Usage:
  export TCB_DB_PATH=/path/to/data.db   # optional; default is server/db/data.db
  python3 server/scripts/import_words.py --device-id YOUR_DEVICE_ID words.json

  python3 server/scripts/import_words.py --device-id dev --dry-run words.json
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path

_SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
_REPO_ROOT = os.path.abspath(os.path.join(_SCRIPT_DIR, "..", ".."))
if _REPO_ROOT not in sys.path:
    sys.path.insert(0, _REPO_ROOT)


def _date_to_ts_utc_midnight(date_str: str) -> int:
    dt = datetime.strptime(date_str.strip(), "%Y-%m-%d").replace(tzinfo=timezone.utc)
    return int(dt.timestamp())


def _make_word_id(device_id: str, word: str, ts: int) -> str:
    return str(
        uuid.uuid5(
            uuid.NAMESPACE_URL,
            f"tcb:baby_word:{device_id}:{word.strip().lower()}:{ts}",
        )
    )


def main() -> int:
    p = argparse.ArgumentParser(description="Import words JSON into baby_words.")
    p.add_argument("json_file", type=Path, help="Path to JSON file")
    p.add_argument("--device-id", required=True, help="Device id (must match the app)")
    p.add_argument("--db", default=os.environ.get("TCB_DB_PATH"), help="SQLite DB path (or set TCB_DB_PATH)")
    p.add_argument("--dry-run", action="store_true", help="Parse and print only; no DB writes")
    args = p.parse_args()

    if args.db:
        os.environ["TCB_DB_PATH"] = os.path.abspath(args.db)

    raw = args.json_file.read_text(encoding="utf-8")
    data = json.loads(raw)
    if not isinstance(data, list):
        print("JSON root must be an array", file=sys.stderr)
        return 1

    rows: list[tuple[str, str, int]] = []
    for i, item in enumerate(data):
        if not isinstance(item, dict):
            print(f"Item {i} must be an object", file=sys.stderr)
            return 1
        word = item.get("word")
        date_s = item.get("date")
        if not word or not isinstance(word, str) or not word.strip():
            print(f"Item {i}: missing or empty 'word'", file=sys.stderr)
            return 1
        if not date_s or not isinstance(date_s, str):
            print(f"Item {i}: missing 'date' (YYYY-MM-DD)", file=sys.stderr)
            return 1
        try:
            ts = _date_to_ts_utc_midnight(date_s)
        except ValueError as e:
            print(f"Item {i}: bad date '{date_s}': {e}", file=sys.stderr)
            return 1
        rows.append((word.strip(), date_s, ts))

    if args.dry_run:
        for w, d, ts in rows:
            wid = _make_word_id(args.device_id, w, ts)
            print(f"would upsert id={wid} word={w!r} date={d} ts={ts}")
        print(f"dry-run: {len(rows)} row(s)")
        return 0

    from server.app.crud import upsert_baby_word
    from server.app.database import Base, SessionLocal, engine
    from server.app.models import BabyWord

    Base.metadata.create_all(bind=engine)
    session = SessionLocal()
    now = int(time.time())
    try:
        for word, _date_s, ts in rows:
            wid = _make_word_id(args.device_id, word, ts)
            incoming = BabyWord(
                id=wid,
                device_id=args.device_id,
                word=word,
                ts=ts,
                created_ts=now,
                updated_ts=now,
                version=1,
                deleted=False,
            )
            upsert_baby_word(session, incoming)
            print(f"upserted {wid} {word!r} ts={ts}")
        print(f"Done: {len(rows)} word(s).")
        return 0
    finally:
        session.close()


if __name__ == "__main__":
    raise SystemExit(main())
