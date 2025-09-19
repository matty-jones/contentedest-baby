from __future__ import annotations
from fastapi import Header, HTTPException, status, Depends
from sqlalchemy.orm import Session
from .database import SessionLocal
from .models import Device
from .security import token_hash as th


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def get_current_device(authorization: str | None = Header(default=None), db: Session = Depends(get_db)) -> Device:
    if not authorization or not authorization.lower().startswith("bearer "):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Missing token")
    token = authorization.split(" ", 1)[1].strip()
    token_h = th(token)
    device = db.query(Device).filter(Device.token_hash == token_h, Device.enabled == True).first()
    if not device:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token")
    return device


