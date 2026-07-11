from datetime import datetime

import pytest

from app.api.routes import site_profiles
from app.services.extractor import ExtractResult


@pytest.mark.asyncio
async def test_article_extract_endpoint_returns_preview(monkeypatch):
    async def fake_extract_article(url: str, article_selector: str | None = None, timeout_seconds: int | None = None):
        assert url == "https://example.com/post/1"
        assert article_selector == "article"
        assert timeout_seconds == 15
        return ExtractResult(
            title="测试标题",
            summary="测试摘要",
            markdown="# 标题\n正文内容",
            source_site="example.com",
            fallback_used=False,
            image_urls=["https://example.com/a.png"],
            tags=["Python", "FastAPI"],
            category_level1="技术",
            category_level2="后端",
            published_at=datetime.now(),
            author="作者A",
        )

    monkeypatch.setattr(site_profiles, "extract_article", fake_extract_article)

    data = await site_profiles.test_article_extract(
        site_profiles.SiteProfileArticleTestRequest(
            article_url="https://example.com/post/1",
            article_selector="article",
            timeout_seconds=15,
        )
    )

    assert data.title == "测试标题"
    assert data.markdown_length > 0
    assert data.image_count == 1
    assert data.tags == ["Python", "FastAPI"]
