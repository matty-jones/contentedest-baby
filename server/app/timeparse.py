from __future__ import annotations

from datetime import datetime, timedelta, timezone
from typing import Iterable

DEFAULT_PARSE_FORMATS = (
    "%Y-%m-%d %I:%M%p",
    "%Y-%m-%d %I:%M",
    "%Y-%m-%d %H:%M",
)


def parse_local_utc_minus_7_to_utc_ts(
    date_str: str, time_str: str, formats: Iterable[str] = DEFAULT_PARSE_FORMATS
) -> int | None:
    candidate = (time_str or "").strip()
    if not candidate:
        return None

    tz_utc_minus_7 = timezone(timedelta(hours=-7))
    for fmt in formats:
        try:
            naive_dt = datetime.strptime(f"{date_str} {candidate}", fmt)
        except ValueError:
            continue
        return int(naive_dt.replace(tzinfo=tz_utc_minus_7).timestamp())
    return None
