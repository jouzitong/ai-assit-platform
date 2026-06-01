# Skill: code-review

## Purpose

针对变更执行工程化审查：优先发现 bug、回归风险、边界缺陷和测试缺口。

## When To Use

- 用户显式提到 `code-review` 或 `review`
- 开发任务完成后做收尾质量检查

## Workflow

1. 读取变更上下文（diff/关联模块）。
2. 先列高风险问题，再列中低风险问题。
3. 标注文件位置和影响范围。
4. 给出可执行修复建议。

## Output Contract

- Findings（按严重度排序）
- Open Questions / Assumptions
- Residual Risks

## Assets

- Checklist: `checklists/review-focus.md`
- Template: `templates/review-report.md`
