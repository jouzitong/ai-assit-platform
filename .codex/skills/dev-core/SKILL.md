# Skill: dev-core

## Purpose

基于规划阶段的 `PLAN_READY` 产物执行系统开发与系统测试，交付可裁决结果。
本 Skill 不负责口语化需求澄清与原型规划。

## When To Use

- 用户显式提到 `dev-core`
- 已存在规划产物（来自 `standard_plan_core` 或 `simple_plan_core`）
- 任务语义属于开发实现、联调验证、系统测试

## Input Contract (Mandatory)

进入 `dev-core` 前必须具备：

- 规划结论：`PLAN_READY`
- 可执行任务清单（Task/Sub Task + depends_on + owner）
- 需求验收标准
- 原型/接口说明（复杂需求时必须有）

若输入不满足，必须返回 `BLOCKED` 并指出缺失项。

## Boundary

- 允许：代码实现、文档同步更新、测试执行、交付裁决
- 禁止：重写需求目标、跳过依赖、绕过 DSL 门禁

## Workflow (Execution Stages)

1. 开发准备校验（DEV_INTAKE）
2. 系统开发（IMPLEMENTATION）
3. 系统测试（SYSTEM_TEST）
4. 交付裁决（DELIVERY_DECISION）

### Stage 1: 开发准备校验（DEV_INTAKE）

目标：确认开发输入完整且可执行。

必产物：

- 输入完整性清单
- 缺失项列表（如有）

门禁：

- 无 `PLAN_READY` 不得进入开发

### Stage 2: 系统开发（IMPLEMENTATION）

目标：按任务清单完成代码与必要文档更新。

执行要求：

- ANALYZE: 理解任务与约束
- PLAN: 输出路径级文件改动计划
- EXECUTE: 仅改已声明文件
- VALIDATE: 显式通过/不通过
- REPORT: 输出证据与风险

必产物：

- 计划文件清单
- 实际修改清单
- 开发执行报告
- 文档同步记录

### Stage 3: 系统测试（SYSTEM_TEST）

目标：验证实现满足验收标准。

必产物：

- 测试范围
- 测试结果与证据
- 失败根因与修复记录（如有）
- 残余风险

门禁：

- 关键路径失败不得进入交付完成

### Stage 4: 交付裁决（DELIVERY_DECISION）

目标：输出最终结论。

必产物：

- 交付结论（DONE/BLOCKED）
- 阻断原因（如 BLOCKED）
- 后续行动项（如有）

## Output Contract

每次执行必须输出：

- 开发变更摘要（含文件清单）
- 测试结果摘要（含证据）
- 文档更新摘要
- 最终交付结论（DONE/BLOCKED）

## DSL Alignment

- 状态机与门禁：`.codex/DSL/rules/04-state-and-gates-rules.adoc`
- 开发流程规范：`.codex/DSL/flows/standard-dev-flow.adoc`
- 门禁规则：`.codex/DSL/rules/06-standard-dev-flow-rules.adoc`
- Task 定义：`.codex/DSL/rules/01-task-rules.adoc`
- 日志规范：`.codex/DSL/rules/02-logs-rules.adoc`

