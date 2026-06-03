# ai-assit-platform

`ai-assit-platform` 是一个基于 JDK 17 + Maven 多模块的 AI 平台项目，当前包含：
- AI 引擎域（对话/向量/知识库接口与 Provider 扩展）
- 用户服务启动工程（基础安全能力已接入 Athena）
- 网关占位模块
- 前端 `ai-assit-ui`（Vue 3）

## 1. 目录结构

```text
ai-assit-platform/
├── pom.xml                                # 根聚合 POM（packaging=pom）
├── lombok.config
├── app/
│   ├── app-gateway/                       # 网关聚合模块（packaging=pom）
│   │   ├── pom.xml
│   │   ├── core/                          # 网关核心实现层
│   │   │   ├── pom.xml
│   │   │   └── src/main/java/...
│   │   └── boot/                          # 网关启动模块（Spring Boot）
│   │       ├── pom.xml
│   │       └── src/main/java/...
│   ├── app-platform-ai-engine/            # AI 引擎聚合模块（packaging=pom）
│   │   ├── pom.xml
│   │   ├── service-ai-api/                # AI 领域 API 契约层（DTO + API 接口）
│   │   │   ├── pom.xml
│   │   │   └── src/main/java/...
│   │   ├── service-ai-core/               # AI 领域编排层（校验/路由/控制器/领域服务）
│   │   │   ├── pom.xml
│   │   │   └── src/main/java/...
│   │   ├── service-ai-provider/           # AI 提供方实现层（Qwen 等）
│   │   │   ├── pom.xml
│   │   │   └── src/main/java/...
│   │   └── boot-ai-engine/                # AI 引擎启动模块（Spring Boot）
│   │       ├── pom.xml
│   │       └── src/main/resources/application.yml
│   └── app-platform-user/                 # 用户服务聚合模块（packaging=pom）
│       ├── pom.xml
│       ├── api/                           # 用户域内部调用 API 契约（OpenFeign）
│       │   ├── pom.xml
│       │   └── src/main/java/...
│       ├── core/                          # 用户域核心实现（API Controller 实现）
│       │   ├── pom.xml
│       │   └── src/main/java/...
│       └── boot/                          # 用户服务启动模块（Spring Boot）
│           ├── pom.xml
│           └── src/main/resources/application.yml
├── ai-assit-ui/                           # 前端工程（Vue 3 + Vite）
├── http/
│   └── ai-engine/                         # AI 接口调试脚本/HTTP 示例
├── tools/
│   └── sse_chat_assemble.js               # SSE 数据处理辅助脚本
└── script/                                # 预留脚本目录
```

## 2. 模块与服务划分

### 2.1 根聚合层
- `pom.xml` 管理版本与模块清单。
- 当前聚合模块：`app-gateway`、`app-platform-ai-engine`、`app-platform-user`。

### 2.1.1 `api` 模块统一约定
- 仓库内所有 `api` 模块都只用于后端内部服务之间的契约定义。
- `api` 模块可以被 `core`、`boot`、其他后端服务依赖，但不面向前端直接调用。
- 前端只应调用网关暴露的 HTTP 接口，不应直接依赖或访问任一 `api` 模块中的接口定义。
- `api` 模块通常只放 DTO、请求/响应模型、Feign 接口或跨服务契约，不承载具体业务实现。

### 2.1.2 应用最小模块约定
- 一个应用至少包含 `core` 和 `boot` 两个模块。
- `core` 承载业务实现、控制器、领域服务和核心装配逻辑。
- `boot` 只负责启动类、配置导入、自动装配和模块组装，不下沉业务实现。
- 如果应用还需要对外提供内部契约，可以额外增加 `api` 模块，但 `api` 仍然只面向后端内部使用。

### 2.2 AI 引擎服务（`app/app-platform-ai-engine`）
该目录是一个“分层清晰”的 AI 服务域，内部拆成 API / Core / Provider / Boot 四层。

#### A. `service-ai-api`（契约层）
职责：
- 定义外部可见的 AI 能力接口：
  - `AiChatExecutionApi`
  - `AiVectorExecutionApi`
  - `AiKnowledgeBaseExecutionApi`
- 定义通用数据模型：`ChatRequest/Response`、`EmbedRequest/Response`、`Kb*` DTO、`ProviderType` 等。

边界：
- 只放协议与模型定义，不承载业务编排和第三方 SDK 调用。
- 可以被 `core`、`provider`、`boot` 依赖。

#### B. `service-ai-core`（领域编排层）
职责：
- 控制器接入层：
  - 路由前缀 `/api/v1/ai/execution`
  - 能力入口：`/chat`、`/chat/stream`、`/vector/embed`、`/vector/rerank`、`/kb/upsert`、`/kb/delete`、`/kb/search`
- 领域服务 `DefaultAiExecutionDomainService`：
  - 请求校验（`AiRequestValidator`）
  - Provider 选择与路由（基于 `ProviderType` + `AiCoreProperties`）
  - 请求映射（`AiProviderRequestMapper`）
  - 异步流式调度（`chatStreamAsync`）
- 定义 Provider SPI：`AiProvider`（规范 provider 接口）

边界：
- 负责“业务编排”和“统一领域规则”，不直接绑定某一厂商 SDK。
- 通过 `AiProvider` SPI 间接调用具体实现。

#### C. `service-ai-provider`（提供方实现层）
职责：
- 实现具体模型厂商接入，当前已实现 `QwenProvider`。
- 基于 Spring AI OpenAI 兼容接口调用 DashScope。
- 通过 `QwenProperties` 管理提供方参数。

边界：
- 只做“厂商适配与协议转换”，不承担 Controller、路由、业务规则。
- 通过实现 `AiProvider` 接入 core 层。
- `@ConditionalOnProperty(prefix = "ai.provider.qwen", name = "enabled", havingValue = "true")` 控制启用。

说明：
- 当前 `QwenProvider` 已实现 `chat/chatStream/embed`。
- `rerank`、`kbUpsert`、`kbDelete`、`kbSearch` 在 provider 中仍是 `unsupported`（待完善）。

#### D. `boot-ai-engine`（启动层）
职责：
- 作为 AI 引擎可运行服务入口（`BootAiEngineApplication`）。
- 聚合并装配 `service-ai-core` + `service-ai-provider`。
- 提供环境配置（端口、provider 参数等）。

边界：
- 不放领域实现，只负责启动与装配。

### 2.3 用户服务（`app/app-platform-user`）
职责：
- 用户域拆分为 `api/core/boot` 三层：
  - `api`：提供内部后端服务调用契约（用户查询、权限查询），并引入 OpenFeign；不对前端开放。
  - `core`：实现 `api` 中定义的接口（Controller 落地）。
  - `boot`：`PlatformUserApplication` 启动装配。
- 已引入 Athena 安全相关 Starter（含 `athena-framework-starter-security-user-mybatis`）。

边界：
- 专注账号、认证、用户相关能力。
- 不应直接承载 AI 领域逻辑。

### 2.4 网关模块（`app/app-gateway`）
职责：
- 当前拆分为 `core/boot` 两层：
  - `core`：承载网关核心实现与后续接入层逻辑。
  - `boot`：`PlatformGatewayApplication` 启动装配。

边界：
- 未来承载统一入口、路由编排、鉴权前置等能力。
- 不直接实现具体业务域（AI/User）的领域逻辑。

### 2.5 前端与工具目录
- `ai-assit-ui`：前端界面工程。
- `http/ai-engine`：本地联调样例（`*.http`、`*.sh`）。
- `tools/sse_chat_assemble.js`：SSE 流式返回处理辅助脚本。

## 3. 当前服务调用链（AI 方向）

```text
HTTP Request
  -> service-ai-core Controller
  -> DefaultAiExecutionDomainService
  -> AiProvider (SPI)
  -> QwenProvider (具体实现)
  -> DashScope/OpenAI-Compatible API
```

流式对话：
- `/chat/stream` 返回 `text/event-stream`，由 `SseEmitter` 持续输出 `chunk` 事件。

## 4. 功能边界与约束（建议长期保持）

- `service-ai-api`：只定义契约，不写业务流程。
- `service-ai-core`：只做领域编排和统一规则，不直接耦合具体厂商 SDK。
- `service-ai-provider`：只做外部模型接入适配，不定义业务路由规则。
- `boot-*` 模块：只做启动装配，不下沉业务逻辑。
- `app-platform-user-api`：只定义内部后端调用契约，不写业务实现，也不面向前端开放。
- `app-platform-user-core`：只承载用户域实现，不承担启动职责。
- `app-platform-user` 与 `app-platform-ai-engine`：按业务域隔离，避免跨域相互侵入。
- `app-gateway`：承担接入层能力，不替代各域服务内部逻辑。

## 5. 配置与安全注意事项

- `boot-ai-engine/src/main/resources/application.yml` 当前包含 `ai.provider.qwen.apiKey` 明文示例。
- 建议改为环境变量或外部配置中心注入，避免将真实密钥提交到仓库。

## 6. 构建示例

在项目根目录执行：

```bash
mvn clean compile -DskipTests
```

仅构建 AI 引擎聚合模块：

```bash
mvn -pl app/app-platform-ai-engine -am clean compile -DskipTests
```
