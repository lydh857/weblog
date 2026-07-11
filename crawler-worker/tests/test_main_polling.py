import pytest

from app import main


class _DummySession:
    def __init__(self) -> None:
        self.committed = False
        self.rolled_back = False

    async def commit(self) -> None:
        self.committed = True

    async def rollback(self) -> None:
        self.rolled_back = True


class _DummySessionContext:
    def __init__(self, session: _DummySession) -> None:
        self._session = session

    async def __aenter__(self) -> _DummySession:
        return self._session

    async def __aexit__(self, exc_type, exc, tb) -> bool:
        return False


@pytest.mark.asyncio
async def test_run_poll_cycle_rolls_back_on_failure(monkeypatch):
    session = _DummySession()

    async def fake_poll_and_run_pending_tasks(_session, _scheduler):
        raise RuntimeError("boom")

    monkeypatch.setattr(main, "SessionLocal", lambda: _DummySessionContext(session))
    monkeypatch.setattr(main, "poll_and_run_pending_tasks", fake_poll_and_run_pending_tasks)

    await main._run_poll_cycle()

    assert session.rolled_back is True
    assert session.committed is False
