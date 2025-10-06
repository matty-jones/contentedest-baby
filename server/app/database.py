from __future__ import annotations
import os
from sqlalchemy import create_engine, event
from sqlalchemy.orm import sessionmaker, DeclarativeBase
from sqlalchemy.engine import Engine

# Allow overriding DB path for tests via env var
DB_PATH = os.environ.get("TCB_DB_PATH", os.path.join(os.path.dirname(__file__), "..", "data.db"))
SQLALCHEMY_DATABASE_URL = f"sqlite:///{os.path.abspath(DB_PATH)}"

os.makedirs(os.path.dirname(os.path.abspath(DB_PATH)), exist_ok=True)

@event.listens_for(Engine, "connect")
def set_sqlite_pragma(dbapi_connection, connection_record):
    try:
        cursor = dbapi_connection.cursor()
        cursor.execute("PRAGMA journal_mode=WAL;")
        cursor.execute("PRAGMA synchronous=NORMAL;")
        cursor.execute("PRAGMA foreign_keys=ON;")
        cursor.close()
    except Exception:
        # Best-effort; ignore if not SQLite
        pass


class Base(DeclarativeBase):
    pass


engine = create_engine(SQLALCHEMY_DATABASE_URL, echo=False, future=True)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


