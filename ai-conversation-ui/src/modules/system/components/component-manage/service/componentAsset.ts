import {
  findApplicationComponent,
  type ApplicationComponentDefinition,
  type ApplicationComponentParameter,
} from '../../../../../application/component-manifest'

export type ComponentAssetStatus = string | number

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

interface ComponentAssetEnvelope {
  schemaVersion: 'component-asset/v1'
  sourceComponent: {
    key: string
    name: string
    version: string
    sourcePath: string
  }
  props: Record<string, unknown>
  asset: {
    summary: string
    useCases: string[]
    usageGuide: string
    limitations: string
    notes: string
    tags: string[]
    owner: string
    generateKnowledgeDocument: boolean
    knowledgeBaseCode: string
    documentCode: string
  }
}

const DEFAULT_STATUS = 1

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function toStringArray(value: unknown) {
  return Array.isArray(value) ? value.filter(item => typeof item === 'string') as string[] : []
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
    if (!isRecord(parsed) || parsed.schemaVersion !== 'component-asset/v1') {
      return null
    }
    return parsed as unknown as ComponentAssetEnvelope
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

  return {
    sourceKey,
    key: record?.key || definition?.key || '',
    name: record?.name || definition?.name || '',
    category: record?.category || definition?.category || '',
    status: record?.status ?? DEFAULT_STATUS,
    summary: asset?.summary || definition?.description || '',
    useCases: (asset?.useCases?.length ? asset.useCases : definition?.useCases || []).join('\n'),
    usageGuide: asset?.usageGuide || '先准备组件所需数据，再按参数契约传入 props；业务请求和状态编排由上层页面负责。',
    limitations: asset?.limitations || '',
    notes: asset?.notes || (!envelope && record?.docMarkdown ? record.docMarkdown : ''),
    tags: asset?.tags?.length ? [...asset.tags] : [...(definition?.tags || [])],
    owner: asset?.owner || '',
    generateKnowledgeDocument: asset?.generateKnowledgeDocument ?? true,
    knowledgeBaseCode: asset?.knowledgeBaseCode || 'system-component-assets',
    parameters: createParameterDrafts(definition, envelope?.props || {}),
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
  draft.summary = definition.description
  draft.useCases = definition.useCases.join('\n')
  draft.usageGuide = '先准备组件所需数据，再按参数契约传入 props；业务请求和状态编排由上层页面负责。'
  draft.limitations = ''
  draft.notes = ''
  draft.tags = [...definition.tags]
  draft.parameters = createParameterDrafts(definition)
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

export function buildComponentAssetExampleJson(draft: ComponentAssetDraft) {
  const definition = findApplicationComponent(draft.sourceKey)
  if (!definition) {
    return '{}'
  }
  const envelope: ComponentAssetEnvelope = {
    schemaVersion: 'component-asset/v1',
    sourceComponent: {
      key: definition.key,
      name: definition.name,
      version: definition.version,
      sourcePath: definition.sourcePath,
    },
    props: buildComponentProps(draft),
    asset: {
      summary: draft.summary.trim(),
      useCases: splitLines(draft.useCases),
      usageGuide: draft.usageGuide.trim(),
      limitations: draft.limitations.trim(),
      notes: draft.notes.trim(),
      tags: [...draft.tags],
      owner: draft.owner.trim(),
      generateKnowledgeDocument: draft.generateKnowledgeDocument,
      knowledgeBaseCode: draft.knowledgeBaseCode.trim(),
      documentCode: getKnowledgeDocumentCode(draft.key),
    },
  }
  return JSON.stringify(envelope, null, 2)
}

export function buildComponentAssetDocument(draft: ComponentAssetDraft) {
  const definition = findApplicationComponent(draft.sourceKey)
  if (!definition) {
    return '# 组件数字资产\n\n请先选择 Application 组件。'
  }
  const props = buildComponentProps(draft)
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
| 组件版本 | ${definition.version} |
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

## 5. 当前配置示例

\`\`\`json
${JSON.stringify({ component: definition.key, props }, null, 2)}
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
- 目标知识库：${draft.knowledgeBaseCode.trim() || '未指定'}
- 文档编码：${getKnowledgeDocumentCode(draft.key)}
- 同步状态：待后端同步能力接入${notesContent}
`
}

export function toComponentAssetSubmission(draft: ComponentAssetDraft): ComponentAssetSubmission {
  return {
    key: draft.key.trim(),
    name: draft.name.trim(),
    category: draft.category.trim() || undefined,
    status: draft.status,
    docMarkdown: buildComponentAssetDocument(draft),
    exampleJson: buildComponentAssetExampleJson(draft),
  }
}

export function getComponentAssetCardInfo(record: ComponentAssetRecord) {
  const envelope = readComponentAssetEnvelope(record.exampleJson)
  const definition = findApplicationComponent(envelope?.sourceComponent?.key || record.key)
  const props = envelope?.props || {}
  return {
    sourceName: envelope?.sourceComponent?.name || definition?.name || '未绑定 Application 组件',
    sourceKey: envelope?.sourceComponent?.key || definition?.key || '',
    parameterCount: Object.keys(props).length || definition?.parameters.filter(item => item.required).length || 0,
    knowledgeBaseCode: envelope?.asset?.knowledgeBaseCode || '',
    documentCode: envelope?.asset?.documentCode || '',
    isAsset: Boolean(envelope),
  }
}
