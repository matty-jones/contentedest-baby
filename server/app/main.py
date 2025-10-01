from __future__ import annotations
import time
import logging
import csv
import os
from pathlib import Path
from fastapi import FastAPI, Depends, Request
import shutil
import glob
from sqlalchemy.orm import Session
from .database import Base, engine, SessionLocal
from .database import DB_PATH as ACTIVE_DB_PATH
from .models import Device, Event
from .schemas import PairRequest, PairResponse, EventDTO, SyncPushResponse, SyncPushResponseItem, SyncPullResponse
from .security import mint_token, token_hash
from .auth import get_current_device, get_db
from . import crud


app = FastAPI(title="The Contentedest Baby Server")

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

@app.middleware("http")
async def log_requests(request: Request, call_next):
    logger.info(f"Request: {request.method} {request.url}")
    response = await call_next(request)
    logger.info(f"Response: {response.status_code}")
    return response

Base.metadata.create_all(bind=engine)

def _active_db_file() -> str:
    return os.path.abspath(ACTIVE_DB_PATH)


def bootstrap_database_if_empty(db: Session) -> None:
    """If active DB has 0 events, try to initialize it from a committed initial DB.

    Looks for a file named 'initial_data.db' under the server directory (sibling to data.db).
    If found and current DB has no events, copies it over the active DB file.
    """
    try:
        current_count = db.query(Event).count()
    except Exception:
        current_count = 0

    if current_count > 0:
        logger.info("Database already contains events; skipping bootstrap from initial DB")
        return

    server_dir = os.path.dirname(os.path.dirname(__file__))
    initial_db_path = os.path.join(server_dir, "initial_data.db")
    if os.path.exists(initial_db_path):
        active_path = _active_db_file()
        try:
            db.close()
        except Exception:
            pass
        try:
            # Replace active DB file with the initial one
            shutil.copy2(initial_db_path, active_path)
            logger.info(f"Bootstrapped active DB from initial DB: {initial_db_path}")
        except Exception as e:
            logger.error(f"Failed to copy initial DB to active DB: {e}")
    else:
        logger.info("No initial_data.db found; will attempt CSV seeding if configured")


# Seed database on startup (bootstrap first, then CSV-seed as fallback)
@app.on_event("startup")
def startup_event():
    """Initialize database content on startup if empty.

    Order:
      1) If empty, try to copy from committed initial_data.db
      2) If still empty, try to seed from CSV (TEMP or env-configured)
    """
    db = SessionLocal()
    try:
        bootstrap_database_if_empty(db)
    finally:
        db.close()

    # Re-open a new session to check and possibly seed from CSV
    db = SessionLocal()
    try:
        if db.query(Event).count() == 0:
            seed_database(db)
    finally:
        db.close()

def seed_database(db: Session):
    """Seed the database with sample data from CSV file.

    CSV path resolution order:
      - Environment variable TCB_SEED_CSV
      - Any CSV file under the repository TEMP directory
    """
    csv_path = os.environ.get("TCB_SEED_CSV")
    if not csv_path:
        # Look for any CSV under repo TEMP directory
        repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
        candidates = glob.glob(os.path.join(repo_root, "TEMP", "*.csv"))
        if candidates:
            csv_path = candidates[0]

    if not csv_path or not os.path.exists(csv_path):
        logger.warning("CSV seed path not configured or file not found; skipping CSV seed")
        return

    # Check if database already has data
    existing_events = db.query(Event).count()
    if existing_events > 0:
        logger.info(f"Database already has {existing_events} events, skipping seed")
        return

    logger.info("Seeding database with CSV data...")

    try:
        with open(csv_path, 'r', encoding='utf-8') as csvfile:
            reader = csv.DictReader(csvfile)
            events_added = 0

            for row in reader:
                try:
                    # Parse date and time
                    date_str = row['Date']
                    start_time_str = row['Start'].strip()
                    end_time_str = row['End'].strip()

                    # Skip entries with missing or empty times
                    if not start_time_str or not end_time_str:
                        logger.warning(f"Skipping row with missing times: {row}")
                        continue

                    # Convert to epoch seconds
                    from datetime import datetime
                    try:
                        start_datetime = datetime.strptime(f"{date_str} {start_time_str}", "%Y-%m-%d %I:%M%p")
                        end_datetime = datetime.strptime(f"{date_str} {end_time_str}", "%Y-%m-%d %I:%M%p")
                    except ValueError:
                        # Try alternative format without AM/PM
                        try:
                            start_datetime = datetime.strptime(f"{date_str} {start_time_str}", "%Y-%m-%d %I:%M")
                            end_datetime = datetime.strptime(f"{date_str} {end_time_str}", "%Y-%m-%d %I:%M")
                        except ValueError:
                            logger.warning(f"Could not parse times for row: {row}")
                            continue

                    start_ts = int(start_datetime.timestamp())
                    end_ts = int(end_datetime.timestamp())

                    # Map event types
                    event_type = row['Type']
                    if event_type == 'sleep':
                        event_type = 'sleep'
                    elif event_type == 'feeding':
                        event_type = 'feed'
                    elif event_type == 'diaper':
                        event_type = 'nappy'

                    # Create event
                    event = Event(
                        event_id=f"seed_{events_added}_{start_ts}",
                        type=event_type,
                        payload={
                            'details': row['Details'],
                            'raw_text': row['Raw_Text']
                        },
                        start_ts=start_ts,
                        end_ts=end_ts,
                        ts=start_ts,  # Use start time as the main timestamp
                        created_ts=start_ts,
                        updated_ts=start_ts,
                        version=1,
                        deleted=False,
                        device_id="seed_device",
                        server_clock=events_added + 1  # Assign sequential server clock values
                    )

                    db.add(event)
                    events_added += 1

                    if events_added % 100 == 0:
                        db.commit()
                        logger.info(f"Added {events_added} events...")

                except Exception as e:
                    logger.warning(f"Failed to process row {row}: {e}")
                    continue

            db.commit()
            
            # Update server clock to match the highest server_clock assigned to seeded events
            from .crud import ensure_server_clock
            server_clock = ensure_server_clock(db)
            server_clock.counter = events_added
            db.add(server_clock)
            db.commit()
            
            logger.info(f"Successfully seeded database with {events_added} events")

    except Exception as e:
        logger.error(f"Failed to seed database: {e}")

@app.get("/healthz")
def healthz():
    return {"status": "ok"}

@app.post("/admin/seed")
def seed_database_endpoint(db: Session = Depends(get_db)):
    """Admin endpoint to seed database with sample data."""
    seed_database(db)
    return {"message": "Database seeded successfully"}

@app.get("/admin/events/count")
def get_event_count(db: Session = Depends(get_db)):
    """Get the current number of events in the database."""
    count = db.query(Event).count()
    return {"count": count}


@app.post("/pair", response_model=PairResponse)
def pair(req: PairRequest, db: Session = Depends(get_db)):
    now = int(time.time())

    # Check if device already exists and is enabled
    existing_device = crud.get_device_by_id(db, req.device_id)
    if existing_device and existing_device.enabled:
        # Device already paired, return existing token
        token = mint_token()  # Generate new token for security
        existing_device.last_seen_ts = now
        existing_device.token_hash = token_hash(token)
        if req.name and req.name != existing_device.name:
            existing_device.name = req.name
        db.commit()

        logger.info(f"Re-paired existing device: {req.device_id}, name: {req.name}")
        return PairResponse(device_id=req.device_id, token=token)
    else:
        # New device pairing
        token = mint_token()
        device = Device(
            device_id=req.device_id,
            name=req.name,
            created_ts=now,
            last_seen_ts=now,
            token_hash=token_hash(token),
            enabled=True,
        )
        crud.upsert_device(db, device)
        logger.info(f"Paired new device: {req.device_id}, name: {req.name}")
        return PairResponse(device_id=req.device_id, token=token)


@app.post("/sync/push", response_model=SyncPushResponse)
def sync_push(items: list[EventDTO], db: Session = Depends(get_db), device: Device = Depends(get_current_device)):
    logger.info(f"Sync push from device {device.device_id}: {len(items)} events")
    incoming = []
    for dto in items:
        incoming.append(Event(
            event_id=dto.event_id,
            type=dto.type,
            details=dto.details,  # Include details field
            payload=dto.payload,
            start_ts=dto.start_ts,
            end_ts=dto.end_ts,
            ts=dto.ts,
            created_ts=dto.created_ts,
            updated_ts=dto.updated_ts,
            version=dto.version,
            deleted=dto.deleted,
            device_id=dto.device_id,
        ))
    applied_events, new_clock = crud.upsert_events(db, incoming)
    logger.info(f"Applied {len(applied_events)} events, new clock: {new_clock}")
    results = []
    for ev in applied_events:
        results.append(SyncPushResponseItem(
            applied=True,
            event=EventDTO(
                event_id=ev.event_id,
                type=ev.type,
                details=ev.details,  # Include details field
                payload=ev.payload,
                start_ts=ev.start_ts,
                end_ts=ev.end_ts,
                ts=ev.ts,
                created_ts=ev.created_ts,
                updated_ts=ev.updated_ts,
                version=ev.version,
                deleted=ev.deleted,
                device_id=ev.device_id,
            )
        ))
    return SyncPushResponse(server_clock=new_clock, results=results)


@app.get("/sync/pull", response_model=SyncPullResponse)
def sync_pull(since: int = 0, db: Session = Depends(get_db), device: Device = Depends(get_current_device)):
    events = crud.select_events_since(db, since)
    current_clock = crud.get_clock(db)
    logger.info(f"Sync pull from device {device.device_id}: since={since}, returning {len(events)} events, clock={current_clock}")
    payload = [
        EventDTO(
            event_id=ev.event_id,
            type=ev.type,
            details=ev.details,  # Include details field
            payload=ev.payload,
            start_ts=ev.start_ts,
            end_ts=ev.end_ts,
            ts=ev.ts,
            created_ts=ev.created_ts,
            updated_ts=ev.updated_ts,
            version=ev.version,
            deleted=ev.deleted,
            device_id=ev.device_id,
        ) for ev in events
    ]
    return SyncPullResponse(server_clock=current_clock, events=payload)


