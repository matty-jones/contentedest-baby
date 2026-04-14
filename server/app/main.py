from __future__ import annotations

import logging

from fastapi import FastAPI, Request

from .database import Base, SessionLocal, engine
from .routers import (
    growth_router,
    health_admin_router,
    sync_router,
    updates_router,
    webhook_router,
)
from .seed import seed_database

app = FastAPI(title="The Contentedest Baby Server")

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
)
logger = logging.getLogger(__name__)


@app.middleware("http")
async def log_requests(request: Request, call_next):
    logger.info("Request: %s %s", request.method, request.url)
    response = await call_next(request)
    logger.info("Response: %s", response.status_code)
    return response


Base.metadata.create_all(bind=engine)


@app.on_event("startup")
def startup_event():
    db = SessionLocal()
    try:
        seed_database(db)
    finally:
        db.close()


app.include_router(health_admin_router)
app.include_router(webhook_router)
app.include_router(sync_router)
app.include_router(updates_router)
app.include_router(growth_router)
