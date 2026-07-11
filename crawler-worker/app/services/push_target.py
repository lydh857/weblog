from __future__ import annotations

import json
from pathlib import Path
from typing import Literal

from app.core.config import settings


PushTargetName = Literal["local", "server"]


class PushTargetManager:
    def __init__(self) -> None:
        self._config_path = Path(settings.sqlite_path).parent / "push-targets.json"

    def _default_config(self) -> dict:
        return {
            "active_target": settings.backend_active_target if settings.backend_active_target in {"local", "server"} else "local",
            "profiles": {
                "local": {
                    "base_url": settings.backend_base_url,
                    "crawler_token": settings.backend_crawler_token,
                    "device_id": settings.backend_device_id,
                    "request_origin": settings.backend_request_origin,
                },
                "server": {
                    "base_url": settings.backend_server_base_url,
                    "crawler_token": settings.backend_server_crawler_token,
                    "device_id": settings.backend_server_device_id,
                    "request_origin": settings.backend_server_request_origin,
                },
            },
        }

    def load_config(self) -> dict:
        default_config = self._default_config()
        self._config_path.parent.mkdir(parents=True, exist_ok=True)
        if not self._config_path.exists():
            self.save_config(default_config)
            return default_config
        try:
            loaded = json.loads(self._config_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            self.save_config(default_config)
            return default_config

        profiles = loaded.get("profiles") if isinstance(loaded, dict) else None
        active_target = loaded.get("active_target") if isinstance(loaded, dict) else None
        merged = default_config
        if isinstance(profiles, dict):
            for target in ("local", "server"):
                profile = profiles.get(target)
                if isinstance(profile, dict):
                    merged["profiles"][target].update({
                        "base_url": str(profile.get("base_url") or "").strip(),
                        "crawler_token": str(profile.get("crawler_token") or "").strip(),
                        "device_id": str(profile.get("device_id") or merged["profiles"][target]["device_id"]).strip(),
                        "request_origin": str(profile.get("request_origin") or "").strip(),
                    })
        if active_target in {"local", "server"}:
            merged["active_target"] = active_target
        return merged

    def save_config(self, config: dict) -> None:
        self._config_path.parent.mkdir(parents=True, exist_ok=True)
        self._config_path.write_text(json.dumps(config, ensure_ascii=False, indent=2), encoding="utf-8")

    def get_active_profile(self) -> dict:
        config = self.load_config()
        active_target: PushTargetName = config["active_target"]
        profile = config["profiles"][active_target]
        return {"name": active_target, **profile}

    def update_config(self, active_target: PushTargetName, profiles: dict) -> dict:
        config = self._default_config()
        config["active_target"] = active_target
        for target in ("local", "server"):
            source = profiles.get(target, {}) if isinstance(profiles, dict) else {}
            config["profiles"][target] = {
                "base_url": str(source.get("base_url") or "").strip(),
                "crawler_token": str(source.get("crawler_token") or "").strip(),
                "device_id": str(source.get("device_id") or self._default_config()["profiles"][target]["device_id"]).strip(),
                "request_origin": str(source.get("request_origin") or "").strip(),
            }
        self.save_config(config)
        return config


def get_active_request_profile() -> dict[str, str]:
    profile = push_target_manager.get_active_profile()
    base_url = str(profile.get("base_url") or "").strip().rstrip("/")
    crawler_token = str(profile.get("crawler_token") or "").strip()
    device_id = str(profile.get("device_id") or "").strip()
    request_origin = str(profile.get("request_origin") or "").strip().rstrip("/")
    if not base_url:
        raise ValueError("当前推送目标未配置后端地址")
    if not crawler_token:
        raise ValueError("当前推送目标未配置采集集成令牌")
    if not device_id:
        raise ValueError("当前推送目标未配置设备标识")
    return {
        "name": str(profile.get("name") or "local"),
        "base_url": base_url,
        "crawler_token": crawler_token,
        "device_id": device_id,
        "request_origin": request_origin,
    }


def build_auth_headers() -> dict[str, str]:
    profile = get_active_request_profile()
    headers = {
        "X-Crawler-Token": profile["crawler_token"],
        "X-Crawler-Device-Id": profile["device_id"],
    }
    if profile["request_origin"]:
        origin = profile["request_origin"]
        headers["Origin"] = origin
        headers["Referer"] = f"{origin}/crawler-worker"
    return headers


push_target_manager = PushTargetManager()
