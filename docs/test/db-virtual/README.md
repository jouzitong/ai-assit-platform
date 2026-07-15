# Db Virtual 三数据源联调包

这是一套可重复执行的 `DbQueryApi` 集成测试数据。它使用三个逻辑独立的 MySQL 数据源，并由一个 JSON 同时描述：物理测试数据、虚拟实体/关系目录，以及 `query.list`、`query.get`、`query.count`、`query.aggregate` 的断言用例。

## 文件

| 文件 | 职责 |
| --- | --- |
| `sql/00-create-databases.sql` | 创建三个测试库 |
| `sql/10-user-info.sql` | 用户、地址、角色、用户角色桥接表 |
| `sql/20-order.sql` | 订单、订单明细表 |
| `sql/30-account.sql` | 账户、账户流水表 |
| `db-virtual-suite.json` | 数据源、虚拟目录、关系声明和接口断言的单一事实来源 |
| `run_db_virtual_tests.py` | 按 JSON 调用 DbQuery API，并逐项汇总断言结果 |

## 关系覆盖

| 关系 | 虚拟目录声明 | 用例 |
| --- | --- | --- |
| 用户 → 账户 | `OBJECT` | `list-user-with-parallel-collections`、`get-account-with-transactions` |
| 订单 → 用户 / 账户 | `OBJECT`，跨数据源 | `list-order-with-object-and-collection`、`get-order-with-items` |
| 订单 → 明细 | `COLLECTION` | `list-order-with-object-and-collection`、`get-order-with-items` |
| 用户 → 地址 / 订单 | `COLLECTION`，其中订单跨数据源 | `list-user-with-parallel-collections` |
| 账户 → 流水 | `COLLECTION` | `get-account-with-transactions` |
| 用户 ↔ 角色 | `ods_trade_account_user_role` 桥接实体，两端均为 `OBJECT` | `list-user-role-bridge` |

`OBJECT` 与 `COLLECTION` 是虚拟关系的返回形态，不是实体表上的 1:1、1:N、N:N 标签。`user_role` 是 N:N 的物理桥接表；当前用例显式查询 `ods_trade_account_user_role`，不假定 `ods_trade_account_user_profile.role_links.role` 能多跳自动展开。

## 1. 初始化 MySQL 数据

以下脚本会重建各自库中的表和固定数据，适合本地/测试环境，不要对生产库执行。

```bash
mysql -h 127.0.0.1 -P 3306 -u root -p < docs/test/db-virtual/sql/00-create-databases.sql
mysql -h 127.0.0.1 -P 3306 -u root -p < docs/test/db-virtual/sql/10-user-info.sql
mysql -h 127.0.0.1 -P 3306 -u root -p < docs/test/db-virtual/sql/20-order.sql
mysql -h 127.0.0.1 -P 3306 -u root -p < docs/test/db-virtual/sql/30-account.sql
```

即使三个库位于同一 MySQL 实例，也要在平台中注册为三个独立 `sourceKey`，以保证实际经过跨源关联分支：

| sourceKey | database |
| --- | --- |
| `dbv_mysql_user` | `db_virtual_user` |
| `dbv_mysql_order` | `db_virtual_order` |
| `dbv_mysql_account` | `db_virtual_account` |

## 2. 建立并发布虚拟目录

`db-virtual-suite.json` 的 `virtualCatalog` 是目录配置蓝图，不是可直接写入 `vd_*` 表的 SQL：`physicalTableMetaId`、虚拟字段 ID 等由当前物理目录导入流程生成。按以下顺序在虚拟数据管理端完成配置。

1. 为三个 sourceKey 导入对应物理表元数据。
2. 按 `virtualCatalog.entities` 创建八个虚拟实体、字段和只读主绑定；字段编码与物理列同名，`id` 均标记为虚拟主键。
3. 按 `virtualCatalog.relations` 创建关系。每个关系以 `sourceEntityCode` 为作用域，映射 `sourceField → targetField`，并严格设置正向 `resultMode` 与反向 `reverseResultMode`。
4. 发布全部实体；查询请求里的 `model` 使用 JSON 中的 `entityCode`，例如 `ods_trade_order_sales_order`，而不是物理表名。

例如订单列表的目标响应形状是：

```json
{
  "id": 50001,
  "order_no": "SO-202607-001",
  "user": { "user_name": "Alice" },
  "account": { "account_no": "ACC-10001" },
  "items": [
    { "sku": "SKU-BOOK-001", "quantity": 1 },
    { "sku": "SKU-PEN-001", "quantity": 2 }
  ]
}
```

集合关系不会复制订单根记录，因此该订单的 `query.list` 结果仍是一条记录，`pageInfo.total` 也按订单主实体计数。

## 3. 执行用例

先进行不依赖服务的 JSON/用例结构校验：

```bash
python3 docs/test/db-virtual/run_db_virtual_tests.py --dry-run
```

启动 db-engine 并完成目录发布后，执行全部接口用例：

```bash
export DB_VIRTUAL_BASE_URL=http://localhost:8080/dbEngine
export DB_VIRTUAL_TOKEN=replace-with-test-token
python3 docs/test/db-virtual/run_db_virtual_tests.py \
  --report /tmp/db-virtual-report.json
```

可使用环境所需的额外网关头，或只运行一个用例：

```bash
python3 docs/test/db-virtual/run_db_virtual_tests.py \
  --base-url http://localhost:8080/dbEngine \
  --header 'X-Tenant-Id:1' \
  --case list-order-with-object-and-collection
```

脚本要求成功响应为 `R<T>` 的 `code == 0`，断言路径相对于完整 HTTP 响应（因此从 `data.*` 开始）。它会继续执行后续用例，最后以非零退出码表示存在失败；`--report` 会保存逐项断言、HTTP 状态和耗时。

## 当前边界

- `ext.relations` 的 `key`、`model` 必填；`key` 是本次查询的返回别名，`model` 是目标虚拟表编码。
- 两张虚拟表已有唯一已发布关系时可省略 `on`；没有已发布关系时必须用 `on` 声明“当前表字段 -> 目标表字段”。
- 已发布关系支持双向查询；反向查询会交换字段方向，并按反向结果形态返回对象或数组。
- `COLLECTION` 允许明细投影，空值返回 `[]`；`OBJECT` 无匹配返回 `null`。
- 集合字段不能作为全局标量过滤、排序、分组或聚合字段。因此统计用例只作用于 `ods_trade_order_sales_order` 自身字段。
- N:N 的直接多跳投影不属于当前用例范围；桥接实体查询是当前可执行、语义明确的方式。
