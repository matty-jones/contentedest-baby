from __future__ import annotations
import time
import uuid
from typing import Iterable, Tuple
from sqlalchemy.orm import Session
from sqlalchemy import select
from .models import BabyWord, Device, Event, ServerClock, GrowthData
from .event_policy import (
    ADJACENT_MERGE_GAP_SECONDS,
    MIN_CRIB_WEBHOOK_SLEEP_SECONDS,
    intervals_mergeable_ordered,
)

_EVENT_UPSERT_FIELDS = (
    "type",
    "payload",
    "start_ts",
    "end_ts",
    "ts",
    "created_ts",
    "updated_ts",
    "version",
    "deleted",
    "device_id",
)
_GROWTH_UPSERT_FIELDS = (
    "device_id",
    "category",
    "value",
    "unit",
    "ts",
    "created_ts",
    "updated_ts",
    "version",
    "deleted",
)
_WORD_UPSERT_FIELDS = (
    "device_id",
    "word",
    "ts",
    "created_ts",
    "updated_ts",
    "version",
    "deleted",
    "understands",
    "says",
)


def _incoming_wins(existing_device_ts_version: tuple, incoming_device_ts_version: tuple) -> bool:
    return incoming_device_ts_version > existing_device_ts_version


def _copy_fields(target: object, source: object, fields: tuple[str, ...]) -> None:
    for field in fields:
        setattr(target, field, getattr(source, field))


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
    existing_key = (existing.version, existing.updated_ts, existing.device_id)
    incoming_key = (incoming.version, incoming.updated_ts, incoming.device_id)
    if _incoming_wins(existing_key, incoming_key):
        _copy_fields(existing, incoming, _EVENT_UPSERT_FIELDS)
        return existing, True
    return existing, False


def upsert_events(session: Session, incoming_events: Iterable[Event]) -> Tuple[list[Event], int]:
    incoming_list = sorted(incoming_events, key=lambda e: (e.start_ts or 0, e.event_id or ""))
    applied: list[Event] = []
    sc_before = get_clock(session)
    for inc in incoming_list:
        existing = session.get(Event, inc.event_id)
        winner, changed = resolve_event(existing, inc)
        if changed:
            clock = next_clock(session)
            winner.server_clock = clock
            session.add(winner)
            session.commit()
            session.refresh(winner)
        out = winner
        if (
            changed
            and winner.type in ("sleep", "feed")
            and winner.end_ts is not None
            and winner.start_ts is not None
            and not winner.deleted
        ):
            out = maybe_merge_after_sync_upsert(session, winner)
        applied.append(out)
    new_clock = get_clock(session)
    if new_clock < sc_before:
        new_clock = sc_before
    return applied, new_clock


def resolve_growth_data(existing: GrowthData | None, incoming: GrowthData) -> Tuple[GrowthData, bool]:
    if existing is None:
        return incoming, True
    existing_key = (existing.version, existing.updated_ts, existing.device_id)
    incoming_key = (incoming.version, incoming.updated_ts, incoming.device_id)
    if _incoming_wins(existing_key, incoming_key):
        _copy_fields(existing, incoming, _GROWTH_UPSERT_FIELDS)
        return existing, True
    return existing, False


def upsert_growth_data(session: Session, incoming_data: GrowthData) -> Tuple[GrowthData, int]:
    existing = session.get(GrowthData, incoming_data.id)
    winner, changed = resolve_growth_data(existing, incoming_data)
    if changed:
        clock = next_clock(session)
        winner.server_clock = clock
        session.add(winner)
        session.commit()
        session.refresh(winner)
    new_clock = get_clock(session)
    return winner, new_clock


def select_growth_data_since(session: Session, since_clock: int, category: str | None = None) -> list[GrowthData]:
    stmt = select(GrowthData).where(GrowthData.server_clock > since_clock)
    if category:
        stmt = stmt.where(GrowthData.category == category)
    return list(session.scalars(stmt).all())


def get_growth_data_by_category(session: Session, category: str) -> list[GrowthData]:
    stmt = select(GrowthData).where(
        GrowthData.category == category,
        GrowthData.deleted == False
    ).order_by(GrowthData.ts)
    return list(session.scalars(stmt).all())


def resolve_baby_word(existing: BabyWord | None, incoming: BabyWord) -> Tuple[BabyWord, bool]:
    if existing is None:
        return incoming, True
    existing_key = (existing.version, existing.updated_ts, existing.device_id)
    incoming_key = (incoming.version, incoming.updated_ts, incoming.device_id)
    if _incoming_wins(existing_key, incoming_key):
        _copy_fields(existing, incoming, _WORD_UPSERT_FIELDS)
        return existing, True
    return existing, False


def upsert_baby_word(session: Session, incoming: BabyWord) -> Tuple[BabyWord, int]:
    existing = session.get(BabyWord, incoming.id)
    winner, changed = resolve_baby_word(existing, incoming)
    if changed:
        clock = next_clock(session)
        winner.server_clock = clock
        session.add(winner)
        session.commit()
        session.refresh(winner)
    new_clock = get_clock(session)
    return winner, new_clock


def select_baby_words_since(session: Session, since_clock: int) -> list[BabyWord]:
    stmt = select(BabyWord).where(BabyWord.server_clock > since_clock)
    return list(session.scalars(stmt).all())


def get_open_sleep(session: Session, device_id: str) -> Event | None:
    stmt = (
        select(Event)
        .where(
            Event.device_id == device_id,
            Event.type == "sleep",
            Event.end_ts.is_(None),
            Event.deleted == False,
        )
        .order_by(Event.start_ts.desc())
        .limit(1)
    )
    return session.scalars(stmt).first()


def create_crib_sleep(session: Session, device_id: str, start_ts: int) -> Event:
    event_id = str(uuid.uuid4())
    event = Event(
        event_id=event_id,
        type="sleep",
        details="Crib",
        payload={"source": "crib_webhook"},
        start_ts=start_ts,
        end_ts=None,
        ts=start_ts,
        created_ts=start_ts,
        updated_ts=start_ts,
        version=1,
        deleted=False,
        device_id=device_id,
        server_clock=0,
    )
    event.server_clock = next_clock(session)
    session.add(event)
    session.commit()
    session.refresh(event)
    return event


def close_sleep(session: Session, event: Event, end_ts: int) -> Event:
    event.end_ts = end_ts
    event.updated_ts = end_ts
    event.version += 1
    event.server_clock = next_clock(session)
    session.add(event)
    session.commit()
    session.refresh(event)
    return event


def _soft_delete_event(session: Session, event: Event) -> None:
    now_ts = int(time.time())
    event.deleted = True
    event.updated_ts = now_ts
    event.version = event.version + 1
    event.server_clock = next_clock(session)
    session.add(event)
    session.commit()
    session.refresh(event)


def _find_merge_predecessor(
    session: Session, curr: Event, min_start_ts: int | None = None
) -> Event | None:
    """Find a prior sleep/feed segment to merge with curr (gap <= 60s or overlap)."""
    if curr.type not in ("sleep", "feed") or not curr.start_ts or not curr.end_ts:
        return None
    if min_start_ts is not None and curr.start_ts < min_start_ts:
        return None
    stmt = (
        select(Event)
        .where(
            Event.device_id == curr.device_id,
            Event.type == curr.type,
            Event.deleted == False,  # noqa: E712
            Event.end_ts.isnot(None),
            Event.start_ts.isnot(None),
            Event.event_id != curr.event_id,
            Event.end_ts <= curr.start_ts,
            *(
                (Event.start_ts >= min_start_ts,)
                if min_start_ts is not None
                else ()
            ),
        )
        .order_by(Event.end_ts.desc())
    )
    for prev in session.scalars(stmt).all():
        gap = curr.start_ts - prev.end_ts
        if 0 <= gap <= ADJACENT_MERGE_GAP_SECONDS:
            return prev
    stmt2 = (
        select(Event)
        .where(
            Event.device_id == curr.device_id,
            Event.type == curr.type,
            Event.deleted == False,  # noqa: E712
            Event.end_ts.isnot(None),
            Event.start_ts.isnot(None),
            Event.event_id != curr.event_id,
            Event.start_ts < curr.end_ts,
            Event.end_ts > curr.start_ts,
            *(
                (Event.start_ts >= min_start_ts,)
                if min_start_ts is not None
                else ()
            ),
        )
        .order_by(Event.start_ts.asc())
    )
    return session.scalars(stmt2).first()


def _try_single_merge_backward(
    session: Session, curr: Event, min_start_ts: int | None = None
) -> Event:
    """Merge curr with one predecessor if eligible. Returns surviving row."""
    if curr.type not in ("sleep", "feed") or curr.deleted:
        return curr
    if not curr.start_ts or not curr.end_ts:
        return curr
    prev = _find_merge_predecessor(session, curr, min_start_ts=min_start_ts)
    if prev is None:
        return curr
    if prev.start_ts <= curr.start_ts:
        keep, drop = prev, curr
    else:
        keep, drop = curr, prev
    keep.start_ts = min(prev.start_ts, curr.start_ts)
    keep.end_ts = max(prev.end_ts, curr.end_ts)
    keep.updated_ts = int(time.time())
    keep.version = keep.version + 1
    keep.server_clock = next_clock(session)
    session.add(keep)
    drop.deleted = True
    drop.updated_ts = keep.updated_ts
    drop.version = drop.version + 1
    drop.server_clock = next_clock(session)
    session.add(drop)
    session.commit()
    session.refresh(keep)
    return keep


def merge_adjacent_chain(
    session: Session, start: Event, min_start_ts: int | None = None
) -> Event:
    """Repeatedly merge backward until stable (handles 1h + gap + 1h + gap + ...)."""
    ev = start
    while True:
        nxt = _try_single_merge_backward(session, ev, min_start_ts=min_start_ts)
        if nxt.event_id == ev.event_id:
            return nxt
        ev = nxt


def merge_adjacent_sorted_pair(
    session: Session,
    first: Event,
    second: Event,
    min_start_ts: int | None = None,
) -> None:
    """
    Merge two sleep/feed rows that are consecutive in (start_ts, event_id) order.
    Caller must ensure first.start_ts <= second.start_ts (or equal with first <= second by id).
    No DB scans for predecessors; for bulk consolidation scripts only.
    """
    if first.type not in ("sleep", "feed") or second.type not in ("sleep", "feed"):
        raise ValueError("merge_adjacent_sorted_pair: type must be sleep or feed")
    if first.device_id != second.device_id or first.type != second.type:
        raise ValueError("merge_adjacent_sorted_pair: device_id and type must match")
    if first.deleted or second.deleted:
        raise ValueError("merge_adjacent_sorted_pair: event deleted")
    if not first.start_ts or not first.end_ts or not second.start_ts or not second.end_ts:
        raise ValueError("merge_adjacent_sorted_pair: missing bounds")
    if min_start_ts is not None:
        if first.start_ts < min_start_ts or second.start_ts < min_start_ts:
            raise ValueError("merge_adjacent_sorted_pair: below min_start_ts")
    if not intervals_mergeable_ordered(
        first.start_ts, first.end_ts, second.start_ts, second.end_ts
    ):
        raise ValueError("merge_adjacent_sorted_pair: intervals not mergeable")
    if (first.start_ts, first.event_id) > (second.start_ts, second.event_id):
        first, second = second, first
    keep, drop = first, second
    keep.start_ts = min(first.start_ts, second.start_ts)
    keep.end_ts = max(first.end_ts, second.end_ts)
    keep.updated_ts = int(time.time())
    keep.version = keep.version + 1
    keep.server_clock = next_clock(session)
    session.add(keep)
    drop.deleted = True
    drop.updated_ts = keep.updated_ts
    drop.version = drop.version + 1
    drop.server_clock = next_clock(session)
    session.add(drop)
    session.commit()
    session.refresh(keep)


def try_merge_first_mergeable_pair_for_device_type(
    session: Session,
    device_id: str,
    event_type: str,
    min_start_ts: int | None,
) -> bool:
    """
    Load sleep/feed rows for one device and type, ordered by start_ts, event_id.
    If some consecutive pair is mergeable (gap<=60s or overlap), merge the first such pair and return True.
    Otherwise return False. O(n) per call with no overlap table scan.
    """
    if event_type not in ("sleep", "feed"):
        return False
    conditions = [
        Event.device_id == device_id,
        Event.type == event_type,
        Event.deleted == False,  # noqa: E712
        Event.start_ts.isnot(None),
        Event.end_ts.isnot(None),
    ]
    if min_start_ts is not None:
        conditions.append(Event.start_ts >= min_start_ts)
    stmt = (
        select(Event)
        .where(*conditions)
        .order_by(Event.start_ts.asc(), Event.event_id.asc())
    )
    evs = list(session.scalars(stmt).all())
    for i in range(len(evs) - 1):
        a, b = evs[i], evs[i + 1]
        if intervals_mergeable_ordered(a.start_ts, a.end_ts, b.start_ts, b.end_ts):
            merge_adjacent_sorted_pair(session, a, b, min_start_ts=min_start_ts)
            return True
    return False


def close_crib_webhook_sleep(session: Session, open_sleep: Event, end_ts: int) -> tuple[Event | None, str]:
    """
    Close open crib sleep. If duration < 5 minutes, soft-delete and return (None, 'discarded').
    Otherwise merge with adjacent segment if within 60s, return (surviving_event, 'closed').
    """
    open_sleep.end_ts = end_ts
    open_sleep.updated_ts = end_ts
    open_sleep.version += 1
    duration = end_ts - open_sleep.start_ts
    if duration < MIN_CRIB_WEBHOOK_SLEEP_SECONDS:
        _soft_delete_event(session, open_sleep)
        return None, "discarded"
    open_sleep.server_clock = next_clock(session)
    session.add(open_sleep)
    session.commit()
    session.refresh(open_sleep)
    merged = merge_adjacent_chain(session, open_sleep)
    return merged, "closed"


def maybe_merge_after_sync_upsert(session: Session, ev: Event) -> Event:
    """After sync push applies a closed sleep/feed row, merge with neighbors if needed."""
    if ev.type not in ("sleep", "feed") or ev.deleted:
        return ev
    if not ev.start_ts or not ev.end_ts:
        return ev
    return merge_adjacent_chain(session, ev)


