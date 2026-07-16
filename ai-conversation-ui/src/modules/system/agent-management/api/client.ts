import { request } from '../../../../api/request'
import { getBackendService, SERVICE_NAMES } from '../../../../config/services'
import type {
  CatalogItem,
  CatalogQuery,
  DefinitionVersionResponse,
  PageResult,
  ValidationReport,
} from '../types'

const CHAT_API_PREFIX = getBackendService(SERVICE_NAMES.CHAT).gatewayPrefix

export function agentManagementPath(path: string) {
  return `${CHAT_API_PREFIX}/api/v1/ai${path}`
}

export function listCatalog<T extends CatalogItem>(resource: string, query: CatalogQuery = {}) {
  return request<PageResult<T> | T[]>(agentManagementPath(`/${resource}`), {
    method: 'GET',
    query,
  })
}

export function getCatalog<T>(resource: string, code: string) {
  return request<T>(agentManagementPath(`/${resource}/${encodeURIComponent(code)}`), {
    method: 'GET',
  })
}

export function createCatalog<TPayload, TResult>(resource: string, payload: TPayload) {
  return request<TResult>(agentManagementPath(`/${resource}`), {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateCatalog<TPayload, TResult>(resource: string, code: string, payload: TPayload) {
  return request<TResult>(agentManagementPath(`/${resource}/${encodeURIComponent(code)}`), {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteCatalog(resource: string, code: string) {
  return request<boolean>(agentManagementPath(`/${resource}/${encodeURIComponent(code)}`), {
    method: 'DELETE',
  })
}

export function createDefinitionVersion<TPayload, TResult>(resource: string, code: string, payload: TPayload) {
  return request<DefinitionVersionResponse<TResult>>(agentManagementPath(`/${resource}/${encodeURIComponent(code)}/versions`), {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function validateDefinition(resource: string, code: string, version: number) {
  return request<ValidationReport>(agentManagementPath(`/${resource}/${encodeURIComponent(code)}/versions/${version}/validate`), {
    method: 'POST',
  })
}

export function publishDefinition(resource: string, code: string, version: number) {
  return request<CatalogItem>(agentManagementPath(`/${resource}/${encodeURIComponent(code)}/versions/${version}/publish`), {
    method: 'POST',
  })
}

export function testDefinition(resource: string, code: string, version: number, payload: Record<string, unknown> = {}) {
  return request<Record<string, unknown>>(agentManagementPath(`/${resource}/${encodeURIComponent(code)}/versions/${version}/test-runs`), {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}
