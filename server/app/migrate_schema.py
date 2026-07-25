from __future__ import annotations

import logging

from sqlalchemy import text
from sqlalchemy.engine import Engine

logger = logging.getLogger(__name__)


def migrate_schema(engine: Engine) -> None:
    """Apply additive SQLite column migrations that create_all cannot do."""
    alterations = [
        ("baby_words", "understands", "ALTER TABLE baby_words ADD COLUMN understands BOOLEAN NOT NULL DEFAULT 0"),
        ("baby_words", "says", "ALTER TABLE baby_words ADD COLUMN says BOOLEAN NOT NULL DEFAULT 0"),
    ]
    with engine.begin() as conn:
        for table, column, ddl in alterations:
            rows = conn.execute(text(f"PRAGMA table_info({table})")).fetchall()
            if any(row[1] == column for row in rows):
                continue
            logger.info("Applying schema migration: %s", ddl)
            conn.execute(text(ddl))
