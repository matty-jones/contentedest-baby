#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import hashlib
import os
import sys
from datetime import datetime
from typing import Dict, Tuple, Optional


def _ensure_repo_root_on_path() -> None:
    repo_root = os.path.abspath(os.path.dirname(__file__))
    if repo_root not in sys.path:
        sys.path.insert(0, repo_root)


_ensure_repo_root_on_path()

try:
    from server.app.database import Base, engine, SessionLocal
    from server.app.models import Event
    from server.app.crud import ensure_server_clock
except Exception as import_err:  # pragma: no cover
    print(f"Failed to import server modules: {import_err}")
    print("Ensure you run this from the repository root and that Python can import the 'server.app' package.")
    sys.exit(1)


def parse_datetime(date_str: str, time_str: str) -> Optional[int]:
    time_str = (time_str or "").strip()
    if not time_str:
        return None
    fmts = [
        "%Y-%m-%d %I:%M%p",  # 2025-09-16 02:15PM
        "%Y-%m-%d %I:%M",    # 2025-09-16 02:15
        "%Y-%m-%d %H:%M",    # 2025-09-16 14:15
    ]
    for fmt in fmts:
        try:
            return int(datetime.strptime(f"{date_str} {time_str}", fmt).timestamp())
        except ValueError:
            continue
    return None


def canonical_event_key(row: Dict[str, str]) -> Tuple:
    # Build a tuple that uniquely identifies a row for deduplication
    return (
        row.get("Date", "").strip(),
        row.get("Start", "").strip(),
        row.get("End", "").strip(),
        row.get("Type", "").strip().lower(),
        row.get("Details", "").strip(),
        row.get("Raw_Text", "").strip(),
    )


def map_event_type(raw_type: str) -> Optional[str]:
    t = (raw_type or "").strip().lower()
    if t in ("sleep",):
        return "sleep"
    if t in ("feeding", "feed", "breastfeed", "bottle"):
        return "feed"
    if t in ("diaper", "nappy", "diaper_change"):
        return "nappy"
    return None


def make_event_id(key: Tuple) -> str:
    h = hashlib.sha1("|".join(key).encode("utf-8")).hexdigest()[:16]
    return f"csv_{h}"


def load_csv_and_replace_db(csv_path: str, device_id: str) -> int:
    # Use same DB path logic as server via engine already configured
    # Danger: destructive operation – we drop and recreate tables
    if not os.path.exists(csv_path):
        raise FileNotFoundError(f"CSV not found: {csv_path}")

    # Drop and recreate schema
    Base.metadata.drop_all(bind=engine)
    Base.metadata.create_all(bind=engine)

    session = SessionLocal()
    try:
        seen = set()
        inserted = 0

        with open(csv_path, "r", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            for row in reader:
                key = canonical_event_key(row)
                if key in seen:
                    continue

                mapped_type = map_event_type(row.get("Type", ""))
                if mapped_type is None:
                    continue

                date_str = row.get("Date", "").strip()
                start_ts = parse_datetime(date_str, row.get("Start", ""))
                end_ts = parse_datetime(date_str, row.get("End", ""))
                # If no valid timestamps, skip
                if start_ts is None and end_ts is None:
                    continue

                ts = start_ts if start_ts is not None else end_ts

                ev = Event(
                    event_id=make_event_id(key),
                    type=mapped_type,
                    details=(row.get("Details") or None),
                    payload={
                        "raw_text": row.get("Raw_Text") or None,
                    },
                    start_ts=start_ts,
                    end_ts=end_ts,
                    ts=ts,
                    created_ts=ts or int(datetime.utcnow().timestamp()),
                    updated_ts=ts or int(datetime.utcnow().timestamp()),
                    version=1,
                    deleted=False,
                    device_id=device_id,
                    server_clock=inserted + 1,
                )

                session.add(ev)
                seen.add(key)
                inserted += 1

                if inserted % 500 == 0:
                    session.commit()

        session.commit()

        # Update server_clock to the highest assigned
        clock = ensure_server_clock(session)
        clock.counter = inserted
        session.add(clock)
        session.commit()

        return inserted
    finally:
        session.close()


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        description=(
            "Destructively rebuild the SQLite database from a CSV file. "
            "Honors TCB_DB_PATH for the destination DB path."
        )
    )
    p.add_argument("csv", help="Path to CSV file to import")
    p.add_argument(
        "--device-id",
        default="seed_device",
        help="Device id to attribute imported events to (default: seed_device)",
    )
    return p


def main(argv: Optional[list[str]] = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    count = load_csv_and_replace_db(args.csv, args.device_id)
    print(f"Imported {count} unique events into the database.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())









