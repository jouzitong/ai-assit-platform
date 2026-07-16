import { request } from '../../../../api/request'
import type { AgentDefinition, AgentEntryBinding, AvailableAgent, CatalogQuery, PageResult, ValidationReport } from '../types'
import {
  agentManagementPath,
  createCatalog,
  createDefinitionVersion,
  deleteCatalog,
  getCatalog,
  listCatalog,
  publishDefinition,
  testDefinition,
  updateCatalog,
  validateDefinition,
} from './client'

export const listAgents = (query: CatalogQuery = {}) => listCatalog<AgentDefinition>('agents', query)
export const getAgent = (code: string) => getCatalog<AgentDefinition>('agents', code)
export const createAgent = (payload: AgentDefinition) => createCatalog<AgentDefinition, AgentDefinition>('agents', payload)
export const updateAgent = (code: string, payload: AgentDefinition) => updateCatalog<AgentDefinition, AgentDefinition>('agents', code, payload)
export const deleteAgent = (code: string) => deleteCatalog('agents', code)
export const createAgentVersion = (code: string, payload: AgentDefinition) => createDefinitionVersion<AgentDefinition, AgentDefinition>('agents', code, payload)
export const validateAgent = (code: string, version: number) => validateDefinition('agents', code, version)
export const publishAgent = (code: string, version: number) => publishDefinition('agents', code, version)
export const testAgent = (code: string, version: number, payload: Record<string, unknown>) => testDefinition('agents', code, version, payload)

export function getAgentCompatibility(code: string, version: number) {
  return request<ValidationReport>(agentManagementPath(`/agents/${encodeURIComponent(code)}/versions/${version}/compatibility`), {
    method: 'GET',
  })
}

export function listHomeAvailableAgents() {
  return request<AvailableAgent[]>(agentManagementPath('/agent-entries/HOME_CHAT/available-agents'), {
    method: 'GET',
  })
}

export function listAgentEntries() {
  return request<AgentEntryBinding[]>(agentManagementPath('/agent-entries'), {
    method: 'GET',
  })
}

export function updateAgentEntry(entryCode: string, payload: AgentEntryBinding) {
  return request<AgentEntryBinding>(agentManagementPath(`/agent-entries/${encodeURIComponent(entryCode)}`), {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function listAgentOptions(keyword = '') {
  return request<PageResult<AgentDefinition> | AgentDefinition[]>(agentManagementPath('/agents'), {
    method: 'GET',
    query: { page: 1, size: 200, keyword: keyword || undefined, status: 'PUBLISHED', enabled: true },
  })
}
