import { request } from '../utils/request'

const RENDER_COMPONENT_API_PREFIX = '/render/api/v1/render/components'

export function searchRenderComponents(payload) {
  return request(`${RENDER_COMPONENT_API_PREFIX}/page`, {
    method: 'POST',
    body: JSON.stringify(payload ?? {})
  })
}

export function getRenderComponent(id) {
  return request(`${RENDER_COMPONENT_API_PREFIX}/${id}`, {
    method: 'GET'
  })
}

export function createRenderComponent(payload) {
  return request(RENDER_COMPONENT_API_PREFIX, {
    method: 'POST',
    body: JSON.stringify(payload ?? {})
  })
}

export function updateRenderComponent(id, payload) {
  return request(`${RENDER_COMPONENT_API_PREFIX}/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload ?? {})
  })
}

export function updateRenderComponentStatus(id, payload) {
  return request(`${RENDER_COMPONENT_API_PREFIX}/${id}/status`, {
    method: 'PUT',
    body: JSON.stringify(payload ?? {})
  })
}

export function deleteRenderComponent(id) {
  return request(`${RENDER_COMPONENT_API_PREFIX}/${id}`, {
    method: 'DELETE'
  })
}

export function listRenderComponentCategories() {
  return request(`${RENDER_COMPONENT_API_PREFIX}/categories`, {
    method: 'GET'
  })
}

export function getRenderComponentSummary() {
  return request(`${RENDER_COMPONENT_API_PREFIX}/summary`, {
    method: 'GET'
  })
}
