import { request } from '../../../api/request'
import { getBackendService, SERVICE_NAMES } from '../../../config/services'

const CHAT_API_PREFIX = getBackendService(SERVICE_NAMES.CHAT).gatewayPrefix

const AI_MODEL_MANAGE_API_PREFIX = `${CHAT_API_PREFIX}/api/v1/ai/meta/internal/model-manage`
const AI_KB_STORE_API_PREFIX = `${CHAT_API_PREFIX}/api/v1/ai/kb/internal/store`
const AI_FLOW_SKILL_API_PREFIX = `${CHAT_API_PREFIX}/api/v1/ai/chat/workflow/internal/skill`

export interface AiModelManageItem {
  id: string | number
  modelCode?: string
  modelName?: string
  providerCode?: string
  providerName?: string
  baseUrl?: string
  apiModel?: string
  enabled?: boolean
  apiKeyMasked?: string
  extJson?: Record<string, unknown> | null
  createTime?: string
  updateTime?: string
}

export interface AiKbStoreItem {
  id: string | number
  kbCode?: string
  kbName?: string
  providerKbId?: string
  enabled?: boolean
  tags?: string[]
  url?: string
  extJson?: Record<string, unknown> | null
  createTime?: string
  updateTime?: string
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
  providerCode?: string
  enabled?: boolean
}

export interface AiKbStoreQueryPayload {
  page?: number
  size?: number
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

export function searchAiKbStores(payload: AiKbStoreQueryPayload = {}) {
  return request<PageResult<AiKbStoreItem>>(`${AI_KB_STORE_API_PREFIX}/_search`, {
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
