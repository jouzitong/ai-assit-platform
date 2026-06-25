import ListRenderer from '../renderer/renderers/ListRenderer.vue'
import PlaceholderRenderer from '../renderer/renderers/PlaceholderRenderer.vue'

const rendererRegistry = {
  list: ListRenderer,
  info: PlaceholderRenderer,
  form: PlaceholderRenderer,
  report: PlaceholderRenderer
}

export function resolveRenderer(type) {
  return rendererRegistry[type] || PlaceholderRenderer
}
