# 知识库客户端配置

## 1. 系统参数

知识库客户端统一配置在系统参数 `chat.engine.kb.client.list`。每项的 `key` 是稳定标识，
`type` 对应 `AiKnowledgeClientType`，`url` 是 Provider 根地址。

```json
[
  {
    "key": "ragflow",
    "type": 2,
    "url": "http://ragflow.example.com",
    "auth": { "type": "bearer", "value": "ragflow-api-key" }
  }
]
```

历史格式中的顶层 `apikey` / `apiKey` 仍兼容，并按 RAGFlow 的 Bearer Token 处理。

百炼知识库客户端使用 AK/SK 时，客户端配置形如：

```json
{
  "key": "bailian-kb",
  "type": 1,
  "url": "https://bailian.cn-beijing.aliyuncs.com",
  "auth": {
    "type": "aliyun_aksk",
    "accessKeyId": "...",
    "accessKeySecret": "..."
  }
}
```

## 2. 认证模型

- `bearer`：默认使用 `Authorization: Bearer {value}`，用于 RAGFlow。
- `aliyun_aksk`：使用 `accessKeyId` 与 `accessKeySecret` 完成阿里云 OpenAPI 签名，用于百炼知识库。

认证值只由 Chat 后端从系统参数读取；管理页接口只返回 `key`、类型、访问地址、认证类型及脱敏凭据
（前 8 位与后 4 位），绝不返回 Token、API Key 或完整认证对象。新建或更新 KB 时，后端会把所选系统客户端的认证对象写入
`ai_kb_store.auth_json`；`workspaceId` 等 Provider 专属参数继续存放在 `ext_json`。不同 Provider 的请求头和签名逻辑继续由各自的
Provider 适配层负责。

## 3. KB 选择与运行时

管理页先选择系统客户端，再由后端携带该客户端的认证上下文调用 `KnowledgeDatasetService#listDatasets`，
让用户选择 Provider Dataset。保存的 KB 扩展字段保留 `knowledgeClientKey`，`url` 是便于展示和
历史兼容的快照。实际写入、检索和删除优先使用 KB 已持久化的 `auth_json`；客户端凭据轮换后，重新保存对应 KB 即可同步新认证对象。
