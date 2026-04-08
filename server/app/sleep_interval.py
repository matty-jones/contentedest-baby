"""Helpers for sleep intervals that may cross local midnight in a single CSV row."""

from __future__ import annotations

from typing import Optional, Tuple

# Longest plausible continuous sleep (seconds); used to reject bad data after +1 day fix.
MAX_CONTINUOUS_SLEEP_SECONDS = 12 * 3600
SECONDS_PER_DAY = 86400


def normalize_sleep_end_after_start(
    start_ts: Optional[int],
    end_ts: Optional[int],
    *,
    max_duration_seconds: int = MAX_CONTINUOUS_SLEEP_SECONDS,
) -> Tuple[Optional[int], Optional[int], Optional[str]]:
    """
    When both bounds exist and end_ts < start_ts, treat end as the morning after start
    (add one local-calendar day in epoch space).

    If that would make duration > max_duration_seconds, do not adjust; return the original
    pair and error code "exceeds_max_duration_after_overnight_fix".

    Returns (start_ts, end_ts, error_or_none).
    """
    if start_ts is None or end_ts is None:
        return start_ts, end_ts, None
    if end_ts >= start_ts:
        return start_ts, end_ts, None
    fixed_end = end_ts + SECONDS_PER_DAY
    if fixed_end - start_ts > max_duration_seconds:
        return start_ts, end_ts, "exceeds_max_duration_after_overnight_fix"
    return start_ts, fixed_end, None
