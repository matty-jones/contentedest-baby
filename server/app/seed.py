from __future__ import annotations

import csv
import logging
import os

from sqlalchemy.orm import Session

from . import crud
from .models import Event
from .sleep_interval import normalize_sleep_end_after_start
from .timeparse import parse_local_utc_minus_7_to_utc_ts

logger = logging.getLogger(__name__)


def seed_database(db: Session) -> None:
    """Seed the database with sample data from CSV file."""
    csv_path = "/home/blasky/Projects/extractedest-baby/complete_historical_data/screenshot_processed_data_20250916_212254.csv"

    if not os.path.exists(csv_path):
        logger.warning("CSV file not found at %s", csv_path)
        return

    existing_events = db.query(Event).count()
    if existing_events > 0:
        logger.info("Database already has %s events, skipping seed", existing_events)
        return

    logger.info("Seeding database with CSV data...")
    try:
        with open(csv_path, "r", encoding="utf-8") as csvfile:
            reader = csv.DictReader(csvfile)
            events_added = 0

            for row in reader:
                try:
                    date_str = row["Date"]
                    start_time_str = row["Start"].strip()
                    end_time_str = row["End"].strip()

                    if not start_time_str or not end_time_str:
                        logger.warning("Skipping row with missing times: %s", row)
                        continue

                    start_ts = parse_local_utc_minus_7_to_utc_ts(
                        date_str,
                        start_time_str,
                        formats=("%Y-%m-%d %I:%M%p", "%Y-%m-%d %I:%M"),
                    )
                    end_ts = parse_local_utc_minus_7_to_utc_ts(
                        date_str,
                        end_time_str,
                        formats=("%Y-%m-%d %I:%M%p", "%Y-%m-%d %I:%M"),
                    )
                    if start_ts is None or end_ts is None:
                        logger.warning("Could not parse times for row: %s", row)
                        continue

                    event_type = row["Type"]
                    if event_type == "sleep":
                        event_type = "sleep"
                    elif event_type == "feeding":
                        event_type = "feed"
                    elif event_type == "diaper":
                        event_type = "nappy"

                    if event_type == "sleep":
                        start_ts, end_ts, err = normalize_sleep_end_after_start(
                            start_ts, end_ts
                        )
                        if err:
                            logger.warning(
                                "Skipping seed row: overnight fix would exceed 12h: %s", row
                            )
                            continue

                    event = Event(
                        event_id=f"seed_{events_added}_{start_ts}",
                        type=event_type,
                        payload={
                            "details": row["Details"],
                            "raw_text": row["Raw_Text"],
                        },
                        start_ts=start_ts,
                        end_ts=end_ts,
                        ts=start_ts,
                        created_ts=start_ts,
                        updated_ts=start_ts,
                        version=1,
                        deleted=False,
                        device_id="seed_device",
                        server_clock=events_added + 1,
                    )
                    db.add(event)
                    events_added += 1

                    if events_added % 100 == 0:
                        db.commit()
                        logger.info("Added %s events...", events_added)
                except Exception as exc:
                    logger.warning("Failed to process row %s: %s", row, exc)
                    continue

            db.commit()
            server_clock = crud.ensure_server_clock(db)
            server_clock.counter = events_added
            db.add(server_clock)
            db.commit()
            logger.info("Successfully seeded database with %s events", events_added)
    except Exception as exc:
        logger.error("Failed to seed database: %s", exc)
