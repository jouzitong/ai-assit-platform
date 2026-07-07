import { request } from '../../../api/request'
import { getBackendService, SERVICE_NAMES } from '../../../config/services'

const CHAT_API_PREFIX = getBackendService(SERVICE_NAMES.CHAT).gatewayPrefix
const WORKFLOW_API_PREFIX = `${CHAT_API_PREFIX}/api/v1/ai/chat/workflow/internal/catalog`
const NODE_API_PREFIX = `${CHAT_API_PREFIX}/api/v1/ai/chat/workflow/internal/node`

export interface WorkflowPageResult<T> {
  list?: T[]
  pageInfo?: {
    total?: number
  }
}

export interface AiWorkflowItem {
  id: string | number
  code?: string
  name?: string
  type?: string
  enabled?: boolean
  config?: Record<string, unknown> | null
  createTime?: string
  updateTime?: string
}

export interface AiNodeItem {
  id: string | number
  code?: string
  name?: string
  type?: string
  enabled?: boolean
  config?: Record<string, unknown> | null
  createTime?: string
  updateTime?: string
}

export interface WorkflowQueryPayload {
  page?: number
  size?: number
  code?: string
  name?: string
  type?: string
  enabled?: boolean
}

export interface NodeQueryPayload {
  page?: number
  size?: number
  code?: string
  name?: string
  type?: string
  enabled?: boolean
}

export function searchWorkflows(payload: WorkflowQueryPayload = {}) {
  return request<WorkflowPageResult<AiWorkflowItem>>(`${WORKFLOW_API_PREFIX}/_search`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function deleteWorkflow(id: string | number) {
  return request<boolean>(`${WORKFLOW_API_PREFIX}/${id}`, {
    method: 'DELETE',
  })
}

export function searchNodes(payload: NodeQueryPayload = {}) {
  return request<WorkflowPageResult<AiNodeItem>>(`${NODE_API_PREFIX}/_search`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function deleteNode(id: string | number) {
  return request<boolean>(`${NODE_API_PREFIX}/${id}`, {
    method: 'DELETE',
  })
}
