export type ScrollMinimapAnchorKind = 'default' | 'current' | 'muted'

export type ScrollMinimapAnchor = {
  id: string
  label?: string
  top: number
  height?: number
  kind?: ScrollMinimapAnchorKind
}
