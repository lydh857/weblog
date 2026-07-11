from __future__ import annotations

import json
import re
from pathlib import Path

import httpx
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.services.push_target import build_auth_headers, get_active_request_profile
from app.models import CrawlerAsset, CrawlerTaskItem
from app.schemas import PushApprovedItemResult, PushApprovedResponse

# 后端 OpenAPI 契约接口路由定义
API_CANDIDATES_INGEST = "/api/admin/crawler/v1/candidates:ingest"
API_DRAFTS_PUSH = "/api/admin/crawler/v1/drafts:push"
API_ASSETS_UPLOAD = "/api/admin/crawler/v1/assets:upload"


def _headers() -> dict[str, str]:
    return build_auth_headers()


async def push_approved_items(session: AsyncSession, item_ids: list[int], push_mode: str = "skip") -> PushApprovedResponse:
    rows = (
        await session.execute(select(CrawlerTaskItem).where(CrawlerTaskItem.id.in_(item_ids)))
    ).scalars().all()

    row_map = {r.id: r for r in rows}
    results: list[PushApprovedItemResult] = []
    for item_id in item_ids:
        row = row_map.get(item_id)
        if not row:
            results.append(PushApprovedItemResult(item_id=item_id, status="failed", message="item not found"))
            continue
        if row.state != "approved":
            results.append(PushApprovedItemResult(item_id=item_id, status="failed", message="item state not pushable"))
            continue
        row.push_retry_count += 1
        row.state = "pushing"
        row.last_error_kind = None

    ingest_items = []
    for row in rows:
        rewritten_markdown, uploaded_assets = await _rewrite_markdown_with_uploaded_assets(session, row)
        tags = _parse_tags(row.tags_json)
        content_image_refs = [item["uploaded_url"] for item in uploaded_assets if item["asset_role"] == "content"]
        cover_candidates = [
            item["uploaded_url"]
            for item in uploaded_assets
            if item["asset_role"] == "cover" and _is_cover_supported(item.get("mime_type"))
        ]
        if not cover_candidates:
            cover_candidates = [
                item["uploaded_url"] for item in uploaded_assets if item["asset_role"] == "content" and _is_cover_supported(item.get("mime_type"))
            ]
        cover_image = cover_candidates[0] if cover_candidates else (content_image_refs[0] if content_image_refs else None)
        ingest_items.append(
            {
                "itemIdempotencyKey": row.item_idempotency_key,
                "externalUrl": row.external_url,
                "normalizedUrl": row.normalized_url,
                "sourceSite": row.source_site or "",
                "title": row.title or "untitled",
                "summary": row.summary or "",
                "contentMarkdown": _sanitize_markdown(rewritten_markdown),
                "coverImage": cover_image,
                "imageRefs": content_image_refs,
                "contentFingerprint": row.content_fingerprint or "",
                "author": "",
                "metadata": {
                    "workerTaskItemId": row.id,
                    "tags": tags,
                    "categoryLevel1": row.category_level1,
                    "categoryLevel2": row.category_level2,
                },
            }
        )

    async with httpx.AsyncClient(timeout=30) as client:
        target_profile = get_active_request_profile()
        ingest_resp = await client.post(
            f"{target_profile['base_url']}{API_CANDIDATES_INGEST}",
            headers=_headers(),
            json={
                "idempotencyKey": f"ingest-batch-{item_ids[0]}-{item_ids[-1]}",
                "workerRunId": f"{target_profile['name']}-push",
                "items": ingest_items,
            },
        )
        ingest_resp.raise_for_status()
        ingest_data = ingest_resp.json()["data"]

        candidate_ids = [a["candidateId"] for a in ingest_data.get("accepted", [])]
        accepted_key_to_candidate = {a["itemIdempotencyKey"]: a["candidateId"] for a in ingest_data.get("accepted", [])}
        rejected_key_to_reason = {
            str(item.get("itemIdempotencyKey", "")): _format_ingest_rejected_reason(item)
            for item in ingest_data.get("rejected", [])
            if item.get("itemIdempotencyKey")
        }
        if candidate_ids:
            push_resp = await client.post(
                f"{target_profile['base_url']}{API_DRAFTS_PUSH}",
                headers=_headers(),
                json={"candidateIds": candidate_ids, "pushMode": push_mode},
            )
            push_resp.raise_for_status()
            push_data = push_resp.json()["data"]
            draft_map = {r["candidateId"]: r for r in push_data.get("results", [])}
        else:
            draft_map = {}

    for row in rows:
        candidate_id = accepted_key_to_candidate.get(row.item_idempotency_key)
        if not candidate_id:
            duplicate_candidate_id = None
            duplicate_target_draft_id = None
            duplicate_title = None
            for rejected_item in ingest_data.get("rejected", []):
                if str(rejected_item.get("itemIdempotencyKey", "")) == row.item_idempotency_key:
                    duplicate_candidate_id = rejected_item.get("duplicateCandidateId")
                    duplicate_target_draft_id = rejected_item.get("duplicateTargetDraftId")
                    duplicate_title = rejected_item.get("duplicateTitle")
                    break

            if duplicate_candidate_id and push_mode != "skip":
                async with httpx.AsyncClient(timeout=30) as client:
                    target_profile = get_active_request_profile()
                    push_resp = await client.post(
                        f"{target_profile['base_url']}{API_DRAFTS_PUSH}",
                        headers=_headers(),
                        json={"candidateIds": [duplicate_candidate_id], "pushMode": push_mode},
                    )
                    push_resp.raise_for_status()
                    duplicate_push = (push_resp.json()["data"].get("results") or [None])[0] or {}
                if duplicate_push.get("status") == "succeeded":
                    row.backend_candidate_id = duplicate_candidate_id
                    row.draft_push_status = "succeeded"
                    row.state = "pushed"
                    row.last_error_kind = None
                    row.last_push_message = duplicate_push.get("message") or "pushed"
                    results.append(PushApprovedItemResult(
                        item_id=row.id,
                        status="succeeded",
                        message=row.last_push_message,
                        backend_candidate_id=duplicate_candidate_id,
                        draft_id=duplicate_push.get("draftId"),
                        pushed_at=duplicate_push.get("pushedAt"),
                        duplicate_candidate_id=duplicate_candidate_id,
                        duplicate_target_draft_id=duplicate_target_draft_id,
                        duplicate_title=duplicate_title,
                    ))
                    continue

            row.draft_push_status = "failed"
            row.last_push_message = rejected_key_to_reason.get(row.item_idempotency_key, "候选内容入库被拒绝")
            row.state = "failed"
            row.last_error_kind = "ingest_rejected"
            results.append(PushApprovedItemResult(
                item_id=row.id,
                status="failed",
                message=row.last_push_message,
                duplicate_candidate_id=duplicate_candidate_id,
                duplicate_target_draft_id=duplicate_target_draft_id,
                duplicate_title=duplicate_title,
            ))
            continue

        row.backend_candidate_id = candidate_id
        push_result = draft_map.get(candidate_id)
        if push_result and push_result.get("status") == "succeeded":
            row.draft_push_status = "succeeded"
            row.state = "pushed"
            row.last_error_kind = None
            row.last_push_message = push_result.get("message") or "pushed"
            results.append(
                PushApprovedItemResult(
                    item_id=row.id,
                    status="succeeded",
                    message=row.last_push_message,
                    backend_candidate_id=candidate_id,
                    draft_id=push_result.get("draftId"),
                    pushed_at=push_result.get("pushedAt"),
                )
            )
        else:
            row.draft_push_status = "failed"
            row.state = "failed"
            row.last_error_kind = "push_failed"
            row.last_push_message = (push_result or {}).get("message", "push failed")
            results.append(
                PushApprovedItemResult(
                    item_id=row.id,
                    status="failed",
                    message=row.last_push_message,
                    backend_candidate_id=candidate_id,
                    pushed_at=(push_result or {}).get("pushedAt"),
                )
            )

    await session.commit()
    succeeded = len([r for r in results if r.status == "succeeded"])
    return PushApprovedResponse(total=len(item_ids), succeeded=succeeded, failed=len(item_ids) - succeeded, results=results)


async def _rewrite_markdown_with_uploaded_assets(session: AsyncSession, row: CrawlerTaskItem) -> tuple[str, list[dict[str, str | None]]]:
    markdown = row.content_markdown or ""
    asset_rows = (
        await session.execute(select(CrawlerAsset).where(CrawlerAsset.task_item_id == row.id).order_by(CrawlerAsset.id.asc()))
    ).scalars().all()
    if not asset_rows:
        return markdown, []

    replace_map: dict[str, str] = {}
    uploaded_assets: list[dict[str, str | None]] = []
    async with httpx.AsyncClient(timeout=30) as client:
        target_profile = get_active_request_profile()
        for asset in asset_rows:
            file_path = Path(asset.local_path)
            if not file_path.exists():
                continue
            with file_path.open("rb") as fh:
                files = {"file": (file_path.name, fh, asset.mime_type or "application/octet-stream")}
                resp = await client.post(
                    f"{target_profile['base_url']}{API_ASSETS_UPLOAD}",
                    headers=_headers(),
                    files=files,
                )
                resp.raise_for_status()
                uploaded_url = resp.json()["data"]["url"]
                replace_map[asset.source_url] = uploaded_url
                uploaded_assets.append(
                    {
                        "source_url": asset.source_url,
                        "uploaded_url": uploaded_url,
                        "asset_role": asset.asset_role or "content",
                        "mime_type": asset.mime_type,
                    }
                )

    rewritten = markdown
    for source_url, uploaded_url in replace_map.items():
        rewritten = rewritten.replace(source_url, uploaded_url)

    dedup: list[dict[str, str | None]] = []
    seen_url: set[str] = set()
    for item in uploaded_assets:
        uploaded_url = str(item.get("uploaded_url") or "")
        if not uploaded_url or uploaded_url in seen_url:
            continue
        seen_url.add(uploaded_url)
        dedup.append(item)
    return rewritten, dedup


def _sanitize_markdown(markdown: str) -> str:
    text = markdown.replace("\r\n", "\n").replace("\r", "\n")
    text = re.sub(r"\[Pasted ~\d+ lines\]\s*", "", text)
    text = re.sub(r"\n{5,}", "\n\n\n\n", text)
    return text.strip()


def _parse_tags(raw: str | None) -> list[str]:
    if not raw:
        return []
    try:
        value = json.loads(raw)
    except json.JSONDecodeError:
        return []
    if not isinstance(value, list):
        return []
    clean: list[str] = []
    seen: set[str] = set()
    for item in value:
        tag = str(item).strip()
        if not tag:
            continue
        lower = tag.lower()
        if lower in seen:
            continue
        seen.add(lower)
        clean.append(tag[:30])
        if len(clean) >= 5:
            break
    return clean


def _format_ingest_rejected_reason(item: dict) -> str:
    reason_code = str(item.get("reasonCode") or "unknown").strip()
    reason_message = str(item.get("reasonMessage") or "候选内容入库被拒绝").strip()
    duplicate_candidate_id = item.get("duplicateCandidateId")
    duplicate_title = str(item.get("duplicateTitle") or "").strip()
    duplicate_url = str(item.get("duplicateExternalUrl") or item.get("duplicateNormalizedUrl") or "").strip()

    reason_label_map = {
        "idempotency_conflict": "同一幂等键对应的链接不一致",
        "duplicate_content": "内容指纹已存在，系统判定为重复内容",
        "unknown": "后端拒绝接收入库",
    }

    reason_label = reason_label_map.get(reason_code)
    duplicate_hint_parts: list[str] = []
    if duplicate_candidate_id:
        duplicate_hint_parts.append(f"候选ID {duplicate_candidate_id}")
    if duplicate_title:
        duplicate_hint_parts.append(f"标题《{duplicate_title}》")
    if duplicate_url:
        duplicate_hint_parts.append(duplicate_url)
    duplicate_hint = f"；已命中重复内容：{' / '.join(duplicate_hint_parts)}" if duplicate_hint_parts else ""

    if reason_label:
        if reason_message and reason_message != "候选内容入库被拒绝":
            return f"候选内容入库被拒绝：{reason_label}（{reason_message}）{duplicate_hint}"
        return f"候选内容入库被拒绝：{reason_label}{duplicate_hint}"

    if reason_message:
        return f"候选内容入库被拒绝：{reason_code}（{reason_message}）{duplicate_hint}"
    return f"候选内容入库被拒绝：{reason_code}{duplicate_hint}"


def _is_cover_supported(mime_type: str | None) -> bool:
    if not mime_type:
        return True
    clean = mime_type.lower().split(";", 1)[0].strip()
    return clean in {"image/jpeg", "image/jpg", "image/png", "image/webp"}
