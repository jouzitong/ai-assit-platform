import { GATEWAY_BASE_URL } from '../config/runtime'
import { getToken } from './session'

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
  const contentType = response.headers.get('content-type') || ''
  if (contentType.includes('application/json')) {
    return response.json()
  }
  return response.text()
}

async function request(path, options = {}) {
  const token = getToken()
  const isFormDataBody = typeof FormData !== 'undefined' && options.body instanceof FormData
  const response = await fetch(buildUrl(path), {
    headers: {
      ...(isFormDataBody ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.headers || {})
    },
    ...options
  })

  if (!response.ok) {
    const errorPayload = await readResponsePayload(response).catch(() => '')
    throw new Error(resolveBusinessMessage(errorPayload, `Request failed with status ${response.status}`))
  }

  const payload = await readResponsePayload(response)
  return unwrapBusinessPayload(payload, '请求失败')
}

export { request, buildUrl, readResponsePayload, resolveBusinessMessage, unwrapBusinessPayload }
