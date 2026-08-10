# Linux development, test, and deployment runway

## Migration principle

Keep today's macOS workflow operational, but remove workstation assumptions at the boundary: paths, credentials, service supervision, network addresses, persistent storage, and logs. Do not copy a personal home directory or tracked development secret onto Linux.

## Stage 1: Linux development host

Provision and verify:

- Linux x86_64 unless every required image is verified on arm64;
- JDK 17 and compatible Maven;
- Node supported by the current Vite version and npm lockfile;
- Python 3.11+ for the Agent worker;
- MySQL client and access to isolated development databases;
- Docker and Compose versions sufficient for owned infrastructure;
- Nacos, optional Redis, MinIO when File is exercised, and RAGFlow when locally owned;
- enough memory/disk for Maven, Node, Docker, RAGFlow, logs, and backups.

Create a non-root service user. Put source, runtime state, logs, configuration, and backups in separate directories with explicit ownership.

## Externalize current assumptions

- Replace the hard-coded Nacos path with `NACOS_HOME` or a managed Nacos endpoint.
- Inject datasource credentials, JWT secrets, provider keys, and object-storage credentials through protected environment files or a secret manager.
- Keep startup configuration in Spring-compatible configuration/Nacos and runtime business settings in `SystemSettingInternalApi`; do not merge the two.
- Configure Chat proxy variables explicitly; do not assume macOS `scutil` exists.
- Set the frontend gateway URL for the target environment.
- Set `RAGFLOW_HOME`, `RAGFLOW_BASE_URL`, and credential source explicitly.
- Use absolute deployment paths only in environment-specific unit files, never shared source scripts.

## Stage 2: repeatable test environment

Build immutable artifacts in CI or a controlled build host. Record Git commit, Maven revision, frontend artifact hash, Python dependency lock/fingerprint, and configuration version. Provision isolated test databases and RAGFlow Datasets; seed deterministic fixtures through APIs or reviewed SQL.

Add service supervision with systemd or container orchestration. Each unit must define:

- working directory and unprivileged user;
- environment/secret file;
- exact artifact and profile;
- restart policy and startup timeout;
- graceful stop;
- stdout/stderr or structured log destination;
- dependency/readiness behavior;
- resource limits.

Do not model Nacos/MySQL/RAGFlow readiness as only process order; poll the actual service boundary.

## Stage 3: deployment design

Before production-like deployment, decide and document:

- host versus container packaging for each Java service and UI;
- gateway/TLS/DNS and allowed external origins;
- database ownership, migrations, backup, restore, and retention;
- Nacos namespace/group and configuration promotion;
- RAGFlow topology, volumes, backup consistency, model-provider access, and upgrade strategy;
- MinIO/object-storage ownership and lifecycle;
- Redis mode, persistence, and failure behavior if cluster coordination is enabled;
- logs, metrics, traces, alerting, health/readiness endpoints, and incident identifiers;
- least-privilege service accounts, firewall rules, secret rotation, and audit access;
- rolling/blue-green strategy and rollback artifact.

## Acceptance gates

Require evidence for:

1. clean host bootstrap from documented prerequisites;
2. reproducible backend and frontend builds;
3. isolated configuration with no secret in Git or process arguments;
4. controlled start, readiness, stop, restart, and log retrieval;
5. database fixture seed and cleanup;
6. RAGFlow upload/parse/retrieval plus local-state consistency;
7. gateway-to-service and UI-to-gateway end-to-end calls;
8. backup and restore rehearsal;
9. deployment rollback;
10. a directly accessible acceptance address for the intended audience.

Treat this runbook as a compatibility target. Implement Linux automation only when the target host, ownership, and packaging decision are in scope.
