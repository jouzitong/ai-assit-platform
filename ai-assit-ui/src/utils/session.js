export const TOKEN_STORAGE_KEY = 'emp-console-token'
export const USER_STORAGE_KEY = 'emp-console-user'
export const THEME_STORAGE_KEY = 'emp-console-theme'

export function getToken() {
  return window.localStorage.getItem(TOKEN_STORAGE_KEY)
}

function decodeBase64Url(value) {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/')
  const padding = normalized.length % 4
  const padded = padding === 0 ? normalized : `${normalized}${'='.repeat(4 - padding)}`
  return window.atob(padded)
}

export function parseJwtPayload(token) {
  if (!token || typeof token !== 'string') {
    return null
  }

  const parts = token.split('.')
  if (parts.length !== 3) {
    return null
  }

  try {
    const payload = decodeBase64Url(parts[1])
    return JSON.parse(payload)
  } catch {
    return null
  }
}

export function isTokenPastHalfLife(token, nowSeconds = Math.floor(Date.now() / 1000)) {
  const payload = parseJwtPayload(token)
  const iat = Number(payload?.iat)
  const exp = Number(payload?.exp)

  if (!Number.isFinite(iat) || !Number.isFinite(exp) || exp <= iat) {
    return false
  }

  const totalLifetime = exp - iat
  const remainingLifetime = exp - nowSeconds
  return remainingLifetime <= totalLifetime / 2
}

export function setSession(session) {
  window.localStorage.setItem(TOKEN_STORAGE_KEY, session.token)
  window.localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(session.user))
}

export function clearSession() {
  window.localStorage.removeItem(TOKEN_STORAGE_KEY)
  window.localStorage.removeItem(USER_STORAGE_KEY)
}
