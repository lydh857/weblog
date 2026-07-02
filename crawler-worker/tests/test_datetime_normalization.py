from datetime import datetime, timezone

from app.models import utc_now
from app.services.extractor import _normalize_datetime


def test_utc_now_returns_naive_datetime():
    value = utc_now()

    assert value.tzinfo is None


def test_normalize_datetime_keeps_naive_value():
    value = datetime(2026, 4, 22, 12, 0, 0)

    assert _normalize_datetime(value) == value


def test_normalize_datetime_converts_aware_value_to_local_time():
    value = datetime(2026, 4, 22, 12, 0, 0, tzinfo=timezone.utc)

    normalized = _normalize_datetime(value)

    assert normalized is not None
    assert normalized.tzinfo is None
    assert normalized == value.astimezone().replace(tzinfo=None)
