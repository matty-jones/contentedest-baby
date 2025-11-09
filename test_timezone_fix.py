#!/usr/bin/env python3
"""
Quick test script to verify fix_timezone_offset.py works correctly.
This creates a test database, runs the migration, and verifies the results.
"""
from __future__ import annotations

import os
import sys
import tempfile
import shutil
from pathlib import Path

# Add repo root to path
repo_root = os.path.abspath(os.path.dirname(__file__))
if repo_root not in sys.path:
    sys.path.insert(0, repo_root)

def test_timezone_fix():
    """Test the timezone fix script with a temporary database."""
    print("🧪 Testing timezone fix script...")
    
    # Create a temporary database
    temp_dir = tempfile.mkdtemp()
    test_db = os.path.join(temp_dir, "test_data.db")
    
    try:
        # Set up test database path
        os.environ["TCB_DB_PATH"] = test_db
        
        # Import and set up database
        from server.app.database import Base, engine, SessionLocal
        from server.app.models import Event, GrowthData
        from server.app.crud import next_clock
        
        # Create tables
        Base.metadata.create_all(bind=engine)
        
        # Create test data with "wrong" timestamps (as if they were stored as UTC when they were actually UTC-7)
        session = SessionLocal()
        try:
            # Create a test event with timestamp that needs fixing
            # Simulate: event was at 7:27 PM local time (UTC-7), but stored as if it was 7:27 PM UTC
            # So stored timestamp is 7 hours too early
            wrong_timestamp = 1733442000  # This represents a time that needs +7 hours
            correct_timestamp = wrong_timestamp + 25200  # Add 7 hours
            
            test_event = Event(
                event_id="test_event_1",
                type="feed",
                details="Test feeding",
                start_ts=wrong_timestamp,
                end_ts=wrong_timestamp + 1800,  # 30 minutes later
                ts=None,
                created_ts=wrong_timestamp,
                updated_ts=wrong_timestamp,
                version=1,
                deleted=False,
                device_id="test_device",
                server_clock=next_clock(session)
            )
            session.add(test_event)
            session.commit()
            
            print(f"✅ Created test event with timestamp: {wrong_timestamp}")
            print(f"   Expected after fix: {correct_timestamp}")
            
        finally:
            session.close()
        
        # Run the fix script
        print("\n🔧 Running timezone fix script...")
        import subprocess
        result = subprocess.run(
            [
                sys.executable,
                "fix_timezone_offset.py",
                "--db-path", test_db,
                "--offset-hours", "7",
                "--yes"  # Skip confirmation
            ],
            capture_output=True,
            text=True
        )
        
        if result.returncode != 0:
            print(f"❌ Script failed with return code {result.returncode}")
            print("STDOUT:", result.stdout)
            print("STDERR:", result.stderr)
            return False
        
        print("✅ Script completed successfully")
        print("\nScript output:")
        print(result.stdout)
        if result.stderr:
            print("Warnings/Errors:")
            print(result.stderr)
        
        # Verify the fix was applied
        session = SessionLocal()
        try:
            event = session.get(Event, "test_event_1")
            if event is None:
                print("❌ Test event not found after fix")
                return False
            
            print(f"\n📊 Verification:")
            print(f"   start_ts after fix: {event.start_ts}")
            print(f"   Expected: {correct_timestamp}")
            
            if event.start_ts == correct_timestamp:
                print("✅ Timestamp correctly fixed!")
            else:
                print(f"❌ Timestamp mismatch! Got {event.start_ts}, expected {correct_timestamp}")
                return False
            
            if event.end_ts == correct_timestamp + 1800:
                print("✅ End timestamp correctly fixed!")
            else:
                print(f"❌ End timestamp mismatch!")
                return False
            
        finally:
            session.close()
        
        print("\n🎉 All tests passed!")
        return True
        
    except Exception as e:
        print(f"❌ Test failed with exception: {e}")
        import traceback
        traceback.print_exc()
        return False
    finally:
        # Clean up
        if os.path.exists(test_db):
            os.remove(test_db)
        shutil.rmtree(temp_dir, ignore_errors=True)
        if "TCB_DB_PATH" in os.environ:
            del os.environ["TCB_DB_PATH"]


if __name__ == "__main__":
    success = test_timezone_fix()
    sys.exit(0 if success else 1)

