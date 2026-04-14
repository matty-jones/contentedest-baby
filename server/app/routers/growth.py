from __future__ import annotations

import logging

from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.orm import Session

from .. import crud
from ..auth import get_db
from ..models import GrowthData
from ..schemas import GrowthDataDTO, GrowthPullResponse, GrowthPushResponse

router = APIRouter()
logger = logging.getLogger(__name__)


@router.post("/growth", response_model=GrowthPushResponse)
def create_growth_data(data: GrowthDataDTO, db: Session = Depends(get_db)):
    logger.info("Growth push: %s (%s)", data.id, data.category)
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
    logger.info("Applied growth data %s, new clock: %s", applied_data.id, new_clock)

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
    return GrowthPushResponse(server_clock=new_clock, applied=True, data=result_dto)


@router.get("/growth", response_model=GrowthPullResponse)
def get_growth_data(
    category: str | None = None, since: int = 0, db: Session = Depends(get_db)
):
    logger.info("Growth pull: category=%s, since=%s", category, since)
    if since > 0:
        data_list = crud.select_growth_data_since(db, since, category)
    elif category:
        data_list = crud.get_growth_data_by_category(db, category)
    else:
        stmt = (
            select(GrowthData)
            .where(GrowthData.deleted == False)
            .order_by(GrowthData.ts)
        )
        data_list = list(db.scalars(stmt).all())

    current_clock = crud.get_clock(db)
    logger.info("Returning %s growth entries, clock=%s", len(data_list), current_clock)

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
        )
        for gd in data_list
    ]
    return GrowthPullResponse(server_clock=current_clock, data=payload)
