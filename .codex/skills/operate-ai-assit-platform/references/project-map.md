# Current project map

Use this file as a navigation aid. Verify every drift-prone value from the current checkout and `project_doctor.py` before acting.

## Runtime applications

| Key | Module | Port | Context path | Main database | Build scope |
| --- | --- | ---: | --- | --- | --- |
| `gateway` | `app/app-gateway` | 9764 | `/` | none | `app/app-gateway` |
| `user` | `app/app-platform-user` | 8082 | `/user` | `ai_assist_user` | `app/app-platform-user` |
| `chat` | `app/app-platform-chat` | 13103 | `/chat` | `ai_assist_chat_v3` | `app/app-platform-chat` |
| `db-engine` | `app/app-platform-db-engine` | 14102 | `/dbEngine` | `ai_assist_db_engine` | `app/app-platform-db-engine` |
| `file` | `app/app-platform-file` | 14103 | `/file` | verify at runtime | `app/app-platform-file` |
| `render` | `app/app-platform-render` | 14401 | `/render` | `ai_assist_render` | `app/app-platform-render` |
| `ui` | `ai-conversation-ui` | 5173 | `/` | none | `npm run build` |

The root Maven reactor currently contains Gateway, Render, Chat, DB Engine, Commons, File, and User. The former standalone `app-platform-ai-engine` is not a current reactor module; AI engine behavior now lives inside Chat modules.

## Main boundaries

- `api`: internal contracts and DTOs only.
- `web` or `core`: controllers, orchestration, and business implementation.
- `data/*`: entities, mappers, persistence services, and conversions.
- `providers/*`: external provider adaptation such as OpenAI, AI Agent, Bailian KB, and RAGFlow.
- `boot`: startup and assembly only.
- `ai-conversation-ui`: independent Vue 3 + Vite frontend.
- `docs/dev-spec`: authoritative development conventions.
- `docs/plans`: requested designs, plans, acceptance records, and staged work.

Read `docs/dev-spec/README.md`, then every topic required by the change. Configuration, logging, exceptions, knowledge clients, and AI client/model configuration are independent mandatory topics when involved.

## Configuration sources

- Shared startup configuration: `app/config/application.yaml`, `application-common.yaml`, and `application-dev.yaml`.
- Service startup configuration: each boot module's `src/main/resources/application.yml` or Chat's `app/app-platform-chat/config/application.yml`.
- Nacos bootstrap: each service's `bootstrap.yml`; local address is currently `127.0.0.1:8848`.
- Effective development datasource configuration currently comes from Nacos `common.yaml`; repository `application-common.yaml` is a fallback and points elsewhere.
- Runtime system settings: User service `system_settings`, accessed in application code through `SystemSettingInternalApi`.
- RAGFlow client setting: `chat.engine.kb.client.list`.
- DB Engine default knowledge base code: `dbEngine.kb.kbId`; its value is `ai_kb_store.kb_code`, not the row ID.
- Frontend gateway: `ai-conversation-ui/src/config/runtime.ts`, default `http://127.0.0.1:9764`.

Do not log or quote shared datasource credentials, JWT configuration, provider credentials, knowledge-client `setting_value`, or KB `auth_json`.

## Known repository drift

As of the 2026-08-09 checkout:

- `tools/service-manager/services.json` still lists the removed standalone AI Engine.
- The same file and `start-ui-local.sh` still use the old `ai-assit-ui` directory; the current frontend is `ai-conversation-ui`.
- The service manager omits the File service.
- Nacos scripts contain a workstation-specific absolute path.
- The root README and parts of `AGENTS.md` retain older module names and build examples.
- The service manager is useful as historical/local scaffolding, not an unquestioned source of truth.

Use the bundled `projectctl.py` for current operations and report drift rather than silently pretending the stale entry worked.

## Validation matrix

| Scope | Default validation |
| --- | --- |
| Root backend | `mvn clean compile -DskipTests` |
| Chat | `mvn -pl app/app-platform-chat -am clean compile -DskipTests` plus focused tests |
| DB Engine | `mvn -pl app/app-platform-db-engine -am clean compile -DskipTests` plus focused tests |
| Render | `mvn -pl app/app-platform-render -am clean compile -DskipTests` plus focused tests |
| User | `mvn -pl app/app-platform-user -am clean compile -DskipTests` plus focused tests |
| Gateway | `mvn -pl app/app-gateway -am clean compile -DskipTests` plus route/security checks |
| File | `mvn -pl app/app-platform-file -am clean compile -DskipTests` |
| Frontend | `cd ai-conversation-ui && npm run build` |
| Python Agent | initialize its venv, then run focused Python tests/import checks |
| Project Skill | run `quick_validate.py`, script tests, and a fresh-agent forward test |

Maven currently configures `testFailureIgnore`; a successful reactor can coexist with recorded test failures. Inspect summaries and distinguish baseline failures from introduced regressions.

## Current workstation snapshot

The 2026-08-10 inspection found macOS arm64, JDK 17, Maven 3.8.8, Node 24, npm 11, Python 3.12, `uv`, Docker 20.10, and Docker Compose 2.13. Local MySQL and Redis containers and Nacos were running. The PATH-level MySQL CLI had a broken dynamic-library link, while its Homebrew Cellar binary and the existing `my-mysql` container client both worked; the bundled data tool discovers these fallbacks. Nacos resolved the effective datasource to local MySQL, and all four named databases existed. No local RAGFlow container was found; the enabled project setting resolved to an external development RAGFlow instance and passed the Dataset health request. Re-run the doctor because all of these facts can change.
