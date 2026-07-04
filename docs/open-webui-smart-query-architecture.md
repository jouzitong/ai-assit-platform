# 基于 Open WebUI 构建智能问数平台：可执行架构与实施方案

> 文档版本：v1.0  
> 适用场景：Open WebUI 前端 + Java/Spring Boot 后端 + AI Workflow + 智能问数 + Render JSON  
> 核心目标：保留 Open WebUI 的聊天交互能力，由自研 Java 后端实现智能问数流程、Agent Node、Tool、数据查询、结果评估和动态页面渲染。

---

## 1. 项目目标

本项目基于 Open WebUI 前端源码构建统一 AI 交互入口，不使用 Open WebUI Python 后端。

系统需要支持：

1. AI 聊天与会话管理。
2. 根据用户问题识别业务意图。
3. 根据意图选择并执行指定 Workflow。
4. WorkflowEngine 控制流程边界和节点流转。
5. Node 作为受控 Agent，自主决定当前节点内部如何完成任务。
6. Node 可以按需调用知识库、元数据、查询、校验等 Tool。
7. 根据最初的 Task Contract 判断当前结果是否真正满足用户目标。
8. 查询结果可以生成 Render JSON。
9. Open WebUI 前端解析 Render JSON，渲染列表、详情、看板、报表等页面。
10. 全流程通过 SSE/WebSocket 向前端展示执行进度、节点状态、工具调用和最终结果。

核心原则：

```text
Open WebUI 负责交互体验
Java 后端负责业务和 AI 执行
WorkflowEngine 负责流程控制
NodeAgent 负责节点内部智能
Tool 负责真实能力
Evaluator 负责判断是否完成
Render Engine 负责最终业务展示
```

---

# 2. 总体架构

```text
┌───────────────────────────────────────────────┐
│              Open WebUI Frontend              │
│                                               │
│ Chat / Session / Message / Model / SSE       │
│ Thinking / Tool Event / Render JSON          │
└──────────────────────┬────────────────────────┘
                       │
                       ▼
┌───────────────────────────────────────────────┐
│          openwebui-adapter（Java）             │
│                                               │
│ Config / Auth / Models / Chats / Completion  │
│ Open WebUI 前端协议适配                       │
└──────────────────────┬────────────────────────┘
                       │
                       ▼
┌───────────────────────────────────────────────┐
│               AI Chat Service                 │
│                                               │
│ Conversation / Message / Run / Event         │
└──────────────────────┬────────────────────────┘
                       │
                       ▼
┌───────────────────────────────────────────────┐
│              Intent Router                    │
│                                               │
│ general-chat                                  │
│ smart-query                                   │
│ smart-analysis                                │
│ app-builder                                   │
└──────────────────────┬────────────────────────┘
                       │
                       ▼
┌───────────────────────────────────────────────┐
│             Workflow Engine                   │
│                                               │
│ WorkflowDefinition                            │
│ NodeExecutor                                  │
│ TransitionResolver                            │
│ RuntimePolicy                                 │
└──────────────────────┬────────────────────────┘
                       │
                       ▼
┌───────────────────────────────────────────────┐
│                Node Runtime                   │
│                                               │
│ NodeDefinition / NodeContract                 │
│ ContextView / Scoped Tools                    │
│ AgentExecutor / Validator                     │
└───────────┬──────────────────────┬─────────────┘
            │                      │
            ▼                      ▼
┌─────────────────────┐  ┌─────────────────────┐
│     Tool Registry   │  │     Data Engine     │
│                     │  │                     │
│ Knowledge Search    │  │ Query DSL           │
│ Metadata Search     │  │ API / SQL / RPC     │
│ Query Validate      │  │ Permission          │
│ Render Validate     │  │ Audit               │
└─────────────────────┘  └─────────────────────┘
            │
            ▼
┌───────────────────────────────────────────────┐
│              Render Service                   │
│                                               │
│ Render Plan / Render JSON / Page Version     │
└──────────────────────┬────────────────────────┘
                       │
                       ▼
┌───────────────────────────────────────────────┐
│           Svelte Render Engine                │
│                                               │
│ List / Detail / Dashboard / Report           │
└───────────────────────────────────────────────┘
```

---

# 3. Open WebUI 的使用边界

## 3.1 保留

第一阶段保留：

```text
登录页面
当前用户
基础系统配置
模型列表
会话列表
消息列表
创建会话
发送消息
流式输出
停止生成
消息重新生成
基础设置
```

后续按需保留：

```text
文件上传
知识库入口
Tool 展示
模型配置
语音
图片
分享
文件夹
```

## 3.2 不直接复刻 Open WebUI Python 后端

Java 后端不应完全复制 Open WebUI 内部业务模型。

增加独立模块：

```text
openwebui-adapter
```

职责仅限：

```text
Open WebUI Request
        ↓
协议转换
        ↓
平台标准 Service
        ↓
协议转换
        ↓
Open WebUI Response
```

后续 Open WebUI 前端升级，只需要重点维护 Adapter，不影响核心 AI 平台。

---

# 4. 第一阶段需要对接的接口

## 4.1 基础配置

```text
GET /api/config
```

用于前端启动和功能开关。

## 4.2 登录认证

```text
POST /api/v1/auths/signin
GET  /api/v1/auths/
POST /api/v1/auths/signout
```

内部调用现有用户服务和 JWT 体系。

## 4.3 模型列表

```text
GET /api/models
```

注意：这里的“模型”可以是实际 LLM，也可以是业务 Agent。

例如：

```text
general-chat        通用聊天
smart-query         智能问数
smart-analysis      智能分析
app-builder         智能应用生成
```

## 4.4 会话管理

```text
GET    /api/v1/chats/
POST   /api/v1/chats/new
GET    /api/v1/chats/{id}
POST   /api/v1/chats/{id}
DELETE /api/v1/chats/{id}
```

## 4.5 聊天执行

兼容入口：

```text
POST /api/chat/completions
```

内部转换为平台执行模型：

```text
POST /api/ai/runs
GET  /api/ai/runs/{runId}/events
```

---

# 5. AI 聊天完整执行流程

```text
用户输入
  ↓
前端创建 User Message
  ↓
前端创建 Assistant 占位消息
  ↓
发送 Chat Request
  ↓
创建 ChatRun
  ↓
Intent Router 识别业务类型
  ↓
选择 WorkflowDefinition
  ↓
WorkflowEngine 执行
  ↓
Node 执行
  ↓
Tool 调用
  ↓
结果校验
  ↓
节点建议下一步
  ↓
WorkflowEngine 最终决策
  ↓
完成 / 修复 / 回退 / 继续
  ↓
生成 Render JSON
  ↓
前端渲染
```

建议把一次用户请求抽象为：

```text
Chat Message
    ↓
Chat Run
    ↓
Workflow Run
    ↓
Node Run
    ↓
Tool Run
```

---

# 6. 智能问数 Workflow

推荐流程：

```text
START
  ↓
QueryPlanningNode
  ↓
QueryBuildNode
  ↓
QueryExecuteNode
  ↓
ResultAnalysisNode
  ↓
ResultEvaluationNode
  ├─ 满足目标 → RenderPlanningNode
  ├─ 查询方案错误 → QueryBuildNode
  ├─ 数据缺失 → QueryExecuteNode
  ├─ 意图理解错误 → QueryPlanningNode
  └─ 无法继续 → Clarify / Partial Complete
  ↓
RenderBuildNode
  ↓
RenderValidationNode
  ├─ 通过 → COMPLETE
  └─ 失败 → RenderBuildNode
```

节点类型建议：

| 节点 | 类型 | 主要职责 |
|---|---|---|
| QueryPlanningNode | AGENT | 理解用户目标，建立 Task Contract |
| QueryBuildNode | AGENT | 构建 Query Plan |
| QueryExecuteNode | SYSTEM | 确定性执行 Query Plan |
| ResultAnalysisNode | AGENT | 趋势、异常、比较、结论 |
| ResultEvaluationNode | HYBRID | AI 判断 + 程序规则验证 |
| RenderPlanningNode | AGENT | 选择页面类型、布局、组件 |
| RenderBuildNode | AGENT | 生成 Render JSON |
| RenderValidationNode | SYSTEM | Schema、组件、数据绑定校验 |

原则：

```text
理解、规划、分析、生成 → Agent Node
查询、存储、校验、转换 → System Node
```

---

# 7. WorkflowDefinition 设计

WorkflowDefinition 不定义唯一死顺序，而定义：

```text
有哪些节点
起始节点
允许的流转路径
默认路径
条件
重试策略
循环策略
超时策略
```

示例：

```json
{
  "id": "smart-query",
  "version": "1.0",
  "startNodeId": "query-planning",
  "nodes": [
    "query-planning",
    "query-build",
    "query-execute",
    "result-analysis",
    "result-evaluation",
    "render-planning",
    "render-build",
    "render-validation"
  ],
  "transitions": [
    {
      "from": "query-planning",
      "to": "query-build",
      "type": "DEFAULT"
    },
    {
      "from": "query-build",
      "to": "query-execute",
      "type": "DEFAULT"
    },
    {
      "from": "query-execute",
      "to": "result-analysis",
      "type": "DEFAULT"
    },
    {
      "from": "result-analysis",
      "to": "result-evaluation",
      "type": "DEFAULT"
    },
    {
      "from": "result-evaluation",
      "to": "render-planning",
      "condition": "evaluation.passed == true"
    },
    {
      "from": "result-evaluation",
      "to": "query-build",
      "condition": "evaluation.problemType == 'QUERY_PLAN_ERROR'"
    }
  ],
  "policy": {
    "maxTotalSteps": 30,
    "maxNodeRetries": 3,
    "maxWorkflowDurationSeconds": 300
  }
}
```

核心边界：

```text
WorkflowDefinition 定义“允许怎么走”
Node 提出“建议怎么走”
WorkflowEngine 决定“最终怎么走”
```

---

# 8. WorkflowEngine 设计

WorkflowEngine 核心职责：

```text
加载 WorkflowDefinition
创建 WorkflowContext
确定当前 Node
执行 Node
接收 NodeExecutionResult
写入 Context
解析 TransitionProposal
检查流程约束
生成 TransitionDecision
执行下一个 Node
直到 COMPLETE / FAIL / WAIT / CLARIFY
```

核心伪流程：

```text
while workflow is running:

    currentNode = resolveCurrentNode()

    result = nodeExecutor.execute(currentNode, context)

    contextWriter.write(result)

    proposal = result.transitionProposal

    decision = transitionResolver.resolve(
        workflowDefinition,
        workflowState,
        context,
        proposal
    )

    apply(decision)
```

决策优先级：

```text
1. 系统安全和权限规则
2. WorkflowDefinition
3. Retry / Loop / Timeout Policy
4. Edge Condition
5. Node TransitionProposal
6. Default Transition
```

---

# 9. Node 的核心模型

Node 不应该只是一个 Java 类。

每个 Node 需要：

```text
NodeDefinition
NodeContract
NodeRuntime
NodeExecutor
```

## 9.1 NodeDefinition

回答：

```text
这个节点是谁？
是什么类型？
使用哪个执行器？
```

## 9.2 NodeContract

回答：

```text
目标是什么？
可以读取哪些 Context？
可以使用哪些 Tool？
输出必须是什么？
什么条件算完成？
最多执行多少步？
```

示例：

```json
{
  "nodeId": "query-build",
  "name": "查询方案构建",
  "type": "AGENT",
  "goal": "根据用户目标、业务知识和元数据生成可执行 Query Plan",
  "contextSelectors": [
    "request",
    "taskContract",
    "knowledge",
    "previousQueryPlan",
    "previousErrors"
  ],
  "tools": [
    "knowledge_search",
    "metadata_search",
    "metric_search",
    "query_validate"
  ],
  "output": {
    "type": "query-plan",
    "schema": "query-plan-v1"
  },
  "completion": {
    "validators": [
      "schema-validator",
      "metadata-validator",
      "permission-validator"
    ]
  },
  "policy": {
    "maxSteps": 8,
    "maxToolCalls": 10,
    "timeoutSeconds": 120
  }
}
```

---

# 10. NodeRuntime 设计

NodeAgent 不应直接获得整个 WorkflowContext。

运行前，由系统构建：

```text
NodeRuntime
  ├─ NodeDefinition
  ├─ NodeContract
  ├─ ContextView
  ├─ ScopedToolSet
  ├─ OutputContract
  ├─ ExecutionHistory
  └─ RuntimePolicy
```

NodeAgent 的真实执行公式：

```text
NodeAgent
=
明确目标
+
裁剪后的 Context
+
限定 Tool
+
输出协议
+
完成条件
+
执行限制
```

而不是：

```text
整个 Context
+
全部 Tool
+
让 AI 自己决定一切
```

---

# 11. WorkflowContext 设计

不要采用完全松散的 Map，也不要全部定义成固定 DTO。

采用：

```text
稳定公共结构
+
标准业务区
+
扩展区
+
Artifact 引用
```

建议结构：

```json
{
  "request": {},
  "user": {},
  "conversation": {},
  "workflow": {},
  "taskContract": {},
  "knowledge": {},
  "query": {
    "plan": {},
    "resultArtifactId": ""
  },
  "analysis": {},
  "render": {
    "plan": {},
    "pageArtifactId": ""
  },
  "errors": [],
  "executionHistory": [],
  "extensions": {}
}
```

## 11.1 ContextView

每个 Node 有自己的 ContextBuilder。

例如 QueryBuildNode 只读取：

```text
taskContract
knowledge
metadata
previousQueryPlan
previousErrors
```

RenderBuildNode 只读取：

```text
taskContract
queryResultSummary
analysisResult
renderPlan
availableComponents
previousRenderErrors
```

目的：

```text
减少 Token
避免上下文污染
保持节点边界
提高 Agent 准确率
```

---

# 12. Node Agent 内部执行机制

Agent Node 内部允许自主循环：

```text
分析
  ↓
发现信息不足
  ↓
调用 Tool
  ↓
观察 Tool Result
  ↓
继续分析
  ↓
构建结果
  ↓
提交结果
  ↓
Validator 校验
  ├─ 成功 → Node Completed
  └─ 失败 → 将错误反馈给 Agent 修正
```

例如 QueryBuildNode：

```text
读取 Task Contract
  ↓
调用 MetadataSearchTool
  ↓
调用 MetricSearchTool
  ↓
生成 Query Plan
  ↓
调用 QueryValidateTool
  ↓
失败
  ↓
根据错误修正
  ↓
再次校验
  ↓
成功
```

WorkflowEngine 不关心 Node 内部调用几次 Tool。

WorkflowEngine 只关心：

```text
节点是否完成
节点输出是什么
节点建议下一步去哪
```

---

# 13. Tool 设计

所有真实能力通过 Tool 提供。

推荐 Tool：

```text
KnowledgeSearchTool
MetadataSearchTool
MetricSearchTool
EntityResolveTool
QueryValidateTool
QueryExecuteTool
StatisticsTool
PermissionCheckTool
ComponentSearchTool
RenderValidateTool
```

全局：

```text
ToolRegistry
```

节点运行时：

```text
ToolRegistry
  ↓
ToolScopeResolver
  ↓
ScopedToolSet
```

原则：

```text
不是所有 Tool 都暴露给所有 Node
```

例如：

```text
QueryBuildNode:
- knowledge_search
- metadata_search
- metric_search
- query_validate
```

```text
RenderBuildNode:
- component_search
- layout_search
- render_validate
```

---

# 14. Node 输入输出协议

采用：

```text
固定 Envelope
+
动态 Payload
```

标准返回：

```json
{
  "nodeId": "result-evaluation",
  "status": "SUCCESS",
  "summary": "当前查询结果缺少2024年业绩数据",
  "confidence": 0.94,
  "payloadType": "result-evaluation",
  "payloadVersion": "1.0",
  "payload": {},
  "artifacts": [],
  "transitionProposal": {}
}
```

固定字段：

```text
nodeId
status
summary
confidence
payloadType
payloadVersion
artifacts
transitionProposal
```

动态字段：

```text
payload
```

不要：

```text
全部使用强类型 DTO
```

也不要：

```text
全部使用 Map<String,Object>
```

原则：

```text
框架强类型
业务 Payload 可扩展
关键 Payload 使用 Schema 管理
```

---

# 15. TransitionProposal 与 TransitionDecision

Node 有建议权，没有流程控制权。

## 15.1 Node 返回 TransitionProposal

```json
{
  "action": "GOTO",
  "targetNodeId": "query-build",
  "reasonCode": "MISSING_DATA",
  "reason": "当前结果缺少2024年业绩数据",
  "confidence": 0.94,
  "instructions": {
    "missingYears": [2024]
  }
}
```

支持动作：

```text
GOTO
CONTINUE
RETRY
COMPLETE
WAIT
CLARIFY
FAIL
```

## 15.2 WorkflowEngine 生成 TransitionDecision

```json
{
  "action": "GOTO",
  "targetNodeId": "query-build",
  "acceptedProposal": true,
  "decisionSource": "NODE_PROPOSAL",
  "reason": "目标节点符合 WorkflowDefinition 且未超过重试限制",
  "confidence": 0.94
}
```

Engine 检查：

```text
目标节点是否存在
Definition 是否允许
Edge Condition 是否满足
是否超过 Retry
是否形成死循环
可信度是否足够
是否违反权限和安全策略
```

---

# 16. Task Contract：如何知道最终是不是用户要的结果

QueryPlanningNode 的核心输出不只是“意图”，而是：

```text
Goal
+
Task Contract
+
Result Contract
```

示例：

用户：

```text
查询张三近三年的工作业绩，并分析一下。
```

生成：

```json
{
  "intent": "employee_performance_analysis",
  "goal": "查询并分析张三近三年的业绩表现",
  "subject": {
    "type": "employee",
    "name": "张三"
  },
  "requirements": [
    "查询张三身份信息",
    "获取近三年业绩数据",
    "分析年度变化趋势",
    "给出综合结论"
  ],
  "expectedResult": {
    "requiredFacts": [
      "employee",
      "yearlyPerformance"
    ],
    "requiredAnalysis": [
      "trend",
      "bestYear",
      "overallEvaluation"
    ],
    "preferredViews": [
      "profile",
      "lineChart",
      "analysis"
    ]
  },
  "ambiguities": [],
  "confidence": 0.94
}
```

ResultEvaluationNode：

```text
Task Contract
        VS
Actual Result
```

输出：

```json
{
  "passed": false,
  "score": 0.72,
  "problemType": "DATA_MISSING",
  "missing": [
    "2024年业绩数据"
  ],
  "transitionProposal": {
    "action": "GOTO",
    "targetNodeId": "query-build",
    "confidence": 0.94
  }
}
```

这就是系统判断“是否完成”的核心机制。

---

# 17. QueryBuildNode 实现

输入：

```text
Task Contract
Knowledge
Metadata
Previous Query Plan
Previous Errors
```

可用 Tool：

```text
KnowledgeSearchTool
MetadataSearchTool
MetricSearchTool
EntityResolveTool
QueryValidateTool
```

输出：

```text
Query Plan / Query DSL
```

不建议 AI 直接输出最终 SQL。

示例：

```json
{
  "queries": [
    {
      "id": "q1",
      "purpose": "查询张三基本信息",
      "model": "employee",
      "filters": {
        "name": {
          "op": "eq",
          "value": "张三"
        }
      }
    },
    {
      "id": "q2",
      "purpose": "查询近三年工作业绩",
      "model": "employee_performance",
      "dependsOn": ["q1"],
      "filters": {
        "employeeId": "{{q1.id}}",
        "year": {
          "op": "gte",
          "value": 2023
        }
      }
    }
  ]
}
```

---

# 18. QueryExecuteNode 实现

QueryExecuteNode 为 System Node。

流程：

```text
Query Plan
  ↓
权限检查
  ↓
Query DSL 校验
  ↓
Data Query Engine
  ↓
SQL / API / RPC
  ↓
Result Artifact
```

Node 不做 AI Agent Loop。

输出：

```text
结果摘要
Artifact ID
列信息
数据量
异常信息
```

大数据结果不直接写入 WorkflowContext。

使用 Artifact：

```json
{
  "artifactId": "artifact-query-result-001",
  "type": "QUERY_RESULT"
}
```

---

# 19. RenderBuildNode 与 Render JSON

流程：

```text
Task Contract
+
Query Result Summary
+
Analysis Result
+
Component Registry
        ↓
RenderPlanningNode
        ↓
RenderBuildNode
        ↓
RenderValidationNode
```

Render JSON 协议必须独立于 Svelte。

不要：

```json
{
  "component": "SomeSvelteComponent"
}
```

应该：

```json
{
  "type": "dataTable"
}
```

前端：

```text
dataTable → Svelte DataTable
barChart  → Svelte BarChart
statCard  → Svelte StatCard
```

推荐页面类型：

```text
list
detail
dashboard
report
canvas
```

布局：

```text
list       → 流式业务布局
dashboard  → Grid
report     → 文档流
canvas     → 自由定位
```

不要所有页面都使用自由画布。

---

# 20. 前端 Render Engine

建议目录：

```text
src/lib/render/
  ├─ RenderPage.svelte
  ├─ RenderNode.svelte
  ├─ componentRegistry.ts
  ├─ schema.ts
  ├─ dataSourceManager.ts
  ├─ eventEngine.ts
  ├─ layouts/
  │   ├─ ListLayout.svelte
  │   ├─ DetailLayout.svelte
  │   ├─ DashboardLayout.svelte
  │   ├─ ReportLayout.svelte
  │   └─ CanvasLayout.svelte
  └─ components/
      ├─ DataTable.svelte
      ├─ StatCard.svelte
      ├─ BarChart.svelte
      ├─ LineChart.svelte
      ├─ ProfileCard.svelte
      └─ AnalysisCard.svelte
```

核心模块：

```text
Schema Validator
Component Registry
Layout Renderer
DataSource Manager
Event Engine
State Store
```

---

# 21. SSE / WebSocket 事件体系

建议：

```text
HTTP：发起命令和普通 CRUD
SSE：当前 AI Run 主执行流
WebSocket：全局通知和跨页面事件
```

事件建议：

```text
conversation.created
conversation.updated

run.started
run.completed
run.failed

node.started
node.progress
node.completed
node.failed

thinking.summary

tool.started
tool.completed
tool.failed

transition.proposed
transition.decided

render.started
render.completed

global.error
```

示例：

```text
event: node.started
data: {
  "runId": "run-001",
  "nodeId": "query-build",
  "title": "正在构建查询方案"
}
```

```text
event: tool.completed
data: {
  "nodeId": "query-build",
  "tool": "metadata_search",
  "summary": "找到2个相关数据模型"
}
```

```text
event: transition.decided
data: {
  "from": "result-evaluation",
  "to": "query-build",
  "reason": "缺少2024年业绩数据"
}
```

```text
event: render.completed
data: {
  "pageId": "page-001"
}
```

前端不展示模型原始私有推理，只展示：

```text
节点目标
执行状态
工具调用
结果摘要
流转原因
可信度
```

---

# 22. 核心 Java 模块建议

```text
ai-data-platform
  ├─ openwebui-adapter
  ├─ chat-service
  ├─ workflow-core
  ├─ workflow-definition
  ├─ node-runtime
  ├─ agent-executor
  ├─ tool-core
  ├─ knowledge-service
  ├─ metadata-service
  ├─ query-service
  ├─ render-service
  ├─ artifact-service
  └─ audit-service
```

核心类：

```text
WorkflowDefinition
WorkflowEngine
WorkflowContext
WorkflowState

NodeDefinition
NodeContract
NodeRuntime
NodeExecutor
AgentNodeExecutor
SystemNodeExecutor
NodeExecutionResult

TransitionProposal
TransitionResolver
TransitionDecision

ToolDefinition
ToolRegistry
ToolScopeResolver
ToolExecutionResult

TaskContract
Artifact
```

---

# 23. 数据持久化建议

至少保存：

## 23.1 Chat

```text
conversation
message
```

## 23.2 AI Run

```text
ai_run
workflow_run
node_run
tool_run
```

## 23.3 执行历史

保存：

```text
输入摘要
输出摘要
使用模型
Token
耗时
工具调用
错误
重试
TransitionProposal
TransitionDecision
```

## 23.4 Artifact

```text
query_result
render_json
report
file
sql
```

目的：

```text
可恢复
可审计
可回放
可调试
可评估
```

---

# 24. 第一阶段实施顺序

## Phase 1：Open WebUI 前端最小接入

完成：

```text
前端启动
登录
当前用户
基础配置
模型列表
会话列表
创建会话
发送消息
SSE 输出
消息保存
```

验收：

```text
用户可以正常登录并完成普通 AI 对话
```

---

## Phase 2：建立 Workflow Core

完成：

```text
WorkflowDefinition
WorkflowEngine
WorkflowContext
NodeExecutor
TransitionProposal
TransitionDecision
执行日志
```

先实现固定测试流程：

```text
A → B → C
```

再实现：

```text
A → B
    ├→ C
    └→ D
```

验收：

```text
Node 可提出下一节点建议
Engine 可接受或拒绝建议
所有流转有日志
```

---

## Phase 3：实现 Node Agent

完成：

```text
NodeContract
ContextView
Tool Scope
Agent Loop
Output Parser
Validator
```

先实现：

```text
QueryPlanningNode
QueryBuildNode
```

验收：

```text
Node 可以自主调用 Tool
Node 只能使用被授权 Tool
Node 输出经过 Schema 校验
```

---

## Phase 4：智能问数 MVP

实现：

```text
QueryPlanningNode
QueryBuildNode
QueryExecuteNode
ResultEvaluationNode
```

先不做复杂分析和 Render。

验收案例：

```text
查询张三近三年业绩
```

系统可以：

```text
识别主体
查询知识
构建 Query Plan
执行查询
判断结果是否完整
缺失时回退补查
```

---

## Phase 5：Render JSON

实现：

```text
RenderPlanningNode
RenderBuildNode
RenderValidationNode
Svelte Render Engine
```

先支持：

```text
dataTable
statCard
barChart
lineChart
profileCard
analysisCard
```

布局：

```text
list
dashboard
report
```

验收：

```text
AI 查询完成后可直接生成业务页面
```

---

## Phase 6：完善与治理

增加：

```text
权限
审计
Artifact
失败恢复
人工确认
流程版本
Node 版本
Prompt 版本
模型路由
效果评估
成本统计
```

---

# 25. MVP 推荐范围

第一版只实现一个完整闭环：

用户：

```text
查询张三近三年的工作业绩并分析
```

后台：

```text
QueryPlanningNode
  ↓
QueryBuildNode
  ↓
QueryExecuteNode
  ↓
ResultAnalysisNode
  ↓
ResultEvaluationNode
  ↓
RenderBuildNode
```

前端最终展示：

```text
张三基本信息
近三年业绩表格
业绩趋势图
分析结论
```

必须支持一次失败修正：

```text
缺少2024年数据
  ↓
Evaluation 失败
  ↓
返回 QueryBuildNode
  ↓
补查
  ↓
重新 Evaluation
```

这一个场景跑通后，再扩展其他 Workflow。

---

# 26. 最终架构原则

## 原则一：流程可控

```text
WorkflowDefinition 定义允许路径
WorkflowEngine 最终裁决
```

## 原则二：节点智能

```text
NodeAgent 自主决定当前节点内部如何完成
```

## 原则三：工具受限

```text
每个 Node 只能使用 Scoped Tools
```

## 原则四：上下文裁剪

```text
Node 只读取 ContextView，不读取全部 WorkflowContext
```

## 原则五：AI 输出不可信任

```text
AI Output
  ↓
Parser
  ↓
Schema Validator
  ↓
Business Validator
  ↓
Context Writer
```

## 原则六：完成必须可验证

```text
Task Contract
  VS
Actual Result
```

## 原则七：渲染协议独立

```text
Render JSON 不绑定 Svelte
```

## 原则八：确定性操作不要 Agent 化

```text
查询执行、权限、存储、校验由程序负责
```

---

# 27. 最终推荐的核心抽象

```text
WorkflowDefinition
WorkflowEngine
WorkflowContext

NodeDefinition
NodeContract
NodeRuntime
NodeExecutionResult

ToolDefinition
ToolRegistry

TaskContract
ResultEvaluator

TransitionProposal
TransitionDecision

Artifact
RenderJson
```

整个系统可以概括为：

```text
用户问题
  ↓
Intent Router
  ↓
Workflow
  ↓
Task Contract
  ↓
Node Agent
  ↓
Tool
  ↓
Validator
  ↓
Transition Proposal
  ↓
Workflow Engine Decision
  ↓
Evaluator
  ↓
Render JSON
  ↓
Open WebUI Frontend
```

最终边界：

```text
WorkflowEngine：
决定现在执行哪个 Node

NodeContract：
规定 Node 的目标和边界

NodeAgent：
决定当前 Node 内部具体怎么做

Tool：
提供真实能力

Validator：
决定 Node 是否真的完成

Evaluator：
决定用户目标是否真的完成

WorkflowEngine：
决定下一步去哪
```

---

# 28. 结论

本方案不把 Open WebUI 当成完整 AI 后端，而是把它定位为成熟 AI 交互前端。

最终推荐架构：

```text
Open WebUI Frontend
+
Java OpenWebUI Adapter
+
Chat Service
+
WorkflowEngine
+
NodeAgent
+
Scoped Tools
+
Task Contract / Evaluator
+
Data Query Engine
+
Render JSON / Svelte Renderer
```

最关键的一句话：

> **流程由程序控制，节点由 AI 增强；Node 有路径建议权，但没有流程控制权；最终是否完成，由 Task Contract 和 Evaluator 判断。**
