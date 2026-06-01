# Skill: release

## Purpose

组织发布前后的关键动作：发布检查、发布说明、回滚预案。

## When To Use

- 用户显式提到 `release` 或 `发版`
- 需要产出发布清单或发布说明

## Workflow

1. 确认发布范围（commit/module/change list）。
2. 执行发布前检查（测试、配置、迁移、兼容性）。
3. 产出 release note。
4. 明确回滚路径与触发条件。

## Output Contract

- 发布范围摘要
- 发布前检查结果
- Release Note
- 回滚预案

## Assets

- Checklist: `checklists/pre-release.md`
- Checklist: `checklists/rollback.md`
- Template: `templates/release-note.md`
