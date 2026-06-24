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
- 枚举字段在实体上显式声明 `typeHandler = DefaultEnumTypeHandler.class`。
- 布尔字段统一使用 `Boolean`，避免原始类型导致空值语义丢失。

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

## 11. 落地原则

- 新增基础数据模块时，先确认 Athena 是否已有可复用基类和 CRUD 结构，再决定是否自定义。
- 优先保持“表结构简单 + service 负责业务 + controller 负责暴露”的分层。
- 如果某块实现已经明显偏离本规范，优先先补清真实原因，再决定统一收敛，不要机械替换。
