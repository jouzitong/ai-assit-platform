function createFunctionHook(source) {
  const expression = String(source || '').trim()
  if (!expression.startsWith('=')) {
    return null
  }

  try {
    return Function(`return (${expression.slice(1)})`)()
  } catch (error) {
    throw new Error(`Hook 解析失败: ${error.message}`)
  }
}

const builtinHooks = {
  mergeDefaultQuery(context, params = {}) {
    Object.entries(params).forEach(([key, value]) => {
      const currentValue = context.state.query[key]
      if (currentValue === '' || currentValue === null || currentValue === undefined) {
        context.setQueryValue(key, value)
      }
    })
  },
  buildListSummary(context, params = {}) {
    const rows = Array.isArray(context.state.rows) ? context.state.rows : []
    const total = Number(context.state.total || rows.length || 0)
    const stats = Array.isArray(params.stats) ? params.stats.map((item) => {
      if (item.valueFrom === 'total') {
        return { key: item.key || item.label, label: item.label, value: total }
      }
      if (item.countBy?.field) {
        const count = rows.filter((row) => row?.[item.countBy.field] === item.countBy.equals).length
        return { key: item.key || item.label, label: item.label, value: count }
      }
      return { key: item.key || item.label, label: item.label, value: item.value ?? 0 }
    }) : []
    context.setMeta('statsItems', stats)
  }
}

export async function executeHook(hookConfig, context) {
  if (!hookConfig) {
    return
  }

  if (typeof hookConfig === 'function') {
    await hookConfig(context)
    return
  }

  if (typeof hookConfig === 'string') {
    const hook = createFunctionHook(hookConfig)
    if (!hook) {
      return
    }
    await hook(context)
    return
  }

  if (hookConfig.type === 'builtin' && hookConfig.name && builtinHooks[hookConfig.name]) {
    await builtinHooks[hookConfig.name](context, hookConfig.params || {})
  }
}
