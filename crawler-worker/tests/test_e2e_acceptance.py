from fastapi.testclient import TestClient

from app.main import app
from app.schemas import PushApprovedItemResult, PushApprovedResponse


def test_single_capture_review_and_push_flow(monkeypatch):
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
        item_rows = list_items_resp.json()
        assert len(item_rows) >= 1

        item_id = item_rows[0]["id"]
        review_resp = client.post(
            "/tasks/review-action",
            json={"item_ids": [item_id], "action": "approve"},
        )
        assert review_resp.status_code == 200

    async def fake_push_approved_items(_, item_ids):
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
                    pushed_at=None,
                )
            ],
        )

    monkeypatch.setattr("app.api.routes.tasks.push_approved_items", fake_push_approved_items)

    with TestClient(app) as client:
        push_resp = client.post("/tasks/push-approved", json={"item_ids": [item_id]})
        assert push_resp.status_code == 200
        assert push_resp.json()["succeeded"] == 1
        assert "pushed_at" in push_resp.json()["results"][0]
