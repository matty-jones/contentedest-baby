from app.event_policy import intervals_mergeable_ordered


def test_gap_within_60_mergeable():
    assert intervals_mergeable_ordered(1000, 2000, 2050, 3000) is True


def test_gap_61_not_mergeable():
    assert intervals_mergeable_ordered(1000, 2000, 2100, 3000) is False


def test_overlap_mergeable():
    assert intervals_mergeable_ordered(1000, 2500, 2000, 3000) is True
