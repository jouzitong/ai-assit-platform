# Planning, development, and project diagnosis

## Build a verified baseline

1. Read `AGENTS.md` and the relevant `docs/dev-spec` topics.
2. Run CodeGraph status and query the target symbols, callers, callees, and impact.
3. Read the current root and target module POMs plus the real contracts and implementations.
4. Read relevant technical documentation and the newest matching plan; label stale documents rather than treating them as code.
5. Record current behavior, failing behavior, configuration path, and validation surface.

## Classify every statement

Use these labels in plans and handoff notes:

- **Implemented**: present in current source.
- **Validated**: supported by a successful build, test, API check, or runtime observation from this task.
- **Partial**: some required path exists, but the requested outcome is incomplete.
- **Design-only**: proposed under `docs/plans` and not claimed as code.
- **Blocked**: cannot proceed without named authority, credential, environment, or external-state change.

Never convert a design document, stale README, or passing compile into a stronger claim.

## Shape a project plan

Lead with conclusion, reasons, and landing approach. Include:

- goal and non-goals;
- current evidence and pain point;
- module ownership and contract boundaries;
- data/configuration changes and compatibility;
- API/event/state transitions;
- security, authorization, secrets, and audit behavior;
- failure, retry, cancellation, recovery, and rollback paths;
- macOS development phase and Linux/test/deployment extension point;
- phased implementation with explicit acceptance criteria;
- tests, builds, runtime verification, observability, and operations;
- migration and cleanup of legacy paths.

Place requested designs under the appropriate `docs/plans/YYYYMM/designs` path. Keep acceptance records separate when the repository already follows that pattern.

## Implement narrowly

- Modify the owning module first; avoid cross-module cleanup that is not required.
- Keep API modules contract-only and boot modules assembly-only.
- Keep external-provider protocol logic in provider adapters and domain routing in Chat core.
- Follow current frontend routing, request, component, theme, and application-runtime conventions.
- Preserve a stable extension point for explicitly deferred Linux or deployment work without adding speculative infrastructure.
- Add business-semantic comments only where ownership, request/response meaning, constraints, or failure behavior would otherwise be unclear.

## Diagnose end to end

Trace the observed path before proposing edits:

```text
UI -> Gateway -> owning service -> database/runtime setting -> Provider/Agent worker -> external upstream
```

Separate first-byte deadlines, local process timeouts, proxy behavior, authentication, discovery, database failures, RAGFlow parsing, and model-upstream errors. Inspect the earliest authoritative error and relevant IDs; do not blindly increase timeouts.

## Validate proportionally

Run focused tests first, then the owning module compile/build. Use the project-map validation matrix. For delivery or release work, validate the actual artifact, startup command, and accessible endpoint. When output is truncated, rerun quietly and verify the exit code and summaries. Run `codegraph sync` after changes and ensure `.codegraph/` stays uncommitted.
