import { request } from '../../../api/request'
import { getBackendService, SERVICE_NAMES } from '../../../config/services'

const RENDER_PAGE_API_PREFIX = `${getBackendService(SERVICE_NAMES.RENDER).gatewayPrefix}/api/v1/render/pages`
const RENDER_PAGE_CATEGORY_API_PREFIX = `${getBackendService(SERVICE_NAMES.RENDER).gatewayPrefix}/api/v1/render/page-categories`

export type RenderPageStatus = 1 | 2 | 3 | 'DRAFT' | 'PUBLISHED' | 'DISABLED' | string | number
export type RenderPageContent = Record<string, unknown>

export interface RenderPageItem {
  id: string | number
  code?: string
  name?: string
  categoryCode?: string
  status?: RenderPageStatus
  content?: RenderPageContent
  createTime?: string
  updateTime?: string
  createdBy?: string | number
  updatedBy?: string | number
}

export interface RenderPageQueryPayload {
  page?: number
  size?: number
  keyword?: string
  categoryCode?: string
  status?: RenderPageStatus
}

export interface RenderPagePageResult {
  list?: RenderPageItem[]
  pageInfo?: {
    total?: number
  }
}

export interface RenderPageCategoryTreeItem {
  id?: string | number
  code?: string
  name?: string
  parentCode?: string
  path?: string
  sortNo?: number
  enabled?: boolean
  children?: RenderPageCategoryTreeItem[]
  pages?: RenderPageItem[]
}

export interface RenderPageCategoryUpsertPayload {
  code: string
  name: string
  parentCode?: string
  path: string
  sortNo?: number
  enabled: boolean
}

export interface RenderPageTreeResult {
  categories?: RenderPageCategoryTreeItem[]
  uncategorizedPages?: RenderPageItem[]
}

export interface RenderPageUpsertPayload {
  code: string
  name: string
  categoryCode?: string
  status?: RenderPageStatus
  content?: RenderPageContent
}

export function searchRenderPages(payload: RenderPageQueryPayload = {}) {
  return request<RenderPagePageResult>(`${RENDER_PAGE_API_PREFIX}/page`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getRenderPageTree(payload: RenderPageQueryPayload & { enabled?: boolean } = {}) {
  return request<RenderPageTreeResult>(`${RENDER_PAGE_API_PREFIX}/tree`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function listRenderPageCategories(payload: { keyword?: string; parentCode?: string; enabled?: boolean } = {}) {
  return request<RenderPageCategoryTreeItem[]>(`${RENDER_PAGE_CATEGORY_API_PREFIX}/tree`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function createRenderPageCategory(payload: RenderPageCategoryUpsertPayload) {
  return request<RenderPageCategoryTreeItem>(`${RENDER_PAGE_CATEGORY_API_PREFIX}`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateRenderPageCategory(id: string | number, payload: Partial<RenderPageCategoryUpsertPayload>) {
  return request<RenderPageCategoryTreeItem>(`${RENDER_PAGE_CATEGORY_API_PREFIX}/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteRenderPageCategory(id: string | number) {
  return request<boolean>(`${RENDER_PAGE_CATEGORY_API_PREFIX}/${id}`, {
    method: 'DELETE',
  })
}

export function updateRenderPage(id: string | number, payload: RenderPageUpsertPayload) {
  return request<RenderPageItem>(`${RENDER_PAGE_API_PREFIX}/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function createRenderPage(payload: RenderPageUpsertPayload) {
  return request<RenderPageItem>(`${RENDER_PAGE_API_PREFIX}`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function deleteRenderPage(id: string | number) {
  return request<boolean>(`${RENDER_PAGE_API_PREFIX}/${id}`, {
    method: 'DELETE',
  })
}
