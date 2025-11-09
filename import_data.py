#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import json
import hashlib
import os
import sys
from datetime import datetime
from typing import Dict, Tuple, Optional, List, Any
from pathlib import Path


def _ensure_repo_root_on_path() -> None:
    repo_root = os.path.abspath(os.path.dirname(__file__))
    if repo_root not in sys.path:
        sys.path.insert(0, repo_root)


_ensure_repo_root_on_path()

try:
    from server.app.database import Base, engine, SessionLocal
    from server.app.models import Event
    from server.app.crud import ensure_server_clock, get_clock, next_clock
except Exception as import_err:  # pragma: no cover
    print(f"Failed to import server modules: {import_err}")
    print("Ensure you run this from the repository root and that Python can import the 'server.app' package.")
    sys.exit(1)


def parse_datetime(date_str: str, time_str: str) -> Optional[int]:
    """
    Parse date and time strings into epoch timestamp (UTC).
    
    Assumes the input datetime strings represent local time (UTC-7).
    Converts to UTC by adding 7 hours (25200 seconds) to the local timestamp.
    """
    time_str = (time_str or "").strip()
    if not time_str:
        return None
    fmts = [
        "%Y-%m-%d %I:%M%p",  # 2025-10-12 7:35am
        "%Y-%m-%d %I:%M",    # 2025-10-12 7:35
        "%Y-%m-%d %H:%M",    # 2025-10-12 07:35
    ]
    for fmt in fmts:
        try:
            # Parse as naive datetime (assumed to be in local timezone UTC-7)
            naive_dt = datetime.strptime(f"{date_str} {time_str}", fmt)
            # Convert to UTC by treating the naive datetime as UTC-7 and adding offset
            # This is equivalent to: local_time + 7 hours = UTC
            # We do this by getting the timestamp assuming local timezone, then adjusting
            # Since Python's timestamp() on naive datetime uses system timezone,
            # we need to explicitly handle UTC-7
            from datetime import timezone, timedelta
            # Create timezone-aware datetime in UTC-7
            tz_utc_minus_7 = timezone(timedelta(hours=-7))
            aware_dt = naive_dt.replace(tzinfo=tz_utc_minus_7)
            # Convert to UTC timestamp
            return int(aware_dt.timestamp())
        except ValueError:
            continue
    return None


def canonical_event_key(row: Dict[str, str]) -> Tuple:
    """Build a tuple that uniquely identifies a row for deduplication."""
    # Handle both CSV formats (Start_Time/End_Time vs Start/End)
    start = row.get("Start_Time", row.get("Start", "")).strip()
    end = row.get("End_Time", row.get("End", "")).strip()
    return (
        row.get("Date", "").strip(),
        start,
        end,
        row.get("Type", "").strip().lower(),
        row.get("Details", "").strip(),
        row.get("Raw_Text", "").strip(),
    )


def canonical_event_key_json(event: Dict[str, Any]) -> Tuple:
    """Build a tuple that uniquely identifies a JSON event for deduplication."""
    return (
        event.get("date", "").strip(),
        event.get("start", "").strip(),
        event.get("end", "").strip(),
        event.get("type", "").strip().lower(),
        event.get("details", "").strip(),
        event.get("raw_text", "").strip(),
    )


def map_event_type(raw_type: str) -> Optional[str]:
    """Map raw event type to server event type."""
    t = (raw_type or "").strip().lower()
    if t in ("sleep",):
        return "sleep"
    if t in ("feeding", "feed", "breastfeed", "bottle"):
        return "feed"
    if t in ("diaper", "nappy", "diaper_change"):
        return "nappy"
    return None


def make_event_id(key: Tuple) -> str:
    """Generate a deterministic event ID from a canonical key."""
    h = hashlib.sha1("|".join(key).encode("utf-8")).hexdigest()[:16]
    return f"import_{h}"


def load_csv_events(csv_path: str) -> List[Dict[str, Any]]:
    """Load events from CSV file."""
    events = []
    seen = set()
    
    with open(csv_path, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            key = canonical_event_key(row)
            if key in seen:
                continue
            seen.add(key)
            
            mapped_type = map_event_type(row.get("Type", ""))
            if mapped_type is None:
                continue
            
            date_str = row.get("Date", "").strip()
            # Handle both Start_Time/End_Time and Start/End column names
            start_time = row.get("Start_Time", row.get("Start", "")).strip()
            end_time = row.get("End_Time", row.get("End", "")).strip()
            
            start_ts = parse_datetime(date_str, start_time)
            end_ts = parse_datetime(date_str, end_time)
            
            if start_ts is None and end_ts is None:
                continue
            
            ts = start_ts if start_ts is not None else end_ts
            
            events.append({
                "event_id": make_event_id(key),
                "type": mapped_type,
                "details": row.get("Details") or None,
                "payload": {
                    "raw_text": row.get("Raw_Text") or None,
                },
                "start_ts": start_ts,
                "end_ts": end_ts,
                "ts": ts,
                "created_ts": ts or int(datetime.utcnow().timestamp()),
                "updated_ts": ts or int(datetime.utcnow().timestamp()),
                "version": 1,
                "deleted": False,
            })
    
    return events


def load_json_events(json_path: str) -> List[Dict[str, Any]]:
    """Load events from JSON file."""
    events = []
    seen = set()
    
    with open(json_path, "r", encoding="utf-8") as f:
        data = json.load(f)
    
    # Handle different JSON structures
    event_lists = []
    if "sleep_events" in data:
        event_lists.extend(data["sleep_events"])
    if "feed_events" in data:
        event_lists.extend(data["feed_events"])
    if "nappy_events" in data or "diaper_events" in data:
        event_lists.extend(data.get("nappy_events", data.get("diaper_events", [])))
    # If it's a flat list
    if isinstance(data, list):
        event_lists = data
    
    for event in event_lists:
        key = canonical_event_key_json(event)
        if key in seen:
            continue
        seen.add(key)
        
        mapped_type = map_event_type(event.get("type", ""))
        if mapped_type is None:
            continue
        
        date_str = event.get("date", "").strip()
        start_time = event.get("start", "").strip()
        end_time = event.get("end", "").strip()
        
        start_ts = parse_datetime(date_str, start_time)
        end_ts = parse_datetime(date_str, end_time)
        
        if start_ts is None and end_ts is None:
            continue
        
        ts = start_ts if start_ts is not None else end_ts
        
        events.append({
            "event_id": make_event_id(key),
            "type": mapped_type,
            "details": event.get("details") or None,
            "payload": {
                "raw_text": event.get("raw_text") or None,
            },
            "start_ts": start_ts,
            "end_ts": end_ts,
            "ts": ts,
            "created_ts": ts or int(datetime.utcnow().timestamp()),
            "updated_ts": ts or int(datetime.utcnow().timestamp()),
            "version": 1,
            "deleted": False,
        })
    
    return events


def merge_events_into_db(events: List[Dict[str, Any]], device_id: str, session) -> int:
    """Merge events into database, skipping duplicates."""
    # Ensure tables exist
    Base.metadata.create_all(bind=engine)
    
    # Get existing event IDs to avoid duplicates
    existing_ids = {row[0] for row in session.query(Event.event_id).all()}
    
    inserted = 0
    updated = 0
    skipped = 0
    
    # Get current server clock
    current_clock = get_clock(session)
    
    for event_data in events:
        event_id = event_data["event_id"]
        
        # Check if event already exists
        existing = session.get(Event, event_id)
        
        if existing:
            # Event exists, check if we should update
            # Only update if incoming version is higher or updated_ts is newer
            if (event_data["version"] > existing.version or 
                event_data["updated_ts"] > existing.updated_ts):
                # Update existing event
                for key, value in event_data.items():
                    if key != "event_id" and key != "server_clock":
                        setattr(existing, key, value)
                existing.device_id = device_id
                # Update server clock for this event
                current_clock = next_clock(session)
                existing.server_clock = current_clock
                session.add(existing)
                updated += 1
            else:
                skipped += 1
                continue
        else:
            # New event
            event = Event(
                event_id=event_id,
                type=event_data["type"],
                details=event_data["details"],
                payload=event_data["payload"],
                start_ts=event_data["start_ts"],
                end_ts=event_data["end_ts"],
                ts=event_data["ts"],
                created_ts=event_data["created_ts"],
                updated_ts=event_data["updated_ts"],
                version=event_data["version"],
                deleted=event_data["deleted"],
                device_id=device_id,
                server_clock=0,  # Will be set below
            )
            # Assign server clock
            current_clock = next_clock(session)
            event.server_clock = current_clock
            session.add(event)
            inserted += 1
        
        # Commit in batches
        if (inserted + updated) % 500 == 0:
            session.commit()
            print(f"Processed {inserted + updated} events...")
    
    session.commit()
    return inserted, updated, skipped


def main():
    parser = argparse.ArgumentParser(
        description="Import CSV or JSON data into the database, merging with existing data."
    )
    parser.add_argument(
        "data_file",
        help="Path to CSV or JSON file to import"
    )
    parser.add_argument(
        "--device-id",
        default="seed_device",
        help="Device id to attribute imported events to (default: seed_device)",
    )
    parser.add_argument(
        "--db-path",
        help="Override database path (uses TCB_DB_PATH env var or default)",
    )
    args = parser.parse_args()
    
    # Set database path if provided
    if args.db_path:
        os.environ["TCB_DB_PATH"] = args.db_path
    
    data_file = Path(args.data_file)
    if not data_file.exists():
        print(f"Error: Data file not found: {data_file}")
        return 1
    
    # Load events based on file extension
    if data_file.suffix.lower() == ".csv":
        print(f"Loading events from CSV: {data_file}")
        events = load_csv_events(str(data_file))
    elif data_file.suffix.lower() == ".json":
        print(f"Loading events from JSON: {data_file}")
        events = load_json_events(str(data_file))
    else:
        print(f"Error: Unsupported file format. Expected .csv or .json, got {data_file.suffix}")
        return 1
    
    print(f"Loaded {len(events)} unique events from file")
    
    # Merge into database
    session = SessionLocal()
    try:
        print("Merging events into database...")
        inserted, updated, skipped = merge_events_into_db(events, args.device_id, session)
        print(f"\nImport complete:")
        print(f"  Inserted: {inserted} new events")
        print(f"  Updated: {updated} existing events")
        print(f"  Skipped: {skipped} duplicate events")
        print(f"  Total processed: {inserted + updated + skipped}")
        
        # Show final counts
        total_events = session.query(Event).count()
        print(f"\nDatabase now contains {total_events} total events")
        
        return 0
    except Exception as e:
        session.rollback()
        print(f"Error importing data: {e}")
        import traceback
        traceback.print_exc()
        return 1
    finally:
        session.close()


if __name__ == "__main__":
    raise SystemExit(main())
