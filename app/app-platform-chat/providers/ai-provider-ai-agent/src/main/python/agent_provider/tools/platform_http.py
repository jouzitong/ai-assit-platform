from __future__ import annotations

import json
import os
import re
from typing import Any, Iterable
from urllib import error, request
from urllib.parse import urlsplit


DEFAULT_TIMEOUT_SECONDS = 20
MAX_REQUEST_BYTES = 1024 * 1024
MAX_RESPONSE_BYTES = 4 * 1024 * 1024


def post_platform_json(
    url: str,
    payload: dict[str, Any],
    *,
    token_env_keys: Iterable[str],
    trace_id: str | None = None,
    run_id: str | None = None,
    session_code: str | None = None,
    timeout_seconds: int = DEFAULT_TIMEOUT_SECONDS,
) -> dict[str, Any]:
    """POST JSON to an authenticated platform endpoint and normalize its envelope."""

    try:
        endpoint = urlsplit(url if isinstance(url, str) else "")
    except ValueError as exc:
        return _failure("PLATFORM_ENDPOINT_INVALID", f"Invalid platform endpoint: {exc}")
    if endpoint.scheme not in {"http", "https"} or not endpoint.hostname:
        return _failure("PLATFORM_ENDPOINT_INVALID", "Platform endpoint must be an HTTP(S) URL")

    headers = {"Content-Type": "application/json; charset=utf-8"}
    token = _first_env(token_env_keys)
    if token:
        headers["Authorization"] = token if token.lower().startswith("bearer ") else f"Bearer {token}"
    if _text(trace_id):
        headers["X-Trace-Id"] = str(trace_id).strip()
    if _text(run_id):
        headers["X-Agent-Run-Id"] = str(run_id).strip()
    if _text(session_code):
        headers["X-Session-Code"] = str(session_code).strip()

    try:
        body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    except (TypeError, ValueError, RecursionError) as exc:
        return _failure("PLATFORM_REQUEST_INVALID", f"Request payload is not JSON-serializable: {exc}")
    if len(body) > MAX_REQUEST_BYTES:
        return _failure("PLATFORM_REQUEST_TOO_LARGE", "Request payload exceeds 1 MiB")
    try:
        http_request = request.Request(url, data=body, headers=headers, method="POST")
    except (TypeError, ValueError) as exc:
        return _failure("PLATFORM_ENDPOINT_INVALID", f"Invalid platform endpoint: {exc}")
    try:
        with request.urlopen(http_request, timeout=max(1, timeout_seconds)) as response:
            response_bytes = response.read(MAX_RESPONSE_BYTES + 1)
            if len(response_bytes) > MAX_RESPONSE_BYTES:
                return _failure("PLATFORM_RESPONSE_TOO_LARGE", "Platform response exceeds 4 MiB")
            response_text = response_bytes.decode("utf-8", errors="replace")
    except error.HTTPError as exc:
        response_bytes = exc.read(MAX_RESPONSE_BYTES + 1)
        response_text = response_bytes[:MAX_RESPONSE_BYTES].decode("utf-8", errors="replace")
        return _failure("PLATFORM_HTTP_ERROR", f"HTTP {exc.code}: {response_text}")
    except error.URLError as exc:
        return _failure("PLATFORM_REQUEST_FAILED", str(exc.reason))
    except (TimeoutError, ValueError) as exc:
        return _failure("PLATFORM_REQUEST_FAILED", str(exc))

    try:
        decoded = json.loads(response_text) if response_text else {}
    except json.JSONDecodeError:
        return _failure("PLATFORM_RESPONSE_INVALID", f"Non-JSON response: {response_text}")

    if not isinstance(decoded, dict):
        return _failure("PLATFORM_RESPONSE_INVALID", "Response must be a JSON object")

    # Athena endpoints may return a regular R envelope when an exception is
    # handled, even when the success DTO is marked with IgnoredResultWrapper.
    if "code" in decoded and ("data" in decoded or "msg" in decoded or "message" in decoded):
        code = _integer(decoded.get("code"))
        if code not in (None, 0):
            message = decoded.get("msg") or decoded.get("message") or "Platform request was rejected"
            message_text = str(message)
            # The virtual-data advice deliberately wraps stable category
            # codes inside the platform's numeric R envelope.
            category = re.match(r"^([A-Z][A-Z0-9_]{2,127})\s*:\s*", message_text)
            return _failure(category.group(1) if category else str(code), message_text)
        data = decoded.get("data")
        if data is None:
            data = {}
        if not isinstance(data, dict):
            return _failure("PLATFORM_RESPONSE_INVALID", "Envelope data must be a JSON object")
        decoded = data

    return {"success": True, "data": decoded}


def _first_env(keys: Iterable[str]) -> str:
    for key in keys:
        value = (os.getenv(key) or "").strip()
        if value:
            return value
    return ""


def _failure(code: str, message: str) -> dict[str, Any]:
    return {
        "success": False,
        "errorCode": code,
        "error": _safe_message(message),
    }


def _safe_message(value: str) -> str:
    normalized = str(value).replace("\r", " ").replace("\n", " ")
    normalized = re.sub(r"(?i)bearer\s+[^\s,;]+", "Bearer [REDACTED]", normalized)
    normalized = re.sub(r"\bsk-[A-Za-z0-9_-]{8,}\b", "[REDACTED_OPENAI_KEY]", normalized)
    return normalized[:500]


def _integer(value: Any) -> int | None:
    try:
        return int(value) if value is not None else None
    except (TypeError, ValueError):
        return None


def _text(value: Any) -> bool:
    return isinstance(value, str) and bool(value.strip())
