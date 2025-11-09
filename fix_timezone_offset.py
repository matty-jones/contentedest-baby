#!/usr/bin/env python3
"""
Fix timezone offset in database timestamps.

This script corrects timestamps that were stored as if local time (UTC-7) was UTC.
It adds the specified offset (default 7 hours = 25200 seconds) to all timestamp fields.

Usage:
    # Dry run (preview changes without applying)
    ./fix_timezone_offset.py --dry-run

    # Apply fix with default 7-hour offset
    ./fix_timezone_offset.py

    # Apply fix with custom offset (in seconds)
    ./fix_timezone_offset.py --offset 25200

    # Apply fix with custom offset (in hours)
    ./fix_timezone_offset.py --offset-hours 7

    # Use custom database path
    ./fix_timezone_offset.py --db-path /path/to/data.db
"""
from __future__ import annotations

import argparse
import os
import sys
from datetime import datetime
from typing import Optional

# Add repo root to path
repo_root = os.path.abspath(os.path.dirname(__file__))
if repo_root not in sys.path:
    sys.path.insert(0, repo_root)

# Note: We import database connection modules later, after TCB_DB_PATH may be set
# This allows the --db-path argument to override the default path
# Models can be imported at module level since they don't depend on DB path
from server.app.models import Event, GrowthData


def format_timestamp(ts: Optional[int]) -> str:
    """Format timestamp for display."""
    if ts is None:
        return "NULL"
    try:
        dt = datetime.fromtimestamp(ts)
        return f"{dt.strftime('%Y-%m-%d %H:%M:%S')} ({ts})"
    except Exception:
        return str(ts)


def preview_changes(session, offset_seconds: int) -> tuple[int, int]:
    """Preview changes that would be made."""
    events = session.query(Event).all()
    growth_data = session.query(GrowthData).all()
    
    event_count = 0
    growth_count = 0
    
    print("\n=== PREVIEW OF CHANGES ===\n")
    print(f"Offset to apply: {offset_seconds} seconds ({offset_seconds / 3600:.1f} hours)\n")
    
    # Preview events
    print("EVENTS:")
    for event in events[:10]:  # Show first 10 as examples
        changes = []
        if event.start_ts is not None:
            old = format_timestamp(event.start_ts)
            new = format_timestamp(event.start_ts + offset_seconds)
            changes.append(f"  start_ts: {old} → {new}")
        if event.end_ts is not None:
            old = format_timestamp(event.end_ts)
            new = format_timestamp(event.end_ts + offset_seconds)
            changes.append(f"  end_ts: {old} → {new}")
        if event.ts is not None:
            old = format_timestamp(event.ts)
            new = format_timestamp(event.ts + offset_seconds)
            changes.append(f"  ts: {old} → {new}")
        if event.created_ts is not None:
            old = format_timestamp(event.created_ts)
            new = format_timestamp(event.created_ts + offset_seconds)
            changes.append(f"  created_ts: {old} → {new}")
        if event.updated_ts is not None:
            old = format_timestamp(event.updated_ts)
            new = format_timestamp(event.updated_ts + offset_seconds)
            changes.append(f"  updated_ts: {old} → {new}")
        
        if changes:
            event_count += 1
            print(f"\nEvent {event.event_id} ({event.type}):")
            for change in changes:
                print(change)
    
    if len(events) > 10:
        print(f"\n... and {len(events) - 10} more events")
    
    # Count total events that will be updated
    total_events = len(events)
    
    # Preview growth data
    if growth_data:
        print("\n\nGROWTH DATA:")
        for gd in growth_data[:5]:  # Show first 5 as examples
            if gd.ts is not None:
                old = format_timestamp(gd.ts)
                new = format_timestamp(gd.ts + offset_seconds)
                print(f"\nGrowth {gd.id} ({gd.category}):")
                print(f"  ts: {old} → {new}")
                growth_count += 1
        
        if len(growth_data) > 5:
            print(f"\n... and {len(growth_data) - 5} more growth records")
    
    total_growth = len(growth_data)
    
    print(f"\n\n=== SUMMARY ===")
    print(f"Total events to update: {total_events}")
    print(f"Total growth data to update: {total_growth}")
    print(f"Feed segments will also be updated (if table exists)")
    
    return total_events, total_growth


def apply_fix(session, offset_seconds: int, dry_run: bool = False) -> dict:
    """Apply timezone offset fix to all timestamps."""
    stats = {
        "events_updated": 0,
        "growth_updated": 0,
        "segments_updated": 0,
    }
    
    if dry_run:
        print("\n=== DRY RUN MODE - No changes will be applied ===\n")
        preview_changes(session, offset_seconds)
        return stats
    
    print(f"\n=== APPLYING FIX (offset: {offset_seconds} seconds) ===\n")
    
    # Update events table
    # IMPORTANT: We update created_ts and updated_ts to maintain relative ordering,
    # but this means server versions will win in conflict resolution after migration.
    # This is intentional - we want the corrected timestamps to win.
    print("Updating events table...")
    result = session.execute(
        text("""
            UPDATE events SET
                start_ts = CASE WHEN start_ts IS NOT NULL THEN start_ts + :offset ELSE NULL END,
                end_ts = CASE WHEN end_ts IS NOT NULL THEN end_ts + :offset ELSE NULL END,
                ts = CASE WHEN ts IS NOT NULL THEN ts + :offset ELSE NULL END,
                created_ts = created_ts + :offset,
                updated_ts = updated_ts + :offset
        """),
        {"offset": offset_seconds}
    )
    stats["events_updated"] = result.rowcount
    print(f"  Updated {stats['events_updated']} events")
    
    # Update feed_segments table if it exists (Android local database)
    # Note: This table exists in Android Room database, not server database
    # But we'll check for it in case it's been synced
    try:
        result = session.execute(
            text("""
                UPDATE feed_segments SET
                    start_ts = start_ts + :offset,
                    end_ts = end_ts + :offset
                WHERE EXISTS (SELECT 1 FROM sqlite_master WHERE type='table' AND name='feed_segments')
            """),
            {"offset": offset_seconds}
        )
        stats["segments_updated"] = result.rowcount
        if stats["segments_updated"] > 0:
            print(f"  Updated {stats['segments_updated']} feed segments")
    except Exception as e:
        print(f"  Note: feed_segments table not found or not accessible: {e}")
    
    # Update growth_data table
    print("Updating growth_data table...")
    result = session.execute(
        text("""
            UPDATE growth_data SET
                ts = ts + :offset,
                created_ts = created_ts + :offset,
                updated_ts = updated_ts + :offset
        """),
        {"offset": offset_seconds}
    )
    stats["growth_updated"] = result.rowcount
    print(f"  Updated {stats['growth_updated']} growth data records")
    
    return stats


def main():
    parser = argparse.ArgumentParser(
        description="Fix timezone offset in database timestamps",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__
    )
    parser.add_argument(
        "--offset",
        type=int,
        help="Offset in seconds to add to timestamps (default: 25200 = 7 hours)"
    )
    parser.add_argument(
        "--offset-hours",
        type=float,
        help="Offset in hours to add to timestamps (converted to seconds)"
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Preview changes without applying them"
    )
    parser.add_argument(
        "--db-path",
        help="Override database path (uses TCB_DB_PATH env var or default)"
    )
    parser.add_argument(
        "--yes",
        action="store_true",
        help="Skip confirmation prompt (use with caution!)"
    )
    
    args = parser.parse_args()
    
    # Set database path if provided
    if args.db_path:
        os.environ["TCB_DB_PATH"] = args.db_path
    
    # Import database modules after TCB_DB_PATH may have been set
    try:
        from server.app import database
        from server.app.database import engine, SessionLocal
        # Re-import models to ensure they're available (in case initial import failed)
        from server.app.models import Event, GrowthData
        from sqlalchemy import text
        # Get the resolved database path
        DB_PATH = database.DB_PATH
    except Exception as import_err:
        print(f"Failed to import server modules: {import_err}")
        print("Ensure you run this from the repository root and that Python can import the 'server.app' package.")
        import traceback
        traceback.print_exc()
        return 1
    
    # Calculate offset
    if args.offset_hours is not None:
        offset_seconds = int(args.offset_hours * 3600)
    elif args.offset is not None:
        offset_seconds = args.offset
    else:
        # Default: 7 hours = 25200 seconds
        offset_seconds = 25200
    
    # Get the actual resolved database path
    actual_db_path = os.path.abspath(DB_PATH)
    
    print(f"Timezone Offset Fix Script")
    print(f"Database: {actual_db_path}")
    if not os.path.exists(actual_db_path):
        print(f"❌ ERROR: Database file not found at: {actual_db_path}")
        print(f"\nPlease specify the correct path using:")
        print(f"  --db-path /path/to/data.db")
        print(f"  or set TCB_DB_PATH environment variable")
        return 1
    print(f"Offset: {offset_seconds} seconds ({offset_seconds / 3600:.1f} hours)")
    
    session = SessionLocal()
    try:
        # Preview changes
        if args.dry_run:
            preview_changes(session, offset_seconds)
            print("\n=== DRY RUN COMPLETE ===")
            print("Run without --dry-run to apply changes")
            return 0
        
        # Show preview before applying
        print("\n" + "="*60)
        preview_changes(session, offset_seconds)
        print("="*60)
        
        # Confirm before applying
        if not args.yes:
            print("\n⚠️  WARNING: This will modify timestamps in the database!")
            response = input("Continue? (yes/no): ").strip().lower()
            if response not in ("yes", "y"):
                print("Aborted.")
                return 1
        
        # Apply fix
        stats = apply_fix(session, offset_seconds, dry_run=False)
        
        # Commit changes
        session.commit()
        
        print("\n=== FIX APPLIED SUCCESSFULLY ===")
        print(f"Events updated: {stats['events_updated']}")
        print(f"Growth data updated: {stats['growth_updated']}")
        if stats['segments_updated'] > 0:
            print(f"Feed segments updated: {stats['segments_updated']}")
        print("\n✅ All timestamps have been adjusted.")
        print("⚠️  Note: Android app local database will need to re-sync from server.")
        
        return 0
        
    except Exception as e:
        session.rollback()
        print(f"\n❌ Error applying fix: {e}")
        import traceback
        traceback.print_exc()
        return 1
    finally:
        session.close()


if __name__ == "__main__":
    raise SystemExit(main())

