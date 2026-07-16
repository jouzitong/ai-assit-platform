# Chat 服务 Agent-first 重构设计

> 状态：已实施，待部署验收
> 日期：2026-07-16
> 范围：app/app-platform-chat、ai-conversation-ui；Skill 包首期由 Chat 控制面保存，后续可迁移到 app-platform-file
> 核心目标：移除 Node 领域概念，让所有正式聊天统一由 Agent 执行；Workflow 只定义产出物规范与验收规则；保留一个明确隔离的 Spring AI 单轮任务接口。
> 阅读说明：第 2 章记录重构前基线，第 1—25 章保留设计决策与迁移依据；第 26 章是当前代码落地、部署顺序和验收约束的最终准确信息源。当前状态不表示生产数据库、模型凭据或外部 Worker 依赖已经部署。

## 1. 结论

本次重构采用以下最终模型：

1. 正式聊天统一走 Agent。
   - 首页聊天、历史会话续聊、系统设置页助手均由服务端 Agent Runtime 执行。
   - 浏览器只提交消息、Agent 目标和页面上下文，不创建正式 Agent，不接触模型密钥。
   - 首页默认入口通过 HOME_CHAT Entry Binding 绑定一个已发布的根 Agent。

2. 删除 Node 概念，但不把每个旧 Node 机械替换为 Agent。
   - 需求分析、查询规划、SQL 生成、渲染决策、语义复核等推理职责迁为专业 Agent。
   - JSON Schema、格式、权限、SQL 静态安全、Render JSON 结构等确定性逻辑迁为 Tool、Guardrail 或 Workflow Check。
   - 会话创建、消息持久化、超时、取消、事件投递、重试上限等运行基础设施继续由 Java 服务负责。

3. Workflow 不再描述执行图。
   - 不再保存 node、nextCode、sort、edge、phase。
   - Workflow 只定义 required artifacts、JSON Schema、检查规则、完成条件和修复策略。
   - Agent 决定如何分析和调用能力；Workflow 只判断最终产出是否合格。

4. 平台定义自己的中立 AgentManifest。
   - OpenAI Agents SDK Python 和 JavaScript 具有相似概念，但没有可直接跨语言反序列化的通用 Agent JSON。
   - AgentManifest 只保存声明和引用；PythonAdapter、TypeScriptAdapter 分别把它编译成 SDK 对象。
   - Agent 定义、运行时绑定和单次 Run Request 必须分离，禁止把函数、客户端、Session、密钥或 SDK 私有对象存入定义。

5. Skill 升级为不可变、可版本化的文件包。
   - 同时支持 FORM 表单创建和 PACKAGE ZIP 上传。
   - 两种方式最终都生成同一种标准包：SKILL.md 加任意资源文件。
   - 首期以 Agent Skills 规范为兼容基线，保留 scripts、references、assets、templates、data 等目录和未知附加文件。
   - 包先隔离、校验、扫描，再发布；脚本只能在受控 Sandbox 中执行。

6. 简单任务与聊天 Agent 明确分流。
   - 保留 POST /internal/v1/ai/text/generate。
   - 该接口固定为无会话、无 Agent、无 Skill、无 Tool、无 Workflow 的 Spring AI 单轮调用。
   - 如果 modelCode 对应的 clientType 不是 SPRING_AI，直接拒绝。

## 2. 重构前现状事实

本章用于保留 2026-07-16 重构启动时的事实基线，其中“当前”均指重构前实现，不代表第 26 章所列的已落地代码状态。

### 2.1 当前正式聊天是 Java Node 编排

当前后端主链为：

~~~text
ChatTransportProtocolController / ConversationController
  -> ConversationExecutionService
  -> ConversationPreparationService
  -> ConversationIntentRouteService
  -> ConversationIntentAnalyzeService
  -> WorkflowDefinitionFactory
  -> DefaultWorkflowEngineImpl
  -> IWorkflowNode Java Bean
  -> AiExecutionDomainService
  -> Spring AI Provider 或 Python Agent Provider
~~~

关键事实：

- DefaultConversationExecutionServiceImpl 仍依赖 IWorkflowEngine。
- ConversationPreparationService 在准备会话后调用 ConversationIntentRouteService。
- WorkflowDefinitionFactory 只硬编码 simpleChatWorkflow 和 queryRenderWorkflow。
- DefaultWorkflowEngineImpl 逐个查找并执行 IWorkflowNode。
- 数据库中的 ai_chat_workflow_config、ai_chat_workflow_config_node 并没有驱动这条真实主链。

因此，现有 Workflow/Node 页面更接近一套未接入运行时的配置目录，而不是实际编排控制面。

### 2.2 当前 Node 的职责混杂

| 旧职责 | 当前实现 | 目标归属 |
|---|---|---|
| 会话、轮次、用户消息初始化 | ConversationPreparationService 和 ChatMessageNode 语义 | Java 运行基础设施 |
| 意图分析与路由 | ConversationIntentAnalyzeService、ConversationIntentRouteService | 根 Agent 自主分析和选择专业 Agent |
| 普通聊天 | SimpleChatNode | 根聊天 Agent |
| 查询/需求规划 | QueryPlanningNode | requirement-analyst Agent |
| 知识检索与 SQL 预生成 | SqlPreGenerateNode | sql-specialist Agent 加 KB/SQL Tools |
| 结果语义评价 | ResultEvaluateNode | reviewer Agent |
| 格式、Schema、安全检查 | 分散在各 Node | Workflow deterministic checks / Tool Gateway |
| Render JSON 生成 | RenderNode 中的 AI 调用 | render-specialist Agent |
| Render 校验与持久化 | RenderNode 中的 Java 逻辑 | Tool 和 Artifact Infrastructure |
| 跳转、重试、结束 | Workflow Engine | Agent Run Policy 和 Artifact Acceptance |

ResultEvaluateNode 当前遇到 AI 评价异常时存在放行路径。目标体系必须区分：

- 确定性检查：fail-closed，失败即阻断或进入有限修复。
- 语义质量检查：由 Reviewer Agent 返回结构化报告，按 Workflow 策略决定修复、补问或失败。

### 2.3 当前 Python Provider 仍是单 Agent

现有 ai-provider-ai-agent 的执行方式为：

~~~text
AiAgentProvider
  -> AiAgentProcessExecutor
  -> 每次请求启动一个 Python 进程
  -> agent_provider/main.py
  -> 创建一个固定 Agent
~~~

当前限制：

- 输入只有 model、messages、tools、options、ext，没有 Agent Definition Snapshot。
- Python 只创建一个固定名称的 Agent。
- tools 只从源码 TOOL_REGISTRY 选择四个内置工具。
- 数据库 ai_chat_tool 的脚本并未进入 Python Runtime。
- 虽然事件解析支持 handoff 事件，但 Agent 本身没有配置 handoffs 或 agents-as-tools。
- 没有 Agent 版本、Skill 包、Guardrail、Session 策略和真实 Usage。
- 每个请求启动独立进程，后续多 Agent 会放大进程与依赖加载开销。

可复用部分：

- Java 与 Worker 之间的 NDJSON 流协议。
- 文本 delta、Tool activity、Agent activity 的事件转换。
- 当前模型配置解析、Provider SPI、超时和取消基础。

### 2.4 当前 Skill 和 Tool 只是简化目录

当前 AiChatSkillEntity 只有：

~~~text
code, name, desc, content, toolRefs, enabled, remark
~~~

其中 content 在主 DDL 中还是 VARCHAR(255)，无法承载完整 SKILL.md，更不能承载 data、templates、references 或 scripts。

当前 AiChatToolEntity 只有：

~~~text
code, name, desc, content, runtimeType, syncStatus, enabled, remark
~~~

它没有输入输出 Schema、权限策略、Secret 引用、审批策略、版本或多 Runtime Binding；数据库脚本也没有接入真实 Agent Runtime。

此外，以下两个 DDL 来源对 Node/Skill 的列定义已经不一致：

- app/app-platform-chat/config/chat/1.0.0/create_table_ddl.sql
- app/app-platform-chat/data/data-workflow/src/main/resources/db/schema/ai_chat_workflow_init.sql

Agent-first 迁移必须确定一个部署 DDL 来源，不能继续双轨维护。

### 2.5 当前首页和管理端是 model-first / node-first

正式首页路由 /、/c/:sessionId、/g/:groupId/c/:sessionId 均进入 ChatWorkspaceView。当前消息请求只有 modelId，没有 agentCode 或 agentVersion。

系统设置中的“智能体配置”实际仍是 WorkflowSection：

~~~text
/settings/system/workflow?tab=workflow
/settings/system/workflow?tab=node
/settings/system/workflow?tab=skill
/settings/system/workflow?tab=tool
~~~

WorkflowSection 是一个同时承载四类资源的大型组件，当前不存在真正的 Agent 管理页。

另外，系统设置页辅助 Agent 当时仍在浏览器中使用 OpenAI Agents JS 创建单 Agent，并通过 dangerouslyAllowBrowser 直连模型；后端浏览器 Agent 配置接口还会返回 baseUrl 和 apiKey。该链路必须退出正式架构。

## 3. 目标与非目标

### 3.1 目标

- 所有正式聊天请求在服务端解析为 AgentRun。
- 首页没有显式 agentCode 时，由 HOME_CHAT Entry Binding 选择默认已发布 Agent。
- 支持根 Agent 调用多个专业 Agent。
- 同一份 AgentManifest 可由 Python 和 JavaScript Runtime 编译执行。
- Agent、Skill、Tool、Workflow 均支持草稿、校验、发布、版本固定和回滚。
- Skill 表单和 ZIP 上传最终进入同一套包模型。
- Workflow 能可靠检查产出物，并在限定次数内驱动 Agent 修复。
- 保留现有 session、round、message、artifact、activity、SSE、重连和停止能力。
- 简单任务接口保持低开销、无状态，并固定走 Spring AI。

### 3.2 非目标

- 不保留可视化 Node DAG。
- 不把所有确定性代码包装成 Agent。
- 不允许前端上传完整 Agent 定义后直接执行。
- 不把 Python 或 JavaScript 源码对象当成跨语言 Agent 协议。
- 不在首期支持任意未审核 Skill 脚本直接执行。
- 不在首期引入 Responses Hosted Multi-agent；它与 Agents SDK handoff/agent-as-tool 是不同执行契约。
- 不在首期同时使用 SDK Session、OpenAI conversationId 和 previousResponseId。

## 4. 目标领域模型

| 概念 | 定义 | 不负责 |
|---|---|---|
| Conversation | 用户会话、轮次、消息、流式事件和历史 | Agent 定义和产出规范 |
| Agent | 有明确职责、模型、指令、能力和协作关系的执行主体 | 持久化基础设施和任意系统权限 |
| Tool | 有 JSON Schema、实现绑定、权限和审批策略的确定性能力 | 长篇流程知识 |
| Skill | 带 SKILL.md 和资源文件的可版本化知识/工作方法包 | 作为权限 ACL |
| Workflow | 产出物契约、检查规则、完成和修复策略 | Node、边、顺序和执行图 |
| Artifact | Agent Run 产生的结构化或非结构化结果 | 决定如何生成自己 |
| AgentRun | 某个 Agent 版本快照的一次执行 | 修改已发布定义 |
| Runtime Binding | Agent 定义到 Python/TypeScript 执行器、SDK、Sandbox 的部署绑定 | 业务指令和本轮用户输入 |
| Entry Binding | HOME_CHAT 等入口到已发布 Agent 的路由 | 模型密钥和 SDK 对象 |

核心边界是：

~~~text
Agent 决定“怎么做”
Tool 提供“能做什么”
Skill 提供“按什么方法做”
Workflow 判断“产出是否合格”
Conversation 记录“本次发生了什么”
~~~

## 5. 总体架构

~~~mermaid
flowchart LR
    UI["ChatWorkspace / Settings Assistant"] --> API["Conversation Transport API"]
    API --> CHAT["core-ai-chat"]
    CHAT --> ENTRY["Agent Entry Resolver"]
    ENTRY --> SNAPSHOT["Published Snapshot Resolver"]
    SNAPSHOT --> ARUN["core-agent-runtime"]
    ARUN --> PY["OpenAI Agents Python Adapter"]
    ARUN --> JS["OpenAI Agents TypeScript Adapter"]
    PY --> TG["Tool Gateway"]
    JS --> TG
    PY --> SR["Skill Repository / Sandbox"]
    JS --> SR
    PY --> MODEL["Model Config Resolver"]
    JS --> MODEL
    PY --> AC["Artifact Collector"]
    JS --> AC
    AC --> WF["Workflow Acceptance Service"]
    WF --> HISTORY["Session / Round / Message / Artifact / Activity"]
    HISTORY --> UI

    TASK["Stateless Task API"] --> SPRING["Spring AI Stateless Executor"]
    SPRING --> MODEL
~~~

架构分为两个平面：

### 5.1 控制面

- Agent、Skill、Tool、Workflow 的编辑、校验、发布、归档和回滚。
- AgentManifest 和引用关系管理。
- Python/TypeScript 双适配器兼容性检查。
- Skill 包隔离上传、扫描、索引和对象存储。
- Runtime Binding 与 Entry Binding 管理。

### 5.2 运行面

- 只读取已发布、不可变的版本快照。
- 在 Run 开始时固定 Agent、子 Agent、Skill、Tool、Workflow 和模型引用。
- 选择 Runtime Adapter 并归一化事件。
- 收集 Artifact，执行 Workflow 检查和有限修复。
- 记录 runId、agentCode、agentVersion、runtime、snapshotHash 和 traceId。

控制面草稿不得被运行面直接读取。

## 6. Node 到 Agent 的替换原则

Node 可以被 Agent 替代，但替换依据是“是否需要模型自主推理”，而不是类名。

### 6.1 迁为 Agent

- Requirement Analyst：理解目标、上下文、业务术语和缺失信息。
- Data Query Planner：生成结构化查询计划。
- SQL Specialist：结合知识库和数据库工具生成候选 SQL。
- Render Specialist：根据用户目标和数据产出 Render JSON。
- Result Reviewer：进行语义完整性、可解释性和业务质量复核。

### 6.2 迁为 Tool 或 Check

- JSON 语法与 JSON Schema 校验。
- SQL 只读、安全、表字段白名单和方言静态校验。
- Render JSON 树结构校验。
- 权限、租户、数据范围、Secret 和审批检查。
- Artifact 持久化、Render Page upsert、知识库搜索和数据库查询。

### 6.3 保留为 Java 基础设施

- 创建 session、round、message。
- 读取和裁剪历史消息。
- SSE 事件投递、重连、取消和停止。
- 超时、预算、最大 Turn、幂等和故障恢复。
- Artifact 和 Activity 持久化。

### 6.4 首页推荐协作模型

首页采用 manager + agents-as-tools：

~~~text
home-assistant
  ├─ analyze_requirement -> requirement-analyst
  ├─ plan_and_generate_sql -> sql-specialist
  ├─ build_render -> render-specialist
  └─ review_result -> result-reviewer
~~~

根 Agent 始终拥有最终回复权。只有当某个领域专家需要接管后续会话时，才配置 handoff。

OpenAI 官方对两种方式的区分是：

- handoff：控制权和当前分支的最终回复交给专家。
- agent-as-tool：管理 Agent 调用专家完成有边界的子任务，仍由管理 Agent综合最终回复。

参考：[Orchestration and handoffs](https://developers.openai.com/api/docs/guides/agents/orchestration)。

## 7. 中立 AgentManifest

### 7.1 为什么不能直接存 Python/JavaScript Agent

OpenAI 两套 Agents SDK 的公共抽象包括 name、instructions、model、tools、handoffs、guardrails 和 structured output，但 FunctionTool、Handoff、Session、RunContext、Pydantic/Zod 类型、回调和客户端都是语言运行时对象。

因此平台采用三份契约：

1. AgentManifest：业务可编辑、可版本化、跨 Runtime。
2. AgentRuntimeBinding：部署和语言相关。
3. AgentRunRequest：单次运行相关。

该结论是基于两套 SDK 公共能力做的平台抽象，不是 OpenAI 提供的现成跨语言格式。参考：

- [Agent definitions](https://developers.openai.com/api/docs/guides/agents/define-agents)
- [Python Agent reference](https://openai.github.io/openai-agents-python/ref/agent/)
- [TypeScript Agent configuration](https://openai.github.io/openai-agents-js/openai/agents-core/interfaces/agentconfiguration/)

### 7.2 v1 Manifest 示例

~~~yaml
apiVersion: ai.platform/v1alpha1
kind: Agent
metadata:
  code: home-assistant
  version: 7
  name: Home Assistant
  description: 首页统一聊天 Agent，负责理解目标、选择专业 Agent 并汇总最终答复
  labels:
    scene: home-chat
spec:
  instructions:
    type: inline
    text: >
      你是平台首页助手。自行判断任务复杂度；需要专业分析时调用已配置的专业 Agent，
      最终只向用户返回经过验收的结果。
  model:
    ref: model://default-quality
    settings:
      temperature: 0.2
  output:
    mode: artifactSet
    workflowRef: workflow://home-chat-output/v3
  toolRefs:
    - ref: tool://artifact-submit/v1
    - ref: tool://kb-search/v2
  mcpRefs: []
  knowledgeRefs:
    - ref: knowledge://business-default
  skillRefs:
    - ref: skill://home-assistant-policy/v4
      contentHash: sha256:example
      required: true
  collaboration:
    agentTools:
      - targetAgentRef: agent://requirement-analyst/v3
        toolName: analyze_requirement
        description: 分析复杂需求并返回结构化目标、约束和缺失信息
      - targetAgentRef: agent://render-specialist/v5
        toolName: build_render
        description: 根据已确认的数据和展示目标生成 Render Artifact
    handoffs: []
  guardrails:
    input:
      - ref: guardrail://input-policy/v1
        execution: blocking
    output:
      - ref: guardrail://output-safety/v1
  runtimeDefaults:
    maxTurns: 12
    timeoutMs: 120000
    maxAgentDepth: 4
    stateStrategy: applicationReplay
    tracing:
      enabled: true
      includeSensitiveData: false
      workflowName: home-chat
  extensions: {}
~~~

### 7.3 Manifest 允许保存

- 静态 instructions 或受控 promptRef。
- 平台 modelRef 和白名单 model settings。
- JSON Schema 或 Artifact Workflow 引用。
- Tool、MCP、Knowledge、Skill、Guardrail 引用。
- agent-as-tool 和 handoff 的目标引用、名称和描述。
- maxTurns、timeout、Agent 深度、Tracing 等运行默认策略。

### 7.4 Manifest 禁止保存

- Python Agent、JavaScript Agent、Runner。
- FunctionTool、Agent.as_tool、Handoff 实例。
- Pydantic、Zod 或动态类型对象。
- 动态 instructions 函数、Tool handler、Hook、Guardrail 回调。
- OpenAI Client、ModelProvider、MCP 活动连接。
- Session、RunContext、当前用户、历史消息和附件。
- API Key、Token、Secret 明文。
- SDK RunState；若未来保存，只能作为绑定 engine、sdkVersion、agentVersion、checksum 和 TTL 的 opaque blob。

### 7.5 发布校验

Agent 版本发布前必须通过：

- code、version、引用存在性和已发布状态校验。
- JSON Schema 兼容性校验；跨语言中立源只使用 JSON Schema。
- PythonAdapter 和 TypeScriptAdapter 的双编译 Golden Test。
- Tool/Skill/MCP 在目标 Runtime 的 capability matrix 校验。
- Agent Graph 无环校验；v1 禁止 agent-as-tool 和 handoff 循环。
- 最大 Agent 数不超过 16、最大深度不超过 4、maxTurns 在平台允许范围内。
- handoff 和 agent-as-tool 不得使用相同 toolName。
- 模型、Tool、Skill、Workflow 引用全部解析为明确版本和 content hash。
- Manifest 中不存在密钥、函数源码和不受支持的 extension。

Structured Output 只使用 OpenAI 支持的 JSON Schema 子集，发布时执行严格校验。参考：[Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs)。

## 8. Runtime Binding 与跨语言编译

### 8.1 Runtime Binding

Runtime Binding 是部署配置，不是 Agent 业务定义：

~~~yaml
bindingCode: home-assistant-python-prod
agentRef: agent://home-assistant/v7
executor: OPENAI_AGENTS_PYTHON
sdkVersion: pinned-tested-version
modelProviderRef: provider://openai-prod
sessionStoreRef: session-store://chat-history
sandboxProfileRef: sandbox://agent-default
secretRefs:
  - secret://openai-prod-api-key
enabled: true
~~~

同一个 Agent 版本可以有 Python 和 TypeScript 两份 Binding。

### 8.2 编译映射

| 中立字段 | Python Adapter | TypeScript Adapter |
|---|---|---|
| metadata.name | Agent.name | Agent.name |
| metadata.description | handoff_description | handoffDescription |
| instructions | instructions | instructions |
| model.ref/settings | ModelResolver 和 model_settings | ModelResolver 和 modelSettings |
| output JSON Schema | Pydantic/AgentOutputSchema 适配 | JSON Schema/Zod 适配 |
| toolRefs | ToolRegistry 解析为 FunctionTool/Hosted Tool | ToolRegistry 解析为 Tool |
| agentTools | targetAgent.as_tool | targetAgent.asTool |
| handoffs | Agent/Handoff 实例 | Agent/Handoff 实例 |
| guardrailRefs | 注册表解析为 Guardrail 实例 | 注册表解析为 Guardrail 实例 |
| mcpRefs | MCP Server/Hosted MCP 绑定 | MCP Server/Hosted MCP 绑定 |

### 8.3 Agent Runtime SPI

Java 侧新增稳定端口：

~~~java
public interface AgentRuntime {
    AgentRuntimeCapabilities capabilities();

    AgentRunResult run(
            CompiledAgentSnapshot snapshot,
            AgentRunCommand command,
            AgentRunObserver observer,
            AgentCancellation cancellation
    );
}
~~~

Worker 只接收已冻结的 CompiledAgentSnapshot，不直接查数据库。

首期可继续复用当前子进程和 NDJSON；同时完成以下升级：

- payload 增加 protocolVersion、run、rootAgent、agentGraph、resolvedCapabilities、workflowSnapshot。
- Python main.py 从固定单 Agent 改为递归编译完整 Agent Graph。
- 新增 TypeScript Worker，消费同一份 Snapshot。
- 统一事件，不向前端泄露 SDK 原始事件结构。
- SDK 依赖锁定精确版本，禁止 pyproject 或 package.json 使用无约束版本。

稳定后把“每 Run 一个进程”替换为长驻 Worker/进程池；这属于运行优化，不影响 Manifest 和 SPI。

### 8.4 状态策略

v1 固定使用 applicationReplay：

- 平台 chat history 是唯一会话事实来源。
- 每轮由 Java 读取、裁剪并传入模型可见历史。
- 不同时启用 SDK Session、OpenAI conversationId 或 previousResponseId，避免重复上下文。
- Runtime 本地 context 只放 userId、tenant、trace、权限端口、数据库客户端等模型不可见对象。

## 9. Tool 设计

### 9.1 Tool 定义

Tool 从“数据库脚本”升级为版本化能力：

~~~text
code, name, description
inputSchema, outputSchema
permissionPolicy
approvalPolicy
timeoutPolicy
secretRefs
bindings[]: bindingType, runtimeType, endpointRef, packageUri, entrypoint, secretRefs, config
publishedVersion
~~~

Binding 只在 `bindings[]` 中定义一次；版本的 `adapterType` 是兼容旧请求和快速筛选的摘要字段，不再维护第二份独立 Binding 定义。

### 9.2 Binding 类型

| Binding | Python/JS 可移植性 | 说明 |
|---|---|---|
| HTTP | 高 | 通过 Tool Gateway 调内部或外部受控 API |
| MCP | 高 | 由各 Runtime 的 MCP Adapter 接入 |
| JAVA_INTERNAL | 高 | 通过内部受鉴权 HTTP/Feign Gateway 暴露 |
| HOSTED | 依赖能力矩阵 | Web Search、Shell 等 Runtime 托管工具 |
| PYTHON_MODULE | 仅 Python | 受控包和入口函数 |
| JAVASCRIPT_MODULE | 仅 TypeScript | 受控包和入口函数 |

跨语言 Agent 只允许引用：

- HTTP、MCP、JAVA_INTERNAL 等可移植 Binding；或
- 同一 Tool 同时存在通过兼容性测试的 Python 和 JavaScript Binding。

### 9.3 权限边界

Agent Guardrail 不能替代 Tool 权限。Tool Gateway 必须独立执行：

- 当前用户和租户权限。
- 数据范围。
- 参数 Schema。
- Secret 注入。
- 高风险动作审批。
- 网络域名 Allowlist。
- 审计和幂等。

官方 Guardrail 的覆盖面并不等于所有 Tool、MCP、handoff 和 agent-as-tool 路径，因此高风险策略必须落在平台 Gateway。参考：[Guardrails and approvals](https://developers.openai.com/api/docs/guides/agents/guardrails-approvals)。

## 10. Skill 包设计

### 10.1 兼容基线

Agent Skills 规范将 Skill 定义为包含 SKILL.md 的目录，可选包含 scripts、references、assets，并允许其他文件和目录。SKILL.md 由 YAML frontmatter 和 Markdown 正文组成。

平台首期接受：

~~~text
skill-name/
├── SKILL.md
├── scripts/
├── references/
├── assets/
├── templates/
├── data/
└── 其他原包文件
~~~

必填 frontmatter：

- name：1 到 64 字符，小写字母、数字和连字符，和顶层目录名一致。
- description：1 到 1024 字符，说明做什么以及何时使用。

可选 frontmatter：

- license
- compatibility
- metadata
- allowed-tools

allowed-tools 只是兼容元数据，最终可执行工具必须取“Skill 声明、Agent 授权、用户权限和 Runtime 能力”的交集，不能把它当 ACL。

参考：

- [OpenAI Agent Skills](https://developers.openai.com/api/docs/guides/tools-skills)
- [Agent Skills Specification](https://agentskills.io/specification)

### 10.2 两种创建方式

#### FORM

表单字段：

- name、description、license、compatibility。
- SKILL.md 正文。
- Tool 引用。
- 可选模板、参考文档和数据文件。

服务端生成标准目录和 ZIP，再走与上传包完全相同的校验、扫描和发布流程。

#### PACKAGE

v1 接受 ZIP：

- 必须只有一个顶层目录。
- 顶层目录中必须恰好存在一个大小写不敏感匹配的 SKILL.md。
- 原始 ZIP 必须原样保存，不能先修改再作为原包。
- 未识别目录和文件必须保留。

后续通过 SkillPackageImporter SPI 增加其他格式，不把 tar.gz 解析逻辑混入首期 ZIP Importer。

### 10.3 上传状态机

~~~mermaid
flowchart LR
    A["UPLOADED"] --> B["QUARANTINED"]
    B --> C["STRUCTURE_VALIDATED"]
    C --> D["SECURITY_SCANNED"]
    D --> E["INDEXED"]
    E --> F["DRAFT"]
    F --> G["PUBLISHED"]
    B --> X["REJECTED"]
    C --> X
    D --> X
~~~

具体流程：

1. 上传到隔离临时区。
2. 计算 SHA-256；首期把通过检查的原始 ZIP 和文件索引写入 Chat 控制面，后续再按容量需要迁移到 app-platform-file 对象存储。
3. 安全解包和路径校验。
4. 解析 SKILL.md frontmatter。
5. 生成文件索引、目录树、风险和兼容性报告。
6. 扫描恶意文件、Secret、可执行脚本和网络依赖。
7. 管理员预览并补充 Tool 映射。
8. 发布不可变版本。

### 10.4 安全约束

OpenAI 兼容 Profile 默认采用：

- ZIP 最大 50 MB。
- 每版本最多 500 个文件。
- 单个未压缩文件最大 25 MB。

平台额外拒绝：

- Zip Slip、绝对路径和父目录逃逸。
- symlink、hardlink。
- 重复路径、大小写冲突。
- 压缩炸弹和异常压缩比。
- 未声明的二进制可执行文件。
- Secret、私钥和凭据。

脚本执行要求：

- 只能在 Sandbox 中运行。
- 默认无网络；需要网络时使用域名 Allowlist。
- 文件写入限制在 Run Workspace。
- 危险 Tool 和外部写操作必须审批。
- Skill 只提供方法和资源，不自动获得 Agent 未授权的 Tool。

### 10.5 运行时加载

Skill 不是 Agent 构造器中的一个通用原生字段。Runtime Adapter 必须把 skillRef 解析成以下一种能力：

- OpenAI Hosted Skill Reference 加明确版本。
- Local Sandbox 中的只读挂载目录。
- 平台 Skill Loader Tool，按需读取 SKILL.md 和资源文件。

不得把所有 Skill 全文拼进系统 Prompt。应保留渐进加载：

1. 启动时只暴露 name 和 description。
2. Agent 选择 Skill 后加载完整 SKILL.md。
3. scripts、references、assets、templates、data 按需读取。

## 11. Workflow 重新定义

### 11.1 Workflow 只描述验收

~~~yaml
apiVersion: ai.platform/v1alpha1
kind: ArtifactWorkflow
metadata:
  code: home-chat-output
  version: 3
  name: 首页聊天产出规范
spec:
  artifacts:
    - code: final-answer
      artifactType: TEXT
      contentFormat: MARKDOWN
      required: true
      visible: true
    - code: render-document
      artifactType: RENDER_JSON
      contentFormat: JSON
      required: false
      schemaRef: schema://render-document/v2
      visible: true
  checks:
    - code: render-schema
      targetArtifact: render-document
      checkerType: JSON_SCHEMA
      severity: ERROR
      blocking: true
      retryable: true
    - code: semantic-review
      targetArtifact: final-answer
      checkerType: AGENT
      checkerRef: agent://result-reviewer/v2
      severity: WARNING
      blocking: false
      retryable: true
  completionPolicy:
    requireAllRequiredArtifacts: true
    requireAllBlockingChecksPassed: true
  repairPolicy:
    maxRepairAttempts: 2
    onExhausted: INPUT_REQUIRED
~~~

Workflow 不包含 entryAgent、node、edge 或执行顺序。Agent 版本可引用默认 Workflow；HOME_CHAT Entry Binding 负责选择根 Agent。

### 11.2 验收流程

~~~mermaid
sequenceDiagram
    participant A as Root Agent
    participant C as Artifact Collector
    participant W as Workflow Acceptance
    participant R as Reviewer Agent
    participant H as Chat History

    A->>C: final output / submit_artifact
    C->>W: ArtifactSet
    W->>W: deterministic checks
    opt semantic check
        W->>R: isolated review request
        R-->>W: structured check report
    end
    alt passed
        W->>H: persist artifacts and PASSED report
    else retryable and within limit
        W-->>A: repair request with check report
        A->>C: revised artifacts
    else needs user input
        W->>H: INPUT_REQUIRED
    else failed
        W->>H: FAILED
    end
~~~

Reviewer Agent 只返回检查报告，不直接静默修改 Artifact。修复仍由拥有最终回复权的根 Agent 完成。

## 12. 正式聊天执行链

### 12.1 请求目标

现有 ChatTransportRequest 增加：

~~~json
{
  "target": {
    "type": "AGENT",
    "agentCode": "home-assistant",
    "agentVersion": null
  },
  "modelOverrideId": null
}
~~~

规则：

- target 可空；空时服务端按 entryCode=HOME_CHAT 解析。
- 普通用户只能选择 Entry Binding 允许的 Agent。
- 前端不得传完整 Manifest、Runtime Binding、Tool 实现或 Secret。
- agentVersion 为空时，在 Run 开始时解析当前发布版本并固定。
- modelOverrideId 只在 Agent 策略允许且开发模式有权限时生效。

### 12.2 执行时序

~~~mermaid
sequenceDiagram
    participant UI as Chat UI
    participant C as core-ai-chat
    participant E as Entry Resolver
    participant A as Agent Runtime
    participant T as Tool/Skill Gateway
    participant W as Workflow Acceptance
    participant H as History

    UI->>C: chat.user_message
    C->>H: create/load session, create round, save user message
    C->>E: resolve HOME_CHAT or explicit target
    E-->>C: agentCode + published version
    C->>A: AgentRunCommand + frozen snapshot
    A-->>UI: run/agent/tool/handoff delta events
    A->>T: authorized tool and skill calls
    T-->>A: normalized results
    A->>W: ArtifactSet
    W-->>A: pass or bounded repair request
    W->>H: artifacts, checks, activity, snapshot hash
    C-->>UI: round.completed / failed / input_required
~~~

ConversationPreparationService 继续准备会话和历史，但删除：

- intentRouteService.route。
- Node output 初始化。
- 硬编码 workflowCode=ai-chat-intent-routing。

DefaultConversationExecutionServiceImpl 改为依赖 AgentConversationRunner，不再依赖 IWorkflowEngine。

### 12.3 统一事件

保留现有 run 和消息事件，并增加：

~~~text
agent.started
agent.changed
agent.completed
handoff.requested
handoff.completed
tool.started
tool.completed
tool.failed
skill.loaded
artifact.created
artifact.updated
check.started
check.completed
assistant.message.delta
assistant.input_required
round.completed
round.failed
round.cancelled
~~~

事件只暴露平台字段：

~~~text
runId, sessionCode, roundCode, traceId
agentCode, agentVersion, agentName
activityCode, activityType, status
toolCode, callId
artifactCode, artifactType
summary, timestamp, ext
~~~

不得把 Python snake_case、JavaScript camelCase 或 SDK 原始事件直接暴露给页面。

## 13. Spring AI 简单任务接口

### 13.1 保留接口

~~~text
POST /internal/v1/ai/text/generate
~~~

可选增加面向受控调用方的：

~~~text
POST /api/v1/ai/tasks/text/generate
~~~

### 13.2 强制语义

- 单次 systemPrompt + userPrompt。
- 不创建 session、round、message、artifact 或 AgentRun。
- 不加载 Agent、Skill、Tool、KB 或 Workflow。
- modelCode 必须解析为 enabled=true 且 clientType=SPRING_AI。
- 使用 SpringAiStatelessExecutor；不要仅依赖可能路由到 AI_AGENT 的通用 AiExecutionDomainService。
- 记录 requestId、scene、modelCode、actualModel、usage、duration 和错误摘要。

建议把现有 AiTextGenerationService 重命名或内部重构为 SpringAiTextTaskService，并增加 clientType 校验测试。

## 14. Maven 模块与依赖

### 14.1 首期实际目录

~~~text
app/app-platform-chat/
├── api/
├── web/
├── boot/
├── data/
│   ├── data-chat-history/
│   ├── data-meta/
│   ├── data-workflow/              # Agent/Skill/Tool/Workflow 控制面与发布快照存储
│   ├── data-kb/
├── modules/
│   ├── core-ai-engine/
│   ├── core-ai-chat/
│   ├── core-agent-runtime/         # 新增
│   ├── core-conversation-runtime/
│   ├── core-workflow/              # 重写为验收服务
│   └── core-kb/
├── providers/
│   ├── ai-provider-ai-agent/       # Python 与 TypeScript Runtime Adapter/Worker
│   ├── ai-provider-openai/
│   └── ...
└── spi/
~~~

首期没有额外拆出 `data-agent`，也没有把 Python/TypeScript Adapter 拆成两个 Maven 模块；二者通过中立 SPI 和协议隔离，待规模或发布节奏需要时再做物理拆分。

### 14.2 依赖方向

~~~mermaid
flowchart LR
    BOOT["boot"] --> WEB["web"]
    WEB --> CHAT["core-ai-chat"]
    CHAT --> AR["core-agent-runtime"]
    CHAT --> CR["core-conversation-runtime"]
    AR --> WF["core-workflow"]
    AR --> ENG["core-ai-engine"]
    AR --> SPI["Agent/Tool/Skill SPI"]
    DW["data-workflow 控制面"] --> SPI
    WF --> SPI
    CHAT --> HD["data-chat-history"]
    RT["ai-provider-ai-agent\nPython + TypeScript"] --> SPI
~~~

约束：

- core-ai-chat 不再依赖 Node Engine。
- core-agent-runtime 位于模型 Provider 之上，Agent 不再只是 AiChatClientType 的一种。
- core-workflow 不调用固定业务 Agent，只通过 AgentReviewPort 调用发布的 Reviewer。
- Runtime Adapter 不直接访问 Entity 或 Mapper。
- Skill 包首期保存在 `ai_chat_skill_package` 和 `ai_chat_skill_file`；达到数据库容量阈值后，应保持现有控制面契约并把二进制迁移到 app-platform-file。

### 14.3 过渡目录

首期已在 `providers/ai-provider-ai-agent` 中实现 AgentRuntime SPI，并同时打包 Python 与 TypeScript Worker；模块重命名不是部署前置条件。

## 15. 数据模型（1.1.0 物理落地）

所有运行版本都不可变；编辑行为只修改 Draft，Publish 创建新版本。

首期采用“目录身份表 + 不可变版本 JSON/BLOB”的收敛模型，避免同时维护规范化引用表和 Manifest 两套事实源。未来若为检索性能拆分索引表，仍以发布版本的 canonical JSON/checksum 为准。

### 15.1 Agent

| 表 | 关键字段 | 说明 |
|---|---|---|
| ai_chat_agent | code、name、description、status、current_version、enabled | Agent 目录身份 |
| ai_chat_agent_version | agent_code、version_no、status、manifest_json、validation_json、checksum、published_at | 完整中立 Manifest 和不可变发布版本 |
| ai_chat_agent_entry_binding | entry_code、agent_code、agent_version、runtime_type、sdk_version、priority、config_json、enabled | HOME_CHAT 等入口到发布版本和 Runtime 的绑定 |

Manifest 中的引用类型：

~~~text
TOOL
SKILL
KNOWLEDGE
MCP
WORKFLOW
AGENT_TOOL
HANDOFF
INPUT_GUARDRAIL
OUTPUT_GUARDRAIL
~~~

### 15.2 Skill

| 表 | 关键字段 | 说明 |
|---|---|---|
| ai_chat_skill | code、name、desc、content、tool_refs、enabled | 复用 1.0 目录身份；`content` 只保留 FORM 预览，发布状态以版本表为准 |
| ai_chat_skill_version | skill_code、version_no、source_type、entrypoint、manifest_json、validation_json、package_checksum、package_size、status | 不可变包版本 |
| ai_chat_skill_file | skill_version_id、path、media_type、content_size、checksum、content | 安全检查后的文件索引和首期 BLOB 内容 |
| ai_chat_skill_package | skill_version_id、original_filename、package_checksum、compressed_size、content | 通过隔离检查后原样保留的 ZIP |

source_type：

~~~text
FORM
ZIP
~~~

首期原始包和拆分文件保存在 Chat 数据库；对象存储是后续容量优化，不改变 API 和 checksum 语义。

### 15.3 Tool

| 表 | 关键字段 | 说明 |
|---|---|---|
| ai_chat_tool | code、name、desc、content、runtime_type、sync_status、enabled | 复用 1.0 目录身份；可执行定义和发布状态以版本表为准 |
| ai_chat_tool_version | tool_code、version_no、status、adapter_type、definition_json、validation_json、checksum、published_at | `definition_json` 唯一保存 Schema、权限、审批、超时和 `bindings[]`；不再维护重复 Tool Binding 表 |

### 15.4 Workflow

| 表 | 关键字段 | 说明 |
|---|---|---|
| ai_chat_workflow | code、name、type、enabled、config | 复用 1.0 目录身份；运行时不再使用旧流程类型进行路由 |
| ai_chat_workflow_version | workflow_code、version_no、status、specification_json、validation_json、checksum、published_at | 唯一保存 Artifact、Check、Completion 和 Repair Policy 的不可变规范 |

checker_type：

~~~text
JSON_SCHEMA
TOOL
AGENT
~~~

sort 只表示检查展示和执行优先级，不表示业务生成流程。

### 15.5 Run 审计

新增 ai_chat_agent_run：

~~~text
run_id
session_code
round_code
root_agent_code
root_agent_version
workflow_code
workflow_version
runtime_type
sdk_version
snapshot_hash
trace_id
status
started_at
finished_at
usage_json
error_summary
~~~

现有 session、round、message、artifact、activity 保留。round 增加 rootAgentCode、rootAgentVersion、runId 或通过 ai_chat_agent_run 关联，避免只记录最终模型而无法复现 Agent 定义。

### 15.6 删除表

完成双跑和只读保留期后删除：

- ai_chat_node
- ai_chat_workflow_config_node
- ai_chat_workflow_config_node_skill
- 旧 ai_chat_workflow_config 的节点编排语义

新 DDL 只放在 `app/app-platform-chat/config/chat/1.1.0`；`data-workflow` 下的重复初始化 SQL 已移除。

## 16. 管理 API

### 16.1 Agent

~~~text
GET    /api/v1/ai/agents
POST   /api/v1/ai/agents
GET    /api/v1/ai/agents/{code}
PUT    /api/v1/ai/agents/{code}
DELETE /api/v1/ai/agents/{code}

POST   /api/v1/ai/agents/{code}/versions
GET    /api/v1/ai/agents/{code}/versions
GET    /api/v1/ai/agents/{code}/versions/{version}
POST   /api/v1/ai/agents/{code}/versions/{version}/validate
POST   /api/v1/ai/agents/{code}/versions/{version}/publish
POST   /api/v1/ai/agents/{code}/versions/{version}/test-runs
GET    /api/v1/ai/agents/{code}/versions/{version}/compatibility
~~~

### 16.2 Entry Binding

~~~text
GET  /api/v1/ai/agent-entries
PUT  /api/v1/ai/agent-entries/{entryCode}
GET  /api/v1/ai/agent-entries/{entryCode}/available-agents
GET  /api/v1/ai/agent-entries/{entryCode}/bindings
PUT  /api/v1/ai/agent-entries/{entryCode}/bindings
DELETE /api/v1/ai/agent-entries/bindings/{bindingId}
~~~

公开页面只读取安全展示字段，不返回 Runtime Binding、Base URL 或 Secret。

### 16.3 Skill

~~~text
POST /api/v1/ai/skills/form
POST /api/v1/ai/skills/packages/inspect
POST /api/v1/ai/skills/packages/{draftId}/import
GET  /api/v1/ai/skills/{code}/versions/{version}
GET  /api/v1/ai/skills/{code}/versions/{version}/files
POST /api/v1/ai/skills/{code}/versions/{version}/validate
POST /api/v1/ai/skills/{code}/versions/{version}/publish
GET  /api/v1/ai/skills/{code}/versions/{version}/package
~~~

packages/inspect 使用 multipart/form-data，返回 Draft ID、解析后的 Manifest、文件树、兼容性报告和风险项，不直接发布。

### 16.4 Tool 和 Workflow

Tool 提供 CRUD、版本、Binding、Validate、Publish、Test。

Workflow 提供 CRUD、版本、Artifact Contract、Check、Validate、Publish、Test；不再提供 Node 排序、nextCode 或 Node-Skill phase API。

### 16.5 已下线与兼容保留 API

已从当前代码移除的入口只列一次：

- `/api/v1/chat/models/browser-agent`，不再向浏览器下发 Base URL 或 API Key。
- `/api/v1/ai/chat/workflow/internal/node` 及旧 Workflow Node/Node-Skill CRUD。

`/api/v1/chat/completions` 和 `/api/v1/chat/completions/stream` 暂时作为兼容入口保留，但内部和新 Transport 一样委托到 Agent 主链；它们不属于仍在运行的 Node API，后续仅按调用方迁移情况决定是否废弃。

当前生产页面使用的 /api/chat/rounds/stream、/api/chat/sessions/{sessionCode}/rounds/stream、run status、stop 和 reconnect 保持兼容，只改变内部执行器并扩展 target 字段。

## 17. 前端改造

### 17.1 路由

新增独立页面：

~~~text
/settings/system/agents
/settings/system/agents/:agentCode
/settings/system/workflows
/settings/system/workflows/:workflowCode
/settings/system/skills
/settings/system/skills/:skillCode
/settings/system/tools
/settings/system/tools/:toolCode
~~~

旧入口重定向：

~~~text
?tab=workflow -> /settings/system/workflows
?tab=node     -> /settings/system/agents?source=legacy-node
?tab=skill    -> /settings/system/skills
?tab=tool     -> /settings/system/tools
~~~

WorkflowSection 不再继续扩展，应拆分为：

~~~text
src/modules/system/agent-management/
├── api/
├── types/
├── components/
└── views/
    ├── AgentListView.vue
    ├── AgentEditorView.vue
    ├── WorkflowListView.vue
    ├── WorkflowEditorView.vue
    ├── SkillListView.vue
    ├── SkillEditorView.vue
    ├── ToolListView.vue
    └── ToolEditorView.vue
~~~

### 17.2 Agent 编辑页

分区：

1. 基础信息和版本状态。
2. Instructions 和模型。
3. Skill、Tool、KB、MCP。
4. 专业 Agent 协作：agent-as-tool 与 handoff 分栏。
5. Output Contract 和默认 Workflow。
6. Guardrail 和运行策略。
7. Python/TypeScript 兼容性。
8. 测试、Diff、发布和回滚。

复杂 Agent 不使用小型 Dialog 编辑。

### 17.3 Workflow 编辑页

- Artifact 列表。
- 每个 Artifact 的类型、Schema、模板和必填性。
- Check 列表。
- Completion 和 Repair Policy。
- 验收测试输入与报告。

不再展示 Node Canvas。

### 17.4 Skill 编辑页

两种入口：

- 表单创建。
- ZIP 包上传。

上传 UI：

~~~text
选择 ZIP
  -> 服务端 Inspect
  -> 展示 SKILL.md、文件树、兼容性和风险
  -> 补充 Tool 映射
  -> 确认 Import
  -> Validate
  -> Publish
~~~

ai-conversation-ui 的共享 request 层已经支持 FormData，API Wrapper 复用现有请求层，不在页面直接 fetch。

### 17.5 首页聊天

- 当前模型选择器改为 Agent 身份选择器。
- 模型覆盖只在开发模式和有权限时显示。
- target 缺省时不由前端硬编码版本，交给 HOME_CHAT Entry Binding。
- 会话详情保留并渲染 activities 和 artifacts，不再在 flattenRoundsToMessages 时丢弃。
- 从 TestChatView 抽取统一 useChatRun 和 RunActivityTimeline，供正式首页复用。
- 补齐 Agent、handoff、Tool、Skill、Artifact 和 Check 事件展示。

### 17.6 浏览器 Agent

系统设置页现有浏览器 Agent：

- 不得作为正式首页 Agent Runtime。
- 停止使用 dangerouslyAllowBrowser 和服务端下发 API Key。
- 若保留页面 DOM 辅助能力，应改为服务端 SETTINGS_ASSISTANT Entry Agent 加受控页面 Tool Bridge；不能使用生产模型密钥直连。

## 18. 迁移方案

以下 Phase 保留原实施顺序。当前仓库已完成对应代码改造；生产 DDL、运行时依赖、模型/Secret、真实多实例路由和端到端流量切换仍须按第 26 章验收。

### Phase 0：安全与基线

- 停止新增浏览器直连 Agent 能力。
- 完成第 16.5 节所列浏览器密钥入口下线，不再维护第二份重复的下线清单。
- 为 Python 和前端 Agents SDK 锁定经过测试的精确版本。
- 固化现有 Query、Render、SSE、重连、取消和 Artifact 回归样例。
- 确定 config/chat/1.1.0 为唯一迁移 DDL 来源。

验收：

- 浏览器网络请求中不存在模型 API Key。
- 现有主聊天基线测试可重复运行。

### Phase 1：Agent 控制面和 Runtime SPI

- 在 data-workflow 落地 Agent 控制面，并新增 core-agent-runtime 和 Agent Runtime SPI。
- 落 Agent、版本、引用、Binding、Entry、Run 表。
- 实现 AgentManifest Validator、Snapshot Resolver 和 Python Compiler。
- 改造当前 Python Worker 接收完整 Agent Graph。
- 建 TypeScript Compiler 和 Golden Test；可先不承载生产流量。
- 实现 Tool Gateway 和统一 Agent 事件。

验收：

- 同一 Manifest 可通过 Python 和 TypeScript 编译测试。
- manager Agent 能调用一个 agent-as-tool，并能完成一次 handoff 测试。
- Run 记录固定 snapshotHash。

### Phase 2：首页 Agent 化

- 发布 home-assistant Agent 和 HOME_CHAT Entry Binding。
- DefaultConversationExecutionServiceImpl 切换到 AgentConversationRunner。
- ConversationPreparationService 去掉 Intent Route 和 Node 输出。
- 保持现有 Transport、SSE、重连、停止和历史协议。
- 简单文本任务接口增加 SPRING_AI 强校验。
- 系统设置页浏览器 Agent 改走 SETTINGS_ASSISTANT Entry 或暂时关闭。

验收：

- 首页每个 Round 都有 rootAgentCode、version、runtime、snapshotHash。
- 简单聊天不经过 Workflow Node Engine。
- 简单任务不创建会话且不能选择 AI_AGENT 模型。

### Phase 3：专业 Agent 和 Artifact Workflow

- 发布 requirement-analyst、sql-specialist、render-specialist、result-reviewer。
- 把 KB Search、SQL Validate、Render Validate、Artifact Submit、Render Persist 注册成 Tool。
- 重写 core-workflow 为 Artifact Acceptance Service。
- 创建 query-render Artifact Workflow。
- 对旧链和新链执行采样双跑；有外部写入的 Tool 使用 Dry Run 或幂等隔离。

验收：

- Query 和 Render 样例的必需 Artifact 全部通过确定性检查。
- 检查异常不会 fail-open。
- 修复次数达到上限后能进入 INPUT_REQUIRED 或 FAILED。

### Phase 4：Skill 包和管理页面

- 落 Skill Package Import、对象存储、扫描、索引和发布。
- 旧 Skill 自动生成 FORM 标准包。
- Tool 和 Workflow 版本化。
- 上线 Agent、Workflow、Skill、Tool 独立页面。
- 首页 Agent selector 和统一 Activity Timeline 上线。

验收：

- FORM 和 ZIP 导入可导出语义一致的标准包。
- scripts、references、assets、templates、data 和未知目录可保留。
- 恶意 ZIP、安全违规和 Runtime 不兼容包不能发布。

### Phase 5：清理旧 Node 体系

- 旧表先只读保留至少一个发布周期。
- 停止旧 Workflow/Node 写入。
- 删除 ConversationIntentRouteService、WorkflowDefinitionFactory、DefaultWorkflowEngineImpl。
- 删除 IWorkflowNode、BaseWorkflowNode 和五个 Node 实现。
- 删除 AiChatNode Controller/Service/Mapper/DTO/Entity。
- 删除无真实调用的旧 Workflow Skill/Tool 包装类和 AiFlowPageServiceImpl。
- 删除 ai_chat_node 和 workflow config node 关系表。
- 运行 codegraph sync 和全量编译。

验收：

- 运行时、管理 API、数据库和页面均不再出现 Node 领域概念。
- 所有正式聊天仍支持 SSE、重连、停止、历史和 Artifact。

## 19. 旧数据迁移规则

| 旧字段/记录 | 新目标 |
|---|---|
| AiChatNode.executeType=AGENT | Agent Draft |
| AiChatNode.executeType=CHAT | 迁为单职责 Agent；不进入 Stateless Task |
| modelCode | AgentManifest model.ref |
| skillRefs | ai_chat_agent_ref.SKILL，发布时固定版本 |
| toolRefs | ai_chat_agent_ref.TOOL，发布时固定版本 |
| kbRefs | ai_chat_agent_ref.KNOWLEDGE |
| inputConfig 中 SYSTEM | Agent instructions |
| 其他 inputConfig | Agent Input Template 或 Skill Reference |
| outputConfig.schema | Agent JSON Schema 或 Workflow Artifact Schema |
| workflow config node sort/nextCode | 丢弃，不迁入新 Workflow |
| workflow config node skill phase | 迁为 Agent Skill Ref；需要验收的迁为 Workflow Check |
| AiChatSkill.content | 生成 FORM 包的 SKILL.md |
| AiChatSkill.toolRefs | 平台 Skill Tool Mapping，仍受 Agent 授权限制 |
| AiChatTool.content/runtimeType | Tool Version 的 Runtime Binding 或 Package |
| 旧 Workflow config | 只读 Snapshot，人工/脚本转换为 Artifact Contract |

迁移脚本要求：

- 先生成 Draft，不直接发布。
- 输出每条记录的 MIGRATED、NEEDS_REVIEW、REJECTED 状态和原因。
- code 冲突、无模型、无效引用、Schema 不合法的记录进入 NEEDS_REVIEW。
- 新旧数量、引用完整性和 checksum 形成迁移报告。

## 20. 代码变更清单

### 后端重点修改

- web/.../ChatTransportProtocolController.java
  - ChatTransportRequest 增加 target。
- web/.../ConversationController.java
  - 移除 browserAgentModels 及密钥 DTO。
- core-ai-chat/.../DefaultConversationExecutionServiceImpl.java
  - IWorkflowEngine 替换为 AgentConversationRunner。
- core-ai-chat/.../ConversationPreparationService.java
  - 仅保留 Conversation 基础设施。
- core-ai-chat/.../ConversationIntentAnalyzeService.java
  - 专业 Agent 上线后删除。
- core-ai-chat/.../ConversationIntentRouteService.java
  - HOME_CHAT 切换后删除。
- core-workflow/.../WorkflowDefinitionFactory.java
  - 删除。
- core-workflow/.../DefaultWorkflowEngineImpl.java
  - 删除或完全重写为 Artifact Acceptance Service。
- core-workflow/.../node/
  - 完成迁移后删除。
- providers/ai-provider-ai-agent/.../AiAgentProcessExecutor.java
  - Payload 升级为 Agent Snapshot 协议，补齐 Context 空值和 Secret 边界。
- providers/ai-provider-ai-agent/src/main/python/agent_provider/main.py
  - 从固定 Agent 改为 Agent Graph Compiler/Runner。
- core-ai-engine/.../AiTextGenerationService.java
  - 固定 Spring AI Stateless 路径。
- data/data-workflow
  - 删除 Node 数据模型，落地 Agent/Skill/Tool/Artifact Workflow 控制面和发布快照存储。
- modules/core-agent-runtime
  - 实现 Agent 运行面、快照冻结、Capability Gateway grant 和 Workflow 验收。

### 前端重点修改

- ai-conversation-ui/src/modules/ai-chat/api/index.ts
  - 请求增加 target，响应和事件类型补齐 Agent/Artifact。
- ai-conversation-ui/src/modules/ai-chat/views/ChatWorkspaceView.vue
  - 接入统一 Run Hook 和 Activity Timeline。
- ai-conversation-ui/src/modules/test/views/TestChatView.vue
  - 抽取可复用事件处理，不再保留独立协议实现。
- ai-conversation-ui/src/modules/system/components/sections/WorkflowSection.vue
  - 拆除并迁移到独立资源页面。
- ai-conversation-ui/src/modules/system/api/workflow.ts
  - 替换为 Agent/Workflow/Skill/Tool 版本 API。
- ai-conversation-ui/src/modules/system/api/aiPlatform.ts
  - 删除重复且无调用的 Skill 契约。
- ai-conversation-ui/src/modules/ai-assistant/services/agentRunner.ts
  - 不再直连模型；改成服务端 Entry Agent 或停用。

## 21. 测试方案

### 21.1 单元测试

- AgentManifestValidatorTest
- AgentGraphCycleValidatorTest
- AgentSnapshotResolverTest
- PythonAgentCompilerContractTest
- TypeScriptAgentCompilerContractTest
- AgentRuntimeCapabilityMatrixTest
- SkillZipSecurityValidatorTest
- SkillManifestParserTest
- ToolPermissionPolicyTest
- ArtifactAcceptanceServiceTest
- RepairPolicyTest
- SpringAiTextTaskServiceTest

### 21.2 契约测试

- 同一 Agent fixture 生成等价的 Python/TypeScript Agent Graph。
- 同一 JSON Schema 在两端通过或以同一错误码拒绝。
- Python/TypeScript 原始事件映射成同一平台事件。
- agent-as-tool 和 handoff 的最终回复所有权符合配置。

### 21.3 集成测试

- 首页默认 HOME_CHAT Agent。
- 显式选择允许的 Agent。
- 简单聊天不调用专业 Agent。
- 查询任务调用 Requirement 和 SQL Agent。
- Render 任务产生并持久化 Render Artifact。
- 确定性检查失败后修复成功。
- 修复耗尽后 INPUT_REQUIRED。
- SSE 断线重连、停止和取消。
- Agent 发布后旧 Run 仍可按 snapshotHash 回放。
- 简单任务拒绝 AI_AGENT clientType。

### 21.4 安全测试

- Browser API 和页面 Bundle 中没有模型密钥。
- ZIP Slip、symlink、重复路径、压缩炸弹和 Secret 包被拒绝。
- Skill 的 allowed-tools 不能扩大 Agent Tool 权限。
- Tool Secret 不进入 Prompt、Manifest、事件和 Trace。
- 无权限 Agent、Tool、Knowledge、Entry Binding 请求被拒绝。

### 21.5 构建验证

~~~text
mvn -pl app/app-platform-chat -am clean compile -DskipTests
cd ai-conversation-ui
npm run build
~~~

实现阶段每次修改后执行 codegraph sync。

## 22. 最终验收标准

1. 首页、历史会话和系统设置正式助手 100% 通过服务端 Agent Runtime。
2. 浏览器不再持有或请求模型 API Key。
3. 每个 Chat Round 可追溯 agentCode、agentVersion、runtime、sdkVersion 和 snapshotHash。
4. 一个根 Agent 能通过 agent-as-tool 调用多个专业 Agent，并能配置 handoff。
5. 同一已发布 AgentManifest 通过 Python 和 TypeScript 双适配器测试。
6. 管理端可以配置、验证、测试、发布和回滚 Agent。
7. FORM 和 ZIP 均能创建标准 Skill 包，且未知合法资源文件不会丢失。
8. 首期不执行 Skill 中的任意脚本；未来启用时只能进入受控 Sandbox，且 Tool 权限不会被 Skill 扩大。
9. Workflow 中不存在 Node、Edge、nextCode 或固定执行序列。
10. Workflow 能检查必需 Artifact，确定性检查失败不会放行。
11. Spring AI 简单任务接口无 Session、无 Agent、无 Tool，并拒绝非 SPRING_AI 模型。
12. SSE、停止、重连、消息历史、Artifact 和 Activity 能力保持兼容。
13. 旧 Node API、页面和 Java Engine 已移除；旧关系表只作为迁移/回滚档案保留，并在数据保留窗口结束后由部署方清理。

## 23. 关键风险与默认决策

| 风险 | 默认决策 |
|---|---|
| Python/JS SDK API 不同步 | 锁定精确版本，Manifest 独立版本，维护 Capability Matrix |
| 多 Agent 过度拆分 | 只有职责、Tool、模型、审批或输出契约明显不同才拆 Agent |
| Agent 自主性导致不可验收 | Workflow 只对 Artifact 做确定性和语义检查，并限制修复次数 |
| Skill 包携带恶意代码 | 隔离、扫描、审批、Sandbox、网络 Allowlist |
| Tool/Skill 权限越界 | Tool Gateway 是最终权限边界 |
| 发布后行为漂移 | Run 开始固定所有版本和 content hash |
| 双跑产生外部副作用 | Shadow Run 使用 Dry Run、只读 Tool 或幂等隔离 |
| 每 Run 一个 Python 进程成本高 | 先保证契约，随后切长驻 Worker，不改变上层设计 |
| Workflow 再次演变为编排图 | 数据模型中禁止 Node/Edge；Agent 协作只存在于 AgentManifest |
| DDL 双轨继续漂移 | config/chat/1.1.0 作为唯一部署来源 |

## 24. 实施起点

第一批实现不应从页面开始，而应按以下依赖顺序：

1. AgentManifest、版本表、Validator 和 Snapshot Resolver。
2. Agent Runtime SPI 和 Python Compiler。
3. Tool Gateway、统一事件和 Run 审计。
4. home-assistant Seed Agent 与 HOME_CHAT Entry Binding。
5. DefaultConversationExecutionServiceImpl 切换。
6. Spring AI Stateless Task 强校验。
7. 专业 Agent、Artifact Workflow 和 Skill 包。
8. 管理页面。
9. TypeScript 生产 Runtime。
10. Node 体系清理。

这样每一步都有可运行的纵向切片，也能在 Node 删除前保留明确回滚入口。

## 25. 外部标准依据

- [OpenAI Agent definitions](https://developers.openai.com/api/docs/guides/agents/define-agents)
- [OpenAI Orchestration and handoffs](https://developers.openai.com/api/docs/guides/agents/orchestration)
- [OpenAI Guardrails and approvals](https://developers.openai.com/api/docs/guides/agents/guardrails-approvals)
- [OpenAI Running agents](https://developers.openai.com/api/docs/guides/agents/running-agents)
- [OpenAI Agent Skills](https://developers.openai.com/api/docs/guides/tools-skills)
- [OpenAI Skills operational practices and limits](https://developers.openai.com/cookbook/examples/skills_in_api#operational-best-practices)
- [Agent Skills Specification](https://agentskills.io/specification)
- [OpenAI Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs)

## 26. 实施结果与部署验收

### 26.1 当前实施结论

代码侧已完成 Agent-first 主链和控制面改造，状态为“待部署验收”。这里的“已实施”仅表示仓库内代码、DDL、Worker、页面和自动化测试入口已经落地，不表示任何生产数据库已经执行 DDL，也不表示 OpenAI 凭据、Python/Node 依赖、模型映射、外部 Tool 端点或多实例路由已经配置完成。

| 领域 | 已落地 | 部署验收重点 |
|---|---|---|
| Agent SPI | `spi` 中的 Agent Runtime、Definition Store、Tool/Skill Store 和事件契约 | 确认所有部署实例加载相同发布快照 |
| Agent Runtime | `modules/core-agent-runtime` 中的 Manifest 校验、引用冻结、图环/深度/数量限制、snapshotHash、Run grant、Workflow 验收与有限修复 | 用真实发布 Agent 验证 Python/TypeScript Runtime 选择和取消/超时 |
| 正式聊天 | `modules/core-ai-chat` 已由 `AgentConversationRunner` 驱动；缺省 target 解析 `HOME_CHAT`，显式 target 和模型覆盖在服务端鉴权 | 验证首页、历史续聊、SSE 重连、停止和审计字段 |
| Artifact Workflow | `modules/core-workflow` 已改为 Artifact Contract、确定性 Check、Repair/Input Required，不再执行 Node DAG | 验证阻断检查 fail-closed 和修复耗尽行为 |
| 控制面 | 首期集中落在 `data/data-workflow`，提供 Agent、Entry、Skill、Tool、Artifact Workflow 的版本、校验和发布能力 | `test-runs` 当前是定义/Binding/兼容性 dry-run，不等同于真实模型端到端执行 |
| Skill | FORM 和 ZIP Inspect/Import、不可变文件索引、原包 checksum、安全限制、按需 Resource Gateway | 验证大包容量、恶意 ZIP 拒绝、资源 checksum；首期不执行包内脚本 |
| Tool | JSON Schema、权限、审批、Secret 引用、网络策略、幂等和 Tool Gateway | 配置 Secret/Approval 实现，验证 Allowlist 和写操作幂等 |
| OpenAI Agents Worker | `providers/ai-provider-ai-agent` 同时打包 Python 与 TypeScript Worker，支持 agent-as-tool、handoff、Tool/Skill Gateway 和统一事件 | Python >= 3.11、Node.js >= 20、SDK 依赖和模型网络必须在目标环境实测 |
| Spring AI 简单任务 | `POST /internal/v1/ai/text/generate` 固定校验 `SPRING_AI`，不进入会话或 Agent 主链 | 验证非 `SPRING_AI` 模型被拒绝且不生成会话数据 |
| 历史与审计 | Agent Run 表、Round Agent 快照字段、Activity 的 `agent_code` 已有 DDL/实体支持 | 抽查 runId、根 Agent、Runtime、SDK 和 snapshotHash 可关联 |
| 前端 | 首页 Agent selector、Agent activity/artifact 展示，以及 Agent/Workflow/Skill/Tool 独立管理页已落地 | 用已部署后端执行浏览器验收；构建成功不能替代交互验收 |
| 旧 Node 体系 | Node Java Runtime、管理 API、页面和数据访问代码已从新版本移除 | 旧表仅作迁移/旧版本回滚档案，新二进制不会读取旧 Node 执行表 |

首期有两个有意保留的物理边界：控制面继续位于 `data-workflow`，Python/TypeScript 继续位于同一个 Provider Maven 模块。它们不影响中立 Manifest 和 Agent Runtime SPI，暂不为目录纯度增加一次额外迁移。

### 26.2 已落地 API

以下为 Controller 的逻辑路径。直接访问 Chat 服务时还要加 `server.servlet.context-path=/chat`；经网关访问时按网关路由为准。

正式聊天与运行控制：

~~~text
POST /api/chat/rounds/stream
POST /api/chat/sessions/{sessionCode}/rounds/stream
POST /api/chat/stream/reconnect
GET  /api/chat/runs/{runId}
POST /api/chat/runs/{runId}/stop
GET  /api/chat/sessions/{sessionCode}/rounds/{roundCode}/thinking
GET  /api/chat/render-artifacts/{codeRef}
~~~

请求的 `target` 可省略；省略时解析 `HOME_CHAT`。`target.type` 只接受 `AGENT`。`modelOverrideId` 需要管理员角色、`ai:chat:model-override` 或 `ai:agent:debug` 权限；旧 `modelId` 仅用于兼容请求。`/api/v1/chat/completions`、`/api/v1/chat/completions/stream` 和 `/api/v1/chat/stream/reconnect` 暂时保留，但共用 Agent 执行链。

简单任务：

~~~text
POST /internal/v1/ai/text/generate
~~~

控制面：

~~~text
/api/v1/ai/agents                 CRUD、版本列表/详情、validate、compatibility、test-runs、publish
/api/v1/ai/agent-entries          入口选择、Runtime Binding、available-agents
/api/v1/ai/skills                 FORM、ZIP inspect/import、版本、files、package、validate、publish
/api/v1/ai/tools                  CRUD、版本、validate、test-runs、publish
/api/v1/ai/workflows              CRUD、版本、validate、test-runs、publish
~~~

除 `GET .../{entryCode}/available-agents` 外，控制面需要 `admin`、`ai:agent:manage`，或资源对应的 `ai:skill:manage`、`ai:tool:manage`、`ai:workflow:manage` 权限。`test-runs` 当前返回 frozen snapshot/validation 预览或 Binding validation dry-run，不会替代正式聊天的真实模型验收。

Worker 运行网关：

~~~text
POST /api/v1/ai/tool-gateway/{toolCode}/versions/{version}/invoke
POST /api/v1/ai/skill-gateway/{skillCode}/versions/{version}/resources/read
~~~

两个网关都校验当前用户、runId、snapshotHash、发布版本和本轮 capability grant。Tool Gateway 还校验参数/返回 Schema、权限、审批、网络策略、超时、大小和写操作幂等键。

### 26.3 1.1.0 DDL 部署顺序

`app/app-platform-chat/config/chat/1.1.0` 是 1.1.0 的唯一部署 DDL 来源；已移除 `data-workflow` 下的重复初始化 SQL。部署顺序不可交换：

1. 备份现有 Chat Schema，并确认可回滚到旧应用版本。
2. 执行 [`create_table_ddl.sql`](../../../../app/app-platform-chat/config/chat/1.1.0/create_table_ddl.sql)。它创建 Agent/版本/Entry/Skill 包/Tool 版本/Workflow 版本/Agent Run 表，并幂等写入 `home-assistant@1` 与 `HOME_CHAT` 引导绑定。
3. 执行 [`update_table_ddl.sql`](../../../../app/app-platform-chat/config/chat/1.1.0/update_table_ddl.sql)。它幂等把 Activity 的 `node_code` 迁为 `agent_code`，并给 Round 增加 Agent Run、根 Agent、Runtime、SDK 和 snapshotHash 字段及索引。
4. 部署新应用，完成第 26.4 节配置，然后验证 `GET /api/v1/ai/agent-entries/HOME_CHAT/available-agents` 至少返回一个已发布 Agent。
5. 如需转换 1.0 目录数据，再执行可选的 [`migrate_legacy_control_plane.sql`](../../../../app/app-platform-chat/config/chat/1.1.0/migrate_legacy_control_plane.sql)。该脚本只生成 `DRAFT`，不删除旧表，也不会自动发布或绑定。
6. 对迁移出的每个 Agent、Skill、Tool、Workflow 执行 validate；人工核对后 publish，再显式更新 Entry Binding。
7. 执行真实聊天冒烟测试，核对 `ai_chat_agent_run`、`ai_chat_round`、`ai_chat_activity`、Artifact 和 SSE 重连/停止。

引导 Seed 只保证系统有可解析入口，不代表生产 Agent 已完成业务配置。生产环境应把 `model://default-quality` 映射到可用模型，并把 Runtime Binding 的 SDK 版本替换为经过该环境测试的明确版本。若回滚，新应用需先停止流量并禁用新 Entry Binding，再回滚到可读取旧表的旧应用版本；仅保留旧表并不能让新二进制恢复 Node Runtime。

### 26.4 部署配置

当前默认配置由 `app/app-platform-chat/config/application.yml` 和 `AiAgentProperties` 提供：

| 环境变量/配置键 | 默认值 | 用途 |
|---|---|---|
| `AI_PROVIDER_AI_AGENT_ENABLED` | `true` | 启用 Agent Provider |
| `AI_PROVIDER_AI_AGENT_PYTHON_COMMAND` | `python3` | Python Worker 命令 |
| `AI_PROVIDER_AI_AGENT_SCRIPT_PATH` | 空 | 可选外部 Python Worker 入口；空时使用打包资源 |
| `AI_PROVIDER_AI_AGENT_WORKING_DIRECTORY` | 空 | Python Worker 工作目录 |
| `AI_PROVIDER_AI_AGENT_NODE_COMMAND` | `node` | TypeScript Worker 的 Node.js 命令 |
| `AI_PROVIDER_AI_AGENT_TYPESCRIPT_SCRIPT_PATH` | 空 | 可选外部 `worker.mjs`；空时使用 JAR 内 bundle |
| `AI_PROVIDER_AI_AGENT_TYPESCRIPT_WORKING_DIRECTORY` | 空 | TypeScript Worker 工作目录 |
| `ai.provider.ai-agent.typescript-dry-run` | `false` | 只用于部署探针/离线编译，不得作为生产聊天模式 |
| `AI_PROVIDER_AI_AGENT_TIMEOUT_MS` | `120000` | Provider 默认超时；还受 Manifest 上限约束 |
| `AI_PROVIDER_AI_AGENT_KNOWLEDGE_SEARCH_URL` | `http://127.0.0.1:9764/chat/api/v1/ai/execution/kb/search` | Worker 知识检索地址 |
| `AI_PROVIDER_AI_AGENT_TOOL_GATEWAY_URL` | `http://127.0.0.1:9764/chat` | Tool Gateway 服务基址 |
| `AI_PROVIDER_AI_AGENT_SKILL_GATEWAY_URL` | `http://127.0.0.1:9764/chat` | Skill Resource Gateway 服务基址 |
| `AI_CHAT_RUNTIME_MODE` | `local` | 会话运行状态存储模式 |
| `AI_CHAT_RUNTIME_NODE_ID` | 空 | 多实例运行节点标识 |

默认 URL 假设本机 9764 网关把 `/chat` 路由到 Chat 服务；若容器、Service Mesh 或端口不同，必须显式覆盖。模型 Base URL、API Key 和实际模型来自服务端模型/客户端配置，不写入 AgentManifest、页面或静态 Worker 配置。Java 在启动子进程时从当前用户上下文临时注入 `AI_AGENT_*_GATEWAY_TOKEN`，部署配置中不要固化用户 Bearer Token。

### 26.5 验证命令

从仓库根目录执行后端编译与核心测试：

~~~bash
mvn -pl app/app-platform-chat/boot -am clean compile -DskipTests
mvn -pl app/app-platform-chat/modules/core-agent-runtime -am test
mvn -pl app/app-platform-chat/modules/core-workflow -am test
mvn -pl app/app-platform-chat/modules/core-ai-chat -am test
mvn -pl app/app-platform-chat/data/data-workflow -am test
mvn -pl app/app-platform-chat/providers/ai-provider-ai-agent -am test
~~~

验证 Python/TypeScript Worker 契约和前端构建：

~~~bash
PYTHONPATH=app/app-platform-chat/providers/ai-provider-ai-agent/src/main/python \
  python3 -m unittest discover \
  -s app/app-platform-chat/providers/ai-provider-ai-agent/src/test/python \
  -p 'test_*.py'

cd app/app-platform-chat/providers/ai-provider-ai-agent/src/main/typescript
npm ci
npm test

cd -
cd ai-conversation-ui
npm ci
npm run build
~~~

文档和索引检查：

~~~bash
git diff --check -- docs/plans/202607/designs/agent-first-chat-service-refactoring-design.md
codegraph sync
codegraph status
~~~

自动化通过后仍需在部署环境执行以下端到端用例：HOME_CHAT 缺省路由、显式 Agent、agent-as-tool、handoff、Python 与 TypeScript 各一次、Tool/Skill Gateway 各一次、Workflow 阻断与修复耗尽、SSE 断线重连、stop、历史回放，以及简单任务拒绝 `AI_AGENT` 模型。

### 26.6 已知部署约束

1. `AgentCapabilityGrantService` 当前使用 JVM 内 `ConcurrentHashMap` 保存短期 run grant，最长 TTL 30 分钟，不是分布式状态。Worker 的 Tool/Skill Gateway 请求必须回到创建该 Run 的同一 JVM；首期建议使用同实例 loopback，或基于 runId/会话做粘性路由。无此前提的普通负载均衡会因找不到 grant 而 fail-closed。后续多实例无粘性部署需把 grant 迁到共享存储。
2. Python 和 TypeScript Worker 当前按 Run 启动子进程，不是长驻池。生产容量评估必须覆盖进程启动、依赖加载、并发上限、内存和取消回收。
3. Python 要求 3.11 及以上并锁定 `openai-agents==0.18.2`；TypeScript Worker 以 Node.js 20 为目标并锁定 `@openai/agents==0.13.4`。构建产物已可随 JAR 打包，但目标镜像仍必须提供对应解释器和可用依赖。
4. Tool Gateway 首期只执行 `HTTP/FUNCTION` 适配器并把调用转为受控 HTTP；`MCP`、`SCRIPT` 以及语言模块型 Binding 在便携 Gateway 中 fail-closed。需要审批或 Secret 的 Tool 必须先部署 `ToolApprovalVerifier`/`ToolSecretResolver` 实现。
5. Skill ZIP 可以保留 `scripts/`、`templates/`、`data/`、`references/`、`assets/` 和未知合法文件，但 Runtime 首期只按需读取已发布资源，不会任意执行脚本。`allowed-tools` 只是元数据，不能扩大 Agent snapshot 和用户权限的交集。
6. Skill 原始 ZIP 和拆分文件首期存为数据库 BLOB。部署方需评估数据库包大小、备份、复制和保留周期；迁移对象存储前不得绕过 checksum 与不可变版本语义。
7. Tool/Skill 资源访问依赖当前用户 Bearer Token，且 Capability Gateway 会同时核对 userId、runId、snapshotHash 和版本。异步线程、网关或服务间调用若丢失用户上下文会被拒绝，不能用静态管理员 Token 绕过。
8. `test-runs` 是控制面 dry-run；它验证 Manifest、引用、Compatibility 或 Binding 形状，但不请求真实模型，也不证明外部 Tool、KB、网络和凭据可用。
9. Seed Agent 的模型别名、默认网关 URL 和 `latest-compatible` SDK 标记只适合引导。上线前必须发布业务 Agent、验证明确模型映射和固定 Runtime/SDK，并通过真实流量冒烟。
10. 旧 Node 表仍存在是为了旧版本回滚和一次性迁移，不是双运行开关。新应用不存在 Node Engine，不能通过配置切回旧链。

### 26.7 部署验收出口

满足以下条件后，状态才能从“待部署验收”变为“已上线”：

- 1.1.0 DDL 按顺序完成并留存备份、执行记录和迁移报告。
- HOME_CHAT 绑定的是人工确认过的已发布 Agent 版本和明确 Runtime/SDK，模型别名可解析。
- Python 与 TypeScript 至少各完成一次真实 Run；manager 能完成 agent-as-tool 和 handoff，事件归一化一致。
- Tool/Skill Gateway 在目标负载均衡拓扑下命中同一 run grant，权限、审批、Secret、checksum 和幂等测试通过。
- Round 与 Agent Run 可追溯 runId、rootAgentCode/version、runtime、sdkVersion、snapshotHash，Artifact/Activity 可回放。
- Workflow 确定性检查失败不会放行，有限修复耗尽进入 `INPUT_REQUIRED` 或 `FAILED`。
- 首页、历史续聊、SSE 重连、停止、前端管理页面和 Agent selector 通过浏览器验收，浏览器请求与 Bundle 中无模型 API Key。
- `/internal/v1/ai/text/generate` 不创建会话数据，并能拒绝非 `SPRING_AI` 模型。

在这些外部验收项完成前，仓库状态保持“已实施，待部署验收”。
