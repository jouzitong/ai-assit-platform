# 知识库客户端配置

## 1. 系统参数

知识库客户端统一配置在系统参数 `chat.engine.kb.client.list`。当前项目只支持一个 RAGFlow
知识库平台，因此该数组必须且只能包含一项；`key` 是稳定标识，`type` 对应
`AiKnowledgeClientType`，`url` 是 Provider 根地址。

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

## 2. 认证模型

- `bearer`：默认使用 `Authorization: Bearer {value}`，用于 RAGFlow。
- `aliyun_aksk`：使用 `accessKeyId` 与 `accessKeySecret` 完成阿里云 OpenAPI 签名，用于百炼知识库。

认证值只由 Chat 后端从系统参数读取；创建或更新 KB 时，后端会将当前认证复制到
`ai_kb_store.auth_json` 作为凭据快照。管理页只显示脱敏摘要，绝不返回 Token、API Key
或完整认证对象。不同 Provider 的请求头和签名逻辑继续由各自 Provider 适配层负责。

## 3. KB 选择与运行时

管理页新增知识库时，后端基于系统参数直接调用 RAGFlow 创建 Dataset，并保存返回的 Dataset ID。
每个本地知识库保存自己的 `chunk_method`、`parser_config`、embedding 模型、权限和可选 Pipeline；
文档同步、检索、删除均在运行时从系统参数注入唯一客户端的地址；认证则优先使用 KB 保存的
凭据快照，以便凭据轮换前保持既有 KB 的调用稳定。
