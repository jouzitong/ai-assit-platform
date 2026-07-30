from typing import Any

from agents import function_tool

from agent_provider.chat_endpoint import chat_endpoint

from .platform_http import post_platform_json

KB_SEARCH_CHAT_ROUTE = "/api/v1/ai/execution/kb/search"


def _kb_search_url() -> str:
    return chat_endpoint(KB_SEARCH_CHAT_ROUTE)


def _build_request_payload(
    kb_code: str,
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
        "kbCode": kb_code,
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


def _request_kb_search(
    payload: dict[str, Any],
    *,
    trace_id: str | None = None,
    run_id: str | None = None,
    session_code: str | None = None,
) -> dict[str, Any]:
    result = post_platform_json(
        _kb_search_url(),
        payload,
        token_env_keys=("AI_AGENT_KB_SEARCH_TOKEN", "AI_AGENT_PLATFORM_TOKEN"),
        trace_id=trace_id,
        run_id=run_id,
        session_code=session_code,
    )
    if not result.get("success"):
        return {**result, "items": []}

    data = result.get("data") if isinstance(result.get("data"), dict) else {}
    items = _normalize_items(data.get("items"))
    return {
        "success": True,
        "kbCode": data.get("kbCode") or payload.get("kbCode"),
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


def search_authorized_knowledge_base(
    run: dict[str, Any],
    kb_code: str,
    query: str,
    top_k: int = 5,
    scene: str = "ai-agent-tool",
) -> dict[str, Any]:
    """Search a knowledge base from the current run's frozen allowlist."""

    allowed_codes = {item["kbCode"] for item in available_knowledge_bases(run)}
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
    trace_id = _run_text(run, "traceId")
    payload = _build_request_payload(
        kb_code=normalized_code,
        query_text=query.strip(),
        top_k=max(1, int(top_k or 5)),
        trace_id=trace_id,
        scene=scene,
    )
    result = _request_kb_search(
        payload,
        trace_id=trace_id,
        run_id=_run_text(run, "runId"),
        session_code=_run_text(run, "sessionCode"),
    )
    result["tool"] = "knowledge_base_search_tool"
    result["kbCode"] = normalized_code
    result["topK"] = payload["topK"]
    if result.get("success"):
        result["summary"] = f"Returned {len(result.get('items', []))} knowledge hits."
    return result


def build_knowledge_base_search_tool(run: dict[str, Any], function_tool: Any) -> Any | None:
    """Create a per-run KB search tool limited to the Java-provided allowlist."""

    knowledge_bases = available_knowledge_bases(run)
    allowed_codes = {item["kbCode"] for item in knowledge_bases}
    if not allowed_codes:
        return None
    def search_knowledge_base(kb_code: str, query: str, top_k: int = 5) -> dict[str, Any]:
        """Search one knowledge base authorized for this Agent run by kb_code and query text."""
        return search_authorized_knowledge_base(run, kb_code, query, top_k)

    decorator = function_tool(
        name_override="knowledge_base_search_tool",
        description_override="Search a knowledge base explicitly listed in the current Agent run by kb_code.",
    )
    return decorator(search_knowledge_base)


@function_tool
def knowledge_base_search_tool(
    kb_code: str,
    query: str,
    top_k: int = 5,
    trace_id: str | None = None,
    scene: str | None = None,
) -> dict[str, Any]:
    """Search a knowledge base by kb_code and query text, then return matched items."""

    if not isinstance(kb_code, str) or not kb_code.strip():
        return {"tool": "knowledge_base_search_tool", "success": False, "error": "kb_code is required."}
    if not isinstance(query, str) or not query.strip():
        return {"tool": "knowledge_base_search_tool", "success": False, "error": "query is required."}

    payload = _build_request_payload(
        kb_code=kb_code.strip(),
        query_text=query.strip(),
        top_k=max(1, int(top_k or 5)),
        trace_id=trace_id.strip() if isinstance(trace_id, str) and trace_id.strip() else None,
        scene=scene.strip() if isinstance(scene, str) and scene.strip() else "ai-agent-tool",
    )
    result = _request_kb_search(
        payload,
        trace_id=payload["meta"].get("traceId"),
    )
    result["tool"] = "knowledge_base_search_tool"
    result["topK"] = payload["topK"]
    if result.get("success"):
        result["summary"] = f"Returned {len(result.get('items', []))} knowledge hits."
    return result


def _run_text(run: dict[str, Any], key: str) -> str | None:
    value = run.get(key) if isinstance(run, dict) else None
    return value.strip() if isinstance(value, str) and value.strip() else None
