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

## 1. 当前结构

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

## 2. 模块边界

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

## 3. 工作规则

1. 优先在目标子模块内修改，避免无关文件连带变更。
2. 保持改动小而聚焦，优先修正当前需求对应的路径。
3. `api` 模块只放契约，不放业务实现。
4. `core` 模块承载业务实现和控制器落地。
5. `boot` 模块只做启动、配置和装配，不下沉业务逻辑。
6. `service-ai-provider` 只做外部模型适配，不承担领域路由规则。
7. 如果模块边界不清楚，先看对应 `pom.xml` 和 `README`，不要凭印象改。

## 4. 构建与验证

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

## 5. 变更原则

- 不要把别的仓库的目录名、模块名、服务边界直接复制过来。
- 不要假设这里还有 `okx-tradingView/`、`platform/` 之类目录。
`athena-framework/` 是外部框架目录；当开发人员或 AI 在代码里遇到 `athena` 相关包时，可以按需读取该目录下的内容，但不要默认把它当成当前仓库必须先读完的内容。
- 如果要补充规则，尽量写成和当前仓库真实目录对应的说明。
