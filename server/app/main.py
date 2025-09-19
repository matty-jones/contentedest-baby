from __future__ import annotations
import time
from fastapi import FastAPI, Depends
from sqlalchemy.orm import Session
from .database import Base, engine
from .models import Device, Event
from .schemas import PairRequest, PairResponse, EventDTO, SyncPushResponse, SyncPushResponseItem, SyncPullResponse
from .security import mint_token, token_hash
from .auth import get_current_device, get_db
from . import crud


app = FastAPI(title="The Contentedest Baby Server")


Base.metadata.create_all(bind=engine)


@app.get("/healthz")
def healthz():
    return {"status": "ok"}


@app.post("/pair", response_model=PairResponse)
def pair(req: PairRequest, db: Session = Depends(get_db)):
    now = int(time.time())
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
    return PairResponse(device_id=req.device_id, token=token)


@app.post("/sync/push", response_model=SyncPushResponse)
def sync_push(items: list[EventDTO], db: Session = Depends(get_db), device: Device = Depends(get_current_device)):
    incoming = []
    for dto in items:
        incoming.append(Event(
            event_id=dto.event_id,
            type=dto.type,
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
    results = []
    for ev in applied_events:
        results.append(SyncPushResponseItem(
            applied=True,
            event=EventDTO(
                event_id=ev.event_id,
                type=ev.type,
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
    payload = [
        EventDTO(
            event_id=ev.event_id,
            type=ev.type,
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


