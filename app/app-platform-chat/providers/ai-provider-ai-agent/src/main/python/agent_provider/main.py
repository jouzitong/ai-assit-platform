from __future__ import annotations

import asyncio
import json
import os
import re
import sys
from pathlib import Path
from typing import Any


if __package__ in {None, ""}:
    package_directory = Path(__file__).resolve().parent
    project_directory = package_directory.parent
    # This worker is executed as ``python agent_provider/main.py``. Python then
    # places ``agent_provider/`` before site-packages, causing our local
    # ``agent_provider.agents`` package to shadow the OpenAI Agents SDK package
    # named ``agents``. Keep only the project parent on the import path.
    sys.path[:] = [
        entry for entry in sys.path
        if Path(entry or ".").resolve() != package_directory
    ]
    sys.path.insert(0, str(project_directory))

from agent_provider.compiler import compile_snapshot
from agent_provider.events import EventEmitter
from agent_provider.protocol import normalize_payload
from agent_provider.runtime import run_graph


_ENV_DIAGNOSTIC_PREFIX = "AI_AGENT_ENV"
_STARTUP_ENV_KEYS = (
    "OPENAI_API_KEY",
    "OPENAI_BASE_URL",
    "OPENAI_MODEL",
    "AI_AGENT_CHAT_BASE_URL",
    "AI_AGENT_KB_SEARCH_TOKEN",
    "AI_AGENT_DATA_PREVIEW_TOKEN",
    "AI_AGENT_PLATFORM_TOKEN",
    "AI_AGENT_TOOL_GATEWAY_TOKEN",
    "AI_AGENT_SKILL_GATEWAY_TOKEN",
)


def main() -> None:
    emitter: EventEmitter | None = None
    try:
        _log_startup_environment()
        raw_payload = json.load(sys.stdin)
        if not isinstance(raw_payload, dict):
            raise ValueError("Worker input must be a JSON object")
        payload = normalize_payload(raw_payload)
        emitter = EventEmitter(payload)
        graph = compile_snapshot(payload)
        result = asyncio.run(run_graph(graph, emitter))
        emitter.result(result)
    except Exception as exc:
        safe_message = _safe_message(exc)
        if emitter is None:
            emitter = EventEmitter(normalize_payload({}))
        emitter.event(
            "round.failed",
            status="FAILED",
            message=safe_message,
            ext={"errorType": type(exc).__name__},
            frame_type="error",
        )
        raise SystemExit(1) from None


def _log_startup_environment() -> None:
    """Report whether Java-provided environment values reached this worker."""

    for key in _STARTUP_ENV_KEYS:
        value = os.getenv(key)
        print(
            f"{_ENV_DIAGNOSTIC_PREFIX} key={key} "
            f"present={str(value is not None).lower()} "
            f"nonEmpty={str(bool(value)).lower()} "
            f"length={len(value or '')} value={_diagnostic_value(key, value)}",
            file=sys.stderr,
            flush=True,
        )


def _diagnostic_value(key: str, value: str | None) -> str:
    if value is None:
        return "<missing>"
    if not value:
        return "<empty>"
    if any(marker in key for marker in ("TOKEN", "API_KEY", "SECRET", "PASSWORD")):
        return "[REDACTED]"
    return value.replace("\r", " ").replace("\n", " ")[:256]


def _safe_message(error: BaseException) -> str:
    message = str(error).strip() or type(error).__name__
    message = re.sub(r"(?i)bearer\s+[^\s,;]+", "Bearer [REDACTED]", message)
    message = re.sub(r"\bsk-[A-Za-z0-9_-]{8,}\b", "[REDACTED_OPENAI_KEY]", message)
    return message[:1000]


if __name__ == "__main__":
    main()
