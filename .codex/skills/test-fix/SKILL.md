# Skill: test-fix

## Purpose

快速定位并修复测试失败，恢复可重复通过的测试基线。

## When To Use

- 用户显式提到 `test-fix`
- CI/本地测试失败需要排障

## Workflow

1. 复现失败（记录命令与日志）。
2. 缩小故障范围（模块、测试集、最小用例）。
3. 修复根因并避免过度修复。
4. 回归关键测试并输出结果。

## Output Contract

- 失败根因
- 修复策略
- 验证结果
- 未覆盖风险

## Assets

- Script: `scripts/run-tests.sh`
- Script: `scripts/collect-failures.sh`
- Template: `templates/test-fix-report.md`
