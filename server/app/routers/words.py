from __future__ import annotations

import logging

from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.orm import Session

from .. import crud
from ..auth import get_db
from ..models import BabyWord
from ..schemas import WordDTO, WordPullResponse, WordPushResponse

router = APIRouter()
logger = logging.getLogger(__name__)


def _to_dto(w: BabyWord) -> WordDTO:
    return WordDTO(
        id=w.id,
        device_id=w.device_id,
        word=w.word,
        ts=w.ts,
        created_ts=w.created_ts,
        updated_ts=w.updated_ts,
        version=w.version,
        deleted=w.deleted,
        understands=bool(w.understands),
        says=bool(w.says),
    )


@router.post("/words", response_model=WordPushResponse)
def create_word(data: WordDTO, db: Session = Depends(get_db)):
    logger.info("Word push: %s (%s)", data.id, data.word)
    incoming = BabyWord(
        id=data.id,
        device_id=data.device_id,
        word=data.word,
        ts=data.ts,
        created_ts=data.created_ts,
        updated_ts=data.updated_ts,
        version=data.version,
        deleted=data.deleted,
        understands=data.understands,
        says=data.says,
    )
    applied_data, new_clock = crud.upsert_baby_word(db, incoming)
    logger.info("Applied word %s, new clock: %s", applied_data.id, new_clock)
    return WordPushResponse(server_clock=new_clock, applied=True, data=_to_dto(applied_data))


@router.get("/words", response_model=WordPullResponse)
def get_words(since: int = 0, db: Session = Depends(get_db)):
    logger.info("Word pull: since=%s", since)
    if since > 0:
        data_list = crud.select_baby_words_since(db, since)
    else:
        stmt = (
            select(BabyWord)
            .where(BabyWord.deleted == False)
            .order_by(BabyWord.ts)
        )
        data_list = list(db.scalars(stmt).all())

    current_clock = crud.get_clock(db)
    logger.info("Returning %s word entries, clock=%s", len(data_list), current_clock)
    return WordPullResponse(server_clock=current_clock, data=[_to_dto(w) for w in data_list])
