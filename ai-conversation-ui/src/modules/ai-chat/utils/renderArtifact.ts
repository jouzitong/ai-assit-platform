import type { ChatArtifact } from '../types'

export interface RenderJsonNode {
  id: string
  component: string
  componentVersion: string
  props?: Record<string, unknown>
  layout?: Record<string, unknown>
  datasource?: Record<string, unknown>
  bindings?: Record<string, unknown>
  events?: unknown[]
  actions?: unknown[]
  children?: RenderJsonNode[]
}

export interface RenderJsonDocument {
  protocol: 'render-json'
  protocolVersion: string
  pageId: string
  revision?: string
  root: RenderJsonNode
}

export interface RenderArtifactResult {
  document: RenderJsonDocument | null
  error: string | null
}

const DEFAULT_REFERENCE_SIZE = Object.freeze({ width: 1200, height: 720 })

export function isRenderJsonArtifact(artifact: ChatArtifact) {
  return artifact.artifactType?.trim().toUpperCase() === 'RENDER_JSON'
}

export function normalizeRenderArtifact(artifact: ChatArtifact): RenderArtifactResult {
  if (!isRenderJsonArtifact(artifact)) {
    return { document: null, error: '当前产物不是 Render JSON 页面。' }
  }

  let content = artifact.content
  if (typeof content === 'string') {
    try {
      content = JSON.parse(content)
    } catch {
      return { document: null, error: 'Render JSON 内容格式不合法。' }
    }
  }

  if (!isRecord(content)) {
    return { document: null, error: 'Render JSON 内容为空或不是对象。' }
  }

  if (isRecord(content.root)) {
    const root = normalizeNode(content.root, artifact)
    if (!root) {
      return { document: null, error: 'Render JSON 根节点缺少 component。' }
    }
    return {
      document: {
        protocol: 'render-json',
        protocolVersion: String(content.protocolVersion || '1.0.0'),
        pageId: String(content.pageId || artifact.artifactCode || artifact.codeRef || root.id),
        ...(content.revision ? { revision: String(content.revision) } : {}),
        root,
      },
      error: null,
    }
  }

  const root = normalizeNode(content, artifact)
  if (!root) {
    return { document: null, error: 'Render JSON 节点缺少 component。' }
  }

  return {
    document: {
      protocol: 'render-json',
      protocolVersion: '1.0.0',
      pageId: artifact.artifactCode || artifact.codeRef || root.id,
      root,
    },
    error: null,
  }
}

export function resolveRenderReferenceSize(document: RenderJsonDocument | null) {
  const referenceSize = document?.root.layout?.referenceSize
  if (!isRecord(referenceSize)) {
    return DEFAULT_REFERENCE_SIZE
  }

  const width = toPositiveNumber(referenceSize.width)
  const height = toPositiveNumber(referenceSize.height)
  return {
    width: width || DEFAULT_REFERENCE_SIZE.width,
    height: height || DEFAULT_REFERENCE_SIZE.height,
  }
}

function normalizeNode(value: Record<string, unknown>, artifact: ChatArtifact): RenderJsonNode | null {
  if (typeof value.component !== 'string' || !value.component.trim()) {
    return null
  }

  const children = Array.isArray(value.children)
    ? value.children
        .filter(isRecord)
        .map(child => normalizeNode(child, artifact))
        .filter((child): child is RenderJsonNode => Boolean(child))
    : undefined

  return {
    id: String(value.id || artifact.artifactCode || artifact.codeRef || 'generated-page-root'),
    component: value.component.trim(),
    componentVersion: String(value.componentVersion || '1.0.0'),
    ...(isRecord(value.props) ? { props: value.props } : {}),
    ...(isRecord(value.layout) ? { layout: value.layout } : {}),
    ...(isRecord(value.datasource) ? { datasource: value.datasource } : {}),
    ...(isRecord(value.bindings) ? { bindings: value.bindings } : {}),
    ...(Array.isArray(value.events) ? { events: value.events } : {}),
    ...(Array.isArray(value.actions) ? { actions: value.actions } : {}),
    ...(children?.length ? { children } : {}),
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function toPositiveNumber(value: unknown) {
  const parsed = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : 0
}
