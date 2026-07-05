import type { App, Directive } from 'vue'

const permissionDirective: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding) {
    const value = binding.value
    const permissions = Array.isArray(value) ? value : value ? [value] : []
    if (permissions.length === 0) {
      return
    }
    el.dataset.permission = permissions.join(',')
  },
}

export function registerPermissionDirective(app: App) {
  app.directive('permission', permissionDirective)
}
