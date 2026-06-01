# Codex DSL Governance

本文档定义 `.codex/DSL/` 的目录归属与变更准入规则，用于避免 DSL 与 Skills 混放、规则漂移和越界修改。

## 1. 目标

- 明确 DSL 与 Skills 的目录归属边界。
- 统一 DSL 变更入口与审批方式。
- 保证 DSL 变更可追踪、可回滚、可审计。

## 2. 目录归属

### 2.1 DSL 目录（制度层）

以下目录和文件属于 DSL，必须放在 `.codex/DSL/` 下：

- `rules/`：规范与约束（任务结构、状态机、门禁、裁决标准）
- `flows/`：执行/生成流程定义
- `template/`：任务、日志、文档、升级模板
- `updates/`：DSL 升级任务与升级日志
- `tasks/`：DSL 驱动任务清单
- `logs/`：执行日志
- `command/`：与 DSL 使用相关的命令说明
- `runner.md`：DSL 执行入口
- `README.adoc`：DSL 总说明

### 2.2 Skills 目录（执行层）

以下目录属于 Skills，必须放在 `.codex/skills/` 下：

- 各 skill 的 `SKILL.md`
- skill 内的 `checklists/`、`templates/`、`scripts/`、`examples/`

## 3. 变更准入规则

### 3.1 可直接修改（非结构性）

满足以下任一条件可直接修改：

- 文案修订、错别字修正、说明补充
- 非破坏性的示例与模板补充
- 不改变字段语义的排版或可读性优化

### 3.2 必须走 DSL 升级任务（结构性）

出现以下变更必须先在 `.codex/DSL/updates/README.adoc` 建立升级任务并记录：

- 新增/删除/重命名 DSL 核心目录或核心文件
- 修改任务状态机、流程门禁、完成/失败裁决标准
- 修改 Task/Sub Task 必填字段或结构块
- 修改跨 skill 的统一约束
- 引入与现行规则不兼容的行为

## 4. 禁止事项

- 禁止在 `.codex/` 根目录新增与 DSL 同名平级目录（如 `rules/`、`flows/`、`template/`）。
- 禁止 skill 文档私自定义状态机或绕过 DSL 门禁。
- 禁止只改 skill 不同步 DSL（当变更属于制度层时）。
- 禁止只改 DSL 不同步 skill（当变更影响执行方式时）。

## 5. 变更同步要求

当 DSL 发生结构性变更时，必须同步：

1. `.codex/DSL/README.adoc`（目录与入口说明）
2. 相关 `.codex/skills/*/SKILL.md`（DSL 对齐路径与执行约束）
3. `AGENT.md`（边界与对齐策略）
4. `.codex/DSL/updates/logs/*`（升级记录）

## 6. 提交前检查清单

- 所有 DSL 路径引用均指向 `.codex/DSL/`
- 无历史路径残留（如 `codex/...` 或 `.codex/rules/...`）
- 规则、流程、模板引用一致
- 升级任务与升级日志已补齐（如属于结构性变更）

## 7. 冲突处理

1. DSL（`.codex/DSL/rules/*` + `.codex/DSL/flows/*`）优先于 Skill。
2. 如需更改制度，先升级 DSL，再调整 Skill。
3. 如 Skill 间冲突，以用户当前任务目标和显式指定 skill 为准。
