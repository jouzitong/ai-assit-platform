from __future__ import annotations

import json
import time
import uuid
from typing import Any, AsyncIterator

import httpx

from .config import settings
from .schemas import ChatCompletionRequest


class UpstreamError(RuntimeError):
    pass


def _unwrap_business_payload(payload: Any) -> Any:
    if not isinstance(payload, dict):
        return payload

    if "data" in payload:
        code = payload.get("code")
        if code not in (None, 0, "0", 200, "200"):
            message = payload.get("message") or payload.get("msg") or "ai-engine request failed"
            raise UpstreamError(str(message))
        return payload["data"]

    success = payload.get("success")
    if success is False:
        message = payload.get("message") or payload.get("msg") or "ai-engine request failed"
        raise UpstreamError(str(message))

    return payload


def _normalize_role(role: str) -> str:
    return role.strip().upper()


def _build_meta_ext(request: ChatCompletionRequest, forwarded_user: dict[str, str]) -> dict[str, Any]:
    ext: dict[str, Any] = {
        "source": "open-webui",
        "openWebUiUser": forwarded_user,
    }
    if request.user:
        ext["openAiUser"] = request.user
    if request.metadata:
        ext["openAiMetadata"] = request.metadata
    return ext


def build_ai_engine_request(
    request: ChatCompletionRequest,
    trace_id: str,
    forwarded_user: dict[str, str],
) -> dict[str, Any]:
    tool_definitions = []
    for tool in request.tools:
        tool_definitions.append(
            {
                "name": tool.function.name,
                "description": tool.function.description,
                "inputSchema": tool.function.parameters or {},
            }
        )

    payload: dict[str, Any] = {
        "model": request.model,
        "messages": [
            {
                "role": _normalize_role(message.role),
                "content": message.content or "",
                "name": message.name,
            }
            for message in request.messages
        ],
        "tools": tool_definitions,
        "options": {
            "temperature": request.temperature,
            "topP": request.top_p,
            "maxTokens": request.max_tokens,
            "timeoutMs": settings.request_timeout_ms,
        },
        "meta": {
            "traceId": trace_id,
            "scene": settings.scene,
            "ext": {
                "adapter": "app-platform-ai-chat-python",
            },
        },
        "ext": _build_meta_ext(request, forwarded_user),
    }
    if settings.default_provider:
        payload["provider"] = settings.default_provider
    return payload


def build_model_list_payload(items: list[dict[str, Any]]) -> dict[str, Any]:
    return {
        "object": "list",
        "data": [
            {
                "id": item.get("apiModel") or item.get("modelCode") or "unknown-model",
                "object": "model",
                "created": 0,
                "owned_by": item.get("providerName") or item.get("providerCode") or "ai-engine",
            }
            for item in items
            if item.get("apiModel") or item.get("modelCode")
        ],
    }


def _to_openai_tool_call(output: dict[str, Any]) -> dict[str, Any] | None:
    tool_call = output.get("toolCall") or {}
    if not tool_call:
        return None
    return {
        "id": tool_call.get("id") or f"call_{uuid.uuid4().hex[:12]}",
        "type": "function",
        "function": {
            "name": tool_call.get("name") or "unknown_tool",
            "arguments": json.dumps(tool_call.get("arguments") or {}, ensure_ascii=False),
        },
    }


def build_openai_completion_response(
    request: ChatCompletionRequest,
    upstream_payload: dict[str, Any],
) -> dict[str, Any]:
    outputs = upstream_payload.get("outputs") or []
    text_fragments: list[str] = []
    tool_calls: list[dict[str, Any]] = []

    for output in outputs:
        output_type = str(output.get("type") or "").upper()
        if output_type == "TOOL_CALL":
            tool_call = _to_openai_tool_call(output)
            if tool_call:
                tool_calls.append(tool_call)
            continue
        if output_type == "JSON":
            text_fragments.append(json.dumps(output.get("json") or {}, ensure_ascii=False))
            continue
        text_fragments.append(output.get("text") or "")

    finish_reason = "tool_calls" if tool_calls else "stop"
    content = "".join(text_fragments)

    message: dict[str, Any] = {
        "role": "assistant",
        "content": content,
    }
    if tool_calls:
        message["tool_calls"] = tool_calls

    usage = upstream_payload.get("usage") or {}
    request_id = upstream_payload.get("requestId") or f"chatcmpl-{uuid.uuid4().hex}"

    return {
        "id": request_id,
        "object": "chat.completion",
        "created": int(time.time()),
        "model": upstream_payload.get("model") or request.model,
        "choices": [
            {
                "index": 0,
                "message": message,
                "finish_reason": finish_reason,
            }
        ],
        "usage": {
            "prompt_tokens": usage.get("inputTokens", 0) or 0,
            "completion_tokens": usage.get("outputTokens", 0) or 0,
            "total_tokens": usage.get("totalTokens", 0) or 0,
        },
    }


class AiEngineClient:
    def __init__(self) -> None:
        timeout = settings.request_timeout_ms / 1000
        self._client = httpx.AsyncClient(timeout=timeout)

    async def close(self) -> None:
        await self._client.aclose()

    async def list_models(self) -> dict[str, Any]:
        response = await self._client.get(f"{settings.ai_engine_base_url}/api/v1/ai/models/enable")
        response.raise_for_status()
        payload = _unwrap_business_payload(response.json())
        if not isinstance(payload, list):
            raise UpstreamError("ai-engine returned invalid model list payload")
        return build_model_list_payload(payload)

    async def chat_completion(
        self,
        request: ChatCompletionRequest,
        trace_id: str,
        forwarded_user: dict[str, str],
    ) -> dict[str, Any]:
        payload = build_ai_engine_request(request, trace_id, forwarded_user)
        response = await self._client.post(
            f"{settings.ai_engine_base_url}/api/v1/ai/execution/chat",
            json=payload,
        )
        response.raise_for_status()
        upstream_payload = _unwrap_business_payload(response.json())
        if not isinstance(upstream_payload, dict):
            raise UpstreamError("ai-engine returned invalid chat payload")
        return build_openai_completion_response(request, upstream_payload)

    async def stream_chat_completion(
        self,
        request: ChatCompletionRequest,
        trace_id: str,
        forwarded_user: dict[str, str],
    ) -> AsyncIterator[str]:
        payload = build_ai_engine_request(request, trace_id, forwarded_user)
        completion_id = f"chatcmpl-{uuid.uuid4().hex}"
        created_at = int(time.time())

        initial_chunk = {
            "id": completion_id,
            "object": "chat.completion.chunk",
            "created": created_at,
            "model": request.model,
            "choices": [
                {
                    "index": 0,
                    "delta": {"role": "assistant"},
                    "finish_reason": None,
                }
            ],
        }
        yield f"data: {json.dumps(initial_chunk, ensure_ascii=False)}\n\n"

        async with self._client.stream(
            "POST",
            f"{settings.ai_engine_base_url}/api/v1/ai/execution/chat/stream",
            json=payload,
            headers={"Accept": "text/event-stream"},
        ) as response:
            response.raise_for_status()
            async for line in response.aiter_lines():
                if not line or not line.startswith("data:"):
                    continue
                raw_data = line[5:].strip()
                if not raw_data:
                    continue
                chunk_payload = json.loads(raw_data)
                delta = chunk_payload.get("delta") or ""
                if not delta:
                    continue
                openai_chunk = {
                    "id": completion_id,
                    "object": "chat.completion.chunk",
                    "created": created_at,
                    "model": request.model,
                    "choices": [
                        {
                            "index": 0,
                            "delta": {"content": delta},
                            "finish_reason": None,
                        }
                    ],
                }
                yield f"data: {json.dumps(openai_chunk, ensure_ascii=False)}\n\n"

        final_chunk = {
            "id": completion_id,
            "object": "chat.completion.chunk",
            "created": created_at,
            "model": request.model,
            "choices": [
                {
                    "index": 0,
                    "delta": {},
                    "finish_reason": "stop",
                }
            ],
        }
        yield f"data: {json.dumps(final_chunk, ensure_ascii=False)}\n\n"
        yield "data: [DONE]\n\n"
