from __future__ import annotations

import uuid
from contextlib import asynccontextmanager

import httpx
from fastapi import Depends, FastAPI, Header, HTTPException, Request, status
from fastapi.responses import JSONResponse, StreamingResponse

from .config import settings
from .schemas import ChatCompletionRequest
from .upstream import AiEngineClient, UpstreamError


@asynccontextmanager
async def lifespan(_: FastAPI):
    client = AiEngineClient()
    app.state.ai_engine_client = client
    try:
        yield
    finally:
        await client.close()


app = FastAPI(
    title="app-platform-ai-chat-python",
    version="0.1.0",
    lifespan=lifespan,
)


def get_ai_engine_client(request: Request) -> AiEngineClient:
    return request.app.state.ai_engine_client


def verify_api_key(authorization: str | None = Header(default=None)) -> None:
    if not settings.require_api_key:
        return
    expected = settings.api_key.strip()
    actual = (authorization or "").strip()
    if actual == f"Bearer {expected}":
        return
    raise HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="invalid api key",
    )


def build_forwarded_user(
    x_openwebui_user_id: str | None = Header(default=None),
    x_openwebui_user_email: str | None = Header(default=None),
    x_openwebui_user_name: str | None = Header(default=None),
    x_openwebui_user_role: str | None = Header(default=None),
) -> dict[str, str]:
    forwarded = {
        "id": (x_openwebui_user_id or "").strip(),
        "email": (x_openwebui_user_email or "").strip(),
        "name": (x_openwebui_user_name or "").strip(),
        "role": (x_openwebui_user_role or "").strip(),
    }
    return {key: value for key, value in forwarded.items() if value}


def _request_id() -> str:
    return uuid.uuid4().hex


@app.get("/healthz")
async def healthz() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/v1/models", dependencies=[Depends(verify_api_key)])
async def list_models(client: AiEngineClient = Depends(get_ai_engine_client)):
    try:
        return await client.list_models()
    except httpx.HTTPError as exc:
        raise HTTPException(status_code=502, detail=f"ai-engine unavailable: {exc}") from exc
    except UpstreamError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc


@app.post("/v1/chat/completions", dependencies=[Depends(verify_api_key)])
async def chat_completions(
    request: ChatCompletionRequest,
    client: AiEngineClient = Depends(get_ai_engine_client),
    forwarded_user: dict[str, str] = Depends(build_forwarded_user),
):
    trace_id = _request_id()
    try:
        if request.stream:
            return StreamingResponse(
                client.stream_chat_completion(request, trace_id, forwarded_user),
                media_type="text/event-stream",
                headers={
                    "Cache-Control": "no-cache",
                    "Connection": "keep-alive",
                    "X-Accel-Buffering": "no",
                },
            )
        payload = await client.chat_completion(request, trace_id, forwarded_user)
        return JSONResponse(payload)
    except httpx.HTTPStatusError as exc:
        raise HTTPException(status_code=502, detail=f"ai-engine http error: {exc.response.status_code}") from exc
    except httpx.HTTPError as exc:
        raise HTTPException(status_code=502, detail=f"ai-engine unavailable: {exc}") from exc
    except UpstreamError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
