import { request } from '../../../api/request'
import { getBackendService, SERVICE_NAMES } from '../../../config/services'

const USER_API_PREFIX = getBackendService(SERVICE_NAMES.USER).gatewayPrefix
const ERROR_CODE_API_PREFIX = `${USER_API_PREFIX}/api/v1/err-code`
const ERROR_CODE_I18N_API_PREFIX = `${USER_API_PREFIX}/api/v1/err-code-i18n`

export interface ErrorCodeItem {
  id: string | number
  code?: number
  httpStatus?: number
  description?: string
  tags?: string
  createTime?: string
  updateTime?: string
}

export interface ErrorCodeI18nItem {
  id: string | number
  errCode?: number
  locale?: string
  messageTemplate?: string
  description?: string
  createTime?: string
  updateTime?: string
}

export interface ErrorCodeSearchPayload {
  page?: number
  size?: number
  keyword?: string
  code?: number
  httpStatus?: number
  tags?: string
}

export interface ErrorCodeI18nSearchPayload {
  page?: number
  size?: number
  keyword?: string
  errCode?: number
  locale?: string
}

export interface SearchResult<T> {
  list?: T[]
  pageInfo?: {
    total?: number
  }
}

export interface ErrorCodePayload {
  code: number
  httpStatus?: number
  description?: string
  tags?: string
}

export interface ErrorCodeI18nPayload {
  errCode: number
  locale: string
  messageTemplate?: string
  description?: string
}

export interface ErrorCodeImportResult {
  received?: number
  errCodeUpserted?: number
  i18nUpserted?: number
  skipped?: number
}

export interface ErrorCodeExportDocument {
  code?: number
  httpStatus?: number
  description?: string
  tags?: string
  value?: Array<{
    locale?: string
    messageTemplate?: string
    description?: string
  }>
}

export function searchErrorCodes(payload: ErrorCodeSearchPayload = {}) {
  return request<SearchResult<ErrorCodeItem>>(`${ERROR_CODE_API_PREFIX}/_search`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function createErrorCode(payload: ErrorCodePayload) {
  return request(`${ERROR_CODE_API_PREFIX}`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateErrorCode(id: string | number, payload: ErrorCodePayload) {
  return request(`${ERROR_CODE_API_PREFIX}/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteErrorCode(id: string | number) {
  return request(`${ERROR_CODE_API_PREFIX}/${id}`, {
    method: 'DELETE',
  })
}

export function searchErrorCodeI18n(payload: ErrorCodeI18nSearchPayload = {}) {
  return request<SearchResult<ErrorCodeI18nItem>>(`${ERROR_CODE_I18N_API_PREFIX}/_search`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function createErrorCodeI18n(payload: ErrorCodeI18nPayload) {
  return request(`${ERROR_CODE_I18N_API_PREFIX}`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateErrorCodeI18n(id: string | number, payload: ErrorCodeI18nPayload) {
  return request(`${ERROR_CODE_I18N_API_PREFIX}/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteErrorCodeI18n(id: string | number) {
  return request(`${ERROR_CODE_I18N_API_PREFIX}/${id}`, {
    method: 'DELETE',
  })
}

export function importErrorCodeJson(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request<ErrorCodeImportResult>(`${ERROR_CODE_API_PREFIX}/import-json`, {
    method: 'POST',
    body: formData,
  })
}

export function exportErrorCodeJson() {
  return request<ErrorCodeExportDocument[]>(`${ERROR_CODE_API_PREFIX}/export-json`, {
    method: 'GET',
  })
}
