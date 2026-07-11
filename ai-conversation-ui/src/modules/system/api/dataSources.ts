import { request, requestRaw } from '../../../api/request'
import { getBackendService, SERVICE_NAMES } from '../../../config/services'

const DB_ENGINE_API_PREFIX = getBackendService(SERVICE_NAMES.DB_ENGINE).gatewayPrefix
const DATA_SOURCE_API_PREFIX = `${DB_ENGINE_API_PREFIX}/api/v1/meta/data-source`
const TABLE_META_API_PREFIX = `${DB_ENGINE_API_PREFIX}/api/v1/meta/table`
const DB_ACCESS_API_PREFIX = `${DB_ENGINE_API_PREFIX}/api/v1/db/access`
const WORKBOOK_API_PREFIX = `${DB_ENGINE_API_PREFIX}/api/v1/meta/workbook`

export type DataSourceConfigType = 'DATABASE' | 'HTTP_API' | 'SERVICE_API' | 'FILE' | 'STREAM'
export type DatabaseConnectionMode = 'JDBC_URL' | 'HOST_PORT'

export interface DbDataSourceCredential {
  authType?: string | number
  username?: string
  passwordCiphertext?: string
  tokenCiphertext?: string
  accessKey?: string
  secretKeyCiphertext?: string
  credentialRef?: string
}

export interface DatabaseSourceConfig {
  configType?: 'DATABASE'
  configVersion?: number
  dbType?: string | number
  connection?: {
    mode?: DatabaseConnectionMode
    jdbcUrl?: string
    host?: string
    port?: number
    databaseName?: string
    schemaName?: string
  }
  credential?: DbDataSourceCredential
  network?: {
    connectTimeoutMs?: number
    readTimeoutMs?: number
    writeTimeoutMs?: number
  }
  driverProperties?: unknown
}

export interface HttpApiSourceConfig {
  configType?: 'HTTP_API'
  configVersion?: number
  baseUrl?: string
  credential?: DbDataSourceCredential
  network?: {
    connectTimeoutMs?: number
    readTimeoutMs?: number
    writeTimeoutMs?: number
  }
  attributes?: unknown
}

interface LegacyDataSourceConfig {
  dbType?: string | number
  endpoint?: string
  network?: DatabaseSourceConfig['network']
  auth?: DbDataSourceCredential
  attributes?: unknown
  database?: {
    dbType?: string | number
    jdbcUrl?: string
    host?: string
    port?: number
    databaseName?: string
    schemaName?: string
  }
}

export type DbDataSourceConfig = DatabaseSourceConfig | HttpApiSourceConfig | LegacyDataSourceConfig

export interface DbDataSourceItem {
  id: string | number
  sourceKey?: string
  sourceName?: string
  sourceType?: string | number
  ownerTeam?: string
  ownerUser?: string
  enabled?: boolean
  syncMode?: string
  summary?: string
  remark?: string
  lastSyncAt?: string
  lastAccessAt?: string
  config?: DbDataSourceConfig
}

export interface DbDataSourceSearchPayload {
  page?: number
  size?: number
  keyword?: string
  sourceKey?: string
  sourceType?: string | number
  ownerTeam?: string
  enabled?: boolean
}

export interface DbDataSourceSearchResult {
  list?: DbDataSourceItem[]
  pageInfo?: {
    total?: number
  }
}

export interface DbDataSourceUpsertPayload {
  sourceKey: string
  sourceName: string
  sourceType: string | number
  ownerTeam?: string
  ownerUser?: string
  enabled: boolean
  syncMode: string | number
  summary?: string
  remark?: string
  config: DbDataSourceConfig
}

export interface KnowledgeSyncPayload {
  sourceKey: string
  tableName?: string
}

export interface KnowledgePreviewResult {
  type?: string
  content?: string
}

export interface KnowledgeSyncResult {
  totalCount?: number
  createdCount?: number
  updatedCount?: number
  unchangedCount?: number
}

export interface DbDataSourceTestConnectionResult {
  success?: boolean
  message?: string
  databaseProductName?: string
  databaseProductVersion?: string
  catalog?: string
  schema?: string
}

export interface DbTableMetaItem {
  id: string | number
  sourceKey?: string
  tableName?: string
  tableComment?: string
  tableType?: string
  layerType?: string
  rowCount?: number | null
  columnCount?: number | null
  partitionKey?: string | null
  freshnessSeconds?: number | null
  status?: string
  enabled?: boolean
  lastScanAt?: string | null
  lastSyncAt?: string | null
  remark?: string | null
}

export interface DbTableMetaSearchPayload {
  page?: number
  size?: number
  sourceKey?: string
  keyword?: string
  tableName?: string
  status?: string
  enabled?: boolean
}

export interface DbTableMetaSearchResult {
  list?: DbTableMetaItem[]
  pageInfo?: {
    total?: number
  }
}

export interface DbTableMetaUpsertPayload {
  sourceKey: string
  tableName: string
  tableComment?: string
  tableType?: string
  layerType?: string
  rowCount?: number
  columnCount?: number
  partitionKey?: string
  freshnessSeconds?: number
  status?: string
  enabled?: boolean
  remark?: string
}

export interface DbTableMetaCascadeDeleteResult {
  sourceKey?: string
  tableName?: string
  deletedTableCount?: number
  deletedFieldCount?: number
  deletedIndexCount?: number
}

export interface DbTableFieldMetaItem {
  id: string | number
  sourceKey?: string
  tableName?: string
  columnName?: string
  columnComment?: string
  dataType?: string
  columnLength?: number | null
  columnPrecision?: number | null
  columnScale?: number | null
  nullable?: boolean
  primaryKey?: boolean
  partitionKey?: boolean
  defaultValue?: string | null
  ordinalPosition?: number | null
  fieldRole?: string
  enabled?: boolean
  remark?: string | null
}

export interface DbTableFieldMetaSearchPayload {
  page?: number
  size?: number
  sourceKey?: string
  tableName?: string
  columnName?: string
  keyword?: string
  enabled?: boolean
}

export interface DbTableFieldMetaSearchResult {
  list?: DbTableFieldMetaItem[]
  pageInfo?: {
    total?: number
  }
}

export interface DbAccessTableCandidate {
  tableName?: string
  tableComment?: string
  synced?: boolean
  tableMeta?: {
    columnCount?: number | null
  }
}

export interface DbAccessTableListResult {
  tables?: DbAccessTableCandidate[]
}

export interface DbAccessTableSyncPayload {
  sourceKey: string
  tables: string[]
}

export interface DbAccessTableSyncResult {
  createdTableCount?: number
  updatedTableCount?: number
  createdFieldCount?: number
  updatedFieldCount?: number
  createdIndexCount?: number
  updatedIndexCount?: number
}

export interface DbAccessTableDataPreviewPayload {
  sourceKey: string
  tableName: string
  page?: number
  pageSize?: number
}

export interface DbAccessTableDataPreviewResult {
  sourceKey?: string
  tableName?: string
  page?: number
  pageSize?: number
  hasNext?: boolean
  executionMs?: number
  columns?: string[]
  records?: Array<Record<string, unknown>>
  metadata?: Record<string, unknown>
}

export interface WorkbookDownloadResult {
  blob: Blob
  filename: string
}

export function searchDbDataSources(payload: DbDataSourceSearchPayload = {}) {
  return request<DbDataSourceSearchResult>(`${DATA_SOURCE_API_PREFIX}/_search`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function createDbDataSource(payload: DbDataSourceUpsertPayload) {
  return request(`${DATA_SOURCE_API_PREFIX}`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateDbDataSource(id: string | number, payload: DbDataSourceUpsertPayload) {
  return request(`${DATA_SOURCE_API_PREFIX}/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function testDbDataSourceConnection(payload: DbDataSourceUpsertPayload) {
  return request<DbDataSourceTestConnectionResult>(`${DB_ACCESS_API_PREFIX}/test-connection`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function syncDbTableKnowledge(payload: KnowledgeSyncPayload) {
  return request<KnowledgeSyncResult>(`${TABLE_META_API_PREFIX}/knowledge-sync`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function previewDbTableKnowledge(sourceKey: string, tableName: string) {
  return request<KnowledgePreviewResult>(`${TABLE_META_API_PREFIX}/knowledge-preview`, {
    method: 'GET',
    query: {
      sourceKey,
      tableName,
    },
  })
}

export function searchDbTables(payload: DbTableMetaSearchPayload = {}) {
  return request<DbTableMetaSearchResult>(`${TABLE_META_API_PREFIX}/_search`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateDbTableMeta(id: string | number, payload: DbTableMetaUpsertPayload) {
  return request(`${TABLE_META_API_PREFIX}/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteDbTableMetaCascade(sourceKey: string, tableName: string) {
  return request<DbTableMetaCascadeDeleteResult>(`${TABLE_META_API_PREFIX}/delete-cascade`, {
    method: 'POST',
    body: JSON.stringify({ sourceKey, tableName }),
  })
}

export function searchDbTableFields(payload: DbTableFieldMetaSearchPayload = {}) {
  return request<DbTableFieldMetaSearchResult>(`${DB_ENGINE_API_PREFIX}/api/v1/meta/field/_search`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function listDbAccessTables(payload: { sourceKey: string }) {
  return request<DbAccessTableListResult>(`${DB_ACCESS_API_PREFIX}/tables`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function syncDbAccessTableMeta(payload: DbAccessTableSyncPayload) {
  return request<DbAccessTableSyncResult>(`${DB_ACCESS_API_PREFIX}/sync/table-meta`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function previewDbAccessTableData(payload: DbAccessTableDataPreviewPayload) {
  return request<DbAccessTableDataPreviewResult>(`${DB_ACCESS_API_PREFIX}/table-data-preview`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export async function exportDbMetaWorkbook(sourceKey: string, format = 'json'): Promise<WorkbookDownloadResult> {
  const response = await requestRaw(
    `${WORKBOOK_API_PREFIX}/export?sourceKey=${encodeURIComponent(sourceKey)}&format=${encodeURIComponent(format)}`,
    { method: 'GET' },
  )

  return resolveWorkbookDownload(response, `meta-workbook.${format === 'json' ? 'json' : 'xlsx'}`, '导出失败')
}

export async function downloadDbMetaTemplateWorkbook(format = 'json'): Promise<WorkbookDownloadResult> {
  const response = await requestRaw(
    `${WORKBOOK_API_PREFIX}/template?format=${encodeURIComponent(format)}`,
    { method: 'GET' },
  )

  return resolveWorkbookDownload(response, `db-meta-template.${format === 'json' ? 'json' : 'xlsx'}`, '模板下载失败')
}

export async function streamDbMetaImportWorkbook(sourceKey: string, file: File, signal?: AbortSignal) {
  const formData = new FormData()
  formData.append('file', file)
  if (sourceKey) {
    formData.append('sourceKey', sourceKey)
  }

  const response = await requestRaw(`${WORKBOOK_API_PREFIX}/import/stream`, {
    method: 'POST',
    body: formData,
    signal,
    headers: {
      Accept: 'text/event-stream;charset=UTF-8',
    },
  })

  const contentType = response.headers.get('content-type') || ''
  if (contentType.includes('application/json')) {
    const payload = await response.json().catch(() => null)
    if (payload && typeof payload === 'object' && 'msg' in payload) {
      throw new Error(String((payload as { msg?: string }).msg || '获取导入进度流失败'))
    }
    throw new Error('导入进度接口未返回事件流')
  }

  return response
}

function safeParseJson(text: string) {
  try {
    return JSON.parse(text)
  }
  catch {
    return null
  }
}

function hasBusinessCode(payload: unknown) {
  return payload !== null && typeof payload === 'object' && Object.prototype.hasOwnProperty.call(payload, 'code')
}

async function resolveWorkbookDownload(response: Response, fallbackFilename: string, fallbackError: string): Promise<WorkbookDownloadResult> {
  const contentType = response.headers.get('content-type') || ''
  if (contentType.includes('application/json')) {
    const text = await response.text()
    const payload = safeParseJson(text)
    if (hasBusinessCode(payload)) {
      const businessPayload = payload as { code?: number; msg?: string; message?: string }
      if (Number(businessPayload.code) !== 0) {
        throw new Error(businessPayload.msg || businessPayload.message || fallbackError)
      }
      throw new Error('接口未返回文件流')
    }
    return {
      blob: new Blob([text], { type: 'application/json' }),
      filename: fallbackFilename,
    }
  }

  const disposition = response.headers.get('content-disposition') || ''
  const match = disposition.match(/filename\*=UTF-8''([^;]+)/i)
  const filename = match ? decodeURIComponent(match[1]) : fallbackFilename
  const blob = await response.blob()
  return { blob, filename }
}
