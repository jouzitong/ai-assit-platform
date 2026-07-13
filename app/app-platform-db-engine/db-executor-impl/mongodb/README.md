# MongoDB executor

本模块通过官方同步驱动实现 `DbAccessExecutor`。为保持现有 SPI 二进制兼容，
`QueryRequest.sql`、`ExecuteRequest.sql` 和结果中的 `executedSqls` 仍沿用历史字段名，
但实际内容是严格 JSON/Extended JSON 命令，而不是 SQL 或 Mongo shell JavaScript。

## 连接配置

- `DatabaseConnectionConfig.jdbcUrl` 暂承载 `mongodb://` 或 `mongodb+srv://` URI。
- `HOST_PORT` 模式默认端口为 `27017`，必须配置 `databaseName`。
- `schemaName` 在本实现中等价于 MongoDB database；默认禁止请求访问配置之外的 database。
- `driverProperties.allowCrossDatabase=true` 可显式允许跨 database。
- 支持 `driverProperties.authSource`、`applicationName`、`maxPoolSize`、`minPoolSize`、
  `schemaInferenceSampleSize`。连接和读取超时取数据源 network 配置。
- URI 自带认证信息时，不可再同时配置独立 username/password。

## 查询信封

`find` 示例：

```json
{
  "operation": "find",
  "collection": "orders",
  "filter": {"tenantId": {"$param": 0}, "status": "PAID"},
  "projection": {"_id": 1, "amount": 1, "createdAt": 1},
  "sort": {"createdAt": -1},
  "skip": 0,
  "limit": 100
}
```

`$param` 下标对应 `QueryRequest.parameters`。每个参数必须被使用，越界或未使用均报错。
最终行数取信封 `limit` 与 `QueryRequest.maxRows` 的较小正值；两者都未配置时默认最多返回 1000 条。

`aggregate` 示例：

```json
{
  "operation": "aggregate",
  "collection": "orders",
  "pipeline": [
    {"$match": {"tenantId": {"$param": 0}}},
    {"$group": {"_id": "$status", "amount": {"$sum": "$amount"}}}
  ],
  "limit": 100
}
```

聚合阶段采用只读白名单，禁止 `$lookup`、`$unionWith`、`$graphLookup` 等跨 collection 访问，
同时禁止 `$out`、`$merge`、`$where`、`$function` 和 `$accumulator`，也不提供任意
`runCommand` 入口。返回值会递归归一化；例如 ObjectId 转十六进制字符串、日期转 ISO-8601、
Binary 转 Base64、Decimal128 转 BigDecimal 或字符串。

## 写入信封

支持以下 operation：

- `insertOne`：`document`
- `insertMany`：`documents`
- `updateOne` / `updateMany`：`filter`、`update`、可选 `upsert`
- `replaceOne`：`filter`、`replacement`、可选 `upsert`
- `deleteOne` / `deleteMany`：`filter`

示例：

```json
{
  "operation": "updateMany",
  "collection": "orders",
  "filter": {"tenantId": {"$param": 0}},
  "update": {"$set": {"archived": true}},
  "upsert": false
}
```

写入参数对应 `ExecuteRequest.parameters`。更新只接受模块白名单内的 MongoDB update operator，
不接受 replacement 冒充 update、更新 pipeline 或服务器端脚本。collection 必须已经存在，
避免 insert 隐式创建 collection。

`updateMany` / `deleteMany` 使用空 `filter` 时必须显式设置 `"allowAllDocuments": true`；
`updateOne` / `replaceOne` / `deleteOne` 不接受空过滤器。

## collection 与字段语义

- table 对应 collection；view/timeseries 会出现在列表中，但当前执行器只允许读取，不允许结构或数据写入。
- table comment 映射为 `validator.$jsonSchema.description`。
- 字段定义映射为顶层 `validator.$jsonSchema.properties`；嵌套文档整体视为 `OBJECT`。
- `nullable=false` 表示字段加入 `required` 且不接受 BSON null；`nullable=true` 表示非必需且接受 null。
- 只有 `_id` 能标记为主键。`autoIncrement`、`defaultValue`、精度和小数位没有可靠的
  MongoDB validator 等价语义，配置这些属性会明确报错。
- 列表字段以 validator 为准，并用最多 `schemaInferenceSampleSize` 个文档补充推断；因此无
  validator collection 的字段结果是采样性质，混合类型返回 `MIXED`。
- 复合索引按字段拆成多条 `DbIndexMeta`。partial、sparse、TTL、hidden 等 MongoDB 特性受
  当前 SPI 限制无法完整表达。

## 删除字段的非原子风险

`deleteTableColumns` 与关系型 `DROP COLUMN` 对齐，会删除全部文档中的对应字段，而不是只删除
validator 定义。执行顺序是：

1. 使用 `collMod` 从 JSON Schema properties/required 移除字段，避免 required validator 阻止 `$unset`。
2. 使用 `updateMany({}, {$unset: ...})` 删除全部文档中的数据。

`collMod` 与多文档更新不能组成一个原子事务。第二步失败时可能出现 validator 已放宽、部分或全部
数据仍保留的中间态；操作可重试，但调用方必须将其作为有破坏性的非原子变更审计和告警。
