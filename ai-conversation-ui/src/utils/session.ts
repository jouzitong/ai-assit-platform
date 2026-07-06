export const TOKEN_STORAGE_KEY = 'ai-conversation-ui-token'
export const USER_STORAGE_KEY = 'ai-conversation-ui-user'
export const THEME_STORAGE_KEY = 'ai-conversation-ui-theme'
export const LOGIN_PATH = '/auth/login'

export function getToken() {
  return window.localStorage.getItem(TOKEN_STORAGE_KEY)
}

export function getStoredUser<T = unknown>() {
  const rawValue = window.localStorage.getItem(USER_STORAGE_KEY)
  if (!rawValue) {
    return null
  }

  try {
    return JSON.parse(rawValue) as T
  } catch {
    return null
  }
}

export function setSession(session: { token: string; user?: unknown | null }) {
  window.localStorage.setItem(TOKEN_STORAGE_KEY, session.token)
  if (session.user === undefined) {
    return
  }
  if (session.user === null) {
    window.localStorage.removeItem(USER_STORAGE_KEY)
    return
  }
  window.localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(session.user))
}

export function clearSession() {
  window.localStorage.removeItem(TOKEN_STORAGE_KEY)
  window.localStorage.removeItem(USER_STORAGE_KEY)
}

export function isLoginRoute(path = window.location.pathname) {
  return path === LOGIN_PATH
}

export function normalizeRedirectPath(redirectPath?: string | null) {
  if (!redirectPath || !redirectPath.startsWith('/') || redirectPath.startsWith(LOGIN_PATH)) {
    return ''
  }
  return redirectPath
}

export function buildLoginPath(redirectPath?: string | null) {
  const normalizedRedirectPath = normalizeRedirectPath(redirectPath)
  if (!normalizedRedirectPath) {
    return LOGIN_PATH
  }

  return `${LOGIN_PATH}?redirect=${encodeURIComponent(normalizedRedirectPath)}`
}

export function redirectToLogin(redirectPath?: string | null) {
  clearSession()

  const currentPath = `${window.location.pathname}${window.location.search}${window.location.hash}`
  if (isLoginRoute()) {
    return
  }

  window.location.replace(buildLoginPath(redirectPath ?? currentPath))
}
