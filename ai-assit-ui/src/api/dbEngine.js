import { buildUrl, request } from '../utils/request'
import { getToken } from '../utils/session'

const DB_ENGINE_META_API_PREFIX = '/dbEngine/api/v1/meta/data-source'
const DB_ENGINE_TABLE_META_API_PREFIX = '/dbEngine/api/v1/meta/table'
const DB_ENGINE_FIELD_META_API_PREFIX = '/dbEngine/api/v1/meta/field'

export function searchDbDataSources(payload) {
  return request(`${DB_ENGINE_META_API_PREFIX}/_search`, {
    method: 'POST',
    body: JSON.stringify(payload ?? {})
  })
}

export function createDbDataSource(payload) {
  return request(DB_ENGINE_META_API_PREFIX, {
    method: 'POST',
    body: JSON.stringify(payload ?? {})
  })
}

export function updateDbDataSource(id, payload) {
  return request(`${DB_ENGINE_META_API_PREFIX}/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload ?? {})
  })
}

export function searchDbTables(payload) {
  return request(`${DB_ENGINE_TABLE_META_API_PREFIX}/_search`, {
    method: 'POST',
    body: JSON.stringify(payload ?? {})
  })
}

export function searchDbTableFields(payload) {
  return request(`${DB_ENGINE_FIELD_META_API_PREFIX}/_search`, {
    method: 'POST',
    body: JSON.stringify(payload ?? {})
  })
}

export function importDbMetaWorkbook(sourceKey, file) {
  const formData = new FormData()
  formData.append('file', file)
  if (sourceKey) {
    formData.append('sourceKey', sourceKey)
  }
  return request('/dbEngine/api/v1/meta/workbook/import', {
    method: 'POST',
    body: formData
  })
}

export async function exportDbMetaWorkbook(sourceKey) {
  const token = getToken()
  const response = await fetch(buildUrl(`/dbEngine/api/v1/meta/workbook/export?sourceKey=${encodeURIComponent(sourceKey)}`), {
    method: 'GET',
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    }
  })

  if (!response.ok) {
    const errorText = await response.text().catch(() => '')
    throw new Error(errorText || `Request failed with status ${response.status}`)
  }

  const disposition = response.headers.get('content-disposition') || ''
  const match = disposition.match(/filename\*=UTF-8''([^;]+)/i)
  const filename = match ? decodeURIComponent(match[1]) : 'meta-workbook.xlsx'
  const blob = await response.blob()
  return { blob, filename }
}

export async function downloadDbMetaTemplateWorkbook() {
  const token = getToken()
  const response = await fetch(buildUrl('/dbEngine/api/v1/meta/workbook/template'), {
    method: 'GET',
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    }
  })

  if (!response.ok) {
    const errorText = await response.text().catch(() => '')
    throw new Error(errorText || `Request failed with status ${response.status}`)
  }

  const disposition = response.headers.get('content-disposition') || ''
  const match = disposition.match(/filename\*=UTF-8''([^;]+)/i)
  const filename = match ? decodeURIComponent(match[1]) : 'db-meta-template.xlsx'
  const blob = await response.blob()
  return { blob, filename }
}
