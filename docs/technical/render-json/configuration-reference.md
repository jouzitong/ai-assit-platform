# Render JSON 配置参考

> 状态：演进中  
> 当前协议：`render-json` `1.0.0`  
> 最后核对：2026-07-20

## 1. 标准文档结构

正式动态应用可以使用以下结构：

```json
{
  "protocol": "render-json",
  "protocolVersion": "1.0.0",
  "id": "account-list",
  "revision": "2026-07-20.1",
  "title": "账户列表",
  "presentation": {
    "defaultMode": "standard",
    "allowedModes": ["standard", "dashboard"],
    "title": "账户列表",
    "description": "展示当前虚拟模型中的账户数据",
    "refreshInterval": 30,
    "responsivePreset": "dashboard",
    "readonly": true
  },
  "root": {
    "key": "account-list-root",
    "component": "zg-list-main-layout",
    "componentVersion": "1.0.0",
    "props": {
      "schema": {
        "key": "account-list-schema",
        "version": "1.0.0",
        "title": "账户列表",
        "fields": [
          {
            "key": "user_id",
            "name": "user_id",
            "label": "用户 ID",
            "field": ["user_id"]
          },
          {
            "key": "account_name",
            "name": "account_name",
            "label": "账户名称",
            "field": ["account_name"]
          }
        ],
        "list_config": {
          "itemType": "table",
          "pagination": {
            "enabled": true,
            "pageSize": 20,
            "pageSizeOptions": [20, 50, 100]
          }
        }
      }
    },
    "datasource": {
      "key": "account-list-query",
      "type": "db-query-list",
      "model": "ods_trade_user_account",
      "page": 1,
      "page_size": 20,
      "filter_dict": {},
      "ext": {
        "fields": ["user_id", "account_name"],
        "sorts": [
          { "field": "user_id", "order": "asc" }
        ]
      }
    }
  }
}
```

说明：Render JSON 文档顶层只保留一个全局 `id`；节点需要区分时使用 `key`，不再重复声明 `id`。Runtime 内部会把顶层 `id` 兼容映射为页面标识，并为节点生成运行时作用域标识。历史输入中的 `pageId` 仍可兼容读取。

## 2. RenderDocument 字段

| 字段 | 类型 | 当前正式 Runtime | 说明 |
| --- | --- | --- | --- |
| `protocol` | string | 可省略，归一化为 `render-json` | 显式填写时必须是 `render-json` |
| `protocolVersion` | string | 可省略，默认 `1.0.0` | 当前只支持主版本 `1` |
| `id` | string | 可省略，默认使用页面 code | 文档全局稳定标识 |
| `revision` | string | 可选 | 内容修订标识，不等同于数据库乐观锁版本 |
| `title` | string | 可选 | 正式页面标题候选值 |
| `presentation` | object | 可选 | 宿主模式、标题、刷新和响应式配置 |
| `root` | object | 必填 | Render 节点树入口 |

正式 Runtime 还兼容两种历史输入：

- 文档本身是一个带 `component` 的裸 Renderer Schema。
- 文档包含 `schema`，且 `schema.component` 存在。

兼容输入会被包装成标准 `root.props.schema`。新配置应直接使用标准文档，不应继续制造历史结构。

## 3. Presentation 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `defaultMode` | enum | 建议打开方式，仅作元数据；显式路由 mode 优先 |
| `allowedModes` | enum[] | 限制允许使用的路由 mode |
| `title` | string | 页面宿主标题 |
| `description` | string | 页面宿主描述 |
| `refreshInterval` | number | Dashboard 数据重载间隔，单位秒，实际最小 5 秒 |
| `responsivePreset` | string | Dashboard `ResponsiveViewport` 使用的稳定预设 key |
| `readonly` | boolean | 页面级只读意图；具体 Renderer 是否消费取决于组件实现 |

支持的 mode：

| mode | 宿主行为 |
| --- | --- |
| `standard` | 自然文档流，适合常规管理页面 |
| `form` | 居中的表单任务宿主，通过 `formMode=view|edit|add` 控制交互状态 |
| `dashboard` | 使用唯一响应式缩放宿主，可定时刷新数据 |
| `report` | 面向自然排版和打印规则 |
| `embedded` | 最小外壳，适合嵌入其他容器 |

Layout key 和 mode 是不同概念。`dashboard` 是页面宿主模式，`zg-grid-layout` 是 Render 树内部的布局组件，不能互相替代。

`form` 模式使用独立的 `formMode` 查询参数；`model` 保留给 `preview=1` 场景的虚拟模型绑定。例如：

```text
/app/form/system-setting.info?formMode=view
/app/form/system-setting.info?formMode=edit&id=10001
/app/form/system-setting.info?formMode=add
```

## 4. 节点结构

标准节点字段：

```json
{
  "key": "stable-node-key",
  "component": "zg-list-main-layout",
  "componentVersion": "1.0.0",
  "props": {},
  "layout": {},
  "datasource": {},
  "bindings": {},
  "events": [],
  "actions": [],
  "children": []
}
```

| 字段 | 说明 |
| --- | --- |
| `key` | 节点稳定标识，用于观测、错误定位、点击事件区分和后续绑定 |
| `component` | Registry 中的稳定组件或布局 key |
| `componentVersion` | 组件契约版本；正式 Runtime 暂未执行版本匹配，Agent 校验会检查 |
| `props` | 传入组件的声明式参数；列表通常把 Renderer Schema 放在 `props.schema` |
| `layout` | 节点在父布局中的位置和受控尺寸 |
| `datasource` | 节点的数据查询意图 |
| `bindings` | 数据到组件 props 的绑定描述，当前 Runtime 支持程度有限 |
| `events` | 事件声明，当前通用事件执行链尚未完整接入正式节点 |
| `actions` | 动作声明，必须由受信任 Action Executor 解析 |
| `children` | 子节点数组 |

### 4.1 Layout 安全样式

`RenderJsonRuntimeNode` 当前只会从节点 `layout` 中读取以下样式：

- `gridColumn`
- `gridRow`
- `width`
- `height`
- `minHeight`

其他任意 CSS 字段不会通过节点通用样式逻辑写入 DOM。具体 Layout 组件可以按自己的受控契约读取额外字段。

### 4.2 静态节点

无需 Registry 的内置静态节点：

```json
{
  "key": "page-heading",
  "component": "heading",
  "props": {
    "text": "账户概览"
  }
}
```

- `text` 显示段落。
- `heading`、`title` 显示二级标题。
- 文本值按 `props.value`、`props.text`、`props.title` 顺序读取。

## 5. Renderer Schema

Runtime 对列表和表单保留了历史兼容：

- 推荐：节点的 `props.schema` 保存 Renderer Schema。
- 兼容：列表或表单节点可以直接把 schema 字段放在 `props`。
- 节点顶层存在 datasource 且 schema 内没有 datasource 时，Runtime 会把节点 datasource 合并进 schema 的内存副本。

Renderer Schema 是具体组件契约，不等同于 RenderDocument。以列表为例，它包含：

- `id`、`version`、`title`。
- `fields`、`filters`、`actions`、`tabs`、`summary`。
- `list_config`。
- `datasource`。

具体字段应同时参考 `ai-conversation-ui/src/application/schema/list.ts` 和组件资产契约。

### 5.1 表单字段 label 布局

`zg-common-form` 的字段 label 默认位于控件左侧并保持同行。单个字段可通过
`field.options.labelPosition` 覆盖：

| 值 | 行为 |
| --- | --- |
| `left` | 默认值，label 位于控件左侧 |
| `right` | label 位于控件右侧 |
| `top` | label 位于控件上方 |
| `inline` | label 内联显示在控件边框中 |

历史值 `inner` 会按 `inline` 解析。`form_config.labelWidth` 控制 `left`、`right`
模式下的 label 列宽，支持数字像素值以及 `px`、`rem`、`em`、`%` 字符串。

```json
{
  "key": "config_key",
  "label": "配置键",
  "component": "zg-input",
  "options": {
    "labelPosition": "left",
    "required": true
  }
}
```

## 6. Datasource 配置

### 6.1 direct-json

适合静态示例或已经准备好的数据：

```json
{
  "key": "local-accounts",
  "type": "direct-json",
  "data": {
    "records": [
      { "user_id": 1, "account_name": "演示账户" }
    ],
    "total": 1
  },
  "summary": {
    "source": "demo"
  }
}
```

它会生成本地 request plan，但不会发起 HTTP 请求。Resolver 最终产出：

```json
{
  "data": {
    "records": [
      { "user_id": 1, "account_name": "演示账户" }
    ],
    "total": 1
  },
  "summary": {
    "source": "demo"
  }
}
```

### 6.2 db-query-list

适合虚拟表列表查询：

```json
{
  "key": "account-query",
  "type": "db-query-list",
  "model": "ods_trade_user_account",
  "title": "账户列表",
  "page": 1,
  "page_size": 20,
  "filter_dict": {
    "enabled": true
  },
  "filterExpr": "enabled",
  "ext": {
    "fields": ["user_id", "account_name", "status"],
    "relations": [
      {
        "key": "profile",
        "model": "user_profile",
        "type": "left",
        "on": {
          "user_id": "user_id"
        }
      }
    ],
    "sorts": [
      { "field": "user_id", "order": "desc" }
    ]
  }
}
```

字段语义：

| 字段 | 说明 |
| --- | --- |
| `key` | request plan 的稳定标识；缺失时可退回 schema id |
| `type` | 当前远程列表固定为 `db-query-list` |
| `model` | 必填，虚拟实体编码 |
| `title` | 可选，传给 DbQuery 请求用于说明 |
| `page` | 初始页码 |
| `page_size` | 初始分页大小；Renderer 分页配置和运行时 query 可覆盖 |
| `filter_dict` | 固定过滤参数 |
| `filterExpr` | 过滤表达式，运行时筛选 key 会按需追加 |
| `ext.fields` | 显式请求的虚拟字段 |
| `ext.relations` | 虚拟实体关联 |
| `ext.sorts` | 排序规则 |

禁止在 datasource 中保存接口 URL。`db-query-list` 到 `/dbEngine/api/v1/query.list` 的映射属于可信 Data Requester。

### 6.3 semantic-query 等类型

Agent 校验器还识别：

- `semantic-query`
- `preview-result`
- `static`

这些类型是 AI 数据应用构建契约中的受控变体，但当前正式前端 Data Requester 只实现 `direct-json` 和 `db-query-list`。配置能够通过某一层的结构校验，不代表正式 Runtime 已具备对应执行器。

## 7. Filter 到 DbQuery 的映射

列表 filter 示例：

```json
{
  "key": "keyword",
  "label": "账户名称",
  "component": "input",
  "options": {
    "query": {
      "field": "account_name",
      "op": "like",
      "submitOnEnter": true
    }
  }
}
```

用户输入进入 `query.filters.keyword` 后，Resolver 转为：

```json
{
  "filter_dict": {
    "account_name": {
      "op": "like",
      "value": "关键字"
    }
  }
}
```

空字符串、空数组、null 和无有效 value 的条件会被剔除；`is_null`、`is_not_null` 不要求 value。

## 8. Actions 与 Events

Renderer/Page 动作声明示例：

```json
{
  "actions": [
    {
      "key": "refresh",
      "name": "刷新",
      "action": "RELOAD",
      "options": {
        "type": "primary",
        "icon": "refresh"
      }
    }
  ]
}
```

节点事件声明示例：

```json
{
  "events": [
    {
      "event": "queryChange",
      "actionRef": "refresh"
    }
  ]
}
```

当前需要区分：

- Renderer 自身通过 Vue emit 发出的 `query-change`、`reload`、`action` 已被节点监听。
- JSON 中声明的通用节点 events/actions 尚未被正式 Runtime 完整解释执行；它们不是 Renderer/Page 按钮配置。
- Renderer/Page 动作的唯一契约为 `{ key, name, action, options? }`。`options` 可包含 `type`、`style`、`class`、`icon`，其中按钮视觉类型使用 `options.type`。
- `options.type` 支持 `default`、`primary`、`success`、`warning`、`danger`、`info`；当前内置 icon 为 `download`、`fullscreen`、`operation`、`print`、`refresh`。`style` 与 `class` 会经过安全归一化。
- 此契约同时适用于页面 `actions`、列表/表单 schema 的 `actions` 和列表的 `list_config.actionColumns`。旧的扁平 `type`、`icon` 以及 `id`、`target`/`targets`、`title`、`disabled` 都不是 Renderer/Page 动作字段。
- Python Agent 执行计划的 `RenderNode.actions` 是独立 Agent 契约；不要将其字段或校验规则与 Renderer/Page 动作混用。
- 任意删除、写入、跳转等有副作用动作必须映射到受信任实现，不能执行 JSON 中的函数或 URL。

## 9. 版本兼容

当前存在三处版本判断：

| 位置 | 当前行为 |
| --- | --- |
| 正式页面 Normalizer | 接受主版本为 `1` 的字符串 |
| 聊天 Artifact Normalizer | 默认补 `1.0.0`，不执行支持版本判断 |
| Agent Validator | 只接受精确 `1.0` 或 `1.0.0` |

新增协议字段时应先统一这三处策略，再更新组件目录、示例和文档。只修改 TypeScript interface 不会自动形成运行时兼容。

## 10. 配置检查清单

提交正式 Render JSON 前至少确认：

- `protocol`、`protocolVersion` 和 `root` 正确。
- 顶层 `id` 和节点 `key` 稳定，不使用随机展示文案。
- component 已在前端 Registry 注册；AI 产物还需要存在于已发布组件目录。
- componentVersion 与组件契约匹配。
- datasource 只描述查询意图，不含 URL、SQL 或凭据。
- `model` 使用已发布虚拟实体编码，fields 和 relations 使用虚拟字段。
- Dashboard 只设置一个连续缩放宿主。
- actions/events 不依赖尚未接入的通用执行能力。
- 正式保存后确认已产生页面快照。
