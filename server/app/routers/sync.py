from __future__ import annotations

import logging
import time

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from .. import crud
from ..auth import get_db
from ..models import Device, Event
from ..schemas import (
    EventDTO,
    PairRequest,
    PairResponse,
    SyncPullResponse,
    SyncPushResponse,
    SyncPushResponseItem,
)
from ..security import mint_token, token_hash

router = APIRouter()
logger = logging.getLogger(__name__)


@router.post("/pair", response_model=PairResponse)
def pair(req: PairRequest, db: Session = Depends(get_db)):
    now = int(time.time())
    existing_device = crud.get_device_by_id(db, req.device_id)
    if existing_device and existing_device.enabled:
        token = mint_token()
        existing_device.last_seen_ts = now
        existing_device.token_hash = token_hash(token)
        if req.name and req.name != existing_device.name:
            existing_device.name = req.name
        db.commit()
        logger.info("Re-paired existing device: %s, name: %s", req.device_id, req.name)
        return PairResponse(device_id=req.device_id, token=token)

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
    logger.info("Paired new device: %s, name: %s", req.device_id, req.name)
    return PairResponse(device_id=req.device_id, token=token)


@router.post("/sync/push", response_model=SyncPushResponse)
def sync_push(items: list[EventDTO], db: Session = Depends(get_db)):
    logger.info("Sync push: %s events", len(items))
    incoming = [
        Event(
            event_id=dto.event_id,
            type=dto.type,
            details=dto.details,
            payload=dto.payload,
            start_ts=dto.start_ts,
            end_ts=dto.end_ts,
            ts=dto.ts,
            created_ts=dto.created_ts,
            updated_ts=dto.updated_ts,
            version=dto.version,
            deleted=dto.deleted,
            device_id=dto.device_id,
        )
        for dto in items
    ]
    applied_events, new_clock = crud.upsert_events(db, incoming)
    logger.info("Applied %s events, new clock: %s", len(applied_events), new_clock)
    results = [
        SyncPushResponseItem(
            applied=True,
            event=EventDTO(
                event_id=ev.event_id,
                type=ev.type,
                details=ev.details,
                payload=ev.payload,
                start_ts=ev.start_ts,
                end_ts=ev.end_ts,
                ts=ev.ts,
                created_ts=ev.created_ts,
                updated_ts=ev.updated_ts,
                version=ev.version,
                deleted=ev.deleted,
                device_id=ev.device_id,
            ),
        )
        for ev in applied_events
    ]
    return SyncPushResponse(server_clock=new_clock, results=results)


@router.get("/sync/pull", response_model=SyncPullResponse)
def sync_pull(since: int = 0, db: Session = Depends(get_db)):
    events = crud.select_events_since(db, since)
    current_clock = crud.get_clock(db)
    logger.info(
        "Sync pull: since=%s, returning %s events, clock=%s",
        since,
        len(events),
        current_clock,
    )
    payload = [
        EventDTO(
            event_id=ev.event_id,
            type=ev.type,
            details=ev.details,
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
        for ev in events
    ]
    return SyncPullResponse(server_clock=current_clock, events=payload)
