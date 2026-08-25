---
name: agent-develop-debug
description: Develop, diagnose, and fix ai-assit-platform end to end. Use when Codex must trace a defect across Vue, Gateway, Java services, the Chat Agent worker, MySQL, Nacos/runtime settings, RAGFlow or other providers; reproduce an exception or slow request; correlate logs and trace IDs; inspect or create scoped development test data; attach Arthas to a project JVM for thread, class, method, Spring Bean/configuration, trace, stack, or bounded watch diagnostics; implement the smallest owning-module change; and prove the fix with focused tests, builds, and runtime evidence.
---

# Agent Develop Debug

Drive a development incident from observed symptom to verified fix. Treat source, logs, database state, runtime state, and external dependencies as separate evidence planes; do not guess across them.

## Establish the baseline

1. Locate the repository root from `AGENTS.md` and `pom.xml`.
2. Read the root `AGENTS.md` completely, then every nearer `AGENTS.md` for the target path.
3. Run `git status --short`; preserve unrelated changes.
4. Run `codegraph status`. Prefer `codegraph query`, `callers`, `callees`, and `impact`; this installed CodeGraph version does not provide `codegraph context`.
5. Read [project-architecture.md](references/project-architecture.md) and verify drift-prone facts from current POMs/configuration.
6. Run the project doctor before service, database, or runtime work:

```bash
python3 .codex/skills/operate-ai-assit-platform/scripts/project_doctor.py --repo .
```

7. Record the failing behavior, expected behavior, environment, service, request or trace ID, reproducible input, and acceptance check.

## Follow the evidence ladder

Read [debug-playbook.md](references/debug-playbook.md), then use this order unless evidence justifies skipping a layer:

1. Trace the owning route, contract, implementation, callers, configuration source, and tests in code.
2. Reproduce through the narrowest real entry point and preserve the first authoritative error.
3. Correlate Gateway and service logs by `X-Trace-Id`, session/run/task ID, and timestamp.
4. Inspect database rows and runtime settings without exposing credentials.
5. Use Arthas only when the remaining uncertainty is inside the live JVM: actual class, method path, thread, Bean, configuration value, or timing.
6. Form one falsifiable root-cause statement before editing code.
7. Modify the owning module only; rerun the same reproduction and focused regression checks.

Do not start with database writes, broad log dumps, or bytecode enhancement when code/config/log evidence can answer the question.

## Inspect services and logs

Read [log-and-trace-debugging.md](references/log-and-trace-debugging.md) and the existing [local development runbook](../operate-ai-assit-platform/references/local-development.md).

```bash
python3 .codex/skills/operate-ai-assit-platform/scripts/projectctl.py --repo . status all
python3 .codex/skills/agent-develop-debug/scripts/log_inspector.py --repo . --service chat --errors
python3 .codex/skills/agent-develop-debug/scripts/log_inspector.py --repo . --service chat --trace-id <trace-id> --context 3
python3 .codex/skills/agent-develop-debug/scripts/log_inspector.py --file <explicit-log> --contains '<stable-id>'
```

Use `projectctl.py logs` only when raw output is specifically required; prefer `log_inspector.py` because it bounds scans and redacts common credential shapes. If the controller does not own the process, locate its real log explicitly instead of assuming an old path.

## Inspect and operate development data

Read the existing [database operations runbook](../operate-ai-assit-platform/references/database-operations.md) before every database action.

```bash
python3 .codex/skills/operate-ai-assit-platform/scripts/db_safe_query.py --repo . --list-targets
python3 .codex/skills/operate-ai-assit-platform/scripts/db_safe_query.py --repo . --service chat --connection-test
python3 .codex/skills/operate-ai-assit-platform/scripts/db_safe_query.py --repo . --service chat --sql '<bounded read-only SQL>'
```

Default to read-only. Resolve the actual entity, table, keys, audit fields, relationships, and effective datasource first. Prefer application APIs when writes must preserve validation, encryption, events, snapshots, caches, or provider synchronization. For authorized test-data DML, preview exact affected rows and cleanup SQL, then use every required `--allow-write --execute --commit --confirm-host --confirm-database --reason` guard. Never run DDL, unbounded DML, cross-database writes, or production writes.

## Diagnose the live JVM with Arthas

Read [arthas-runtime-debugging.md](references/arthas-runtime-debugging.md) before attaching.

```bash
python3 .codex/skills/agent-develop-debug/scripts/arthas_safe.py --repo . list
python3 .codex/skills/agent-develop-debug/scripts/arthas_safe.py --repo . --environment local --dry-run overview --service chat
python3 .codex/skills/agent-develop-debug/scripts/arthas_safe.py --repo . --environment local trace --service chat \
  --class-name <fully.qualified.Class> --method-name <method> --count 3 --min-cost-ms 100
python3 .codex/skills/agent-develop-debug/scripts/arthas_safe.py --repo . --environment local spring-property --service chat \
  --property-name server.port
```

Use exact project service and class/method targets. The wrapper discovers the JVM from its service port plus exact main-class/module markers, uses a loopback-only ephemeral telnet port, disables HTTP, bounds batch time, appends `reset;stop`, and rejects class mutation, dumps, arbitrary OGNL, environment dumps, and unbounded observation. Every attach/dry-run action requires an explicitly named `--environment`; production additionally requires `--confirm-production`. `watch` value output requires `--allow-value-output`; do not observe credentials, tokens, full request bodies, or personal data.

## Implement the fix

1. Read `docs/dev-spec/README.md` and every mandatory topic for the change.
2. Keep `api` contract-only, `data` persistence-focused, `providers` external-adapter-focused, `web/core` responsible for orchestration, and `boot` assembly-only.
3. Preserve external contracts and current caller semantics unless the request explicitly changes them.
4. Add logs that carry stable identifiers and useful outcomes without duplicating exception stacks or exposing secrets.
5. Add or update the narrowest regression test that would have caught the defect.
6. Avoid unrelated cleanup and speculative infrastructure.

## Verify and finish

Run focused tests first, then the owning module build from [project-architecture.md](references/project-architecture.md). Reproduce the original failure path after the change. If runtime verification needs data, record fixture identifiers and cleanup status. Run:

```bash
python3 -m py_compile .codex/skills/agent-develop-debug/scripts/*.py
uv run --with pyyaml python "${CODEX_HOME:-$HOME/.codex}/skills/.system/skill-creator/scripts/quick_validate.py" \
  .codex/skills/agent-develop-debug
codegraph sync
git diff --check
```

Report:

- root cause and evidence chain;
- files and owning modules changed;
- services, databases, external systems, and JVMs touched;
- any DML, Arthas enhancement, cleanup, or rollback performed;
- focused tests, builds, reproduction, and runtime results;
- remaining caveats;
- status for each requested outcome: implemented, validated, partial, design-only, or blocked.

Never claim a passing compile proves runtime behavior, or that a design/reference proves implementation.
