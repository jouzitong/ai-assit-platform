import type { ApiResponse } from './types'

export type RequestInterceptor = (input: string, init: RequestInit) => Promise<[string, RequestInit]> | [string, RequestInit]
export type ResponseInterceptor = <T>(response: ApiResponse<T>) => Promise<ApiResponse<T>> | ApiResponse<T>

export const applyRequestInterceptor: RequestInterceptor = async (input, init) => {
  return [input, init]
}

export const applyResponseInterceptor: ResponseInterceptor = async (response) => {
  return response
}
