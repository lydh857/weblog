from pathlib import Path

import pytest
from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine

from app.models import Base, CrawlerAsset
from app.services.assets import delete_unreferenced_files, stage_image


class _FakeResponse:
    def __init__(self, content: bytes, content_type: str = "image/png") -> None:
        self.content = content
        self.headers = {"content-type": content_type}

    def raise_for_status(self) -> None:
        return None


class _FakeAsyncClient:
    def __init__(self, *_args, **_kwargs) -> None:
        pass

    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc, tb):
        return False

    async def get(self, _url: str):
        return _FakeResponse(b"same-image-bytes")


async def _create_session_factory(tmp_path: Path):
    engine = create_async_engine(f"sqlite+aiosqlite:///{(tmp_path / 'assets-test.db').as_posix()}", future=True, echo=False)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    return engine, async_sessionmaker(bind=engine, autoflush=False, autocommit=False, expire_on_commit=False)


@pytest.mark.asyncio
async def test_stage_image_reuses_same_file_for_same_content(tmp_path, monkeypatch):
    engine, session_factory = await _create_session_factory(tmp_path)
    monkeypatch.setattr("app.services.assets.httpx.AsyncClient", _FakeAsyncClient)
    monkeypatch.setattr("app.services.assets.settings.cache_dir", str(tmp_path / "cache"))

    async with session_factory() as session:
        asset1 = await stage_image(session, 1, "https://example.com/a.png")
        asset2 = await stage_image(session, 2, "https://example.com/b.png")
        await session.commit()

        assert asset1.local_path == asset2.local_path
        assert asset1.sha256 != asset2.sha256
        assert Path(asset1.local_path).exists()

    await engine.dispose()


@pytest.mark.asyncio
async def test_delete_unreferenced_files_keeps_shared_file_until_last_reference_removed(tmp_path):
    engine, session_factory = await _create_session_factory(tmp_path)
    shared_path = tmp_path / "cache" / "shared.png"
    shared_path.parent.mkdir(parents=True, exist_ok=True)
    shared_path.write_bytes(b"shared")

    async with session_factory() as session:
        asset1 = CrawlerAsset(task_item_id=1, source_url="https://example.com/a.png", local_path=str(shared_path), mime_type="image/png", file_size=6, sha256="a1")
        asset2 = CrawlerAsset(task_item_id=2, source_url="https://example.com/b.png", local_path=str(shared_path), mime_type="image/png", file_size=6, sha256="b2")
        session.add_all([asset1, asset2])
        await session.commit()

        await session.delete(asset1)
        await session.flush()
        deleted = await delete_unreferenced_files(session, [str(shared_path)])
        assert deleted == 0
        assert shared_path.exists()

        await session.delete(asset2)
        await session.flush()
        deleted = await delete_unreferenced_files(session, [str(shared_path)])
        assert deleted == 1
        assert not shared_path.exists()

    await engine.dispose()
