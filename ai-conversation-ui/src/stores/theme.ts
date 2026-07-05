import { THEME_STORAGE_KEY } from '../utils/session'

export type ThemeName = 'light' | 'dark'

const DEFAULT_THEME: ThemeName = 'light'
const THEME_TRANSITION_CLASS = 'theme-transition'
const THEME_TRANSITION_DURATION = 220

let transitionTimer: number | null = null

function normalizeTheme(theme: unknown): ThemeName {
  return theme === 'dark' ? 'dark' : DEFAULT_THEME
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

export function getSavedTheme(): ThemeName {
  return normalizeTheme(window.localStorage.getItem(THEME_STORAGE_KEY))
}

export function applyTheme(theme: ThemeName, options: { persist?: boolean; withTransition?: boolean } = {}): ThemeName {
  const nextTheme = normalizeTheme(theme)
  const { persist = true, withTransition = true } = options
  const root = document.documentElement

  if (withTransition) {
    startThemeTransition()
  }

  root.setAttribute('data-theme', nextTheme)
  root.style.colorScheme = nextTheme

  if (persist) {
    window.localStorage.setItem(THEME_STORAGE_KEY, nextTheme)
  }

  return nextTheme
}

export function initTheme(): ThemeName {
  return applyTheme(getSavedTheme(), { persist: false, withTransition: false })
}
