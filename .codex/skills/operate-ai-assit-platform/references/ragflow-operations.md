# RAGFlow operations

## Project integration

The current Chat service owns the RAGFlow Provider adapter. Runtime client configuration is stored under `chat.engine.kb.client.list`; the project currently expects exactly one RAGFlow client. The stable local business code is `ai_kb_store.kb_code`, while `ai_kb_store.provider_kb_id` is the real RAGFlow Dataset ID.

Knowledge-base records also snapshot authentication for compatibility during credential rotation. Never print or directly edit `setting_value` or `auth_json` during routine operations.

## Choose the control plane

- Use platform APIs/UI for normal Dataset creation/update/delete and document synchronization. They coordinate local metadata, sync status, task state, credentials, and RAGFlow.
- Use `ragflow_ops.py` for health, inventory, retrieval checks, isolated Provider diagnosis, and explicitly approved repair.
- Use RAGFlow Web UI for model-provider configuration, chunk inspection, parsing quality, and manual retrieval experiments.
- Use Docker Compose only when this team owns the target RAGFlow deployment.

Never mutate the same Dataset independently through both RAGFlow and platform APIs in one workflow without a reconciliation plan.

## API diagnostics

Provide `RAGFLOW_BASE_URL` and `RAGFLOW_API_KEY` through the environment, or resolve the enabled local-development setting internally:

```bash
python3 .codex/skills/operate-ai-assit-platform/scripts/ragflow_ops.py --repo . --project-db health
python3 .codex/skills/operate-ai-assit-platform/scripts/ragflow_ops.py --repo . --project-db datasets --page-size 30
python3 .codex/skills/operate-ai-assit-platform/scripts/ragflow_ops.py --repo . --project-db models
python3 .codex/skills/operate-ai-assit-platform/scripts/ragflow_ops.py --repo . --project-db documents --dataset-id <id>
python3 .codex/skills/operate-ai-assit-platform/scripts/ragflow_ops.py --repo . --project-db retrieve --dataset-id <id> --question '<question>'
```

The helper redacts sensitive response keys. Do not enable shell tracing while credentials are present.

## Destructive API actions

Before deleting a Dataset:

1. Resolve the RAGFlow instance and Dataset ID.
2. Query local `ai_kb_store` mapping and dependent documents/tasks without displaying credentials.
3. Verify backup/export requirements and the intended local-state transition.
4. Show the exact DELETE scope.
5. Require `--execute --confirm-id <same-id>`.

When `--project-db` uses `auto`, the helper also refuses destructive execution if Nacos was unreachable and configuration silently fell back to the repository datasource. Select `--db-config-source local` only after verifying that fallback instance is intentionally targeted.

Direct deletion is a recovery tool, not the normal business path. Prefer the platform delete API so local and Provider state converge.

## Local or Linux deployment

Set `RAGFLOW_HOME` to an owned RAGFlow checkout containing its Docker Compose file:

```bash
export RAGFLOW_HOME=/opt/ragflow
python3 .codex/skills/operate-ai-assit-platform/scripts/ragflow_ops.py local-status
python3 .codex/skills/operate-ai-assit-platform/scripts/ragflow_ops.py local-start
python3 .codex/skills/operate-ai-assit-platform/scripts/ragflow_ops.py local-logs --lines 200
python3 .codex/skills/operate-ai-assit-platform/scripts/ragflow_ops.py local-stop --execute
```

Starting is additive; stopping is disruptive and requires explicit execution. Confirm no other environment consumes the instance before stopping.

The repository's RAGFlow guide records v0.26.4 guidance: prefer Linux x86, at least 4 cores/16 GB RAM/50 GB disk, Docker 24+, Compose 2.26.1+, and `vm.max_map_count=262144`. Re-check the owned deployment's actual version and upstream release documentation before install or upgrade.

## Health and quality checks

- API authentication and Dataset listing work.
- Embedding model is configured and reachable.
- Document parse status completes and Chunk count is nonzero.
- Random Chunk samples preserve headings, fields, units, and table context.
- Retrieval returns the intended Dataset and relevant top results.
- Chat's configured network/proxy path reaches the same instance.
- Local `provider_kb_id`, sync status, document mappings, and Provider state agree.

For stuck parsing, inspect deployment resources and RAGFlow backend logs (commonly `/opt/ragflow/logs/backend.log` in the documented layout), then test with one small document. Do not bulk retry before identifying model, parser, disk, memory, or queue failure.

## Backup and upgrade

Inventory the exact RAGFlow version, Compose project, services, volumes, database, object storage, search/index storage, and configuration before backup. Stop writes or use a deployment-supported consistent snapshot. Verify restore in an isolated environment. Pin versions; do not run a floating upgrade on the only test instance.
