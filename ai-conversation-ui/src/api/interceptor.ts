import type { ApiResponse } from './types'
import { redirectToLogin } from '../utils/session'

const AUTH_FAILURE_CODES = new Set([401, 40401, 40402, 40404, 40405])

export type RequestInterceptor = (input: string, init: RequestInit) => Promise<[string, RequestInit]> | [string, RequestInit]
export type ResponseInterceptor = <T>(response: ApiResponse<T>) => Promise<ApiResponse<T>> | ApiResponse<T>

export const applyRequestInterceptor: RequestInterceptor = async (input, init) => {
  return [input, init]
}

export const applyResponseInterceptor: ResponseInterceptor = async (response) => {
  const responseCode = Number(response?.code)

  if (AUTH_FAILURE_CODES.has(responseCode)) {
    redirectToLogin()
  }

  if (AUTH_FAILURE_CODES.has(responseCode) || responseCode === 403) {
    throw new Error(response?.msg || response?.message || (AUTH_FAILURE_CODES.has(responseCode) ? '登录已失效，请重新登录' : '无权限访问该资源'))
  }

  return response
}
