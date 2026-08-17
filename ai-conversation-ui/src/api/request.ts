import { FRONTEND_ENVIRONMENT, GATEWAY_BASE_URL } from '../config/runtime'
import { getToken, redirectToLogin } from '../utils/session'
import { applyRequestInterceptor, applyResponseInterceptor } from './interceptor'
import type { ApiRequestConfig, ApiResponse } from './types'

const TRACE_HEADER = 'X-Trace-Id'
const FRONTEND_ENVIRONMENT_HEADER = 'X-Frontend-Environment'

function buildBaseUrl(path: string) {
  if (!path) {
    return GATEWAY_BASE_URL
  }
  if (/^https?:\/\//i.test(path)) {
    return path
  }
  if (path.startsWith('/')) {
    return `${GATEWAY_BASE_URL}${path}`
  }
  return `${GATEWAY_BASE_URL}/${path}`
}

function buildUrl(input: string, query?: ApiRequestConfig['query']) {
  if (!query) {
    return input
  }

  const url = new URL(input, window.location.origin)
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined) {
      url.searchParams.set(key, String(value))
    }
  })
  return url.toString()
}

function resolveBusinessMessage(payload: unknown, fallback: string) {
  if (typeof payload === 'string' && payload.trim()) {
    return payload
  }
  if (!payload || typeof payload !== 'object') {
    return fallback
  }
  const candidate = payload as { msg?: string; message?: string }
  return candidate.msg || candidate.message || fallback
}

function resolveStatusFallback(status: number) {
  if (status === 401) {
    return '登录已失效，请重新登录'
  }
  if (status === 403) {
    return '无权限访问该资源'
  }
  return `Request failed with status ${status}`
}

function unwrapBusinessPayload<T>(payload: ApiResponse<T> | T, fallback = '请求失败') {
  if (!payload || typeof payload !== 'object' || !Object.prototype.hasOwnProperty.call(payload, 'code')) {
    return payload as T
  }

  const businessPayload = payload as ApiResponse<T>
  if (Number(businessPayload.code) !== 0) {
    throw new Error(resolveBusinessMessage(businessPayload, fallback))
  }

  return Object.prototype.hasOwnProperty.call(businessPayload, 'data') ? businessPayload.data : (payload as T)
}

function generateTraceId() {
  if (typeof crypto !== 'undefined') {
    if (typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID().replace(/-/g, '')
    }
    if (typeof crypto.getRandomValues === 'function') {
      const bytes = new Uint8Array(16)
      crypto.getRandomValues(bytes)
      return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')
    }
  }

  return `${Date.now().toString(16)}${Math.random().toString(16).slice(2)}`.padEnd(32, '0').slice(0, 32)
}

function buildRequestHeaders(options: ApiRequestConfig = {}) {
  const token = getToken()
  const isFormDataBody = typeof FormData !== 'undefined' && options.body instanceof FormData
  return {
    ...(isFormDataBody ? {} : { 'Content-Type': 'application/json' }),
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    [TRACE_HEADER]: generateTraceId(),
    ...(options.headers || {}),
    // 环境门禁头必须最后写入，避免单个调用方意外覆盖统一环境标识。
    [FRONTEND_ENVIRONMENT_HEADER]: FRONTEND_ENVIRONMENT,
  }
}

async function readResponsePayload(response: Response) {
  if (response.status === 204 || response.status === 205) {
    return null
  }

  const contentLength = response.headers.get('content-length')
  if (contentLength === '0') {
    return null
  }

  const contentType = response.headers.get('content-type') || ''
  if (contentType.includes('application/json')) {
    return response.json()
  }

  const text = await response.text()
  return text.trim() ? text : null
}

export async function requestRaw(input: string, config: ApiRequestConfig = {}) {
  const { baseURL = '', query, ...rest } = config
  const basePath = baseURL ? `${baseURL}${input}` : input
  const [url, finalConfig] = await applyRequestInterceptor(buildUrl(buildBaseUrl(basePath), query), {
    ...rest,
    headers: buildRequestHeaders(rest),
  })

  const response = await fetch(url, finalConfig)
  if (!response.ok) {
    if (response.status === 401) {
      redirectToLogin()
    }

    const errorPayload = await readResponsePayload(response).catch(() => '')
    const message = resolveBusinessMessage(errorPayload, resolveStatusFallback(response.status))
    throw new Error(message)
  }

  return response
}

export async function request<T = unknown>(input: string, config: ApiRequestConfig = {}): Promise<T> {
  const response = await requestRaw(input, config)
  const payload = (await readResponsePayload(response)) as ApiResponse<T> | T
  const interceptedPayload = await applyResponseInterceptor(payload as ApiResponse<T>)
  return unwrapBusinessPayload(interceptedPayload)
}
