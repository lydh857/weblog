import asyncio
from fastapi.testclient import TestClient

from app.main import app
import app.main as app_main
from app.schemas import PushApprovedItemResult, PushApprovedResponse


def test_single_capture_review_and_push_flow(monkeypatch):
    async def noop_run_poll_cycle():
        await asyncio.sleep(0.1)

    monkeypatch.setattr(app_main, "_run_poll_cycle", noop_run_poll_cycle)

    async def fake_push_approved_items(session, item_ids, push_mode="skip"):
        return PushApprovedResponse(
            total=len(item_ids),
            succeeded=len(item_ids),
            failed=0,
            results=[
                PushApprovedItemResult(
                    item_id=item_ids[0],
                    status="succeeded",
                    message="ok",
                    backend_candidate_id=101,
                    draft_id=2001,
                    pushed_at="2026-09-03T01:00:00Z",
                )
            ],
        )

    monkeypatch.setattr("app.api.routes.tasks.push_approved_items", fake_push_approved_items)

    with TestClient(app) as client:
        create_resp = client.post(
            "/tasks",
            json={
                "mode": "single",
                "source_url": "https://example.com/post/1",
                "max_items": 1,
            },
        )
        assert create_resp.status_code == 200

        list_items_resp = client.get("/tasks/items")
        assert list_items_resp.status_code == 200
        body = list_items_resp.json()
        item_rows = body["records"] if isinstance(body, dict) and "records" in body else body
        assert len(item_rows) >= 1

        item_id = item_rows[0]["id"]
        review_resp = client.post(
            "/tasks/review-action",
            json={"item_ids": [item_id], "action": "approve"},
        )
        assert review_resp.status_code == 200

        push_resp = client.post("/tasks/push-approved", json={"item_ids": [item_id]})
        assert push_resp.status_code == 200
        assert push_resp.json()["succeeded"] == 1
        assert "pushed_at" in push_resp.json()["results"][0]
