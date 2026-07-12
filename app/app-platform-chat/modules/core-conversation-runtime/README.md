# Conversation Runtime

`core-conversation-runtime` 管理一次 AI 对话运行的生命周期，不承载具体 SSE 或 WebSocket 实现。

## 职责

- 为每次运行生成独立 `runId`。
- 管理 `ACCEPTED`、`RUNNING`、`CANCELLED`、`COMPLETED`、`FAILED` 等状态。
- 提供状态查询和幂等取消。
- 为运行事件补充协议版本、事件序号和时间戳。
- 缓存近期事件，支持按 `lastEventId` 重放。
- 提供与传输方式无关的订阅接口。

## 边界

- SSE 适配位于 `web/.../transport/sse`。
- 后续 WebSocket 适配应复用 `ConversationRunManager`，不要把连接对象放进运行时。
- 工作流通过 `ConversationEventPublisher` 发布事件，通过 `ConversationCancellation` 感知取消。
- `local` 模式使用进程内注册表和事件缓存，适合单实例部署。
- `redis` 模式使用 Redis 共享任务快照、索引、事件和取消信号，适合多实例部署。

## 部署模式

默认不需要 Redis：

```yaml
ai:
  chat:
    runtime:
      mode: local
```

单节点 Redis：

```yaml
ai:
  chat:
    runtime:
      mode: redis
      node-id: ${HOSTNAME}
      redis:
        key-prefix: ai:chat:runtime

spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
```

Redis Cluster：

```yaml
ai:
  chat:
    runtime:
      mode: redis
      node-id: ${HOSTNAME}

spring:
  data:
    redis:
      cluster:
        nodes:
          - redis-0:6379
          - redis-1:6379
          - redis-2:6379
```

Redis 单节点、哨兵和 Cluster 的连接拓扑由 Spring Data Redis 配置决定，运行时实现不区分拓扑。

Redis 模式保存：

- `run:{runId}`：任务快照及所属应用节点。
- `events:{runId}`：有限长度的事件重放列表。
- `index:*`：用户、会话、轮次到运行任务的索引。
- `cancel:{runId}`：具备 TTL 的可靠取消标记。
- Pub/Sub channel：实时事件通知和快速取消通知。

即使取消通知短暂丢失，工作流在执行边界检查 `cancel:{runId}` 时仍会停止。Redis 不可用时，本节点正在运行的任务仍保持本地执行，但跨节点查询、订阅和取消能力会暂时不可用。
