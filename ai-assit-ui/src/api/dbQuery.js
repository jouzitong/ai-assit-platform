import { request } from '../utils/request'

const DB_QUERY_API_PREFIX = '/dbEngine/api/v1'

export function queryDbGet(payload) {
  return request(`${DB_QUERY_API_PREFIX}/query.get`, {
    method: 'POST',
    body: JSON.stringify(payload ?? {})
  })
}

export function queryDbList(payload) {
  return request(`${DB_QUERY_API_PREFIX}/query.list`, {
    method: 'POST',
    body: JSON.stringify(payload ?? {})
  })
}

export function queryDbCount(payload) {
  return request(`${DB_QUERY_API_PREFIX}/query.count`, {
    method: 'POST',
    body: JSON.stringify(payload ?? {})
  })
}

export function queryDbTree(payload) {
  return request(`${DB_QUERY_API_PREFIX}/query.tree`, {
    method: 'POST',
    body: JSON.stringify(payload ?? {})
  })
}
