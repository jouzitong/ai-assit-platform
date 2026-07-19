from __future__ import annotations

import os


CHAT_BASE_URL_ENV_KEY = "AI_AGENT_CHAT_BASE_URL"
DEFAULT_CHAT_BASE_URL = "http://127.0.0.1:13103/chat"


def chat_base_url() -> str:
    """Return the Chat service base URL injected into the local Agent worker."""

    configured = (os.getenv(CHAT_BASE_URL_ENV_KEY) or "").strip()
    return (configured or DEFAULT_CHAT_BASE_URL).rstrip("/")


def chat_endpoint(route: str) -> str:
    """Build a Chat-owned endpoint from a fixed absolute route."""

    if not isinstance(route, str) or not route.startswith("/") or route.startswith("//"):
        raise ValueError("Chat endpoint route must be an absolute application route")
    return f"{chat_base_url()}{route}"
