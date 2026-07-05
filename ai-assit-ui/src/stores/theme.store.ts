import { reactive, readonly } from 'vue'
import { THEME_STORAGE_KEY } from '../utils/session'
import { DEFAULT_THEME, getThemeDefinition, isThemeName } from '../themes/registry'
import type { ThemeName } from '../themes/types'

const THEME_TRANSITION_CLASS = 'theme-transition'
const THEME_TRANSITION_DURATION = 220

const state = reactive({
  currentTheme: DEFAULT_THEME as ThemeName,
  initialized: false,
})

let transitionTimer: number | null = null

function normalizeTheme(theme: unknown): ThemeName {
  return isThemeName(theme) ? theme : DEFAULT_THEME
}

function persistTheme(theme: ThemeName) {
  window.localStorage.setItem(THEME_STORAGE_KEY, theme)
}

function startThemeTransition() {
  document.documentElement.classList.add(THEME_TRANSITION_CLASS)
  if (transitionTimer !== null) {
    window.clearTimeout(transitionTimer)
  }
  transitionTimer = window.setTimeout(() => {
    document.documentElement.classList.remove(THEME_TRANSITION_CLASS)
    transitionTimer = null
  }, THEME_TRANSITION_DURATION)
}

function syncThemeToDom(theme: ThemeName) {
  const root = document.documentElement
  const definition = getThemeDefinition(theme)
  root.setAttribute('data-theme', theme)
  root.style.colorScheme = definition.colorScheme
}

export function getSavedTheme(): ThemeName {
  return normalizeTheme(window.localStorage.getItem(THEME_STORAGE_KEY))
}

export function applyTheme(theme: ThemeName, options: { persist?: boolean; withTransition?: boolean } = {}): ThemeName {
  const nextTheme = normalizeTheme(theme)
  const { persist = true, withTransition = true } = options

  if (withTransition) {
    startThemeTransition()
  }

  syncThemeToDom(nextTheme)
  state.currentTheme = nextTheme
  state.initialized = true

  if (persist) {
    persistTheme(nextTheme)
  }

  return nextTheme
}

export function initTheme(): ThemeName {
  return applyTheme(getSavedTheme(), { persist: false, withTransition: false })
}

export function toggleTheme(): ThemeName {
  return applyTheme(state.currentTheme === 'dark' ? 'light' : 'dark')
}

export function useThemeStore() {
  return {
    state: readonly(state),
    applyTheme,
    getSavedTheme,
    initTheme,
    toggleTheme,
  }
}
