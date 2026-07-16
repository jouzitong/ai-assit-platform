from __future__ import annotations

import asyncio
import hashlib
import json
import os
import re
from typing import Any
from urllib import error, parse, request


DEFAULT_GATEWAY_TIMEOUT_SECONDS = 20.0


def build_gateway_tool(
    descriptor: dict[str, Any],
    run: dict[str, Any],
    snapshot_hash: str | None,
) -> Any:
    """Build one dynamic OpenAI Agents SDK FunctionTool backed by the Java Tool Gateway."""

    from agents import FunctionTool

    async def invoke(_context: Any, arguments_json: str) -> Any:
        try:
            arguments = json.loads(arguments_json or "{}")
        except json.JSONDecodeError as exc:
            return _failure(descriptor, f"Tool arguments are not valid JSON: {exc.msg}")
        if not isinstance(arguments, dict):
            return _failure(descriptor, "Tool arguments must be a JSON object")
        return await asyncio.to_thread(_invoke_gateway, descriptor, run, arguments, snapshot_hash)

    return FunctionTool(
        name=str(descriptor["sdkName"]),
        description=str(descriptor["description"]),
        params_json_schema=dict(descriptor["inputSchema"]),
        on_invoke_tool=invoke,
        strict_json_schema=False,
        timeout_seconds=max(0.1, int(descriptor.get("timeoutMs") or 20_000) / 1000),
    )


def _invoke_gateway(
    descriptor: dict[str, Any],
    run: dict[str, Any],
    arguments: dict[str, Any],
    snapshot_hash: str | None = None,
) -> dict[str, Any]:
    base_url = (os.getenv("AI_AGENT_TOOL_GATEWAY_URL") or "").strip().rstrip("/")
    token = (os.getenv("AI_AGENT_TOOL_GATEWAY_TOKEN") or "").strip()
    if not base_url:
        return _failure(descriptor, "AI_AGENT_TOOL_GATEWAY_URL is required")
    if not token:
        return _failure(descriptor, "AI_AGENT_TOOL_GATEWAY_TOKEN is required")
    run_id = str(run.get("runId") or "").strip()
    if not run_id:
        return _failure(descriptor, "Tool Gateway runId is required")
    frozen_hash = str(snapshot_hash or "").strip()
    if not frozen_hash:
        return _failure(descriptor, "Tool Gateway snapshotHash is required")

    code = str(descriptor["code"])
    version = int(descriptor["version"])
    url = (
        f"{base_url}/api/v1/ai/tool-gateway/"
        f"{parse.quote(code, safe='')}/versions/{version}/invoke"
    )
    safe_run = {
        key: run.get(key)
        for key in ("runId", "requestId", "traceId", "sessionCode", "roundCode", "userId")
        if run.get(key) is not None
    }
    safe_run["snapshotHash"] = frozen_hash
    body_value = {"arguments": arguments, "run": safe_run}
    body = json.dumps(body_value, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    idempotency_source = json.dumps(arguments, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    idempotency_key = hashlib.sha256(
        f"{run_id}|tool://{code}/v{version}|{idempotency_source}".encode("utf-8")
    ).hexdigest()
    headers = {
        "Content-Type": "application/json; charset=utf-8",
        "Authorization": token if token.lower().startswith("bearer ") else f"Bearer {token}",
        "Idempotency-Key": idempotency_key,
    }
    approval = (os.getenv("AI_AGENT_TOOL_APPROVAL") or "").strip()
    if approval:
        headers["X-Tool-Approval"] = approval
    http_request = request.Request(url, data=body, headers=headers, method="POST")
    timeout = max(0.1, int(descriptor.get("timeoutMs") or 20_000) / 1000)
    try:
        with request.urlopen(http_request, timeout=timeout) as response:
            response_text = response.read().decode("utf-8", errors="replace")
    except error.HTTPError as exc:
        response_text = exc.read().decode("utf-8", errors="replace")
        return {
            "toolCode": code,
            "toolVersion": version,
            "status": "FAILED",
            "error": f"Tool Gateway HTTP {exc.code}: {_safe_message(response_text)}",
        }
    except error.URLError as exc:
        return {
            "toolCode": code,
            "toolVersion": version,
            "status": "FAILED",
            "error": f"Tool Gateway request failed: {_safe_message(str(exc.reason))}",
        }
    try:
        decoded = json.loads(response_text) if response_text else {}
    except json.JSONDecodeError:
        return {
            "toolCode": code,
            "toolVersion": version,
            "status": "FAILED",
            "error": f"Tool Gateway returned non-JSON: {_safe_message(response_text)}",
        }
    if not isinstance(decoded, dict):
        return {
            "toolCode": code,
            "toolVersion": version,
            "status": "FAILED",
            "error": "Tool Gateway response must be a JSON object",
        }
    if decoded.get("toolCode") != code or _integer(decoded.get("toolVersion")) != version:
        return _failure(descriptor, "Tool Gateway response identity does not match the invoked Tool")
    status = str(decoded.get("status") or "").upper()
    if status not in {"SUCCESS", "FAILED"}:
        return _failure(descriptor, "Tool Gateway response status must be SUCCESS or FAILED")
    decoded["status"] = status
    return decoded


def _failure(descriptor: dict[str, Any], message: str) -> dict[str, Any]:
    return {
        "toolCode": str(descriptor.get("code") or ""),
        "toolVersion": _integer(descriptor.get("version")),
        "status": "FAILED",
        "error": _safe_message(message),
    }


def _integer(value: Any) -> int | None:
    try:
        return int(value) if value is not None else None
    except (TypeError, ValueError):
        return None


def _safe_message(value: str) -> str:
    normalized = (
        str(value)
        .replace("\r", " ")
        .replace("\n", " ")
    )
    normalized = re.sub(r"(?i)bearer\s+[^\s,;]+", "Bearer [REDACTED]", normalized)
    normalized = re.sub(r"\bsk-[A-Za-z0-9_-]{8,}\b", "[REDACTED_OPENAI_KEY]", normalized)
    return normalized[:500]
