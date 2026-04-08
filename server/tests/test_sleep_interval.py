"""Tests for overnight sleep interval normalization."""

from app.sleep_interval import (
    MAX_CONTINUOUS_SLEEP_SECONDS,
    normalize_sleep_end_after_start,
)


def test_ordered_interval_unchanged():
    s, e, err = normalize_sleep_end_after_start(1000, 2000)
    assert err is None
    assert s == 1000 and e == 2000


def test_overnight_adds_one_day():
    # Same calendar date for evening start and morning end: end is 16h before start (e.g. 6am vs 10pm).
    start = 1_000_000
    end_same_calendar_morning = start - 16 * 3600
    s, e, err = normalize_sleep_end_after_start(start, end_same_calendar_morning)
    assert err is None
    assert s == start
    assert e == end_same_calendar_morning + 86400
    assert e - s == 8 * 3600


def test_exceeds_12h_not_fixed():
    start = 2_000_000
    end = start - 1000
    s, e, err = normalize_sleep_end_after_start(start, end)
    assert err == "exceeds_max_duration_after_overnight_fix"
    assert s == start and e == end
