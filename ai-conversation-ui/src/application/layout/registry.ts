import type { Component } from 'vue'
import ApplicationLayoutContainer from './runtime/ApplicationLayoutContainer.vue'
import { APPLICATION_LAYOUT_CATALOG, type ApplicationLayoutKind } from './catalog'

export type { ApplicationLayoutKind } from './catalog'

export interface ApplicationLayoutDefinition {
  key: string
  aliases?: readonly string[]
  kind: ApplicationLayoutKind
  component: Component
}

const APPLICATION_LAYOUT_DEFINITIONS = APPLICATION_LAYOUT_CATALOG.map(definition => ({
  ...definition,
  component: ApplicationLayoutContainer,
})) satisfies readonly ApplicationLayoutDefinition[]

const APPLICATION_LAYOUT_MAP = new Map<string, ApplicationLayoutDefinition>()

for (const definition of APPLICATION_LAYOUT_DEFINITIONS) {
  APPLICATION_LAYOUT_MAP.set(definition.key.toLowerCase(), definition)
  for (const alias of definition.aliases || []) {
    APPLICATION_LAYOUT_MAP.set(alias.toLowerCase(), definition)
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
