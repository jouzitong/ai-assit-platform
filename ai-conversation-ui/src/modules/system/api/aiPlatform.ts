import { request } from '../../../api/request'
import { getBackendService, SERVICE_NAMES } from '../../../config/services'

const CHAT_API_PREFIX = getBackendService(SERVICE_NAMES.CHAT).gatewayPrefix

const AI_MODEL_MANAGE_API_PREFIX = `${CHAT_API_PREFIX}/api/v1/ai/meta/internal/model-manage`
const AI_CLIENT_MANAGE_API_PREFIX = `${CHAT_API_PREFIX}/api/v1/ai/meta/internal/client-manage`
const AI_KB_STORE_API_PREFIX = `${CHAT_API_PREFIX}/api/v1/ai/kb/internal/store`
const AI_KB_CLIENT_OPTION_API_PREFIX = `${CHAT_API_PREFIX}/api/v1/ai/kb/internal/client-options`
const AI_FLOW_SKILL_API_PREFIX = `${CHAT_API_PREFIX}/api/v1/ai/chat/workflow/internal/skill`

export interface AiModelManageItem {
  id: string | number
  modelCode?: string
  modelName?: string
  clientId?: string | number
  clientCode?: string
  clientName?: string
  clientType?: number
  baseUrl?: string
  apiModel?: string
  enabled?: boolean
  apiKeyMasked?: string
  extJson?: Record<string, unknown> | null
  createTime?: string
  updateTime?: string
}

export interface AiClientConfigItem {
  id: string | number
  clientCode?: string
  clientName?: string
  clientType?: number
  baseUrl?: string
  apiKeyMasked?: string
  enabled?: boolean
  modelCount?: number
  extJson?: Record<string, unknown> | null
  createTime?: string
  updateTime?: string
}

export interface AiClientConfigUpsertPayload {
  clientCode?: string
  clientName?: string
  clientType?: number
  baseUrl?: string
  apiKey?: string
  enabled?: boolean
  extJson?: Record<string, unknown> | null
}

export interface AiProviderModelItem {
  id: string
  object?: string
  created?: number
  ownedBy?: string
}

export interface AiModelManageUpsertPayload {
  modelCode?: string
  modelName?: string
  clientType?: number
  baseUrl?: string
  apiModel?: string
  enabled?: boolean
  apiKey?: string
  extJson?: Record<string, unknown> | null
}

export interface AiModelTestChatMessage {
  role: 'system' | 'user' | 'assistant'
  content: string
}

export interface AiModelTestChatPayload {
  id?: string | number | null
  clientType?: number
  baseUrl?: string
  apiModel?: string
  apiKey?: string
  messages: AiModelTestChatMessage[]
  extJson?: Record<string, unknown> | null
}

export interface AiModelTestChatResult {
  success?: boolean
  durationMs?: number
  clientType?: number
  apiModel?: string
  answer?: string
  errorMessage?: string
}

export interface AiKbStoreItem {
  id: string | number
  kbCode?: string
  kbName?: string
  providerKbId?: string
  description?: string
  embeddingModel?: string
  permission?: string
  chunkMethod?: string
  parserConfig?: Record<string, unknown> | null
  parseType?: string
  pipelineId?: string
  enabled?: boolean
  syncStatus?: number | string
  syncError?: string
  lastSyncAt?: string
  tags?: string[]
  auth?: AiKbAuthItem | null
  extJson?: Record<string, unknown> | null
  createTime?: string
  updateTime?: string
}

export interface AiKbStoreUpsertPayload {
  kbCode?: string
  kbName?: string
  description?: string
  embeddingModel?: string
  permission?: string
  chunkMethod?: string
  parserConfig?: Record<string, unknown> | null
  parseType?: string
  pipelineId?: string
  enabled?: boolean
  tags?: string[]
  extJson?: Record<string, unknown> | null
}

export interface AiKbAuthPayload {
  type?: number
  apiKey?: string
  accessKeyId?: string
  accessKeySecret?: string
}

export interface AiKbAuthItem {
  type?: number
  apiKeyMasked?: string
  accessKeyIdMasked?: string
  accessKeySecretMasked?: string
}

export interface AiKbClientOption {
  key: string
  clientType: number
  url?: string
  authType?: string
  authValueMasked?: string
  accessKeyIdMasked?: string
}

export interface AiKbDatasetItem {
  kbId: string
  kbName?: string
  clientType?: number
  description?: string
  documentCount?: number
}

export interface AiKbDatasetListPayload {
  name?: string
  page?: number
  pageSize?: number
  includeParsingStatus?: boolean
}

export interface AiKbEmbeddingModelItem {
  value: string
  modelId?: string
  name?: string
  providerName?: string
  instanceName?: string
  modelTypes?: string[]
  enabled?: boolean
  ext?: Record<string, unknown> | null
}

export interface AiFlowSkillItem {
  id: string | number
  code?: string
  name?: string
  type?: string
  enabled?: boolean
  config?: {
    summary?: string
    supportedPhases?: string[]
    [key: string]: unknown
  } | null
  createTime?: string
  updateTime?: string
}

export interface AiKbDocumentItem {
  id: string | number
  kbCode?: string
  documentCode?: string
  documentName?: string
  documentType?: number | string
  bizType?: number | string
  bizKey?: string
  sourceSystem?: string
  status?: number | string
  providerDocumentId?: string
  providerSyncStatus?: number | string
  currentVersionNo?: number
  contentFormat?: number | string
  contentSize?: number
  lastGeneratedAt?: string
  updateTime?: string
}

export interface AiKbDocumentDetail extends AiKbDocumentItem {
  contentChecksum?: string
  metaJson?: Record<string, unknown> | null
  lastError?: string
  remark?: string
  contentJson?: Record<string, unknown> | null
  renderedContent?: string
  extJson?: Record<string, unknown> | null
}

export interface AiKbDocumentQueryPayload {
  kbCode?: string
  documentCode?: string
  keyword?: string
  bizTypeCode?: number
  tab?: string
  page?: number
  size?: number
}

export interface AiKbDocumentUpsertPayload {
  kbCode: string
  documentId: string
  documentName?: string
  documentType: number
  bizType?: number
  content: string
  canUpdate?: boolean
  enabled?: boolean
  ext?: Record<string, unknown>
}

export interface AiKbDocumentContentUpdatePayload {
  documentId: number | string
  content: string
  ext?: Record<string, unknown>
}

export interface AiKbDocumentDeletePayload {
  kbCode: string
  documentCodes: string[]
}

export interface AiKbDocumentDeleteResult {
  deletedCount?: number
  deletedContentCount?: number
  deletedVersionCount?: number
  deletedVersionContentCount?: number
  skippedDocumentCodes?: string[]
}

export interface AiKbDocumentStatusUpdatePayload {
  kbCode: string
  documentCodes: string[]
  enabled: boolean
}

export interface AiKbDocumentSyncPayload {
  kbCode: string
  documentCodes?: string[]
  force?: boolean
}

export interface AiKbDocumentSyncResult {
  acceptedCount?: number
  taskCode?: string
  skippedDocumentCodes?: string[]
}

export interface AiKbSyncTaskDocumentResult {
  documentCode?: string
  documentName?: string
  status?: string
  message?: string
}

export interface AiKbSyncTaskResult {
  totalCount?: number
  completedCount?: number
  successCount?: number
  failedCount?: number
  documents?: AiKbSyncTaskDocumentResult[]
}

export interface AiKbSyncTaskItem {
  taskCode?: string
  kbCode?: string
  status?: number | string
  progressPercent?: number
  currentStage?: number | string
  resultJson?: AiKbSyncTaskResult | null
  errorMessage?: string
  startedAt?: string
  finishedAt?: string
}

export interface AiKbRetrievalTestPayload {
  kbCode: string
  /** @deprecated 请改用 kbCode。 */
  kbId?: string
  query: string
  topK?: number
  page?: number
  pageSize?: number
  retrievalTopK?: number
  similarityThreshold?: number
  vectorSimilarityWeight?: number
  rerankId?: string
  keyword?: boolean
  highlight?: boolean
  useKg?: boolean
  tocEnhance?: boolean
  documentIds?: string[]
  crossLanguages?: string[]
  metadataCondition?: Record<string, unknown> | null
  meta?: {
    scene?: string
    ext?: Record<string, unknown>
  }
}

export interface AiKbRetrievalTestItem {
  documentId?: string
  score?: number
  content?: string
  metadata?: Record<string, unknown> | null
}

export interface AiKbRetrievalTestResult {
  kbCode?: string
  /** @deprecated 请改用 kbCode。 */
  kbId?: string
  total?: number
  items?: AiKbRetrievalTestItem[]
}

export interface PageResult<T> {
  list?: T[]
  pageInfo?: {
    total?: number
  }
}

export interface AiModelManageQueryPayload {
  page?: number
  size?: number
  keyword?: string
  clientType?: number
  enabled?: boolean
}

export interface AiKbStoreQueryPayload {
  page?: number
  size?: number
  kbCode?: string
  kbName?: string
  keyword?: string
  enabled?: boolean
}

export interface AiFlowSkillQueryPayload {
  page?: number
  size?: number
  keyword?: string
  enabled?: boolean
}

export function searchAiModelManages(payload: AiModelManageQueryPayload = {}) {
  return request<PageResult<AiModelManageItem>>(`${AI_MODEL_MANAGE_API_PREFIX}/_search`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function createAiModelManage(payload: AiModelManageUpsertPayload) {
  return request<AiModelManageItem>(`${AI_MODEL_MANAGE_API_PREFIX}`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function batchSaveAiModels(clientId: string | number, apiModels: string[]) {
  return request<AiModelManageItem[]>(`${AI_MODEL_MANAGE_API_PREFIX}/_batch`, {
    method: 'POST',
    body: JSON.stringify({ clientId, apiModels }),
  })
}

export function listAiClients() {
  return request<AiClientConfigItem[]>(AI_CLIENT_MANAGE_API_PREFIX)
}

export function createAiClient(payload: AiClientConfigUpsertPayload) {
  return request<AiClientConfigItem>(AI_CLIENT_MANAGE_API_PREFIX, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateAiClient(id: string | number, payload: AiClientConfigUpsertPayload) {
  return request<AiClientConfigItem>(`${AI_CLIENT_MANAGE_API_PREFIX}/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteAiClient(id: string | number) {
  return request<boolean>(`${AI_CLIENT_MANAGE_API_PREFIX}/${id}`, { method: 'DELETE' })
}

export function discoverAiClientModels(id: string | number) {
  return request<AiProviderModelItem[]>(`${AI_CLIENT_MANAGE_API_PREFIX}/${id}/_models`, { method: 'POST' })
}

export function editAiModelManage(id: string | number, payload: Partial<AiModelManageUpsertPayload>) {
  return request<AiModelManageItem>(`${AI_MODEL_MANAGE_API_PREFIX}/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  })
}

export function updateAiModelManage(id: string | number, payload: AiModelManageUpsertPayload) {
  return request<AiModelManageItem>(`${AI_MODEL_MANAGE_API_PREFIX}/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteAiModelManage(id: string | number) {
  return request<boolean>(`${AI_MODEL_MANAGE_API_PREFIX}/${id}`, {
    method: 'DELETE',
  })
}

export function testAiModelChat(payload: AiModelTestChatPayload) {
  return request<AiModelTestChatResult>(`${AI_MODEL_MANAGE_API_PREFIX}/_test-chat`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function searchAiKbStores(payload: AiKbStoreQueryPayload = {}) {
  return request<PageResult<AiKbStoreItem>>(`${AI_KB_STORE_API_PREFIX}/_search`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function createAiKbStore(payload: AiKbStoreUpsertPayload) {
  return request<AiKbStoreItem>(`${AI_KB_STORE_API_PREFIX}`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function editAiKbStore(id: string | number, payload: Partial<AiKbStoreUpsertPayload>) {
  return request<AiKbStoreItem>(`${AI_KB_STORE_API_PREFIX}/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  })
}

export function updateAiKbStore(id: string | number, payload: AiKbStoreUpsertPayload) {
  return request<AiKbStoreItem>(`${AI_KB_STORE_API_PREFIX}/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteAiKbStore(id: string | number) {
  return request<boolean>(`${AI_KB_STORE_API_PREFIX}/${id}`, {
    method: 'DELETE',
  })
}

export function retryAiKbStoreSync(id: string | number) {
  return request<boolean>(`${AI_KB_STORE_API_PREFIX}/${id}/_retry-sync`, {
    method: 'POST',
    body: JSON.stringify({}),
  })
}

export function listAiKbClientOptions() {
  return request<AiKbClientOption[]>(AI_KB_CLIENT_OPTION_API_PREFIX)
}

export function listAiKbClientDatasets(clientKey: string, payload: AiKbDatasetListPayload = {}) {
  return request<AiKbDatasetItem[]>(`${AI_KB_CLIENT_OPTION_API_PREFIX}/${encodeURIComponent(clientKey)}/datasets`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function listAiKbClientEmbeddingModels(clientKey: string) {
  return request<AiKbEmbeddingModelItem[]>(`${AI_KB_CLIENT_OPTION_API_PREFIX}/${encodeURIComponent(clientKey)}/embedding-models`, {
    method: 'POST',
    body: JSON.stringify({}),
  })
}

export function searchAiKbDocuments(payload: AiKbDocumentQueryPayload = {}) {
  return request<PageResult<AiKbDocumentItem>>(`${CHAT_API_PREFIX}/api/v1/ai/kb/document/list`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getAiKbDocumentDetail(kbCode: string, documentCode: string) {
  return request<AiKbDocumentDetail>(`${CHAT_API_PREFIX}/api/v1/ai/kb/document/detail`, {
    method: 'GET',
    query: {
      kbCode,
      documentCode,
    },
  })
}

export function createOrUpdateAiKbDocument(payload: AiKbDocumentUpsertPayload) {
  return request(`${CHAT_API_PREFIX}/api/v1/ai/kb/document/upsert`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateAiKbDocumentContent(payload: AiKbDocumentContentUpdatePayload) {
  return request(`${CHAT_API_PREFIX}/api/v1/ai/kb/document/content/update`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function deleteAiKbDocuments(payload: AiKbDocumentDeletePayload) {
  return request<AiKbDocumentDeleteResult>(`${CHAT_API_PREFIX}/api/v1/ai/kb/document/delete`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateAiKbDocumentStatus(payload: AiKbDocumentStatusUpdatePayload) {
  return request<number>(`${CHAT_API_PREFIX}/api/v1/ai/kb/document/status/update`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function syncAiKbDocuments(payload: AiKbDocumentSyncPayload) {
  return request<AiKbDocumentSyncResult>(`${CHAT_API_PREFIX}/api/v1/ai/kb/document/sync`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getAiKbSyncTask(taskCode: string) {
  return request<AiKbSyncTaskItem>(`${CHAT_API_PREFIX}/api/v1/ai/kb/document/sync/task`, {
    method: 'GET',
    query: { taskCode },
  })
}

export function testAiKbRetrieval(payload: AiKbRetrievalTestPayload) {
  return request<AiKbRetrievalTestResult>(`${CHAT_API_PREFIX}/api/v1/ai/execution/kb/search`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function searchAiFlowSkills(payload: AiFlowSkillQueryPayload = {}) {
  return request<PageResult<AiFlowSkillItem>>(`${AI_FLOW_SKILL_API_PREFIX}/_search`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}
