# ai-assit-platform（Codex 协作规则 / 项目约定）

## Skill

### CodeGraph

This repository is indexed with CodeGraph.

Prefer CodeGraph queries before broad file scans:
- `codegraph query <symbol>`
- `codegraph callers <symbol>`
- `codegraph callees <symbol>`
- `codegraph impact <symbol>`
- `codegraph context "<task>"`

Maintenance:
- Run `codegraph init -i` once per repo.
- Run `codegraph sync` after edits.
- Run `codegraph index` for a full refresh when needed.

Do not commit `.codegraph/`.

本文件只约束 `ai-assit-platform` 仓库内的协作方式，目标是让 Codex 先对齐当前项目结构，再做最小必要改动。

## 1. Codex 启动上下文

### 默认会拿到什么

- 当前工作目录、时间、工具能力、网络与权限等运行环境信息。
- 本文件 `AGENT.md` 的规则。
- 当前对话线程的上下文（如果不是新线程）。
- 系统/平台层面的工具与编辑约束。

### 默认不会完整读取什么

- Codex **不会**在新任务启动时自动完整读取整个仓库源码。
- `src/`、`app/`、`ai-assit-ui/`、`docs/`、`http/` 下的业务文件，通常都需要按任务主动读取。
- 除非任务中明确要求，否则不要假设 `README.md`、`pom.xml`、模块 `pom.xml`、接口定义、DTO、controller、service 已经被自动读过。

### 新任务建议优先读取

- 根 `pom.xml`
- 目标子模块 `pom.xml`
- 目标路径下的 `entity`、`dto`、`controller`、`service`、`mapper`
- 如果是接口任务，先读对应 `api` 模块契约
- 如果是页面任务，先读 `ai-assit-ui/src/router.js` 和目标页面目录
- 如果任务涉及开发规范、目录约定、页面组织、样式、组件或模块分层，先读 `docs/dev-spec/README.md`，再按需进入对应子文档
- 如果是框架适配问题，再按需读取 `athena-framework/`，不要默认把整个框架仓库当成前置必读

### 行为要求

- 回答或改动前，先通过 CodeGraph 或精确读文件确认真实实现，不要凭记忆假设模块结构。
- 如果要新增规范、接口、表结构、DTO 字段，先确认当前仓库真实基类与现有模式是否已变化。
- 若任务依赖某个“必须先读”的文件，先显式读取，再开始设计或编码。

## 2. 开发规范文档

- `docs/dev-spec/` 是本仓库开发规范入口，采用“总览 + 子文档”结构。
- 根入口是 `docs/dev-spec/README.md`，这里只做索引；细则要进入 `detail/` 下对应文档读取。
- 前端任务默认按需读取 `docs/dev-spec/detail/frontend/README.md` 及其子文档：
  - 路由相关读 `router.md`
  - 页面结构相关读 `page.md`
  - 样式相关读 `style.md`
  - 主题相关读 `theme.md`
  - 组件设计相关读 `component.md`
- 后端模块边界、服务分层类任务默认按需读取 `docs/dev-spec/detail/backend/README.md` 和 `service-module.md`
- 如果规范文档与历史实现不一致，先以当前任务范围内的真实代码为准进行核对，再决定是修代码还是补规范，不要跳过确认。

## 3. 当前结构

- `pom.xml`：根聚合 POM，统一管理 `app/*` 子模块。
- `app/`：后端服务模块集合。
  - `app/app-gateway/`
  - `app/app-platform-ai-engine/`
  - `app/app-platform-ai-chat/`
  - `app/app-platform-db-engine/`
  - `app/app-platform-user/`
- `ai-assit-ui/`：前端独立工程，基于 Vue 3 + Vite。
- `http/`：接口联调脚本与 `*.http` 示例。
- `tools/`：辅助脚本，当前主要是流式数据处理相关脚本。

## 4. 模块边界

### 根聚合层

- 根 `pom.xml` 负责版本与模块聚合，不承载业务实现。
- 修改前先定位到具体子模块，不要跨模块顺手重构。

### `app/app-platform-ai-engine`

- `service-ai-api`：AI 领域契约层，放 API 接口、DTO、枚举等。
- `service-ai-core`：领域编排层，放控制器、请求校验、路由分发、领域服务。
- `service-ai-provider`：提供方适配层，放具体厂商接入实现。
- `boot-ai-engine`：启动层，只做装配、配置和启动。

### `app/app-platform-ai-chat`

- `api`：聊天元数据相关契约与 DTO。
- `core`：聊天域核心实现与接口落地。
- `meta`：模型、提供方、凭证等元数据。
- `chat-history`：会话、消息、轮次等历史数据。
- `boot`：启动层，只负责启动和装配。

### `app/app-platform-db-engine`

- `api`：内部契约。
- `core`：核心实现。
- `meta`：数据库引擎相关元数据。
- `boot`：启动层。

### `app/app-platform-user`

- `api`：用户、权限相关内部契约。
- `core`：用户域实现。
- `boot`：启动层。

### `app/app-gateway`

- `core`：网关过滤器、上下文、鉴权等实现。
- `boot`：网关启动层。

### `ai-assit-ui`

- 这是独立前端工程，不要把后端模块的边界直接搬到这里。
- 修改前端时优先保持路由、布局、组件职责清晰，避免把业务逻辑堆进 `App.vue`。

## 5. 工作规则

1. 优先在目标子模块内修改，避免无关文件连带变更。
2. 保持改动小而聚焦，优先修正当前需求对应的路径。
3. `api` 模块只放契约，不放业务实现。
4. `core` 模块承载业务实现和控制器落地。
5. `boot` 模块只做启动、配置和装配，不下沉业务逻辑。
6. `service-ai-provider` 只做外部模型适配，不承担领域路由规则。
7. 如果模块边界不清楚，先看对应 `pom.xml` 和 `README`，不要凭印象改。
8. 如果任务本身涉及“规范”或实现方式选择，先读 `docs/dev-spec` 对应文档，再落代码。

## 6. 构建与验证

- 根目录全量编译：
  - `mvn clean compile -DskipTests`
- 单独验证 AI 引擎模块：
  - `mvn -pl app/app-platform-ai-engine -am clean compile -DskipTests`
- 单独验证 AI Chat 模块：
  - `mvn -pl app/app-platform-ai-chat -am clean compile -DskipTests`
- 单独验证用户模块：
  - `mvn -pl app/app-platform-user -am clean compile -DskipTests`
- 单独验证网关模块：
  - `mvn -pl app/app-gateway -am clean compile -DskipTests`
- 前端构建：
  - `cd ai-assit-ui && npm run build`

## 7. 变更原则

- 不要把别的仓库的目录名、模块名、服务边界直接复制过来。
- 不要假设这里还有 `okx-tradingView/`、`platform/` 之类目录。
- `athena-framework/` 是外部框架目录；当开发人员或 AI 在代码里遇到 `athena` 相关包时，可以按需读取该目录下的内容，但不要默认把它当成当前仓库必须先读完的内容。
- 如果要补充规则，尽量写成和当前仓库真实目录对应的说明。
