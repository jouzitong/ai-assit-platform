# Skill: standard_plan_core

## Purpose

将口语化/不完整需求转化为可执行“规划包”，并给出 `PLAN_READY/BLOCKED` 裁决。
本 Skill 是默认规划入口。

## When To Use

- 默认使用本 Skill（未显式要求简易流程时）
- 需求跨模块、跨团队、跨服务
- 涉及 API/数据结构/依赖关系变更
- 需求歧义较多或风险较高

## Boundary

- 允许：需求澄清、标准需求文档、原型设计、任务拆分、责任划分、风险评估
- 禁止：直接修改业务代码、执行发布动作

## Workflow (Planning Stages)

1. 口语化需求澄清（INTAKE）
2. 标准需求定义（REQUIREMENT_SPEC）
3. 原型设计（PROTOTYPE_DESIGN）
4. 任务规划与责任划分（PLANNING_OWNERSHIP）
5. 规划就绪裁决（PLAN_READY_DECISION）

### Stage 1: INTAKE

目标：将口语化输入转化为可裁决需求描述。

必产物：

- 背景与目标
- 范围与排除项
- 依赖与约束
- 歧义与待确认清单

门禁：

- 关键歧义未裁决不得进入下一阶段

### Stage 2: REQUIREMENT_SPEC

目标：形成标准需求文档。

必产物：

- 需求文档（目标、范围、约束、验收标准）

门禁：

- 验收标准不可验证则 BLOCKED

### Stage 3: PROTOTYPE_DESIGN

目标：将需求转换为实现导向原型。

必产物：

- 业务流程说明
- API 草案
- 数据与状态变化说明
- 异常场景说明

门禁：

- 原型与需求不一致不得进入下一阶段

### Stage 4: PLANNING_OWNERSHIP

目标：形成可执行任务清单和责任划分。

必产物：

- Task/Sub Task 清单
- depends_on 依赖关系
- owner 责任划分
- 风险与前置条件清单

门禁：

- owner 缺失、依赖不清、循环依赖时 BLOCKED

### Stage 5: PLAN_READY_DECISION

目标：给出规划是否可进入开发执行。

必产物：

- 规划结论（PLAN_READY/BLOCKED）
- 缺失项与补齐建议（如 BLOCKED）

## Output Contract (Mandatory)

每次执行必须输出：

- 标准需求文档摘要
- 原型设计摘要
- 任务与责任划分摘要
- 风险与前置条件摘要
- 规划结论（PLAN_READY/BLOCKED）

## DSL Alignment

- 开发流程规范：`.codex/DSL/flows/standard-dev-flow.adoc`
- 门禁规则：`.codex/DSL/rules/06-standard-dev-flow-rules.adoc`
- Task 定义：`.codex/DSL/rules/01-task-rules.adoc`
