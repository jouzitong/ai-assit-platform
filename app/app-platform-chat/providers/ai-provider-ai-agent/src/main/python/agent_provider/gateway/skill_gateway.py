from __future__ import annotations

import base64
import json
import os
from typing import Any
from urllib import error, parse, request

from agent_provider.chat_endpoint import chat_endpoint
from agent_provider.skills.validator import checksum_matches


def read_skill_resource(
    record: Any,
    relative: str,
    run: dict[str, Any],
    snapshot_hash: str | None,
    expected: dict[str, Any] | None,
    max_resource_bytes: int,
) -> tuple[str, dict[str, Any]]:
    if not record.code or record.version is None:
        raise ValueError(f"Skill resource not found: {relative}")
    token = (
        os.getenv("AI_AGENT_SKILL_GATEWAY_TOKEN")
        or os.getenv("AI_AGENT_TOOL_GATEWAY_TOKEN")
        or ""
    ).strip()
    if not token:
        raise ValueError("AI_AGENT_SKILL_GATEWAY_TOKEN is required")
    run_id = _text(run.get("runId"))
    if not run_id:
        raise ValueError("Skill Gateway runId is required")
    frozen_hash = _text(snapshot_hash)
    if not frozen_hash:
        raise ValueError("Skill Gateway snapshotHash is required")
    url = chat_endpoint(
        f"/api/v1/ai/skill-gateway/{parse.quote(record.code, safe='')}"
        f"/versions/{record.version}/resources/read"
    )
    body = json.dumps({
        "path": relative,
        "run": {
            "runId": run_id,
            "snapshotHash": frozen_hash,
        },
    }, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    http_request = request.Request(
        url,
        data=body,
        headers={
            "Content-Type": "application/json; charset=utf-8",
            "Authorization": token if token.lower().startswith("bearer ") else f"Bearer {token}",
        },
        method="POST",
    )
    try:
        with request.urlopen(http_request, timeout=20) as response:
            response_text = response.read().decode("utf-8", errors="replace")
    except error.HTTPError as exc:
        raise ValueError(f"Skill Gateway HTTP {exc.code}") from None
    except error.URLError as exc:
        raise ValueError(f"Skill Gateway request failed: {exc.reason}") from None
    try:
        decoded = json.loads(response_text) if response_text else {}
    except json.JSONDecodeError:
        raise ValueError("Skill Gateway returned non-JSON") from None
    if not isinstance(decoded, dict):
        raise ValueError("Skill Gateway response must be a JSON object")
    if decoded.get("skillCode") != record.code or _optional_int(decoded.get("skillVersion")) != record.version:
        raise ValueError("Skill Gateway response identity does not match the frozen Skill")
    if decoded.get("path") != relative:
        raise ValueError("Skill Gateway response path does not match the requested resource")
    encoding = str(decoded.get("encoding") or "utf-8").lower()
    raw_content = decoded.get("content")
    if not isinstance(raw_content, str):
        raise ValueError("Skill Gateway response content must be a string")
    if encoding == "base64":
        try:
            content_bytes = base64.b64decode(raw_content, validate=True)
        except ValueError:
            raise ValueError("Skill Gateway returned invalid base64 content") from None
        content = raw_content
    elif encoding == "utf-8":
        content = raw_content
        content_bytes = raw_content.encode("utf-8")
    else:
        raise ValueError(f"Skill Gateway returned unsupported encoding: {encoding}")
    if len(content_bytes) > max_resource_bytes:
        raise ValueError(f"Skill resource is larger than {max_resource_bytes} bytes")
    response_checksum = _text(decoded.get("checksum"))
    expected_checksum = _text(expected.get("checksum")) if expected else None
    if expected_checksum and response_checksum != expected_checksum:
        raise ValueError("Skill Gateway checksum does not match the frozen package manifest")
    if response_checksum and not checksum_matches(response_checksum, content_bytes):
        raise ValueError("Skill Gateway content checksum is invalid")
    return content, {
        "mediaType": decoded.get("mediaType"),
        "checksum": response_checksum,
        "encoding": encoding,
    }


def _text(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def _optional_int(value: Any) -> int | None:
    try:
        return int(value) if value is not None else None
    except (TypeError, ValueError):
        return None
