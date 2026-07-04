# Open WebUI Phase A

This directory contains the local runtime assets for Phase A:

- `Open WebUI`
- `app-platform-ai-chat-python`

The `Open WebUI` source now lives in:

- [open-webui](/Users/zhouzhitong/workroom/items/okx-core/ai-assit-platform/open-webui:1)

## Start

```bash
docker compose -f tools/open-webui/compose.yaml up --build -d
```

## Stop

```bash
docker compose -f tools/open-webui/compose.yaml down
```

## URLs

- Open WebUI: `http://127.0.0.1:3000`
- Python chat service: `http://127.0.0.1:13103`

## Upstream dependency

The Python chat service proxies the existing Java `ai-engine`.

Default upstream:

- `http://host.docker.internal:13101/aiEngine`

If you need another endpoint, override `AI_ENGINE_BASE_URL` before startup.

## Source build

The compose file builds Open WebUI from the local `open-webui/` directory instead of pulling the published image.
