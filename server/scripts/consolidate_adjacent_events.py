#!/usr/bin/env python3
"""
Merge sleep and feed rows on the same device when consecutive segments have gap <= 60s
or overlap. Keeps the earlier-start row, expands end_ts, soft-deletes the later row.

Run after backup. Typically run before delete_short_sleep_events.py.

Usage:
  python3 consolidate_adjacent_events.py --db /path/to/data.db
  python3 consolidate_adjacent_events.py --db /path/to/data.db --dry-run
"""
from __future__ import annotations

import argparse
import os
import sys

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

    from sqlalchemy import or_
    from server.app.crud import merge_adjacent_chain
    from server.app.database import SessionLocal
    from server.app.event_policy import intervals_mergeable_ordered
    from server.app.models import Event

    session = SessionLocal()
    try:
        evs = (
            session.query(Event)
            .filter(
                or_(Event.type == "sleep", Event.type == "feed"),
                Event.deleted == False,  # noqa: E712
                Event.start_ts.isnot(None),
                Event.end_ts.isnot(None),
            )
            .order_by(Event.device_id, Event.type, Event.start_ts)
            .all()
        )
        by_group: dict[tuple[str, str], list] = {}
        for ev in evs:
            by_group.setdefault((ev.device_id, ev.type), []).append(ev)

        if args.dry_run:
            n = 0
            for _key, lst in sorted(by_group.items()):
                for i in range(len(lst) - 1):
                    a, b = lst[i], lst[i + 1]
                    if intervals_mergeable_ordered(a.start_ts, a.end_ts, b.start_ts, b.end_ts):
                        print(f"would_merge device={a.device_id} type={a.type} keep={a.event_id} drop={b.event_id}")
                        n += 1
            print(f"dry-run: {n} merge pair(s)")
            return 0

        rounds = 0
        total_merges = 0
        while True:
            evs = (
                session.query(Event)
                .filter(
                    or_(Event.type == "sleep", Event.type == "feed"),
                    Event.deleted == False,  # noqa: E712
                    Event.start_ts.isnot(None),
                    Event.end_ts.isnot(None),
                )
                .order_by(Event.device_id, Event.type, Event.start_ts)
                .all()
            )
            if len(evs) < 2:
                break
            progressed = False
            for ev in evs:
                fresh = session.get(Event, ev.event_id)
                if fresh is None or fresh.deleted:
                    continue
                before = fresh.event_id
                merge_adjacent_chain(session, fresh)
                gone = session.get(Event, before)
                if gone is None or gone.deleted:
                    progressed = True
                    total_merges += 1
                    break
            rounds += 1
            if not progressed:
                break
            if rounds > 10000:
                print("Aborting: too many rounds", file=sys.stderr)
                return 1
        print(f"Done. rounds={rounds} merges={total_merges}")
        return 0
    finally:
        session.close()


if __name__ == "__main__":
    raise SystemExit(main())
