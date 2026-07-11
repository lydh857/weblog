from app.services.push_target import PushTargetManager


def test_push_target_manager_loads_default_and_updates(tmp_path, monkeypatch):
    manager = PushTargetManager()
    monkeypatch.setattr(manager, "_config_path", tmp_path / "push-targets.json")

    default_config = manager.load_config()
    assert default_config["active_target"] in {"local", "server"}
    assert "local" in default_config["profiles"]
    assert "server" in default_config["profiles"]

    updated = manager.update_config(
        "server",
        {
            "local": {
                "base_url": "http://127.0.0.1:9091",
                "crawler_token": "local-token",
                "device_id": "local-worker",
                "request_origin": "http://localhost:3001",
            },
            "server": {
                "base_url": "https://example.com",
                "crawler_token": "server-token",
                "device_id": "server-worker",
                "request_origin": "https://admin.example.com",
            },
        },
    )

    assert updated["active_target"] == "server"
    assert updated["profiles"]["server"]["base_url"] == "https://example.com"
    assert manager.get_active_profile()["name"] == "server"
