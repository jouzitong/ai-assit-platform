import {
  isRenderAppMode,
  normalizeRenderRuntimeDocument,
  type RenderAppMode,
  type RenderRuntimeDocument,
} from '../../render/model/render-app'
import type { ChatArtifact } from '../types'

export interface RenderArtifactReference {
  pageCode: string
  layout: RenderAppMode
}

export interface RenderArtifactResult {
  reference: RenderArtifactReference | null
  document: RenderRuntimeDocument | null
  error: string | null
}

const DEFAULT_REFERENCE_SIZE = Object.freeze({ width: 1200, height: 720 })
const RENDER_REFERENCE_KEYS = new Set(['pageCode', 'layout'])

export function isRenderJsonArtifact(artifact: ChatArtifact) {
  return artifact.artifactType?.trim().toUpperCase() === 'RENDER_JSON'
}

export function normalizeRenderArtifact(artifact: ChatArtifact): RenderArtifactResult {
  if (!isRenderJsonArtifact(artifact)) {
    return emptyResult('当前产物不是 Render JSON 页面。')
  }

  let content = artifact.content
  if (typeof content === 'string') {
    try {
      content = JSON.parse(content)
    } catch {
      return emptyResult('Render JSON 内容格式不合法。')
    }
  }

  if (!isRecord(content)) {
    return emptyResult('Render JSON 内容为空或不是对象。')
  }

  if (isRenderArtifactReference(content)) {
    return {
      reference: {
        // pageCode 是 Render 服务中的完整编码，不能补充或裁剪后缀。
        pageCode: content.pageCode,
        layout: isRenderAppMode(content.layout) ? content.layout : 'standard',
      },
      document: null,
      error: null,
    }
  }

  try {
    const fallbackCode = artifact.artifactCode || artifact.codeRef || 'generated-page'
    return {
      reference: null,
      document: normalizeRenderRuntimeDocument(content, fallbackCode),
      error: null,
    }
  } catch (error) {
    return emptyResult(error instanceof Error ? error.message : 'Render JSON 内容无法解析。')
  }
}

export function resolveRenderReferenceSize(document: RenderRuntimeDocument | null) {
  const referenceSize = document?.root.layout
  if (!isRecord(referenceSize) || !isRecord(referenceSize.referenceSize)) {
    return DEFAULT_REFERENCE_SIZE
  }

  const width = toPositiveNumber(referenceSize.referenceSize.width)
  const height = toPositiveNumber(referenceSize.referenceSize.height)
  return {
    width: width || DEFAULT_REFERENCE_SIZE.width,
    height: height || DEFAULT_REFERENCE_SIZE.height,
  }
}

function isRenderArtifactReference(
  value: Record<string, unknown>,
): value is Record<string, unknown> & { pageCode: string; layout?: unknown } {
  return typeof value.pageCode === 'string'
    && Boolean(value.pageCode.trim())
    && Object.keys(value).every(key => RENDER_REFERENCE_KEYS.has(key))
}

function emptyResult(error: string): RenderArtifactResult {
  return { reference: null, document: null, error }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function toPositiveNumber(value: unknown) {
  const parsed = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : 0
}
