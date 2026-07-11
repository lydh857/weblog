from urllib.parse import urlparse

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db import get_session
from app.models import SiteProfile
from app.schemas import SiteProfileArticleTestRequest, SiteProfileArticleTestResponse, SiteProfileExtractTestRequest, SiteProfileUpsertRequest
from app.services.extractor import extract_article, extract_candidate_urls_from_list_with_diagnostics
from app.services.url_policy import UrlPolicyError


router = APIRouter(prefix="/site-profiles", tags=["site-profiles"])


@router.get("")
async def list_site_profiles(session: AsyncSession = Depends(get_session)) -> list[dict]:
    rows = (await session.execute(select(SiteProfile).order_by(SiteProfile.id.desc()))).scalars().all()
    return [
        {
            "id": r.id,
            "name": r.name,
            "domain": r.domain,
            "defaultListUrl": r.default_list_url,
            "listSelector": r.list_selector,
            "articleSelector": r.article_selector,
            "maxItems": r.max_items,
            "intervalSeconds": r.interval_seconds,
            "timeoutSeconds": r.timeout_seconds,
            "enabled": bool(r.enabled),
        }
        for r in rows
    ]


@router.post("")
async def create_site_profile(payload: SiteProfileUpsertRequest, session: AsyncSession = Depends(get_session)) -> dict:
    row = SiteProfile(
        name=payload.name,
        domain=payload.domain,
        default_list_url=payload.default_list_url,
        list_selector=payload.list_selector,
        article_selector=payload.article_selector,
        max_items=payload.max_items,
        interval_seconds=payload.interval_seconds,
        timeout_seconds=payload.timeout_seconds,
        enabled=1 if payload.enabled else 0,
    )
    session.add(row)
    await session.commit()
    return {"id": row.id}


@router.put("/{profile_id}")
async def update_site_profile(profile_id: int, payload: SiteProfileUpsertRequest, session: AsyncSession = Depends(get_session)) -> dict:
    row = await session.scalar(select(SiteProfile).where(SiteProfile.id == profile_id))
    if not row:
        raise HTTPException(status_code=404, detail="site profile not found")
    row.name = payload.name
    row.domain = payload.domain
    row.default_list_url = payload.default_list_url
    row.list_selector = payload.list_selector
    row.article_selector = payload.article_selector
    row.max_items = payload.max_items
    row.interval_seconds = payload.interval_seconds
    row.timeout_seconds = payload.timeout_seconds
    row.enabled = 1 if payload.enabled else 0
    await session.commit()
    return {"updated": True}


@router.post("/test-extract")
async def test_extract(payload: SiteProfileExtractTestRequest) -> dict:
    parsed_domain = payload.domain.strip().lower()
    if not parsed_domain:
        parsed_domain = (urlparse(payload.list_url).hostname or "").strip().lower()
    if not parsed_domain:
        raise HTTPException(status_code=400, detail="domain is required")

    try:
        result = await extract_candidate_urls_from_list_with_diagnostics(
            list_url=payload.list_url,
            list_selector=payload.list_selector,
            domain=parsed_domain,
            max_items=payload.max_items,
            scan_limit=payload.max_items,
            timeout_seconds=payload.timeout_seconds,
        )
    except UrlPolicyError as ex:
        raise HTTPException(status_code=400, detail=str(ex)) from ex
    except Exception as ex:
        raise HTTPException(status_code=400, detail=f"extract failed: {ex}") from ex

    return {
        "domain": parsed_domain,
        "count": len(result["links"]),
        "links": result["links"],
        "diagnostics": result["diagnostics"],
    }


@router.post("/test-article-extract", response_model=SiteProfileArticleTestResponse)
async def test_article_extract(payload: SiteProfileArticleTestRequest) -> SiteProfileArticleTestResponse:
    try:
        result = await extract_article(
            url=payload.article_url,
            article_selector=payload.article_selector,
            timeout_seconds=payload.timeout_seconds,
        )
    except UrlPolicyError as ex:
        raise HTTPException(status_code=400, detail=str(ex)) from ex
    except Exception as ex:
        raise HTTPException(status_code=400, detail=f"extract failed: {ex}") from ex

    return SiteProfileArticleTestResponse(
        title=result.title,
        summary=result.summary,
        markdown=result.markdown,
        markdown_length=len((result.markdown or "").strip()),
        image_count=len(result.image_urls),
        tags=result.tags,
        category_level1=result.category_level1,
        category_level2=result.category_level2,
        source_site=result.source_site,
        fallback_used=result.fallback_used,
        author=result.author,
    )
