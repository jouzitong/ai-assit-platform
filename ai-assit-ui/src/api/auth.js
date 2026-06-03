import { request } from '../utils/request'

const USER_API_PREFIX = '/user'

export function loginAuth(payload) {
  return request(`${USER_API_PREFIX}/auth/login`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function logoutAuth() {
  return request(`${USER_API_PREFIX}/auth/logout`, {
    method: 'POST'
  })
}

export function refreshAuth() {
  return request(`${USER_API_PREFIX}/auth/refresh`, {
    method: 'POST'
  })
}

export function getCurrentUser() {
  return request(`${USER_API_PREFIX}/auth/me`, {
    method: 'GET'
  })
}
