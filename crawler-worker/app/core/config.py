from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="CRAWLER_", env_file=".env", extra="ignore")

    app_name: str = "crawler-worker"
    app_env: str = "dev"
    sqlite_path: str = "./data/crawler.db"
    cache_dir: str = "./data/cache"
    cache_max_bytes: int = 10 * 1024 * 1024 * 1024
    scheduler_tick_seconds: int = 5
    max_global_concurrency: int = 3
    max_domain_concurrency: int = 1
    max_retry_count: int = 3
    batch_known_url_stop_count: int = 10
    batch_expand_scan_multiplier: int = 5
    batch_expand_max_scan_items: int = 200
    request_timeout_seconds: int = 20
    static_body_min_chars: int = 280
    backend_base_url: str = "http://127.0.0.1:9091"
    backend_crawler_token: str = ""
    backend_device_id: str = "local-worker"
    backend_request_origin: str = "http://localhost:3001"
    backend_server_base_url: str = ""
    backend_server_crawler_token: str = ""
    backend_server_device_id: str = "server-worker"
    backend_server_request_origin: str = ""
    backend_active_target: str = "local"
    url_policy_allow_private: bool = False
    url_policy_allowlist: str = ""

    @property
    def sqlite_url(self) -> str:
        sqlite_file = Path(self.sqlite_path)
        sqlite_file.parent.mkdir(parents=True, exist_ok=True)
        db_path = sqlite_file.as_posix()
        return f"sqlite+aiosqlite:///{db_path}"


settings = Settings()
