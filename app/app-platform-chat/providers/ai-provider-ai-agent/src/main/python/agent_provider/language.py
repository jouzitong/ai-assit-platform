from __future__ import annotations

from typing import Any


DEFAULT_RESPONSE_LANGUAGE = "zh-CN"


def resolve_response_language(payload: dict[str, Any] | None) -> str:
    """Resolve the language for user-facing Agent output."""

    source = payload if isinstance(payload, dict) else {}
    run = _mapping(source.get("run"))
    context = _mapping(run.get("context"))
    client_context = _mapping(context.get("clientContext"))
    options = _mapping(source.get("options"))
    candidates = (
        context.get("responseLanguage"),
        context.get("language"),
        client_context.get("responseLanguage"),
        client_context.get("locale"),
        source.get("responseLanguage"),
        options.get("responseLanguage"),
        options.get("language"),
    )
    for value in candidates:
        normalized = normalize_response_language(value)
        if normalized is not None:
            return normalized
    return DEFAULT_RESPONSE_LANGUAGE


def normalize_response_language(value: Any) -> str | None:
    if not isinstance(value, str):
        return None
    normalized = value.strip().lower().replace("_", "-")
    if not normalized:
        return None
    if normalized in {"auto", "user", "user-locale"}:
        return "auto"
    if normalized.startswith("zh"):
        return "zh-CN"
    if normalized.startswith("en"):
        return "en-US"
    return None


def response_language_instruction(language: str) -> str:
    if language == "en-US":
        return (
            "Language requirement: use English for user-facing natural-language output by default. "
            "Keep code, JSON field names, tool names, Agent codes, and model names unchanged. "
            "Follow an explicit request for another language."
        )
    if language == "auto":
        return (
            "Language requirement: respond in the language used by the user. "
            "Keep code, JSON field names, tool names, Agent codes, and model names unchanged."
        )
    return (
        "语言要求：面向用户的自然语言输出默认使用简体中文；代码、JSON 字段名、工具名、"
        "Agent 编码和模型名等技术标识符保持原样。只有用户明确要求其他语言时才切换。"
    )


def _mapping(value: Any) -> dict[str, Any]:
    return value if isinstance(value, dict) else {}
