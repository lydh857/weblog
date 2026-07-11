from __future__ import annotations

from pathlib import Path

import pytest
from sqlalchemy import select
from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine

from app.models import Base, CrawlerDiscoveredArticle, CrawlerTask, CrawlerTaskItem, SiteProfile
from app.services.extractor import ExtractResult
from app.services.scheduler import TaskScheduler, _expand_batch_tasks


async def _create_session_factory(tmp_path: Path):
    engine = create_async_engine(f"sqlite+aiosqlite:///{(tmp_path / 'crawler-test.db').as_posix()}", future=True, echo=False)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    return engine, async_sessionmaker(bind=engine, autoflush=False, autocommit=False, expire_on_commit=False)


async def _create_profile(session, domain: str = "example.com") -> SiteProfile:
    profile = SiteProfile(
        name="示例站点",
        domain=domain,
        default_list_url=f"https://{domain}/list",
        list_selector=".article-link",
        article_selector="article",
        max_items=100,
        interval_seconds=1,
        timeout_seconds=5,
        enabled=1,
    )
    session.add(profile)
    await session.flush()
    return profile


@pytest.mark.asyncio
async def test_batch_tasks_only_create_new_items_for_new_articles(tmp_path, monkeypatch):
    engine, session_factory = await _create_session_factory(tmp_path)
    monkeypatch.setattr("app.services.scheduler.settings.batch_known_url_stop_count", 10)
    monkeypatch.setattr("app.services.scheduler.settings.batch_expand_scan_multiplier", 5)
    monkeypatch.setattr("app.services.scheduler.settings.batch_expand_max_scan_items", 100)

    first_batch_links = [f"https://example.com/posts/{idx}" for idx in range(1, 21)]
    second_batch_links = first_batch_links + [f"https://example.com/posts/{idx}" for idx in range(21, 31)]
    extracted_batches = [first_batch_links, second_batch_links]

    async def fake_extract_candidate_urls_from_list(**_kwargs):
        return extracted_batches.pop(0)

    monkeypatch.setattr("app.services.scheduler.extract_candidate_urls_from_list", fake_extract_candidate_urls_from_list)

    async with session_factory() as session:
        profile = await _create_profile(session)

        first_task = CrawlerTask(mode="batch", source_url=profile.default_list_url, site_profile_id=profile.id, max_items=20, status="queued")
        session.add(first_task)
        await session.commit()

        await _expand_batch_tasks(session)
        await session.commit()

        first_items = (
            await session.execute(select(CrawlerTaskItem).where(CrawlerTaskItem.task_id == first_task.id).order_by(CrawlerTaskItem.id.asc()))
        ).scalars().all()
        assert len(first_items) == 20

        second_task = CrawlerTask(mode="batch", source_url=profile.default_list_url, site_profile_id=profile.id, max_items=10, status="queued")
        session.add(second_task)
        await session.commit()

        await _expand_batch_tasks(session)
        await session.commit()

        second_items = (
            await session.execute(select(CrawlerTaskItem).where(CrawlerTaskItem.task_id == second_task.id).order_by(CrawlerTaskItem.id.asc()))
        ).scalars().all()
        assert len(second_items) == 10
        assert {item.normalized_url for item in second_items} == {f"https://example.com/posts/{idx}" for idx in range(21, 31)}

        discovered_rows = (await session.execute(select(CrawlerDiscoveredArticle))).scalars().all()
        assert len(discovered_rows) == 30

        refreshed_second_task = await session.scalar(select(CrawlerTask).where(CrawlerTask.id == second_task.id))
        assert refreshed_second_task is not None
        assert refreshed_second_task.status == "running"

    await engine.dispose()


@pytest.mark.asyncio
async def test_batch_task_marks_done_when_all_links_are_known(tmp_path, monkeypatch):
    engine, session_factory = await _create_session_factory(tmp_path)
    known_links = [f"https://example.com/posts/{idx}" for idx in range(1, 6)]

    async def fake_extract_candidate_urls_from_list(**_kwargs):
        return known_links

    monkeypatch.setattr("app.services.scheduler.extract_candidate_urls_from_list", fake_extract_candidate_urls_from_list)
    monkeypatch.setattr("app.services.scheduler.settings.batch_known_url_stop_count", 3)

    async with session_factory() as session:
        profile = await _create_profile(session)
        for link in known_links:
            session.add(
                CrawlerDiscoveredArticle(
                    site_profile_id=profile.id,
                    normalized_url=link,
                    status="fetched",
                )
            )
        task = CrawlerTask(mode="batch", source_url=profile.default_list_url, site_profile_id=profile.id, max_items=5, status="queued")
        session.add(task)
        await session.commit()

        await _expand_batch_tasks(session)
        await session.commit()

        task_items = (await session.execute(select(CrawlerTaskItem).where(CrawlerTaskItem.task_id == task.id))).scalars().all()
        assert task_items == []

        refreshed_task = await session.scalar(select(CrawlerTask).where(CrawlerTask.id == task.id))
        assert refreshed_task is not None
        assert refreshed_task.status == "done"

    await engine.dispose()


@pytest.mark.asyncio
async def test_batch_task_requeues_failed_discovered_article(tmp_path, monkeypatch):
    engine, session_factory = await _create_session_factory(tmp_path)
    target_link = "https://example.com/posts/failed-one"

    async def fake_extract_candidate_urls_from_list(**_kwargs):
        return [target_link]

    monkeypatch.setattr("app.services.scheduler.extract_candidate_urls_from_list", fake_extract_candidate_urls_from_list)

    async with session_factory() as session:
        profile = await _create_profile(session)
        discovered = CrawlerDiscoveredArticle(
            site_profile_id=profile.id,
            normalized_url=target_link,
            status="failed",
            failure_count=2,
            last_error="旧错误",
        )
        session.add(discovered)
        await session.flush()

        task = CrawlerTask(mode="batch", source_url=profile.default_list_url, site_profile_id=profile.id, max_items=1, status="queued")
        session.add(task)
        await session.commit()

        await _expand_batch_tasks(session)
        await session.commit()

        task_item = await session.scalar(select(CrawlerTaskItem).where(CrawlerTaskItem.task_id == task.id))
        assert task_item is not None
        assert task_item.discovered_article_id == discovered.id

        refreshed_discovered = await session.scalar(select(CrawlerDiscoveredArticle).where(CrawlerDiscoveredArticle.id == discovered.id))
        assert refreshed_discovered is not None
        assert refreshed_discovered.status == "queued"
        assert refreshed_discovered.last_error is None
        assert refreshed_discovered.failure_count == 2

    await engine.dispose()


@pytest.mark.asyncio
async def test_process_task_item_updates_discovered_article_status_on_success(tmp_path, monkeypatch):
    engine, session_factory = await _create_session_factory(tmp_path)
    scheduler = TaskScheduler()

    async def fake_extract_article(*_args, **_kwargs):
        return ExtractResult(
            title="新文章",
            summary="摘要",
            markdown="# 标题\n正文",
            source_site="example.com",
            fallback_used=False,
            image_urls=[],
            tags=["Python"],
            category_level1="技术",
            category_level2="后端",
            published_at=None,
            author="作者",
        )

    async def fake_enforce_cache_quota(_session):
        return None

    monkeypatch.setattr("app.services.scheduler.extract_article", fake_extract_article)
    monkeypatch.setattr("app.services.scheduler.enforce_cache_quota", fake_enforce_cache_quota)
    monkeypatch.setattr("app.services.scheduler.ensure_url_allowed", lambda *_args, **_kwargs: None)

    async with session_factory() as session:
        profile = await _create_profile(session)
        discovered = CrawlerDiscoveredArticle(site_profile_id=profile.id, normalized_url="https://example.com/posts/1", status="queued")
        session.add(discovered)
        await session.flush()

        task = CrawlerTask(mode="batch", source_url=profile.default_list_url, site_profile_id=profile.id, max_items=1, status="running")
        session.add(task)
        await session.flush()

        item = CrawlerTaskItem(
            task_id=task.id,
            discovered_article_id=discovered.id,
            item_idempotency_key="item-1",
            external_url="https://example.com/posts/1",
            normalized_url="https://example.com/posts/1",
            state="queued",
        )
        session.add(item)
        await session.commit()

        await scheduler.process_task_item(session, item, article_selector="article", timeout_seconds=5)
        await session.commit()

        refreshed_discovered = await session.scalar(select(CrawlerDiscoveredArticle).where(CrawlerDiscoveredArticle.id == discovered.id))
        assert refreshed_discovered is not None
        assert refreshed_discovered.status == "fetched"
        assert refreshed_discovered.title == "新文章"
        assert refreshed_discovered.content_fingerprint is not None

    await engine.dispose()


@pytest.mark.asyncio
async def test_process_task_item_marks_discovered_article_failed_after_retry_exhausted(tmp_path, monkeypatch):
    engine, session_factory = await _create_session_factory(tmp_path)
    scheduler = TaskScheduler()

    async def fake_extract_article(*_args, **_kwargs):
        raise RuntimeError("抓取失败")

    monkeypatch.setattr("app.services.scheduler.extract_article", fake_extract_article)
    monkeypatch.setattr("app.services.scheduler.settings.max_retry_count", 0)
    monkeypatch.setattr("app.services.scheduler.ensure_url_allowed", lambda *_args, **_kwargs: None)

    async with session_factory() as session:
        profile = await _create_profile(session)
        discovered = CrawlerDiscoveredArticle(site_profile_id=profile.id, normalized_url="https://example.com/posts/2", status="queued")
        session.add(discovered)
        await session.flush()

        task = CrawlerTask(mode="batch", source_url=profile.default_list_url, site_profile_id=profile.id, max_items=1, status="running")
        session.add(task)
        await session.flush()

        item = CrawlerTaskItem(
            task_id=task.id,
            discovered_article_id=discovered.id,
            item_idempotency_key="item-2",
            external_url="https://example.com/posts/2",
            normalized_url="https://example.com/posts/2",
            state="queued",
        )
        session.add(item)
        await session.commit()

        await scheduler.process_task_item(session, item, article_selector="article", timeout_seconds=5)
        await session.commit()

        refreshed_item = await session.scalar(select(CrawlerTaskItem).where(CrawlerTaskItem.id == item.id))
        refreshed_discovered = await session.scalar(select(CrawlerDiscoveredArticle).where(CrawlerDiscoveredArticle.id == discovered.id))
        assert refreshed_item is not None
        assert refreshed_item.state == "failed"
        assert refreshed_discovered is not None
        assert refreshed_discovered.status == "failed"
        assert refreshed_discovered.failure_count == 1
        assert refreshed_discovered.last_error == "抓取失败"

    await engine.dispose()
