# Skill: simple_plan_core

## Purpose

针对小范围、低风险需求，快速输出最小可执行规划包。
仅在用户显式要求简易流程时使用。

## When To Use

仅在用户明确表达以下意图时使用：

- 简单流程
- 简易流程
- 快速处理
- 小改动
- 轻量任务

## Boundary

- 允许：简化需求确认、最小任务规划、最小测试规划
- 禁止：复杂原型建模、跨模块复杂责任编排、直接改代码

## Workflow (Minimal Planning Stages)

1. 简化需求确认（SIMPLE_INTAKE）
2. 最小开发规划（SIMPLE_DEV_PLAN）
3. 最小测试规划（SIMPLE_TEST_PLAN）
4. 规划就绪裁决（PLAN_READY_DECISION）

### Stage 1: SIMPLE_INTAKE

必产物：

- 目标
- 范围
- 排除项

### Stage 2: SIMPLE_DEV_PLAN

必产物：

- 最小开发任务清单
- 依赖说明（如无写“无”）

### Stage 3: SIMPLE_TEST_PLAN

必产物：

- 最小测试任务清单
- 核心验收点

### Stage 4: PLAN_READY_DECISION

必产物：

- 规划结论（PLAN_READY/BLOCKED）
- 升级建议（若需切换 standard_plan_core）

## Escalation Rule

出现以下任一条件必须升级到 `standard_plan_core`：

- 跨模块或跨服务
- 涉及 API/数据结构变更
- 存在外部依赖或高风险不确定项
- 用户补充需求后复杂度明显上升

## Output Contract (Mandatory)

每次执行必须输出：

- 简化需求说明
- 最小开发任务
- 最小测试任务
- 风险提示
- 规划结论（PLAN_READY/BLOCKED）

## DSL Alignment

- Task 定义：`.codex/DSL/rules/01-task-rules.adoc`
- 状态机门禁：`.codex/DSL/rules/04-state-and-gates-rules.adoc`
