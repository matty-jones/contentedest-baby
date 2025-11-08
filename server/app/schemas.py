from __future__ import annotations
from pydantic import BaseModel
from typing import Optional, Literal, List


class PairRequest(BaseModel):
    pairing_code: str
    device_id: str
    name: Optional[str] = None


class PairResponse(BaseModel):
    device_id: str
    token: str


class EventDTO(BaseModel):
    event_id: str
    type: Literal["sleep", "feed", "nappy"]
    details: Optional[str] = None  # New field for Details from CSV
    payload: Optional[dict] = None
    start_ts: Optional[int] = None
    end_ts: Optional[int] = None
    ts: Optional[int] = None
    created_ts: int
    updated_ts: int
    version: int
    deleted: bool = False
    device_id: str


class SyncPushResponseItem(BaseModel):
    event: EventDTO
    applied: bool


class SyncPushResponse(BaseModel):
    server_clock: int
    results: List[SyncPushResponseItem]


class SyncPullResponse(BaseModel):
    server_clock: int
    events: List[EventDTO]


class UpdateInfoResponse(BaseModel):
    version_code: int
    version_name: str
    download_url: str
    release_notes: Optional[str] = None
    mandatory: bool = False


class GrowthDataDTO(BaseModel):
    id: str
    device_id: str
    category: Literal["weight", "height", "head"]
    value: float
    unit: str
    ts: int
    created_ts: int
    updated_ts: int
    version: int
    deleted: bool = False


class GrowthPushResponse(BaseModel):
    server_clock: int
    applied: bool
    data: GrowthDataDTO


class GrowthPullResponse(BaseModel):
    server_clock: int
    data: List[GrowthDataDTO]


