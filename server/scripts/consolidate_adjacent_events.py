#!/usr/bin/env python3
"""
Merge sleep and feed rows on the same device when consecutive segments have gap <= 60s
or overlap. Keeps the earlier-start row, expands end_ts, soft-deletes the later row.

Only considers rows with start_ts >= 2026-01-01 00:00 UTC so older seed/import data is left
unchanged. Live crib/sync merge behavior is unchanged (no cutoff there).

Apply mode merges consecutive rows in (start_ts, event_id) order using a fast path (no per-merge
overlap table scans). Same merge rules as dry-run / runtime merge_adjacent_chain.

Run after backup. Typically run before delete_short_sleep_events.py.

Usage:
  python3 consolidate_adjacent_events.py --db /path/to/data.db
  python3 consolidate_adjacent_events.py --db /path/to/data.db --dry-run
"""
from __future__ import annotations

import argparse
import os
import sys
from datetime import datetime, timezone

_SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
_REPO_ROOT = os.path.abspath(os.path.join(_SCRIPT_DIR, "..", ".."))
if _REPO_ROOT not in sys.path:
    sys.path.insert(0, _REPO_ROOT)

# Epoch for 2026-01-01 00:00:00 UTC; only these rows are eligible for this script.
CONSOLIDATION_MIN_START_TS = int(datetime(2026, 1, 1, tzinfo=timezone.utc).timestamp())

def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--db", default=os.environ.get("TCB_DB_PATH"))
    p.add_argument("--dry-run", action="store_true")
    args = p.parse_args()
    if args.db:
        os.environ["TCB_DB_PATH"] = os.path.abspath(args.db)

    from sqlalchemy import or_
    from server.app.crud import try_merge_first_mergeable_pair_for_device_type
    from server.app.database import SessionLocal
    from server.app.event_policy import intervals_mergeable_ordered
    from server.app.models import Event

    min_ts = CONSOLIDATION_MIN_START_TS
    session = SessionLocal()
    try:
        evs = (
            session.query(Event)
            .filter(
                or_(Event.type == "sleep", Event.type == "feed"),
                Event.deleted == False,  # noqa: E712
                Event.start_ts.isnot(None),
                Event.end_ts.isnot(None),
                Event.start_ts >= min_ts,
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
            print(f"dry-run: {n} merge pair(s) (start_ts >= {min_ts} UTC)")
            return 0

        # Per (device, type): merge consecutive sorted pairs until stable. Each step is O(n)
        # for one query over that group; no expensive overlap scan (unlike merge_adjacent_chain).
        groups = sorted(by_group.keys())
        total_merges = 0
        for device_id, typ in groups:
            while try_merge_first_mergeable_pair_for_device_type(
                session, device_id, typ, min_ts
            ):
                total_merges += 1
        print(f"Done. merges={total_merges}")
        return 0
    finally:
        session.close()


if __name__ == "__main__":
    raise SystemExit(main())
