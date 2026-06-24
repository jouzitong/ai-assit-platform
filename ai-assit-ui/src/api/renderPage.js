import { request } from '../utils/request'

const RENDER_API_PREFIX = '/render'
const RENDER_PAGE_API_PREFIX = `${RENDER_API_PREFIX}/api/v1/render/pages`
const RENDER_PAGE_CATEGORY_API_PREFIX = `${RENDER_API_PREFIX}/api/v1/render/page-categories`

export function searchRenderPages(payload) {
  return request(`${RENDER_PAGE_API_PREFIX}/page`, {
    method: 'POST',
    body: JSON.stringify(payload ?? {})
  })
}

export function getRenderPage(id) {
  return request(`${RENDER_PAGE_API_PREFIX}/${id}`, {
    method: 'GET'
  })
}

export function createRenderPage(payload) {
  return request(RENDER_PAGE_API_PREFIX, {
    method: 'POST',
    body: JSON.stringify(payload ?? {})
  })
}

export function updateRenderPage(id, payload) {
  return request(`${RENDER_PAGE_API_PREFIX}/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload ?? {})
  })
}

export function deleteRenderPage(id) {
  return request(`${RENDER_PAGE_API_PREFIX}/${id}`, {
    method: 'DELETE'
  })
}

export function listRenderPageCategoryTree(payload) {
  return request(`${RENDER_PAGE_CATEGORY_API_PREFIX}/tree`, {
    method: 'POST',
    body: JSON.stringify(payload ?? {})
  })
}
