import { request } from '../utils/request'

const AI_FLOW_PAGE_API_PREFIX = '/aiChat/api/v1/ai/chat/workflow/internal/page'
const AI_FLOW_WORKFLOW_API_PREFIX = '/aiChat/api/v1/ai/chat/workflow/internal/workflow'
const AI_FLOW_NODE_API_PREFIX = '/aiChat/api/v1/ai/chat/workflow/internal/node'
const AI_FLOW_SKILL_API_PREFIX = '/aiChat/api/v1/ai/chat/workflow/internal/skill'
const AI_FLOW_CONFIG_NODE_API_PREFIX = '/aiChat/api/v1/ai/chat/workflow/internal/config-node'
const AI_FLOW_CONFIG_NODE_SKILL_API_PREFIX = '/aiChat/api/v1/ai/chat/workflow/internal/config-node-skill'

export function getAiFlowOverview() {
  return request(`${AI_FLOW_PAGE_API_PREFIX}/overview`, {
    method: 'GET'
  })
}

export function getAiFlowDetail(workflowKey) {
  return request(`${AI_FLOW_PAGE_API_PREFIX}/detail/${encodeURIComponent(workflowKey)}`, {
    method: 'GET'
  })
}

export function createAiFlowWorkflow(payload) {
  return request(`${AI_FLOW_PAGE_API_PREFIX}/workflow`, {
    method: 'POST',
    body: JSON.stringify(payload ?? {})
  })
}

export function updateAiFlowWorkflow(id, payload) {
  return request(`${AI_FLOW_PAGE_API_PREFIX}/workflow/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload ?? {})
  })
}

export function deleteAiFlowWorkflow(id) {
  return request(`${AI_FLOW_PAGE_API_PREFIX}/workflow/${id}`, {
    method: 'DELETE'
  })
}

export function searchAiFlowWorkflows(payload) {
  return request(`${AI_FLOW_WORKFLOW_API_PREFIX}/_search`, {
    method: 'POST',
    body: JSON.stringify(payload ?? {})
  })
}

export function searchAiFlowNodes(payload) {
  return request(`${AI_FLOW_NODE_API_PREFIX}/_search`, {
    method: 'POST',
    body: JSON.stringify(payload ?? {})
  })
}

export function createAiFlowNode(payload) {
  return request(AI_FLOW_NODE_API_PREFIX, {
    method: 'POST',
    body: JSON.stringify(payload ?? {})
  })
}

export function updateAiFlowNode(id, payload) {
  return request(`${AI_FLOW_NODE_API_PREFIX}/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload ?? {})
  })
}

export function deleteAiFlowNode(id) {
  return request(`${AI_FLOW_NODE_API_PREFIX}/${id}`, {
    method: 'DELETE'
  })
}

export function searchAiFlowSkills(payload) {
  return request(`${AI_FLOW_SKILL_API_PREFIX}/_search`, {
    method: 'POST',
    body: JSON.stringify(payload ?? {})
  })
}

export function createAiFlowSkill(payload) {
  return request(AI_FLOW_SKILL_API_PREFIX, {
    method: 'POST',
    body: JSON.stringify(payload ?? {})
  })
}

export function updateAiFlowSkill(id, payload) {
  return request(`${AI_FLOW_SKILL_API_PREFIX}/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload ?? {})
  })
}

export function deleteAiFlowSkill(id) {
  return request(`${AI_FLOW_SKILL_API_PREFIX}/${id}`, {
    method: 'DELETE'
  })
}

export function createAiFlowConfigNode(payload) {
  return request(AI_FLOW_CONFIG_NODE_API_PREFIX, {
    method: 'POST',
    body: JSON.stringify(payload ?? {})
  })
}

export function updateAiFlowConfigNode(id, payload) {
  return request(`${AI_FLOW_CONFIG_NODE_API_PREFIX}/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload ?? {})
  })
}

export function deleteAiFlowConfigNode(id) {
  return request(`${AI_FLOW_CONFIG_NODE_API_PREFIX}/${id}`, {
    method: 'DELETE'
  })
}

export function createAiFlowConfigNodeSkill(payload) {
  return request(AI_FLOW_CONFIG_NODE_SKILL_API_PREFIX, {
    method: 'POST',
    body: JSON.stringify(payload ?? {})
  })
}

export function updateAiFlowConfigNodeSkill(id, payload) {
  return request(`${AI_FLOW_CONFIG_NODE_SKILL_API_PREFIX}/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload ?? {})
  })
}

export function deleteAiFlowConfigNodeSkill(id) {
  return request(`${AI_FLOW_CONFIG_NODE_SKILL_API_PREFIX}/${id}`, {
    method: 'DELETE'
  })
}
