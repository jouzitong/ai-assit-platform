import asyncio
import json
import os
import sys
import uuid
from typing import Any

from agents import Agent, Runner

from tools import (
    render_json_dry_run_tool,
    render_json_preview_tool,
    render_json_validate_tool,
)


def _build_transcript(messages: list[dict[str, Any]]) -> tuple[str, str]:
    system_parts: list[str] = []
    conversation_parts: list[str] = []
    for item in messages or []:
        if not isinstance(item, dict):
            continue
        role = str(item.get("role") or "USER").upper()
        content = item.get("content")
        if content is None:
            continue
        text = str(content).strip()
        if not text:
            continue
        if role == "SYSTEM":
            system_parts.append(text)
        else:
            conversation_parts.append(f"{role}: {text}")
    return "\n\n".join(system_parts).strip(), "\n".join(conversation_parts).strip()


def _append_json_instruction(instructions: str, response_format: dict[str, Any] | None) -> str:
    if not isinstance(response_format, dict):
        return instructions
    format_type = response_format.get("type")
    schema = response_format.get("schema")
    if str(format_type or "").upper() != "JSON_SCHEMA" or not schema:
        return instructions
    extra = (
        "Return strictly valid JSON that matches this schema. "
        "Do not wrap it in markdown.\n"
        f"{json.dumps(schema, ensure_ascii=False)}"
    )
    if instructions:
        return instructions + "\n\n" + extra
    return extra


async def _run(payload: dict[str, Any]) -> dict[str, Any]:
    messages = payload.get("messages") or []
    instructions, transcript = _build_transcript(messages)
    instructions = _append_json_instruction(instructions, payload.get("responseFormat"))
    model = payload.get("model") or os.getenv("OPENAI_MODEL") or "gpt-5.5"
    agent = Agent(
        name="AI Agent Provider",
        instructions=instructions or (
            "Answer the user's request clearly and concisely. "
            "When the task involves render JSON, prefer using the render_json_validate_tool, "
            "render_json_dry_run_tool, and render_json_preview_tool before giving a final answer."
        ),
        model=model,
        tools=[
            render_json_validate_tool,
            render_json_dry_run_tool,
            render_json_preview_tool,
        ],
    )
    result = await Runner.run(agent, transcript or "USER: ")
    final_output = result.final_output
    text_output = final_output if isinstance(final_output, str) else json.dumps(final_output, ensure_ascii=False)
    outputs = [{"type": "TEXT", "text": text_output, "json": {}}]
    provider_meta = {
        "last_agent": getattr(getattr(result, "last_agent", None), "name", None),
        "raw_type": type(final_output).__name__,
    }
    return {
        "requestId": str(uuid.uuid4()),
        "model": model,
        "finishReason": "STOP",
        "outputs": outputs,
        "usage": {
            "inputTokens": 0,
            "outputTokens": 0,
            "totalTokens": 0,
        },
        "providerMeta": provider_meta,
    }


def main() -> None:
    payload = json.load(sys.stdin)
    result = asyncio.run(_run(payload))
    json.dump(result, sys.stdout, ensure_ascii=False)


if __name__ == "__main__":
    main()
