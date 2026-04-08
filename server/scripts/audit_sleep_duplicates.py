#!/usr/bin/env python3
"""
Audit SQLite events for duplicate sleep rows (read-only).

Reports:
  1) Summary counts (sleep rows, deleted vs active)
  2) Exact duplicate groups: same device_id + start_ts + end_ts, multiple event_ids
  3) Near-duplicate pairs: same device, start/end within --tolerance-seconds, different event_id

Does not modify the database.

Usage on server:
  export TCB_DB_PATH=/path/to/data.db
  python3 audit_sleep_duplicates.py
  python3 audit_sleep_duplicates.py --db /path/to/data.db --tolerance-seconds 60
"""
from __future__ import annotations

import argparse
import os
import sqlite3
import sys

_SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
_DEFAULT_DB = os.path.join(_SCRIPT_DIR, "..", "data.db")


def main() -> int:
    p = argparse.ArgumentParser(description="Audit duplicate sleep events (read-only)")
    p.add_argument(
        "--db",
        default=os.environ.get("TCB_DB_PATH", _DEFAULT_DB),
        help="Path to SQLite DB (default: TCB_DB_PATH or server/data.db)",
    )
    p.add_argument(
        "--tolerance-seconds",
        type=int,
        default=1,
        help="Max |delta| on start_ts and end_ts for near-duplicate pairs (default: 1)",
    )
    args = p.parse_args()
    db_path = os.path.abspath(args.db)
    tol = max(0, args.tolerance_seconds)

    if not os.path.isfile(db_path):
        print(f"Database not found: {db_path}", file=sys.stderr)
        return 1

    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    cur = conn.cursor()

    print(f"Database: {db_path}\n")

    # --- Summary ---
    cur.execute(
        """
        SELECT
          COUNT(*) AS total,
          SUM(CASE WHEN deleted = 0 THEN 1 ELSE 0 END) AS active,
          SUM(CASE WHEN deleted = 1 THEN 1 ELSE 0 END) AS deleted,
          SUM(CASE WHEN type = 'sleep' AND deleted = 0 THEN 1 ELSE 0 END) AS sleep_active,
          SUM(CASE WHEN type = 'sleep' AND deleted = 0 AND start_ts IS NOT NULL AND end_ts IS NOT NULL THEN 1 ELSE 0 END) AS sleep_closed_active
        FROM events
        """
    )
    s = cur.fetchone()
    print("=== Summary (events table) ===")
    print(f"  Total rows:        {s['total']}")
    print(f"  Active (not del):  {s['active']}")
    print(f"  Deleted:           {s['deleted']}")
    print(f"  Sleep (active):    {s['sleep_active']}")
    print(f"  Sleep closed:      {s['sleep_closed_active']}")
    print()

    # --- Exact duplicate groups (identical device + start + end) ---
    cur.execute(
        """
        SELECT device_id, start_ts, end_ts, COUNT(*) AS n,
               GROUP_CONCAT(event_id) AS event_ids
        FROM events
        WHERE deleted = 0 AND type = 'sleep'
          AND start_ts IS NOT NULL AND end_ts IS NOT NULL
        GROUP BY device_id, start_ts, end_ts
        HAVING COUNT(*) > 1
        ORDER BY n DESC, device_id, start_ts
        """
    )
    exact = cur.fetchall()
    print("=== Exact duplicate groups (same device_id, start_ts, end_ts) ===")
    if not exact:
        print("  None.\n")
    else:
        print(f"  {len(exact)} group(s):\n")
        for r in exact:
            print(
                f"  count={r['n']} device={r['device_id']} start={r['start_ts']} end={r['end_ts']}\n"
                f"    event_ids: {r['event_ids']}\n"
            )

    # --- Near-duplicate pairs (different event_id, within tolerance) ---
    cur.execute(
        f"""
        SELECT e1.event_id AS id1, e2.event_id AS id2, e1.device_id,
               e1.start_ts AS s1, e2.start_ts AS s2, e1.end_ts AS e1e, e2.end_ts AS e2e,
               e1.updated_ts AS u1, e2.updated_ts AS u2
        FROM events e1
        JOIN events e2
          ON e1.device_id = e2.device_id
         AND e1.type = 'sleep' AND e2.type = 'sleep'
         AND e1.event_id < e2.event_id
         AND e1.deleted = 0 AND e2.deleted = 0
         AND e1.start_ts IS NOT NULL AND e2.start_ts IS NOT NULL
         AND e1.end_ts IS NOT NULL AND e2.end_ts IS NOT NULL
         AND ABS(e1.start_ts - e2.start_ts) <= ?
         AND ABS(e1.end_ts - e2.end_ts) <= ?
        ORDER BY e1.device_id, e1.start_ts
        """,
        (tol, tol),
    )
    near = cur.fetchall()
    print(f"=== Near-duplicate pairs (|start_ts| and |end_ts| deltas <= {tol}s) ===")
    if not near:
        print("  None.\n")
    else:
        print(f"  {len(near)} pair(s):\n")
        for r in near:
            print(
                f"  device={r['device_id']}\n"
                f"    id1={r['id1']} start={r['s1']} end={r['e1e']} updated={r['u1']}\n"
                f"    id2={r['id2']} start={r['s2']} end={r['e2e']} updated={r['u2']}\n"
            )

    conn.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
