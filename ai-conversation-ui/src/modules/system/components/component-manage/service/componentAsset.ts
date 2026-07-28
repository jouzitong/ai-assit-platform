import {
  findApplicationComponent,
  type ApplicationComponentDefinition,
  type ApplicationComponentEvent,
  type ApplicationComponentParameter,
  type ApplicationRenderDocument,
} from '../../../../../application/component-manifest'

export type ComponentAssetStatus = string | number

export const COMPONENT_ASSET_SCHEMA_VERSION = 'component-asset/v1' as const
export const RENDER_COMPONENT_KNOWLEDGE_BASE_SETTING_KEY = 'render.component.kbId' as const

export interface ComponentAssetRecord {
  key?: string
  name?: string
  category?: string
  status?: ComponentAssetStatus
  docMarkdown?: string
  exampleJson?: string
}

export interface ComponentParameterDraft {
  key: string
  enabled: boolean
  value: string | number | boolean
  description: string
}

export interface ComponentAssetDraft {
  sourceKey: string
  key: string
  name: string
  category: string
  status: ComponentAssetStatus
  summary: string
  useCases: string
  usageGuide: string
  limitations: string
  notes: string
  tags: string[]
  owner: string
  generateKnowledgeDocument: boolean
  knowledgeBaseId: string
  knowledgeBaseSettingKey: string
  /** @deprecated 仅用于旧页面并行迁移；新资产不会再写入 knowledgeBaseCode。 */
  knowledgeBaseCode: string
  parameters: Record<string, ComponentParameterDraft>
}

export interface ComponentAssetSubmission {
  key: string
  name: string
  category?: string
  status: ComponentAssetStatus
  docMarkdown: string
  exampleJson: string
}

export interface ComponentAssetEditableContent {
  docMarkdown: string
  renderExampleJson: string
}

export interface ComponentAssetParameterContract extends ApplicationComponentParameter {
  enabled: boolean
}

export interface ComponentAssetContract {
  parameters: ComponentAssetParameterContract[]
  events: ApplicationComponentEvent[]
}

export type ComponentAssetRenderDocument = ApplicationRenderDocument

export interface ComponentAssetEnvelope {
  schemaVersion: typeof COMPONENT_ASSET_SCHEMA_VERSION
  sourceComponent: {
    key: string
    name: string
    version: string
    sourcePath: string
  }
  props: Record<string, unknown>
  contract: ComponentAssetContract
  renderExample: ComponentAssetRenderDocument
  asset: {
    summary: string
    useCases: string[]
    usageGuide: string
    limitations: string
    notes: string
    tags: string[]
    owner: string
    generateKnowledgeDocument: boolean
    knowledgeBaseId: string
    knowledgeBaseSettingKey: typeof RENDER_COMPONENT_KNOWLEDGE_BASE_SETTING_KEY
    /** @deprecated 兼容读取 component-asset/v1 早期数据。 */
    knowledgeBaseCode?: string
    documentCode: string
  }
}

const DEFAULT_STATUS = 2

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function toStringArray(value: unknown) {
  return Array.isArray(value) ? value.filter(item => typeof item === 'string') as string[] : []
}

function readText(value: unknown) {
  return typeof value === 'string' ? value : ''
}

function cloneJson<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

function normalizeExampleId(value: string) {
  return value.trim().toLowerCase().replace(/[^a-z0-9_-]+/g, '-') || 'component'
}

function createDefaultRenderExample(
  definition: ApplicationComponentDefinition | undefined,
  props: Record<string, unknown>,
  fallback?: { key?: string; version?: string },
): ComponentAssetRenderDocument {
  const componentKey = definition?.key || fallback?.key || 'unknown-component'
  const componentVersion = definition?.version || fallback?.version || '1.0.0'
  const manifestExample = definition?.examples[0]?.renderDocument

  if (manifestExample) {
    const document = cloneJson(manifestExample)
    return {
      ...document,
      root: {
        ...document.root,
        component: componentKey,
        componentVersion,
        props: cloneJson(props),
      },
    }
  }

  const exampleId = normalizeExampleId(componentKey)
  return {
    protocol: 'render-json',
    protocolVersion: '1.0.0',
    pageId: `component-example-${exampleId}`,
    root: {
      id: `component-example-${exampleId}-root`,
      component: componentKey,
      componentVersion,
      props: cloneJson(props),
    },
  }
}

function getDefaultExampleProps(definition?: ApplicationComponentDefinition) {
  const props = definition?.examples[0]?.renderDocument.root.props
  return isRecord(props) ? cloneJson(props) : {}
}

function buildContract(
  definition: ApplicationComponentDefinition | undefined,
  props: Record<string, unknown>,
  draft?: ComponentAssetDraft,
): ComponentAssetContract {
  return {
    parameters: (definition?.parameters || []).map(parameter => ({
      ...cloneJson(parameter),
      enabled: Object.prototype.hasOwnProperty.call(props, parameter.key),
      description: draft?.parameters[parameter.key]?.description || parameter.description,
    })),
    events: cloneJson(definition?.events || []),
  }
}

export function parseComponentAssetRenderExample(value: string | unknown): ComponentAssetRenderDocument {
  let parsed: unknown = value
  if (typeof value === 'string') {
    if (!value.trim()) {
      throw new Error('Render JSON 不能为空')
    }
    try {
      parsed = JSON.parse(value) as unknown
    }
    catch {
      throw new Error('Render JSON 格式不正确')
    }
  }

  if (!isRecord(parsed)) {
    throw new Error('Render JSON 根结构必须是对象')
  }
  const allowedDocumentKeys = new Set(['protocol', 'protocolVersion', 'pageId', 'revision', 'root'])
  const unsupportedKey = Object.keys(parsed).find(key => !allowedDocumentKeys.has(key))
  if (unsupportedKey) {
    throw new Error(`Render JSON 包含不支持的顶层字段“${unsupportedKey}”`)
  }
  if (parsed.protocol !== 'render-json') {
    throw new Error('Render JSON protocol 必须为 render-json')
  }
  if (!readText(parsed.protocolVersion).trim()) {
    throw new Error('Render JSON 缺少 protocolVersion')
  }
  if (!readText(parsed.pageId).trim()) {
    throw new Error('Render JSON 缺少 pageId')
  }
  if (parsed.revision !== undefined && !readText(parsed.revision).trim()) {
    throw new Error('Render JSON revision 必须是非空字符串')
  }
  if (!isRecord(parsed.root)) {
    throw new Error('Render JSON 缺少 root 节点')
  }
  if (!readText(parsed.root.id).trim()) {
    throw new Error('Render JSON root.id 不能为空')
  }
  if (!readText(parsed.root.component).trim()) {
    throw new Error('Render JSON root.component 不能为空')
  }
  if (parsed.root.props !== undefined && !isRecord(parsed.root.props)) {
    throw new Error('Render JSON root.props 必须是对象')
  }

  return cloneJson(parsed) as unknown as ComponentAssetRenderDocument
}

function formatParameterValue(parameter: ApplicationComponentParameter, value: unknown) {
  if (parameter.control === 'json') {
    return JSON.stringify(value ?? parameter.defaultValue, null, 2)
  }
  if (parameter.control === 'boolean') {
    return Boolean(value ?? parameter.defaultValue)
  }
  if (parameter.control === 'number') {
    const numberValue = Number(value ?? parameter.defaultValue)
    return Number.isFinite(numberValue) ? numberValue : 0
  }
  return String(value ?? parameter.defaultValue ?? '')
}

function createParameterDrafts(
  definition?: ApplicationComponentDefinition,
  values: Record<string, unknown> = {},
) {
  if (!definition) {
    return {}
  }

  return definition.parameters.reduce<Record<string, ComponentParameterDraft>>((result, parameter) => {
    const hasPersistedValue = Object.prototype.hasOwnProperty.call(values, parameter.key)
    result[parameter.key] = {
      key: parameter.key,
      enabled: parameter.required || hasPersistedValue,
      value: formatParameterValue(parameter, hasPersistedValue ? values[parameter.key] : parameter.defaultValue),
      description: parameter.description,
    }
    return result
  }, {})
}

export function readComponentAssetEnvelope(value?: string): ComponentAssetEnvelope | null {
  if (!value?.trim()) {
    return null
  }
  try {
    const parsed = JSON.parse(value) as unknown
    if (!isRecord(parsed) || parsed.schemaVersion !== COMPONENT_ASSET_SCHEMA_VERSION) {
      return null
    }
    const sourceComponent = isRecord(parsed.sourceComponent) ? parsed.sourceComponent : {}
    const componentKey = readText(sourceComponent.key).trim()
    if (!componentKey) {
      return null
    }
    const definition = findApplicationComponent(componentKey)
    const componentVersion = readText(sourceComponent.version).trim() || definition?.version || '1.0.0'
    const props = isRecord(parsed.props) ? cloneJson(parsed.props) : {}
    const rawContract = isRecord(parsed.contract) ? parsed.contract : null
    const contract = rawContract
      && Array.isArray(rawContract.parameters)
      && Array.isArray(rawContract.events)
      ? cloneJson(rawContract) as unknown as ComponentAssetContract
      : buildContract(definition, props)
    let renderExample = createDefaultRenderExample(definition, props, {
      key: componentKey,
      version: componentVersion,
    })
    if (parsed.renderExample !== undefined) {
      try {
        renderExample = parseComponentAssetRenderExample(parsed.renderExample)
      }
      catch {
        // Early component-asset/v1 records may contain incomplete examples; use a valid fallback.
      }
    }

    const rawAsset = isRecord(parsed.asset) ? parsed.asset : {}
    const legacyKnowledgeBaseCode = readText(rawAsset.knowledgeBaseCode).trim()
    const knowledgeBaseId = readText(rawAsset.knowledgeBaseId).trim() || legacyKnowledgeBaseCode

    return {
      schemaVersion: COMPONENT_ASSET_SCHEMA_VERSION,
      sourceComponent: {
        key: componentKey,
        name: readText(sourceComponent.name).trim() || definition?.name || componentKey,
        version: componentVersion,
        sourcePath: readText(sourceComponent.sourcePath).trim() || definition?.sourcePath || '',
      },
      props,
      contract,
      renderExample,
      asset: {
        summary: readText(rawAsset.summary),
        useCases: toStringArray(rawAsset.useCases),
        usageGuide: readText(rawAsset.usageGuide),
        limitations: readText(rawAsset.limitations),
        notes: readText(rawAsset.notes),
        tags: toStringArray(rawAsset.tags),
        owner: readText(rawAsset.owner),
        generateKnowledgeDocument: typeof rawAsset.generateKnowledgeDocument === 'boolean'
          ? rawAsset.generateKnowledgeDocument
          : true,
        knowledgeBaseId,
        knowledgeBaseSettingKey: RENDER_COMPONENT_KNOWLEDGE_BASE_SETTING_KEY,
        ...(legacyKnowledgeBaseCode ? { knowledgeBaseCode: legacyKnowledgeBaseCode } : {}),
        documentCode: readText(rawAsset.documentCode),
      },
    }
  }
  catch {
    return null
  }
}

export function createComponentAssetDraft(record: ComponentAssetRecord | null = null): ComponentAssetDraft {
  const envelope = readComponentAssetEnvelope(record?.exampleJson)
  const sourceKey = envelope?.sourceComponent?.key
    || (findApplicationComponent(record?.key) ? record?.key : '')
    || ''
  const definition = findApplicationComponent(sourceKey)
  const asset = envelope?.asset
  const knowledgeBaseId = asset?.knowledgeBaseId || asset?.knowledgeBaseCode || ''
  const parameterValues = envelope ? envelope.props : getDefaultExampleProps(definition)

  return {
    sourceKey,
    key: record?.key || definition?.key || '',
    name: record?.name || definition?.name || '',
    category: record?.category || definition?.category || '',
    status: record?.status ?? DEFAULT_STATUS,
    summary: asset?.summary || definition?.documentation.summary || definition?.description || '',
    useCases: (asset?.useCases?.length ? asset.useCases : definition?.useCases || []).join('\n'),
    usageGuide: asset?.usageGuide || definition?.documentation.usageGuide || '',
    limitations: asset?.limitations || definition?.documentation.limitations || '',
    notes: asset?.notes
      || (!envelope && record?.docMarkdown ? record.docMarkdown : definition?.documentation.notes || ''),
    tags: asset?.tags?.length ? [...asset.tags] : [...(definition?.tags || [])],
    owner: asset?.owner || '',
    generateKnowledgeDocument: asset?.generateKnowledgeDocument ?? true,
    knowledgeBaseId,
    knowledgeBaseSettingKey: RENDER_COMPONENT_KNOWLEDGE_BASE_SETTING_KEY,
    knowledgeBaseCode: knowledgeBaseId,
    parameters: createParameterDrafts(definition, parameterValues),
  }
}

export function bindApplicationComponent(draft: ComponentAssetDraft, componentKey: string) {
  const definition = findApplicationComponent(componentKey)
  if (!definition) {
    return
  }
  draft.sourceKey = definition.key
  draft.key = definition.key
  draft.name = definition.name
  draft.category = definition.category
  draft.summary = definition.documentation.summary || definition.description
  draft.useCases = definition.useCases.join('\n')
  draft.usageGuide = definition.documentation.usageGuide
  draft.limitations = definition.documentation.limitations
  draft.notes = definition.documentation.notes
  draft.tags = [...definition.tags]
  draft.parameters = createParameterDrafts(definition, getDefaultExampleProps(definition))
}

function parseParameterValue(parameter: ApplicationComponentParameter, draft: ComponentParameterDraft) {
  if (parameter.control === 'json') {
    const value = String(draft.value).trim()
    if (!value) {
      return null
    }
    return JSON.parse(value)
  }
  if (parameter.control === 'number') {
    return Number(draft.value)
  }
  if (parameter.control === 'boolean') {
    return Boolean(draft.value)
  }
  return String(draft.value)
}

export function validateComponentAssetDraft(draft: ComponentAssetDraft, includeDetails = true) {
  const definition = findApplicationComponent(draft.sourceKey)
  if (!definition) {
    return '请先选择 Application 组件'
  }
  if (!includeDetails) {
    return ''
  }
  if (!draft.key.trim()) {
    return '请输入资产标识'
  }
  if (!draft.name.trim()) {
    return '请输入资产名称'
  }
  if (!draft.summary.trim()) {
    return '请填写组件能力说明'
  }

  for (const parameter of definition.parameters) {
    const parameterDraft = draft.parameters[parameter.key]
    if (parameter.required && !parameterDraft?.enabled) {
      return `必填参数“${parameter.label}”不能取消纳入`
    }
    if (!parameterDraft?.enabled) {
      continue
    }
    try {
      parseParameterValue(parameter, parameterDraft)
    }
    catch {
      return `参数“${parameter.label}”的 JSON 格式不正确`
    }
  }
  return ''
}

export function buildComponentProps(draft: ComponentAssetDraft) {
  const definition = findApplicationComponent(draft.sourceKey)
  if (!definition) {
    return {}
  }
  return definition.parameters.reduce<Record<string, unknown>>((result, parameter) => {
    const parameterDraft = draft.parameters[parameter.key]
    if (parameterDraft?.enabled) {
      result[parameter.key] = parseParameterValue(parameter, parameterDraft)
    }
    return result
  }, {})
}

function escapeTableCell(value: unknown) {
  return String(value ?? '').replace(/\|/g, '\\|').replace(/\r?\n/g, '<br>')
}

function formatStatus(status: ComponentAssetStatus) {
  if (status === 1 || status === 'DRAFT') return '草稿'
  if (status === 2 || status === 'PUBLISHED') return '已发布'
  if (status === 3 || status === 'DISABLED') return '已停用'
  return String(status)
}

function splitLines(value: string) {
  return value.split(/\r?\n/).map(item => item.trim()).filter(Boolean)
}

export function getKnowledgeDocumentCode(assetKey: string) {
  const normalized = assetKey.trim().toLowerCase().replace(/[^a-z0-9_-]+/g, '-')
  return `render-component-${normalized || 'draft'}`
}

export function buildComponentAssetRenderExample(draft: ComponentAssetDraft) {
  const definition = findApplicationComponent(draft.sourceKey)
  return createDefaultRenderExample(definition, buildComponentProps(draft), {
    key: draft.sourceKey || draft.key,
    version: definition?.version,
  })
}

export function buildComponentAssetRenderExampleJson(draft: ComponentAssetDraft) {
  return JSON.stringify(buildComponentAssetRenderExample(draft), null, 2)
}

function normalizeRenderExampleForDefinition(
  definition: ApplicationComponentDefinition,
  value: ComponentAssetRenderDocument,
  fallbackProps: Record<string, unknown>,
) {
  const renderExample = parseComponentAssetRenderExample(value)
  if (renderExample.root.component !== definition.key) {
    throw new Error(`Render JSON root.component 必须为 ${definition.key}`)
  }
  if (
    renderExample.root.componentVersion
    && renderExample.root.componentVersion !== definition.version
  ) {
    throw new Error(`Render JSON 组件版本必须为 ${definition.version}`)
  }

  return {
    ...renderExample,
    root: {
      ...renderExample.root,
      componentVersion: definition.version,
      props: isRecord(renderExample.root.props)
        ? cloneJson(renderExample.root.props)
        : cloneJson(fallbackProps),
    },
  }
}

export function buildComponentAssetEnvelope(
  draft: ComponentAssetDraft,
  renderExampleInput: ComponentAssetRenderDocument = buildComponentAssetRenderExample(draft),
): ComponentAssetEnvelope {
  const definition = findApplicationComponent(draft.sourceKey)
  if (!definition) {
    throw new Error('请先选择 Application 组件')
  }
  const fallbackProps = buildComponentProps(draft)
  const renderExample = normalizeRenderExampleForDefinition(definition, renderExampleInput, fallbackProps)
  const props = cloneJson(renderExample.root.props || fallbackProps)
  const knowledgeBaseId = draft.knowledgeBaseId.trim() || draft.knowledgeBaseCode.trim()

  return {
    schemaVersion: COMPONENT_ASSET_SCHEMA_VERSION,
    sourceComponent: {
      key: definition.key,
      name: definition.name,
      version: definition.version,
      sourcePath: definition.sourcePath,
    },
    props,
    contract: buildContract(definition, props, draft),
    renderExample,
    asset: {
      summary: draft.summary.trim(),
      useCases: splitLines(draft.useCases),
      usageGuide: draft.usageGuide.trim(),
      limitations: draft.limitations.trim(),
      notes: draft.notes.trim(),
      tags: [...draft.tags],
      owner: draft.owner.trim(),
      generateKnowledgeDocument: draft.generateKnowledgeDocument,
      knowledgeBaseId,
      knowledgeBaseSettingKey: RENDER_COMPONENT_KNOWLEDGE_BASE_SETTING_KEY,
      documentCode: getKnowledgeDocumentCode(draft.key),
    },
  }
}

export function buildComponentAssetExampleJson(draft: ComponentAssetDraft) {
  if (!findApplicationComponent(draft.sourceKey)) {
    return '{}'
  }
  const envelope = buildComponentAssetEnvelope(draft)
  return JSON.stringify(envelope, null, 2)
}

export function buildComponentAssetDocument(draft: ComponentAssetDraft) {
  const definition = findApplicationComponent(draft.sourceKey)
  if (!definition) {
    return '# 组件数字资产\n\n请先选择 Application 组件。'
  }
  const props = buildComponentProps(draft)
  const renderExample = buildComponentAssetRenderExample(draft)
  const useCases = splitLines(draft.useCases)
  const parameterRows = definition.parameters.map((parameter) => {
    const state = draft.parameters[parameter.key]
    const currentValue = state?.enabled ? JSON.stringify(props[parameter.key]) : '-'
    return `| ${escapeTableCell(parameter.key)} | ${escapeTableCell(parameter.type)} | ${parameter.required ? '是' : '否'} | ${state?.enabled ? '是' : '否'} | ${escapeTableCell(currentValue)} | ${escapeTableCell(state?.description || parameter.description)} |`
  })
  const eventRows = definition.events.length
    ? definition.events.map(event => `| ${escapeTableCell(event.name)} | ${escapeTableCell(event.description)} |`).join('\n')
    : '| - | 当前组件未暴露业务事件。 |'
  const scenarioContent = useCases.length ? useCases.map(item => `- ${item}`).join('\n') : '- 暂未补充'
  const limitationContent = draft.limitations.trim() || '暂无额外限制，实际使用时仍需遵循参数类型与上层数据契约。'
  const notesContent = draft.notes.trim() ? `\n\n## 9. 补充说明\n\n${draft.notes.trim()}` : ''

  return `# ${draft.name.trim() || definition.name}

> 由系统组件中心根据 Application 定义生成的数字资产文档。

## 1. 资产身份

| 属性 | 值 |
| --- | --- |
| 资产标识 | ${escapeTableCell(draft.key)} |
| 资产分类 | ${escapeTableCell(draft.category || definition.category)} |
| 当前状态 | ${formatStatus(draft.status)} |
| Application 组件 | ${escapeTableCell(definition.name)} (${definition.key}) |
| 源码路径 | \`${definition.sourcePath}\` |
| 负责人 | ${escapeTableCell(draft.owner || '未指定')} |
| 标签 | ${escapeTableCell(draft.tags.join(', ') || '无')} |

## 2. 能力说明

${draft.summary.trim()}

## 3. 适用场景

${scenarioContent}

## 4. 参数契约

| 参数 | 类型 | 必填 | 纳入资产 | 当前默认值 | 说明 |
| --- | --- | --- | --- | --- | --- |
${parameterRows.join('\n')}

## 5. Render JSON 示例

\`\`\`json
${JSON.stringify(renderExample, null, 2)}
\`\`\`

## 6. 事件契约

| 事件 | 说明 |
| --- | --- |
${eventRows}

## 7. 使用指引与限制

${draft.usageGuide.trim() || '暂未补充使用指引。'}

### 限制与注意事项

${limitationContent}

## 8. 知识资产信息

- 是否生成知识库文档：${draft.generateKnowledgeDocument ? '是' : '否'}
- 目标知识库 ID：${draft.knowledgeBaseId.trim() || draft.knowledgeBaseCode.trim() || '由系统设置解析'}
- 知识库设置键：${RENDER_COMPONENT_KNOWLEDGE_BASE_SETTING_KEY}
- 文档编码：${getKnowledgeDocumentCode(draft.key)}
- 同步流程：保存组件资产后，由管理页面提交知识文档同步；目标知识库 ID 从系统设置解析。${notesContent}
`
}

export function createComponentAssetEditableContent(
  draft: ComponentAssetDraft,
  record: ComponentAssetRecord | null = null,
): ComponentAssetEditableContent {
  const envelope = readComponentAssetEnvelope(record?.exampleJson)
  return {
    docMarkdown: record?.docMarkdown?.trim() || buildComponentAssetDocument(draft),
    renderExampleJson: JSON.stringify(
      envelope?.renderExample || buildComponentAssetRenderExample(draft),
      null,
      2,
    ),
  }
}

export function toComponentAssetSubmission(
  draft: ComponentAssetDraft,
  editedContent: Partial<ComponentAssetEditableContent> = {},
): ComponentAssetSubmission {
  const renderExample = editedContent.renderExampleJson === undefined
    ? buildComponentAssetRenderExample(draft)
    : parseComponentAssetRenderExample(editedContent.renderExampleJson)
  const envelope = buildComponentAssetEnvelope(draft, renderExample)

  return {
    key: draft.key.trim(),
    name: draft.name.trim(),
    category: draft.category.trim() || undefined,
    status: draft.status,
    docMarkdown: editedContent.docMarkdown ?? buildComponentAssetDocument(draft),
    exampleJson: JSON.stringify(envelope, null, 2),
  }
}

export function getComponentAssetCardInfo(record: ComponentAssetRecord) {
  const envelope = readComponentAssetEnvelope(record.exampleJson)
  const definition = findApplicationComponent(envelope?.sourceComponent?.key || record.key)
  const props = envelope?.props || {}
  const knowledgeBaseId = envelope?.asset?.knowledgeBaseId
    || envelope?.asset?.knowledgeBaseCode
    || ''
  return {
    sourceName: envelope?.sourceComponent?.name || definition?.name || '未绑定 Application 组件',
    sourceKey: envelope?.sourceComponent?.key || definition?.key || '',
    parameterCount: Object.keys(props).length || definition?.parameters.filter(item => item.required).length || 0,
    knowledgeBaseId,
    knowledgeBaseSettingKey: envelope?.asset?.knowledgeBaseSettingKey
      || RENDER_COMPONENT_KNOWLEDGE_BASE_SETTING_KEY,
    knowledgeBaseCode: knowledgeBaseId,
    documentCode: envelope?.asset?.documentCode || '',
    isAsset: Boolean(envelope),
  }
}
