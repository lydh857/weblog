from __future__ import annotations

import asyncio
import hashlib
import json
import logging
from collections import defaultdict
from urllib.parse import urlparse

from apscheduler.schedulers.asyncio import AsyncIOScheduler
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.models import CrawlerDiscoveredArticle, CrawlerTask, CrawlerTaskItem, SiteProfile
from app.services.assets import AssetValidationError, enforce_cache_quota, stage_image
from app.services.diagnostics import add_diagnostic
from app.services.extractor import extract_article, extract_candidate_urls_from_list
from app.services.url_normalizer import normalize_article_url
from app.services.url_policy import UrlPolicyError, ensure_url_allowed

logger = logging.getLogger(__name__)


def _now():
    from datetime import datetime

    return datetime.now()


async def _touch_task(session: AsyncSession, task_id: int, stage: str | None = None, error_summary: str | None = None) -> None:
    task = await session.scalar(select(CrawlerTask).where(CrawlerTask.id == task_id))
    if task is None:
        return
    task.last_active_at = _now()
    if stage:
        task.current_stage = stage
    if error_summary is not None:
        task.last_error_summary = error_summary[:500] if error_summary else None


async def _set_item_state(session: AsyncSession, item: CrawlerTaskItem, state: str, error_kind: str | None = None) -> None:
    item.state = state
    item.last_error_kind = error_kind
    await _touch_task(session, item.task_id, stage=state)
    await session.flush()


async def _is_task_paused(session: AsyncSession, task_id: int) -> bool:
    task = await session.scalar(select(CrawlerTask).where(CrawlerTask.id == task_id))
    return task is not None and task.status == "paused"


class TaskScheduler:
    def __init__(self) -> None:
        self._scheduler = AsyncIOScheduler()
        self._global_semaphore = asyncio.Semaphore(settings.max_global_concurrency)
        self._domain_semaphores = defaultdict(lambda: asyncio.Semaphore(settings.max_domain_concurrency))

    def start(self) -> None:
        self._scheduler.add_job(self._tick, "interval", seconds=settings.scheduler_tick_seconds)
        self._scheduler.start()

    def shutdown(self) -> None:
        self._scheduler.shutdown(wait=False)

    async def _tick(self) -> None:
        logger.debug("scheduler tick")

    async def process_task_item(
        self,
        session: AsyncSession,
        item: CrawlerTaskItem,
        article_selector: str | None = None,
        timeout_seconds: int | None = None,
        interval_seconds: int | None = None,
    ) -> None:
        if await _is_task_paused(session, item.task_id):
            return
        domain = urlparse(item.external_url).netloc
        domain_semaphore = self._domain_semaphores[domain]
        await _touch_task(session, item.task_id, stage="queued")

        async with self._global_semaphore, domain_semaphore:
            if await _is_task_paused(session, item.task_id):
                return
            if interval_seconds and interval_seconds > 0:
                await asyncio.sleep(interval_seconds)
            if await _is_task_paused(session, item.task_id):
                return
            await self._run_item_with_retry(session, item, article_selector, timeout_seconds)

    async def _run_item_with_retry(
        self,
        session: AsyncSession,
        item: CrawlerTaskItem,
        article_selector: str | None,
        timeout_seconds: int | None,
    ) -> None:
        try:
            ensure_url_allowed(item.external_url)
        except UrlPolicyError as ex:
            item.fail_reason = str(ex)
            item.last_error_kind = "policy_blocked"
            await _set_item_state(session, item, "policy_blocked", "policy_blocked")
            await _mark_discovered_article_failed(session, item, str(ex))
            await add_diagnostic(session, item.id, "policy_blocked", "url policy blocked", str(ex))
            await _touch_task(session, item.task_id, stage="policy_blocked", error_summary=str(ex))
            return

        while item.retry_count <= settings.max_retry_count:
            try:
                if await _is_task_paused(session, item.task_id):
                    return
                await _set_item_state(session, item, "fetching")
                if await _is_task_paused(session, item.task_id):
                    return
                await _set_item_state(session, item, "extracting")

                extracted = await extract_article(
                    item.external_url,
                    article_selector=article_selector,
                    timeout_seconds=timeout_seconds,
                )
                item.title = extracted.title
                item.summary = extracted.summary
                item.content_markdown = extracted.markdown
                suggested_tags = extracted.tags or []
                item.extracted_tags_json = json.dumps(suggested_tags, ensure_ascii=False) if suggested_tags else None
                item.tags_json = json.dumps(suggested_tags[:5], ensure_ascii=False) if suggested_tags else None
                item.category_level1 = extracted.category_level1
                item.category_level2 = extracted.category_level2
                item.source_site = extracted.source_site
                item.content_fingerprint = hashlib.sha256(extracted.markdown.encode("utf-8")).hexdigest()
                await _mark_discovered_article_fetched(session, item, extracted)

                if extracted.fallback_used:
                    await add_diagnostic(session, item.id, "fallback", "dynamic renderer fallback triggered")

                if await _is_task_paused(session, item.task_id):
                    return
                if extracted.image_urls:
                    await _set_item_state(session, item, "staging_assets")

                for image_url in extracted.image_urls:
                    if await _is_task_paused(session, item.task_id):
                        return
                    try:
                        asset = await stage_image(session, item.id, image_url, referer_url=item.external_url)
                        if item.cover_asset_id is None:
                            item.cover_asset_id = asset.id
                            asset.asset_role = "cover"
                    except AssetValidationError as ex:
                        await add_diagnostic(session, item.id, "asset_skip", str(ex), image_url)

                item.fail_reason = None
                item.last_error_kind = None
                if await _is_task_paused(session, item.task_id):
                    return
                await _set_item_state(session, item, "review_pending")
                await _touch_task(session, item.task_id, stage="review_pending", error_summary=None)
                await enforce_cache_quota(session)
                return
            except Exception as ex:
                item.retry_count += 1
                item.fail_reason = str(ex)
                item.last_error_kind = "extract_failed"
                if item.retry_count > settings.max_retry_count:
                    await _set_item_state(session, item, "failed", "extract_failed")
                    await _mark_discovered_article_failed(session, item, str(ex))
                    await add_diagnostic(session, item.id, "failed", "item retry exhausted", str(ex))
                    await _touch_task(session, item.task_id, stage="failed", error_summary=str(ex))
                    return
                await add_diagnostic(session, item.id, "retry", "item retry", str(ex))
                await _touch_task(session, item.task_id, stage="extracting", error_summary=str(ex))


async def poll_and_run_pending_tasks(session: AsyncSession, scheduler: TaskScheduler) -> None:
    logger.info("worker poll cycle started")
    await _expand_batch_tasks(session)

    pending_items = (
        await session.execute(
            select(CrawlerTaskItem).where(CrawlerTaskItem.state.in_(["queued", "crawling"]))
        )
    ).scalars().all()

    for item in pending_items:
        task = await session.scalar(select(CrawlerTask).where(CrawlerTask.id == item.task_id))
        if not task or task.status in {"paused", "done"}:
            continue
        profile = None
        if task.site_profile_id:
            profile = await session.scalar(select(SiteProfile).where(SiteProfile.id == task.site_profile_id))
        await scheduler.process_task_item(
            session,
            item,
            article_selector=profile.article_selector if profile else None,
            timeout_seconds=profile.timeout_seconds if profile else None,
            interval_seconds=profile.interval_seconds if profile else None,
        )

    await _sync_task_status(session)


async def _expand_batch_tasks(session: AsyncSession) -> None:
    batch_tasks = (
        await session.execute(
            select(CrawlerTask).where(CrawlerTask.mode == "batch", CrawlerTask.status.in_(["queued", "running"]))
        )
    ).scalars().all()

    for task in batch_tasks:
        await _touch_task(session, task.id, stage="expanding")
        existing_count = await session.scalar(
            select(CrawlerTaskItem.id).where(CrawlerTaskItem.task_id == task.id).limit(1)
        )
        if existing_count:
            continue
        if not task.site_profile_id or not task.source_url:
            task.status = "failed"
            task.current_stage = "failed"
            task.last_error_summary = "批量任务缺少站点规则或列表页链接"
            continue

        profile = await session.scalar(select(SiteProfile).where(SiteProfile.id == task.site_profile_id))
        if not profile or not profile.enabled:
            task.status = "failed"
            task.current_stage = "failed"
            task.last_error_summary = "站点规则不存在或已停用"
            continue

        try:
            target_item_limit = min(task.max_items, profile.max_items)
            scan_limit = min(
                settings.batch_expand_max_scan_items,
                max(
                    target_item_limit * settings.batch_expand_scan_multiplier,
                    target_item_limit + settings.batch_known_url_stop_count,
                ),
            )
            links = await extract_candidate_urls_from_list(
                list_url=task.source_url,
                list_selector=profile.list_selector,
                domain=profile.domain,
                max_items=target_item_limit,
                scan_limit=scan_limit,
                timeout_seconds=profile.timeout_seconds,
            )
            if not links:
                task.status = "failed"
                task.current_stage = "failed"
                task.last_error_summary = "列表页未提取到任何链接"
                continue

            existing_records = (
                await session.execute(
                    select(CrawlerDiscoveredArticle).where(
                        CrawlerDiscoveredArticle.site_profile_id == profile.id,
                        CrawlerDiscoveredArticle.normalized_url.in_(links),
                    )
                )
            ).scalars().all()
            existing_by_url = {record.normalized_url: record for record in existing_records}

            created_count = 0
            skipped_known_count = 0
            consecutive_known = 0
            task.scanned_link_count = len(links)
            for link in links:
                normalized_link = normalize_article_url(link)
                discovered = existing_by_url.get(normalized_link)
                if discovered is not None and discovered.status != "failed":
                    discovered.last_seen_at = _now()
                    discovered.last_task_id = task.id
                    skipped_known_count += 1
                    consecutive_known += 1
                    if created_count > 0 and consecutive_known >= settings.batch_known_url_stop_count:
                        break
                    continue

                consecutive_known = 0
                seen_at = _now()
                if discovered is None:
                    discovered = CrawlerDiscoveredArticle(
                        site_profile_id=profile.id,
                        normalized_url=normalized_link,
                        status="queued",
                        last_task_id=task.id,
                        first_seen_at=seen_at,
                        last_seen_at=seen_at,
                    )
                    session.add(discovered)
                    await session.flush()
                    existing_by_url[normalized_link] = discovered
                else:
                    discovered.status = "queued"
                    discovered.last_task_id = task.id
                    discovered.last_seen_at = seen_at
                    discovered.last_error = None

                key_raw = f"{normalized_link}|{task.id}"
                item = CrawlerTaskItem(
                    task_id=task.id,
                    discovered_article_id=discovered.id,
                    item_idempotency_key=hashlib.sha256(key_raw.encode("utf-8")).hexdigest()[:64],
                    external_url=link,
                    normalized_url=normalized_link,
                    state="queued",
                )
                session.add(item)
                await session.flush()
                discovered.last_item_id = item.id
                created_count += 1
                if created_count >= target_item_limit:
                    break

            task.discovered_new_count = created_count
            task.skipped_known_count = skipped_known_count
            task.status = "running" if created_count > 0 else "done"
            task.current_stage = "queued" if created_count > 0 else "done"
            task.last_error_summary = None
            task.last_active_at = _now()
            await session.flush()
        except Exception as ex:
            task.status = "failed"
            task.current_stage = "failed"
            task.last_error_summary = str(ex)[:500]
            task.last_active_at = _now()
            logger.warning("batch task expansion failed, task_id=%s, reason=%s", task.id, ex)


async def _mark_discovered_article_fetched(session: AsyncSession, item: CrawlerTaskItem, extracted) -> None:
    if not item.discovered_article_id:
        return
    discovered = await session.scalar(
        select(CrawlerDiscoveredArticle).where(CrawlerDiscoveredArticle.id == item.discovered_article_id)
    )
    if discovered is None:
        return
    discovered.title = extracted.title or discovered.title
    discovered.published_at = extracted.published_at or discovered.published_at
    discovered.content_fingerprint = item.content_fingerprint
    discovered.status = "fetched"
    discovered.last_error = None
    discovered.last_item_id = item.id
    discovered.last_seen_at = _now()


async def _mark_discovered_article_failed(session: AsyncSession, item: CrawlerTaskItem, reason: str) -> None:
    if not item.discovered_article_id:
        return
    discovered = await session.scalar(
        select(CrawlerDiscoveredArticle).where(CrawlerDiscoveredArticle.id == item.discovered_article_id)
    )
    if discovered is None:
        return
    discovered.status = "failed"
    discovered.failure_count += 1
    discovered.last_error = reason[:500]
    discovered.last_item_id = item.id
    discovered.last_seen_at = _now()


async def _sync_task_status(session: AsyncSession) -> None:
    tasks = (await session.execute(select(CrawlerTask))).scalars().all()
    for task in tasks:
        rows = (
            await session.execute(select(CrawlerTaskItem.state).where(CrawlerTaskItem.task_id == task.id))
        ).scalars().all()
        if not rows:
            continue
        if any(state in {"queued", "crawling"} for state in rows):
            task.status = "running"
            continue
        if task.status == "paused":
            task.current_stage = "paused"
            continue
        if any(state in {"fetching", "extracting", "staging_assets", "pushing"} for state in rows):
            task.status = "running"
            continue
        if any(state in {"review_pending", "approved"} for state in rows):
            task.status = "review_pending"
            continue
        if all(state in {"pushed", "rejected", "failed", "policy_blocked"} for state in rows):
            task.status = "done"
