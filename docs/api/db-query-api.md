# DbQueryApi 接口实现说明

## 1. 目标

- 说明 `DbQueryApi` 在当前仓库中的实现入口、调用链和查询语义。
- 对齐 `query.get`、`query.list`、`query.count`、`query.aggregate`、`query.tree`、`query.pivot` 六类接口的当前行为边界。
- 沉淀 db-engine 查询能力的现状，避免后续继续按旧的“单表扁平返回”认知实现。

## 2. 实现入口

接口定义位于：

- `app/app-platform-db-engine/api/src/main/java/ai/platform/aiassit/db/engine/api/DbQueryApi.java`

控制器实现位于：

- `app/app-platform-db-engine/core/src/main/java/ai/platform/aiassit/db/engine/core/controller/DbQueryController.java`

服务实现位于：

- `app/app-platform-db-engine/core/src/main/java/ai/platform/aiassit/db/engine/core/service/impl/DbQueryServiceImpl.java`

当前调用链固定为：

1. `DbQueryController` 通过 Spring MVC 暴露 HTTP 接口。
2. `DbQueryController` 直接委托 `DbQueryService`。
3. `DbQueryServiceImpl` 负责把请求 DTO 转成 SQL。
4. `DbQueryServiceImpl` 调用 `DbAccessService.query(...)` 执行 SQL。
5. 查询结果根据接口类型转换为明细记录、树结构、统计结果或透视结果。

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

- 入口方法：`DbQueryServiceImpl.queryGet(...)`
- 语义：查询一条记录，最终 SQL 固定 `LIMIT 1`
- 过滤来源：
  - `id` 存在时，自动拼接 `id = ?`
  - `filters` 同时支持两种结构：
    - `{ "status": "ENABLE" }`
    - `{ "status": { "op": "eq", "value": "ENABLE" } }`
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

- 入口方法：`DbQueryServiceImpl.queryList(...)`
- 语义：分页查询
- 过滤来源：`filter_dict`
- 排序来源：`ext.sorts`
- 关联来源：`ext.relations`
- 总数统计：通过 `queryTotal(...)` 额外执行一条 `COUNT(1)` SQL
- 返回结构：`records` 中每条记录同样支持关联对象嵌套

### 4.3 `query.count`

- 入口方法：`DbQueryServiceImpl.queryCount(...)`
- 实际依赖：`buildAggregateBundle(...)`
- 语义：分组统计 / 计数统计
- 过滤来源：`filters`
- 分组来源：`dimensions`
- 指标来源：`metrics`
- having 来源：`having`
- 排序来源：`sorts`
- 关联来源：`ext.relations`
- 返回结构：统计结果保持扁平，不做关联对象嵌套

### 4.4 `query.aggregate`

- 入口方法：`DbQueryServiceImpl.queryAggregate(...)`
- 与 `query.count` 共用 `buildAggregateBundle(...)`
- 语义：聚合统计
- 返回结构：保持扁平

### 4.5 `query.tree`

- 入口方法：`DbQueryServiceImpl.queryTree(...)`
- 语义：先查平铺数据，再在内存中按父子关系组装树
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

- 入口方法：`DbQueryServiceImpl.queryPivot(...)`
- 语义：基于聚合结果再转成透视表
- 行维度：`rows`
- 列维度：`columns`
- 指标：`metrics`
- 关联来源：`ext.relations`
- 返回结构：透视结果保持扁平，不做关联对象嵌套

## 5. SQL 生成规则

### 5.1 表与字段标识符

- `model`、字段名、排序字段、维度字段、指标字段都走统一校验。
- 当前允许格式：
  - `table`
  - `field`
  - `relationKey.field`
- 最终统一转为反引号包裹的 SQL 标识符。

示例：

- `user.name` -> `` `user`.`name` ``

### 5.2 关联表实现

`DbQueryRelation` 当前关键字段：

- `key`：关联 SQL 别名，也是返回结果中的关联对象 key
- `model`：关联表名
- `type`：关联类型，当前支持 `left`、`inner`、`right`、`full`
- `on`：关联条件映射
- `filter`：关联表过滤条件

当前实现规则：

1. 主表固定使用 `model`。
2. 每个 relation 追加一段 `JOIN`。
3. `on` 的 key 视为左侧字段，value 视为关联表字段。
4. `filter` 会拼进 `ON`，不会下沉到 `WHERE`，这样可以保留 `LEFT JOIN` 语义。

示例：

```json
{
  "key": "user",
  "model": "users",
  "type": "left",
  "on": {
    "user_id": "id"
  },
  "filter": {
    "deleted": 0
  }
}
```

大致生成：

```sql
LEFT JOIN `users` `user`
  ON `user_id` = `user`.`id`
 AND `user`.`deleted` = 0
```

## 6. 关联结果组装规则

当前只有“明细型结果”会把关联字段从扁平列名还原为嵌套对象：

- `query.get`
- `query.list`
- `query.tree` 的 `node.data`

实现方式：

1. 关联字段在 `SELECT` 阶段自动加内部别名。
2. 查询返回后，按 `relation.key` 把字段还原成嵌套对象。
3. 如果某个关联对象所有字段都为 `null`，则整个关联对象置为 `null`。

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

`DbQueryGetRequest.filters` 额外兼容简写：

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
