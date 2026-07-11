from sqlalchemy.ext.asyncio import AsyncSession

from app.models import CrawlerDiagnostic


async def add_diagnostic(
    session: AsyncSession,
    task_item_id: int,
    kind: str,
    message: str,
    detail: str | None = None,
) -> None:
    session.add(
        CrawlerDiagnostic(
            task_item_id=task_item_id,
            kind=kind,
            message=message,
            detail=detail,
        )
    )
    await session.flush()
