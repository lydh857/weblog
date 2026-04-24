import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import text

from app.api.routes.site_profiles import router as site_profile_router
from app.api.routes.settings import router as settings_router
from app.api.routes.tasks import router as task_router
from app.core.config import settings
from app.core.logging import setup_logging
from app.db import SessionLocal, engine
from app.models import Base
from app.schemas import HealthResponse
from app.services.browser_pool import browser_pool
from app.services.scheduler import TaskScheduler, poll_and_run_pending_tasks

setup_logging()
logger = logging.getLogger(__name__)
scheduler = TaskScheduler()
polling_task: asyncio.Task | None = None


async def _migrate_sqlite_schema() -> None:
    alter_sql = {
        "crawler_task_item": [
            "ALTER TABLE crawler_task_item ADD COLUMN tags_json TEXT",
            "ALTER TABLE crawler_task_item ADD COLUMN extracted_tags_json TEXT",
            "ALTER TABLE crawler_task_item ADD COLUMN category_level1 VARCHAR(120)",
            "ALTER TABLE crawler_task_item ADD COLUMN category_level2 VARCHAR(120)",
            "ALTER TABLE crawler_task_item ADD COLUMN cover_asset_id INTEGER",
            "ALTER TABLE crawler_task_item ADD COLUMN discovered_article_id INTEGER",
            "ALTER TABLE crawler_task_item ADD COLUMN last_error_kind VARCHAR(64)",
        ],
        "crawler_task": [
            "ALTER TABLE crawler_task ADD COLUMN discovered_new_count INTEGER DEFAULT 0",
            "ALTER TABLE crawler_task ADD COLUMN skipped_known_count INTEGER DEFAULT 0",
            "ALTER TABLE crawler_task ADD COLUMN scanned_link_count INTEGER DEFAULT 0",
            "ALTER TABLE crawler_task ADD COLUMN current_stage VARCHAR(64)",
            "ALTER TABLE crawler_task ADD COLUMN last_error_summary VARCHAR(500)",
            "ALTER TABLE crawler_task ADD COLUMN last_active_at DATETIME",
        ],
        "site_profile": [
            "ALTER TABLE site_profile ADD COLUMN default_list_url VARCHAR(1000)",
        ],
        "crawler_asset": [
            "ALTER TABLE crawler_asset ADD COLUMN asset_role VARCHAR(32) DEFAULT 'content'",
        ],
    }
    async with engine.begin() as conn:
        for table_name, statements in alter_sql.items():
            rows = (await conn.execute(text(f"PRAGMA table_info({table_name})"))).fetchall()
            existing_columns = {str(row[1]) for row in rows}
            for statement in statements:
                column_name = statement.split(" ADD COLUMN ", 1)[1].split(" ", 1)[0]
                if column_name in existing_columns:
                    continue
                await conn.execute(text(statement))
        await conn.execute(text("CREATE INDEX IF NOT EXISTS idx_crawler_task_item_discovered_article_id ON crawler_task_item(discovered_article_id)"))


async def _run_poll_cycle() -> None:
    async with SessionLocal() as session:
        try:
            await poll_and_run_pending_tasks(session, scheduler)
            await session.commit()
        except Exception:
            await session.rollback()
            logger.exception("worker poll cycle failed")


async def _poll_loop() -> None:
    while True:
        try:
            await _run_poll_cycle()
        except asyncio.CancelledError:
            raise
        except Exception:
            logger.exception("worker poll loop unexpected failure")
        await asyncio.sleep(settings.scheduler_tick_seconds)


@asynccontextmanager
async def lifespan(_: FastAPI):
    global polling_task
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    await _migrate_sqlite_schema()
    scheduler.start()
    polling_task = asyncio.create_task(_poll_loop())
    try:
        yield
    finally:
        if polling_task:
            polling_task.cancel()
        scheduler.shutdown()
        await browser_pool.close()
        await engine.dispose()


app = FastAPI(title=settings.app_name, lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://127.0.0.1:17890",
        "http://localhost:17890",
        "tauri://localhost",
        "http://tauri.localhost",
        "https://tauri.localhost",
    ],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(task_router)
app.include_router(site_profile_router)
app.include_router(settings_router)


@app.get("/health", response_model=HealthResponse)
async def health() -> HealthResponse:
    return HealthResponse(status="ok", service=settings.app_name)
