---
name: operate-ai-assit-platform
description: Operate, plan, develop, test, and troubleshoot the ai-assit-platform repository. Use for project startup and shutdown, environment checks, service health and logs, local macOS development, Linux migration or deployment preparation, safe MySQL test-data reads and writes, RAGFlow Dataset or document operations, architecture and roadmap planning, module selection, builds, tests, and project-wide incident diagnosis.
---

# Operate AI Assist Platform

Treat the current checkout as the source of truth. Operate the development environment directly when the user asks, but keep data, infrastructure, and secret-handling guardrails intact.

## Establish context

1. Locate the repository root from `AGENTS.md` and `pom.xml`.
2. Read the root `AGENTS.md` completely before analysis or commands. Read every nearer `AGENTS.md` between the target path and root.
3. Run `git status --short` and preserve unrelated user changes.
4. Run `codegraph status`. Prefer `codegraph query`, `callers`, `callees`, and `impact` before broad scans; use exact file reads to verify configuration.
5. Run `python3 .codex/skills/operate-ai-assit-platform/scripts/project_doctor.py --repo .` before environment operations. Treat its facts as a live snapshot, not permanent documentation.
6. Do not trust the root README, old module names, cached build output, or `tools/service-manager/services.json` without checking current files.

## Route the task

- Read [project-map.md](references/project-map.md) for modules, ports, configuration sources, validation commands, and known drift.
- Read [planning-and-development.md](references/planning-and-development.md) for roadmap, design, implementation, refactoring, or project-wide diagnosis.
- Read [local-development.md](references/local-development.md) before starting, stopping, restarting, building, checking logs, or repairing a local environment.
- Read [database-operations.md](references/database-operations.md) before connecting to MySQL or inspecting, creating, changing, or deleting test data.
- Read [ragflow-operations.md](references/ragflow-operations.md) before RAGFlow health, Dataset, document, retrieval, parsing, restart, backup, or recovery work.
- Read [linux-runbook.md](references/linux-runbook.md) for Linux development, test-environment rollout, deployment design, or workstation-to-server migration.

Load only the references required for the current task.

## Operate services

Use the bundled controller instead of copying stale service-manager commands:

```bash
python3 .codex/skills/operate-ai-assit-platform/scripts/projectctl.py --repo . status all
python3 .codex/skills/operate-ai-assit-platform/scripts/projectctl.py --repo . plan-start core
python3 .codex/skills/operate-ai-assit-platform/scripts/projectctl.py --repo . start chat
python3 .codex/skills/operate-ai-assit-platform/scripts/projectctl.py --repo . logs chat --lines 120
python3 .codex/skills/operate-ai-assit-platform/scripts/projectctl.py --repo . stop chat
```

Start only the requested scope. For `core` or `all`, report the dependency order and wait for port readiness. Stop only processes recorded as started by this controller; do not kill an unknown process merely because it owns a project port. Use direct repository scripts only after checking them against [local-development.md](references/local-development.md).

## Operate data safely

Use `db_safe_query.py`; never put a database password on the command line or in output.

```bash
python3 .codex/skills/operate-ai-assit-platform/scripts/db_safe_query.py --repo . --list-targets
python3 .codex/skills/operate-ai-assit-platform/scripts/db_safe_query.py --repo . --service chat --connection-test
python3 .codex/skills/operate-ai-assit-platform/scripts/db_safe_query.py --repo . --service chat --sql 'SELECT COUNT(*) FROM conversation_session'
```

The default `--config-source auto` prefers the reachable Nacos `common.yaml`, then falls back to the repository's local shared configuration. Use `--config-source local` or `nacos` only when diagnosing precedence explicitly.

Default to read-only. Before a persistent `INSERT`, `UPDATE`, or `DELETE`:

1. Confirm the environment is local/development or an explicitly named test environment.
2. Resolve the real entity, table, key, audit fields, and relationships from current code and database metadata.
3. Select and show the exact affected rows or count.
4. Show the proposed SQL with secrets redacted and state the cleanup or rollback method.
5. Treat a direct user request to create or edit precisely scoped test data as write authorization. Ask before writing when the environment, database, row scope, or business effect is ambiguous.
6. Pass `--allow-write --execute --commit --confirm-host <exact-host> --confirm-database <exact-name> --reason <reason>` only after the checks above. Never persist a write when `auto` silently fell back from Nacos; select `--config-source local` explicitly if that fallback is genuinely intended.

Never use the data script for DDL, migrations, full-table updates/deletes, production writes, or cross-database writes. Prefer application APIs when writes must preserve domain events or external-system consistency.

## Operate RAGFlow safely

Use `ragflow_ops.py` with `RAGFLOW_BASE_URL` and `RAGFLOW_API_KEY`, or use `--project-db` to resolve the enabled development client without printing its credential.

```bash
python3 .codex/skills/operate-ai-assit-platform/scripts/ragflow_ops.py --repo . --project-db health
python3 .codex/skills/operate-ai-assit-platform/scripts/ragflow_ops.py --repo . --project-db datasets
python3 .codex/skills/operate-ai-assit-platform/scripts/ragflow_ops.py --repo . --project-db retrieve --dataset-id <id> --question '<question>'
```

Prefer platform APIs for business creates, updates, document synchronization, and deletes because local tables and RAGFlow must remain consistent. Use direct RAGFlow APIs for inspection, isolated diagnostics, and explicitly approved recovery. Before a destructive RAGFlow action, show the instance, Dataset/document IDs, local mappings, backup state, and exact request; require the script's matching confirmation flag.

## Plan and develop

State the outcome first, then explain why and how to land it. Separate:

- current implementation verified in code;
- behavior verified by tests or runtime checks;
- partial or blocked work;
- design-only or future Linux/deployment work.

Place requested designs under `docs/plans`, follow current module boundaries, and keep future deployment seams stable without implementing speculative infrastructure. Run the smallest relevant build/test set, rerun quiet commands when output is truncated, and distinguish baseline failures from regressions. Run `codegraph sync` after code or documentation edits that affect the indexed repository.

## Protect the environment

- Never print, persist, commit, or paste passwords, tokens, API keys, `setting_value`, or `auth_json`. Use environment variables, protected files, or current runtime configuration.
- Treat repository configuration containing credentials as sensitive even if already tracked.
- Do not infer production authority from a request about local development.
- Do not stop shared MySQL, Redis, Nacos, Docker, or RAGFlow services unless the requested scope clearly includes the disruption.
- Take or verify a backup before irreversible data or RAGFlow deletion.
- Preserve user changes and do not rewrite unrelated stale files while completing an operational request.

## Finish with evidence

Report which services, databases, or external systems were touched; commands or scripts used; ports and health results; builds/tests; changed files; remaining operational caveats; and whether each requested outcome is implemented, validated, partial, or design-only.
