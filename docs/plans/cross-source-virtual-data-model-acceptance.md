# 跨数据源虚拟数据模型实施与验收说明

> 状态：代码实施完成，待业务数据库联调验收
> 日期：2026-07-13
> 对应设计：[cross-source-virtual-data-model-design.md](cross-source-virtual-data-model-design.md)

## 1. 已交付范围

本次实施新增独立聚合模块 `app/app-platform-data-virtualization`，包含 `api`、`data`、`core` 三层，并由 DB Engine Boot 统一装配。

已完成：

1. 六张虚拟配置表及 Entity、DTO、QueryRequest、Mapper、Convert、Service 和 CRUD Controller。
2. 从真实表生成虚拟实体草稿、目录校验、版本发布、提交后缓存更新和不可变运行时快照。
3. 强类型 `BindingRoutingConfig`，支持 SINGLE、HASH、LIST、RANGE 路由，以及 PRIMARY/REPLICA 和一致性选择。
4. 字段变换规则、结构化端口、注册表、配置版本/未知字段/端口引用校验、读写预览和双向血缘查询。
5. 内置 `identity`、`type_cast`、`json_extract`、`json_compose`、`text_concat`、`text_split`、`enum_map`、`coalesce` 变换器。
6. 标准 Filter AST、逻辑计划、物理计划、Explain、参数化 SQL、明确 `sourceKey` 的物理执行适配。
7. 多分片受控并行、全局扫描预算、字段归一、过滤、排序、聚合、分页和预定义关系的跨源 Hash Join。
8. 单绑定 INSERT/UPDATE/DELETE、写回端口完整性、组合字段部分写保护、写冲突保护和进程内幂等键。
9. 跨绑定 `BEST_EFFORT` 结果明细；跨绑定 `LOCAL/ATOMIC` 明确拒绝，不伪装成分布式原子事务。
10. 稳定错误类别、任务上下文、脱敏错误响应和执行摘要日志。

## 2. 关键文件

| 内容 | 位置 |
|---|---|
| 数据库初始化脚本 | `app/app-platform-data-virtualization/data/src/main/resources/db/schema/data_virtualization_init.sql` |
| 内部 API 契约 | `app/app-platform-data-virtualization/api/src/main/java/ai/platform/aiassit/data/virtualization/api/VirtualDataApi.java` |
| 目录发布校验 | `app/app-platform-data-virtualization/core/src/main/java/ai/platform/aiassit/data/virtualization/core/catalog/CatalogValidator.java` |
| 字段变换管理 | `app/app-platform-data-virtualization/core/src/main/java/ai/platform/aiassit/data/virtualization/core/transform/FieldTransformManagementService.java` |
| 查询与跨源关联 | `app/app-platform-data-virtualization/core/src/main/java/ai/platform/aiassit/data/virtualization/core/execution/VirtualDataQueryService.java` |
| 写入编排 | `app/app-platform-data-virtualization/core/src/main/java/ai/platform/aiassit/data/virtualization/core/execution/VirtualDataCommandService.java` |
| 联调请求样例 | `http/dbEngine/data-virtualization.http` |

## 3. 验收准备

1. 在 DB Engine 使用的管理数据库执行 `data_virtualization_init.sql`。
2. 确认真实数据源、真实表和真实字段已经进入 `db_*_meta` 物理目录且处于启用状态。
3. 启动 DB Engine，确认新模块的 Mapper 和 Controller 已被扫描。
4. 使用 `http/dbEngine/data-virtualization.http` 从真实表创建草稿。
5. 根据业务语义调整虚拟字段、绑定、变换规则、端口和虚拟关系，再执行校验与发布。

## 4. 建议验收路径

### 4.1 单表闭环

1. 从一张真实表创建虚拟实体草稿。
2. 调用目录校验和发布接口。
3. 调用 Explain，确认响应只暴露虚拟请求，计划中物理任务的路由原因符合预期。
4. 执行 LIST、GET、COUNT、AGGREGATE。
5. 执行单绑定 INSERT、UPDATE、DELETE，并确认所有值都通过参数绑定。

### 4.2 字段语义拆分与合并

1. 配置 `json_extract`，验证一个 JSON 物理字段生成多个虚拟字段。
2. 配置 `text_concat`，验证多个物理字段生成一个只读虚拟字段。
3. 对同一物理端口配置两条 `enum_map` 读取规则，验证可生成两种虚拟语义。
4. 使用 preview 和 lineage 检查端口、变换结果及双向血缘。
5. 对不可逆规则尝试写回，预期返回 `FIELD_TRANSFORM_WRITE_UNSUPPORTED`。

### 4.3 多数据源

1. 为同一虚拟实体配置多个 HASH/LIST/RANGE 主绑定。
2. 用等值、IN 和范围条件检查分片裁剪。
3. 配置只读副本，分别使用 STRONG 与 EVENTUAL 检查主副本选择。
4. 配置 `vd_relation` 后，使用 `relationCodes` 和 `relationCode.fieldCode` 执行跨源关联。
5. 降低 `maxPhysicalTasks` 或 `maxScanRows`，确认扇出、扫描和 Hash Join 超限时被拒绝。

## 5. 安全边界

- 虚拟请求不接收真实表名、`sourceKey` 或任意 SQL。
- 标识符只来自已发布目录且经过白名单格式校验；业务值始终参数绑定。
- 跨源关系只支持已配置的等值字段组，当前执行语义为受预算保护的 LEFT Hash Join。
- MongoDB 虚拟关系型计划和 SQL 写入当前明确拒绝。
- `READ_YOUR_WRITES` 在没有副本水位证明时选择 PRIMARY。
- 当前物理执行器每条命令独立连接，因此只承诺单物理命令的本地原子性。
- `BEST_EFFORT` 返回每个任务的成功/失败状态，但不提供无业务补偿定义的自动 Saga；`ATOMIC` 跨绑定请求明确拒绝。
- 幂等缓存当前为进程内能力；跨实例、重启后幂等需要后续接入持久化幂等存储。

## 6. 构建验收命令

```bash
mvn -pl app/app-platform-data-virtualization/core -am test
mvn -pl app/app-platform-db-engine/boot -am clean compile -DskipTests
mvn clean compile -DskipTests
codegraph sync
codegraph status
```

构建中的 `app-gateway-core` 重复依赖声明和缺失 `nexus` profile 警告是仓库已有问题，不属于本次变更。
