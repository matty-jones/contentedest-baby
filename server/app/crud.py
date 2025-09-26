from __future__ import annotations
from typing import Iterable, Tuple
from sqlalchemy.orm import Session
from sqlalchemy import select
from .models import Device, Event, ServerClock


def ensure_server_clock(session: Session) -> ServerClock:
    sc = session.get(ServerClock, 1)
    if sc is None:
        sc = ServerClock(id=1, counter=0)
        session.add(sc)
        session.commit()
        session.refresh(sc)
    return sc


def next_clock(session: Session) -> int:
    sc = ensure_server_clock(session)
    sc.counter += 1
    session.add(sc)
    session.commit()
    session.refresh(sc)
    return sc.counter


def get_clock(session: Session) -> int:
    sc = ensure_server_clock(session)
    return sc.counter


def get_device_by_id(session: Session, device_id: str) -> Device | None:
    return session.get(Device, device_id)


def upsert_device(session: Session, device: Device) -> None:
    existing = session.get(Device, device.device_id)
    if existing is None:
        session.add(device)
    else:
        existing.name = device.name
        existing.last_seen_ts = device.last_seen_ts
        existing.token_hash = device.token_hash
        existing.enabled = device.enabled
    session.commit()


def select_events_since(session: Session, since_clock: int) -> list[Event]:
    stmt = select(Event).where(Event.server_clock > since_clock)
    return list(session.scalars(stmt).all())


def resolve_event(existing: Event | None, incoming: Event) -> Tuple[Event, bool]:
    if existing is None:
        return incoming, True
    # Compare by (version, updated_ts, device_id)
    a = (existing.version, existing.updated_ts, existing.device_id)
    b = (incoming.version, incoming.updated_ts, incoming.device_id)
    if b > a:
        existing.type = incoming.type
        existing.payload = incoming.payload
        existing.start_ts = incoming.start_ts
        existing.end_ts = incoming.end_ts
        existing.ts = incoming.ts
        existing.created_ts = incoming.created_ts
        existing.updated_ts = incoming.updated_ts
        existing.version = incoming.version
        existing.deleted = incoming.deleted
        existing.device_id = incoming.device_id
        return existing, True
    return existing, False


def upsert_events(session: Session, incoming_events: Iterable[Event]) -> Tuple[list[Event], int]:
    applied: list[Event] = []
    sc_before = get_clock(session)
    for inc in incoming_events:
        existing = session.get(Event, inc.event_id)
        winner, changed = resolve_event(existing, inc)
        if changed:
            clock = next_clock(session)
            winner.server_clock = clock
            session.add(winner)
            session.commit()
            session.refresh(winner)
        applied.append(winner)
    new_clock = get_clock(session)
    if new_clock < sc_before:
        new_clock = sc_before
    return applied, new_clock


