import { GATEWAY_BASE_URL } from '../config/runtime'
import { getToken } from './session'

const TRACE_HEADER = 'X-Trace-Id'

function buildUrl(path) {
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

function isObjectPayload(value) {
  return value !== null && typeof value === 'object'
}

function hasBusinessCode(payload) {
  return isObjectPayload(payload) && Object.prototype.hasOwnProperty.call(payload, 'code')
}

function resolveBusinessMessage(payload, fallback) {
  if (typeof payload === 'string' && payload.trim()) {
    return payload
  }
  if (!isObjectPayload(payload)) {
    return fallback
  }
  return payload.msg || payload.message || fallback
}

function unwrapBusinessPayload(payload, fallback = '请求失败') {
  if (!hasBusinessCode(payload)) {
    return payload
  }

  if (Number(payload.code) !== 0) {
    throw new Error(resolveBusinessMessage(payload, fallback))
  }

  return Object.prototype.hasOwnProperty.call(payload, 'data') ? payload.data : payload
}

async function readResponsePayload(response) {
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

function buildRequestHeaders(options = {}) {
  const token = getToken()
  const isFormDataBody = typeof FormData !== 'undefined' && options.body instanceof FormData
  return {
    ...(isFormDataBody ? {} : { 'Content-Type': 'application/json' }),
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    [TRACE_HEADER]: generateTraceId(),
    ...(options.headers || {})
  }
}

async function doRequest(path, options = {}) {
  return fetch(buildUrl(path), {
    ...options,
    headers: buildRequestHeaders(options)
  })
}

async function requestRaw(path, options = {}) {
  const response = await doRequest(path, options)

  if (!response.ok) {
    const errorPayload = await readResponsePayload(response).catch(() => '')
    throw new Error(resolveBusinessMessage(errorPayload, `Request failed with status ${response.status}`))
  }

  return response
}

async function request(path, options = {}) {
  const response = await requestRaw(path, options)

  const payload = await readResponsePayload(response)
  return unwrapBusinessPayload(payload, '请求失败')
}

export { request, requestRaw, buildRequestHeaders, buildUrl, readResponsePayload, resolveBusinessMessage, unwrapBusinessPayload }
