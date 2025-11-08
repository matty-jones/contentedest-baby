from __future__ import annotations
import time
import logging
import csv
import os
from pathlib import Path
from fastapi import FastAPI, Depends, Request, status
from fastapi.responses import FileResponse
from sqlalchemy.orm import Session
from .database import Base, engine, SessionLocal
from .models import Device, Event, GrowthData
from .schemas import PairRequest, PairResponse, EventDTO, SyncPushResponse, SyncPushResponseItem, SyncPullResponse, UpdateInfoResponse, GrowthDataDTO, GrowthPushResponse, GrowthPullResponse
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

@app.get("/health", status_code=status.HTTP_200_OK)
def health():
    return {"status": "ok"}

# Seed database on startup
@app.on_event("startup")
def startup_event():
    """Seed database with sample data on startup."""
    db = SessionLocal()
    try:
        seed_database(db)
    finally:
        db.close()

def seed_database(db: Session):
    """Seed the database with sample data from CSV file."""
    csv_path = "/home/blasky/Projects/extractedest-baby/complete_historical_data/screenshot_processed_data_20250916_212254.csv"

    if not os.path.exists(csv_path):
        logger.warning(f"CSV file not found at {csv_path}")
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
def sync_push(items: list[EventDTO], db: Session = Depends(get_db)):
    logger.info(f"Sync push: {len(items)} events")
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
def sync_pull(since: int = 0, db: Session = Depends(get_db)):
    events = crud.select_events_since(db, since)
    current_clock = crud.get_clock(db)
    logger.info(f"Sync pull: since={since}, returning {len(events)} events, clock={current_clock}")
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


@app.get("/app/update", response_model=UpdateInfoResponse)
def get_update_info():
    """
    Returns the latest app version information.
    Update this when you deploy a new APK version.
    """
    # TODO: Consider storing this in a config file or database for easier updates
    # For now, hardcode the latest version info
    # When you build a new APK, update these values and place the APK in server/apks/
    
    # Get the base URL from environment or use default
    base_url = os.getenv("BASE_URL", "http://192.168.86.3:8005")
    
    return UpdateInfoResponse(
        version_code=1,  # Increment this for each new release
        version_name="1.0",  # Human-readable version
        download_url=f"{base_url}/app/download/latest.apk",
        release_notes="Initial release",
        mandatory=False  # Set to True to force updates
    )


@app.get("/app/download/{filename}")
def download_apk(filename: str):
    """
    Serves APK files from the server/apks/ directory.
    Place your APK files in server/apks/ and name the latest one 'latest.apk'
    """
    apk_dir = Path(__file__).parent.parent / "apks"
    apk_path = apk_dir / filename
    
    if not apk_path.exists():
        logger.error(f"APK not found: {apk_path}")
        return {"error": "APK not found"}, status.HTTP_404_NOT_FOUND
    
    if not apk_path.is_file():
        logger.error(f"Path is not a file: {apk_path}")
        return {"error": "Invalid path"}, status.HTTP_400_BAD_REQUEST
    
    logger.info(f"Serving APK: {filename}")
    return FileResponse(
        path=str(apk_path),
        media_type="application/vnd.android.package-archive",
        filename=filename
    )


@app.post("/growth", response_model=GrowthPushResponse)
def create_growth_data(data: GrowthDataDTO, db: Session = Depends(get_db)):
    """Create or update growth data entry."""
    logger.info(f"Growth push: {data.id} ({data.category})")
    incoming = GrowthData(
        id=data.id,
        device_id=data.device_id,
        category=data.category,
        value=data.value,
        unit=data.unit,
        ts=data.ts,
        created_ts=data.created_ts,
        updated_ts=data.updated_ts,
        version=data.version,
        deleted=data.deleted,
    )
    applied_data, new_clock = crud.upsert_growth_data(db, incoming)
    logger.info(f"Applied growth data {applied_data.id}, new clock: {new_clock}")
    
    result_dto = GrowthDataDTO(
        id=applied_data.id,
        device_id=applied_data.device_id,
        category=applied_data.category,
        value=applied_data.value,
        unit=applied_data.unit,
        ts=applied_data.ts,
        created_ts=applied_data.created_ts,
        updated_ts=applied_data.updated_ts,
        version=applied_data.version,
        deleted=applied_data.deleted,
    )
    
    return GrowthPushResponse(
        server_clock=new_clock,
        applied=True,
        data=result_dto
    )


@app.get("/growth", response_model=GrowthPullResponse)
def get_growth_data(category: str | None = None, since: int = 0, db: Session = Depends(get_db)):
    """Get growth data entries, optionally filtered by category and server clock."""
    logger.info(f"Growth pull: category={category}, since={since}")
    if since > 0:
        data_list = crud.select_growth_data_since(db, since, category)
    elif category:
        data_list = crud.get_growth_data_by_category(db, category)
    else:
        # Get all non-deleted entries (when since=0 and no category)
        from sqlalchemy import select
        # First check if table exists and has data
        total_count = db.query(GrowthData).count()
        logger.info(f"Total growth_data entries in DB: {total_count}")
        stmt = select(GrowthData).where(GrowthData.deleted == False).order_by(GrowthData.ts)
        data_list = list(db.scalars(stmt).all())
        logger.info(f"Query returned {len(data_list)} non-deleted entries from growth_data table")
    
    current_clock = crud.get_clock(db)
    logger.info(f"Returning {len(data_list)} growth entries, clock={current_clock}")
    
    payload = [
        GrowthDataDTO(
            id=gd.id,
            device_id=gd.device_id,
            category=gd.category,
            value=gd.value,
            unit=gd.unit,
            ts=gd.ts,
            created_ts=gd.created_ts,
            updated_ts=gd.updated_ts,
            version=gd.version,
            deleted=gd.deleted,
        ) for gd in data_list
    ]
    
    return GrowthPullResponse(server_clock=current_clock, data=payload)


