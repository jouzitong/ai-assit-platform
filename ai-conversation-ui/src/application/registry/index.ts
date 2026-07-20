import { LIST_RENDERER_DEFINITION } from './list'
import { FORM_RENDERER_DEFINITION } from './form'
import { CHART_RENDERER_DEFINITIONS } from './charts'
import type { ApplicationRendererDefinition } from './types'

const APPLICATION_RENDERER_DEFINITIONS = [
  LIST_RENDERER_DEFINITION,
  FORM_RENDERER_DEFINITION,
  ...CHART_RENDERER_DEFINITIONS,
] as const satisfies readonly ApplicationRendererDefinition<any>[]

const APPLICATION_RENDERER_MAP = new Map<string, ApplicationRendererDefinition<any>>()

for (const definition of APPLICATION_RENDERER_DEFINITIONS) {
  APPLICATION_RENDERER_MAP.set(definition.key, definition)
  for (const alias of definition.aliases || []) {
    APPLICATION_RENDERER_MAP.set(alias, definition)
  }
}

export function listApplicationRenderers() {
  return [...APPLICATION_RENDERER_DEFINITIONS]
}

export function findApplicationRenderer(rendererKey?: string) {
  if (!rendererKey) {
    return undefined
  }
  return APPLICATION_RENDERER_MAP.get(rendererKey)
}

export function hasApplicationRenderer(rendererKey?: string) {
  return Boolean(findApplicationRenderer(rendererKey))
}

export function resolveApplicationRenderer(rendererKey?: string) {
  return findApplicationRenderer(rendererKey)?.component
}

export type { ApplicationRendererDefinition } from './types'
export { LIST_RENDERER_DEFINITION, LIST_RENDERER_KEY } from './list'
export { FORM_RENDERER_DEFINITION, FORM_RENDERER_KEY } from './form'
export { CHART_RENDERER_DEFINITIONS } from './charts'
