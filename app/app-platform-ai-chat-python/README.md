# app-platform-ai-chat-python

Phase A chat gateway for the `Open WebUI + Python service` migration path.

## Role

This service exposes an OpenAI-compatible API surface for Open WebUI and adapts requests to the existing Java `ai-engine`.

Current scope:

- `GET /healthz`
- `GET /v1/models`
- `POST /v1/chat/completions`

Current non-goals:

- replicate the old `ai-chat` workflow/session schema
- replace knowledge-base management
- replace provider/model metadata management

## Runtime

- Python 3.11+
- FastAPI
- httpx

## Local run

```bash
cd app/app-platform-ai-chat-python
python3 -m venv .venv
. .venv/bin/activate
pip install -e .
uvicorn open_webui_chat_service.main:app --host 0.0.0.0 --port 13103
```

## Required environment variables

- `CHAT_SERVICE_API_KEY`: bearer token expected from Open WebUI
- `AI_ENGINE_BASE_URL`: base URL of the existing Java ai-engine, default `http://127.0.0.1:13101/aiEngine`

Optional:

- `CHAT_SERVICE_PORT`
- `CHAT_SERVICE_REQUIRE_API_KEY`
- `CHAT_SERVICE_DEFAULT_PROVIDER`
- `CHAT_SERVICE_REQUEST_TIMEOUT_MS`
- `CHAT_SERVICE_SCENE`

## Open WebUI integration

Open WebUI should point to:

- Base URL: `http://<host>:13103/v1`
- API key: the same value as `CHAT_SERVICE_API_KEY`

For a ready-to-run local setup, use [tools/open-webui/compose.yaml](/Users/zhouzhitong/workroom/items/okx-core/ai-assit-platform/tools/open-webui/compose.yaml:1).
