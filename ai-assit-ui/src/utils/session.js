export const TOKEN_STORAGE_KEY = 'emp-console-token'
export const USER_STORAGE_KEY = 'emp-console-user'
export const THEME_STORAGE_KEY = 'emp-console-theme'

export function getToken() {
  return window.localStorage.getItem(TOKEN_STORAGE_KEY)
}

export function setSession(session) {
  window.localStorage.setItem(TOKEN_STORAGE_KEY, session.token)
  window.localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(session.user))
}

export function clearSession() {
  window.localStorage.removeItem(TOKEN_STORAGE_KEY)
  window.localStorage.removeItem(USER_STORAGE_KEY)
}
