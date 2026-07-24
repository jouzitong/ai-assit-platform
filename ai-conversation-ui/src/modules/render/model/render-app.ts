import type { RendererAction, RendererFilter } from '../../../application/schema'

export const RENDER_APP_MODES = [
  'standard',
  'dashboard',
  'report',
  'embedded',
] as const

export type RenderAppMode = typeof RENDER_APP_MODES[number]

export interface RenderDocumentPresentation {
  defaultMode?: RenderAppMode
  allowedModes?: RenderAppMode[]
  title?: string
  description?: string
  refreshInterval?: number
  responsivePreset?: string
  readonly?: boolean
}

export interface RenderRuntimeDocument extends Record<string, unknown> {
  protocol: 'render-json'
  protocolVersion: string
  pageId: string
  revision?: string
  title?: string
  presentation?: RenderDocumentPresentation
  actions?: RendererAction[]
  filters?: RendererFilter[]
  root: Record<string, unknown>
}

export interface RenderModeHostProps {
  title: string
  description?: string
  loading?: boolean
  refreshable?: boolean
  lastRefreshedAt?: string
  responsivePreset?: string
  compact?: boolean
  actions?: RendererAction[]
  filters?: RendererFilter[]
  filterValues?: Record<string, unknown>
}

const RENDER_APP_MODE_SET = new Set<string>(RENDER_APP_MODES)
const RENDER_APP_CODE_PATTERN = /^[A-Za-z0-9._-]+$/
const SUPPORTED_PROTOCOL_MAJORS = new Set(['1', '2'])
const PREVIEW_MODEL_DATASOURCE_TYPES = new Set(['db-query-list', 'semantic-query'])
const UNSAFE_OBJECT_KEYS = new Set(['__proto__', 'prototype', 'constructor'])

export function isRenderAppMode(value: unknown): value is RenderAppMode {
  return typeof value === 'string' && RENDER_APP_MODE_SET.has(value)
}

export function normalizeRenderAppCode(value: unknown) {
  if (typeof value !== 'string') {
    throw new Error('Render JSON code 不能为空')
  }

  const code = value.trim()
  if (!code || !RENDER_APP_CODE_PATTERN.test(code)) {
    throw new Error('Render JSON code 仅支持字母、数字、点、下划线和短横线')
  }
  return code
}

export function normalizeRenderRuntimeDocument(
  content: Record<string, unknown>,
  code: string,
): RenderRuntimeDocument {
  const protocol = readString(content.protocol)
  if (protocol && protocol !== 'render-json') {
    throw new Error(`不支持的渲染协议: ${protocol}`)
  }

  const protocolVersion = readString(content.protocolVersion) || '1.0.0'
  if (!SUPPORTED_PROTOCOL_MAJORS.has(protocolVersion.split('.')[0])) {
    throw new Error(`暂不支持 Render JSON 协议版本 ${protocolVersion}`)
  }

  const root = resolveRootNode(content)
  if (!root) {
    throw new Error('Render JSON 缺少可渲染的 root 节点')
  }

  const normalizedRoot = normalizeReportRoot(root, content)

  const presentation = resolveRenderDocumentPresentation(content)
  const title = readString(content.title) || presentation.title

  return {
    ...content,
    protocol: 'render-json',
    protocolVersion,
    pageId: readString(content.pageId) || code,
    ...(readString(content.revision) ? { revision: readString(content.revision) } : {}),
    ...(title ? { title } : {}),
    ...(hasPresentationValue(presentation) ? { presentation } : {}),
    ...(Array.isArray(content.actions)
      ? { actions: content.actions.filter(isRecord) as RendererAction[] }
      : {}),
    ...(Array.isArray(content.filters)
      ? { filters: content.filters.filter(isRecord) as RendererFilter[] }
      : {}),
    root: normalizedRoot,
  }
}

/**
 * 为元数据预览创建运行时副本：替换 :model 占位符，并将查询型 datasource
 * 绑定到本次选择的虚拟模型。原始 Render Meta 不会被修改。
 */
export function applyRenderPreviewModel(
  content: Record<string, unknown>,
  model: string,
): Record<string, unknown> {
  const normalizedModel = model.trim()
  if (!normalizedModel) {
    return content
  }

  const transform = (value: unknown): unknown => {
    if (typeof value === 'string') {
      return value.includes(':model')
        ? value.replaceAll(':model', normalizedModel)
        : value
    }
    if (Array.isArray(value)) {
      return value.map(transform)
    }
    if (!isRecord(value)) {
      return value
    }

    const result: Record<string, unknown> = {}
    Object.entries(value).forEach(([key, child]) => {
      if (!UNSAFE_OBJECT_KEYS.has(key)) {
        result[key] = transform(child)
      }
    })
    if (
      typeof result.type === 'string'
      && PREVIEW_MODEL_DATASOURCE_TYPES.has(result.type)
    ) {
      result.model = normalizedModel
    }
    return result
  }

  return transform(content) as Record<string, unknown>
}

export function resolveRenderDocumentPresentation(
  content: Record<string, unknown>,
): RenderDocumentPresentation {
  const rawPresentation = isRecord(content.presentation)
    ? content.presentation
    : isRecord(content.page)
      ? content.page
      : {}

  const allowedModes = Array.isArray(rawPresentation.allowedModes)
    ? rawPresentation.allowedModes.filter(isRenderAppMode)
    : undefined
  const defaultMode = isRenderAppMode(rawPresentation.defaultMode)
    ? rawPresentation.defaultMode
    : undefined
  const refreshInterval = toPositiveNumber(rawPresentation.refreshInterval)

  return {
    ...(defaultMode ? { defaultMode } : {}),
    ...(allowedModes?.length ? { allowedModes } : {}),
    ...(readString(rawPresentation.title) ? { title: readString(rawPresentation.title) } : {}),
    ...(readString(rawPresentation.description)
      ? { description: readString(rawPresentation.description) }
      : {}),
    ...(refreshInterval ? { refreshInterval } : {}),
    ...(readString(rawPresentation.responsivePreset)
      ? { responsivePreset: readString(rawPresentation.responsivePreset) }
      : {}),
    ...(typeof rawPresentation.readonly === 'boolean'
      ? { readonly: rawPresentation.readonly }
      : {}),
  }
}

export function assertRenderModeAllowed(
  mode: RenderAppMode,
  presentation?: RenderDocumentPresentation,
) {
  if (presentation?.allowedModes?.length && !presentation.allowedModes.includes(mode)) {
    throw new Error(`当前 Render JSON 不允许使用 ${mode} 模式打开`)
  }
}

function resolveRootNode(content: Record<string, unknown>) {
  if (isRecord(content.root)) {
    return normalizeRootNode(content.root)
  }

  if (isRendererNode(content)) {
    return wrapRendererSchema(content)
  }

  if (isRecord(content.schema) && isRendererNode(content.schema)) {
    return wrapRendererSchema(content.schema)
  }

  return null
}

function normalizeRootNode(root: Record<string, unknown>) {
  if (isRendererNode(root)) {
    if (isRecord(root.schema)) {
      return {
        ...root,
        props: {
          ...(isRecord(root.props) ? root.props : {}),
          schema: root.schema,
        },
      }
    }
    return root
  }

  if (isRecord(root.schema) && isRendererNode(root.schema)) {
    return wrapRendererSchema(root.schema)
  }

  return null
}

/**
 * Report JSON 以 components[] 描述页面内容；Runtime 内部仍使用 root.children
 * 递归渲染。这里做一次纯数据归一化，不把 report 协议细节下沉到 Renderer。
 */
function normalizeReportRoot(
  root: Record<string, unknown>,
  content: Record<string, unknown>,
) {
  if (!Array.isArray(content.components) || content.components.length === 0) {
    return root
  }

  const existingChildren = Array.isArray(root.children)
    ? root.children.filter(isRecord)
    : []
  const reportChildren = content.components
    .map((component, index) => normalizeReportComponent(component, index))
    .filter((component): component is Record<string, unknown> => Boolean(component))
  const layout = normalizeReportRootLayout(root.layout)

  return {
    ...root,
    layout,
    children: [...existingChildren, ...reportChildren],
  }
}

function normalizeReportComponent(value: unknown, index: number) {
  if (!isRecord(value)) {
    return null
  }

  const component = readString(value.component)
  if (!component) {
    return null
  }

  const id = readString(value.id) || `${component}-${index}`
  const rawProps = isRecord(value.props) ? { ...value.props } : {}
  const layout = normalizeReportComponentLayout(value.layout)
  const node: Record<string, unknown> = {
    id,
    component,
    ...(readString(value.componentVersion)
      ? { componentVersion: readString(value.componentVersion) }
      : {}),
    props: rawProps,
    ...(layout ? { layout } : {}),
  }

  const datasource = resolveReportDatasource(value, rawProps)
  if (datasource) {
    node.datasource = datasource
  }

  if (
    isListOrFormComponent(component)
    && !readString(rawProps.title)
    && readString(value.title)
  ) {
    node.props = {
      ...rawProps,
      title: readString(value.title),
    }
  }

  return node
}

function isListOrFormComponent(component: string) {
  return [
    'zg-list-main-layout',
    'list-main-layout',
    'zg-common-list',
    'zg-common-tree-list',
    'common-list',
    'common-tree-list',
    'form-main-layout',
    'zg-common-form',
    'zg-common-info',
    'common-form',
    'common-info',
  ].includes(component.toLowerCase())
}

function resolveReportDatasource(
  component: Record<string, unknown>,
  props: Record<string, unknown>,
) {
  if (isRecord(props.datasource)) {
    return props.datasource
  }
  if (isRecord(component.datasource)) {
    return component.datasource
  }
  if (Array.isArray(component.datasources)) {
    return component.datasources.find(isRecord) || undefined
  }
  return undefined
}

function normalizeReportRootLayout(value: unknown) {
  if (!isRecord(value)) {
    return value
  }

  const layout = { ...value }
  const columns = toPositiveInteger(layout.columns)
  if (columns && !layout.gridTemplateColumns) {
    layout.gridTemplateColumns = `repeat(${columns}, minmax(0, 1fr))`
  }

  const rows = toPositiveInteger(layout.rows) || 12
  if (!layout.gridTemplateRows) {
    layout.gridTemplateRows = `repeat(${rows}, minmax(0, 1fr))`
  }
  if (layout.height === undefined && layout.minHeight === undefined) {
    layout.height = '100%'
  }

  const rowHeight = toPositiveNumber(layout.rowHeight)
  if (rowHeight && !layout.gridAutoRows) {
    layout.gridAutoRows = `${rowHeight}px`
  }

  return layout
}

function normalizeReportComponentLayout(value: unknown) {
  if (Array.isArray(value) && value.length >= 4) {
    const [row, column, width, height] = value.map(toPositiveInteger)
    if (row && column && width && height) {
      return {
        gridColumn: `${column} / span ${width}`,
        gridRow: `${row} / span ${height}`,
      }
    }
  }

  return isRecord(value) ? { ...value } : undefined
}

function toPositiveInteger(value: unknown) {
  const numberValue = Number(value)
  return Number.isInteger(numberValue) && numberValue > 0 ? numberValue : undefined
}

function wrapRendererSchema(schema: Record<string, unknown>) {
  return {
    id: readString(schema.id) || 'root',
    component: schema.component,
    props: {
      schema,
    },
  }
}

function isRendererNode(value: Record<string, unknown>) {
  return typeof value.component === 'string' && value.component.trim().length > 0
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function readString(value: unknown) {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined
}

function toPositiveNumber(value: unknown) {
  const numberValue = Number(value)
  return Number.isFinite(numberValue) && numberValue > 0 ? numberValue : undefined
}

function hasPresentationValue(value: RenderDocumentPresentation) {
  return Object.keys(value).length > 0
}
