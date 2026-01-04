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
                    # Note: CSV times are in local time (UTC-7), need to convert to UTC
                    from datetime import datetime, timezone, timedelta
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

                    # Treat parsed datetime as UTC-7 and convert to UTC
                    tz_utc_minus_7 = timezone(timedelta(hours=-7))
                    start_aware = start_datetime.replace(tzinfo=tz_utc_minus_7)
                    end_aware = end_datetime.replace(tzinfo=tz_utc_minus_7)
                    start_ts = int(start_aware.timestamp())
                    end_ts = int(end_aware.timestamp())

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
        version_code=32,
        version_name="1.5.2",
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
        from sqlalchemy import select, inspect
        from .database import DB_PATH
        
        # Log database path being used
        from sqlalchemy import text
        import os
        logger.info(f"Database path: {DB_PATH}")
        logger.info(f"Database path (absolute): {os.path.abspath(DB_PATH)}")
        logger.info(f"Database path (real/resolved): {os.path.realpath(DB_PATH) if os.path.exists(DB_PATH) else 'N/A'}")
        logger.info(f"Database file exists: {os.path.exists(DB_PATH)}")
        if os.path.exists(DB_PATH):
            logger.info(f"Database file size: {os.path.getsize(DB_PATH)} bytes")
        
        # Check what database SQLite is actually using
        db_path_check = db.execute(text("PRAGMA database_list")).fetchall()
        logger.info(f"SQLite databases: {db_path_check}")
        
        # Also check the actual file path from SQLite
        if db_path_check:
            sqlite_path = db_path_check[0][2] if len(db_path_check[0]) > 2 else None
            if sqlite_path:
                logger.info(f"SQLite reported path (absolute): {os.path.abspath(sqlite_path) if sqlite_path != ':memory:' else ':memory:'}")
                logger.info(f"SQLite reported path (real/resolved): {os.path.realpath(sqlite_path) if sqlite_path != ':memory:' and os.path.exists(sqlite_path) else 'N/A'}")
        
        # Check if table exists
        inspector = inspect(db.bind)
        tables = inspector.get_table_names()
        logger.info(f"Tables in database: {tables}")
        has_growth_table = 'growth_data' in tables
        logger.info(f"growth_data table exists: {has_growth_table}")
        
        if has_growth_table:
            # Check WAL mode and checkpoint if needed
            wal_mode = db.execute(text("PRAGMA journal_mode")).scalar()
            logger.info(f"SQLite journal mode: {wal_mode}")
            if wal_mode == "wal":
                # Try checkpointing WAL to ensure we see latest data
                try:
                    checkpoint_result = db.execute(text("PRAGMA wal_checkpoint")).fetchone()
                    logger.info(f"WAL checkpoint result: {checkpoint_result}")
                except Exception as e:
                    logger.warning(f"WAL checkpoint failed: {e}")
            
            # Try direct SQL query to bypass any SQLAlchemy session issues
            raw_count_result = db.execute(text("SELECT COUNT(*) FROM growth_data WHERE deleted = 0"))
            raw_count = raw_count_result.scalar()
            logger.info(f"Raw SQL COUNT query returned: {raw_count} entries")
            
            # Also try without the deleted filter to see total count
            total_raw_count = db.execute(text("SELECT COUNT(*) FROM growth_data")).scalar()
            logger.info(f"Raw SQL total count (including deleted): {total_raw_count} entries")
            
            # Try to see what's actually in the table structure
            try:
                sample_rows = db.execute(text("SELECT id, category, value, deleted FROM growth_data LIMIT 5")).fetchall()
                logger.info(f"Sample rows from growth_data: {sample_rows}")
            except Exception as e:
                logger.warning(f"Could not fetch sample rows: {e}")
            
            # Check if there are any rows at all, even with different queries
            try:
                any_rows = db.execute(text("SELECT 1 FROM growth_data LIMIT 1")).fetchone()
                logger.info(f"Any rows exist check: {any_rows}")
            except Exception as e:
                logger.warning(f"Could not check for any rows: {e}")
            
            # Force a fresh connection by creating a new session
            # This helps if there's a connection pooling or caching issue
            from .database import SessionLocal
            fresh_db = SessionLocal()
            try:
                fresh_count = fresh_db.execute(text("SELECT COUNT(*) FROM growth_data WHERE deleted = 0")).scalar()
                logger.info(f"Fresh connection COUNT query returned: {fresh_count} entries")
                
                # Also try fetching sample rows with fresh connection
                fresh_samples = fresh_db.execute(text("SELECT id, category, value FROM growth_data LIMIT 3")).fetchall()
                logger.info(f"Fresh connection sample rows: {fresh_samples}")
            except Exception as e:
                logger.warning(f"Fresh connection query failed: {e}")
            finally:
                fresh_db.close()
            
            # Also try SQLAlchemy query
            total_count = db.query(GrowthData).count()
            logger.info(f"SQLAlchemy query count: {total_count}")
            
            # Try raw SQL select
            raw_select = db.execute(text("SELECT * FROM growth_data WHERE deleted = 0 ORDER BY ts"))
            raw_rows = raw_select.fetchall()
            logger.info(f"Raw SQL SELECT returned {len(raw_rows)} rows")
            
            # SQLAlchemy query
            stmt = select(GrowthData).where(GrowthData.deleted == False).order_by(GrowthData.ts)
            data_list = list(db.scalars(stmt).all())
            logger.info(f"SQLAlchemy query returned {len(data_list)} non-deleted entries from growth_data table")
            
            # If SQLAlchemy returns 0 but raw SQL returns data, use raw SQL
            if len(data_list) == 0 and len(raw_rows) > 0:
                logger.warning("SQLAlchemy query returned 0 but raw SQL found data - using raw SQL results")
                # Convert raw rows to GrowthData objects
                data_list = []
                for row in raw_rows:
                    gd = GrowthData(
                        id=row[0],
                        device_id=row[1],
                        category=row[2],
                        value=row[3],
                        unit=row[4],
                        ts=row[5],
                        created_ts=row[6],
                        updated_ts=row[7],
                        version=row[8],
                        deleted=bool(row[9]),
                        server_clock=row[10]
                    )
                    data_list.append(gd)
        else:
            logger.warning("growth_data table does not exist in database!")
            data_list = []
    
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


