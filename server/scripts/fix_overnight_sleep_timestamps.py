#!/usr/bin/env python3
"""
One-off: fix sleep rows where end_ts < start_ts by adding one day to end_ts,
unless the resulting duration would exceed 12 hours (then print and skip).

Updates version, updated_ts, and server_clock so sync/pull picks up changes.

Usage:
  export TCB_DB_PATH=/path/to/data.db
  python3 fix_overnight_sleep_timestamps.py [--dry-run]

  python3 fix_overnight_sleep_timestamps.py --db /path/to/data.db
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
    p.add_argument("--dry-run", action="store_true", help="Print actions only")
    args = p.parse_args()
    if args.db:
        os.environ["TCB_DB_PATH"] = os.path.abspath(args.db)

    # Import after TCB_DB_PATH is set (engine binds at import)
    from server.app.crud import next_clock
    from server.app.database import SessionLocal
    from server.app.models import Event
    from server.app.sleep_interval import MAX_CONTINUOUS_SLEEP_SECONDS, normalize_sleep_end_after_start

    session = SessionLocal()
    try:
        q = (
            session.query(Event)
            .filter(Event.type == "sleep")
            .filter(Event.deleted == False)  # noqa: E712
            .filter(Event.start_ts.isnot(None))
            .filter(Event.end_ts.isnot(None))
            .filter(Event.end_ts < Event.start_ts)
        )
        rows = q.order_by(Event.device_id, Event.start_ts).all()
        now = int(time.time())
        fixed = 0
        flagged = 0
        for ev in rows:
            _, e, err = normalize_sleep_end_after_start(ev.start_ts, ev.end_ts)
            if err:
                print(
                    f"FLAG (not fixed): event_id={ev.event_id} device={ev.device_id} "
                    f"start={ev.start_ts} end={ev.end_ts} (overnight +1d would exceed "
                    f"{MAX_CONTINUOUS_SLEEP_SECONDS}s)",
                    file=sys.stderr,
                )
                flagged += 1
                continue
            assert e is not None
            print(
                f"fix: event_id={ev.event_id} device={ev.device_id} end {ev.end_ts} -> {e} "
                f"(duration {e - ev.start_ts}s)"
            )
            if not args.dry_run:
                ev.end_ts = e
                ev.updated_ts = now
                ev.version = ev.version + 1
                ev.server_clock = next_clock(session)
                session.add(ev)
            fixed += 1
        if not args.dry_run:
            session.commit()
        print(f"\nDone. fixed={fixed} flagged={flagged} dry_run={args.dry_run}")
        return 0
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()


if __name__ == "__main__":
    raise SystemExit(main())
