export type ThemeName = 'light' | 'dark'

export type ThemeColorScheme = 'light' | 'dark'

export interface ThemeTokens {
  fontFamily: string[]
  bgCanvas: string
  bgSurface: string
  bgSurfaceRaised: string
  bgSurfaceMuted: string
  textPrimary: string
  textSecondary: string
  textMuted: string
  borderDefault: string
  borderStrong: string
  brandPrimary: string
  brandSecondary: string
  success: string
  warning: string
  danger: string
  overlay: string
  shellNav: string
  shellSidebar: string
  shellPanel: string
  shellPopup: string
  controlFill: string
  controlFillHover: string
  controlFillDanger: string
  controlFillDangerHover: string
  glowPrimary: string
  glowSecondary: string
  shadowSm: string
  shadowMd: string
  shadowLg: string
  shadowXl: string
  scrollbarTrack: string
  scrollbarThumb: string
  scrollbarThumbHover: string
}

export interface ThemeDefinition {
  name: ThemeName
  label: string
  colorScheme: ThemeColorScheme
  tokens: ThemeTokens
}
