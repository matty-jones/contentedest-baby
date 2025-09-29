#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import sys
from datetime import datetime
from typing import Any, Dict, Optional


def _ensure_repo_root_on_path() -> None:
    repo_root = os.path.abspath(os.path.dirname(__file__))
    if repo_root not in sys.path:
        sys.path.insert(0, repo_root)


_ensure_repo_root_on_path()

# We import after sys.path adjustment so that `server.app` can be resolved.
try:
    from server.app.database import SessionLocal, SQLALCHEMY_DATABASE_URL
    from server.app.models import Event, Device
except Exception as import_err:  # pragma: no cover
    print(f"Failed to import server modules: {import_err}")
    print("Ensure you run this from the repository root and that Python can import the 'server.app' package.")
    sys.exit(1)

from sqlalchemy import select, func


def parse_time(value: Optional[str]) -> Optional[int]:
    if not value:
        return None
    # Accept epoch seconds
    if value.isdigit():
        try:
            return int(value)
        except ValueError:
            pass
    # Accept ISO-like formats
    fmts = [
        "%Y-%m-%dT%H:%M:%S",
        "%Y-%m-%d %H:%M:%S",
        "%Y-%m-%dT%H:%M",
        "%Y-%m-%d %H:%M",
        "%Y-%m-%d",
    ]
    for fmt in fmts:
        try:
            return int(datetime.strptime(value, fmt).timestamp())
        except ValueError:
            continue
    raise argparse.ArgumentTypeError(
        f"Invalid time '{value}'. Use epoch seconds or ISO format like 2025-09-29T13:45:00"
    )


def human_dt(ts: Optional[int]) -> Optional[str]:
    if ts is None:
        return None
    try:
        return datetime.utcfromtimestamp(int(ts)).strftime("%Y-%m-%d %H:%M:%S UTC")
    except Exception:
        return str(ts)


def as_dict_event(ev: Event) -> Dict[str, Any]:
    return {
        "event_id": ev.event_id,
        "type": ev.type,
        "details": ev.details,  # New field
        "payload": ev.payload,
        "start_ts": ev.start_ts,
        "end_ts": ev.end_ts,
        "ts": ev.ts,
        "created_ts": ev.created_ts,
        "updated_ts": ev.updated_ts,
        "version": ev.version,
        "deleted": ev.deleted,
        "device_id": ev.device_id,
        "server_clock": ev.server_clock,
        "start": human_dt(ev.start_ts),
        "end": human_dt(ev.end_ts),
        "at": human_dt(ev.ts),
    }


def command_events(args: argparse.Namespace) -> int:
    # Allow override via CLI or environment. The server uses TCB_DB_PATH; we honor it too.
    if args.db_path:
        os.environ["TCB_DB_PATH"] = args.db_path

    session = SessionLocal()
    try:
        stmt = select(Event)

        if args.type:
            stmt = stmt.where(Event.type == args.type)
        if args.device_id:
            stmt = stmt.where(Event.device_id == args.device_id)
        if args.deleted is not None:
            stmt = stmt.where(Event.deleted == args.deleted)
        if args.since_ts is not None:
            # filter by main timestamp `ts` by default, or by start/end if specified
            if args.time_field == "ts":
                stmt = stmt.where((Event.ts.is_not(None)) & (Event.ts >= args.since_ts))
            elif args.time_field == "start_ts":
                stmt = stmt.where((Event.start_ts.is_not(None)) & (Event.start_ts >= args.since_ts))
            else:
                stmt = stmt.where((Event.end_ts.is_not(None)) & (Event.end_ts >= args.since_ts))
        if args.until_ts is not None:
            if args.time_field == "ts":
                stmt = stmt.where((Event.ts.is_not(None)) & (Event.ts <= args.until_ts))
            elif args.time_field == "start_ts":
                stmt = stmt.where((Event.start_ts.is_not(None)) & (Event.start_ts <= args.until_ts))
            else:
                stmt = stmt.where((Event.end_ts.is_not(None)) & (Event.end_ts <= args.until_ts))

        order_col = {
            "ts": Event.ts,
            "start_ts": Event.start_ts,
            "end_ts": Event.end_ts,
            "created_ts": Event.created_ts,
            "updated_ts": Event.updated_ts,
            "server_clock": Event.server_clock,
        }[args.order_by]
        if args.desc:
            stmt = stmt.order_by(order_col.desc())
        else:
            stmt = stmt.order_by(order_col.asc())

        if args.limit:
            stmt = stmt.limit(args.limit)

        rows = session.execute(stmt).scalars().all()

        if args.json:
            print(json.dumps([as_dict_event(ev) for ev in rows], indent=2, ensure_ascii=False))
        else:
            for ev in rows:
                d = as_dict_event(ev)
                print(
                    f"{d['event_id']}  {d['type']:<6}  details={d['details']:<10}  dev={d['device_id']:<12} deleted={d['deleted']}  "
                    f"ts={d['at']}  start={d['start']}  end={d['end']}  v={d['version']}  clk={d['server_clock']}"
                )
        return 0
    finally:
        session.close()


def command_counts(args: argparse.Namespace) -> int:
    if args.db_path:
        os.environ["TCB_DB_PATH"] = args.db_path

    session = SessionLocal()
    try:
        q_total = session.execute(select(func.count()).select_from(Event)).scalar_one()
        q_deleted = session.execute(select(func.count()).where(Event.deleted.is_(True))).scalar_one()
        q_sleep = session.execute(select(func.count()).where(Event.type == "sleep")).scalar_one()
        q_feed = session.execute(select(func.count()).where(Event.type == "feed")).scalar_one()
        q_nappy = session.execute(select(func.count()).where(Event.type == "nappy")).scalar_one()

        payload: Dict[str, Any] = {
            "database_url": SQLALCHEMY_DATABASE_URL,
            "counts": {
                "total": q_total,
                "deleted": q_deleted,
                "sleep": q_sleep,
                "feed": q_feed,
                "nappy": q_nappy,
            },
        }
        if args.json:
            print(json.dumps(payload, indent=2))
        else:
            print(f"DB: {payload['database_url']}")
            for k, v in payload["counts"].items():
                print(f"{k:>7}: {v}")
        return 0
    finally:
        session.close()


def command_devices(args: argparse.Namespace) -> int:
    if args.db_path:
        os.environ["TCB_DB_PATH"] = args.db_path

    session = SessionLocal()
    try:
        stmt = select(Device)
        if args.enabled is not None:
            stmt = stmt.where(Device.enabled == args.enabled)
        rows = session.execute(stmt).scalars().all()
        if args.json:
            print(
                json.dumps(
                    [
                        {
                            "device_id": d.device_id,
                            "name": d.name,
                            "created_ts": d.created_ts,
                            "last_seen_ts": d.last_seen_ts,
                            "enabled": d.enabled,
                        }
                        for d in rows
                    ],
                    indent=2,
                )
            )
        else:
            for d in rows:
                print(
                    f"{d.device_id:<24} name={d.name!s:<16} enabled={d.enabled} "
                    f"created={human_dt(d.created_ts)} last_seen={human_dt(d.last_seen_ts)}"
                )
        return 0
    finally:
        session.close()


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        description=(
            "Inspect the SQLite database used by the Android/Server app. "
            "By default uses the same path logic as the server. You can override with --db-path or TCB_DB_PATH."
        )
    )
    p.add_argument("--db-path", help="Path to SQLite db (overrides TCB_DB_PATH and server default)")
    p.add_argument("--json", action="store_true", help="Output JSON where applicable")

    sub = p.add_subparsers(dest="cmd", required=True)

    # events
    pe = sub.add_parser("events", help="List events with filters")
    pe.add_argument("--type", choices=["sleep", "feed", "nappy"], help="Filter by event type")
    pe.add_argument("--device-id", help="Filter by device id")
    pe.add_argument("--deleted", dest="deleted", action="store_true", help="Only deleted events")
    pe.add_argument("--not-deleted", dest="deleted", action="store_false", help="Only non-deleted events")
    pe.add_argument("--all", dest="deleted", action="store_const", const=None, help="Include both deleted and non-deleted")
    pe.set_defaults(deleted=None)
    pe.add_argument("--since", type=parse_time, dest="since_ts", help="Lower bound time (epoch or ISO)")
    pe.add_argument("--until", type=parse_time, dest="until_ts", help="Upper bound time (epoch or ISO)")
    pe.add_argument(
        "--time-field",
        choices=["ts", "start_ts", "end_ts"],
        default="ts",
        help="Which timestamp column to filter/order on",
    )
    pe.add_argument(
        "--order-by",
        choices=["ts", "start_ts", "end_ts", "created_ts", "updated_ts", "server_clock"],
        default="ts",
        help="Sort column",
    )
    pe.add_argument("--desc", action="store_true", help="Sort descending")
    pe.add_argument("--limit", type=int, default=100, help="Limit number of rows")
    pe.set_defaults(func=command_events)

    # counts
    pc = sub.add_parser("counts", help="Show aggregate counts and DB path")
    pc.set_defaults(func=command_counts)

    # devices
    pd = sub.add_parser("devices", help="List devices")
    g = pd.add_mutually_exclusive_group()
    g.add_argument("--enabled", dest="enabled", action="store_true", help="Only enabled devices")
    g.add_argument("--disabled", dest="enabled", action="store_false", help="Only disabled devices")
    pd.set_defaults(enabled=None)
    pd.set_defaults(func=command_devices)

    return p


def main(argv: Optional[list[str]] = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    func = getattr(args, "func", None)
    if not func:
        parser.print_help()
        return 2
    return int(func(args))


if __name__ == "__main__":
    sys.exit(main())


