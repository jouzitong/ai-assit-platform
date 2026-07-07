import { request } from '../../../api/request'
import { getBackendService, SERVICE_NAMES } from '../../../config/services'

const CHAT_API_PREFIX = getBackendService(SERVICE_NAMES.CHAT).gatewayPrefix
const WORKFLOW_API_PREFIX = `${CHAT_API_PREFIX}/api/v1/ai/chat/workflow/internal/catalog`
const NODE_API_PREFIX = `${CHAT_API_PREFIX}/api/v1/ai/chat/workflow/internal/node`
const SKILL_API_PREFIX = `${CHAT_API_PREFIX}/api/v1/ai/chat/workflow/internal/skill`
const TOOL_API_PREFIX = `${CHAT_API_PREFIX}/api/v1/ai/chat/workflow/internal/tool`

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
  desc?: string
  executeType?: number | string
  modelCode?: string
  skillRefs?: string[]
  toolRefs?: string[]
  kbRefs?: string[]
  inputConfig?: Array<{
    role?: string
    content?: string
  }>
  outputConfig?: {
    outputType?: string
    storeAs?: string
    schema?: Record<string, unknown> | null
  } | null
  enabled?: boolean
  remark?: string
  createTime?: string
  updateTime?: string
}

export interface AiToolItem {
  id: string | number
  code?: string
  name?: string
  desc?: string
  content?: string
  runtimeType?: string
  syncStatus?: number | string
  enabled?: boolean
  remark?: string
  createTime?: string
  updateTime?: string
}

export interface AiSkillItem {
  id: string | number
  code?: string
  name?: string
  desc?: string
  content?: string
  toolRefs?: string[]
  enabled?: boolean
  remark?: string
  createTime?: string
  updateTime?: string
}

export interface AiToolUpsertPayload {
  code?: string
  name?: string
  desc?: string
  content?: string
  runtimeType?: string
  syncStatus?: number | string
  enabled?: boolean
  remark?: string
}

export interface AiSkillUpsertPayload {
  code?: string
  name?: string
  desc?: string
  content?: string
  toolRefs?: string[]
  enabled?: boolean
  remark?: string
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
  desc?: string
  executeType?: number | string
  enabled?: boolean
}

export interface AiNodeUpsertPayload {
  code?: string
  name?: string
  desc?: string
  executeType?: number | string
  modelCode?: string
  skillRefs?: string[]
  toolRefs?: string[]
  kbRefs?: string[]
  inputConfig?: Array<{
    role?: string
    content?: string
  }>
  outputConfig?: {
    outputType?: string
    storeAs?: string
    schema?: Record<string, unknown> | null
  } | null
  enabled?: boolean
  remark?: string
}

export interface ToolQueryPayload {
  page?: number
  size?: number
  code?: string
  name?: string
  runtimeType?: string
  syncStatus?: number | string
  enabled?: boolean
}

export interface SkillQueryPayload {
  page?: number
  size?: number
  code?: string
  name?: string
  desc?: string
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

export function createNode(payload: AiNodeUpsertPayload) {
  return request<AiNodeItem>(`${NODE_API_PREFIX}`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateNode(id: string | number, payload: AiNodeUpsertPayload) {
  return request<AiNodeItem>(`${NODE_API_PREFIX}/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function searchSkills(payload: SkillQueryPayload = {}) {
  return request<WorkflowPageResult<AiSkillItem>>(`${SKILL_API_PREFIX}/_search`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function deleteSkill(id: string | number) {
  return request<boolean>(`${SKILL_API_PREFIX}/${id}`, {
    method: 'DELETE',
  })
}

export function createSkill(payload: AiSkillUpsertPayload) {
  return request<AiSkillItem>(`${SKILL_API_PREFIX}`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateSkill(id: string | number, payload: AiSkillUpsertPayload) {
  return request<AiSkillItem>(`${SKILL_API_PREFIX}/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function searchTools(payload: ToolQueryPayload = {}) {
  return request<WorkflowPageResult<AiToolItem>>(`${TOOL_API_PREFIX}/_search`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function deleteTool(id: string | number) {
  return request<boolean>(`${TOOL_API_PREFIX}/${id}`, {
    method: 'DELETE',
  })
}

export function createTool(payload: AiToolUpsertPayload) {
  return request<AiToolItem>(`${TOOL_API_PREFIX}`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateTool(id: string | number, payload: AiToolUpsertPayload) {
  return request<AiToolItem>(`${TOOL_API_PREFIX}/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}
