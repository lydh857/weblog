from datetime import datetime

from pydantic import BaseModel, Field


class HealthResponse(BaseModel):
    status: str
    service: str


class PushTargetProfile(BaseModel):
    base_url: str = ""
    crawler_token: str = ""
    device_id: str = ""
    request_origin: str = ""


class PushTargetConfigResponse(BaseModel):
    active_target: str
    profiles: dict[str, PushTargetProfile]


class PushTargetConfigRequest(BaseModel):
    active_target: str = Field(pattern="^(local|server)$")
    profiles: dict[str, PushTargetProfile]


class TaskCreateRequest(BaseModel):
    mode: str = Field(pattern="^(single|batch)$")
    source_url: str | None = None
    site_profile_id: int | None = None
    max_items: int = Field(default=1, ge=1, le=200)


class TaskResponse(BaseModel):
    id: int
    mode: str
    status: str
    current_stage: str | None = None
    last_error_summary: str | None = None
    max_items: int
    discovered_new_count: int = 0
    skipped_known_count: int = 0
    scanned_link_count: int = 0
    total_items: int = 0
    completed_items: int = 0
    created_at: datetime
    last_active_at: datetime | None = None


class TaskPageResponse(BaseModel):
    records: list[TaskResponse]
    total: int
    page_num: int
    page_size: int


class TaskItemResponse(BaseModel):
    id: int
    task_id: int
    title: str | None
    summary: str | None
    tags: list[str] = Field(default_factory=list)
    suggested_tags: list[str] = Field(default_factory=list)
    category_level1: str | None = None
    category_level2: str | None = None
    external_url: str
    source_site: str | None
    state: str
    last_error_kind: str | None = None
    draft_push_status: str | None
    backend_candidate_id: int | None
    last_push_message: str | None
    fail_reason: str | None
    updated_at: datetime
    created_at: datetime


class TaskItemPageResponse(BaseModel):
    records: list[TaskItemResponse]
    total: int
    page_num: int
    page_size: int


class TaskItemDetailResponse(TaskItemResponse):
    normalized_url: str
    content_markdown: str | None
    retry_count: int
    push_retry_count: int
    cover_asset_id: int | None = None


class TaskItemUpdateRequest(BaseModel):
    title: str | None = Field(default=None, max_length=255)
    summary: str | None = Field(default=None, max_length=500)
    tags: list[str] | None = None
    category_level1: str | None = Field(default=None, max_length=120)
    category_level2: str | None = Field(default=None, max_length=120)
    content_markdown: str | None = None


class ReviewActionRequest(BaseModel):
    item_ids: list[int] = Field(min_length=1)
    action: str = Field(pattern="^(approve|reject|recrawl)$")


class TaskControlRequest(BaseModel):
    task_ids: list[int] = Field(min_length=1)
    action: str = Field(pattern="^(pause|resume)$")


class TaskItemDeleteRequest(BaseModel):
    item_ids: list[int] = Field(min_length=1)


class TaskItemAssetResponse(BaseModel):
    id: int
    source_url: str
    preview_url: str
    mime_type: str | None
    file_size: int
    asset_role: str


class TaskItemAssetRoleRequest(BaseModel):
    role: str = Field(pattern="^(cover|content)$")


class AssetManageResponse(BaseModel):
    id: int
    task_item_id: int
    task_id: int | None
    task_item_state: str | None
    title: str | None
    source_url: str
    preview_url: str
    mime_type: str | None
    file_size: int
    asset_role: str
    usage_status: str
    usage_reason: str
    created_at: datetime


class AssetManageSummary(BaseModel):
    total_count: int = 0
    total_file_size: int = 0
    unique_file_count: int = 0
    unique_file_size: int = 0
    shared_saved_bytes: int = 0
    pending_submit_count: int = 0
    submitted_cleanable_count: int = 0
    unused_count: int = 0
    active_count: int = 0


class AssetManagePageResponse(BaseModel):
    records: list[AssetManageResponse]
    summary: AssetManageSummary


class AssetManageDeleteRequest(BaseModel):
    asset_ids: list[int] = Field(min_length=1)


class TaskPurgeRequest(BaseModel):
    mode: str = Field(default="all", pattern="^(all|finished)$")


class SiteProfileUpsertRequest(BaseModel):
    name: str = Field(min_length=1, max_length=100)
    domain: str = Field(min_length=1, max_length=255)
    default_list_url: str | None = Field(default=None, max_length=1000)
    list_selector: str | None = Field(default=None, max_length=300)
    article_selector: str | None = Field(default=None, max_length=300)
    max_items: int = Field(default=20, ge=1, le=1000)
    interval_seconds: int = Field(default=2, ge=1, le=60)
    timeout_seconds: int = Field(default=20, ge=1, le=120)
    enabled: bool = True


class SiteProfileExtractTestRequest(BaseModel):
    list_url: str = Field(min_length=1, max_length=1000)
    domain: str = Field(default="", max_length=255)
    list_selector: str | None = Field(default=None, max_length=300)
    max_items: int = Field(default=20, ge=1, le=200)
    timeout_seconds: int = Field(default=20, ge=1, le=120)


class SiteProfileArticleTestRequest(BaseModel):
    article_url: str = Field(min_length=1, max_length=1000)
    article_selector: str | None = Field(default=None, max_length=300)
    timeout_seconds: int = Field(default=20, ge=1, le=120)


class SiteProfileArticleTestResponse(BaseModel):
    title: str
    summary: str
    markdown: str
    markdown_length: int
    image_count: int
    tags: list[str] = Field(default_factory=list)
    category_level1: str | None = None
    category_level2: str | None = None
    source_site: str
    fallback_used: bool
    author: str | None = None


class PushApprovedRequest(BaseModel):
    item_ids: list[int] = Field(min_length=1)
    push_mode: str = Field(default="skip", pattern="^(skip|update_existing_draft|create_new_draft)$")


class PushApprovedItemResult(BaseModel):
    item_id: int
    status: str
    message: str
    backend_candidate_id: int | None = None
    draft_id: int | None = None
    pushed_at: datetime | None = None
    duplicate_candidate_id: int | None = None
    duplicate_target_draft_id: int | None = None
    duplicate_title: str | None = None


class PushApprovedResponse(BaseModel):
    total: int
    succeeded: int
    failed: int
    results: list[PushApprovedItemResult]
