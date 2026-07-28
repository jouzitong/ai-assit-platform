import {
  APPLICATION_COMPONENT_MANIFEST,
  findApplicationComponent,
  type ApplicationComponentDefinition,
  type ApplicationComponentEvent,
  type ApplicationComponentParameter,
  type ApplicationRenderDocument,
} from '../../../../../application/component-manifest'
import {
  assertApplicationComponentRenderDocument,
  getApplicationComponentParameterValueError,
} from '../../../../../application/component-manifest-validation'
import { APPLICATION_LAYOUT_CATALOG } from '../../../../../application/layout/catalog'
import { APPLICATION_STATIC_RENDER_NODE_CATALOG } from '../../../../../application/runtime/node-catalog'

export type ComponentAssetStatus = string | number
export type ComponentAssetDesiredStatus = 1 | 2 | 3

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
  example: unknown
}

export interface ComponentAssetContract {
  parameters: ComponentAssetParameterContract[]
  events: ApplicationComponentEvent[]
}

export type ComponentAssetRenderDocument = ApplicationRenderDocument

export interface ComponentAssetKnowledgeDocumentIdentity {
  knowledgeBaseId: string
  documentCode: string
}

export interface ComponentAssetPendingKnowledgeSync {
  taskCode: string
  componentId: string
  assetKey: string
  documentName: string
  contentFingerprint: string
  desiredStatus: ComponentAssetDesiredStatus
  target: ComponentAssetKnowledgeDocumentIdentity
}

export interface ComponentAssetKnowledgeState {
  current: ComponentAssetKnowledgeDocumentIdentity | null
  pendingCleanup: ComponentAssetKnowledgeDocumentIdentity[]
  pendingSync: ComponentAssetPendingKnowledgeSync | null
}

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
    /** 同步失败暂存为草稿时，保留用户希望最终落地的状态。 */
    desiredStatus?: ComponentAssetDesiredStatus
    knowledgeBaseId: string
    knowledgeBaseSettingKey: typeof RENDER_COMPONENT_KNOWLEDGE_BASE_SETTING_KEY
    /** @deprecated 兼容读取 component-asset/v1 早期数据。 */
    knowledgeBaseCode?: string
    documentCode: string
    /** 新知识文档同步成功前保留的旧文档坐标，用于失败后自动重试清理。 */
    pendingKnowledgeCleanup: ComponentAssetKnowledgeDocumentIdentity[]
    /** 跨刷新、跨标签页恢复在途同步所需的最小任务坐标。 */
    pendingKnowledgeSync: ComponentAssetPendingKnowledgeSync | null
  }
}

const DEFAULT_STATUS = 2
const SUPPORTED_RENDER_PROTOCOL_VERSIONS = new Set(['1.0', '1.0.0'])
const STABLE_RENDER_ID_PATTERN = /^[A-Za-z][A-Za-z0-9_.:-]{0,127}$/
const COMPONENT_VERSION_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._+-]{0,127}$/
const ALLOWED_RENDER_NODE_KEYS = new Set([
  'id',
  'component',
  'componentVersion',
  'props',
  'layout',
  'datasource',
  'bindings',
  'events',
  'actions',
  'children',
])

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function toStringArray(value: unknown) {
  return Array.isArray(value) ? value.filter(item => typeof item === 'string') as string[] : []
}

function readText(value: unknown) {
  return typeof value === 'string' ? value : ''
}

export async function getComponentAssetContentFingerprint(content: string) {
  const digest = await globalThis.crypto.subtle.digest(
    'SHA-256',
    new TextEncoder().encode(content),
  )
  const hex = Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('')
  return `sha256:${hex}`
}

export function normalizeComponentAssetDesiredStatus(
  status: ComponentAssetStatus | undefined,
): ComponentAssetDesiredStatus {
  return parseComponentAssetDesiredStatus(status) ?? DEFAULT_STATUS
}

function parseComponentAssetDesiredStatus(value: unknown): ComponentAssetDesiredStatus | undefined {
  if (value === 1 || value === '1' || value === 'DRAFT') return 1
  if (value === 2 || value === '2' || value === 'PUBLISHED') return 2
  if (value === 3 || value === '3' || value === 'DISABLED') return 3
  return undefined
}

function normalizeKnowledgeDocumentIdentities(
  value: unknown,
): ComponentAssetKnowledgeDocumentIdentity[] {
  if (!Array.isArray(value)) return []
  const identities = value.flatMap((item) => {
    if (!isRecord(item)) return []
    const knowledgeBaseId = readText(item.knowledgeBaseId).trim()
      || readText(item.kbCode).trim()
    const documentCode = readText(item.documentCode).trim()
    return knowledgeBaseId && documentCode ? [{ knowledgeBaseId, documentCode }] : []
  })
  return identities.filter((identity, index) => identities.findIndex(item => (
    item.knowledgeBaseId === identity.knowledgeBaseId
    && item.documentCode === identity.documentCode
  )) === index)
}

function normalizePendingKnowledgeSync(value: unknown): ComponentAssetPendingKnowledgeSync | null {
  if (!isRecord(value)) return null
  const taskCode = readText(value.taskCode).trim()
  const componentId = readText(value.componentId).trim()
  const assetKey = readText(value.assetKey).trim()
  const documentName = readText(value.documentName).trim()
  const contentFingerprint = readText(value.contentFingerprint).trim()
  const target = normalizeKnowledgeDocumentIdentities([value.target])[0]
  const desiredStatus = parseComponentAssetDesiredStatus(value.desiredStatus)
  if (
    !taskCode
    || !componentId
    || !assetKey
    || !documentName
    || !/^sha256:[0-9a-f]{64}$/.test(contentFingerprint)
    || !target
    || desiredStatus === undefined
  ) {
    return null
  }
  return {
    taskCode,
    componentId,
    assetKey,
    documentName,
    contentFingerprint,
    desiredStatus,
    target,
  }
}

function cloneJson<T>(value: T): T {
  if (value === undefined) return value
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
      example: cloneJson(
        Object.prototype.hasOwnProperty.call(props, parameter.key)
          ? props[parameter.key]
          : parameter.defaultValue,
      ),
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
  const protocolVersion = readText(parsed.protocolVersion).trim()
  if (!protocolVersion) {
    throw new Error('Render JSON 缺少 protocolVersion')
  }
  if (!SUPPORTED_RENDER_PROTOCOL_VERSIONS.has(protocolVersion)) {
    throw new Error(`Render JSON 暂不支持 protocolVersion=${protocolVersion}`)
  }
  const pageId = readText(parsed.pageId).trim()
  if (!pageId) {
    throw new Error('Render JSON 缺少 pageId')
  }
  if (!STABLE_RENDER_ID_PATTERN.test(pageId)) {
    throw new Error('Render JSON pageId 不是合法的稳定标识')
  }
  if (parsed.revision !== undefined && !readText(parsed.revision).trim()) {
    throw new Error('Render JSON revision 必须是非空字符串')
  }
  if (!isRecord(parsed.root)) {
    throw new Error('Render JSON 缺少 root 节点')
  }
  const unsupportedRootKey = Object.keys(parsed.root).find(key => !ALLOWED_RENDER_NODE_KEYS.has(key))
  if (unsupportedRootKey) {
    throw new Error(`Render JSON root 包含不支持的字段“${unsupportedRootKey}”`)
  }
  const rootId = readText(parsed.root.id).trim()
  if (!rootId) {
    throw new Error('Render JSON root.id 不能为空')
  }
  if (!STABLE_RENDER_ID_PATTERN.test(rootId)) {
    throw new Error('Render JSON root.id 不是合法的稳定标识')
  }
  const rootComponent = readText(parsed.root.component).trim()
  if (!rootComponent) {
    throw new Error('Render JSON root.component 不能为空')
  }
  if (!STABLE_RENDER_ID_PATTERN.test(rootComponent)) {
    throw new Error('Render JSON root.component 不是合法的 Renderer Key')
  }
  if (
    parsed.root.componentVersion !== undefined
    && !COMPONENT_VERSION_PATTERN.test(readText(parsed.root.componentVersion).trim())
  ) {
    throw new Error('Render JSON root.componentVersion 格式不正确')
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
        desiredStatus: parseComponentAssetDesiredStatus(rawAsset.desiredStatus),
        knowledgeBaseId,
        knowledgeBaseSettingKey: RENDER_COMPONENT_KNOWLEDGE_BASE_SETTING_KEY,
        ...(legacyKnowledgeBaseCode ? { knowledgeBaseCode: legacyKnowledgeBaseCode } : {}),
        documentCode: readText(rawAsset.documentCode),
        pendingKnowledgeCleanup: normalizeKnowledgeDocumentIdentities(
          rawAsset.pendingKnowledgeCleanup,
        ),
        pendingKnowledgeSync: normalizePendingKnowledgeSync(rawAsset.pendingKnowledgeSync),
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
    status: normalizeStatus(
      asset?.pendingKnowledgeSync?.desiredStatus
      ?? asset?.desiredStatus
      ?? record?.status,
    ),
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
      const value = parseParameterValue(parameter, parameterDraft)
      const typeError = getApplicationComponentParameterValueError(value, parameter)
      if (typeError) {
        return `参数“${parameter.label}”${typeError}`
      }
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

function splitLines(value: string) {
  return value.split(/\r?\n/).map(item => item.trim()).filter(Boolean)
}

function normalizeStatus(status: ComponentAssetStatus | undefined) {
  if (status === 1 || status === '1' || status === 'DRAFT') return 1
  if (status === 2 || status === '2' || status === 'PUBLISHED') return 2
  if (status === 3 || status === '3' || status === 'DISABLED') return 3
  return status ?? DEFAULT_STATUS
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

  const props = isRecord(renderExample.root.props)
    ? cloneJson(renderExample.root.props)
    : cloneJson(fallbackProps)
  const normalized = {
    ...renderExample,
    root: {
      ...renderExample.root,
      componentVersion: definition.version,
      props,
    },
  }
  assertApplicationComponentRenderDocument(
    normalized,
    definition,
    APPLICATION_COMPONENT_MANIFEST,
    [...APPLICATION_LAYOUT_CATALOG, ...APPLICATION_STATIC_RENDER_NODE_CATALOG],
  )
  return normalized
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
      desiredStatus: normalizeComponentAssetDesiredStatus(draft.status),
      knowledgeBaseId,
      knowledgeBaseSettingKey: RENDER_COMPONENT_KNOWLEDGE_BASE_SETTING_KEY,
      documentCode: getKnowledgeDocumentCode(draft.key),
      pendingKnowledgeCleanup: [],
      pendingKnowledgeSync: null,
    },
  }
}

export function getComponentAssetKnowledgeState(
  record: ComponentAssetRecord | null | undefined,
): ComponentAssetKnowledgeState {
  const envelope = readComponentAssetEnvelope(record?.exampleJson)
  const knowledgeBaseId = envelope?.asset.knowledgeBaseId.trim() || ''
  const documentCode = envelope?.asset.documentCode.trim()
    || getKnowledgeDocumentCode(record?.key || '')
  return {
    current: knowledgeBaseId && documentCode ? { knowledgeBaseId, documentCode } : null,
    pendingCleanup: normalizeKnowledgeDocumentIdentities(
      envelope?.asset.pendingKnowledgeCleanup,
    ),
    pendingSync: normalizePendingKnowledgeSync(envelope?.asset.pendingKnowledgeSync),
  }
}

export function withComponentAssetPendingKnowledgeCleanup(
  submission: ComponentAssetSubmission,
  identities: readonly ComponentAssetKnowledgeDocumentIdentity[],
): ComponentAssetSubmission {
  const envelope = readComponentAssetEnvelope(submission.exampleJson)
  if (!envelope) {
    throw new Error('组件知识资产数据格式不正确')
  }
  envelope.asset.pendingKnowledgeCleanup = normalizeKnowledgeDocumentIdentities(identities)
  return {
    ...submission,
    exampleJson: JSON.stringify(envelope, null, 2),
  }
}

export function withComponentAssetPendingKnowledgeSync(
  submission: ComponentAssetSubmission,
  attempt: ComponentAssetPendingKnowledgeSync | null,
): ComponentAssetSubmission {
  const envelope = readComponentAssetEnvelope(submission.exampleJson)
  if (!envelope) {
    throw new Error('组件知识资产数据格式不正确')
  }
  envelope.asset.pendingKnowledgeSync = attempt
    ? normalizePendingKnowledgeSync(attempt)
    : null
  if (attempt && !envelope.asset.pendingKnowledgeSync) {
    throw new Error('组件知识同步任务坐标不完整')
  }
  return {
    ...submission,
    exampleJson: JSON.stringify(envelope, null, 2),
  }
}

export function withComponentAssetDesiredStatus(
  submission: ComponentAssetSubmission,
  status: ComponentAssetStatus,
): ComponentAssetSubmission {
  const envelope = readComponentAssetEnvelope(submission.exampleJson)
  if (!envelope) {
    throw new Error('组件知识资产数据格式不正确')
  }
  envelope.asset.desiredStatus = normalizeComponentAssetDesiredStatus(status)
  return {
    ...submission,
    exampleJson: JSON.stringify(envelope, null, 2),
  }
}

export function buildComponentAssetExampleJson(draft: ComponentAssetDraft) {
  if (!findApplicationComponent(draft.sourceKey)) {
    return '{}'
  }
  const envelope = buildComponentAssetEnvelope(draft)
  return JSON.stringify(envelope, null, 2)
}

export function buildComponentAssetDocument(
  draft: ComponentAssetDraft,
  renderExampleInput: ComponentAssetRenderDocument = buildComponentAssetRenderExample(draft),
) {
  const definition = findApplicationComponent(draft.sourceKey)
  if (!definition) {
    return '# 组件数字资产\n\n请先选择 Application 组件。'
  }
  const fallbackProps = buildComponentProps(draft)
  const renderExample = normalizeRenderExampleForDefinition(definition, renderExampleInput, fallbackProps)
  const props = cloneJson(renderExample.root.props || fallbackProps)
  const useCases = splitLines(draft.useCases)
  const parameterRows = definition.parameters.map((parameter) => {
    const state = draft.parameters[parameter.key]
    const included = Object.prototype.hasOwnProperty.call(props, parameter.key)
    const currentValue = included ? JSON.stringify(props[parameter.key]) : '-'
    return `| ${escapeTableCell(parameter.key)} | ${escapeTableCell(parameter.type)} | ${parameter.required ? '是' : '否'} | ${included ? '是' : '否'} | ${escapeTableCell(currentValue)} | ${escapeTableCell(state?.description || parameter.description)} |`
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
    docMarkdown: record?.docMarkdown?.trim()
      || buildComponentAssetDocument(draft, envelope?.renderExample),
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
  const generatedMarkdown = buildComponentAssetDocument(draft)
  const editedMarkdown = editedContent.docMarkdown ?? generatedMarkdown
  const docMarkdown = editedMarkdown.trim() === generatedMarkdown.trim()
    ? buildComponentAssetDocument(draft, envelope.renderExample)
    : editedMarkdown

  return {
    key: draft.key.trim(),
    name: draft.name.trim(),
    category: draft.category.trim() || undefined,
    status: draft.status,
    docMarkdown,
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
