import type { ThemeDefinition } from '../types'
import { lightThemeTokens } from './tokens'

export const lightTheme: ThemeDefinition = {
  name: 'light',
  label: '浅色主题',
  colorScheme: 'light',
  tokens: lightThemeTokens,
}
