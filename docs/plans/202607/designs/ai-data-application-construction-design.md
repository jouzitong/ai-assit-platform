# AI 问数与 Render 应用构建实施方案

> 状态：待实施
> 日期：2026-07-17
> 范围：`app/app-platform-chat`、`app/app-platform-render`、`app/app-platform-db-engine`、`ai-conversation-ui` 与 RAGFlow
> 本文只定义实施方案，不包含本次代码实现。

## 1. 结论

AI 问数不应被实现为“模型一次性生成 Render JSON”。目标链路应先构造可信的数据契约，再构造可验证、可预览、可直接发布的 Render JSON：

```text
用户需求
  -> 企业工作主控 Agent 识别为 AI 问数/应用构建
  -> 看板与应用构建 Agent
  -> 数据语义检索与 DataContract
  -> 受控数据预览查询
  -> ApplicationPlan
  -> Render JSON
  -> 静态校验
  -> 运行时预览
  -> 直接发布并自动创建页面快照
```

第一期不建设 `data_catalog_search_tool`。数据模型、字段、同义词、关系和指标含义由 RAGFlow 的“数据语义目录”知识库检索；真实字段是否可用、真实权限是否允许访问，必须由受控的数据预览查询在服务端校验。知识库用于发现和理解，不能作为权限或查询正确性的最终依据。

## 2. 现有基础与约束

1. Python Agent Runtime 已有 `knowledge_base_search_tool`，且只允许访问本次 Run 上下文授权的 `knowledgeBases` 列表。
2. `render_json_validate_tool` 目前仅提供 JSON 和基础节点结构检查，后续必须升级为 Render 协议、组件、数据绑定和安全规则的确定性校验。
3. `data-component` 已保存组件 key、说明 Markdown 和示例 JSON；它与前端 `component-manifest.ts`、registry 共同构成组件契约的事实来源。
4. `data-render` 已支持页面内容更新，并在写入时创建页面快照。发布流程可以直接使用该能力，不需要单独的草稿审批阶段。
5. `DbQueryApi` 已有 list、aggregate、pivot 等查询契约；其演进方向是已发布虚拟实体和虚拟字段，而不是让 Agent 直接感知物理表和 SQL。

## 3. 责任边界

| 主体 | 负责 | 不负责 |
|---|---|---|
| 企业工作主控 Agent | 识别 AI 问数意图、委派应用构建 Agent、整合用户答复 | 构造 SQL、直接发布页面 |
| 看板与应用构建 Agent | 维护构建阶段产物、检索知识、选择组件、生成并修复 Render JSON | 绕过校验、绕过服务端权限 |
| 数据语义知识库 | 解释业务术语、同义词、候选虚拟模型/字段、业务关系与指标口径 | 判定实时权限、执行数据查询 |
| 数据预览查询 Tool | 服务端校验虚拟模型、字段、行列权限和样例结果 | 解释用户自然语言 |
| Render 组件目录 Tool | 返回启用组件的精确版本、props、events、约束和示例 | 通过自然语言猜测业务数据 |
| Render 校验/预览 Tool | 进行确定性校验和真实运行时预览 | 选择业务口径 |
| Render 发布 Tool | 校验前置证明后更新页面内容并创建快照 | 接受未经校验的 Agent 声明 |

## 4. 所需知识库的列表清单

以下 `kbCode` 为建议稳定编码。RAGFlow 创建完成后，Java 侧应把相应的 `kbCode`、名称、描述和 tags 注入本次 Run 的 `knowledgeBases` 白名单；Python Agent 只能从该白名单选择知识库。

| 优先级 | 建议 kbCode | RAGFlow 知识库名称 | 内容与文档粒度 | 主要使用阶段 |
|---|---|---|---|---|
| 必需 | `data-semantic-catalog` | 数据语义目录 | 一份“已发布虚拟业务模型”一个文档；不要仅按物理表拆分 | 识别模型、字段、同义词、关系、指标口径 |
| 必需 | `render-component-catalog` | Render 组件目录 | 一个启用组件版本一个文档 | 选择 renderer、生成 props/events/bindings |
| 必需 | `render-build-faq` | Render 构建诊断 FAQ | 一个错误类型或已审核修复案例一个文档 | 低置信、校验失败、预览失败后的修复 |
| 强烈建议 | `render-app-templates` | Render 应用模板库 | 一份已审核的完整页面/看板模板一个文档 | 按页面类型选择稳定布局与示例 |
| 按业务域启用 | `enterprise-business-knowledge` | 企业业务知识库 | 制度、业务术语、流程、项目资料等 | 理解企业特有业务语义与展示约束 |

### 4.1 `data-semantic-catalog`：数据语义目录

这是 AI 问数的首要知识库。文档的主体应是“虚拟业务模型”或已发布查询模型；只有当一个物理表就是稳定的业务模型时，才按物理表建立文档。

每份文档至少包含：

```yaml
documentType: data-semantic-model
model: sales_order
modelAliases: [销售订单, 成交订单, 订单明细]
domain: 销售
description: 已支付订单的销售分析模型
fields:
  - code: paid_amount
    aliases: [销售额, 实付金额, GMV]
    type: decimal
    description: 用户实际支付金额
    classification: internal
  - code: paid_at
    aliases: [成交时间, 支付时间]
    type: datetime
relations:
  - targetModel: region
    joinHint: region_id
recommendedDimensions: [region_name, paid_at]
recommendedMetrics: [sum(paid_amount), count(order_id)]
exampleQuestions:
  - 各区域近半年销售趋势
owner: 销售数据团队
sourceRevision: virtual-model/v12
updatedAt: 2026-07-17
```

要求：

- 同义词既要写入正文，也要写入 RAGFlow metadata/tags，提升关键词与向量检索召回。
- 明确 `model`、`fields.code`、关系和版本；这些字段必须能映射到已发布虚拟目录。
- 可写入数据分级和“需额外授权”的说明，但不能把该说明当成真实权限判断依据。
- 虚拟目录发布、字段变更或模型下线后，必须自动重建或更新对应文档；检索结果必须携带 `sourceRevision` 和 `updatedAt`。

### 4.2 `render-component-catalog`：Render 组件目录

每份文档对应一个已启用的组件版本，来源为 `data-component` 的组件说明与示例 JSON，并与前端 registry/manifest 的稳定 key 对齐。

必填内容：`componentKey`、版本、类别、适用场景、输入 props、必填字段、events、限制条件、示例 JSON、常见错误、替代组件和 `sourceRevision`。

这个知识库用于给模型提供可读说明；后续的 `render_component_catalog_tool` 必须读取实时启用组件并做最终确认，避免知识库过期时生成已下线组件。

### 4.3 `render-build-faq`：Render 构建诊断 FAQ

该库只用于故障诊断，不用于初次生成。每份文档对应一个稳定错误码、组件错误或经过审核的修复案例。

必填内容：`stage`、`errorCode`、`componentKey`（可选）、症状、根因、最小修复步骤、适用版本、反例、示例 JSON、`sourceRevision` 和失效时间。

建议阶段值：`semantic-discovery`、`data-preview`、`render-validation`、`render-preview`、`publish`。

触发条件：

1. Agent 的置信度低于阈值；
2. 数据语义目录无法唯一匹配模型或字段；
3. Render 校验返回确定性错误；
4. Render 预览失败。

FAQ 仅提供修复建议，不能覆盖校验器、组件目录或权限系统的确定性结论。

### 4.4 `render-app-templates`：Render 应用模板库

只收入已审核、可运行、已脱敏的完整 Render 页面，例如趋势看板、指标总览、列表检索页、明细钻取页和组合图页。

每份文档必须含：页面目标、适用数据形态、所用组件、布局说明、完整或裁剪后的 Render JSON、数据绑定说明、版本和验证记录。不要把未验证的 Agent 产物直接回灌为模板。

### 4.5 `enterprise-business-knowledge`：企业业务知识库

存放制度、业务术语、组织规则、业务流程和项目资料。它帮助模型理解“什么是有效订单”“区域的归属规则”等业务概念，但不能替代数据语义目录中的 `model` 与字段编码。

多租户或多业务域场景应按租户、业务域和敏感等级拆分知识库或加服务端过滤，不应让一个 Run 获得所有领域的知识库白名单。

## 5. Agent 构建流程与中间产物

看板与应用构建 Agent 必须按阶段工作，不允许跳过数据契约直接生成最终 Render JSON。

### 5.1 `ApplicationBrief`

从用户请求提取：页面目标、目标用户、业务问题、指标、维度、时间范围、筛选需求、展示偏好、是否允许创建新页面和待澄清项。

无法确认“分析结果”还是“需要生成可交互应用”时，主控 Agent 必须向用户追问，而不是默认发布页面。

### 5.2 `DataContract`

由 `data-semantic-catalog` 检索结果形成：

```json
{
  "model": "sales_order",
  "measures": [{"field": "paid_amount", "aggregation": "sum", "label": "销售额"}],
  "dimensions": [{"field": "region_name", "label": "区域"}],
  "timeRange": {"field": "paid_at", "preset": "LAST_6_MONTHS"},
  "filters": [],
  "sourceRevision": "virtual-model/v12",
  "assumptions": []
}
```

只有当模型、字段、粒度与口径足够明确时，才进入数据预览。数据预览查询必须由服务端基于虚拟实体、虚拟字段和当前用户权限执行；RAGFlow 结果不构成授权证明。

### 5.3 `ApplicationPlan`

数据预览成功后，选择页面布局、组件、图表类型、过滤器、数据绑定和交互。组件选择先检索 `render-component-catalog` 或模板库；最终通过实时组件目录确认 key、版本与 props。

### 5.4 `RenderDocument`

Render JSON 只描述页面结构、稳定组件 key、layout、props、datasource、bindings 与稳定 action key。它不得内嵌可执行函数、SQL、凭据、任意 URL 或绕过权限的请求地址。

### 5.5 校验、预览与直接发布

```text
RenderDocument
  -> render_json_validate_tool
  -> 失败：用错误码检索 render-build-faq，最小修复后重新校验
  -> render_preview_tool
  -> 失败：检索 FAQ，最小修复后重新预览
  -> render_publish_tool
  -> data-render 更新当前页面内容并创建快照
```

无需草稿审批，但发布 Tool 的服务端必须重新检查以下前置证明：

- 文档 hash 与已校验文档一致；
- 校验成功结果及规则版本；
- 预览成功会话或结果 hash，且未过期；
- 当前组件目录 revision 未变化，或重新校验通过；
- 当前用户对目标 Render 页面具有发布权限。

发布成功后由 `data-render` 写入页面内容并创建不可变快照；失败时不得覆盖现有内容。

## 6. 计划新增的 Skill 与 Tool

### 6.1 Skill

| Skill | 责任 | 主要读取的知识库 |
|---|---|---|
| `semantic-data-contract` | 将自然语言需求转换为受限的 DataContract，并处理同义词与不确定性 | `data-semantic-catalog`、`enterprise-business-knowledge` |
| `render-json-authoring` | 基于 ApplicationPlan、组件契约和模板生成声明式 Render JSON | `render-component-catalog`、`render-app-templates` |
| `render-json-repair` | 根据校验或预览错误做最小修复 | `render-build-faq`、`render-component-catalog` |
| `application-build-release` | 组织校验、预览、直接发布及发布结果回传 | 无固定知识库 |

Skill 只保存流程、产物 Schema、检查清单和少量审核样例；实时组件状态、数据字段和权限不写入 Skill。

### 6.2 Tool

| Tool | 阶段 | 责任 | 第一阶段状态 |
|---|---|---|---|
| `knowledge_base_search_tool` | 发现/诊断 | 按 Run 白名单检索上述 RAGFlow 知识库 | 已有，按 KB 建设接入 |
| `data_preview_query_tool` | DataContract 验证 | 使用已发布虚拟模型与字段做限量只读预览，强制权限 | 待建设 |
| `render_component_catalog_tool` | ApplicationPlan/校验 | 获取实时启用组件、版本、props、events、示例 | 待建设 |
| `render_json_validate_tool` | RenderDocument 校验 | 升级为协议、组件、绑定、安全的确定性校验 | 已有基础能力，待升级 |
| `render_preview_tool` | 运行时验证 | 装载 Render JSON，返回渲染结果、截图或稳定错误码 | 待建设 |
| `render_publish_tool` | 发布 | 校验发布前置证明后写入页面并创建快照 | 待建设 |

`data_catalog_search_tool` 不在第一阶段建设。若未来 RAGFlow 同步存在明显延迟、模型数量过大或需要一次精确返回全量字段，再新增 `data_catalog_verify_tool`，仅用于对已选定的 `model + fields` 做实时确认，不承担自然语言语义搜索。

## 7. 分期实施计划

### Phase 0：知识与契约准备

1. 在 RAGFlow 创建第 4 章列出的必需知识库；业务知识库按实际领域决定是否创建。
2. 定义 `ApplicationBrief`、`DataContract`、`ApplicationPlan`、`RenderDocument`、`ValidationReport` 的 JSON Schema。
3. 建立虚拟模型/字段到 `data-semantic-catalog` 文档的同步任务。
4. 建立组件目录到 `render-component-catalog` 文档的同步任务。
5. 录入最小可用模板和诊断 FAQ；每个案例必须有版本与审核人。

验收：用 10 个真实业务问句能检索到正确或可解释的候选数据模型与字段；组件目录能检索到当前启用组件。

### Phase 1：受控构建与静态校验

1. 为看板与应用构建 Agent 增加知识库检索能力与上述四个 Skill。
2. 实现 `data_preview_query_tool`，仅允许限量只读虚拟查询。
3. 实现 `render_component_catalog_tool`。
4. 升级 `render_json_validate_tool`，输出稳定错误码、jsonPath、nodeId 和可修复标记。
5. Agent 在校验失败时检索 FAQ 并限制修复次数。

验收：可从自然语言生成通过静态校验的 Render JSON；字段不存在、组件不存在、非法 props 和危险配置均可被确定性拦截。

### Phase 2：预览与直接发布

1. 实现 `render_preview_tool` 与运行时错误归一化。
2. 实现 `render_publish_tool` 的验证证明校验、幂等、权限和快照写入。
3. 对接 `data-render` 内容更新与快照服务。
4. 在会话中展示构建阶段、校验报告、预览链接和最终页面信息。

验收：预览成功的文档可直接发布；预览失败或发布前置证明不匹配时不能覆盖既有页面。

### Phase 3：质量与运营闭环

1. 按错误码和 Agent 修复失败案例审核后回灌 `render-build-faq`。
2. 把成功且脱敏的页面快照纳入模板库。
3. 建立知识库同步监控、文档 revision、过期清理与召回评估。
4. 基于运行审计统计字段命中率、组件选择失败率、FAQ 命中率、一次预览通过率和发布成功率。

## 8. 风险与不可违反的规则

- 不允许 Agent 直接查询物理表、拼接 SQL、读取凭据或绕过虚拟目录。
- 不允许由知识库内容决定用户是否有数据或发布权限。
- 不允许校验失败、预览失败或前置证明过期时继续发布。
- 不允许将未审核的 Agent 生成 JSON 直接回灌模板库或 FAQ。
- 不允许把组件目录、数据语义目录与企业业务知识混成一个无边界知识库；不同更新频率、权限范围和事实来源必须分开管理。
