#!/usr/bin/env python3
"""
Soft-delete duplicate sleep events: same device_id, start_ts, end_ts (non-deleted rows).

Keeps one row per group (lexicographically smallest event_id). Sets deleted=1, bumps
version, updated_ts, and server_clock on removed rows so clients can sync.

Usage:
  export TCB_DB_PATH=/path/to/data.db
  python3 dedupe_exact_sleep_duplicates.py [--dry-run]

  python3 dedupe_exact_sleep_duplicates.py --db /path/to/data.db
"""
from __future__ import annotations

import argparse
import os
import sys
import time

_SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
_REPO_ROOT = os.path.abspath(os.path.join(_SCRIPT_DIR, "..", ".."))
if _REPO_ROOT not in sys.path:
    sys.path.insert(0, _REPO_ROOT)


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--db", default=os.environ.get("TCB_DB_PATH"), help="SQLite path (or set TCB_DB_PATH)")
    p.add_argument("--dry-run", action="store_true")
    args = p.parse_args()
    if args.db:
        os.environ["TCB_DB_PATH"] = os.path.abspath(args.db)

    from sqlalchemy import func
    from server.app.crud import next_clock
    from server.app.database import SessionLocal
    from server.app.models import Event

    session = SessionLocal()
    now = int(time.time())
    try:
        subq = (
            session.query(
                Event.device_id,
                Event.start_ts,
                Event.end_ts,
                func.min(Event.event_id).label("keep_id"),
                func.count().label("n"),
            )
            .filter(Event.type == "sleep")
            .filter(Event.deleted == False)  # noqa: E712
            .filter(Event.start_ts.isnot(None))
            .filter(Event.end_ts.isnot(None))
            .group_by(Event.device_id, Event.start_ts, Event.end_ts)
            .having(func.count() > 1)
        )
        groups = subq.all()
        removed = 0
        for g in groups:
            keep_id = g.keep_id
            dups = (
                session.query(Event)
                .filter(Event.type == "sleep")
                .filter(Event.deleted == False)  # noqa: E712
                .filter(Event.device_id == g.device_id)
                .filter(Event.start_ts == g.start_ts)
                .filter(Event.end_ts == g.end_ts)
                .filter(Event.event_id != keep_id)
                .all()
            )
            for ev in dups:
                print(
                    f"remove duplicate: keep={keep_id} drop={ev.event_id} "
                    f"device={ev.device_id} start={ev.start_ts} end={ev.end_ts}"
                )
                if not args.dry_run:
                    ev.deleted = True
                    ev.updated_ts = now
                    ev.version = ev.version + 1
                    ev.server_clock = next_clock(session)
                    session.add(ev)
                removed += 1
        if not args.dry_run:
            session.commit()
        print(f"\nDone. duplicates_soft_deleted={removed} groups={len(groups)} dry_run={args.dry_run}")
        return 0
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()


if __name__ == "__main__":
    raise SystemExit(main())
