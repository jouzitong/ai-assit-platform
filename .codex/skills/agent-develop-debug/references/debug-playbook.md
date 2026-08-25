# Development and debugging playbook

## 1. Define the incident

Capture a compact incident frame before changing anything:

- environment and current revision;
- expected and observed behavior;
- entry route or action;
- service and owning module;
- timestamp, `X-Trace-Id`, session/run/task/artifact ID;
- smallest reproducible input with secrets removed;
- acceptance check that proves recovery.

If the user supplied only an exception, trace its actual provider/service path and explain the cause before proposing a change.

## 2. Establish current code behavior

1. Query the route/controller or named symbol with CodeGraph.
2. Inspect callers, callees, impact, contracts, and nearest tests.
3. Read the target module POM and required `docs/dev-spec` topics.
4. Follow configuration from bootstrap through Nacos/runtime settings.
5. Write down the expected state transition and failure boundary.

Do not treat plans, README text, generated output, or old module names as runtime evidence.

## 3. Reproduce narrowly

Prefer the lowest-cost entry that still exercises the failure:

- focused unit/application test;
- one service endpoint rather than the full UI;
- direct Gateway route when auth/trace behavior matters;
- controlled Agent request with a stable run ID;
- read-only database query for state mismatch;
- provider health/model request only after resolving the actual enabled provider.

Do not start the full stack when one focused test or service proves the branch.

## 4. Correlate evidence

Follow the request in timestamp order:

```text
client -> Gateway trace/auth -> owning service -> persistence/config
       -> Agent worker/provider -> downstream service/external upstream
```

Preserve the first authoritative error. Later wrappers commonly replace a specific database/provider error with a generic 5xx.

Use these identifiers where available:

- `X-Trace-Id` / `traceId` for cross-service HTTP;
- conversation/session/message/round identifiers for Chat;
- `runId`, workflow/task ID, artifact ID/hash for Agent workflows;
- datasource, virtual model, KB code, provider Dataset ID for data/KB work.

## 5. Choose the next diagnostic plane

| Remaining question | Diagnostic plane |
| --- | --- |
| Which branch or owner should run? | CodeGraph and source |
| What failed first for this request? | Logs by trace/stable ID |
| Is persisted state/configuration wrong? | Guarded read-only SQL or platform API |
| Is the effective Spring value/Bean different from source? | Arthas Spring property/Bean check |
| Which live method is slow or called? | Arthas `trace`/`stack` |
| Is the JVM CPU/thread state abnormal? | Arthas overview/thread |
| Did the external system reject the call? | Provider request/response metadata and official health API |

Escalate one plane at a time. Do not use database mutation to test a theory that can be verified with a read, and do not use `watch` when a trace has enough evidence.

## 6. State a falsifiable cause

Use this form:

```text
Because <verified condition>, <specific code/runtime branch> produces <observed result>.
If the cause is correct, <focused change or controlled input> will make <same reproduction> pass,
while <named invariant> remains unchanged.
```

Separate confirmed cause, contributing condition, symptom wrapper, and unrelated baseline failures.

## 7. Implement in the owner

- Change the smallest owning module.
- Preserve contracts, audit fields, security context, trace propagation, and provider boundaries.
- Add a regression test at the lowest layer that expresses the defect.
- Add operational logging only when the incident lacked a stable identifier or outcome.
- Do not commit debug-only endpoints, credentials, ad hoc production switches, or permanent broad log levels.

## 8. Verify the same path

1. Run the focused regression test.
2. Run affected tests or `codegraph affected` where useful.
3. Compile/build the owning module.
4. Repeat the original reproduction with the same class of input.
5. Recheck logs, persisted state, or runtime method evidence as applicable.
6. Clean temporary test data and confirm Arthas `reset;stop` completion.
7. Run `codegraph sync` and `git diff --check`.

Report compile-only, test-validated, runtime-validated, and unverified outcomes separately.
