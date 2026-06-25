export function resolveHooks(hooks = {}) {
  return {
    beforeLoad: hooks.beforeLoad ?? null,
    afterLoad: hooks.afterLoad ?? null
  }
}
