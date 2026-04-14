from __future__ import annotations

from fastapi import APIRouter, Depends, status
from sqlalchemy.orm import Session

from ..auth import get_db
from ..models import Event
from ..seed import seed_database

router = APIRouter()


@router.get("/health", status_code=status.HTTP_200_OK)
def health():
    return {"status": "ok"}


@router.get("/healthz")
def healthz():
    return {"status": "ok"}


@router.post("/admin/seed")
def seed_database_endpoint(db: Session = Depends(get_db)):
    seed_database(db)
    return {"message": "Database seeded successfully"}


@router.get("/admin/events/count")
def get_event_count(db: Session = Depends(get_db)):
    count = db.query(Event).count()
    return {"count": count}
