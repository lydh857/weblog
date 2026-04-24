from __future__ import annotations

from datetime import datetime

from sqlalchemy import DateTime, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column

def utc_now() -> datetime:
    return datetime.now()


class Base(DeclarativeBase):
    pass


class CrawlerTask(Base):
    __tablename__ = "crawler_task"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    mode: Mapped[str] = mapped_column(String(32), nullable=False)
    source_url: Mapped[str | None] = mapped_column(String(1000), nullable=True)
    site_profile_id: Mapped[int | None] = mapped_column(Integer, nullable=True)
    status: Mapped[str] = mapped_column(String(32), default="queued", nullable=False)
    max_items: Mapped[int] = mapped_column(Integer, default=1, nullable=False)
    retry_count: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    current_stage: Mapped[str | None] = mapped_column(String(64), nullable=True)
    last_error_summary: Mapped[str | None] = mapped_column(String(500), nullable=True)
    discovered_new_count: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    skipped_known_count: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    scanned_link_count: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utc_now, nullable=False)
    last_active_at: Mapped[datetime] = mapped_column(DateTime, default=utc_now, nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=utc_now, onupdate=utc_now, nullable=False)


class CrawlerTaskItem(Base):
    __tablename__ = "crawler_task_item"
    __table_args__ = (UniqueConstraint("item_idempotency_key", name="uk_item_idempotency_key"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    task_id: Mapped[int] = mapped_column(Integer, nullable=False, index=True)
    discovered_article_id: Mapped[int | None] = mapped_column(Integer, nullable=True, index=True)
    item_idempotency_key: Mapped[str] = mapped_column(String(128), nullable=False)
    external_url: Mapped[str] = mapped_column(String(1000), nullable=False)
    normalized_url: Mapped[str] = mapped_column(String(1000), nullable=False, index=True)
    title: Mapped[str | None] = mapped_column(String(255), nullable=True)
    content_markdown: Mapped[str | None] = mapped_column(Text, nullable=True)
    summary: Mapped[str | None] = mapped_column(String(500), nullable=True)
    tags_json: Mapped[str | None] = mapped_column(Text, nullable=True)
    extracted_tags_json: Mapped[str | None] = mapped_column(Text, nullable=True)
    category_level1: Mapped[str | None] = mapped_column(String(120), nullable=True)
    category_level2: Mapped[str | None] = mapped_column(String(120), nullable=True)
    cover_asset_id: Mapped[int | None] = mapped_column(Integer, nullable=True)
    source_site: Mapped[str | None] = mapped_column(String(255), nullable=True)
    content_fingerprint: Mapped[str | None] = mapped_column(String(128), nullable=True, index=True)
    state: Mapped[str] = mapped_column(String(32), default="queued", nullable=False, index=True)
    last_error_kind: Mapped[str | None] = mapped_column(String(64), nullable=True)
    fail_reason: Mapped[str | None] = mapped_column(String(500), nullable=True)
    retry_count: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    draft_push_status: Mapped[str | None] = mapped_column(String(32), nullable=True)
    backend_candidate_id: Mapped[int | None] = mapped_column(Integer, nullable=True, index=True)
    push_retry_count: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    last_push_message: Mapped[str | None] = mapped_column(String(500), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utc_now, nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=utc_now, onupdate=utc_now, nullable=False)


class CrawlerDiscoveredArticle(Base):
    __tablename__ = "crawler_discovered_article"
    __table_args__ = (UniqueConstraint("site_profile_id", "normalized_url", name="uk_site_profile_normalized_url"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    site_profile_id: Mapped[int] = mapped_column(Integer, nullable=False, index=True)
    normalized_url: Mapped[str] = mapped_column(String(1000), nullable=False)
    title: Mapped[str | None] = mapped_column(String(255), nullable=True)
    published_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    content_fingerprint: Mapped[str | None] = mapped_column(String(128), nullable=True, index=True)
    status: Mapped[str] = mapped_column(String(32), default="queued", nullable=False, index=True)
    failure_count: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    last_error: Mapped[str | None] = mapped_column(String(500), nullable=True)
    last_task_id: Mapped[int | None] = mapped_column(Integer, nullable=True, index=True)
    last_item_id: Mapped[int | None] = mapped_column(Integer, nullable=True, index=True)
    first_seen_at: Mapped[datetime] = mapped_column(DateTime, default=utc_now, nullable=False)
    last_seen_at: Mapped[datetime] = mapped_column(DateTime, default=utc_now, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utc_now, nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=utc_now, onupdate=utc_now, nullable=False)


class CrawlerAsset(Base):
    __tablename__ = "crawler_asset"
    __table_args__ = (UniqueConstraint("sha256", name="uk_asset_sha256"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    task_item_id: Mapped[int] = mapped_column(Integer, nullable=False, index=True)
    source_url: Mapped[str] = mapped_column(String(1000), nullable=False)
    local_path: Mapped[str] = mapped_column(String(1000), nullable=False)
    asset_role: Mapped[str] = mapped_column(String(32), default="content", nullable=False)
    mime_type: Mapped[str | None] = mapped_column(String(100), nullable=True)
    file_size: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    sha256: Mapped[str] = mapped_column(String(128), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utc_now, nullable=False)


class CrawlerDiagnostic(Base):
    __tablename__ = "crawler_diagnostic"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    task_item_id: Mapped[int] = mapped_column(Integer, nullable=False, index=True)
    kind: Mapped[str] = mapped_column(String(64), nullable=False)
    message: Mapped[str] = mapped_column(String(500), nullable=False)
    detail: Mapped[str | None] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utc_now, nullable=False)


class SiteProfile(Base):
    __tablename__ = "site_profile"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    name: Mapped[str] = mapped_column(String(100), nullable=False)
    domain: Mapped[str] = mapped_column(String(255), nullable=False, unique=True)
    default_list_url: Mapped[str | None] = mapped_column(String(1000), nullable=True)
    list_selector: Mapped[str | None] = mapped_column(String(300), nullable=True)
    article_selector: Mapped[str | None] = mapped_column(String(300), nullable=True)
    max_items: Mapped[int] = mapped_column(Integer, default=20, nullable=False)
    interval_seconds: Mapped[int] = mapped_column(Integer, default=2, nullable=False)
    timeout_seconds: Mapped[int] = mapped_column(Integer, default=20, nullable=False)
    enabled: Mapped[int] = mapped_column(Integer, default=1, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utc_now, nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=utc_now, onupdate=utc_now, nullable=False)
