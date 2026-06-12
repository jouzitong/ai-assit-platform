# app-platform-ai-chat WorkflowContext 与流程节点边界优化方案

## 1. 背景

当前 `app/app-platform-ai-chat/core` 下的工作流已经具备基础可运行能力，主链路为：

`Chat-Message -> Query-Planning -> Knowledge-Search -> Sql-Generate -> Sql-Validate -> Sql-Execute -> Render`

但从当前实现看，`WorkflowContext` 已经同时承担了以下几类职责：

- 请求输入与执行入口载体
- 会话/轮次/历史消息共享状态
- 各节点阶段性产物承载
- SSE 事件发布能力
- 错误状态兜底通道
- 扩展 `data` 容器

这导致两个明显问题：

1. 顶层字段不断吸收节点专属结果，新增或调整节点时容易继续修改 `WorkflowContext` 结构。
2. 节点共享状态、节点私有产物、运行时控制信息混在一起，边界不够清楚。

本方案的目标不是把上下文做成完全抽象的万能容器，而是把“稳定共享骨架”和“阶段性节点结果”拆开：前者尽量长期稳定，后者允许随流程演进扩展，但不再反复改 `WorkflowContext` 顶层属性。

## 2. 当前问题梳理

### 2.1 `WorkflowContext` 结构层面

当前 `WorkflowContext` 顶层同时包含：

- 稳定共享状态：`command`、`workflowCode`、`session`、`sessionMessages`、`sessionArtifacts`、`currentUserMessage`、`round`
- 模型调用态：`engineRequest`、`engineResponse`
- 具体节点产物：`analysisResult`、`knowledgeBaseId`、`knowledgeResult`、`generatedSql`、`validatedSql`、`sqlValidationError`、`sqlExecutionStatus`、`sqlExecutionResult`、`renderedAnswer`
- 运行时能力：`emitter`
- 扩展数据：`data`

这里的核心问题不是字段多，而是字段分层不清：

- `analysisResult`、`generatedSql`、`validatedSql`、`renderedAnswer` 都是明确的阶段产物，不应长期占据顶层。
- `engineRequest`、`engineResponse` 被多个节点复用，但没有区分是 planning、render 还是 sql-generate 的模型请求。
- 顶层字段和 `data` 并存，形成“双通道”写入，后续维护者很难判断某个状态应该放哪。
- 错误信息依赖 `context.put("error", ...)`，缺少明确的运行时失败模型。

### 2.2 节点边界层面

当前节点职责已具备雏形，但仍存在边界耦合：

- `ChatMessageNode` 同时做会话解析/创建、历史加载、轮次创建、用户消息落库，这个边界基本合理。
- `QueryPlanningNode` 除了生成规划，还会更新会话标题、记录模型请求/响应、生成规划摘要；其中“规划结果”和“会话命名建议”混在一个节点里。
- `KnowledgeSearchNode` 既负责知识库检索，也顺带拼接“当前可用模型概览”，说明“知识补全”内部来源还没分层。
- `SqlValidateNode` 既做校验，又维护重试计数，又控制回跳到 `Sql-Generate`，属于“校验 + 重试策略”耦合。
- `RenderNode` 同时负责最终回答生成、消息落库、轮次收尾，属于“回答生成 + 输出提交”耦合。

这些问题单看都不致命，但会让后续新增节点时变成：

- 加一个阶段，就补一个 `WorkflowContext` 字段
- 调一个节点职责，就顺带改 2 到 3 个已有节点
- 运行态、持久化态、阶段产物态继续交叉污染

## 3. 优化目标

本次优化建议围绕两个核心目标展开。

### 3.1 `WorkflowContext` 只保留稳定共享骨架

`WorkflowContext` 应该表达“一次工作流实例始终需要的公共上下文”，而不是表达“当前流程里所有节点可能产生的所有结果”。

### 3.2 节点产物改为阶段化承载

节点产物应按“阶段/stage”存放，节点之间通过明确的阶段输入输出契约协作，而不是继续把结果摊在 `WorkflowContext` 顶层。

## 4. 目标结构方案

建议把 `WorkflowContext` 收敛为 5 个稳定区块。

### 4.1 Request 区

承载本次请求的不可变输入。

- `request.command`
- `request.workflowCode`
- `request.traceId`
- `request.scene`

建议：

- `AiChatQueryCommand` 仍可保留，但要把常用访问项抽到更稳定的 request 视图，避免节点对 `command.ext` 的散读。

### 4.2 Conversation 区

承载本次会话共享的业务主线状态。

- `conversation.session`
- `conversation.round`
- `conversation.history.messages`
- `conversation.history.artifacts`
- `conversation.currentUserMessage`

这部分是整个流程最稳定的共享业务上下文，新增节点通常不会改变这一层的结构。

### 4.3 Runtime 区

承载运行期控制能力与执行结果。

- `runtime.status`
- `runtime.error`
- `runtime.currentNodeId`
- `runtime.eventPublisher`
- `runtime.attributes`

建议：

- 将 `emitter` 抽象为 `WorkflowEventPublisher`，`SseEmitter` 只作为其一种实现。
- `runtime.attributes` 只保存运行时控制信息，如重试计数、调试标记、兼容迁移标记，不再承担业务阶段结果存储。

### 4.4 Stage 区

承载阶段性节点产物，这是未来稳定扩展的关键。

- `stageStates["planning"]`
- `stageStates["knowledge"]`
- `stageStates["sql_generate"]`
- `stageStates["sql_validate"]`
- `stageStates["sql_execute"]`
- `stageStates["render"]`

每个 stage 都有自己的类型对象，而不是直接散落成顶层字符串。

建议定义统一模型：

```java
public class WorkflowContext {
    private WorkflowRequestContext request;
    private WorkflowConversationContext conversation;
    private WorkflowRuntimeContext runtime;
    private Map<String, WorkflowStageState> stageStates;
}
```

其中 `WorkflowStageState` 只作为统一父类型，实际由各阶段 DTO 承载：

- `PlanningStageState`
- `KnowledgeStageState`
- `SqlDraftStageState`
- `SqlValidationStageState`
- `SqlExecutionStageState`
- `RenderStageState`

这样未来如果新增：

- `IntentDetectNode`
- `ClarificationNode`
- `MetricResolveNode`
- `ChartBuildNode`

只需要新增对应 `StageState`，不需要继续膨胀 `WorkflowContext` 顶层。

### 4.5 Output 区

用于表达最终可对外返回的结果，而不是中间态。

- `output.finalAnswer`
- `output.finalStatus`
- `output.responseMeta`

这比把 `renderedAnswer` 直接作为顶层字段更清晰，因为它天然代表“流程最终产出”。

## 5. 上下文设计原则

### 5.1 稳定字段只保留跨多数节点共享的骨架

进入顶层稳定结构的判断标准应是：

- 是否被多个阶段长期共享
- 是否属于一次流程实例的主业务身份
- 是否与具体节点实现无关

如果答案是否定的，就不应该再进 `WorkflowContext` 顶层。

### 5.2 节点结果不再直接写顶层字段

例如：

- `analysisResult` -> `planningStage.analysisSummary`
- `knowledgeBaseId` -> `knowledgeStage.knowledgeBaseId`
- `knowledgeResult` -> `knowledgeStage.knowledgeSummary`
- `generatedSql` -> `sqlDraftStage.generatedSql`
- `validatedSql` -> `sqlValidationStage.validatedSql`
- `sqlExecutionStatus` / `sqlExecutionResult` -> `sqlExecutionStage`
- `renderedAnswer` -> `output.finalAnswer` 或 `renderStage.renderedAnswer`

### 5.3 避免“全靠 Map”，但允许“受控扩展”

不建议退化成一个无类型 `Map<String, Object>` 上下文，因为这会把问题从“字段过多”变成“语义失控”。

建议采用：

- 顶层稳定强类型
- 阶段结果强类型
- 运行时属性弱类型但受限用途

这是比“全字段”或“全 Map”都更稳的折中方案。

### 5.4 事件发布能力保留，但从具体传输协议里抽离

保留 `WorkflowContext` 发布事件的能力是合理的，因为事件确实是跨节点共享能力。

但建议从：

- `WorkflowContext -> SseEmitter`

调整为：

- `WorkflowContext -> WorkflowEventPublisher`

好处：

- SSE、日志回放、异步消息、调试录制都可以复用同一套事件接口
- 节点不需要知道底层是不是 `SseEmitter`

## 6. 建议的节点边界重构

下面按“节点应该只负责什么”来定义边界。

### 6.1 ChatMessageNode

职责：

- 校验请求基础输入
- 解析或创建会话
- 加载历史消息/历史产物
- 创建当前 round
- 落库当前用户消息

不负责：

- 意图理解
- 模型规划
- 知识补全
- 最终回答生成

输出：

- `conversation.session`
- `conversation.round`
- `conversation.history`
- `conversation.currentUserMessage`

结论：

- 该节点适合作为“会话准备阶段”，边界基本正确，应保持稳定。

### 6.2 QueryPlanningNode

职责：

- 基于当前问题和历史消息生成结构化规划
- 产出后续节点所需的执行意图、分析摘要、风险和上下文需求

建议输出：

- `planningStage.sessionTitleSuggestion`
- `planningStage.userGoal`
- `planningStage.analysisSummary`
- `planningStage.analysisDimensions`
- `planningStage.requiredContext`
- `planningStage.sqlFocus`
- `planningStage.risks`
- `planningStage.needClarification`
- `planningStage.modelCall`

不建议继续直接输出：

- `context.analysisResult`
- `context.engineRequest`
- `context.engineResponse`

补充建议：

- “新会话标题更新”可以短期保留在该节点内，但更清晰的做法是沉到一个单独的 `SessionEnrichmentNode` 或 `ConversationFinalizeNode`。

### 6.3 KnowledgeSearchNode

职责：

- 基于 planning 结果决定是否检索知识库
- 聚合知识库命中结果
- 生成给 SQL 阶段使用的知识摘要

建议输出：

- `knowledgeStage.knowledgeBaseId`
- `knowledgeStage.searchRequest`
- `knowledgeStage.searchResponse`
- `knowledgeStage.summary`
- `knowledgeStage.degradeReason`

不建议顺带承担：

- 与知识无关的模型概览拼装

补充建议：

- 如果“模型概览”确实属于 SQL 生成依赖，应拆成独立 `ModelMetaPrepareNode`，不要长期塞在知识节点内部。

### 6.4 SqlGenerateNode

职责：

- 根据 planning + knowledge 产出候选 SQL

建议输出：

- `sqlDraftStage.request`
- `sqlDraftStage.response`
- `sqlDraftStage.generatedSql`
- `sqlDraftStage.generationFeedback`

不负责：

- SQL 合法性判断
- 重试决策
- 执行结果判断

### 6.5 SqlValidateNode

职责：

- 只做 SQL 安全校验与结构校验

建议输出：

- `sqlValidationStage.normalizedSql`
- `sqlValidationStage.validatedSql`
- `sqlValidationStage.validationError`
- `sqlValidationStage.retryCount`
- `sqlValidationStage.needRegenerate`

边界建议：

- “校验失败后回跳哪个节点”属于流程控制策略，建议逐步从节点内部判断迁到引擎层或统一策略层。
- 节点可以只返回标准化结果，例如 `needRegenerate=true`，由引擎决定跳转。

### 6.6 SqlExecuteNode

职责：

- 根据已校验 SQL 执行查询，或明确返回降级说明

建议输出：

- `sqlExecutionStage.status`
- `sqlExecutionStage.result`
- `sqlExecutionStage.executed`
- `sqlExecutionStage.degradeReason`

结论：

- 该节点的“显式降级”设计是合理的，应保留。

### 6.7 RenderNode

职责：

- 基于前序阶段产物生成最终回答

建议拆分边界：

- `RenderNode` 只生成最终答案
- `ResponseCommitNode` 负责消息落库、轮次收尾、最终产物快照落库

原因：

- “回答生成”和“结果提交”本质上是两类职责
- 未来如果要支持流式渲染、回答二次编辑、人工审核，拆开更稳

短期如果不拆节点，至少要明确内部分层：

- 先生成 `renderStage.renderedAnswer`
- 再由提交逻辑统一落库和结束 round

## 7. 建议的阶段模型

建议按业务阶段定义固定 stage code，而不是按实现类名随意扩散。

推荐阶段：

- `conversation_prepare`
- `query_planning`
- `knowledge_enrich`
- `sql_draft`
- `sql_validate`
- `sql_execute`
- `answer_render`
- `response_commit`

说明：

- 节点类名可以调整
- 但 stage code 一旦稳定，`WorkflowContext` 的主结构就不会因节点拆分/合并频繁变化

## 8. 对工作流引擎的配套建议

### 8.1 统一节点输入输出契约

节点执行时不再默认自由读写上下文任意字段，而是遵循：

- 输入读取哪些稳定区块
- 输出写入哪个 stageState
- 失败返回什么标准结果

### 8.2 强化 `NodeResult`

当前 `NodeResult` 只有：

- `success`
- `nextNodeId`
- `errorMessage`

建议扩展为：

- `success`
- `decision`
- `nextNodeId`
- `errorCode`
- `errorMessage`
- `stageCode`

其中 `decision` 可表达：

- `CONTINUE`
- `REDIRECT`
- `TERMINATE`
- `WAIT_CLARIFICATION`

这样回跳、终止、补问等流程控制，不必继续通过上下文隐式约定。

### 8.3 错误模型显式化

当前错误主要通过 `context.put("error", ...)` 传递。

建议调整为：

- `runtime.error.code`
- `runtime.error.message`
- `runtime.error.nodeId`
- `runtime.error.stageCode`
- `runtime.error.retryable`

## 9. 推荐实施顺序

### 第一阶段：收敛上下文骨架

- 保留当前业务语义不变
- 将 `WorkflowContext` 拆成 `request/conversation/runtime/stage/output`
- 先增加新结构，保留旧字段兼容一小段时间

### 第二阶段：迁移节点阶段产物

- `QueryPlanningNode` 先迁到 `planningStage`
- `KnowledgeSearchNode` 迁到 `knowledgeStage`
- `SqlGenerateNode`、`SqlValidateNode`、`SqlExecuteNode` 依次迁移
- `RenderNode` 最后迁移到 `renderStage/output`

### 第三阶段：收缩顶层旧字段

- 删除 `analysisResult`
- 删除 `knowledgeBaseId`
- 删除 `knowledgeResult`
- 删除 `generatedSql`
- 删除 `validatedSql`
- 删除 `sqlValidationError`
- 删除 `sqlExecutionStatus`
- 删除 `sqlExecutionResult`
- 删除 `renderedAnswer`

### 第四阶段：明确节点控制与提交边界

- 把回跳策略从节点逻辑中逐步抽离
- 视情况新增 `ResponseCommitNode`
- 将事件发布抽象成 `WorkflowEventPublisher`

## 10. 预期收益

完成上述调整后，能够得到以下结果：

- 新增节点时，大多数情况下不需要再改 `WorkflowContext` 顶层属性
- 节点职责清晰，调试时更容易判断状态应该落在哪个阶段
- 模型请求、知识结果、SQL 结果、渲染结果不再相互污染
- SSE 能力保留，但不再绑死具体传输对象
- 后续扩展补问、人工审核、图表渲染、结果解释等节点时，结构成本更低

## 11. 结论

本次优化的关键，不是把 `WorkflowContext` 做得更“通用抽象”，而是让它只承载稳定骨架；真正变化快、节点特有的结果，应迁移到阶段化 `StageState` 中。

建议后续实现时遵循一句原则：

`WorkflowContext` 负责“这次流程是谁、在哪、运行到什么状态”；各 `StageState` 负责“这个阶段产出了什么”。

这会比继续追加顶层字段，或者直接退回无类型 `Map`，都更稳定。
