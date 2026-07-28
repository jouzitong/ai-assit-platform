export type ApplicationLayoutKind =
  | 'page'
  | 'container'
  | 'section'
  | 'stack'
  | 'grid'
  | 'split'
  | 'sheet'

export interface ApplicationLayoutCatalogEntry {
  key: string
  aliases: readonly string[]
  kind: ApplicationLayoutKind
}

export const APPLICATION_LAYOUT_CATALOG = [
  { key: 'zg-page-layout', aliases: ['page'], kind: 'page' },
  { key: 'zg-container-layout', aliases: ['container'], kind: 'container' },
  { key: 'zg-section-layout', aliases: ['section'], kind: 'section' },
  { key: 'zg-stack-layout', aliases: ['stack'], kind: 'stack' },
  { key: 'zg-grid-layout', aliases: ['grid'], kind: 'grid' },
  { key: 'zg-split-layout', aliases: ['split'], kind: 'split' },
  { key: 'zg-sheet-layout', aliases: ['sheet'], kind: 'sheet' },
] as const satisfies readonly ApplicationLayoutCatalogEntry[]
