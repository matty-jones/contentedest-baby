#!/usr/bin/env python3
"""
Import growth data from JSON file into the server database.

Usage:
    python import_growth_data.py TEMP/growth_data.json [--device-id device_id]
"""

from __future__ import annotations

import argparse
import json
import hashlib
import os
import sys
from datetime import datetime
from typing import Dict, List, Any
from pathlib import Path


def _ensure_repo_root_on_path() -> None:
    repo_root = os.path.abspath(os.path.dirname(__file__))
    if repo_root not in sys.path:
        sys.path.insert(0, repo_root)


_ensure_repo_root_on_path()

try:
    from server.app.database import Base, engine, SessionLocal
    from server.app.models import GrowthData
    from server.app.crud import ensure_server_clock, get_clock, next_clock, upsert_growth_data
except Exception as import_err:  # pragma: no cover
    print(f"Failed to import server modules: {import_err}")
    print("Ensure you run this from the repository root and that Python can import the 'server.app' package.")
    sys.exit(1)


def make_growth_id(device_id: str, category: str, ts: int) -> str:
    """Generate a unique ID for growth data entry."""
    key = f"{device_id}_{category}_{ts}"
    h = hashlib.sha256(key.encode()).hexdigest()[:16]
    return f"growth_{h}"


def parse_date(date_str: str) -> int:
    """Parse ISO date string to epoch timestamp."""
    try:
        # Handle ISO format with or without time
        if 'T' in date_str:
            dt = datetime.fromisoformat(date_str.replace('Z', '+00:00'))
        else:
            dt = datetime.fromisoformat(date_str)
        return int(dt.timestamp())
    except ValueError as e:
        print(f"Error parsing date '{date_str}': {e}")
        raise


def import_growth_data(json_path: str, device_id: str) -> int:
    """Import growth data from JSON file into database."""
    # Ensure all tables exist
    Base.metadata.create_all(bind=engine)
    
    db = SessionLocal()
    try:
        # Ensure server clock exists
        ensure_server_clock(db)
        
        # Load JSON file
        with open(json_path, 'r') as f:
            data = json.load(f)
        
        if not isinstance(data, list):
            print(f"Error: JSON file must contain an array of growth data entries")
            return 0
        
        print(f"Found {len(data)} growth data entries in {json_path}")
        
        imported_count = 0
        skipped_count = 0
        
        for entry in data:
            try:
                # Validate required fields
                if 'date' not in entry or 'category' not in entry or 'value' not in entry or 'unit' not in entry:
                    print(f"Skipping entry missing required fields: {entry}")
                    skipped_count += 1
                    continue
                
                # Parse date
                ts = parse_date(entry['date'])
                
                # Validate category
                category = entry['category'].lower()
                if category not in ['weight', 'height', 'head']:
                    print(f"Skipping entry with invalid category '{category}': {entry}")
                    skipped_count += 1
                    continue
                
                # Get value and unit
                value = float(entry['value'])
                unit = entry['unit']
                
                # Generate ID
                growth_id = make_growth_id(device_id, category, ts)
                
                # Check if entry already exists
                existing = db.get(GrowthData, growth_id)
                if existing:
                    print(f"Entry already exists for {category} at {entry['date']}, skipping...")
                    skipped_count += 1
                    continue
                
                # Create growth data entry
                now = int(datetime.now().timestamp())
                growth_data = GrowthData(
                    id=growth_id,
                    device_id=device_id,
                    category=category,
                    value=value,
                    unit=unit,
                    ts=ts,
                    created_ts=now,
                    updated_ts=now,
                    version=1,
                    deleted=False,
                    server_clock=0  # Will be set by upsert_growth_data
                )
                
                # Upsert into database
                applied_data, new_clock = upsert_growth_data(db, growth_data)
                imported_count += 1
                
                if imported_count % 10 == 0:
                    print(f"Imported {imported_count} entries...")
                
            except Exception as e:
                print(f"Error processing entry {entry}: {e}")
                skipped_count += 1
                continue
        
        print(f"\nImport complete:")
        print(f"  Imported: {imported_count} entries")
        print(f"  Skipped: {skipped_count} entries")
        
        return imported_count
        
    finally:
        db.close()


def main():
    parser = argparse.ArgumentParser(description='Import growth data from JSON file')
    parser.add_argument('json_file', type=str, help='Path to JSON file with growth data')
    parser.add_argument('--device-id', type=str, default='import_device',
                       help='Device ID to use for imported entries (default: import_device)')
    
    args = parser.parse_args()
    
    json_path = Path(args.json_file)
    if not json_path.exists():
        print(f"Error: JSON file not found: {json_path}")
        sys.exit(1)
    
    print(f"Importing growth data from {json_path}")
    print(f"Using device ID: {args.device_id}")
    
    imported = import_growth_data(str(json_path), args.device_id)
    
    if imported > 0:
        print(f"\nSuccessfully imported {imported} growth data entries!")
    else:
        print("\nNo entries were imported.")


if __name__ == '__main__':
    main()

