import { request } from '../../../api/request'
import { getBackendService, SERVICE_NAMES } from '../../../config/services'
import type { LoginPayload, LoginResult } from '../types/auth'

const USER_API_PREFIX = getBackendService(SERVICE_NAMES.USER).gatewayPrefix

export function loginAuth(payload: LoginPayload) {
  return request<LoginResult>(`${USER_API_PREFIX}/auth/login`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function logoutAuth() {
  return request(`${USER_API_PREFIX}/auth/logout`, {
    method: 'POST',
  })
}

export function refreshAuth() {
  return request<LoginResult>(`${USER_API_PREFIX}/auth/refresh`, {
    method: 'POST',
  })
}

export function getCurrentUser<T = unknown>() {
  return request<T>(`${USER_API_PREFIX}/auth/me`, {
    method: 'GET',
  })
}
