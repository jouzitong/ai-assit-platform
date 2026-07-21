# Conversation 与 Python Agent 技术文档

> 状态：当前实现说明
>
> 适用范围：`app/app-platform-chat`、`ai-conversation-ui`
>
> 最后核对：2026-07-21

本专题说明聊天请求如何从 Controller 进入会话运行时、如何通过 SSE 到达前端，以及 Python Agent Worker 如何组织 Agent、Skill、Tool 和 MCP 扩展边界。

## 文档导航

- [IConversationController 与 completionsStream](./completions-stream.md)
  - `IConversationController` 全部接口职责。
  - `/api/v1/chat/completions/stream` 的架构、实现、SSE 协议、重连与案例。
  - 内部事件到 `chat-event.v2`、当前前端处理逻辑的完整映射。
- [Python Agent 架构与扩展指南](./python-agent-extension.md)
  - Java Run Plane 与 Python Worker 的职责分工。
  - Agent、Skill、内置 Tool、平台 Tool Gateway 的扩展步骤。
  - MCP 当前限制与建议演进方案。

## 当前实现结论

1. `IConversationController.completionsStream` 不是在 HTTP 线程中直接调用模型，而是创建异步 Run，再把 Run 的事件订阅转换为 SSE。
2. `/api/v1/chat/completions/stream` 直接发送 `ConversationQueryStreamEvent`，属于兼容协议入口。
3. 当前 `ai-conversation-ui` 主聊天页面不直接调用上述兼容入口，而是调用 `/api/chat/**/rounds/stream`，消费 `chat-event.v2` Envelope。
4. 两套 SSE 入口共享同一个 `ConversationRunManager`、`ConversationExecutionService` 和 Python Agent 执行链路，差异主要在请求 DTO、事件投影和心跳机制。
5. 当前 Java 只控制登录身份、会话、模型连接、运行调度、持久化和审计；Agent Prompt、协作拓扑、Skill 和 Python Tool 由 Python 本地目录定义。
6. MCP binding 在 Python Snapshot 编译阶段会被拒绝；目前没有可直接挂载 MCP Server 的实现。

## 主要源码目录

- Controller 与传输：`app/app-platform-chat/web/src/main/java/ai/platform/aiassit/conversation/`
- 会话业务：`app/app-platform-chat/modules/core-ai-chat/`
- Run 管理：`app/app-platform-chat/modules/core-conversation-runtime/`
- 工作流事件模型：`app/app-platform-chat/modules/core-workflow/`
- Agent 运行桥接：`app/app-platform-chat/modules/core-agent-runtime/`
- Python Provider：`app/app-platform-chat/providers/ai-provider-ai-agent/`
- 前端消费端：`ai-conversation-ui/src/modules/ai-chat/`

## 相关文档

- [Render JSON 架构与前后端交互](../render-json/architecture-and-interaction.md)
- [AI 生成、校验与聊天产物](../render-json/ai-generation-and-validation.md)
- [后端服务模块结构规范](../../dev-spec/detail/backend/service-module.md)
- [异常处理规范](../../dev-spec/detail/backend/exception-module.md)
