import { GATEWAY_BASE_URL } from '../config/runtime'

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

async function request(path, options = {}) {
  const response = await fetch(buildUrl(path), {
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {})
    },
    ...options
  })

  if (!response.ok) {
    const errorText = await response.text().catch(() => '')
    throw new Error(errorText || `Request failed with status ${response.status}`)
  }

  const contentType = response.headers.get('content-type') || ''
  if (contentType.includes('application/json')) {
    return response.json()
  }
  return response.text()
}

export { request, buildUrl }
