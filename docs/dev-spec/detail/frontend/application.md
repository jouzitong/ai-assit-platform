# Application 开发规范

## 1. 目标

- `src/application/` 承载可被页面、画布、组件资产和 Render JSON 复用的应用级渲染能力。
- Application 层只定义通用渲染器、可序列化配置契约和运行时注册表，不承载具体菜单页面的业务流程。
- 页面级请求、状态编排、权限判断和提交逻辑仍归属具体页面模块的 `service/`、`views/`、`data/`，不要下沉到 Application 渲染器中。

## 2. 目录边界

Application 目录建议按以下职责组织：

```text
src/application/
  component-manifest.ts
  schema.ts
  registry.ts
  resolver/
  renderers/
    list/
      types.ts
      schema.ts
      ListMainLayout.vue
      components/
    form/
      types.ts
      schema.ts
      FormMainLayout.vue
      components/
    echarts/
      types.ts
      index.ts
      *.vue
```

- `renderers/`：放可独立渲染的通用组件和其内部子组件。
- `renderers/*/types.ts`：放当前 renderer 的配置类型、事件类型、运行时状态类型。
- `renderers/*/schema.ts`：放 schema 归一化、默认值补齐、字段取值、派生状态等纯函数；不要在这里注册组件或发起请求。
- `schema.ts`：放跨 renderer 的 Application JSON 契约，例如画布节点、renderer key、layout、props、bindings、events。
- `registry.ts`：放运行时注册表，把稳定 key 解析为 Vue 组件、元信息、默认 props 和可选 normalize 函数。
- `resolver/`：放数据解析和 props 组装逻辑，把 datasource、bindings、原始数据解析成 renderer 可消费的 `data/state`。
- `component-manifest.ts`：放组件资产中心使用的说明性元数据，包括名称、分类、版本、参数、事件、示例值；不要依赖它完成动态渲染。

## 3. Schema 定义

Schema 是可保存、可传输、可由后台或低代码配置生成的声明式结构。它应该描述“要渲染什么”和“需要哪些输入”，不直接描述“如何执行”。

Schema 可以定义：

- `id`、`version`、`title` 等身份信息。
- `renderer` 或 `component` 等稳定 key。
- `props`，用于传给 renderer 的配置与受控数据入口。
- `layout`，用于画布、看板等容器布局。
- `bindings` 或 `datasource`，用于声明数据来源、模型、过滤条件、字段绑定。
- `events`，用于声明事件 key、动作 key 或流程节点 key。

Schema 不应该定义：

- Vue component 引用、`markRaw`、异步 import 等运行时对象。
- 请求函数、提交函数、权限函数、格式化函数等可执行实现。
- 字符串形式的函数，例如 `=function(){}`。
- 具体页面私有状态，例如弹窗打开状态、临时选择项、局部 loading 编排。

如果需要动态行为，Schema 只写稳定 key，例如 `beforeLoad: "normalizeRiskQuery"`；具体实现放到 registry 或页面 service 中解析。

## 4. Registry 定义

Registry 是运行时解析层，负责把 Schema 中的稳定 key 转成真实前端实现。

Registry 应该定义：

- renderer key 到 Vue component 的映射。
- renderer 的名称、分类、版本、源码路径等元信息。
- renderer 支持的参数、事件和默认 props。
- 可选的 `normalizeProps`、`validateProps`、`resolveEventHandler` 等运行时扩展点。
- `list`、`find`、`resolve` 这类稳定查询方法。

Registry 不应该定义：

- 页面级业务数据。
- 菜单、路由、权限树等页面入口信息。
- 后端接口地址和请求流程。
- 某个业务页面的专属动作实现。

新增 renderer 时必须同步补充 registry 记录。画布、组件资产中心和动态渲染入口都应该通过 registry 查找组件，避免各处用 `v-if` 硬编码 renderer key。

## 5. Resolver 定义

Resolver 是 Schema 与 Renderer 之间的数据解析层。它负责统一处理 datasource、bindings、原始响应和组件数据形态，避免每个 renderer 重复实现数据解析。

Resolver 应该定义：

- 根据 `schema.datasource` 读取查询意图，调用页面 service、查询引擎或运行时上下文中的数据源。
- 数据库查询类 datasource 默认对齐后端 `DbQueryApi` 契约，使用 `model`、`filter_dict`、`filterExpr`、`page`、`page_size`、`ext.fields`、`ext.relations`、`ext.sorts` 等字段。
- 静态或外部已准备好的 JSON 数据使用 `direct-json` datasource，resolver 只做结构归一化，不发起请求。
- 根据 `schema.bindings` 把原始数据映射为 renderer 的 `data`。
- 为 renderer 统一产出 `{ schema, data, state }` 结构。
- 统一处理 `loading`、`error`、`empty` 等运行时状态。
- 支持 `direct-json`、`binding`、`db-query-list`、`computed` 等解析方式时，新增解析方式只改 resolver，不改具体 renderer。

Resolver 不应该定义：

- DOM 结构和 Vue 模板逻辑。
- 具体页面的私有业务流程。
- renderer 内部展示细节。
- 可执行字符串形式的业务函数。

Renderer 不直接依赖 resolver。动态渲染入口或页面容器先调用 resolver 得到 props，再通过 registry 找到组件并渲染。

## 6. Manifest 定义

`component-manifest.ts` 面向组件资产和文档生成，重点是“这个 Application 组件如何被识别和说明”。

Manifest 应该定义：

- `key`、`name`、`category`、`version`、`sourcePath`。
- `description`、`useCases`、`tags`。
- 对外参数清单，包括参数 key、label、类型、控件类型、是否必填、默认值、说明。
- 对外事件清单，包括事件名称和说明。

Manifest 不应该定义真实 Vue component，也不应该替代 registry。需要运行时渲染时，由 registry 组合 manifest 元信息和真实组件。

## 7. Renderer 设计

- Renderer props 必须明确稳定，优先使用 `schema + data props + loading/readonly/total` 这类受控输入。
- 新 renderer 优先接收统一入口 `{ schema, data, state }`；已有 renderer 可以保留旧 props，但应由 registry 或宿主层适配到统一入口。
- Renderer 只负责展示、局部交互和事件抛出，不直接请求后端。
- Renderer 通过 `emit` 抛出 `action`、`change`、`reload`、`queryChange` 等语义事件，由上层页面或运行时容器处理。
- Renderer 内部子组件只服务当前 renderer，不直接被页面跨层引用；需要跨页面复用时再上升为公共组件。
- Renderer 内部的默认值、字段取值、展示格式化等纯逻辑可以放在本 renderer 的 `schema.ts`。

## 8. 数据与动作

- 列表查询、表单提交、动作执行由页面 service 或运行时容器负责。
- `schema.datasource` 只声明模型、过滤、排序、关联等查询意图，不直接写接口地址。
- 列表型 datasource 优先映射到 `POST /dbEngine/api/v1/query.list`，返回的 `records`、`total`、`summary` 由 resolver 转成 renderer 的 `data`。
- `direct-json` 列表数据使用最简单的 `{ records, total, ... }` 结构；resolver 统一转成 renderer 的 `data`。
- 关联查询使用 `ext.relations[{ key, model, type, on, filter }]`，不要在前端 schema 中再定义 `foreign_key/local_key` 这类平行结构。
- `schema.actions[].action` 表达业务动作 key，`schema.actions[].type` 只表达按钮视觉类型。
- 行内动作、顶部动作、表单动作统一通过事件抛出，不在 renderer 内部直接执行。
- 枚举、选择器、远程数据源等能力在协议未稳定前，只保留声明字段和 TODO，不提前写死单一实现。

## 9. 命名要求

- renderer key 使用稳定短横线命名，并统一使用 `zg-` 前缀，例如 `zg-list-main-layout`、`zg-form-main-layout`、`zg-line-chart-renderer`。
- renderer 内部 schema 类型使用业务语义命名，例如 `ListRendererSchema`、`FormRendererSchema`。
- action key 使用业务语义，例如 `CREATE`、`SAVE`、`DELETE`；视觉样式使用 `type` 单独表达。
- 字段 key、filter key、group key 保持可追踪，不使用临时序号或展示文案作为唯一标识。

## 10. 新增 Renderer 流程

1. 在 `renderers/{type}/` 下定义 `types.ts`、`schema.ts` 和主渲染组件。
2. 主渲染组件只暴露稳定 props 和 events，不直接耦合页面 service。
3. 在 `schema/` 中补充 renderer 对外结构、统一 `data/state` 类型和必要的 wire contract。
4. 在 `registry.ts` 注册 renderer key、Vue component、默认 props 和可选 normalize 函数。
5. 如需数据解析，在 `resolver/` 中补充 datasource 或 binding 到 renderer data 的转换。
6. 在 `component-manifest.ts` 补充组件资产元信息、参数清单和事件清单。
7. 增加测试页或示例配置，验证 schema、props、events 是否能独立工作。
8. 如果该 renderer 会被页面路由使用，再由具体页面模块按页面结构规范接入。

## 11. 维护要求

- 修改 schema 字段前先检查现有 renderer、测试页、组件资产生成逻辑和后端保存数据是否依赖旧字段。
- 删除 renderer 时同步清理 schema、registry、resolver、manifest、测试页、示例 JSON 和文档。
- 扩展 schema 时优先保持声明式，不要为了单个场景提前引入可执行配置。
- 当 registry key 与 schema 中的 `component`/`renderer` key 不一致时，必须增加明确映射，不允许在页面里散落临时判断。
- Application 层变更影响面通常较大，至少执行 `cd ai-conversation-ui && npm run build` 或等价的前端类型/构建校验。
