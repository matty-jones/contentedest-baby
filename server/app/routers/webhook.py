from __future__ import annotations

import os
import time

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from .. import crud
from ..auth import get_db, verify_webhook_secret
from ..schemas import CribWebhookPayload, CribWebhookResponse

router = APIRouter()


@router.post("/webhook/crib", response_model=CribWebhookResponse)
def webhook_crib(
    body: CribWebhookPayload,
    _: None = Depends(verify_webhook_secret),
    db: Session = Depends(get_db),
):
    device_id = (
        body.device_id or os.environ.get("CRIB_WEBHOOK_DEVICE_ID") or "crib_webhook"
    ).strip()
    now_ts = int(time.time())

    if body.state == "occupied":
        open_sleep = crud.get_open_sleep(db, device_id)
        if open_sleep:
            return CribWebhookResponse(
                action="already_recording", event_id=open_sleep.event_id
            )
        event = crud.create_crib_sleep(db, device_id, now_ts)
        return CribWebhookResponse(action="created", event_id=event.event_id)

    if body.state == "empty":
        open_sleep = crud.get_open_sleep(db, device_id)
        if not open_sleep:
            return CribWebhookResponse(action="no_open_sleep", event_id=None)
        discarded_id = open_sleep.event_id
        result, action = crud.close_crib_webhook_sleep(db, open_sleep, now_ts)
        if action == "discarded":
            return CribWebhookResponse(action="discarded", event_id=discarded_id)
        assert result is not None
        return CribWebhookResponse(action="closed", event_id=result.event_id)

    raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid state")
