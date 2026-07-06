import type { ApiResponse } from './types'
import { redirectToLogin } from '../utils/session'

export type RequestInterceptor = (input: string, init: RequestInit) => Promise<[string, RequestInit]> | [string, RequestInit]
export type ResponseInterceptor = <T>(response: ApiResponse<T>) => Promise<ApiResponse<T>> | ApiResponse<T>

export const applyRequestInterceptor: RequestInterceptor = async (input, init) => {
  return [input, init]
}

export const applyResponseInterceptor: ResponseInterceptor = async (response) => {
  const responseCode = Number(response?.code)

  if (responseCode === 401) {
    redirectToLogin()
  }

  if (responseCode === 401 || responseCode === 403) {
    throw new Error(response?.msg || response?.message || (responseCode === 401 ? '登录已失效，请重新登录' : '无权限访问该资源'))
  }

  return response
}
