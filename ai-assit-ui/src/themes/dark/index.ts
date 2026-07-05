import type { ThemeDefinition } from '../types'
import { darkThemeTokens } from './tokens'

export const darkTheme: ThemeDefinition = {
  name: 'dark',
  label: '深色主题',
  colorScheme: 'dark',
  tokens: darkThemeTokens,
}
