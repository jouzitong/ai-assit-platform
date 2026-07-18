---
documentType: data-semantic-model
model: sales_order
modelAliases: [销售订单, 交易订单, 成交订单]
domain: 销售
description: 一笔订单一条记录；用于订单明细、销售额和成交趋势分析。
sourceRevision: virtual-model/sales_order/v12
updatedAt: 2026-07-18
owner: 销售数据团队
queryCapabilities: [get, list, count, aggregate]
defaultPageSize: 20
maxPageSize: 1000
---

# 销售订单（sales_order）

## 模型粒度
- 一行代表：一笔销售订单。
- 主键：`id`
- 默认时间字段：`paid_at`
- 禁止将订单行、退款单与本模型混合聚合。

## 字段目录

| code | 中文名/同义词 | 类型 | 业务含义 | 可用于 | 允许操作/聚合 | 示例 |
|---|---|---|---|---|---|---|
| id | 订单ID, 订单号 | long | 订单唯一标识 | filter, display, sort | eq, in | 10001 |
| status | 订单状态, 成交状态 | string | 订单当前状态 | filter, dimension | eq, in | PAID |
| paid_at | 支付时间, 成交时间 | datetime | 实际支付完成时间 | filter, dimension, sort | gte, lt | 2026-07-01T00:00:00 |
| paid_amount | 实付金额, 销售额, GMV | decimal | 用户实际支付金额 | metric, display | sum, avg, min, max | 199.00 |
| customer_id | 客户ID | long | 下单客户标识 | filter, relation | eq, in | 3001 |

## 值域与口径
- `status`：`CREATED`、`PAID`、`CANCELLED`、`REFUNDED`。
- “销售额/GMV”：`sum(paid_amount)`，默认只统计 `status=PAID`。
- “订单数”：`count(id)`；不要以 `count(customer_id)` 代替。

## 可用关系
- alias: customer
  targetModel: customer
  type: left
  relationMeaning: 下单客户
  fieldUsage: [customer.name, customer.level]
  on: { customer_id: id } # 仅在目录无法唯一推导关系时才生成

## 请求生成规则
1. 只能使用本页 `model`、字段 code、关系 alias 和关系字段。
2. 明细查询使用 `query.list` 或 `query.get`；统计使用 `query.aggregate`。
3. `filter_dict` 无 `filterExpr` 时默认为 AND。
4. 有 `filterExpr` 时，只能用 and/or/括号，且必须覆盖全部 filter_dict key。
5. `like` 传原始关键词，不手工加 `%`。
6. 关系字段如 `customer.name` 必须同时声明 `ext.relations` 中 key 为 `customer` 的关系。
7. 禁止生成 SQL、物理表名、物理列名、未列出的字段或聚合函数。

## 已审核示例

### 查询已支付订单
```json
{
  "title": "查询近七天已支付订单",
  "model": "sales_order",
  "filter_dict": {
    "status": { "op": "eq", "value": "PAID" },
    "paid_at": { "op": "gte", "value": "2026-07-11T00:00:00" }
  },
  "ext": {
    "fields": ["id", "paid_at", "paid_amount"],
    "sorts": [{ "field": "paid_at", "order": "desc" }]
  },
  "page": 1,
  "page_size": 20
}