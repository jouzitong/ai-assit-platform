# Skills Directory

`skills/` contains both the runtime skill loader modules and optional local skill packages.

Each skill package should be a directory with a `SKILL.md` file. Optional folders:

- `scripts/`: deterministic helper scripts used by the skill.
- `references/`: domain rules, examples, and task-specific documentation.
- `assets/`: schemas, templates, images, or other read-only resources.
- `tests/`: package-local validation tests.

Skill packages are not enabled just because they exist here. A run must still assign
the skill through the frozen agent snapshot before `load_skill_resource` can read it.
The loader accepts the assigned `skill_key`; `name` is display-only metadata.
