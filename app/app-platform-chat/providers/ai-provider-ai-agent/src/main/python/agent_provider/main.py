from __future__ import annotations

import asyncio
import json
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


def main() -> None:
    emitter: EventEmitter | None = None
    try:
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


def _safe_message(error: BaseException) -> str:
    message = str(error).strip() or type(error).__name__
    message = re.sub(r"(?i)bearer\s+[^\s,;]+", "Bearer [REDACTED]", message)
    message = re.sub(r"\bsk-[A-Za-z0-9_-]{8,}\b", "[REDACTED_OPENAI_KEY]", message)
    return message[:1000]


if __name__ == "__main__":
    main()
