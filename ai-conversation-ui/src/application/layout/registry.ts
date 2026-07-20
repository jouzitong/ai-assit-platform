import type { Component } from 'vue'
import ApplicationLayoutContainer from './runtime/ApplicationLayoutContainer.vue'

export type ApplicationLayoutKind =
  | 'page'
  | 'container'
  | 'section'
  | 'stack'
  | 'grid'
  | 'split'
  | 'sheet'

export interface ApplicationLayoutDefinition {
  key: string
  aliases?: readonly string[]
  kind: ApplicationLayoutKind
  component: Component
}

const APPLICATION_LAYOUT_DEFINITIONS = [
  { key: 'zg-page-layout', aliases: ['page'], kind: 'page', component: ApplicationLayoutContainer },
  { key: 'zg-container-layout', aliases: ['container'], kind: 'container', component: ApplicationLayoutContainer },
  { key: 'zg-section-layout', aliases: ['section'], kind: 'section', component: ApplicationLayoutContainer },
  { key: 'zg-stack-layout', aliases: ['stack'], kind: 'stack', component: ApplicationLayoutContainer },
  { key: 'zg-grid-layout', aliases: ['grid'], kind: 'grid', component: ApplicationLayoutContainer },
  { key: 'zg-split-layout', aliases: ['split'], kind: 'split', component: ApplicationLayoutContainer },
  { key: 'zg-sheet-layout', aliases: ['sheet'], kind: 'sheet', component: ApplicationLayoutContainer },
] as const satisfies readonly ApplicationLayoutDefinition[]

const APPLICATION_LAYOUT_MAP = new Map<string, ApplicationLayoutDefinition>()

for (const definition of APPLICATION_LAYOUT_DEFINITIONS) {
  APPLICATION_LAYOUT_MAP.set(definition.key, definition)
  for (const alias of definition.aliases || []) {
    APPLICATION_LAYOUT_MAP.set(alias, definition)
  }
}

export function listApplicationLayouts() {
  return [...APPLICATION_LAYOUT_DEFINITIONS]
}

export function findApplicationLayout(layoutKey?: string) {
  if (!layoutKey) {
    return undefined
  }
  return APPLICATION_LAYOUT_MAP.get(layoutKey.toLowerCase())
}
