# DbQueryApi 基于虚拟表层的架构设计与实施方案

> 状态：待实施
> 日期：2026-07-13
> 目标：保留 DbQueryApi 已发布的六类查询契约，将其底层实现从“调用方指定物理表并动态拼接 SQL”迁移为“调用方只使用已发布虚拟实体和虚拟字段，由虚拟表层完成目录解析、权限校验、路由、物理计划、执行和结果合并”。
> 关联文档：[跨数据源虚拟数据模型与执行编排方案](cross-source-virtual-data-model-design.md)

## 1. 结论

本次改造采用“兼容门面 + 虚拟查询内核 + 物理执行端口”的架构：

1. DbQueryApi 的 URL、既有请求字段、响应 DTO 和 R 包装在第一阶段保持不变；允许对 Ext DTO 做非必填的加法扩展，继续服务现有前端 Runtime、存量 Render JSON 和仓库外 Feign 调用。
2. DbQueryApi 不再直接查询物理表。请求中的 model 在新实现中只表示虚拟实体 entityCode，字段只表示虚拟字段编码。
3. ext.relations 不再是调用方提交任意物理表、ON 条件和关联表过滤的入口；关联必须预先配置为虚拟关系并发布。
4. DbQueryApi 只作为旧协议兼容入口。新的内部调用优先使用 VirtualDataApi 和 VirtualQueryRequest。
5. DbQueryController 不通过 Feign 调用本服务，而是通过同进程的 VirtualQueryGateway 调用虚拟查询内核。
6. commons-lib 中的虚拟化模块不再直接依赖 app 下的 DB Engine 实现。虚拟层定义物理目录、物理查询和能力发现端口，由 app-platform-db-engine 的适配模块实现。
7. query.list 必须返回精确 total。虚拟计划需要升级为“分页数据计划 + 总数计划”，不能继续用预算内扫描行数冒充总数。
8. 无法在预算内保证精确过滤、排序、关联、聚合或分页时，明确失败，不静默回退到旧物理 SQL，也不返回截断结果。
9. query.get、query.list、query.count、query.aggregate 直接映射为虚拟查询；query.tree 和 query.pivot 在虚拟查询结果之上进行受预算保护的结构化后处理。
10. 迁移过程按虚拟实体白名单灰度。目录未发布或映射失败时不允许自动回退物理表查询，避免绕开虚拟目录、字段权限和行级策略。

目标调用链如下：

~~~mermaid
flowchart LR
    A["调用方 / Feign / 前端 Runtime"] --> B["DbQueryApi"]
    B --> C["DbQueryController"]
    C --> D["DbQueryCompatibilityFacade"]
    D --> E["LegacyRequestTranslator"]
    E --> F["VirtualQueryGateway"]
    F --> G["目录快照与权限策略"]
    G --> H["逻辑计划与精确性判定"]
    H --> I["路由与物理计划"]
    I --> J["PhysicalQueryPort"]
    J --> K["DB Engine Adapter"]
    K --> L["DbQueryExecutionPipeline"]
    L --> M["数据库执行器"]
    M --> N["虚拟结果合并"]
    N --> O["LegacyResponseAssembler"]
    O --> A
~~~

## 2. 背景与现状

### 2.1 DbQueryApi 当前是物理表动态查询

当前 DbQueryServiceImpl 的核心语义是：

- model 直接作为真实表名。
- 请求不携带 sourceKey，统一使用系统配置中的默认数据源。
- fields、filter_dict、filterExpr、sorts 和 relations 被直接编译为 SQL。
- relations 由调用方提交物理表名、Join 类型、ON 字段映射和关联过滤条件。
- query.list 执行一条分页 SQL 和一条 COUNT SQL。
- 查询计划虽然支持 parameters，但旧实现主要把值拼入 SQL 文本。
- DB 执行策略目前主要是日志占位，还没有虚拟实体、字段和行级权限。

这条链路绕过虚拟目录，调用方能够直接感知物理表、物理字段和物理关系，不适合作为后续多数据源、分片、字段变换和统一权限的公共入口。

### 2.2 虚拟表层当前已经具备的能力

commons-lib/data-virtualization 已具备以下基础：

- 已发布虚拟实体和目录版本解析。
- 虚拟字段白名单和字段变换规则。
- Filter AST、LIST、GET、COUNT、AGGREGATE 逻辑计划。
- SINGLE、HASH、LIST、RANGE 绑定路由。
- PRIMARY、REPLICA 和一致性选择。
- 多物理任务并发执行和执行预算。
- 参数化物理查询。
- 物理字段到虚拟字段的读取变换。
- 预定义虚拟关系和受预算保护的应用层 LEFT Hash Join。
- Explain、稳定错误类别和基本执行摘要。

### 2.3 不能直接替换的关键缺口

现有虚拟实现尚不能直接承接 DbQueryApi：

| 缺口 | 当前影响 | 本方案要求 |
|---|---|---|
| 模块依赖方向 | virtualization-core 直接依赖 db-engine-core/meta 和 app-platform-chat-api；反向接入会产生循环或职责倒置 | 抽取物理、知识库和文本生成端口及 app 适配模块 |
| model 身份 | 旧接口是物理表名，虚拟层是 entityCode | 做目录迁移并重新定义 model 语义 |
| 精确分页 | 当前主要扫描后在内存排序分页 | 增加可下推的数据计划和独立总数计划 |
| total | 当前 LIST total 是预算内物化结果数量 | DbQuery v1 必须返回精确总数 |
| relation | 旧接口允许任意物理 Join；虚拟层只认 relationCode | 关联配置化，补充关系语义和兼容映射 |
| 过滤操作符 | 虚拟层缺少 prefix_like、suffix_like 等兼容语义 | 扩展强类型操作符，禁止静默降级 |
| filterExpr | 旧接口是条件 key 的布尔表达式 | 翻译成 Filter AST，不再生成 SQL |
| 聚合 | 缺少 HAVING、聚合别名排序和完整分片合并 | 扩展聚合逻辑计划和合并计划 |
| TREE / PIVOT | 虚拟 QueryType 尚未定义 | 通过标准虚拟查询加后处理器实现 |
| 响应结构 | 虚拟响应为 records/total，关系字段为点路径 | 兼容层恢复旧响应和嵌套对象 |
| 目录不可变性 | 子配置可直接改，缓存版本与内容可能漂移 | 发布快照必须真正不可变 |
| 权限 | 虚拟层尚无实体、字段、行级策略 | 逻辑计划前完成授权与强制过滤注入 |

## 3. 设计目标与非目标

### 3.1 目标

- 所有 DbQueryApi 查询都必须先解析已发布虚拟目录。
- 调用方不再控制真实数据源、真实表、真实字段和 SQL Join。
- 保持现有六个接口的主要请求和响应形状，降低前端与存量协议迁移成本。
- 以一个统一虚拟查询内核承载目录、路由、字段变换、关系、分页和聚合。
- 保证 query.list 的分页和 total 语义准确。
- 保证所有业务值使用参数绑定。
- 建立明确的 commons 能力层与 app 服务适配层边界。
- 支持灰度、影子比对、审计和安全回滚。

### 3.2 非目标

- 不继续支持调用方任意提交真实表 Join。
- 不允许虚拟目录缺失时自动查询同名物理表。
- 不在本次方案中实现任意 SQL、任意表达式或脚本执行。
- 不承诺无限制跨数据源 Join、无限制深分页或无预算全量扫描。
- 不把 DbQueryApi 扩展为新的高级虚拟查询协议；高级能力继续进入 VirtualDataApi。
- 不在第一阶段实现 MongoDB 上的关系型虚拟查询。
- 除 4.6 明确列出的正确性修正外，不让当前未生效字段在 v1 中突然改变行为。

## 4. 核心架构决策

### 4.1 DbQueryApi 定位为兼容门面

DbQueryApi 保留现有六个路由：

- POST /api/v1/query.get
- POST /api/v1/query.list
- POST /api/v1/query.count
- POST /api/v1/query.aggregate
- POST /api/v1/query.tree
- POST /api/v1/query.pivot

它们不再拥有独立查询内核，只负责：

1. 兼容旧 DTO。
2. 翻译为标准虚拟请求。
3. 调用 VirtualQueryGateway。
4. 将标准虚拟响应组装为旧响应形状。
5. 将虚拟错误映射为统一 Web 错误。

### 4.2 model 只表示虚拟实体

切换后：

~~~text
DbQueryRequest.model == VirtualQueryRequest.entityCode
~~~

不允许以下行为：

- 把 model 直接放入 FROM。
- 根据 model 猜测 sourceKey。
- 在目录不存在时查询同名物理表。
- 根据调用方传入的 relation.model 拼接 SQL。

为了兼容旧调用，第一阶段不强制修改 DTO 字段名，但文档和前端 Schema 必须明确 model 已是“虚拟模型编码”，不再是物理表名。后续 v2 可以新增 entityCode 并废弃 model。

### 4.3 关联只来自已发布虚拟目录

旧 ext.relations 中的 key 可在迁移期映射为 relationCode。model、on 和 type 不再生成运行时 Join，但不能被静默忽略；它们用于和已发布关系做迁移一致性校验。filter 是当前有效语义，需要翻译为受控的关系域 Filter AST。

- model/on/type 缺省：按已发布关系执行。
- model/on/type 已提供且与迁移清单或已发布关系不一致：拒绝请求。
- filter 可映射为目标虚拟字段和白名单操作符：生成 RelationRequestFilter，并保持原 JOIN ON 作用域。
- filter 无法映射：返回 LEGACY_RELATION_FILTER_NOT_MIGRATED，不能丢弃后继续执行。

兼容策略：

1. 第一阶段直接用 ext.relations[].key 解析 relationCode，不要求旧调用方增加字段。
2. 可在 DbQueryGetExt、DbQueryExt、DbQueryCountExt、DbQueryAggregateExt、DbQueryTreeExt 和 DbQueryPivotExt 中加法新增可选 relationCodes；这是兼容扩展，不删除或改名既有字段。
3. 新调用方优先显式提交 relationCodes 和标准 relationFilters。
4. model/on/type 只做一致性校验，不能作为 SQL 标识符或 Join 定义进入执行器。
5. relations[].filter 翻译为关系作用域过滤；目录中的固定关系过滤与请求过滤使用 AND 合并。
6. 无法与已发布虚拟关系匹配时请求失败。
7. 稳定后在 v2 移除旧 relations 的运行时兼容。

### 4.4 不通过 Feign 自调用

DbQueryApi 与 VirtualDataApi 当前由同一个 DB Engine 进程装配。DbQueryController 不应通过 Feign 再调用 /internal/v1/virtual-data/query，否则会引入：

- 额外网络开销。
- 自调用的鉴权和上下文透传问题。
- 服务注册依赖。
- 调试链路复杂化。
- 本地事务和请求取消信号丢失。

内部统一使用 Java 本地契约 VirtualQueryGateway。VirtualDataApi Controller 和 DbQueryApi Controller 都调用该 Gateway。

### 4.5 精确优先，不能静默近似

DbQuery v1 没有 approximate 字段，调用方默认 total 和排序分页是准确结果。因此：

- 可以证明精确：执行并返回。
- 需要全量本地计算但在预算内：执行并返回。
- 无法在预算内证明精确：返回明确错误。
- 不允许返回 maxScanRows 范围内的局部 total。
- 不允许排序后截断造成“有数据但翻页为空”。

### 4.6 兼容边界与正确性修正

迁移不是对当前实现逐行复刻。以下内容必须保持兼容：

- URL、HTTP 方法、请求与响应 DTO、R 包装和 JSON 字段名。
- page/page_size 默认值及最大页大小。
- LIST 的空 summary、无分组 COUNT 的 records/summary 形状。
- 明细关系字段的嵌套结果结构。

以下内容属于明确的治理或正确性修正：

- model 从物理表名收敛为虚拟实体编码。
- 任意物理 relation 改为已发布 relationCode。
- 分组查询的 total 改为分页前的全部分组数。
- TREE 的重复节点、循环和深度问题改为显式校验。
- PIVOT 的 topN 与时间粒度从隐式或未生效行为改为明确语义。

这些修正必须写入 golden contract 基线的差异清单，并在 SHADOW 报告中区分“预期差异”和“非预期差异”，不能把行为变化隐藏在内部重构中。

## 5. 目标模块结构与依赖方向

### 5.1 推荐目录

~~~text
commons-lib/data-virtualization/
├── api
├── spi
├── data
└── core

app/app-platform-db-engine/
├── api
├── meta
├── core
├── data-virtualization-adapter
├── db-executor-spi
├── db-executor-impl
└── boot
~~~

建议新增两个模块：

1. commons-lib-data-virtualization-spi
   - 定义物理目录、物理查询、物理写入、数据源能力、知识库文档和文本生成端口。
   - 使用数据库无关 DTO，不引用 db-engine 类。

2. app-platform-db-engine-data-virtualization-adapter
   - 承载 DbQueryApi 兼容门面。
   - 承载 VirtualDataApi 与虚拟目录管理 HTTP Controller。
   - 实现虚拟层需要的物理目录和物理执行端口。
   - 实现 KnowledgeDocumentPort 和 TextGenerationPort，内部适配 AiKnowledgeApi、AiTextGenerationApi。
   - 由 boot 装配，不承载 Planner、Router、Transform 等虚拟核心逻辑。

### 5.2 依赖图

~~~mermaid
flowchart TD
    BOOT["db-engine-boot"] --> DBADAPTER["db-engine-data-virtualization-adapter"]
    BOOT --> DBCORE["db-engine-core"]
    BOOT --> VCORE["commons virtualization-core"]
    BOOT --> EXEC["db-executor-impl"]

    DBADAPTER --> DBAPI["db-engine-api"]
    DBADAPTER --> DBCORE
    DBADAPTER --> DBMETA["db-engine-meta"]
    DBADAPTER --> VAPI["commons virtualization-api"]
    DBADAPTER --> VSPI["commons virtualization-spi"]
    DBADAPTER --> CHATAPI["chat-api / service-ai-api"]

    VCORE --> VAPI
    VCORE --> VSPI
    VCORE --> VDATA["commons virtualization-data"]
    VDATA --> VAPI

    DBCORE --> DBAPI
    DBCORE --> DBMETA
    DBCORE --> DBSPI["db-executor-spi"]
~~~

强制规则：

- virtualization-core 不得 import app-platform-db-engine 包。
- virtualization-core 不得 import app-platform-chat-api 或 service-ai-api 包。
- db-engine-core 不得依赖 virtualization-core。
- app 适配模块可以同时依赖 db-engine 和 virtualization 的 API/SPI，但不承载虚拟领域逻辑。
- boot 只做装配。
- Feign 契约与本地 Gateway 分离，不能用 Feign 接口代替进程内应用接口。

### 5.3 Controller 的归属

既然 app 目录负责提供程序服务，所有 HTTP Controller 应归属于 app 适配模块，包括：

- DbQueryController。
- VirtualDataExecutionController。
- 虚拟实体、字段、绑定、变换规则和关系管理 Controller。
- 虚拟目录发布、Explain、预览等接口 Controller。

commons-lib 中保留：

- 请求和响应模型。
- 应用用例接口。
- 目录、规划、路由、变换、执行与合并实现。
- 配置持久化能力。
- 对外依赖端口。

为保证 adapter 只编译依赖 virtualization-api/spi，现有 Controller 不能原样搬迁。需要把查询、目录管理、发布、变换管理、知识同步和说明生成所需的用例接口及 Web DTO 提升到 virtualization-api；core 实现这些用例，data 实体与 Repository 不得出现在 Controller 方法签名中。这样 adapter 不需要直接依赖 virtualization-core/data，运行时实现由 boot 注入。

## 6. 关键接口设计

### 6.1 虚拟查询本地入口

~~~java
public interface VirtualQueryGateway {
    VirtualQueryResponse query(VirtualQueryRequest request);
    VirtualExplainResponse explain(VirtualQueryRequest request);
}
~~~

VirtualDataQueryService 实现该接口。两个 HTTP 入口都只依赖接口。

管理类 Controller 同样只依赖 api 中的本地用例接口，至少拆为：

- VirtualCatalogAdminGateway：实体、字段、绑定、关系、发布和撤销发布。
- VirtualTransformAdminGateway：变换规则、预览和血缘。
- VirtualKnowledgeGateway：知识预览、状态与同步。
- VirtualDescriptionGateway：说明生成。

这些 Gateway 的输入输出均为 api DTO；core/data 中的 Service、Entity、Mapper 只作为实现细节。

### 6.2 物理查询端口

~~~java
public interface PhysicalQueryPort {
    PhysicalQueryResult query(PhysicalQueryCommand command);
}

public record PhysicalQueryCommand(
        String requestId,
        String planId,
        String taskId,
        String sourceKey,
        PhysicalQuerySpec querySpec,
        int maxRows,
        int timeoutMs
) {
}
~~~

PhysicalQuerySpec 应表达数据库无关语义：

- 物理表身份。
- 投影字段。
- 参数化过滤 AST。
- 排序。
- 分组和聚合。
- offset/limit。
- Join 能力受控描述。

执行路径固定为：commons 生成 PhysicalQuerySpec；DbEnginePhysicalQueryAdapter 将其映射为 DB Engine 内部 DbQueryPlan；DbQueryExecutionPipeline 继续负责方言渲染、策略、审计和执行。commons 与 SPI 不暴露 BoundSql，也不新增绕过 Pipeline 的 executeBoundSql 路径，避免重复渲染或丢失现有策略链。

PhysicalQueryResult 除 records 和 aggregate values 外，还必须返回：

- exhausted：本任务候选域是否已经完整读完。
- truncated：是否因 maxRows、预算或 Provider 限制被截断。
- scannedRows：物理扫描或读取行数。
- nextCursor：Provider 支持游标续扫时返回。

只有 exhausted = true 且 truncated = false，才可以用本地物化结果证明精确 total。

### 6.3 物理目录端口

~~~java
public interface PhysicalCatalogPort {
    PhysicalTableDefinition requireTable(long tableMetaId);
    List<PhysicalFieldDefinition> fields(long tableMetaId);
    PhysicalSourceCapabilities capabilities(String sourceKey);
}
~~~

VirtualEntityDraftFactory 和 CatalogValidator 改为依赖该端口，不再直接使用 DbTableMetaMapper 与 DbTableFieldMetaMapper。

### 6.4 虚拟权限端口

~~~java
public interface VirtualDataPolicyPort {
    VirtualPolicyDecision authorize(VirtualPolicyRequest request);
}
~~~

返回内容至少包括：

- 是否允许访问实体。
- 允许投影的字段集合。
- 允许排序、聚合的字段集合。
- 必须注入的行级 Filter AST。
- 最大页大小和最大执行预算。

权限判定发生在逻辑计划编译之前。系统注入的行级条件与用户条件使用 AND 合并，调用方不能覆盖。

### 6.5 外部知识与文本生成端口

~~~java
public interface KnowledgeDocumentPort {
    KnowledgeDocumentPage list(KnowledgeDocumentQuery query);
    KnowledgeDocumentRef upsert(KnowledgeDocumentCommand command);
    void delete(KnowledgeDocumentDeleteCommand command);
}

public interface TextGenerationPort {
    TextGenerationResult generate(TextGenerationCommand command);
}
~~~

VirtualKnowledgeService 和 VirtualDescriptionService 仅依赖上述 SPI。app adapter 分别调用 AiKnowledgeApi 与 AiTextGenerationApi，并在边界处完成 R 包装、外部 DTO 和异常的转换。这样 commons-lib 不再通过 app-platform-chat-api 间接绑定具体应用服务。

## 7. 端到端执行流程

### 7.1 公共流程

~~~mermaid
sequenceDiagram
    participant Caller as 调用方
    participant Controller as DbQueryController
    participant Facade as CompatibilityFacade
    participant Gateway as VirtualQueryGateway
    participant Catalog as CatalogService
    participant Policy as PolicyPort
    participant Planner as Planner
    participant Router as BindingRouter
    participant Physical as PhysicalQueryPort
    participant DB as DB Executor

    Caller->>Controller: DbQuery 请求
    Controller->>Facade: 旧 DTO
    Facade->>Facade: 归一化与 Filter AST 翻译
    Facade->>Gateway: VirtualQueryRequest
    Gateway->>Catalog: 固定已发布目录版本
    Gateway->>Policy: 实体/字段/行级授权
    Policy-->>Gateway: 强制过滤与预算
    Gateway->>Planner: 逻辑计划
    Planner->>Router: 绑定、分片和副本选择
    Router-->>Planner: 路由决策
    Planner->>Physical: data branch 与 relation-aware count branch
    Physical->>DB: 参数化物理请求
    DB-->>Physical: 物理结果
    Physical-->>Gateway: 标准结果块
    Gateway->>Gateway: 变换、Join、精确计数、合并、分页
    Gateway-->>Facade: VirtualQueryResponse
    Facade->>Facade: 旧响应整形
    Facade-->>Caller: R 包装响应
~~~

### 7.2 query.list 专用流程

query.list 必须生成 VirtualListExecutionPlan：

~~~text
VirtualListExecutionPlan
├── catalogRef
├── dataPlan
│   ├── physicalDataTasks
│   ├── mergeStrategy
│   └── pageStrategy
├── countPlan
│   ├── physicalCountTasks
│   └── countMergeStrategy
├── relationPlan
├── transformPlan
├── exactnessDecision
└── executionBudget
~~~

执行顺序：

1. 固定目录版本。
2. 将旧请求翻译为标准虚拟语义。
3. 注入字段与行级权限。
4. 判定过滤、排序、关系和字段变换能否下推。
5. 生成数据计划。
6. 生成同语义的总数计划；关系或本地变换影响筛选时，生成 relation-aware semi-join/count 或完整候选域计数，而不是主表 COUNT。
7. 并行执行可并行的物理任务。
8. 先字段归一，再执行必要的本地过滤、关系合并和按主实体稳定标识去重计数。
9. 校验参与本地精确计数的物理结果 exhausted = true 且 truncated = false。
10. 执行全局稳定排序与分页。
11. 组装 list、pageInfo 和 summary。

## 8. 旧协议到虚拟协议的映射

### 8.1 query.list

| DbQueryListRequest | VirtualQueryRequest | 规则 |
|---|---|---|
| title | traceLabel | 仅用于日志和链路标识，不参与查询 |
| model | entityCode | 必须对应已发布虚拟实体 |
| filter_dict | filter | 每个条件翻译为 Predicate |
| filterExpr | filter | 解析为 AND/OR 组合树 |
| ext.fields | fields | 只允许虚拟字段或 relationCode.fieldCode |
| ext.sorts | sorts | 字段必须通过目录和权限校验 |
| ext.relations | relationCodes + relationFilters | key 映射关系；model/on/type 校验；filter 翻译为关系域 AST |
| page | page.number | 默认保持 1 |
| page_size | page.size | 默认保持 10，最大值按旧接口先保持 1000 |

### 8.2 filter_dict 与 filterExpr

LegacyRequestTranslator 需要提供独立的 LegacyFilterAstParser：

1. scalar 值等价于 EQ。
2. 对象结构读取 op 和 value。
3. filterExpr 只引用 filter_dict 中的 key。
4. 保持旧语义：表达式必须恰好引用全部 key，缺少或多余都失败。
5. 只支持 AND、OR 和括号；不把字符串直接传给 SQL。
6. relationCode.fieldCode 可作为条件 key，但 relationCode 必须显式声明。

操作符映射：

| 旧 op | 虚拟操作符 |
|---|---|
| eq | EQ |
| ne / neq | NE |
| gt | GT |
| gte / ge | GTE |
| lt | LT |
| lte / le | LTE |
| like | LIKE（保持旧 `%value%` 语义） |
| prefix_like | STARTS_WITH |
| suffix_like | ENDS_WITH |
| in | IN |
| not_in | NOT_IN |
| is_null | IS_NULL |
| is_not_null | IS_NOT_NULL |

虚拟枚举需要新增 STARTS_WITH 和 ENDS_WITH，不能把它们静默转换为普通 contains。

### 8.3 字段编码迁移

现有虚拟草稿工厂会把部分物理 snake_case 转成 camelCase，而存量 DbQuery Schema 很可能直接使用物理字段名。迁移时应二选一：

1. 优先方案：批量更新 Render JSON 和调用方，使其使用正式虚拟字段编码。
2. 兼容方案：迁移工具创建虚拟字段时保留旧字段编码，后续再通过显式版本升级统一命名。

不建议长期维护隐式字段别名猜测。字段映射必须来自目录配置或一次性迁移清单。

### 8.4 关系字段与响应嵌套

虚拟层内部继续使用 relationCode.fieldCode 点路径，兼容响应层负责将明细型结果恢复为嵌套对象：

~~~json
{
  "orderId": 1001,
  "customer.name": "Alice"
}
~~~

转换为：

~~~json
{
  "orderId": 1001,
  "customer": {
    "name": "Alice"
  }
}
~~~

当某个关联对象的全部字段都为 null 时，整个关联对象设为 null。该规则只用于：

- query.get
- query.list
- query.tree 的 node.data

聚合和透视结果继续保持扁平。

投影必须显式：声明 relationCode 只表示本次查询允许使用该关系，不代表自动投影远端全部字段。为保持旧行为：

- ext.fields 为空时只返回主虚拟实体字段。
- 只有 ext.fields 中显式出现 relationCode.fieldCode 时才读取并返回对应远端字段。
- 关系仅用于过滤或存在性判断时，不得把远端字段附带到响应中。
- Planner 应按实际需要补充内部 Join key，但 LegacyResponseAssembler 必须移除这些内部字段，避免数据泄露。

### 8.5 六类接口字段兼容矩阵

| 接口 | 字段 | 当前行为 | 虚拟化目标 |
|---|---|---|---|
| 全部 | title | 基本不参与执行 | 作为 traceLabel，不影响语义 |
| 全部 | model | 物理表名 | 已发布 entityCode；缺失目录即失败 |
| 全部 | filter_dict / filterExpr | 生成主表或关系表 WHERE | 翻译为虚拟 Filter AST；保持 key 全引用校验 |
| GET | id | 固定匹配物理 id | 映射单一虚拟主键；复合主键用 filter_dict |
| GET/LIST | ext.fields | 投影主表或关系字段；空值为主表全部字段 | 只投影虚拟字段；空值仅为主实体字段 |
| GET/LIST | ext.sorts | SQL ORDER BY | 虚拟排序并追加稳定 tie-breaker |
| 全部 | ext.relations | 动态物理 Join，filter 位于 ON | key 映射 relationCode；model/on/type 校验；filter 翻译为关系域 AST |
| COUNT/AGGREGATE | dimensions.field/alias | GROUP BY 与别名 | 强类型 groupBy 与显式别名 |
| COUNT/AGGREGATE/PIVOT | metrics.field/func/alias | 聚合表达式 | 白名单 AggregateFunction 与别名 |
| COUNT/AGGREGATE/PIVOT | having | 聚合别名过滤 | HAVING AST；只允许分组字段或聚合别名 |
| COUNT/AGGREGATE | sorts | 聚合别名排序 | 分组字段或聚合别名排序 |
| COUNT/AGGREGATE | page/page_size | 聚合结果分页 | 保留；新增精确分组 total 计划 |
| COUNT/AGGREGATE | ext.time_grain | 当前未生效 | v1 继续忽略并告警；v2 使用 TimeBucket |
| COUNT/AGGREGATE | ext.top_n | 当前未生效 | v1 继续忽略并告警；v2 定义与分页的优先级 |
| TREE | fields/sorts | 生效 | 翻译为虚拟投影和排序 |
| TREE | metrics/having | 当前未生效 | v1 继续忽略并告警；若需要聚合树进入 v2 |
| TREE | ext.id_field/parent_field/label_field/root_value | 生效 | 映射虚拟字段并保持组树语义 |
| TREE | ext.children_field | 当前未生效，响应固定 children | v1 继续固定 children；动态字段进入 v2 |
| TREE | ext.max_depth | 当前未生效 | 作为安全上限启用属于正确性修正，纳入预期差异 |
| PIVOT | rows/columns/metrics/having | 生效 | 翻译为虚拟聚合后透视 |
| PIVOT | ext.fill_value | 生效 | 保持缺失单元填充值 |
| PIVOT | ext.top_n | 当前作为聚合 page_size | 改为明确的透视分组/列限制，纳入预期差异 |
| PIVOT | ext.time_grain | 当前未生效 | v1 明确拒绝非空值；v2 使用 TimeBucket |

“继续忽略”只用于保持已发布但未生效字段的 v1 行为，同时必须记录弃用告警；不能在 Planner 中半生效。安全修正或语义修正必须进入 SHADOW 预期差异清单。

## 9. 六类接口的实现方案

### 9.1 query.get

翻译为 QueryType.GET。

规则：

- ext.fields、ext.sorts、关系和过滤按通用规则翻译。
- request.id 不再硬编码为物理字段 id。
- 当目录只有一个虚拟主键字段时，id 翻译为该虚拟主键的 EQ 条件。
- 复合主键实体使用 id 简写时明确失败，调用方必须使用 filter_dict。
- GET 最多返回一条，但如果过滤结果不唯一，可按明确排序后取第一条。

### 9.2 query.list

翻译为 QueryType.LIST，并设置 exactTotal=true。

返回：

~~~text
records -> list
total -> pageInfo.total
request.page -> pageInfo.page
request.page_size -> pageInfo.size
summary -> summary
~~~

兼容层继续保持当前 LIST 行为：summary 返回空对象，不因为接入虚拟层而隐式增加聚合摘要。

单绑定且过滤、排序可下推时：

- 数据任务下推 WHERE、ORDER BY、OFFSET、LIMIT。
- 总数任务下推相同 WHERE 的 COUNT。
- 自动追加虚拟主键作为稳定排序 tie-breaker。

多分片时：

- 每个分片下推排序并读取 offset + limit 个候选。
- 使用 k-way merge 执行全局排序。
- 全局截取目标页。
- 每个分片执行 COUNT，最后求和。
- AVG 等聚合不能对分片平均值再次求平均，必须合并 SUM 与 COUNT。

包含本地变换或跨源关系时：

- Planner 先计算候选扫描预算和精确性。
- 在预算内扫描、变换、Join、排序和分页。
- 超出预算直接返回 PLAN_EXACTNESS_UNPROVABLE 或 PLAN_BUDGET_EXCEEDED。

### 9.3 query.count

区分两类语义：

1. dimensions 和 metrics 均为空：
   - 翻译为 QueryType.COUNT。
   - 为保持现有响应契约，返回 records = [{"count": total}]、summary = {"count": total}，同时设置 total、page 和 page_size。

2. 存在 dimensions 或 metrics：
   - 翻译为 QueryType.AGGREGATE。
   - dimensions 映射为 groupBy。
   - metrics 映射为 aggregates。
   - having 映射为聚合结果 Filter AST。
   - sorts 可以引用分组字段或聚合别名。
   - 返回 records、total、summary。

需要补充强类型 GroupBy 定义，以保留 dimension.alias，而不是只使用字符串字段列表。

### 9.4 query.aggregate

与分组 query.count 共用聚合计划，但保持独立兼容响应类型。

需要支持：

- COUNT、SUM、MIN、MAX、AVG 白名单。
- 聚合别名。
- HAVING AST。
- 聚合别名排序。
- 分组分页。
- 无分组且只有一条聚合结果时设置 summary。
- 目标语义下，分组 total 表示全部分组数量，不是当前页记录数。当前实现只返回当前页行数，这是需要在契约基线中显式标记的正确性修正。

旧 DTO 中已存在但当前未真正生效的扩展字段，不应在 v1 切换时突然启用；需要单独版本说明。

### 9.5 query.tree

树结构是虚拟 LIST 查询后的兼容后处理，不要求虚拟内核新增通用 TREE QueryType。

流程：

1. 将 idField、parentField、labelField 和显式 fields 加入必需字段。
2. 执行不分页的虚拟 LIST，仍受 maxScanRows 和超时预算保护。
3. 根据虚拟字段值构建节点索引。
4. 检测重复 id、循环父子关系和超过 maxDepth。
5. parent 不存在的节点按旧行为作为根节点。
6. 组装 DbQueryTreeNode。

默认字段应迁移为虚拟字段语义。不能继续默认假设物理列 parent_id；默认值应由虚拟目录或兼容配置确定。

childrenField 在当前响应 DTO 中没有动态字段承载能力，第一阶段继续输出固定 children；若要支持动态名称，应进入 v2 协议。

### 9.6 query.pivot

透视查询通过虚拟 AGGREGATE 加兼容 PivotAssembler 实现：

1. rows 和 columns 映射为 groupBy。
2. metrics 映射为 aggregates。
3. having 在聚合后执行。
4. 对聚合结果执行行键、列键和指标透视。
5. fillValue 填充缺失单元格。
6. topN 作为明确的透视列或分组限制，不再隐式等价于 page_size。

timeGrain 当前未生效。第一阶段继续明确不支持，调用方应使用目录中预定义的日期粒度虚拟字段；后续可增加受控 TimeBucket 逻辑节点。

## 10. 物理计划与精确分页

### 10.1 从任务列表升级为计划分支

当前 PhysicalExecutionPlan 主要是一组并行任务。目标结构至少需要：

~~~text
VirtualExecutionPlan
├── dataBranch
├── countBranch
├── relationBranches
├── transformPlan
├── mergePlan
├── pagePlan
├── exactnessDecision
└── budget
~~~

每个分支明确：

- 输入目录版本。
- 物理任务依赖关系。
- 下推能力。
- 本地执行步骤。
- 最大候选行数。
- 最大 Join key 数量。
- 内存预算。
- 超时和取消策略。

### 10.2 下推能力

Planner 不能继续只按 identity 规则硬编码判断。FieldTransformer 的 capabilities 应真正参与规划：

- predicatePushdown。
- sortPushdown。
- aggregatePushdown。
- projectionPushdown。
- writePushdown。

只有变换器能够把虚拟谓词安全重写为物理谓词时才允许下推。不能安全重写时进入本地计算和预算判定。

### 10.3 稳定排序

分页必须有稳定顺序：

- 显式 sorts 后自动追加虚拟主键。
- 未指定 sorts 时，优先使用虚拟主键升序。
- 没有虚拟主键且无法构造稳定排序时，第一页可以按 Provider 明确顺序执行；深分页必须拒绝或要求调用方提供稳定排序。
- 多分片合并时使用完整排序键和来源 tie-breaker。

### 10.4 total 语义

query.list 的 total 定义为“应用全部请求过滤和已声明关系条件后，分页前的主虚拟实体记录数”。

关系基数处理：

- 1:1 和 M:1：一条主记录最多对应一个关系对象，total 为主记录数。
- 1:N：可能复制主行，旧嵌套对象语义不明确；第一阶段拒绝用于 DbQuery 明细查询。
- 如果未来支持集合关系，响应和 total 语义必须进入新协议。

总数计划按可下推程度分两种：

- 全部过滤、变换和关系语义可下推时，各物理分支执行等价 COUNT，再按路由语义合并。
- INNER 关系、关系字段 WHERE、跨源 Join 或关系存在性影响主记录集合时，优先生成 relation-aware semi-join/count；按主实体虚拟主键去重后计数，不能使用普通主表 COUNT。
- 存在本地过滤、不可下推变换或无法下推的关系条件时，countBranch 必须切换为 postMergeExactCount，对完整候选域执行本地语义后计数；不能继续使用主表 COUNT。完整候选域超出预算或缺少稳定主实体标识时直接失败。

dataBranch 与 countBranch 必须共享同一目录快照、权限决策、行级过滤和路由决策，防止两个分支的查询语义漂移。

多绑定 COUNT 求和之前还必须证明：

- HASH/LIST/RANGE 分片集合互斥；配置有重叠或无法证明时拒绝求和。
- 同一逻辑分片在 PRIMARY/REPLICA 中只选择一个物理副本，不能把副本当分片重复统计。
- LEFT 关系自身的 ON 域过滤只影响关联对象时不改变主实体 total；INNER、关系字段 WHERE 或存在性条件必须进入 relation-aware count。

### 10.5 数据一致性边界

本文的“精确”表示结果没有被 maxScanRows、局部排序或局部 Join 截断，不等同于跨数据源全局快照隔离。

- 单数据源且 Provider 支持一致性快照时，dataBranch 与 countBranch 应共享事务或 snapshot token。
- 多数据源时至少固定目录版本、策略版本和路由决策；若业务要求严格时间点一致，Provider 必须提供可传播的快照能力，否则明确声明为读已提交一致性。
- 未提供严格快照能力时，并发写入可能使 records 与 total 出现瞬时差异，这与精确计划判定分开记录，并通过 consistencyLevel 暴露在 Explain 与执行摘要中。

## 11. 关系执行方案

### 11.1 关系模型补充

vd_relation 需要增加或通过关系头模型表达：

- joinType。
- cardinality。
- source/target 方向。
- 是否允许明细展开。
- 远端关系过滤。
- 最大键数与最大结果数。
- 固定关系过滤和允许的请求级过滤字段。

DbQuery 兼容入口第一阶段只开放：

- LEFT 或 INNER。
- 1:1、M:1。
- 已发布的等值字段关系。

VirtualQueryRequest 需要增加 relationFilters。它与普通 filter 的区别是作用域：relationFilters 约束关系对象并保持 JOIN ON 语义；普通 filter 引用 relationCode.fieldCode 时属于结果 WHERE 语义，可能改变主实体集合。Planner 必须保留这一区别，尤其不能把 LEFT Join 的 ON 条件错误移动到 WHERE。

### 11.2 同源下推

当两端最终路由到相同 sourceKey 且 Provider 声明支持 Join：

- 由 DB Engine Adapter 生成同源参数化 Join。
- 关系过滤放在 ON 或派生表中，保持 LEFT Join 语义。
- count 按主实体身份执行，避免 1:N 重复计数。

### 11.3 跨源 Join

跨源时：

1. 根据目录统计和预算选择小表侧。
2. 执行小表任务并收集 Join key。
3. 在键数量预算内向大表侧下推 IN 条件。
4. 执行 Hash Join。
5. 应用关系范围内过滤。
6. 再执行全局排序、分页和 total。

预算至少包括：

- 左右候选行数。
- Join key 数量。
- Hash 索引内存。
- Join 结果行数。
- 总执行时间。

## 12. 目录版本与发布一致性

当前仅用 entity 上的 catalogVersion 和本机缓存不足以保证多实例下同一版本内容完全一致。承接公共查询前必须满足：

1. PUBLISHED 配置禁止直接 CRUD。
2. 修改已发布目录必须创建 DRAFT revision。
3. 发布在本地事务内完成校验并固化快照。
4. 运行时以 entityCode + catalogVersion + checksum 读取不可变内容。
5. 发布后发送跨实例缓存失效事件。
6. 在途请求始终持有同一快照。

推荐新增：

~~~text
vd_catalog_snapshot
├── entity_id
├── catalog_version
├── snapshot_content
├── checksum
├── published_at
└── published_by
~~~

如果暂不增加快照表，最低要求是已发布子配置不可修改，并确保发布事务、版本递增和缓存失效原子完成。

## 13. 安全与权限

### 13.1 虚拟权限边界

权限对象只使用：

- entityCode。
- 虚拟字段编码。
- relationCode。
- QueryType。

不向调用方暴露 sourceKey、物理表名、物理字段名和 SQL。

### 13.2 行级过滤

租户、组织、数据域等系统过滤由 VirtualDataPolicyPort 注入：

~~~text
effectiveFilter = AND(userFilter, mandatoryRowFilter)
~~~

系统过滤参与分片裁剪和物理下推。不能在物理执行后才补，否则会导致越权扫描、错误分页和错误 total。

### 13.3 SQL 安全

- 所有值参数化。
- 物理标识符只能来自已发布目录快照。
- 调用方关系定义不能进入 SQL。
- 聚合函数使用枚举白名单。
- Explain 对外隐藏 SQL、凭证和敏感参数。

## 14. 错误处理

建议补充稳定错误类别：

- LEGACY_MODEL_NOT_MIGRATED
- LEGACY_FIELD_NOT_MIGRATED
- LEGACY_RELATION_NOT_MIGRATED
- LEGACY_RELATION_FILTER_NOT_MIGRATED
- FILTER_EXPRESSION_INVALID
- QUERY_SEMANTIC_UNSUPPORTED
- PLAN_EXACTNESS_UNPROVABLE
- UNSTABLE_PAGINATION
- RELATION_CARDINALITY_UNSUPPORTED
- CATALOG_SNAPSHOT_INCONSISTENT

DbQueryCompatibilityFacade 捕获 VirtualDataException，并交给统一业务异常映射器。不能依赖仅覆盖 virtualization controller 包的局部 RestControllerAdvice。

错误响应不得包含：

- 真实 SQL。
- sourceKey。
- 连接信息。
- 物理表和物理字段明细。
- 业务过滤值。

## 15. 可观测性

一次 DbQuery 请求至少记录：

- requestId。
- legacy operation。
- model/entityCode。
- catalogVersion 和 checksum。
- planId。
- 路由到的 binding 数量和物理任务数量。
- 是否执行 count branch。
- 下推步骤与本地步骤。
- 扫描行数、合并行数和返回行数。
- total。
- relationCodes。
- 执行时间与各阶段耗时。
- 兼容字段或弃用字段告警。
- 灰度模式和影子比对结果。

requestId、planId 和 taskId 必须透传至 DbExecutionContext，便于从兼容入口追踪到具体物理任务。

## 16. 缓存策略

### 16.1 目录缓存

- 键：entityCode + catalogVersion + checksum。
- 只缓存不可变快照。
- 发布事件主动失效。
- 设置最大容量和 TTL 兜底。

### 16.2 计划模板缓存

只缓存不包含业务值的计划模板，键至少包括：

- 目录版本和 checksum。
- QueryType。
- 投影结构。
- Filter AST 结构哈希。
- 关系集合。
- 排序、聚合和分页模式。
- 权限策略版本。
- 变换器版本。

分片路由、参数值和副本健康选择每次重新计算。

## 17. 兼容迁移方案

### 17.1 存量盘点

源码检索只能发现前端 Runtime 的 query.list 调用，不能覆盖：

- 数据库中持久化的 Render JSON。
- 仓库外 Feign 调用。
- 人工配置的页面数据源。
- 外部脚本和联调工具。

迁移前必须采集一段时间的 DbQuery 请求摘要，按以下维度聚合：

- model。
- fields。
- relation key/model/on。
- filter op。
- sort。
- operation。
- 调用方。

### 17.2 虚拟目录初始化

对每个存量 model：

1. 确定真实 sourceKey 和物理表。
2. 创建虚拟实体草稿。
3. 为旧字段创建 identity 映射。
4. 根据存量关系创建并人工确认虚拟关系。
5. 校验字段类型、主键、关系基数和权限。
6. 发布目录。
7. 生成旧 model 到 entityCode 的迁移清单。

当表名全局唯一且编码合法时，可以让 entityCode 与旧 model 相同，减少第一阶段调用方修改。不同数据源存在同名表时必须使用带命名空间的 entityCode，并更新调用方。

### 17.3 灰度模式

建议提供：

| 模式 | 行为 |
|---|---|
| LEGACY | 只执行旧物理查询，仅迁移初期使用 |
| SHADOW | 返回旧结果，同时异步执行虚拟查询并比较 |
| VIRTUAL | 只执行虚拟查询 |

灰度键使用 entityCode/model 白名单，不使用全局一次性开关。

SHADOW 比较需要归一化：

- 数字精度。
- 时间格式。
- null。
- 行排序。
- 关系嵌套。
- total。
- summary。
- tree 和 pivot 结构。

SHADOW 只适用于只读查询，且物理旧链路必须设置下线日期。最终状态只保留 VIRTUAL。

### 17.4 禁止 silent fallback

VIRTUAL 模式下以下错误不能回退物理 SQL：

- 目录不存在。
- 目录未发布。
- 字段未映射。
- 关系未迁移。
- 权限拒绝。
- 预算不足。
- 精确性无法保证。

否则同一请求会因目录状态不同而绕过虚拟治理。

## 18. 分阶段实施计划

### 阶段 0：契约基线

交付：

- 为六个旧接口补 golden contract tests。
- 固化请求默认值、错误、响应结构和现有已生效语义。
- 盘点前端 Schema、持久化 Render JSON 和外部调用。

验收：

- 典型请求和边界请求都有可重复基线。
- 能识别每个 model、字段和关系的迁移状态。

### 阶段 1：依赖倒置与应用适配模块

交付：

- 新增 virtualization-spi。
- 新增 db-engine-data-virtualization-adapter。
- 抽取 VirtualQueryGateway。
- 将目录管理、发布、变换、知识和说明生成用例接口及 Web DTO 提升到 virtualization-api。
- 抽取 PhysicalCatalogPort、PhysicalQueryPort、KnowledgeDocumentPort、TextGenerationPort 和能力端口。
- virtualization-core 移除对 db-engine-core/meta/executor、app-platform-chat-api 和 service-ai-api 的直接引用。
- Controller 重写为仅依赖 api 用例接口后移入 app 适配模块。

验收：

- commons-lib 不依赖 app 模块。
- Maven 依赖无环。
- DB Engine Boot 能同时装配虚拟核心和端口实现。
- VirtualDataApi 现有查询可正常执行。

### 阶段 2：query.get/list

交付：

- LegacyRequestTranslator。
- LegacyFilterAstParser。
- relationCode 兼容映射。
- 数据计划 + 总数计划。
- 稳定排序、分页下推和多分片合并。
- LegacyResponseAssembler。
- 实体、字段和行级权限策略。

验收：

- model 不再进入物理 SQL。
- query.list 返回精确 total。
- query.get 正确解析虚拟主键。
- 关系明细恢复旧嵌套结构。
- 无目录、无权限或无法保证精确时明确失败。

### 阶段 3：count/aggregate

交付：

- GroupBy、Aggregate、Having 强类型模型。
- 聚合别名排序。
- 多分片聚合合并。
- 分组总数计划。
- summary 兼容。

验收：

- COUNT/SUM/MIN/MAX/AVG 结果与单库基线一致。
- 分组 total 是全部分组数量。
- HAVING 和 alias sort 生效。

### 阶段 4：tree/pivot

交付：

- TreeAssembler。
- PivotAssembler。
- 重复节点、循环和深度保护。
- Pivot topN、fillValue 和列键兼容。

验收：

- 树节点和 pivot 列结构与旧接口一致。
- 超预算或不支持的 timeGrain 明确失败。

### 阶段 5：目录迁移与切流

交付：

- 批量目录初始化和发布工具。
- SHADOW 比对。
- entity 灰度配置。
- 监控与迁移报表。

验收：

- 存量 model 全部迁移或明确下线。
- 关键查询在灰度期结果一致。
- 默认切为 VIRTUAL。

### 阶段 6：旧物理入口下线

交付：

- 删除或取消注册 DbQueryServiceImpl 的动态 SQL 公共实现。
- 物理查询能力只通过内部 PhysicalQueryPort 使用。
- 更新 DbQuery API 文档、前端 Application 规范和示例。

验收：

- 公共请求无法通过 model 访问任意物理表。
- 仓库中不存在从 DbQuery DTO 直接拼 SQL 的运行路径。

## 19. 代码变更清单

### 19.1 commons-lib/data-virtualization/api

新增或调整：

- VirtualQueryGateway。
- VirtualCatalogAdminGateway、VirtualTransformAdminGateway、VirtualKnowledgeGateway、VirtualDescriptionGateway。
- VirtualQueryRequest 的 exactTotal、having、强类型 groupBy 等能力。
- VirtualQueryRequest 的 relationFilters 及其作用域语义。
- STARTS_WITH、ENDS_WITH。
- 必要的关系语义和稳定错误模型。
- 将 Feign client 与本地用例接口分离。

### 19.2 commons-lib/data-virtualization/spi

新增：

- PhysicalCatalogPort。
- PhysicalQueryPort。
- PhysicalCommandPort。
- PhysicalSourceCapabilityPort。
- VirtualDataPolicyPort。
- KnowledgeDocumentPort。
- TextGenerationPort。
- 数据库无关的物理请求和结果 DTO。

### 19.3 commons-lib/data-virtualization/core

调整：

- VirtualDataQueryService 实现 VirtualQueryGateway。
- VirtualLogicalPlanCompiler 增加权限、HAVING 和精确性输入。
- PhysicalExecutionPlan 拆分 data/count/relation 分支。
- PhysicalPlanGenerator 使用 SPI，不再依赖 DbAccessService。
- 移除直接拼数据库方言的职责。
- ResultFinalizer 支持稳定分页与精确 total。
- CatalogService 读取不可变发布快照。
- Controller 移出 commons。

### 19.4 app/app-platform-db-engine/data-virtualization-adapter

新增：

- DbQueryController。
- DbQueryCompatibilityFacade。
- LegacyRequestTranslator。
- LegacyFilterAstParser。
- LegacyResponseAssembler。
- TreeAssembler。
- PivotAssembler。
- DbEnginePhysicalCatalogAdapter。
- DbEnginePhysicalQueryAdapter。
- AiKnowledgeDocumentAdapter。
- AiTextGenerationAdapter。
- VirtualData Controller 与管理 Controller。
- 虚拟错误到统一 Web 错误的适配器。

### 19.5 app/app-platform-db-engine/core

保留：

- DbQueryExecutionPipeline。
- 数据源解析和 Provider 注册。
- 方言、审计、执行策略和物理执行能力。

调整：

- DbQueryServiceImpl 不再作为公共接口实现。
- 物理执行上下文接收并贯穿 requestId、planId、taskId。
- 增强 Provider 能力声明和语义化物理请求渲染。

### 19.6 app/app-platform-db-engine/boot

- 装配 db-engine-core、virtualization-core 和 adapter。
- 只保留组件扫描、Mapper 扫描、线程池和配置。
- 不写兼容转换或虚拟查询业务逻辑。

### 19.7 前端与文档

需要同步：

- ai-conversation-ui/src/application/schema/db-query.ts：说明 model 是虚拟实体编码。
- resolver 示例：字段和 relation 使用虚拟编码。
- docs/dev-spec/detail/frontend/application.md：删除物理关系直传建议。
- docs/api/db-query-api.md：更新为虚拟兼容门面实现。
- 持久化 Render JSON：批量迁移 model、fields 和 relations。

## 20. 测试策略

### 20.1 兼容契约测试

六个接口分别覆盖：

- 默认值。
- snake_case 与 camelCase JSON 字段。
- scalar filter 和对象 filter。
- filterExpr 的 AND、OR、括号、漏 key、多 key。
- 全部旧操作符和别名。
- relation 嵌套与全 null。
- relation.filter 的 ON 作用域、迁移失败和空投影。
- pageInfo、summary。
- tree 孤儿、重复、循环、深度。
- pivot 多指标、fillValue、topN。
- 错误映射。

### 20.2 虚拟规划单元测试

- entityCode 和目录版本。
- 字段授权和行级过滤注入。
- identity 与非 identity 变换下推。
- 单绑定分页和 count 双计划。
- 多分片稳定排序、k-way merge 和 count 合并。
- 分片互斥校验、PRIMARY/REPLICA 去重和物理结果 exhausted/truncated 判定。
- INNER、关系字段 WHERE、跨源 Join 的 relation-aware exact count。
- HAVING 与聚合别名排序。
- AVG 的 SUM + COUNT 合并。
- exactness 判定。
- 预算拒绝。

### 20.3 集成测试

- 单 MySQL 数据源。
- MySQL 与 JDBC 多数据源。
- 同源关系下推。
- 跨源 M:1 Hash Join。
- 目录发布后多实例缓存一致。
- 请求取消和物理任务超时。
- 权限行过滤影响分片路由和 total。

### 20.4 影子比对

比较：

- 行集合。
- 稳定顺序。
- total。
- 数值与时间类型。
- relation 嵌套。
- 聚合与 summary。
- tree。
- pivot。

## 21. 验收标准

功能验收：

1. DbQueryApi 六个 URL 和响应类型保持可用。
2. query.list 在单绑定和多分片场景返回精确 total。
3. INNER、关系字段 WHERE、跨源关系和本地变换场景要么返回可证明的精确 total，要么明确拒绝。
4. model、fields、sort 和 filter 只能引用已发布虚拟目录。
5. relation 只能引用已发布 relationCode，关系过滤保持 ON/WHERE 作用域。
6. query.get/list 的关联结果保持旧嵌套结构，空投影不会泄露远端字段。
7. count/aggregate 的分组、HAVING、别名排序和 summary 正确。
8. tree/pivot 通过虚拟结果完成，且预算保护生效。

架构验收：

1. commons-lib 不依赖 db-engine、chat、ai-engine 等 app 模块。
2. db-engine-core 与 virtualization-core 无循环依赖。
3. Controller 和外部系统适配在 app 模块。
4. 虚拟核心通过 SPI 使用物理目录与执行能力。
5. 虚拟核心通过 SPI 使用知识库与文本生成能力。
6. adapter Controller 只依赖 api 用例接口和 DTO，不暴露 data Entity。
7. boot 不承载业务逻辑。

安全验收：

1. 调用方不能提交真实表名、sourceKey 或任意 SQL。
2. 目录缺失时不能回退物理查询。
3. 所有业务值参数绑定。
4. 行级策略影响物理计划、分页和 total。
5. 错误和 Explain 不泄露敏感物理信息。

运维验收：

1. requestId、planId、taskId 可贯穿完整链路。
2. 能按 entityCode 灰度和回滚切流配置，但 VIRTUAL 模式不允许物理 fallback。
3. 目录发布跨实例一致。
4. 可以统计影子比对差异和迁移完成率。

## 22. 风险与控制

| 风险 | 控制措施 |
|---|---|
| 存量 model 实际是物理表 | 批量盘点、目录初始化、灰度映射 |
| 持久化 Render JSON 无法通过源码发现 | 运行日志采样和数据库迁移脚本 |
| 旧 relation 是任意物理 Join | 迁移为已发布 relationCode；filter 翻译为关系域 AST，其他字段只校验，无法映射则失败 |
| 多分片分页不稳定 | 强制稳定排序和主键 tie-breaker |
| total 因本地变换或 Join 不准确 | 双计划、精确性判定、超预算失败 |
| 1:N 关系复制主行 | DbQuery v1 首期只支持 1:1/M:1 |
| commons 反向依赖 db/chat/AI app | 物理、知识库、文本生成 SPI 与 app adapter 依赖倒置 |
| 发布配置在相同版本下漂移 | 不可变快照、checksum、跨实例失效 |
| 灰度时双跑增加数据库压力 | 按实体和采样率控制 SHADOW |
| 目录错误时绕过治理 | 禁止 silent fallback |
| 新实现突然启用旧 DTO 未生效字段 | 保持 v1 现状，新增语义走版本化变更 |

## 23. 默认决策

实施时若无新的业务约束，按以下默认值执行：

- DbQueryApi 继续保留，定位为 legacy compatibility facade。
- model 等同 entityCode。
- 新调用优先 VirtualDataApi。
- relation 使用预发布 relationCode。
- query.list total 必须精确。
- 明细关系首期只支持 1:1/M:1。
- 目录缺失不回退物理表。
- tree 和 pivot 使用虚拟查询后的受控后处理。
- commons 定义 SPI，app 提供适配器和 Controller。
- 按 entity 灰度，最终删除公共物理动态 SQL 路径。

## 24. 最终形态

改造完成后，DbQueryApi 的作用只剩协议兼容：

~~~text
旧协议
  -> 虚拟语义翻译
  -> 已发布目录
  -> 权限与行级过滤
  -> 逻辑计划
  -> 路由与物理计划
  -> DB Engine 物理端口
  -> 字段变换与关系合并
  -> 精确分页和总数
  -> 旧响应整形
~~~

物理表查询能力仍保留在 DB Engine 内部，但不再直接暴露给 DbQueryApi 调用方。这样 DbQueryApi、VirtualDataApi、前端 Runtime 和未来其他服务最终共享同一个虚拟数据治理与执行内核。
