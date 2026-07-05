import { request } from '../../../api/request'
import { getBackendService, SERVICE_NAMES } from '../../../config/services'

const RENDER_COMPONENT_API_PREFIX = `${getBackendService(SERVICE_NAMES.RENDER).gatewayPrefix}/api/v1/render/components`

export type RenderComponentStatus = 'DRAFT' | 'PUBLISHED' | 'DISABLED' | string

export interface RenderComponentItem {
  id: string | number
  key?: string
  name?: string
  category?: string
  status?: RenderComponentStatus
  docMarkdown?: string
  exampleJson?: string
  createTime?: string
  updateTime?: string
  createdBy?: string | number
  updatedBy?: string | number
}

export interface RenderComponentPagePayload {
  page?: number
  size?: number
  keyword?: string
  category?: string
  status?: RenderComponentStatus
}

export interface RenderComponentPageResult {
  list?: RenderComponentItem[]
  pageInfo?: {
    total?: number
  }
}

export interface RenderComponentCategoryItem {
  category?: string
  label?: string
  count?: number
}

export interface RenderComponentSummary {
  total?: number
  published?: number
  draft?: number
  disabled?: number
  categories?: number
}

export function searchRenderComponents(payload: RenderComponentPagePayload = {}) {
  return request<RenderComponentPageResult>(`${RENDER_COMPONENT_API_PREFIX}/page`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function listRenderComponentCategories() {
  return request<RenderComponentCategoryItem[]>(`${RENDER_COMPONENT_API_PREFIX}/categories`, {
    method: 'GET',
  })
}

export function getRenderComponentSummary() {
  return request<RenderComponentSummary>(`${RENDER_COMPONENT_API_PREFIX}/summary`, {
    method: 'GET',
  })
}
