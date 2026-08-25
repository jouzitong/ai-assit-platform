# AI Assist Platform architecture map

Use this map to choose the owning module and evidence source. Verify ports, configuration, runtime processes, and provider settings from the current checkout before acting.

## Runtime applications

| Service key | Owning module | Port | Context path | Primary responsibility | Main metadata database |
| --- | --- | ---: | --- | --- | --- |
| `gateway` | `app/app-gateway` | 9764 | `/` | Routing, trace propagation, request guard, authentication/authorization | none |
| `user` | `app/app-platform-user` | 8082 | `/user` | Users, permissions, shared runtime system settings | `ai_assist_user` |
| `chat` | `app/app-platform-chat` | 13103 | `/chat` | Conversation, models, KB, workflows, Agent runtime and providers | `ai_assist_chat_v3` |
| `db-engine` | `app/app-platform-db-engine` | 14102 | `/dbEngine` | Datasource metadata, virtual models, controlled data preview/query | `ai_assist_db_engine` |
| `file` | `app/app-platform-file` | 14103 | `/file` | File metadata and object-storage access | verify at runtime |
| `render` | `app/app-platform-render` | 14401 | `/render` | Render pages/components, content and versions | `ai_assist_render` |
| `ui` | `ai-conversation-ui` | 5173 | `/` | Vue 3/Vite client and render runtime | none |

The root Maven reactor contains Gateway, Render, Chat, DB Engine, Commons, File, and User. There is no standalone `app-platform-ai-engine`; current AI-engine behavior is inside Chat.

## Module boundaries

- Put cross-module contracts and DTOs in `api`.
- Put HTTP entry and request adaptation in `web` or the owning `core` module.
- Put persistence entities, mappers, conversions, and base data services in `data/*` or the service's data modules.
- Put external protocol adaptation in `providers/*`.
- Put startup, configuration assembly, and main classes in `boot` only.
- Keep the Vue frontend independent of backend module layout.

Chat is the widest domain and currently aggregates:

- `api`, `web`, `boot`, and `spi`;
- conversation, metadata, Agent-control, and knowledge-base data modules;
- AI engine, KB, AI Chat, Agent runtime, conversation runtime, and workflow modules;
- OpenAI, AI Agent, Bailian KB, and RAGFlow providers.

DB Engine separates contract, metadata, core behavior, executor SPI, MySQL/JDBC/MongoDB/HTTP executors, virtualization adapter, and boot assembly.

## Main request and Agent path

```text
ai-conversation-ui
  -> Gateway route and GatewayTraceFilter (`X-Trace-Id`)
  -> Chat HTTP controller / transport
  -> conversation execution and workflow modules
  -> AI engine route
  -> provider adapter
       -> direct model/KB provider, or
       -> Java AI Agent process executor
            -> Python or TypeScript Agent worker
            -> Chat tool/skill gateway
            -> DB Engine controlled data-preview API
            -> Render/File/platform APIs as needed
  -> persisted conversation/run/artifact state
  -> SSE response or replay
```

Start diagnosis at the user-visible route, then follow the actual branch. A Gateway 5xx is not automatically a Gateway defect; a Chat error can originate in database state, the Agent worker, a model endpoint, RAGFlow, DB Engine, Render, or File.

## Configuration hierarchy

1. Read each service's `bootstrap.yml` and application file.
2. Treat reachable Nacos `common.yaml` and service-specific data IDs as the effective local-development source.
3. Treat `app/config/application-common.yaml` as a fallback that can point to a different environment.
4. Resolve database-backed runtime settings through User's system-setting APIs or the guarded database client; never print `setting_value`.
5. Resolve provider/model/KB selection from current Chat records and enabled configuration; do not infer them from a request alone.

Protect datasource passwords, JWT values, provider credentials, `setting_value`, `auth_json`, cookies, tokens, and full personal data in every evidence surface.

## Evidence ownership

| Symptom | First owner to inspect | Next evidence |
| --- | --- | --- |
| Route missing, 401/403, trace absent | Gateway route/filter/security | User permission API and downstream registration |
| Chat request/SSE/reconnect failure | Chat `web` and conversation runtime | persisted events, workflow, provider frame |
| Agent provider error | Chat AI Agent provider executor | worker stderr/frame, resolved model/provider, upstream response |
| Wrong model or runtime setting | Chat metadata plus User system settings | enabled records and Nacos/runtime property |
| Wrong query/data preview | DB Engine API/virtualization adapter | virtual catalog, access policy, executor and source data |
| Missing or rejected render artifact | Chat workflow/acceptance and Render API | artifact lineage, validation report, Render persistence |
| File URL/upload failure | File API/core | object storage configuration and permissions |
| CPU, lock, live path, Bean mismatch | owning JVM | bounded Arthas after logs/config inspection |

## Startup and validation

Start the local stack only as required:

```text
Nacos -> User -> Chat -> DB Engine -> Render -> File -> Gateway -> UI
```

Use these default builds:

| Scope | Command |
| --- | --- |
| Gateway | `mvn -pl app/app-gateway -am clean compile -DskipTests` |
| User | `mvn -pl app/app-platform-user -am clean compile -DskipTests` |
| Chat | `mvn -pl app/app-platform-chat -am clean compile -DskipTests` |
| DB Engine | `mvn -pl app/app-platform-db-engine -am clean compile -DskipTests` |
| Render | `mvn -pl app/app-platform-render -am clean compile -DskipTests` |
| File | `mvn -pl app/app-platform-file -am clean compile -DskipTests` |
| Root backend | `mvn clean compile -DskipTests` |
| Frontend | `cd ai-conversation-ui && npm run build` |

The root Surefire configuration uses `testFailureIgnore`; inspect test summaries even when Maven ends successfully.

## Known drift to recheck

- Root `AGENTS.md` still contains some retired module names and an old frontend build path.
- Legacy service-manager metadata still contains the removed AI Engine, an old UI path, and no File service.
- Nacos helper paths contain workstation-specific values.
- CodeGraph 0.9.9 supports `query`, `callers`, `callees`, `impact`, and `affected`, but not the documented `context` command.

Use `project_doctor.py`, current POMs, and exact source reads rather than silently inheriting these stale facts.
