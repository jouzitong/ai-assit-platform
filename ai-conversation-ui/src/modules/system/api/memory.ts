import { request } from '../../../api/request'
import { getBackendService, SERVICE_NAMES } from '../../../config/services'

const CHAT_API_PREFIX = getBackendService(SERVICE_NAMES.CHAT).gatewayPrefix
const MEMORY_API_PREFIX = `${CHAT_API_PREFIX}/api/chat/memories`

export type MemoryItemStatus = 'ACTIVE' | 'DISABLED' | 'PROCESSING' | 'FAILED' | 'FORGOTTEN'
export type MemoryType = 'RAW' | 'SEMANTIC' | 'EPISODIC' | 'PROCEDURAL'

export interface MemoryManagementItem {
  memoryRef?: string | null
  memoryType?: MemoryType | null
  status?: MemoryItemStatus | null
  content?: string | null
  sourceSessionCode?: string | null
  sourceRoundCode?: string | null
  createdAt?: string | null
}

export interface MemoryManagementListResponse {
  generatedAt?: string | null
  providerStatus?: string | null
  memoryLag?: boolean
  items?: MemoryManagementItem[]
  processingItems?: MemoryManagementItem[]
}

export interface MemoryManagementOperationResponse {
  memoryRef?: string | null
  status?: MemoryItemStatus | null
  accepted?: boolean
}

export interface MemoryManagementContentPayload {
  content: string
  confirmed: boolean
}

export function fetchManagedLongTermMemories() {
  return request<MemoryManagementListResponse>(`${MEMORY_API_PREFIX}/long-term`, {
    method: 'GET',
  })
}

export function createManagedLongTermMemory(payload: MemoryManagementContentPayload) {
  return request<MemoryManagementOperationResponse>(MEMORY_API_PREFIX + '/long-term', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function correctManagedMemory(memoryRef: string, payload: MemoryManagementContentPayload) {
  return request<MemoryManagementOperationResponse>(
    `${MEMORY_API_PREFIX}/${encodeURIComponent(memoryRef)}/correct`,
    { method: 'POST', body: JSON.stringify(payload) },
  )
}

export function disableManagedMemory(memoryRef: string) {
  return request<MemoryManagementOperationResponse>(
    `${MEMORY_API_PREFIX}/${encodeURIComponent(memoryRef)}/disable`,
    { method: 'POST' },
  )
}

export function restoreManagedMemory(memoryRef: string) {
  return request<MemoryManagementOperationResponse>(
    `${MEMORY_API_PREFIX}/${encodeURIComponent(memoryRef)}/restore`,
    { method: 'POST' },
  )
}

export function forgetManagedMemory(memoryRef: string) {
  return request<MemoryManagementOperationResponse>(
    `${MEMORY_API_PREFIX}/${encodeURIComponent(memoryRef)}`,
    { method: 'DELETE' },
  )
}

export function clearManagedLongTermMemories() {
  return request<MemoryManagementOperationResponse>(`${MEMORY_API_PREFIX}/long-term/clear`, {
    method: 'POST',
    body: JSON.stringify({ confirmed: true }),
  })
}
