# Chat Memory 前端端到端验证报告（2026-08-11）

## 结论

本次验证的结论是 **部分通过，不能作为 Chat Memory 完整闭环验收通过**。

- 已验证：前端登录、页面发起聊天、SSE 完成、会话与轮次持久化、异步同步任务、RAGFlow Memory 写入及异步提取。
- 未通过：RAGFlow 返回的记忆无法通过平台归属校验，导致前端上下文接口返回 `SECURITY_REJECTED`；记忆没有注入 Agent，也不能继续验证“提升长期记忆 → 新会话跨会话召回”。
- 风险判断：这是安全降级而非越权泄漏。平台将不符合归属条件的 Provider 结果全部丢弃，聊天主链路仍可正常完成。

## 测试范围与环境

| 项目 | 实际值 |
| --- | --- |
| 代码分支 | `codex/chat-memory-ragflow-context` |
| 基线提交 | `ef469ab3 feat(chat): integrate RAGFlow memory and context recall` |
| 前端 | `http://127.0.0.1:5173`，Vue 3 + Vite 开发服务 |
| 网关 | `http://127.0.0.1:9764` |
| Chat 服务 | `127.0.0.1:13103`（开发人员通过 IDE 启动，测试期间未重启） |
| RAGFlow | 已通过项目配置的健康检查，配置来源为 Nacos `common.yaml` |
| 测试账号 | 前端默认本地管理员账号；令牌未记录、未输出 |
| 单租户兼容 | 绑定记录使用服务端固定范围 `single-tenant`，没有依赖 Token 的 `currentTenantId` |

## 主测试案例：页面发起一轮聊天

### 用例定义

| 字段 | 内容 |
| --- | --- |
| 用例编号 | `CM-E2E-UI-001` |
| 目标 | 验证从浏览器页面聊天请求到 RAGFlow 会话记忆写入、回读和 Agent 召回的完整链路 |
| 测试标识 | `MEMORY-E2E-20260811-B1` |
| 用户输入 | `这是 Chat Memory 页面全链路验证。请只回复确认标识：MEMORY-E2E-20260811-B1` |
| 期望 | SSE 正常完成；同步任务成功；RAGFlow 形成可召回记忆；上下文接口返回可用记忆，后续 Agent 可注入该上下文 |

### 执行过程与证据

| 步骤 | 操作与观察点 | 实际结果 | 判定 |
| --- | --- | --- | --- |
| 1 | 打开真实前端登录页并提交登录表单 | 路径由 `/auth/login` 进入 `/`；浏览器本地登录态存在 | 通过 |
| 2 | 在聊天首页确认模型并输入测试消息 | 页面显示已选择 `gpt-5.6-luna`；发送按钮由禁用变为可用 | 通过 |
| 3 | 点击页面“发送消息” | 浏览器实际发起 `POST /chat/api/chat/rounds/stream`；收到 `200 text/event-stream` | 通过 |
| 4 | 等待页面流式完成 | 新建会话 `session-79188e890e974057988848fed579a60a`；页面渲染助手确认标识；输入框清空并恢复可发送状态 | 通过 |
| 5 | 检查平台同步任务 | 轮次 `round-02b374b47df944028fbbc25af8a2d562` 的 `ADD_ROUND / SESSION` 任务为 `SUCCEEDED`，无重试、无稳定错误码 | 通过 |
| 6 | 检查 RAGFlow Memory | 绑定的会话 Memory 中出现该会话的 `raw` 消息和 `episodic` 提取消息；提取任务进度为 `1.0` | 通过 |
| 7 | 从同一浏览器登录态读取上下文 | `GET /chat/api/chat/sessions/{sessionCode}/context` 返回 HTTP `200`，但 `providerStatus=SECURITY_REJECTED`，各类可用记忆计数均为 `0` | 不通过 |
| 8 | 检查页面上下文提示 | 页面显示“记忆内容暂不可用，本次聊天不受影响” | 通过（降级提示） |
| 9 | 验证 Agent 注入及跨会话召回 | 前置上下文回读被安全拒绝，未继续执行，避免把未注入的模型回答误判为召回成功 | 阻断 |

## 链路结果

```text
浏览器登录
  -> 页面发送聊天请求
  -> 网关 / Chat SSE（200，页面完成）
  -> 会话与轮次持久化
  -> conversation_memory_sync_task: ADD_ROUND SUCCEEDED
  -> RAGFlow: raw 对话写入、episodic 记忆提取完成
  -> 平台归属校验
  -> SECURITY_REJECTED（停止，未注入 Agent）
```

## 失败定位

平台的 `ConversationMemoryProviderAccess.validateItems` 与 `ConversationContextAssembler` 会同时校验 Provider 返回项的 `memory_id`、`user_id` 和（会话范围内的）`session_id`。这是正确的越权防护策略。

本次对绑定会话 Memory 的只读查询观察到两个与当前实现不兼容的 Provider 行为：

1. 写入时传入的平台稳定用户标识没有被 RAGFlow 原样回显。返回的 `user_id` 是 RAGFlow 内部 UUID，而平台预期的是以 `platform-user-` 开头的散列标识；因此当前会话自己的记录也无法通过用户归属校验。
2. 以当前会话 `session_id` 查询时，RAGFlow 同时返回了一条不属于该会话的历史记录。平台对任意不匹配项采取“全部丢弃”，因此避免了错会话内容进入上下文。

所以 `SECURITY_REJECTED` 并不是 HTTP 鉴权失败，也不是 `currentTenantId` 缺失造成的失败；本轮实际绑定已使用 `single-tenant`，写入和同步均成功。阻断点是 **RAGFlow Memory API 的返回身份/过滤语义与平台严格归属校验的契约不一致**。

## 自动化回归

以下聚焦测试已在当前工作区通过：

| 测试范围 | 结果 |
| --- | --- |
| `ConversationRequestContextResolverTest`、`ConversationCommandFactoryTest`、`ChatTransportProtocolControllerTest` | 通过 |
| `ConversationContextAssemblerTest` | 通过，包含“Provider 返回其他用户记忆时丢弃全部”的安全分支 |
| `RagflowMemoryClientTest` | 通过，覆盖写入、列表、搜索、最近消息及异常映射 |

这些单元测试证明当前代码按预期保护了归属边界；它们没有覆盖当前外部 RAGFlow 实例“改写 user_id / 非严格 session 过滤”的真实契约差异，因此无法替代本次端到端验证。

## 测试后恢复与测试数据

- 已精确恢复本次临时启用的 Nacos `application-chat.yaml` 配置：恢复前先验证运行内容仍与本次临时配置快照一致，恢复后再次读取并确认 SHA-256 与原始快照一致。
- 已从同一浏览器登录态复核 Chat 上下文接口，返回 HTTP `200` 且 `providerStatus=DISABLED`，证明运行时已回到测试前的功能关闭状态。
- 为避免扩大数据影响，本次没有删除已有的测试会话、绑定和 RAGFlow Memory 资源。正常的“删除会话”流程会创建 `DELETE_SESSION` 任务，但该任务同样依赖当前失败的严格归属校验，预期会以 `MEMORY_SOURCE_INVALID` 终止；直接删除外部 RAGFlow Memory 需要额外的显式确认，不能作为本报告的隐式清理动作。
- 保留的隔离测试定位：`session-f87d9ed5d8704fba93b18f87c4fad847`（首轮写入验证）和 `session-79188e890e974057988848fed579a60a`（本报告主用例）。修复后可用它们复核兼容性，或由开发人员确认后按“平台业务 API + Provider 资源”成对清理。

## 验收状态

| 验收项 | 状态 |
| --- | --- |
| 页面登录与发起聊天 | 通过 |
| SSE 与会话/轮次完成 | 通过 |
| 记忆控制面绑定和异步任务 | 通过 |
| RAGFlow 存储核心记忆数据 | 通过 |
| 当前会话记忆回读 | 不通过 |
| Agent 上下文注入 | 未验证（被回读阻断） |
| 记忆提升到长期空间 | 未验证（被回读阻断） |
| 新会话跨会话召回 | 未验证（被回读阻断） |

## 后续建议

在修复前，不应将 Chat Memory 标记为“完整可用”。建议先对接当前 RAGFlow 实例的真实 Memory API 契约，并选择其中一种最小修复方向：

1. 使用 RAGFlow 能稳定回显且可过滤的 Provider 侧用户/会话标识，并将该映射作为轻量控制面元数据保存；或
2. 若该版本不保证 `user_id` 与 `session_id` 的查询/回显语义，调整平台读取路径为先按绑定 Memory 获取结果，再以平台可验证的 Provider 消息定位信息做精确筛选，同时保持“出现无法证明归属的内容即不注入”的保护原则。

修复后需重新执行本用例，并新增：会话记忆可见、确认提升长期记忆、新建会话命中长期记忆且 Agent 响应可证明使用了该上下文，才可完成完整链路验收。

## 修复复验追加（2026-08-11）

针对“RAGFlow 中已有记忆，但记忆管理列表显示暂无长期记忆”的问题，已完成调用链复核和代码修复。

### 根因

RAGFlow 的 Memory 分页接口会返回 `raw` 父消息及已提炼的 `procedural/semantic/episodic` 子消息元数据，但部分部署不会在分页响应中返回子消息正文。平台原先只拿分页响应映射出的 `content` 做可见性过滤，因此所有提炼消息都被过滤，页面最终得到 `items=0`；这不是记忆不存在，也不是数据库缺表。

### 修复内容

- `RagflowMemoryClient` 在列出 Memory 后，对缺少正文的非 `RAW` 消息调用 RAGFlow 内容详情接口补齐正文。
- `RagflowMemoryResponseMapper` 增加内容详情响应映射，仅提取文本，不把向量数据带入平台控制面。
- `RAW` 父消息仍不直接展示，提炼出的子消息按既有类型和归属规则展示。
- 增加“分页只有元数据、正文通过详情接口补齐”的回归测试。

### 修复后自动化验证

| 验证项 | 结果 |
| --- | --- |
| `RagflowMemoryClientTest` | 5 项通过，覆盖正文详情补齐和提炼子消息 |
| Chat Memory 核心测试 | 6 项通过 |
| Chat Boot 全量编译 | 通过 |
| 前端 `npm run build` | 通过；仅保留既有 Rolldown 注释及 chunk size 警告 |
| `git diff --check` | 通过 |

### 运行态复验边界

修复后的 class 已在 `2026-08-11 07:34` 编译完成，但 Chat 服务仍是 IntelliJ 于 `06:59` 启动的外部 JVM。浏览器刷新后的页面仍显示“暂无长期记忆”，这是旧 JVM 尚未加载新 class 的结果，不足以否定上述代码修复。

完成运行态验收前，请在 IntelliJ 中重启 Chat 的 Run Configuration，然后刷新 `AI平台管理 → 记忆管理`。预期页面显示 RAGFlow 已提炼的长期记忆，接口保持 `providerStatus=AVAILABLE`，且不再是 `items=0`。重启后的这一步仍需补录实际页面/API 证据，才能将本报告的“部分通过”改为完整闭环通过。
