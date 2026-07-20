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
  root: Record<string, unknown>
}

export interface RenderModeHostProps {
  title: string
  description?: string
  loading?: boolean
  refreshable?: boolean
  lastRefreshedAt?: string
  responsivePreset?: string
}

const RENDER_APP_MODE_SET = new Set<string>(RENDER_APP_MODES)
const RENDER_APP_CODE_PATTERN = /^[A-Za-z0-9._-]+$/
const SUPPORTED_PROTOCOL_MAJOR = '1'

export function isRenderAppMode(value: unknown): value is RenderAppMode {
  return typeof value === 'string' && RENDER_APP_MODE_SET.has(value)
}

export function normalizeRenderAppCode(value: unknown) {
  if (typeof value !== 'string') {
    throw new Error('Render JSON code 不能为空')
  }

  const code = value.trim().replace(/\.json$/i, '')
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
  if (protocolVersion.split('.')[0] !== SUPPORTED_PROTOCOL_MAJOR) {
    throw new Error(`暂不支持 Render JSON 协议版本 ${protocolVersion}`)
  }

  const root = resolveRootNode(content)
  if (!root) {
    throw new Error('Render JSON 缺少可渲染的 root 节点')
  }

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
    root,
  }
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
