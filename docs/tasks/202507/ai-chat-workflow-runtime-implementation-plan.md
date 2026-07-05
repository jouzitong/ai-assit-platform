# AI Chat Workflow Runtime 一期实现方案

## 1. 目标

本次实现不直接替换现有 `DefaultWorkflowEngineImpl` 主链路，而是在 `app/app-platform-chat/core-workflow` 内补齐一套可编译的新版 workflow runtime 骨架，为后续将当前 `NodeResult + nextNodeId` 线性模型升级为 `NodeExecutionResult + TransitionDecision` 状态机模型做准备。

一期目标：

- 保留现有会话初始化、SSE、intent 分流、round 收口能力。
- 扩展 `WorkflowDefinition`，支持 `version`、`transitions`、`policy`。
- 增加 `TransitionProposal` / `TransitionDecision` / `TransitionResolver`。
- 增加 `NodeRuntime` / `ContextView` / `ScopedToolSet` / `NodeContract`。
- 不切换现有节点接口，不在本次直接改造 `IWorkflowNode`。

## 2. 当前现状

当前运行模型：

1. 上游 chat/query service 构造 `WorkflowContext`
2. `DefaultWorkflowEngineImpl.run(...)` 负责异步与同步执行入口
3. `executeWorkflow(...)` 中按 `WorkflowDefinition.startNodeId` 开始
4. 节点依赖 `WorkflowNodeConfig.nextNodeId` 或 `NodeResult.nextNodeId` 线性前进
5. 失败时由 engine 统一写 error、发 SSE、结束 round

当前问题：

- `WorkflowDefinition` 只能表达“线性下一个节点”，不能表达显式条件流转。
- 节点返回值过薄，无法携带结构化结论、artifact、proposal、评估信息。
- 节点默认可读全量 `WorkflowContext`，后续容易继续失控增长。
- 缺少统一的 transition resolver，节点建议和引擎最终决策没有被明确分离。

## 3. 本次新增骨架

### 3.1 workflow bean

- `WorkflowPolicy`
- `WorkflowTransitionEdge`
- `TransitionAction`
- `TransitionProposal`
- `TransitionDecision`
- `DecisionSource`
- `NodeExecutionResult`
- `NodeArtifactRef`
- `WorkflowExecutionState`
- `WorkflowNodeType`

### 3.2 runtime

- `NodeRuntime`
- `NodeContract`
- `ContextView`
- `ExecutionHistory`
- `RuntimePolicy`

### 3.3 tool

- `WorkflowTool`
- `ToolExecutionContext`
- `ScopedToolSet`

### 3.4 transition

- `TransitionResolver`
- `DefaultTransitionResolver`

## 4. 兼容策略

### 4.1 保留现有接口

以下接口本次不改签名：

- `IWorkflowEngine`
- `IWorkflowNode`
- `BaseWorkflowNode`
- 现有各 `*Node`

### 4.2 扩展旧模型而不是替换

`WorkflowDefinition` 与 `WorkflowNodeConfig` 继续保留旧构造：

- `new WorkflowDefinition(workflowCode, nodes, startNodeId)`
- `new WorkflowNodeConfig(nodeId, nextNodeId, skills)`
- `new WorkflowNodeConfig(nodeId, nextNodeId, skills, capabilities)`

现有代码不需要立即改动即可继续工作。

## 5. 下一步接线顺序

建议按以下顺序继续演进，而不是一次性切换所有节点：

1. 在 engine 中引入 `WorkflowExecutionState`
2. 在 `executeWorkflow(...)` 中记录 step/attempt
3. 新增 `NodeExecutionResultAdapter`，让旧 `NodeResult` 先适配成新结果模型
4. 在 engine 中接入 `TransitionResolver`
5. 将 `QueryPlanningNode` 先升级为 `NodeRuntime` 驱动
6. 将 `RenderNode` 拆为 `RenderPlanNode + RenderBuildNode`
7. 增加 `ResultEvaluateNode`，承接回退/补查/完成判定

## 6. 一期后的目标链路

推荐先落 6 节点版本：

1. `TaskContractNode`
2. `QueryPlanNode`
3. `QueryExecuteNode`
4. `ResultEvaluateNode`
5. `RenderPlanNode`
6. `RenderBuildNode`

目标流转：

- `TaskContractNode -> QueryPlanNode`
- `QueryPlanNode -> QueryExecuteNode`
- `QueryExecuteNode -> ResultEvaluateNode`
- `ResultEvaluateNode -> QueryPlanNode`
- `ResultEvaluateNode -> QueryExecuteNode`
- `ResultEvaluateNode -> RenderPlanNode`
- `RenderPlanNode -> RenderBuildNode`
- `RenderBuildNode -> COMPLETE`

## 7. 风险说明

- 当前 `DefaultTransitionResolver` 只实现了基础合法性校验和 policy 兜底，还没有表达式条件求值能力。
- 当前 `NodeRuntime` 只是结构骨架，尚未提供 builder/factory，也未绑定现有 `WorkflowContext`。
- 当前 `WorkflowTool` 只是统一接口，实际 registry/scope resolver 还未接入。
- 当前节点仍然基于旧接口执行，本次只是为后续平滑迁移铺底。
