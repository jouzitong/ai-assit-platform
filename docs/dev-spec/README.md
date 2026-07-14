# 开发规范

这里用于沉淀 `ai-assit-platform` 的开发规范，按专题拆到 `detail/` 目录。

## 索引

### 前端

- [路由规范](./detail/frontend/router.md)
  - 适用于页面入口、菜单挂载、父子路由关系、路由目录组织相关任务。
- [样式规范](./detail/frontend/style.md)
  - 适用于样式文件组织、局部样式边界、命名方式、页面样式落点相关任务。
- [主题规范](./detail/frontend/theme.md)
  - 适用于主题变量、配色体系、视觉风格统一和主题扩展相关任务。
- [组件规范](./detail/frontend/component.md)
  - 适用于通用组件封装、组件职责边界、组件复用方式相关任务。
- [Application 开发规范](./detail/frontend/application.md)
  - 适用于 `src/application` 下 schema、registry、renderer、component manifest 的边界和扩展方式。

### 后端

- [服务模块结构规范](./detail/backend/service-module.md)
  - 适用于新服务建模块、`boot/core/api` 分层、模块职责边界相关任务。
- [基础数据模块开发规范](./detail/backend/data-module.md)
  - 适用于基础表、entity/dto、mapper、service、controller、枚举处理等 CRUD 数据模块任务。
- [后端配置规范](./detail/backend/configuration-module.md)
  - 适用于启动配置、运行时系统参数、`SystemSettingInternalApi` 和配置 key 常量定义相关任务。
- [日志规范](./detail/backend/logging-module.md)
  - 适用于业务日志级别选择、可追溯字段、审计日志、异常日志边界相关任务。
- [异常处理规范](./detail/backend/exception-module.md)
  - 适用于 Web 异常返回、业务异常抛出、自定义异常类设计、HTTP 状态语义相关任务。
- [知识库客户端配置](./detail/backend/knowledge-client-config.md)
  - 适用于系统参数中的知识库 Provider 客户端、认证信息和 KB Dataset 选择联动。
- [AI 客户端与模型配置](./detail/backend/ai-client-model-config.md)
  - 适用于多 AI 客户端、标准模型发现、模型启停和运行时配置解析。

## 说明

- `docs/dev-spec/README.md` 是唯一规范入口，前后端目录下不再保留二级 `README.md`。
- 细则直接拆到 `detail/frontend/` 和 `detail/backend/` 下的具体文档中。
- 后续补充时继续保持“单一入口 + 具体子文档”的结构，避免索引分散、重复维护。
- 新任务如果涉及实现方式选择，先根据这里的简述定位到对应规范，再进入具体文档读取。
