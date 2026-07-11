import { request } from '../../../api/request'
import { getBackendService, SERVICE_NAMES } from '../../../config/services'

const USER_API_PREFIX = getBackendService(SERVICE_NAMES.USER).gatewayPrefix
const SECURITY_USERS_API_PREFIX = `${USER_API_PREFIX}/api/v1/security/users`
const SECURITY_USER_ROLES_API_PREFIX = `${USER_API_PREFIX}/api/v1/security/user-roles`
const SECURITY_ROLES_API_PREFIX = `${USER_API_PREFIX}/api/v1/security/roles`

export interface SecurityUserItem {
  id: string | number
  username: string
  displayName?: string | null
  status: string
  tenantId?: string | null
}

export interface SecurityUserRoleItem {
  id: string | number
  userId: string | number
  roleCode: string
}

export interface SecurityRoleItem {
  id: string | number
  roleCode: string
  roleName: string
  status: string
}

export interface SecurityUserProfile {
  user: SecurityUserItem
  roleCodes: string[]
  passwordConfigured: boolean
  passwordAlgo?: string | null
}

export interface PageResult<T> {
  list?: T[]
  pageInfo?: {
    total?: number
  }
}

export interface SecurityUserQuery {
  page?: number
  size?: number
  keyword?: string
}

export interface SecurityUserRoleQuery {
  page?: number
  size?: number
  keyword?: string
}

export type SecurityUserPayload = Omit<SecurityUserItem, 'id'>
export type SecurityUserRolePayload = Omit<SecurityUserRoleItem, 'id'>
export type SecurityRolePayload = Omit<SecurityRoleItem, 'id'>

export interface SecurityUserProfilePayload {
  user: SecurityUserPayload
  roleCodes: string[]
  password?: string
}

export function searchSecurityUsers(payload: SecurityUserQuery = {}) {
  return request<PageResult<SecurityUserItem>>(`${SECURITY_USERS_API_PREFIX}/_search`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function createSecurityUser(payload: SecurityUserPayload) {
  return request<SecurityUserItem>(SECURITY_USERS_API_PREFIX, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateSecurityUser(id: string | number, payload: SecurityUserPayload) {
  return request<SecurityUserItem>(`${SECURITY_USERS_API_PREFIX}/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteSecurityUser(id: string | number) {
  return request(`${SECURITY_USERS_API_PREFIX}/${id}`, {
    method: 'DELETE',
  })
}

export function getSecurityUserProfile(id: string | number) {
  return request<SecurityUserProfile>(`${SECURITY_USERS_API_PREFIX}/${id}/profile`)
}

export function updateSecurityUserProfile(id: string | number, payload: SecurityUserProfilePayload) {
  return request<SecurityUserProfile>(`${SECURITY_USERS_API_PREFIX}/${id}/profile`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function searchSecurityRoles(payload: SecurityUserQuery = {}) {
  return request<PageResult<SecurityRoleItem>>(`${SECURITY_ROLES_API_PREFIX}/_search`, {
    method: 'POST', body: JSON.stringify(payload),
  })
}

export function createSecurityRole(payload: SecurityRolePayload) {
  return request<SecurityRoleItem>(SECURITY_ROLES_API_PREFIX, { method: 'POST', body: JSON.stringify(payload) })
}

export function updateSecurityRole(id: string | number, payload: SecurityRolePayload) {
  return request<SecurityRoleItem>(`${SECURITY_ROLES_API_PREFIX}/${id}`, { method: 'PUT', body: JSON.stringify(payload) })
}

export function deleteSecurityRole(id: string | number) {
  return request(`${SECURITY_ROLES_API_PREFIX}/${id}`, { method: 'DELETE' })
}

export function searchSecurityUserRoles(payload: SecurityUserRoleQuery = {}) {
  return request<PageResult<SecurityUserRoleItem>>(`${SECURITY_USER_ROLES_API_PREFIX}/_search`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function createSecurityUserRole(payload: SecurityUserRolePayload) {
  return request<SecurityUserRoleItem>(SECURITY_USER_ROLES_API_PREFIX, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateSecurityUserRole(id: string | number, payload: SecurityUserRolePayload) {
  return request<SecurityUserRoleItem>(`${SECURITY_USER_ROLES_API_PREFIX}/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function deleteSecurityUserRole(id: string | number) {
  return request(`${SECURITY_USER_ROLES_API_PREFIX}/${id}`, {
    method: 'DELETE',
  })
}
