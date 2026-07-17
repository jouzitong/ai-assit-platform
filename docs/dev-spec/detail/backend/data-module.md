# 基础数据模块开发规范

## 1. 目标

- 统一基础表、entity、dto、mapper、service、controller 的开发方式。
- 优先复用 `athena-framework` 已有基类和 CRUD 能力，不重复造轮子。
- 提前约束枚举、审计字段、状态字段等常见问题，减少后续返工。

## 2. 适用范围

- 适用于 `app/` 下以数据维护为主的后端模块。
- 典型场景包括：配置表、元数据表、页面结构表、组件定义表、任务记录表等。
- 如果模块是复杂领域编排，不要强行套成“纯 CRUD 模块”；但基础数据部分仍应遵循本规范。

## 3. 基础表设计

### 3.1 通用原则

- 表结构先满足当前业务最小闭环，避免一开始堆过多“可能以后会用”的字段。
- 主表负责核心业务属性；大文本、大 JSON、快照、历史内容优先拆到独立内容表或快照表。
- 状态、类型、阶段等有明确语义的字段，优先使用枚举，不要直接用裸字符串。
- 单纯开关类字段使用 `BOOLEAN` / `Boolean`；有多状态流转语义时不要用布尔代替枚举。
- 命名优先保持清晰直接，避免 `data`、`info`、`ext` 这类语义弱字段泛滥。

### 3.2 基础字段

- 主键统一使用 `id`。
- 乐观锁版本字段统一使用 `version`。
- 审计字段统一使用：
  - `create_time`
  - `created_by`
  - `update_time`
  - `updated_by`
- 只要是正常业务表，默认都应具备上述审计字段。

### 3.3 表拆分建议

- 主表只放检索频繁、列表展示频繁、需要参与筛选的字段。
- `json`、`markdown`、超长文本、页面结构内容等大字段，优先拆到 `*_content` 表。
- 历史留痕、内容快照、版本快照，优先拆到 `*_snapshot` 表，不要持续堆在主表中。

## 4. Entity / DTO 规范

### 4.1 Entity 基类选择

- 默认继承 `org.athena.framework.data.mybatis.entity.AuditableEntity`。
- 只有在明确不需要审计字段的场景下，才考虑继承 `BaseEntity`。
- 不要在子类里重复定义 `id`、`version`、`createTime`、`updateTime`、`createdBy`、`updatedBy`。

### 4.2 DTO 基类选择

- 默认继承 `org.athena.framework.data.mybatis.entity.dto.AuditableDTO`。
- DTO 与 Entity 的基础字段语义保持一致，不要重新发明一套审计字段命名。

### 4.3 字段映射

- 实体类必须显式声明 `@TableName`。
- 字段名和数据库列名不一致时，使用 `@TableField` 明确映射。
- 业务字段必须显式声明 `org.athena.framework.data.jdbc.annotations.JdbcColumn`，用于描述自动建表/补表所需的列名、类型、长度、非空、默认值、唯一约束和注释。
- 枚举字段在实体上显式声明 `typeHandler = DefaultEnumTypeHandler.class`。
- 布尔字段统一使用 `Boolean`，避免原始类型导致空值语义丢失。
- 使用 JSON、枚举 typeHandler 或自定义 typeHandler 的实体，`@TableName` 必须设置 `autoResultMap = true`。
- `@JdbcColumn.name` 与 `@TableField` 的列名必须保持一致；如果列名是数据库关键字，`@TableField` 可使用反引号转义，但 `@JdbcColumn.name` 仍填写真实列名。

### 4.4 `@JdbcColumn` 列定义规范

- `name` 必填，使用数据库列名的 snake_case，不使用 Java 字段名。
- `dataType` 必填，必须写成明确的数据库类型；字符串类型同时写 `length`，避免只写泛化的 `VARCHAR(255)`。
- `nullable` 必填，业务必填字段使用 `nullable = false`；可空字段必须有明确空值语义。
- `comment` 必填，说明业务含义，不只重复字段名。
- `defaultValue` 只用于数据库层面确实需要兜底的字段，例如启用标记、优先级、初始状态；不要用默认值掩盖业务侧应显式传入的数据。
- `unique = true` 只用于单列全局唯一约束，例如稳定业务编码；复合唯一约束不要拆成多个单列 `unique`。
- `JdbcColumn` 只描述当前实体自己的业务字段；继承自基类的 `id`、`version`、审计字段不要在子类重复声明。

常用类型和长度建议：

- 稳定业务编码、外部引用编码：`VARCHAR(64)`；同一语义字段在主表、版本表、关联表中长度必须一致。
- 名称、标题：`VARCHAR(128)`；确有长标题展示需求时再提升到 `VARCHAR(255)`。
- 短类型、运行时类型、状态文本、模式值：`VARCHAR(32)` 或枚举 `INT`；有固定集合时优先枚举 `INT`。
- 描述、备注、错误摘要：`VARCHAR(512)` 或 `VARCHAR(1024)`；超过 1024 或内容可持续增长时改用 `TEXT` 并评估是否拆表。
- JSON 配置、校验报告、规格定义、Manifest：优先 `MEDIUMTEXT`；如果用于数据库原生 JSON 查询，才考虑 `JSON` 类型。
- Markdown、脚本、正文、快照类内容：优先拆到内容表或版本表；确需直接存储时使用 `TEXT` / `MEDIUMTEXT`，不要使用 `VARCHAR(255)`。
- SHA-256 摘要：`CHAR(64)`；带算法前缀或多算法格式时使用 `VARCHAR(80)` 或更明确长度。
- 路径、入口文件、包内相对路径：`VARCHAR(512)`；同时在业务校验中限制绝对路径、路径穿越和重复路径。
- MIME / media type：`VARCHAR(128)`。
- 字节数、文件大小、计数类长整型：`BIGINT`。
- 布尔开关：`BOOLEAN`，Java 类型使用 `Boolean`；默认启用类字段可配 `defaultValue = "TRUE"`。
- 时间字段：`DATETIME`，Java 类型按现有模块约定使用 `LocalDateTime` 或 `Instant`，同一表内保持一致。
- 二进制文件内容：`LONGBLOB`；大文件默认优先放对象存储或文件服务，只有小规模受控内容才直接入库。

示例：

```java
@JdbcColumn(name = "code", dataType = "VARCHAR(64)", length = 64,
        nullable = false, unique = true, comment = "业务编码")
@TableField("code")
private String code;

@JdbcColumn(name = "status", dataType = "INT", nullable = false,
        defaultValue = "1", comment = "状态：1=草稿,2=已发布")
@TableField(value = "status", typeHandler = DefaultEnumTypeHandler.class)
private DefinitionStatus status;
```

## 5. Mapper 规范

- Mapper 统一继承 `org.athena.framework.data.mybatis.mapper.CrudMapper<Entity>`。
- Mapper 必须加 `@Mapper`。
- 基础增删改查优先复用框架通用能力，不要为了普通 CRUD 重写整套 SQL。
- 只有在存在明确业务查询时，才补充自定义 SQL。
- 自定义 SQL 应只补当前模块必要的查询，不要把复杂业务编排塞进 Mapper。

## 6. Service 规范

- 基础数据服务默认继承 `org.athena.framework.data.mybatis.service.BaseMapperService<Entity, Mapper, DTO>`。
- `convert()` 返回当前模块的 convert 实现，保持 entity / dto 转换单一出口。
- 通用 CRUD 优先复用基类，不要重复包装同名空方法。
- 业务校验、唯一性检查、补充查询、跨表写入等逻辑放在 service 层，不放 controller 或 mapper 层。
- 如果模块同时承担“写管理”和“读查询”两类职责，职责已经明显分叉时，要考虑拆分服务，而不是持续堆到一个类里。

## 7. Controller 规范

- 标准数据维护接口优先继承 `org.athena.framework.data.jdbc.web.BaseController<DTO, QueryRequest, Service>`。
- Controller 只负责接口暴露和少量请求编排，不承载核心业务逻辑。
- 面向前端页面访问的接口使用 `/api/{version}` 前缀。
- 面向内部服务调用的契约接口放在 `api` 模块，路径使用 `/internal/{version}` 前缀。
- 不要把“给前端页面查询用”的接口定义到 `/internal`。

## 8. QueryRequest 规范

- 分页查询请求统一继承 `org.athena.framework.data.jdbc.req.BaseRequest`。
- QueryRequest 只放查询条件，不要混入创建、更新所需字段。
- 模糊搜索、状态筛选、分类筛选等条件命名要直接表达业务含义。

## 9. 枚举规范

### 9.1 基本约束

- 公共业务枚举优先放到 `api` 模块，供多个模块共享。
- 枚举统一实现 `org.arthena.framework.common.enums.IEnum`。
- 每个枚举至少包含：
  - `code`
  - `name`
- `code` 使用稳定整数值，不要使用字符串持久化业务枚举。
- `getCode()` 对应的字段要加 `@JsonValue`，保证序列化和持久化语义一致。

### 9.2 持久化约束

- MyBatis 默认通过 `DefaultEnumTypeHandler` 按 `code` 持久化枚举。
- 数据库枚举列应按整数列设计，不要新建 `VARCHAR` 类型来存枚举值。
- 实体枚举字段要显式声明 `@TableField(..., typeHandler = DefaultEnumTypeHandler.class)`，不要依赖隐式猜测。

### 9.3 常见问题

- 不要把“多状态字段”简化成字符串常量比较。
- 不要把“状态枚举”和“是否启用”混成一个字段。
- 不要随意调整已上线枚举的 `code`，否则会直接影响历史数据解释。
- 如果线上历史数据已经存在字符串旧值，必须补兼容处理或数据迁移方案，不能只改 Java 枚举后假设自动兼容。

## 10. 常见反模式

- 明明可以继承 `AuditableEntity`，却手写一套重复的审计字段。
- 明明可以复用 `BaseController` / `BaseMapperService` / `CrudMapper`，却为标准 CRUD 重写整套样板代码。
- 把 controller 写成业务服务入口，导致校验、事务、跨表逻辑都堆在接口层。
- 把 JSON 大字段直接塞进主表，导致列表查询和更新负担过重。
- 枚举字段用 `String` 存库，后续再补 handler 和迁移，增加兼容成本。
- `@JdbcColumn` 全部写成 `VARCHAR(255)` 或全部允许为空，导致建表结果无法表达真实业务约束。
- `@JdbcColumn` 与 `@TableField` 的列名、类型语义不一致，后续自动建表和 MyBatis 持久化出现偏差。

## 11. 落地原则

- 新增基础数据模块时，先确认 Athena 是否已有可复用基类和 CRUD 结构，再决定是否自定义。
- 优先保持“表结构简单 + service 负责业务 + controller 负责暴露”的分层。
- 如果某块实现已经明显偏离本规范，优先先补清真实原因，再决定统一收敛，不要机械替换。
