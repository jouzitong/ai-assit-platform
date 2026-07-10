import { findApplicationRenderer } from '../registry'

export async function resolveRendererRuntimeData(
  rendererKey: string | undefined,
  options: unknown,
) {
  const definition = findApplicationRenderer(rendererKey)
  if (!definition) {
    throw new Error(`未找到 renderer: ${rendererKey || 'unknown'}`)
  }

  if (!definition.resolveData) {
    return {
      definition,
      resolved: null,
    }
  }

  const resolved = await definition.resolveData(options)

  return {
    definition,
    resolved,
  }
}
