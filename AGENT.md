# AGENT.md

This repository is indexed with CodeGraph.

Prefer CodeGraph queries before broad file scans:
- `codegraph query <symbol>`
- `codegraph callers <symbol>`
- `codegraph callees <symbol>`
- `codegraph impact <symbol>`
- `codegraph context "<task>"`

Maintenance:
- Run `codegraph init -i` once per repo.
- Run `codegraph sync` after edits.
- Run `codegraph index` for a full refresh when needed.

Do not commit `.codegraph/`.
