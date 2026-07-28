import { request } from '../../../api/request'
import { getBackendService, SERVICE_NAMES } from '../../../config/services'

const USER_API_PREFIX = getBackendService(SERVICE_NAMES.USER).gatewayPrefix
const SYSTEM_SETTINGS_API_PREFIX = `${USER_API_PREFIX}/api/v1/system-settings`

export interface SystemSettingItem {
  id: string | number
  settingKey?: string
  description?: string
  settingValue?: string
  valueType?: string
  enabled?: boolean
  createTime?: string
  updateTime?: string
  createdBy?: string
  updatedBy?: string
  lastModifiedBy?: string
}

export interface SystemSettingSearchPayload {
  page?: number
  size?: number
  keyword?: string
  settingKey?: string
  valueType?: string
  enabled?: boolean
}

export interface SystemSettingSearchResult {
  list?: SystemSettingItem[]
  pageInfo?: {
    total?: number
  }
}

export interface SystemSettingUpsertPayload {
  settingKey: string
  description?: string
  settingValue: string
  valueType: string
  enabled: boolean
}

export interface SystemSettingTransferDocument {
  settingKey?: string
  description?: string
  settingValue?: string
  valueType?: string
  enabled?: boolean
}

export interface SystemSettingImportResult {
  received?: number
  created?: number
  updated?: number
  skipped?: number
}

export interface SystemSettingExportPayload {
  settingKeys?: string[]
  keyword?: string
}

export function searchSystemSettings(payload: SystemSettingSearchPayload = {}) {
  return request<SystemSettingSearchResult>(`${SYSTEM_SETTINGS_API_PREFIX}/_search`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function createSystemSetting(payload: SystemSettingUpsertPayload) {
  return request(`${SYSTEM_SETTINGS_API_PREFIX}`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function editSystemSetting(id: string | number, payload: Partial<SystemSettingUpsertPayload>) {
  return request(`${SYSTEM_SETTINGS_API_PREFIX}/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  })
}

export function updateSystemSetting(id: string | number, payload: SystemSettingUpsertPayload) {
  return request(`${SYSTEM_SETTINGS_API_PREFIX}/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteSystemSetting(id: string | number) {
  return request(`${SYSTEM_SETTINGS_API_PREFIX}/${id}`, {
    method: 'DELETE',
  })
}

export function importSystemSettingsJson(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request<SystemSettingImportResult>(`${SYSTEM_SETTINGS_API_PREFIX}/import-json`, {
    method: 'POST',
    body: formData,
  })
}

export function exportSystemSettingsJson(payload: SystemSettingExportPayload = {}) {
  return request<SystemSettingTransferDocument[]>(`${SYSTEM_SETTINGS_API_PREFIX}/export-json`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}
