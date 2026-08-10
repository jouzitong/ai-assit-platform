# Safe database and test-data operations

## Database map

| Service | Database |
| --- | --- |
| User and system settings | `ai_assist_user` |
| Chat, conversations, models, agents, and KB metadata | `ai_assist_chat_v3` |
| DB Engine metadata and virtual models | `ai_assist_db_engine` |
| Render pages and components | `ai_assist_render` |

Resolve the datasource endpoint and credentials from the current startup configuration or injected environment. In local development, prefer reachable Nacos `common.yaml`; fall back to `app/config/application-common.yaml` only when Nacos is unavailable or `--config-source local` is explicit. Never repeat credentials in conversation, logs, plans, SQL comments, or committed files.

## Choose API or direct SQL

Prefer a project API when the operation must also execute validation, password hashing, audit events, external Provider synchronization, snapshots, versioning, or cache invalidation. Use direct SQL for inspection, isolated development fixtures, deterministic cleanup, or recovery only after confirming those side effects are unnecessary or reproduced.

Examples that should normally use application APIs:

- users, credentials, and roles;
- AI clients/models with encrypted or masked configuration;
- RAGFlow-backed KB creation, update, document synchronization, and deletion;
- Render pages/components that maintain snapshots or versions;
- Agent, Skill, Tool, and Workflow publication/version state.

## Inspect before writing

1. Use CodeGraph to locate the current entity, mapper, service, controller, DTO, and callers.
2. Run `SHOW CREATE TABLE` or `DESCRIBE` and inspect relevant foreign keys/indexes.
3. Read base-entity fields from the current Athena dependency or generated table, not memory.
4. Select the exact candidate rows and dependent rows.
5. Define a stable fixture namespace such as a request-specific code prefix; do not collide with normal data.
6. Define cleanup SQL before insertion.

## Use the guarded client

Read-only examples:

```bash
python3 .codex/skills/operate-ai-assit-platform/scripts/db_safe_query.py --repo . --service user --connection-test
python3 .codex/skills/operate-ai-assit-platform/scripts/db_safe_query.py --repo . --service chat --sql 'SHOW TABLES'
python3 .codex/skills/operate-ai-assit-platform/scripts/db_safe_query.py --repo . --service chat --sql 'SELECT id, session_code FROM conversation_session ORDER BY id DESC LIMIT 10'
```

Prepare a write without executing it:

```bash
python3 .codex/skills/operate-ai-assit-platform/scripts/db_safe_query.py \
  --repo . --service chat --allow-write \
  --sql "INSERT INTO <verified_table>(...) VALUES (...)" \
  --confirm-host 127.0.0.1 --confirm-database ai_assist_chat_v3 \
  --reason 'fixture for <test>'
```

Persist only after reviewing the preview and affected-row query:

```bash
python3 .codex/skills/operate-ai-assit-platform/scripts/db_safe_query.py \
  --repo . --service chat --allow-write --execute --commit \
  --sql "UPDATE <verified_table> SET ... WHERE <scoped predicate>" \
  --confirm-host 127.0.0.1 --confirm-database ai_assist_chat_v3 \
  --reason 'fixture for <test>'
```

The tool permits one `INSERT`, `UPDATE`, or `DELETE` statement; `UPDATE` and `DELETE` require `WHERE`. It rejects DDL and cross-database statements. It requires exact host and database confirmation, and refuses writes when `auto` silently falls back from Nacos to repository configuration. Without `--commit`, an executed DML statement is rolled back.

The client uses a working host `mysql`/`mariadb` executable when available, including Homebrew Cellar binaries when the PATH-level link is broken. Set `AI_ASSIST_MYSQL_CLIENT` to select an explicit binary. It then falls back to the `my-mysql` Docker container, placing credentials in a temporary mode-0600 client file inside that container and deleting it immediately after the command. Override the container name with `AI_ASSIST_MYSQL_CLIENT_CONTAINER` when needed.

## Test-data rules

- Use deterministic business codes and a visible test prefix when the schema permits.
- Insert the smallest complete relationship graph.
- Reuse valid enum codes from current Java enums, not guessed labels.
- Preserve tenant, owner, enabled, soft-delete, version, and audit semantics.
- Never copy production personal data or real credentials into fixtures.
- Record fixture identifiers and cleanup criteria in the task result.
- Verify through the same API/page the test targets, then clean up unless the user asked to retain the fixture.

## Sensitive runtime settings

`system_settings.setting_value` can contain provider URLs and credentials. Query only `setting_key`, `enabled`, type, and a masked or length summary during diagnosis. The RAGFlow helper can consume `chat.engine.kb.client.list` internally with `--project-db` without printing it. Never select or display `ai_kb_store.auth_json`.

## Backup and rollback

For multi-row or irreversible work, create a scoped export or database backup before mutation. Store it outside Git with restrictive permissions. Validate that the backup is nonempty and restorable. A transaction is not a backup; MySQL DDL and external Provider calls may not roll back with SQL.
