---
documentType: data-semantic-model
model: sales_order
modelAliases: [销售订单, 交易订单, 成交订单]
domain: 销售
description: 一笔订单一条记录；用于订单明细、销售额和成交趋势分析。
sourceRevision: virtual-model/sales_order/v12
updatedAt: 2026-07-18
owner: 销售数据团队
---

# 销售订单（sales_order）

## 字段目录（机器可读）

```json
{
  "schemaVersion": "1.0",
  "model": "sales_order",
  "primaryKeys": ["id"],
  "defaultTimeField": "paid_at",
  "fields": [
    {
      "code": "id",
      "name": "订单ID",
      "aliases": ["订单号"],
      "logicalType": "long",
      "description": "订单唯一标识",
      "example": 10001
    },
    {
      "code": "status",
      "name": "订单状态",
      "aliases": ["成交状态"],
      "logicalType": "string",
      "description": "订单当前状态",
      "filterOperators": ["eq", "in"],
      "example": "PAID"
    },
    {
      "code": "paid_at",
      "name": "支付时间",
      "aliases": ["成交时间"],
      "logicalType": "datetime",
      "description": "实际支付完成时间",
      "filterOperators": ["eq", "gt", "gte", "lt", "lte", "in"],
      "example": "2026-07-01T00:00:00"
    },
    {
      "code": "paid_amount",
      "name": "实付金额",
      "aliases": ["销售额", "GMV"],
      "logicalType": "decimal",
      "description": "用户实际支付金额",
      "example": 199.00
    },
    {
      "code": "customer_id",
      "name": "客户ID",
      "aliases": [],
      "logicalType": "long",
      "description": "下单客户标识",
      "example": 3001
    }
  ],
  "relations": [
    {
      "key": "customer",
      "model": "customer",
      "type": "left",
      "on": {"customer_id": "id"},
      "fields": [
        {"code": "name", "name": "客户名称"},
        {"code": "level", "name": "客户等级"}
      ]
    }
  ]
}
```

## 值域与口径
- `status`：`CREATED`、`PAID`、`CANCELLED`、`REFUNDED`。
- “销售额/GMV”：`sum(paid_amount)`，默认只统计 `status=PAID`。
- “订单数”：`count(id)`；不要以 `count(customer_id)` 代替。

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
```
