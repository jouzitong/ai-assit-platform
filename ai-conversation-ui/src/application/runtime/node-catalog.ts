export type ApplicationStaticRenderNodeKind = 'text' | 'heading'

export interface ApplicationStaticRenderNodeCatalogEntry {
  key: string
  aliases: readonly string[]
  kind: ApplicationStaticRenderNodeKind
}

export const APPLICATION_STATIC_RENDER_NODE_CATALOG = [
  { key: 'text', aliases: [], kind: 'text' },
  { key: 'heading', aliases: ['title'], kind: 'heading' },
] as const satisfies readonly ApplicationStaticRenderNodeCatalogEntry[]

const APPLICATION_STATIC_RENDER_NODE_MAP = new Map<string, ApplicationStaticRenderNodeCatalogEntry>()

for (const definition of APPLICATION_STATIC_RENDER_NODE_CATALOG) {
  APPLICATION_STATIC_RENDER_NODE_MAP.set(definition.key.toLowerCase(), definition)
  for (const alias of definition.aliases) {
    APPLICATION_STATIC_RENDER_NODE_MAP.set(alias.toLowerCase(), definition)
  }
}

export function findApplicationStaticRenderNode(nodeKey?: string) {
  if (!nodeKey) return undefined
  return APPLICATION_STATIC_RENDER_NODE_MAP.get(nodeKey.toLowerCase())
}
