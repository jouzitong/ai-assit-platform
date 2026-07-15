# DbQueryApi 接口实现说明

## 1. 目标

- 说明 `DbQueryApi` 在当前仓库中的实现入口、调用链和查询语义。
- 对齐 `query.get`、`query.list`、`query.count`、`query.aggregate`、`query.tree`、`query.pivot` 六类接口的当前行为边界。
- 沉淀 db-engine 查询能力的现状，避免后续继续按旧的“单表扁平返回”认知实现。

## 2. 实现入口

接口定义位于：

- `app/app-platform-db-engine/api/src/main/java/ai/platform/aiassit/db/engine/api/DbQueryApi.java`

控制器实现位于：

- `app/app-platform-db-engine/data-virtualization-adapter/src/main/java/ai/platform/aiassit/db/engine/virtualization/adapter/controller/DbQueryController.java`

兼容门面与请求翻译位于：

- `app/app-platform-db-engine/data-virtualization-adapter/src/main/java/ai/platform/aiassit/db/engine/virtualization/adapter/compat/DbQueryCompatibilityFacade.java`
- `app/app-platform-db-engine/data-virtualization-adapter/src/main/java/ai/platform/aiassit/db/engine/virtualization/adapter/compat/LegacyRequestTranslator.java`

当前调用链固定为：

1. `DbQueryController` 通过 Spring MVC 暴露 HTTP 接口。
2. `DbQueryController` 直接委托 `DbQueryService`。
3. `DbQueryCompatibilityFacade` 把旧 `DbQueryApi` 请求交给 `LegacyRequestTranslator`。
4. Translator 只生成虚拟字段、虚拟模型和请求级关系描述，不接收物理表或物理列。
5. `VirtualQueryGateway` 通过已发布目录执行主查询及受预算保护的应用层关联。
6. 查询结果再组装为旧接口的明细、树、统计或透视结构。

## 3. 路由清单

`DbQueryController` 当前实现了以下路由：

- `POST /api/v1/query.get`
- `POST /api/v1/query.list`
- `POST /api/v1/query.count`
- `POST /api/v1/query.aggregate`
- `POST /api/v1/query.tree`
- `POST /api/v1/query.pivot`

所有接口统一返回 `R<T>`。

## 4. 各接口实现行为

### 4.1 `query.get`

- 入口方法：`DbQueryCompatibilityFacade.queryGet(...)`
- 语义：按虚拟目录查询一条记录
- 过滤来源：
  - `id` 存在时，自动拼接 `id = ?`
  - `filter_dict` 同时支持两种结构：
    - `{ "status": "ENABLE" }`
    - `{ "status": { "op": "eq", "value": "ENABLE" } }`
  - `filterExpr` 可选，用于组合 `filter_dict` 中的条件，例如：`(status or id) and code`
  - 未传 `filterExpr` 时，`filter_dict` 默认仍按顶层 `AND` 拼接
- 排序来源：`ext.sorts`
- 关联来源：`ext.relations`
- 返回结构：明细记录支持按关联 `key` 组装嵌套对象

示例返回：

```json
{
  "id": 101,
  "order_no": "SO20260624001",
  "user": {
    "name": "Alice",
    "mobile": "13800000000"
  }
}
```

### 4.2 `query.list`

- 入口方法：`DbQueryCompatibilityFacade.queryList(...)`
- 语义：分页查询
- 过滤来源：`filter_dict`
- 过滤组合：`filterExpr`
- 排序来源：`ext.sorts`
- 关联来源：`ext.relations`
- 总数统计：由虚拟查询内核按当前关系与过滤语义计算
- 返回结构：分页数据放在 `list`，分页信息放在 `pageInfo.{ total, size, page }`，其中每条列表记录同样支持关联对象嵌套

### 4.3 `query.count`

- 入口方法：`DbQueryCompatibilityFacade.queryCount(...)`
- 语义：分组统计 / 计数统计
- 过滤来源：`filter_dict`
- 过滤组合：`filterExpr`
- 分组来源：`dimensions`
- 指标来源：`metrics`
- having 来源：`having`
- 排序来源：`sorts`
- 关联来源：`ext.relations`
- 返回结构：统计结果保持扁平，不做关联对象嵌套

### 4.4 `query.aggregate`

- 入口方法：`DbQueryCompatibilityFacade.queryAggregate(...)`
- 语义：聚合统计
- 过滤来源：`filter_dict`
- 过滤组合：`filterExpr`
- 返回结构：保持扁平

### 4.5 `query.tree`

- 入口方法：`DbQueryCompatibilityFacade.queryTree(...)`
- 语义：先查平铺数据，再在内存中按父子关系组装树
- 过滤来源：`filter_dict`
- 过滤组合：`filterExpr`
- 可配置字段：
  - `ext.idField`
  - `ext.parentField`
  - `ext.labelField`
  - `ext.rootValue`
- 关联来源：`ext.relations`
- 返回结构：
  - 节点本身有 `id`、`parentId`、`label`
  - 节点 `data` 支持关联对象嵌套

### 4.6 `query.pivot`

- 入口方法：`DbQueryCompatibilityFacade.queryPivot(...)`
- 语义：基于聚合结果再转成透视表
- 过滤来源：`filter_dict`
- 过滤组合：`filterExpr`
- 行维度：`rows`
- 列维度：`columns`
- 指标：`metrics`
- 关联来源：`ext.relations`
- 返回结构：透视结果保持扁平，不做关联对象嵌套

## 5. 虚拟字段与关联规则

### 5.1 表与字段标识符

- `model`、字段名、排序字段、维度字段、指标字段都走已发布虚拟目录校验。
- 当前允许格式：
  - `table`
  - `field`
  - `relationKey.field`
- `relationKey.field` 中的 `relationKey` 是本次请求声明的返回别名，不是物理表别名。
- 物理表和物理字段只能由 Binding 与 TransformRule 解析，不能从请求直接进入执行器。

### 5.2 关联表实现

`DbQueryRelation` 当前关键字段：

- `key`：必填，本次查询的关联别名，也是返回结果中的对象或数组 key
- `model`：必填，目标虚拟表编码
- `type`：可选，当前仅支持 `left`
- `on`：当前主虚拟表字段到目标虚拟表字段的映射
- `filter`：关联表过滤条件

当前实现规则：

1. `key` 只负责字段前缀和返回命名，不参与已发布关系身份匹配。
2. 当前虚拟表与 `model` 之间存在唯一已发布关系时，只需提交 `key + model`。
3. 不存在已发布关系时，必须提交 `on`；`on` 两端都只能引用已发布且类型兼容的虚拟字段。
4. 同一对虚拟表存在多条关系时，必须用 `on` 唯一消歧。
5. 已发布关系可以从 source 或 target 任一侧查询；反向查询会同时交换 Join 字段方向和返回形态。
6. `filter` 只作用于关联对象，保持 `LEFT JOIN ON` 作用域。

示例：

```json
{
  "key": "employee",
  "model": "emp",
  "type": "left",
  "on": {
    "emp_id": "id"
  },
  "filter": {
    "deleted": 0
  }
}
```

若 `emp_base.emp_id -> emp.id` 已配置为虚拟关系，可以省略 `on`；从 `emp` 查询 `emp_base` 时，内核自动使用反向字段映射。

## 6. 关联结果组装规则

当前只有“明细型结果”会把关联字段还原为嵌套对象或对象数组：

- `query.get`
- `query.list`
- `query.tree` 的 `node.data`

实现方式：

1. 关联字段在 `SELECT` 阶段自动加内部别名。
2. 查询返回后，按 `relation.key` 还原关联结果。
3. 有效形态为 `OBJECT` 时返回 `key: { ... }`，无匹配返回 `key: null`。
4. 有效形态为 `COLLECTION` 时返回 `key: [{ ... }]`，无匹配返回 `key: []`。
5. 反向查询优先使用关系配置的 `reverseResultMode`；旧关系未配置时按源侧虚拟主键保守推导。

示例字段：

```json
["id", "order_no", "user.name", "user.mobile"]
```

示例返回：

```json
{
  "id": 101,
  "order_no": "SO20260624001",
  "user": {
    "name": "Alice",
    "mobile": "13800000000"
  }
}
```

统计型结果当前不做嵌套还原：

- `query.count`
- `query.aggregate`
- `query.pivot`

## 7. 过滤条件规则

### 7.1 通用结构

大多数查询接口当前仍然使用：

```json
{
  "field": {
    "op": "eq",
    "value": "xxx"
  }
}
```

支持的操作符：

- `eq`
- `ne` / `neq`
- `gt`
- `gte` / `ge`
- `lt`
- `lte` / `le`
- `like`
- `prefix_like`
- `suffix_like`
- `in`
- `not_in`
- `is_null`
- `is_not_null`

### 7.2 `query.get` 兼容写法

`DbQueryGetRequest.filterDict` 额外兼容简写，JSON 字段名为 `filter_dict`：

```json
{
  "status": "ENABLE",
  "biz_type": "ORDER"
}
```

等价于：

```json
{
  "status": { "op": "eq", "value": "ENABLE" },
  "biz_type": { "op": "eq", "value": "ORDER" }
}
```

兼容逻辑只在 service 内部做归一化，对外语义仍然是“默认等于”。

## 8. 当前边界

- `DbQueryApi` 当前是“动态 SQL 查询接口”，不是 ORM 风格的领域查询服务。
- SQL 拼接能力集中在 `DbQueryServiceImpl`，控制器层不承载业务。
- 聚合 / 透视结果当前保持扁平结构，不做嵌套对象还原。
- 关联字段要返回嵌套对象时，调用方必须在 `fields` 中显式写出 `relationKey.field`。
- 关联查询依赖调用方显式声明 `relations`，当前没有自动推导关联关系的能力。
