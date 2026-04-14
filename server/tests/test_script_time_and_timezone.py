from __future__ import annotations

import sqlite3
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

from import_data import parse_datetime as parse_import_datetime
from migrate_database import parse_datetime as parse_migrate_datetime


def test_parse_datetime_accepts_supported_formats_consistently():
    date_str = "2025-10-12"
    expected = parse_import_datetime(date_str, "7:35am")
    assert expected is not None

    assert parse_import_datetime(date_str, "7:35") == expected
    assert parse_import_datetime(date_str, "07:35") == expected
    assert parse_migrate_datetime(date_str, "7:35am") == expected
    assert parse_migrate_datetime(date_str, "7:35") == expected
    assert parse_migrate_datetime(date_str, "07:35") == expected


def test_parse_datetime_rejects_blank_or_invalid_values():
    assert parse_import_datetime("2025-10-12", "") is None
    assert parse_import_datetime("2025-10-12", "not-a-time") is None
    assert parse_migrate_datetime("2025-10-12", "") is None
    assert parse_migrate_datetime("2025-10-12", "not-a-time") is None


def test_fix_timezone_offset_script_applies_expected_shift(tmp_path):
    db_path = tmp_path / "timezone_fix_test.db"
    conn = sqlite3.connect(db_path)
    conn.execute(
        """
        CREATE TABLE events (
            event_id TEXT PRIMARY KEY,
            type TEXT NOT NULL,
            details TEXT,
            payload TEXT,
            start_ts INTEGER,
            end_ts INTEGER,
            ts INTEGER,
            created_ts INTEGER NOT NULL,
            updated_ts INTEGER NOT NULL,
            version INTEGER NOT NULL,
            deleted INTEGER NOT NULL,
            device_id TEXT NOT NULL,
            server_clock INTEGER NOT NULL DEFAULT 0
        )
        """
    )
    conn.execute(
        """
        CREATE TABLE growth_data (
            id TEXT PRIMARY KEY,
            device_id TEXT NOT NULL,
            category TEXT NOT NULL,
            value REAL NOT NULL,
            unit TEXT NOT NULL,
            ts INTEGER NOT NULL,
            created_ts INTEGER NOT NULL,
            updated_ts INTEGER NOT NULL,
            version INTEGER NOT NULL,
            deleted INTEGER NOT NULL,
            server_clock INTEGER NOT NULL DEFAULT 0
        )
        """
    )

    event_start = 1_733_442_000
    growth_ts = 1_733_500_000
    conn.execute(
        """
        INSERT INTO events (
            event_id, type, details, payload, start_ts, end_ts, ts,
            created_ts, updated_ts, version, deleted, device_id, server_clock
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        (
            "test_event_1",
            "feed",
            "Test feeding",
            "{}",
            event_start,
            event_start + 1800,
            event_start,
            event_start,
            event_start,
            1,
            0,
            "test_device",
            1,
        ),
    )
    conn.execute(
        """
        INSERT INTO growth_data (
            id, device_id, category, value, unit, ts,
            created_ts, updated_ts, version, deleted, server_clock
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        (
            "test_growth_1",
            "test_device",
            "weight",
            12.5,
            "lb",
            growth_ts,
            growth_ts,
            growth_ts,
            1,
            0,
            2,
        ),
    )
    conn.commit()
    conn.close()

    script_path = REPO_ROOT / "fix_timezone_offset.py"
    result = subprocess.run(
        [
            sys.executable,
            str(script_path),
            "--db-path",
            str(db_path),
            "--offset-hours",
            "7",
            "--yes",
        ],
        cwd=REPO_ROOT,
        capture_output=True,
        text=True,
    )
    assert result.returncode == 0, result.stderr or result.stdout

    conn = sqlite3.connect(db_path)
    row = conn.execute(
        "SELECT start_ts, end_ts, ts, created_ts, updated_ts FROM events WHERE event_id = ?",
        ("test_event_1",),
    ).fetchone()
    assert row == (
        event_start + 25200,
        event_start + 1800 + 25200,
        event_start + 25200,
        event_start + 25200,
        event_start + 25200,
    )

    growth_row = conn.execute(
        "SELECT ts, created_ts, updated_ts FROM growth_data WHERE id = ?",
        ("test_growth_1",),
    ).fetchone()
    assert growth_row == (
        growth_ts + 25200,
        growth_ts + 25200,
        growth_ts + 25200,
    )
    conn.close()
