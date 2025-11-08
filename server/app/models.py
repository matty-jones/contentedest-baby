from __future__ import annotations
from sqlalchemy import Integer, String, JSON, Boolean, Float
from sqlalchemy.orm import Mapped, mapped_column
from .database import Base


class Device(Base):
    __tablename__ = "devices"

    device_id: Mapped[str] = mapped_column(String, primary_key=True)
    name: Mapped[str | None] = mapped_column(String, nullable=True)
    created_ts: Mapped[int] = mapped_column(Integer, nullable=False)
    last_seen_ts: Mapped[int] = mapped_column(Integer, nullable=False)
    token_hash: Mapped[str] = mapped_column(String, nullable=False)
    enabled: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)


class Event(Base):
    __tablename__ = "events"

    event_id: Mapped[str] = mapped_column(String, primary_key=True)
    type: Mapped[str] = mapped_column(String, nullable=False)
    details: Mapped[str | None] = mapped_column(String, nullable=True)  # New field for Details from CSV
    payload: Mapped[dict | None] = mapped_column(JSON, nullable=True)
    start_ts: Mapped[int | None] = mapped_column(Integer, nullable=True)
    end_ts: Mapped[int | None] = mapped_column(Integer, nullable=True)
    ts: Mapped[int | None] = mapped_column(Integer, nullable=True)
    created_ts: Mapped[int] = mapped_column(Integer, nullable=False)
    updated_ts: Mapped[int] = mapped_column(Integer, nullable=False)
    version: Mapped[int] = mapped_column(Integer, nullable=False)
    deleted: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    device_id: Mapped[str] = mapped_column(String, nullable=False)
    server_clock: Mapped[int] = mapped_column(Integer, nullable=False, default=0)


class Watermark(Base):
    __tablename__ = "watermarks"

    device_id: Mapped[str] = mapped_column(String, primary_key=True)
    last_clock: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    updated_ts: Mapped[int] = mapped_column(Integer, nullable=False)


class ServerClock(Base):
    __tablename__ = "server_clock"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    counter: Mapped[int] = mapped_column(Integer, nullable=False)


class GrowthData(Base):
    __tablename__ = "growth_data"

    id: Mapped[str] = mapped_column(String, primary_key=True)
    device_id: Mapped[str] = mapped_column(String, nullable=False)
    category: Mapped[str] = mapped_column(String, nullable=False)  # weight, height, head
    value: Mapped[float] = mapped_column(Float, nullable=False)
    unit: Mapped[str] = mapped_column(String, nullable=False)  # lb, in, cm
    ts: Mapped[int] = mapped_column(Integer, nullable=False)  # timestamp of measurement
    created_ts: Mapped[int] = mapped_column(Integer, nullable=False)
    updated_ts: Mapped[int] = mapped_column(Integer, nullable=False)
    version: Mapped[int] = mapped_column(Integer, nullable=False)
    deleted: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    server_clock: Mapped[int] = mapped_column(Integer, nullable=False, default=0)


