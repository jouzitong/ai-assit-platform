import { darkTheme } from './dark'
import { lightTheme } from './default'
import type { ThemeDefinition, ThemeName } from './types'

export const DEFAULT_THEME: ThemeName = 'light'

export const themeRegistry: Record<ThemeName, ThemeDefinition> = {
  light: lightTheme,
  dark: darkTheme,
}

export const themeList = Object.values(themeRegistry)

export function isThemeName(value: unknown): value is ThemeName {
  return typeof value === 'string' && value in themeRegistry
}

export function getThemeDefinition(themeName: ThemeName): ThemeDefinition {
  return themeRegistry[themeName]
}
