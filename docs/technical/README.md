# 技术文档

这里沉淀当前仓库已经落地的技术架构、实现原理、跨模块交互和配置参考。技术文档以真实代码为准，并明确区分“当前实现”和“规划能力”。

## 专题索引

### Conversation 与 Python Agent

- [Conversation 技术文档总览](./conversation/README.md)
- [用户聊天端到端执行链路](./conversation/user-chat-end-to-end.md)

### Render JSON

- [Render JSON 技术文档总览](./render-json/README.md)
- [架构与前后端交互](./render-json/architecture-and-interaction.md)
- [配置参考](./render-json/configuration-reference.md)
- [AI 生成、校验与聊天产物](./render-json/ai-generation-and-validation.md)

## 维护要求

每份长期技术文档至少应说明：

- 文档状态、适用版本、主要实现目录和最后核对日期。
- 入口、核心调用链、数据模型、异常边界和安全边界。
- 哪些内容是当前实现，哪些只是规范目标或后续计划。
- 关键源码索引，以及与开发规范、接口文档之间的链接。

实现发生变化时，优先更新对应专题，不在阶段性 `plans/` 文档中覆盖长期技术说明。
