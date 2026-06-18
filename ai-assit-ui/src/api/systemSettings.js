import { request } from '../utils/request'

const SYSTEM_SETTINGS_API_PREFIX = '/user/api/v1/system-settings'

export function searchSystemSettings(payload) {
  return request(`${SYSTEM_SETTINGS_API_PREFIX}/_search`, {
    method: 'POST',
    body: JSON.stringify(payload ?? {})
  })
}

export function createSystemSetting(payload) {
  return request(SYSTEM_SETTINGS_API_PREFIX, {
    method: 'POST',
    body: JSON.stringify(payload ?? {})
  })
}

export function updateSystemSetting(id, payload) {
  return request(`${SYSTEM_SETTINGS_API_PREFIX}/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload ?? {})
  })
}

export function editSystemSetting(id, payload) {
  return request(`${SYSTEM_SETTINGS_API_PREFIX}/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(payload ?? {})
  })
}

export function deleteSystemSetting(id) {
  return request(`${SYSTEM_SETTINGS_API_PREFIX}/${id}`, {
    method: 'DELETE'
  })
}

export function getSystemSetting(id) {
  return request(`${SYSTEM_SETTINGS_API_PREFIX}/${id}`, {
    method: 'GET'
  })
}
