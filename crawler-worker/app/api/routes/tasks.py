from datetime import datetime, timezone
from hashlib import sha256
import json
import re
from pathlib import Path

from fastapi import APIRouter, Depends, HTTPException, Query
from fastapi.responses import FileResponse
import httpx
from sqlalchemy import delete, func, or_, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.db import get_session
from app.models import CrawlerAsset, CrawlerDiagnostic, CrawlerTask, CrawlerTaskItem
from app.schemas import (
    AssetManageDeleteRequest,
    AssetManagePageResponse,
    AssetManageResponse,
    AssetManageSummary,
    PushApprovedRequest,
    PushApprovedResponse,
    ReviewActionRequest,
    TaskControlRequest,
    TaskCreateRequest,
    TaskItemDeleteRequest,
    TaskItemDetailResponse,
    TaskItemPageResponse,
    TaskItemAssetResponse,
    TaskItemAssetRoleRequest,
    TaskItemResponse,
    TaskPurgeRequest,
    TaskItemUpdateRequest,
    TaskPageResponse,
    TaskResponse,
)
from app.services.assets import delete_unreferenced_files
from app.services.push_client import push_approved_items
from app.services.push_target import build_auth_headers, get_active_request_profile
from app.services.url_normalizer import normalize_article_url


router = APIRouter(prefix="/tasks", tags=["tasks"])


def _normalize_tags(raw_tags: list[str]) -> list[str]:
    clean_tags: list[str] = []
    seen: set[str] = set()
    for raw_tag in raw_tags:
        tag = raw_tag.strip()
        if not tag:
            continue
        lower = tag.lower()
        if lower in seen:
            continue
        seen.add(lower)
        clean_tags.append(tag[:30])
        if len(clean_tags) >= 5:
            break
    return clean_tags


def _normalize_suggested_tags(raw_tags: list[str]) -> list[str]:
    clean_tags: list[str] = []
    seen: set[str] = set()
    for raw_tag in raw_tags:
        tag = raw_tag.strip()
        if not tag:
            continue
        lower = tag.lower()
        if lower in seen:
            continue
        seen.add(lower)
        clean_tags.append(tag[:30])
        if len(clean_tags) >= 30:
            break
    return clean_tags


def _to_task_item_response(row: CrawlerTaskItem) -> TaskItemResponse:
    tags: list[str] = []
    suggested_tags: list[str] = []
    if row.tags_json:
        try:
            parsed = json.loads(row.tags_json)
            if isinstance(parsed, list):
                tags = _normalize_tags([str(item) for item in parsed])
        except json.JSONDecodeError:
            tags = []
    if row.extracted_tags_json:
        try:
            parsed_suggested = json.loads(row.extracted_tags_json)
            if isinstance(parsed_suggested, list):
                suggested_tags = _normalize_suggested_tags([str(item) for item in parsed_suggested])
        except json.JSONDecodeError:
            suggested_tags = []
    if not suggested_tags:
        suggested_tags = tags
    return TaskItemResponse(
        id=row.id,
        task_id=row.task_id,
        title=row.title,
        summary=row.summary,
        tags=tags,
        suggested_tags=suggested_tags,
        category_level1=row.category_level1,
        category_level2=row.category_level2,
        external_url=row.external_url,
        source_site=row.source_site,
        state=row.state,
        last_error_kind=row.last_error_kind,
        draft_push_status=row.draft_push_status,
        backend_candidate_id=row.backend_candidate_id,
        last_push_message=row.last_push_message,
        fail_reason=row.fail_reason,
        updated_at=row.updated_at,
        created_at=row.created_at,
    )


def _to_task_item_detail_response(row: CrawlerTaskItem) -> TaskItemDetailResponse:
    base = _to_task_item_response(row)
    return TaskItemDetailResponse(
        **base.model_dump(),
        normalized_url=row.normalized_url,
        content_markdown=row.content_markdown,
        retry_count=row.retry_count,
        push_retry_count=row.push_retry_count,
        cover_asset_id=row.cover_asset_id,
    )


def _resolve_asset_usage_status(item: CrawlerTaskItem | None, asset: CrawlerAsset) -> tuple[str, str]:
    if item is None:
        return "unused", "关联候选项不存在"

    if item.state == "approved":
        return "pending_submit", "已通过，后续推送仍会使用"
    if item.state == "pushed":
        return "submitted_cleanable", "已推送草稿，可按需清理本地缓存"
    if item.state in {"failed", "rejected"}:
        return "unused", "候选项已失败/拒绝"

    markdown = item.content_markdown or ""
    referenced_in_content = asset.source_url in markdown
    referenced_as_cover = item.cover_asset_id == asset.id
    if referenced_in_content or referenced_as_cover:
        return "active", "当前候选内容仍引用该资源"
    return "unused", "当前候选内容未引用该资源"


def _detach_asset_from_item(item: CrawlerTaskItem | None, asset: CrawlerAsset) -> None:
    if item and item.content_markdown:
        escaped = re.escape(asset.source_url)
        item.content_markdown = re.sub(
            rf"!?\[[^\]]*\]\(\s*<?{escaped}>?(?:\s+\"[^\"]*\")?\s*\)",
            "",
            item.content_markdown,
        )
        item.content_markdown = re.sub(rf"<img[^>]*src=['\"]{escaped}['\"][^>]*>", "", item.content_markdown)
        item.content_markdown = re.sub(r"\n{3,}", "\n\n", item.content_markdown).strip()

    if item and item.cover_asset_id == asset.id:
        item.cover_asset_id = None

def _normalize_fs_path(path: str | None) -> str | None:
    if not path:
        return None
    try:
        return str(Path(path).resolve(strict=False))
    except OSError:
        return None


async def _cleanup_orphan_cache_files(session: AsyncSession) -> int:
    cache_root = Path(settings.cache_dir)
    if not cache_root.exists():
        return 0

    db_paths = (await session.execute(select(CrawlerAsset.local_path))).scalars().all()
    referenced = {
        normalized
        for normalized in (_normalize_fs_path(path) for path in db_paths)
        if normalized
    }

    deleted_files = 0
    for file_path in cache_root.rglob("*"):
        if not file_path.is_file():
            continue
        normalized = _normalize_fs_path(str(file_path))
        if normalized and normalized in referenced:
            continue
        try:
            file_path.unlink(missing_ok=True)
            deleted_files += 1
        except OSError:
            continue
    return deleted_files


@router.post("", response_model=TaskResponse)
async def create_task(payload: TaskCreateRequest, session: AsyncSession = Depends(get_session)) -> TaskResponse:
    if payload.mode == "single" and not payload.source_url:
        raise HTTPException(status_code=400, detail="single mode requires source_url")
    if payload.mode == "batch" and (not payload.source_url or payload.site_profile_id is None):
        raise HTTPException(status_code=400, detail="batch mode requires source_url and site_profile_id")

    task = CrawlerTask(
        mode=payload.mode,
        source_url=payload.source_url,
        site_profile_id=payload.site_profile_id,
        max_items=payload.max_items,
        status="running",
    )
    session.add(task)
    await session.flush()

    if payload.mode == "single" and payload.source_url:
        normalized_url = normalize_article_url(payload.source_url)
        key_raw = f"{normalized_url}|{datetime.now(timezone.utc).isoformat()}"
        item = CrawlerTaskItem(
            task_id=task.id,
            item_idempotency_key=sha256(key_raw.encode("utf-8")).hexdigest()[:64],
            external_url=payload.source_url,
            normalized_url=normalized_url,
            state="queued",
        )
        session.add(item)

    await session.commit()
    return TaskResponse(
        id=task.id,
        mode=task.mode,
        status=task.status,
        current_stage=task.current_stage or task.status,
        last_error_summary=task.last_error_summary,
        max_items=task.max_items,
        discovered_new_count=task.discovered_new_count,
        skipped_known_count=task.skipped_known_count,
        scanned_link_count=task.scanned_link_count,
        created_at=task.created_at,
        last_active_at=task.last_active_at,
    )


@router.get("", response_model=TaskPageResponse)
async def list_tasks(
    session: AsyncSession = Depends(get_session),
    page_num: int = Query(default=1, ge=1),
    page_size: int = Query(default=10, ge=1, le=100),
    status: str | None = None,
) -> TaskPageResponse:
    count_stmt = select(func.count()).select_from(CrawlerTask)
    stmt = select(CrawlerTask).order_by(CrawlerTask.id.desc())
    if status:
        count_stmt = count_stmt.where(CrawlerTask.status == status)
        stmt = stmt.where(CrawlerTask.status == status)

    total = int((await session.execute(count_stmt)).scalar_one())
    offset = (page_num - 1) * page_size
    rows = (await session.execute(stmt.offset(offset).limit(page_size))).scalars().all()
    task_ids = [row.id for row in rows]
    if not task_ids:
        return TaskPageResponse(records=[], total=total, page_num=page_num, page_size=page_size)

    item_rows = (
        await session.execute(
            select(
                CrawlerTaskItem.task_id,
                CrawlerTaskItem.state,
                CrawlerTaskItem.fail_reason,
                CrawlerTaskItem.last_push_message,
                CrawlerTaskItem.updated_at,
            ).where(CrawlerTaskItem.task_id.in_(task_ids)).order_by(CrawlerTaskItem.updated_at.desc(), CrawlerTaskItem.id.desc())
        )
    ).all()

    stage_priority = [
        "pushing",
        "staging_assets",
        "extracting",
        "fetching",
        "queued",
        "review_pending",
        "approved",
        "policy_blocked",
        "failed",
        "pushed",
        "rejected",
    ]
    task_summary_map: dict[int, dict] = {}
    done_states = {"review_pending", "approved", "rejected", "pushed", "failed", "policy_blocked"}
    for task_id, state, fail_reason, last_push_message, updated_at in item_rows:
        summary = task_summary_map.setdefault(task_id, {"states": set(), "last_error_summary": None, "last_active_at": None, "total_items": 0, "completed_items": 0})
        summary["states"].add(state)
        summary["total_items"] += 1
        if state in done_states:
            summary["completed_items"] += 1
        if summary["last_error_summary"] is None:
            reason = (fail_reason or last_push_message or "").strip()
            if reason:
                summary["last_error_summary"] = reason
        if summary["last_active_at"] is None or updated_at > summary["last_active_at"]:
            summary["last_active_at"] = updated_at

    def resolve_current_stage(task_id: int, task_status: str) -> str:
        if task_status == "paused":
            return "paused"
        summary = task_summary_map.get(task_id)
        if not summary or not summary["states"]:
            return task_status
        states = summary["states"]
        for stage in stage_priority:
            if stage in states:
                return stage
        return task_status

    records = [
        TaskResponse(
            id=row.id,
            mode=row.mode,
            status=row.status,
            current_stage=resolve_current_stage(row.id, row.current_stage or row.status),
            last_error_summary=(task_summary_map.get(row.id) or {}).get("last_error_summary") or row.last_error_summary,
            max_items=row.max_items,
            discovered_new_count=row.discovered_new_count,
            skipped_known_count=row.skipped_known_count,
            scanned_link_count=row.scanned_link_count,
            total_items=(task_summary_map.get(row.id) or {}).get("total_items", 0),
            completed_items=(task_summary_map.get(row.id) or {}).get("completed_items", 0),
            created_at=row.created_at,
            last_active_at=(task_summary_map.get(row.id) or {}).get("last_active_at") or row.last_active_at,
        )
        for row in rows
    ]
    return TaskPageResponse(records=records, total=total, page_num=page_num, page_size=page_size)


@router.post("/review-action")
async def apply_review_action(payload: ReviewActionRequest, session: AsyncSession = Depends(get_session)) -> dict:
    rows = (
        await session.execute(select(CrawlerTaskItem).where(CrawlerTaskItem.id.in_(payload.item_ids)))
    ).scalars().all()
    if not rows:
        raise HTTPException(status_code=404, detail="items not found")

    state_map = {
        "approve": "approved",
        "reject": "rejected",
        "recrawl": "queued",
    }
    for row in rows:
        row.state = state_map[payload.action]
        if payload.action == "recrawl":
            row.retry_count = 0
            row.fail_reason = None
            row.last_error_kind = None
            row.last_push_message = None
        elif payload.action == "approve":
            row.fail_reason = None
            row.last_error_kind = None
    await session.commit()
    return {"updated": len(rows), "action": payload.action}


@router.post("/control")
async def control_tasks(payload: TaskControlRequest, session: AsyncSession = Depends(get_session)) -> dict:
    rows = (
        await session.execute(select(CrawlerTask).where(CrawlerTask.id.in_(payload.task_ids)))
    ).scalars().all()
    if not rows:
        raise HTTPException(status_code=404, detail="tasks not found")

    updated = 0
    for row in rows:
        if payload.action == "pause":
            if row.status in {"done", "failed", "completed"}:
                continue
            row.status = "paused"
            row.current_stage = "paused"
            updated += 1
            continue

        if row.status != "paused":
            continue

        row.status = "running"
        row.current_stage = "queued"
        updated += 1

    await session.commit()
    return {"updated": updated, "action": payload.action}


@router.post("/push-approved", response_model=PushApprovedResponse)
async def push_approved(payload: PushApprovedRequest, session: AsyncSession = Depends(get_session)) -> PushApprovedResponse:
    return await push_approved_items(session, payload.item_ids, payload.push_mode)


@router.delete("/backend/candidates/{candidate_id}")
async def delete_backend_candidate(candidate_id: int, delete_draft: bool = False) -> dict:
    profile = get_active_request_profile()
    async with httpx.AsyncClient(timeout=30) as client:
        resp = await client.request(
            "DELETE",
            f"{profile['base_url']}/api/admin/crawler/v1/candidates/{candidate_id}",
            headers=build_auth_headers(),
            json={"deleteDraft": delete_draft},
        )
        resp.raise_for_status()
    return {"deleted": True}


@router.post("/backend/cleanup")
async def cleanup_backend_residuals(candidate_retention_days: int = 30, push_record_retention_days: int = 90) -> dict:
    profile = get_active_request_profile()
    async with httpx.AsyncClient(timeout=30) as client:
        resp = await client.post(
            f"{profile['base_url']}/api/admin/crawler/v1/cleanup",
            headers=build_auth_headers(),
            json={
                "candidateRetentionDays": candidate_retention_days,
                "pushRecordRetentionDays": push_record_retention_days,
            },
        )
        resp.raise_for_status()
        return resp.json()["data"]


@router.get("/items", response_model=TaskItemPageResponse)
async def list_task_items(
    session: AsyncSession = Depends(get_session),
    task_id: int | None = None,
    state: str | None = None,
    keyword: str | None = None,
    page_num: int = Query(default=1, ge=1),
    page_size: int = Query(default=20, ge=1, le=200),
) -> TaskItemPageResponse:
    stmt = select(CrawlerTaskItem)
    count_stmt = select(func.count()).select_from(CrawlerTaskItem)
    if task_id is not None:
        stmt = stmt.where(CrawlerTaskItem.task_id == task_id)
        count_stmt = count_stmt.where(CrawlerTaskItem.task_id == task_id)
    if state:
        if state == "running":
            running_states = ["crawling", "fetching", "extracting", "staging_assets", "pushing"]
            stmt = stmt.where(CrawlerTaskItem.state.in_(running_states))
            count_stmt = count_stmt.where(CrawlerTaskItem.state.in_(running_states))
        else:
            stmt = stmt.where(CrawlerTaskItem.state == state)
            count_stmt = count_stmt.where(CrawlerTaskItem.state == state)
    if keyword and keyword.strip():
        like_keyword = f"%{keyword.strip()}%"
        filters = or_(
            CrawlerTaskItem.title.like(like_keyword),
            CrawlerTaskItem.fail_reason.like(like_keyword),
            CrawlerTaskItem.last_push_message.like(like_keyword),
            CrawlerTaskItem.external_url.like(like_keyword),
            CrawlerTaskItem.source_site.like(like_keyword),
        )
        stmt = stmt.where(filters)
        count_stmt = count_stmt.where(filters)
    total = int((await session.execute(count_stmt)).scalar_one())
    offset = (page_num - 1) * page_size
    stmt = stmt.order_by(CrawlerTaskItem.updated_at.desc(), CrawlerTaskItem.id.desc()).offset(offset).limit(page_size)
    rows = (await session.execute(stmt)).scalars().all()
    return TaskItemPageResponse(
        records=[_to_task_item_response(row) for row in rows],
        total=total,
        page_num=page_num,
        page_size=page_size,
    )


@router.delete("/items")
async def delete_task_items(payload: TaskItemDeleteRequest, session: AsyncSession = Depends(get_session)) -> dict:
    rows = (
        await session.execute(select(CrawlerTaskItem).where(CrawlerTaskItem.id.in_(payload.item_ids)))
    ).scalars().all()
    if not rows:
        raise HTTPException(status_code=404, detail="items not found")

    deleted = 0
    for row in rows:
        await session.delete(row)
        deleted += 1
    await session.commit()
    return {"deleted": deleted}


@router.get("/items/{item_id}", response_model=TaskItemDetailResponse)
async def get_task_item_detail(item_id: int, session: AsyncSession = Depends(get_session)) -> TaskItemDetailResponse:
    row = await session.scalar(select(CrawlerTaskItem).where(CrawlerTaskItem.id == item_id))
    if not row:
        raise HTTPException(status_code=404, detail="item not found")
    return _to_task_item_detail_response(row)


@router.get("/items/{item_id}/assets", response_model=list[TaskItemAssetResponse])
async def list_task_item_assets(item_id: int, session: AsyncSession = Depends(get_session)) -> list[TaskItemAssetResponse]:
    item = await session.scalar(select(CrawlerTaskItem).where(CrawlerTaskItem.id == item_id))
    if not item:
        raise HTTPException(status_code=404, detail="item not found")
    rows = (
        await session.execute(select(CrawlerAsset).where(CrawlerAsset.task_item_id == item_id).order_by(CrawlerAsset.id.asc()))
    ).scalars().all()
    return [
        TaskItemAssetResponse(
            id=row.id,
            source_url=row.source_url,
            preview_url=f"/tasks/assets/{row.id}/preview",
            mime_type=row.mime_type,
            file_size=row.file_size,
            asset_role=row.asset_role or "content",
        )
        for row in rows
    ]


@router.get("/assets", response_model=AssetManagePageResponse)
async def list_assets(
    session: AsyncSession = Depends(get_session),
    usage_status: str | None = None,
    limit: int = Query(default=500, ge=1, le=2000),
) -> AssetManagePageResponse:
    rows = (await session.execute(select(CrawlerAsset).order_by(CrawlerAsset.id.desc()).limit(limit))).scalars().all()
    if not rows:
        return AssetManagePageResponse(records=[], summary=AssetManageSummary())

    item_ids = list({row.task_item_id for row in rows})
    item_rows = (
        await session.execute(select(CrawlerTaskItem).where(CrawlerTaskItem.id.in_(item_ids)))
    ).scalars().all()
    item_map = {row.id: row for row in item_rows}

    data: list[AssetManageResponse] = []
    all_statuses: list[str] = []
    unique_size_map: dict[str, int] = {}
    for asset in rows:
        item = item_map.get(asset.task_item_id)
        resolved_status, resolved_reason = _resolve_asset_usage_status(item, asset)
        all_statuses.append(resolved_status)
        unique_size_map.setdefault(asset.local_path, asset.file_size)
        if usage_status and usage_status != resolved_status:
            continue
        data.append(
            AssetManageResponse(
                id=asset.id,
                task_item_id=asset.task_item_id,
                task_id=item.task_id if item else None,
                task_item_state=item.state if item else None,
                title=item.title if item else None,
                source_url=asset.source_url,
                preview_url=f"/tasks/assets/{asset.id}/preview",
                mime_type=asset.mime_type,
                file_size=asset.file_size,
                asset_role=asset.asset_role or "content",
                usage_status=resolved_status,
                usage_reason=resolved_reason,
                created_at=asset.created_at,
            )
        )

    total_file_size = sum(asset.file_size for asset in rows)
    unique_file_size = sum(unique_size_map.values())
    summary = AssetManageSummary(
        total_count=len(rows),
        total_file_size=total_file_size,
        unique_file_count=len(unique_size_map),
        unique_file_size=unique_file_size,
        shared_saved_bytes=max(0, total_file_size - unique_file_size),
        pending_submit_count=sum(1 for status in all_statuses if status == "pending_submit"),
        submitted_cleanable_count=sum(1 for status in all_statuses if status == "submitted_cleanable"),
        unused_count=sum(1 for status in all_statuses if status == "unused"),
        active_count=sum(1 for status in all_statuses if status == "active"),
    )
    return AssetManagePageResponse(records=data, summary=summary)


@router.delete("/assets")
async def delete_assets(payload: AssetManageDeleteRequest, session: AsyncSession = Depends(get_session)) -> dict:
    rows = (
        await session.execute(select(CrawlerAsset).where(CrawlerAsset.id.in_(payload.asset_ids)))
    ).scalars().all()
    if not rows:
        raise HTTPException(status_code=404, detail="assets not found")

    item_ids = list({row.task_item_id for row in rows})
    item_rows = (
        await session.execute(select(CrawlerTaskItem).where(CrawlerTaskItem.id.in_(item_ids)))
    ).scalars().all()
    item_map = {row.id: row for row in item_rows}

    deleted = 0
    deleted_paths: list[str] = []
    for asset in rows:
        item = item_map.get(asset.task_item_id)
        _detach_asset_from_item(item, asset)
        deleted_paths.append(asset.local_path)
        await session.delete(asset)
        deleted += 1

    await session.flush()
    await delete_unreferenced_files(session, deleted_paths)
    await session.commit()
    return {"deleted": deleted}


@router.get("/assets/{asset_id}/preview")
async def preview_asset(asset_id: int, session: AsyncSession = Depends(get_session)):
    asset = await session.scalar(select(CrawlerAsset).where(CrawlerAsset.id == asset_id))
    if not asset:
        raise HTTPException(status_code=404, detail="asset not found")
    path = Path(asset.local_path)
    if not path.exists():
        raise HTTPException(status_code=404, detail="asset file not found")
    return FileResponse(path=str(path), media_type=asset.mime_type or "application/octet-stream")


@router.put("/items/{item_id}/assets/{asset_id}/role")
async def update_asset_role(
    item_id: int,
    asset_id: int,
    payload: TaskItemAssetRoleRequest,
    session: AsyncSession = Depends(get_session),
) -> dict:
    item = await session.scalar(select(CrawlerTaskItem).where(CrawlerTaskItem.id == item_id))
    if not item:
        raise HTTPException(status_code=404, detail="item not found")
    asset = await session.scalar(
        select(CrawlerAsset).where(CrawlerAsset.id == asset_id, CrawlerAsset.task_item_id == item_id)
    )
    if not asset:
        raise HTTPException(status_code=404, detail="asset not found")

    if payload.role == "cover":
        rows = (
            await session.execute(select(CrawlerAsset).where(CrawlerAsset.task_item_id == item_id))
        ).scalars().all()
        for row in rows:
            row.asset_role = "content"
        asset.asset_role = "cover"
        item.cover_asset_id = asset.id
    else:
        asset.asset_role = "content"
        if item.cover_asset_id == asset.id:
            item.cover_asset_id = None
    await session.commit()
    return {"updated": True}


@router.delete("/items/{item_id}/assets/{asset_id}")
async def delete_task_item_asset(item_id: int, asset_id: int, session: AsyncSession = Depends(get_session)) -> dict:
    item = await session.scalar(select(CrawlerTaskItem).where(CrawlerTaskItem.id == item_id))
    if not item:
        raise HTTPException(status_code=404, detail="item not found")
    asset = await session.scalar(
        select(CrawlerAsset).where(CrawlerAsset.id == asset_id, CrawlerAsset.task_item_id == item_id)
    )
    if not asset:
        raise HTTPException(status_code=404, detail="asset not found")

    _detach_asset_from_item(item, asset)
    deleted_path = asset.local_path
    await session.delete(asset)
    await session.flush()
    await delete_unreferenced_files(session, [deleted_path])
    await session.commit()
    return {"deleted": True}


@router.put("/items/{item_id}", response_model=TaskItemDetailResponse)
async def update_task_item_detail(
    item_id: int,
    payload: TaskItemUpdateRequest,
    session: AsyncSession = Depends(get_session),
) -> TaskItemDetailResponse:
    row = await session.scalar(select(CrawlerTaskItem).where(CrawlerTaskItem.id == item_id))
    if not row:
        raise HTTPException(status_code=404, detail="item not found")
    if (
        payload.title is None
        and payload.summary is None
        and payload.content_markdown is None
        and payload.tags is None
        and payload.category_level1 is None
        and payload.category_level2 is None
    ):
        raise HTTPException(status_code=400, detail="no fields to update")

    if payload.title is not None:
        row.title = payload.title.strip()
    if payload.summary is not None:
        row.summary = payload.summary.strip()
    if payload.content_markdown is not None:
        row.content_markdown = payload.content_markdown.strip()
    if payload.tags is not None:
        clean_tags = _normalize_tags(payload.tags)
        row.tags_json = json.dumps(clean_tags, ensure_ascii=False)
    if payload.category_level1 is not None:
        row.category_level1 = payload.category_level1.strip() or None
    if payload.category_level2 is not None:
        row.category_level2 = payload.category_level2.strip() or None

    await session.commit()
    await session.refresh(row)
    return _to_task_item_detail_response(row)


@router.post("/purge")
async def purge_tasks(payload: TaskPurgeRequest, session: AsyncSession = Depends(get_session)) -> dict:
    task_ids: list[int] = []
    if payload.mode == "finished":
        finished = (
            await session.execute(
                select(CrawlerTask.id).where(CrawlerTask.status.in_(["done", "failed", "completed"]))
            )
        ).scalars().all()
        task_ids = [int(task_id) for task_id in finished]
        if not task_ids:
            return {"deleted_tasks": 0, "deleted_items": 0, "deleted_assets": 0, "deleted_diagnostics": 0}

    deleted_files_count = 0

    if task_ids:
        item_ids = (
            await session.execute(select(CrawlerTaskItem.id).where(CrawlerTaskItem.task_id.in_(task_ids)))
        ).scalars().all()
        deleted_assets_count = 0
        deleted_diagnostics_count = 0
        deleted_items_count = 0
        if item_ids:
            asset_rows = (
                await session.execute(select(CrawlerAsset.local_path).where(CrawlerAsset.task_item_id.in_(item_ids)))
            ).scalars().all()
            deleted_assets = await session.execute(delete(CrawlerAsset).where(CrawlerAsset.task_item_id.in_(item_ids)))
            deleted_diagnostics = await session.execute(delete(CrawlerDiagnostic).where(CrawlerDiagnostic.task_item_id.in_(item_ids)))
            deleted_items = await session.execute(delete(CrawlerTaskItem).where(CrawlerTaskItem.id.in_(item_ids)))
            deleted_files_count += await delete_unreferenced_files(session, [str(path) for path in asset_rows if path])
            deleted_assets_count = deleted_assets.rowcount or 0
            deleted_diagnostics_count = deleted_diagnostics.rowcount or 0
            deleted_items_count = deleted_items.rowcount or 0
        deleted_tasks = await session.execute(delete(CrawlerTask).where(CrawlerTask.id.in_(task_ids)))
        deleted_files_count += await _cleanup_orphan_cache_files(session)
        await session.commit()
        return {
            "deleted_tasks": deleted_tasks.rowcount or 0,
            "deleted_items": deleted_items_count,
            "deleted_assets": deleted_assets_count,
            "deleted_diagnostics": deleted_diagnostics_count,
            "deleted_files": deleted_files_count,
        }

    all_asset_paths = (await session.execute(select(CrawlerAsset.local_path))).scalars().all()
    deleted_assets = await session.execute(delete(CrawlerAsset))
    deleted_diagnostics = await session.execute(delete(CrawlerDiagnostic))
    deleted_items = await session.execute(delete(CrawlerTaskItem))
    deleted_tasks = await session.execute(delete(CrawlerTask))
    deleted_files_count += await delete_unreferenced_files(session, [str(path) for path in all_asset_paths if path])
    deleted_files_count += await _cleanup_orphan_cache_files(session)
    await session.commit()
    return {
        "deleted_tasks": deleted_tasks.rowcount or 0,
        "deleted_items": deleted_items.rowcount or 0,
        "deleted_assets": deleted_assets.rowcount or 0,
        "deleted_diagnostics": deleted_diagnostics.rowcount or 0,
        "deleted_files": deleted_files_count,
    }
