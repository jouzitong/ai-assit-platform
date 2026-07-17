import json
import os
from typing import Any
from urllib import error, request

from agents import function_tool


DEFAULT_KB_SEARCH_URL = "http://127.0.0.1:9764/chat/api/v1/ai/execution/kb/search"


def _kb_search_url() -> str:
    return os.getenv("AI_AGENT_KB_SEARCH_URL") or DEFAULT_KB_SEARCH_URL


def _kb_search_headers() -> dict[str, str]:
    headers = {"Content-Type": "application/json; charset=utf-8"}
    token = (os.getenv("AI_AGENT_KB_SEARCH_TOKEN") or "").strip()
    if token:
        headers["Authorization"] = token if token.lower().startswith("bearer ") else f"Bearer {token}"
    return headers

def _build_request_payload(
    kb_id: str,
    query_text: str,
    top_k: int,
    trace_id: str | None,
    scene: str | None,
) -> dict[str, Any]:
    meta: dict[str, Any] = {}
    if trace_id:
        meta["traceId"] = trace_id
    if scene:
        meta["scene"] = scene
    return {
        "kbId": kb_id,
        "query": query_text,
        "topK": top_k,
        "meta": meta,
    }


def _normalize_items(items: Any) -> list[dict[str, Any]]:
    normalized: list[dict[str, Any]] = []
    if not isinstance(items, list):
        return normalized
    for item in items:
        if not isinstance(item, dict):
            continue
        normalized.append(
            {
                "documentId": item.get("documentId"),
                "score": item.get("score"),
                "content": item.get("content"),
                "metadata": item.get("metadata") if isinstance(item.get("metadata"), dict) else {},
            }
        )
    return normalized


def _request_kb_search(payload: dict[str, Any]) -> dict[str, Any]:
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = request.Request(
        _kb_search_url(),
        data=body,
        headers=_kb_search_headers(),
        method="POST",
    )
    try:
        with request.urlopen(req, timeout=20) as response:
            text = response.read().decode("utf-8")
    except error.HTTPError as exc:
        text = exc.read().decode("utf-8", errors="replace")
        return {
            "success": False,
            "error": f"KB search HTTP {exc.code}: {text[:500]}",
            "items": [],
        }
    except error.URLError as exc:
        return {
            "success": False,
            "error": f"KB search request failed: {exc.reason}",
            "items": [],
        }

    try:
        data = json.loads(text) if text else {}
    except json.JSONDecodeError:
        return {
            "success": False,
            "error": f"KB search returned non-JSON response: {text[:500]}",
            "items": [],
        }

    items = _normalize_items(data.get("items"))
    return {
        "success": True,
        "kbId": data.get("kbId") or payload.get("kbId"),
        "items": items,
    }


def available_knowledge_bases(run: Any) -> list[dict[str, Any]]:
    """Return the secret-free KB allowlist frozen into the current run context."""

    context = run.get("context") if isinstance(run, dict) else None
    values = context.get("knowledgeBases") if isinstance(context, dict) else None
    result: list[dict[str, Any]] = []
    seen: set[str] = set()
    if not isinstance(values, list):
        return result
    for value in values:
        if not isinstance(value, dict):
            continue
        kb_code = value.get("kbCode")
        if not isinstance(kb_code, str) or not kb_code.strip():
            continue
        normalized = kb_code.strip()
        if normalized in seen:
            continue
        seen.add(normalized)
        descriptor: dict[str, Any] = {"kbCode": normalized}
        for key in ("name", "description", "tags"):
            item = value.get(key)
            if isinstance(item, str) and item.strip():
                descriptor[key] = item.strip()
            elif key == "tags" and isinstance(item, list):
                descriptor[key] = [str(tag).strip() for tag in item if str(tag).strip()]
        result.append(descriptor)
    return result


def build_knowledge_base_search_tool(run: dict[str, Any], function_tool: Any) -> Any | None:
    """Create a per-run KB search tool limited to the Java-provided allowlist."""

    knowledge_bases = available_knowledge_bases(run)
    allowed_codes = {item["kbCode"] for item in knowledge_bases}
    if not allowed_codes:
        return None
    trace_id = run.get("traceId") if isinstance(run.get("traceId"), str) else None

    def search_knowledge_base(kb_code: str, query: str, top_k: int = 5) -> dict[str, Any]:
        """Search one knowledge base authorized for this Agent run by kb_code and query text."""

        normalized_code = kb_code.strip() if isinstance(kb_code, str) else ""
        if normalized_code not in allowed_codes:
            return {
                "tool": "knowledge_base_search_tool",
                "success": False,
                "error": "kb_code is not available for this Agent run.",
                "availableKbCodes": sorted(allowed_codes),
            }
        if not isinstance(query, str) or not query.strip():
            return {"tool": "knowledge_base_search_tool", "success": False, "error": "query is required."}
        payload = _build_request_payload(
            kb_id=normalized_code,
            query_text=query.strip(),
            top_k=max(1, int(top_k or 5)),
            trace_id=trace_id.strip() if trace_id and trace_id.strip() else None,
            scene="ai-agent-tool",
        )
        result = _request_kb_search(payload)
        result["tool"] = "knowledge_base_search_tool"
        result["kbCode"] = normalized_code
        result["topK"] = payload["topK"]
        if result.get("success"):
            result["summary"] = f"Returned {len(result.get('items', []))} knowledge hits."
        return result

    decorator = function_tool(
        name_override="knowledge_base_search_tool",
        description_override="Search a knowledge base explicitly listed in the current Agent run by kb_code.",
    )
    return decorator(search_knowledge_base)


@function_tool
def knowledge_base_search_tool(
    kb_id: str,
    query: str,
    top_k: int = 5,
    trace_id: str | None = None,
    scene: str | None = None,
) -> dict[str, Any]:
    """Search a knowledge base by kb_id and query text, then return matched items."""

    if not isinstance(kb_id, str) or not kb_id.strip():
        return {"tool": "knowledge_base_search_tool", "success": False, "error": "kb_id is required."}
    if not isinstance(query, str) or not query.strip():
        return {"tool": "knowledge_base_search_tool", "success": False, "error": "query is required."}

    payload = _build_request_payload(
        kb_id=kb_id.strip(),
        query_text=query.strip(),
        top_k=max(1, int(top_k or 5)),
        trace_id=trace_id.strip() if isinstance(trace_id, str) and trace_id.strip() else None,
        scene=scene.strip() if isinstance(scene, str) and scene.strip() else "ai-agent-tool",
    )
    result = _request_kb_search(payload)
    result["tool"] = "knowledge_base_search_tool"
    result["topK"] = payload["topK"]
    if result.get("success"):
        result["summary"] = f"Returned {len(result.get('items', []))} knowledge hits."
    return result
