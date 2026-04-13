#!/usr/bin/env python3
"""
Soft-delete sleep events whose duration is strictly less than 5 minutes (300 seconds).
Run after consolidate_adjacent_events.py if you want short fragments removed post-merge.

Usage:
  python3 delete_short_sleep_events.py --db /path/to/data.db [--dry-run]
"""
from __future__ import annotations

import argparse
import os
import sys
import time

_SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
_REPO_ROOT = os.path.abspath(os.path.join(_SCRIPT_DIR, "..", ".."))
if _REPO_ROOT not in sys.path:
    sys.path.insert(0, _REPO_ROOT))


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--db", default=os.environ.get("TCB_DB_PATH"))
    p.add_argument("--dry-run", action="store_true")
    args = p.parse_args()
    if args.db:
        os.environ["TCB_DB_PATH"] = os.path.abspath(args.db)

    from server.app.crud import next_clock
    from server.app.database import SessionLocal
    from server.app.event_policy import MIN_SLEEP_TO_KEEP_SECONDS
    from server.app.models import Event

    session = SessionLocal()
    now_ts = int(time.time())
    try:
        q = (
            session.query(Event)
            .filter(Event.type == "sleep")
            .filter(Event.deleted == False)  # noqa: E712
            .filter(Event.start_ts.isnot(None))
            .filter(Event.end_ts.isnot(None))
            .filter(Event.end_ts > Event.start_ts)
        )
        rows = [e for e in q.all() if (e.end_ts - e.start_ts) < MIN_SLEEP_TO_KEEP_SECONDS]
        if args.dry_run:
            print(f"dry-run: would soft-delete {len(rows)} sleep row(s) shorter than {MIN_SLEEP_TO_KEEP_SECONDS}s")
            for ev in rows[:50]:
                dur = ev.end_ts - ev.start_ts
                print(f"  {ev.event_id} device={ev.device_id} duration={dur}s")
            if len(rows) > 50:
                print(f"  ... and {len(rows) - 50} more")
            return 0
        for ev in rows:
            ev.deleted = True
            ev.updated_ts = now_ts
            ev.version = ev.version + 1
            ev.server_clock = next_clock(session)
            session.add(ev)
        session.commit()
        print(f"Soft-deleted {len(rows)} short sleep event(s).")
        return 0
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()


if __name__ == "__main__":
    raise SystemExit(main())
