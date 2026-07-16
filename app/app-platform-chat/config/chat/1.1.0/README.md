# Chat control plane 1.1.0

`create_table_ddl.sql` is the only authoritative DDL source for the 1.1.0 Agent-first control-plane tables.
Do not copy these `CREATE TABLE` statements into `data-workflow/src/main/resources/db/schema` or another
version directory.

The historical `config/chat/1.0.0` scripts and existing legacy tables are retained only as migration/rollback
sources. They are not application-startup schema resources and are not part of the Agent-first runtime path.

## Deployment order

1. Back up the existing Chat schema.
2. Run `create_table_ddl.sql` once. It creates only new versioned tables, the quarantined original Skill ZIP
   store, and an idempotent canonical `HOME_CHAT -> home-assistant@1` seed.
3. Run `update_table_ddl.sql`. It idempotently migrates activity attribution from `node_code` to
   `agent_code` and adds the Agent run snapshot fields to `ai_chat_round`.
4. Deploy the application and verify `GET /api/v1/ai/agent-entries/HOME_CHAT/available-agents`.
5. Optionally run `migrate_legacy_control_plane.sql` to copy legacy Agent-like Nodes, Skills, Tools and
   Workflows into **DRAFT** version rows.
6. Review every migrated definition through the validate/publish APIs before binding it to an entry.

## Compatibility and rollback

- Legacy control-plane tables are not dropped or altered. They are migration/rollback archive sources only;
  the Agent-first runtime does not read Node or Node-Skill execution tables.
- The history migration renames activity attribution and adds nullable Agent audit fields to rounds.
- Existing `ai_chat_skill`, `ai_chat_tool` and `ai_chat_workflow` rows remain the catalog identities;
  executable content is frozen in their new `*_version` tables.
- Form Skills keep a maximum 255-character preview in the legacy `ai_chat_skill.content` column. The complete
  canonical `SKILL.md` is stored in `ai_chat_skill_file`.
- To roll back application code, disable new `ai_chat_agent_entry_binding` rows. The legacy tables are intact.
  Do not drop the 1.1.0 tables until run-audit and version data retention requirements have been reviewed.

## Skill package safety limits

- ZIP only; compressed size at most 50 MiB.
- At most 500 files and 100 MiB uncompressed total.
- A single file is at most 25 MiB; compressed-to-expanded ratio is capped at 100:1.
- Absolute paths, `.`/`..` traversal segments, backslashes, NULs and case-insensitive duplicate paths are rejected.
- Packages are inspected in memory and are never extracted to a server filesystem.
- Exactly one top-level directory is required. It must contain exactly one case-insensitive `SKILL.md`; nested
  `SKILL.md` files are rejected.
- `SKILL.md` must start with safe YAML frontmatter. Its lowercase/hyphenated `name` must match the top-level
  directory and `description` must be 1-1024 characters.
- Inspect creates a single-use in-memory quarantine Draft ID with a 15-minute TTL. Import only accepts that
  Draft ID and stores the unchanged original ZIP separately from the immutable file index.
- Encrypted entries, links, executable bits/binaries, embedded credentials and private keys are rejected.

## Lifecycle

Status codes are shared by Agent, Skill, Tool and Workflow versions:

- `1`: DRAFT
- `2`: VALIDATED
- `3`: PUBLISHED
- `4`: ARCHIVED

Runtime lookup reads only status `3`. Supplying an explicit version never bypasses this rule. Publish resolves
unversioned Skill, Tool, Workflow and collaborator references to concrete published versions before freezing the
Agent manifest checksum.
