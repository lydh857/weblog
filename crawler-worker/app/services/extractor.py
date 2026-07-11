from __future__ import annotations

import re
from datetime import datetime
from dataclasses import dataclass
from urllib.parse import urljoin, urlparse

import httpx
import trafilatura
from bs4 import BeautifulSoup

from app.core.config import settings
from app.services.browser_pool import browser_pool
from app.services.url_normalizer import normalize_article_url
from app.services.url_policy import UrlPolicyError, ensure_url_allowed


@dataclass
class ExtractResult:
    title: str
    summary: str
    markdown: str
    source_site: str
    fallback_used: bool
    image_urls: list[str]
    tags: list[str]
    category_level1: str | None
    category_level2: str | None
    published_at: datetime | None
    author: str | None


async def extract_article(
    url: str,
    article_selector: str | None = None,
    timeout_seconds: int | None = None,
) -> ExtractResult:
    static_result = await _extract_static(url, article_selector, timeout_seconds)
    if len(static_result.markdown.strip()) >= settings.static_body_min_chars:
        return static_result
    dynamic_result = await _extract_dynamic(url, article_selector, timeout_seconds)
    if len(dynamic_result.markdown.strip()) >= len(static_result.markdown.strip()):
        return dynamic_result
    return static_result


async def _extract_static(
    url: str,
    article_selector: str | None,
    timeout_seconds: int | None,
) -> ExtractResult:
    timeout = timeout_seconds or settings.request_timeout_seconds
    async with httpx.AsyncClient(timeout=timeout) as client:
        resp = await client.get(url)
        resp.raise_for_status()
        html = resp.text

    if article_selector:
        scoped = _scope_html_by_selector(html, article_selector)
        if scoped:
            html = scoped

    downloaded = trafilatura.extract(html, output_format="markdown", include_links=True)
    markdown = downloaded or ""
    title = _extract_title(html)
    summary = _build_summary(markdown)
    images = _extract_images(html, url)
    tags = _extract_tags(html)
    category_level1, category_level2 = _extract_categories(html)
    published_at = _extract_published_at(html)
    author = _extract_author(html)
    return ExtractResult(
        title=title,
        summary=summary,
        markdown=markdown,
        source_site=urlparse(url).netloc,
        fallback_used=False,
        image_urls=images,
        tags=tags,
        category_level1=category_level1,
        category_level2=category_level2,
        published_at=published_at,
        author=author,
    )


async def _extract_dynamic(
    url: str,
    article_selector: str | None,
    timeout_seconds: int | None,
) -> ExtractResult:
    html = await browser_pool.render_html(url, timeout_seconds or settings.request_timeout_seconds)

    if article_selector:
        scoped = _scope_html_by_selector(html, article_selector)
        if scoped:
            html = scoped

    downloaded = trafilatura.extract(html, output_format="markdown", include_links=True)
    markdown = downloaded or ""
    title = _extract_title(html)
    summary = _build_summary(markdown)
    images = _extract_images(html, url)
    tags = _extract_tags(html)
    category_level1, category_level2 = _extract_categories(html)
    published_at = _extract_published_at(html)
    author = _extract_author(html)
    return ExtractResult(
        title=title,
        summary=summary,
        markdown=markdown,
        source_site=urlparse(url).netloc,
        fallback_used=True,
        image_urls=images,
        tags=tags,
        category_level1=category_level1,
        category_level2=category_level2,
        published_at=published_at,
        author=author,
    )


def _extract_title(html: str) -> str:
    soup = BeautifulSoup(html, "html.parser")
    if soup.title and soup.title.text.strip():
        return soup.title.text.strip()[:200]
    h1 = soup.find("h1")
    return (h1.text.strip() if h1 and h1.text else "untitled")[:200]


def _build_summary(markdown: str) -> str:
    text = re.sub(r"\s+", " ", markdown).strip()
    return text[:200]


def _extract_images(html: str, url: str) -> list[str]:
    soup = BeautifulSoup(html, "html.parser")
    image_urls: list[str] = []
    seen: set[str] = set()
    for img in soup.select("img"):
        candidates: list[str] = []
        for key in ("src", "data-src", "data-original", "data-lazy-src"):
            raw = (img.get(key) or "").strip()
            if raw:
                candidates.append(raw)
        srcset = (img.get("srcset") or "").strip()
        if srcset:
            for part in srcset.split(","):
                candidate = part.strip().split(" ")[0]
                if candidate:
                    candidates.append(candidate)

        for src in candidates:
            if src.startswith("data:"):
                continue
            absolute = urljoin(url, src)
            parsed = urlparse(absolute)
            if parsed.scheme not in {"http", "https"}:
                continue
            clean = absolute.split("#", 1)[0]
            if clean in seen:
                continue
            seen.add(clean)
            image_urls.append(clean)
    return image_urls[:30]


def _extract_tags(html: str) -> list[str]:
    soup = BeautifulSoup(html, "html.parser")
    tags: list[str] = []

    keywords_meta = soup.select_one('meta[name="keywords"],meta[property="article:tag"]')
    if keywords_meta:
        content = (keywords_meta.get("content") or "").strip()
        if content:
            tags.extend(re.split(r"[,，|/]", content))

    for node in soup.select("a[rel='tag'], .tags a, .tag a, a[href*='/tag/']"):
        text = node.get_text(strip=True)
        if text:
            tags.append(text)

    dedup: list[str] = []
    seen: set[str] = set()
    for tag in tags:
        clean = re.sub(r"\s+", " ", tag).strip()
        if not clean:
            continue
        lower = clean.lower()
        if lower in seen:
            continue
        seen.add(lower)
        dedup.append(clean[:30])
        if len(dedup) >= 20:
            break
    return dedup


def _extract_categories(html: str) -> tuple[str | None, str | None]:
    soup = BeautifulSoup(html, "html.parser")

    section_meta = soup.select_one('meta[property="article:section"],meta[name="category"]')
    if section_meta:
        value = (section_meta.get("content") or "").strip()
        if value:
            parts = [part.strip() for part in re.split(r"[>/|｜-]", value) if part.strip()]
            if len(parts) >= 2:
                return parts[0][:50], parts[1][:50]
            return value[:50], None

    crumbs: list[str] = []
    for node in soup.select(".breadcrumb a, .breadcrumbs a, [class*='crumb'] a"):
        text = node.get_text(strip=True)
        if text and text not in {"首页", "Home", "/"}:
            crumbs.append(text)
    if len(crumbs) >= 2:
        return crumbs[0][:50], crumbs[1][:50]
    if len(crumbs) == 1:
        return crumbs[0][:50], None
    return None, None


def _extract_published_at(html: str) -> datetime | None:
    soup = BeautifulSoup(html, "html.parser")
    node = soup.select_one('meta[property="article:published_time"],meta[name="pubdate"],time[datetime]')
    if not node:
        return None
    value = (node.get("content") or node.get("datetime") or "").strip()
    if not value:
        return None
    normalized = value.replace("Z", "+00:00")
    try:
        return _normalize_datetime(datetime.fromisoformat(normalized))
    except ValueError:
        return None


def _normalize_datetime(value: datetime | None) -> datetime | None:
    if value is None or value.tzinfo is None:
        return value
    return value.astimezone().replace(tzinfo=None)


def _extract_author(html: str) -> str | None:
    soup = BeautifulSoup(html, "html.parser")
    node = soup.select_one('meta[name="author"],meta[property="article:author"],.author,[rel="author"]')
    if not node:
        return None
    value = (node.get("content") or node.get_text(strip=True) or "").strip()
    return value[:80] if value else None


def _scope_html_by_selector(html: str, selector: str) -> str | None:
    soup = BeautifulSoup(html, "html.parser")
    node = soup.select_one(selector)
    if not node:
        return None
    return str(node)


async def extract_candidate_urls_from_list(
    list_url: str,
    list_selector: str | None,
    domain: str,
    max_items: int,
    scan_limit: int | None,
    timeout_seconds: int,
) -> list[str]:
    result = await extract_candidate_urls_from_list_with_diagnostics(
        list_url=list_url,
        list_selector=list_selector,
        domain=domain,
        max_items=max_items,
        scan_limit=scan_limit,
        timeout_seconds=timeout_seconds,
    )
    return result["links"]


async def extract_candidate_urls_from_list_with_diagnostics(
    list_url: str,
    list_selector: str | None,
    domain: str,
    max_items: int,
    scan_limit: int | None,
    timeout_seconds: int,
) -> dict:
    ensure_url_allowed(list_url)
    async with httpx.AsyncClient(timeout=timeout_seconds) as client:
        resp = await client.get(list_url)
        resp.raise_for_status()
        html = resp.text

    static_links, static_diag = _collect_candidate_links_from_html(
        html,
        list_url,
        list_selector,
        domain,
        max_items,
        scan_limit,
    )
    if static_links:
        return {
            "links": static_links,
            "diagnostics": {
                "used_dynamic_fallback": False,
                "static": static_diag,
                "dynamic": None,
            },
        }

    # 部分站点（如前端渲染列表页）静态 HTML 没有文章链接，回退到浏览器渲染后再提取一次。
    rendered_html = await _render_list_html(list_url, timeout_seconds)
    dynamic_links, dynamic_diag = _collect_candidate_links_from_html(
        rendered_html,
        list_url,
        list_selector,
        domain,
        max_items,
        scan_limit,
    )
    return {
        "links": dynamic_links,
        "diagnostics": {
            "used_dynamic_fallback": True,
            "static": static_diag,
            "dynamic": dynamic_diag,
        },
    }


async def _render_list_html(list_url: str, timeout_seconds: int) -> str:
    return await browser_pool.render_html(list_url, timeout_seconds)


def _collect_candidate_links_from_html(
    html: str,
    list_url: str,
    list_selector: str | None,
    domain: str,
    max_items: int,
    scan_limit: int | None,
) -> tuple[list[str], dict]:
    soup = BeautifulSoup(html, "html.parser")
    links: list[str] = []

    if list_selector:
        for node in soup.select(list_selector):
            href = (node.get("href") or "").strip()
            if href:
                links.append(urljoin(list_url, href))
            for sub_link in node.select("a[href]"):
                sub_href = (sub_link.get("href") or "").strip()
                if sub_href:
                    links.append(urljoin(list_url, sub_href))
    else:
        for link in soup.select("a[href]"):
            href = (link.get("href") or "").strip()
            if href:
                links.append(urljoin(list_url, href))

    normalized_domain = domain.lower().strip()
    effective_scan_limit = max_items if scan_limit is None else max(scan_limit, max_items)
    dedup: list[str] = []
    seen: set[str] = set()
    invalid_scheme_filtered = 0
    domain_filtered = 0
    policy_filtered = 0
    duplicate_filtered = 0
    for link in links:
        parsed = urlparse(link)
        if parsed.scheme not in {"http", "https"}:
            invalid_scheme_filtered += 1
            continue
        if normalized_domain and normalized_domain not in parsed.netloc.lower():
            domain_filtered += 1
            continue
        clean = normalize_article_url(link)
        try:
            ensure_url_allowed(clean)
        except UrlPolicyError:
            policy_filtered += 1
            continue
        if clean in seen:
            duplicate_filtered += 1
            continue
        seen.add(clean)
        dedup.append(clean)
        if len(dedup) >= effective_scan_limit:
            break

    diagnostics = {
        "raw_link_count": len(links),
        "accepted_count": len(dedup),
        "invalid_scheme_filtered": invalid_scheme_filtered,
        "domain_filtered": domain_filtered,
        "policy_filtered": policy_filtered,
        "duplicate_filtered": duplicate_filtered,
        "max_items": max_items,
        "scan_limit": effective_scan_limit,
    }
    return dedup, diagnostics
