#!/usr/bin/env python3
"""
List near-duplicate sleep pairs only (same device, start/end within 1s, different event_id).

For a full read-only audit (counts, exact duplicate groups, and configurable tolerance),
use audit_sleep_duplicates.py in this directory.

Usage:
  export TCB_DB_PATH=/path/to/data.db
  python3 find_duplicate_sleep_events.py
"""
from __future__ import annotations

import os
import sqlite3
import sys

# Default: server/data.db relative to repo
_SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
_DEFAULT_DB = os.path.join(_SCRIPT_DIR, "..", "data.db")


def main() -> int:
    db_path = os.path.abspath(os.environ.get("TCB_DB_PATH", _DEFAULT_DB))
    if not os.path.isfile(db_path):
        print(f"Database not found: {db_path}", file=sys.stderr)
        return 1

    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    cur = conn.cursor()

    # Pairs of sleep rows with different event_id, same device, same start/end (within 1s)
    cur.execute(
        """
        SELECT e1.event_id AS id1, e2.event_id AS id2, e1.device_id,
               e1.start_ts, e1.end_ts, e1.updated_ts AS u1, e2.updated_ts AS u2
        FROM events e1
        JOIN events e2
          ON e1.device_id = e2.device_id
         AND e1.type = 'sleep' AND e2.type = 'sleep'
         AND e1.event_id < e2.event_id
         AND e1.deleted = 0 AND e2.deleted = 0
         AND e1.start_ts IS NOT NULL AND e2.start_ts IS NOT NULL
         AND e1.end_ts IS NOT NULL AND e2.end_ts IS NOT NULL
         AND ABS(e1.start_ts - e2.start_ts) <= 1
         AND ABS(e1.end_ts - e2.end_ts) <= 1
        ORDER BY e1.device_id, e1.start_ts
        """
    )
    rows = cur.fetchall()
    conn.close()

    if not rows:
        print("No duplicate sleep candidates (same device, start/end within 1s) found.")
        return 0

    print(f"Found {len(rows)} candidate pair(s):\n")
    for r in rows:
        print(
            f"  device={r['device_id']} start={r['start_ts']} end={r['end_ts']}\n"
            f"    id1={r['id1']} updated={r['u1']}\n"
            f"    id2={r['id2']} updated={r['u2']}\n"
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
