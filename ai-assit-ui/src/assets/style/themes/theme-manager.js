import { THEME_STORAGE_KEY } from '../../../utils/session'

export const DEFAULT_THEME = 'light'
export const VALID_THEMES = ['dark', 'light']

export function normalizeTheme(theme) {
  return VALID_THEMES.includes(theme) ? theme : DEFAULT_THEME
}

export function applyTheme(theme) {
  const targetTheme = normalizeTheme(theme)
  document.documentElement.setAttribute('data-theme', targetTheme)
  window.localStorage.setItem(THEME_STORAGE_KEY, targetTheme)
  return targetTheme
}

export function getSavedTheme() {
  return normalizeTheme(window.localStorage.getItem(THEME_STORAGE_KEY))
}

export function initTheme() {
  return applyTheme(getSavedTheme())
}
