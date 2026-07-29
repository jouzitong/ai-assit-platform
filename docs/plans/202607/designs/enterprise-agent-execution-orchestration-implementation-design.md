# 企业级 Agent 执行模式实施方案

> 状态：待实施  
> 日期：2026-07-29  
> 范围：app/app-platform-chat/providers/ai-provider-ai-agent、app/app-platform-chat/modules/core-agent-runtime，并与 Chat 会话、DB Engine、Render 和权限/审计能力对接。  
> 目标：把当前“请求分析 + Root Agent 自主执行”的 Python Runtime，演进为可路由、可规划、可恢复、可校验的企业级 Agent 执行工作流。  
> 说明：本文基于当前代码审查形成，不代表本次已经修改或部署任何生产代码。

## 1. 结论

当前 Python Agent Runtime 已具备以下基础：

- 使用 OpenAI Agents Python SDK 的 Runner.run_streamed 实现模型、工具和 Agent 协作循环；
- 使用 Agent-as-tool 委派数据分析、看板构建、文档分析等专业 Agent；
- 已有 RequestAnalysis、allowlist、maxTurns、maxAgentDepth；
- 看板 Agent 已按 ApplicationBrief -> DataContract -> data preview -> ApplicationPlan -> RenderDocument -> ValidationReport 工作；
- 已有事件审计、Artifact 提取、知识证据守卫、DataContract 和 Render JSON 校验；
- Java Tool Gateway 已有 capability、schema、权限、审批、幂等和审计基础。

但当前实现还不是完整的企业级 Plan-and-Execute，最重要的差距有五个：

1. analyze_request 的 Router 结果只写入事件和 providerMeta，没有真正驱动执行分支。
2. 通用请求没有统一的 TaskState、ExecutionPlan、Step 状态和重规划机制。
3. data-analysis Agent 只有知识库搜索工具，不能完成实时语义查询和业务数据分析。
4. guard_output 主要校验知识证据，不校验数值、口径、数据新鲜度、血缘和报表一致性。
5. Python Runtime 尚未接入 Agents SDK 的 needs_approval、interruptions、to_state() 恢复闭环。

因此，本方案采用以下目标：

~~~text
Context Builder
  -> Router
  -> Execution Controller
  -> Plan and Execute
  -> ReAct
  -> Tool / Specialist Agent
  -> Deterministic Validator + Evaluator
  -> Clarify / Repair / Approval / Success
  -> TaskState + Artifact + Trace
~~~

核心原则：

~~~text
模型负责理解、规划、解释和选择候选能力；
程序负责路由、权限、状态、预算、确定性校验和最终状态；
Tool 负责执行确定性业务动作；
Specialist Agent 负责受限领域推理；
Evaluator 负责判断产出是否满足业务契约；
任何高风险副作用必须可暂停、可审批、可恢复。
~~~

## 2. 当前实现基线

### 2.1 当前 Python 主链

当前入口为：

- agent_provider/main.py：读取 JSON/NDJSON 请求并启动一次 Run；
- agent_provider/compiler/snapshot.py：规范化并编译 Agent graph；
- agent_provider/runtime/runner.py：分析请求、执行 Root Agent、收集事件、进行输出守卫；
- agent_provider/protocol/normalize.py：拼装历史、当前请求和页面上下文；
- agent_provider/runtime/request_analysis.py：生成结构化请求分析；
- agent_provider/agents/dispatcher.py：把专业 Agent 包装成 Agent-as-tool；
- agent_provider/runtime/confidence_guard.py：知识证据和可信度守卫。

当前真实流程是：

~~~text
JSON input
  -> normalize_payload / compile_snapshot
  -> analyze_request
  -> build_application_input
  -> Root Agent + Runner.run_streamed
  -> Tools / Agent-as-tool
  -> guard_output
  -> finalOutput / artifacts / providerMeta
~~~

其中 analyze_request 是独立的分析调用，但它的 route、successCriteria 和 validationPlan 没有进入执行控制器。实际执行仍由 Root Agent 根据 Prompt 自主决定。

### 2.2 当前实现的优点

| 能力 | 当前实现 | 评价 |
|---|---|---|
| Context 边界 | 页面上下文标记为不可信数据并限制大小 | 已具备安全意识 |
| Agent 协作 | Dispatcher 使用 Agent-as-tool | 符合主控 Agent 保留最终回答的模式 |
| 复杂度限制 | maxTurns、maxAgentDepth、图校验 | 有基础运行保护 |
| 看板流程 | DataContract、数据预览、Render 校验和最多三次修复 | 业务流程较完整 |
| Tool 安全 | DataContract 字段、过滤、时间范围和行数限制 | 适合继续扩展为语义查询 |
| 事件审计 | Agent、Tool、结论、Artifact、usage 事件 | 便于排查和评估 |
| 知识证据 | 检索、证据覆盖和可信度评估 | 适合知识问答和制度类问题 |

### 2.3 当前实现的缺口

| 优先级 | 缺口 | 影响 |
|---|---|---|
| P0 | Router 不驱动执行 | CLARIFY、DELEGATE、TOOL 只是建议，无法形成程序级边界 |
| P0 | data-analysis 缺少真实查询 Tool | 智能问数只能给方案，不能稳定查企业实时数据 |
| P0 | 最终状态固定为 SUCCESS | 澄清、证据不足、审批等待、工具失败无法正确传递 |
| P1 | 无统一 TaskState/Plan | 无法恢复中断任务，也无法判断某个步骤是否完成 |
| P1 | 无业务数据 Validator | 可能输出错误合计、错误同比或过期数据 |
| P1 | parent/child 没有全局预算 | 多 Agent 嵌套会放大 token、延迟和工具调用 |
| P1 | Approval 只停留在 Prompt/Gateway 约束 | 写入和发布无法真正暂停并恢复 |
| P1 | PYTHON_LOCAL 覆盖 capability | 需要确认 Java Gateway 的权限和审批是否仍处于调用链 |
| P2 | 历史 Tool output 作为 user replay | 需要更清晰地标记为外部数据，降低提示注入风险 |

### 2.4 代码证据索引

以下路径是本方案的现状依据，实施时应优先在这些入口做最小改造：

| 现状结论 | 代码位置 |
|---|---|
| 请求分析完成后直接启动 Root Agent | app/app-platform-chat/providers/ai-provider-ai-agent/src/main/python/agent_provider/runtime/runner.py:39-62 |
| Router 结构化结果和 allowlist 校验 | app/app-platform-chat/providers/ai-provider-ai-agent/src/main/python/agent_provider/runtime/request_analysis.py:224-430 |
| 历史 Tool output 被包装为 user replay | app/app-platform-chat/providers/ai-provider-ai-agent/src/main/python/agent_provider/protocol/normalize.py:106-141 |
| Agent-as-tool 委派和 child run | app/app-platform-chat/providers/ai-provider-ai-agent/src/main/python/agent_provider/agents/dispatcher.py:28-85 |
| data-analysis 当前只声明知识库 Tool | app/app-platform-chat/providers/ai-provider-ai-agent/src/main/python/agent_provider/agents/definitions/data_analysis.py:4-19 |
| 看板构建的阶段性门禁和校验 Tool | app/app-platform-chat/providers/ai-provider-ai-agent/src/main/python/agent_provider/agents/definitions/dashboard_application.py:4-41 |
| PYTHON_LOCAL 替换 Java manifest capability | app/app-platform-chat/providers/ai-provider-ai-agent/src/main/python/agent_provider/compiler/snapshot.py:152-168 |
| 输出守卫、证据不足和可信度评估 | app/app-platform-chat/providers/ai-provider-ai-agent/src/main/python/agent_provider/runtime/confidence_guard.py:136-407 |
| 最终结果状态和 finishReason 当前固定 | app/app-platform-chat/providers/ai-provider-ai-agent/src/main/python/agent_provider/runtime/runner.py:113-135 |

## 3. 目标与非目标

### 3.1 目标

1. 每个 Run 在开始时固定用户、租户、权限、Agent、Tool、Skill、模型和 Workflow 版本。
2. Router 结果必须由程序读取，并转换为可执行的 RouteDecision。
3. 复杂动态任务必须生成结构化 Plan，并维护每个步骤的状态。
4. ReAct 只能在当前计划、能力白名单和预算内运行。
5. 智能问数必须能完成“指标解析、受控查询、数据质量检查、确定性分析、解释性输出”闭环。
6. 报表和看板必须有结构化 Artifact、数据证明和确定性校验。
7. 工具失败、证据不足、需澄清、需审批和运行失败必须使用不同状态。
8. 支持人工审批后从同一 Run 状态恢复，而不是创建新的伪续聊请求。
9. 保留现有 NDJSON/SSE 事件、Session、Round、Message、Artifact 和 Trace 兼容性。

### 3.2 非目标

- 不把所有业务逻辑改写成 Agent；
- 不允许 Agent 直接访问物理表、SQL、数据库凭据或任意 URL；
- 不让知识库结果替代权限校验、数据目录和数据质量校验；
- 不在首期同时引入 SDK Session、OpenAI conversationId 和自定义历史 replay；
- 不把草稿 Agent 定义直接用于生产执行；
- 不通过 Prompt 文本代替审批、权限和状态控制；
- 不在本方案中重写已有 Chat、DB Engine 或 Render 领域服务。

## 4. 目标总体架构

### 4.1 控制面与执行面

~~~mermaid
flowchart LR
    UI["Chat / Report / Dashboard UI"] --> API["Conversation API"]
    API --> PREP["Chat Preparation"]
    PREP --> ENTRY["Entry Binding Resolver"]
    ENTRY --> SNAP["Published Agent Snapshot"]
    SNAP --> CTRL["Agent Execution Controller"]

    CTRL --> CTX["Context Builder"]
    CTX --> ROUTER["Structured Router"]
    ROUTER -->|CLARIFY| CLARIFY["NEEDS_CLARIFICATION"]
    ROUTER -->|DIRECT| DIRECT["Direct / Fixed Workflow"]
    ROUTER -->|TOOL| TOOL_ROUTE["Restricted Tool Route"]
    ROUTER -->|DELEGATE| DELEGATE["Specialist Agent Route"]

    DIRECT --> PLAN["Plan Builder"]
    TOOL_ROUTE --> PLAN
    DELEGATE --> PLAN
    PLAN --> EXEC["Plan Executor"]
    EXEC --> REACT["Agents SDK ReAct Loop"]
    REACT --> TOOLS["Tool Gateway / Function Tools"]
    REACT --> AGENTS["Agent-as-tool / Handoff"]
    TOOLS --> STATE["TaskState + Step State"]
    AGENTS --> STATE
    STATE --> COMPLETE{"Plan complete?"}
    COMPLETE -->|No| EXEC
    COMPLETE -->|Yes| VALIDATE["Deterministic Validator + Evaluator"]
    VALIDATE -->|Repairable| EXEC
    VALIDATE -->|Approval required| APPROVAL["AWAITING_APPROVAL"]
    VALIDATE -->|Clarification required| CLARIFY
    VALIDATE -->|Pass| OUTPUT["Answer + Artifact + Trace"]

    CONTROL["Agent / Tool / Skill / Workflow Control Plane"] --> SNAP
    IAM["Tenant / Identity / Capability / Approval"] --> CTRL
    AUDIT["Audit / Metrics / Trace Store"] --> STATE
    OUTPUT --> AUDIT
~~~

### 4.2 运行时责任边界

| 组件 | 负责 | 不负责 |
|---|---|---|
| Context Builder | 历史、当前消息、页面上下文、身份和权限快照 | 判断业务答案是否正确 |
| Router | 识别任务类型、完整性、复杂度、候选 Agent/Tool | 直接执行数据查询或写操作 |
| Planner | 生成结构化步骤、成功条件和校验计划 | 绕过权限或改变已发布能力 |
| Execution Controller | 读取 Route/Plan，控制分支、预算和状态转移 | 生成自然语言业务结论 |
| ReAct Runner | 在当前步骤中调用模型和允许的 Tool/Agent | 决定全局权限和最终状态 |
| Tool | 执行确定性、可审计的业务能力 | 自己解释不确定业务意图 |
| Specialist Agent | 对限定领域进行推理和材料生成 | 获得超出授权的工具或权限 |
| Validator | 校验 JSON、数值、口径、数据新鲜度、权限证明和 Artifact | 依赖模型自报“已通过” |
| Evaluator | 评估完整性、证据覆盖和业务质量 | 替代确定性规则 |
| TaskState Store | 保存可恢复状态、计划、步骤、审批和证据 | 保存 Agent 草稿定义 |

## 5. 任务模式和路由策略

### 5.1 四种执行模式

| 模式 | 适用场景 | 执行方式 | 典型结果 |
|---|---|---|---|
| DIRECT | 简单问答、解释术语、单轮总结 | Root Agent 或固定 Tool 链 | SUCCESS |
| FIXED_WORKFLOW | 固定报表、看板、格式化输出 | 程序固定步骤 + Agent 填充内容 | SUCCESS / VALIDATION_FAILED |
| DYNAMIC_PLAN | 智能问数、异常分析、跨来源综合分析 | Planner 生成步骤，Controller 驱动 ReAct | SUCCESS / NEEDS_CLARIFICATION |
| SIDE_EFFECT | 发布、写入、发起流程、发送通知 | Plan + Tool approval + resume | AWAITING_APPROVAL / SUCCESS |

### 5.2 RouteDecision

建议把现有 RequestAnalysis 拆为两个对象：

1. RequestAnalysis：模型对请求的结构化理解，允许包含不确定性；
2. RouteDecision：经过程序校验、权限求交和策略计算后的执行决定。

~~~json
{
  "mode": "DYNAMIC_PLAN",
  "agentCode": "data-analysis",
  "agentVersion": 1,
  "allowedToolCodes": [
    "metric_catalog_search_tool",
    "semantic_data_query_tool",
    "data_quality_check_tool",
    "data_freshness_check_tool"
  ],
  "allowedKnowledgeBaseCodes": [
    "data-semantic-catalog",
    "enterprise-business-knowledge"
  ],
  "requiresClarification": false,
  "requiresApproval": false,
  "reason": "请求包含明确的区域、指标和分析目标，适合结构化数据分析。",
  "policyRevision": "agent-policy/v3"
}
~~~

RouteDecision 必须由程序生成，不能直接信任模型返回值。最终可用能力取交集：

~~~text
effectiveCapabilities
  = requestedCapabilities
  ∩ publishedAgentCapabilities
  ∩ tenantCapabilities
  ∩ userGrants
  ∩ runPolicy
~~~

### 5.3 Router 的程序分支

~~~python
analysis = await analyze_request(...)
decision = route_policy.decide(
    analysis=analysis,
    snapshot=published_snapshot,
    identity=identity_snapshot,
    capability_grant=capability_grant,
)

if decision.requires_clarification:
    return outcome.needs_clarification(decision.questions)

if decision.mode == "DIRECT":
    return await direct_executor.run(...)

if decision.mode in {"FIXED_WORKFLOW", "DYNAMIC_PLAN"}:
    return await plan_executor.run(...)

if decision.mode == "SIDE_EFFECT":
    return await approval_aware_executor.run(...)

return outcome.failed("UNSUPPORTED_ROUTE")
~~~

## 6. TaskState、Plan 和状态机

### 6.1 TaskState

TaskState 是一次业务任务的可持久化状态，不等同于 OpenAI SDK 的内部 Run state。两者的关系是：

~~~text
TaskState：业务层状态，可跨进程、跨服务、跨审批持久化；
SDK Run state：SDK 层暂停/恢复所需的运行快照；
Trace：观测层事件，不能作为恢复状态的唯一来源。
~~~

建议结构：

~~~json
{
  "taskId": "task-20260729-001",
  "runId": "run-001",
  "conversationId": "conversation-001",
  "tenantId": "tenant-001",
  "userId": "user-001",
  "status": "EXECUTING",
  "mode": "DYNAMIC_PLAN",
  "snapshotHash": "snapshot-abc",
  "rootAgent": {
    "code": "enterprise-work",
    "version": 1
  },
  "routeDecision": {},
  "plan": {},
  "steps": [],
  "evidence": [],
  "artifacts": [],
  "approval": null,
  "budgets": {
    "maxTurns": 12,
    "usedTurns": 4,
    "maxToolCalls": 20,
    "usedToolCalls": 3,
    "maxReplans": 2,
    "replanCount": 0
  },
  "lastError": null,
  "createdAt": "2026-07-29T00:00:00Z",
  "updatedAt": "2026-07-29T00:01:00Z"
}
~~~

### 6.2 状态枚举

建议统一 Java、Python 和 SSE 协议中的状态：

~~~text
RECEIVED
CONTEXT_BUILT
ANALYZING
NEEDS_CLARIFICATION
ROUTED
PLANNING
EXECUTING
VALIDATING
REPAIRING
AWAITING_APPROVAL
INSUFFICIENT_EVIDENCE
COMPLETED
FAILED
CANCELLED
TIMED_OUT
~~~

对外结果中的 status 和 finishReason 不再固定为 SUCCESS/STOP。示例：

~~~json
{
  "status": "AWAITING_APPROVAL",
  "finishReason": "APPROVAL_REQUIRED",
  "runId": "run-001",
  "taskId": "task-001",
  "approval": {
    "approvalId": "approval-001",
    "toolCode": "render_publish_tool",
    "summary": "即将发布销售经营看板",
    "expiresAt": "2026-07-29T01:00:00Z"
  }
}
~~~

### 6.3 Plan 和 Step

~~~json
{
  "planId": "plan-001",
  "version": 1,
  "objective": "分析华东区域销售额下降原因",
  "successCriteria": [
    "已解析指标销售额及其口径",
    "已获得授权范围内的真实数据",
    "已完成同比或环比计算",
    "结论带有数据时间和来源证明"
  ],
  "validationPlan": [
    "metric_definition_consistency",
    "numeric_calculation",
    "data_freshness",
    "evidence_coverage"
  ],
  "steps": [
    {
      "stepId": "resolve_metric",
      "kind": "TOOL",
      "toolCode": "metric_catalog_search_tool",
      "status": "PENDING",
      "dependsOn": [],
      "maxAttempts": 2
    },
    {
      "stepId": "query_sales",
      "kind": "TOOL",
      "toolCode": "semantic_data_query_tool",
      "status": "PENDING",
      "dependsOn": ["resolve_metric"],
      "maxAttempts": 2
    },
    {
      "stepId": "analyze_variance",
      "kind": "DETERMINISTIC",
      "toolCode": "deterministic_analysis_tool",
      "status": "PENDING",
      "dependsOn": ["query_sales"],
      "maxAttempts": 1
    },
    {
      "stepId": "explain_result",
      "kind": "AGENT",
      "agentCode": "data-analysis",
      "status": "PENDING",
      "dependsOn": ["analyze_variance"],
      "maxAttempts": 1
    }
  ]
}
~~~

Step 状态只能由 Controller 更新：

~~~text
PENDING -> READY -> RUNNING -> SUCCEEDED
                         -> RETRYING -> RUNNING
                         -> BLOCKED
                         -> FAILED
~~~

模型可以提出新计划或修订建议，但不能直接把步骤标记为 SUCCEEDED。步骤完成必须由 Tool 返回结果或确定性 Validator 证明。

## 7. Python Runtime 的目标模块拆分

建议在现有 Python Provider 内按以下职责拆分。第一阶段可以先以最小模块落地，不要求一次性重构全部文件。

~~~text
agent_provider/
  runtime/
    context_builder.py       # 从协议输入生成 ContextSnapshot
    request_analysis.py      # 保留模型分析和结构化校验
    router.py                # RoutePolicy + RouteDecision
    planner.py               # Plan 生成、校验和重规划
    execution_controller.py  # 模式分支、预算、状态机
    task_state.py            # TaskState、StepState、状态迁移
    approval.py               # interruption、state 序列化和恢复
    validators/
      business_data.py       # 数值、口径、新鲜度、血缘
      artifact.py            # Artifact Schema 和 hash
      evidence.py            # 知识证据和覆盖率
    runner.py                # SDK 运行适配和事件转发
  agents/
    dispatcher.py            # Agent-as-tool
    factory.py               # Agent 编译和能力求交
  tools/
    metric_catalog_search_tool.py
    semantic_data_query_tool.py
    data_quality_check_tool.py
    data_freshness_check_tool.py
    deterministic_analysis_tool.py
~~~

### 7.1 对现有文件的最小改造

| 现有文件 | 改造方向 |
|---|---|
| runtime/runner.py | 从“大函数”拆出 Context、Route、Plan、Execute、Validate、Outcome 六个阶段 |
| runtime/request_analysis.py | 保留结构化分析，但只生成候选理解；新增程序级 RouteDecision |
| protocol/normalize.py | 输出 ContextSnapshot，对 Tool output 使用明确的 untrusted data envelope |
| compiler/snapshot.py | 输出不可变 Snapshot 和 capability intersection；明确 PYTHON_LOCAL 与 Gateway 模式 |
| agents/dispatcher.py | 传递 parent task/step/budget；对子 Agent 结果生成结构化 AgentWorkMaterial |
| runtime/confidence_guard.py | 保留知识证据逻辑，拆出 BusinessDataValidator 和 ArtifactValidator |
| agents/definitions/data_analysis.py | 增加只读语义查询、数据质量和新鲜度能力 |
| tools/data_preview_query_tool.py | 作为 DataContract 预览工具保留，不替代通用语义查询 |

## 8. Tool 与 Specialist Agent 设计

### 8.1 Tool 分类

| 类型 | 例子 | 是否允许模型自由探索 | 是否需要审批 |
|---|---|---:|---:|
| Discovery | knowledge_base_search_tool、metric_catalog_search_tool | 有限 | 否 |
| Read-only query | semantic_data_query_tool、data_preview_query_tool | 受 DataContract 限制 | 否 |
| Deterministic validation | data_quality_check_tool、render_json_validate_tool | 否，按契约调用 | 否 |
| Artifact generation | report_artifact_build_tool | 受 schema 限制 | 通常否 |
| Side effect | render_publish_tool、workflow_submit_tool | 否 | 是 |

每个 Tool 的声明至少包含：

~~~json
{
  "toolCode": "semantic_data_query_tool",
  "version": 1,
  "runtimeType": "JAVA_GATEWAY",
  "inputSchema": "SemanticQueryRequest/v1",
  "outputSchema": "SemanticQueryResult/v1",
  "capabilities": ["readonly-data"],
  "sideEffect": false,
  "approvalPolicy": "NONE",
  "idempotencyRequired": true,
  "timeoutMs": 10000,
  "tenantScoped": true
}
~~~

### 8.2 Agent-as-tool 与 handoff

默认采用 Agent-as-tool：

~~~text
Enterprise Work Agent
  -> ask_data_analysis(task)
  -> ask_dashboard_builder(task)
  -> ask_knowledge_policy(task)
  -> 主 Agent 整合最终回答
~~~

仅在专业 Agent 应该直接接管下一轮对话时使用 handoff。选择规则：

- 主 Agent 需要综合多份材料：使用 Agent-as-tool；
- 专业 Agent 应拥有下一轮用户对话：使用 handoff；
- 具有不同权限、成本或审批边界：优先使用显式 Controller 分支，而不是仅依赖模型 handoff。

OpenAI 官方文档对 Agent-as-tool 和 handoff 的边界有明确说明：[Orchestration and handoffs](https://developers.openai.com/api/docs/guides/agents/orchestration)。

## 9. 企业智能问数实施方案

### 9.1 目标链路

~~~mermaid
sequenceDiagram
    participant U as 用户
    participant C as Context Builder
    participant R as Router
    participant P as Planner
    participant X as Plan Executor
    participant M as Metric Catalog
    participant Q as Semantic Query
    participant V as Data Validators
    participant A as Data Analysis Agent

    U->>C: 查询华东区域销售额并分析下降原因
    C->>R: 任务、历史、身份、权限、页面上下文
    R->>R: 识别 DATA_ANALYSIS + 完整性
    R->>P: 生成结构化 Plan
    P->>X: resolve_metric
    X->>M: 查找销售额指标和口径
    M-->>X: metricCode + definition + revision
    X->>Q: 受控语义查询
    Q-->>X: 数据 + 时间范围 + sourceRevision + lineage
    X->>V: 数值、质量、新鲜度、权限校验
    V-->>X: ValidationReport
    X->>A: 基于已验证数据解释下降原因
    A-->>X: 结论、假设、证据和待确认项
    X->>V: 结论覆盖和数字一致性校验
    V-->>U: 带口径、时间、来源和限制的回答
~~~

### 9.2 推荐 Tool

#### metric_catalog_search_tool

职责：

- 根据自然语言候选指标、维度和业务域查找已发布指标；
- 返回 metricCode、定义、默认聚合、允许维度、默认时间字段、版本和 owner；
- 只返回当前用户有权使用的指标候选。

不负责：

- 判定最终数据权限；
- 直接执行数据查询；
- 仅凭知识库文本确认指标已发布。

#### semantic_data_query_tool

输入必须是受限语义协议：

~~~json
{
  "model": "sales_order",
  "measures": [
    {
      "metricCode": "sales_amount",
      "aggregation": "SUM"
    }
  ],
  "dimensions": [
    {
      "field": "region_name"
    },
    {
      "field": "month"
    }
  ],
  "filters": [
    {
      "field": "region_name",
      "operator": "EQ",
      "value": "华东"
    }
  ],
  "timeRange": {
    "field": "paid_at",
    "start": "2026-01-01",
    "end": "2026-06-30"
  },
  "limit": 200
}
~~~

服务端必须拒绝：

- SQL；
- 物理表名；
- 任意 endpoint；
- 凭据；
- 任意 URL；
- 未发布模型；
- 未授权字段；
- 无界时间范围；
- 无界行数或列数。

返回必须带证明：

~~~json
{
  "status": "SUCCESS",
  "rows": [],
  "columns": [],
  "queryHash": "query-abc",
  "modelRevision": "sales-order/v12",
  "metricRevision": "metric/v8",
  "dataFreshness": {
    "asOf": "2026-07-29T08:00:00Z",
    "delayMinutes": 15
  },
  "lineage": [],
  "permissionProof": "proof-xyz"
}
~~~

#### data_quality_check_tool

检查：

- 空值率；
- 重复主键；
- 时间字段缺失；
- 维度值异常；
- 汇总是否可比；
- 数据是否跨越多个粒度；
- 数据是否包含权限过滤后的截断。

#### data_freshness_check_tool

检查：

- 查询结果的 asOf；
- 当前业务指标允许的最大延迟；
- 用户请求是否要求“实时”；
- 数据是否来自过期缓存；
- source revision 是否仍然有效。

#### deterministic_analysis_tool

把模型不应自行计算的逻辑程序化，包括：

- 同比、环比；
- 增长率；
- 贡献度；
- Top N；
- 小计和总计；
- 期间对齐；
- 维度拆解。

模型只负责解释结果，不负责凭文本重算核心数字。

### 9.3 智能问数最终输出

建议返回结构化 AnalysisReport：

~~~json
{
  "artifactCode": "analysis-report",
  "artifactType": "ANALYSIS_REPORT",
  "contentFormat": "JSON",
  "content": {
    "answer": "华东销售额较上期下降 12.4%。",
    "metrics": [
      {
        "metricCode": "sales_amount",
        "label": "销售额",
        "value": 123456,
        "comparison": {
          "type": "MOM",
          "value": -0.124
        }
      }
    ],
    "drivers": [
      {
        "dimension": "product_category",
        "value": "A类",
        "contribution": -0.071,
        "evidence": ["query-abc"]
      }
    ],
    "assumptions": [],
    "limitations": [],
    "sourceProofs": [
      {
        "queryHash": "query-abc",
        "modelRevision": "sales-order/v12",
        "metricRevision": "metric/v8"
      }
    ],
    "validationReport": {
      "valid": true,
      "checks": [
        "metric_definition_consistency",
        "numeric_calculation",
        "data_freshness",
        "evidence_coverage"
      ]
    }
  }
}
~~~

## 10. 报表分析和看板构建

### 10.1 报表分析

报表分析应沿用智能问数的查询和校验结果，不允许 Report Agent 直接从自然语言生成未经验证的数字。

~~~text
Report Request
  -> 报表模板和指标口径解析
  -> DataContract
  -> Semantic Query
  -> Deterministic Calculation
  -> ReportPlan
  -> ReportArtifact
  -> Report Validator
  -> Answer / Downloadable Artifact
~~~

ReportArtifact 至少包含：

- reportCode；
- queryHash；
- metricRevision；
- modelRevision；
- asOf；
- columns；
- rows 或聚合结果；
- calculation definitions；
- validationReport；
- generatedAt；
- permissionProof。

### 10.2 看板/轻应用

现有 dashboard-application-builder 的阶段约束应保留，并改由通用 Plan/Step 状态机承载：

~~~text
ApplicationBrief
  -> DataContract
  -> Controlled Data Preview
  -> ApplicationPlan
  -> RenderDocument
  -> render_json_validate_tool
  -> render_preview_tool
  -> render_publish_tool（需要审批）
~~~

看板发布属于副作用操作，必须进入 SIDE_EFFECT 模式。发布 Tool 不接受模型声称“已经校验”的文本，只接受：

- RenderDocument hash；
- ValidationReport hash；
- Preview proof；
- 组件目录 revision；
- 当前用户和页面权限证明。

## 11. Evaluator 与 Validator 分层

### 11.1 三类校验

| 层级 | 例子 | 失败处理 |
|---|---|---|
| Schema Validator | JSON Schema、字段类型、Artifact envelope | 直接阻断 |
| Business Validator | 指标口径、数值计算、新鲜度、权限、血缘 | 阻断或有限修复 |
| Semantic Evaluator | 回答完整性、解释质量、证据覆盖 | 重新规划、澄清或降级回答 |

### 11.2 Validator 的 fail-closed 原则

确定性检查无法确认时，不得把结果标记为成功：

~~~text
valid = true
  only when all required checks passed

unknown / timeout / missing proof
  -> VALIDATION_INCOMPLETE
  -> not SUCCESS
~~~

### 11.3 Evaluator 的修复策略

建议最多允许：

- 当前步骤工具重试：2 次；
- 当前 Plan 重规划：2 次；
- Render JSON 稳定错误修复：3 次；
- 证据补充检索：1 次；
- 重新评估：1 次。

超过上限时进入：

- NEEDS_CLARIFICATION：缺少用户输入；
- INSUFFICIENT_EVIDENCE：无法获得可靠依据；
- FAILED：工具、系统或策略失败。

## 12. Approval、暂停和恢复

### 12.1 SDK 层

对于 Python Agents SDK 支持的函数工具，可以使用 needs_approval=True。运行结果包含 interruptions 时：

~~~python
result = await Runner.run(agent, input)

if result.interruptions:
    sdk_state = result.to_state()
    await task_state_store.save_sdk_state(run_id, serialize(sdk_state))
    return awaiting_approval(result.interruptions)
~~~

用户批准后：

~~~python
sdk_state = deserialize(await task_state_store.load_sdk_state(run_id))
for interruption in sdk_state.interruptions:
    sdk_state.approve(interruption)

result = await Runner.run(agent, sdk_state)
~~~

OpenAI 官方文档明确建议把审批当作暂停的 Run，而不是新建一轮用户对话：[Running agents](https://developers.openai.com/api/docs/guides/agents/running-agents)、[Guardrails and human review](https://developers.openai.com/api/docs/guides/agents/guardrails-approvals)。

### 12.2 Java/NDJSON 协议层

建议新增：

~~~json
{
  "eventType": "run.interrupted",
  "status": "AWAITING_APPROVAL",
  "runId": "run-001",
  "taskId": "task-001",
  "approvalId": "approval-001",
  "toolCode": "render_publish_tool",
  "summary": "发布销售经营看板",
  "expiresAt": "2026-07-29T01:00:00Z"
}
~~~

恢复请求只携带：

~~~json
{
  "runId": "run-001",
  "approvalId": "approval-001",
  "decision": "APPROVE",
  "actor": "user-001"
}
~~~

恢复时必须重新检查：

- approval 是否属于该用户、租户和 Run；
- Snapshot hash 是否仍然有效；
- Tool 版本是否发生变化；
- 权限是否仍然有效；
- 状态是否已过期、已执行或已拒绝；
- 幂等键是否一致。

## 13. Java Gateway 和 PYTHON_LOCAL 路径

当前 compiler/snapshot.py 在 PYTHON_LOCAL 模式下会用 Python 本地 Agent catalog 和 skills 替换 Java manifest capability。这个设计需要明确二选一：

### 方案 A：Python 只负责 SDK 编排，Java 统一负责 Tool Gateway

~~~text
Python Agent
  -> Java Tool Gateway facade
  -> DB Engine / Render / Knowledge / Workflow
~~~

优点：

- 权限、审批、幂等、审计集中；
- Java 领域服务继续是唯一真实业务入口；
- Python 不需要知道领域系统地址和凭据。

要求：

- PYTHON_LOCAL 不能清空 Java resolvedCapabilities；
- Python 只能获得 Java 计算后的 effective capability；
- Tool input/output schema 必须在 Java Gateway 再次校验。

### 方案 B：Python 使用本地 Tool，但本地 Tool 统一调用受控 facade

~~~text
Python FunctionTool
  -> platform_http
  -> Chat internal facade
  -> Java domain service
~~~

优点：

- Python Tool 定义简单；
- 适合早期迁移和本地测试。

要求：

- facade 必须验证临时 token、runId、tenantId、Agent snapshot；
- 本地 Tool 不能直接连 DB、Render 或第三方；
- 所有 Tool 调用必须生成同一套审计事件；
- 生产环境必须禁用未授权的 local capability。

建议生产采用方案 A 或 A+B 混合，但只能有一个最终权限裁决点，不能由 Prompt、Python 和 Java 分别做不一致的判断。

## 14. 事件、Trace 和审计

### 14.1 事件最小字段

所有事件应包含：

~~~json
{
  "eventId": "event-001",
  "eventType": "tool.completed",
  "status": "SUCCESS",
  "runId": "run-001",
  "taskId": "task-001",
  "parentEventId": "event-000",
  "agentCode": "data-analysis",
  "agentVersion": 1,
  "stepId": "query_sales",
  "toolCode": "semantic_data_query_tool",
  "toolVersion": 1,
  "snapshotHash": "snapshot-abc",
  "traceId": "trace-001",
  "durationMs": 342,
  "usage": {
    "inputTokens": 1200,
    "outputTokens": 300
  },
  "ext": {}
}
~~~

### 14.2 事件类型

~~~text
context.started / context.completed
route.started / route.completed
plan.created / plan.updated
step.started / step.completed / step.failed
agent.delegated / agent.delegation.completed
tool.started / tool.completed / tool.failed
validation.started / validation.completed
evaluation.started / evaluation.completed
run.interrupted / run.resumed
artifact.created
run.completed / run.failed / run.cancelled
~~~

Trace 用于观察和评估，TaskState 用于恢复。不能只依赖 event replay 恢复一个已经暂停的 SDK Run。

## 15. 分阶段实施计划

### Phase 0：契约和协议冻结

交付物：

1. RequestAnalysis、RouteDecision、TaskState、ExecutionPlan、ValidationReport、ApprovalRequest JSON Schema；
2. 统一状态枚举和 finishReason；
3. 确定 Python local Tool 与 Java Gateway 的最终权限边界；
4. 固定 Snapshot、Tool、Agent、Skill 和 Workflow version 语义；
5. 明确只采用一种会话续接策略。

验收：

- Java、Python、SSE 和测试 fixture 使用同一套字段；
- 无法识别的状态、工具、Agent、版本会 fail-closed；
- Schema 变更有版本号和兼容策略。

### Phase 1：Router 真正驱动执行

交付物：

1. 新增 RoutePolicy 和 RouteDecision；
2. CLARIFY 可以直接返回 NEEDS_CLARIFICATION；
3. TOOL route 限制本轮 Tool；
4. DELEGATE route 限制目标 Agent；
5. 记录 route reason、policy revision 和 fallback reason。

验收场景：

- 缺少时间范围时返回澄清，不调用查询 Tool；
- 未授权 Agent code 被拒绝；
- Router 选择无效 Tool 时进入 DEGRADED 或 FAILED，不扩大能力；
- 普通问答不创建无意义的动态 Plan。

### Phase 2：TaskState 和 Plan Executor

交付物：

1. TaskState 持久化；
2. Plan/Step 状态机；
3. 每步成功条件和最大尝试次数；
4. parent/child 统一预算；
5. 失败后的重试和重规划策略。

验收：

- 进程中断后可恢复到当前 Step；
- 相同 Tool 参数不会无限重复；
- parent/child 总 Tool 调用和 token 不超过 Run 预算；
- Plan 不能跳过必需的前置证明。

### Phase 3：智能问数闭环

交付物：

1. metric_catalog_search_tool；
2. semantic_data_query_tool；
3. data_quality_check_tool；
4. data_freshness_check_tool；
5. deterministic_analysis_tool；
6. AnalysisReport Artifact；
7. DataContract、queryHash、lineage 和 permissionProof。

验收：

- “华东销售额下降原因”可返回真实数据、口径、时间、来源和限制；
- SQL、物理表、任意 URL、超范围时间和未授权字段都会被拒绝；
- 同比/环比由程序计算，模型只负责解释；
- 数据过期或权限证明缺失不会返回 SUCCESS。

### Phase 4：业务 Validator 和 Evaluator

交付物：

1. BusinessDataValidator；
2. ArtifactValidator；
3. KnowledgeEvidenceEvaluator；
4. ReportEvaluator；
5. 有限修复和重新规划；
6. INSUFFICIENT_EVIDENCE、VALIDATION_INCOMPLETE 等状态。

验收：

- 合计不一致、口径冲突、数据新鲜度不足会阻断；
- 可修复问题进入有限修复，不可修复问题进入澄清或失败；
- Evaluator 不能覆盖确定性校验失败。

### Phase 5：审批和恢复

交付物：

1. Python needs_approval 工具；
2. SDK state 序列化和恢复；
3. Java AWAITING_APPROVAL 运行状态；
4. approve/reject 接口；
5. 审批过期、重复提交和幂等处理。

验收：

- 发布、写入、发起流程会暂停；
- 用户批准后从原 Run 恢复；
- 审批期间不能修改 Snapshot 或偷偷换 Tool；
- 重复批准不会重复执行副作用。

### Phase 6：可观测性、成本和性能

交付物：

1. 全链路 Trace；
2. Route、Tool、Agent、Validator 指标；
3. token、工具次数、延迟和失败原因统计；
4. 子 Agent 并发和超时策略；
5. 限流、熔断和取消传播。

验收：

- 单个 run 可以还原完整的 route、plan、step、tool、evaluator 和 artifact；
- 复杂任务超预算时进入可解释的 FAILED；
- 取消请求可传播到 Python、SDK、Tool Gateway 和领域服务。

## 16. 测试方案

### 16.1 单元测试

- ContextBuilder：消息去重、system 过滤、不可信上下文和大小限制；
- RoutePolicy：模式、allowlist、权限求交和 fallback；
- PlanValidator：依赖、状态、循环和最大重规划次数；
- BudgetManager：turn、token、Tool call、child depth；
- BusinessDataValidator：计算、口径、新鲜度和血缘；
- OutcomeMapper：状态和 finishReason 映射；
- ApprovalManager：approve、reject、expired、duplicate。

### 16.2 Contract 测试

- Python Tool input/output Schema；
- Java Tool Gateway request/response；
- NDJSON event；
- Agent runtime fixture；
- Artifact envelope；
- TaskState 序列化兼容；
- Snapshot hash 和 capability revision。

### 16.3 场景测试

至少覆盖：

1. 简单术语问答；
2. 缺少时间范围的问数；
3. 指标存在多个口径；
4. 用户无权访问目标模型；
5. 数据查询超时；
6. 数据新鲜度不足；
7. 同比计算；
8. 多维度下降归因；
9. 看板 Render JSON 校验失败后修复；
10. 发布前人工审批；
11. 审批后恢复；
12. child Agent 失败；
13. Tool output 含提示注入；
14. parent/child 超预算；
15. 进程中断后恢复。

### 16.4 负向安全测试

- 注入 SQL；
- 注入物理表和字段；
- 注入 endpoint；
- 伪造 permissionProof；
- 伪造 ValidationReport.valid=true；
- 修改已发布 Snapshot；
- 使用未授权 KB；
- 使用未授权 Agent；
- 重放旧 approval；
- 重复执行发布 Tool。

## 17. 观测指标和 SLO

建议第一阶段记录：

| 指标 | 说明 |
|---|---|
| agent_route_clarification_rate | Router 判定需要补充信息的比例 |
| agent_route_fallback_rate | Route 校验失败回退比例 |
| plan_completion_rate | Plan 在预算内完成比例 |
| plan_replan_rate | 发生重新规划的比例 |
| tool_success_rate | 按 Tool code/version 统计 |
| business_validation_failure_rate | 数值、口径、新鲜度、权限失败比例 |
| insufficient_evidence_rate | 证据不足比例 |
| approval_wait_duration | 审批等待时间 |
| agent_run_latency | P50/P95/P99 |
| agent_run_token_cost | parent + child + evaluator 总成本 |
| data_query_freshness | 查询结果相对业务要求的延迟 |

不要只看模型回答成功率。企业智能体的主要质量指标应包括：

~~~text
回答是否有数据证明；
查询是否符合权限；
数字是否由确定性逻辑计算；
是否正确区分成功、澄清、证据不足和审批等待；
是否可以从同一个 Run 恢复；
是否能解释每一步为什么发生。
~~~

## 18. 风险和待决策项

### 18.1 必须尽快决定

1. 生产 Tool 的最终权限裁决在 Java Gateway 还是 Python facade；
2. 是否保留 PYTHON_LOCAL，以及它是否允许覆盖 Java capability；
3. TaskState 存储位置和 SDK state 加密/序列化格式；
4. Session、历史 replay 和 SDK session 的唯一策略；
5. 指标目录、虚拟模型和知识库之间的 revision 对齐方式；
6. 查询结果的最大行数、列数、时间跨度和缓存策略；
7. 报表发布是否必须人工审批；
8. 可信度低于阈值时是澄清、降级回答还是直接失败。

### 18.2 主要风险

| 风险 | 缓解方式 |
|---|---|
| Router 与实际执行脱节 | RouteDecision 必须由 Controller 消费，增加分支测试 |
| Agent 自由调用不该用的 Tool | capability intersection + per-step tool filter |
| 数字幻觉 | deterministic_analysis + sourceProof + BusinessDataValidator |
| Prompt 注入 | untrusted data envelope、Tool schema、输出不直接执行 |
| 子 Agent 成本爆炸 | 全局预算、深度限制、重复调用检测 |
| 审批绕过 | SDK interruption + Java 状态机 + 幂等 |
| Python/Java 权限不一致 | 单一最终裁决点和 capability revision |
| 知识库过期 | sourceRevision、updatedAt、实时 Tool 校验 |
| 历史上下文膨胀 | TaskState 摘要、消息窗口和有效证据引用 |

## 19. 最小可行落地顺序

如果需要快速推进，建议不要同时建设全部能力，按以下最小闭环执行：

~~~text
第一步：RouteDecision 真正驱动 CLARIFY / DIRECT / DELEGATE
第二步：TaskState + Plan/Step 状态机
第三步：metric_catalog + semantic_query + deterministic_analysis
第四步：业务 Validator 和正确状态码
第五步：Approval interruption + state resume
第六步：完整 Trace、成本和性能治理
~~~

第一阶段完成后，即可把当前代码从：

~~~text
分析请求（审计）
  -> Root Agent 自主选择
  -> 输出固定 SUCCESS
~~~

升级为：

~~~text
结构化分析
  -> 程序路由
  -> 受限计划
  -> ReAct 执行
  -> 业务校验
  -> 正确状态
  -> 可恢复任务
~~~

## 20. 验收清单

### 路由与计划

- [ ] CLARIFY 不会进入查询或写操作；
- [ ] DELEGATE 只能调用已授权 Agent；
- [ ] TOOL 只能使用 RouteDecision 中的工具；
- [ ] 动态任务拥有 Plan、Step、成功条件和预算；
- [ ] 失败后只能有限重试或重规划；
- [ ] 模型不能伪造 Step 成功。

### 智能问数

- [ ] 指标、模型和字段均来自已发布目录；
- [ ] 查询不接受 SQL 和物理表；
- [ ] 查询结果包含 queryHash、revision、freshness 和 lineage；
- [ ] 同比、环比、贡献度由确定性工具计算；
- [ ] 输出包含口径、时间、数据来源和限制；
- [ ] 无权限、无数据、过期数据和证据不足不会返回 SUCCESS。

### 报表和看板

- [ ] DataContract 是 Render 的前置证明；
- [ ] Render JSON 通过确定性校验；
- [ ] 预览和发布使用 hash/proof 绑定；
- [ ] 发布 Tool 需要审批；
- [ ] 发布失败不会覆盖已有页面；
- [ ] Artifact 可下载、可追踪、可复核。

### 状态、审批和审计

- [ ] 状态可区分成功、澄清、证据不足、审批等待和失败；
- [ ] 审批通过后从原 Run 恢复；
- [ ] 重复审批不会重复执行副作用；
- [ ] TaskState 可跨进程恢复；
- [ ] Trace 能关联 route、plan、step、Tool、Agent、Validator 和 Artifact；
- [ ] parent/child 总成本可统计和限制。

## 21. 参考实现与官方 SDK 能力

当前项目使用 openai-agents==0.18.2。升级或接入新能力时，需要先建立版本兼容测试，不应直接把最新文档 API 当作当前版本必然可用。

OpenAI Agents SDK 当前官方能力与本方案的映射如下：

| 本方案能力 | SDK 原语 | 仍需业务层实现的部分 |
|---|---|---|
| ReAct | Runner.run / Runner.run_streamed | Plan Step、预算和状态机 |
| Agent 协作 | agent.as_tool()、handoffs | 企业路由、权限求交、版本固定 |
| Tool | function_tool、MCP/Hosted tools | Tool Gateway、业务 schema、幂等 |
| Guardrail | input/output/tool guardrails | 指标、数据血缘和业务质量规则 |
| Approval | needs_approval、interruptions、to_state() | 审批持久化、租户权限、过期和恢复协议 |
| Session/State | SDK result/history/session/state | 企业 TaskState、数据库和跨服务恢复 |
| Trace | SDK tracing/hooks/events | 业务审计、指标、SLO 和脱敏 |

参考官方文档：

- [Agents SDK Orchestration and handoffs](https://developers.openai.com/api/docs/guides/agents/orchestration)
- [Running agents](https://developers.openai.com/api/docs/guides/agents/running-agents)
- [Guardrails and human review](https://developers.openai.com/api/docs/guides/agents/guardrails-approvals)
- [Results and state](https://developers.openai.com/api/docs/guides/agents/results)
