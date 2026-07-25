from __future__ import annotations

import logging

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from .. import crud
from ..auth import get_db
from ..models import BabyProfile
from ..schemas import BabyProfileDTO, BabyProfileResponse

router = APIRouter()
logger = logging.getLogger(__name__)


def _to_dto(profile: BabyProfile) -> BabyProfileDTO:
    return BabyProfileDTO(
        dob_epoch_days=profile.dob_epoch_days,
        updated_ts=profile.updated_ts,
        version=profile.version,
        device_id=profile.device_id or "",
    )


@router.get("/baby-profile", response_model=BabyProfileResponse)
def get_baby_profile(db: Session = Depends(get_db)):
    profile = crud.get_baby_profile(db)
    return BabyProfileResponse(server_clock=crud.get_clock(db), data=_to_dto(profile))


@router.post("/baby-profile", response_model=BabyProfileResponse)
def push_baby_profile(data: BabyProfileDTO, db: Session = Depends(get_db)):
    logger.info(
        "Baby profile push: dob=%s version=%s device=%s",
        data.dob_epoch_days,
        data.version,
        data.device_id,
    )
    incoming = BabyProfile(
        id=1,
        dob_epoch_days=data.dob_epoch_days,
        updated_ts=data.updated_ts,
        version=data.version,
        device_id=data.device_id,
    )
    applied, clock = crud.upsert_baby_profile(db, incoming)
    return BabyProfileResponse(server_clock=clock, data=_to_dto(applied))
