"""Durations and gaps for crib webhook and adjacent-event consolidation."""

# Crib/HA classification: discard completed sleep shorter than this (manual app exempt).
MIN_CRIB_WEBHOOK_SLEEP_SECONDS = 300

# Merge sleep or feed segments if gap or overlap within this many seconds.
ADJACENT_MERGE_GAP_SECONDS = 60

# Ad-hoc tidy: remove sleep shorter than this (after consolidation).
MIN_SLEEP_TO_KEEP_SECONDS = 300


def intervals_mergeable_ordered(
    a_start: int,
    a_end: int,
    b_start: int,
    b_end: int,
    gap_seconds: int = ADJACENT_MERGE_GAP_SECONDS,
) -> bool:
    """True if interval b follows a in time (sorted by start) and should merge (gap or overlap)."""
    if b_start < a_end and a_start < b_end:
        return True
    gap = b_start - a_end
    return 0 <= gap <= gap_seconds
