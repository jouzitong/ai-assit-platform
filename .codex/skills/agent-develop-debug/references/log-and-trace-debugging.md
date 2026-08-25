# Log and trace debugging

## Use stable correlation keys

Gateway normalizes and returns `X-Trace-Id`; Chat also accepts legacy `traceId` in relevant paths. Prefer `X-Trace-Id` and carry it through the reproduction. Pair it with domain IDs because async work and reconnect/replay can outlive the original HTTP thread.

Useful pairs include:

- `traceId + request URI` at Gateway;
- `traceId + sessionId/runId` in Chat;
- `runId + tool/skill name` in the Agent worker;
- `runId + datasource/virtual model` in DB Engine;
- `artifact ID/hash + runId` in Chat/Render.

## Resolve the real log

Use the project controller for processes it started:

```bash
python3 .codex/skills/operate-ai-assit-platform/scripts/projectctl.py --repo . status all --json
```

The controller records logs under the user state directory. For IDE, container, systemd, Kubernetes, or externally started processes, provide the actual log file or use the environment's native read-only log command. Do not assume a historical `logs/` path.

## Search safely

The bundled inspector scans only a bounded recent tail and redacts common credential shapes:

```bash
python3 .codex/skills/agent-develop-debug/scripts/log_inspector.py \
  --repo . --service chat --trace-id <trace-id> --context 3 --lines 200

python3 .codex/skills/agent-develop-debug/scripts/log_inspector.py \
  --file <log-path> --errors --scan-lines 10000 --lines 300

python3 .codex/skills/agent-develop-debug/scripts/log_inspector.py \
  --file <log-path> --contains '<run-id>' --contains '<artifact-id>' --json
```

Repeated `--contains` values use AND matching. `--errors` matches common exception/failure/timeout markers. `--follow` streams only matching redacted lines until interrupted; do not use it when a bounded snapshot is sufficient.

Redaction is a safety net, not proof that arbitrary payloads are safe. Do not print full request/response bodies, `setting_value`, `auth_json`, SQL credentials, provider keys, cookies, JWTs, or personal data. Summarize only the fields required to prove the branch.

## Read logs in causal order

1. Find the first line for the correlation key.
2. Identify the first exception or rejected state, not the final wrapper.
3. Capture the responsible class/method or downstream target.
4. Match it to source and configuration.
5. Check whether retries, fallback, timeout, or SSE adaptation changed the visible error.
6. Verify whether the same stable ID reached the next service.

For duplicated stack traces, use the lowest layer that owns the failure as root evidence and treat upper layers as transport context.

## Distinguish common failure families

- Discovery/route failures: Gateway route, Nacos registration, context path.
- Authentication/authorization: Gateway guard/security and User permission result.
- Database failures: effective datasource, schema/table/row, transaction and constraint.
- Agent worker failures: Java process executor frame plus worker stderr and resolved runtime.
- Model/provider failures: actual base URL, selected model, credential availability, upstream status/body summary.
- SSE/reconnect failures: active runtime state, persisted event replay, stream timeout/cancellation.
- Render acceptance failures: artifact proof, hash lineage, content format, validation report.

Do not fix a downstream provider timeout by changing an unrelated Java process timeout unless the measured path proves that boundary.
