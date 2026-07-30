# Python Agent 架构与扩展指南

> 状态：当前实现说明 + 明确标注的演进建议
>
> 运行时：OpenAI Agents Python SDK `0.18.2`、Python `>=3.11`
>
> 最后核对：2026-07-22

## 1. 架构定位

当前 Python Agent 不是一个独立常驻 HTTP 服务，而是 Chat JVM 为每次 Agent 执行启动的本地 Worker 子进程：

```mermaid
flowchart LR
    Conversation["ConversationExecutionService"] --> Runner["DefaultAgentConversationRunner"]
    Runner --> Runtime["AiAgentProvider / AgentRuntime SPI"]
    Runtime --> Process["AiAgentProcessExecutor"]
    Process -->|"stdin: protocol 2.0 JSON"| Worker["agent_provider/main.py"]
    Worker --> Compiler["compile_snapshot"]
    Compiler --> Factory["AgentFactory"]
    Factory --> SDK["OpenAI Agents SDK Runner"]
    SDK -->|"SDK stream events"| Emitter["EventEmitter"]
    Emitter -->|"stdout: JSON Lines"| Process
    Process --> Runner
```

### 1.1 Java 与 Python 的职责分工

| Java Run Plane | Python Agent Plane |
| --- | --- |
| 登录身份、Trace、会话和轮次 | Agent Prompt 和本地 Agent Catalog |
| 模型配置、Base URL、API Key | Agent Graph 编译和循环/深度校验 |
| Run 调度、取消、SSE 与重连 | OpenAI Agents SDK 对话执行 |
| 历史消息、活动、回答、Artifact 持久化 | Agent-as-Tool、Handoff、Tool 调用 |
| 临时平台 Token、Tool/Skill Gateway | Skill 元数据注入与按需加载 |
| Artifact Acceptance、修复轮次、审计 | SDK 事件到平台事件的映射、置信度守卫 |

`DefaultAgentConversationRunner.pythonRuntimeSnapshot()` 只创建 `python-agent-runtime/python-local` 占位 Snapshot。`AiAgentProcessExecutor` 明确写入 `agentDefinitionSource=PYTHON_LOCAL`，Python 编译器随后用本地 Catalog 替换 Java 传入的 Agent 定义。因此当前实现中：

- Java 数据库或 Java Snapshot 不能覆盖 Python Prompt、Tool、Skill 和协作拓扑。
- `agentEntry` 只是 Python Catalog 的入口选择器。
- 未知入口会 fail closed，不会回退到一个宽权限默认 Agent。

## 2. Worker 协议与运行流程

### 2.1 输入

JVM 通过 stdin 一次写入完整 JSON，敏感凭证只放环境变量：

```json
{
  "protocolVersion": "2.0",
  "model": "gpt-model-name",
  "messages": [{"role": "user", "content": "..."}],
  "run": {
    "runId": "agent-run-xxx",
    "requestId": "trace-xxx",
    "sessionCode": "session-xxx",
    "roundCode": "round-xxx",
    "userId": 10001,
    "input": "用户当前问题",
    "context": {
      "agentEntry": "HOME_CHAT",
      "knowledgeBases": []
    },
    "maxTurns": 12,
    "timeoutMs": 120000
  },
  "agentDefinitionSource": "PYTHON_LOCAL",
  "snapshotHash": "python-local",
  "confidencePolicy": {}
}
```

Worker 的主要步骤：

1. `normalize_payload` 兼容旧输入并规范化为协议 2.0。
2. `compile_snapshot` 根据 `agentEntry` 读取 Python 本地 Agent 定义和 Skill Capability。
3. 编译器校验 Agent 数量、引用、Tool、Skill Hash、协作环和最大深度。
4. `AgentFactory` 立即构建 Root Agent；Agent-as-Tool 的专业 Agent 在实际委派时延迟构建。
5. `Runner.run_streamed` 执行并把 SDK 原生事件映射成平台事件。
6. Confidence Guard 先检查证据是否充分；必要时复用主执行阶段的知识库证据，或补充检索并重新整理回答；只有证据满足条件时才进行最终可信度评分。
7. Worker 输出最终 `result` Frame，JVM 完成 Artifact Acceptance、审计和持久化。

当前 Java 默认下发 `enabled=true`、`scoring.enabled=true`、阈值 `0.9`、最多 3 次证据补检的 Confidence Policy。补检后若已经取得有效证据，守卫最多执行一次基于证据的回答重新整理。该策略启用时，SDK 原始回答 Delta 不会对外发送；通过守卫后的文本只作为最终 `finalOutput` 返回。这意味着“流式 Agent”默认主要流式呈现执行活动，而不是未经校验的正文 Token。

主执行阶段（Root Agent 或被委派 Agent）调用 `knowledge_base_search_tool` 时，`EventEmitter` 在保持原有 stdout 事件不变的同时，把映射后的 Tool 事件交给当前执行范围内的 `KnowledgeEvidenceCollector`。Collector 通过 `callId` 关联 `tool.started` 与 `tool.completed`，只接收已授权知识库的真实 Tool 结果；因此守卫可以复用当前 Agent 主执行阶段已经取得的证据，避免为评分重复检索。若现有证据不足，再按 Policy 补充检索；证据检查和检索排序本身都不产生可信度分数。

最终评估必须同时接收候选回答和累计证据的正文、来源元数据、检索排序分值。评分维度为
`evidenceCoverage`（事实主张被证据覆盖的比例）、`evidenceConsistency`（回答与证据的一致性）和
`answerCompleteness`（回答对请求范围的完整程度），最终可信度按 `45% + 45% + 10%` 加权。检索排序分值只用于
相关性排序，不能直接当作事实可信度；知识库是否覆盖全部数据只影响完整性，不应降低已被明确证据支持的事实可信度。
没有有效证据、缺少任一评分维度或回答完整性低于 `0.5` 时，结果是“证据不足、暂不评分”，而不是 `0` 分：
`scoreStatus=INSUFFICIENT_EVIDENCE`，并且不得包含 `confidence` 字段，也不能回退使用模型自评分。非事实型任务使用
`scoreStatus=NOT_APPLICABLE`，同样不输出百分比。知识库检索异常只让本次补充依据失败，不得中断已经完成的回答。

### 2.2 输出

stdout 是一行一个 JSON Frame：

```json
{"type":"event","eventType":"tool.started","status":"RUNNING","ext":{"callId":"call-1"}}
{"type":"event","eventType":"assistant.message.delta","status":"RUNNING","delta":"分析结果"}
{"type":"result","data":{"status":"SUCCESS","finalOutput":"...","artifacts":[]}}
```

stderr 只用于进程诊断。`AiAgentProcessExecutor` 会并行读取 stdout/stderr，收到 `error` Frame、非零退出码、超时或空结果时终止本轮并生成失败事件。

### 2.3 `thinking.analysis` 结构化可审计摘要

Python Runtime 在主 Agent 开始执行前发布一组请求分析事件：

1. `thinking.analysis.started` 表示预执行分析已经开始。
2. 独立分析器基于当前请求、近期对话、页面上下文，以及本轮实际可达的 Agent、已安装 Tool 和已授权知识库形成结构化摘要。
3. `thinking.analysis.completed` 必须先于主 Agent 的 SDK 执行产生。

这里的“分析”是给用户和审计系统使用的**决策摘要**，不是模型私有 chain-of-thought。允许输出目标、交付物、约束、信息缺口、建议路线、置信度依据、执行就绪度、成功标准、验证计划和补救建议；不得输出逐 Token 推理、隐藏提示词或内部思维过程。

`thinking.analysis.completed.ext` 的当前契约如下：

| 字段 | 类型 | 语义 |
| --- | --- | --- |
| `analysisSchemaVersion` | `int` | 结构化分析 Schema 版本，当前为 `1`。 |
| `analysisStatus` | `SUCCESS \| DEGRADED` | 分析内容质量状态；分析超时、结构错误或能力校验降级时为 `DEGRADED`。 |
| `routeNature` | `RECOMMENDATION` | 路线只是经过校验的建议，不代表 Tool、知识库或协作 Agent 已经实际执行。 |
| `confidenceKind` | `REQUEST_ROUTING` | `confidence` 衡量请求理解和路线匹配，不是最终回答的事实可信度。 |
| `confidence` / `confidenceBasis` | `0..1` / `string[]` | 请求分析总体置信度及简洁、可审计的依据。 |
| `executionReadiness` / `executionReadinessLevel` | `0..1` / `READY \| PARTIAL \| LOW` | 当前上下文和能力是否足以可靠开始执行。 |
| `durationMs` / `analysisUsage` | `int` / Token Usage | 预分析耗时和独立 Token 用量；总结果 Usage 会合并预分析与主执行用量。 |
| `analysis` | `object` | 完整结构化摘要，字段见下方示例。 |

```json
{
  "eventType": "thinking.analysis.completed",
  "status": "SUCCESS",
  "ext": {
    "activityCode": "main-agent-request-analysis:1",
    "activityType": "THINKING",
    "activityName": "分析用户请求",
    "outputSummary": "目标：识别订单异常原因。建议路线：先查询已授权数据……",
    "analysisSchemaVersion": 1,
    "analysisStatus": "SUCCESS",
    "routeNature": "RECOMMENDATION",
    "confidenceKind": "REQUEST_ROUTING",
    "confidence": 0.82,
    "confidenceBasis": ["目标和交付物明确", "统计范围仍待确认"],
    "executionReadiness": 0.68,
    "executionReadinessLevel": "PARTIAL",
    "durationMs": 438,
    "analysisUsage": {"inputTokens": 320, "outputTokens": 110, "totalTokens": 430},
    "analysis": {
      "status": "SUCCESS",
      "goal": "识别订单异常的原因与影响范围",
      "deliverable": "异常结论、证据和后续建议",
      "constraints": ["只使用已授权数据"],
      "gaps": ["尚未确认统计时间范围"],
      "route": {
        "mode": "TOOL",
        "agentCode": "root-agent",
        "toolCodes": ["data_preview_query_tool"],
        "knowledgeBaseCodes": [],
        "rationale": "先取得可验证数据，再形成结论。"
      },
      "confidence": {
        "overall": 0.82,
        "intentClarity": 0.9,
        "contextSufficiency": 0.62,
        "routeFit": 0.88,
        "basis": ["目标和交付物明确", "统计范围仍待确认"]
      },
      "executionReadiness": {
        "score": 0.68,
        "level": "PARTIAL",
        "reason": "可以开始取数，但形成最终结论前需要确认统计范围。"
      },
      "successCriteria": ["异常原因有数据依据"],
      "validationPlan": ["核对工具结果与用户范围"],
      "lowReadinessRemediation": [
        {"action": "ASK_USER", "description": "向用户确认统计时间范围。"}
      ],
      "validationWarnings": []
    }
  }
}
```

事件顶层 `status=SUCCESS` 只表示“分析活动正常结束并产生了可消费结果”。分析器失败时会产生安全降级摘要，顶层仍可为 `SUCCESS`，但 `ext.analysisStatus=DEGRADED`，并在 `analysis.degradedReason` 和 `validationWarnings` 中说明降级类型。这样预分析失败不会阻断主任务。

分析器有独立的短超时，超时后立即降级并把大部分运行预算留给实际执行。所有 Agent、Tool、知识库和补救目标都必须再次通过本轮可达性与授权校验。`lowReadinessRemediation` 当前也是建议：只有后续真实出现对应 `tool.*`、`agent.*`、`handoff.*` 或知识库检索事件，才能认定补救动作已经发生。初始执行和 Artifact Repair 重跑分别使用 `run.context.executionAttempt=1,2,...`，该值进入 `activityCode` 后缀，防止不同尝试的分析活动互相覆盖。最终 Worker Result 的 `providerMeta.requestAnalysis` 还会保留 `status`、`durationMs` 和独立 Usage。

### 2.4 Confidence Guard 的证据优先事件时序

Confidence Guard 把“是否具备评分条件”和“最终评分”拆成两个阶段。`confidence.evidence_check.*` 只回答证据是否充分，不评估百分比；只有最终 `confidence.assessment.completed` 才能携带 `confidenceKind=GROUNDED` 的 `confidence`。

典型时序如下：

- 主 Agent 已取得充分知识库证据：`confidence.evidence_check.started` → `confidence.evidence_check.completed` → `confidence.assessment.started` → `confidence.assessment.completed`。
- 证据需要补充：`confidence.evidence_check.*` → `confidence.retrieval.*` → 可选的 `confidence.reanalysis.*` → 最终 `confidence.assessment.completed` 或 `confidence.assessment.skipped`。
- 非事实型任务或始终没有充分证据：`confidence.evidence_check.*` → `confidence.assessment.skipped`；不会为了显示百分比而制造 `0` 分。
- 最终评估虽已开始，但返回的评分维度不完整：`confidence.assessment.started` → `confidence.assessment.skipped`；此时也不得发布 `confidence.assessment.completed`。

| 事件 | 语义 | 是否允许携带 `confidence` |
| --- | --- | --- |
| `confidence.evidence_check.started` | 开始检查已有知识证据；`reusedEvidence` 表示是否复用主 Agent 的知识库结果。 | 否。 |
| `confidence.evidence_check.completed` | 输出 `evidenceStatus=SUFFICIENT \| NEEDS_SUPPLEMENT \| NOT_APPLICABLE` 和证据命中数。 | 否。证据检查不是评分。 |
| `confidence.retrieval.started/completed/skipped` | 补充已授权知识库证据，或说明没有可用知识库。 | 否。检索分值不是回答可信度。 |
| `confidence.reanalysis.started/completed` | 基于累计证据重新整理候选回答。 | 否。重新整理不是复评。 |
| `confidence.assessment.started` | 使用已经确认的证据开始最终评估。 | 否。 |
| `confidence.assessment.skipped` | 最终评估不适用或证据不足；携带 `scoreStatus` 和说明。 | 否，字段必须省略。 |
| `confidence.assessment.completed` | 最终有证据评分完成；携带 `scoreStatus=SCORED`、三项维度和阈值。 | **是，这是 Python Confidence 事件族唯一的 Grounded 百分比来源。** |

证据不足的事件示例：

```json
{"type":"event","eventType":"confidence.evidence_check.completed","status":"SUCCESS","ext":{"activityCode":"confidence-evidence-check:enterprise-work-assistant","activityType":"EVIDENCE_SUFFICIENCY_CHECK","reusedEvidence":false,"evidenceHitCount":0,"evidenceStatus":"NEEDS_SUPPLEMENT","outputSummary":"当前尚无充分知识证据，需要先补充依据，暂不评分：当前没有足以支持回答事实主张的有效知识证据。"}}
{"type":"event","eventType":"confidence.assessment.skipped","status":"SUCCESS","ext":{"activityCode":"confidence-assessment:enterprise-work-assistant","activityType":"CONFIDENCE_ASSESSMENT","confidenceKind":"GROUNDED","scoreStatus":"INSUFFICIENT_EVIDENCE","evidenceHitCount":0,"outputSummary":"最终可信度暂不评分：当前没有足以支持回答事实主张的有效知识证据。"}}
```

最终可评分事件示例：

```json
{"type":"event","eventType":"confidence.assessment.completed","status":"SUCCESS","ext":{"activityCode":"confidence-assessment:enterprise-work-assistant","activityType":"CONFIDENCE_ASSESSMENT","confidenceKind":"GROUNDED","scoreStatus":"SCORED","confidence":0.94,"threshold":0.9,"evidenceCoverage":0.9,"evidenceConsistency":1.0,"answerCompleteness":0.85,"evidenceHitCount":3,"outputSummary":"最终可信度评估完成：可信度 94%（证据覆盖 90%，证据一致性 100%，回答完整性 85%），达到 90% 的评分阈值。"}}
```

Confidence Guard 启用时，Worker Result 的 `providerMeta.confidence` 保留 `scoreStatus`、证据数和尝试次数，但只在
`scoreStatus=SCORED` 时包含 `confidence`。Java Acceptance 完成后，可以把这个同一最终分数投影为
`execution.result.completed.ext.answerConfidence`；这是最终结果聚合字段，不是一次新的评分。证据不足时
`answerConfidence` 也必须省略，不能写成 `0`。

## 3. Agent 定义与扩展

### 3.1 当前 Agent 模型

`AgentDefinition` 是 Python 本地不可变定义：

```python
AgentDefinition(
    code="data-analysis",
    version=1,
    name="数据分析 Agent",
    description="...",
    prompt="...",
    model_ref="model://default-quality",
    tool_refs=("knowledge_base_search_tool",),
    capabilities=("data-analysis",),
    skill_refs=(),
    agent_tools=(),
)
```

当前 Root 是 `enterprise-work-assistant`，它通过以下 Agent-as-Tool 调用专业 Agent：

- `ask_data_analysis`
- `ask_dashboard_application_builder`
- `ask_document_analysis`
- `ask_knowledge_policy`
- `ask_workflow_forms`

Agent-as-Tool 的子 Agent 延迟创建；Handoff 目标必须在父 Agent 启动前创建。当前本地定义使用 Agent-as-Tool 为主，Handoff 编译和 SDK 接入能力已存在。

### 3.2 新增专业 Agent

推荐步骤：

1. 在 `agent_provider/agents/definitions/` 新建定义文件，完整声明 Prompt、模型引用、Tool、Skill 和能力说明。
2. 在 `definitions/__init__.py` 导出定义。
3. 把定义加入 `agents/catalog.py` 的 `_ROLES`，使 `definition_for` 可以解析。
4. 若由主 Agent 委派，在父 Agent 的 `agent_tools` 中增加 `AgentDelegation`，Tool Name 应稳定且使用 snake_case。
5. 若它是新的直接入口，在 `local_agent_documents` 中增加入口到 Root Code 的映射；不要使用静默默认回退。
6. 增加编译器、Factory 和事件测试，至少覆盖引用解析、实际委派、Tool/Skill 授权和失败路径。

编译期限制：

- Agent Graph 最多 16 个 Agent。
- `maxAgentDepth` 最大为 4。
- Graph 不允许协作环。
- 协作目标必须已经位于本轮冻结 Graph 中。
- `tool_refs` 和 `skill_refs` 默认 required，找不到时 Worker 启动失败。

### 3.3 案例：增加“合同审查 Agent”

```python
CONTRACT_REVIEW_AGENT = AgentDefinition(
    code="contract-review",
    version=1,
    name="合同审查 Agent",
    description="识别合同条款风险并形成审查意见。",
    prompt="只基于用户提供的合同和已授权制度资料审查，不得声称完成法律审批。",
    model_ref="model://default-quality",
    tool_refs=("knowledge_base_search_tool",),
    capabilities=("contract-review", "knowledge-retrieval"),
    skill_refs=("skill://contract-review/v1",),
)
```

还需要把它注册到 Catalog，并在主 Agent 增加指向 `agent://contract-review/v1` 的 `AgentDelegation`。只创建 Python 文件但不注册 Catalog，不会自动获得执行权限。

## 4. Skill 架构与扩展

### 4.1 Skill 包结构

每个内置 Skill 是 `agent_provider/skills/{skill-code}/` 下的版本化资源包：

```text
contract-review/
├── SKILL.md
├── manifest.json
├── references/
│   └── review-rules.md
├── assets/
│   └── output-schema.json
└── scripts/
    └── validate_clause.py
```

`manifest.json` 至少包含：

```json
{
  "code": "contract-review",
  "version": 1,
  "name": "Contract Review",
  "description": "Contract review workflow and resources.",
  "files": [
    {"path": "SKILL.md", "mediaType": "text/markdown"},
    {"path": "references/review-rules.md", "mediaType": "text/markdown"}
  ]
}
```

约束：

- `code` 必须是小写 kebab-case，并与目录名一致。
- `version` 必须是正整数。
- `files` 必须显式列出所有可加载资源，且必须包含 `SKILL.md`。
- Registry 冻结每个文件的 size、SHA-256 和整个 Manifest 的 `contentHash`。
- Skill 目录存在不等于启用；Agent 必须通过 `skill_refs` 显式授权。

### 4.2 渐进加载

编译器只把 Skill 的 `ref/name/description/contentHash` 元数据加入 Agent 指令。模型确有需要时才调用自动注册的 `load_skill_resource`：

1. 校验请求 Skill 是否分配给当前 Agent。
2. 规范化相对路径并阻止 `..`、绝对路径和越界访问。
3. 优先读取内联资源或本地冻结目录，否则调用 Skill Gateway。
4. 校验 Manifest 中的 size 和 checksum。
5. 单个资源最大 256 KiB。
6. 发布 `skill.loaded` 事件用于前端时间线和审计。

`scripts/` 当前只是可加载资源目录。Skill Loader 不会自动执行脚本；如果需要确定性执行，应把逻辑实现为受控 Tool，并对输入、权限、超时和输出做独立校验。

### 4.3 新增 Skill

1. 创建 Skill 目录、`SKILL.md`、`manifest.json` 和必要资源。
2. 在 Manifest 中列全文件，不手工填写 checksum 也可以，由 Registry 冻结时生成。
3. 在目标 `AgentDefinition.skill_refs` 中加入 `skill://{code}/v{version}`。
4. 若发布新版本，新建版本化内容并同步修改引用；不要原地改变旧版本语义。
5. 运行 Skill Registry/Catalog/Loader 测试，覆盖 Hash 不匹配、路径越界、未授权读取和资源过大。

## 5. Tool 架构与扩展

### 5.1 内置 Python Tool

当前内置 Tool 包括：

- `knowledge_base_search_tool`
- `data_preview_query_tool`
- `data_format_validate_tool`
- `render_json_validate_tool`
- `web_search_tool`

其中部分 Tool 是纯函数，部分使用本轮 `run` 构建并携带 JVM 签发的临时 Token 调用 Chat 内部接口。

新增内置 Tool 需要同步修改多个注册点：

1. 在 `agent_provider/tools/` 实现 `@function_tool`；若依赖用户/Run 上下文，提供 `build_xxx_tool(run, function_tool)`。
2. 在 `tools/__init__.py` 导出。
3. 在 `AgentFactory` 导入并加入 `_tool_registry`，Run-bound Tool 增加专门构建分支。
4. 在 `compiler/snapshot.py` 的 `BUILTIN_TOOL_NAMES` 增加名称。
5. 在目标 Agent 的 `tool_refs` 中授权。
6. 为成功、参数错误、权限失败、超时、敏感信息脱敏和取消增加测试。

只实现函数而未加入 Compiler Allowlist，`tool_refs` 会在编译期失败；只加入 Registry 但未分配给 Agent，也不会进入 SDK Agent。

### 5.2 平台 Tool Gateway

编译器还支持把冻结 Capability 中的版本化 Tool 转换成动态 `FunctionTool`：

- 支持的类型：`HTTP`、`FUNCTION`、`JAVA_INTERNAL`。
- Runtime Name：`gateway::{code}::v{version}`。
- 调用路径：`/api/v1/ai/tool-gateway/{code}/versions/{version}/invoke`。
- 每次请求携带 `runId`、`snapshotHash`、临时 Bearer Token 和基于参数生成的幂等 Key。
- Gateway 响应必须回显匹配的 `toolCode/toolVersion`，状态只能是 `SUCCESS` 或 `FAILED`。

当前重要限制：`PYTHON_LOCAL` 编译会把 `resolvedCapabilities` 替换为本地 Skill Capability，因此主聊天链路目前不会自动合并 Java 下发的平台 Tool Capability。现阶段主链路扩展应使用 Python 内置 Tool 或显式的 Chat Gateway Builder；若要启用通用平台 Tool，必须先设计“Python 本地 Agent 允许引用哪些冻结平台能力”的合并规则，不能直接放开全部远程 Capability。

## 6. MCP 当前状态与扩展设计

### 6.1 当前实现

当前 Python Agent Runtime **不支持直接 MCP Binding**：

- Snapshot Compiler 将 `MCP`、`HOSTED`、`SCRIPT`、`PYTHON_MODULE`、`JAVASCRIPT_MODULE` 列为不支持的 Binding，并直接抛错。
- `AgentFactory` 没有创建或挂载 MCP Server Client。
- Worker 没有 MCP 连接生命周期、审批、凭证、超时和审计事件映射。

因此仅在 Tool 元数据中增加 `bindingType=MCP` 会导致本轮编译失败；这不是可用的扩展方式。

### 6.2 当前可采用的安全接入方式

建议把 MCP 能力先收敛在平台侧：

```mermaid
flowchart LR
    Agent["Python Agent"] --> FunctionTool["版本化 FunctionTool"]
    FunctionTool --> Gateway["Java Tool Gateway"]
    Gateway --> Policy["授权 / 审批 / 幂等 / 审计"]
    Policy --> MCPAdapter["平台 MCP Adapter"]
    MCPAdapter --> MCP["MCP Server"]
```

Python 只看到版本化 Tool 契约，不直接持有 MCP Server 凭证和任意工具列表。平台负责：

- Server Allowlist 与 Tool Allowlist。
- 用户/租户授权和高风险操作审批。
- 参数 Schema 校验、超时、重试和幂等。
- 访问令牌、Secret Ref 和审计日志。
- 把 MCP 响应归一化为稳定 `SUCCESS/FAILED` Tool Result。

要在当前 `PYTHON_LOCAL` 主链路实际使用这种方式，还需先实现受控的平台 Capability 合并，或提供一个专用内置 Gateway Tool Builder。

### 6.3 若未来支持 Python 直连 MCP

这是演进建议，不是当前能力。至少需要：

1. 定义版本化、可冻结的 MCP Server/Tool Capability，包含 Server Ref、Tool Allowlist、Transport、审批策略和兼容 Runtime。
2. Compiler 只接受明确授权且兼容 `OPENAI_AGENTS_PYTHON` 的 Binding，禁止模型自行发现任意 Server。
3. Factory 使用 SDK MCP 类型创建 Server，并只向指定 Agent 挂载允许的 Tool。
4. 凭证通过环境变量或 Secret Ref 注入，禁止进入 Snapshot、Prompt、stdout 和 SSE。
5. 把连接、列举工具、调用、审批、失败、取消映射为平台 `tool.*`/`mcp.*` 审计事件。
6. Run 结束、超时或取消时关闭 MCP 连接；设置并发、响应大小和资源读取上限。
7. 增加恶意 Tool 描述、Prompt Injection、Schema 变更、越权 Tool、断线和重复调用测试。

## 7. Artifact、校验、修复与权威执行结果

Python 最终输出可以包含 `artifacts`。Java 不直接信任这些产物：

1. `DefaultAgentConversationRunner` 调用 `ArtifactAcceptanceService.accept`。
2. 每个检查发布 `check.started`、`check.completed`。
3. 可修复失败发布 `artifact.repair.requested`，把修复说明追加成新一轮 Agent 输入。
4. 最多按 Acceptance Contract 的 `maxRepairAttempts` 重试。
5. 接受后才把 Artifact 交给会话层持久化；需要用户补充时返回 `INPUT_REQUIRED`。

Render JSON Artifact 还会通过 `RenderInternalApi` 保存正式 Render Page 引用。详见 [AI 生成、校验与聊天产物](../render-json/ai-generation-and-validation.md)。

### 7.1 事件权威层级

各事件表达的事实层级不同，消费方不能把“建议”或“某一步完成”误当作整轮成功：

| 事件 | 表达的事实 | 是否是最终权威结果 |
| --- | --- | --- |
| `thinking.analysis.completed` | 执行前的目标、缺口和建议路线 | 否，`routeNature=RECOMMENDATION`。 |
| `tool.*`、`agent.*`、`handoff.*` | 某个实际调用或协作生命周期 | 否，只能证明对应动作发生。 |
| `check.completed` | 某次 Acceptance Attempt 中单个检查的结果 | 否，是最终聚合结果的证据。 |
| `artifact.repair.completed` | 一次修复 Runtime 已返回，接下来需要重新验收 | 否，不代表修复产物已经通过检查。 |
| `execution.result.completed` | Java 完成执行、检查、修复和 Acceptance 后的聚合裁决 | **是**，以 `ext.authoritative=true` 标识。 |

Python `result.data.finalOutput`、`outputs` 和 `artifacts` 使用 Confidence Guard 之后的规范化输出，但在 Java Acceptance 完成前仍是候选结果。跨端展示、持久化和后续动作应以 `execution.result.completed` 的裁决字段为准。

### 7.2 Check 事件契约

每个检查按 `check.started` → `check.completed` 发布，两者使用相同的
`activityCode=artifact-check:{checkCode}:attempt:{acceptanceAttempt}`。主要 `ext` 字段包括：

- `attempt`：Acceptance Attempt，从 `1` 开始。
- `repairAttempt`：产生本次候选结果的修复次数，初始候选为 `0`。
- `checkCode`、`targetArtifact`、`checkerType`、`severity`。
- `blocking`、`retryable`、`passed`、`checkStatus`。
- `inputSummary`；完成事件另外包含 `outputSummary`。

`check.completed.status=FAILED` 只表示该检查未通过。非阻断检查失败时，最终结果仍可能是 `PARTIAL`，不能仅根据单个检查推断整轮 `FAILED`。

### 7.3 Repair 事件契约

一次自动修复使用稳定的 `activityCode=artifact-repair:attempt:{repairAttempt}`，事件可能为：

- `artifact.repair.requested`：准备使用验收返回的修复说明再次调用 Agent。
- `artifact.repair.completed`：Agent 修复调用已经返回，`artifactCount` 是该次 Runtime 候选产物数；仍需下一次 Acceptance。
- `artifact.repair.failed`：修复调用失败并停止本次自动补救，包含脱敏后的 `failureType`。

通用字段为 `attempt`、`repairAttempt`、`maxRepairAttempts` 和输入/输出摘要。只有后续 Check 和最终 `execution.result.completed` 能证明修复是否真正解决问题。

### 7.4 `execution.result.completed` 权威字段

该事件固定使用 `activityCode=execution-result`、`activityType=EXECUTION_RESULT` 和
`ext.authoritative=true`。核心字段如下：

| 字段 | 语义 |
| --- | --- |
| `resultStatus` / `outcomeStatus` | 权威状态：`SUCCESS`、`PARTIAL`、`INPUT_REQUIRED`、`FAILED` 或 `CANCELLED`。 |
| `accepted` | Acceptance Service 是否接受当前结果。 |
| `artifactCount` | 验收后的权威产物数量；优先使用 Acceptance 返回的产物。 |
| `attempt` | 总 Acceptance Attempt 数。 |
| `repairAttempts` / `remediationAttempts` | 已执行的自动修复次数。 |
| `maxRepairAttempts` | 本轮 Acceptance Contract 允许的最大修复次数。 |
| `checks` | `{total, passed, failed, blockingFailed}` 聚合计数。 |
| `checksPassed` / `checksFailed` | 通过和未通过的 Check Code。 |
| `completionCoverage` | 已通过检查占比；无检查的成功结果为 `1.0`。 |
| `remainingIssues` | 尚未解决的问题，包含检查身份、阻断性、可重试性、状态和摘要。 |
| `nextAction` | 结构化后续动作：`DELIVER_RESULT`、`REVIEW_REMAINING_ISSUES`、`REQUEST_USER_INPUT`、`STOP_AND_REPORT_FAILURE` 或 `NONE`。 |
| `resultSummary` / `outputSummary` | 候选回答摘要和权威结果摘要，均为有界审计文本。 |
| `answerConfidence` | 可选，只在 Python 最终 `scoreStatus=SCORED` 时复制同一个 Grounded 分数；证据不足时省略，不能写成 `0`。不要与 `thinking.analysis` 的请求路由置信度混用。 |
| `failureType` | 可选，执行失败类型；不包含原始敏感异常消息。 |

状态解释：

- `SUCCESS`：结果已接受，所有检查通过。
- `PARTIAL`：结果已接受，但仍有非阻断问题需要复核。
- `INPUT_REQUIRED`：当前结果不能交付，需要用户补充信息。
- `FAILED`：执行或验收失败，未形成可交付结果。
- `CANCELLED`：执行被取消，不继续验收或交付。

## 8. 扩展检查清单

### Agent

- 定义是否注册到 Catalog，入口或父 Agent 是否显式引用。
- Prompt 是否说明权限、事实来源、写操作确认和禁止虚假成功。
- Graph 是否小于 16 个 Agent、深度不超过 4、无环。
- 子 Agent 输出是否仍由 Root Agent 核验后交付。

### Skill

- `code/version/files` 是否完整且版本稳定。
- `SKILL.md` 是否在 Manifest 中。
- Agent 是否显式分配 Skill。
- 路径、Hash、Size 和未授权读取测试是否齐全。

### Tool

- 是否加入 Compiler Allowlist、Factory Registry 和 Agent `tool_refs`。
- 是否使用最小权限临时 Token，且不把 Secret 写入输入/输出。
- 输入 Schema、输出契约、超时、取消、幂等、错误脱敏是否明确。
- 写操作是否有审批边界。

### MCP

- 当前是否错误地声明了会被 Compiler 拒绝的 `MCP` Binding。
- 是否应先通过 Java Tool Gateway 收敛授权和审计。
- 若要直连，是否已补齐 Server Allowlist、Secret、生命周期、审批和审计设计。

## 9. 源码索引

- `app/app-platform-chat/modules/core-agent-runtime/src/main/java/ai/platform/aiassit/agent/runtime/DefaultAgentConversationRunner.java`
- `app/app-platform-chat/providers/ai-provider-ai-agent/src/main/java/ai/platform/aiassit/service/ai/agent/service/AiAgentProvider.java`
- `app/app-platform-chat/providers/ai-provider-ai-agent/src/main/java/ai/platform/aiassit/service/ai/agent/service/AiAgentProcessExecutor.java`
- `app/app-platform-chat/providers/ai-provider-ai-agent/src/main/python/agent_provider/main.py`
- `app/app-platform-chat/providers/ai-provider-ai-agent/src/main/python/agent_provider/compiler/snapshot.py`
- `app/app-platform-chat/providers/ai-provider-ai-agent/src/main/python/agent_provider/agents/`
- `app/app-platform-chat/providers/ai-provider-ai-agent/src/main/python/agent_provider/skills/`
- `app/app-platform-chat/providers/ai-provider-ai-agent/src/main/python/agent_provider/tools/`
- `app/app-platform-chat/providers/ai-provider-ai-agent/src/main/python/agent_provider/gateway/`
- `app/app-platform-chat/providers/ai-provider-ai-agent/src/main/python/agent_provider/events/emitter.py`
- `app/app-platform-chat/providers/ai-provider-ai-agent/src/main/python/agent_provider/runtime/runner.py`
