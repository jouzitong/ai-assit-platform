# Local development and service operations

## Preflight

1. Run `project_doctor.py` and fix errors before launching a full stack.
2. Verify JDK 17, Maven, Node/npm, Python 3.11+, MySQL reachability, and Nacos reachability. Treat reachable Nacos `common.yaml` as the effective development datasource source; use the repository shared YAML only as fallback.
3. Initialize the Python Agent runtime when its dependencies changed:

   ```bash
   app/app-platform-chat/scripts/init-ai-agent-python.sh --python python3
   ```

4. Confirm required provider and model runtime settings through the platform without printing credentials.
5. Run `projectctl.py status all`; do not start duplicates on occupied ports.

The Chat runtime defaults to local in-memory coordination. Redis is required when `AI_CHAT_RUNTIME_MODE=redis`, but not for the default local mode.

## Preferred controller

List and inspect without mutation:

```bash
python3 .codex/skills/operate-ai-assit-platform/scripts/projectctl.py --repo . list
python3 .codex/skills/operate-ai-assit-platform/scripts/projectctl.py --repo . status all
python3 .codex/skills/operate-ai-assit-platform/scripts/projectctl.py --repo . plan-start core
```

Start scopes:

- One service: `start chat`, `start db-engine`, and so on.
- `backend`: Nacos, User, Chat, DB Engine, Render, File, Gateway.
- `core` or `all`: backend followed by the UI.
- `manager` and `open-webui` are optional and excluded from `core`.

Default order:

```text
Nacos -> User -> Chat -> DB Engine -> Render -> File -> Gateway -> UI
```

The controller records PIDs and logs under the user's state/cache directories. It refuses to stop a port owner that it did not start. Use `logs <service>` for captured output.

## Direct fallback commands

Use these only when repairing or validating the controller:

```bash
./tools/service-manager/scripts/start-nacos-local.sh
./tools/service-manager/scripts/start-user-local.sh
./tools/service-manager/scripts/start-ai-chat-local.sh
./tools/service-manager/scripts/start-db-engine-local.sh
./tools/service-manager/scripts/start-render-local.sh
./tools/service-manager/scripts/start-gateway-local.sh
```

The legacy UI and AI Engine entries are stale. Start the current UI directly with:

```bash
cd ai-conversation-ui
npm run dev -- --host 127.0.0.1 --port 5173 --strictPort
```

Start File through its boot module after installing reactor dependencies. The bundled controller contains the corresponding command.

## Readiness and diagnostics

Use TCP readiness for initial process supervision, then validate an actual low-cost endpoint. Do not treat an open port alone as complete application health.

- Nacos: port 8848 and service registration/config visibility.
- Gateway: port 9764, route resolution, and an allowed health or low-cost API.
- User: port 8082 with `/user` context.
- Chat: port 13103 with `/chat` context; also verify its Agent worker can reach the configured model endpoint.
- DB Engine: port 14102 with `/dbEngine` context and datasource connectivity.
- File: port 14103 with `/file` context and MinIO only when file operations are needed.
- Render: port 14401 with `/render` context.
- UI: port 5173 and gateway requests.

Inspect the first real exception in service logs. A gateway error can originate from discovery, downstream service, database, Agent worker, model upstream, or RAGFlow; trace the actual request path before editing timeouts or providers.

## macOS-specific behavior

- Current Nacos scripts reference `/Users/zhouzhitong/tools/cloud/nacos-2.3.1`; verify it exists or set up a portable replacement before use elsewhere.
- Chat's startup script deliberately clears shell proxies, then maps the macOS system HTTP/HTTPS proxy into JVM properties for external providers. Set `AI_CHAT_USE_SYSTEM_PROXY=false` where providers are directly reachable.
- Other local service scripts clear proxy inheritance to keep loopback calls off the system proxy.
- Docker Desktop runs Linux containers through a VM; RAGFlow's memory and `vm.max_map_count` requirements apply to that VM.
- The workstation is arm64 while official RAGFlow deployment guidance primarily targets x86; prefer a Linux x86 host for stable RAGFlow development.
- The PATH-level `mysql` executable currently has a broken Homebrew dynamic-library link. `db_safe_query.py` automatically discovers a working Cellar binary, then falls back to the running `my-mysql` container client without exposing credentials; repair the PATH-level installation separately when convenient.

## Stop and recovery

Stop in reverse dependency order. Restart the smallest affected service. Before stopping Nacos, MySQL, Redis, Docker, RAGFlow, or a full stack, identify other local consumers.

If a managed process cannot stop gracefully, preserve the tail of its log, send `SIGTERM`, wait, and use `SIGKILL` only for the recorded process group. Never use broad `pkill java`, `killall`, or port-based killing without proving ownership.
