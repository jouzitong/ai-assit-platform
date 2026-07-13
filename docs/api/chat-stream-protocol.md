# AI Chat 流式通信协议

## 1. 目标

- 定义 AI Chat 后端与前端之间的流式通信协议。
- 统一 SSE 当前实现的事件类型、事件字段和前端处理语义。
- 让 SSE 与 WebSocket 复用稳定的业务事件模型。

当前实现分为两层协议：工作流与运行时内部继续使用 `ConversationQueryStreamEvent`（`protocolVersion=1.0`）；面向新前端的 SSE 和 WebSocket 统一投影为 `chat-event.v2`。内部事件不直接约束前端，外部协议不反向侵入任务池与工作流。

当前实现由两层事件组成：

- 运行时事件：描述一次执行任务的创建、启动和取消，例如 `run.accepted`、`run.started`、`run.cancelled`。
- 对话业务事件：描述工作流进度、回答、澄清、失败和完成，例如 `progress`、`answer_delta`、`answer`、`clarification`、`error`、`complete`。

除非特别标明，本文中的事件示例允许省略值为 `null` 的字段；第 6 节之后的业务示例还可能为可读性省略重复的 `protocolVersion`、`runId`、`eventId`、`timestamp`，实际实时事件会由运行时统一补齐。

### 1.1 相对旧版文档的实现差异

当前实现相对旧版协议文档有以下变化，本文已经按实际实现修订：

1. 发起对话的模型字段为 `modelId`（模型配置主键），前端不再提交 `modelCode` 或 `apiModel`。
2. 事件增加统一运行时信封字段：`protocolVersion`、`runId`、`eventId`、`timestamp`。
3. SSE 增加 `id` 行，并与 payload 的 `eventId` 保持一致。
4. 事件集合增加 `run.accepted`、`run.started`、`run.cancelled`。
5. 重连支持 `runId + lastEventId`，可以重新订阅仍在运行的任务并按序号增量重放。
6. 新增任务状态查询和停止接口，并定义独立运行状态机。
7. 运行任务支持本地单实例和 Redis 多实例协调；通信连接断开不等于取消任务。
8. 后端实际抽象为 `ConversationRunManager`、`ConversationEventPublisher`、`ConversationCancellation` 和传输适配器，不再由业务上下文持有 `SseEmitter`。
9. `PLAN`、`NODE` 当前仍属于逐步收敛的推荐 `progressType`，并非所有现有进度事件都会携带。
10. AI Agent（Python）活动使用 `progressType=ACTIVITY`，实时投影为 `thinking.updated`，同时写入 `ai_chat_activity` 作为可查询的活动事件日志。
11. 明确区分业务终态事件与传输层 EOF：只有协议终态事件可以结束当前轮次，连接自然结束、超时或网络断开均不能推断为成功。
12. `chat-event.v2` 的重连同时支持运行时缓存重放和持久化快照回放；实时事件按 `runId + eventId` 去重，持久化快照按回放标识和业务对象幂等覆盖。

### 1.2 前端 `chat-event.v2` 适配层

前端标准协议以 [`data/chatMessage/chat-transport-protocol.json`](../../data/chatMessage/chat-transport-protocol.json) 为准，已经实现：

- SSE：`POST /api/chat/sessions/{sessionCode}/rounds/stream`；新会话使用 `POST /api/chat/rounds/stream`。
- SSE 重连：`POST /api/chat/stream/reconnect`，支持 `runId + lastEventId`。
- WebSocket：`/ws/chat`，支持 `chat.user_message`、`chat.reconnect`、`chat.stop`。
- 查询：思考详情、Render Artifact、运行状态及运行中断。
- 标准包络：`schemaVersion=chat-event.v2`、ISO-8601 `timestamp`、统一 `payload`。

一个内部事件可能投影为多个前端事件。例如对话初始化会拆成 `session.initialized`、`round.initialized`、`assistant.started`、`thinking.started`。此时事件 ID 使用 `{内部序号}.{子序号}`，例如 `3.1` 至 `3.4`；从 `3.1` 重连时，传输层会重新取得内部事件 `3`，过滤 `3.1`，继续发送尚未确认的子事件，不会丢失同一主序号的剩余内容。

## 2. 设计原则

1. 传输方式与业务事件分离。
2. `eventType` 只表达前端关心的业务事件类型。
3. 节点、阶段、来源等过程信息放在 `source`、`phase`、`ext` 中。
4. `complete` 只表示整轮会话完成，不表示单个节点完成。
5. 单个节点完成通过 `progress` 或 `answer` 配合 `source`、`phase` 表达。
6. `runId`、`sessionCode`、`roundCode` 语义相互独立：分别表示执行实例、会话和对话轮次。
7. SSE 连接断开只取消订阅，不自动取消后端运行任务。
8. 只有 `round.completed` 表示本轮成功；`thinking.completed`、HTTP 200、SSE EOF 和 WebSocket close 都不是业务成功标志。
9. 传输恢复必须有限重试，不允许无限重连；重试期间后端任务继续运行，除非用户明确发起停止。
10. 面向用户的错误信息与内部诊断信息分离，响应不得泄露 API Key、模型原始请求、Python 堆栈或未经裁剪的工具输出。

## 3. 当前接口

### 3.1 发起流式对话

```http
POST /api/v1/chat/completions/stream
Accept: text/event-stream
Content-Type: application/json
```

请求体：

```json
{
  "sessionCode": "session-xxx",
  "modelId": 1001,
  "message": "帮我分析一下本月订单数据",
  "attachments": [],
  "tools": [],
  "ext": {}
}
```

字段说明：

- `sessionCode`：可选，继续已有会话时传入；为空时后端创建新会话。
- `modelId`：可选，前端选择的 `ai_model_config.id`；为空时由后端选择默认模型。
- `message`：必填，用户本轮输入。
- `attachments`：可选，附件引用列表。
- `tools`：可选，本轮允许或期望使用的工具声明。
- `ext`：可选，扩展上下文。

模型解析规则：

1. 前端只能获得并提交启用模型的 `id`、展示名和非敏感模型信息，不返回 API Key。
2. 服务端按 `modelId` 联表读取 `ai_model_config` 与 `ai_client_config`，解析客户端类型、Base URL、API Key 和提供方真实模型 `api_model`。
3. 模型或关联客户端被停用、记录不存在时，请求直接失败，不回退使用前端提供的 URL、密钥或真实模型名。
4. 轮次中的 `model_code` 保存内部配置编码，`actual_model` 保存最终解析出的提供方真实模型名。
5. 兼容接口 `/api/v1/chat/completions/stream` 与 `chat-event.v2` 接口使用同一套 `modelId` 解析逻辑。

### 3.2 重新挂接流式输出

```http
POST /api/v1/chat/stream/reconnect
Accept: text/event-stream
Content-Type: application/json
```

请求体：

```json
{
  "runId": "run-xxx",
  "lastEventId": "18.2",
  "sessionCode": "session-xxx",
  "roundCode": "round-xxx"
}
```

语义：

- `runId`：优先使用，用于精确定位运行任务。
- `lastEventId`：可选，只重放当前运行中 `eventId` 大于该值的事件。
- `sessionCode`、`roundCode`：兼容定位字段，也用于运行时任务已过期后的持久化快照回放。
- 如果运行任务仍存在，后端重新订阅该任务，并从内存或 Redis 的有限事件缓存中重放。
- 如果运行任务不存在，后端从会话历史回放初始化、回答快照以及完成、失败或取消状态；此时 `sessionCode` 和 `roundCode` 必填。
- `lastEventId` 是请求体字段。当前接口为 POST，不依赖浏览器原生 `EventSource` 的 `Last-Event-ID` 请求头。
- 当前默认每个任务最多缓存 512 个事件；Redis 活动任务 TTL 默认 2 小时，终态任务和本地终态缓存默认保留 30 分钟，均可通过 `ai.chat.runtime` 配置调整。

重连与去重规则：

1. 客户端只有在事件 JSON 解析成功且本地状态应用成功后，才更新 `lastEventId`；解析失败不能跳过该事件序号。
2. 实时事件以 `(runId, eventId)` 作为幂等键。收到已应用事件时必须忽略，尤其不能再次追加 `answer_delta` 或 `assistant.message.delta`。
3. `{sequence}.{subSequence}` 按数字分段比较，不得按普通字符串比较。例如 `12.10` 大于 `12.2`。
4. 同一个 `runId` 同一时刻最多保留一个活动订阅。新重连成功后，应先关闭旧连接，避免双流重复消费。
5. 建议自动重试最多 3 次，使用 1 秒、2 秒、4 秒指数退避并加入小幅随机抖动。网络错误、HTTP 408/429 和 5xx 可以重试；其他 4xx 默认不可重试。
6. 自动重试前先查询运行状态：`ACCEPTED`、`RUNNING`、`CANCELLING` 时继续重连；`WAITING_INPUT` 时转入等待用户输入；运行任务已终态或已过期时请求持久化快照。
7. 重试耗尽后保留已接收回答和活动，展示“连接已中断”，提供“重新连接”和“停止任务”操作，不得把本轮标记为完成。

### 3.3 查询运行任务

```http
POST /api/v1/task/chat/status
Content-Type: application/json
```

请求体支持按 `runId` 精确查询，也兼容使用 `sessionCode`、`roundCode` 查询最近一次匹配任务：

```json
{
  "runId": "run-xxx",
  "sessionCode": "session-xxx",
  "roundCode": "round-xxx"
}
```

响应示例：

```json
{
  "runId": "run-xxx",
  "ownerNodeId": "chat-node-1",
  "requestId": "trace-id",
  "sessionCode": "session-xxx",
  "roundCode": "round-xxx",
  "active": true,
  "status": "RUNNING",
  "createdAt": "2026-07-11T12:00:00Z",
  "startedAt": "2026-07-11T12:00:00.050Z",
  "finishedAt": null,
  "error": null,
  "taskCodes": ["run-xxx"]
}
```

`ownerNodeId` 用于多实例运行时定位任务所属应用节点。`taskCodes` 是当前兼容字段，内容为 `runId`。

### 3.4 中断运行任务

```http
POST /api/v1/task/chat/stop
Content-Type: application/json
```

请求字段与任务查询接口相同，推荐始终传 `runId`。返回值为布尔值：

- 活动任务成功接受取消：`true`。
- 已经处于 `CANCELLED` 的任务再次取消：`true`，保证幂等。
- 任务不存在，或者已经 `COMPLETED` / `FAILED`：`false`。
- 多实例 Redis 模式下，可以在任意应用节点发起取消；运行时通过取消标记和通知将请求传递给任务所属节点。

## 4. SSE 传输格式

SSE 的 `event` 名称必须与 payload 中的 `eventType` 保持一致。

示例：

```text
id: 3
event: progress
data: {"protocolVersion":"1.0","runId":"run-xxx","eventId":"3","timestamp":1783760000000,"eventType":"progress","source":"WORKFLOW","phase":"STARTED","message":"start node: query_planning"}

id: 4
event: answer
data: {"protocolVersion":"1.0","runId":"run-xxx","eventId":"4","timestamp":1783760000100,"eventType":"answer","source":"RENDER","phase":"COMPLETED","answer":"{\"component\":\"Table\"}","status":"SUCCESS"}

id: 5
event: complete
data: {"protocolVersion":"1.0","runId":"run-xxx","eventId":"5","timestamp":1783760000200,"eventType":"complete","source":"CONVERSATION","phase":"COMPLETED","status":"SUCCESS"}
```

约束：

- SSE `event` 必须等于 payload 的 `eventType`。
- SSE `id` 必须等于 payload 的 `eventId`。
- `eventId` 在同一个 `runId` 内单调递增，不保证跨任务全局唯一。
- `timestamp` 为 Unix epoch 毫秒时间戳。

### 4.1 心跳与无活动处理

- SSE 服务端可以发送注释心跳帧 `: heartbeat\n\n`。心跳没有 `eventId`，不进入业务事件分发，也不能推进 `lastEventId`。
- WebSocket 可以使用协议层 ping/pong；不得把心跳伪装成 `thinking.updated` 或回答事件。
- 建议服务端心跳间隔不超过 15 秒，客户端无活动检测阈值为 30 秒；部署环境可调整，但客户端阈值应大于两次心跳间隔。
- 客户端收到任何有效字节、SSE 注释心跳、WebSocket pong 或业务事件时刷新最后活动时间。
- 超过无活动阈值仅表示连接可能失活。客户端应进入 `RECONNECTING` 并查询任务状态，不能直接转为 `FAILED`、`CANCELLED` 或 `COMPLETED`。
- 未实现心跳的部署中，长时间没有业务事件同样不表示任务结束；客户端可用相同状态查询与有限重连机制探活。
- SSE EOF、读取超时、代理断流和 WebSocket close 都属于传输层信号。若此前未收到本节定义的交互终态事件，必须按异常断流处理。

## 5. 事件结构

所有事件统一使用以下结构：

```json
{
  "protocolVersion": "1.0",
  "runId": "run-xxx",
  "eventId": "3",
  "timestamp": 1783760000000,
  "eventType": "progress",
  "progressType": "NODE",
  "source": "WORKFLOW",
  "phase": "STARTED",
  "requestId": "trace-id",
  "sessionCode": "session-xxx",
  "sessionName": "本月订单数据分析",
  "roundCode": "round-xxx",
  "delta": "",
  "answer": "",
  "status": "RUNNING",
  "message": "start node: query_planning",
  "ext": {}
}
```

字段说明：

- `protocolVersion`：协议版本，当前固定为 `1.0`。
- `runId`：当前运行任务编码。实时任务事件一定存在；历史持久化快照回放可能为空。
- `eventId`：当前运行内的事件序号，同时写入 SSE `id`；历史快照回放会重新从 `1` 编号。
- `timestamp`：事件生成时间，Unix epoch 毫秒。
- `eventType`：业务事件类型，见“事件类型”。
- `progressType`：进度子类型，仅用于 `progress`，见“progress 子类型”。
- `source`：事件来源，表示哪个业务域或节点产生该事件。
- `phase`：事件阶段，表示当前来源的执行阶段。
- `requestId`：本次请求链路追踪 ID。
- `sessionCode`：会话编码。
- `sessionName`：会话名称。
- `roundCode`：本轮对话编码。
- `delta`：增量回答内容，仅用于 `answer_delta`。
- `answer`：当前完整回答或最终回答快照。
- `status`：当前事件状态。
- `message`：面向前端状态展示或错误展示的文本。
- `ext`：扩展字段，承载节点、产物、渲染页等结构化信息。

## 6. 事件类型

### 6.1 运行时事件

#### `run.accepted`

表示运行管理器已经创建任务并分配 `runId`。它通常是实时流中的第一个事件，此时新会话的 `sessionCode`、`roundCode` 可能尚未生成。

```json
{
  "protocolVersion": "1.0",
  "runId": "run-xxx",
  "eventId": "1",
  "eventType": "run.accepted",
  "source": "RUNTIME",
  "phase": "ACCEPTED",
  "requestId": "trace-id",
  "status": "ACCEPTED",
  "message": "conversation run accepted"
}
```

#### `run.started`

表示任务已经进入执行线程。它不等价于会话初始化完成；`sessionCode` 和 `roundCode` 通常在后续 `progress + CONVERSATION + STARTED` 中返回。

```json
{
  "runId": "run-xxx",
  "eventId": "2",
  "eventType": "run.started",
  "source": "RUNTIME",
  "phase": "RUNNING",
  "status": "RUNNING",
  "message": "conversation run started"
}
```

#### `run.cancelled`

表示任务已被用户中断，是终态事件。实时取消事件由运行时产生，当前主要依赖 `runId` 定位任务，不保证回填 `sessionCode`、`roundCode`；历史回放中的取消事件由会话历史产生，会携带会话和轮次，但 `runId` 可能为空。

```json
{
  "runId": "run-xxx",
  "eventId": "12",
  "eventType": "run.cancelled",
  "source": "RUNTIME",
  "phase": "CANCELLED",
  "status": "CANCELLED",
  "message": "conversation run cancelled"
}
```

前端处理规则：

- 收到 `run.accepted` 后保存 `runId`，后续重连、状态查询和停止优先使用它。
- 收到 `run.started` 后可以展示任务已进入执行状态，但不能据此推断会话和轮次已经创建。
- 收到 `run.cancelled` 后停止加载态，并将本轮标记为用户取消，而不是执行失败。

### 6.2 `progress`

用于通知前端执行进度、节点状态、节点内活动或中间阶段变化。

`progress` 不新增大量顶层事件类型，而是在 payload 中通过 `progressType` 区分进度语义。前端应以 `eventType = progress` 做一级分发，再以 `progressType` 做二级分发。

progress 子类型：

- `PLAN`：计划进度，声明本轮意图、工作流和计划执行节点。用于前端初始化步骤条、节点列表和整体执行图。
- `NODE`：节点进度，更新某个 workflow 节点的生命周期状态。用于前端更新节点开始、运行中、完成、跳过或失败。
- `ACTIVITY`：活动进度，更新某个节点内部具体活动的执行状态。用于前端展示 AI 调用、知识库检索、工具调用、SQL 执行、数据处理、渲染生成等细节。

当前实现状态：

- `ACTIVITY` 已由专用发布方法显式写入；AI Agent（Python）的执行开始、工具调用、工具结果、角色切换、完成和失败均进入该通道。
- AI Agent 活动在发送实时事件前写入 `ai_chat_activity`；同一工具调用通过 `correlationCode`（通常来自 `callId` 或 `activityCode`）关联多条生命周期事件。
- `PLAN`、`NODE` 是协议支持和推荐值，但当前部分工作流事件仍未显式设置 `progressType`。
- 前端必须保留“`progressType` 为空”的兼容逻辑，不能假设所有节点事件都已经规范化为 `NODE`。

适用场景：

- 会话开始。
- 意图分析完成后，声明本轮意图类型、工作流和计划节点。
- workflow 开始执行某个节点。
- 节点执行完成但没有最终回答输出。
- 节点内部开始或完成 AI 调用、知识库检索、工具调用、SQL 执行等活动。
- 意图分析、查询计划、SQL 生成、结果评估等过程状态变化。

推荐结构：

```json
{
  "eventType": "progress",
  "progressType": "PLAN",
  "source": "WORKFLOW",
  "phase": "READY",
  "status": "RUNNING",
  "message": "workflow plan prepared",
  "ext": {
    "intent": {
      "intentType": "QUERY_RENDER",
      "confidence": 0.92
    },
    "workflow": {
      "workflowCode": "ai-chat-query-render",
      "workflowName": "智能问数渲染流程"
    },
    "nodes": [
      {
        "nodeCode": "query_planning",
        "nodeName": "查询规划",
        "order": 1
      },
      {
        "nodeCode": "knowledge_retrieve",
        "nodeName": "知识检索",
        "order": 2
      },
      {
        "nodeCode": "render",
        "nodeName": "结果渲染",
        "order": 3
      }
    ]
  }
}
```

对应的 `chat-event.v2` 对外事件统一投影为：

```json
{
  "eventType": "thinking.updated",
  "payload": {
    "action": "activity.updated",
    "progressType": "ACTIVITY",
    "nodeCode": "render",
    "thinking": {
      "status": "running",
      "statusText": "AI Agent 正在调用工具"
    },
    "activity": {
      "id": "call-xxx",
      "activityCode": "call-xxx",
      "activityType": "TOOL_CALL",
      "activityName": "render_json_validate_tool",
      "title": "render_json_validate_tool",
      "description": "AI Agent 正在调用工具",
      "source": "AI_AGENT",
      "phase": "RUNNING",
      "status": "running",
      "inputSummary": "...",
      "outputSummary": null
    }
  }
}
```

示例：

```json
{
  "eventType": "progress",
  "progressType": "NODE",
  "source": "WORKFLOW",
  "phase": "STARTED",
  "status": "RUNNING",
  "message": "start node: query_planning",
  "ext": {
    "nodeCode": "query_planning"
  }
}
```

节点内活动示例：

```json
{
  "eventType": "progress",
  "progressType": "ACTIVITY",
  "source": "QUERY_PLAN",
  "phase": "COMPLETED",
  "status": "SUCCESS",
  "message": "query plan generated by ai model",
  "ext": {
    "nodeCode": "query_planning",
    "activity": {
      "activityCode": "query_plan_ai_call",
      "activityType": "AI_CALL",
      "activityName": "生成查询规划",
      "provider": "QWEN",
      "model": "qwen-plus",
      "inputSummary": "统计本月每天的订单数和成交金额",
      "outputSummary": "按天聚合 order_count 和 deal_amount"
    },
    "artifacts": [
      {
        "artifactType": "QUERY_PLAN",
        "contentFormat": "JSON",
        "summary": "包含指标、时间粒度和过滤条件"
      }
    ],
    "durationMs": 1280
  }
}
```

活动类型推荐值：

- `AI_CALL`：模型调用。
- `KNOWLEDGE_SEARCH`：知识库检索。
- `TOOL_CALL`：工具调用。
- `SQL_EXECUTE`：SQL 执行。
- `DATA_PROCESS`：数据处理。
- `RENDER_GENERATE`：渲染产物生成。

前端处理规则：

- `progressType = PLAN`：初始化或更新本轮意图、工作流和计划节点列表。
- `progressType = NODE`：按 `ext.nodeCode` 更新节点状态。
- `progressType = ACTIVITY`：按 `activity.activityCode` 合并同一活动的开始/完成状态，挂载到 `nodeCode` 对应节点下，展示活动类型、输入摘要、输出摘要、产物和耗时。
- 历史或兼容事件如果没有 `progressType`，前端可按 `NODE` 或普通过程消息兜底处理。

### 6.3 `answer_delta`

用于真正的 token 级或片段级增量输出。

适用场景：

- 模型接口本身支持流式返回。
- 前端需要边生成边追加显示回答内容。

示例：

```json
{
  "eventType": "answer_delta",
  "source": "SIMPLE_CHAT",
  "phase": "RUNNING",
  "delta": "本月订单",
  "status": "RUNNING"
}
```

前端处理规则：

- 将 `delta` 追加到当前轮次的临时回答中。
- 不应把 `answer_delta` 当作最终结果。

### 6.4 `answer`

用于通知前端当前完整回答已准备好。

适用场景：

- 简单聊天回答生成完成。
- Render JSON 生成完成。
- reconnect 时回放当前回答快照。

Render JSON 生成完成时，必须使用：

```text
eventType = answer
source = RENDER
phase = COMPLETED
```

示例：

```json
{
  "eventType": "answer",
  "source": "RENDER",
  "phase": "COMPLETED",
  "answer": "{\"component\":\"Table\",\"props\":{\"data\":[]}}",
  "status": "SUCCESS",
  "message": "render json prepared",
  "ext": {
    "contentFormat": "JSON",
    "artifactType": "RENDER_JSON",
    "renderPageCode": "render-xxx"
  }
}
```

前端处理规则：

- 如果 `source = RENDER`，按 Render JSON 处理 `answer`。
- 如果 `source = SIMPLE_CHAT`，按普通文本回答处理 `answer`。
- `answer` 可以覆盖前端已通过 `answer_delta` 拼接出的临时回答。

### 6.5 `clarification`

用于通知前端当前轮次需要用户补充或确认信息。

适用场景：

- 意图不明确。
- 查询条件不足。
- workflow 进入需要用户确认的暂停状态。

示例：

```json
{
  "eventType": "clarification",
  "source": "WORKFLOW",
  "phase": "READY",
  "status": "RUNNING",
  "message": "请确认要查询的时间范围"
}
```

前端处理规则：

- 停止当前轮次的加载态。
- 展示 `message` 作为追问或确认问题。
- 用户补充后发起下一轮请求。

当前实现说明：`WAITING_INPUT` 和同一 `runId` 内提交交互答案尚未接入工作流恢复机制。当前收到 `clarification` 后，应按“结束当前执行、下一轮补充信息”处理，不能假设可以通过 WebSocket 恢复原任务。

`chat-event.v2` 将该语义投影为 `assistant.input_required`。它是当前流的交互终态，但不是运行结果的成功或失败终态。前端应停止旋转加载态，保留上下文，展示明确的补充输入提示并聚焦输入区；当前实现通过新一轮消息继续，未来工作流恢复能力接入后才允许复用同一 `runId`。

### 6.6 `error`

用于通知前端当前流式任务失败。

适用场景：

- workflow 执行失败。
- 节点执行失败。
- 模型调用失败。
- Render JSON 生成或校验失败。

示例：

```json
{
  "eventType": "error",
  "source": "RENDER",
  "phase": "FAILED",
  "status": "FAILED",
  "message": "暂时无法生成可视化结果，请稍后重试。",
  "ext": {
    "error": {
      "code": "RENDER_VALIDATE_FAILED",
      "userMessage": "暂时无法生成可视化结果，请稍后重试。",
      "retryable": true,
      "traceId": "trace-id",
      "detail": "render schema validation failed"
    }
  }
}
```

安全错误结构：

- `code`：稳定、可枚举的机器错误码，前端可以据此选择恢复动作，不应使用异常类名。
- `userMessage`：可以直接向普通用户展示的安全文案；缺失时前端使用统一兜底文案。
- `retryable`：是否建议重试。它只表达服务端判断，前端仍须遵守有限重试规则。
- `traceId`：排障关联标识，建议与事件 `requestId` 保持一致，可提供复制入口。
- `detail`：可选的裁剪后技术详情，只在开发或有权限的诊断界面展示。不得包含堆栈、密钥、认证头、完整模型提示词或敏感工具结果。

`chat-event.v2` 的 `round.failed` 应在 `payload.error` 携带同一结构，并可在 `payload.round` 返回失败快照。兼容实现可以继续提供 `message`，但前端优先使用 `payload.error.userMessage`，不能直接展示原始异常字符串。

前端处理规则：

- 停止当前轮次加载态。
- 展示 `message`。
- 保留已收到的 `progress` 和 `answer_delta`，但不得标记整轮成功。

### 6.7 `complete`

用于通知前端整轮会话流程已完成。

适用场景：

- workflow 已正常结束。
- 后端已完成会话轮次状态更新。
- 前端可以刷新会话详情、历史列表和产物列表。

示例：

```json
{
  "eventType": "complete",
  "source": "CONVERSATION",
  "phase": "COMPLETED",
  "answer": "{\"component\":\"Table\",\"props\":{\"data\":[]}}",
  "status": "SUCCESS",
  "message": "conversation completed"
}
```

前端处理规则：

- 停止当前轮次加载态。
- 标记执行完成。
- 使用 `sessionCode`、`roundCode` 刷新会话详情。
- 不要用 `complete` 表示 Render JSON 或某个 workflow 节点完成。

### 6.8 交互终态与运行终态

`chat-event.v2` 当前流的交互终态事件集合固定为：

| 事件 | 前端状态 | 是否运行结果终态 | 语义 |
| --- | --- | --- | --- |
| `round.completed` | `COMPLETED` | 是 | 本轮唯一成功终态 |
| `round.failed` | `FAILED` | 是 | 执行失败，保留已生成内容和活动 |
| `round.cancelled` | `CANCELLED` | 是 | 用户或系统中断，不应展示为执行失败 |
| `assistant.input_required` | `WAITING_INPUT` | 否 | 当前流结束并等待用户补充，不表示成功或失败 |

内部 `1.0` 事件分别对应 `complete`、`error`、`run.cancelled` 和 `clarification`。`thinking.completed` 只是思考阶段结束，`assistant.message.delta` 只是内容更新，均不是交互终态。

前端在收到交互终态后才可以结束当前流式 UI 状态，并按下列规则处理尚未结束的活动：

- `round.failed`：明确失败的当前活动标记为 `failed`；无法确认结果但被终止的活动标记为 `interrupted`。
- `round.cancelled`：仍为 `running` 的活动标记为 `interrupted`，并注明取消来源。
- `assistant.input_required`：仍为 `running` 的活动标记为 `interrupted` 或 `waiting_input`，不得标记为完成。
- `round.completed`：只有已收到完成事件或由最终快照确认成功的活动才标记为 `completed`；缺少结果的活动不得被批量推断成功。

EOF、HTTP 200、SSE `onCompletion`、读取超时、网络错误和 WebSocket close 不属于上述集合。如果连接结束前没有收到交互终态，客户端必须进入 `RECONNECTING`，不能把助手消息或思考活动改为 `completed`。

## 7. 事件来源

当前约定的 `source`：

- `RUNTIME`：运行任务生命周期事件。
- `CONVERSATION`：会话级事件。
- `WORKFLOW`：工作流级事件。
- `CHAT_MESSAGE`：聊天消息准备或保存事件。
- `INTENT_ANALYZE`：意图分析事件。
- `QUERY_PLAN`：查询计划事件。
- `SIMPLE_CHAT`：普通聊天回答事件。
- `RENDER`：Render JSON 生成事件。
- `EVALUATION`：结果评估事件。

前端不应只依赖 `source` 判断事件大类，事件主分发应以 `eventType` 为准。

## 8. 事件阶段

当前约定的 `phase`：

- `ACCEPTED`：运行任务已创建、等待执行。
- `STARTED`：已开始。
- `RUNNING`：执行中。
- `READY`：已准备好，通常表示等待前端展示或用户补充。
- `COMPLETED`：已完成。
- `SKIPPED`：已跳过。
- `FAILED`：已失败。
- `CANCELLED`：运行任务已被用户取消。

### 8.1 运行任务状态

任务状态接口当前可能返回：

- `ACCEPTED`：任务已创建。
- `RUNNING`：任务正在执行。
- `WAITING_INPUT`：为后续交互式工作流预留；当前普通对话流程尚未进入该状态。
- `CANCELLING`：多实例取消请求已写入，等待所属节点完成中断。
- `CANCELLED`：任务已取消，终态。
- `COMPLETED`：任务正常完成，终态。
- `FAILED`：任务执行失败，终态。

`status` 字段的语义取决于事件对象：运行时事件使用任务状态，节点和活动事件可以使用 `RUNNING`、`SUCCESS`、`FAILED` 等业务状态。

状态语义约束：

- `WAITING_INPUT` 是稳定的非结果终态：当前流不再保持加载动画，但任务上下文可以保留。当前实现通过新轮次补充，不能把它显示成 `FAILED` 或 `COMPLETED`。
- `FAILED` 表示系统、模型、工具或工作流未能完成任务。前端保留部分回答和活动，展示安全错误信息及可用的重试操作。
- `CANCELLED` 表示用户或系统明确中断。它与失败分开展示，并保留“重新发起”入口。
- `CANCELLING` 只表示取消请求已接受，前端显示“正在停止”，直到收到 `round.cancelled` 或状态查询确认 `CANCELLED`。
- UI 可以增加只存在于客户端的 `RECONNECTING` 状态；它不是后端运行状态，表示流失活但任务结果未知。

## 9. 推荐前端处理逻辑

前端应按 `eventType` 做一级分发：

```text
progress       更新计划、节点状态、节点内活动
answer_delta   追加输出内容
answer         覆盖或确认当前完整回答
clarification  停止当前轮，展示补充确认问题
error          标记失败，展示 message
complete       标记整轮完成，刷新会话详情
run.accepted   保存 runId，任务进入已接收状态
run.started    任务进入执行状态
run.cancelled  标记用户取消并停止加载态
```

推荐补充分发：

```text
progress + progressType=PLAN      -> 初始化意图、工作流、计划节点
progress + progressType=NODE      -> 更新节点生命周期状态
progress + progressType=ACTIVITY  -> 更新节点内活动明细
answer + source=SIMPLE_CHAT  -> 文本回答
answer + source=RENDER       -> Render JSON 回答
error  + source=RENDER       -> Render JSON 生成失败
```

当前仓库前端的 `/test/chat` 已处理 `run.*`、`thinking.*`、`assistant.message.delta`、`ACTIVITY`、完成和失败事件，并在发出请求后立即创建“正在连接 AI / 思考中”占位。正式聊天页已使用 `modelId` 和基础流式回答事件，但思考抽屉仍以 `/test/chat` 的实现为交互参考逐步收敛。

### 9.1 异常断流处理流程

客户端必须记录本轮是否已经收到交互终态事件。流读取结束时按以下顺序处理：

1. 已收到 `round.completed`、`round.failed`、`round.cancelled` 或 `assistant.input_required`：按对应状态结束本轮 UI，不再重连。
2. 未收到交互终态但发生 EOF、超时或网络错误：保留当前内容，将本地状态切换为 `RECONNECTING`，展示“连接中断，正在恢复”。
3. 查询 `/api/chat/runs/{runId}`。运行中则按 `runId + lastEventId` 重新挂接；`WAITING_INPUT` 则进入等待输入；任务终态或运行缓存不存在则用 `sessionCode + roundCode` 请求持久化快照。
4. 重放事件先按 `(runId, eventId)` 去重，再更新 UI。不能因为重放而重复追加消息、活动或 artifact。
5. 自动恢复最多 3 次。恢复失败后停止自动请求，保持失败前页面内容，展示可操作的“重新连接”和“停止任务”。

### 9.2 `chat-event.v2` 持久化回放

运行时任务或有限事件缓存过期后，重连端点必须支持使用 `sessionCode + roundCode` 回放持久化快照，并继续输出 `chat-event.v2` 包络。请求应同时携带原 `runId`（若已知）和 `lastEventId`，便于诊断与兼容。

持久化回放至少应包含：

- `session.initialized` 与 `round.initialized`，用于恢复会话和轮次标识。
- 当前助手消息完整快照；优先作为可覆盖的完整消息块发送，不重放不可验证的 token 增量。
- 已持久化的 thinking/activity 时间线，或可用于查询 `/thinking` 的引用。
- `round.completed`、`round.failed`、`round.cancelled` 或 `assistant.input_required` 中与持久化状态一致的一个交互终态。

回放事件仍须使用 `schemaVersion=chat-event.v2`。建议在 `payload.replay` 增加兼容性元数据：

```json
{
  "replayId": "replay-xxx",
  "mode": "PERSISTED_SNAPSHOT",
  "source": "CONVERSATION_HISTORY",
  "snapshot": true
}
```

`payload.replay` 是可选字段，旧客户端可以忽略。持久化回放可以重新编号 `eventId`，但同一次回放流内必须单调递增；存在 `replayId` 时客户端以 `(replayId, eventId)` 去重。兼容服务未提供 `replayId` 时，客户端按 `sessionCode + roundCode + eventType + 业务对象 id` 幂等覆盖，不能将快照内容重复追加。

## 10. 后端实现边界

当前实现分层：

1. `ConversationController`：处理 HTTP 入参、用户上下文、traceId，并将流式请求交给传输适配器。
2. `SseConversationTransport`：保留旧版内部事件 SSE 接口；`ProtocolSseConversationTransport` 对外发送 `chat-event.v2`。
3. `ConversationRunManager`：创建 `runId`，管理任务状态、执行线程、事件缓存、订阅、查询和取消。
4. `ConversationRunClusterCoordinator`：抽象单实例或多实例协调；当前有本地空协调实现和 Redis 实现。
5. `ConversationExecutionService`：编排会话准备和 workflow 执行，但不依赖 SSE。
6. Workflow 和 Node：通过 `ConversationEventPublisher` 发布 `ConversationQueryStreamEvent`。
7. `ChatTransportProtocolAdapter`：把内部运行/工作流事件投影为前端事件及 `payload` 快照。
8. `ChatWebSocketHandler`：复用运行任务订阅、重放和中断能力，并编码与 SSE 相同的包络。
9. `ConversationCancellation`：向 workflow 提供协作式取消检查。
10. `WorkflowHistoryRecorder`：在活动发送前写入 `ai_chat_activity`；`ConversationProtocolQueryService` 通过思考详情接口返回真实活动时间线。

关键约束：

- `SseEmitter` 只允许存在于 `web` 传输适配层。
- `ConversationRuntimeContext` 只持有传输无关的 `ConversationEventPublisher` 和 `ConversationCancellation`。
- WebSocket 已复用 `ConversationRunManager.subscribe()`；后续增加交互式恢复时仍不得修改 workflow 与传输层的依赖方向。
- 本地模式下任务和事件缓存在当前进程；Redis 模式下共享任务快照、索引、有限事件列表、取消标记，并通过 Pub/Sub 实时通知其他实例。
- 运行时实时事件缓存不是永久审计日志。缓存过期后，reconnect 仍只能回退到回答和终态快照；但已持久化的活动可通过 `/api/chat/sessions/{sessionCode}/rounds/{roundCode}/thinking` 查询，不依赖运行时缓存。
- `ai_chat_activity` 记录结构化活动摘要和详情，不存储模型 API Key；敏感工具输出进入摘要或 `detail_json` 前应由活动生产方裁剪。
- 活动记录属于可观测性和历史详情数据。默认采用 best-effort、异步队列或 outbox 降级策略；活动表、日志或消息队列暂时不可用时，应记录监控告警，但不能因此把原本可成功的模型回答改为 `round.failed`。
- 如果业务场景要求活动审计强一致，必须通过显式配置启用，并使用稳定错误码说明失败原因；不得依赖未捕获的数据库异常隐式中断整轮。
- 活动持久化失败后，实时 `thinking.updated` 可以继续发送，并可在诊断字段中标记 `persistenceStatus=degraded`。该字段不能向普通用户暴露内部数据库信息。
- `ProtocolSseConversationTransport` 和 WebSocket 的 `chat-event.v2` 重连必须同时实现运行时缓存重放与第 9.2 节的持久化快照回放，不能仅依赖仍驻留内存或 Redis 的活动任务。

## 11. 兼容说明

如果历史前端仍按 `init`、`chunk`、`complete`、`error` 处理事件，需要迁移为：

```text
init   -> progress + source=CONVERSATION + phase=STARTED
chunk  -> answer_delta
final  -> answer
done   -> complete
```

迁移后，前端应支持以下事件集合：

```text
run.accepted
run.started
run.cancelled
progress
answer_delta
answer
clarification
error
complete
```

其中 `run.*` 属于运行任务事件，其余属于对话业务事件。

## 12. 案例说明

### 12.1 智能问数流程

用户首轮输入：

```json
{
  "message": "统计本月每天的订单数和成交金额",
  "modelId": 1001
}
```

后端先创建运行任务并返回运行时事件：

```text
id: 1
event: run.accepted
data: {"protocolVersion":"1.0","runId":"run-a001","eventId":"1","eventType":"run.accepted","source":"RUNTIME","phase":"ACCEPTED","requestId":"trace-001","status":"ACCEPTED","message":"conversation run accepted"}

id: 2
event: run.started
data: {"protocolVersion":"1.0","runId":"run-a001","eventId":"2","eventType":"run.started","source":"RUNTIME","phase":"RUNNING","requestId":"trace-001","status":"RUNNING","message":"conversation run started"}
```

随后创建新会话和新轮次，并返回会话初始化事件：

```text
event: progress
data: {"eventType":"progress","source":"CONVERSATION","phase":"STARTED","requestId":"trace-001","sessionCode":"session-a001","sessionName":"统计本月每天的订单数","roundCode":"round-a001","status":"RUNNING","message":"conversation started","ext":{}}
```

前端收到该事件后，应立即保存 `sessionCode`、`roundCode`，并将页面路由切换到当前会话。

随后后端推送意图分析和工作流计划事件。以下带 `progressType=PLAN/NODE` 的内容表示推荐的规范化结构；当前实现中的部分对应事件可能不带 `progressType`，或者只携带较精简的 `ext`：

```text
event: progress
data: {"eventType":"progress","progressType":"PLAN","source":"WORKFLOW","phase":"READY","requestId":"trace-001","sessionCode":"session-a001","sessionName":"统计本月每天的订单数","roundCode":"round-a001","status":"RUNNING","message":"workflow plan prepared","ext":{"intent":{"intentType":"QUERY_RENDER","confidence":0.92},"workflow":{"workflowCode":"ai-chat-query-render","workflowName":"智能问数渲染流程"},"nodes":[{"nodeCode":"query_planning","nodeName":"查询规划","order":1},{"nodeCode":"knowledge_retrieve","nodeName":"知识检索","order":2},{"nodeCode":"render","nodeName":"结果渲染","order":3}]}}
```

工作流开始执行查询规划、SQL 生成、结果评估等节点：

```text
event: progress
data: {"eventType":"progress","progressType":"NODE","source":"WORKFLOW","phase":"STARTED","requestId":"trace-001","sessionCode":"session-a001","sessionName":"统计本月每天的订单数","roundCode":"round-a001","status":"RUNNING","message":"start node: query_planning","ext":{"nodeCode":"query_planning"}}

event: progress
data: {"eventType":"progress","progressType":"ACTIVITY","source":"QUERY_PLAN","phase":"STARTED","requestId":"trace-001","sessionCode":"session-a001","sessionName":"统计本月每天的订单数","roundCode":"round-a001","status":"RUNNING","message":"start query plan ai call","ext":{"nodeCode":"query_planning","activity":{"activityCode":"query_plan_ai_call","activityType":"AI_CALL","activityName":"生成查询规划","provider":"QWEN","model":"qwen-plus","inputSummary":"统计本月每天的订单数和成交金额"}}}

event: progress
data: {"eventType":"progress","progressType":"ACTIVITY","source":"QUERY_PLAN","phase":"COMPLETED","requestId":"trace-001","sessionCode":"session-a001","sessionName":"统计本月每天的订单数","roundCode":"round-a001","status":"SUCCESS","message":"query plan prepared","ext":{"nodeCode":"query_planning","activity":{"activityCode":"query_plan_ai_call","activityType":"AI_CALL","activityName":"生成查询规划","provider":"QWEN","model":"qwen-plus","outputSummary":"按天聚合 order_count 和 deal_amount"},"artifacts":[{"artifactType":"QUERY_PLAN","contentFormat":"JSON","summary":"包含指标、时间粒度和过滤条件"}],"durationMs":1280}}

event: progress
data: {"eventType":"progress","progressType":"NODE","source":"WORKFLOW","phase":"COMPLETED","requestId":"trace-001","sessionCode":"session-a001","sessionName":"统计本月每天的订单数","roundCode":"round-a001","status":"SUCCESS","message":"node completed: query_planning","ext":{"nodeCode":"query_planning"}}

event: progress
data: {"eventType":"progress","progressType":"NODE","source":"WORKFLOW","phase":"STARTED","requestId":"trace-001","sessionCode":"session-a001","sessionName":"统计本月每天的订单数","roundCode":"round-a001","status":"RUNNING","message":"start node: render","ext":{"nodeCode":"render"}}
```

Render JSON 生成完成时，后端使用 `answer` 事件，而不是 `complete`：

```text
event: answer
data: {"eventType":"answer","source":"RENDER","phase":"COMPLETED","requestId":"trace-001","sessionCode":"session-a001","sessionName":"统计本月每天的订单数","roundCode":"round-a001","answer":"{\"component\":\"Chart\",\"props\":{\"type\":\"line\",\"title\":\"本月每日订单数与成交金额\",\"series\":[]}}","status":"SUCCESS","message":"render json prepared","ext":{"contentFormat":"JSON","artifactType":"RENDER_JSON","renderPageCode":"render-a001"}}
```

前端收到该事件后，应按 Render JSON 渲染 `answer`，并可记录 `ext.renderPageCode` 作为后续查看或刷新产物的依据。

整轮会话完成时，后端最后返回 `complete`：

```text
event: complete
data: {"eventType":"complete","source":"CONVERSATION","phase":"COMPLETED","requestId":"trace-001","sessionCode":"session-a001","sessionName":"统计本月每天的订单数","roundCode":"round-a001","answer":"{\"component\":\"Chart\",\"props\":{\"type\":\"line\",\"title\":\"本月每日订单数与成交金额\",\"series\":[]}}","status":"SUCCESS","message":"conversation completed","ext":{}}
```

前端收到 `complete` 后，应停止当前轮加载态，并刷新会话详情、历史列表和产物列表。

### 12.2 简单对话流程

用户首轮输入：

```json
{
  "message": "你是谁？",
  "modelId": 1001
}
```

后端同样先返回 `run.accepted`、`run.started`，再创建新会话和新轮次并返回会话初始化事件：

```text
event: progress
data: {"eventType":"progress","source":"CONVERSATION","phase":"STARTED","requestId":"trace-002","sessionCode":"session-b001","sessionName":"你是谁？","roundCode":"round-b001","status":"RUNNING","message":"conversation started","ext":{}}
```

意图分析判定为简单对话，并路由到 simple chat workflow：

```text
event: progress
data: {"eventType":"progress","source":"INTENT_ANALYZE","phase":"READY","requestId":"trace-002","sessionCode":"session-b001","sessionName":"你是谁？","roundCode":"round-b001","status":"RUNNING","message":"base intent analysis prepared","ext":{"intentType":"SIMPLE_CHAT"}}

event: progress
data: {"eventType":"progress","source":"INTENT_ANALYZE","phase":"READY","requestId":"trace-002","sessionCode":"session-b001","sessionName":"你是谁？","roundCode":"round-b001","status":"RUNNING","message":"workflow routed","ext":{"workflowCode":"ai-chat-simple-chat","intentType":"SIMPLE_CHAT"}}
```

如果执行节点使用流式模型调用，可以先返回 `answer_delta`。当前并非所有节点都走流式调用，因此前端不能要求每轮一定收到该事件：

```text
event: answer_delta
data: {"eventType":"answer_delta","source":"SIMPLE_CHAT","phase":"RUNNING","requestId":"trace-002","sessionCode":"session-b001","sessionName":"你是谁？","roundCode":"round-b001","delta":"我是一个","status":"RUNNING","message":"simple chat streaming"}

event: answer_delta
data: {"eventType":"answer_delta","source":"SIMPLE_CHAT","phase":"RUNNING","requestId":"trace-002","sessionCode":"session-b001","sessionName":"你是谁？","roundCode":"round-b001","delta":"中文 AI 助手。","status":"RUNNING","message":"simple chat streaming"}
```

简单对话完整回答生成完成时，返回 `answer`：

```text
event: answer
data: {"eventType":"answer","source":"SIMPLE_CHAT","phase":"COMPLETED","requestId":"trace-002","sessionCode":"session-b001","sessionName":"你是谁？","roundCode":"round-b001","answer":"我是一个中文 AI 助手，可以帮你进行问答、分析和内容生成。","status":"SUCCESS","message":"simple chat answer prepared","ext":{"contentFormat":"PLAIN_TEXT"}}
```

整轮会话完成时，返回 `complete`：

```text
event: complete
data: {"eventType":"complete","source":"CONVERSATION","phase":"COMPLETED","requestId":"trace-002","sessionCode":"session-b001","sessionName":"你是谁？","roundCode":"round-b001","answer":"我是一个中文 AI 助手，可以帮你进行问答、分析和内容生成。","status":"SUCCESS","message":"conversation completed","ext":{}}
```

如果当前底层模型暂不支持 token 级流式输出，可以不发送 `answer_delta`，直接发送 `answer` 和 `complete`。
