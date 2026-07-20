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
  layout/
    list/
      SingleListLayout.vue
    index.ts
  runtime/
    RenderJsonRuntimeHost.vue
    index.ts
  schema/
    index.ts
    *.ts
  registry/
    index.ts
    *.ts
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
- `layout/`：放系统支持的布局容器，负责定义子节点编排方式和视觉外壳，例如单列表、双栏、分组页签等业务布局风格。
- `runtime/`：放 Render JSON 运行时入口和节点调度能力，负责接收文档、构建上下文、递归分发节点，不承担具体布局样式。
- `renderers/*/types.ts`：放当前 renderer 的配置类型、事件类型、运行时状态类型。
- `renderers/*/schema.ts`：放 schema 归一化、默认值补齐、字段取值、派生状态等纯函数；不要在这里注册组件或发起请求。
- `schema/`：放跨 renderer 的 Application JSON 契约，例如画布节点、renderer key、layout、props、bindings、events。
- `registry/`：放运行时注册表，把稳定 key 解析为 Vue 组件、元信息、默认 props 和可选 normalize 函数。
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

Resolver 是 Render JSON 结构与 Runtime 执行链路之间的结构解析层。它负责统一处理 datasource、bindings、query 参数和组件数据形态，避免每个 renderer 重复实现结构解析。

Resolver 应该定义：

- 根据 `schema.datasource` 读取查询意图，结合 Runtime SCOPE 中的 query/params 生成 request plan。
- 数据库查询类 datasource 默认对齐后端 `DbQueryApi` 契约，使用 `model`、`filter_dict`、`filterExpr`、`page`、`page_size`、`ext.fields`、`ext.relations`、`ext.sorts` 等字段。
- 静态或外部已准备好的 JSON 数据使用 `direct-json` datasource，resolver 生成 `direct-json` request plan 或结构归一化结果，不发起远程请求。
- 根据 `schema.bindings` 把原始数据映射为 renderer 的 `data`。
- 为 renderer 统一产出 `{ schema, data, state }` 结构。
- 统一处理 `loading`、`error`、`empty` 等运行时状态。
- 支持 `direct-json`、`binding`、`db-query-list`、`computed` 等解析方式时，新增解析方式只改 resolver，不改具体 renderer。
- 列表 filter 的输入值先进入运行时 `query.filters`，再由 resolver 映射到 datasource 请求；filter 可以用 `query.field` 改写后端字段名，用 `query.op` 声明 `like`、`in` 等操作符。
- 当 datasource 已声明 `filterExpr` 时，resolver 必须把运行时 filter key 合并进表达式，避免后端只解析固定条件而忽略用户输入。

Resolver 不应该定义：

- DOM 结构和 Vue 模板逻辑。
- 具体页面的私有业务流程。
- renderer 内部展示细节。
- 可执行字符串形式的业务函数。
- 直接调用后端接口；远程请求统一交给 Data Requester。

Renderer 不直接依赖 resolver。动态渲染入口或页面容器先调用 resolver 得到 props，再通过 registry 找到组件并渲染。

## 5.1 Data Requester 定义

Data Requester 是 Runtime 下的数据请求执行层。它只消费 resolver 产出的 request plan，并把原始响应交回 parser/binding。

Data Requester 应该定义：

- 根据 request plan 的 `type` 分发请求，例如 `db-query-list`、`direct-json`、`tree-query`、`remote-options`。
- 统一处理真实请求地址、HTTP method、请求体、鉴权请求头、trace id 和错误抛出。
- 保持 request plan 可观测，便于 SCOPE 展示、调试和事件后重新请求。

Data Requester 不应该定义：

- renderer 的 DOM 和展示逻辑。
- filter UI 触发策略。
- 按钮 action 的业务含义。
- Render JSON 协议升级和节点解析。

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
- Renderer 通过 `emit` 抛出 `action`、`itemAction`、`reload`、`queryChange`、`valueChange`、`submit`、`reset` 等语义事件，由 Runtime 的 Event Dispatcher 统一处理。
- 语义一致的操作必须复用同一个事件名，例如列表筛选、树节点选择、分页变化都可以归一为 `queryChange` 或 `reload`；只有组件特有交互才新增私有事件名。
- Renderer 内部子组件只服务当前 renderer，不直接被页面跨层引用；需要跨页面复用时再上升为公共组件。
- Renderer 内部的默认值、字段取值、展示格式化等纯逻辑可以放在本 renderer 的 `schema.ts`。

## 8. Layout 与 Runtime 定义

- `layout/` 代表系统支持的布局方式，是 Render JSON 中的容器型节点实现层。
- Layout 负责“怎么摆”，例如单列表壳、双栏布局、分组区块、页签容器、仪表盘网格；它决定 children 的编排、分区和外层风格。
- Runtime 负责“怎么跑”，它是 Render JSON 的程序入口，负责按 code 加载 Render JSON、校验与升级、构建 SCOPE、解析节点、分发节点到 `layout`/`renderers`，并通过 Event Dispatcher 处理 renderer 抛出的操作事件。
- Runtime SCOPE 是全局运行时上下文，至少包含 `code`、`document`、`schema`、`query`、`params`、`requestPlans`、`data`、`state`、`events`。resolver、Data Requester、Event Dispatcher、Hook Runner 都通过 SCOPE 协作，不让 renderer 直接持有业务状态。
- Event Dispatcher 属于 Runtime 层，负责把 renderer 的语义事件解释成上下文更新、resolver 重新加载、action 执行、hook 执行、局部 state 更新或路由跳转。
- Event Dispatcher 接收统一事件结构 `{ type, source, payload, meta }`；`source` 至少应能表达 renderer key、componentId 和原始事件名，便于日志、权限、hook 和调试。
- Runtime Hook 是 Event Dispatcher 流程里的扩展点，适合承载 `beforeEvent`、`afterEvent`、`onQueryChange`、`beforeLoad`、`afterLoad`、`beforeAction`、`afterAction` 等跨组件逻辑。
- Layout 不负责协议读取、版本迁移、请求调度、错误恢复和节点注册查找。
- Runtime 不负责定义具体视觉风格，不把业务布局样式硬编码在入口层；页面私有动作可以通过 action executor 回调接入 Runtime，但不要写进 renderer。
- 容器节点优先交给 `layout/`，内容节点优先交给 `renderers/`，不要把两类职责混在同一个组件里。
- 第一个 Layout 应优先从已有 renderer 中抽离“稳定的容器壳”，而不是新建一份平行的大组件。
- 需要按宿主容器等比例缩放时，由页面或画布宿主组合 `ResponsiveViewport`；Runtime 和 Renderer 不单独测量浏览器视口。
- `ResponsiveViewport` 的全局默认、预设继承和局部覆盖统一通过 `src/config/responsive.ts` 解析，Render JSON 只在确有持久化需求时保存稳定预设 key，不保存运行时测量结果。
- 同一渲染树只设置一个连续缩放宿主；Select、Popover、DatePicker 等浮层和拖拽坐标通过响应式上下文继承 overlay target 与逻辑缩放比例。
- Application 的主题 Token、缩放计算、配置扩展和验收要求统一遵循 [前端主题与容器响应式开发规范](./responsive-theme.md)。

### 8.1 动态应用路由与宿主模式

- 正式动态应用入口统一使用 `/app/{mode}/{renderJsonCode}.json`；`.json` 是 URL 表达，加载 Render Meta 前应从 code 中移除。
- `mode` 只决定页面宿主行为，不替代 Render JSON 内部 Layout。首批稳定值为 `standard`、`dashboard`、`report`、`embedded`。
- `standard` 使用自然文档流；`dashboard` 使用唯一 `ResponsiveViewport`；`report` 提供自然排版和打印规则；`embedded` 使用最小页面外壳并跟随父容器。
- Render JSON 可以在 `presentation` 中声明 `defaultMode`、`allowedModes`、`title`、`description`、`refreshInterval`、`responsivePreset`。显式路由 mode 优先，但必须通过 `allowedModes` 校验。
- 内部容器继续使用 Layout Registry 的稳定 key，例如 `zg-stack-layout`、`zg-grid-layout`、`zg-split-layout`、`zg-section-layout`、`zg-sheet-layout`，禁止把这些 key 扩展成路由 mode。
- 历史裸 renderer schema 可以在 Runtime Loader 中归一化为标准 `RenderDocument.root`，Renderer 和 Layout 不承担历史协议兼容。
- 开发者模式下，正式动态应用入口应提供 Render Meta 编辑和页面级 SCOPE 查看；节点观测仅在开发者模式开启，SCOPE 必须汇总真实的 `query`、`requestPlans`、`data`、`state` 和事件，不再复制测试页的手工请求链路。
- Renderer 已提供页面标题和头部动作区时，标准宿主不重复渲染标题；开发者入口优先作为 Runtime-only 动作进入 Renderer 头部，不写回 Render JSON，只有缺少可承载动作区时才使用浮动入口。
- `standard` 宿主和列表类 Renderer 应保持从宿主到 Runtime 节点的完整高度链。列表布局按 Header 与 ListTable 两个主区域组织：Header 承载标题、页面动作、页签和过滤条件；ListTable 位于 Main，承载汇总、列表内容及其分页 Footer；可选 Aside 与 ListTable 并列。Main 承担剩余空间，避免数据较少时页面上下形成无意义空白区。

## 9. 数据与动作

- 列表查询、表单提交、动作执行由页面 service 或运行时容器负责。
- 组件操作事件先进入 Event Dispatcher；Dispatcher 判断需要加载数据时才调用 resolver，resolver 不直接处理按钮点击、弹窗、跳转、权限判断等操作含义。
- `schema.datasource` 只声明模型、过滤、排序、关联等查询意图，不直接写接口地址。
- 列表型 datasource 优先映射到 `POST /dbEngine/api/v1/query.list`，后端响应以 `list` 和 `pageInfo.{ total, size, page }` 表达分页结果，再由 resolver 转成 renderer 的 `data`。
- `direct-json` 列表数据使用最简单的 `{ records, total, ... }` 结构；resolver 统一转成 renderer 的 `data`。
- 关联查询使用 `ext.relations[{ key, model, type, on, filter }]`，不要在前端 schema 中再定义 `foreign_key/local_key` 这类平行结构。
- filter 触发查询由 schema 声明：选择器、树选择、日期类 filter 默认 `change` 后触发 reload；输入类 filter 默认回车触发 reload。特殊场景可通过 `filter.query.submitOnChange`、`filter.query.submitOnEnter` 或 `filter.options.submitOnChange`、`filter.options.submitOnEnter` 覆盖。
- `schema.actions[].action` 表达业务动作 key，`schema.actions[].type` 只表达按钮视觉类型。
- 行内动作、顶部动作、表单动作统一通过事件抛出，由 Event Dispatcher 分发给通用 action executor 或页面私有 action executor，不在 renderer 内部直接执行。
- 枚举、选择器、远程数据源等能力在协议未稳定前，只保留声明字段和 TODO，不提前写死单一实现。

## 10. 命名要求

- renderer key 使用稳定短横线命名，并统一使用 `zg-` 前缀，例如 `zg-list-main-layout`、`zg-form-main-layout`、`zg-line-chart-renderer`。
- renderer 内部 schema 类型使用业务语义命名，例如 `ListRendererSchema`、`FormRendererSchema`。
- action key 使用业务语义，例如 `CREATE`、`SAVE`、`DELETE`；视觉样式使用 `type` 单独表达。
- 字段 key、filter key、group key 保持可追踪，不使用临时序号或展示文案作为唯一标识。

## 11. 新增 Renderer / Layout 流程

1. 在 `renderers/{type}/` 下定义 `types.ts`、`schema.ts` 和主渲染组件。
2. 主渲染组件只暴露稳定 props 和 events，不直接耦合页面 service。
3. 在 `schema/` 中补充 renderer 对外结构、统一 `data/state` 类型和必要的 wire contract。
4. 在 `registry/` 中注册 renderer key、Vue component、默认 props 和可选 normalize 函数。
5. 如需数据解析，在 `resolver/` 中补充 datasource 或 binding 到 renderer data 的转换。
6. 在 `component-manifest.ts` 补充组件资产元信息、参数清单和事件清单。
7. 增加测试页或示例配置，验证 schema、props、events 是否能独立工作。
8. 如果该 renderer 会被页面路由使用，再由具体页面模块按当前路由和页面实现约定接入。

新增 Layout 时：

1. 在 `layout/{type}/` 下定义布局容器组件和必要的入口导出。
2. Layout 只接收已经解析好的 children、slot 或局部状态，不直接处理 Render JSON 协议。
3. Layout 负责外层容器风格、区域编排和插槽顺序，不内嵌页面级请求逻辑。
4. 如果某个 renderer 的外壳样式已经稳定，优先抽成 Layout 复用，而不是复制整份 renderer。

## 12. Render JSON 协议与版本

- Application 层承载的 Render JSON 是低代码前端的声明式输入，只描述“渲染什么、依赖什么、触发什么”，不包含可执行实现。
- Render JSON 必须是可序列化 JSON 对象，不允许出现 Vue `Component`、`markRaw`、请求函数、回调函数或任意运行时实例。
- 根文档必须显式区分协议版本和组件版本，禁止继续用单个 `version` 同时表达两者。
- 节点必须至少具备稳定 `id` 和 `component`，并允许按需声明 `props`、`layout`、`datasource`、`bindings`、`events`、`actions`、`children`。
- `component`、`datasource.type`、事件 key、动作 key 都必须对应注册表中的稳定 key，不允许直接写页面私有实现名。
- 所有表达式必须是受限声明，不允许 `eval`、`new Function`、`javascript:` URL 或字符串形式函数。

建议的根协议：

```ts
interface RenderDocument {
  protocol: 'render-json'
  protocolVersion: string
  pageId: string
  revision?: string
  root: RenderNode
}

interface RenderNode {
  id: string
  component: string
  componentVersion?: string
  props?: Record<string, unknown>
  layout?: Record<string, unknown>
  datasource?: Record<string, unknown>
  bindings?: Record<string, unknown>
  events?: Array<Record<string, unknown>>
  actions?: Array<Record<string, unknown>>
  children?: RenderNode[]
}
```

- 文档内 `node.id` 必须唯一且稳定，禁止使用数组下标作为长期标识。
- 新增字段默认向后兼容；字段删除、重命名或语义变化必须升级 `protocolVersion`。
- 历史配置中的可执行字符串，例如 `'=function(){}'`，只能作为兼容迁移输入处理，不属于当前有效协议。
- 校验与升级必须固定执行 `结构校验 -> 协议版本判断 -> 逐级迁移 -> 当前版本重校验 -> 组件版本校验`。
- 迁移函数必须是纯函数、幂等、无副作用，不允许修改原始对象。

## 13. Render JSON 运行时解析流程

- 运行时必须固定执行 `读取 -> 校验与升级 -> 节点解析 -> 上下文构建 -> 数据解析 -> 组件渲染` 六阶段，不允许在 Renderer 内部绕过管线直接请求或执行业务动作。
- 每个阶段都必须有明确输入、输出和错误模型；前一阶段未通过时，后续阶段不得继续执行。
- 运行时容器应持有整页 Session，负责取消旧请求、丢弃过期结果、隔离节点级错误。

节点解析阶段负责把规范化后的文档转成与 Vue 无关的执行计划，例如：

```ts
interface RenderNodePlan {
  nodeId: string
  parentId?: string
  rendererKey: string
  layoutPlan?: Record<string, unknown>
  dataPlan?: Record<string, unknown>
  eventPlan: Array<Record<string, unknown>>
  children: RenderNodePlan[]
}
```

- 节点解析必须通过 `registry/` 查找 Renderer 定义、默认属性、别名映射和可选 `resolveData` 能力。
- 节点解析必须通过 Resolver 注册表查找数据解析器，不允许由 Renderer 自行决定数据源实现。
- 节点解析必须解析父子节点、插槽和布局关系，明确渲染顺序和上下文继承链。
- 节点解析必须检测重复 `node.id`、非法 children、循环依赖、未知组件、未知数据源和未知动作。
- 节点解析不得发起网络请求，不创建 VNode，不执行动作副作用。

## 14. 运行时上下文与数据执行

- 运行时上下文必须使用显式命名空间，不允许把所有变量平铺后按名称碰撞解析。
- 上下文至少应包含 `user`、`page.params`、`page.variables`、`globals`、`parent`、`component.state` 和 `signal`。
- `user`、`page.params`、`globals` 必须只读；可变状态只允许留在 `page.variables` 或当前 `component.state`。
- 父组件上下文只能暴露显式允许下传的数据，子节点不得持有父组件实例或直接修改父组件状态。
- `component.state` 必须按 `nodeId` 隔离，并在节点卸载时销毁。
- 上下文中禁止注入 token、cookie、完整 Store、HTTP client、Router 实例等高风险对象。
- 表达式优先使用完整路径，例如 `user.id`、`page.params.id`、`globals.locale`；不要依赖裸字段猜测。

数据执行阶段必须统一完成四件事：解析上下文表达式、生成请求参数、获取数据、转换为 Renderer 需要的结构。

- 新数据源必须注册到统一 Resolver，而不是在具体 Renderer 中写请求逻辑。
- Resolver 必须先校验 datasource 配置，再生成标准请求参数。
- 数据请求必须支持 `AbortSignal`、超时、在途去重、过期响应丢弃和必要的缓存失效。
- 同级无依赖的数据计划可以并行执行；存在依赖关系时必须按拓扑顺序执行。
- 响应转换必须是纯函数，统一产出 `data` 和 `state`，不得把后端私有响应结构泄漏给 Renderer。
- 属性合并顺序必须固定为 `registry.defaultProps <- JSON 静态 props <- normalizeProps 结果 <- resolver 输出的 data/state <- 运行时注入的受控事件处理器`。
- 数组默认整体替换，不自动拼接；`undefined` 不覆盖，`null` 表示显式覆盖。
- 必须过滤 `__proto__`、`prototype`、`constructor` 等污染键。

## 15. 异常、降级与测试

- Render JSON 必须视为不可信输入，协议解析层必须先做校验，再允许进入后续阶段。
- 统一错误模型至少应包含 `code`、`stage`、`message`、`jsonPath`、`nodeId`、`recoverable`、`traceId`。
- 推荐错误码包括 `JSON_PARSE_FAILED`、`SCHEMA_INVALID`、`PROTOCOL_VERSION_UNSUPPORTED`、`PROTOCOL_VERSION_TOO_OLD`、`MIGRATION_FAILED`、`DUPLICATE_NODE_ID`、`RENDERER_NOT_FOUND`、`DATA_RESOLVER_NOT_FOUND`、`ACTION_NOT_FOUND`、`EXPRESSION_INVALID`、`DATA_REQUEST_TIMEOUT`、`RENDER_FAILED`。
- 节点级渲染失败应优先局部降级；只有根节点关键错误才中止整页。
- 整页 Session 必须记录 `traceId`、文档版本、节点数、阶段耗时、缓存命中和失败节点。
- 节点级错误必须能定位到 `nodeId` 和 `jsonPath`，但生产日志中不得输出敏感上下文或完整请求体。
- 路由切换、页面卸载、文档 revision 变化时，必须取消旧 Session，旧异步结果不得写回新页面。
- 单元测试必须覆盖非法 JSON、协议升级、组件版本检查、节点重复、表达式作用域、属性合并、请求参数生成、取消和过期响应。
- 集成测试必须覆盖一份 Render JSON 跑通六阶段、多层父子节点、上下文继承、并行数据源、局部降级和动作触发后的数据刷新。
- 安全测试必须覆盖 XSS、危险 URL、原型污染、表达式注入、超深节点、超多节点和事件递归。

## 16. 维护要求

- 修改 schema 字段前先检查现有 renderer、测试页、组件资产生成逻辑和后端保存数据是否依赖旧字段。
- 删除 renderer 时同步清理 schema、registry、resolver、manifest、测试页、示例 JSON 和文档。
- 扩展 schema 时优先保持声明式，不要为了单个场景提前引入可执行配置。
- 当 registry key 与 schema 中的 `component`/`renderer` key 不一致时，必须增加明确映射，不允许在页面里散落临时判断。
- Application 层变更影响面通常较大，至少执行 `cd ai-conversation-ui && npm run build` 或等价的前端类型/构建校验。
