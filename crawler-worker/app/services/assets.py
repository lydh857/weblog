from __future__ import annotations

import hashlib
from pathlib import Path

import httpx
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.models import CrawlerAsset


ALLOWED_MIME_PREFIXES = ("image/",)
MAX_IMAGE_BYTES = 8 * 1024 * 1024


class AssetValidationError(ValueError):
    pass


def _resolve_file_suffix(content_type: str) -> str:
    if "/" not in content_type:
        return ".bin"
    return f".{content_type.split('/', maxsplit=1)[1].split(';', maxsplit=1)[0]}"


async def delete_unreferenced_files(session: AsyncSession, paths: list[str]) -> int:
    deleted_files = 0
    for raw_path in {path for path in paths if path}:
        ref_count = int((await session.execute(
            select(func.count()).select_from(CrawlerAsset).where(CrawlerAsset.local_path == raw_path)
        )).scalar_one())
        if ref_count > 0:
            continue
        try:
            resolved = Path(raw_path)
            if resolved.exists() and resolved.is_file():
                resolved.unlink(missing_ok=True)
                deleted_files += 1
        except OSError:
            continue
    return deleted_files


async def stage_image(
    session: AsyncSession,
    task_item_id: int,
    source_url: str,
    referer_url: str | None = None,
) -> CrawlerAsset:
    cache_dir = Path(settings.cache_dir)
    cache_dir.mkdir(parents=True, exist_ok=True)

    headers = {"User-Agent": "Mozilla/5.0 weblog-crawler-worker"}
    if referer_url:
        headers["Referer"] = referer_url
    async with httpx.AsyncClient(timeout=settings.request_timeout_seconds, headers=headers, follow_redirects=True) as client:
        resp = await client.get(source_url)
        resp.raise_for_status()
        content_type = resp.headers.get("content-type", "")
        if not content_type.startswith(ALLOWED_MIME_PREFIXES):
            raise AssetValidationError(f"invalid mime type: {content_type}")
        content = resp.content

    if len(content) > MAX_IMAGE_BYTES:
        raise AssetValidationError("image too large")

    content_digest = hashlib.sha256(content).hexdigest()
    record_digest = hashlib.sha256(f"{content_digest}|{task_item_id}|{source_url}".encode("utf-8")).hexdigest()

    file_path = cache_dir / f"{content_digest}{_resolve_file_suffix(content_type)}"
    if not file_path.exists():
        file_path.write_bytes(content)

    asset = CrawlerAsset(
        task_item_id=task_item_id,
        source_url=source_url,
        local_path=str(file_path),
        mime_type=content_type,
        file_size=len(content),
        sha256=record_digest,
    )
    session.add(asset)
    await session.flush()
    return asset


async def enforce_cache_quota(session: AsyncSession) -> None:
    rows = (await session.execute(select(CrawlerAsset).order_by(CrawlerAsset.created_at.asc(), CrawlerAsset.id.asc()))).scalars().all()
    unique_size_map: dict[str, int] = {}
    for row in rows:
        unique_size_map.setdefault(row.local_path, row.file_size)
    total = sum(unique_size_map.values())
    if total <= settings.cache_max_bytes:
        return

    for row in rows:
        if total <= settings.cache_max_bytes:
            break
        path = row.local_path
        await session.delete(row)
        await session.flush()
        remaining_refs = int((await session.execute(
            select(func.count()).select_from(CrawlerAsset).where(CrawlerAsset.local_path == path)
        )).scalar_one())
        if remaining_refs == 0:
            total -= unique_size_map.get(path, row.file_size)
            await delete_unreferenced_files(session, [path])
    await session.flush()
