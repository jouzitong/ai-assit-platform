import type { App, Directive } from 'vue'

const loadingDirective: Directive<HTMLElement, boolean> = {
  mounted(el, binding) {
    if (binding.value) {
      el.setAttribute('aria-busy', 'true')
    }
  },
  updated(el, binding) {
    if (binding.value) {
      el.setAttribute('aria-busy', 'true')
    } else {
      el.removeAttribute('aria-busy')
    }
  },
}

export function registerLoadingDirective(app: App) {
  app.directive('loading-state', loadingDirective)
}
