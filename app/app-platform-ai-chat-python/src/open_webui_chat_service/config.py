from __future__ import annotations

import os
from dataclasses import dataclass


def _as_bool(value: str | None, default: bool) -> bool:
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def _as_int(value: str | None, default: int) -> int:
    if value is None:
        return default
    try:
        return int(value.strip())
    except ValueError:
        return default


@dataclass(frozen=True)
class Settings:
    host: str
    port: int
    api_key: str
    require_api_key: bool
    ai_engine_base_url: str
    request_timeout_ms: int
    default_provider: str | None
    scene: str


def load_settings() -> Settings:
    return Settings(
        host=os.getenv("CHAT_SERVICE_HOST", "0.0.0.0"),
        port=_as_int(os.getenv("CHAT_SERVICE_PORT"), 13103),
        api_key=os.getenv("CHAT_SERVICE_API_KEY", "phase-a-dev-key"),
        require_api_key=_as_bool(os.getenv("CHAT_SERVICE_REQUIRE_API_KEY"), True),
        ai_engine_base_url=os.getenv("AI_ENGINE_BASE_URL", "http://127.0.0.1:13101/aiEngine").rstrip("/"),
        request_timeout_ms=_as_int(os.getenv("CHAT_SERVICE_REQUEST_TIMEOUT_MS"), 60000),
        default_provider=(os.getenv("CHAT_SERVICE_DEFAULT_PROVIDER") or "").strip() or None,
        scene=os.getenv("CHAT_SERVICE_SCENE", "open-webui-phase-a").strip() or "open-webui-phase-a",
    )


settings = load_settings()
